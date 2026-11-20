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
package org.exoplatform.documents.service;

import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.RecycleBinDrive;
import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.model.RecycleBinScope;

import io.meeds.portal.thumbnail.model.FileContent;

/**
 * The Recycle Bins of the drives: who may see the bin of a drive and act on
 * its items.
 * <p>
 * Every method acting for someone takes that user's name and refuses an
 * absent or anonymous one before reading anything. A bin is named by a
 * {@link RecycleBinScope}: a user holds the bin of their personal drive, the
 * managers and the publishers of a space hold the bin of the space, and an
 * administrator of the platform may act on the bin of any drive while the
 * platform allows it, without ever reading a document of it.
 * <p>
 * A refusal says, in this order, what does not exist
 * ({@link ObjectNotFoundException}), what the caller may not do
 * ({@link IllegalAccessException}), then what is wrong in the request
 * ({@link IllegalArgumentException}, whose message is a code of
 * {@link org.exoplatform.documents.constant.RecycleBinError} when it has
 * one). A repository that cannot answer is an {@link IllegalStateException},
 * and the item is then left where it was.
 */
public interface RecycleBinService {

  /**
   * Lists the items of a bin.
   *
   * @param scope the bin
   * @param filter the optional term, the sort and the page; its owner is
   *          ignored, the scope names the drive
   * @param username who asks
   * @return the items of that page; in the bin of a drive opened by an
   *         administrator of the platform they carry no document id
   * @throws ObjectNotFoundException when the space or the drive's owner does
   *           not exist
   * @throws IllegalAccessException when the caller does not hold that bin
   */
  List<RecycleBinItem> getItems(RecycleBinScope scope, RecycleBinFilter filter, String username) throws ObjectNotFoundException,
                                                                                                 IllegalAccessException;

  /**
   * Counts the items {@link #getItems(RecycleBinScope, RecycleBinFilter, String)}
   * lists, whatever the page.
   *
   * @param scope the bin
   * @param filter the optional term
   * @param username who asks
   * @return the number of items
   * @throws ObjectNotFoundException when the space or the drive's owner does
   *           not exist
   * @throws IllegalAccessException when the caller does not hold that bin
   */
  int countItems(RecycleBinScope scope, RecycleBinFilter filter, String username) throws ObjectNotFoundException,
                                                                                  IllegalAccessException;

