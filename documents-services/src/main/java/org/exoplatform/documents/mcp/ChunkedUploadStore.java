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
package org.exoplatform.documents.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.documents.constant.UploadConflictAction;
import org.exoplatform.documents.mcp.model.ChunkedUpload;
import org.exoplatform.documents.mcp.model.DocumentModel;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Holds the files an assistant sends in pieces, each in a temporary file of
 * this node, until it is filed: a file too large for one tool call is sent in
 * several. An upload announces its size at its start; the piece that brings
 * it to that size hands it to the caller to file, and the document it was
 * filed as is kept until the upload expires, to answer that piece sent again.
 * An upload belongs to the user who started it, who alone sees it; it
 * expires when left alone, and a user keeps a bounded number in progress. The
 * pieces come in order, numbered from 0; the last piece received, sent again
 * after a lost answer, is acknowledged without being written twice, and
 * another piece under its number is refused. The temporary files live in a
 * directory of their own, this process's user's only, emptied when the store
 * is built, so a restart leaves none behind; when that directory can't be
 * used, the pieces go to a new directory of this user's, which a restart does
 * not empty. Every change goes through one lock.
 */
class ChunkedUploadStore {

  private static final Log                  LOG          = ExoLogger.getLogger(ChunkedUploadStore.class);

  private static final String               PART_SUFFIX  = ".part";

  private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");

  private final Map<String, ChunkedUpload>  uploads      = new ConcurrentHashMap<>();

  private final long                        maxBytes;

  private final int                         maxOpenPerUser;

  private final Duration                    timeToLive;

  private final Clock                       clock;

  private final Path                        requestedDirectory;

  private Path                              directory;

  /**
   * Builds the store, and empties its directory of the pieces an earlier run
   * left.
   *
   * @param maxBytes the largest file sent in pieces
   * @param maxOpenPerUser the most uploads a user keeps in progress
   * @param timeToLive how long an upload left alone is kept
   * @param clock the clock telling when an upload was last used
   * @param directory the directory of the temporary files, created readable
   *          by this process's user only when missing; when it is a link,
   *          another user's or its owner can't be told, a new directory of
   *          this user's is used instead. A directory that can't be prepared
   *          refuses the uploads, never the store's building
   */
  ChunkedUploadStore(long maxBytes, int maxOpenPerUser, Duration timeToLive, Clock clock, Path directory) {
    this.maxBytes = maxBytes;
    this.maxOpenPerUser = maxOpenPerUser;
    this.timeToLive = timeToLive;
    this.clock = clock;
    this.requestedDirectory = directory;
    this.directory = prepareDirectory();
    if (this.directory != null) {
      emptyDirectory();
    }
  }

  /**
   * Starts an upload.
   *
   * @param userName the user starting it
   * @param folderId the folder it will be filed into
   * @param name the file name it will be filed under
   * @param size the file's size, in bytes
   * @param conflictAction what to do when the folder holds a file of that name
   * @return the upload
   * @throws IllegalArgumentException when the size is not positive or over the
   *           largest
   * @throws IllegalStateException when the user has too many uploads in
   *           progress, or the temporary file can't be created
   */
  synchronized ChunkedUpload start(String userName,
                                   String folderId,
                                   String name,
                                   long size,
                                   UploadConflictAction conflictAction) {
    if (size <= 0) {
      throw new IllegalArgumentException("The 'size' is the file's size in bytes, more than 0.");
    }
    if (size > maxBytes) {
      throw new IllegalArgumentException("The file is %d bytes, over the %d MB a file uploaded can be.".formatted(size,
                                                                                                                maxBytes / (1024 * 1024)));
    }
    expire();
    long open = uploads.values()
                       .stream()
                       .filter(upload -> upload.userName().equals(userName) && upload.document() == null)
                       .count();
    if (open >= maxOpenPerUser) {
      throw new IllegalStateException(("You already have %d uploads in progress. Send their last pieces, "
          + "or wait %d minutes for them to expire.").formatted(open, timeToLive.toMinutes()));
    }
    if (directory == null || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      // gone since the store was built, removed by a temporary files cleaner for instance
      directory = prepareDirectory();
      if (directory == null) {
        throw new IllegalStateException("The upload can't be started: the directory of its pieces can't be prepared on this server.");
      }
    }
    try {
      Path file = Files.createTempFile(directory, "upload-", PART_SUFFIX);
      ChunkedUpload upload = new ChunkedUpload(UUID.randomUUID().toString(),
                                               userName,
                                               folderId,
                                               name,
                                               size,
                                               conflictAction,
                                               file,
                                               0,
                                               0,
                                               null,
                                               clock.instant(),
                                               false,
                                               null);
      uploads.put(upload.id(), upload);
      return upload;
    } catch (IOException e) {
      throw new IllegalStateException("The upload can't be started: " + e.getMessage());
    }
  }

