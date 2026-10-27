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
package org.exoplatform.documents.storage.jcr.model;

import java.nio.file.Path;
import java.util.List;

import org.exoplatform.documents.model.DocumentOutlineEntry;
import org.exoplatform.documents.model.DocumentTextContent.Status;

/**
 * The text of one version of a file, as extracted to be read part by part.
 *
 * @param file the cache file holding the text as UTF-16BE, null without text
 * @param totalChars the length of the text
 * @param truncated true when the text was cut at the most characters read
 * @param outline the file's headings
 * @param status {@link Status#EXTRACTED}, else why there is no text
 */
public record PagedTextExtraction(Path file, long totalChars, boolean truncated, List<DocumentOutlineEntry> outline, Status status) {

  /**
   * Builds the extraction of a file that gave no text.
   *
   * @param status why
   * @return the extraction
   */
  public static PagedTextExtraction none(Status status) {
    return new PagedTextExtraction(null, 0, false, List.of(), status);
  }
}
