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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ICell;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFSDTCell;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;

import org.exoplatform.documents.model.DocumentOutlineEntry;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

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

  /** The most bookmarks read from one PDF, its outline being cut there. */
  static final int              MAX_BOOKMARKS     = 10_000;

  private static final Log      LOG               = ExoLogger.getLogger(DocumentTextStructureParser.class);

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
   * Writes a Word document's paragraphs, tables and block content controls in
   * their order, each heading paragraph becoming an outline entry, then its
   * headers, footers and comments.
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
            for (ICell cell : row.getTableICells()) {
              cells.add(cellText(cell));
            }
            out.write(String.join(" | ", cells));
            out.write('\n');
          }
          out.write('\n');
        } else if (element instanceof XWPFSDT contentControl) {
          // a block content control, as in documents made from a template: its text, no heading read in it
          String text = StringUtils.trimToNull(contentControl.getContent().getText());
          if (text != null) {
            out.write(text);
            out.write('\n');
          }
        }
      }
      writeWordMargins(document, out);
    } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
      throw new IOException("Not a Word document", e);
    }
  }

  /**
   * Writes the text of a Word document's headers, footers and comments after
   * its body, as the one-shot read gives them, so the headings of the body
   * keep their offsets. A header or footer repeated by several sections is
   * written once.
   *
   * @param document the document
   * @param out where the text is written
   * @throws IOException when the writer refuses more text
   */
  private static void writeWordMargins(XWPFDocument document, CountingWriter out) throws IOException {
    Set<String> written = new HashSet<>();
    for (XWPFHeaderFooter part : document.getHeaderList()) {
      writeLabelled("Header: ", part.getText(), written, out);
    }
    for (XWPFHeaderFooter part : document.getFooterList()) {
      writeLabelled("Footer: ", part.getText(), written, out);
    }
    // null for a document without comments
    XWPFComment[] comments = document.getComments();
    for (XWPFComment comment : comments == null ? new XWPFComment[0] : comments) {
      String author = StringUtils.trimToNull(comment.getAuthor());
      writeLabelled(author == null ? "Comment: " : "Comment by %s: ".formatted(author), comment.getText(), null, out);
    }
  }

  /**
   * Writes a text on its own line after a label, nothing when it is blank or
   * already written.
   *
   * @param label the label
   * @param text the text
   * @param written the texts already written, null to write every one
   * @param out where the text is written
   * @throws IOException when the writer refuses more text
   */
  private static void writeLabelled(String label, String text, Set<String> written, CountingWriter out) throws IOException {
    String line = StringUtils.trimToNull(StringUtils.normalizeSpace(text));
    if (line != null && (written == null || written.add(label + line))) {
      out.write(label);
      out.write(line);
      out.write('\n');
    }
  }

  /**
   * Reads the text of a Word table's cell, as the one-shot read does: its
   * paragraphs, nested tables and content controls, or the content of a
   * content control holding the cell.
   *
   * @param cell the cell
   * @return its text on one line
   */
  private static String cellText(ICell cell) {
    String text = null;
    if (cell instanceof XWPFTableCell tableCell) {
      text = tableCell.getTextRecursively();
    } else if (cell instanceof XWPFSDTCell contentControl) {
      text = contentControl.getContent().getText();
    }
    return StringUtils.normalizeSpace(StringUtils.defaultString(text));
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
   * Writes the text of a slide's shape, the shapes of a group included, the
   * title already written left out.
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
    } else if (shape instanceof XSLFGroupShape group) {
      for (XSLFShape member : group.getShapes()) {
        writeShape(member, title, out);
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
   * Writes a PDF's text in one pass, and turns its bookmarks into outline entries
   * at the start of the page each one points to. Bookmarks pointing to no
   * page, to a page that isn't there or past the text written, or whose
   * destination can't be read, are left out.
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
      PageStartRecordingStripper stripper = new PageStartRecordingStripper(out, pageStarts);
      try {
        stripper.writeText(document, out);
        // the last pages, with no content, start at the end of the text
        stripper.fillUpTo(pages);
      } finally {
        PDDocumentOutline bookmarks = document.getDocumentCatalog().getDocumentOutline();
        if (bookmarks != null) {
          addBookmarks(document, bookmarks, pageStarts, out, outline);
        }
      }
    }
  }

  /**
   * Adds a PDF's bookmarks, depth first. A bookmark met twice, a list of
   * bookmarks looping on itself or on a parent, ends the list it is in; past
   * {@link #MAX_BOOKMARKS} bookmarks, or past the deadline, the walk stops.
   *
   * @param document the PDF
   * @param bookmarks its outline
   * @param pageStarts where each page written starts in the text
   * @param out the writer of the text, whose deadline bounds the walk
   * @param outline the outline to add to
   * @throws IOException when a bookmark can't be read or the deadline is
   *           passed
   */
  private static void addBookmarks(PDDocument document,
                                   PDDocumentOutline bookmarks,
                                   List<Long> pageStarts,
                                   CountingWriter out,
                                   List<DocumentOutlineEntry> outline) throws IOException {
    Map<COSDictionary, Integer> pageIndexes = new IdentityHashMap<>();
    int index = 0;
    for (PDPage page : document.getPages()) {
      pageIndexes.putIfAbsent(page.getCOSObject(), index++);
    }
    Set<COSDictionary> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    visited.add(bookmarks.getCOSObject());
    addBookmarks(document, bookmarks, 1, pageIndexes, pageStarts, visited, out, outline);
  }

  /**
   * Adds the bookmarks under a node, and theirs, following each one's next
   * sibling by hand: PDFBox's own iteration of the children never ends on a
   * list whose last bookmark points back to an earlier one.
   *
   * @param document the PDF
   * @param node the node whose children are added
   * @param level the depth of its children
   * @param pageIndexes the index of each page, by its dictionary
   * @param pageStarts where each page written starts in the text
   * @param visited the bookmarks already met, by their dictionary
   * @param out the writer of the text, whose deadline bounds the walk
   * @param outline the outline to add to
   * @throws IOException when a bookmark can't be read or the deadline is
   *           passed
   */
  private static void addBookmarks(PDDocument document, // NOSONAR
                                   PDOutlineNode node,
                                   int level,
                                   Map<COSDictionary, Integer> pageIndexes,
                                   List<Long> pageStarts,
                                   Set<COSDictionary> visited,
                                   CountingWriter out,
                                   List<DocumentOutlineEntry> outline) throws IOException {
    for (PDOutlineItem item = node.getFirstChild(); item != null; item = item.getNextSibling()) {
      if (visited.size() > MAX_BOOKMARKS || !visited.add(item.getCOSObject())) {
        return;
      }
      out.checkDeadline();
      Integer pageIndex = null;
      String title = null;
      try {
        PDPage page = item.findDestinationPage(document);
        pageIndex = page == null ? null : pageIndexes.get(page.getCOSObject());
        title = StringUtils.trimToNull(StringUtils.normalizeSpace(item.getTitle()));
      } catch (IOException | RuntimeException e) {
        // a destination PDFBox can't resolve, a page number past the last page for instance: left out
        LOG.debug("A PDF bookmark whose destination can't be read is left out", e);
      }
      if (title != null && pageIndex != null && pageIndex < pageStarts.size()) {
        outline.add(new DocumentOutlineEntry(outline.size(), level, title, pageStarts.get(pageIndex), pageIndex + 1));
      }
      if (level < MAX_HEADING_LEVEL) {
        addBookmarks(document, item, level + 1, pageIndexes, pageStarts, visited, out, outline);
      }
    }
  }

}
