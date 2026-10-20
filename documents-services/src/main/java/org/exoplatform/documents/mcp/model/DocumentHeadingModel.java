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
 */
package org.exoplatform.documents.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One heading of a document's outline.
 *
 * @param index its number, to read its section with read_document_text
 * @param level its depth, 1 for a top-level heading
 * @param title its text
 * @param offset where its section starts in the text
 * @param endOffset where its section ends: the next heading of the same or a
 *          higher level, or the end of the text
 * @param page the PDF page or the slide it is on, null for a Word document
 */
@JsonInclude(Include.NON_NULL)
public record DocumentHeadingModel(int index,
                                   int level,
                                   String title,
                                   long offset,
                                   @JsonProperty("end_offset") long endOffset,
                                   Integer page) {
}
