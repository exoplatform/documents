/*
 * Copyright (C) 2022 eXo Platform SAS
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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileVersion implements Comparable<FileVersion> {

  private String  id;

  private String  frozenId;

  private String  originId;

  private int     versionNumber;

  private String  title;

  private String  summary;

  private String  author;

  private String  authorFullName;

  private Date    createdDate;

  private boolean isCurrent;

  private long    size;

  @Override
  public int compareTo(FileVersion fileVersion) {
    return createdDate.compareTo(fileVersion.createdDate);
  }
}
