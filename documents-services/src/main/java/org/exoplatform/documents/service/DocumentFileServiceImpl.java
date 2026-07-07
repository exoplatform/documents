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

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.utils.DocumentUtils;
import org.exoplatform.documents.webdav.model.OperationCancelledException;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.commons.file.model.FileInfo;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.HTMLSanitizer;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.plugin.DocumentCategoryPlugin;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.services.thumbnail.ImageThumbnailService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.analytics.model.StatisticData;
import io.meeds.analytics.model.filter.AnalyticsFilter;
import io.meeds.analytics.utils.AnalyticsUtils;
import io.meeds.portal.thumbnail.model.FileContent;
import io.meeds.social.category.model.CategoryObject;
import io.meeds.social.category.service.CategoryLinkService;
import lombok.SneakyThrows;

public class DocumentFileServiceImpl implements DocumentFileService {

  private static final Log      LOG               = ExoLogger.getLogger(DocumentFileServiceImpl.class);

  private static final String   RENAME_FILE_EVENT = "rename_file_event";

  private DocumentFileStorage   documentFileStorage;

  private IdentityManager       identityManager;

  private SpaceService          spaceService;

  private IdentityRegistry      identityRegistry;

  private Authenticator         authenticator;

  private JCRDeleteFileStorage  jcrDeleteFileStorage;

  private ListenerService       listenerService;

  private AnalyticsService      analyticsService;

  private ImageThumbnailService imageThumbnailService;

  private CategoryLinkService   categoryLinkService;

  private RepositoryService      repositoryService;

  private NodeHierarchyCreator   nodeHierarchyCreator;

  private static final String    DEFAULT_GROUPS_HOME_PATH = "/Groups";

  private static final String    GROUPS_PATH_ALIAS        = "groupsPath";

  private static final String    DOCUMENTS_NODE           = "Documents";

  private static String          groupsPath               = null;

  String                         dateFormat               = "MM-dd-yyyy";

  SimpleDateFormat               simpleDateFormat         = new SimpleDateFormat(dateFormat);

  public DocumentFileServiceImpl(DocumentFileStorage documentFileStorage, // NOSONAR
                                 JCRDeleteFileStorage jcrDeleteFileStorage,
                                 Authenticator authenticator,
                                 SpaceService spaceService,
                                 IdentityManager identityManager,
                                 IdentityRegistry identityRegistry,
                                 ListenerService listenerService,
                                 AnalyticsService analyticsService,
                                 ImageThumbnailService imageThumbnailService,
                                 RepositoryService repositoryService,
                                 NodeHierarchyCreator nodeHierarchyCreator,
                                 SessionProviderService sessionProviderService) {
    this.documentFileStorage = documentFileStorage;
    this.jcrDeleteFileStorage = jcrDeleteFileStorage;
    this.spaceService = spaceService;
    this.identityManager = identityManager;
    this.identityRegistry = identityRegistry;
    this.authenticator = authenticator;
    this.listenerService = listenerService;
    this.analyticsService = analyticsService;
    this.imageThumbnailService = imageThumbnailService;
    this.repositoryService = repositoryService;
    this.nodeHierarchyCreator = nodeHierarchyCreator;
  }

  @Override
  @SneakyThrows
  public FolderNode getSpaceRootFolder(long spaceId, org.exoplatform.services.security.Identity userAclIdentity) throws ObjectNotFoundException, IllegalAccessException {
    Space space = spaceService.getSpaceById(spaceId);
    if (space == null) {
      throw new ObjectNotFoundException("Space with id %s doesn't exist".formatted(spaceId));
    } else if (!spaceService.canViewSpace(space, userAclIdentity.getUserId())) {
      throw new IllegalAccessException("Space with id %s isn't accessible for user %s".formatted(spaceId,
                                                                                                 userAclIdentity.getUserId()));
    }
    return documentFileStorage.getSpaceRootFolder(spaceId, userAclIdentity);
  }
  @Override
  @SneakyThrows
  public FolderNode getPersonalRootFolder(org.exoplatform.services.security.Identity userAclIdentity) {
    return documentFileStorage.getPersonalRootFolder(userAclIdentity);
  }

  @Override
  @SneakyThrows
  public long getRootFolderOwnerId(String documentId) {
    return documentFileStorage.getRootFolderOwnerId(documentId);
  }

