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

import org.exoplatform.documents.constant.RecycleBinSortField;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Which items of a Recycle Bin to list: always the ones of one drive, with an
 * optional term their name contains, sorted and paged.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecycleBinFilter {

  /** Identity id of the user or the space the drive belongs to. */
  private long                ownerId;

  /** Term the name of an item contains, whatever its case; null for all. */
  private String              query;

  private RecycleBinSortField sortField = RecycleBinSortField.DELETED_DATE;

  private boolean             ascending;

  private int                 offset;

  /** Maximum number of items to return, 0 for no limit. */
  private int                 limit;

}
