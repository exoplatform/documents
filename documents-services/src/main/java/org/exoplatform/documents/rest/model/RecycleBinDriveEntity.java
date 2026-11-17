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
package org.exoplatform.documents.rest.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A drive whose Recycle Bin holds items, as the Bin Overview lists it.
 */
@Data
@NoArgsConstructor
public class RecycleBinDriveEntity {

  /** Identity id of the owner of the drive: what opens its bin. */
  private long    ownerIdentityId;

  /** USER or SPACE, null when the owner is no longer known. */
  private String  type;

  /** Display name of the owner, the last one known when it was deleted. */
  private String  name;

  private String  avatarUrl;

  /** Sum of the sizes of the items of the bin, in bytes. */
  private long    size;

  private long    itemsCount;

  /** Whether the owner of the drive was deleted. */
  private boolean deleted;

}
