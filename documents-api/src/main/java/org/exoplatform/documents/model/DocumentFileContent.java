/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.model;

import java.io.InputStream;
import java.util.Date;

import io.meeds.portal.thumbnail.model.FileContent;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * The content of a document file together with its size in bytes, so that a
 * part of it can be served on its own.
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class DocumentFileContent extends FileContent {

  /** The size of the content, in bytes. */
  private final long size;

  /**
   * Builds the content of a document file.
   *
   * @param id the document's id
   * @param name the document's name
   * @param mimeType the content's media type
   * @param content the content
   * @param updatedDate when the document was last modified
   * @param size the size of the content, in bytes
   */
  public DocumentFileContent(String id, String name, String mimeType, InputStream content, Date updatedDate, long size) {
    super(id, name, mimeType, content, updatedDate);
    this.size = size;
  }

}
