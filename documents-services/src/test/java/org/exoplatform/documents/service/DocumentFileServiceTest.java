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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.listener.AttachmentsActivityCacheUpdater;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;

import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.IdentityRegistry;

import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.storage.api.ActivityStorage;
import org.exoplatform.social.core.storage.cache.CachedActivityStorage;
import org.junit.Before;
import org.junit.Test;

public class DocumentFileServiceTest {

  private DocumentFileStorage     documentFileStorage;

  private IdentityManager         identityManager;

  private SpaceService            spaceService;

  private IdentityRegistry        identityRegistry;

  private Authenticator           authenticator;

  private DocumentFileServiceImpl documentFileService;

  private JCRDeleteFileStorage    jcrDeleteFileStorage;

  private ActivityStorage         activityStorage;

  private ListenerService         listenerService;

  private CachedActivityStorage   cachedActivityStorage;

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
    activityStorage = mock(ActivityStorage.class);
    listenerService = mock(ListenerService.class);
    jcrDeleteFileStorage = mock(JCRDeleteFileStorage.class);
    cachedActivityStorage = mock(CachedActivityStorage.class);
    documentFileService = new DocumentFileServiceImpl(documentFileStorage,
                                                      jcrDeleteFileStorage,
                                                      authenticator,
                                                      spaceService,
                                                      identityManager,
                                                      identityRegistry,
                                                      listenerService);

    currentIdentity = new Identity(OrganizationIdentityProvider.NAME, userName);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(userName);
    when(identityRegistry.getIdentity(userName)).thenReturn(userID);
    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(userName))).thenReturn(currentIdentity);
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
      documentFileService.getDocumentItems(FileListingType.TIMELINE, null, 0, 0, Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(), "File filter is mandatory");

    filter = new DocumentTimelineFilter(0L);
    DocumentTimelineFilter finalFilter1 = filter;
    exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.TIMELINE, finalFilter1, 0, 0, Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(), "OwnerId is mandatory");

    filter = new DocumentTimelineFilter(Long.valueOf(currentIdentity.getId()));
    DocumentTimelineFilter finalFilter = filter;
    exception = assertThrows(IllegalAccessException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.TIMELINE, finalFilter, 0, 0, 0);
    });
    assertEquals(exception.getMessage(), "User Identity is mandatory");

    DocumentFolderFilter docFilter = new DocumentFolderFilter("", "", 0L);
    DocumentFolderFilter finalDocFilter = docFilter;
    exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.TIMELINE, finalDocFilter, 0, 0, Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(), "filter must be an instance of DocumentTimelineFilter");

    exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.FOLDER, finalFilter, 0, 0, Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(), "filter must be an instance of DocumentFolderFilter");

    exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.FOLDER, finalDocFilter, 0, 0, Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(), "ParentFolderId or OwnerId is mandatory");

    when(identityRegistry.getIdentity(userName)).thenReturn(userID);
    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
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
    List<AbstractNode> files_ = new ArrayList<>();
    files_ = documentFileService.getDocumentItems(FileListingType.TIMELINE, filter, 0, 0, Long.parseLong(currentIdentity.getId()));
    assertEquals(files_.size(), 4);
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
    DocumentTimelineFilter filter = new DocumentTimelineFilter(Long.valueOf(currentIdentity.getId()));

    when(identityRegistry.getIdentity(username)).thenReturn(userID);
    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(username))).thenReturn(currentIdentity);

    DocumentTimelineFilter filter_ = new DocumentTimelineFilter(0L);
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
    DocumentTimelineFilter filter = new DocumentTimelineFilter(Long.valueOf(currentIdentity.getId()));

    when(identityRegistry.getIdentity(username)).thenReturn(userID);
    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(username))).thenReturn(currentIdentity);

    DocumentTimelineFilter filter_ = new DocumentTimelineFilter(0L);
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
    long spaceId = 4;
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
    DocumentFolderFilter filter = new DocumentFolderFilter(null, null, Long.valueOf(currentIdentity.getId()));

    when(identityRegistry.getIdentity(username)).thenReturn(userID);
    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(username))).thenReturn(currentIdentity);

    when(identityRegistry.getIdentity(user2name)).thenReturn(user2ID);
    when(identityManager.getIdentity(eq(String.valueOf(user2Id)))).thenReturn(user2Identity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(user2name))).thenReturn(user2Identity);

    DocumentFolderFilter filter_ = new DocumentFolderFilter(null, null, 0L);
    DocumentFolderFilter finalFilter1 = filter_;
    Exception exception = assertThrows(ObjectNotFoundException.class, () -> {
      documentFileService.getFolderChildNodes(finalFilter1, 0, 0, Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(), "Owner Identity with id : " + finalFilter1.getOwnerId() + " isn't found");

    String spacePrettyName = "spacetest";

    Space space = new Space();
    org.exoplatform.services.security.Identity spaceID = new org.exoplatform.services.security.Identity(spacePrettyName);

    Identity spaceIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    spaceIdentity.setId(String.valueOf(spaceId));
    spaceIdentity.setRemoteId(spacePrettyName);
    spaceIdentity.setProviderId(SpaceIdentityProvider.NAME);

    when(identityRegistry.getIdentity(spacePrettyName)).thenReturn(spaceID);
    when(identityManager.getIdentity(eq(String.valueOf(spaceId)))).thenReturn(spaceIdentity);
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);
    when(spaceService.hasAccessPermission(space, username)).thenReturn(true);
    when(spaceService.hasAccessPermission(space, user2name)).thenReturn(false);

    filter_ = new DocumentFolderFilter(null, null, Long.valueOf(spaceIdentity.getId()));
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
    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);

    BreadCrumbItem breadCrumbItem1 = new BreadCrumbItem("1", "Folder1", "");
    BreadCrumbItem breadCrumbItem2 = new BreadCrumbItem("2", "Folder2", "");
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
  public void testUpdatePermissions() throws IllegalAccessException {
    NodePermission nodePermission = new NodePermission();
    Map<Long, String> toShare = new HashMap<>();
    toShare.put(1L, "read");
    nodePermission.setToShare(toShare);
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Identity socialIdentity = mock(Identity.class);
    when(identityRegistry.getIdentity("username")).thenReturn(identity);
    when(socialIdentity.getRemoteId()).thenReturn("username");
    when(identityManager.getIdentity("1")).thenReturn(socialIdentity);
    documentFileService.updatePermissions("123", nodePermission, 1L);
    verify(documentFileStorage, times(1)).updatePermissions("123", nodePermission, identity);
    verify(documentFileStorage, times(1)).shareDocument("123", 1L);
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
}
