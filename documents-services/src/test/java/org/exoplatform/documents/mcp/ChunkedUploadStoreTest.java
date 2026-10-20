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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.exoplatform.documents.constant.UploadConflictAction;
import org.exoplatform.documents.mcp.model.ChunkedUpload;
import org.exoplatform.documents.mcp.model.DocumentModel;

/**
 * Sends files in pieces through the store: order, repeats, owners, sizes,
 * completion, uploads in progress and expiry, on a clock the test moves.
 */
public class ChunkedUploadStoreTest {

  /** A clock the test moves. */
  private static final class MovingClock extends Clock {

    private Instant now = Instant.parse("2026-10-20T10:00:00Z");

    /**
     * Moves the clock forward.
     *
     * @param duration how much
     */
    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Rule
  public TemporaryFolder     temporaryFolder = new TemporaryFolder();

  private final MovingClock  clock           = new MovingClock();

  /**
   * Builds a store of files of at most 10 bytes, 2 uploads in progress per
   * user, in a directory of the test.
   *
   * @return the store
   * @throws IOException never
   */
  private ChunkedUploadStore newStore() throws IOException {
    return new ChunkedUploadStore(10, 2, Duration.ofMinutes(30), clock, temporaryFolder.getRoot().toPath().resolve("uploads"));
  }

  /**
   * Starts an upload keeping both files on a conflict.
   *
   * @param store the store
   * @param userName the user
   * @param size the file's size
   * @return the upload
   */
  private static ChunkedUpload start(ChunkedUploadStore store, String userName, long size) {
    return store.start(userName, "folder", "grid.xlsx", size, UploadConflictAction.KEEP_BOTH);
  }

  /**
   * Pieces sent in order make the file, a piece sent again after a lost
   * answer is not added twice, and the piece that completes the size hands
   * the upload over to be filed, its file holding the pieces in order.
   * Mutants: the repeat added twice, the order unchecked, the completion
   * missed or announced early.
   *
   * @throws Exception never
   */
  @Test
  public void piecesInOrderMakeTheFileOnce() throws Exception {
    ChunkedUploadStore store = newStore();
    ChunkedUpload upload = start(store, "john", 4);
    assertFalse(store.append("john", upload.id(), 0, new byte[] { 1, 2 }).filing());
    store.append("john", upload.id(), 1, new byte[] { 3 });
    ChunkedUpload repeated = store.append("john", upload.id(), 1, new byte[] { 3 });

    assertEquals(3, repeated.received());
    assertEquals(2, repeated.nextIndex());
    assertFalse(repeated.filing());
    assertThrows(IllegalArgumentException.class, () -> store.append("john", upload.id(), 3, new byte[] { 4 }));
    assertThrows(IllegalArgumentException.class, () -> store.append("john", upload.id(), 0, new byte[] { 9, 9 }));

    ChunkedUpload complete = store.append("john", upload.id(), 2, new byte[] { 4 });

    assertTrue(complete.filing());
    assertNull(complete.document());
    assertArrayEquals(new byte[] { 1, 2, 3, 4 }, Files.readAllBytes(complete.file()));
  }

  /**
   * Another piece sent under the number of the last piece received is
   * refused, not acknowledged as the same piece sent again, even of the same
   * length, and nothing is written.
   * Mutants: the repeat recognised by its number and length alone.
   *
   * @throws Exception never
   */
  @Test
  public void anotherPieceUnderTheLastNumberIsRefused() throws Exception {
    ChunkedUploadStore store = newStore();
    ChunkedUpload upload = start(store, "john", 6);
    store.append("john", upload.id(), 0, new byte[] { 1, 2 });

    assertTrue(assertThrows(IllegalArgumentException.class,
                            () -> store.append("john", upload.id(), 0, new byte[] { 1, 3 })).getMessage()
                                                                                         .contains("another content"));
    ChunkedUpload next = store.append("john", upload.id(), 1, new byte[] { 3 });
    assertEquals(3, next.received());
    assertArrayEquals(new byte[] { 1, 2, 3 }, Files.readAllBytes(next.file()));
  }

