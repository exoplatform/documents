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

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractNode {

  private String         id;

  private String         name;

  private String         path;

  private String         description;

  private String         datasource;

  private long           ownerId;

  private String         parentFolderId;

  private long           creatorId;

  private long           createdDate;

  private long           modifierId;

  private long           modifiedDate;

  private NodePermission acl;

  private String         sourceID;

  private boolean        cloudDriveFolder;

  private boolean        cloudDriveFile;

  public  boolean isFolder(){
    return this instanceof FolderNode;
  }
}
