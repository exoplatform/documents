/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.storage.jcr.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.exoplatform.documents.model.DocumentTextContent;
import org.exoplatform.documents.model.DocumentTextContent.Status;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.document.AdvancedDocumentReader;
import org.exoplatform.services.document.DocumentReadException;
import org.exoplatform.services.document.DocumentReader;
import org.exoplatform.services.document.DocumentReaderService;
import org.exoplatform.services.document.HandlerNotFoundException;
import org.exoplatform.services.jcr.core.ExtendedSession;

/**
 * The on-the-fly text extraction of a document file (EXO-90419): the text, and
 * every reason there can be none, within its size, length and time limits.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentTextExtractorTest {

  private static final String   DOCUMENT_ID = "b9098fb4c0a800f82218fdba71def5ed";

  private static final String   MIME_TYPE   = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  private static final long     MAX_SIZE    = 1024;

  private static final int      MAX_CHARS   = 10;

  @Mock
  private DocumentReaderService documentReaderService;

  @Mock
  private ExtendedSession       session;

  @Mock
  private Node                  node;

  @Mock
  private Node                  content;

  @Mock
  private Property              data;

  private DocumentTextExtractor extractor;

  /**
   * Builds an extractor on a mocked session holding one file of 100 bytes.
   *
   * @throws Exception never, the mocks do not throw
   */
  @BeforeEach
  void setUp() throws Exception {
    extractor = newExtractor(500);
    when(session.getNodeByIdentifier(DOCUMENT_ID)).thenReturn(node);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(node.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(content);
    when(content.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(true);
    when(content.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(true);
    when(content.getProperty(NodeTypeConstants.JCR_DATA)).thenReturn(data);
    Property mimeType = mock(Property.class);
    when(mimeType.getString()).thenReturn(MIME_TYPE);
    when(content.getProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(mimeType);
    when(data.getLength()).thenReturn(100L);
    when(data.getStream()).thenReturn(new ByteArrayInputStream(new byte[100]));
  }

  /**
   * Stops the extractor's threads.
   */
  @AfterEach
  void tearDown() {
    extractor.shutdown();
  }

  @Test
  void extractsTheTextOfTheFile() throws Exception {
    readerReturning("Hello");

    DocumentTextContent text = extractor.extract(DOCUMENT_ID);

    assertEquals(Status.EXTRACTED, text.status());
    assertEquals("Hello", text.text());
    verify(session).logout();
  }

  @Test
  void cutsTheTextAtTheMaximumLength() throws Exception {
    readerReturning("0123456789 and much more text");

    assertEquals("0123456789", extractor.extract(DOCUMENT_ID).text());
  }

  @Test
  void cutsTheTextOfANonStreamingReaderToo() throws Exception {
    DocumentReader reader = mock(DocumentReader.class);
    when(reader.getContentAsText(any())).thenReturn("0123456789 and much more text");
    when(documentReaderService.getDocumentReader(MIME_TYPE)).thenReturn(reader);

    assertEquals("0123456789", extractor.extract(DOCUMENT_ID).text());
  }

  @Test
  void doesNotReadAFileLargerThanTheLimit() throws Exception {
    when(data.getLength()).thenReturn(MAX_SIZE + 1);

    DocumentTextContent text = extractor.extract(DOCUMENT_ID);

    assertEquals(Status.TOO_LARGE, text.status());
    assertNull(text.text());
    verify(documentReaderService, never()).getDocumentReader(any());
    verify(data, never()).getStream();
  }

  @Test
  void reportsAFormatNoExtractorHandles() throws Exception {
    when(documentReaderService.getDocumentReader(MIME_TYPE)).thenThrow(new HandlerNotFoundException("no reader"));

    assertEquals(Status.UNSUPPORTED_FORMAT, extractor.extract(DOCUMENT_ID).status());
  }

  @Test
  void reportsAFileWithoutText() throws Exception {
    readerReturning("  \n ");

    DocumentTextContent text = extractor.extract(DOCUMENT_ID);

    assertEquals(Status.NO_TEXT, text.status());
    assertNull(text.text());
  }

  @Test
  void reportsADocumentThatIsNotAFile() throws Exception {
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(false);

    assertEquals(Status.NOT_A_FILE, extractor.extract(DOCUMENT_ID).status());
  }

  @Test
  void reportsAMissingDocumentAsNotAFile() throws Exception {
    when(session.getNodeByIdentifier(DOCUMENT_ID)).thenReturn(null);

    assertEquals(Status.NOT_A_FILE, extractor.extract(DOCUMENT_ID).status());
  }

  @Test
  void reportsAFileThatCannotBeParsed() throws Exception {
    AdvancedDocumentReader reader = mock(AdvancedDocumentReader.class);
    when(reader.getContentAsReader(any())).thenThrow(new DocumentReadException("corrupted"));
    when(documentReaderService.getDocumentReader(MIME_TYPE)).thenReturn(reader);

    assertEquals(Status.UNREADABLE, extractor.extract(DOCUMENT_ID).status());
    verify(session).logout();
  }

  @Test
  void abandonsAnExtractionTakingLongerThanTheTimeout() throws Exception {
    extractor.shutdown();
    extractor = newExtractor(100);
    CountDownLatch neverReleased = new CountDownLatch(1);
    AdvancedDocumentReader reader = mock(AdvancedDocumentReader.class);
    when(reader.getContentAsReader(any())).thenAnswer(invocation -> {
      neverReleased.await(10, TimeUnit.SECONDS);
      return new StringReader("too late");
    });
    when(documentReaderService.getDocumentReader(MIME_TYPE)).thenReturn(reader);

    long start = System.nanoTime();
    DocumentTextContent text = extractor.extract(DOCUMENT_ID);
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertEquals(Status.UNREADABLE, text.status());
    assertTrue(elapsedMillis < 5000, "the extraction should be abandoned at the timeout, took " + elapsedMillis + " ms");
  }

  /**
   * Makes the file's format handled by a streaming reader returning that text.
   *
   * @param text the text of the file
   * @throws Exception never, the mocks do not throw
   */
  private void readerReturning(String text) throws Exception {
    AdvancedDocumentReader reader = mock(AdvancedDocumentReader.class);
    Reader textReader = new StringReader(text);
    when(reader.getContentAsReader(any())).thenReturn(textReader);
    when(documentReaderService.getDocumentReader(MIME_TYPE)).thenReturn(reader);
  }

  /**
   * An extractor reading from the mocked session.
   *
   * @param timeoutMillis the extraction timeout
   * @return the extractor
   */
  private DocumentTextExtractor newExtractor(long timeoutMillis) {
    return new DocumentTextExtractor(null, documentReaderService, MAX_SIZE, MAX_CHARS, timeoutMillis) {
      @Override
      protected Session openSystemSession() {
        return session;
      }
    };
  }
}
