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
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(Include.NON_EMPTY)
public abstract class DocumentModel {

  @JsonProperty("document_id")
  private String    id;

  private String    name;

  private String    path;

  private String    url;

  @JsonProperty("parent_folder_id")
  private String    parentFolderId;

  @JsonProperty("created_date")
  private String    createdDate;

  @JsonProperty("created_username")
  private UserModel createdUser;

  /**
   * Whether the current user can change the document: for a file, edit it or
   * add a version; for a folder, create, upload or rename documents in it.
   * Null when the rights weren't computed for the listing that returned it.
   */
  @JsonProperty("can_edit")
  private Boolean   canEdit;

  /**
   * Whether the current user can delete the document, null when the rights
   * weren't computed for the listing that returned it.
   */
  @JsonProperty("can_delete")
  private Boolean   canDelete;

  /**
   * Builds the document's common fields, its rights left unknown.
   *
   * @param id the document id
   * @param name the document title
   * @param path the document path
   * @param url the document URL in the Documents app
   * @param parentFolderId the id of the folder holding it
   * @param createdDate the creation date, formatted
   * @param createdUser the creator
   */
  protected DocumentModel(String id,
                          String name,
                          String path,
                          String url,
                          String parentFolderId,
                          String createdDate,
                          UserModel createdUser) {
    this.id = id;
    this.name = name;
    this.path = path;
    this.url = url;
    this.parentFolderId = parentFolderId;
    this.createdDate = createdDate;
    this.createdUser = createdUser;
  }

}
