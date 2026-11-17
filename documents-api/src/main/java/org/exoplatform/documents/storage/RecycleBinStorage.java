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

import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;

/**
 * Reads and removes the items of the Recycle Bins. It checks no permission:
 * it acts with the rights of the system, and its callers decide who may see
 * or act on the bin of a drive.
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

}
