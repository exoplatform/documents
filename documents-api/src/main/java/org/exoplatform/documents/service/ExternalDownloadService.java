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
   * Download a zipped folder
   * 
   * @param folderId folder id
   * @return byte array of the zipped folder
   * @throws IOException
   */
  byte[] downloadZippedFolder(String folderId) throws IOException;
}
