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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.utils.MimeTypeResolver;
import org.exoplatform.commons.utils.PropertyManager;
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
 * Bounded on every side, since it runs on a user's request: files of
 * {@code maxFileSizeBytes} or larger are not read (the same 10 MB as the search index's own
 * content extraction), only the file types the search index extracts the text of
 * are read, the text is cut at {@code maxChars}, the caller stops waiting after
 * {@code timeoutMillis}, and at most {@link #MAX_CONCURRENT_EXTRACTIONS} run at a
 * time with a short queue, anything beyond being refused at once rather than
 * piling up.
 * <p>
 * What bounds memory is the file size times {@link #MAX_CONCURRENT_EXTRACTIONS}:
 * the readers configured for PDF and office files are not streaming ones and
 * build the whole text before it is cut, and they cannot be interrupted, so an
 * extraction the caller stopped waiting for keeps its slot until it ends.
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

  /** The property listing, as regular expressions, the file types whose text the search index extracts. */
  public static final String  SUPPORTED_MIME_TYPES_PROPERTY = "exo.unified-search.indexing.supportedMimeTypes";

  /** The search index's own default for {@link #SUPPORTED_MIME_TYPES_PROPERTY}, ecms core-search-configuration.xml. */
  public static final String  DEFAULT_SUPPORTED_MIME_TYPES  =
                                                           "text/.*, application/ms.* , application/vnd.* , application/xml , "
                                                               + "application/excel , application/powerpoint , application/xls, "
                                                               + "application/ppt , application/pdf , application/xhtml+xml , "
                                                               + "application/javascript , application/x-javascript , "
                                                               + "application/x-jaxrs+groovy , script/groovy";

  private static final String GENERIC_MIME_TYPE          = "application/octet-stream";

  private static final MimeTypeResolver MIME_TYPE_RESOLVER = new MimeTypeResolver();

  private final RepositoryService     repositoryService;

  private final DocumentReaderService documentReaderService;

  private final long                  maxFileSizeBytes;

  private final int                   maxChars;

  private final long                  timeoutMillis;

  private final ExecutorService       executor;

  private final List<String>          supportedMimeTypes;

  /**
   * Creates an extractor with its own bounded pool of extraction threads.
   *
   * @param repositoryService the JCR repository holding the files
   * @param documentReaderService the platform's text extractor
   * @param maxFileSizeBytes files of this size or larger are not read
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
    this.supportedMimeTypes = Arrays.stream(StringUtils.split(StringUtils.defaultIfBlank(PropertyManager.getProperty(SUPPORTED_MIME_TYPES_PROPERTY),
                                                                                         DEFAULT_SUPPORTED_MIME_TYPES),
                                                               ','))
                                    .map(String::trim)
                                    .filter(StringUtils::isNotBlank)
                                    .toList();
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
    AtomicBoolean started = new AtomicBoolean();
    Future<DocumentTextContent> extraction;
    try {
      extraction = executor.submit(() -> {
        started.set(true);
        return readText(documentId);
      });
    } catch (RejectedExecutionException e) {
      LOG.warn("Too many text extractions in progress, the text of document {} is not extracted", documentId);
      return DocumentTextContent.none(Status.BUSY);
    }
    try {
      return extraction.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      // cancelled either way: a queued extraction must not run once nobody waits for it
      extraction.cancel(true);
      if (!started.get()) {
        // still queued behind extractions holding every slot: the extractor was
        // busy, this file was never even opened
        LOG.warn("No free slot to extract the text of document {} within {} ms", documentId, timeoutMillis);
        return DocumentTextContent.none(Status.BUSY);
      }
      LOG.warn("Extracting the text of document {} took more than {} ms, abandoned", documentId, timeoutMillis);
      return DocumentTextContent.none(Status.TIMED_OUT);
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
      if (node == null || !node.isNodeType(NodeTypeConstants.NT_FILE) || !node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
        return DocumentTextContent.none(Status.NOT_A_FILE);
      }
      Node content = node.getNode(NodeTypeConstants.JCR_CONTENT);
      if (!content.hasProperty(NodeTypeConstants.JCR_DATA)) {
        return DocumentTextContent.none(Status.NOT_A_FILE);
      }
      Property data = content.getProperty(NodeTypeConstants.JCR_DATA);
      // the search index extracts a file's content only when it is smaller than its limit
      if (data.getLength() >= maxFileSizeBytes) {
        return DocumentTextContent.none(Status.TOO_LARGE);
      }
      String mimeType = resolveMimeType(node, content);
      if (!isSupported(mimeType)) {
        return DocumentTextContent.none(Status.UNSUPPORTED_FORMAT);
      }
      DocumentReader reader;
      try {
        reader = documentReaderService.getDocumentReader(mimeType);
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
   * The type of a file: the stored one, unless it says nothing (absent, or the
   * generic binary type a mail part is often declared with), in which case it is
   * resolved from the file name the way the platform does for an upload.
   *
   * @param node the file node
   * @param content its content node
   * @return the type to extract the text with
   * @throws RepositoryException when the node cannot be read
   */
  private String resolveMimeType(Node node, Node content) throws RepositoryException {
    String mimeType = content.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)
        ? content.getProperty(NodeTypeConstants.JCR_MIME_TYPE).getString()
        : null;
    if (StringUtils.isBlank(mimeType) || GENERIC_MIME_TYPE.equalsIgnoreCase(mimeType.trim())) {
      mimeType = MIME_TYPE_RESOLVER.getMimeType(node.getName());
    }
    return StringUtils.trimToEmpty(mimeType);
  }

  /**
   * Whether text is extracted from files of this type: the very list the search
   * index extracts the content of (its {@code documents.content.indexing.mimetypes}
   * parameter, read from the same property with the same default), each entry a
   * regular expression the type must match. Two differences with the index, both
   * more permissive: the type is normalised first (parameters such as
   * {@code ;charset=} dropped, lower-cased) where the index matches the stored
   * type as it is, and a file stored as {@code application/octet-stream} gets its
   * type from its name (see resolveMimeType) where the index keeps the generic
   * type and so extracts nothing. The platform's
   * reader service cannot answer that itself: it hands back a reader for any type,
   * one that simply finds no text in a type no parser handles.
   *
   * @param mimeType the file type
   * @return true when its text is extracted
   */
  boolean isSupported(String mimeType) {
    if (StringUtils.isBlank(mimeType) || GENERIC_MIME_TYPE.equalsIgnoreCase(mimeType)) {
      return false;
    }
    String type = StringUtils.substringBefore(mimeType, ";").trim().toLowerCase();
    return supportedMimeTypes.stream().anyMatch(type::matches);
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
   * can stream, else cut once the reader has built the whole text.
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
