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
package org.exoplatform.documents.model;

import java.util.List;

/**
 * A part of a document's text, read from where the caller asks, with what is
 * needed to read the rest: the length of the whole text and the document's
 * outline. When there is no text, its status says why.
 *
 * @param text the characters read, null when there is no text
 * @param offset the character the part starts at
 * @param totalChars the length of the whole text
 * @param truncated true when the whole text was cut at the most characters
 *          read from one document, so its end is missing
 * @param outline the document's headings, in the order they appear, empty when
 *          it has none
 * @param status {@link DocumentTextContent.Status#EXTRACTED} with a text, else
 *          why there is none
 */
public record DocumentTextPage(String text,
                               long offset,
                               long totalChars,
                               boolean truncated,
                               List<DocumentOutlineEntry> outline,
                               DocumentTextContent.Status status) {

  /**
   * Builds the answer for a document whose text could not be read.
   *
   * @param status why there is no text
   * @return a page holding no text
   */
  public static DocumentTextPage none(DocumentTextContent.Status status) {
    return new DocumentTextPage(null, 0, 0, false, List.of(), status);
  }

  /**
   * Whether the document's text was read, even when the part asked lies past
   * its end.
   *
   * @return true when the status is {@link DocumentTextContent.Status#EXTRACTED}
   */
  public boolean hasText() {
    return status == DocumentTextContent.Status.EXTRACTED;
  }
}
