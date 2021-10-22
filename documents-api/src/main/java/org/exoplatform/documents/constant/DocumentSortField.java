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
