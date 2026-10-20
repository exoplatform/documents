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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A document's outline, as {@code get_document_outline} returns it.
 *
 * @param id the document id
 * @param name the document title
 * @param totalChars the length of the document's text
 * @param truncated true when the text was cut at the most characters read
 *          from one document, so the outline misses the headings past it
 * @param headings the headings, in the order they appear
 */
@JsonInclude(Include.NON_NULL)
public record DocumentOutlineModel(@JsonProperty("document_id") String id,
                                   String name,
                                   @JsonProperty("total_chars") long totalChars,
                                   boolean truncated,
                                   List<HeadingModel> headings) {

  /**
   * One heading.
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
  public record HeadingModel(int index,
                             int level,
                             String title,
                             long offset,
                             @JsonProperty("end_offset") long endOffset,
                             Integer page) {
  }
}
