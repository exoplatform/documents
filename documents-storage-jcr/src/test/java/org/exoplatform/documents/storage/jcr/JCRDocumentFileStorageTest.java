package org.exoplatform.documents.storage.jcr;



import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getIdentityRootNode;
import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getNodeByIdentifier;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.*;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;


import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.metadata.tag.TagService;
import org.exoplatform.social.metadata.tag.model.TagName;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.junit.runner.RunWith;

import org.exoplatform.commons.ObjectAlreadyExistsException;
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
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Calendar;
import java.util.List;

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
  
  @Mock
  private TagService                     tagService;


  private JCRDocumentFileStorage         jcrDocumentFileStorage;

  @Before
  public void setUp() {
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
    Node currentNode = mock(ExtendedNode.class);
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
    AccessControlEntry accessControlEntry = new AccessControlEntry("username", "read");
    AccessControlList acl1 = new AccessControlList("username", Arrays.asList(accessControlEntry));
    when(((ExtendedNode) currentNode).getACL()).thenReturn(acl1);
    when(linkNode.canAddMixin(NodeTypeConstants.EXO_PRIVILEGEABLE)).thenReturn(true);
    jcrDocumentFileStorage.shareDocument("1", 1L);
    PowerMockito.verifyStatic(Utils.class, times(1));

    Utils.broadcast(listenerService, "share_document_event", identity, linkNode);
    verify(sessionProvider, times(1)).close();

    //assert that linkNode set  read only permissions
    verify(linkNode).setPermissions(argThat((Map<String, String[]> map) -> map.containsKey("username") && Arrays.equals(map.get("username"),new String[]{"read"})));

    //share document with edit permission
    AccessControlEntry accessControlEntry1 = new AccessControlEntry("username", "edit");
    AccessControlList acl = new AccessControlList("username", Arrays.asList(accessControlEntry1));
    when(((ExtendedNode) currentNode).getACL()).thenReturn(acl);
    jcrDocumentFileStorage.shareDocument("1", 1L);

    //assert that the linkNode set edit permission
    verify(linkNode).setPermissions(argThat((Map<String, String[]> map) -> map.containsKey("username") && Arrays.equals(map.get("username"),new String[]{"edit"})));

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
    PowerMockito.verifyStatic(VersionHistoryUtils.class, times(1));
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
    FolderNode folderWithNumericName = new FolderNode();
    folderWithNumericName.setName("15");
    FolderNode folderWithSpecificName = new FolderNode();
    folderWithSpecificName.setName("15f");
    FolderNode folderWithSpecificName1 = new FolderNode();
    folderWithSpecificName1.setName("16L");
    when(nodeIterator.hasNext()).thenReturn(true, true, false);
    Node fileNode = mock(Node.class);
    Node folderNode1 = mock(Node.class);
    Node folderNode2 = mock(Node.class);
    Node folderNode3 = mock(Node.class);
    Node folderNode4 = mock(Node.class);
    Node folderNode5 = mock(Node.class);
    when(fileNode.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
    when(folderNode1.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderNode2.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderNode3.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderNode4.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderNode5.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(nodeIterator.nextNode()).thenReturn(folderNode2, folderNode1);
    when(JCRDocumentsUtil.toNodes(identityManager,
                                  userSession,
                                  nodeIterator,
                                  identity,
                                  spaceService,
                                  false,
                                  filter)).thenCallRealMethod();
    when(JCRDocumentsUtil.toFileNode(identityManager, identity, fileNode, "", spaceService)).thenReturn(file);
    when(JCRDocumentsUtil.toFolderNode(identityManager, identity, folderNode1, "", spaceService)).thenReturn(folder1);
    when(JCRDocumentsUtil.toFolderNode(identityManager, identity, folderNode2, "", spaceService)).thenReturn(folder2);
    when(JCRDocumentsUtil.toFolderNode(identityManager, identity, folderNode3, "", spaceService)).thenReturn(folderWithNumericName);
    when(JCRDocumentsUtil.toFolderNode(identityManager, identity, folderNode4, "", spaceService)).thenReturn(folderWithSpecificName);
    when(JCRDocumentsUtil.toFolderNode(identityManager, identity, folderNode5, "", spaceService)).thenReturn(folderWithSpecificName1);


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
    when(nodeIterator1.nextNode()).thenReturn(folderNode2, folderNode1);
    when(JCRDocumentsUtil.toNodes(
                            identityManager,
                            userSession,
                            nodeIterator1,
                            identity,
                            spaceService,
                            false,
            filter)).thenCallRealMethod();
    List<AbstractNode> nodes1 = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 2);
    assertEquals(2, nodes1.size());
    when(nodeIterator1.hasNext()).thenReturn(true, false);
    when(nodeIterator1.nextNode()).thenReturn(folderNode2);
    nodes1 = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 2, 4);
    assertEquals(1, nodes1.size());

    // case folder with specific name
    NodeIterator nodeIterator2 = mock(NodeIterator.class);
    when(queryResult.getNodes()).thenReturn(nodeIterator2);
    when(nodeIterator2.hasNext()).thenReturn(true, true, true,false);
    when(nodeIterator2.nextNode()).thenReturn(folderNode3, folderNode4, folderNode5);
    when(JCRDocumentsUtil.toNodes(identityManager,
                            userSession,
                            nodeIterator2,
                            identity,
                            spaceService,
                            false,
            filter)).thenCallRealMethod();

    //assert NumberFormatException when try to parse specific folder name
    String folderName = folderWithSpecificName.getName();
    Assert.assertThrows(NumberFormatException.class, () -> Integer.parseInt(folderName));

    List<AbstractNode> nodes2 = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 3);

    //assert that the method return the correct result and dosen't throw any exception
    assertEquals(3, nodes2.size());
    assertEquals("15", nodes2.get(0).getName());
    assertEquals("15f", nodes2.get(1).getName());
    assertEquals("16L", nodes2.get(2).getName());

    // case filter with query
    filter.setQuery("docum");
    when(userSession.getWorkspace()).thenReturn(workspace);
    when(workspace.getName()).thenReturn("collaboration");
    doCallRealMethod().when(JCRDocumentsUtil.class, "getSortField", filter, false);
    doCallRealMethod().when(JCRDocumentsUtil.class, "getSortDirection", filter);
    jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 0);
    verify(documentSearchServiceConnector,
           times(1)).search(identity, "collaboration", "/documents/path", filter, 0, 0, "lastUpdatedDate", "ASC");

  }

  @Test
  public void createShortcut() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session userSession = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(userSession);
    Throwable exception =
            assertThrows(IllegalStateException.class, () -> jcrDocumentFileStorage.createShortcut(null, null, "user", null));
    assertEquals("Error while creating a shortcut for document's id " + null + " to destination path" + null, exception.getMessage());

    Node rootNode = mock(Node.class);
    ExtendedNode currentNode = mock(ExtendedNode.class);
    ExtendedNode linkNode = mock(ExtendedNode.class);
    Property property = mock(Property.class);
    NodeType nodeType =  mock(NodeType.class);
    AccessControlList acl = new AccessControlList();
    acl.setOwner("test_root");

    when(JCRDocumentsUtil.getNodeByIdentifier(userSession, "11111111")).thenReturn(currentNode);
    when((Node) userSession.getItem("/Groups/spaces/test/Documents/test")).thenReturn(rootNode);

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

    jcrDocumentFileStorage.createShortcut("11111111", "/Groups/spaces/test/Documents/test", "user", null);
    verify(sessionProvider, times(2)).close();
    when(rootNode.hasNode("test")).thenReturn(true);
    when(rootNode.addNode("test", NodeTypeConstants.EXO_SYMLINK)).thenReturn(currentNode);
    when(currentNode.getPath()).thenReturn("/Groups/spaces/test/Documents/test[1]");
    jcrDocumentFileStorage.createShortcut("11111111", "/Groups/spaces/test/Documents/test", "user", "keepBoth");
    verify(sessionProvider, times(3)).close();
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
    when(node.isNodeType(NodeTypeConstants.EXO_MODIFY)).thenReturn(true);
    this.jcrDocumentFileStorage.restoreVersion("123", "user");
    verify(node, times(1)).restore(version, true);
    verify(node, times(1)).checkin();
  }

  @Test
  public void renameDocument() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class,
                                       () -> this.jcrDocumentFileStorage.renameDocument(1L, "123", "test:<*?", identity));
    assertEquals("document title is not valid", exception.getMessage());
    exception = assertThrows(IllegalArgumentException.class,
            () -> this.jcrDocumentFileStorage.renameDocument(1L, "123", "   ", identity));
    assertEquals("document title is not valid", exception.getMessage());
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    Node node = mock(Node.class);
    when(getNodeByIdentifier(session, "123")).thenReturn(node);
    when(identity.getUserId()).thenReturn("user");
    doCallRealMethod().when(JCRDocumentsUtil.class, "isValidDocumentTitle", anyString());
    doCallRealMethod().when(JCRDocumentsUtil.class, "cleanName", anyString());
    when(node.getName()).thenReturn("oldName");
    when(node.canAddMixin(NodeTypeConstants.EXO_MODIFY)).thenReturn(true);
    when(node.canAddMixin(NodeTypeConstants.EXO_SORTABLE)).thenReturn(true);
    when(node.hasProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(true);
    Node parentNode = mock(Node.class);
    when(node.getParent()).thenReturn(parentNode);
    when(node.getPath()).thenReturn("nodePath");
    when(parentNode.getPath()).thenReturn("parentNodePath");
    when(node.getSession()).thenReturn(session);
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
    doNothing().when(workspace).move(anyString(), anyString());
    this.jcrDocumentFileStorage.renameDocument(1L, "123", "test.docx", identity);
    verify(node, times(2)).save();
    verify(sessionProvider, times(1)).close();
    Node parent = mock(Node.class);
    Node existNode = mock(Node.class);
    when(node.getParent()).thenReturn(parent);
    when(parent.hasNode("exist")).thenReturn(true);
    when(parent.getNode("exist")).thenReturn(existNode);
    NodeType nodeType = mock(NodeType.class);
    when(nodeType.getName()).thenReturn("nt:file");
    when(existNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(node.getPrimaryNodeType()).thenReturn(nodeType);
    assertThrows(ObjectAlreadyExistsException.class,
            () -> this.jcrDocumentFileStorage.renameDocument(1L, "123", "exist", identity));
  }

  @Test
  public void testGetFullTreeData() throws Exception {
    String userName = "Adham";
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identity.getUserId()).thenReturn(userName);

    long ownerId = 1L;
    Identity ownerIdentity = mock(Identity.class);
    String folderId = "uniqueFolderIdentifier";
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    when(identityManager.getIdentity(String.valueOf(ownerId))).thenReturn(ownerIdentity);

    List<FullTreeItem> fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, identity);
    assertTrue("When node is null, return empty list", fullTreeItemList.isEmpty());

    Node folderNode = mock(NodeImpl.class);
    when(folderNode.getName()).thenReturn("myFolder");
    when(folderNode.getPath()).thenReturn("/root/folder");

    NodeIterator nodeIterator = mock(NodeIterator.class);
    when(nodeIterator.hasNext()).thenReturn(false);
    when(folderNode.getNodes()).thenReturn(nodeIterator);

    when(getNodeByIdentifier(session, folderId)).thenReturn(folderNode);

    // return list with just the parent folder when the node has no child nodes
    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, identity);
    assertEquals(1, fullTreeItemList.size());

    // when current folder is hidden
    Node hiddenFolder = mock(NodeImpl.class);
    when(hiddenFolder.isNodeType(NodeTypeConstants.EXO_HIDDENABLE)).thenReturn(true);
    NodeIterator nodeIteratorFolder = mock(NodeIterator.class);
    Node childFolder = mock(NodeImpl.class);
    when(nodeIteratorFolder.hasNext()).thenReturn(true, false);
    when(nodeIteratorFolder.nextNode()).thenReturn(childFolder);
    when(hiddenFolder.getNodes()).thenReturn(nodeIteratorFolder);

    when(nodeIterator.hasNext()).thenReturn(true, false);
    when(nodeIterator.nextNode()).thenReturn(hiddenFolder);
    when(folderNode.getNodes()).thenReturn(nodeIterator);


    // return list with just the parent folder when it contains just a hidden folder
    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, identity);
    assertEquals(1, fullTreeItemList.size());

    Node folderNTFolder = mock(NodeImpl.class);
    when(folderNTFolder.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderNTFolder.getName()).thenReturn("ntFolderName");
    when(folderNTFolder.getPath()).thenReturn("/root/folder/ntFolderName");
    when(((NodeImpl)folderNTFolder).getIdentifier()).thenReturn("ntFolderIdentifier");
    when(folderNTFolder.getNodes()).thenReturn(nodeIteratorFolder);

    Node folderNTUnstructured = mock(NodeImpl.class);
    when(folderNTUnstructured.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED)).thenReturn(true);
    when(folderNTUnstructured.getName()).thenReturn("ntUnstructuredFolderName");
    when(folderNTUnstructured.getPath()).thenReturn("/root/folder/ntUnstructuredFolderName");
    when(((NodeImpl)folderNTUnstructured).getIdentifier()).thenReturn("ntUnstructuredFolderIdentifier");
    when(folderNTUnstructured.getNodes()).thenReturn(nodeIteratorFolder);

    Node symlinkFolder = mock(NodeImpl.class);
    when(symlinkFolder.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true);
    when(symlinkFolder.getPath()).thenReturn("/root/folder/symlinkFolderName.lnk");
    when(symlinkFolder.getName()).thenReturn("symlinkFolderName.lnk");
    Node sourceFolder = mock(NodeImpl.class);
    when(sourceFolder.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED)).thenReturn(true);
    when(sourceFolder.getName()).thenReturn("sourceFolderName");
    when(sourceFolder.getPath()).thenReturn("/root/anotherFolder/sourceFolderName");
    String sourceFolderIdentifier = "sourceFolderIdentifier";
    when(((NodeImpl)sourceFolder).getIdentifier()).thenReturn(sourceFolderIdentifier);
    when(sourceFolder.getNodes()).thenReturn(nodeIteratorFolder);
    Property symlinkUUID = mock(Property.class);
    when(symlinkUUID.getString()).thenReturn(sourceFolderIdentifier);
    when(symlinkFolder.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(symlinkUUID);
    when(getNodeByIdentifier(session, sourceFolderIdentifier)).thenReturn(sourceFolder);
    when(symlinkFolder.getNodes()).thenReturn(nodeIteratorFolder);

    when(nodeIterator.hasNext()).thenReturn(true, true, true, false);
    when(nodeIterator.nextNode()).thenReturn(folderNTFolder, folderNTUnstructured, symlinkFolder);
    when(folderNode.getNodes()).thenReturn(nodeIterator);

    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, identity);
    assertEquals(1, fullTreeItemList.size());
    assertEquals(3, fullTreeItemList.get(0).getChildren().size());

    // Natural sorted items
    Node folder1 = mock(NodeImpl.class);
    when(folder1.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folder1.getName()).thenReturn("folder1");
    when(folder1.getPath()).thenReturn("/root/folder/folder1");
    when(((NodeImpl)folder1).getIdentifier()).thenReturn("folder1Identifiuer");
    when(folder1.getNodes()).thenReturn(nodeIteratorFolder);

    Node folder2 = mock(NodeImpl.class);
    when(folder2.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folder2.getName()).thenReturn("folder2");
    when(folder2.getPath()).thenReturn("/root/folder/folder2");
    when(((NodeImpl)folder2).getIdentifier()).thenReturn("folder2Identifiuer");
    when(folder2.getNodes()).thenReturn(nodeIteratorFolder);

    Node folder10 = mock(NodeImpl.class);
    when(folder10.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folder10.getName()).thenReturn("folder10");
    when(folder10.getPath()).thenReturn("/root/folder/folder10");
    when(((NodeImpl)folder10).getIdentifier()).thenReturn("folder10Identifiuer");
    when(folder10.getNodes()).thenReturn(nodeIteratorFolder);


    when(nodeIterator.hasNext()).thenReturn(true, true, true, false);
    when(nodeIterator.nextNode()).thenReturn(folder1, folder10, folder2);
    when(folderNode.getNodes()).thenReturn(nodeIterator);

    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, identity);
    assertEquals(1, fullTreeItemList.size());
    assertEquals(3, fullTreeItemList.get(0).getChildren().size());
    //assert that the folder1 on the first position
    assertEquals("folder1", fullTreeItemList.get(0).getChildren().get(0).getName());
    //assert that the folder2 on the second position
    assertEquals("folder2", fullTreeItemList.get(0).getChildren().get(1).getName());
    //assert that the folder10 on the last position
    assertEquals("folder10", fullTreeItemList.get(0).getChildren().get(2).getName());

    // when folder ID is null, we return user Home folder
    Node userHome = mock(NodeImpl.class);
    when(((NodeImpl)userHome).getIdentifier()).thenReturn("userHomeFolderIdentifier");
    when(userHome.getPath()).thenReturn("/Users/adham");
    when(userHome.getName()).thenReturn("Home folder of Adham");
    when(nodeIterator.hasNext()).thenReturn(true, true, false);
    when(nodeIterator.nextNode()).thenReturn(folderNTFolder, folderNTUnstructured);
    when(userHome.getNodes()).thenReturn(nodeIterator);
    when(getIdentityRootNode(spaceService, nodeHierarchyCreator, userName, ownerIdentity, sessionProvider)).thenReturn(userHome);

    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, null, identity);

    assertEquals(1, fullTreeItemList.size());
    assertEquals(2, fullTreeItemList.get(0).getChildren().size());


    // When symlink is a link of one of its parents, then we do not check its sub-folders
    when(symlinkUUID.getString()).thenReturn(folderId);
    when(symlinkFolder.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(symlinkUUID);
    when(nodeIterator.hasNext()).thenReturn(true, false);
    when(nodeIterator.nextNode()).thenReturn(symlinkFolder);
    when(folderNode.getNodes()).thenReturn(nodeIterator);


    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, identity);
    assertEquals(1, fullTreeItemList.size());
    assertTrue(fullTreeItemList.get(0).getChildren().isEmpty());

  }

  @Test
  public void updateDocumentDescription() throws RepositoryException {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identity.getUserId()).thenReturn("user");
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(JCRDocumentsUtil.getUserSessionProvider(repositoryService, identity))
                      .thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    ExtendedNode node = mock(ExtendedNode.class);
    Node contentNode = mock(Node.class);
    when(JCRDocumentsUtil.getNodeByIdentifier(session, "123")).thenReturn(node);
    when(node.canAddMixin(NodeTypeConstants.EXO_MODIFY)).thenReturn(true);
    when(node.canAddMixin(NodeTypeConstants.DC_ELEMENT_SET)).thenReturn(true);
    when(node.hasProperty(NodeTypeConstants.DC_DESCRIPTION)).thenReturn(false);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(node.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(contentNode);
    when(node.getSession()).thenReturn(session);
    when(CommonsUtils.getService(TagService.class)).thenReturn(tagService);
    Set<TagName> tagNames = new HashSet<>();
    tagNames.add(new TagName("test"));
    when(tagService.detectTagNames(anyString())).thenReturn(tagNames);
    when(node.getPath()).thenReturn("path");
    Identity audienceIdentity = mock(Identity.class);
    when(JCRDocumentsUtil.getOwnerIdentityFromNodePath("path", identityManager, spaceService))
                      .thenReturn(audienceIdentity);
    when(audienceIdentity.getProviderId()).thenReturn("space");
    Space space = new Space();
    space.setId("1");
    when(audienceIdentity.getRemoteId()).thenReturn("testSpace");
    when(audienceIdentity.getId()).thenReturn("1");
    Identity userIdentity = mock(Identity.class);
    when(userIdentity.getId()).thenReturn("1");
    when(identityManager.getOrCreateUserIdentity("user")).thenReturn(userIdentity);
    when(spaceService.getSpaceByPrettyName("testSpace")).thenReturn(space);
    when(node.getIdentifier()).thenReturn("123");
    this.jcrDocumentFileStorage.updateDocumentDescription(1L, "123", "test description", identity);
    verify(session, times(1)).save();
    verify(sessionProvider, times(1)).close();
  }
  
  @Test
  public void testFoldersThenFilesLoading() throws Exception {
    Node parentNode = mock(Node.class);
    Session userSession = mock(Session.class);
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identity.getUserId()).thenReturn("user");
    DocumentFolderFilter filter = new DocumentFolderFilter("12e2", "documents/path", 1L, "");

    // mock session
    SessionProvider sessionProvider = mock(SessionProvider.class);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(JCRDocumentsUtil.getUserSessionProvider(repositoryService, identity))
                      .thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(userSession);

    when(JCRDocumentsUtil.getNodeByIdentifier(userSession, filter.getParentFolderId()))
                      .thenReturn(parentNode);
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
    NodeIterator subItemsIterator = mock(NodeIterator.class);
    QueryResult queryResult = mock(QueryResult.class);
    when(userSession.getWorkspace()).thenReturn(workspace);

    Node folderAbc = createFolderMock("Abc", Calendar.getInstance(), userSession);
    Node folderXyz = createFolderMock("Xyz", Calendar.getInstance(), userSession);
    Node folderEfg = createFolderMock("Efg", Calendar.getInstance(), userSession);
    Node file1 = createFileMock("file1", Calendar.getInstance(), userSession);
    Node file2 = createFileMock("file2", Calendar.getInstance(), userSession);
    Node symlinkFile2 = createSymlinkMock("file2FileIdentifier", "file2");

    Node symlinkFolderEfg = createSymlinkMock("EfgIdentifier", "Efg");

    when(subItemsIterator.hasNext()).thenReturn(true, true, true, true, true, false);
    when(subItemsIterator.nextNode()).thenReturn(file1, symlinkFile2, folderXyz, folderAbc, symlinkFolderEfg);

    when(queryResult.getNodes()).thenReturn(subItemsIterator);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    when(queryManager.createQuery(anyString(), anyString())).thenReturn(jcrQuery);
    when(jcrQuery.execute()).thenReturn(queryResult);

    when(JCRDocumentsUtil.toNodes(any(), any(), any(), any(), any(), anyBoolean(), any()))
                      .thenCallRealMethod();
    when(JCRDocumentsUtil.toFolderNode(any(), any(), any(), any(), any())).thenCallRealMethod();
    doCallRealMethod().when(JCRDocumentsUtil.class,"retrieveFileProperties",any(), any(), any(), any(), any());
    when(JCRDocumentsUtil.toFileNode(any(IdentityManager.class),
                                                              any(org.exoplatform.services.security.Identity.class),
                                                              any(Node.class),
                                                              anyString(),
                                                              any(SpaceService.class)))
                      .thenCallRealMethod();

     //creation date
    filter.setSortField(DocumentSortField.CREATED_DATE);
    filter.setAscending(true);
    List<AbstractNode> fileNodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 5);
    assertNotNull(fileNodes);
    assertEquals("Abc", fileNodes.get(0).getName());
    assertEquals("Xyz", fileNodes.get(1).getName());
    assertEquals("Efg.lnk", fileNodes.get(2).getName());

    filter.setAscending(false);
    when(subItemsIterator.hasNext()).thenReturn(true, true, true, false);
    when(subItemsIterator.nextNode()).thenReturn(folderXyz, folderAbc, symlinkFolderEfg);
    fileNodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 5);
    assertEquals("Abc", fileNodes.get(2).getName());
    assertEquals("Xyz", fileNodes.get(1).getName());
    assertEquals("Efg.lnk", fileNodes.get(0).getName());

    // modified date
    when(subItemsIterator.hasNext()).thenReturn(true, true, true, false);
    when(subItemsIterator.nextNode()).thenReturn(folderXyz, folderAbc, symlinkFolderEfg);

    filter.setSortField(DocumentSortField.MODIFIED_DATE);
    filter.setAscending(true);

    fileNodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 5);
    assertNotNull(fileNodes);
    assertEquals("Abc", fileNodes.get(0).getName());
    assertEquals("Xyz", fileNodes.get(1).getName());
    assertEquals("Efg.lnk", fileNodes.get(2).getName());

    when(subItemsIterator.hasNext()).thenReturn(true, true, true, false);
    when(subItemsIterator.nextNode()).thenReturn(folderXyz, folderAbc, symlinkFolderEfg);

    filter.setAscending(false);

    fileNodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 5);
    assertEquals("Abc", fileNodes.get(2).getName());
    assertEquals("Xyz", fileNodes.get(1).getName());
    assertEquals("Efg.lnk", fileNodes.get(0).getName());

    when(subItemsIterator.hasNext()).thenReturn(true, true, true, false);
    when(subItemsIterator.nextNode()).thenReturn(folderXyz, folderAbc, symlinkFolderEfg);
    // name
    filter.setSortField(DocumentSortField.NAME);
    filter.setAscending(true);

    fileNodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 5);
    assertNotNull(fileNodes);
    assertEquals("Abc", fileNodes.get(0).getName());
    assertEquals("Xyz", fileNodes.get(2).getName());
    assertEquals("Efg.lnk", fileNodes.get(1).getName());

    when(subItemsIterator.hasNext()).thenReturn(true, true, true, false);
    when(subItemsIterator.nextNode()).thenReturn(folderXyz, folderAbc, symlinkFolderEfg);

    filter.setAscending(false);
    when(subItemsIterator.hasNext()).thenReturn(true, true, true, false);
    when(subItemsIterator.nextNode()).thenReturn(folderXyz, folderAbc, symlinkFolderEfg);
    fileNodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 5);
    assertEquals("Abc", fileNodes.get(2).getName());
    assertEquals("Xyz", fileNodes.get(0).getName());
    assertEquals("Efg.lnk", fileNodes.get(1).getName());

    Node file3 = createFileMock("file3", Calendar.getInstance(), userSession);
    Node file4 = createFileMock("file4", Calendar.getInstance(), userSession);
    Node file5 = createFileMock("file5", Calendar.getInstance(), userSession);
    Node file6 = createFileMock("file6", Calendar.getInstance(), userSession);
    Node file7 = createFileMock("file7", Calendar.getInstance(), userSession);
    Node file8 = createFileMock("file8", Calendar.getInstance(), userSession);
    Node file9 = createFileMock("file9", Calendar.getInstance(), userSession);
    Node file10 = createFileMock("file10", Calendar.getInstance(), userSession);

    when(subItemsIterator.hasNext()).thenReturn(false, true, true, true, true, true, true, true, true, true, true, false);
    when(subItemsIterator.nextNode()).thenReturn(file1, file2, file3, file4, file5, file6, file7, file8, file9, file10);

    fileNodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 4);
    assertNotNull(fileNodes);
    assertEquals(4, fileNodes.size());

    when(subItemsIterator.hasNext()).thenReturn(false, true, true, true, true, true, true, true, true, true, true, false);
    when(subItemsIterator.nextNode()).thenReturn(file1, file2, file3, file4, file5, file6, file7, file8, file9, file10);

    fileNodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 8);
    assertNotNull(fileNodes);
    assertEquals(8, fileNodes.size());

    when(subItemsIterator.hasNext()).thenReturn(false, true, true, true, true, true, true, true, true, true, true, false);
    when(subItemsIterator.nextNode()).thenReturn(file1, file2, file3, file4, file5, file6, file7, file8, file9, file10);

    fileNodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 12);
    assertNotNull(fileNodes);
    assertEquals(10, fileNodes.size());

  }

  private Node createFolderMock(String name, Calendar createdDate, Session session) throws RepositoryException {
    Node folderMock = mock(NodeImpl.class);
    Property namePropertyXyz = mock(Property.class);
    when(namePropertyXyz.getString()).thenReturn(name);
    when(folderMock.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(namePropertyXyz);
    when(folderMock.getName()).thenReturn(name);
    when(folderMock.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderMock.hasProperty("ecd:connected")).thenReturn(false);
    when(folderMock.getPath()).thenReturn("/path/to/" + name);
    when(((NodeImpl)folderMock).getIdentifier()).thenReturn(name + "Identifier");
    when(folderMock.hasProperty(NodeTypeConstants.EXO_DATE_CREATED)).thenReturn(true);
    Property createdDateProperty = mock(Property.class);
    when(createdDateProperty.getDate()).thenReturn(createdDate);
    when(folderMock.getProperty(NodeTypeConstants.EXO_DATE_CREATED)).thenReturn(createdDateProperty);
    when(getNodeByIdentifier(session, name + "Identifier")).thenReturn(folderMock);
    when(getNodeByIdentifier(null, name + "Identifier")).thenReturn(folderMock);
    return folderMock;
  }

  private Node createFileMock(String name, Calendar createdDate, Session session) throws RepositoryException {
    Node fileMock = mock(NodeImpl.class);
    Property nameProperty = mock(Property.class);
    when(nameProperty.getString()).thenReturn(name);
    when(fileMock.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(nameProperty);
    when(fileMock.getName()).thenReturn(name);
    when(fileMock.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
    when(fileMock.hasProperty("ecd:connected")).thenReturn(false);
    when(fileMock.getPath()).thenReturn("/path/to/" + name);
    when(((NodeImpl)fileMock).getIdentifier()).thenReturn(name + "FileIdentifier");
    when(fileMock.hasProperty(NodeTypeConstants.EXO_DATE_CREATED)).thenReturn(true);
    Property createdDateProperty = mock(Property.class);
    when(createdDateProperty.getDate()).thenReturn(createdDate);
    when(fileMock.getProperty(NodeTypeConstants.EXO_DATE_CREATED)).thenReturn(createdDateProperty);
    when(getNodeByIdentifier(session, name + "FileIdentifier")).thenReturn(fileMock);
    when(getNodeByIdentifier(null, name + "FileIdentifier")).thenReturn(fileMock);
    return fileMock;
  }

  private Node createSymlinkMock(String nodeIdentifier, String nodeName) throws RepositoryException {
    Node symlink = mock(NodeImpl.class);
    when(symlink.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true);
    Property symlinkUUIDProperty = mock(Property.class);
    when(symlinkUUIDProperty.getString()).thenReturn(nodeIdentifier);
    when(symlink.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(symlinkUUIDProperty);
    when(symlink.getName()).thenReturn(nodeName + ".lnk");
    when(symlink.getPath()).thenReturn("/path/to/" + nodeName);
    Property createdDate = mock(Property.class);
    when(createdDate.getDate()).thenReturn(Calendar.getInstance());
    when(symlink.getProperty(NodeTypeConstants.EXO_DATE_CREATED)).thenReturn(createdDate);
    when(symlink.hasProperty(NodeTypeConstants.EXO_DATE_CREATED)).thenReturn(true);
    when(((NodeImpl)symlink).getIdentifier()).thenReturn(nodeName + "LinkIdentifier");
    return symlink;
  }
  
    @Test
  public void countNodeAccessListTest() throws RepositoryException {
    ExtendedNode extendedNode = mock(ExtendedNode.class);
    org.exoplatform.services.security.Identity aclIdentity = mock(org.exoplatform.services.security.Identity.class);
    when(aclIdentity.getUserId()).thenReturn("john");
    AccessControlEntry accessControlEntry = new AccessControlEntry("*:/spaces/testspace", PermissionType.READ);
    AccessControlList acl1 = new AccessControlList("john", Arrays.asList(accessControlEntry));
    when(aclIdentity.isMemberOf(accessControlEntry.getMembershipEntry())).thenReturn(true);
    when(extendedNode.getACL()).thenReturn(acl1);
    //when
    Map<String, Boolean> accessList = jcrDocumentFileStorage.countNodeAccessList(extendedNode,aclIdentity);
    //then
    assertEquals(false, accessList.isEmpty());
    assertEquals(true, accessList.get("canAccess"));
    assertEquals(false, accessList.get("canEdit"));
    assertEquals(false, accessList.get("canDelete"));

    AccessControlEntry accessControlEntry1 = new AccessControlEntry("*:/spaces/testspace", PermissionType.SET_PROPERTY);
    AccessControlList acl2 = new AccessControlList("john", Arrays.asList(accessControlEntry,accessControlEntry1));
    when(aclIdentity.isMemberOf(accessControlEntry1.getMembershipEntry())).thenReturn(true);
    when(extendedNode.getACL()).thenReturn(acl2);

    //when
    Map<String, Boolean> accessList1 = jcrDocumentFileStorage.countNodeAccessList(extendedNode,aclIdentity);

    //then
    assertEquals(false, accessList1.isEmpty());
    assertEquals(true, accessList1.get("canAccess"));
    assertEquals(true, accessList1.get("canEdit"));
  }
}
