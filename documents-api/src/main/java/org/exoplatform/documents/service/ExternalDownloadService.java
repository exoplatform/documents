/*
 * Copyright (C) 2023 eXo Platform SAS
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
package org.exoplatform.documents.service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.DownloadItem;

import java.io.IOException;

public interface ExternalDownloadService {

  /**
   * Gets a download item of a given document
   *
   * @param documentId document id
   * @return {@link DownloadItem}
   */
  DownloadItem getDocumentDownloadItem(String documentId);

  /**
   * Download a zipped folder shared by a public link, with the rights of the
   * user who created or last replaced the link, evaluated when this method
   * runs: what that user cannot read, a sub-folder, a document or a shortcut
   * target, is left out of the zip.
   *
   * @param folderId id of the folder holding the public link
   * @return byte array of the zipped folder
   * @throws ObjectNotFoundException when the folder has no public link, the
   *           link records no creator, the creator is unknown, deleted or
   *           disabled, or the creator cannot read the folder
   * @throws IOException when the zip cannot be built or read
   */
  byte[] downloadZippedFolder(String folderId) throws IOException, ObjectNotFoundException;
}
