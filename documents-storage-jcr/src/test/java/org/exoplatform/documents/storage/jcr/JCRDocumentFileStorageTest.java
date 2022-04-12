package org.exoplatform.documents.storage.jcr;

import org.exoplatform.documents.storage.jcr.search.DocumentSearchServiceConnector;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.documents.storage.jcr.util.Utils;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Utils.class, SessionProvider.class, JCRDocumentsUtil.class})
public class JCRDocumentFileStorageTest {

  @Mock
  private SpaceService                   spaceService;

  @Mock
  private RepositoryService              repositoryService;

  @Mock
  private IdentityManager                identityManager;

  @Mock
  private NodeHierarchyCreator           nodeHierarchyCreator;

  @Mock
  private DocumentSearchServiceConnector documentSearchServiceConnector;

  @Mock
  private ListenerService                listenerService;

  private JCRDocumentFileStorage         jcrDocumentFileStorage;

  @Before
  public void setUp() throws Exception {
    this.jcrDocumentFileStorage = new JCRDocumentFileStorage(nodeHierarchyCreator,
                                                             repositoryService,
                                                             documentSearchServiceConnector,
                                                             identityManager,
                                                             spaceService,
                                                             listenerService);
    PowerMockito.mockStatic(Utils.class);
    PowerMockito.mockStatic(SessionProvider.class);
    PowerMockito.mockStatic(JCRDocumentsUtil.class);
  }

  @Test
  public void shareDocument() throws Exception {
    Session systemSession = mock(Session.class);
    Identity identity = mock(Identity.class);
    Node rootNode = mock(Node.class);
    Node sharedNode = mock(Node.class);
    Node currentNode = mock(Node.class);
    ExtendedNode linkNode = mock(ExtendedNode.class);
    Property property = mock(Property.class);
    NodeType nodeType =  mock(NodeType.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(SessionProvider.createSystemProvider()).thenReturn(sessionProvider);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession(manageableRepository.getConfiguration().getDefaultWorkspaceName(),
                                    manageableRepository)).thenReturn(systemSession);
    when(identityManager.getIdentity("1")).thenReturn(identity);
    when(JCRDocumentsUtil.getNodeByIdentifier(systemSession, "1")).thenReturn(currentNode);
    when(JCRDocumentsUtil.getIdentityRootNode(spaceService, nodeHierarchyCreator,identity, systemSession)).thenReturn(rootNode);
    when(identity.getProviderId()).thenReturn("USER");
    when(rootNode.hasNode("Shared")).thenReturn(false);
    when(rootNode.getNode("Documents")).thenReturn(rootNode);
    when(rootNode.addNode("Shared")).thenReturn(sharedNode);
    when(rootNode.getNode("Shared")).thenReturn(sharedNode);
    when(currentNode.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(false);
    when(currentNode.getName()).thenReturn("test");
    when(sharedNode.hasNode("test")).thenReturn(false);
    when(sharedNode.addNode("test", NodeTypeConstants.EXO_SYMLINK)).thenReturn(linkNode);
    when(sharedNode.getNode("test")).thenReturn(linkNode);
    when(linkNode.canAddMixin("exo:sortable")).thenReturn(true);
    when(currentNode.hasProperty("exo:title")).thenReturn(true);
    when(currentNode.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(property);
    when(property.getString()).thenReturn("test");
    when(JCRDocumentsUtil.getMimeType(currentNode)).thenReturn("testMimeType");
    when(currentNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn("nt:file");
    when(currentNode.getUUID()).thenReturn("123");
    when(identity.getRemoteId()).thenReturn("username");
    when(linkNode.canAddMixin(NodeTypeConstants.EXO_PRIVILEGEABLE)).thenReturn(true);
    jcrDocumentFileStorage.shareDocument("1", 1L);
    PowerMockito.verifyStatic(Utils.class, times(1));
    Utils.broadcast(listenerService, "share_document_event", identity, linkNode);
    verify(sessionProvider, times(1)).close();
  }
}
