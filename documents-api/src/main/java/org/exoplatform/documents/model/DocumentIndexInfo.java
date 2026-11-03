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

/**
 * What a search index built outside Documents needs to know about a file,
 * read whoever may see it: its metadata, where it lies and who may read it.
 *
 * @param id the file's identifier
 * @param title the file's title, its node name when it has none
 * @param creator the user name of the file's creator, null when not recorded
 * @param owner the owner of the file's access control list, null when it has
 *          none
 * @param createdDate the creation date in milliseconds, 0 when not recorded
 * @param modifiedDate the last modification date in milliseconds, the creation
 *          date when not recorded
 * @param description the file's description, null when it has none
 * @param url the Documents link opening the file, null until the service sets
 *          it
 * @param spaceId the id of the space whose library holds the file, 0 when no
 *          space library holds it
 * @param ancestorFolderIds the identifiers of the folders holding the file,
 *          from its parent up to the library root excluded, nearest first,
 *          as {@link DocumentNodeInfo#ancestorFolderIds()} gives them
 * @param readIdentities the identities that may read the file, as its access
 *          control list names them: its owner, then every entry holding the
 *          read permission (a user name, {@code any}, or a membership
 *          {@code type:/group} where the type may be {@code *}), each once
 */
public record DocumentIndexInfo(String id,
                                String title,
                                String creator,
                                String owner,
                                long createdDate,
                                long modifiedDate,
                                String description,
                                String url,
                                long spaceId,
                                List<String> ancestorFolderIds,
                                List<String> readIdentities) {

  /**
   * Copies the lists, so the record stays immutable.
   */
  public DocumentIndexInfo {
    ancestorFolderIds = ancestorFolderIds == null ? List.of() : List.copyOf(ancestorFolderIds);
    readIdentities = readIdentities == null ? List.of() : List.copyOf(readIdentities);
  }

  /**
   * @param fileUrl the Documents link opening the file
   * @return a copy of this description carrying that link
   */
  public DocumentIndexInfo withUrl(String fileUrl) {
    return new DocumentIndexInfo(id,
                                 title,
                                 creator,
                                 owner,
                                 createdDate,
                                 modifiedDate,
                                 description,
                                 fileUrl,
                                 spaceId,
                                 ancestorFolderIds,
                                 readIdentities);
  }
}
