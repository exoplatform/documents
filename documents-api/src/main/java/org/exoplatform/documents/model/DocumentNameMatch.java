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
 * A file or a folder found by its name, with the drive it was found in.
 *
 * @param document the file or the folder, a shortcut answered as its target
 *          with the shortcut's path
 * @param ownerIdentityId the identity id of the drive's owner: the space for a
 *          space drive, the user for their own drive
 * @param folderPath the path of the folder holding it, from the drive's root
 *          ({@code /} for the root itself), made of the folders' titles as
 *          Documents shows them
 */
public record DocumentNameMatch(AbstractNode document, long ownerIdentityId, String folderPath) {
}
