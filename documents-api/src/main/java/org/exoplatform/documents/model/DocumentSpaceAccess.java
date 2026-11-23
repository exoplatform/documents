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
package org.exoplatform.documents.model;

/**
 * What the space of a document lets a user do with its access, decided on one
 * read of the space's committed settings
 *
 * @param canManageAccess whether its "Limit Access Management" setting lets
 *          the user change the document's access list
 * @param keptInsideSpace whether the space keeps its documents inside
 */
public record DocumentSpaceAccess(boolean canManageAccess, boolean keptInsideSpace) {
}
