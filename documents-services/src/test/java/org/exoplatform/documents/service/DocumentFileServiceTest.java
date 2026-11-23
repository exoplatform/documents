/*
 * Copyright (C) 2021 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import io.meeds.analytics.api.service.AnalyticsService;
import io.meeds.portal.thumbnail.model.FileContent;
import io.meeds.social.space.security.model.SpaceSecuritySettings;
import io.meeds.social.space.security.service.SpaceSecurityService;
import org.junit.Before;
import org.junit.Test;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.exception.DocumentSharingRefusedException;
import org.exoplatform.commons.file.model.FileInfo;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.listener.AttachmentsActivityCacheUpdater;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.rest.model.AbstractNodeEntity;
import org.exoplatform.documents.rest.model.FileNodeEntity;
import org.exoplatform.documents.rest.util.EntityBuilder;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.services.thumbnail.ImageThumbnailService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.storage.cache.CachedActivityStorage;

import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;

public class DocumentFileServiceTest {

  private static final String     OWNER          = "owner";

  private static final String     ADMINISTRATORS = "*:/platform/administrators";

  private static final String     TEAM_MEMBERS   = "*:/spaces/team";

  private DocumentFileStorage     documentFileStorage;

  private IdentityManager         identityManager;

  private SpaceService            spaceService;

  private IdentityRegistry        identityRegistry;

  private Authenticator           authenticator;

  private DocumentFileServiceImpl documentFileService;

  private JCRDeleteFileStorage    jcrDeleteFileStorage;

  private ListenerService         listenerService;

  private ImageThumbnailService   imageThumbnailService;

  private CachedActivityStorage   cachedActivityStorage;

  private AnalyticsService        analyticsService;

  private RepositoryService       repositoryService;

  private NodeHierarchyCreator    nodeHierarchyCreator;

  private SessionProviderService  sessionProviderService;

  private Identity                currentIdentity;

  String                          userName       = "testuser";

  long                            currentOwnerId = 2;

  @Before
  public void setUp() {
    spaceService = mock(SpaceService.class);
    identityManager = mock(IdentityManager.class);
    identityRegistry = mock(IdentityRegistry.class);
    authenticator = mock(Authenticator.class);
    documentFileStorage = mock(DocumentFileStorage.class);
    listenerService = mock(ListenerService.class);
    jcrDeleteFileStorage = mock(JCRDeleteFileStorage.class);
    cachedActivityStorage = mock(CachedActivityStorage.class);
    analyticsService = mock(AnalyticsService.class);
    imageThumbnailService = mock(ImageThumbnailService.class);
    repositoryService = mock(RepositoryService.class);
    nodeHierarchyCreator = mock(NodeHierarchyCreator.class);
    sessionProviderService = mock(SessionProviderService.class);
    documentFileService = new DocumentFileServiceImpl(documentFileStorage,
                                                      jcrDeleteFileStorage,
                                                      authenticator,
                                                      spaceService,
                                                      identityManager,
                                                      identityRegistry,
                                                      listenerService,
                                                      analyticsService,
                                                      imageThumbnailService,
                                                      repositoryService,
                                                      nodeHierarchyCreator,
                                                      sessionProviderService);

    currentIdentity = new Identity(OrganizationIdentityProvider.NAME, userName);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(userName);
    when(identityRegistry.getIdentity(userName)).thenReturn(userID);
    when(identityManager.getIdentity(currentOwnerId)).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(userName))).thenReturn(currentIdentity);
    // the users changing access in these tests may edit the documents
    when(documentFileStorage.hasEditPermissions(anyString(), any())).thenReturn(true);
  }

  @Test
  public void testClearActivityCacheWhenFileRenamed() throws Exception {

    String documentID = "2";
    Event<String, String> renameFileEvent = new Event<>("rename_file_event", null, documentID);
    lenient().doNothing().when(documentFileStorage).renameDocument(anyLong(), anyString(), anyString(), any());
    documentFileService.renameDocument(1, documentID, "newName", currentOwnerId);
    cachedActivityStorage = mock(CachedActivityStorage.class);
    AttachmentsActivityCacheUpdater attachmentsActivityCacheUpdater = new AttachmentsActivityCacheUpdater(cachedActivityStorage);
    attachmentsActivityCacheUpdater.onEvent(renameFileEvent);
    verify(cachedActivityStorage, times(1)).clearActivityCachedByAttachmentId(documentID);
  }

  @Test
  public void testGetDocumentItems() throws Exception {
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, userName);
    currentIdentity.setProfile(currentProfile);

    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(userName);
    DocumentTimelineFilter filter = null;
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.TIMELINE, null, 0, 0, Long.valueOf(currentIdentity.getId()), false);
    });
    assertEquals(exception.getMessage(), "File filter is mandatory");

    filter = new DocumentTimelineFilter(0L, null);
    DocumentTimelineFilter finalFilter1 = filter;
    exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.TIMELINE,
                                           finalFilter1,
                                           0,
                                           0,
                                           Long.valueOf(currentIdentity.getId()),
                                           false);
    });
    assertEquals(exception.getMessage(), "OwnerId is mandatory");

    filter = new DocumentTimelineFilter(Long.valueOf(currentIdentity.getId()), null);
    DocumentTimelineFilter finalFilter = filter;
    exception = assertThrows(IllegalAccessException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.TIMELINE, finalFilter, 0, 0, 0, false);
    });
    assertEquals(exception.getMessage(), "User Identity is mandatory");

    DocumentFolderFilter docFilter = new DocumentFolderFilter("", "", 0L, "");
    DocumentFolderFilter finalDocFilter = docFilter;
    exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.TIMELINE,
                                           finalDocFilter,
                                           0,
                                           0,
                                           Long.valueOf(currentIdentity.getId()),
                                           false);
    });
    assertEquals(exception.getMessage(), "filter must be an instance of DocumentTimelineFilter");

    exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.FOLDER,
                                           finalFilter,
                                           0,
                                           0,
                                           Long.valueOf(currentIdentity.getId()),
                                           false);
    });
    assertEquals(exception.getMessage(), "filter must be an instance of DocumentFolderFilter");

    exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.FOLDER,
                                           finalDocFilter,
                                           0,
                                           0,
                                           Long.valueOf(currentIdentity.getId()),
                                           false);
    });
    assertEquals(exception.getMessage(), "ParentFolderId or OwnerId is mandatory");

    when(identityRegistry.getIdentity(userName)).thenReturn(userID);
    when(identityManager.getIdentity(currentOwnerId)).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(userName))).thenReturn(currentIdentity);

    String spacePrettyName = "spacetest";
    currentIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
    org.exoplatform.services.security.Identity spaceID = new org.exoplatform.services.security.Identity(spacePrettyName);
    when(identityRegistry.getIdentity(spacePrettyName)).thenReturn(spaceID);
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);
    when(spaceService.hasAccessPermission(space, userName)).thenReturn(true);

    List<FileNode> files = new ArrayList<>();

    FileNode file1 = new FileNode();
    FileNode file2 = new FileNode();
    FileNode file3 = new FileNode();
    FileNode file4 = new FileNode();

    files.add(file1);
    files.add(file2);
    files.add(file3);
    files.add(file4);

    when(documentFileStorage.getFilesTimeline(filter, spaceID, 0, 0)).thenReturn(files);
    List<? extends AbstractNode> documentItems = documentFileService.getDocumentItems(FileListingType.TIMELINE,
                                                                                      filter,
                                                                                      0,
                                                                                      0,
                                                                                      Long.parseLong(currentIdentity.getId()),
                                                                                      false);
    assertEquals(documentItems.size(), 4);
  }

  @Test
  public void testGetFilesTimeline() throws Exception { // NOSONAR

    String username = "testuser";
    long currentOwnerId = 2;
    Identity currentIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);

    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(username);
    DocumentTimelineFilter filter = new DocumentTimelineFilter(Long.valueOf(currentIdentity.getId()), null);

    when(identityRegistry.getIdentity(username)).thenReturn(userID);
    when(identityManager.getIdentity(currentOwnerId)).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(username))).thenReturn(currentIdentity);

    DocumentTimelineFilter filter_ = new DocumentTimelineFilter(0L, null);
    DocumentTimelineFilter finalFilter1 = filter_;
    Exception exception = assertThrows(ObjectNotFoundException.class, () -> {
      documentFileService.getFilesTimeline(finalFilter1, 0, 0, Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(), "Owner Identity with id : " + finalFilter1.getOwnerId() + " isn't found");

    String spacePrettyName = "spacetest";
    currentIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
    org.exoplatform.services.security.Identity spaceID = new org.exoplatform.services.security.Identity(spacePrettyName);
    when(identityRegistry.getIdentity(spacePrettyName)).thenReturn(spaceID);
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);
    when(spaceService.hasAccessPermission(space, username)).thenReturn(true);

    List<FileNode> files = new ArrayList<>();

    FileNode file1 = new FileNode();
    FileNode file2 = new FileNode();
    FileNode file3 = new FileNode();
    FileNode file4 = new FileNode();

    files.add(file1);
    files.add(file2);
    files.add(file3);
    files.add(file4);

    when(documentFileStorage.getFilesTimeline(filter, spaceID, 0, 0)).thenReturn(files);
    List<FileNode> files_ = new ArrayList<>();
    files_ = documentFileService.getFilesTimeline(filter, 0, 0, Long.valueOf(currentIdentity.getId()));
    assertEquals(files_.size(), 4);
  }

  @Test
  public void testGetGroupDocumentsCount() throws Exception { // NOSONAR

    String username = "testuser";
    long currentOwnerId = 2;
    Identity currentIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);

    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(username);
    DocumentTimelineFilter filter = new DocumentTimelineFilter(Long.valueOf(currentIdentity.getId()), null);

    when(identityRegistry.getIdentity(username)).thenReturn(userID);
    when(identityManager.getIdentity(currentOwnerId)).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(username))).thenReturn(currentIdentity);

    DocumentTimelineFilter filter_ = new DocumentTimelineFilter(0L, null);
    DocumentTimelineFilter finalFilter1 = filter_;
    Exception exception = assertThrows(ObjectNotFoundException.class, () -> {
      documentFileService.getFilesTimeline(finalFilter1, 0, 0, Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(), "Owner Identity with id : " + finalFilter1.getOwnerId() + " isn't found");

    String spacePrettyName = "spacetest";
    currentIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
    org.exoplatform.services.security.Identity spaceID = new org.exoplatform.services.security.Identity(spacePrettyName);
    when(identityRegistry.getIdentity(spacePrettyName)).thenReturn(spaceID);
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);
    when(spaceService.hasAccessPermission(space, username)).thenReturn(true);

    DocumentGroupsSize documentGroupsSize = new DocumentGroupsSize();
    documentGroupsSize.setThisDay(4);

    when(documentFileStorage.getGroupDocumentsCount(filter, spaceID)).thenReturn(documentGroupsSize);
    DocumentGroupsSize documentGroupsSize_ = new DocumentGroupsSize();
    documentGroupsSize_ = documentFileService.getGroupDocumentsCount(filter, Long.valueOf(currentIdentity.getId()));

    currentIdentity.setProviderId("space");
    when(spaceService.getSpaceByPrettyName(spacePrettyName)).thenReturn(space);
    when(spaceService.hasAccessPermission(space, spacePrettyName)).thenReturn(true);
    DocumentGroupsSize documentGroupsSize_2 = documentFileService.getGroupDocumentsCount(filter,
                                                                                         Long.valueOf(currentIdentity.getId()));

    assertEquals(4, documentGroupsSize_.getThisDay());
    assertEquals(4, documentGroupsSize_2.getThisDay());

  }

  @Test
  public void testGetFolderChildNodes() throws Exception { // NOSONAR
    String username = "testuser";
    long currentOwnerId = 2;
    long spaceIdentityId = 4;
    Identity currentIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    String user2name = "testuser2";
    long user2Id = 3;
    Identity user2Identity = new Identity(OrganizationIdentityProvider.NAME, user2name);
    user2Identity.setId(String.valueOf(user2Id));
    Profile user2Profile = new Profile();
    user2Profile.setProperty(Profile.FULL_NAME, user2name);
    user2Identity.setProfile(user2Profile);

    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(username);
    org.exoplatform.services.security.Identity user2ID = new org.exoplatform.services.security.Identity(user2name);

    when(identityRegistry.getIdentity(username)).thenReturn(userID);
    when(identityManager.getIdentity(currentOwnerId)).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(username))).thenReturn(currentIdentity);

    when(identityRegistry.getIdentity(user2name)).thenReturn(user2ID);
    when(identityManager.getIdentity(user2Id)).thenReturn(user2Identity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(user2name))).thenReturn(user2Identity);

    DocumentFolderFilter filter_ = new DocumentFolderFilter(null, null, 0L, "");
    DocumentFolderFilter finalFilter1 = filter_;
    Exception exception = assertThrows(ObjectNotFoundException.class, () -> {
      documentFileService.getFolderChildNodes(finalFilter1, 0, 0, Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(), "Owner Identity with id : " + finalFilter1.getOwnerId() + " isn't found");

    String spacePrettyName = "spacetest";

    Space space = new Space();
    org.exoplatform.services.security.Identity spaceID = new org.exoplatform.services.security.Identity(spacePrettyName);

    Identity spaceIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    spaceIdentity.setId(String.valueOf(spaceIdentityId));
    spaceIdentity.setRemoteId(spacePrettyName);
    spaceIdentity.setProviderId(SpaceIdentityProvider.NAME);

    when(identityRegistry.getIdentity(spacePrettyName)).thenReturn(spaceID);
    when(identityManager.getIdentity(spaceIdentityId)).thenReturn(spaceIdentity);
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);
    when(spaceService.hasAccessPermission(space, username)).thenReturn(true);
    when(spaceService.hasAccessPermission(space, user2name)).thenReturn(false);

    filter_ = new DocumentFolderFilter(null, null, Long.valueOf(spaceIdentity.getId()), "");
    DocumentFolderFilter finalFilter2 = filter_;
    List<AbstractNode> files = new ArrayList<>();

    AbstractNode file1 = new FileNode();
    AbstractNode file2 = new FileNode();
    AbstractNode file3 = new FileNode();
    AbstractNode file4 = new FileNode();

    files.add(file1);
    files.add(file2);
    files.add(file3);
    files.add(file4);

    exception = assertThrows(IllegalAccessException.class, () -> {
      documentFileService.getFolderChildNodes(finalFilter2, 0, 0, Long.valueOf(user2Identity.getId()));
    });
    assertEquals(exception.getMessage(),
                 "User " + user2name + " attempts to access documents of space " + space.getDisplayName()
                     + "while it's not a member");

    when(documentFileStorage.getFolderChildNodes(finalFilter2, userID, 0, 0)).thenReturn(files);
    List<AbstractNode> files_ = new ArrayList<>();
    files_ = documentFileService.getFolderChildNodes(finalFilter2, 0, 0, Long.valueOf(currentIdentity.getId()));
    assertEquals(files_.size(), 4);
  }

  @Test
  public void testGetBreadCrumbs() throws Exception { // NOSONAR
    String username = "testuser";
    org.exoplatform.services.security.Identity root = new org.exoplatform.services.security.Identity(username);
    ConversationState.setCurrent(new ConversationState(root));
    long currentOwnerId = 2;
    Identity currentIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);

    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(username);

    when(identityRegistry.getIdentity(username)).thenReturn(userID);
    when(identityManager.getIdentity(currentOwnerId)).thenReturn(currentIdentity);

    BreadCrumbItem breadCrumbItem1 = new BreadCrumbItem("1", "Folder1", "Folder1", "", false, new HashMap<>());
    BreadCrumbItem breadCrumbItem2 = new BreadCrumbItem("2", "Folder2", "Folder1", "", false, new HashMap<>());
    BreadCrumbItem breadCrumbItem3 = new BreadCrumbItem();
    breadCrumbItem3.setId("3");
    breadCrumbItem3.setName("Folder3");
    BreadCrumbItem breadCrumbItem4 = new BreadCrumbItem();
    breadCrumbItem4.setId("4");
    breadCrumbItem4.setName("Folder4");

    List<BreadCrumbItem> breadCrumbItems = new ArrayList<>();
    breadCrumbItems.add(breadCrumbItem1);
    breadCrumbItems.add(breadCrumbItem2);
    breadCrumbItems.add(breadCrumbItem3);
    breadCrumbItems.add(breadCrumbItem4);

    List<BreadCrumbItem> breadCrumbItems_ = new ArrayList<>();

    when(documentFileStorage.getBreadcrumb(2, "Folder1", "", userID)).thenReturn(breadCrumbItems);

    Exception exception = assertThrows(IllegalAccessException.class, () -> {
      documentFileService.getBreadcrumb(0, "", "", 0);
    });
    assertEquals(exception.getMessage(), "Can't find user identity with id 0");

    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);
    breadCrumbItems_ = documentFileService.getBreadcrumb(Long.valueOf(2), "Folder1", "", 2);
    assertEquals(breadCrumbItems_.size(), 4);
    assertEquals(breadCrumbItems_.get(0).getId(), "1");
    assertEquals(breadCrumbItems_.get(0).getName(), "Folder1");
  }

  @Test
  public void testUpdatePermissionsRefusedWhenTheSpaceLimitsAccessManagement() throws Exception {
    NodePermission nodePermission = new NodePermission();
    nodePermission.setToShare(Map.of(1L, "read"));
    SpaceSecurityService spaceSecurityService = limitAccessManagement("username", false);

    DocumentSharingRefusedException refusal = assertThrows(DocumentSharingRefusedException.class,
                                                           () -> documentFileService.updatePermissions("123",
                                                                                                       nodePermission,
                                                                                                       1L));

    assertEquals(DocumentSharingRefusedException.ACCESS_MANAGEMENT_LIMITED, refusal.getMessageCode());
    assertEquals(7L, refusal.getSpaceId());
    assertEquals("123", refusal.getDocumentId());
    assertEquals(DocumentSharingRefusedException.CHANGE_PERMISSIONS_ACTION, refusal.getAction());
    verify(spaceSecurityService).getSettings(7L);
    verify(documentFileStorage, never()).updatePermissions(anyString(), any(), any());
    verify(documentFileStorage, never()).shareDocument(anyString(), anyLong(), any(), anyBoolean());
  }

  @Test
  public void testUpdatePermissionsAllowedWhenTheSpaceLetsTheUserManageAccess() throws Exception {
    NodePermission nodePermission = new NodePermission();
    nodePermission.setToShare(Map.of(1L, "read"));
    limitAccessManagement("username", true);
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("username");
    when(identityRegistry.getIdentity("username")).thenReturn(aclIdentity);

    documentFileService.updatePermissions("123", nodePermission, 1L);

    verify(documentFileStorage).updatePermissions("123", nodePermission, aclIdentity);
    verify(documentFileStorage).shareDocument("123", 1L, aclIdentity, false);
  }

  @Test
  public void testShareDocumentRefusedWhenTheSpaceLimitsAccessManagement() throws Exception {
    limitAccessManagement("username", false);

    assertThrows(DocumentSharingRefusedException.class, () -> documentFileService.shareDocument("123", 5L, 1L, false));

    verify(documentFileStorage, never()).shareDocument(anyString(), anyLong(), any(), anyBoolean());
  }

  @Test
  public void testShareDocumentAllowedWhenTheSpaceLetsTheUserManageAccess() throws Exception {
    limitAccessManagement("username", true);
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("username");
    when(identityRegistry.getIdentity("username")).thenReturn(aclIdentity);

    documentFileService.shareDocument("123", 5L, 1L, true);

    verify(documentFileStorage).shareDocument("123", 5L, aclIdentity, true);
  }

  @Test
  public void testCheckCanManageAccess() throws Exception {
    SpaceSecurityService spaceSecurityService = limitAccessManagement("username", false);
    assertThrows(DocumentSharingRefusedException.class,
                 () -> documentFileService.checkCanManageAccess("123", 1L, DocumentSharingRefusedException.REVOKE_PUBLIC_LINK_ACTION));

    when(spaceSecurityService.canManageDocumentsAccess(any(), any(), eq("username"))).thenReturn(true);
    documentFileService.checkCanManageAccess("123", 1L, DocumentSharingRefusedException.REVOKE_PUBLIC_LINK_ACTION);
  }

  @Test
  public void testCheckCanManageAccessOfADocumentOutsideEverySpace() throws Exception {
    SpaceSecurityService spaceSecurityService = limitAccessManagement("username", false);
    when(documentFileStorage.getDocumentSpace("123")).thenReturn(null);

    documentFileService.checkCanManageAccess("123", 1L, DocumentSharingRefusedException.REVOKE_PUBLIC_LINK_ACTION);
    verify(spaceSecurityService, never()).getSettings(anyLong());
  }

  @Test
  public void testCheckCanManageAccessRefusedWhenTheSpaceIsGone() throws Exception {
    SpaceSecurityService spaceSecurityService = limitAccessManagement("username", true);
    when(spaceSecurityService.getSettings(7L)).thenThrow(new ObjectNotFoundException("gone"));

    DocumentSharingRefusedException refusal =
                                            assertThrows(DocumentSharingRefusedException.class,
                                                         () -> documentFileService.checkCanManageAccess("123",
                                                                                                        1L,
                                                                                                        DocumentSharingRefusedException.REVOKE_PUBLIC_LINK_ACTION));
    assertEquals(DocumentSharingRefusedException.ACCESS_MANAGEMENT_LIMITED, refusal.getMessageCode());
  }

  /**
   * Document 123 lies in space 7, whose settings limit access management;
   * the space lets the user manage access or not.
   */
  private SpaceSecurityService limitAccessManagement(String username, boolean allowed) throws Exception {
    Identity socialIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    socialIdentity.setId("1");
    when(identityManager.getIdentity(1L)).thenReturn(socialIdentity);
    Space space = new Space();
    space.setId(7L);
    when(documentFileStorage.getDocumentSpace("123")).thenReturn(space);
    SpaceSecuritySettings settings = new SpaceSecuritySettings();
    settings.setLimitAccessManagement(true);
    SpaceSecurityService spaceSecurityService = mock(SpaceSecurityService.class);
    when(spaceSecurityService.getSettings(7L)).thenReturn(settings);
    when(spaceSecurityService.canManageDocumentsAccess(space, settings, username)).thenReturn(allowed);
    documentFileService = spy(documentFileService);
    doReturn(spaceSecurityService).when(documentFileService).getSpaceSecurityService();
    return spaceSecurityService;
  }

  @Test
  public void testUpdatePermissions() throws IllegalAccessException {
    NodePermission nodePermission = new NodePermission();
    Map<Long, String> toShare = new HashMap<>();
    toShare.put(1L, "read");
    nodePermission.setToShare(toShare);
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Identity socialIdentity = mock(Identity.class);
    when(identityRegistry.getIdentity("username")).thenReturn(identity);
    when(socialIdentity.getRemoteId()).thenReturn("username");
    when(identityManager.getIdentity(1)).thenReturn(socialIdentity);
    documentFileService.updatePermissions("123", nodePermission, 1L);
    verify(documentFileStorage, times(1)).updatePermissions("123", nodePermission, identity);
    verify(documentFileStorage, times(1)).shareDocument("123", 1L, identity, false);
    //
    Map<Long, String> toNotify = new HashMap<>();
    toNotify.put(1L, "read");
    nodePermission.setToNotify(toNotify);
    documentFileService.updatePermissions("123", nodePermission, 1L);
    verify(documentFileStorage, atLeast(1)).updatePermissions("123", nodePermission, identity);
    verify(documentFileStorage, atLeast(1)).shareDocument("123", 1L, identity, true);

  }

  @Test
  public void testDeleteDocuments() throws IllegalAccessException {

    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Identity socialIdentity = mock(Identity.class);
    when(identityRegistry.getIdentity("username")).thenReturn(identity);
    when(socialIdentity.getRemoteId()).thenReturn("username");
    when(identityManager.getIdentity(1)).thenReturn(socialIdentity);
    FileNodeEntity file1 = new FileNodeEntity();
    file1.setId("1");
    file1.setName("oldFile");
    file1.setPath("/document/oldFile");
    file1.setDatasource("datasource");
    file1.setMimeType(":file");
    file1.setSize(50);
    FileNodeEntity file2 = new FileNodeEntity();
    file2.setId("2");
    file2.setName("oldFile2");
    file2.setPath("/document/oldFile2");
    file2.setDatasource("datasource");
    file2.setMimeType(":file");
    file2.setSize(50);
    FileNodeEntity file3 = new FileNodeEntity();
    file3.setId("3");
    file3.setName("oldFile3");
    file3.setPath("/document/oldFile3");
    file3.setDatasource("datasource");
    file3.setMimeType(":file");
    file3.setSize(50);

    List<AbstractNodeEntity> nodeEntities = new ArrayList<>();

    nodeEntities.add(file1);
    nodeEntities.add(file2);
    nodeEntities.add(file3);
    List<AbstractNode> nodes = EntityBuilder.toAbstractNodes(nodeEntities);

    documentFileService.deleteDocuments(123456, nodes, 1L);
    verify(jcrDeleteFileStorage, times(1)).deleteDocuments(123456, nodes, identity, 1L);
  }

  @Test
  public void testDownloadDocuments() throws IllegalAccessException {

    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Identity socialIdentity = mock(Identity.class);
    when(identityRegistry.getIdentity("username")).thenReturn(identity);
    when(socialIdentity.getRemoteId()).thenReturn("username");
    when(identityManager.getIdentity(1)).thenReturn(socialIdentity);
    FileNodeEntity file1 = new FileNodeEntity();
    file1.setId("1");
    file1.setName("oldFile");
    file1.setPath("/document/oldFile");
    file1.setDatasource("datasource");
    file1.setMimeType(":file");
    file1.setSize(50);
    FileNodeEntity file2 = new FileNodeEntity();
    file2.setId("2");
    file2.setName("oldFile2");
    file2.setPath("/document/oldFile2");
    file2.setDatasource("datasource");
    file2.setMimeType(":file");
    file2.setSize(50);
    FileNodeEntity file3 = new FileNodeEntity();
    file3.setId("3");
    file3.setName("oldFile3");
    file3.setPath("/document/oldFile3");
    file3.setDatasource("datasource");
    file3.setMimeType(":file");
    file3.setSize(50);

    List<AbstractNodeEntity> nodeEntities = new ArrayList<>();

    nodeEntities.add(file1);
    nodeEntities.add(file2);
    nodeEntities.add(file3);
    List<AbstractNode> nodes = EntityBuilder.toAbstractNodes(nodeEntities);
    doNothing().when(documentFileStorage).downloadDocuments(123456, nodes, identity, 1L);
    documentFileService.downloadDocuments(123456, nodes, 1L);
    verify(documentFileStorage, times(1)).downloadDocuments(123456, nodes, identity, 1L);
  }

  @Test
  public void testGetDownloadZipBytes() throws IOException {

    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Identity socialIdentity = mock(Identity.class);
    when(identityRegistry.getIdentity("username")).thenReturn(identity);
    when(socialIdentity.getRemoteId()).thenReturn("username");
    when(identityManager.getIdentity(1)).thenReturn(socialIdentity);

    when(documentFileStorage.getDownloadZipBytes(123456, "userName")).thenReturn(null);
    documentFileService.getDownloadZipBytes(123456, "userName");
    verify(documentFileStorage, times(1)).getDownloadZipBytes(123456, "userName");
  }

  @Test
  public void getAclUserIdentity() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identityRegistry.getIdentity("user")).thenReturn(null);
    when(authenticator.createIdentity("user")).thenReturn(identity);
    org.exoplatform.services.security.Identity aclIdentity = documentFileService.getAclUserIdentity("user");
    assertNotNull(aclIdentity);
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    org.exoplatform.services.security.Identity aclIdentity1 = documentFileService.getAclUserIdentity("user");
    assertNotNull(aclIdentity1);
  }

  @Test
  public void testCreateShortcut() throws Exception {
    String docId = "11111111";
    String docPath = "/Groups/spaces/test/Documents/test";
    documentFileService.createShortcut(docId, "/Groups/spaces/test/Documents/test", "user", "keepBoth");
    verify(documentFileStorage, times(1)).createShortcut(docId, docPath, "user", "keepBoth");
  }

  @Test
  public void getFileVersions() {
    Throwable exception =
                        assertThrows(IllegalArgumentException.class, () -> this.documentFileService.getFileVersions(null, null));
    assertEquals("file node id is mandatory", exception.getMessage());
    verify(documentFileStorage, times(0)).getFileVersions(anyString(), anyString());
    this.documentFileService.getFileVersions("5456456456", "user");
    verify(documentFileStorage, times(1)).getFileVersions("5456456456", "user");
  }

  @Test
  public void updateVersionSummary() {
    Throwable exception = assertThrows(IllegalArgumentException.class,
                                       () -> this.documentFileService.updateVersionSummary("123", null, "test", "user"));
    assertEquals("version id is mandatory", exception.getMessage());
    verify(documentFileStorage, times(0)).updateVersionSummary(anyString(), anyString(), anyString(), anyString());
    Throwable exception1 = assertThrows(IllegalArgumentException.class,
                                        () -> this.documentFileService.updateVersionSummary(null, "333", "test", "user"));
    assertEquals("original file id is mandatory", exception1.getMessage());
    verify(documentFileStorage, times(0)).updateVersionSummary(anyString(), anyString(), anyString(), anyString());
    this.documentFileService.updateVersionSummary("5456456456", "333erf", "new summary", "user");
    verify(documentFileStorage, times(1)).updateVersionSummary(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  public void restoreVersion() {
    Throwable exception =
                        assertThrows(IllegalArgumentException.class, () -> this.documentFileService.restoreVersion(null, "user"));
    assertEquals("version id is mandatory", exception.getMessage());
    verify(documentFileStorage, times(0)).restoreVersion(anyString(), anyString(), anyBoolean());
    documentFileService.restoreVersion("123", "user");
    verify(documentFileStorage, times(1)).restoreVersion("123", "user", false);
  }

  @Test
  public void restoreVersionKeepsTheAccessListForAUserTheSpaceRefuses() throws Exception {
    SpaceSecurityService spaceSecurityService = limitAccessManagement("username", false);

    documentFileService.restoreVersion("123", "username");
    verify(documentFileStorage).restoreVersion("123", "username", true);

    when(spaceSecurityService.canManageDocumentsAccess(any(), any(), eq("username"))).thenReturn(true);
    documentFileService.restoreVersion("123", "username");
    verify(documentFileStorage).restoreVersion("123", "username", false);

    // undecidable: the list is kept
    when(spaceSecurityService.getSettings(7L)).thenThrow(new IllegalStateException("unreadable"));
    documentFileService.restoreVersion("123", "username");
    verify(documentFileStorage, times(2)).restoreVersion("123", "username", true);
  }

  @Test
  public void createNewVersion() {
    Throwable exception = assertThrows(IllegalArgumentException.class,
                                       () -> this.documentFileService.createNewVersion(null, null, null));
    assertEquals("node id is mandatory", exception.getMessage());
    exception = assertThrows(IllegalArgumentException.class, () -> this.documentFileService.createNewVersion("123", null, null));
    assertEquals("User identity id is mandatory", exception.getMessage());
    verify(documentFileStorage, times(0)).createNewVersion(anyString(), anyString(), any());
    InputStream newContent = new ByteArrayInputStream("test".getBytes());
    documentFileService.createNewVersion("123", "user", newContent);
    verify(documentFileStorage, times(1)).createNewVersion("123", "user", newContent);
  }

  @Test
  public void moveDocument() throws IllegalAccessException {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Identity userIdentity = mock(Identity.class);
    AbstractNode abstractNode = mock(AbstractNode.class);
    List<AbstractNode> documents = List.of(abstractNode);
    when(identityManager.getIdentity(anyLong())).thenReturn(userIdentity);
    when(userIdentity.getRemoteId()).thenReturn("user");
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    documentFileService.moveDocuments(1, 1L, documents, "destPath", 1L);
    verify(documentFileStorage, times(1)).moveDocuments(1, 1L, documents, "destPath", identity, 1L);
  }

  @Test
  public void getDocumentsSizeStats() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Exception exception = assertThrows(ObjectNotFoundException.class, () -> {
      documentFileService.getDocumentsSizeStat(1, 1);
    });
    assertEquals("Owner Identity with id : 1 isn't found", exception.getMessage());
    Identity ownerIdentity = mock(Identity.class);
    Identity currentUserIdentity = mock(Identity.class);
    when(identityManager.getIdentity(1)).thenReturn(ownerIdentity);
    when(identityManager.getIdentity(2)).thenReturn(currentUserIdentity);
    when(ownerIdentity.getRemoteId()).thenReturn("user");
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    when(ownerIdentity.isUser()).thenReturn(true);
    when(ownerIdentity.isSpace()).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> {
      documentFileService.getDocumentsSizeStat(1, 2);
    });
    assertEquals("Current user with identity id : 2 attempts to get the size of private documents of user  with identity id  1",
                 exception.getMessage());
    when(ownerIdentity.isUser()).thenReturn(false);
    when(ownerIdentity.isSpace()).thenReturn(true);
    when(identity.getUserId()).thenReturn("user");
    when(currentUserIdentity.getRemoteId()).thenReturn("user");
    when(ownerIdentity.getRemoteId()).thenReturn("test1");
    Space space = new Space();
    space.setPrettyName("test1");
    space.setDisplayName("test1");
    when(spaceService.getSpaceByPrettyName("test1")).thenReturn(space);
    when(spaceService.hasAccessPermission(space, currentUserIdentity.getRemoteId())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> {
      documentFileService.getDocumentsSizeStat(1, 2);
    });
    assertEquals("Current user with identity id : 2 attempts to get size of documents of space with identity id 1 while it's not a member",
                 exception.getMessage());
    when(spaceService.hasAccessPermission(space, currentUserIdentity.getRemoteId())).thenReturn(true);
    documentFileService.getDocumentsSizeStat(1, 2);
    verify(analyticsService, times(1)).retrieveData(any());
  }

  @Test
  public void addDocumentsSizeStat() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Exception exception = assertThrows(ObjectNotFoundException.class, () -> {
      documentFileService.addDocumentsSizeStat(1, 1);
    });
    assertEquals("Owner Identity with id : 1 isn't found", exception.getMessage());
    Identity ownerIdentity = mock(Identity.class);
    Identity currentUserIdentity = mock(Identity.class);
    when(identityManager.getIdentity(1)).thenReturn(ownerIdentity);
    when(identityManager.getIdentity(2)).thenReturn(currentUserIdentity);
    when(ownerIdentity.getRemoteId()).thenReturn("user");
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    when(ownerIdentity.isUser()).thenReturn(true);
    when(ownerIdentity.isSpace()).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> {
      documentFileService.addDocumentsSizeStat(1, 2);
    });
    assertEquals("Current user with identity id : 2 attempts to calculate the size of private documents of user  with identity id  1",
                 exception.getMessage());
    when(ownerIdentity.isUser()).thenReturn(false);
    when(ownerIdentity.isSpace()).thenReturn(true);
    when(identity.getUserId()).thenReturn("user");
    when(currentUserIdentity.getRemoteId()).thenReturn("user");
    when(ownerIdentity.getRemoteId()).thenReturn("test1");
    Space space = new Space();
    space.setPrettyName("test1");
    space.setDisplayName("test1");
    when(spaceService.getSpaceByPrettyName("test1")).thenReturn(space);
    when(spaceService.hasAccessPermission(space, currentUserIdentity.getRemoteId())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> {
      documentFileService.addDocumentsSizeStat(1, 2);
    });
    assertEquals("Current user with identity id : 2 attempts to calculate size of documents of space with identity id 1 while it's not a member",
                 exception.getMessage());
    when(spaceService.hasAccessPermission(space, currentUserIdentity.getRemoteId())).thenReturn(true);
    documentFileService.addDocumentsSizeStat(1, 2);
    verify(documentFileStorage, times(1)).calculateFilesSize(1L, identity);
  }

  @Test
  public void hasEditPermissionOnDocument() throws IllegalAccessException {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Identity userIdentity = mock(Identity.class);
    when(identityManager.getIdentity(anyLong())).thenReturn(userIdentity);
    when(userIdentity.getRemoteId()).thenReturn("user");
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    when(documentFileStorage.hasEditPermissions("123", identity)).thenReturn(true, false);
    assertTrue(documentFileService.hasEditPermissionOnDocument("123", 1L));
    assertFalse(documentFileService.hasEditPermissionOnDocument("123", 1L));
  }

  @Test
  public void importDocuments() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Exception exception = assertThrows(ObjectNotFoundException.class, () -> {
      documentFileService.importFiles("1", null, null, "1", "ignore", identity, 1L);
    });
    assertEquals("Owner Identity with id : 1 isn't found", exception.getMessage());
    Identity ownerIdentity = mock(Identity.class);
    when(identityManager.getIdentity(anyLong())).thenReturn(ownerIdentity);
    when(ownerIdentity.getRemoteId()).thenReturn("user");
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    documentFileService.importFiles("1", null, null, "1", "ignore", identity, 1L);
    verify(documentFileStorage, times(1)).importFiles("1", null, "", null, null, "ignore", identity, "1", 1L);
    when(ownerIdentity.isUser()).thenReturn(true);
    when(ownerIdentity.getRemoteId()).thenReturn("test");
    when(identity.getUserId()).thenReturn("test1");
    exception = assertThrows(IllegalAccessException.class, () -> {
      documentFileService.importFiles("1", null, null, "1", "ignore", identity, 1L);
    });
    assertEquals("User test1 attempts to access private documents of user test", exception.getMessage());
    when(ownerIdentity.isSpace()).thenReturn(true);
    when(identity.getUserId()).thenReturn("user");
    when(ownerIdentity.getRemoteId()).thenReturn("test1");
    Space space = new Space();
    space.setPrettyName("test1");
    space.setDisplayName("test1");
    when(spaceService.getSpaceByPrettyName("test1")).thenReturn(space);
    when(spaceService.hasAccessPermission(space, identity.getUserId())).thenReturn(false);
    exception = assertThrows(IllegalAccessException.class, () -> {
      documentFileService.importFiles("1", null, null, "1", "ignore", identity, 1L);
    });
    assertEquals("User user attempts to access documents of space test1 while it's not a member", exception.getMessage());
  }

  @Test
  public void getDeletedDocumentsTest() throws Exception {
    TrashElementNode trashElementNode = mock(TrashElementNode.class);
    List<TrashElementNode> trashElementNodes = Arrays.asList(trashElementNode);
    when(jcrDeleteFileStorage.getDeletedDocuments(any(TrashElementNodeFilter.class))).thenReturn(trashElementNodes);

    assertEquals(1, documentFileService.getDeletedDocuments(new TrashElementNodeFilter()).size());
  }

  @Test
  public void testGetImageThumbnailContentChecksDocumentAccess() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("user");
    when(identityRegistry.getIdentity("user")).thenReturn(aclIdentity);

    assertThrows(IllegalAccessException.class,
                 () -> documentFileService.getImageThumbnailContent("file", "12", "user", 0, 0));
    verify(imageThumbnailService, never()).getOrCreateThumbnail(anyString(), anyString(), anyString(), anyInt(), anyInt());

    when(documentFileStorage.canAccess("docId", aclIdentity)).thenReturn(true);
    FileItem fileItem = mock(FileItem.class);
    FileInfo fileInfo = new FileInfo(3L, "image.png", "image/png", "documents", 10, new Date(), "user", null, false);
    when(fileItem.getFileInfo()).thenReturn(fileInfo);
    when(imageThumbnailService.getOrCreateThumbnail("imageThumbnail", "docId", "user", 250, 250)).thenReturn(fileItem);
    FileContent thumbnail = documentFileService.getImageThumbnailContent("imageThumbnail", "docId", "user", 250, 250);
    assertEquals("image.png", thumbnail.getName());
  }

  @Test
  public void testGetImageThumbnailContentChecksAnonymousAccess() throws Exception {
    assertThrows(IllegalAccessException.class,
                 () -> documentFileService.getImageThumbnailContent("imageThumbnail", "docId", IdentityConstants.ANONIM, 0, 0));
    verify(authenticator, never()).createIdentity(anyString());
    verify(imageThumbnailService, never()).getOrCreateThumbnail(anyString(), anyString(), anyString(), anyInt(), anyInt());

    when(documentFileStorage.canAccess(eq("docId"),
                                       argThat(identity -> IdentityConstants.ANONIM.equals(identity.getUserId())))).thenReturn(true);
    FileItem fileItem = mock(FileItem.class);
    FileInfo fileInfo = new FileInfo(3L, "image.png", "image/png", "documents", 10, new Date(), "user", null, false);
    when(fileItem.getFileInfo()).thenReturn(fileInfo);
    when(imageThumbnailService.getOrCreateThumbnail("imageThumbnail", "docId", IdentityConstants.ANONIM, 250, 250)).thenReturn(fileItem);
    assertNotNull(documentFileService.getImageThumbnailContent("imageThumbnail", "docId", IdentityConstants.ANONIM, 250, 250));
  }

  /**
   * Two first reads of a preview at the same moment create and store it once:
   * the second waits for the first.
   */
  @Test
  public void testGetImageThumbnailContentJoinsReadsRunningAtTheSameMoment() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("user");
    when(identityRegistry.getIdentity("user")).thenReturn(aclIdentity);
    when(documentFileStorage.canAccess("docId", aclIdentity)).thenReturn(true);
    FileItem fileItem = mock(FileItem.class);
    when(fileItem.getFileInfo()).thenReturn(new FileInfo(3L, "image.png", "image/png", "documents", 10, new Date(), "user", null, false));
    java.util.concurrent.CountDownLatch firstReadRunning = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    when(imageThumbnailService.getOrCreateThumbnail(anyString(), anyString(), anyString(), anyInt(), anyInt())).thenAnswer(invocation -> {
      firstReadRunning.countDown();
      release.await(10, java.util.concurrent.TimeUnit.SECONDS);
      return fileItem;
    });

    java.util.concurrent.ExecutorService readers = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Future<FileContent> first = readers.submit(() -> documentFileService.getImageThumbnailContent("imageThumbnail", "docId", "user", 250, 250));
      assertTrue(firstReadRunning.await(10, java.util.concurrent.TimeUnit.SECONDS));
      Thread[] secondThread = new Thread[1];
      java.util.concurrent.Future<FileContent> second = readers.submit(() -> {
        secondThread[0] = Thread.currentThread();
        return documentFileService.getImageThumbnailContent("imageThumbnail", "docId", "user", 250, 250);
      });
      long deadline = System.currentTimeMillis() + 10_000;
      while ((secondThread[0] == null || secondThread[0].getState() != Thread.State.WAITING) && System.currentTimeMillis() < deadline) {
        Thread.sleep(10); // NOSONAR
      }
      release.countDown();
      assertNotNull(first.get(10, java.util.concurrent.TimeUnit.SECONDS));
      assertNotNull(second.get(10, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
      readers.shutdownNow();
    }
    verify(imageThumbnailService, times(1)).getOrCreateThumbnail("imageThumbnail", "docId", "user", 250, 250);
  }

  /**
   * A read joined to another user's read that fails runs its own read: it gets
   * its own preview, never the other user's failure.
   */
  @Test
  public void testGetImageThumbnailContentJoinedToAFailedReadRunsItsOwnRead() throws Exception {
    for (String user : new String[] { "first", "second" }) {
      org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity(user);
      when(identityRegistry.getIdentity(user)).thenReturn(aclIdentity);
      when(documentFileStorage.canAccess("docId", aclIdentity)).thenReturn(true);
    }
    FileItem fileItem = mock(FileItem.class);
    when(fileItem.getFileInfo()).thenReturn(new FileInfo(3L, "image.png", "image/png", "documents", 10, new Date(), "second", null, false));
    java.util.concurrent.CountDownLatch firstReadRunning = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    when(imageThumbnailService.getOrCreateThumbnail(eq("imageThumbnail"), eq("docId"), eq("first"), anyInt(), anyInt())).thenAnswer(invocation -> {
      firstReadRunning.countDown();
      release.await(10, java.util.concurrent.TimeUnit.SECONDS);
      throw new IllegalAccessException("first may not read it");
    });
    when(imageThumbnailService.getOrCreateThumbnail(eq("imageThumbnail"), eq("docId"), eq("second"), anyInt(), anyInt())).thenReturn(fileItem);

    java.util.concurrent.ExecutorService readers = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Future<FileContent> first = readers.submit(() -> documentFileService.getImageThumbnailContent("imageThumbnail", "docId", "first", 250, 250));
      assertTrue(firstReadRunning.await(10, java.util.concurrent.TimeUnit.SECONDS));
      Thread[] secondThread = new Thread[1];
      java.util.concurrent.Future<FileContent> second = readers.submit(() -> {
        secondThread[0] = Thread.currentThread();
        return documentFileService.getImageThumbnailContent("imageThumbnail", "docId", "second", 250, 250);
      });
      awaitWaiting(secondThread);
      release.countDown();
      java.util.concurrent.ExecutionException firstFailure = assertThrows(java.util.concurrent.ExecutionException.class,
                                                                          () -> first.get(10, java.util.concurrent.TimeUnit.SECONDS));
      assertTrue(firstFailure.getCause() instanceof IllegalAccessException);
      assertNotNull(second.get(10, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
      readers.shutdownNow();
    }
    verify(imageThumbnailService, times(1)).getOrCreateThumbnail("imageThumbnail", "docId", "second", 250, 250);
  }

  /**
   * A joined read interrupted while it waits reports the interruption, not a
   * missing preview.
   */
  @Test
  public void testGetImageThumbnailContentInterruptedWhileJoinedReportsTheInterruption() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("user");
    when(identityRegistry.getIdentity("user")).thenReturn(aclIdentity);
    when(documentFileStorage.canAccess("docId", aclIdentity)).thenReturn(true);
    FileItem fileItem = mock(FileItem.class);
    when(fileItem.getFileInfo()).thenReturn(new FileInfo(3L, "image.png", "image/png", "documents", 10, new Date(), "user", null, false));
    java.util.concurrent.CountDownLatch firstReadRunning = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    when(imageThumbnailService.getOrCreateThumbnail(anyString(), anyString(), anyString(), anyInt(), anyInt())).thenAnswer(invocation -> {
      firstReadRunning.countDown();
      release.await(10, java.util.concurrent.TimeUnit.SECONDS);
      return fileItem;
    });

    java.util.concurrent.ExecutorService readers = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Future<FileContent> first = readers.submit(() -> documentFileService.getImageThumbnailContent("imageThumbnail", "docId", "user", 250, 250));
      assertTrue(firstReadRunning.await(10, java.util.concurrent.TimeUnit.SECONDS));
      Thread[] secondThread = new Thread[1];
      java.util.concurrent.Future<FileContent> second = readers.submit(() -> {
        secondThread[0] = Thread.currentThread();
        return documentFileService.getImageThumbnailContent("imageThumbnail", "docId", "user", 250, 250);
      });
      awaitWaiting(secondThread);
      secondThread[0].interrupt();
      java.util.concurrent.ExecutionException secondFailure = assertThrows(java.util.concurrent.ExecutionException.class,
                                                                           () -> second.get(10, java.util.concurrent.TimeUnit.SECONDS));
      assertTrue(secondFailure.getCause() instanceof InterruptedException);
      release.countDown();
      assertNotNull(first.get(10, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
      readers.shutdownNow();
    }
  }

  private static void awaitWaiting(Thread[] thread) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10_000;
    while ((thread[0] == null || thread[0].getState() != Thread.State.WAITING) && System.currentTimeMillis() < deadline) {
      Thread.sleep(10); // NOSONAR
    }
  }

  /**
   * A preview read that fails throws its own exception, and is not run twice.
   */
  @Test
  public void testGetImageThumbnailContentThrowsTheReadOwnFailure() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("user");
    when(identityRegistry.getIdentity("user")).thenReturn(aclIdentity);
    when(documentFileStorage.canAccess("docId", aclIdentity)).thenReturn(true);
    when(imageThumbnailService.getOrCreateThumbnail(anyString(), anyString(), anyString(), anyInt(), anyInt())).thenThrow(new IllegalArgumentException("no access"));

    assertThrows(IllegalArgumentException.class,
                 () -> documentFileService.getImageThumbnailContent("imageThumbnail", "docId", "user", 250, 250));
    verify(imageThumbnailService, times(1)).getOrCreateThumbnail("imageThumbnail", "docId", "user", 250, 250);
  }

  /**
   * 250x250 is the size every documents client asks for a preview
   * (DocumentsUtils.getThumbnailUrl callers): any other size asked is served
   * and stored at that one size, so a read never stores a preview at a new size.
   */
  @Test
  public void testGetImageThumbnailContentStoresOnlyThePreviewSize() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("user");
    when(identityRegistry.getIdentity("user")).thenReturn(aclIdentity);
    when(documentFileStorage.canAccess("docId", aclIdentity)).thenReturn(true);
    FileItem fileItem = mock(FileItem.class);
    FileInfo fileInfo = new FileInfo(3L, "image.png", "image/png", "documents", 10, new Date(), "user", null, false);
    when(fileItem.getFileInfo()).thenReturn(fileInfo);
    when(imageThumbnailService.getOrCreateThumbnail(anyString(), anyString(), anyString(), anyInt(), anyInt())).thenReturn(fileItem);

    assertNotNull(documentFileService.getImageThumbnailContent("imageThumbnail", "docId", "user", 250, 250));
    verify(imageThumbnailService, times(1)).getOrCreateThumbnail("imageThumbnail", "docId", "user", 250, 250);

    assertNotNull(documentFileService.getImageThumbnailContent("imageThumbnail", "docId", "user", 500, 500));
    assertNotNull(documentFileService.getImageThumbnailContent("imageThumbnail", "docId", "user", 0, 0));
    assertNotNull(documentFileService.getImageThumbnailContent("imageThumbnail", "docId", "user", 250, 99999));
    assertNotNull(documentFileService.getImageThumbnailContent("officeThumbnail", "docId", "user", 1, 1));

    verify(imageThumbnailService, times(4)).getOrCreateThumbnail("imageThumbnail", "docId", "user", 250, 250);
    verify(imageThumbnailService, times(1)).getOrCreateThumbnail("officeThumbnail", "docId", "user", 250, 250);
    verify(imageThumbnailService, never()).getOrCreateThumbnail(anyString(),
                                                                anyString(),
                                                                anyString(),
                                                                intThat(width -> width != 250),
                                                                anyInt());
    verify(imageThumbnailService, never()).getOrCreateThumbnail(anyString(),
                                                                anyString(),
                                                                anyString(),
                                                                anyInt(),
                                                                intThat(height -> height != 250));
  }

  @Test
  public void testCreateThumbnailChecksTypeAndEditPermission() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("user");
    when(identityRegistry.getIdentity("user")).thenReturn(aclIdentity);
    FileContent content = new FileContent("docId", "docId", "image/jpeg", new ByteArrayInputStream(new byte[] { 1 }));
    when(documentFileStorage.hasEditPermissions("docId", aclIdentity)).thenReturn(true);

    IllegalArgumentException typeException = assertThrows(IllegalArgumentException.class,
                                                           () -> documentFileService.createThumbnail("file", content, "user", 250, 250));
    assertEquals("thumbnail_type_not_supported", typeException.getMessage());
    verify(imageThumbnailService, never()).deleteThumbnails(anyString(), anyString());
    verify(imageThumbnailService, never()).createThumbnail(anyString(), any(), anyString(), anyInt(), anyInt());

    when(documentFileStorage.hasEditPermissions("docId", aclIdentity)).thenReturn(false);
    assertThrows(IllegalAccessException.class,
                 () -> documentFileService.createThumbnail("videoThumbnail", content, "user", 250, 250));
    verify(imageThumbnailService, never()).deleteThumbnails(anyString(), anyString());
    verify(imageThumbnailService, never()).createThumbnail(anyString(), any(), anyString(), anyInt(), anyInt());

    when(documentFileStorage.hasEditPermissions("docId", aclIdentity)).thenReturn(true);
    documentFileService.createThumbnail("videoThumbnail", content, "user", 250, 250);
    verify(imageThumbnailService).createThumbnail("videoThumbnaildocId", content, "user", 250, 250);
  }

  @Test
  public void testCreateDocumentFromTemplateDocumentDelegatesWithTheUserAcl() throws Exception {
    FileNode created = new FileNode();
    created.setId("new-1");
    when(documentFileStorage.createDocumentFromTemplateDocument(eq(1L), eq("tpl"), eq("dest"), eq("Report"), any())).thenReturn(created);

    AbstractNode result = documentFileService.createDocumentFromTemplateDocument(1L, "tpl", "dest", "Report", currentOwnerId);

    assertEquals("new-1", result.getId());
    verify(documentFileStorage).createDocumentFromTemplateDocument(eq(1L),
                                                                   eq("tpl"),
                                                                   eq("dest"),
                                                                   eq("Report"),
                                                                   argThat(acl -> userName.equals(acl.getUserId())));
  }

  /**
   * POST /{fileType}/{documentId} takes its size from the query: whatever is
   * asked, the thumbnail is looked up, replaced and stored at the 250x250
   * preview size the read serves, so a new size never adds a stored copy.
   */
  @Test
  public void testCreateThumbnailStoresOnlyThePreviewSize() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("user");
    when(identityRegistry.getIdentity("user")).thenReturn(aclIdentity);
    when(documentFileStorage.hasEditPermissions("docId", aclIdentity)).thenReturn(true);
    FileContent content = new FileContent("docId", "docId", "image/jpeg", new ByteArrayInputStream(new byte[] { 1 }));
    when(imageThumbnailService.getThumbnail("videoThumbnaildocId", 250, 250)).thenReturn(mock(FileItem.class));

    documentFileService.createThumbnail("videoThumbnail", content, "user", 500, 99999);
    documentFileService.createThumbnail("videoThumbnail", content, "user", 0, 0);

    verify(imageThumbnailService, times(2)).getThumbnail("videoThumbnaildocId", 250, 250);
    verify(imageThumbnailService, times(2)).deleteThumbnails("videoThumbnail", "docId");
    verify(imageThumbnailService, times(2)).createThumbnail("videoThumbnaildocId", content, "user", 250, 250);
    verify(imageThumbnailService, never()).getThumbnail(anyString(), intThat(width -> width != 250), anyInt());
    verify(imageThumbnailService, never()).getThumbnail(anyString(), anyInt(), intThat(height -> height != 250));
    verify(imageThumbnailService, never()).createThumbnail(anyString(),
                                                           any(),
                                                           anyString(),
                                                           intThat(width -> width != 250),
                                                           anyInt());
    verify(imageThumbnailService, never()).createThumbnail(anyString(),
                                                           any(),
                                                           anyString(),
                                                           anyInt(),
                                                           intThat(height -> height != 250));
  }

  @Test
  public void testUpdatePermissionsRefusedWhenItGivesEveryoneTheDocumentOfASpaceKeepingItsDocumentsInside() throws Exception {
    assertSharingRefused(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS), Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "any"));
  }

  @Test
  public void testUpdatePermissionsRefusedWhenItSharesWithAnotherSpace() throws Exception {
    assertSharingRefused(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS), Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "*:/spaces/other"));
  }

  @Test
  public void testUpdatePermissionsRefusedWhenItSharesWithTheManagersOfASubSpace() throws Exception {
    // A sub-space has a group of its own, outside its parent space
    assertSharingRefused(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS),
                         Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "manager:/spaces/team_sub", "redactor:/spaces/team_sub"));
  }

  @Test
  public void testUpdatePermissionsRefusedWhenItSharesWithAPlatformGroup() throws Exception {
    assertSharingRefused(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS), Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "*:/platform/users"));
  }

  @Test
  public void testUpdatePermissionsRefusedWhenItSharesWithAUserWhoIsNotAMember() throws Exception {
    SpaceSecurityService spaceSecurityService = assertSharingRefused(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS),
                                                                     Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "outsider"));
    verify(spaceSecurityService).getCommittedMembers(7L, Set.of("outsider"));
  }

  /**
   * Nobody is exempt: the space's manager, who is also a platform
   * administrator, is refused as any member is.
   */
  @Test
  public void testUpdatePermissionsRefusedToTheSpaceManagerAndPlatformAdministrator() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS),
                                                                    Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "any"));
    when(spaceSecurityService.getMemberStatuses(anyLong(), anyString())).thenReturn(Set.of(io.meeds.social.space.constant.SpaceMembershipStatus.MANAGER));
    when(spaceSecurityService.canManageSpace(any(), anyString())).thenReturn(true);

    assertThrows(DocumentSharingRefusedException.class, () -> documentFileService.updatePermissions("123", request(), 1L));

    verify(documentFileStorage, never()).updatePermissions(anyString(), any(), any());
  }

  @Test
  public void testUpdatePermissionsAllowedWhenItSharesWithAMember() throws Exception {
    keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS), Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "member"));

    NodePermission nodePermission = request();
    documentFileService.updatePermissions("123", nodePermission, 1L);

    verify(documentFileStorage).updatePermissions(eq("123"), eq(nodePermission), any());
  }

  @Test
  public void testUpdatePermissionsAllowedWhenItRemovesOneOfTwoSharesOutsideTheSpace() throws Exception {
    keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "*:/spaces/other", "outsider"),
                        Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "outsider"));

    NodePermission nodePermission = request();
    documentFileService.updatePermissions("123", nodePermission, 1L);

    verify(documentFileStorage).updatePermissions(eq("123"), eq(nodePermission), any());
  }

  /**
   * A share outside the space the stored access list already holds is kept
   * and shared again, while an entry inside the space is edited.
   */
  @Test
  public void testUpdatePermissionsAllowedWhenItEditsAnInsideEntryOfADocumentSharedOutside() throws Exception {
    keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "*:/spaces/other", "member"),
                        Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "*:/spaces/other", "member"));
    shareDestination(5L, SpaceIdentityProvider.NAME, "other", "/spaces/other");

    NodePermission nodePermission = request();
    nodePermission.setToShare(Map.of(5L, "read"));
    documentFileService.updatePermissions("123", nodePermission, 1L);

    verify(documentFileStorage).updatePermissions(eq("123"), eq(nodePermission), any());
    verify(documentFileStorage).shareDocument(eq("123"), eq(5L), any(), anyBoolean());
  }

  @Test
  public void testUpdatePermissionsAllowedWhenItKeepsEveryoneTheDocumentAlreadyGives() throws Exception {
    keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "any"), Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "any"));

    NodePermission nodePermission = request();
    documentFileService.updatePermissions("123", nodePermission, 1L);

    verify(documentFileStorage).updatePermissions(eq("123"), eq(nodePermission), any());
  }

  /**
   * A share destination the request does not list among its entries is still
   * decided: its share would place the document in the other space's drive.
   */
  @Test
  public void testUpdatePermissionsRefusedWhenItSharesWithASpaceTheAccessListDoesNotHold() throws Exception {
    keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS), Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS));
    shareDestination(5L, SpaceIdentityProvider.NAME, "other", "/spaces/other");
    NodePermission nodePermission = request();
    nodePermission.setToShare(Map.of(5L, "read"));

    DocumentSharingRefusedException refusal = assertThrows(DocumentSharingRefusedException.class,
                                                           () -> documentFileService.updatePermissions("123", nodePermission, 1L));

    assertEquals(DocumentSharingRefusedException.KEPT_INSIDE_SHARE, refusal.getMessageCode());
    verify(documentFileStorage, never()).updatePermissions(anyString(), any(), any());
    verify(documentFileStorage, never()).shareDocument(anyString(), anyLong(), any(), anyBoolean());
  }

  @Test
  public void testUpdatePermissionsDecidesOnTheCommittedSettingsReadOnce() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS),
                                                                    Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "member"));
    shareDestination(5L, OrganizationIdentityProvider.NAME, "member", null);
    shareDestination(6L, SpaceIdentityProvider.NAME, "team", "/spaces/team");
    NodePermission nodePermission = request();
    nodePermission.setToShare(Map.of(5L, "read", 6L, "read"));

    documentFileService.updatePermissions("123", nodePermission, 1L);

    verify(spaceSecurityService, times(1)).getSettings(7L);
    verify(spaceSecurityService, never()).getDisplaySettings(any());
    verify(spaceSecurityService, times(1)).getCommittedMembers(anyLong(), anyCollection());
    verify(documentFileStorage).updatePermissions(eq("123"), eq(nodePermission), any());
  }

  @Test
  public void testUpdatePermissionsAllowedWhenTheSpaceDoesNotKeepItsDocumentsInside() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER), Set.of(OWNER, "any"));
    when(spaceSecurityService.isDocumentsKeptInside(any())).thenReturn(false);

    NodePermission nodePermission = request();
    documentFileService.updatePermissions("123", nodePermission, 1L);

    verify(documentFileStorage, never()).getAccessChange(anyString(), any(), any());
    verify(documentFileStorage).updatePermissions(eq("123"), eq(nodePermission), any());
  }

  @Test
  public void testUpdatePermissionsOfADocumentOutsideEverySpaceIsNotDecided() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER), Set.of(OWNER, "any"));
    when(documentFileStorage.getDocumentSpace("123")).thenReturn(null);

    NodePermission nodePermission = request();
    documentFileService.updatePermissions("123", nodePermission, 1L);

    verify(spaceSecurityService, never()).getSettings(anyLong());
    verify(documentFileStorage).updatePermissions(eq("123"), eq(nodePermission), any());
  }

  @Test
  public void testUpdatePermissionsWritesNothingWhenTheStoredAccessCannotBeRead() throws Exception {
    keepDocumentsInside(Set.of(OWNER), Set.of(OWNER));
    when(documentFileStorage.getAccessChange(anyString(), any(), any())).thenThrow(new IllegalStateException("unreadable"));

    assertThrows(IllegalStateException.class, () -> documentFileService.updatePermissions("123", request(), 1L));

    verify(documentFileStorage, never()).updatePermissions(anyString(), any(), any());
  }

  @Test
  public void testShareDocumentRefusedWhenTheDestinationIsOutsideAndNotInTheAccessList() throws Exception {
    keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS), Set.of());
    shareDestination(5L, OrganizationIdentityProvider.NAME, "outsider", null);

    DocumentSharingRefusedException refusal = assertThrows(DocumentSharingRefusedException.class,
                                                           () -> documentFileService.shareDocument("123", 5L, 1L, false));

    assertEquals(DocumentSharingRefusedException.KEPT_INSIDE_SHARE, refusal.getMessageCode());
    assertEquals(DocumentSharingRefusedException.CHANGE_PERMISSIONS_ACTION, refusal.getAction());
    verify(documentFileStorage).getAccessChange(eq("123"), any(), isNull());
    verify(documentFileStorage, never()).shareDocument(anyString(), anyLong(), any(), anyBoolean());
  }

  @Test
  public void testShareDocumentAllowedWhenTheAccessListHoldsTheDestination() throws Exception {
    keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "manager:/spaces/other"), Set.of());
    shareDestination(5L, SpaceIdentityProvider.NAME, "other", "/spaces/other");

    documentFileService.shareDocument("123", 5L, 1L, false);

    verify(documentFileStorage).shareDocument(eq("123"), eq(5L), any(), eq(false));
  }

  @Test
  public void testShareDocumentAllowedWhenTheDestinationIsAMember() throws Exception {
    keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS), Set.of());
    shareDestination(5L, OrganizationIdentityProvider.NAME, "member", null);

    documentFileService.shareDocument("123", 5L, 1L, true);

    verify(documentFileStorage).shareDocument(eq("123"), eq(5L), any(), eq(true));
  }

  @Test
  public void testShareDocumentRefusedWhenTheDestinationIsUnknown() throws Exception {
    keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS), Set.of());

    assertThrows(DocumentSharingRefusedException.class, () -> documentFileService.shareDocument("123", 404L, 1L, false));

    verify(documentFileStorage, never()).shareDocument(anyString(), anyLong(), any(), anyBoolean());
  }

  @Test
  public void testCheckCanCreatePublicLinkRefusedWhenTheSpaceKeepsItsDocumentsInside() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER), Set.of());

    DocumentSharingRefusedException refusal = assertThrows(DocumentSharingRefusedException.class,
                                                           () -> documentFileService.checkCanCreatePublicLink("123", 1L));

    assertEquals(DocumentSharingRefusedException.KEPT_INSIDE_PUBLIC_LINK, refusal.getMessageCode());
    assertEquals(DocumentSharingRefusedException.CREATE_PUBLIC_LINK_ACTION, refusal.getAction());
    assertEquals(7L, refusal.getSpaceId());
    verify(spaceSecurityService, times(1)).getSettings(7L);
  }

  @Test
  public void testCheckCanCreatePublicLinkAllowedWhenTheSpaceDoesNotKeepItsDocumentsInside() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER), Set.of());
    when(spaceSecurityService.isDocumentsKeptInside(any())).thenReturn(false);

    documentFileService.checkCanCreatePublicLink("123", 1L);
  }

  /**
   * With both settings on, "Limit Access Management" refuses a user it
   * refuses first, under its own code.
   */
  @Test
  public void testCheckCanCreatePublicLinkRefusesOnLimitedAccessManagementFirst() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER), Set.of());
    when(spaceSecurityService.canManageDocumentsAccess(any(), any(), eq("username"))).thenReturn(false);

    DocumentSharingRefusedException refusal = assertThrows(DocumentSharingRefusedException.class,
                                                           () -> documentFileService.checkCanCreatePublicLink("123", 1L));

    assertEquals(DocumentSharingRefusedException.ACCESS_MANAGEMENT_LIMITED, refusal.getMessageCode());
  }

  @Test
  public void testIsKeptInsideSpace() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER), Set.of());
    assertTrue(documentFileService.isKeptInsideSpace("123"));

    when(spaceSecurityService.isDocumentsKeptInside(any())).thenReturn(false);
    assertFalse(documentFileService.isKeptInsideSpace("123"));
    verify(spaceSecurityService, never()).getDisplaySettings(any());

    when(documentFileStorage.getDocumentSpace("123")).thenReturn(null);
    assertFalse(documentFileService.isKeptInsideSpace("123"));
  }

  @Test
  public void testIsKeptInsideSpaceWhenItsSettingsCannotBeRead() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER), Set.of());
    when(spaceSecurityService.isDocumentsKeptInside(any())).thenReturn(false);

    when(spaceSecurityService.getSettings(7L)).thenThrow(new ObjectNotFoundException("gone"));
    assertTrue(documentFileService.isKeptInsideSpace("123"));

    when(documentFileStorage.getDocumentSpace("123")).thenThrow(new IllegalStateException("unreadable"));
    assertTrue(documentFileService.isKeptInsideSpace("123"));
  }

  /**
   * While the space keeps its documents inside, a restore keeps the current
   * access list, whoever restores: the version's may hold an entry outside the
   * space removed since.
   */
  @Test
  public void testRestoreVersionKeepsTheAccessListWhileTheSpaceKeepsItsDocumentsInside() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER), Set.of());

    documentFileService.restoreVersion("123", "username");
    verify(documentFileStorage).restoreVersion("123", "username", true);
    verify(spaceSecurityService, times(1)).getSettings(7L);

    when(spaceSecurityService.isDocumentsKeptInside(any())).thenReturn(false);
    documentFileService.restoreVersion("123", "username");
    verify(documentFileStorage).restoreVersion("123", "username", false);
  }

  /**
   * A user who cannot edit the document is refused before anything of its
   * stored access or of its space's settings is read: the answer is the same
   * whatever its access list and its space's settings.
   */
  @Test
  public void testChangingAccessIsRefusedToANonEditorBeforeAnyRead() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER, TEAM_MEMBERS, "*:/spaces/other"),
                                                                    Set.of(OWNER, TEAM_MEMBERS, "*:/spaces/other"));
    when(documentFileStorage.hasEditPermissions(eq("123"), any())).thenReturn(false);
    shareDestination(5L, SpaceIdentityProvider.NAME, "other", "/spaces/other");

    IllegalAccessException refusal = assertThrows(IllegalAccessException.class,
                                                  () -> documentFileService.updatePermissions("123", request(), 1L));
    assertFalse(refusal instanceof DocumentSharingRefusedException);
    assertThrows(IllegalAccessException.class, () -> documentFileService.shareDocument("123", 5L, 1L, false));
    assertThrows(IllegalAccessException.class, () -> documentFileService.shareDocument("123", 404L, 1L, false));

    verify(documentFileStorage, never()).getAccessChange(anyString(), any(), any());
    verify(documentFileStorage, never()).getDocumentSpace(anyString());
    verify(spaceSecurityService, never()).getSettings(anyLong());
    verify(documentFileStorage, never()).updatePermissions(anyString(), any(), any());
    verify(documentFileStorage, never()).shareDocument(anyString(), anyLong(), any(), anyBoolean());
  }

  /**
   * A save writes back as "*:" a membership of an outside group stored as
   * "member:" (a task attachment's grant), or a stored "manager:" as managers,
   * redactors and publishers: a group the stored list already reaches is
   * kept, while a group it does not reach is refused.
   */
  @Test
  public void testUpdatePermissionsKeepsAnOutsideGroupTheStoredListReachesInAnotherForm() throws Exception {
    keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "member:/spaces/q", "manager:/spaces/r"),
                        Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "member", "*:/spaces/q",
                               "manager:/spaces/r", "redactor:/spaces/r", "publisher:/spaces/r"));
    NodePermission nodePermission = request();

    documentFileService.updatePermissions("123", nodePermission, 1L);
    verify(documentFileStorage).updatePermissions(eq("123"), eq(nodePermission), any());
  }

  @Test
  public void testUpdatePermissionsRefusesAGroupTheStoredListDoesNotReach() throws Exception {
    // a group whose id starts like a stored one is another group
    assertSharingRefused(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "member:/spaces/q"),
                         Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "*:/spaces/q", "*:/spaces/qq"));
  }

  /**
   * A group is matched whole: a stored membership of a group whose id only
   * ends with the requested group's id keeps nothing, as an entry or as a share
   * destination. Mutant: the ':' dropped from the matched suffix.
   */
  @Test
  public void testUpdatePermissionsMatchesAStoredGroupWhole() throws Exception {
    assertSharingRefused(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "member:/partners/spaces/q"),
                         Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "member:/partners/spaces/q", "*:/spaces/q"));
  }

  @Test
  public void testShareDocumentMatchesAStoredGroupWhole() throws Exception {
    keepDocumentsInside(Set.of(OWNER, ADMINISTRATORS, TEAM_MEMBERS, "member:/partners/spaces/q"), Set.of());
    shareDestination(5L, SpaceIdentityProvider.NAME, "q", "/spaces/q");

    DocumentSharingRefusedException refusal = assertThrows(DocumentSharingRefusedException.class,
                                                           () -> documentFileService.shareDocument("123", 5L, 1L, false));

    assertEquals(DocumentSharingRefusedException.KEPT_INSIDE_SHARE, refusal.getMessageCode());
    verify(documentFileStorage, never()).shareDocument(anyString(), anyLong(), any(), anyBoolean());
  }

  @Test
  public void testGetSpaceAccessReadsTheSettingsOnce() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER), Set.of());

    DocumentSpaceAccess spaceAccess = documentFileService.getSpaceAccess("123", 1L);
    assertTrue(spaceAccess.canManageAccess());
    assertTrue(spaceAccess.keptInsideSpace());
    verify(spaceSecurityService, times(1)).getSettings(7L);
    verify(documentFileStorage, times(1)).getDocumentSpace("123");

    when(spaceSecurityService.canManageDocumentsAccess(any(), any(), eq("username"))).thenReturn(false);
    when(spaceSecurityService.isDocumentsKeptInside(any())).thenReturn(false);
    spaceAccess = documentFileService.getSpaceAccess("123", 1L);
    assertFalse(spaceAccess.canManageAccess());
    assertFalse(spaceAccess.keptInsideSpace());

    when(documentFileStorage.getDocumentSpace("123")).thenReturn(null);
    spaceAccess = documentFileService.getSpaceAccess("123", 1L);
    assertTrue(spaceAccess.canManageAccess());
    assertFalse(spaceAccess.keptInsideSpace());

    when(documentFileStorage.getDocumentSpace("123")).thenThrow(new IllegalStateException("unreadable"));
    spaceAccess = documentFileService.getSpaceAccess("123", 1L);
    assertFalse(spaceAccess.canManageAccess());
    assertTrue(spaceAccess.keptInsideSpace());
  }

  @Test
  public void testGetSpaceAccessOfAGoneSpaceRefusesBoth() throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(Set.of(OWNER), Set.of());
    when(spaceSecurityService.getSettings(7L)).thenThrow(new ObjectNotFoundException("gone"));

    DocumentSpaceAccess spaceAccess = documentFileService.getSpaceAccess("123", 1L);
    assertFalse(spaceAccess.canManageAccess());
    assertTrue(spaceAccess.keptInsideSpace());
  }

  private SpaceSecurityService assertSharingRefused(Set<String> storedIdentities, Set<String> requestedIdentities) throws Exception {
    SpaceSecurityService spaceSecurityService = keepDocumentsInside(storedIdentities, requestedIdentities);
    Space space = documentFileStorage.getDocumentSpace("123");
    NodePermission nodePermission = request();

    DocumentSharingRefusedException refusal = assertThrows(DocumentSharingRefusedException.class,
                                                           () -> documentFileService.updatePermissions("123", nodePermission, 1L));

    assertEquals(DocumentSharingRefusedException.KEPT_INSIDE_SHARE, refusal.getMessageCode());
    assertEquals(7L, refusal.getSpaceId());
    assertEquals("123", refusal.getDocumentId());
    assertEquals(DocumentSharingRefusedException.CHANGE_PERMISSIONS_ACTION, refusal.getAction());
    verify(documentFileStorage).getAccessChange("123", space, nodePermission);
    verify(documentFileStorage, never()).updatePermissions(anyString(), any(), any());
    verify(documentFileStorage, never()).shareDocument(anyString(), anyLong(), any(), anyBoolean());
    return spaceSecurityService;
  }

  /**
   * Document 123 lies in space 7 (group /spaces/team), whose committed
   * settings keep its documents inside and let the user manage access. Its
   * stored access list is the given one, a save of the request writes the
   * given identities, its owner is "owner", its space's drive root holds the
   * space's members and the platform administrators, and "member" is the one
   * enabled member among the users asked for.
   */
  private SpaceSecurityService keepDocumentsInside(Set<String> storedIdentities, Set<String> requestedIdentities) throws Exception {
    Identity socialIdentity = new Identity(OrganizationIdentityProvider.NAME, "username");
    socialIdentity.setId("1");
    when(identityManager.getIdentity(1L)).thenReturn(socialIdentity);
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("username");
    when(identityRegistry.getIdentity("username")).thenReturn(aclIdentity);
    Space space = new Space();
    space.setId(7L);
    space.setGroupId("/spaces/team");
    when(documentFileStorage.getDocumentSpace("123")).thenReturn(space);
    SpaceSecuritySettings settings = new SpaceSecuritySettings();
    settings.setKeepDocumentsInside(true);
    SpaceSecurityService spaceSecurityService = mock(SpaceSecurityService.class);
    when(spaceSecurityService.getSettings(7L)).thenReturn(settings);
    when(spaceSecurityService.canManageDocumentsAccess(space, settings, "username")).thenReturn(true);
    when(spaceSecurityService.isDocumentsKeptInside(settings)).thenReturn(true);
    when(spaceSecurityService.getCommittedMembers(eq(7L), anyCollection())).thenAnswer(invocation -> {
      Collection<String> usernames = invocation.getArgument(1);
      return usernames.contains("member") ? Set.of("member") : Set.of();
    });
    when(documentFileStorage.getAccessChange(eq("123"), eq(space), any())).thenReturn(new DocumentAccessChange(storedIdentities,
                                                                                                              requestedIdentities,
                                                                                                              OWNER,
                                                                                                              Set.of(TEAM_MEMBERS,
                                                                                                                     ADMINISTRATORS)));
    documentFileService = spy(documentFileService);
    doReturn(spaceSecurityService).when(documentFileService).getSpaceSecurityService();
    return spaceSecurityService;
  }

  /** A request sharing with nobody, as the drawer sends it */
  private static NodePermission request() {
    NodePermission nodePermission = new NodePermission();
    nodePermission.setToShare(new HashMap<>());
    return nodePermission;
  }

  private void shareDestination(long identityId, String providerId, String remoteId, String groupId) {
    Identity destination = new Identity(providerId, remoteId);
    destination.setId(String.valueOf(identityId));
    when(identityManager.getIdentity(identityId)).thenReturn(destination);
    if (groupId != null) {
      Space destinationSpace = new Space();
      destinationSpace.setPrettyName(remoteId);
      destinationSpace.setGroupId(groupId);
      when(spaceService.getSpaceByPrettyName(remoteId)).thenReturn(destinationSpace);
    }
  }

}
