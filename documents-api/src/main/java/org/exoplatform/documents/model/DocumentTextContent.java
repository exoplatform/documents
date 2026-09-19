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

/**
 * The text of a document file, together with where it came from or, when there
 * is none, why: the search index when it already holds the file's text, the file
 * itself when it does not, or the reason neither could give any.
 *
 * @param text the text of the file, null when none could be obtained
 * @param status where the text came from, or why there is none
 */
public record DocumentTextContent(String text, Status status) {

  /**
   * Where the text of a file came from, or why there is none.
   */
  public enum Status {
    /** Read from the search index, which had already extracted it. */
    INDEXED,
    /** Extracted from the file itself, the search index not holding it. */
    EXTRACTED,
    /** No text extractor handles the file's format. */
    UNSUPPORTED_FORMAT,
    /** The file is larger than what is extracted on the fly. */
    TOO_LARGE,
    /** The file was read but holds no text, a scanned document for instance. */
    NO_TEXT,
    /** The document is not a file with a content, a folder for instance. */
    NOT_A_FILE,
    /** The file could not be read: an error, a timeout or a busy extractor. */
    UNREADABLE,
  }

  /**
   * Builds the content of a file whose text was found.
   *
   * @param text the text of the file
   * @param status {@link Status#INDEXED} or {@link Status#EXTRACTED}
   * @return the content holding that text
   */
  public static DocumentTextContent of(String text, Status status) {
    return new DocumentTextContent(text, status);
  }

  /**
   * Builds the content of a file whose text could not be obtained.
   *
   * @param status why there is no text
   * @return the content holding no text
   */
  public static DocumentTextContent none(Status status) {
    return new DocumentTextContent(null, status);
  }

  /**
   * Whether a non-blank text was obtained.
   *
   * @return true when {@link #text()} holds something to read
   */
  public boolean hasText() {
    return text != null && !text.isBlank();
  }
}
