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

/**
 * What a bulk action does to one of its items, named by its id. It is called
 * once per item, on the thread of the bulk action, where the current user is
 * the one the action was started for.
 */
@FunctionalInterface
public interface BulkItemAction {

  /**
   * @param itemId id of the item, as the action was given it
   * @return false when the item is no longer there to act on: it is left out
   *         of the items treated, and is no failure
   * @throws Exception when the item could not be treated: the action goes on
   *           with the others and ends with errors
   */
  boolean apply(String itemId) throws Exception; // NOSONAR whatever the operation throws is a failure of the item

  /**
   * The order the items are acted on in, asked once on the thread of the bulk
   * action before the first one: the order given, unless an item depends on
   * another the action treats.
   *
   * @param itemIds ids of the items, as the action was given them
   * @return the same ids, in the order to act on them
   */
  default List<String> order(List<String> itemIds) {
    return itemIds;
  }

}
