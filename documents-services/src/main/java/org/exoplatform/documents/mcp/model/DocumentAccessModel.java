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
 * Where Documents offers the user no « Manage access » on it, the list is not
 * shown: {@code access_shown} is false, {@code reason} says why, and
 * {@code your_rights} gives the user's own rights, with no field of the list.
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

  /** Whether the list is shown: false where Documents offers no « Manage access » on it. */
  @JsonProperty("access_shown")
  private boolean                         accessShown;

  /** Why the list is not shown, null when it is. */
  private String                          reason;

  /** The user's own rights, given where the list is not shown, null when it is. */
  @JsonProperty("your_rights")
  private DocumentPermissionsModel        yourRights;

  /**
   * Who can view it: {@code specific_collaborators}, {@code all_space_members},
   * {@code anyone} or {@code collaborators_and_public_link}.
   */
  private String                          visibility;

  /** Whether every member of the space can edit it, as the drawer's switch shows. */
  @JsonProperty("all_space_members_can_edit")
  private Boolean                         allSpaceMembersCanEdit;

  /** The name of its owner, the user who created it. */
  private String                          owner;

  /** Its collaborators, one per user, space or group. */
  private List<DocumentCollaboratorModel> collaborators;

  /**
   * @param id the id the user gave
   * @param name the document's name
   * @param folder whether it is a folder
   * @param visibility who can view it
   * @param allSpaceMembersCanEdit whether every member of its space can edit it
   * @param owner its owner's name
   * @param collaborators its collaborators, by name only
   * @return the list as the drawer shows it
   */
  public static DocumentAccessModel shown(String id,
                                          String name,
                                          boolean folder,
                                          String visibility,
                                          boolean allSpaceMembersCanEdit,
                                          String owner,
                                          List<DocumentCollaboratorModel> collaborators) {
    return new DocumentAccessModel(id, name, folder, true, null, null, visibility, allSpaceMembersCanEdit, owner, collaborators);
  }

  /**
   * @param id the id the user gave
   * @param name the document's name
   * @param folder whether it is a folder
   * @param reason why the list is not shown
   * @param yourRights the user's own rights on it
   * @return no field of the list: the reason and the user's own rights only
   */
  public static DocumentAccessModel notShown(String id,
                                             String name,
                                             boolean folder,
                                             String reason,
                                             DocumentPermissionsModel yourRights) {
    return new DocumentAccessModel(id, name, folder, false, reason, yourRights, null, null, null, null);
  }

}
