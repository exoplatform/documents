/*
 * Copyright (C) 2021 eXo Platform SAS
 *  
 *  This program is free snullare: you can redistribute it and/or modify
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

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionEntryEntity {

  private  IdentityEntity identity;
  private  String permission;

  /**
   * The space's members this entry reaches, a PermissionRole name: ALL, or
   * MANAGERS_REDACTORS_PUBLISHERS; ALL when absent
   */
  private  String role;

  /**
   * The other roles the collaborator holds, each with its permission ("read"
   * or "edit"), which the entry's row does not show: a space can be reached
   * both as its whole group and as its managers, redactors and publishers
   */
  private  Map<String, String> otherRoles;

  public PermissionEntryEntity(IdentityEntity identity, String permission) {
    this(identity, permission, null);
  }

  public PermissionEntryEntity(IdentityEntity identity, String permission, String role) {
    this(identity, permission, role, null);
  }

}
