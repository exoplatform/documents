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
import java.util.Map;

import org.exoplatform.documents.model.NodePermission;

import lombok.Data;

@Data
public class AbstractNodeEntity {

  public AbstractNodeEntity(boolean isFolder) {
    this.folder = isFolder;
  }

  private String                    id;

  private String                    name;

  private String                    description;

  private String                    datasource;

  private String                    parentFolderId;

  private IdentityEntity            ownerIdentity;

  private IdentityEntity            creatorIdentity;

  private IdentityEntity            modifierIdentity;

  private NodePermission            acl;

  private long                      createdDate;

  private long                      modifiedDate;

  private boolean                   folder;

  private NodeAuditTrailsEntity     auditTrails;

  private Map<String, List<Object>> metadatas;

}
