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

  /** No item of a bin carries the id given. */
  public static final String ITEM_NOT_FOUND           = "recycleBin.item.notFound";

  /** The caller can add content neither to the folder the item was deleted from nor to the root of its drive. */
  public static final String RESTORE_TARGET_FORBIDDEN = "recycleBin.restore.targetForbidden";

  /** Neither the folder the item was deleted from nor a drive to restore it to exists any more. */
  public static final String RESTORE_LOCATION_MISSING = "recycleBin.restore.locationMissing";

  private RecycleBinError() {
  }

}
