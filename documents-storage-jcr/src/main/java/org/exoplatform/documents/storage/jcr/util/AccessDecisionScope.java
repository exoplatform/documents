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
package org.exoplatform.documents.storage.jcr.util;

/**
 * The scope of a listing, opened by
 * {@link JCRDocumentsUtil#openAccessDecisions()}: its documents decide once
 * per space and user whether the space lets the user manage their access.
 * Closed in a try-with-resources statement.
 */
@FunctionalInterface
public interface AccessDecisionScope extends AutoCloseable {

  @Override
  void close();

}
