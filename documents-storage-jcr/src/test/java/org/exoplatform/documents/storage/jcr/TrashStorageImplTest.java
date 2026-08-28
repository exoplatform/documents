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

import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.RESTORE_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.AccessControlException;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.model.TrashElementNodeFilter;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.gatein.pc.api.PortletInvokerException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.WorkspaceImpl;
import org.exoplatform.services.jcr.impl.ext.action.SessionActionInterceptor;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class TrashStorageImplTest {

  private static final MockedStatic<JCRDocumentsUtil> JCR_DOCUMENTS_UTIL = mockStatic(JCRDocumentsUtil.class);

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    JCR_DOCUMENTS_UTIL.close();
  }

  private IdentityManager     identityManager;


  private RepositoryService repositoryService;

  private SessionProviderService sessionProviderService;

  private ManageableRepository repository;

  private RepositoryEntry repositoryEntry;

  private SessionProvider sessionProvider;

  private Session session;

  private ListenerService listenerService;

  private TrashStorageImpl trashStorage;


  @Before
  public void setUp() throws PortletInvokerException {
    identityManager = mock(IdentityManager.class);
    listenerService = mock(ListenerService.class);
    repositoryService = mock(RepositoryService.class);
    sessionProviderService = mock(SessionProviderService.class);
    repository = mock(ManageableRepository.class);
    repositoryEntry = mock(RepositoryEntry.class);
    sessionProvider = mock(SessionProvider.class);
    session = mock(Session.class);
    trashStorage = new TrashStorageImpl(repositoryService, sessionProviderService, listenerService, getParams());
  }
  @Test
  public void testMoveToTrash() throws Exception {
    String username = "testuser";
    String currentRepository = "Collaboration";
    String path = "/document/name123";
    long currentOwnerId = 2;
    Identity currentIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);

    Node node = Mockito.mock(NodeImpl.class);
    Workspace workspace = Mockito.mock(WorkspaceImpl.class);
    NodeIterator nodeIterator = Mockito.mock(NodeIterator.class);

    lenient().when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    lenient().when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    lenient().when(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository)).thenReturn(session);
    lenient().when(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getWorkspace()).thenReturn(workspace);
    lenient().when(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).thenReturn(node);
    lenient().when(((Node) sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).getNodes(anyString())).thenReturn(nodeIterator);
    lenient().when(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getWorkspace().getName()).thenReturn(currentRepository);
    lenient().when(sessionProviderService.getSessionProvider(any())).thenReturn(sessionProvider);
    lenient().when(repositoryService.getCurrentRepository()).thenReturn(repository);
    lenient().when(repository.getConfiguration()).thenReturn(repositoryEntry);
    lenient().when(repositoryEntry.getDefaultWorkspaceName()).thenReturn(currentRepository);
    lenient().when(sessionProvider.getSession(any(), any())).thenReturn(session);

    Session session1 = Mockito.mock(SessionImpl.class);
    NodeType nodeType = Mockito.mock(NodeType.class);
    SessionActionInterceptor sessionActionInterceptor = Mockito.mock(SessionActionInterceptor.class);
    lenient().when(node.getUUID()).thenReturn("id123");
    lenient().when(node.getName()).thenReturn("name123");
    lenient().when(node.getPath()).thenReturn(path);
    lenient().when(node.getSession()).thenReturn(session1);
    lenient().when(node.getSession().getWorkspace()).thenReturn(workspace);
    lenient().when(session1.getWorkspace().getName()).thenReturn(currentRepository);
    lenient().when(node.getParent()).thenReturn(node);
    lenient().when(((SessionImpl)node.getSession()).getActionHandler()).thenReturn(sessionActionInterceptor);
    lenient().when(session.getNodeByUUID(eq("id123"))).thenReturn(node);
    lenient().when(session.itemExists(anyString())).thenReturn(true);
    lenient().when(session.getItem(anyString())).thenReturn(node);
    lenient().when(node.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(false);
    lenient().when(node.getNodes()).thenReturn(nodeIterator);
    lenient().when(node.getPrimaryNodeType()).thenReturn(nodeType);
    lenient().when(node.getPrimaryNodeType().getName()).thenReturn(NodeTypeConstants.NT_FILE);


    String trashId = trashStorage.moveToTrash(node, sessionProvider,0);

    assertNotNull(trashId);
  }

  @Test
  public void testMoveToTrashAllowedInsideOwnPrivateSpaceDespiteMissingAcl() throws Exception {
    String username = "testuser";
    String currentRepository = "Collaboration";
    String path = "/Users/testuser/Private/name123";

    Node node = Mockito.mock(NodeImpl.class);
    Workspace workspace = Mockito.mock(WorkspaceImpl.class);
    NodeIterator nodeIterator = Mockito.mock(NodeIterator.class);
    Session session1 = Mockito.mock(SessionImpl.class);
    NodeType nodeType = Mockito.mock(NodeType.class);
    SessionActionInterceptor sessionActionInterceptor = Mockito.mock(SessionActionInterceptor.class);

    lenient().when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    lenient().when(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository)).thenReturn(session);
    lenient().when(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getWorkspace()).thenReturn(workspace);
    lenient().when(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).thenReturn(node);
    lenient().when(((Node) sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).getNodes(anyString())).thenReturn(nodeIterator);
    lenient().when(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getWorkspace().getName()).thenReturn(currentRepository);
    lenient().when(sessionProviderService.getSessionProvider(any())).thenReturn(sessionProvider);
    lenient().when(repositoryService.getCurrentRepository()).thenReturn(repository);
    lenient().when(repository.getConfiguration()).thenReturn(repositoryEntry);
    lenient().when(repositoryEntry.getDefaultWorkspaceName()).thenReturn(currentRepository);
    lenient().when(sessionProvider.getSession(any(), any())).thenReturn(session);

    lenient().when(node.getUUID()).thenReturn("id123");
    lenient().when(node.getName()).thenReturn("name123");
    lenient().when(node.getPath()).thenReturn(path);
    lenient().when(node.getSession()).thenReturn(session1);
    lenient().when(node.getSession().getWorkspace()).thenReturn(workspace);
    lenient().when(session1.getWorkspace().getName()).thenReturn(currentRepository);
    lenient().when(node.getParent()).thenReturn(node);
    lenient().when(((SessionImpl) node.getSession()).getActionHandler()).thenReturn(sessionActionInterceptor);
    lenient().when(session.getNodeByUUID(eq("id123"))).thenReturn(node);
    lenient().when(session.itemExists(anyString())).thenReturn(true);
    lenient().when(session.getItem(anyString())).thenReturn(node);
    lenient().when(node.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(false);
    lenient().when(node.getNodes()).thenReturn(nodeIterator);
    lenient().when(node.getPrimaryNodeType()).thenReturn(nodeType);
    lenient().when(node.getPrimaryNodeType().getName()).thenReturn(NodeTypeConstants.NT_FILE);

    // The node's own ACL does not grant REMOVE (e.g. it was created there on this user's
    // behalf without one), but the node lives inside the acting user's own Private space.
    lenient().when(session1.getUserID()).thenReturn(username);
    doThrow(new AccessControlException("Permission denied " + path + " : remove")).when(session1)
                                                                                   .checkPermission(path, PermissionType.REMOVE);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isInUserPrivateSpace(node, username)).thenReturn(true);

    String trashId = trashStorage.moveToTrash(node, sessionProvider, 0);

    assertNotNull(trashId);
  }

  @Test
  public void testMoveToTrashDeniedOutsideOwnPrivateSpace() throws Exception {
    String username = "testuser";
    String path = "/Users/otheruser/Private/name123";

    Node node = Mockito.mock(NodeImpl.class);
    Session session1 = Mockito.mock(SessionImpl.class);
    SessionActionInterceptor sessionActionInterceptor = Mockito.mock(SessionActionInterceptor.class);

    lenient().when(node.getPath()).thenReturn(path);
    lenient().when(node.getSession()).thenReturn(session1);
    lenient().when(((SessionImpl) node.getSession()).getActionHandler()).thenReturn(sessionActionInterceptor);

    lenient().when(session1.getUserID()).thenReturn(username);
    doThrow(new AccessControlException("Permission denied " + path + " : remove")).when(session1)
                                                                                   .checkPermission(path, PermissionType.REMOVE);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isInUserPrivateSpace(node, username)).thenReturn(false);

    assertThrows(AccessControlException.class, () -> trashStorage.moveToTrash(node, sessionProvider, 0));
  }

  @Test
  public void testRestoreFromTrash() throws Exception {
    String username = "testuser";
    String currentRepository = "Collaboration";
    String path = "/document/name123";
    long currentOwnerId = 2;
    Identity currentIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    currentIdentity.setId(String.valueOf(currentOwnerId));
    Profile currentProfile = new Profile();
    currentProfile.setProperty(Profile.FULL_NAME, username);
    currentIdentity.setProfile(currentProfile);

    Node node = Mockito.mock(NodeImpl.class);
    Workspace workspace = Mockito.mock(WorkspaceImpl.class);
    NodeIterator nodeIterator = Mockito.mock(NodeIterator.class);
    Session session1 = Mockito.mock(SessionImpl.class);
    NodeType nodeType = Mockito.mock(NodeType.class);

    lenient().when(identityManager.getIdentity(eq(String.valueOf(currentOwnerId)))).thenReturn(currentIdentity);
    lenient().when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    lenient().when(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository)).thenReturn(session);
    lenient().when(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getWorkspace()).thenReturn(workspace);
    lenient().when((Node) sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).thenReturn(node);
    lenient().when(((Node) sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).getNodes(anyString())).thenReturn(nodeIterator);
    lenient().when(((Node) sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).getSession()).thenReturn(session1);
    lenient().when(((Node) sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).getSession().getItem(anyString())).thenReturn(node);
    Property property = Mockito.mock(Property.class);
    Property pathProperty = Mockito.mock(Property.class);
    lenient().when(((Node) sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).getProperty(NodeTypeConstants.RESTORE_WORKSPACE)).thenReturn(property);
    lenient().when(((Node) sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).getProperty(NodeTypeConstants.RESTORE_WORKSPACE).getString()).thenReturn(currentRepository);
    lenient().when(((Node) sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).getProperty(RESTORE_PATH)).thenReturn(pathProperty);
    lenient().when(((Node) sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).getProperty(RESTORE_PATH).getString()).thenReturn(path);
    lenient().when(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getWorkspace().getName()).thenReturn(currentRepository);
    lenient().when(sessionProviderService.getSessionProvider(any())).thenReturn(sessionProvider);
    lenient().when(repositoryService.getCurrentRepository()).thenReturn(repository);
    lenient().when(repository.getConfiguration()).thenReturn(repositoryEntry);
    lenient().when(repositoryEntry.getDefaultWorkspaceName()).thenReturn(currentRepository);
    lenient().when(sessionProvider.getSession(any(), any())).thenReturn(session);
    SessionActionInterceptor sessionActionInterceptor = Mockito.mock(SessionActionInterceptor.class);
    lenient().when(node.getUUID()).thenReturn("id123");
    lenient().when(node.getName()).thenReturn("name123");
    lenient().when(node.getPath()).thenReturn(path);
    lenient().when(node.getSession()).thenReturn(session1);
    lenient().when(node.getSession().getWorkspace()).thenReturn(workspace);
    lenient().when(session1.getWorkspace().getName()).thenReturn(currentRepository);
    lenient().when(node.getParent()).thenReturn(node);
    lenient().when(((SessionImpl)node.getSession()).getActionHandler()).thenReturn(sessionActionInterceptor);
    lenient().when(session.getNodeByUUID(eq("id123"))).thenReturn(node);
    lenient().when(session.itemExists(anyString())).thenReturn(true);
    lenient().when(session.getItem(anyString())).thenReturn(node);
    lenient().when(node.isNodeType(NodeTypeConstants.RESTORE_WORKSPACE)).thenReturn(true);
    lenient().when(node.isNodeType(RESTORE_PATH)).thenReturn(true);
    lenient().when(node.isNodeType(NodeTypeConstants.MIX_REFERENCEABLE)).thenReturn(true);
    lenient().when(node.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION)).thenReturn(true);
    lenient().when(node.getNodes()).thenReturn(nodeIterator);
    lenient().when(node.getPrimaryNodeType()).thenReturn(nodeType);
    lenient().when(node.getPrimaryNodeType().getName()).thenReturn(NodeTypeConstants.NT_FILE);


    trashStorage.restoreFromTrash("/trash/document/name123", sessionProvider);

    verify(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository), times(3)).save();
    verify(sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getWorkspace(), times(1)).move(anyString(), anyString());
  }

  private InitParams getParams() {
    InitParams params = new InitParams();
    PropertiesParam propertiesParam = new PropertiesParam();
    propertiesParam.setName("constructor.params");
    propertiesParam.setProperty("trashWorkspace", "trashWorkspace");

    PropertiesParam propertiesParam1 = new PropertiesParam();
    propertiesParam1.setName("constructor.params");
    propertiesParam1.setProperty("trashHomeNodePath", "trashHomeNodePath");

    ValueParam valueParam = new ValueParam();
    valueParam.setName("trashWorkspace");
    valueParam.setValue("trashWorkspace");

    ValueParam valueParam1 = new ValueParam();
    valueParam1.setName("trashHomeNodePath");
    valueParam1.setValue("trashHomeNodePath");

    params.addParameter(propertiesParam);
    params.addParameter(propertiesParam1);
    params.addParameter(valueParam);
    params.addParameter(valueParam1);
    return params;
  }

  @Test
  public void getTrashElementsTest() throws Exception {

    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    when(sessionProvider.getSession(anyString(), eq(repository))).thenReturn(session);
    Workspace workspace = mock(Workspace.class);
    QueryManager queryManager = mock(QueryManager.class);
    org.exoplatform.services.jcr.impl.core.query.QueryImpl query = mock(QueryImpl.class);
    QueryResult queryResult = mock(QueryResult.class);
    NodeIterator nodeIterator = mock(NodeIterator.class);

    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getQueryManager()).thenReturn(queryManager);

    // Mock query execution
    when(queryManager.createQuery(anyString(), eq(Query.SQL))).thenReturn(query);
    when(query.execute()).thenReturn(queryResult);
    when(queryResult.getNodes()).thenReturn(nodeIterator);

    // Mock node iterator
    when(nodeIterator.hasNext()).thenReturn(true, true, false); // 2 nodes
    when(nodeIterator.nextNode()).thenReturn(mock(Node.class), mock(Node.class));

    TrashElementNodeFilter trashElementNodeFilter = new TrashElementNodeFilter();
    trashElementNodeFilter.setSortField(DocumentSortField.NAME);
    trashElementNodeFilter.setAscending(true);
    trashElementNodeFilter.setLimit(10);
    trashElementNodeFilter.setOffset(0);

    List<Node> result = trashStorage.getTrashElements(trashElementNodeFilter);

    //
    verify(repositoryService).getCurrentRepository();
    verify(sessionProviderService).getSystemSessionProvider(null);
    verify(sessionProvider).getSession(anyString(), eq(repository));
    verify(session).getWorkspace();
    verify(workspace).getQueryManager();
    verify(queryManager).createQuery(anyString(), eq(Query.SQL));
    verify(query).setLimit(10);
    verify(query).setOffset(0);
    verify(query).execute();
    verify(queryResult).getNodes();
    verify(nodeIterator, times(3)).hasNext();
    verify(nodeIterator, times(2)).nextNode();

    assertNotNull(result);
    assertEquals(2, result.size());
  }

    @Test
    public void testUpdateRestorePath() throws Exception {
        String oldPath = "/old/folder";
        String newPath = "/new/folder";

        Workspace workspace = mock(Workspace.class);
        QueryManager queryManager = mock(QueryManager.class);
        Query query = mock(Query.class);
        QueryResult queryResult = mock(QueryResult.class);
        NodeIterator nodeIterator = mock(NodeIterator.class);
        Node node1 = mock(Node.class);
        Node node2 = mock(Node.class);
        Property property1 = mock(Property.class);
        Property property2 = mock(Property.class);

        when(repositoryService.getCurrentRepository()).thenReturn(repository);
        when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
        when(sessionProvider.getSession(anyString(), eq(repository))).thenReturn(session);
        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getQueryManager()).thenReturn(queryManager);

        when(queryManager.createQuery(anyString(), eq(Query.SQL))).thenReturn(query);
        when(query.execute()).thenReturn(queryResult);
        when(queryResult.getNodes()).thenReturn(nodeIterator);

        // Iterator behavior (2 nodes)
        when(nodeIterator.hasNext()).thenReturn(true, true, false);
        when(nodeIterator.nextNode()).thenReturn(node1, node2);

        // Node 1 has restore path
        when(node1.hasProperty(RESTORE_PATH)).thenReturn(true);
        when(node1.getProperty(RESTORE_PATH)).thenReturn(property1);
        when(property1.getString()).thenReturn("/old/folder/doc1");

        // Node 2 has restore path
        when(node2.hasProperty(RESTORE_PATH)).thenReturn(true);
        when(node2.getProperty(RESTORE_PATH)).thenReturn(property2);
        when(property2.getString()).thenReturn("/old/folder/sub/doc2");

        // Execute
        trashStorage.updateRestorePath(oldPath, newPath);

        // Verify query execution
        verify(queryManager).createQuery(anyString(), eq(Query.SQL));
        verify(query).execute();
        verify(queryResult).getNodes();

        // Verify iteration
        verify(nodeIterator, times(3)).hasNext();
        verify(nodeIterator, times(2)).nextNode();

        // Verify updates
        verify(node1).setProperty(RESTORE_PATH, "/new/folder/doc1");
        verify(node2).setProperty(RESTORE_PATH, "/new/folder/sub/doc2");

        // Verify save
        verify(session).save();
    }
}