  /**
   * Restores an item of a bin, as
   * {@link org.exoplatform.documents.storage.RecycleBinStorage#restoreItem}
   * says: to the folder it was deleted from or to the root of its drive,
   * where the caller can add content. An administrator of the platform acting
   * on the bin of a drive is not asked for that right.
   *
   * @param itemId id of the item in the bin
   * @param scope the bin the caller acts on
   * @param username who restores
   * @throws ObjectNotFoundException when the space or the drive's owner does
   *           not exist, or no item of a bin carries that id: another actor
   *           may have restored or removed it meanwhile
   * @throws IllegalAccessException when the caller does not hold that bin,
   *           when the item is not one of that bin, or, with the code
   *           RESTORE_TARGET_FORBIDDEN, when the caller can add content to no
   *           target
   * @throws IllegalArgumentException with the code RESTORE_LOCATION_MISSING
   *           when no target exists any more
   */
  void restoreItem(String itemId, RecycleBinScope scope, String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Removes an item of a bin for good, with the shortcuts pointing at it.
   *
   * @param itemId id of the item in the bin
   * @param scope the bin the caller acts on
   * @param username who deletes
   * @throws ObjectNotFoundException when the space or the drive's owner does
   *           not exist, or no item of a bin carries that id: another actor
   *           may have restored or removed it meanwhile
   * @throws IllegalAccessException when the caller does not hold that bin or
   *           the item is not one of that bin
   */
  void deleteItem(String itemId, RecycleBinScope scope, String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Tells whether a user may read the content of an item: the item is one of
   * the user's own bin, or of the bin of a space the user manages or
   * publishes in. Being an administrator of the platform gives no right to
   * read it.
   *
   * @param itemId id of the item in the bin
   * @param username who wants to read
   * @return the item
   * @throws ObjectNotFoundException when no item of a bin carries that id
   * @throws IllegalAccessException when the user may not read it
   */
  RecycleBinItem checkCanView(String itemId, String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * The same rule as {@link #checkCanView(String, String)}, for a caller that
   * knows the document and not its id in the bin.
   *
   * @param documentId id of the deleted document itself
   * @param username who wants to read
   * @return the item
   * @throws ObjectNotFoundException when the document is no item of a bin, or
   *           is a folder or a shortcut, which have no View action in a bin
   * @throws IllegalAccessException when the user may not read it
   */
  RecycleBinItem checkCanViewDocument(String documentId, String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Restores a deleted document for the one who deleted it, or for a holder
   * of the bin it is in, where they can add content. It is the only way to an
   * item deleted from no personal or space drive, which goes back to the
   * folder it was deleted from and nowhere else. It lists nothing: the caller
   * names the document.
   *
   * @param documentId id of the deleted document itself
   * @param username who restores
   * @throws ObjectNotFoundException when the document is no item of a bin
   * @throws IllegalAccessException when the user neither deleted it nor
   *           holds its bin, or, with the code RESTORE_TARGET_FORBIDDEN, can
   *           add content to no target
   * @throws IllegalArgumentException with the code RESTORE_LOCATION_MISSING
   *           when no target exists any more
   */
  void undoDelete(String documentId, String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Lists the drives whose bin holds items, largest bin first, for an
   * administrator of the platform. The totals are the ones kept at each
   * deletion, restore and removal, and corrected by the daily run.
   *
   * @param offset index of the first drive
   * @param limit maximum number of drives
   * @param username who asks
   * @return the drives of that page
   * @throws IllegalAccessException when the caller is no administrator of the
   *           platform
   */
  List<RecycleBinDrive> getDrives(long offset, int limit, String username) throws IllegalAccessException;

  /**
   * @param username who asks
   * @return the number of drives {@link #getDrives(long, int, String)} lists
   * @throws IllegalAccessException when the caller is no administrator of the
   *           platform
   */
  long countDrives(String username) throws IllegalAccessException;

  /**
   * @return whether the platform lets its administrators act on the bin of
   *         any drive
   */
  boolean isAdministratorAccessEnabled();

  /**
   * Counts an item that has just been deleted in the bin of its drive. It
   * acts for no caller: the storage reports each deletion once it is saved.
   * The caller holds no transaction, the count being several writes that are
   * not one.
   *
   * @param ownerId identity id of the drive's owner
   * @param size size of the item, in bytes
   */
  void itemTrashed(long ownerId, long size);

  /**
   * Reads the content of a deleted document for who may view it, as
   * {@link #checkCanView} says: the holder of the bin it is in, and never an
   * administrator acting on the bin of a drive.
   *
   * @param itemId id of the item in the bin
   * @param username the caller
   * @return its name, its type and its content
   * @throws ObjectNotFoundException when no item carries the id, or when the
   *           item has no content of its own, as a folder
   * @throws IllegalAccessException when the caller does not hold the bin of
   *           the item
   */
  FileContent getItemContent(String itemId, String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Restores several items of a bin, as a bulk action: it returns once the
   * action is started, and the caller is told when it is over, on the channel
   * the bulk actions report on, with the ids of the items restored. Each item
   * is restored at its turn as {@link #restoreItem} does, the bin and what the
   * caller may do being checked again then: an item that is no longer in the
   * bin is left out, and one that is refused is a failure of that item, the
   * others being restored.
   *
   * @param actionId the id the action reports under and is cancelled by
   * @param itemIds ids of the items in the bin
   * @param scope the bin
   * @param username the caller
   * @throws ObjectNotFoundException when the drive does not exist
   * @throws IllegalAccessException when the caller does not hold the bin
   * @throws IllegalArgumentException when no item or no action id is given
   */
  void restoreItems(String actionId, List<String> itemIds, RecycleBinScope scope, String username) throws ObjectNotFoundException,
                                                                                                   IllegalAccessException;

  /**
   * Removes for good several items of a bin, as a bulk action, the way
   * {@link #restoreItems} restores them, each as {@link #deleteItem} does. An
   * item that left with another one of the action, as a shortcut does with
   * the document it points at, is left out.
   *
   * @param actionId the id the action reports under and is cancelled by
   * @param itemIds ids of the items in the bin
   * @param scope the bin
   * @param username the caller
   * @throws ObjectNotFoundException when the drive does not exist
   * @throws IllegalAccessException when the caller does not hold the bin
   * @throws IllegalArgumentException when no item or no action id is given
   */
  void deleteItems(String actionId, List<String> itemIds, RecycleBinScope scope, String username) throws ObjectNotFoundException,
                                                                                                  IllegalAccessException;

  /**
   * Empties a bin: removes for good, as {@link #deleteItems} does, the items
   * it holds when the call is made. An item that joins the bin afterwards
   * stays.
   *
   * @param actionId the id the action reports under and is cancelled by
   * @param scope the bin
   * @param username the caller
   * @throws ObjectNotFoundException when the drive does not exist
   * @throws IllegalAccessException when the caller does not hold the bin
   * @throws IllegalArgumentException when no action id is given
   */
  void emptyBin(String actionId, RecycleBinScope scope, String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * @return the number of days an item stays in a Recycle Bin before it is
   *         removed for good, the same for every drive
   */
  long getRetentionDays();

  /**
   * Changes the number of days an item stays in a Recycle Bin. A shorter
   * period makes items due at once: they are removed as
   * {@link #purgeExpired(String)} says, for the caller.
   *
   * @param retentionDays the new period, in days
   * @param username the caller
   * @throws IllegalAccessException when the caller is no administrator of
   *           the platform
   * @throws IllegalArgumentException with the code
   *           {@link org.exoplatform.documents.constant.RecycleBinError#RETENTION_INVALID}
   *           when the period is outside its bounds
   */
  void setRetentionDays(long retentionDays, String username) throws IllegalAccessException;

  /**
   * The daily run. It gives their facts to the items of the trash that lack
   * them, counts the bins again from the trash, then removes for good what
   * has stayed longer than the retention period. It returns at once: the run
   * goes on in the background, one at a time on a server, and a call made
   * while one is going on is served by a further round of that one.
   * <p>
   * Between the moment the items are selected and the turn of each, an item
   * may be restored, deleted again or given more time by a longer period:
   * each is read again at its turn, with the period as it is then, and left
   * when it is no longer due. An item another actor removes meanwhile is
   * skipped, and one that cannot be removed is reported and left to the next
   * run.
   */
  void purgeExpired();

  /**
   * Removes for good what has stayed longer than the retention period, as
   * {@link #purgeExpired()} does but for the counts, which it leaves as they
   * are. The requester is told once it is over, on the channel the bulk
   * actions report on, how many items left the bins, or that it failed.
   *
   * @param requester the user to tell
   */
  void purgeExpired(String requester);

}
