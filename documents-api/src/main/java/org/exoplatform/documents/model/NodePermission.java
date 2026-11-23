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
package org.exoplatform.documents.model;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodePermission {

  private boolean canAccess;

  private boolean canEdit;

  private boolean canDelete;

  private boolean isPublic;

  private List<PermissionEntry> permissions;

  private Map<Long,String> toShare;

  private Map<Long,String> toNotify;

  private Map<Long,String> toUnShare;

  /**
   * Whether the document's space lets the user change its access list: false
   * when its "Limit Access Management" setting refuses the user, or when the
   * user cannot edit the document.
   */
  private boolean canManageAccess = true;

  /**
   * Whether the document's space keeps its documents inside: its "Keep
   * documents inside the space" setting refuses every share outside the space,
   * everyone and public links included.
   */
  private boolean keptInsideSpace;

  /**
   * The pretty name of the space whose drive holds the document, a link's target
   * included: the space {@link #keptInsideSpace} was decided on. Null when the
   * document is in no space, or when its space could not be resolved.
   */
  private String spacePrettyName;

  public NodePermission(boolean canAccess,
                        boolean canEdit,
                        boolean canDelete,
                        boolean isPublic,
                        List<PermissionEntry> permissions,
                        Map<Long, String> toShare,
                        Map<Long, String> toNotify,
                        Map<Long, String> toUnShare) {
    this(canAccess, canEdit, canDelete, isPublic, permissions, toShare, toNotify, toUnShare, true, false, null);
  }

}