  @Override
  @SneakyThrows
  public String getFileContentAsText(String docId) {
    return documentFileStorage.getFileContentAsText(docId);
  }

  @Override
  @SneakyThrows
  public InputStream getFileContentAsStream(String docId, long userIdentityId) {
    return documentFileStorage.getFileContentAsStream(docId, getAclUserIdentity(userIdentityId));
  }

  @Override
  public List<? extends AbstractNode> getDocumentItems(FileListingType listingType,
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
  public List<String> getFavoriteFileIds(DocumentNodeFilter filter,
                                         int offset,
                                         int limit,
                                         long userIdentityId) throws IllegalAccessException {
    if (filter == null) {
      throw new IllegalArgumentException("File filter is mandatory");
    }
    if (userIdentityId <= 0) {
      throw new IllegalAccessException("User Identity is mandatory");
    }
    return documentFileStorage.getFavoriteFileIds(filter, getAclUserIdentity(userIdentityId), offset, limit);
  }

  @Override
  public List<FileNode> search(String keyword, org.exoplatform.services.security.Identity identity, int offset, int limit) {
    return documentFileStorage.search(keyword, identity, offset, limit);
  }

  @Override
  public List<FileNode> search(DocumentTimelineFilter filter, org.exoplatform.services.security.Identity identity, int offset, int limit) {
    return documentFileStorage.search(filter, identity, offset, limit);
  }

  @Override
  public List<FileNode> getFilesTimeline(DocumentTimelineFilter filter,
                                         int offset,
                                         int limit,
                                         long userIdentityId) throws IllegalAccessException, ObjectNotFoundException {
    org.exoplatform.services.security.Identity aclIdentity = getAclUserIdentity(userIdentityId);
    String username = aclIdentity.getUserId();
    Long ownerId = filter.getOwnerId();
    org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(ownerId);
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
  public List<AbstractNode> getBiggestDocuments(long ownerId, Identity userIdentity, int offset, int limit) throws
                                                                                                     IllegalAccessException,
                                                                                                     ObjectNotFoundException {

    org.exoplatform.services.security.Identity aclIdentity = getAclUserIdentity(userIdentity.getRemoteId());
    return documentFileStorage.getBiggestDocuments(ownerId,aclIdentity,offset,limit);
  }

  @Override
  public DocumentGroupsSize getGroupDocumentsCount(DocumentTimelineFilter filter,
                                         long userIdentityId) throws IllegalAccessException, ObjectNotFoundException {
    org.exoplatform.services.security.Identity aclIdentity = getAclUserIdentity(userIdentityId);
    String username = aclIdentity.getUserId();
    Long ownerId = filter.getOwnerId();
    org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(ownerId);
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
        ownerIdentity = identityManager.getIdentity(ownerId);
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
  public List<FullTreeItem> getFullTreeData(long ownerId, String folderId, String destinationFolderPath, long authenticatedUserId, boolean withChildren, boolean showHidden) throws IllegalAccessException, ObjectNotFoundException {
    return documentFileStorage.getFullTreeData(ownerId, folderId, destinationFolderPath, getAclUserIdentity(authenticatedUserId), withChildren, showHidden);
  }

  @Override
  public AbstractNode duplicateDocument(long ownerId, String fileId, String prefixClone, long authenticatedUserId) throws IllegalAccessException, ObjectNotFoundException {
    return documentFileStorage.duplicateDocument(ownerId, fileId, prefixClone, getAclUserIdentity(authenticatedUserId));
  }

  @Override
  public AbstractNode copyDocument(String nodeId, String destintionNodeId, long authenticatedUserId) throws IllegalAccessException, ObjectNotFoundException {
    return documentFileStorage.copyDocument(nodeId, destintionNodeId, getAclUserIdentity(authenticatedUserId));
  }

  @Override
  public void moveDocument(long ownerId, String fileId, String destPath, long authenticatedUserId, String conflictAction) throws IllegalAccessException, ObjectNotFoundException, ObjectAlreadyExistsException {
     documentFileStorage.moveDocument(ownerId, fileId, destPath, getAclUserIdentity(authenticatedUserId), conflictAction);
  }

  @Override
  public AbstractNode createFolder(long ownerId, String folderId, String folderPath, String name, long authenticatedUserId) throws IllegalAccessException, ObjectAlreadyExistsException, ObjectNotFoundException {
   return documentFileStorage.createFolder(ownerId, folderId, folderPath, name, getAclUserIdentity(authenticatedUserId));
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
      LOG.error("cannot broadcast rename_file_event");
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
  public void deleteDocuments(int actionId,
                              List<AbstractNode> documents,
                              long authenticatedUserId) throws IllegalAccessException {
    jcrDeleteFileStorage.deleteDocuments(actionId, documents, getAclUserIdentity(authenticatedUserId), authenticatedUserId);
  }

  @Override
  public void downloadDocuments(int actionId,
                                List<AbstractNode> documents,
                                long authenticatedUserId) throws IllegalAccessException {
    documentFileStorage.downloadDocuments(actionId, documents, getAclUserIdentity(authenticatedUserId), authenticatedUserId);
  }

  @Override
  public void updatePermissions(String documentId,  NodePermission nodePermissionEntity, long authenticatedUserId) throws IllegalAccessException {

    documentFileStorage.updatePermissions(documentId, nodePermissionEntity, getAclUserIdentity(authenticatedUserId));
    nodePermissionEntity.getToShare().keySet().forEach(destId-> {
      try {
        boolean notifyMember = nodePermissionEntity.getToNotify() != null && nodePermissionEntity.getToNotify().containsKey(destId);
        shareDocument(documentId, destId, authenticatedUserId, notifyMember);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("Error updating sharing of document'" + documentId + " to identity " + destId, e);
      }
    });
    if (nodePermissionEntity.getToUnShare() != null) {
      nodePermissionEntity.getToUnShare().keySet().forEach(destId -> {
        try {
          unShareDocument(documentId, destId);
        } catch (Exception e) {
          throw new IllegalStateException("Error when unsharing the document " + documentId + "with identity " + destId, e);
        }
      });
    }
  }

  @Override
  public void shareDocument(String documentId, long destId, long authenticatedUserId, boolean notifyMember) throws IllegalAccessException {
    documentFileStorage.shareDocument(documentId, destId, getAclUserIdentity(authenticatedUserId), notifyMember );
  }

  @Override
  public void unShareDocument(String documentId, long destId) throws RepositoryException {
    documentFileStorage.unShareDocument(documentId, destId);
  }

  @Override
  public void notifyMember(String documentId, long destId) throws IllegalAccessException {
    documentFileStorage.notifyMember(documentId, destId);
  }

  @Override
  public boolean canAccess(String documentID, org.exoplatform.services.security.Identity aclIdentity) {
    return documentFileStorage.canAccess(documentID, aclIdentity);
  }

  private org.exoplatform.services.security.Identity getAclUserIdentity(long userIdentityId) throws IllegalAccessException{
    Identity userIdentity = identityManager.getIdentity(userIdentityId);
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
  @Override
  public void updateDocumentDescription(long ownerId,
                                        String documentID,
                                        String description,
                                        long aclIdentity) throws IllegalStateException, IllegalAccessException, RepositoryException {
    description = HTMLSanitizer.sanitize(description);
    documentFileStorage.updateDocumentDescription(ownerId, documentID, description, getAclUserIdentity(aclIdentity));
  }

  @Override
  public void updateDocumentMimeType(String documentId,
                                     String mimeType,
                                     long aclIdentity) throws IllegalStateException, IllegalAccessException, RepositoryException {
    documentFileStorage.updateDocumentMimeType(documentId, mimeType, getAclUserIdentity(aclIdentity));
  }

  @Override
  public void updateAudioTranscription(String documentId, String transcription, long aclIdentity) throws IllegalAccessException {
    documentFileStorage.updateAudioTranscription(documentId, transcription, getAclUserIdentity(aclIdentity));
  }

  @Override
  public void updateAudioTranscription(String documentId, String transcription) {
    documentFileStorage.updateAudioTranscription(documentId, transcription);
  }

  @Override
  public String getAudioTranscription(String documentId, long aclIdentity) throws IllegalAccessException {
    return documentFileStorage.getAudioTranscription(documentId, getAclUserIdentity(aclIdentity));
  }

  @Override
  public String getAudioTranscription(String documentId) {
    return documentFileStorage.getAudioTranscription(documentId);
  }

  @Override
  public void createShortcut(String documentId, String destPath, String aclIdentity, String conflictAction) throws IllegalAccessException, ObjectAlreadyExistsException {
    documentFileStorage.createShortcut(documentId, destPath, aclIdentity, conflictAction);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<FileVersion> getFileVersions(String fileNodeId, String aclIdentity) {
    if (fileNodeId == null) {
      throw new IllegalArgumentException("file node id is mandatory");
    }
    return documentFileStorage.getFileVersions(fileNodeId, aclIdentity);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public FileVersion updateVersionSummary(String originFileId, String versionId, String summary, String aclIdentity) {
    if (versionId == null) {
      throw new IllegalArgumentException("version id is mandatory");
    }
    if (originFileId == null) {
      throw new IllegalArgumentException("original file id is mandatory");
    }
    return documentFileStorage.updateVersionSummary(originFileId, versionId, summary, aclIdentity);
  }

  /**
   * {@inheritDoc}
   *
   * @return
   */
  @Override
  public FileVersion restoreVersion(String versionId, String aclIdentity) {
    if (versionId == null) {
      throw new IllegalArgumentException("version id is mandatory");
    }
    return documentFileStorage.restoreVersion(versionId, aclIdentity);
  }

  @Override
  public boolean canAddDocument(String spaceId, String currentUserName) {
    Space space = spaceService.getSpaceById(spaceId);
    boolean canAdd = false;
    if (space != null) {
      canAdd = spaceService.canRedactOnSpace(space, currentUserName);
    }
    return canAdd;
  }

  @Override
  public byte[] getDownloadZipBytes(int actionId, String userName) throws IOException {
    return documentFileStorage.getDownloadZipBytes(actionId,userName);
  }
  @Override
  public void cancelBulkAction(String actionId, String userName) throws IOException {
    documentFileStorage.cancelBulkAction(actionId, userName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public FileVersion createNewVersion(String nodeId, String aclIdentity, InputStream newContent) {
    if (nodeId == null) {
      throw new IllegalArgumentException("node id is mandatory");
    }
    if (aclIdentity == null) {
      throw new IllegalArgumentException("User identity id is mandatory");
    }
    return documentFileStorage.createNewVersion(nodeId, aclIdentity, newContent);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void moveDocuments(int actionId, long ownerId, List<AbstractNode> documents, String destPath, long userIdentityId) throws IllegalAccessException {
    documentFileStorage.moveDocuments(actionId, ownerId, documents, destPath, getAclUserIdentity(userIdentityId), userIdentityId);
  }


  public boolean canImport(org.exoplatform.services.security.Identity identity) {
    return documentFileStorage.canImport(identity);
  }

  @Override
  public DocumentsSize getDocumentsSizeStat(long ownerId, long userIdentityId) throws IllegalAccessException,
                                                                               ObjectNotFoundException {
    Identity currentUserIdentity = identityManager.getIdentity(userIdentityId);
    Identity ownerIdentity = identityManager.getIdentity(ownerId);
    if (ownerIdentity == null) {
      throw new ObjectNotFoundException("Owner Identity with id : " + ownerId + " isn't found");
    }
    if (ownerIdentity.isUser() && ownerId != userIdentityId) {
      throw new IllegalAccessException("Current user with identity id : " + userIdentityId
          + " attempts to get the size of private documents of user  with identity id  " + ownerId);
    }
    if (ownerIdentity.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(ownerIdentity.getRemoteId());
      if (!spaceService.hasAccessPermission(space, currentUserIdentity.getRemoteId())) {
        throw new IllegalAccessException("Current user with identity id : " + userIdentityId
            + " attempts to get size of documents of space with identity id " + ownerId + " while it's not a member");
      }
    }
    DocumentsSize documentsSize = new DocumentsSize();
    LocalDate toLocalDate = LocalDate.now();
    LocalDate fromLocalDate = toLocalDate.minusDays(30);
    LocalDateTime from = fromLocalDate.atTime(LocalTime.MIN);
    LocalDateTime to = toLocalDate.atTime(LocalTime.MAX);
    AnalyticsFilter filter = new AnalyticsFilter();
    filter.addEqualFilter("ownerId", String.valueOf(ownerId));
    filter.addEqualFilter("operation", "documentsSize");
    ZonedDateTime zdtStart = ZonedDateTime.of(from, ZoneId.systemDefault());
    ZonedDateTime zdtEnd = ZonedDateTime.of(to, ZoneId.systemDefault());
    filter.addRangeFilter("timestamp",
                          String.valueOf(zdtStart.toInstant().toEpochMilli()),
                          String.valueOf(zdtEnd.toInstant().toEpochMilli()));
    List<StatisticData> stats = analyticsService.retrieveData(filter);
    if (!stats.isEmpty()) {
      StatisticData toStat = stats.get(0);
      documentsSize.setOwnerId(ownerId);
      documentsSize.setToSize(Long.parseLong(String.valueOf(getStatisticParameter(toStat, "size"))));
      documentsSize.setToSizeDate(toStat.getTimestamp());
      documentsSize.setTodaySize(simpleDateFormat.format(new Date())
                                                 .equals(simpleDateFormat.format(new Date(toStat.getTimestamp()))));
      if (stats.size() > 1) {
        StatisticData fromStat = stats.get(stats.size() - 1);
        documentsSize.setFromSize(Long.parseLong(String.valueOf(getStatisticParameter(fromStat, "size"))));
        documentsSize.setFromSizeDate(fromStat.getTimestamp());
        LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromStat.getTimestamp()), ZoneId.systemDefault());
        long diff = ChronoUnit.DAYS.between(date, to);
        documentsSize.setDiffDays(diff);
      }
    }
    return documentsSize;
  }

  @Override
  public DocumentsSize addDocumentsSizeStat(long ownerId, long userIdentityId) throws IllegalAccessException,
                                                                               ObjectNotFoundException {
    Identity currentUserIdentity = identityManager.getIdentity(userIdentityId);
    Identity ownerIdentity = identityManager.getIdentity(ownerId);
    StatisticData statisticData = new StatisticData();
    if (ownerIdentity == null) {
      throw new ObjectNotFoundException("Owner Identity with id : " + ownerId + " isn't found");
    }
    if (ownerIdentity.isUser()) {
      if (ownerId != userIdentityId) {
        throw new IllegalAccessException("Current user with identity id : " + userIdentityId
            + " attempts to calculate the size of private documents of user  with identity id  " + ownerId);
      }
      statisticData.setUserId(userIdentityId);
    }
    if (ownerIdentity.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(ownerIdentity.getRemoteId());
      statisticData.setSpaceId(space.getSpaceId());
      if (!spaceService.hasAccessPermission(space, currentUserIdentity.getRemoteId())) {
        throw new IllegalAccessException("Current user with identity id : " + userIdentityId
            + " attempts to calculate size of documents of space with identity id " + ownerId + " while it's not a member");
      }
    }
    org.exoplatform.services.security.Identity aclIdentity = getAclUserIdentity(userIdentityId);
    long size = documentFileStorage.calculateFilesSize(ownerId, aclIdentity);
    statisticData.setModule("Drive");
    statisticData.setSubModule("Documents");
    statisticData.setOperation("documentsSize");
    statisticData.setTimestamp(new Date().getTime());
    statisticData.addKeyword("ownerId", ownerId);
    statisticData.addLong("size", size);
    AnalyticsUtils.addStatisticData(statisticData);
    DocumentsSize documentsSize = getDocumentsSizeStat(ownerId, userIdentityId);
    documentsSize.setTodaySize(true);
    documentsSize.setToSize(size);
    return documentsSize;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasEditPermissionOnDocument(String nodeId, long userIdentityId) throws IllegalAccessException {
    return hasEditPermissionOnDocument(nodeId, getAclUserIdentity(userIdentityId));
  }

  @Override
  public boolean hasEditPermissionOnDocument(String nodeId, org.exoplatform.services.security.Identity aclIdentity) {
    return documentFileStorage.hasEditPermissions(nodeId, aclIdentity);
  }

  @Override
  public void importFiles(String ownerId,
                          String folderId,
                          String folderPath,
                          String uploadId,
                          String conflict,
                          org.exoplatform.services.security.Identity identity,
                          long authenticatedUserId) throws Exception {

    String userName = "";
    Space space = null;
    org.exoplatform.social.core.identity.model.Identity ownerIdentity = StringUtils.isBlank(ownerId) ? null : identityManager.getIdentity(Long.parseLong(ownerId));
    if (ownerIdentity == null) {
      throw new ObjectNotFoundException("Owner Identity with id : " + ownerId + " isn't found");
    }
    if (ownerIdentity.isSpace()) {
      space = spaceService.getSpaceByPrettyName(ownerIdentity.getRemoteId());
      if (!spaceService.hasAccessPermission(space, identity.getUserId())) {
        throw new IllegalAccessException("User " + identity.getUserId() + " attempts to access documents of space "
            + space.getDisplayName() + " while it's not a member");
      }
    } else if (ownerIdentity.isUser()) {
      if (!StringUtils.equals(ownerIdentity.getRemoteId(), identity.getUserId())) {
        throw new IllegalAccessException("User " + identity.getUserId() + " attempts to access private documents of user "
            + ownerIdentity.getRemoteId());
      }
      userName = ownerIdentity.getRemoteId();
    }
    documentFileStorage.importFiles(uploadId,
                                    space,
                                    userName,
                                    folderId,
                                    folderPath,
                                    conflict,
                                    identity,
                                    ownerId,
                                    authenticatedUserId);
  }

  @Override
  public List<TrashElementNode> getDeletedDocuments(TrashElementNodeFilter filter) throws RepositoryException {
    return jcrDeleteFileStorage.getDeletedDocuments(filter);
  }

  @Override
  public int countDeletedDocuments() {
    return jcrDeleteFileStorage.countDeletedDocuments();
  }

  @Override
  public void restoreDocumentFromTrash(String trashNodePath) throws RepositoryException {
    jcrDeleteFileStorage.restoreFromTrash(trashNodePath);
  }

  @Override
  public void deleteDocumentPermanently(String documentPath) throws ObjectNotFoundException, RepositoryException {
    jcrDeleteFileStorage.deleteDocumentPermanently(documentPath);
  }

  @Override
  public void deleteDocumentsPermanently(int actionId, List<AbstractNode> trashElementNodes, long userIdentityId) throws IllegalAccessException {
   jcrDeleteFileStorage.deleteDocumentsPermanently(actionId, trashElementNodes, getAclUserIdentity(userIdentityId));
  }

  @Override
  public void restoreDocuments(int actionId, List<AbstractNode> trashElementNodes, long userIdentityId) throws IllegalAccessException {
    jcrDeleteFileStorage.restoreDocuments(actionId, trashElementNodes, getAclUserIdentity(userIdentityId));
  }

  @Override
  public AbstractNode getDocumentById(String documentId, String aclIdentity) throws IllegalAccessException, ObjectNotFoundException{
    return documentFileStorage.getDocumentById(documentId, aclIdentity);
  }

  @Override
  public FileContent getDocumentContent(String documentId, String aclIdentity) throws ObjectNotFoundException {
    return documentFileStorage.getDocumentContent(documentId, aclIdentity);
  }

  @Override
  public FileContent getImageThumbnailContent(String fileType,
                                              String documentId,
                                              String userName,
                                              int width,
                                              int height) throws Exception {
    FileItem image = imageThumbnailService.getOrCreateThumbnail(fileType, documentId, userName, width, height);
    if (image != null) {
      FileInfo imageInfo = image.getFileInfo();
      return new FileContent(String.valueOf(imageInfo.getId()),
                             imageInfo.getName(),
                             imageInfo.getMimetype(),
                             image.getAsStream(),
                             imageInfo.getUpdatedDate());
    } else
      throw new ObjectNotFoundException("Image with id : " + documentId + " isn't found");
  }

  @Override
  public FileItem createThumbnail(String fileType, FileContent file, String userName, int width, int height) throws Exception {
    FileItem image = imageThumbnailService.getThumbnail(fileType + file.getId(), width, height);
    if (image != null) {
      imageThumbnailService.deleteThumbnails(fileType, file.getId());
    }
    return imageThumbnailService.createThumbnail(fileType + file.getId(), file, userName, width, height);
  }

  public AbstractNode getDocumentById(String documentId) {
    return documentFileStorage.getDocumentById(documentId);
  }

  @Override
  public void setDocumentVisibility(long ownerId, String documentID, Boolean hidden, long authenticatedUserId) throws Exception {
    documentFileStorage.setDocumentVisibility(ownerId, documentID, hidden, getAclUserIdentity(authenticatedUserId));
  }

  @Override
  public List<Long> getDocumentCategoryIds(String documentId) {
    return getCategoryLinkService().getLinkedIds(new CategoryObject(DocumentCategoryPlugin.OBJECT_TYPE,
            String.valueOf(documentId),
            0L));
  }

  @SneakyThrows
  @Override
  public List<Long> getDocumentCategoryIds(long spaceIdentityId, String userName) {
    return documentFileStorage.getDocumentCategoryIds(spaceIdentityId, getAclUserIdentity(userName));
  }

  private Object getStatisticParameter(StatisticData statisticData, String fieldName) {
    if (statisticData == null || statisticData.getParameters() == null || StringUtils.isBlank(fieldName)) {
      return null;
    }
    Map<String, Object> parameters = statisticData.getParameters();
    for (int i = AnalyticsUtils.MAX_ALTERNATIVE_FIELD_COUNT; i >= 1; i--) {
      String alternativeFieldName = AnalyticsUtils.getAlternativeFieldName(fieldName, i);
      if (parameters.containsKey(alternativeFieldName)) {
        return parameters.get(alternativeFieldName);
      }
    }
    return parameters.get(fieldName);
  }

  private CategoryLinkService getCategoryLinkService() {
    if (categoryLinkService == null) {
      categoryLinkService = CommonsUtils.getService(CategoryLinkService.class);
    }
    return categoryLinkService;
  }

  @Override
  public void synchronizeSpacePermissions(Space space) {
    synchronizeSpacePermissions(space, isRedactionalSpace(space));
  }

  public void synchronizeSpacePermissions(Space space, boolean targetRedactionalMode) {
    if (space == null) {
      return;
    }
    Session session = null;
    try {
      ManageableRepository repository = repositoryService.getCurrentRepository();
      session = repository.getSystemSession(repository.getConfiguration().getDefaultWorkspaceName());
      Node spaceRootNode = getGroupNode(session, space.getGroupId());
      if (spaceRootNode != null) {
        synchronizeNodePermissions(space, targetRedactionalMode, spaceRootNode);
        synchronizeChildrenPermissions(space, targetRedactionalMode, spaceRootNode);
      }
    } catch (OperationCancelledException e) {
      LOG.info("Permission update cancelled for space width id '{}'", space.getId());
    } catch (Exception e) {
      LOG.error("Error updating permissions of space width id '{}'", space.getId(), e);
    } finally {
      if (session != null) {
        session.logout();
      }
    }
  }

  private void synchronizeChildrenPermissions(Space space,
                                              boolean targetRedactionalMode,
                                              Node parentNode) throws RepositoryException,
          OperationCancelledException {
    NodeIterator children = parentNode.getNodes();
    while (children.hasNext()) {
      Node child = children.nextNode();
      try {
        synchronizeNodePermissions(space, targetRedactionalMode, child);
        synchronizeChildrenPermissions(space, targetRedactionalMode, child);
      } catch (OperationCancelledException e) {
        throw e;
      } catch (Exception e) {
        LOG.warn("Error applying permissions to a child node in space width id '{}', continuing", space.getId(), e);
      }
    }
  }

  private void synchronizeNodePermissions(Space space,
                                          boolean targetRedactionalMode,
                                          Node node) throws RepositoryException,
          OperationCancelledException {
    assertRedactionalModeStillExpected(space, targetRedactionalMode);
    if (DocumentUtils.shouldRewriteSpacePermissions(node, space.getGroupId(), targetRedactionalMode)) {
      Map<String, String[]> permissions = DocumentUtils.buildPermissionsForTargetMode(node, space.getGroupId(), targetRedactionalMode);
      ((ExtendedNode) node).setPermissions(permissions);
      node.save();
    }
  }

  private void assertRedactionalModeStillExpected(Space space,
                                                  boolean expectedRedactionalMode) throws OperationCancelledException {
    Space currentSpace = spaceService.getSpaceById(space.getSpaceId());
    if (isRedactionalSpace(currentSpace) != expectedRedactionalMode) {
      throw new OperationCancelledException();
    }
  }

  private boolean isRedactionalSpace(Space space) {
    return space != null && spaceService.hasRedactor(space);
  }

  private Node getGroupNode(Session session, String groupId) throws RepositoryException {
    String groupsHomePath = getGroupsPath();
    String groupPath = groupsHomePath + groupId + "/" + DOCUMENTS_NODE;
    if (session.itemExists(groupPath)) {
      return (Node) session.getItem(groupPath);
    }
    return null;
  }

  private String getGroupsPath() {
    if (groupsPath != null) {
      return groupsPath;
    }
    groupsPath = nodeHierarchyCreator.getJcrPath(GROUPS_PATH_ALIAS);
    if (StringUtils.isBlank(groupsPath)) {
      groupsPath = DEFAULT_GROUPS_HOME_PATH;
    }
    return groupsPath;
  }
}
