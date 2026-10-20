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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
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

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.documents.model.DocumentOutlineEntry;
import org.exoplatform.documents.model.DocumentTextContent.Status;
import org.exoplatform.documents.model.DocumentTextPage;
import org.exoplatform.documents.storage.jcr.search.DocumentTextStructureParser.CountingWriter;
import org.exoplatform.documents.storage.jcr.search.DocumentTextStructureParser.TextLimitReachedException;
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
 * Reads a document's text part by part, the whole text being extracted once
 * per version of the file into a node-local cache file, so a document far
 * larger than what is extracted on the fly, a book full of pictures for
 * instance, can be read in parts, or one section at a time.
 * <p>
 * The file is copied to a temporary file and parsed from it, never held in
 * memory whole. A Word document, a presentation and a PDF get their outline
 * from the same walk that writes their text; any other supported type is read
 * by the platform's document readers, without an outline. One file is
 * extracted at a time; a caller waits for its extraction a bounded time, the
 * extraction going on after it so a later call finds the text. The text is
 * stored as UTF-16, two bytes a character, so a part is read at its offset
 * without reading what comes before it. The cache is a node's own: another
 * node of a cluster extracts the file again on its first part.
 */
public class DocumentPagedTextExtractor {

  /** How many extractions may wait for the one running. */
  static final int                   MAX_QUEUED_EXTRACTIONS = 4;

  private static final Log           LOG                    = ExoLogger.getLogger(DocumentPagedTextExtractor.class);

  private static final String        COLLABORATION          = "collaboration";

  private final RepositoryService    repositoryService;

  private final DocumentReaderService documentReaderService;

  private final DocumentTextExtractor formats;

  private final long                 maxFileSizeBytes;

  private final long                 maxChars;

  private final long                 timeoutMillis;

  private final long                 waitMillis;

  private final long                 maxCacheBytes;

  private final Path                 cacheDirectory;

  private final ExecutorService      executor;

  private final Map<String, Future<Extraction>> running = new ConcurrentHashMap<>();

  /** The extracted texts by file version, the least recently read first. */
  private final LinkedHashMap<String, Extraction> cache = new LinkedHashMap<>(16, 0.75f, true);

  private long                       cacheBytes;

  /**
   * The text of one version of a file, as extracted.
   *
   * @param file the cache file holding the text as UTF-16BE, null without text
   * @param totalChars the length of the text
   * @param truncated true when the text was cut at the most characters read
   * @param outline the file's headings
   * @param status {@link Status#EXTRACTED}, else why there is no text
   */
  record Extraction(Path file, long totalChars, boolean truncated, List<DocumentOutlineEntry> outline, Status status) {

    /**
     * Builds the extraction of a file that gave no text.
     *
     * @param status why
     * @return the extraction
     */
    static Extraction none(Status status) {
      return new Extraction(null, 0, false, List.of(), status);
    }
  }

  /**
   * Builds the extractor.
   *
   * @param repositoryService the repository holding the files
   * @param documentReaderService the platform's document readers
   * @param formats the on-the-fly extractor, whose supported types and type
   *          resolution are shared
   * @param maxFileSizeBytes the largest file read
   * @param maxChars the most characters kept from one file
   * @param timeoutMillis the longest one extraction runs
   * @param waitMillis the longest a caller waits for an extraction
   * @param maxCacheBytes the most bytes the cache files take
   * @param cacheDirectory the directory of the cache files, emptied here
   * @throws IOException when the directory can't be created or emptied
   */
  public DocumentPagedTextExtractor(RepositoryService repositoryService, // NOSONAR
                                    DocumentReaderService documentReaderService,
                                    DocumentTextExtractor formats,
                                    long maxFileSizeBytes,
                                    long maxChars,
                                    long timeoutMillis,
                                    long waitMillis,
                                    long maxCacheBytes,
                                    Path cacheDirectory) throws IOException {
    this.repositoryService = repositoryService;
    this.documentReaderService = documentReaderService;
    this.formats = formats;
    this.maxFileSizeBytes = maxFileSizeBytes;
    this.maxChars = maxChars;
    this.timeoutMillis = timeoutMillis;
    this.waitMillis = waitMillis;
    this.maxCacheBytes = maxCacheBytes;
    this.cacheDirectory = cacheDirectory;
    // the texts of a previous run are no longer indexed: their versions are unknown
    FileUtils.deleteDirectory(cacheDirectory.toFile());
    Files.createDirectories(cacheDirectory);
    AtomicInteger threadIndex = new AtomicInteger();
    this.executor = new ThreadPoolExecutor(1,
                                           1,
                                           60,
                                           TimeUnit.SECONDS,
                                           new ArrayBlockingQueue<>(MAX_QUEUED_EXTRACTIONS),
                                           runnable -> {
                                             Thread thread = new Thread(runnable,
                                                                        "documents-paged-text-extractor-"
                                                                            + threadIndex.incrementAndGet());
                                             thread.setDaemon(true);
                                             return thread;
                                           });
    ((ThreadPoolExecutor) this.executor).allowCoreThreadTimeOut(true);
  }

