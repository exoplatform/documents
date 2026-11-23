/*
 * Copyright (C) 2021 eXo Platform SAS
 *  
 *  This program is free software: you can redistribute it and/or modify
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
package org.exoplatform.documents.rest.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodePermissionEntity {

  private boolean canAccess;

  private boolean canEdit;

  private boolean canDelete;

  private boolean allMembersCanEdit;

  private String visibilityChoice;

  private List<PermissionEntryEntity> collaborators;

  /**
   * Whether the document's space lets the user change its access list.
   */
  private boolean canManageAccess = true;

  /**
   * Whether the document's space keeps its documents inside: the UI disables
   * every share outside the space, everyone and the public link included.
   */
  private boolean keptInsideSpace;

  /**
   * The pretty name of the space whose drive holds the document, a link's target
   * included: the UI lists that space's members as the ones a document kept
   * inside can still be shared with. Null when the document is in no space, or
   * when its space could not be resolved.
   */
  private String spacePrettyName;

  public NodePermissionEntity(boolean canAccess,
                              boolean canEdit,
                              boolean canDelete,
                              boolean allMembersCanEdit,
                              String visibilityChoice,
                              List<PermissionEntryEntity> collaborators) {
    this(canAccess, canEdit, canDelete, allMembersCanEdit, visibilityChoice, collaborators, true, false, null);
  }

}
