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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.Before;
import org.junit.Test;

public class DocumentFileServiceTest {

  private DocumentFileStorage documentFileStorage;

  private IdentityManager     identityManager;

  private SpaceService        spaceService;

  private IdentityRegistry identityRegistry;

  private Authenticator authenticator;

  private DocumentFileServiceImpl documentFileService;

  @Before
  public void setUp() {
    spaceService = mock(SpaceService.class);
    identityManager = mock(IdentityManager.class);
    identityRegistry = mock(IdentityRegistry.class);
    authenticator = mock(Authenticator.class);
    documentFileStorage = mock(DocumentFileStorage.class);
    documentFileService = new DocumentFileServiceImpl(documentFileStorage, authenticator, spaceService, identityManager, identityRegistry);
  }

  @Test
  public void testGetDocumentItems() throws Exception {
    String username = "testuser";
    long currentOwnerId = 2;
    Identity currentIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);

    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(username);
    DocumentTimelineFilter filter = null;
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.TIMELINE,null,    0,    0,  Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(),"File filter is mandatory");

    filter = new DocumentTimelineFilter(0L);
    DocumentTimelineFilter finalFilter1 = filter;
    exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.TIMELINE, finalFilter1,    0,    0,  Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(),"OwnerId is mandatory");

    filter = new DocumentTimelineFilter(Long.valueOf(currentIdentity.getId()));
    DocumentTimelineFilter finalFilter = filter;
    exception = assertThrows(IllegalAccessException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.TIMELINE, finalFilter,    0,    0,  0);
    });
    assertEquals(exception.getMessage(),"User Identity is mandatory");

    DocumentFolderFilter docFilter = new DocumentFolderFilter("");
    DocumentFolderFilter finalDocFilter = docFilter;
    exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.TIMELINE, finalDocFilter,    0,    0,  Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(),"filter must be an instance of DocumentTimelineFilter");


    exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.FOLDER, finalFilter,    0,    0,  Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(),"filter must be an instance of DocumentFolderFilter");

    exception = assertThrows(IllegalArgumentException.class, () -> {
      documentFileService.getDocumentItems(FileListingType.FOLDER, finalDocFilter,    0,    0,  Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(),"ParentFolderId is mandatory");

    when(identityRegistry.getIdentity(username)).thenReturn(userID);
    when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME),
            eq(username))).thenReturn(currentIdentity);

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

    when(documentFileStorage.getFilesTimeline(filter, spaceID,    0, 0)).thenReturn(files);
    List<AbstractNode> files_ = new ArrayList<>();
    files_ = documentFileService.getDocumentItems(FileListingType.TIMELINE,filter,    0,    0,  Long.valueOf(currentIdentity.getId()));

    assertEquals(files_.size(),4);
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
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME),
            eq(username))).thenReturn(currentIdentity);


    DocumentTimelineFilter filter_ = new DocumentTimelineFilter(0L);
    DocumentTimelineFilter finalFilter1 = filter_;
    Exception exception = assertThrows(ObjectNotFoundException.class, () -> {
      documentFileService.getFilesTimeline(finalFilter1,    0,    0,  Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(),"Owner Identity with id : " + finalFilter1.getOwnerId() + " isn't found");

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

    when(documentFileStorage.getFilesTimeline(filter, spaceID,    0, 0)).thenReturn(files);
    List<FileNode> files_ = new ArrayList<>();
    files_ = documentFileService.getFilesTimeline(filter,    0,    0,  Long.valueOf(currentIdentity.getId()));
    assertEquals(files_.size(),4);
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
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME),
            eq(username))).thenReturn(currentIdentity);


    DocumentTimelineFilter filter_ = new DocumentTimelineFilter(0L);
    DocumentTimelineFilter finalFilter1 = filter_;
    Exception exception = assertThrows(ObjectNotFoundException.class, () -> {
      documentFileService.getFilesTimeline(finalFilter1,    0,    0,  Long.valueOf(currentIdentity.getId()));
    });
    assertEquals(exception.getMessage(),"Owner Identity with id : " + finalFilter1.getOwnerId() + " isn't found");

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
    documentGroupsSize_ = documentFileService.getGroupDocumentsCount(filter,  Long.valueOf(currentIdentity.getId()));
    assertEquals(documentGroupsSize_.getThisDay(),4);
  }

  @Test
  public void testGetFolderChildNodes() throws Exception { // NOSONAR
    //Todo
  }
}
