/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.storage.jcr.search;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;

import org.exoplatform.documents.model.DocumentOutlineEntry;

/**
 * Writes the text of a Word document, a presentation or a PDF together with
 * its outline: the Word headings, the slide titles, the PDF bookmarks, each
 * at the character its section starts in the text written. The text and the
 * outline come from one walk of the file, so the positions match the text.
 * Pictures are never decoded: only the text of the file is read.
 */
final class DocumentTextStructureParser {

  static final String           DOCX_MIME_TYPE   = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  static final String           PPTX_MIME_TYPE   = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

  static final String           PDF_MIME_TYPE    = "application/pdf";

  /** The deepest Word heading kept in the outline. */
  private static final int      MAX_HEADING_LEVEL = 9;

  /** A heading style name, as Word stores it whatever the user's language, or a translated one. */
  private static final Pattern  HEADING_STYLE     = Pattern.compile("(?iu)^(heading|titre|title|\\u00fcberschrift|t\\u00edtulo|titolo|kop)\\s*(\\d)$");

  private DocumentTextStructureParser() {
  }

  /**
   * Tells whether the text and the outline of a file type are read here.
   *
   * @param mimeType the file type
   * @return true for a Word document, a presentation or a PDF
   */
  static boolean isStructured(String mimeType) {
    String type = StringUtils.substringBefore(StringUtils.trimToEmpty(mimeType), ";").trim().toLowerCase(Locale.ROOT);
    return DOCX_MIME_TYPE.equals(type) || PPTX_MIME_TYPE.equals(type) || PDF_MIME_TYPE.equals(type);
  }

  /**
   * Writes the text of a file and adds its headings to an outline, as they
   * come: when the writer refuses more text, the outline holds the headings
   * of the text written.
   *
   * @param file the file
   * @param mimeType its type, one {@link #isStructured(String)} accepts
   * @param out where the text is written
   * @param outline where the headings are added, in the order they appear
   * @throws IOException when the file can't be read or the writer refuses
   *           more text
   */
  static void parse(File file, String mimeType, CountingWriter out, List<DocumentOutlineEntry> outline) throws IOException {
    String type = StringUtils.substringBefore(StringUtils.trimToEmpty(mimeType), ";").trim().toLowerCase(Locale.ROOT);
    switch (type) {
    case DOCX_MIME_TYPE -> parseWord(file, out, outline);
    case PPTX_MIME_TYPE -> parsePresentation(file, out, outline);
    case PDF_MIME_TYPE -> parsePdf(file, out, outline);
    default -> throw new IllegalArgumentException("Not a structured type: " + mimeType);
    }
  }

