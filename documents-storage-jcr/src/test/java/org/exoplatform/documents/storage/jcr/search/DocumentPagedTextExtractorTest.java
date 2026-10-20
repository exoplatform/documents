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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Session;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.exoplatform.documents.model.DocumentTextContent.Status;
import org.exoplatform.documents.model.DocumentTextPage;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.document.AdvancedDocumentReader;
import org.exoplatform.services.document.DocumentReaderService;
import org.exoplatform.services.jcr.core.ExtendedSession;

/**
 * Reads documents part by part from a mocked repository: one extraction per
 * version of a file, the parts at their offsets, the limits, the wait, the
 * cache's size.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentPagedTextExtractorTest {

  private static final String   DOCUMENT_ID = "b9098fb4c0a800f82218fdba71def5ed";

  private static final String   OTHER_ID    = "c9098fb4c0a800f82218fdba71def5ee";

  private static final long     MAX_SIZE    = 1024 * 1024;

  @TempDir
  Path                          directory;

  @Mock
  private DocumentReaderService documentReaderService;

  @Mock
  private ExtendedSession       session;

  private final AtomicInteger   extractions = new AtomicInteger();

  private DocumentTextExtractor formats;

  private DocumentPagedTextExtractor extractor;

  /**
   * Builds an extractor whose cache holds a megabyte, reading from the mocked
   * session.
   *
   * @throws Exception never, the mocks do not throw
   */
  @BeforeEach
  void setUp() throws Exception {
    formats = new DocumentTextExtractor(null, documentReaderService, MAX_SIZE, 1000, 1000);
    extractor = newExtractor(100, 2000, 1024 * 1024);
  }

  /**
   * Stops the extractors' threads.
   */
  @AfterEach
  void tearDown() {
    extractor.shutdown();
    formats.shutdown();
  }

  /**
   * An extractor reading from the mocked session, counting its extractions.
   *
   * @param maxChars the most characters kept
   * @param waitMillis how long a caller waits
   * @param maxCacheBytes the cache's size
   * @return the extractor
   * @throws IOException never
   */
  private DocumentPagedTextExtractor newExtractor(long maxChars, long waitMillis, long maxCacheBytes) throws IOException {
    return new DocumentPagedTextExtractor(null,
                                          documentReaderService,
                                          formats,
                                          MAX_SIZE,
                                          maxChars,
                                          5000,
                                          waitMillis,
                                          maxCacheBytes,
                                          directory.resolve("cache")) {
      @Override
      protected Session openSystemSession() {
        return session;
      }

      @Override
      protected Extraction extract(String documentId, String key) throws Exception {
        extractions.incrementAndGet();
        return super.extract(documentId, key);
      }
    };
  }

  /**
   * Stores a file in the mocked repository.
   *
   * @param id its id
   * @param name its name
   * @param mimeType its type
   * @param bytes its content
   * @param modified the date its content last changed
   * @return its data property
   * @throws Exception never, the mocks do not throw
   */
  private Property storeFile(String id, String name, String mimeType, byte[] bytes, long modified) throws Exception {
    Node node = mock(Node.class);
    Node content = mock(Node.class);
    Property data = mock(Property.class);
    when(session.getNodeByIdentifier(id)).thenReturn(node);
    when(node.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
    when(node.getName()).thenReturn(name);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(node.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(content);
    when(content.getParent()).thenReturn(node);
    when(content.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(true);
    when(content.getProperty(NodeTypeConstants.JCR_DATA)).thenReturn(data);
    when(content.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(true);
    Property mime = mock(Property.class);
    when(mime.getString()).thenReturn(mimeType);
    when(content.getProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(mime);
    when(content.hasProperty(NodeTypeConstants.JCR_LAST_MODIFIED)).thenReturn(true);
    Property lastModified = mock(Property.class);
    Calendar date = Calendar.getInstance();
    date.setTimeInMillis(modified);
    when(lastModified.getDate()).thenReturn(date);
    when(content.getProperty(NodeTypeConstants.JCR_LAST_MODIFIED)).thenReturn(lastModified);
    when(data.getLength()).thenReturn((long) bytes.length);
    when(data.getStream()).thenAnswer(invocation -> new ByteArrayInputStream(bytes));
    return data;
  }

  /**
   * Stores a plain text file read by a streaming platform reader.
   *
   * @param id its id
   * @param text its text
   * @param modified the date its content last changed
   * @return its data property
   * @throws Exception never, the mocks do not throw
   */
  private Property storeText(String id, String text, long modified) throws Exception {
    AdvancedDocumentReader reader = mock(AdvancedDocumentReader.class);
    when(reader.getContentAsReader(any())).thenAnswer(invocation -> new StringReader(text));
    when(documentReaderService.getDocumentReader("text/plain")).thenReturn(reader);
    return storeFile(id, "notes.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8), modified);
  }

  /**
   * The parts of a document are read at their offsets from one extraction,
   * contiguous, with the whole text's length; a part past the end is empty.
   * Mutants: the offset ignored, a part re-extracting the file, the length
   * of the part taken for the whole text's.
   *
   * @throws Exception never
   */
  @Test
  void readsThePartsOfADocumentFromOneExtraction() throws Exception {
    storeText(DOCUMENT_ID, "0123456789abcdefghij", 1L);

    DocumentTextPage first = extractor.getPage(DOCUMENT_ID, 0, 8);
    DocumentTextPage second = extractor.getPage(DOCUMENT_ID, 8, 8);
    DocumentTextPage last = extractor.getPage(DOCUMENT_ID, 16, 8);
    DocumentTextPage past = extractor.getPage(DOCUMENT_ID, 50, 8);

    assertEquals("01234567", first.text());
    assertEquals("89abcdef", second.text());
    assertEquals("ghij", last.text());
    assertEquals("", past.text());
    assertEquals(20, first.totalChars());
    assertEquals(Status.EXTRACTED, past.status());
    assertFalse(first.truncated());
    assertEquals(1, extractions.get());
  }

  /**
   * A new version of the file, its content changed, is extracted again; the
   * same version is not.
   * Mutants: the version left out of the cache key.
   *
   * @throws Exception never
   */
  @Test
  void extractsANewVersionAgain() throws Exception {
    storeText(DOCUMENT_ID, "first version", 1L);
    assertEquals("first", extractor.getPage(DOCUMENT_ID, 0, 5).text());

    storeText(DOCUMENT_ID, "second version", 2L);

    assertEquals("second", extractor.getPage(DOCUMENT_ID, 0, 6).text());
    assertEquals(2, extractions.get());
  }

  /**
   * A text longer than the most characters kept is cut there, and says so.
   * Mutants: the cut not reported, the text kept whole.
   *
   * @throws Exception never
   */
  @Test
  void cutsATextAtTheMostCharactersKept() throws Exception {
    storeText(DOCUMENT_ID, "x".repeat(250), 1L);

    DocumentTextPage page = extractor.getPage(DOCUMENT_ID, 0, 1000);

    assertEquals(100, page.totalChars());
    assertTrue(page.truncated());
  }

  /**
   * A file over the largest size is refused without being read, a type no
   * reader handles is refused, and a document that isn't a file says so.
   * Mutants: the size check dropped or after the copy, the type check
   * dropped.
   *
   * @throws Exception never
   */
  @Test
  void refusesWhatItDoesNotRead() throws Exception {
    Property large = storeFile(DOCUMENT_ID, "huge.bin", "text/plain", new byte[10], 1L);
    when(large.getLength()).thenReturn(MAX_SIZE + 1);
    assertEquals(Status.TOO_LARGE, extractor.getPage(DOCUMENT_ID, 0, 10).status());
    verify(large, never()).getStream();

    storeFile(OTHER_ID, "photo.png", "image/png", new byte[10], 1L);
    assertEquals(Status.UNSUPPORTED_FORMAT, extractor.getPage(OTHER_ID, 0, 10).status());

    when(session.getNodeByIdentifier("folder")).thenReturn(mock(Node.class));
    assertEquals(Status.NOT_A_FILE, extractor.getPage("folder", 0, 10).status());
  }

  /**
   * A Word document is read with its outline, the outline coming with every
   * part.
   * Mutants: the structured parser bypassed, the outline dropped from a part.
   *
   * @throws Exception never
   */
  @Test
  void readsAWordDocumentWithItsOutline() throws Exception {
    ByteArrayOutputStream docx = new ByteArrayOutputStream();
    try (XWPFDocument document = new XWPFDocument()) {
      var heading = document.createParagraph();
      heading.createRun().setText("Chapter");
      heading.getCTP().addNewPPr().addNewOutlineLvl().setVal(java.math.BigInteger.ZERO);
      document.createParagraph().createRun().setText("Body of the chapter.");
      document.write(docx);
    }
    storeFile(DOCUMENT_ID, "book.docx", DocumentTextStructureParser.DOCX_MIME_TYPE, docx.toByteArray(), 1L);

    DocumentTextPage page = extractor.getPage(DOCUMENT_ID, 8, 4);

    assertEquals("Body", page.text());
    assertEquals(1, page.outline().size());
    assertEquals("Chapter", page.outline().get(0).title());
    assertEquals(0, page.outline().get(0).offset());
  }

  /**
   * A caller waiting past the wait is told the document is being read, the
   * extraction going on; the next call, once it is over, gets the text from
   * that same extraction.
   * Mutants: the extraction cancelled on the wait's end, a second
   * extraction started for the same version.
   *
   * @throws Exception never
   */
  @Test
  void tellsTheDocumentIsBeingReadAndFindsItLater() throws Exception {
    extractor.shutdown();
    extractor = newExtractor(100, 100, 1024 * 1024);
    CountDownLatch release = new CountDownLatch(1);
    AdvancedDocumentReader slow = mock(AdvancedDocumentReader.class);
    when(slow.getContentAsReader(any())).thenAnswer(invocation -> {
      release.await(5, TimeUnit.SECONDS);
      return new StringReader("finally");
    });
    storeFile(DOCUMENT_ID, "slow.txt", "text/plain", new byte[3], 1L);
    when(documentReaderService.getDocumentReader("text/plain")).thenReturn(slow);

    assertEquals(Status.BUSY, extractor.getPage(DOCUMENT_ID, 0, 10).status());
    assertEquals(Status.BUSY, extractor.getPage(DOCUMENT_ID, 0, 10).status());
    release.countDown();
    Thread.sleep(300);

    assertEquals("finally", extractor.getPage(DOCUMENT_ID, 0, 10).text());
    assertEquals(1, extractions.get());
  }

  /**
   * Past the cache's size the least recently read text is evicted, its file
   * deleted, and extracted again when read anew; the text just extracted
   * stays.
   * Mutants: nothing evicted, the newest evicted, the file left on disk.
   *
   * @throws Exception never
   */
  @Test
  void evictsTheLeastRecentlyReadTextPastTheCachesSize() throws Exception {
    extractor.shutdown();
    // two characters a byte: room for one 60-character text, not two
    extractor = newExtractor(100, 2000, 150);
    storeText(DOCUMENT_ID, "a".repeat(60), 1L);
    extractor.getPage(DOCUMENT_ID, 0, 1);
    long filesBefore;
    try (var files = Files.list(directory.resolve("cache"))) {
      filesBefore = files.count();
    }
    storeText(OTHER_ID, "b".repeat(60), 1L);
    assertEquals("b", extractor.getPage(OTHER_ID, 0, 1).text());

    try (var files = Files.list(directory.resolve("cache"))) {
      assertEquals(filesBefore, files.count());
    }
    assertEquals(2, extractions.get());
    extractor.getPage(DOCUMENT_ID, 0, 1);
    assertEquals(3, extractions.get());
  }

  /**
   * A part ending inside a surrogate pair leaves the pair out whole, so no
   * half character reaches the caller.
   * Mutants: the half pair kept.
   *
   * @throws Exception never
   */
  @Test
  void neverCutsACharacterInHalf() throws Exception {
    Path file = directory.resolve("emoji.txt");
    Files.write(file, "ab\uD83D\uDE00c".getBytes(StandardCharsets.UTF_16BE));

    assertEquals("ab", DocumentPagedTextExtractor.readChars(file, 0, 3));
    assertEquals("ab\uD83D\uDE00", DocumentPagedTextExtractor.readChars(file, 0, 4));
    assertEquals("c", DocumentPagedTextExtractor.readChars(file, 3, 2));
    assertNull(DocumentTextPage.none(Status.BUSY).text());
  }

}
