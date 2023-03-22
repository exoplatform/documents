/*
 * Copyright (C) 2022 eXo Platform SAS.
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
package org.exoplatform.documents.storage.jcr;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.metadata.favorite.FavoriteService;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;

@RunWith(MockitoJUnitRunner.Silent.class)
public class JCRDeleteFileStorageTest {

  private static final MockedStatic<JCRDocumentsUtil> JCR_DOCUMENTS_UTIL = mockStatic(JCRDocumentsUtil.class);

  @Mock
  private IdentityManager     identityManager;

  @Mock
  private TrashStorage trashStorage;

  @Mock
  private FavoriteService favoriteService;

  @Mock
  private RepositoryService repositoryService;

  @Mock
  private SessionProviderService sessionProviderService;

  @Mock
  private PortalContainer portalContainer;

  @Mock
  private ManageableRepository repository;

  @Mock
  private RepositoryEntry repositoryEntry;

  @Mock
  private SessionProvider sessionProvider;

  @Mock
  private Session session;

  @Mock
  private ListenerService listenerService;

  private JCRDeleteFileStorageImpl jcrDeleteFileStorage;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    JCR_DOCUMENTS_UTIL.close();
  }

  @Test
  public void testUndoDeleteDocument() {
    String username = "testuser";
    long currentOwnerId = 2;
    Identity currentIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);

    jcrDeleteFileStorage = new JCRDeleteFileStorageImpl(repositoryService, identityManager, trashStorage, favoriteService, portalContainer, sessionProviderService, listenerService);


    JCRDeleteFileStorageImpl.documentsToDeleteQueue.put("1", String.valueOf(2));

    //Undo delete can't be applied by others users
    jcrDeleteFileStorage.undoDelete("1", 3);

    assertEquals(1, JCRDeleteFileStorageImpl.documentsToDeleteQueue.size());

    jcrDeleteFileStorage.undoDelete("1", currentOwnerId);

    assertEquals(0, JCRDeleteFileStorageImpl.documentsToDeleteQueue.size());
  }


  @Test
  public void testDeleteDocument() throws Exception {
    String username = "testuser";
    String currentRepository = "collaboration";
    String trashId = "123456789";
    String path = "/document/file1";
    long currentOwnerId = 2;
    Identity currentIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(username);

    jcrDeleteFileStorage = new JCRDeleteFileStorageImpl(repositoryService, identityManager, trashStorage, favoriteService, portalContainer, sessionProviderService, listenerService);

    when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    when(sessionProviderService.getSessionProvider(any())).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn(currentRepository);

    ExtendedSession session1 = mock(ExtendedSession.class);

    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID)).thenReturn(sessionProvider);
    when(sessionProvider.getSession(Mockito.any(), Mockito.any())).thenReturn(session1);

    Node node = Mockito.mock(ExtendedNode.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByPath(session1, path)).thenReturn(node);
    NodeType nodeType = Mockito.mock(NodeType.class);
    when(node.getUUID()).thenReturn("id123");
    when(node.getName()).thenReturn("name123");
    when(node.getPath()).thenReturn(path);
    when(session.getNodeByUUID(eq("id123"))).thenReturn(node);
    when(session.itemExists(anyString())).thenReturn(true);
    when(session.getItem(anyString())).thenReturn(node);
    when(node.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION)).thenReturn(true);

    // Test exception when deleting docuemnt
    try {
      jcrDeleteFileStorage.deleteDocument(path ,"1", false, true, 0, userID,  currentOwnerId);
    } catch (Exception e) {
      // Expected
      fail("Error when deleting the document" + path);
    }

    jcrDeleteFileStorage.deleteDocument(path ,"1", true, true, 0, userID,  currentOwnerId);

    when(node.isCheckedOut()).thenReturn(true);
    when(trashStorage.moveToTrash(node, sessionProvider)).thenReturn(trashId);
    when(trashStorage.getNodeByTrashId(trashId)).thenReturn(node);
    when(nodeType.getName()).thenReturn(NodeTypeConstants.NT_FILE);
    when(node.getPrimaryNodeType()).thenReturn(nodeType);

    jcrDeleteFileStorage.deleteDocument(path ,"1", false, true, 0, userID,  currentOwnerId);

    verify((ExtendedNode)node, times(1)).checkPermission(anyString());

    //remove node
    when(trashStorage.isInTrash(node)).thenReturn(true);
    when(node.getParent()).thenReturn(node);
    jcrDeleteFileStorage.deleteDocument("/document/file1" ,"1", false, true, 0, userID,  currentOwnerId);

    verify(node, times(1)).remove();
    verify(node, times(2)).removeMixin(NodeTypeConstants.EXO_RESTORE_LOCATION);
  }

}
