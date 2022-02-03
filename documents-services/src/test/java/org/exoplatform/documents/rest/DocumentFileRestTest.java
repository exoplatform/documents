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
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import org.junit.Before;
import org.junit.Test;

import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.rest.model.*;
import org.exoplatform.documents.service.DocumentFileServiceImpl;
import org.exoplatform.documents.storage.DocumentFileStorage;
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

public class DocumentFileRestTest {

  private DocumentFileStorage     documentFileStorage;

  private IdentityManager         identityManager;

  private SpaceService            spaceService;

  private IdentityRegistry        identityRegistry;

  private MetadataService         metadataService;

  private Authenticator           authenticator;

  private DocumentFileServiceImpl documentFileService;

  private DocumentFileRest        documentFileRest;

  @Before
  public void setUp() {
    spaceService = mock(SpaceService.class);
    identityManager = mock(IdentityManager.class);
    identityRegistry = mock(IdentityRegistry.class);
    metadataService = mock(MetadataService.class);
    authenticator = mock(Authenticator.class);
    documentFileStorage = mock(DocumentFileStorage.class);
    documentFileService = new DocumentFileServiceImpl(documentFileStorage,
                                                      authenticator,
                                                      spaceService,
                                                      identityManager,
                                                      identityRegistry);
    documentFileRest = new DocumentFileRest(documentFileService, spaceService, identityManager, metadataService);
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
    filter = new DocumentTimelineFilter(Long.valueOf(currentIdentity.getId()), false);
    when(identityRegistry.getIdentity(username)).thenReturn(userID);

    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(username))).thenReturn(currentIdentity);

    String spacePrettyName = "spacetest";
    currentIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
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
                                                           FileListingType.TIMELINE,
                                                           null,
                                                           false,
                                                           "",
                                                           null,
                                                           false,
                                                           0,
                                                           0);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());

    Response response2 = documentFileRest.getDocumentItems(currentOwnerId, null, null, null, false, "", null, false, 0, 0);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response2.getStatus());

    Response response3 = documentFileRest.getDocumentItems(currentOwnerId,
                                                           null,
                                                           FileListingType.TIMELINE,
                                                           null,
                                                           false,
                                                           "",
                                                           null,
                                                           false,
                                                           0,
                                                           0);
    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response3.getStatus());

    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);
    List<AbstractNode> files_ = new ArrayList<>();
    files_ = documentFileService.getDocumentItems(FileListingType.TIMELINE, filter, 0, 0, Long.valueOf(currentIdentity.getId()));

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
                                                           FileListingType.TIMELINE,
                                                           null,
                                                           false,
                                                           "metadatas",
                                                           null,
                                                           false,
                                                           0,
                                                           0);
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
    filter = new DocumentTimelineFilter(Long.valueOf(currentIdentity.getId()), false);
    when(identityRegistry.getIdentity(username)).thenReturn(userID);

    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME), eq(username))).thenReturn(currentIdentity);

    String spacePrettyName = "spacetest";
    currentIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
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

    String spacePrettyName = "spacetest";
    currentIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
    org.exoplatform.services.security.Identity spaceID = new org.exoplatform.services.security.Identity(spacePrettyName);
    when(identityRegistry.getIdentity(spacePrettyName)).thenReturn(spaceID);
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);
    when(spaceService.hasAccessPermission(space, username)).thenReturn(true);

    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);

    DocumentFolderFilter filter = null;
    filter = new DocumentFolderFilter(null,currentOwnerId, false);
    filter.setSortField(DocumentSortField.NAME);
    IdentityEntity identity1 = new IdentityEntity();
    IdentityEntity identity2 = new IdentityEntity("3", "userb", null, "organization", "spacetest");
    identity1.setId("2");
    identity1.setName("usera");
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

    folder.add(folder1);
    folder.add(folder2);

    FolderNodeEntity folderEntity = new FolderNodeEntity();
    folderEntity.setId("2");
    folderEntity.setName("folder1");
    folderEntity.setCreatorIdentity(identity1);
    folderEntity.setDatasource("datasource");
    folderEntity.setDescription("description");
    folderEntity.setCreatedDate(11111);
    folderEntity.setParentFolderId("1");


    String expand = "creator";

    when(documentFileStorage.getFolderChildNodes(filter, spaceID, 0, 0)).thenReturn(folder);

    Response response1 = documentFileRest.getDocumentItems(null,
                                                           null,
                                                           FileListingType.FOLDER,
                                                           null,
                                                           false,
                                                           "",
                                                           null,
                                                           false,
                                                           0,
                                                           0);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());

    Response response2 = documentFileRest.getDocumentItems(null, "2", FileListingType.FOLDER, null, false, "", null, false, 0, 0);
    assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());

    Response response3 = documentFileRest.getDocumentItems(currentOwnerId,
                                                           null,
                                                           FileListingType.FOLDER,
                                                           null,
                                                           false,
                                                           expand,
                                                           null,
                                                           false,
                                                           0,
                                                           0);
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
    assertTrue(folderEntity.equals(foldersNodeEntity.get(0)));
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

    BreadCrumbItem breadCrumbItem1 = new BreadCrumbItem("1","Folder1");
    BreadCrumbItem breadCrumbItem2 = new BreadCrumbItem("2","Folder2");
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


    when(documentFileStorage.getBreadcrumb(2,"Folder1",userID)).thenReturn(breadCrumbItems);

    Response response1 = documentFileRest.getBreadcrumb(null,
            null);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response1.getStatus());

    Response response2 = documentFileRest.getBreadcrumb(Long.valueOf(2),"Folder1");

    assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response2.getStatus());

    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(currentIdentity);
    Response response3 = documentFileRest.getBreadcrumb(Long.valueOf(2),"Folder1");
    assertEquals(Response.Status.OK.getStatusCode(), response3.getStatus());
    breadCrumbItemEntities = (List<BreadCrumbItemEntity>) response3.getEntity();
    assertEquals(breadCrumbItemEntities.size(), 4);
    assertEquals(breadCrumbItemEntities.get(0).getId(),"4");
    assertEquals(breadCrumbItemEntities.get(0).getName(),"Folder4");

  }


}
