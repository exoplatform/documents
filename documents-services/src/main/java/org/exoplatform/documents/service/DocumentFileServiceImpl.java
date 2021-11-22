/*
 * Copyright (C) 2021 eXo Platform SAS
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

import java.util.*;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class DocumentFileServiceImpl implements DocumentFileService {

  private DocumentFileStorage documentFileStorage;

  private IdentityManager     identityManager;

  private SpaceService        spaceService;

  private IdentityRegistry    identityRegistry;

  private Authenticator       authenticator;

  public DocumentFileServiceImpl(DocumentFileStorage documentFileStorage,
                                 Authenticator authenticator,
                                 SpaceService spaceService,
                                 IdentityManager identityManager,
                                 IdentityRegistry identityRegistry) {
    this.documentFileStorage = documentFileStorage;
    this.spaceService = spaceService;
    this.identityManager = identityManager;
    this.identityRegistry = identityRegistry;
    this.authenticator = authenticator;
  }

  @Override
  public List<AbstractNode> getDocumentItems(FileListingType listingType,
                                             DocumentNodeFilter filter,
                                             int offset,
                                             int limit,
                                             long userIdentityId) throws IllegalAccessException,
                                                                  ObjectNotFoundException {
    if (filter == null) {
      throw new IllegalArgumentException("File filter is mandatory");
    }
    if (userIdentityId <= 0) {
      throw new IllegalAccessException("User Identity is mandatory");
    }

    switch (listingType) {
      case TIMELINE:
        if (!(filter instanceof DocumentTimelineFilter)) {
          throw new IllegalArgumentException("filter must be an instance of DocumentTimelineFilter");
        }
        DocumentTimelineFilter timelinefilter = (DocumentTimelineFilter) filter;
        if (timelinefilter.getOwnerId() == null || timelinefilter.getOwnerId() <= 0) {
          throw new IllegalArgumentException("OwnerId is mandatory");
        }
        List<FileNode> files = getFilesTimeline(timelinefilter, offset, limit, userIdentityId);
        return new ArrayList<>(files);
      case FOLDER:
        if (!(filter instanceof DocumentFolderFilter)) {
          throw new IllegalArgumentException("filter must be an instance of DocumentFolderFilter");
        }
        DocumentFolderFilter folderFilter = (DocumentFolderFilter) filter;
        if (StringUtils.isBlank(folderFilter.getParentFolderId())) {
          throw new IllegalArgumentException("ParentFolderId is mandatory");
        }
        return getFolderChildNodes(folderFilter, offset, limit, userIdentityId);
      default:
        return Collections.emptyList();
    }
  }

  @Override
  public List<FileNode> getFilesTimeline(DocumentTimelineFilter filter,
                                         int offset,
                                         int limit,
                                         long userIdentityId) throws IllegalAccessException, ObjectNotFoundException {
    org.exoplatform.services.security.Identity aclIdentity = getAclUserIdentity(userIdentityId);
    String username = aclIdentity.getUserId();
    Long ownerId = filter.getOwnerId();
    org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
    if (ownerIdentity == null) {
      throw new ObjectNotFoundException("Owner Identity with id : " + ownerId + " isn't found");
    }
    if (ownerIdentity.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(ownerIdentity.getRemoteId());
      if (!spaceService.hasAccessPermission(space, username)) {
        throw new IllegalAccessException("User " + username
            + " attempts to access documents of space " + space.getDisplayName()
            + "while it's not a member");
      }
    } else if (ownerIdentity.isUser() && !StringUtils.equals(ownerIdentity.getRemoteId(), username)) {
      throw new IllegalAccessException("User " + username
          + " attempts to access private documents of user " + ownerIdentity.getRemoteId());
    }
    if (filter.getSortField() == null) {
      filter.setSortField(DocumentSortField.MODIFIED_DATE);
    }
    return documentFileStorage.getFilesTimeline(filter, aclIdentity, offset, limit);
  }

  @Override
  public DocumentGroupsSize getGroupDocumentsCount(DocumentTimelineFilter filter,
                                         long userIdentityId) throws IllegalAccessException, ObjectNotFoundException {
    org.exoplatform.services.security.Identity aclIdentity = getAclUserIdentity(userIdentityId);
    String username = aclIdentity.getUserId();
    Long ownerId = filter.getOwnerId();
    org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
    if (ownerIdentity == null) {
      throw new ObjectNotFoundException("Owner Identity with id : " + ownerId + " isn't found");
    }
    if (ownerIdentity.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(ownerIdentity.getRemoteId());
      if (!spaceService.hasAccessPermission(space, username)) {
        throw new IllegalAccessException("User " + username
            + " attempts to access documents of space " + space.getDisplayName()
            + "while it's not a member");
      }
    } else if (ownerIdentity.isUser() && !StringUtils.equals(ownerIdentity.getRemoteId(), username)) {
      throw new IllegalAccessException("User " + username
          + " attempts to access private documents of user " + ownerIdentity.getRemoteId());
    }
    if (filter.getSortField() == null) {
      filter.setSortField(DocumentSortField.MODIFIED_DATE);
    }
    return documentFileStorage.getGroupDocumentsCount(filter, aclIdentity);
  }

  @Override
  public List<AbstractNode> getFolderChildNodes(DocumentFolderFilter filter,
                                                int offset,
                                                int limit,
                                                long userIdentityId) throws IllegalAccessException, ObjectNotFoundException {
    org.exoplatform.services.security.Identity aclIdentity = getAclUserIdentity(userIdentityId);
    if (filter.getSortField() == null) {
      filter.setSortField(DocumentSortField.NAME);
    }
    return documentFileStorage.getFolderChildNodes(filter, aclIdentity, offset, limit);
  }

  private org.exoplatform.services.security.Identity getAclUserIdentity(long userIdentityId) {
    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalStateException("Can't find user identity with id " + userIdentityId);
    }
    String username = userIdentity.getRemoteId();
    org.exoplatform.services.security.Identity aclIdentity = identityRegistry.getIdentity(username);
    if (aclIdentity == null) {
      try {
        aclIdentity = authenticator.createIdentity(username);
      } catch (Exception e) {
        throw new IllegalStateException("Error retrieving user ACL identity with name : " + username, e);
      }
    }
    return aclIdentity;
  }

}
