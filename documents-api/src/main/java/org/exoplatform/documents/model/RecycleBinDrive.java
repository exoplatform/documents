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
package org.exoplatform.documents.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the Recycle Bin of a drive holds: the number of its items and the sum
 * of their sizes, kept per drive owner.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecycleBinDrive {

  /** Identity id of the user or the space the drive belongs to. */
  private long ownerId;

  /** Sum of the sizes of the items of the bin, in bytes. */
  private long itemsSize;

  /** Number of items of the bin. */
  private long itemsCount;

}
