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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Who has access to a folder or a file, as {@code get_document_access}
 * returns it: what the Documents « Manage access » drawer shows, names only.
 */
@Data
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@NoArgsConstructor
public class DocumentAccessModel {

  @JsonProperty("document_id")
  private String                          id;

  private String                          name;

  @JsonProperty("is_folder")
  private boolean                         folder;

  /**
   * Who can view it: {@code specific_collaborators}, {@code all_space_members},
   * {@code anyone} or {@code collaborators_and_public_link}.
   */
  private String                          visibility;

  /** Whether every member of the space can edit it, as the drawer's switch shows. */
  @JsonProperty("all_space_members_can_edit")
  private boolean                         allSpaceMembersCanEdit;

  /** The name of its owner, the user who created it. */
  private String                          owner;

  /** Its collaborators, one per user, space or group. */
  private List<DocumentCollaboratorModel> collaborators;

}
