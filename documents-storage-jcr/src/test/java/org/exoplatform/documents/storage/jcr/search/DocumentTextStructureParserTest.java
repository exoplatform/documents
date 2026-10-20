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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTString;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import org.exoplatform.documents.model.DocumentOutlineEntry;
import org.exoplatform.documents.storage.jcr.search.DocumentTextStructureParser.CountingWriter;
import org.exoplatform.documents.storage.jcr.search.DocumentTextStructureParser.TextLimitReachedException;

/**
 * Parses Word documents, presentations and PDFs built with POI and PDFBox, and
 * checks every heading's offset against the text written, which is what a
 * section read relies on.
 */
class DocumentTextStructureParserTest {

  @TempDir
  Path directory;

  /**
   * Builds a Word document: a "heading 1" style heading, body text, a heading
   * whose level is set on the paragraph itself, a table, a French "Titre 2"
   * style heading, and more body text.
   *
   * @return the docx file
   * @throws IOException never
   */
  private File wordDocument() throws IOException {
    File file = directory.resolve("thesis.docx").toFile();
    try (XWPFDocument document = new XWPFDocument(); FileOutputStream out = new FileOutputStream(file)) {
      XWPFStyles styles = document.createStyles();
      // ids that say nothing: the level must come from the style's name
      styles.addStyle(paragraphStyle("Style7", "heading 1"));
      styles.addStyle(paragraphStyle("StyleFr", "Titre 2"));
      document.createParagraph().createRun().setText("Introduction");
      document.getParagraphs().get(0).setStyle("Style7");
      document.createParagraph().createRun().setText("Why this thesis exists.");
      var direct = document.createParagraph();
      direct.createRun().setText("Context");
      direct.getCTP().addNewPPr().addNewOutlineLvl().setVal(BigInteger.ONE);
      XWPFTable table = document.createTable(1, 2);
      table.getRow(0).getCell(0).setText("Criterion");
      table.getRow(0).getCell(1).setText("Weight");
      var french = document.createParagraph();
      french.createRun().setText("Méthode");
      french.setStyle("StyleFr");
      document.createParagraph().createRun().setText("We read everything.");
      document.write(out);
    }
    return file;
  }

  /**
   * Builds a paragraph style.
   *
   * @param id its id
   * @param name its name
   * @return the style
   */
  private static XWPFStyle paragraphStyle(String id, String name) {
    CTStyle style = CTStyle.Factory.newInstance();
    style.setStyleId(id);
    CTString styleName = CTString.Factory.newInstance();
    styleName.setVal(name);
    style.setName(styleName);
    style.setType(STStyleType.PARAGRAPH);
    return new XWPFStyle(style);
  }

  /**
   * Parses a file into its text and its outline.
   *
   * @param file the file
   * @param mimeType its type
   * @param text where the text is written
   * @param maxChars the most characters
   * @return the outline
   * @throws IOException when the parser fails
   */
  private static List<DocumentOutlineEntry> parse(File file, String mimeType, StringWriter text, long maxChars) throws IOException {
    List<DocumentOutlineEntry> outline = new ArrayList<>();
    DocumentTextStructureParser.parse(file, mimeType, new CountingWriter(text, maxChars, Long.MAX_VALUE), outline);
    return outline;
  }

  /**
   * Asserts each heading's section starts with its title in the text.
   *
   * @param text the text
   * @param outline the outline
   */
  private static void assertHeadingsAtTheirOffsets(String text, List<DocumentOutlineEntry> outline) {
    for (DocumentOutlineEntry entry : outline) {
      assertTrue(text.substring((int) entry.offset()).contains(entry.title()),
                 () -> "heading '" + entry.title() + "' not found from its offset " + entry.offset());
    }
  }

  /**
   * A Word document's headings come from a style named as Word names it, a
   * level set on the paragraph, or a translated style name, each at the
   * character its paragraph starts; the body text and the table are written
   * in their order, the table's cells on one line.
   * Mutants: a heading's offset taken after its text, the paragraph's own
   * level ignored, the translated name ignored, the table dropped.
   *
   * @throws IOException never
   */
  @Test
  void aWordDocumentsHeadingsStartTheirParagraphsInTheText() throws IOException {
    StringWriter text = new StringWriter();

    List<DocumentOutlineEntry> outline = parse(wordDocument(), DocumentTextStructureParser.DOCX_MIME_TYPE, text, Long.MAX_VALUE);

    String written = text.toString();
    assertEquals(List.of("Introduction", "Context", "Méthode"), outline.stream().map(DocumentOutlineEntry::title).toList());
    assertEquals(List.of(1, 2, 2), outline.stream().map(DocumentOutlineEntry::level).toList());
    assertEquals(0, outline.get(0).offset());
    assertTrue(written.substring((int) outline.get(1).offset()).startsWith("Context\n"));
    assertTrue(written.substring((int) outline.get(2).offset()).startsWith("Méthode\n"));
    assertTrue(written.contains("Criterion | Weight\n"));
    assertTrue(written.indexOf("Criterion") < outline.get(2).offset());
    assertNull(outline.get(0).page());
  }

  /**
   * A text cut at the most characters keeps the headings of the text written
   * and none past it, and the cut is known.
   * Mutants: the outline dropped on the cut, a heading past the cut kept.
   *
   * @throws IOException never
   */
  @Test
  void aCutTextKeepsTheHeadingsWrittenBeforeTheCut() throws IOException {
    StringWriter text = new StringWriter();
    List<DocumentOutlineEntry> outline = new ArrayList<>();
    CountingWriter writer = new CountingWriter(text, 40, Long.MAX_VALUE);

    assertThrows(TextLimitReachedException.class,
                 () -> DocumentTextStructureParser.parse(wordDocument(), DocumentTextStructureParser.DOCX_MIME_TYPE, writer, outline));

    assertTrue(writer.isTruncated());
    assertEquals(40, writer.count());
    assertEquals(40, text.toString().length());
    assertEquals(List.of("Introduction", "Context"), outline.stream().map(DocumentOutlineEntry::title).toList());
  }

