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

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import javax.jcr.RepositoryException;

public class DocumentFileServiceImpl implements DocumentFileService {

  private static final Log    LOG     = ExoLogger.getLogger(DocumentFileServiceImpl.class);

  public static String RENAME_FILE_EVENT = "rename_file_event";

  private DocumentFileStorage documentFileStorage;

  private IdentityManager     identityManager;

  private SpaceService        spaceService;

  private IdentityRegistry    identityRegistry;

  private Authenticator       authenticator;

  private JCRDeleteFileStorage       jcrDeleteFileStorage;

  private ListenerService listenerService;

  public DocumentFileServiceImpl(DocumentFileStorage documentFileStorage,
                                 JCRDeleteFileStorage jcrDeleteFileStorage,
                                 Authenticator authenticator,
                                 SpaceService spaceService,
                                 IdentityManager identityManager,
                                 IdentityRegistry identityRegistry,
                                 ListenerService listenerService) {
    this.documentFileStorage = documentFileStorage;
    this.jcrDeleteFileStorage = jcrDeleteFileStorage;
    this.spaceService = spaceService;
    this.identityManager = identityManager;
    this.identityRegistry = identityRegistry;
    this.authenticator = authenticator;
    this.listenerService = listenerService;
  }

  @Override
  public List<AbstractNode> getDocumentItems(FileListingType listingType,
                                             DocumentNodeFilter filter,
                                             int offset,
                                             int limit,
                                             long userIdentityId,
                                             boolean showHiddenFiles) throws IllegalAccessException,
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
        timelinefilter.setIncludeHiddenFiles(showHiddenFiles);
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
        folderFilter.setIncludeHiddenFiles(showHiddenFiles);
        if (StringUtils.isBlank(folderFilter.getParentFolderId())&&(folderFilter.getOwnerId() == null || folderFilter.getOwnerId() <= 0)) {
          throw new IllegalArgumentException("ParentFolderId or OwnerId is mandatory");
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
    if(StringUtils.isBlank(filter.getParentFolderId())){
      String username = aclIdentity.getUserId();
      Long ownerId = filter.getOwnerId();
      String userId = filter.getUserId();
      org.exoplatform.social.core.identity.model.Identity ownerIdentity = null;
      if(StringUtils.isNotEmpty(userId)){
        ownerIdentity = identityManager.getOrCreateUserIdentity(userId);
      } else{
        ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
      }
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
      }
    }


    if (filter.getSortField() == null) {
      filter.setSortField(DocumentSortField.NAME);
    }
    return documentFileStorage.getFolderChildNodes(filter, aclIdentity, offset, limit);
  }

  @Override
  public List<BreadCrumbItem> getBreadcrumb(long ownerId, String folderId, String folderPath, long authenticatedUserId) throws IllegalAccessException, ObjectNotFoundException {
    return documentFileStorage.getBreadcrumb(ownerId, folderId, folderPath, getAclUserIdentity(authenticatedUserId));
  }
  @Override
  public List<FullTreeItem> getFullTreeData(long ownerId, String folderId, long authenticatedUserId) throws IllegalAccessException, ObjectNotFoundException {
    return documentFileStorage.getFullTreeData(ownerId, folderId, getAclUserIdentity(authenticatedUserId));
  }

  @Override
  public AbstractNode duplicateDocument(long ownerId, String fileId, String prefixClone, long authenticatedUserId) throws IllegalAccessException, ObjectNotFoundException {
    return documentFileStorage.duplicateDocument(ownerId, fileId, prefixClone, getAclUserIdentity(authenticatedUserId));
  }

  @Override
  public void moveDocument(long ownerId, String fileId, String destPath, long authenticatedUserId) throws IllegalAccessException, ObjectNotFoundException {
     documentFileStorage.moveDocument(ownerId, fileId, destPath, getAclUserIdentity(authenticatedUserId));
  }

  @Override
  public void createFolder(long ownerId, String folderId, String folderPath, String name, long authenticatedUserId) throws IllegalAccessException, ObjectAlreadyExistsException, ObjectNotFoundException {
    documentFileStorage.createFolder(ownerId, folderId, folderPath, name, getAclUserIdentity(authenticatedUserId));
  }

  @Override
  public String getNewName(long ownerId, String folderId, String folderPath, String name) throws IllegalAccessException, ObjectAlreadyExistsException, ObjectNotFoundException {
    return documentFileStorage.getNewName(ownerId, folderId, folderPath, name);
  }

  @Override
  public void renameDocument(long ownerId, String documentID, String name, long authenticatedUserId) throws IllegalAccessException, ObjectAlreadyExistsException, ObjectNotFoundException {
    documentFileStorage.renameDocument(ownerId, documentID, name, getAclUserIdentity(authenticatedUserId));
    try {
      listenerService.broadcast(RENAME_FILE_EVENT,this,documentID);
    }
    catch (Exception e){
      LOG.error("cnnot broadcast rename_file_event");
    }
  }

  @Override
  public void deleteDocument(String folderPath,String documentId, boolean favorite,long delay, long authenticatedUserId) throws IllegalAccessException {
    jcrDeleteFileStorage.deleteDocument(folderPath, documentId, favorite, true, delay, getAclUserIdentity(authenticatedUserId), authenticatedUserId);
  }

  @Override
  public void undoDeleteDocument(String documentId, long authenticatedUserId) {
    jcrDeleteFileStorage.undoDelete(documentId, authenticatedUserId);
  }

  @Override
  public void updatePermissions(String documentId,  NodePermission nodePermissionEntity, long authenticatedUserId) throws IllegalAccessException {

    documentFileStorage.updatePermissions(documentId, nodePermissionEntity, getAclUserIdentity(authenticatedUserId));
    nodePermissionEntity.getToShare().keySet().forEach(destId-> {
      try {
        shareDocument(documentId, destId);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("Error updating sharing of document'" + documentId + " to identity " + destId, e);
      }
    });

  }

  @Override
  public void shareDocument(String documentId, long destId) throws IllegalAccessException {

    documentFileStorage.shareDocument(documentId, destId);
  }

  @Override
  public boolean canAccess(String documentID, org.exoplatform.services.security.Identity aclIdentity) throws RepositoryException {
   return documentFileStorage.canAccess(documentID, aclIdentity);
  }

  private org.exoplatform.services.security.Identity getAclUserIdentity(long userIdentityId) throws IllegalAccessException{
    Identity userIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    if (userIdentity == null) {
      throw new IllegalAccessException("Can't find user identity with id " + userIdentityId);
    }
    String username = userIdentity.getRemoteId();
    org.exoplatform.services.security.Identity aclIdentity = identityRegistry.getIdentity(username);
    if (aclIdentity == null) {
      try {
        aclIdentity = authenticator.createIdentity(username);
      } catch (Exception e) {
        throw new IllegalAccessException("Error retrieving user ACL identity with name : " + username);
      }
    }
    return aclIdentity;
  }
  @Override
  public org.exoplatform.services.security.Identity getAclUserIdentity(String username) throws IllegalAccessException{

    org.exoplatform.services.security.Identity aclIdentity = identityRegistry.getIdentity(username);
    if (aclIdentity == null) {
      try {
        aclIdentity = authenticator.createIdentity(username);
      } catch (Exception e) {
        throw new IllegalAccessException("Error retrieving user ACL identity with name : " + username);
      }
    }
    return aclIdentity;
  }

}
