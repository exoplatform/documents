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

import org.exoplatform.documents.constant.DocumentSortField;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class DocumentNodeFilter {

  private String            query;

  private boolean           extendedSearch;

  private DocumentSortField sortField;

  private boolean           ascending;

  private Boolean           favorites;

  private String            userId;

  private boolean           includeHiddenFiles;

  private String            fileTypes;

  private Long              afterDate;

  private Long              beforDate;

  private Long              minSize;

  private Long              maxSize;

}
