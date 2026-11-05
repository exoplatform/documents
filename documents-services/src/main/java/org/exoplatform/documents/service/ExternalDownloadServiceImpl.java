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

import org.apache.commons.io.FileUtils;
import org.exoplatform.documents.model.DownloadItem;
import org.exoplatform.documents.storage.DocumentFileStorage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExternalDownloadServiceImpl implements ExternalDownloadService {

  private final DocumentFileStorage documentFileStorage;

  public ExternalDownloadServiceImpl(DocumentFileStorage documentFileStorage) {
    this.documentFileStorage = documentFileStorage;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DownloadItem getDocumentDownloadItem(String documentId) {
    if (documentId == null) {
      throw new IllegalArgumentException("document id is mandatory");
    }
    return documentFileStorage.getDocumentDownloadItem(documentId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] downloadZippedFolder(String folderId) throws IOException {
    String downloadPath = documentFileStorage.downloadFolder(folderId);
    File zipped = new File(downloadPath);
    byte[] filesBytes = FileUtils.readFileToByteArray(zipped);
    Files.delete(Path.of(downloadPath));
    return filesBytes;
  }
}
