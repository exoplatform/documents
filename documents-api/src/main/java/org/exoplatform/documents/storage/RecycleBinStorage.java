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
package org.exoplatform.documents.storage;

import java.util.List;

import javax.jcr.RepositoryException;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.services.security.Identity;

/**
 * Reads, restores and removes the items of the Recycle Bins. It acts with
 * the rights of the system, and its callers decide who may see or act on the
 * bin of a drive: the only right it asks is, on a restore, the one to add
 * content to the folder the item goes to.
 */
public interface RecycleBinStorage {

  /**
   * Lists the items of the bin of a drive. The shortcuts trashed with the
   * document they point at and what a deleted folder holds are no items.
   *
   * @param filter the drive, the optional term, the sort and the page
   * @return the items of that page
   * @throws RepositoryException when the bin cannot be read
   */
  List<RecycleBinItem> getItems(RecycleBinFilter filter) throws RepositoryException;

  /**
   * Counts the items {@link #getItems(RecycleBinFilter)} lists for a filter,
   * whatever its page.
   *
   * @param filter the drive and the optional term
   * @return the number of items
   * @throws RepositoryException when the bin cannot be read
   */
  int countItems(RecycleBinFilter filter) throws RepositoryException;

  /**
   * Reads an item of a bin by its id.
   *
   * @param itemId id of the item in the bin, as {@link RecycleBinItem#getId()}
   *          gives it
   * @return the item, null when the id is not one, when no item carries it
   *         or when it names a shortcut trashed with the document it points at
   * @throws RepositoryException when the bin cannot be read
   */
  RecycleBinItem getItem(String itemId) throws RepositoryException;

  /**
   * Reads the item of a bin a deleted document is, by the id of the document.
   *
   * @param documentId id of the document or folder itself, as
   *          {@link RecycleBinItem#getDocumentId()} gives it
   * @return the item, null when no node carries that id, when the node is
   *         not in the trash, when it is held by a deleted folder or when it
   *         is a shortcut trashed with the document it points at
   * @throws RepositoryException when the node cannot be read
   */
  RecycleBinItem getItemOfDocument(String documentId) throws RepositoryException;

  /**
   * Reads the item of a bin at a path of the trash.
   *
   * @param path path of a node, as {@link RecycleBinItem#getPath()} gives it
   * @return the item, null when the path is not one of the trash, when no
   *         node is there, when the node is held by a deleted folder or when
   *         it is a shortcut trashed with the document it points at
   * @throws RepositoryException when the node cannot be read
   */
  RecycleBinItem getItemAt(String path) throws RepositoryException;

  /**
   * Removes an item of a bin for good, with the shortcuts pointing at it and
   * the ones trashed with it, in one save: all of them are removed or none.
   * The id may also name a shortcut trashed with a document, which
   * {@link #getItem(String)} does not read. Between the moment a caller
   * selects an item and this call, another actor may have restored or
   * removed it: the item is then no longer found. A shortcut pointing at the
   * item may itself be an item of a bin, of this drive or of another one: it
   * is removed too, and returned for the caller to count it out of its bin.
   *
   * @param itemId id of the item in the bin
   * @return the items of a bin removed with the one asked for, as they were
   *         before their removal; empty when there is none
   * @throws javax.jcr.PathNotFoundException when no node of the trash carries
   *           that id
   * @throws javax.jcr.InvalidItemStateException when another session removed
   *           or changed the item meanwhile
   * @throws RepositoryException when the item cannot be removed; nothing is
   *           removed then
   */
  List<RecycleBinItem> removeItem(String itemId) throws RepositoryException;

  /**
   * Restores an item of a bin, with the shortcuts trashed with it, in one
   * save: on a failure nothing has moved.
   * <p>
   * The item goes back to the folder it was deleted from, wherever that
   * folder was moved since, when it still exists in the drive of the item and
   * the caller can add content to it. Otherwise it goes to the root of its
   * drive, with the permissions it had when it was deleted, when the caller
   * can add content there. An item of no drive only goes back to the folder
   * it was deleted from. A name already taken there is not replaced: the
   * restored item is renamed as a moved document is.
   * <p>
   * A shortcut trashed with the item goes back to its own folder when it
   * still exists and its name is free there, and stays in the trash
   * otherwise. An item deleted from another workspace than the trash's is
   * copied back then removed from the trash, as two operations.
   *
   * @param itemId id of the item in the bin
   * @param caller who restores: the target must accept content from them;
   *          ignored when administrator is true
   * @param administrator whether the caller acts as an administrator of the
   *          platform on the bin of a drive: the target is then not checked
   * @return the item as it was in the bin
   * @throws ObjectNotFoundException when no item of a bin carries that id
   * @throws IllegalAccessException with
   *           {@link org.exoplatform.documents.constant.RecycleBinError#RESTORE_TARGET_FORBIDDEN}
   *           when the caller can add content to no target
   * @throws IllegalArgumentException with
   *           {@link org.exoplatform.documents.constant.RecycleBinError#RESTORE_LOCATION_MISSING}
   *           when no target exists any more
   * @throws RepositoryException when a target cannot be read or the item
   *           cannot be restored; it stays in the bin then
   */
  RecycleBinItem restoreItem(String itemId, Identity caller, boolean administrator) throws RepositoryException,
                                                                                    ObjectNotFoundException,
                                                                                    IllegalAccessException;

}
