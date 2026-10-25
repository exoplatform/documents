/**
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
 * Refuses a change of a transcript that another change of the same file holds
 * up longer than a request may wait (EXO-92123): the caller may try again.
 */
public class TranscriptionBusyException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  /**
   * @param message what was held up
   */
  public TranscriptionBusyException(String message) {
    super(message);
  }

}
