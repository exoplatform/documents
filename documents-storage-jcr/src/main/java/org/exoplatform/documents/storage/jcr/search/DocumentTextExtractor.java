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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.documents.model.DocumentTextContent;
import org.exoplatform.documents.model.DocumentTextContent.Status;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.document.AdvancedDocumentReader;
import org.exoplatform.services.document.DocumentReader;
import org.exoplatform.services.document.DocumentReaderService;
import org.exoplatform.services.document.HandlerNotFoundException;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Extracts the text of a document file straight from its JCR binary, for when
 * the search index does not hold it (yet): a file indexed a few seconds after it
 * was saved, or never indexed at all. It reuses the platform's
 * {@link DocumentReaderService}, the Tika-backed extractor the JCR full-text
 * indexing already relies on, rather than Tika directly.
 * <p>
 * Bounded on every side, since it runs on a user's request: files larger than
 * {@code maxFileSizeBytes} are not read (the same 10 MB default as the search
 * index's own content extraction), the text is cut at {@code maxChars}, an
 * extraction taking longer than {@code timeoutMillis} is abandoned, and at most
 * {@link #MAX_CONCURRENT_EXTRACTIONS} run at a time with a short queue, anything
 * beyond being refused at once rather than piling up.
 * <p>
 * The file is read with a system session: the caller checks the user can access
 * the document before asking for its text.
 */
public class DocumentTextExtractor {

  /** How many extractions run at the same time, at most. */
  public static final int     MAX_CONCURRENT_EXTRACTIONS = 2;

  /** How many extractions may wait for a free slot before new ones are refused. */
  public static final int     MAX_QUEUED_EXTRACTIONS     = 8;

  private static final Log    LOG                        = ExoLogger.getLogger(DocumentTextExtractor.class);

  private static final String COLLABORATION              = "collaboration";

  private final RepositoryService     repositoryService;

  private final DocumentReaderService documentReaderService;

  private final long                  maxFileSizeBytes;

  private final int                   maxChars;

  private final long                  timeoutMillis;

  private final ExecutorService       executor;

  /**
   * Creates an extractor with its own bounded pool of extraction threads.
   *
   * @param repositoryService the JCR repository holding the files
   * @param documentReaderService the platform's text extractor
   * @param maxFileSizeBytes files larger than this are not read
   * @param maxChars the extracted text is cut at this length
   * @param timeoutMillis an extraction taking longer is abandoned
   */
  public DocumentTextExtractor(RepositoryService repositoryService,
                               DocumentReaderService documentReaderService,
                               long maxFileSizeBytes,
                               int maxChars,
                               long timeoutMillis) {
    this.repositoryService = repositoryService;
    this.documentReaderService = documentReaderService;
    this.maxFileSizeBytes = maxFileSizeBytes;
    this.maxChars = maxChars;
    this.timeoutMillis = timeoutMillis;
    AtomicInteger threadIndex = new AtomicInteger();
    this.executor = new ThreadPoolExecutor(MAX_CONCURRENT_EXTRACTIONS,
                                           MAX_CONCURRENT_EXTRACTIONS,
                                           60,
                                           TimeUnit.SECONDS,
                                           new ArrayBlockingQueue<>(MAX_QUEUED_EXTRACTIONS),
                                           runnable -> {
                                             Thread thread = new Thread(runnable,
                                                                        "documents-text-extractor-"
                                                                            + threadIndex.incrementAndGet());
                                             thread.setDaemon(true);
                                             return thread;
                                           });
    ((ThreadPoolExecutor) this.executor).allowCoreThreadTimeOut(true);
  }

  /**
   * Extracts the text of a document file, within the time limit.
   *
   * @param documentId the JCR identifier of the document
   * @return the extracted text, or why there is none
   */
  public DocumentTextContent extract(String documentId) {
    Future<DocumentTextContent> extraction;
    try {
      extraction = executor.submit(() -> readText(documentId));
    } catch (RejectedExecutionException e) {
      LOG.warn("Too many text extractions in progress, the text of document {} is not extracted", documentId);
      return DocumentTextContent.none(Status.UNREADABLE);
    }
    try {
      return extraction.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      extraction.cancel(true);
      LOG.warn("Extracting the text of document {} took more than {} ms, abandoned", documentId, timeoutMillis);
      return DocumentTextContent.none(Status.UNREADABLE);
    } catch (InterruptedException e) {
      extraction.cancel(true);
      Thread.currentThread().interrupt();
      return DocumentTextContent.none(Status.UNREADABLE);
    } catch (ExecutionException e) {
      LOG.warn("Error extracting the text of document {}", documentId, e.getCause());
      return DocumentTextContent.none(Status.UNREADABLE);
    }
  }

  /**
   * Reads the text of a document file on the calling thread, with a system
   * session opened and closed here since a session must not cross threads.
   *
   * @param documentId the JCR identifier of the document
   * @return the extracted text, or why there is none
   * @throws Exception when the repository cannot be reached
   */
  protected DocumentTextContent readText(String documentId) throws Exception {
    Session session = openSystemSession();
    try {
      Node node = JCRDocumentsUtil.getNodeByIdentifier(session, documentId);
      if (node == null || !node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
        return DocumentTextContent.none(Status.NOT_A_FILE);
      }
      Node content = node.getNode(NodeTypeConstants.JCR_CONTENT);
      if (!content.hasProperty(NodeTypeConstants.JCR_DATA) || !content.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)) {
        return DocumentTextContent.none(Status.NOT_A_FILE);
      }
      Property data = content.getProperty(NodeTypeConstants.JCR_DATA);
      if (data.getLength() > maxFileSizeBytes) {
        return DocumentTextContent.none(Status.TOO_LARGE);
      }
      DocumentReader reader;
      try {
        reader = documentReaderService.getDocumentReader(content.getProperty(NodeTypeConstants.JCR_MIME_TYPE).getString());
      } catch (HandlerNotFoundException e) {
        return DocumentTextContent.none(Status.UNSUPPORTED_FORMAT);
      }
      String text;
      try (InputStream stream = data.getStream()) {
        text = readUpToMaxChars(reader, stream);
      }
      return StringUtils.isBlank(text) ? DocumentTextContent.none(Status.NO_TEXT)
                                       : DocumentTextContent.of(text, Status.EXTRACTED);
    } finally {
      session.logout();
    }
  }

  /**
   * Opens a system session on the workspace holding the documents.
   *
   * @return a new session, logged out by the caller
   * @throws RepositoryException when the repository cannot be reached
   */
  protected Session openSystemSession() throws RepositoryException {
    return SessionProvider.createSystemProvider().getSession(COLLABORATION, repositoryService.getCurrentRepository());
  }

  /**
   * Reads at most {@code maxChars} characters of text: streamed when the reader
   * can stream, so a file full of text never sits whole in memory, else cut
   * after the fact.
   *
   * @param reader the extractor for the file's format
   * @param stream the file's binary
   * @return the text, cut at {@code maxChars}
   * @throws Exception when the file cannot be parsed
   */
  private String readUpToMaxChars(DocumentReader reader, InputStream stream) throws Exception {
    if (reader instanceof AdvancedDocumentReader advancedReader) {
      try (Reader textReader = advancedReader.getContentAsReader(stream)) {
        return read(textReader);
      }
    } else {
      return StringUtils.truncate(reader.getContentAsText(stream), maxChars);
    }
  }

  /**
   * Reads a character stream up to {@code maxChars}.
   *
   * @param textReader the text of the file
   * @return what was read, at most {@code maxChars} characters
   * @throws IOException when the stream fails
   */
  private String read(Reader textReader) throws IOException {
    StringBuilder text = new StringBuilder();
    char[] buffer = new char[8192];
    int read;
    while (text.length() < maxChars
        && (read = textReader.read(buffer, 0, Math.min(buffer.length, maxChars - text.length()))) != -1) {
      if (Thread.currentThread().isInterrupted()) {
        throw new IOException("Text extraction interrupted");
      }
      text.append(buffer, 0, read);
    }
    return text.toString();
  }

  /**
   * Stops the extraction threads.
   */
  public void shutdown() {
    executor.shutdownNow();
  }
}
