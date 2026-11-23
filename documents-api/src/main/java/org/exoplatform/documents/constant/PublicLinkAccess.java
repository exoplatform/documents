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
package org.exoplatform.documents.constant;

/**
 * What a public link grants an anonymous visitor, decided on one read of the
 * link and of its document's space
 */
public enum PublicLinkAccess {

  /** No link, or a link of a document whose space keeps its documents inside */
  NONE,

  /** The link has expired */
  EXPIRED,

  /** The link asks for a password the visitor did not give */
  INVALID_PASSWORD,

  /** The link serves its document */
  GRANTED

}
