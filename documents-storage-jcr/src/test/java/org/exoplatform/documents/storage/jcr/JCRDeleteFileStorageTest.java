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

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.documents.model.TrashElementNode;
import org.exoplatform.documents.model.TrashElementNodeFilter;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.bulkactions.BulkStorageActionService;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.metadata.favorite.FavoriteService;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.AccessControlException;
import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;
import java.util.Arrays;
import java.util.List;

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
  @Mock
  private BulkStorageActionService bulkStorageActionService;

  private JCRDeleteFileStorageImpl jcrDeleteFileStorage;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    JCR_DOCUMENTS_UTIL.close();
  }

  @Before
  public void setUp() throws Exception {
    jcrDeleteFileStorage = new JCRDeleteFileStorageImpl(repositoryService, identityManager, trashStorage, favoriteService, portalContainer, sessionProviderService, listenerService, bulkStorageActionService);
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

    when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    when(sessionProviderService.getSessionProvider(any())).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn(currentRepository);

    ExtendedSession session1 = mock(ExtendedSession.class);

    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID)).thenReturn(sessionProvider);
    when(sessionProvider.getSession(Mockito.any(), Mockito.any())).thenReturn(session1);

    NodeImpl node = Mockito.mock(NodeImpl.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByPath(session1, path)).thenReturn(node);
    NodeType nodeType = Mockito.mock(NodeType.class);
    when(node.getIdentifier()).thenReturn("id123");
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

  @Test
  public void testDeleteDocumentDeniedOutsideOwnPrivateSpace() throws Exception {
    String username = "testuser";
    String currentRepository = "collaboration";
    String path = "/Users/otheruser/Private/file1";
    long currentOwnerId = 2;
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(username);

    when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn(currentRepository);

    ExtendedSession session1 = mock(ExtendedSession.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID)).thenReturn(sessionProvider);
    when(sessionProvider.getSession(Mockito.any(), Mockito.any())).thenReturn(session1);

    NodeImpl node = Mockito.mock(NodeImpl.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByPath(session1, path)).thenReturn(node);
    when(node.getPath()).thenReturn(path);
    when(node.isCheckedOut()).thenReturn(true);
    SessionImpl nodeSession = mock(SessionImpl.class);
    when(node.getSession()).thenReturn(nodeSession);
    when(nodeSession.getUserID()).thenReturn(username);
    // Neither the node's own ACL nor the private-space override grant this user REMOVE here.
    doThrow(new AccessControlException("denied")).when((ExtendedNode) node).checkPermission(anyString());
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isInUserPrivateSpace(node, username)).thenReturn(false);

    // The refusal must reach the caller as a real failure, not a logged-and-ignored "success".
    assertThrows(IllegalAccessException.class,
                 () -> jcrDeleteFileStorage.deleteDocument(path, "1", false, true, 0, userID, currentOwnerId));
  }

  @Test
  public void testDeleteDocumentAllowedInsideOwnPrivateSpaceDespiteMissingAcl() throws Exception {
    String username = "testuser";
    String currentRepository = "collaboration";
    String trashId = "999";
    String path = "/Users/testuser/Private/file1";
    long currentOwnerId = 2;
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(username);

    when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn(currentRepository);

    ExtendedSession session1 = mock(ExtendedSession.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID)).thenReturn(sessionProvider);
    when(sessionProvider.getSession(Mockito.any(), Mockito.any())).thenReturn(session1);

    NodeImpl node = Mockito.mock(NodeImpl.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByPath(session1, path)).thenReturn(node);
    when(node.getPath()).thenReturn(path);
    when(node.isCheckedOut()).thenReturn(true);
    SessionImpl nodeSession = mock(SessionImpl.class);
    when(node.getSession()).thenReturn(nodeSession);
    when(nodeSession.getUserID()).thenReturn(username);
    // The node's own ACL does not grant REMOVE (e.g. it was created there on this user's
    // behalf without one), but the node lives inside the acting user's own Private space.
    doThrow(new AccessControlException("denied")).when((ExtendedNode) node).checkPermission(anyString());
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isInUserPrivateSpace(node, username)).thenReturn(true);
    when(trashStorage.moveToTrash(node, sessionProvider)).thenReturn(trashId);
    when(trashStorage.getNodeByTrashId(trashId)).thenReturn(node);
    NodeType nodeType = mock(NodeType.class);
    when(nodeType.getName()).thenReturn(NodeTypeConstants.NT_FILE);
    when(node.getPrimaryNodeType()).thenReturn(nodeType);

    jcrDeleteFileStorage.deleteDocument(path, "1", false, true, 0, userID, currentOwnerId);

    verify(trashStorage, times(1)).moveToTrash(node, sessionProvider);
  }

  @Test
  public void testDeleteDocumentSurfacesRemovalRefusal() throws Exception {
    String username = "testuser";
    String path = "/document/file1";
    NodeImpl node = prepareNodeToRemove(username, path);
    // The node is removed outright rather than moved to trash, and the user's own session
    // refuses the removal.
    doThrow(new AccessDeniedException("denied")).when(node).remove();

    // The refusal must reach the caller: returning normally here is reported as "removed".
    assertThrows(IllegalAccessException.class,
                 () -> jcrDeleteFileStorage.deleteDocument(path, "1", false, true, 0,
                                                           new org.exoplatform.services.security.Identity(username), 2));
  }

  @Test
  public void testDeleteDocumentSurfacesRemovalRefusalRaisedByTheSession() throws Exception {
    String username = "testuser";
    String path = "/document/file1";
    NodeImpl node = prepareNodeToRemove(username, path);
    // Same refusal, raised by the session's permission check rather than by the removal.
    doThrow(new AccessControlException("denied")).when(node).remove();

    assertThrows(IllegalAccessException.class,
                 () -> jcrDeleteFileStorage.deleteDocument(path, "1", false, true, 0,
                                                           new org.exoplatform.services.security.Identity(username), 2));
  }

  private NodeImpl prepareNodeToRemove(String username, String path) throws Exception {
    String currentRepository = "collaboration";
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity(username);

    when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn(currentRepository);

    ExtendedSession session1 = mock(ExtendedSession.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID)).thenReturn(sessionProvider);
    when(sessionProvider.getSession(Mockito.any(), Mockito.any())).thenReturn(session1);

    NodeImpl node = Mockito.mock(NodeImpl.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByPath(session1, path)).thenReturn(node);
    when(node.getPath()).thenReturn(path);
    when(node.getParent()).thenReturn(node);
    // Already in trash: the delete removes the node outright instead of moving it there.
    when(trashStorage.isInTrash(node)).thenReturn(true);
    return node;
  }

  @Test
  public void testGetDeletedDocuments() throws RepositoryException {
    // Mock input
    TrashElementNodeFilter filter = new TrashElementNodeFilter();
    Node node1 = mock(Node.class);
    Node node2 = mock(Node.class);

    when(trashStorage.getTrashElements(filter)).thenReturn(Arrays.asList(node1, node2));

    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.retrieveTrashElementProperties(eq(node1), any(TrashElementNode.class)))
            .thenAnswer(invocation -> {
              TrashElementNode node = invocation.getArgument(1);
              node.setName("Document1");
              return null;
            });
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.retrieveTrashElementProperties(eq(node2), any(TrashElementNode.class)))
            .thenAnswer(invocation -> {
              TrashElementNode node = invocation.getArgument(1);
              node.setName("Document2");
              return null;
            });
    List<TrashElementNode> result = jcrDeleteFileStorage.getDeletedDocuments(filter);

    // Verify the result
    assertEquals(2, result.size());
    assertEquals("Document1", result.get(0).getName());
    assertEquals("Document2", result.get(1).getName());
  }

  @Test
  public void testGetDeletedDocuments_repositoryException() throws RepositoryException {
    TrashElementNodeFilter filter = new TrashElementNodeFilter();
    Node node1 = mock(Node.class);
    Node node2 = mock(Node.class);

    when(trashStorage.getTrashElements(filter)).thenReturn(Arrays.asList(node1, node2));

    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.retrieveTrashElementProperties(eq(node1), any(TrashElementNode.class)))
            .thenThrow(new RepositoryException("Error retrieving properties"));
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.retrieveTrashElementProperties(eq(node2), any(TrashElementNode.class)))
            .thenAnswer(invocation -> {
              TrashElementNode node = invocation.getArgument(1);
              node.setName("Document2");
              return null;
            });

    List<TrashElementNode> result = jcrDeleteFileStorage.getDeletedDocuments(filter);

    // Verify the result
    assertEquals(2, result.size());
    assertEquals(null, result.get(0).getName());
    assertEquals("Document2", result.get(1).getName());

  }

  @Test
  public void testDeleteDocumentPermanently() throws RepositoryException, ObjectNotFoundException {
    String trashNodePath = "/node/path";

    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenThrow(RepositoryException.class).thenReturn(repository);
    //
    assertThrows(RepositoryException.class, () -> {
      jcrDeleteFileStorage.deleteDocumentPermanently(trashNodePath);
    });
    //
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("defaultWorkspace");

    Session session = Mockito.mock(Session.class);
    when(sessionProvider.getSession("defaultWorkspace", repository)).thenReturn(session);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByPath(session, trashNodePath)).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> {
      jcrDeleteFileStorage.deleteDocumentPermanently(trashNodePath);
    });
    //
    Node node = mock(Node.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByPath(session, trashNodePath)).thenReturn(node);
    when(trashStorage.isInTrash(node)).thenReturn(false);

    assertThrows(ObjectNotFoundException.class, () -> {
      jcrDeleteFileStorage.deleteDocumentPermanently(trashNodePath);
    });
    //
    when(trashStorage.isInTrash(node)).thenReturn(true);
    jcrDeleteFileStorage.deleteDocumentPermanently(trashNodePath);
    verify(node).remove();
  }

}
