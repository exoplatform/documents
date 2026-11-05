/*
 * Copyright (C) 2026 eXo Platform SAS
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

/**
 * What the public link page shows of a document that has a public access
 * link: the document's name and mime type, and whether the link asks for a
 * password or has expired. It carries no password and no content.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicDocumentInfo {

  private String  documentName;

  private String  mimeType;

  private boolean accessLocked;

  private boolean accessExpired;
}
