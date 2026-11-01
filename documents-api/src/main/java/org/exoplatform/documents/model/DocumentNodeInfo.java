/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.model;

import java.util.List;

import org.exoplatform.documents.constant.DocumentNodeType;

/**
 * Where a node is and what it is, read whoever may see it.
 *
 * @param id the node's identifier
 * @param type what the node is
 * @param targetId for a shortcut, the identifier at the end of its chain of
 *          shortcuts: the last shortcut of a chain that loops, null when the
 *          chain is broken; the node's own identifier otherwise
 * @param targetType the type of the node {@code targetId} names, null when the
 *          chain is broken
 * @param spaceId the id of the space whose library holds the node, 0 when no
 *          space library holds it (a personal drive, the trash)
 * @param ancestorFolderIds the identifiers of the folders holding the node,
 *          from its parent up to the library root excluded, nearest first;
 *          empty outside a space library
 * @param libraryRoot true for a space library's root folder itself
 */
public record DocumentNodeInfo(String id,
                               DocumentNodeType type,
                               String targetId,
                               DocumentNodeType targetType,
                               long spaceId,
                               List<String> ancestorFolderIds,
                               boolean libraryRoot) {

  /**
   * Copies the folder list, so the record stays immutable.
   */
  public DocumentNodeInfo {
    ancestorFolderIds = ancestorFolderIds == null ? List.of() : List.copyOf(ancestorFolderIds);
  }
}
