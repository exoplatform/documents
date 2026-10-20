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
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.exoplatform.documents.storage.jcr.model.PagedTextExtraction;
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
 * The file is copied to a temporary file and parsed from it. A Word document,
 * a presentation, a PDF and a spreadsheet get their outline from the same walk
 * that writes their text, the PDF being parsed with its buffers in temporary
 * files and the spreadsheet streamed a row at a time. Any other supported
 * type, an older Office or an OpenDocument file for instance, is read by the
 * platform's document readers, without an outline; most of them load the
 * whole file, so reading one takes a heap of at least the file's size, up to
 * the largest file read, the same limit as the largest file a user can attach
 * in the UI. One file is extracted at a time; a caller waits for its extraction a bounded time, the
 * extraction going on after it so a later call finds the text. The text is
 * stored as UTF-16, two bytes a character, so a part is read at its offset
 * without reading what comes before it. The cache is a node's own: another
 * node of a cluster extracts the file again on its first part.
 */
public class DocumentPagedTextExtractor {

  /** How many extractions may wait for the one running. */
  static final int                   MAX_QUEUED_EXTRACTIONS = 4;

  /** The most file versions the cache remembers, a version without text taking no room on disk. */
  static final int                   MAX_CACHED_VERSIONS    = 10_000;

  /**
   * The outcomes of an extraction that hold for as long as the version of the
   * file does, and are remembered with it; a timeout or a failure to read is
   * tried again on the next call. A file that is not one, too large or of a
   * format never read is refused before any extraction; an extraction that
   * finds it so has met a file changed since its version was read, so its
   * outcome is not kept under that version.
   */
  static final Set<Status>           REMEMBERED_STATUSES    = EnumSet.of(Status.EXTRACTED, Status.NO_TEXT);

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

  private final Map<String, Future<PagedTextExtraction>> running = new ConcurrentHashMap<>();

  /** The extracted texts by file version, the least recently read first. */
  private final LinkedHashMap<String, PagedTextExtraction> cache = new LinkedHashMap<>(16, 0.75f, true);

  private long                       cacheBytes;

  private int                        maxCachedVersions      = MAX_CACHED_VERSIONS;

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
   * @param length the most characters to read, 0 for none; a part that would
   *          hold only halves of surrogate pairs gets one more character, so
   *          it gives a pair whole
   * @return the part, or why there is no text
   */
  public DocumentTextPage getPage(String documentId, long offset, int length) {
    for (int attempt = 0; attempt < 2; attempt++) {
      PagedTextExtraction extraction = getExtraction(documentId);
      if (extraction.status() != Status.EXTRACTED) {
        return DocumentTextPage.none(extraction.status());
      }
      long start = Math.max(0, Math.min(offset, extraction.totalChars()));
      int count = (int) Math.max(0, Math.min(length, extraction.totalChars() - start));
      try {
        String stored = readStoredChars(extraction.file(), start, count);
        if (count > 0 && withoutHalfPairs(stored).isEmpty() && start + count < extraction.totalChars()) {
          // a part holding only halves of pairs: one more character gives a pair whole, so the reading moves on
          stored = readStoredChars(extraction.file(), start, count + 1);
        }
        // a part starting on a pair's second half starts after it: the next part follows the text given
        long textStart = !stored.isEmpty() && Character.isLowSurrogate(stored.charAt(0)) ? start + 1 : start;
        return new DocumentTextPage(withoutHalfPairs(stored),
                                    textStart,
                                    extraction.totalChars(),
                                    extraction.truncated(),
                                    extraction.outline(),
                                    Status.EXTRACTED);
      } catch (IOException e) {
        // the cache file was evicted between the lookup and the read: extracted again once
        LOG.debug("The cached text of document {} could not be read, extracting it again", documentId, e);
        forget(extraction);
      }
    }
    return DocumentTextPage.none(Status.UNREADABLE);
  }

