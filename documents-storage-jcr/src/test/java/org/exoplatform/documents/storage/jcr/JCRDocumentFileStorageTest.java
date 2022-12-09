package org.exoplatform.documents.storage.jcr;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.jcr.search.DocumentSearchServiceConnector;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.documents.storage.jcr.util.Utils;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.ext.utils.VersionHistoryUtils;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.jcr.*;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;

import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Utils.class, SessionProvider.class, JCRDocumentsUtil.class, CommonsUtils.class , VersionHistoryUtils.class })
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

  @Mock
  private IdentityRegistry               identityRegistry;

  @Mock
  private ActivityManager                activityManager;


  private JCRDocumentFileStorage         jcrDocumentFileStorage;

  @Before
  public void setUp() throws Exception {
    this.jcrDocumentFileStorage = new JCRDocumentFileStorage(nodeHierarchyCreator,
                                                             repositoryService,
                                                             documentSearchServiceConnector,
                                                             identityManager,
                                                             spaceService,
                                                             listenerService,
                                                             identityRegistry,
                                                             activityManager);
    PowerMockito.mockStatic(Utils.class);
    PowerMockito.mockStatic(SessionProvider.class);
    PowerMockito.mockStatic(JCRDocumentsUtil.class);
    PowerMockito.mockStatic(CommonsUtils.class);
    PowerMockito.mockStatic(VersionHistoryUtils.class);
  }

  @Test
  public void shareDocument() throws Exception {
    Session systemSession = mock(Session.class);
    Identity identity = mock(Identity.class);
    Node rootNode = mock(Node.class);
    Node sharedNode = mock(Node.class);
    Node currentNode = Mockito.mock(ExtendedNode.class);
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
    when(((ExtendedNode) currentNode).getIdentifier()).thenReturn("123");
    when(identity.getRemoteId()).thenReturn("username");
    when(linkNode.canAddMixin(NodeTypeConstants.EXO_PRIVILEGEABLE)).thenReturn(true);
    jcrDocumentFileStorage.shareDocument("1", 1L);
    PowerMockito.verifyStatic(Utils.class, times(1));
    Utils.broadcast(listenerService, "share_document_event", identity, linkNode);
    verify(sessionProvider, times(1)).close();
  }

  @Test
  public void duplicateDocument() throws Exception {
    Session systemSession = mock(Session.class);
    Identity identity = mock(Identity.class);
    Node rootNode = mock(Node.class);
    NodeImpl currentNode = mock(NodeImpl.class);

    ExtendedNode linkNode = mock(ExtendedNode.class);
    Property property = mock(Property.class);
    NodeType nodeType =  mock(NodeType.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity("username");
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
    when(rootNode.getNode("Documents")).thenReturn(rootNode);
    when(currentNode.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(false);
    when(currentNode.getName()).thenReturn("test");
    when(currentNode.hasProperty("exo:title")).thenReturn(true);
    when(currentNode.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(property);
    when(property.getString()).thenReturn("test");
    when(JCRDocumentsUtil.getMimeType(currentNode)).thenReturn("testMimeType");
    when(currentNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn("nt:file");
    when(currentNode.getUUID()).thenReturn("123");
    when(currentNode.getParent()).thenReturn(currentNode);
    when(currentNode.getIdentifier()).thenReturn("1");
    when(currentNode.addNode("copy of test","nt:file")).thenReturn(currentNode);
    when(identity.getRemoteId()).thenReturn("username");
    when(JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID)).thenReturn(sessionProvider);
    jcrDocumentFileStorage.duplicateDocument(1L,"1","copy of",userID);
    verify(sessionProvider, times(1)).close();
    PowerMockito.verifyStatic(VersionHistoryUtils.class, Mockito.times(1));
    VersionHistoryUtils.createVersion(any(Node.class));
  }
  
  @Test
  public void getFolderChildNodes() throws Exception {
    Node parentNode = mock(Node.class);
    Session userSession = mock(Session.class);
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Identity ownerIdentity = mock(Identity.class);
    when(identity.getUserId()).thenReturn("user");
    DocumentFolderFilter filter = new DocumentFolderFilter("12e2", "documents/path", 1L, "");

    // mock session
    SessionProvider sessionProvider = mock(SessionProvider.class);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(JCRDocumentsUtil.getUserSessionProvider(repositoryService, identity)).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession(manageableRepository.getConfiguration().getDefaultWorkspaceName(),
                                    manageableRepository)).thenReturn(userSession);

    when(JCRDocumentsUtil.getNodeByIdentifier(userSession, filter.getParentFolderId())).thenReturn(parentNode);
    when(parentNode.getName()).thenReturn("documents");
    when(parentNode.getNode(filter.getFolderPath())).thenReturn(parentNode);
    filter.setSortField(DocumentSortField.MODIFIED_DATE);
    filter.setAscending(true);
    filter.setIncludeHiddenFiles(false);
    when(parentNode.getPath()).thenReturn("/documents/path");

    // mock the query creation and execution
    Workspace workspace = mock(Workspace.class);
    QueryManager queryManager = mock(QueryManager.class);
    QueryImpl jcrQuery = mock(QueryImpl.class);
    NodeIterator nodeIterator = mock(NodeIterator.class);
    QueryResult queryResult = mock(QueryResult.class);
    when(userSession.getWorkspace()).thenReturn(workspace);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    when(queryManager.createQuery(anyString(), anyString())).thenReturn(jcrQuery);
    when(jcrQuery.execute()).thenReturn(queryResult);
    when(queryResult.getNodes()).thenReturn(nodeIterator);

    // mock toNodes method
    FileNode file = new FileNode();
    file.setName("file1");
    FolderNode folder1 = new FolderNode();
    folder1.setName("folder1");
    FolderNode folder2 = new FolderNode();
    folder2.setName("folder2");
    when(nodeIterator.hasNext()).thenReturn(true, true, false);
    Node fileNode = mock(Node.class);
    Node folderNode1 = mock(Node.class);
    Node folderNode2 = mock(Node.class);
    when(fileNode.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
    when(folderNode1.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderNode2.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(nodeIterator.nextNode()).thenReturn(fileNode, folderNode1);
    doCallRealMethod().when(JCRDocumentsUtil.class,
                            "toNodes",
                            identityManager,
                            userSession,
                            nodeIterator,
                            identity,
                            spaceService,
                            false);
    when(JCRDocumentsUtil.toFileNode(identityManager, identity, fileNode, "", spaceService)).thenReturn(file);
    when(JCRDocumentsUtil.toFolderNode(identityManager, identity, folderNode1, "", spaceService)).thenReturn(folder1);
    when(JCRDocumentsUtil.toFolderNode(identityManager, identity, folderNode2, "", spaceService)).thenReturn(folder2);

    List<AbstractNode> nodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 2);
    assertEquals(2, nodes.size());
    when(nodeIterator.hasNext()).thenReturn(true, false);
    when(nodeIterator.nextNode()).thenReturn(folderNode2);
    nodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 2, 4);
    assertEquals(1, nodes.size());

    // case of folderNodeId empty
    filter.setParentFolderId(null);
    when(identityManager.getIdentity(String.valueOf(filter.getOwnerId()))).thenReturn(ownerIdentity);
    NodeImpl parentNodeImp = mock(NodeImpl.class);
    when(parentNodeImp.getName()).thenReturn("documents");
    when(parentNodeImp.getNode(filter.getFolderPath())).thenReturn(parentNodeImp);
    when(parentNodeImp.getPath()).thenReturn("/documents/path");
    when(JCRDocumentsUtil.getIdentityRootNode(spaceService,
                                              nodeHierarchyCreator,
                                              "user",
                                              ownerIdentity,
                                              sessionProvider)).thenReturn(parentNodeImp);
    NodeIterator nodeIterator1 = mock(NodeIterator.class);
    when(queryResult.getNodes()).thenReturn(nodeIterator1);
    when(nodeIterator1.hasNext()).thenReturn(true, true, false);
    when(nodeIterator1.nextNode()).thenReturn(fileNode, folderNode1);
    doCallRealMethod().when(JCRDocumentsUtil.class,
                            "toNodes",
                            identityManager,
                            userSession,
                            nodeIterator1,
                            identity,
                            spaceService,
                            false);
    List<AbstractNode> nodes1 = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 2);
    assertEquals(2, nodes1.size());
    when(nodeIterator1.hasNext()).thenReturn(true, false);
    when(nodeIterator1.nextNode()).thenReturn(folderNode2);
    nodes1 = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 2, 4);
    assertEquals(1, nodes1.size());

    // case filter with query
    filter.setQuery("docum");
    when(userSession.getWorkspace()).thenReturn(workspace);
    when(workspace.getName()).thenReturn("collaboration");
    doCallRealMethod().when(JCRDocumentsUtil.class, "getSortField", filter, false);
    doCallRealMethod().when(JCRDocumentsUtil.class, "getSortDirection", filter);
    jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 0);
    verify(documentSearchServiceConnector,
           times(1)).appSearch(identity, "collaboration", "/documents/path", filter, 0, 0, "lastUpdatedDate", "ASC");

  }

  @Test
  public void createShortcut() throws Exception {
    Throwable exception =
            assertThrows(IllegalStateException.class, () -> jcrDocumentFileStorage.createShortcut(null, null));
    assertEquals("Error while creating a shortcut for document's id " + null + " to destination path" + null, exception.getMessage());

    Session systemSession = mock(Session.class);
    Node rootNode = mock(Node.class);
    ExtendedNode currentNode = Mockito.mock(ExtendedNode.class);
    ExtendedNode linkNode = mock(ExtendedNode.class);
    Property property = mock(Property.class);
    NodeType nodeType =  mock(NodeType.class);
    AccessControlList acl = new AccessControlList();
    acl.setOwner("test_root");
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(SessionProvider.createSystemProvider()).thenReturn(sessionProvider);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession(manageableRepository.getConfiguration().getDefaultWorkspaceName(),
            manageableRepository)).thenReturn(systemSession);

    when(JCRDocumentsUtil.getNodeByIdentifier(systemSession, "11111111")).thenReturn(currentNode);
    when((Node) systemSession.getItem("/Groups/spaces/test/Documents/test")).thenReturn(rootNode);

    when(currentNode.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(false);
    when(currentNode.getName()).thenReturn("test");
    when(rootNode.hasNode("test")).thenReturn(false);
    when(rootNode.addNode("test", NodeTypeConstants.EXO_SYMLINK)).thenReturn(linkNode);
    when(rootNode.getNode("test")).thenReturn(linkNode);
    when(linkNode.canAddMixin("exo:sortable")).thenReturn(true);
    when(currentNode.hasProperty("exo:title")).thenReturn(true);
    when(currentNode.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(property);
    when(property.getString()).thenReturn("test");
    when(JCRDocumentsUtil.getMimeType(currentNode)).thenReturn("testMimeType");
    when(currentNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(currentNode.getACL()).thenReturn(acl);
    when(nodeType.getName()).thenReturn("nt:file");
    when(((ExtendedNode) currentNode).getIdentifier()).thenReturn("123");
    when(linkNode.canAddMixin(NodeTypeConstants.EXO_PRIVILEGEABLE)).thenReturn(true);

    jcrDocumentFileStorage.createShortcut("11111111", "/Groups/spaces/test/Documents/test");
    verify(sessionProvider, times(1)).close();
  }

  @Test
  public void getFileVersions() throws RepositoryException {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    Node node = mock(Node.class);
    Version baseVersion = mock(Version.class);
    when(node.getBaseVersion()).thenReturn(baseVersion);
    when(baseVersion.getName()).thenReturn("2");
    when(node.getUUID()).thenReturn("123");
    when(session.getNodeByUUID("123")).thenReturn(node);
    VersionHistory versionHistory = mock(VersionHistory.class);
    when(node.getVersionHistory()).thenReturn(versionHistory);
    Version rootVersion = mock(Version.class);
    when(versionHistory.getRootVersion()).thenReturn(rootVersion);
    VersionIterator versionIterator = mock(VersionIterator.class);
    when(versionHistory.getAllVersions()).thenReturn(versionIterator);
    when(versionIterator.hasNext()).thenReturn(true,false);
    Version version = mock(Version.class);
    when(versionHistory.getVersionLabels(version)).thenReturn(new String[]{"1@label"});
    when(version.getName()).thenReturn("1");
    when(version.getUUID()).thenReturn("111");
    when(versionIterator.nextVersion()).thenReturn(version);
    when(rootVersion.getUUID()).thenReturn("333");
    when(version.getUUID()).thenReturn("222");
    Value titleValue = mock(Value.class);
    Value ownerValue = mock(Value.class);
    Property titleProperty = mock(Property.class);
    Property ownerProperty = mock(Property.class);

    when(node.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(titleProperty);
    when(titleProperty.getValue()).thenReturn(titleValue);
    when(titleValue.getString()).thenReturn("test.docx");

    Node frozenNode = mock(Node.class);

    when(frozenNode.getProperty(NodeTypeConstants.EXO_LAST_MODIFIER)).thenReturn(ownerProperty);
    when(ownerProperty.getValue()).thenReturn(ownerValue);
    when(ownerValue.getString()).thenReturn("user");

    Profile profile = new Profile();
    profile.setProperty("firstName", "user");
    profile.setProperty("lastName", "user");
    Identity identity1 = mock(Identity.class);
    when(identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "user")).thenReturn(identity1);
    when(identity1.getProfile()).thenReturn(profile);

    when(version.getNode(NodeTypeConstants.JCR_FROZEN_NODE)).thenReturn(frozenNode);
    when(frozenNode.getUUID()).thenReturn("666");
    when(version.getCreated()).thenReturn(Calendar.getInstance());

    List<FileVersion> versions = jcrDocumentFileStorage.getFileVersions("123", "user");
    assertNotNull(versions);
    assertEquals(1, versions.size());
  }

  @Test
  public void updateVersionSummary() throws RepositoryException {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);

    Node node = mock(Node.class);
    when(node.getUUID()).thenReturn("123");
    when(session.getNodeByUUID("123")).thenReturn(node);
    Version version = mock(Version.class);
    when(session.getNodeByUUID("333")).thenReturn(version);
    Node frozen = mock(Node.class);
    when(version.getNode(NodeTypeConstants.JCR_FROZEN_NODE)).thenReturn(frozen);
    when(frozen.hasProperty("eoo:commentId")).thenReturn(true);
    Property property = mock(Property.class);
    when(frozen.getProperty("eoo:commentId")).thenReturn(property);
    when(property.getString()).thenReturn("comment1");

    ExoSocialActivity activity = mock(ExoSocialActivity.class);
    when(activityManager.getActivity("comment1")).thenReturn(activity);
    doNothing().when(activityManager).updateActivity(activity);

    String[] oldLabels = {"1@test", "1:test2"};
    when(version.getName()).thenReturn("1");
    VersionHistory versionHistory = mock(VersionHistory.class);
    when(node.getVersionHistory()).thenReturn(versionHistory);
    PowerMockito.doNothing().when(versionHistory).removeVersionLabel(anyString());
    PowerMockito.doNothing().when(versionHistory).addVersionLabel(anyString(),anyString(),anyBoolean());
    when(versionHistory.getVersionLabels(version)).thenReturn(oldLabels);
    jcrDocumentFileStorage.updateVersionSummary("123", "333", "summary", "user");
    verify(session, times(1)).save();
  }

  @Test
  public void restoreVersion() throws RepositoryException {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);

    Version version = mock(Version.class);
    Node frozen = mock(Node.class);
    Node node = mock(Node.class);
    when(session.getNodeByUUID("123")).thenReturn(version);
    when(version.getNode(NodeTypeConstants.JCR_FROZEN_NODE)).thenReturn(frozen);
    when(frozen.hasProperty(NodeTypeConstants.JCR_FROZEN_UUID)).thenReturn(true);
    when(Utils.getStringProperty(frozen, NodeTypeConstants.JCR_FROZEN_UUID)).thenReturn("111");
    when(session.getNodeByUUID("111")).thenReturn(node);
    when(node.isCheckedOut()).thenReturn(false);
    PowerMockito.doNothing().when(node).checkout();
    PowerMockito.doNothing().when(node).restore(version, true);
    this.jcrDocumentFileStorage.restoreVersion("123", "user");
    verify(node, times(1)).restore(version, true);
    verify(node, times(1)).checkin();

  }
}
