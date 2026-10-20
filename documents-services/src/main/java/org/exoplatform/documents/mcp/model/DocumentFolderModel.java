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
package org.exoplatform.documents.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.meeds.mcp.server.tool.model.UserModel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(Include.NON_EMPTY)
public class DocumentFolderModel extends DocumentModel {

  /** The date the folder was last changed, formatted. */
  @JsonProperty("updated_date")
  private String updatedDate;

  /**
   * Builds a folder, its rights and its last change left unknown.
   *
   * @param id the folder id
   * @param name the folder title
   * @param path the folder path
   * @param url the folder URL in the Documents app
   * @param parentFolderId the id of the folder holding it
   * @param createdDate the creation date, formatted
   * @param creatorUser the creator
   */
  public DocumentFolderModel(String id,
                             String name,
                             String path,
                             String url,
                             String parentFolderId,
                             String createdDate,
                             UserModel creatorUser) {
    super(id, name, path, url, parentFolderId, createdDate, creatorUser);
  }

}
