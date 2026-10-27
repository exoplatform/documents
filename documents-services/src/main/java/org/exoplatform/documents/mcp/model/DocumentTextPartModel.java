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
 * A part of a document's text, as {@code read_document_text} returns it, with
 * where to read the next part.
 *
 * @param id the document id
 * @param name the document title
 * @param section the heading whose section is read, null when reading by offset
 * @param offset the character the part starts at
 * @param endOffset the character after the part's last one
 * @param totalChars the length of the whole text
 * @param nextOffset where the next part starts, null when this one ends the
 *          text or the section
 * @param truncated true when the whole text was cut at the most characters
 *          read from one document
 * @param text the part's text
 */
@JsonInclude(Include.NON_NULL)
public record DocumentTextPartModel(@JsonProperty("document_id") String id,
                                    String name,
                                    DocumentHeadingModel section,
                                    long offset,
                                    @JsonProperty("end_offset") long endOffset,
                                    @JsonProperty("total_chars") long totalChars,
                                    @JsonProperty("next_offset") Long nextOffset,
                                    boolean truncated,
                                    String text) {
}
