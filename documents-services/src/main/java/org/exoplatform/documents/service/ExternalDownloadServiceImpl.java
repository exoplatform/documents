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
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.PublicDocumentAccessStorage;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExternalDownloadServiceImpl implements ExternalDownloadService {

  private final DocumentFileStorage         documentFileStorage;

  private final PublicDocumentAccessStorage publicDocumentAccessStorage;

  private final DocumentFileService         documentFileService;

  private final IdentityManager             identityManager;

  public ExternalDownloadServiceImpl(DocumentFileStorage documentFileStorage,
                                     PublicDocumentAccessStorage publicDocumentAccessStorage,
                                     DocumentFileService documentFileService,
                                     IdentityManager identityManager) {
    this.documentFileStorage = documentFileStorage;
    this.publicDocumentAccessStorage = publicDocumentAccessStorage;
    this.documentFileService = documentFileService;
    this.identityManager = identityManager;
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
  public InputStream downloadZippedFolder(String folderId) throws IOException, ObjectNotFoundException {
    if (folderId == null) {
      throw new IllegalArgumentException("folder id is mandatory");
    }
    org.exoplatform.services.security.Identity creatorAclIdentity = getLinkCreatorAclIdentity(folderId);
    String downloadPath = documentFileStorage.downloadFolder(folderId, creatorAclIdentity);
    if (downloadPath == null) {
      throw new IOException("The zip of folder " + folderId + " could not be built");
    }
    Path zip = Path.of(downloadPath);
    try {
      return new FilterInputStream(Files.newInputStream(zip)) {
        @Override
        public void close() throws IOException {
          try {
            super.close();
          } finally {
            Files.deleteIfExists(zip);
          }
        }
      };
    } catch (IOException e) {
      Files.deleteIfExists(zip);
      throw e;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void checkDocumentDownload(String documentId) throws ObjectNotFoundException {
    if (documentId == null) {
      throw new IllegalArgumentException("document id is mandatory");
    }
    DownloadItem downloadInfo = documentFileStorage.getDocumentDownloadInfo(documentId);
    if (downloadInfo == null) {
      throw new ObjectNotFoundException("No document has id " + documentId);
    }
    if (downloadInfo.getMimeType() == null) {
      documentFileStorage.checkFolderReadable(documentId, getLinkCreatorAclIdentity(documentId));
    }
  }

  /**
   * The rights a folder link is archived with are those of the user who created
   * the link, read now: a link that records no such user, or
   * whose user is gone or disabled, archives nothing.
   */
  private org.exoplatform.services.security.Identity getLinkCreatorAclIdentity(String folderId) throws ObjectNotFoundException {
    PublicDocumentAccess publicDocumentAccess = publicDocumentAccessStorage.getPublicDocumentAccessByNodeId(folderId);
    if (publicDocumentAccess == null || publicDocumentAccess.getCreatorId() == null) {
      throw new ObjectNotFoundException("The public link of folder " + folderId + " records no creator");
    }
    Identity creatorIdentity = identityManager.getIdentity(String.valueOf(publicDocumentAccess.getCreatorId()));
    if (creatorIdentity == null || creatorIdentity.isDeleted() || !creatorIdentity.isEnable()) {
      throw new ObjectNotFoundException("The creator of the public link of folder " + folderId + " is not an active user");
    }
    try {
      return documentFileService.getAclUserIdentity(creatorIdentity.getRemoteId());
    } catch (IllegalAccessException e) {
      throw new ObjectNotFoundException("The rights of the creator of the public link of folder " + folderId
          + " cannot be resolved");
    }
  }
}
