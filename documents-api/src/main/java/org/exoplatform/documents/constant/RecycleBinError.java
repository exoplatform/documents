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
package org.exoplatform.documents.constant;

/**
 * The codes a refused action of the Recycle Bin carries as the message of its
 * exception, for a caller to tell the cases apart.
 */
public final class RecycleBinError {

  /** What every message code of the Recycle Bin starts with. */
  public static final String CODE_PREFIX              = "recycleBin.";

  /** No item of a bin carries the id given. */
  public static final String ITEM_NOT_FOUND           = "recycleBin.item.notFound";

  /** The caller can add content neither to the folder the item was deleted from nor, when the item may go there, to the root of its drive. */
  public static final String RESTORE_TARGET_FORBIDDEN = "recycleBin.restore.targetForbidden";

  /** Neither the folder the item was deleted from nor a drive to restore it to exists any more. */
  public static final String RESTORE_LOCATION_MISSING = "recycleBin.restore.locationMissing";

  /** The retention period asked for is outside the bounds a period may take. */
  public static final String RETENTION_INVALID        = "recycleBin.retention.invalid";

  /** The bin asked for is none of the caller's own, a space's with its id, or a drive's with the identity id of its owner. */
  public static final String SCOPE_INVALID            = "recycleBin.scope.invalid";

  /** The offset or the limit of the page asked for is negative. */
  public static final String PAGE_INVALID             = "recycleBin.page.invalid";

  /** A bulk action names no item, or one of its ids is blank. */
  public static final String ITEMS_MANDATORY          = "recycleBin.items.mandatory";

  /** A bulk action is started under no id. */
  public static final String ACTION_ID_MANDATORY      = "recycleBin.actionId.mandatory";

  private RecycleBinError() {
  }

}
