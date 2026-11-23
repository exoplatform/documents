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

import java.util.Set;

/**
 * What a change of a space document's access is decided on, each identity as
 * an access list writes it: a user name, a membership such as
 * {@code manager:/spaces/team}, or {@code any}
 *
 * @param storedIdentities the identities of the access list the document
 *          reads: its own, or its folder's when it has none of its own
 * @param requestedIdentities the identities a save of the requested
 *          permissions writes on the document's access list, empty when no
 *          permissions are requested
 * @param owner the document's owner as stored (exo:owner), null when it has
 *          none
 * @param driveRootIdentities the identities of the access list of the space's
 *          drive root
 */
public record DocumentAccessChange(Set<String> storedIdentities,
                                   Set<String> requestedIdentities,
                                   String owner,
                                   Set<String> driveRootIdentities) {
}