  /**
   * Adds a piece to an upload. The answer is to be filed by the caller when
   * it is {@link ChunkedUpload#filing()} with no document yet: only the call
   * whose piece completes the upload gets such an answer.
   *
   * @param userName the user sending it
   * @param uploadId the upload
   * @param index the piece's number, from 0
   * @param bytes the piece
   * @return the upload with the piece, or, for its last piece sent again once
   *         filed, the upload with the document it was filed as
   * @throws IllegalArgumentException when the upload isn't the user's or is
   *           gone, the piece comes out of order, differs from the piece
   *           already received under its number, or would grow the file past
   *           its size
   * @throws IllegalStateException when the upload is being filed, which
   *           leaves it open, or the piece can't be stored, which cancels it
   */
  synchronized ChunkedUpload append(String userName, String uploadId, int index, byte[] bytes) {
    ChunkedUpload upload = get(userName, uploadId);
    String digest = digest(bytes);
    if (index == upload.nextIndex() - 1) {
      if (!digest.equals(upload.lastPieceDigest())) {
        throw new IllegalArgumentException(("Piece %d was already received with another content. Send piece %d next; "
            + "to change the file, start a new upload.").formatted(index, upload.nextIndex()));
      }
      if (upload.filing()) {
        throw new IllegalStateException("Piece %d completed the file, which is being filed: send it again in a few seconds to get the document.".formatted(index));
      }
      // the same piece sent again, its answer lost: already written
      return replace(upload.touchedAt(clock.instant()));
    }
    if (upload.document() != null || upload.filing()) {
      throw new IllegalArgumentException("The upload is complete: its %d pieces were received and the file is filed.".formatted(upload.nextIndex()));
    }
    if (index != upload.nextIndex()) {
      throw new IllegalArgumentException("Send the pieces in order: piece %d is expected next, not piece %d.".formatted(upload.nextIndex(),
                                                                                                                       index));
    }
    if (upload.received() + bytes.length > upload.size()) {
      throw new IllegalArgumentException(("Piece %d is %d bytes, but the file announced is %d bytes and %d were received: "
          + "the piece would make it larger. Send the rest of the file's bytes only.").formatted(index,
                                                                                                bytes.length,
                                                                                                upload.size(),
                                                                                                upload.received()));
    }
    try (OutputStream out = Files.newOutputStream(upload.file(), StandardOpenOption.APPEND)) {
      out.write(bytes);
    } catch (IOException e) {
      discard(upload);
      throw new IllegalStateException("The piece could not be stored, the upload is cancelled: " + e.getMessage());
    }
    return replace(upload.withPiece(bytes.length, digest, clock.instant()));
  }

  /**
   * Records the document an upload was filed as, kept to answer its last piece
   * sent again until the upload expires.
   *
   * @param uploadId the upload
   * @param document the document
   */
  synchronized void filed(String uploadId, DocumentModel document) {
    ChunkedUpload upload = uploads.get(uploadId);
    if (upload != null) {
      deleteQuietly(upload.file());
      uploads.put(uploadId, upload.filedAs(document, clock.instant()));
    }
  }

  /**
   * Drops an upload whose file couldn't be filed.
   *
   * @param uploadId the upload
   */
  synchronized void failed(String uploadId) {
    ChunkedUpload upload = uploads.get(uploadId);
    if (upload != null) {
      discard(upload);
    }
  }

  /**
   * Drops the uploads left alone past their time to live, and their files.
   */
  synchronized void expire() {
    Instant limit = clock.instant().minus(timeToLive);
    for (Iterator<ChunkedUpload> iterator = uploads.values().iterator(); iterator.hasNext();) {
      ChunkedUpload upload = iterator.next();
      if (!upload.filing() && upload.touched().isBefore(limit)) {
        iterator.remove();
        deleteQuietly(upload.file());
      }
    }
  }