  /**
   * Writes a Word document's paragraphs and tables in their order, each
   * heading paragraph becoming an outline entry.
   *
   * @param file the docx file
   * @param out where the text is written
   * @param outline where the headings are added
   * @throws IOException when the file can't be read
   */
  private static void parseWord(File file, CountingWriter out, List<DocumentOutlineEntry> outline) throws IOException {
    try (OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ); XWPFDocument document = new XWPFDocument(pkg)) {
      XWPFStyles styles = document.getStyles();
      for (IBodyElement element : document.getBodyElements()) {
        if (element instanceof XWPFParagraph paragraph) {
          String text = paragraph.getText();
          if (StringUtils.isBlank(text)) {
            continue;
          }
          int level = headingLevel(paragraph, styles);
          if (level > 0) {
            outline.add(new DocumentOutlineEntry(outline.size(), level, text.trim(), out.count(), null));
          }
          out.write(text);
          out.write('\n');
        } else if (element instanceof XWPFTable table) {
          for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
              cells.add(StringUtils.normalizeSpace(cell.getText()));
            }
            out.write(String.join(" | ", cells));
            out.write('\n');
          }
          out.write('\n');
        }
      }
    } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
      throw new IOException("Not a Word document", e);
    }
  }

  /**
   * Tells the heading level of a paragraph: its outline level, set on it or
   * on its style, else the number of a heading style name; 0 for body text.
   *
   * @param paragraph the paragraph
   * @param styles the document's styles
   * @return the level from 1, or 0
   */
  static int headingLevel(XWPFParagraph paragraph, XWPFStyles styles) {
    CTPPr ppr = paragraph.getCTP().getPPr();
    Integer level = ppr == null ? null : outlineLevel(ppr.getOutlineLvl());
    String styleId = paragraph.getStyleID();
    XWPFStyle style = styles == null || styleId == null ? null : styles.getStyle(styleId);
    for (int depth = 0; level == null && style != null && depth < 10; depth++) {
      CTPPrGeneral stylePpr = style.getCTStyle().getPPr();
      level = stylePpr == null ? null : outlineLevel(stylePpr.getOutlineLvl());
      if (level == null) {
        level = levelFromName(style.getName());
      }
      String basedOn = style.getBasisStyleID();
      style = level == null && basedOn != null ? styles.getStyle(basedOn) : null;
    }
    if (level == null && styleId != null) {
      level = levelFromName(styleId);
    }
    return level == null || level < 1 || level > MAX_HEADING_LEVEL ? 0 : level;
  }

  /**
   * Reads a Word outline level, 0 for a top-level heading, 9 for body text.
   *
   * @param outlineLevel the level, null for none
   * @return the heading level from 1, null for none or body text
   */
  private static Integer outlineLevel(CTDecimalNumber outlineLevel) {
    if (outlineLevel == null || outlineLevel.getVal() == null) {
      return null;
    }
    int value = ((BigInteger) outlineLevel.getVal()).intValue();
    return value >= 0 && value < MAX_HEADING_LEVEL ? value + 1 : null;
  }

  /**
   * Reads a heading level from a style name such as "heading 2".
   *
   * @param name the style name or id
   * @return the level, null when the name isn't a heading's
   */
  private static Integer levelFromName(String name) {
    if (name == null) {
      return null;
    }
    Matcher matcher = HEADING_STYLE.matcher(name.trim());
    return matcher.find() ? Integer.parseInt(matcher.group(2)) : null;
  }

  /**
   * Writes a presentation slide by slide, each slide's title, or its number
   * when it has none, becoming an outline entry.
   *
   * @param file the pptx file
   * @param out where the text is written
   * @param outline where the slide titles are added
   * @throws IOException when the file can't be read
   */
  private static void parsePresentation(File file, CountingWriter out, List<DocumentOutlineEntry> outline) throws IOException {
    try (OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ); XMLSlideShow show = new XMLSlideShow(pkg)) {
      int number = 0;
      for (XSLFSlide slide : show.getSlides()) {
        number++;
        String title = StringUtils.trimToNull(StringUtils.normalizeSpace(slide.getTitle()));
        String heading = title == null ? "Slide %d".formatted(number) : title;
        outline.add(new DocumentOutlineEntry(outline.size(), 1, heading, out.count(), number));
        out.write("Slide %d: %s\n".formatted(number, heading));
        for (XSLFShape shape : slide.getShapes()) {
          writeShape(shape, title, out);
        }
        out.write('\n');
      }
    } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
      throw new IOException("Not a presentation", e);
    }
  }

  /**
   * Writes the text of a slide's shape, the title already written left out.
   *
   * @param shape the shape
   * @param title the slide's title, null for none
   * @param out where the text is written
   * @throws IOException when the writer refuses more text
   */
  private static void writeShape(XSLFShape shape, String title, CountingWriter out) throws IOException {
    if (shape instanceof XSLFTextShape textShape) {
      String text = StringUtils.trimToNull(textShape.getText());
      if (text != null && !StringUtils.equals(StringUtils.normalizeSpace(text), title)) {
        out.write(text);
        out.write('\n');
      }
    } else if (shape instanceof XSLFTable table) {
      for (XSLFTableRow row : table.getRows()) {
        List<String> cells = new ArrayList<>();
        for (XSLFTableCell cell : row.getCells()) {
          cells.add(StringUtils.normalizeSpace(cell.getText()));
        }
        out.write(String.join(" | ", cells));
        out.write('\n');
      }
    }
  }

  /**
   * Writes a PDF page by page, and turns its bookmarks into outline entries
   * at the start of the page each one points to. Bookmarks pointing to no
   * page, or past the text written, are left out.
   *
   * @param file the PDF file
   * @param out where the text is written
   * @param outline where the bookmarks are added
   * @throws IOException when the file can't be read
   */
  private static void parsePdf(File file, CountingWriter out, List<DocumentOutlineEntry> outline) throws IOException {
    try (PDDocument document = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())) {
      int pages = document.getNumberOfPages();
      List<Long> pageStarts = new ArrayList<>(pages);
      PDFTextStripper stripper = new PDFTextStripper();
      try {
        for (int page = 1; page <= pages; page++) {
          pageStarts.add(out.count());
          stripper.setStartPage(page);
          stripper.setEndPage(page);
          out.write(stripper.getText(document));
        }
      } finally {
        PDDocumentOutline bookmarks = document.getDocumentCatalog().getDocumentOutline();
        if (bookmarks != null) {
          addBookmarks(document, bookmarks, 1, pageStarts, outline);
        }
      }
    }
  }

  /**
   * Adds the bookmarks under a node, and theirs, depth first.
   *
   * @param document the PDF
   * @param node the node whose children are added
   * @param level the depth of its children
   * @param pageStarts where each page written starts in the text
   * @param outline the outline to add to
   * @throws IOException when a bookmark can't be read
   */
  private static void addBookmarks(PDDocument document,
                                   PDOutlineNode node,
                                   int level,
                                   List<Long> pageStarts,
                                   List<DocumentOutlineEntry> outline) throws IOException {
    for (PDOutlineItem item : node.children()) {
      PDPage page = item.findDestinationPage(document);
      int pageIndex = page == null ? -1 : document.getPages().indexOf(page);
      String title = StringUtils.trimToNull(StringUtils.normalizeSpace(item.getTitle()));
      if (title != null && pageIndex >= 0 && pageIndex < pageStarts.size()) {
        outline.add(new DocumentOutlineEntry(outline.size(), level, title, pageStarts.get(pageIndex), pageIndex + 1));
      }
      if (level < MAX_HEADING_LEVEL) {
        addBookmarks(document, item, level + 1, pageStarts, outline);
      }
    }
  }

  /**
   * A writer counting the characters written, refusing any past a limit or a
   * deadline, so a huge or slow file stops being read at either.
   */
  static class CountingWriter extends Writer {

    private final Writer out;

    private final long   maxChars;

    private final long   deadlineNanos;

    private long         count;

    private boolean      truncated;

    /**
     * Wraps a writer.
     *
     * @param out the writer receiving the text
     * @param maxChars the most characters written
     * @param deadlineNanos the {@link System#nanoTime()} past which writing fails
     */
    CountingWriter(Writer out, long maxChars, long deadlineNanos) {
      this.out = out;
      this.maxChars = maxChars;
      this.deadlineNanos = deadlineNanos;
    }

    /**
     * Tells how many characters were written.
     *
     * @return the count
     */
    long count() {
      return count;
    }

    /**
     * Tells whether text was refused for being past the limit.
     *
     * @return true when the text written is cut
     */
    boolean isTruncated() {
      return truncated;
    }

    /**
     * Writes characters up to the limit; past it, writes what fits and fails
     * with {@link TextLimitReachedException}.
     *
     * @param chars the characters
     * @param offset the first one
     * @param length how many
     * @throws IOException when the limit or the deadline is passed, or the
     *           writer fails
     */
    @Override
    public void write(char[] chars, int offset, int length) throws IOException {
      if (System.nanoTime() > deadlineNanos) {
        throw new java.io.InterruptedIOException("Reading the file took too long");
      }
      if (Thread.currentThread().isInterrupted()) {
        throw new java.io.InterruptedIOException("Text extraction interrupted");
      }
      long room = maxChars - count;
      int written = (int) Math.min(room, length);
      if (written > 0) {
        out.write(chars, offset, written);
        count += written;
      }
      if (written < length) {
        truncated = true;
        throw new TextLimitReachedException();
      }
    }

    /**
     * Flushes the writer.
     *
     * @throws IOException when it fails
     */
    @Override
    public void flush() throws IOException {
      out.flush();
    }

    /**
     * Closes the writer.
     *
     * @throws IOException when it fails
     */
    @Override
    public void close() throws IOException {
      out.close();
    }
  }

  /** Raised once the most characters read from one document are written. */
  static class TextLimitReachedException extends IOException {

    private static final long serialVersionUID = 1L;

    /** Builds the exception. */
    TextLimitReachedException() {
      super("The most characters read from one document were reached");
    }
  }

}
