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

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A document or a folder of a Recycle Bin, with what was recorded when it
 * was deleted.
 */
@Data
@NoArgsConstructor
public class RecycleBinItem {

  /**
   * Id of the item in the bin, given when it was deleted. It is not the id of
   * the document.
   */
  private String  id;

  /** Id of the deleted document or folder itself. */
  private String  documentId;

  /** Path of the item in the trash. */
  private String  path;

  /**
   * Identity id of the user or the space whose drive the item was deleted
   * from, null when it belongs to no drive.
   */
  private Long    ownerId;

  private String  name;

  private boolean folder;

  /** Whether the item is a shortcut to another document. */
  private boolean shortcut;

  /**
   * Whether the item is a shortcut to a folder: it is shown with a folder's
   * icon, and is no folder of its own.
   */
  private boolean targetFolder;

  private String  mimeType;

  /** Size in bytes: of a file its content, of a folder the files it holds. */
  private long    size;

  /** When the item was deleted, in milliseconds; null when not recorded. */
  private Long    deletedDate;

  /** Username of who deleted the item, null when not recorded. */
  private String  deletedBy;

  /** Path the item was deleted from. */
  private String  restorePath;

  private String  restoreWorkspace;

  /** Id of the folder the item was deleted from, null when not recorded. */
  private String  restoreParentId;

  /**
   * The names the documents show of the folders of the path the item was
   * deleted from, one for each of them, from the root of the repository down
   * to the folder that held it: its title where it is, or where the trash
   * keeps it, else the name in the path. Null when not read.
   */
  private List<String> restoreFolderTitles;

}