  /**
   * Finds a user's upload, refreshing nothing.
   *
   * @param userName the user
   * @param uploadId the upload
   * @return the upload
   * @throws IllegalArgumentException when it isn't the user's or is gone
   */
  private ChunkedUpload get(String userName, String uploadId) {
    expire();
    ChunkedUpload upload = StringUtils.isBlank(uploadId) ? null : uploads.get(uploadId);
    if (upload == null || !upload.userName().equals(userName)) {
      // another user's upload is not told apart from a missing one
      throw new IllegalArgumentException(("No upload in progress has the id %s: it expired after %d minutes without a piece, "
          + "its file could not be filed, or it was started on another server of the platform. Start again with "
          + "start_document_upload.").formatted(uploadId, timeToLive.toMinutes()));
    }
    return upload;
  }

  /**
   * Stores an upload's new state.
   *
   * @param upload the upload
   * @return the upload
   */
  private ChunkedUpload replace(ChunkedUpload upload) {
    uploads.put(upload.id(), upload);
    return upload;
  }

  /**
   * Drops an upload and its file.
   *
   * @param upload the upload
   */
  private void discard(ChunkedUpload upload) {
    uploads.remove(upload.id());
    deleteQuietly(upload.file());
  }

  /**
   * Prepares the directory the pieces are kept in.
   *
   * @return the directory, or null when it can't be prepared
   */
  private Path prepareDirectory() {
    try {
      return ownDirectory(requestedDirectory);
    } catch (IllegalStateException e) {
      LOG.warn("The directory of the uploads in pieces can't be prepared: uploads in pieces are refused until it can", e);
      return null;
    }
  }

  /**
   * The directory the pieces are kept in, for this process's user only: a
   * shared temporary directory lets any local user create the expected one
   * first, or put a link in its place, and so read or swap the pieces.
   *
   * @param requested the directory asked for
   * @return the directory asked for, created or restricted to this user, or a
   *         new directory of this user's when it is a link, another user's,
   *         or its owner can't be told
   * @throws IllegalStateException when no directory can be prepared
   */
  static Path ownDirectory(Path requested) {
    try {
      boolean posix = requested.getFileSystem().supportedFileAttributeViews().contains("posix");
      if (!Files.exists(requested, LinkOption.NOFOLLOW_LINKS)) {
        Files.createDirectories(requested.getParent());
        if (posix) {
          Files.createDirectory(requested, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
        } else {
          Files.createDirectory(requested);
        }
        return requested;
      }
      if (Files.isSymbolicLink(requested) || !Files.isDirectory(requested, LinkOption.NOFOLLOW_LINKS) || !isOwnedByThisUser(requested)) {
        Path own = Files.createTempDirectory(requested.getFileName().toString() + "-");
        LOG.warn("The directory {} of the uploads in pieces is a link, not a directory, or not this user's: {} is used instead, "
            + "which a restart does not empty", requested, own);
        return own;
      }
      if (posix) {
        Files.setPosixFilePermissions(requested, OWNER_ONLY);
      }
      return requested;
    } catch (IOException e) {
      throw new IllegalStateException("The directory of the uploads in pieces " + requested + " can't be prepared", e);
    }
  }

  /**
   * Tells whether a directory is this process's user's.
   *
   * @param directory the directory
   * @return false when it is another user's, or when this user can't be
   *         resolved, as for a container's user with no account entry
   */
  private static boolean isOwnedByThisUser(Path directory) {
    try {
      UserPrincipal user = directory.getFileSystem()
                                    .getUserPrincipalLookupService()
                                    .lookupPrincipalByName(System.getProperty("user.name"));
      return user.equals(Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS));
    } catch (IOException e) {
      LOG.debug("The owner of {} can't be compared to this user", directory, e);
      return false;
    }
  }

  /**
   * Deletes the temporary files an earlier run of the store left in its
   * directory.
   */
  private void emptyDirectory() {
    if (!Files.isDirectory(directory)) {
      return;
    }
    try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + PART_SUFFIX)) {
      for (Path file : files) {
        deleteQuietly(file);
      }
    } catch (IOException e) {
      LOG.warn("Could not empty the directory of the uploads in pieces {}", directory, e);
    }
  }

  /**
   * The SHA-256 of a piece.
   *
   * @param bytes the piece
   * @return its digest, hex-encoded
   */
  private static String digest(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  /**
   * Deletes a temporary file.
   *
   * @param file the file
   */
  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      LOG.debug("Could not delete the temporary upload file {}", file, e);
    }
  }

}
