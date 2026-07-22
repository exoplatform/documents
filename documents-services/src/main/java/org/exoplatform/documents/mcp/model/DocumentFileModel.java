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
public class DocumentFileModel extends DocumentModel {

  private long      size;

  @JsonProperty("mime_type")
  private String    mimeType;

  private String    transcription;

  private String    description;

  @JsonProperty("updated_date")
  private String    updatedDate;

  @JsonProperty("last_updater_user")
  private UserModel lastUpdaterUser;

  public DocumentFileModel(String id, // NOSONAR
                           String name,
                           String path,
                           String url,
                           String parentFolderId,
                           String createdDate,
                           UserModel creatorUser,
                           long size,
                           String mimeType,
                           String transcription,
                           String description,
                           String updatedDate,
                           UserModel lastUpdaterUser) {
    super(id, name, path, url, parentFolderId, createdDate, creatorUser);
    this.size = size;
    this.mimeType = mimeType;
    this.transcription = transcription;
    this.description = description;
    this.updatedDate = updatedDate;
    this.lastUpdaterUser = lastUpdaterUser;
  }

}
