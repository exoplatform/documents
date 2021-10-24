/*
 * Copyright (C) 2021 eXo Platform SAS
 *  
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.constant;

import org.apache.commons.lang3.StringUtils;

public enum DocumentSortField {
  NAME,
  MODIFIED_DATE,
  CREATED_DATE;

  public static DocumentSortField getFromAlias(String alias) {
    if (StringUtils.isBlank(alias)) {
      return null;
    }
    switch (alias) {
      case "name":
      case "title":
        return NAME;
      case "modified":
      case "modifiedDate":
      case "lastUpdated":
        return MODIFIED_DATE;
      case "created":
      case "createdDate":
        return CREATED_DATE;
      default:
        return null;
    }
  }
}
