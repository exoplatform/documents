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

import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
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
import org.gatein.pc.api.PortletInvokerException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import javax.jcr.*;
import javax.jcr.nodetype.NodeType;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TrashStorageImplTest {

  private IdentityManager     identityManager;


  private RepositoryService repositoryService;

  private SessionProviderService sessionProviderService;

  private ManageableRepository repository;

  private RepositoryEntry repositoryEntry;

  private SessionProvider sessionProvider;

  private Session session;

  private ListenerService listenerService;

  private InitParams initParams;

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
    initParams = mock(InitParams.class);
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
    lenient().when(((Node) sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).getProperty(NodeTypeConstants.RESTORE_PATH)).thenReturn(pathProperty);
    lenient().when(((Node) sessionProviderService.getSystemSessionProvider(any()).getSession("trashWorkspace", repository).getItem(anyString())).getProperty(NodeTypeConstants.RESTORE_PATH).getString()).thenReturn(path);
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
    lenient().when(node.isNodeType(NodeTypeConstants.RESTORE_PATH)).thenReturn(true);
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
}
