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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An item of a Recycle Bin, as a screen lists it.
 */
@Data
@NoArgsConstructor
public class RecycleBinItemEntity {

  /** Id of the item in the bin: what an action on the item names. */
  private String           id;

  private String           name;

  private boolean          folder;

  private String           mimeType;

  private long             size;

  private Long             deletedDate;

  /** Who deleted the item, null when it was not recorded. */
  private User             deletedBy;

  /** Where the item was deleted from. */
  private Location         originalLocation;

  /** Whether the caller may open the item from this bin. */
  private boolean          canView;

  /** Id of the deleted document itself, given only to who may open it. */
  private String           documentId;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class User {

    private String username;

    private String fullName;

  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Location {

    /** Path of the folder, from the root of its drive. */
    private String path;

  }

}
