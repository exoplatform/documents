/*
 * Copyright (C) 2003-2021 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.documents.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentFile {

  private String     id;

  private String     name;

  private String     description;

  private String     datasource;

  private String     driveId;

  private String     folderId;

  private String     parentFileId;

  private long       ownerId;

  private long       creatorId;

  private long       createdDate;

  private long       modifiedDate;

  private long       size;

  private String     mimeType;

  private Permission acl;

}