  /**
   * Sets the most file versions the cache remembers.
   *
   * @param maxCachedVersions the number of versions
   */
  void setMaxCachedVersions(int maxCachedVersions) {
    this.maxCachedVersions = maxCachedVersions;
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
  PagedTextExtraction getExtraction(String documentId) {
    String key;
    Status refusal;
    try {
      Session session = openSystemSession();
      try {
        Node content = getContent(session, documentId);
        if (content == null) {
          return PagedTextExtraction.none(Status.NOT_A_FILE);
        }
        key = versionKey(documentId, content);
        // a file too large or of a format never read is refused here, never queued behind a long extraction
        refusal = refusalOf(content);
      } finally {
        session.logout();
      }
    } catch (RepositoryException e) {
      LOG.warn("Could not read the version of document {}", documentId, e);
      return PagedTextExtraction.none(Status.UNREADABLE);
    }
    if (refusal != null) {
      return PagedTextExtraction.none(refusal);
    }
    Future<PagedTextExtraction> future;
    // the cache and the running extractions are looked up, and an extraction started, under
    // one lock its end takes too: a version is never extracted twice into its one cache file
    synchronized (cache) {
      PagedTextExtraction cached = cache.get(key);
      if (cached != null) {
        return cached;
      }
      try {
        future = running.computeIfAbsent(key, k -> executor.submit(() -> {
          PagedTextExtraction extraction = null;
          try {
            extraction = extract(documentId, k);
            return extraction;
          } finally {
            synchronized (cache) {
              if (extraction != null && REMEMBERED_STATUSES.contains(extraction.status())) {
                remember(k, extraction);
              }
              running.remove(k);
            }
          }
        }));
      } catch (RejectedExecutionException e) {
        LOG.warn("Too many large documents are being read, the text of document {} is not extracted", documentId);
        return PagedTextExtraction.none(Status.BUSY);
      }
    }
    try {
      return future.get(waitMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      // the extraction goes on: a later call finds its text
      return PagedTextExtraction.none(Status.BUSY);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return PagedTextExtraction.none(Status.UNREADABLE);
    } catch (ExecutionException e) {
      LOG.warn("Error extracting the text of document {}", documentId, e.getCause());
      return PagedTextExtraction.none(Status.UNREADABLE);
    }
  }

  /**
   * Names a document's current version: its id, the date its content last
   * changed and its size.
   *
   * @param documentId the document id
   * @param content the document's content node
   * @return the key
   * @throws RepositoryException when the node can't be read
   */
  private String versionKey(String documentId, Node content) throws RepositoryException {
    long modified = content.hasProperty(NodeTypeConstants.JCR_LAST_MODIFIED) ? content.getProperty(NodeTypeConstants.JCR_LAST_MODIFIED)
                                                                                     .getDate()
                                                                                     .getTimeInMillis()
                                                                              : 0;
    return documentId + ":" + modified + ":" + content.getProperty(NodeTypeConstants.JCR_DATA).getLength();
  }

  /**
   * Tells why a file's text is never read, from its size and its type, without
   * reading the file.
   *
   * @param content the file's content node
   * @return {@link Status#TOO_LARGE} or {@link Status#UNSUPPORTED_FORMAT}, null
   *         when its text is read
   * @throws RepositoryException when the node can't be read
   */
  private Status refusalOf(Node content) throws RepositoryException {
    if (content.getProperty(NodeTypeConstants.JCR_DATA).getLength() > maxFileSizeBytes) {
      return Status.TOO_LARGE;
    }
    String mimeType = DocumentTextExtractor.resolveMimeType(content.getParent(), content);
    if (DocumentTextStructureParser.isStructured(mimeType)) {
      return null;
    }
    if (!formats.isSupported(mimeType)) {
      return Status.UNSUPPORTED_FORMAT;
    }
    try {
      documentReaderService.getDocumentReader(mimeType);
      return null;
    } catch (HandlerNotFoundException e) {
      return Status.UNSUPPORTED_FORMAT;
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
  protected PagedTextExtraction extract(String documentId, String key) throws Exception { // NOSONAR
    Session session = openSystemSession();
    File binary = null;
    Path textFile = cacheDirectory.resolve(DigestUtils.sha256Hex(key) + ".txt");
    boolean kept = false;
    try {
      Node content = getContent(session, documentId);
      if (content == null) {
        return PagedTextExtraction.none(Status.NOT_A_FILE);
      }
      // the file may have changed since the caller looked at it
      Status refusal = refusalOf(content);
      if (refusal != null) {
        return PagedTextExtraction.none(refusal);
      }
      Property data = content.getProperty(NodeTypeConstants.JCR_DATA);
      String mimeType = DocumentTextExtractor.resolveMimeType(content.getParent(), content);
      boolean structured = DocumentTextStructureParser.isStructured(mimeType);
      DocumentReader reader = structured ? null : documentReaderService.getDocumentReader(mimeType);
      binary = File.createTempFile("documents-paged-text-", ".bin");
      try (InputStream stream = data.getStream()) {
        Files.copy(stream, binary.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      // the cache directory, under the temporary directory, may have been emptied since the start
      Files.createDirectories(cacheDirectory);
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
        LOG.warn("Reading the text of document {} took more than {} ms, abandoned", documentId, timeoutMillis);
        return PagedTextExtraction.none(Status.TIMED_OUT);
      } catch (IOException | RuntimeException e) {
        LOG.warn("Could not read the text of document {}", documentId, e);
        return PagedTextExtraction.none(Status.UNREADABLE);
      }
      if (writer.count() == 0 || isBlank(textFile, writer.count())) {
        return PagedTextExtraction.none(Status.NO_TEXT);
      }
      kept = true;
      return new PagedTextExtraction(textFile, writer.count(), writer.isTruncated(), List.copyOf(outline), Status.EXTRACTED);
    } finally {
      session.logout();
      if (!kept) {
        // whatever stopped the reading, an Error included, no text but an extracted one stays on disk
        Files.deleteIfExists(textFile);
      }
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
   * than asked, and starts one character later when the offset falls on a
   * pair's second half.
   *
   * @param file the cache file
   * @param offset the first character
   * @param count how many characters
   * @return the characters
   * @throws IOException when the file can't be read
   */
  static String readChars(Path file, long offset, int count) throws IOException {
    return withoutHalfPairs(readStoredChars(file, offset, count));
  }

  /**
   * Reads characters of a cache file at an offset as they are stored, a half
   * pair at either end included.
   *
   * @param file the cache file
   * @param offset the first character
   * @param count how many characters
   * @return the characters
   * @throws IOException when the file can't be read
   */
  private static String readStoredChars(Path file, long offset, int count) throws IOException {
    if (count <= 0) {
      return "";
    }
    byte[] bytes = new byte[count * 2];
    try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
      in.seek(offset * 2);
      in.readFully(bytes);
    }
    // the chars as stored, a half pair kept as it is, never decoded into a replacement character
    return ByteBuffer.wrap(bytes).asCharBuffer().toString();
  }

  /**
   * Leaves out a surrogate pair's half at either end of a text.
   *
   * @param text the text
   * @return the text without a half pair at its ends
   */
  private static String withoutHalfPairs(String text) {
    if (text.isEmpty()) {
      return text;
    }
    int start = Character.isLowSurrogate(text.charAt(0)) ? 1 : 0;
    int end = Character.isHighSurrogate(text.charAt(text.length() - 1)) ? text.length() - 1 : text.length();
    return start >= end ? "" : text.substring(start, end);
  }

  /**
   * Keeps an extraction in the cache, evicting the least recently read ones
   * past the cache's size or its number of versions.
   *
   * @param key the version key
   * @param extraction the extraction
   */
  private void remember(String key, PagedTextExtraction extraction) {
    synchronized (cache) {
      cache.put(key, extraction);
      cacheBytes += extraction.totalChars() * 2;
      Iterator<Map.Entry<String, PagedTextExtraction>> eldest = cache.entrySet().iterator();
      while ((cacheBytes > maxCacheBytes || cache.size() > maxCachedVersions) && eldest.hasNext()) {
        Map.Entry<String, PagedTextExtraction> entry = eldest.next();
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
  private void forget(PagedTextExtraction extraction) {
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
  private void deleteQuietly(PagedTextExtraction extraction) {
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
