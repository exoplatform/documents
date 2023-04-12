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

package org.exoplatform.documents.rest;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.*;

import javax.jcr.RepositoryException;
import javax.ws.rs.core.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.rest.model.*;
import org.exoplatform.documents.rest.util.EntityBuilder;
import org.exoplatform.documents.rest.util.RestUtils;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.documents.service.DocumentFileServiceImpl;
import org.exoplatform.documents.service.DocumentWebSocketService;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.Metadata;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataObject;
import org.exoplatform.social.metadata.model.MetadataType;

@RunWith(MockitoJUnitRunner.Silent.class)
public class DocumentFileRestTest {

  private static MockedStatic<RestUtils>     REST_UTILS     = mockStatic(RestUtils.class);

  private static MockedStatic<EntityBuilder> ENTITY_BUILDER = mockStatic(EntityBuilder.class);

  private DocumentFileStorage                      documentFileStorage;

  private IdentityManager                          identityManager;

  private SpaceService                             spaceService;

  private IdentityRegistry                         identityRegistry;

  private MetadataService                          metadataService;

  private Authenticator                            authenticator;

  private DocumentFileServiceImpl                  documentFileService;

  private DocumentFileRest                         documentFileRest;

  private JCRDeleteFileStorage                     jcrDeleteFileStorage;

  private ListenerService                          listenerService;

  private SettingService                           settingService;

  private DocumentWebSocketService documentWebSocketService;

  @Before
  public void setUp() {
    spaceService = mock(SpaceService.class);
    identityManager = mock(IdentityManager.class);
    identityRegistry = mock(IdentityRegistry.class);
    metadataService = mock(MetadataService.class);
    authenticator = mock(Authenticator.class);
    documentFileStorage = mock(DocumentFileStorage.class);
    jcrDeleteFileStorage = mock(JCRDeleteFileStorage.class);
    listenerService = mock(ListenerService.class);
    settingService = mock(SettingService.class);
    documentWebSocketService = mock(DocumentWebSocketService.class);
    documentFileService = new DocumentFileServiceImpl(documentFileStorage,
                                                      jcrDeleteFileStorage,
                                                      authenticator,
                                                      spaceService,
                                                      identityManager,
                                                      identityRegistry,
                                                      listenerService);
    documentFileRest = new DocumentFileRest(documentFileService,
                                            spaceService,
                                            identityManager,
                                            metadataService,
                                            settingService,
                                            documentWebSocketService);
  }

  @After
  public void teardown() throws Exception { // NOSONAR
    if (REST_UTILS != null) {
      REST_UTILS.close();
      REST_UTILS = null;
    }
    if (ENTITY_BUILDER != null) {
      ENTITY_BUILDER.close();
      ENTITY_BUILDER = null;
    }
  }