  /**
   * A presentation's slides are written in order, each slide's title, or its
   * number when it has none, being an outline entry at the slide's start,
   * with the slide's number; the title is not written twice.
   * Mutants: a slide without a title left out, the title written again with
   * the body, the slide number dropped.
   *
   * @throws IOException never
   */
  @Test
  void aPresentationsSlidesAreItsOutline() throws IOException {
    File file = directory.resolve("deck.pptx").toFile();
    try (XMLSlideShow show = new XMLSlideShow(); FileOutputStream out = new FileOutputStream(file)) {
      XSLFSlideLayout layout = show.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT);
      XSLFSlide first = show.createSlide(layout);
      first.getPlaceholder(0).setText("Budget 2026");
      first.getPlaceholder(1).setText("Up 4%");
      show.createSlide().createTextBox().setText("Questions?");
      show.write(out);
    }
    StringWriter text = new StringWriter();

    List<DocumentOutlineEntry> outline = parse(file, DocumentTextStructureParser.PPTX_MIME_TYPE, text, Long.MAX_VALUE);

    String written = text.toString();
    assertEquals(List.of("Budget 2026", "Slide 2"), outline.stream().map(DocumentOutlineEntry::title).toList());
    assertEquals(List.of(1, 2), outline.stream().map(DocumentOutlineEntry::page).toList());
    assertHeadingsAtTheirOffsets(written, outline);
    assertTrue(written.contains("Up 4%"));
    assertTrue(written.contains("Questions?"));
    assertEquals(written.indexOf("Budget 2026"), written.lastIndexOf("Budget 2026"));
  }

  /**
   * A PDF's bookmarks, nested ones included, are outline entries at the start
   * of the page each points to, with the page's number and the depth of the
   * bookmark.
   * Mutants: a nested bookmark dropped, the offset of the wrong page, the
   * depth flattened.
   *
   * @throws IOException never
   */
  @Test
  void aPdfsBookmarksStartTheirPagesInTheText() throws IOException {
    File file = directory.resolve("book.pdf").toFile();
    try (PDDocument document = new PDDocument()) {
      List<PDPage> pages = new ArrayList<>();
      for (String line : List.of("Cover page", "Chapter one starts", "Section one dot one")) {
        PDPage page = new PDPage();
        document.addPage(page);
        pages.add(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
          stream.beginText();
          stream.setFont(PDType1Font.HELVETICA, 12);
          stream.newLineAtOffset(72, 700);
          stream.showText(line);
          stream.endText();
        }
      }
      PDDocumentOutline bookmarks = new PDDocumentOutline();
      document.getDocumentCatalog().setDocumentOutline(bookmarks);
      PDOutlineItem chapter = bookmark("Chapter 1", pages.get(1));
      bookmarks.addLast(chapter);
      chapter.addLast(bookmark("1.1 Scope", pages.get(2)));
      document.save(file);
    }
    StringWriter text = new StringWriter();

    List<DocumentOutlineEntry> outline = parse(file, DocumentTextStructureParser.PDF_MIME_TYPE, text, Long.MAX_VALUE);

    String written = text.toString();
    assertEquals(List.of("Chapter 1", "1.1 Scope"), outline.stream().map(DocumentOutlineEntry::title).toList());
    assertEquals(List.of(1, 2), outline.stream().map(DocumentOutlineEntry::level).toList());
    assertEquals(List.of(2, 3), outline.stream().map(DocumentOutlineEntry::page).toList());
    assertTrue(written.substring((int) outline.get(0).offset()).startsWith("Chapter one starts"));
    assertTrue(written.substring((int) outline.get(1).offset()).startsWith("Section one dot one"));
    assertFalse(written.substring((int) outline.get(0).offset()).startsWith("Cover"));
  }

  /**
   * Builds a bookmark to a page.
   *
   * @param title the bookmark's title
   * @param page its page
   * @return the bookmark
   */
  private static PDOutlineItem bookmark(String title, PDPage page) {
    PDOutlineItem item = new PDOutlineItem();
    item.setTitle(title);
    PDPageFitDestination destination = new PDPageFitDestination();
    destination.setPage(page);
    item.setDestination(destination);
    return item;
  }

  /**
   * The structured types are the Word document, the presentation and the
   * PDF, whatever their parameters; any other type is read by the platform's
   * readers.
   * Mutants: a parameter making a type unknown, another type taken.
   */
  @Test
  void onlyWordPresentationAndPdfAreStructured() {
    assertTrue(DocumentTextStructureParser.isStructured("application/pdf; charset=binary"));
    assertTrue(DocumentTextStructureParser.isStructured(DocumentTextStructureParser.DOCX_MIME_TYPE.toUpperCase()));
    assertTrue(DocumentTextStructureParser.isStructured(DocumentTextStructureParser.PPTX_MIME_TYPE));
    assertFalse(DocumentTextStructureParser.isStructured("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    assertFalse(DocumentTextStructureParser.isStructured(null));
  }

  /**
   * The writer refuses to write past a deadline, so a slow file stops being
   * read.
   * Mutants: the deadline never checked.
   */
  @Test
  void theWriterRefusesPastItsDeadline() {
    CountingWriter writer = new CountingWriter(new StringWriter(), Long.MAX_VALUE, System.nanoTime() - 1);

    assertThrows(java.io.InterruptedIOException.class, () -> writer.write("late"));
  }

}