  /**
   * Once filed, the last piece sent again returns the document it was filed
   * as, while the upload's file is gone; a new piece is refused, and a
   * piece sent again while the file is being filed asks to wait.
   * Mutants: the document not kept, the last piece written again or filed
   * twice.
   *
   * @throws Exception never
   */
  @Test
  public void theLastPieceSentAgainReturnsTheFiledDocument() throws Exception {
    ChunkedUploadStore store = newStore();
    ChunkedUpload upload = start(store, "john", 2);
    ChunkedUpload complete = store.append("john", upload.id(), 0, new byte[] { 1, 2 });

    assertThrows(IllegalStateException.class, () -> store.append("john", upload.id(), 0, new byte[] { 1, 2 }));

    DocumentModel document = org.mockito.Mockito.mock(DocumentModel.class);
    store.filed(upload.id(), document);

    ChunkedUpload again = store.append("john", upload.id(), 0, new byte[] { 1, 2 });
    assertSame(document, again.document());
    assertFalse(again.filing());
    assertFalse(Files.exists(complete.file()));
    assertThrows(IllegalArgumentException.class, () -> store.append("john", upload.id(), 1, new byte[] { 3 }));
  }

  /**
   * An upload whose file couldn't be filed is gone, its file with it.
   * Mutants: the upload or its file kept.
   *
   * @throws Exception never
   */
  @Test
  public void anUploadThatCouldntBeFiledIsGone() throws Exception {
    ChunkedUploadStore store = newStore();
    ChunkedUpload upload = start(store, "john", 1);
    ChunkedUpload complete = store.append("john", upload.id(), 0, new byte[] { 1 });

    store.failed(upload.id());

    assertFalse(Files.exists(complete.file()));
    assertThrows(IllegalArgumentException.class, () -> store.append("john", upload.id(), 0, new byte[] { 1 }));
  }

  /**
   * Another user's upload is not found, as a missing one, whatever its id.
   * Mutants: the owner unchecked.
   *
   * @throws Exception never
   */
  @Test
  public void anotherUsersUploadIsNotFound() throws Exception {
    ChunkedUploadStore store = newStore();
    ChunkedUpload upload = start(store, "john", 2);

    IllegalArgumentException piece = assertThrows(IllegalArgumentException.class,
                                                  () -> store.append("mary", upload.id(), 0, new byte[] { 1 }));
    assertTrue(piece.getMessage().contains("start_document_upload"));
    assertEquals(1, store.append("john", upload.id(), 0, new byte[] { 1 }).nextIndex());
  }

  /**
   * A size over the largest, or not positive, is refused at the start; a
   * piece that would grow the file past its size is refused without being
   * written, and the upload goes on.
   * Mutants: either bound of the size unchecked, the piece past the size
   * written.
   *
   * @throws Exception never
   */
  @Test
  public void theSizeBoundsTheFile() throws Exception {
    ChunkedUploadStore store = newStore();
    assertThrows(IllegalArgumentException.class, () -> start(store, "john", 11));
    assertThrows(IllegalArgumentException.class, () -> start(store, "john", 0));

    ChunkedUpload upload = start(store, "john", 10);
    store.append("john", upload.id(), 0, new byte[8]);

    assertThrows(IllegalArgumentException.class, () -> store.append("john", upload.id(), 1, new byte[3]));
    assertEquals(8, Files.size(upload.file()));
    assertTrue(store.append("john", upload.id(), 1, new byte[2]).filing());
  }

  /**
   * A user keeps a bounded number of uploads in progress, another user's not
   * counted nor a filed one; an upload left alone past its time to live is
   * dropped with its file, which frees its place.
   * Mutants: the bound unchecked, counting every user or the filed uploads,
   * the expiry dropped or keeping the file.
   *
   * @throws Exception never
   */
  @Test
  public void uploadsInProgressAreBoundedAndExpire() throws Exception {
    ChunkedUploadStore store = newStore();
    ChunkedUpload first = start(store, "john", 1);
    ChunkedUpload second = start(store, "john", 1);

    assertThrows(IllegalStateException.class, () -> start(store, "john", 1));
    start(store, "mary", 1);
    store.append("john", second.id(), 0, new byte[] { 1 });
    store.filed(second.id(), org.mockito.Mockito.mock(DocumentModel.class));
    start(store, "john", 1);

    clock.advance(Duration.ofMinutes(31));
    store.expire();

    assertFalse(Files.exists(first.file()));
    assertThrows(IllegalArgumentException.class, () -> store.append("john", first.id(), 0, new byte[] { 1 }));
    start(store, "john", 1);
  }

  /**
   * A piece keeps its upload alive, a piece sent again too: an upload used
   * within its time to live is not dropped.
   * Mutants: the piece or the repeat not refreshing the upload.
   *
   * @throws Exception never
   */
  @Test
  public void aPieceKeepsItsUploadAlive() throws Exception {
    ChunkedUploadStore store = newStore();
    ChunkedUpload upload = start(store, "john", 3);
    clock.advance(Duration.ofMinutes(20));
    store.append("john", upload.id(), 0, new byte[] { 1 });
    clock.advance(Duration.ofMinutes(20));
    store.append("john", upload.id(), 0, new byte[] { 1 });
    clock.advance(Duration.ofMinutes(20));

    assertEquals(2, store.append("john", upload.id(), 1, new byte[] { 2 }).received());
  }