  @Test
  public void testGetDocumentItems() throws Exception {
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
    DocumentTimelineFilter filter = null;
    filter = new DocumentTimelineFilter(Long.valueOf(currentIdentity.getId()));
    filter.setFavorites(false);
    when(identityRegistry.getIdentity(username)).thenReturn(userID);

    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(username))).thenReturn(currentIdentity);

    String spacePrettyName = "spacetest";
    currentIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
    space.setPrettyName(spacePrettyName);
    org.exoplatform.services.security.Identity spaceID = new org.exoplatform.services.security.Identity(spacePrettyName);
    when(identityRegistry.getIdentity(spacePrettyName)).thenReturn(spaceID);
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);
    when(spaceService.hasAccessPermission(space, username)).thenReturn(true);

    FileVersionsEntity fileVersionsEntity = new FileVersionsEntity();
    fileVersionsEntity.setSize(50);
    fileVersionsEntity.setLimit(0);
    fileVersionsEntity.setOffset(0);

    FileNodeEntity fileEntities = new FileNodeEntity();
    fileEntities.setVersions(fileVersionsEntity);
    fileEntities.hashCode();

    List<AbstractNodeEntity> nodesEntities = new ArrayList<>();
    nodesEntities.add(fileEntities);

    List<FileNode> files = new ArrayList<>();

    FileNode file1 = new FileNode();
    file1.setLinkedFileId("1");
    file1.setVersionnedFileId("1");
    file1.setMimeType(":file");
    file1.setSize(50);

    FileNode file2 = new FileNode();
    FileNode file3 = new FileNode();
    FileNode file4 = new FileNode();

    files.add(file1);
    files.add(file2);
    files.add(file3);
    files.add(file4);

    when(documentFileStorage.getFilesTimeline(filter, spaceID, 0, 0)).thenReturn(files);

    Response response1 = documentFileRest.getDocumentItems(null,
                                                           null,
                                                           null,
                                                           null,
                                                           FileListingType.TIMELINE,
                                                           null,
                                                           false,
                                                           null,
                                                           false,
                                                           "",
                                                           null,
                                                           false,
                                                           0,
                                                           0,
                                                           false);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());

    Response response2 = documentFileRest.getDocumentItems(currentOwnerId,
                                                           null,
                                                           null,
                                                           null,
                                                           null,
                                                           null,
                                                           false,
                                                           null,
                                                           false,
                                                           "",
                                                           null,
                                                           false,
                                                           0,
                                                           0,
                                                           false);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response2.getStatus());

    Response response3 = documentFileRest.getDocumentItems(currentOwnerId,
                                                           null,
                                                           null,
                                                           null,
                                                           FileListingType.TIMELINE,
                                                           null,
                                                           false,
                                                           null,
                                                           false,
                                                           "",
                                                           null,
                                                           false,
                                                           0,
                                                           0,
                                                           false);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response3.getStatus());

    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);
    List<AbstractNode> files_ = new ArrayList<>();
    files_ = documentFileService.getDocumentItems(FileListingType.TIMELINE, filter, 0, 0, Long.valueOf(currentIdentity.getId()),false);

    FileNodeEntity nodeEntity = new FileNodeEntity();
    nodeEntity.setLinkedFileId("1");
    nodeEntity.setVersionnedFileId("1");
    nodeEntity.setMimeType(":file");
    nodeEntity.setSize(50);

    assertEquals(files_.size(), 4);

    List<MetadataItem> metadataItems = new ArrayList<>();
    MetadataType metadataType = new MetadataType();
    metadataType.setId(1);
    metadataType.setName("favorites");
    MetadataObject metadataObject = new MetadataObject();
    metadataObject.setId("7694af9cc0a80104095f8da1bf22a7fb");
    metadataObject.setType("file");
    Metadata metadata = new Metadata();
    metadata.setId(1);
    metadata.setType(metadataType);
    metadata.setName("1");
    metadata.setAudienceId(2);
    metadata.setCreatorId(2);
    MetadataItem metadataItem = new MetadataItem();
    metadataItem.setMetadata(metadata);
    metadataItem.setObjectId(metadataObject.getId());
    metadataItem.setCreatorId(2);
    metadataItems.add(metadataItem);
    when(metadataService.getMetadataItemsByObject(any())).thenReturn(metadataItems);
    Response response4 = documentFileRest.getDocumentItems(currentOwnerId,
                                                           null,
                                                           null,
                                                           null,
                                                           FileListingType.TIMELINE,
                                                           null,
                                                           false,
                                                           null,
                                                           false,
                                                           "metadatas",
                                                           null,
                                                           false,
                                                           0,
                                                           0,
                                                           false);
    assertEquals(Response.Status.OK.getStatusCode(), response4.getStatus());
    List<FileNodeEntity> filesNodeEntity = new ArrayList<>();
    filesNodeEntity = (List<FileNodeEntity>) response4.getEntity();
    assertNotNull(filesNodeEntity);
    assertNotNull(filesNodeEntity.get(0).hashCode());
    assertNotNull(filesNodeEntity.get(0).toString());
    assertEquals(filesNodeEntity.get(0).getMimeType(), ":file");
    assertEquals(filesNodeEntity.get(0).getVersionnedFileId(), "1");
    assertEquals(filesNodeEntity.get(0).getSize(), 50);
    assertTrue(filesNodeEntity.get(0).isFavorite());
    FileNodeEntity fileNodeEntity = filesNodeEntity.get(0);
    fileNodeEntity.setMetadatas(null);
    fileNodeEntity.setFavorite(false);
    assertTrue(nodeEntity.equals(fileNodeEntity));

  }

  @Test
  public void testGETDocumentGroupsCount() throws Exception {
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
    DocumentTimelineFilter filter = null;
    filter = new DocumentTimelineFilter(Long.valueOf(currentIdentity.getId()));
    filter.setFavorites(false);
    when(identityRegistry.getIdentity(username)).thenReturn(userID);

    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(username))).thenReturn(currentIdentity);

    String spacePrettyName = "spacetest";
    currentIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
    space.setPrettyName(spacePrettyName);
    org.exoplatform.services.security.Identity spaceID = new org.exoplatform.services.security.Identity(spacePrettyName);
    when(identityRegistry.getIdentity(spacePrettyName)).thenReturn(spaceID);
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);
    when(spaceService.hasAccessPermission(space, username)).thenReturn(true);

    DocumentGroupsSize documentGroupsSize = new DocumentGroupsSize();
    documentGroupsSize.setThisDay(4);

    when(documentFileService.getGroupDocumentsCount(filter, currentOwnerId)).thenReturn(documentGroupsSize);

    Response response1 = documentFileRest.getDocumentGroupsCount(null, "", null, false);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());

    Response response2 = documentFileRest.getDocumentGroupsCount(currentOwnerId, "", null, false);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response2.getStatus());

    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);

    Response response4 = documentFileRest.getDocumentGroupsCount(currentOwnerId, "", null, false);
    assertEquals(Response.Status.OK.getStatusCode(), response4.getStatus());

    assertEquals(((DocumentGroupsSize) response4.getEntity()).getThisDay(), 4);

    when(documentFileRest.getDocumentGroupsCount(currentOwnerId, "", null, false)).thenThrow(ObjectNotFoundException.class);
    Response response = documentFileRest.getDocumentGroupsCount(currentOwnerId, "", null, false);
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    when(documentFileRest.getDocumentGroupsCount(currentOwnerId, "", null, false)).thenThrow(RuntimeException.class);
    response =  documentFileRest.getDocumentGroupsCount(currentOwnerId, "", null, false);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  public void testGetDocumentFolder() throws Exception {
    String username = "usera";
    org.exoplatform.services.security.Identity root = new org.exoplatform.services.security.Identity(username);
    ConversationState.setCurrent(new ConversationState(root));
    long currentOwnerId = 2;
    Identity currentIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);
    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(currentOwnerId);

    String userbname = "userb";
    long userId = 3;
    Identity userIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    userIdentity.setId(String.valueOf(userId));
    Profile userProfile = new Profile();
    userProfile.setProperty(Profile.FULL_NAME, userbname);
    userIdentity.setProfile(userProfile);
    when(identityManager.getOrCreateUserIdentity(userbname)).thenReturn(userIdentity);

    String spacePrettyName = "spacetest";
    String groupId = "/spaces/spacetest";
    currentIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
    space.setPrettyName(spacePrettyName);
    org.exoplatform.services.security.Identity spaceID = new org.exoplatform.services.security.Identity(spacePrettyName);
    when(identityRegistry.getIdentity(spacePrettyName)).thenReturn(spaceID);
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);
    when(spaceService.getSpaceByGroupId(groupId)).thenReturn(space);
    when(spaceService.hasAccessPermission(space, username)).thenReturn(true);


    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    when(identityManager.getIdentity(String.valueOf(userId))).thenReturn(userIdentity);
    when(identityManager.getOrCreateSpaceIdentity(spacePrettyName)).thenReturn(currentIdentity);



    DocumentFolderFilter filter = null;
    filter = new DocumentFolderFilter(null, null, currentOwnerId, null);
    filter.setFavorites(false);
    filter.setSortField(DocumentSortField.NAME);
    IdentityEntity identity1 = new IdentityEntity();
    IdentityEntity identity2 = new IdentityEntity("3", "userb", "userb", null, "organization", "spacetest");
    identity1.setId("2");
    identity1.setName("usera");
    identity1.setFullname("usera");
    identity1.setAvatar(null);
    identity1.setProviderId("organization");
    identity1.setRemoteId("spacetest");

    NodeAuditTrailItemEntity nodeAuditTrailItemEntity = new NodeAuditTrailItemEntity();
    nodeAuditTrailItemEntity.setId(11);
    nodeAuditTrailItemEntity.setActionType("actionType");
    nodeAuditTrailItemEntity.setUserIdentity(identity1);
    nodeAuditTrailItemEntity.setTargetIdentity(identity2);
    nodeAuditTrailItemEntity.setDate(1111);
    nodeAuditTrailItemEntity.hashCode();
    List<NodeAuditTrailItemEntity> trails = new ArrayList<>();
    trails.add(nodeAuditTrailItemEntity);

    NodeAuditTrailsEntity nodeAuditTrailsEntity = new NodeAuditTrailsEntity();
    nodeAuditTrailsEntity.setSize(50);
    nodeAuditTrailsEntity.setLimit(0);
    nodeAuditTrailsEntity.setOffset(0);
    nodeAuditTrailsEntity.setSize(50);
    nodeAuditTrailsEntity.setTrails(trails);
    nodeAuditTrailsEntity.hashCode();
    AbstractNodeEntity documentEntities = new AbstractNodeEntity(true);
    documentEntities.setId("2");
    documentEntities.setName("document");
    documentEntities.setDescription("description");
    documentEntities.setDatasource("datasource");
    documentEntities.setParentFolderId("1");
    documentEntities.setCreatorIdentity(identity1);
    documentEntities.setModifierIdentity(identity2);
    documentEntities.setCreatedDate(1111);
    documentEntities.setModifiedDate(2222);
    documentEntities.setFolder(true);
    documentEntities.setAuditTrails(nodeAuditTrailsEntity);

    List<AbstractNodeEntity> nodesEntities = new ArrayList<>();
    nodesEntities.add(documentEntities);

    List<AbstractNode> folder = new ArrayList<>();

    AbstractNode folder1 = new FolderNode();
    AbstractNode folder2 = new FolderNode();
    folder1.setId("2");
    folder1.setName("folder1");
    folder1.setCreatorId(currentOwnerId);
    folder1.setDatasource("datasource");
    folder1.setDescription("description");
    folder1.setCreatedDate(11111);
    folder1.setParentFolderId("1");
    folder1.setPath("/Groups/spaces/spacetest");

    PermissionEntry permissionEntry1 = new PermissionEntry(currentIdentity, "add_node", PermissionRole.ALL.name());
    PermissionEntry permissionEntry2 = new PermissionEntry(currentIdentity, "set_property", PermissionRole.ALL.name());
    PermissionEntry permissionEntry3 = new PermissionEntry(currentIdentity, "read", PermissionRole.ALL.name());
    PermissionEntry permissionEntry4 = new PermissionEntry(currentIdentity, "remove", PermissionRole.ALL.name());
    PermissionEntry permissionEntry5 = new PermissionEntry(userIdentity, "add_node", PermissionRole.ALL.name());
    PermissionEntry permissionEntry6 = new PermissionEntry(userIdentity, "set_property", PermissionRole.ALL.name());
    List<PermissionEntry> permissionEntries = new ArrayList<>();
    permissionEntries.add(permissionEntry1);
    permissionEntries.add(permissionEntry2);
    permissionEntries.add(permissionEntry3);
    permissionEntries.add(permissionEntry4);
    permissionEntries.add(permissionEntry5);
    permissionEntries.add(permissionEntry6);
    NodePermission nodePermission = new NodePermission(true, true, true, permissionEntries, null, null);
    folder1.setAcl(nodePermission);

    FolderNodeEntity folderEntity = new FolderNodeEntity();
    folderEntity.setId("2");
    folderEntity.setName("folder1");
    folderEntity.setCreatorIdentity(identity1);
    folderEntity.setDatasource("datasource");
    folderEntity.setDescription("description");
    folderEntity.setCreatedDate(11111);
    folderEntity.setParentFolderId("1");
    List<PermissionEntryEntity> collaborators = new ArrayList<>();
    PermissionEntryEntity permissionEntryEntity = new PermissionEntryEntity();
    PermissionEntryEntity permissionEntryEntity1 = new PermissionEntryEntity(EntityBuilder.toIdentityEntity(identityManager,spaceService,Long.valueOf(userIdentity.getId())),"set_property");
    permissionEntryEntity.setPermission("add_node");
    permissionEntryEntity.setIdentity(EntityBuilder.toIdentityEntity(identityManager,spaceService,Long.valueOf(userIdentity.getId())));
    collaborators.add(permissionEntryEntity1);
    collaborators.add(permissionEntryEntity);
    NodePermissionEntity nodePermissionEntity = new NodePermissionEntity();
    nodePermissionEntity.setCanAccess(true);
    nodePermissionEntity.setCanDelete(true);
    nodePermissionEntity.setCanEdit(true);
    nodePermissionEntity.setAllMembersCanEdit(true);
    nodePermissionEntity.setVisibilityChoice(Visibility.ALL_MEMBERS.name());
    nodePermissionEntity.setCollaborators(collaborators);
    folderEntity.setAcl(nodePermissionEntity);

    String expand = "creator";

    folder.add(folder1);
    folder.add(folder2);

    when(documentFileStorage.getFolderChildNodes(filter, spaceID, 0, 0)).thenReturn(folder);

    Response response1 = documentFileRest.getDocumentItems(null,
                                                           null,
                                                           null,
                                                           null,
                                                           FileListingType.FOLDER,
                                                           null,
                                                           false,
                                                           null,
                                                           false,
                                                           "",
                                                           null,
                                                           false,
                                                           0,
                                                           0,
                                                           false);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());

    Response response2 = documentFileRest.getDocumentItems(currentOwnerId,
                                                           "2",
                                                           null,
                                                           null,
                                                           FileListingType.FOLDER,
                                                           null,
                                                           false,
                                                           null,
                                                           false,
                                                           "",
                                                           null,
                                                           false,
                                                           0,
                                                           0,
                                                           false);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());

    Response response3 = documentFileRest.getDocumentItems(currentOwnerId,
                                                           null,
                                                           null,
                                                           null,
                                                           FileListingType.FOLDER,
                                                           null,
                                                           false,
                                                           null,
                                                           false,
                                                           expand,
                                                           null,
                                                           false,
                                                           0,
                                                           0,
                                                           false);
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());

    List<FolderNodeEntity> foldersNodeEntity = new ArrayList<>();
    foldersNodeEntity = (List<FolderNodeEntity>) response3.getEntity();
    assertNotNull(foldersNodeEntity);
    assertNotNull(foldersNodeEntity.get(0).hashCode());
    assertNotNull(foldersNodeEntity.get(0).toString());
    assertEquals(foldersNodeEntity.get(0).getName(), "folder1");
    assertEquals(foldersNodeEntity.get(0).getCreatorIdentity(), identity1);
    assertEquals(foldersNodeEntity.get(0).getDatasource(), "datasource");
    assertEquals(foldersNodeEntity.get(0).getDescription(), "description");
    assertEquals(foldersNodeEntity.get(0).getCreatedDate(), 11111);
    assertEquals(foldersNodeEntity.get(0).getParentFolderId(), "1");
    NodePermissionEntity nodePermissionEntity1 = foldersNodeEntity.get(0).getAcl();
    assertEquals(nodePermissionEntity1.getVisibilityChoice(), Visibility.SPECIFIC_COLLABORATOR.name());
    assertEquals(nodePermissionEntity1.isAllMembersCanEdit(), false);
    assertEquals(nodePermissionEntity1.isCanAccess(), true);
    assertEquals(nodePermissionEntity1.isCanDelete(), true);
    assertEquals(nodePermissionEntity1.isCanDelete(), true);
    assertEquals(nodePermissionEntity1.getCollaborators().size(), 1);
    assertEquals(nodePermissionEntity1.getCollaborators().get(0).getPermission(), "edit");
    assertEquals(nodePermissionEntity1.getCollaborators().get(0).getIdentity().getId(), String.valueOf(userId));
  }

  @Test
  public void testGetBreadCrumbs() throws Exception {
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

    BreadCrumbItem breadCrumbItem1 = new BreadCrumbItem("1", "Folder1", "", false,new HashMap<>());
    BreadCrumbItem breadCrumbItem2 = new BreadCrumbItem("2", "Folder2", "", false,new HashMap<>());
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

    List<BreadCrumbItemEntity> breadCrumbItemEntities = new ArrayList<>();

    when(documentFileStorage.getBreadcrumb(2, "Folder1", "", userID)).thenReturn(breadCrumbItems);

    Response response1 = documentFileRest.getBreadcrumb(null,
            null,"");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());

    Response response2 = documentFileRest.getBreadcrumb(Long.valueOf(2), "Folder1", "");

    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response2.getStatus());

    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);
    Response response3 = documentFileRest.getBreadcrumb(Long.valueOf(2), "Folder1", "");
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
    breadCrumbItemEntities = (List<BreadCrumbItemEntity>) response3.getEntity();
    assertEquals(breadCrumbItemEntities.size(), 4);
    assertEquals(breadCrumbItemEntities.get(0).getId(), "4");
    assertEquals(breadCrumbItemEntities.get(0).getName(), "Folder4");

    when(documentFileRest.getBreadcrumb(1L, "123", "")).thenThrow(ObjectNotFoundException.class);
    Response response = documentFileRest.getBreadcrumb(1L, "123", "");
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    when(documentFileRest.getBreadcrumb(1L, "123", "")).thenThrow(RuntimeException.class);
    response = documentFileRest.getBreadcrumb(1L, "123", "");
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  public void testDuplicateDocument() throws Exception {
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

    FileNode file1 = new FileNode();
    file1.setId("1");
    file1.setName("oldFile");
    file1.setDatasource("datasource");
    file1.setMimeType(":file");
    file1.setSize(50);

    FileNode file2 = new FileNode();
    file2.setId("2");
    file2.setName("Copy of oldFile");
    file2.setDatasource("datasource");
    file2.setMimeType(":file");
    file2.setSize(50);

    when(documentFileStorage.duplicateDocument(2, "oldFile", "copy of", userID)).thenReturn(file2);

    Response response1 = documentFileRest.duplicateDocument(null,
                                                            null,
                                                            "copy of",
                                                            "");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());

    Response response2 = documentFileRest.duplicateDocument(Long.valueOf(2), "oldFile", "copy of", "");

    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response2.getStatus());

    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);
    Response response3 = documentFileRest.duplicateDocument(Long.valueOf(2), "oldFile", "copy of", "");
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
    AbstractNodeEntity fileNode = (AbstractNodeEntity) response3.getEntity();
    assertEquals(fileNode.getName(), "Copy of oldFile");
    assertEquals(fileNode.getId(), "2");

    when(documentFileRest.duplicateDocument(1L, "oldFile", "copy of", "")).thenThrow(ObjectNotFoundException.class);
    Response response = documentFileRest.duplicateDocument(1L, "oldFile", "copy of", "");
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    when(documentFileRest.duplicateDocument(1L, "oldFile", "copy of", "")).thenThrow(RuntimeException.class);
    response = documentFileRest.duplicateDocument(1L, "oldFile", "copy of", "");
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());

  }

  @Test
  public void testCreateFolder() throws Exception {
    String username = "testuser";
    org.exoplatform.services.security.Identity root = new org.exoplatform.services.security.Identity(username);
    ConversationState.setCurrent(new ConversationState(root));
    long currentOwnerId = 2;
    Identity currentIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(currentOwnerId);

    List<FullTreeItem> children = new ArrayList<>();
    FullTreeItem fullTreeItem = new FullTreeItem("11111222", "test", "path", null);
    children.add(fullTreeItem);

    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(username);

    when(identityRegistry.getIdentity(username)).thenReturn(userID);
    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);

    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);

    Response response = documentFileRest.createFolder(null, null, null, "");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("either_ownerId_or_parentid_is_mandatory", response.getEntity());

    response = documentFileRest.createFolder("11111111", null, null, "");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("Folder Name should not be empty", response.getEntity());

    AbstractNode folder = new FolderNode();
    when(documentFileStorage.createFolder(2L, "11111111", null, "222", userID)).thenReturn(folder);
    response = documentFileRest.createFolder("11111111", null, 2L, "222");
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    when(documentFileStorage.createFolder(2L, "11111111", null, "test", userID)).thenReturn(folder);
    response = documentFileRest.createFolder("11111111", null, 2L, "test");
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

    when(documentFileStorage.createFolder(2L, "11111111", null, "222", userID)).thenThrow(IllegalAccessException.class);
    response = documentFileRest.createFolder("11111111", null, 2L, "222");
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());

    when(documentFileRest.createFolder("11111111", null, 2L, "test")).thenThrow(RuntimeException.class);
    response = documentFileRest.createFolder("11111111", null, 2L, "test");
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());

    response = documentFileRest.renameDocument(null, null, "");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("either_ownerId_or_documentID_is_mandatory", response.getEntity());

    response = documentFileRest.renameDocument("11111111", 2L, "");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("Document Name should not be empty", response.getEntity());

    doNothing().when(documentFileStorage).renameDocument(2L, "11111111", "renameTest", userID);
    Response response1 = documentFileRest.renameDocument("11111111", 2L, "renameTest");
    assertEquals(Response.Status.OK.getStatusCode(), response1.getStatus());

    when(documentFileStorage.getFullTreeData(2L, "11111111", userID)).thenReturn(children);
    Response response2 = documentFileRest.getFullTreeData(2L, "11111111");
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());

    Response response3 = documentFileRest.moveDocument(null, null, "/Groups/spaces/test/Documents/test", null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response3.getStatus());
    assertEquals("either_ownerId_or_documentID_is_mandatory", response3.getEntity());

    Response response4 = documentFileRest.moveDocument("11111111", 2L, null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response4.getStatus());

    doNothing().when(documentFileStorage).moveDocument(2L, "11111111", "/Groups/spaces/test/Documents/test", userID, "keepBoth");
    Response response5 = documentFileRest.moveDocument("11111111", 2L, "/Groups/spaces/test/Documents/test", "keepBoth");
    assertEquals(Response.Status.OK.getStatusCode(), response5.getStatus());

    when(documentFileRest.moveDocument("11111111",2L,"/Groups/spaces/test/Documents/test", null)).thenThrow(RuntimeException.class);
    response = documentFileRest.moveDocument("11111111", 2L, "/Groups/spaces/test/Documents/test", null);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());

  }

  @Test
  public void testDeleteDocument() {
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
    when(identityManager.getIdentity((String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);

    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);
    FileNode file1 = new FileNode();
    file1.setId("1");
    file1.setName("oldFile");
    file1.setPath("/document/oldFile");
    file1.setDatasource("datasource");
    file1.setMimeType(":file");
    file1.setSize(50);

    Response response = documentFileRest.deleteDocument(null, "/document/oldFile", false, 6);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("document_id_is_mandatory", response.getEntity());

    doNothing().when(jcrDeleteFileStorage).deleteDocument("1", "/document/oldFile", false, true, 6, root, currentOwnerId);
    response = documentFileRest.deleteDocument("1", "/document/oldFile", false, 6);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void testDeleteDocuments() throws IllegalAccessException {
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
    when(identityManager.getIdentity((String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);

    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(currentOwnerId);
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

    Response response = documentFileRest.bulkDeleteDocuments(123456, nodeEntities);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("documents list is mandatory", response.getEntity());

    nodeEntities.add(file1);
    nodeEntities.add(file2);
    nodeEntities.add(file3);

    List<AbstractNode> nodes = EntityBuilder.toAbstractNodes(nodeEntities);

    doNothing().when(jcrDeleteFileStorage).deleteDocuments(123456, nodes, userID, currentOwnerId);
    response = documentFileRest.bulkDeleteDocuments(123456, nodeEntities);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void testDownloadDocuments() {
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
    when(identityManager.getIdentity((String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);

    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(currentOwnerId);
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

    Response response = documentFileRest.downloadDocuments(123456, nodeEntities);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("documents list is mandatory", response.getEntity());

    nodeEntities.add(file1);
    nodeEntities.add(file2);
    nodeEntities.add(file3);

    List<AbstractNode> nodes = EntityBuilder.toAbstractNodes(nodeEntities);

    doNothing().when(documentFileStorage).downloadDocuments(123456, nodes, userID, currentOwnerId);
    response = documentFileRest.downloadDocuments(123456, nodeEntities);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void testGetDownloadZip() throws IOException {
    String username = "testuser";
    long currentOwnerId = 2;
    Identity currentIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(username);
    when(identityRegistry.getIdentity(username)).thenReturn(userID);
    when(identityManager.getIdentity((String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentity(identityManager)).thenReturn(currentIdentity);
    when(documentFileStorage.getDownloadZipBytes(123456, username)).thenReturn(null);
    Response response = documentFileRest.getDownloadZip(123456);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void testUndoDeleteDocument() {
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
    when(identityManager.getIdentity((String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);

    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);
    FileNode file1 = new FileNode();
    file1.setId("2");
    file1.setName("oldFile");
    file1.setPath("/document/oldFile");
    file1.setDatasource("datasource");
    file1.setMimeType(":file");
    file1.setSize(50);

    Response response = documentFileRest.undoDeleteDocument(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("document_id_is_mandatory", response.getEntity());

    doNothing().when(jcrDeleteFileStorage).undoDelete("2", currentOwnerId);
    response = documentFileRest.undoDeleteDocument("2");
    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
  }

  @Test
  public void testUpdatePermissions() throws Exception {
    DocumentFileService documentFileService = mock(DocumentFileService.class);
    DocumentFileRest documentFileRest1 =
                                       new DocumentFileRest(documentFileService,
                                                            spaceService,
                                                            identityManager,
                                                            metadataService,
                                                            settingService,
                                                            documentWebSocketService);

    FileNodeEntity nodeEntity = new FileNodeEntity();
    NodePermission nodePermission = mock(NodePermission.class);
    NodePermissionEntity nodePermissionEntity = new NodePermissionEntity();
    Response response = documentFileRest1.updatePermissions(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    Response response1 = documentFileRest1.updatePermissions(nodeEntity);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());
    nodeEntity.setAcl(nodePermissionEntity);
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    nodeEntity.setId("123");
    mockEntityBuilder().when(() -> EntityBuilder.toNodePermission(nodeEntity, documentFileService, spaceService, identityManager))
                  .thenReturn(nodePermission);
    doNothing().when(documentFileService).updatePermissions("123", nodePermission, 1L);
    Response response2 = documentFileRest1.updatePermissions(nodeEntity);
    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response2.getStatus());
    doThrow(new IllegalAccessException()).when(documentFileService).updatePermissions("123", nodePermission, 1L);
    Response response3 = documentFileRest1.updatePermissions(nodeEntity);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response3.getStatus());
    //assert forbidden response status when the share documents forbidden by administrators
    when(settingService.get(Context.GLOBAL.id("sharedDocumentStatus"),
                            Scope.APPLICATION.id("sharedDocumentStatus"),
                            "exo:sharedDocumentStatus")).thenReturn((SettingValue) SettingValue.create("true"));
    Response response4 = documentFileRest1.updatePermissions(nodeEntity);
    assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response4.getStatus());

  }

  @Test
  public void testCreateShortcut() throws Exception {
    mockRestUtils().when(() -> RestUtils.getCurrentUser()).thenReturn("user");
    DocumentFileService documentFileService = mock(DocumentFileService.class);
    DocumentFileRest documentFileRest1 = new DocumentFileRest(documentFileService,
                                                              spaceService,
                                                              identityManager,
                                                              metadataService,
                                                              settingService,
                                                              documentWebSocketService);

    Response response = documentFileRest1.createShortcut(null, null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("Document's id should not be empty", response.getEntity());
    response = documentFileRest1.createShortcut("11111111", null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("Document destination path should not be empty", response.getEntity());

    doNothing().when(documentFileStorage).createShortcut("11111111", "/Groups/spaces/test/Documents/test", "user", null);
    response = documentFileRest.createShortcut("11111111", "/Groups/spaces/test/Documents/test", null);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());


    when(documentFileRest.createShortcut("11111111", "/Groups/spaces/test/Documents/test", null)).thenThrow(RuntimeException.class);
    response = documentFileRest.createShortcut("11111111", "/Groups/spaces/test/Documents/test", null);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  public void getFileVersions() {
    FileVersion fileVersion = new FileVersion();
    fileVersion.setCurrent(true);
    fileVersion.setTitle("test.docx");
    fileVersion.setVersionNumber(1);
    fileVersion.setId("4ezadazd465az4d");
    fileVersion.setCreatedDate(new Date());
    fileVersion.setAuthorFullName("user user");
    fileVersion.setAuthor("user");
    List<FileVersion> versions = new ArrayList<>();
    versions.add(fileVersion);
    Response response = documentFileRest.getFileVersions(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    mockRestUtils().when(() -> RestUtils.getCurrentUser()).thenReturn("user");
    response = documentFileRest.getFileVersions("3654654651");
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(documentFileService.getFileVersions("655645ezfefzef6z54", "user")).thenReturn(versions);
    response = documentFileRest.getFileVersions("qsdqs54dq65sd");
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    when(documentFileRest.getFileVersions("qsdqs54dq65sd")).thenThrow(IllegalArgumentException.class);
    response = documentFileRest.getFileVersions("qsdqs54dq65sd");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    when(documentFileRest.getFileVersions("qsdqs54dq65sd")).thenThrow(RuntimeException.class);
    response = documentFileRest.getFileVersions("qsdqs54dq65sd");
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  public void getNewName() throws ObjectNotFoundException, ObjectAlreadyExistsException, IllegalAccessException {
    Response response = documentFileRest.getNewName(null, "patg", 1L, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    response = documentFileRest.getNewName("123", "patg", 1L, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    response = documentFileRest.getNewName("123", "patg", 1L, "125");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    when(documentFileService.getNewName(1L, "123", "path", "test")).thenReturn("new");
    response = documentFileRest.getNewName("123", "path", 1L, "test");
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    when(documentFileService.getNewName(1L, "123", "path", "test")).thenThrow(RuntimeException.class);
    response = documentFileRest.getNewName("123", "path", 1L, "test");
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  public void updateDocumentDescription() throws IllegalAccessException, RepositoryException {
    DocumentFileService documentFileService1 = mock(DocumentFileService.class);
    DocumentFileRest documentFileRest1 = new DocumentFileRest(documentFileService1,
                                                              spaceService,
                                                              identityManager,
                                                              metadataService,
                                                              settingService,
                                                              documentWebSocketService);
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    doNothing().when(documentFileService1).updateDocumentDescription(1L, "123", "hello", 1L);
    Response response = documentFileRest1.updateDocumentDescription(1L, "123", "hello");
    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    doThrow(new RuntimeException()).when(documentFileService1).updateDocumentDescription(1L, "123", "hello", 1L);
    response = documentFileRest1.updateDocumentDescription(1L, "123", "hello");
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  public void getFullTreeData() {
    DocumentFileService documentFileService1 = mock(DocumentFileService.class);
    DocumentFileRest documentFileRest1 = new DocumentFileRest(documentFileService1,
                                                              spaceService,
                                                              identityManager,
                                                              metadataService,
                                                              settingService,
                                                              documentWebSocketService);
    Response response = documentFileRest1.getFullTreeData(null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    when(documentFileRest1.getFullTreeData(1L, "123")).thenThrow(IllegalAccessException.class);
    response = documentFileRest1.getFullTreeData(1L, "123");
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    when(documentFileRest1.getFullTreeData(1L, "123")).thenThrow(ObjectNotFoundException.class);
    response = documentFileRest1.getFullTreeData(1L, "123");
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    when(documentFileRest1.getFullTreeData(1L, "123")).thenThrow(RuntimeException.class);
    response = documentFileRest1.getFullTreeData(1L, "123");
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  public void updateVersionSummary() {
    mockRestUtils().when(() -> RestUtils.getCurrentUser()).thenReturn("user");
    Map<String, String> summary = new HashMap<>();
    summary.put("value", "test");
    FileVersion fileVersion = new FileVersion();
    fileVersion.setCurrent(true);
    fileVersion.setTitle("test.docx");
    fileVersion.setVersionNumber(1);
    fileVersion.setId("4ezadazd465az4d");
    fileVersion.setCreatedDate(new Date());
    fileVersion.setAuthorFullName("user user");
    fileVersion.setAuthor("user");
    DocumentFileService documentFileService1 = mock(DocumentFileService.class);
    DocumentFileRest documentFileRest1 = new DocumentFileRest(documentFileService1,
                                                              spaceService,
                                                              identityManager,
                                                              metadataService,
                                                              settingService,
                                                              documentWebSocketService);
    Response response = documentFileRest1.updateVersionSummary(summary, null, null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    response = documentFileRest1.updateVersionSummary(summary, "1225", null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    response = documentFileRest1.updateVersionSummary(summary, "123", "123336");
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(documentFileService1.updateVersionSummary(anyString(), anyString(), anyString(), anyString())).thenReturn(fileVersion);
    response = documentFileRest1.updateVersionSummary(summary, "123", "123336");
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    when(documentFileService1.updateVersionSummary(anyString(),
                                                   anyString(),
                                                   anyString(),
                                                   anyString())).thenThrow(IllegalArgumentException.class);
    response = documentFileRest1.updateVersionSummary(summary, "123", "123336");
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }

  @Test
  public void shouldThrowServerErrorWhenUpdateSummary() {
    mockRestUtils().when(() -> RestUtils.getCurrentUser()).thenReturn("user");
    Map<String, String> summary = new HashMap<>();
    summary.put("value", "test");
    DocumentFileService documentFileService1 = mock(DocumentFileService.class);
    DocumentFileRest documentFileRest1 = new DocumentFileRest(documentFileService1,
                                                              spaceService,
                                                              identityManager,
                                                              metadataService,
                                                              settingService,
                                                              documentWebSocketService);
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(documentFileService1.updateVersionSummary(anyString(),
                                                   anyString(),
                                                   anyString(),
                                                   anyString())).thenThrow(RuntimeException.class);
    Response response = documentFileRest1.updateVersionSummary(summary, "123", "123336");
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  public void restoreVersion() {
    mockRestUtils().when(() -> RestUtils.getCurrentUser()).thenReturn("user");
    FileVersion fileVersion = new FileVersion();
    fileVersion.setCurrent(true);
    fileVersion.setTitle("test.docx");
    fileVersion.setVersionNumber(1);
    fileVersion.setId("4ezadazd465az4d");
    fileVersion.setCreatedDate(new Date());
    fileVersion.setAuthorFullName("user user");
    fileVersion.setAuthor("user");
    DocumentFileService documentFileService1 = mock(DocumentFileService.class);
    DocumentFileRest documentFileRest1 = new DocumentFileRest(documentFileService1,
                                                              spaceService,
                                                              identityManager,
                                                              metadataService,
                                                              settingService,
                                                              documentWebSocketService);
    Response response = documentFileRest1.restoreVersion(null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(0L);
    response = documentFileRest1.restoreVersion("123");
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(1L);
    when(documentFileService1.restoreVersion(anyString(), anyString())).thenReturn(fileVersion);
    response = documentFileRest1.restoreVersion("123");
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    doThrow(new RuntimeException()).when(documentFileService1).restoreVersion(anyString(), anyString());
    response = documentFileRest1.restoreVersion("123");
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  public void testRenameDocumentWithExistTitle() throws Exception {
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(2L);
    DocumentFileService documentFileService1 = mock(DocumentFileService.class);
    DocumentFileRest documentFileRest1 = new DocumentFileRest(documentFileService1,
                                                              spaceService,
                                                              identityManager,
                                                              metadataService,
                                                              settingService,
                                                              documentWebSocketService);
    doThrow(new ObjectAlreadyExistsException("exist")).when(documentFileService1).renameDocument(1L, "123", "test", 2L);
    Response response = documentFileRest1.renameDocument("123", 1L, "test");
    assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
  }

  @Test
  public void testMoveWithExistTitle() throws Exception {
    mockRestUtils().when(() -> RestUtils.getCurrentUserIdentityId(identityManager)).thenReturn(2L);
    DocumentFileService documentFileService1 = mock(DocumentFileService.class);
    DocumentFileRest documentFileRest1 = new DocumentFileRest(documentFileService1,
                                                              spaceService,
                                                              identityManager,
                                                              metadataService,
                                                              settingService,
                                                              documentWebSocketService);
    doThrow(new ObjectAlreadyExistsException("exist")).when(documentFileService1).moveDocument(1L, "123", "test", 2L, "");
    Response response = documentFileRest1.moveDocument("123", 1L, "test", "");
    assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
  }

  private MockedStatic<RestUtils> mockRestUtils() {
    if (REST_UTILS == null) {
      REST_UTILS = mockStatic(RestUtils.class);
    }
    return REST_UTILS;
  }

  private MockedStatic<EntityBuilder> mockEntityBuilder() {
    if (ENTITY_BUILDER == null) {
      ENTITY_BUILDER = mockStatic(EntityBuilder.class);
    }
    return ENTITY_BUILDER;
  }

}