  /**
   * Reads a part of a document's text, with its length and its outline.
   *
   * @param documentId the document id
   * @param offset the first character to read
   * @param length the most characters to read, 0 for none
   * @return the part, or why there is no text
   */
  public DocumentTextPage getPage(String documentId, long offset, int length) {
    for (int attempt = 0; attempt < 2; attempt++) {
      Extraction extraction = getExtraction(documentId);
      if (extraction.status() != Status.EXTRACTED) {
        return DocumentTextPage.none(extraction.status());
      }
      long start = Math.max(0, Math.min(offset, extraction.totalChars()));
      int count = (int) Math.max(0, Math.min(length, extraction.totalChars() - start));
      try {
        String text = readChars(extraction.file(), start, count);
        return new DocumentTextPage(text, start, extraction.totalChars(), extraction.truncated(), extraction.outline(), Status.EXTRACTED);
      } catch (IOException e) {
        // the cache file was evicted between the lookup and the read: extracted again once
        LOG.debug("The cached text of document {} could not be read, extracting it again", documentId, e);
        forget(extraction);
      }
    }
    return DocumentTextPage.none(Status.UNREADABLE);
  }

  /**
   * Stops the extraction thread.
   */
  public void shutdown() {
    executor.shutdownNow();
  }

  /**
   * Finds the extracted text of a document's current version, extracting it
   * when the cache doesn't hold it, waiting for that a bounded time.
   *
   * @param documentId the document id
   * @return the extraction, {@link Status#BUSY} when it isn't over in time or
   *         can't be queued
   */
  Extraction getExtraction(String documentId) {
    String key;
    try {
      key = versionKey(documentId);
    } catch (RepositoryException e) {
      LOG.warn("Could not read the version of document {}", documentId, e);
      return Extraction.none(Status.UNREADABLE);
    }
    if (key == null) {
      return Extraction.none(Status.NOT_A_FILE);
    }
    Future<Extraction> future;
    // the cache and the running extractions are looked up, and an extraction started, under
    // one lock its end takes too: a version is never extracted twice into its one cache file
    synchronized (cache) {
      Extraction cached = cache.get(key);
      if (cached != null) {
        return cached;
      }
      try {
        future = running.computeIfAbsent(key, k -> executor.submit(() -> {
          Extraction extraction = null;
          try {
            extraction = extract(documentId, k);
            return extraction;
          } finally {
            synchronized (cache) {
              if (extraction != null) {
                remember(k, extraction);
              }
              running.remove(k);
            }
          }
        }));
      } catch (RejectedExecutionException e) {
        LOG.warn("Too many large documents are being read, the text of document {} is not extracted", documentId);
        return Extraction.none(Status.BUSY);
      }
    }
    try {
      return future.get(waitMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      // the extraction goes on: a later call finds its text
      return Extraction.none(Status.BUSY);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Extraction.none(Status.UNREADABLE);
    } catch (ExecutionException e) {
      LOG.warn("Error extracting the text of document {}", documentId, e.getCause());
      return Extraction.none(Status.UNREADABLE);
    }
  }

  /**
   * Names a document's current version: its id, the date its content last
   * changed and its size.
   *
   * @param documentId the document id
   * @return the key, null when the document isn't a file with a content
   * @throws RepositoryException when the node can't be read
   */
  private String versionKey(String documentId) throws RepositoryException {
    Session session = openSystemSession();
    try {
      Node content = getContent(session, documentId);
      if (content == null) {
        return null;
      }
      long modified = content.hasProperty(NodeTypeConstants.JCR_LAST_MODIFIED) ? content.getProperty(NodeTypeConstants.JCR_LAST_MODIFIED)
                                                                                       .getDate()
                                                                                       .getTimeInMillis()
                                                                                : 0;
      return documentId + ":" + modified + ":" + content.getProperty(NodeTypeConstants.JCR_DATA).getLength();
    } finally {
      session.logout();
    }
  }

  /**
   * Finds the content node of a file.
   *
   * @param session the session
   * @param documentId the file id
   * @return its content, null when the document isn't a file with a content
   * @throws RepositoryException when the node can't be read
   */
  private Node getContent(Session session, String documentId) throws RepositoryException {
    Node node = JCRDocumentsUtil.getNodeByIdentifier(session, documentId);
    if (node == null || !node.isNodeType(NodeTypeConstants.NT_FILE) || !node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
      return null;
    }
    Node content = node.getNode(NodeTypeConstants.JCR_CONTENT);
    return content.hasProperty(NodeTypeConstants.JCR_DATA) ? content : null;
  }

  /**
   * Extracts the whole text of a file into a cache file.
   *
   * @param documentId the file id
   * @param key the version key naming the cache file
   * @return the extraction
   * @throws Exception when the repository fails
   */
  protected Extraction extract(String documentId, String key) throws Exception { // NOSONAR
    Session session = openSystemSession();
    File binary = null;
    Path textFile = cacheDirectory.resolve(DigestUtils.sha256Hex(key) + ".txt");
    try {
      Node content = getContent(session, documentId);
      if (content == null) {
        return Extraction.none(Status.NOT_A_FILE);
      }
      Node node = content.getParent();
      Property data = content.getProperty(NodeTypeConstants.JCR_DATA);
      if (data.getLength() > maxFileSizeBytes) {
        return Extraction.none(Status.TOO_LARGE);
      }
      String mimeType = DocumentTextExtractor.resolveMimeType(node, content);
      boolean structured = DocumentTextStructureParser.isStructured(mimeType);
      DocumentReader reader = null;
      if (!structured) {
        if (!formats.isSupported(mimeType)) {
          return Extraction.none(Status.UNSUPPORTED_FORMAT);
        }
        try {
          reader = documentReaderService.getDocumentReader(mimeType);
        } catch (HandlerNotFoundException e) {
          return Extraction.none(Status.UNSUPPORTED_FORMAT);
        }
      }
      binary = File.createTempFile("documents-paged-text-", ".bin");
      try (InputStream stream = data.getStream()) {
        Files.copy(stream, binary.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
      List<DocumentOutlineEntry> outline = new ArrayList<>();
      CountingWriter writer = new CountingWriter(new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(textFile),
                                                                                           StandardCharsets.UTF_16BE)),
                                                 maxChars,
                                                 deadline);
      try (writer) {
        if (structured) {
          DocumentTextStructureParser.parse(binary, mimeType, writer, outline);
        } else {
          writeText(reader, binary, writer);
        }
      } catch (TextLimitReachedException e) {
        // the text is cut at the most characters kept, the outline holds its headings up to there
        LOG.debug("The text of document {} is cut at {} characters", documentId, maxChars);
      } catch (InterruptedIOException e) {
        Files.deleteIfExists(textFile);
        LOG.warn("Reading the text of document {} took more than {} ms, abandoned", documentId, timeoutMillis);
        return Extraction.none(Status.TIMED_OUT);
      } catch (IOException | RuntimeException e) {
        Files.deleteIfExists(textFile);
        LOG.warn("Could not read the text of document {}", documentId, e);
        return Extraction.none(Status.UNREADABLE);
      }
      if (writer.count() == 0 || isBlank(textFile, writer.count())) {
        Files.deleteIfExists(textFile);
        return Extraction.none(Status.NO_TEXT);
      }
      return new Extraction(textFile, writer.count(), writer.isTruncated(), List.copyOf(outline), Status.EXTRACTED);
    } finally {
      session.logout();
      if (binary != null) {
        Files.deleteIfExists(binary.toPath());
      }
    }
  }

  /**
   * Writes the text a platform document reader extracts from a file.
   *
   * @param reader the reader of the file's type
   * @param binary the file
   * @param writer where the text is written
   * @throws Exception when the file can't be parsed
   */
  private void writeText(DocumentReader reader, File binary, CountingWriter writer) throws Exception { // NOSONAR
    try (InputStream stream = Files.newInputStream(binary.toPath())) {
      if (reader instanceof AdvancedDocumentReader advancedReader) {
        try (Reader text = advancedReader.getContentAsReader(stream)) {
          char[] buffer = new char[8192];
          int read;
          while ((read = text.read(buffer)) != -1) {
            writer.write(buffer, 0, read);
          }
        }
      } else {
        String text = reader.getContentAsText(stream);
        if (text != null) {
          writer.write(text);
        }
      }
    }
  }

  /**
   * Tells whether a text holds only blanks, reading at most its beginning.
   *
   * @param textFile the cache file
   * @param totalChars the length of its text
   * @return true when the text read is blank
   * @throws IOException when the file can't be read
   */
  private boolean isBlank(Path textFile, long totalChars) throws IOException {
    return StringUtils.isBlank(readChars(textFile, 0, (int) Math.min(totalChars, 65_536)))
        && totalChars <= 65_536;
  }

  /**
   * Reads characters of a cache file at an offset, a surrogate pair cut at
   * either end being left out whole: the part is then one character shorter
   * than asked, and the next part starts after the pair's first half.
   *
   * @param file the cache file
   * @param offset the first character
   * @param count how many characters
   * @return the characters
   * @throws IOException when the file can't be read
   */
  static String readChars(Path file, long offset, int count) throws IOException {
    if (count <= 0) {
      return "";
    }
    byte[] bytes = new byte[count * 2];
    try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
      in.seek(offset * 2);
      in.readFully(bytes);
    }
    // the chars as stored, a half pair kept as it is, never decoded into a replacement character
    String text = ByteBuffer.wrap(bytes).asCharBuffer().toString();
    int start = Character.isLowSurrogate(text.charAt(0)) ? 1 : 0;
    int end = Character.isHighSurrogate(text.charAt(text.length() - 1)) ? text.length() - 1 : text.length();
    return start >= end ? "" : text.substring(start, end);
  }

