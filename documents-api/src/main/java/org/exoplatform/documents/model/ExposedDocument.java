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
 * A document or a folder of a space, as the space's exposure lists show it
 *
 * @param id the node's identifier
 * @param name the name shown to users
 * @param mimeType the file's mime type, null for a folder
 * @param folder whether the node is a folder
 * @param owner the node's owner as stored (exo:owner), null when it has none
 */
public record ExposedDocument(String id, String name, String mimeType, boolean folder, String owner) {
}
