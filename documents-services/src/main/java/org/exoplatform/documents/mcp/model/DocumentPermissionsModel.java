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

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the current user may do on a folder or a file, as
 * {@code get_document_permissions} returns it.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentPermissionsModel {

  @JsonProperty("document_id")
  private String  id;

  private String  name;

  @JsonProperty("is_folder")
  private boolean folder;

  /** Always true: a document the user can't read is refused instead. */
  @JsonProperty("can_read")
  private boolean canRead;

  /**
   * For a file, whether the user can edit it or add a version; for a folder,
   * whether the user can create, upload or rename documents in it.
   */
  @JsonProperty("can_edit")
  private boolean canEdit;

  @JsonProperty("can_delete")
  private boolean canDelete;

}