  /**
   * Keeps an extraction in the cache, evicting the least recently read ones
   * past the cache's size.
   *
   * @param key the version key
   * @param extraction the extraction
   */
  private void remember(String key, Extraction extraction) {
    synchronized (cache) {
      cache.put(key, extraction);
      cacheBytes += extraction.totalChars() * 2;
      Iterator<Map.Entry<String, Extraction>> eldest = cache.entrySet().iterator();
      while (cacheBytes > maxCacheBytes && eldest.hasNext()) {
        Map.Entry<String, Extraction> entry = eldest.next();
        if (entry.getValue() == extraction) {
          // the text just extracted stays, however large, for the call waiting for it
          continue;
        }
        eldest.remove();
        cacheBytes -= entry.getValue().totalChars() * 2;
        deleteQuietly(entry.getValue());
      }
    }
  }

  /**
   * Drops an extraction from the cache.
   *
   * @param extraction the extraction
   */
  private void forget(Extraction extraction) {
    // under the lock an extraction starts under: no new extraction of the version writes the file meanwhile
    synchronized (cache) {
      if (cache.values().removeIf(cached -> cached == extraction)) {
        cacheBytes -= extraction.totalChars() * 2;
        deleteQuietly(extraction);
      }
    }
  }

  /**
   * Deletes an extraction's cache file.
   *
   * @param extraction the extraction
   */
  private void deleteQuietly(Extraction extraction) {
    if (extraction.file() != null) {
      try {
        Files.deleteIfExists(extraction.file());
      } catch (IOException e) {
        LOG.debug("Could not delete the cached text {}", extraction.file(), e);
      }
    }
  }

  /**
   * Opens a system session on the collaboration workspace, the caller having
   * checked the user can read the document.
   *
   * @return the session
   * @throws RepositoryException when the repository fails
   */
  protected Session openSystemSession() throws RepositoryException {
    return SessionProvider.createSystemProvider().getSession(COLLABORATION, repositoryService.getCurrentRepository());
  }

}
