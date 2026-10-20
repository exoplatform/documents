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
package org.exoplatform.documents.constant;

/**
 * What an upload does when its folder already holds a document of the name
 * it is filed under: the two choices the Documents app offers, neither of
 * which deletes anything.
 */
public enum UploadConflictAction {

  /** Files a second document, its name numbered. */
  KEEP_BOTH,

  /** Adds the upload as a new version of the existing document. */
  NEW_VERSION;

}
