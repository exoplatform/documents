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

import org.apache.pdfbox.cos.COSName;
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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTString;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import org.exoplatform.documents.model.DocumentOutlineEntry;

/**
 * Parses Word documents, presentations, PDFs and spreadsheets built with POI
 * and PDFBox, and checks every heading's offset against the text written, which
 * is what a section read relies on.
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
   * The text of a shape inside a group, as a presentation's diagrams hold
   * it, is written with the slide's.
   * Mutants: a group's shapes left out.
   *
   * @throws IOException never
   */
  @Test
  void aPresentationsGroupedShapesAreRead() throws IOException {
    File file = directory.resolve("grouped.pptx").toFile();
    try (XMLSlideShow show = new XMLSlideShow(); FileOutputStream out = new FileOutputStream(file)) {
      XSLFSlide slide = show.createSlide();
      slide.createTextBox().setText("Plain box");
      slide.createGroup().createTextBox().setText("Grouped box");
      show.write(out);
    }
    StringWriter text = new StringWriter();

    parse(file, DocumentTextStructureParser.PPTX_MIME_TYPE, text, Long.MAX_VALUE);

    assertTrue(text.toString().contains("Plain box"));
    assertTrue(text.toString().contains("Grouped box"));
  }

  /**
   * The text of a block content control, as documents made from a template
   * hold it, is written in its place among the paragraphs.
   * Mutants: a content control left out.
   *
   * @throws IOException never
   */
  @Test
  void aWordDocumentsContentControlsAreRead() throws IOException {
    File file = directory.resolve("template.docx").toFile();
    try (XWPFDocument document = new XWPFDocument(); FileOutputStream out = new FileOutputStream(file)) {
      document.createParagraph().createRun().setText("Before the control");
      document.getDocument()
              .getBody()
              .addNewSdt()
              .addNewSdtContent()
              .addNewP()
              .addNewR()
              .addNewT()
              .setStringValue("Inside the control");
      document.write(out);
    }
    StringWriter text = new StringWriter();

    parse(file, DocumentTextStructureParser.DOCX_MIME_TYPE, text, Long.MAX_VALUE);

    assertEquals("Before the control\nInside the control\n", text.toString());
  }

  /**
   * A Word table's content controls, inside a cell or holding one, and a
   * table inside a cell, are read with the table's row, as the one-shot read
   * reads them.
   * Mutants: a cell read by its paragraphs only, a cell held by a content
   * control left out, a nested table left out.
   *
   * @throws IOException never
   */
  @Test
  void aWordTablesContentControlsAreRead() throws IOException {
    File file = directory.resolve("form.docx").toFile();
    try (XWPFDocument document = new XWPFDocument(); FileOutputStream out = new FileOutputStream(file)) {
      XWPFTable table = document.createTable(1, 1);
      table.getRow(0)
           .getCell(0)
           .getCTTc()
           .addNewSdt()
           .addNewSdtContent()
           .addNewP()
           .addNewR()
           .addNewT()
           .setStringValue("Field in a cell");
      table.getRow(0)
           .getCell(0)
           .getCTTc()
           .addNewTbl()
           .addNewTr()
           .addNewTc()
           .addNewP()
           .addNewR()
           .addNewT()
           .setStringValue("Table in a cell");
      table.getRow(0)
           .getCtRow()
           .addNewSdt()
           .addNewSdtContent()
           .addNewTc()
           .addNewP()
           .addNewR()
           .addNewT()
           .setStringValue("Cell in a field");
      document.write(out);
    }
    StringWriter text = new StringWriter();

    parse(file, DocumentTextStructureParser.DOCX_MIME_TYPE, text, Long.MAX_VALUE);

    assertTrue(text.toString().contains("Field in a cell"), text.toString());
    assertTrue(text.toString().contains("Cell in a field"), text.toString());
    assertTrue(text.toString().contains("Table in a cell"), text.toString());
  }

  /**
   * A Word document's header, footer and comments are written after its
   * body, a header repeated once, so the body's headings keep their offsets.
   * Mutants: the header, the footer or the comments left out, a repeated
   * header written twice, the margins written before the body.
   *
   * @throws IOException never
   */
  @Test
  void aWordDocumentsHeadersFootersAndCommentsFollowItsBody() throws IOException {
    File file = directory.resolve("reviewed.docx").toFile();
    try (XWPFDocument document = new XWPFDocument(); FileOutputStream out = new FileOutputStream(file)) {
      var heading = document.createParagraph();
      heading.createRun().setText("Chapter");
      heading.getCTP().addNewPPr().addNewOutlineLvl().setVal(BigInteger.ZERO);
      document.createParagraph().createRun().setText("Body text");
      document.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Confidential");
      document.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.FIRST).createParagraph().createRun().setText("Confidential");
      document.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Page footer");
      var comment = document.createComments().createComment(BigInteger.ONE);
      comment.setAuthor("Reviewer");
      comment.createParagraph().createRun().setText("Check this figure");
      document.write(out);
    }
    StringWriter text = new StringWriter();

    List<DocumentOutlineEntry> outline = parse(file, DocumentTextStructureParser.DOCX_MIME_TYPE, text, Long.MAX_VALUE);

    assertEquals("Chapter\nBody text\nHeader: Confidential\nFooter: Page footer\nComment by Reviewer: Check this figure\n",
                 text.toString());
    assertEquals(0, outline.get(0).offset());
  }

  /**
   * A spreadsheet's sheets are its outline, each at the start of its rows with
   * its number, each row on one line, its cells' values separated.
   * Mutants: a sheet's entry dropped, the offset taken after its rows, the
   * sheet number dropped, a row's cells not separated.
   *
   * @throws IOException never
   */
  @Test
  void aSpreadsheetsSheetsAreItsOutline() throws IOException {
    File file = directory.resolve("budget.xlsx").toFile();
    try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
         FileOutputStream out = new FileOutputStream(file)) {
      var costs = workbook.createSheet("Costs");
      var header = costs.createRow(0);
      header.createCell(0).setCellValue("Item");
      header.createCell(1).setCellValue("Amount");
      var line = costs.createRow(1);
      line.createCell(0).setCellValue("Servers");
      line.createCell(1).setCellValue(1200);
      workbook.createSheet("Notes").createRow(0).createCell(0).setCellValue("Reviewed in March");
      workbook.write(out);
    }
    StringWriter text = new StringWriter();

    List<DocumentOutlineEntry> outline = parse(file, DocumentTextStructureParser.XLSX_MIME_TYPE, text, Long.MAX_VALUE);

    String written = text.toString();
    assertEquals(List.of("Costs", "Notes"), outline.stream().map(DocumentOutlineEntry::title).toList());
    assertEquals(List.of(1, 2), outline.stream().map(DocumentOutlineEntry::page).toList());
    assertHeadingsAtTheirOffsets(written, outline);
    assertTrue(written.contains("Item | Amount\nServers | 1200\n"), written);
    assertTrue(written.substring((int) outline.get(1).offset()).startsWith("Sheet 2: Notes\nReviewed in March"), written);
  }

  /**
   * A spreadsheet's row without values, which writes nothing, still stops
   * the reading once the deadline is passed, the writer's refusal carried as
   * itself.
   * Mutants: the deadline checked only when a row is written.
   */
  @Test
  void aSpreadsheetsBlankRowsStopAtTheDeadline() {
    CountingWriter late = new CountingWriter(new StringWriter(), Long.MAX_VALUE, System.nanoTime() - 1);
    SpreadsheetRowWriter rows = new SpreadsheetRowWriter(late);
    rows.startRow(0);

    java.io.UncheckedIOException e = assertThrows(java.io.UncheckedIOException.class, () -> rows.endRow(0));
    assertTrue(e.getCause() instanceof java.io.InterruptedIOException);
  }

  /**
   * A spreadsheet's text cut at the most characters read stops as a cut, the
   * outline holding the sheets written: the writer's refusal reaches the
   * caller as itself through the streaming handler.
   * Mutants: the refusal raised as an unreadable file.
   *
   * @throws IOException never
   */
  @Test
  void aCutSpreadsheetKeepsTheSheetsWrittenBeforeTheCut() throws IOException {
    File file = directory.resolve("large.xlsx").toFile();
    try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
         FileOutputStream out = new FileOutputStream(file)) {
      var first = workbook.createSheet("First");
      for (int i = 0; i < 50; i++) {
        first.createRow(i).createCell(0).setCellValue("Row number " + i);
      }
      workbook.createSheet("Second").createRow(0).createCell(0).setCellValue("Never reached");
      workbook.write(out);
    }
    List<DocumentOutlineEntry> outline = new ArrayList<>();
    CountingWriter writer = new CountingWriter(new StringWriter(), 100, Long.MAX_VALUE);

    assertThrows(TextLimitReachedException.class,
                 () -> DocumentTextStructureParser.parse(file, DocumentTextStructureParser.XLSX_MIME_TYPE, writer, outline));
    assertEquals(List.of("First"), outline.stream().map(DocumentOutlineEntry::title).toList());
    assertTrue(writer.isTruncated());
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
   * A PDF whose last bookmark points back to an earlier sibling, and whose
   * nested bookmark points back to its parent, is read: each bookmark once,
   * the walk ending where the list loops. PDFBox's own iteration of such a
   * list never ends.
   * Mutants: the children iterated by PDFBox, the visited bookmarks not kept.
   *
   * @throws IOException never
   */
  @Test
  @Timeout(30)
  void aPdfsLoopingBookmarksAreEachReadOnce() throws IOException {
    File file = directory.resolve("looping.pdf").toFile();
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      PDDocumentOutline bookmarks = new PDDocumentOutline();
      document.getDocumentCatalog().setDocumentOutline(bookmarks);
      PDOutlineItem first = bookmark("A", page);
      PDOutlineItem second = bookmark("B", page);
      PDOutlineItem third = bookmark("C", page);
      bookmarks.addLast(first);
      bookmarks.addLast(second);
      bookmarks.addLast(third);
      third.getCOSObject().setItem(COSName.NEXT, second);
      PDOutlineItem nested = bookmark("A.1", page);
      first.addLast(nested);
      nested.getCOSObject().setItem(COSName.NEXT, first);
      document.save(file);
    }

    List<DocumentOutlineEntry> outline = parse(file, DocumentTextStructureParser.PDF_MIME_TYPE, new StringWriter(), Long.MAX_VALUE);

    assertEquals(List.of("A", "A.1", "B", "C"), outline.stream().map(DocumentOutlineEntry::title).toList());
  }

  /**
   * A PDF read in one pass places a bookmark at its page's start even past a
   * page without content, which PDFBox skips, and a bookmark to that blank
   * page at the start of the next text, a blank last page at the end of the
   * text.
   * Mutants: the skipped pages not noted, shifting every later page; the
   * blank last pages not noted, their bookmarks dropped.
   *
   * @throws IOException never
   */
  @Test
  void aPdfsBookmarksKeepTheirPagesPastABlankPage() throws IOException {
    File file = directory.resolve("blank-page.pdf").toFile();
    try (PDDocument document = new PDDocument()) {
      List<PDPage> pages = new ArrayList<>();
      for (String line : java.util.Arrays.asList("Page one", null, "Page three", null)) {
        PDPage page = new PDPage();
        document.addPage(page);
        pages.add(page);
        if (line != null) {
          try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA, 12);
            stream.newLineAtOffset(72, 700);
            stream.showText(line);
            stream.endText();
          }
        }
      }
      PDDocumentOutline bookmarks = new PDDocumentOutline();
      document.getDocumentCatalog().setDocumentOutline(bookmarks);
      bookmarks.addLast(bookmark("Blank", pages.get(1)));
      bookmarks.addLast(bookmark("Third", pages.get(2)));
      bookmarks.addLast(bookmark("Last", pages.get(3)));
      document.save(file);
    }
    StringWriter text = new StringWriter();

    List<DocumentOutlineEntry> outline = parse(file, DocumentTextStructureParser.PDF_MIME_TYPE, text, Long.MAX_VALUE);

    String written = text.toString();
    assertEquals(List.of(2, 3, 4), outline.stream().map(DocumentOutlineEntry::page).toList());
    assertTrue(written.substring((int) outline.get(1).offset()).startsWith("Page three"));
    assertEquals(outline.get(1).offset(), outline.get(0).offset());
    assertEquals(written.length(), outline.get(2).offset());
  }

  /**
   * A PDF bookmark whose destination PDFBox can't read, a bare number or a
   * page past the last one, is left out: the text and the other bookmarks
   * are kept.
   * Mutants: a bookmark's failure ending the whole reading.
   *
   * @throws IOException never
   */
  @Test
  void aPdfsBrokenBookmarksAreLeftOut() throws IOException {
    File file = directory.resolve("broken-bookmarks.pdf").toFile();
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(PDType1Font.HELVETICA, 12);
        stream.newLineAtOffset(72, 700);
        stream.showText("Hello body text");
        stream.endText();
      }
      PDDocumentOutline bookmarks = new PDDocumentOutline();
      document.getDocumentCatalog().setDocumentOutline(bookmarks);
      bookmarks.addLast(bookmark("Good", page));
      PDOutlineItem number = new PDOutlineItem();
      number.setTitle("Bare number");
      number.getCOSObject().setInt(COSName.DEST, 7);
      bookmarks.addLast(number);
      PDOutlineItem pastTheEnd = new PDOutlineItem();
      pastTheEnd.setTitle("Past the end");
      org.apache.pdfbox.cos.COSArray destination = new org.apache.pdfbox.cos.COSArray();
      destination.add(org.apache.pdfbox.cos.COSInteger.get(5));
      destination.add(COSName.getPDFName("Fit"));
      pastTheEnd.getCOSObject().setItem(COSName.DEST, destination);
      bookmarks.addLast(pastTheEnd);
      document.save(file);
    }
    StringWriter text = new StringWriter();

    List<DocumentOutlineEntry> outline = parse(file, DocumentTextStructureParser.PDF_MIME_TYPE, text, Long.MAX_VALUE);

    assertTrue(text.toString().contains("Hello body text"));
    assertEquals(List.of("Good"), outline.stream().map(DocumentOutlineEntry::title).toList());
  }

  /**
   * A PDF's outline is cut at the most bookmarks read from one file.
   * Mutants: the cap removed, off by one.
   *
   * @throws IOException never
   */
  @Test
  void aPdfsOutlineIsCutAtTheMostBookmarks() throws IOException {
    File file = directory.resolve("many-bookmarks.pdf").toFile();
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      PDDocumentOutline bookmarks = new PDDocumentOutline();
      document.getDocumentCatalog().setDocumentOutline(bookmarks);
      for (int i = 0; i < DocumentTextStructureParser.MAX_BOOKMARKS + 5; i++) {
        bookmarks.addLast(bookmark("Bookmark " + i, page));
      }
      document.save(file);
    }

    List<DocumentOutlineEntry> outline = parse(file, DocumentTextStructureParser.PDF_MIME_TYPE, new StringWriter(), Long.MAX_VALUE);

    assertEquals(DocumentTextStructureParser.MAX_BOOKMARKS, outline.size());
  }

  /**
   * The walk of a PDF's bookmarks stops at the deadline, even when the file
   * writes no text that would check it.
   * Mutants: the deadline not checked in the walk.
   *
   * @throws IOException never
   */
  @Test
  void aPdfsBookmarksStopBeingReadAtTheDeadline() throws IOException {
    File file = directory.resolve("no-pages.pdf").toFile();
    try (PDDocument document = new PDDocument()) {
      PDDocumentOutline bookmarks = new PDDocumentOutline();
      document.getDocumentCatalog().setDocumentOutline(bookmarks);
      PDOutlineItem item = new PDOutlineItem();
      item.setTitle("Nowhere");
      bookmarks.addLast(item);
      document.save(file);
    }
    CountingWriter late = new CountingWriter(new StringWriter(), Long.MAX_VALUE, System.nanoTime() - 1);
    List<DocumentOutlineEntry> outline = new ArrayList<>();

    assertThrows(java.io.InterruptedIOException.class,
                 () -> DocumentTextStructureParser.parse(file, DocumentTextStructureParser.PDF_MIME_TYPE, late, outline));
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
   * The structured types are the Word document, the presentation, the PDF
   * and the xlsx spreadsheet, whatever their parameters; any other type, an
   * OpenDocument spreadsheet for instance, is read by the platform's readers.
   * Mutants: a parameter making a type unknown, another type taken.
   */
  @Test
  void onlyWordPresentationPdfAndXlsxAreStructured() {
    assertTrue(DocumentTextStructureParser.isStructured("application/pdf; charset=binary"));
    assertTrue(DocumentTextStructureParser.isStructured(DocumentTextStructureParser.DOCX_MIME_TYPE.toUpperCase()));
    assertTrue(DocumentTextStructureParser.isStructured(DocumentTextStructureParser.PPTX_MIME_TYPE));
    assertTrue(DocumentTextStructureParser.isStructured(DocumentTextStructureParser.XLSX_MIME_TYPE));
    assertFalse(DocumentTextStructureParser.isStructured("application/vnd.oasis.opendocument.spreadsheet"));
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
