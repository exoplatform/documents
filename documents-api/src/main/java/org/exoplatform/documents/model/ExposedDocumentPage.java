/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
*/
package org.exoplatform.documents.model;

import java.util.List;

/**
 * One page of a space's exposure list
 *
 * @param documents the documents of the page, sorted by name then identifier
 * @param size the count of documents in the whole list
 */
public record ExposedDocumentPage(List<ExposedDocument> documents, int size) {

  public ExposedDocumentPage {
    documents = documents == null ? List.of() : List.copyOf(documents);
  }

}
