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

import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.analytics.api.service.AnalyticsService;
import org.exoplatform.analytics.model.StatisticData;
import org.exoplatform.analytics.model.filter.AnalyticsFilter;
import org.exoplatform.analytics.utils.AnalyticsUtils;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class DocumentFileServiceImpl implements DocumentFileService {

  private static final Log    LOG     = ExoLogger.getLogger(DocumentFileServiceImpl.class);

  public static String RENAME_FILE_EVENT = "rename_file_event";

  private DocumentFileStorage documentFileStorage;

  private IdentityManager     identityManager;

  private SpaceService        spaceService;

  private IdentityRegistry    identityRegistry;

  private Authenticator       authenticator;

  private JCRDeleteFileStorage jcrDeleteFileStorage;

  private ListenerService listenerService;

  private SettingService       settingService;

  private AnalyticsService     analyticsService;

  private static final Scope   DOCUMENTS_USER_SETTING_SCOPE = Scope.APPLICATION.id("Documents");

  private static final String  DOCUMENTS_USER_SETTING_KEY   = "DocumentsSettings";

  String                       dateFormat                   = "MM-dd-yyyy";

  SimpleDateFormat             simpleDateFormat             = new SimpleDateFormat(dateFormat);

  public DocumentFileServiceImpl(DocumentFileStorage documentFileStorage,
                                 JCRDeleteFileStorage jcrDeleteFileStorage,
                                 Authenticator authenticator,
                                 SpaceService spaceService,
                                 IdentityManager identityManager,
                                 IdentityRegistry identityRegistry,
                                 ListenerService listenerService,
                                 SettingService settingService,
                                 AnalyticsService analyticsService) {
    this.documentFileStorage = documentFileStorage;
    this.jcrDeleteFileStorage = jcrDeleteFileStorage;
    this.spaceService = spaceService;
    this.identityManager = identityManager;
    this.identityRegistry = identityRegistry;
    this.authenticator = authenticator;
    this.listenerService = listenerService;
    this.settingService = settingService;
    this.analyticsService = analyticsService;
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
        shareDocument(documentId, destId);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("Error updating sharing of document'" + documentId + " to identity " + destId, e);
      }
    });
    if (nodePermissionEntity.getToNotify() != null) {
      nodePermissionEntity.getToNotify().keySet().forEach(destId -> {
        try {
          notifyMember(documentId, destId);
        } catch (IllegalAccessException e) {
          throw new IllegalStateException("Error updating sharing of document'" + documentId + " to identity " + destId, e);
        }
      });
    }
  }

  @Override
  public void shareDocument(String documentId, long destId) throws IllegalAccessException {

    documentFileStorage.shareDocument(documentId, destId);
  }

  @Override
  public void notifyMember(String documentId, long destId) throws IllegalAccessException {
    documentFileStorage.notifyMember(documentId, destId);
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
  @Override
  public void updateDocumentDescription(long ownerId,
                                        String documentID,
                                        String description,
                                        long aclIdentity) throws IllegalStateException, IllegalAccessException, RepositoryException {
    documentFileStorage.updateDocumentDescription(ownerId, documentID, description, getAclUserIdentity(aclIdentity));
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
      canAdd = !spaceService.hasRedactor(space) || spaceService.hasRedactor(space)
          && (spaceService.isRedactor(space, currentUserName) || spaceService.isManager(space, currentUserName));
    }
    return canAdd;
  }

  @Override
  public byte[] getDownloadZipBytes(int actionId, String userName) throws IOException {
    return documentFileStorage.getDownloadZipBytes(actionId,userName);
  }
  @Override
  public void cancelBulkAction(int actionId, String userName) throws IOException {
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

  /**
   * {@inheritDoc}
   */
  @Override
  public String getDefaultView(Long ownerId, String userIdentityId) {
    SettingValue<?> settingValue = settingService.get(Context.USER.id(userIdentityId),
                                                      DOCUMENTS_USER_SETTING_SCOPE,
                                                      DOCUMENTS_USER_SETTING_KEY + "_" + ownerId);
    if (settingValue == null || settingValue.getValue() == null || StringUtils.isBlank(settingValue.getValue().toString())) {
      return null;
    } else {
      return settingValue.getValue().toString();
    }
  }

  @Override
  public DocumentsSize getDocumentsSizeStat(long ownerId, long userIdentityId) throws IllegalAccessException,
                                                                               ObjectNotFoundException {
    Identity currentUserIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
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
      stats.stream().sorted(Comparator.comparing(StatisticData::getTimestamp)).toList();
      documentsSize.setOwnerId(ownerId);
      documentsSize.setToSize(Long.parseLong(toStat.getParameters().get("size")));
      documentsSize.setToSizeDate(toStat.getTimestamp());
      documentsSize.setTodaySize(simpleDateFormat.format(new Date())
                                                 .equals(simpleDateFormat.format(new Date(toStat.getTimestamp()))));
      if (stats.size() > 1) {
        StatisticData fromStat = stats.get(stats.size() - 1);
        documentsSize.setFromSize(Long.parseLong(fromStat.getParameters().get("size")));
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
    Identity currentUserIdentity = identityManager.getIdentity(String.valueOf(userIdentityId));
    Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
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
      statisticData.addParameter("spaceId", space.getId());
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
    statisticData.addParameter("ownerId", ownerId);
    statisticData.addParameter("size", size);
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
  public void setDefaultView(Long ownerId, String userIdentityId, String view) {
    if (Long.parseLong(userIdentityId) <= 0) {
      throw new IllegalArgumentException("User identity id is mandatory");
    }
    if (ownerId <= 0) {
      throw new IllegalArgumentException("Owner id is mandatory");
    }
    this.settingService.set(Context.USER.id(userIdentityId),
                            DOCUMENTS_USER_SETTING_SCOPE,
                            DOCUMENTS_USER_SETTING_KEY + "_" + ownerId,
                            SettingValue.create(view));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasEditPermissionOnDocument(String nodeId, long userIdentityId) throws IllegalAccessException {
    return documentFileStorage.hasEditPermissions(nodeId, getAclUserIdentity(userIdentityId));
  }
}