  /**
   * A store built over a directory an earlier run left pieces in deletes
   * them, and only them.
   * Mutants: the directory not emptied, or emptied of other files.
   *
   * @throws Exception never
   */
  @Test
  public void theDirectoryIsEmptiedOfAnEarlierRunsPieces() throws Exception {
    Path directory = Files.createDirectories(temporaryFolder.getRoot().toPath().resolve("uploads"));
    Path leftOver = Files.writeString(directory.resolve("upload-1.part"), "left");
    Path other = Files.writeString(directory.resolve("keep.txt"), "other");

    newStore();

    assertFalse(Files.exists(leftOver));
    assertTrue(Files.exists(other));
  }


  /**
   * The directory of the pieces is created for this process's user only; a
   * link put in its place, as another local user could in a shared temporary
   * directory, is not followed: a directory of this user's is used instead,
   * and the link's target is left alone.
   * Mutants: the directory created with the default permissions, the link
   * followed.
   *
   * @throws Exception never
   */
  @Test
  public void theDirectoryIsThisUsersOnly() throws Exception {
    org.junit.Assume.assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    Path created = ChunkedUploadStore.ownDirectory(temporaryFolder.getRoot().toPath().resolve("uploads"));

    assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(created)));

    Path target = Files.createDirectories(temporaryFolder.getRoot().toPath().resolve("elsewhere"));
    Path leftInTarget = Files.writeString(target.resolve("upload-2.part"), "someone else's");
    Path link = Files.createSymbolicLink(temporaryFolder.getRoot().toPath().resolve("linked"), target);
    ChunkedUploadStore store = new ChunkedUploadStore(10, 2, Duration.ofMinutes(30), clock, link);
    ChunkedUpload upload = start(store, "john", 1);

    assertTrue(Files.exists(leftInTarget));
    assertFalse(upload.file().startsWith(link));
    assertFalse(upload.file().toRealPath().startsWith(target.toRealPath()));
    Files.deleteIfExists(upload.file());
    Files.deleteIfExists(upload.file().getParent());
  }


  /**
   * A directory removed while the server runs, by a temporary files cleaner
   * for instance, is prepared again at the next start.
   * Mutants: the directory prepared at the store's building only.
   *
   * @throws Exception never
   */
  @Test
  public void aDirectoryRemovedWhileRunningIsPreparedAgain() throws Exception {
    ChunkedUploadStore store = newStore();
    Path directory = temporaryFolder.getRoot().toPath().resolve("uploads");
    Files.delete(directory);

    ChunkedUpload upload = start(store, "john", 1);

    assertTrue(Files.isDirectory(directory));
    assertTrue(upload.file().startsWith(directory));
  }

  /**
   * An existing directory whose owner can't be compared to this process's
   * user, as for a container's user with no account entry, is not used, and
   * does not prevent the store from being built.
   * Mutants: the lookup failure thrown out of the building, or the directory
   * used anyway.
   *
   * @throws Exception never
   */
  @Test
  public void aUserThatCantBeResolvedDoesntPreventTheStore() throws Exception {
    Path directory = Files.createDirectories(temporaryFolder.getRoot().toPath().resolve("uploads"));
    String userName = System.getProperty("user.name");
    System.setProperty("user.name", "no-such-user-" + System.nanoTime());
    ChunkedUploadStore store;
    try {
      store = newStore();
    } finally {
      System.setProperty("user.name", userName);
    }
    ChunkedUpload upload = start(store, "john", 1);

    assertFalse(upload.file().startsWith(directory));
    Files.deleteIfExists(upload.file());
    Files.deleteIfExists(upload.file().getParent());
  }


  /**
   * A directory that can't be prepared at all, under a file for instance,
   * leaves the store built, and refuses each upload start rather than putting
   * the pieces elsewhere.
   * Mutants: the failure thrown out of the building, the refusal dropped.
   *
   * @throws Exception never
   */
  @Test
  public void aDirectoryThatCantBePreparedRefusesTheUploadsOnly() throws Exception {
    Path file = Files.writeString(temporaryFolder.getRoot().toPath().resolve("a-file"), "not a directory");
    ChunkedUploadStore store = new ChunkedUploadStore(10, 2, Duration.ofMinutes(30), clock, file.resolve("uploads"));

    assertTrue(assertThrows(IllegalStateException.class, () -> start(store, "john", 1)).getMessage().contains("can't be prepared"));
  }

}
