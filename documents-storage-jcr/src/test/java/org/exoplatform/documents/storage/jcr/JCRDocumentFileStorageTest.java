package org.exoplatform.documents.storage.jcr;

import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getIdentityRootNode;
import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getNodeByIdentifier;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.*;

import javax.jcr.*;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;

import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.jcr.bulkactions.BulkStorageActionService;
import org.exoplatform.documents.storage.jcr.search.DocumentSearchServiceConnector;
import org.exoplatform.documents.storage.jcr.search.DocumentTextExtractor;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.documents.storage.jcr.util.Utils;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.ext.utils.VersionHistoryUtils;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.WorkspaceImpl;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.exoplatform.services.cms.documents.NewDocumentTemplate;
import org.exoplatform.services.cms.documents.NewDocumentTemplateProvider;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.tag.TagService;
import org.exoplatform.social.metadata.tag.model.TagName;
import org.exoplatform.upload.UploadResource;
import org.exoplatform.upload.UploadService;
import org.mockito.ArgumentCaptor;
import org.exoplatform.documents.constant.TranscriptionStatus;

@RunWith(MockitoJUnitRunner.Silent.class)
public class JCRDocumentFileStorageTest {

  private static final MockedStatic<Utils>               UTILS                 = mockStatic(Utils.class);
  
  private static final MockedStatic<JCRDocumentsUtil>    JCR_DOCUMENTS_UTIL    = mockStatic(JCRDocumentsUtil.class);

  private static final MockedStatic<SessionProvider>     SESSION_PROVIDER      = mockStatic(SessionProvider.class);

  private static final MockedStatic<CommonsUtils>        COMMONS_UTILS_UTIL    = mockStatic(CommonsUtils.class);

  private static final MockedStatic<VersionHistoryUtils> VERSION_HISTORY_UTILS = mockStatic(VersionHistoryUtils.class);

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
  private UploadService                                  uploadService;

  @Mock
  private IdentityRegistry               identityRegistry;

  @Mock
  private ActivityManager                activityManager;
  
  @Mock
  private TagService                     tagService;

  @Mock
  private BulkStorageActionService                       bulkStorageActionService;

  @Mock
  private org.exoplatform.services.cms.documents.DocumentService documentService;


  private JCRDocumentFileStorage         jcrDocumentFileStorage;

  @AfterClass
  public static void afterRunBare() {
    JCR_DOCUMENTS_UTIL.close();
    UTILS.close();
    SESSION_PROVIDER.close();
    COMMONS_UTILS_UTIL.close();
    VERSION_HISTORY_UTILS.close();
  }

  @Before
  public void setUp() {
    this.jcrDocumentFileStorage = new JCRDocumentFileStorage(nodeHierarchyCreator,
                                                             repositoryService,
                                                             documentSearchServiceConnector,
                                                             identityManager,
                                                             spaceService,
                                                             listenerService,
                                                             uploadService,
                                                             identityRegistry,
                                                             activityManager,
                                                             bulkStorageActionService,
                                                             documentService);
  }

  @Test
  public void getFileTextContentReadsTheSearchIndexFirst() {
    DocumentTextExtractor textExtractor = mock(DocumentTextExtractor.class);
    jcrDocumentFileStorage.setTextExtractor(textExtractor);
    when(documentSearchServiceConnector.getFileContentAsText("doc")).thenReturn("indexed text");

    DocumentTextContent content = jcrDocumentFileStorage.getFileTextContent("doc");

    assertEquals("indexed text", content.text());
    assertEquals(DocumentTextContent.Status.INDEXED, content.status());
    assertEquals("indexed text", jcrDocumentFileStorage.getFileContentAsText("doc"));
    verify(textExtractor, never()).extract(anyString());
  }

  @Test
  public void getFileTextContentExtractsTheTextFromTheFileWhenTheIndexHasNone() {
    DocumentTextExtractor textExtractor = mock(DocumentTextExtractor.class);
    jcrDocumentFileStorage.setTextExtractor(textExtractor);
    when(documentSearchServiceConnector.getFileContentAsText("doc")).thenReturn(null);
    when(textExtractor.extract("doc")).thenReturn(DocumentTextContent.of("extracted", DocumentTextContent.Status.EXTRACTED));

    assertEquals(DocumentTextContent.Status.EXTRACTED, jcrDocumentFileStorage.getFileTextContent("doc").status());
    assertEquals("extracted", jcrDocumentFileStorage.getFileContentAsText("doc"));
  }

  @Test
  public void getFileTextContentExtractsTheTextFromTheFileWhenTheIndexedTextIsBlank() {
    DocumentTextExtractor textExtractor = mock(DocumentTextExtractor.class);
    jcrDocumentFileStorage.setTextExtractor(textExtractor);
    when(documentSearchServiceConnector.getFileContentAsText("doc")).thenReturn("  ");
    when(textExtractor.extract("doc")).thenReturn(DocumentTextContent.of("extracted", DocumentTextContent.Status.EXTRACTED));

    assertEquals("extracted", jcrDocumentFileStorage.getFileTextContent("doc").text());
  }

  @Test
  public void getFileTextContentExtractsTheTextFromTheFileWhenTheIndexFails() {
    DocumentTextExtractor textExtractor = mock(DocumentTextExtractor.class);
    jcrDocumentFileStorage.setTextExtractor(textExtractor);
    when(documentSearchServiceConnector.getFileContentAsText("doc")).thenThrow(new IllegalStateException("ES down"));
    when(textExtractor.extract("doc")).thenReturn(DocumentTextContent.of("extracted", DocumentTextContent.Status.EXTRACTED));

    assertEquals("extracted", jcrDocumentFileStorage.getFileTextContent("doc").text());
  }

  @Test
  public void getFileContentAsTextIsNullWhenNeitherTheIndexNorTheFileGiveText() {
    DocumentTextExtractor textExtractor = mock(DocumentTextExtractor.class);
    jcrDocumentFileStorage.setTextExtractor(textExtractor);
    when(documentSearchServiceConnector.getFileContentAsText("doc")).thenReturn(null);
    when(textExtractor.extract("doc")).thenReturn(DocumentTextContent.none(DocumentTextContent.Status.UNSUPPORTED_FORMAT));

    assertNull(jcrDocumentFileStorage.getFileContentAsText("doc"));
    assertEquals(DocumentTextContent.Status.UNSUPPORTED_FORMAT, jcrDocumentFileStorage.getFileTextContent("doc").status());
  }

  @Test
  public void getLongPropertyFallsBackToTheDefaultOnAnInvalidValue() {
    assertEquals(5, JCRDocumentFileStorage.getLongProperty(property("5"), 10));
    assertEquals(10, JCRDocumentFileStorage.getLongProperty(property("abc"), 10));
    assertEquals(10, JCRDocumentFileStorage.getLongProperty(property("0"), 10));
    assertEquals(10, JCRDocumentFileStorage.getLongProperty(property("-3"), 10));
    assertEquals(10, JCRDocumentFileStorage.getLongProperty(property("99999999999999999999"), 10));
    assertEquals(10, JCRDocumentFileStorage.getLongProperty(property("3000000000"), 10));
    assertEquals(10, JCRDocumentFileStorage.getLongProperty("exo.documents.test.limit.absent", 10));
  }

  /**
   * Sets a platform property of its own name, the platform caching read values.
   *
   * @param value the property value
   * @return the property name
   */
  private static String property(String value) {
    String name = "exo.documents.test.limit." + UUID.randomUUID();
    PropertyManager.setProperty(name, value);
    return name;
  }

  @Test
  public void shareDocument() throws Exception {
    Session systemSession = mock(Session.class);
    Identity identity = mock(Identity.class);
    org.exoplatform.services.security.Identity aclIdentity = mock(org.exoplatform.services.security.Identity.class);
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
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "1")).thenReturn(currentNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService, nodeHierarchyCreator,identity, systemSession)).thenReturn(rootNode);
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
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getMimeType(currentNode)).thenReturn("testMimeType");
    when(currentNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn("nt:file");
    when(((ExtendedNode) currentNode).getIdentifier()).thenReturn("123");
    when(identity.getRemoteId()).thenReturn("username");
    AccessControlEntry accessControlEntry = new AccessControlEntry("username", "read");
    AccessControlList acl1 = new AccessControlList("username", Arrays.asList(accessControlEntry));
    when(((ExtendedNode) currentNode).getACL()).thenReturn(acl1);
    when(linkNode.canAddMixin(NodeTypeConstants.EXO_PRIVILEGEABLE)).thenReturn(true);
    jcrDocumentFileStorage.shareDocument("1", 1L,aclIdentity, false);

    UTILS.verify(() -> times(1));
    Utils.broadcast(listenerService, "share_document_event", identity, linkNode);
    verify(sessionProvider, times(1)).close();

    //assert that linkNode set  read only permissions
    verify(linkNode).setPermissions(argThat((Map<String, String[]> map) -> map.containsKey("username") && Arrays.equals(map.get("username"),new String[]{"read"})));

    //share document with edit permission
    AccessControlEntry accessControlEntry1 = new AccessControlEntry("username", "edit");
    AccessControlList acl = new AccessControlList("username", Arrays.asList(accessControlEntry1));
    when(((ExtendedNode) currentNode).getACL()).thenReturn(acl);
    jcrDocumentFileStorage.shareDocument("1", 1L,aclIdentity, false);

    //assert that the linkNode set edit permission
    verify(linkNode).setPermissions(argThat((Map<String, String[]> map) -> map.containsKey("username") && Arrays.equals(map.get("username"),new String[]{"edit"})));

    // case of space member
    boolean notifyMember = true ;
    jcrDocumentFileStorage.shareDocument("1", 1L,aclIdentity, notifyMember);
    //assert that the event broadcast with the target node as parameter
    UTILS.verify(() -> Utils.broadcast(listenerService, "share_document_event", identity, currentNode) ,atLeast(1));

    when(rootNode.hasNode("Shared")).thenReturn(true);
    when(rootNode.getNode("Shared")).thenReturn(sharedNode);
    when(sharedNode.hasNode(currentNode.getName())).thenReturn(true);
    when(sharedNode.getNode(currentNode.getName())).thenReturn(linkNode);
    when(linkNode.getACL()).thenReturn(acl);
    // no member
    jcrDocumentFileStorage.shareDocument("1", 1L,aclIdentity, false);

    // Assert that the shared document event was not broadcast
    UTILS.verify(() -> Utils.broadcast(listenerService, "share_document_event", identity, linkNode), atLeast(0));
    verify(sessionProvider, atLeast(1)).close();

    // space member
    jcrDocumentFileStorage.shareDocument("1", 1L,aclIdentity, notifyMember);
    // Assert that the shared document event was not broadcast for member
    UTILS.verify(() -> Utils.broadcast(listenerService, "share_document_event", identity, currentNode), atLeast(0));
    verify(sessionProvider, atLeast(1)).close();
  }

  @Test
  public void createDocumentFromTemplateMatchesDottedExtension() throws Exception {
    // Regression: onlyoffice stores the template extension WITH a leading dot
    // (".docx"). The caller passes "docx" (dot-stripped). The match must normalize
    // the dot on both sides, otherwise ".docx".equalsIgnoreCase("docx") is always
    // false and every call wrongly throws ObjectNotFoundException.
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("john");
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, aclIdentity))
                      .thenReturn(sessionProvider);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);

    ExtendedNode folderNode = mock(ExtendedNode.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "1")).thenReturn(folderNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isValidDocumentTitle("Q3 report.docx")).thenReturn(true);
    // Grant the user edit access so countNodeAccessList reports canEdit=true.
    AccessControlEntry editEntry = new AccessControlEntry("john", PermissionType.ADD_NODE);
    when(folderNode.getACL()).thenReturn(new AccessControlList("john", Arrays.asList(editEntry)));

    // A template whose extension carries the leading dot, as onlyoffice stores it.
    NewDocumentTemplate template = mock(NewDocumentTemplate.class);
    when(template.getExtension()).thenReturn(".docx");
    NewDocumentTemplateProvider provider = mock(NewDocumentTemplateProvider.class);
    when(provider.getTemplates()).thenReturn(Arrays.asList(template));
    when(documentService.getNewDocumentTemplateProviders()).thenReturn(Arrays.asList(provider));

    Node createdNode = mock(Node.class);
    when(documentService.createDocumentFromTemplate(folderNode, "Q3 report.docx", template)).thenReturn(createdNode);
    FileNode expected = new FileNode();
    expected.setId("created-1");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFileNode(identityManager, aclIdentity, createdNode, "", spaceService))
                      .thenReturn(expected);

    AbstractNode result = jcrDocumentFileStorage.createDocumentFromTemplate(0L, "1", null, "Q3 report.docx", "docx", aclIdentity);

    // The dotted template must be matched (no ObjectNotFoundException) and used to
    // create the document.
    assertNotNull(result);
    assertEquals("created-1", result.getId());
    verify(documentService).createDocumentFromTemplate(folderNode, "Q3 report.docx", template);
  }

  @Test
  public void duplicateDocument() throws Exception {
    Session systemSession = mock(Session.class);
    Identity identity = mock(Identity.class);
    Node rootNode = mock(Node.class);
    NodeImpl currentNode = mock(NodeImpl.class);
    NodeImpl parentNode = mock(NodeImpl.class);

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
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "1")).thenReturn(currentNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService, nodeHierarchyCreator,identity, systemSession)).thenReturn(rootNode);
    when(identity.getProviderId()).thenReturn("USER");
    when(rootNode.getNode("Documents")).thenReturn(rootNode);
    when(currentNode.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(false);
    when(currentNode.getName()).thenReturn("test");
    when(currentNode.hasProperty("exo:title")).thenReturn(true);
    when(currentNode.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(property);
    when(property.getString()).thenReturn("test");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getMimeType(currentNode)).thenReturn("testMimeType");
    when(currentNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn("nt:file");
    when(currentNode.getUUID()).thenReturn("123");
    when(currentNode.getParent()).thenReturn(parentNode);
    when(currentNode.getIdentifier()).thenReturn("1");
    when(parentNode.getIdentifier()).thenReturn("1");
    when(currentNode.addNode("copy of test","nt:file")).thenReturn(currentNode);
    when(parentNode.addNode("copy of test", "nt:file")).thenReturn(currentNode);
    when(identity.getRemoteId()).thenReturn("username");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID)).thenReturn(sessionProvider);
    when(parentNode.getPath()).thenReturn("/Documents/Private/user");
    when(currentNode.getPath()).thenReturn("/Documents/Private/user/test");
    when(currentNode.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    Assert.assertThrows(IllegalStateException.class, () -> jcrDocumentFileStorage.duplicateDocument(1L, "1", "copy of", userID));
    when(currentNode.getPath()).thenReturn("/Documents/Private");
    when(parentNode.getPath()).thenReturn("/Documents/Private/user/test");
    when(currentNode.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(false);
    jcrDocumentFileStorage.duplicateDocument(1L,"1","copy of",userID);
    verify(sessionProvider, times(2)).close();
  }

  /**
   * EXO-89931: duplicating "report.docx" into its own folder used to create
   * "report.docx (1)", a name no longer recognised as a .docx, so the copy would
   * not open. The counter must land before the extension.
   * <p>
   * {@code getNewIndexedName} and {@code isFolder} are stubbed to call the real
   * methods: this class mocks {@code JCRDocumentsUtil} statically, so leaving them
   * mocked would make the assertion pass against any implementation.
   */
  @Test
  public void duplicateDocumentPutsTheCopyCounterBeforeTheExtension() throws Exception {
    Session systemSession = mock(Session.class);
    NodeImpl currentNode = mock(NodeImpl.class);
    NodeImpl parentNode = mock(NodeImpl.class);
    Property property = mock(Property.class);
    NodeType nodeType = mock(NodeType.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity("username");
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(systemSession);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID))
                      .thenReturn(sessionProvider);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "1")).thenReturn(currentNode);
    // The naming helpers must be real here, or this test cannot fail.
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNewIndexedName(anyString(), anyString())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isFolder(any(Node.class))).thenCallRealMethod();

    when(currentNode.getName()).thenReturn("report.docx");
    when(currentNode.hasProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(true);
    when(currentNode.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(property);
    when(property.getString()).thenReturn("report.docx");
    when(currentNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn(NodeTypeConstants.NT_FILE);
    when(currentNode.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(false);
    when(currentNode.getParent()).thenReturn(parentNode);
    when(currentNode.getIdentifier()).thenReturn("1");
    when(parentNode.getIdentifier()).thenReturn("1");
    when(currentNode.getPath()).thenReturn("/Documents/Private");
    when(parentNode.getPath()).thenReturn("/Documents/Private/user/report.docx");
    // The source itself occupies the name, so the counter loop runs exactly once.
    when(parentNode.hasNode("report.docx")).thenReturn(true);
    when(parentNode.hasNode("report (1).docx")).thenReturn(false);
    when(parentNode.addNode(anyString(), anyString())).thenReturn(currentNode);

    jcrDocumentFileStorage.duplicateDocument(1L, "1", "", userID);

    // Before the fix this was addNode("report.docx (1)", ...), which Mockito reports
    // verbatim as the actual invocation when this verification fails.
    verify(parentNode).addNode("report (1).docx", NodeTypeConstants.NT_FILE);
    // The listing and the MCP tools display exo:title, not the node name: the title
    // must carry the same "counter before the extension" form (round 3 of #2081).
    verify(currentNode).setProperty(NodeTypeConstants.EXO_TITLE, "report (1).docx");
  }

  @Test
  public void duplicateFolderKeepsTheCounterTrailing() throws Exception {
    Session systemSession = mock(Session.class);
    NodeImpl currentNode = mock(NodeImpl.class);
    NodeImpl parentNode = mock(NodeImpl.class);
    Property property = mock(Property.class);
    NodeType nodeType = mock(NodeType.class);
    NodeIterator emptyChildren = mock(NodeIterator.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity("username");
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(systemSession);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID))
                      .thenReturn(sessionProvider);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "1")).thenReturn(currentNode);
    // The naming helpers must be real here, or this test cannot fail.
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNewIndexedName(anyString(), anyString())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isFolder(any(Node.class))).thenCallRealMethod();

    // A dotted folder name: only reachable via WebDAV (MKCOL bypasses cleanName's
    // dot-stripping for nt:folder), but a real state duplicateItem must handle
    // (round 6 of #2081).
    when(currentNode.getName()).thenReturn("2026.Q1 reports");
    when(currentNode.hasProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(true);
    when(currentNode.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(property);
    when(property.getString()).thenReturn("2026.Q1 reports");
    when(currentNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn(NodeTypeConstants.NT_FOLDER);
    when(currentNode.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(currentNode.getParent()).thenReturn(parentNode);
    when(currentNode.getIdentifier()).thenReturn("1");
    when(parentNode.getIdentifier()).thenReturn("1");
    when(currentNode.getPath()).thenReturn("/Documents/Private");
    when(parentNode.getPath()).thenReturn("/Documents/Private/user/2026.Q1 reports");
    when(parentNode.hasNode("2026.q1 reports")).thenReturn(true);
    when(parentNode.hasNode("2026.q1 reports (1)")).thenReturn(false);
    when(parentNode.addNode(anyString(), anyString())).thenReturn(currentNode);
    // A folder recurses into its children; this one has none.
    when(currentNode.getNodes()).thenReturn(emptyChildren);

    jcrDocumentFileStorage.duplicateDocument(1L, "1", "", userID);

    // isFolder(oldNode) must keep the counter trailing for a folder even though its
    // name contains a dot: without that guard this would be "2026 (1).Q1 reports".
    verify(parentNode).addNode("2026.q1 reports (1)", NodeTypeConstants.NT_FOLDER);
    verify(currentNode).setProperty(NodeTypeConstants.EXO_TITLE, "2026.Q1 reports (1)");
  }

  @Test
  public void copyDocument() throws Exception {
    Session systemSession = mock(Session.class);
    Identity identity = mock(Identity.class);
    NodeImpl node = mock(NodeImpl.class);
    NodeImpl destinationNode = mock(NodeImpl.class);
    NodeImpl parentNode = mock(NodeImpl.class);
    NodeType nodeType = mock(NodeType.class);

    SessionProvider sessionProvider = mock(SessionProvider.class);
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity("username");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID)).thenReturn(sessionProvider);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession(manageableRepository.getConfiguration().getDefaultWorkspaceName(),
            manageableRepository)).thenReturn(systemSession);
    when(identityManager.getIdentity("1")).thenReturn(identity);
    Assert.assertThrows(ObjectNotFoundException.class, () -> jcrDocumentFileStorage.copyDocument("1", "2", userID));
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "1")).thenReturn(node);
    Assert.assertThrows(ObjectNotFoundException.class, () -> jcrDocumentFileStorage.copyDocument("1", "2", userID));
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "2")).thenReturn(destinationNode);
    when(node.getPath()).thenReturn("/Documents/Private");
    when(destinationNode.getPath()).thenReturn("/Documents/Private/user/test");
    when(node.getParent()).thenReturn(parentNode);
    when(parentNode.getIdentifier()).thenReturn("123");
    when(node.getIdentifier()).thenReturn("12");
    when(destinationNode.getIdentifier()).thenReturn("12");
    when(node.getName()).thenReturn("nodeName");
    when(node.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn("nt:file");
    when(destinationNode.addNode("nodeName", "nt:file")).thenReturn(node);
    jcrDocumentFileStorage.copyDocument("1", "2", userID);
    verify(sessionProvider, times(3)).close();
  }

  /**
   * A copy into another folder that already holds a file of the source's name
   * gets the same {@code " (n)"} counter as an in-place duplicate, before the
   * extension, on its node name and its title, and keeps the source's case and
   * no prefix.
   * Mutants: the counter limited to an in-place copy again, or the copy's name
   * lower-cased like an in-place duplicate.
   *
   * @throws Exception never
   */
  @Test
  public void copyIntoAnotherFolderHoldingTheSameNameGetsTheCopyCounter() throws Exception {
    Session systemSession = mock(Session.class);
    NodeImpl sourceNode = mock(NodeImpl.class);
    NodeImpl sourceParent = mock(NodeImpl.class);
    NodeImpl destinationNode = mock(NodeImpl.class);
    NodeImpl copyNode = mock(NodeImpl.class);
    Property property = mock(Property.class);
    NodeType nodeType = mock(NodeType.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity("username");
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(systemSession);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID))
                      .thenReturn(sessionProvider);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "1")).thenReturn(sourceNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "2")).thenReturn(destinationNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNewIndexedName(anyString(), anyString())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isFolder(any(Node.class))).thenCallRealMethod();

    when(sourceNode.getName()).thenReturn("Report.docx");
    when(sourceNode.hasProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(true);
    when(sourceNode.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(property);
    when(property.getString()).thenReturn("Report.docx");
    when(sourceNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn(NodeTypeConstants.NT_FILE);
    when(sourceNode.getParent()).thenReturn(sourceParent);
    when(sourceParent.getIdentifier()).thenReturn("10");
    when(destinationNode.getIdentifier()).thenReturn("2");
    when(sourceNode.getPath()).thenReturn("/Groups/spaces/legal/Documents/Report.docx");
    when(sourceParent.getPath()).thenReturn("/Groups/spaces/legal/Documents");
    when(destinationNode.getPath()).thenReturn("/Groups/spaces/legal/Documents/Archive");
    when(destinationNode.hasNode("Report.docx")).thenReturn(true);
    when(destinationNode.hasNode("Report (1).docx")).thenReturn(false);
    when(destinationNode.addNode(anyString(), anyString())).thenReturn(copyNode);

    jcrDocumentFileStorage.copyDocument("1", "2", userID);

    verify(destinationNode).addNode("Report (1).docx", NodeTypeConstants.NT_FILE);
    verify(copyNode).setProperty(NodeTypeConstants.EXO_TITLE, "Report (1).docx");
  }

  /**
   * A prefixed duplicate of a folder prefixes the folder only: its children
   * are copied under their own names into the new folder.
   * Mutant: the prefix applied to every copied node, not only to the
   * in-place duplicate.
   *
   * @throws Exception never
   */
  @Test
  public void aPrefixedFolderDuplicateKeepsItsChildrensNames() throws Exception {
    Session systemSession = mock(Session.class);
    NodeImpl folderNode = mock(NodeImpl.class);
    NodeImpl parentNode = mock(NodeImpl.class);
    NodeImpl newFolder = mock(NodeImpl.class);
    NodeImpl childNode = mock(NodeImpl.class);
    NodeImpl newChild = mock(NodeImpl.class);
    NodeType folderType = mock(NodeType.class);
    NodeType fileType = mock(NodeType.class);
    NodeIterator children = mock(NodeIterator.class);
    NodeIterator noChildren = mock(NodeIterator.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity("username");
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(systemSession);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID))
                      .thenReturn(sessionProvider);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "1")).thenReturn(folderNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNewIndexedName(anyString(), anyString())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isFolder(any(Node.class))).thenCallRealMethod();

    when(folderNode.getName()).thenReturn("contracts");
    when(folderNode.getPrimaryNodeType()).thenReturn(folderType);
    when(folderType.getName()).thenReturn(NodeTypeConstants.NT_FOLDER);
    when(folderNode.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderNode.getParent()).thenReturn(parentNode);
    when(folderNode.getPath()).thenReturn("/Documents/contracts");
    when(parentNode.getPath()).thenReturn("/Documents");
    when(parentNode.getIdentifier()).thenReturn("10");
    when(parentNode.addNode(anyString(), anyString())).thenReturn(newFolder);
    when(newFolder.getIdentifier()).thenReturn("20");
    when(newFolder.getPath()).thenReturn("/Documents/copy contracts");
    when(folderNode.getNodes()).thenReturn(children);
    when(children.hasNext()).thenReturn(true, false);
    when(children.nextNode()).thenReturn(childNode);
    when(childNode.getName()).thenReturn("nda.txt");
    when(childNode.getPrimaryNodeType()).thenReturn(fileType);
    when(fileType.getName()).thenReturn(NodeTypeConstants.NT_FILE);
    when(childNode.getParent()).thenReturn(folderNode);
    when(childNode.getPath()).thenReturn("/Documents/contracts/nda.txt");
    when(newFolder.addNode(anyString(), anyString())).thenReturn(newChild);
    lenient().when(newChild.getNodes()).thenReturn(noChildren);

    jcrDocumentFileStorage.duplicateDocument(1L, "1", "copy", userID);

    verify(parentNode).addNode("copy contracts", NodeTypeConstants.NT_FOLDER);
    verify(newFolder).addNode("nda.txt", NodeTypeConstants.NT_FILE);
  }

  /**
   * A folder copied into another folder that already holds a folder of the
   * same name in another case gets the copy counter: a folder is added under
   * its lower-cased name, so the clash is probed on that name, not on the
   * source's mixed-case one, which no node holds.
   * Mutant: the probe on the name as it is.
   *
   * @throws Exception never
   */
  @Test
  public void aMixedCaseFolderCopiedOntoItsLowerCaseNameGetsTheCopyCounter() throws Exception {
    Session systemSession = mock(Session.class);
    NodeImpl sourceNode = mock(NodeImpl.class);
    NodeImpl sourceParent = mock(NodeImpl.class);
    NodeImpl destinationNode = mock(NodeImpl.class);
    NodeImpl copyNode = mock(NodeImpl.class);
    NodeType nodeType = mock(NodeType.class);
    NodeIterator noChildren = mock(NodeIterator.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity("username");
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(systemSession);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID))
                      .thenReturn(sessionProvider);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "1")).thenReturn(sourceNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "2")).thenReturn(destinationNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNewIndexedName(anyString(), anyString())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isFolder(any(Node.class))).thenCallRealMethod();

    when(sourceNode.getName()).thenReturn("Reports");
    when(sourceNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn(NodeTypeConstants.NT_FOLDER);
    when(sourceNode.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(sourceNode.getParent()).thenReturn(sourceParent);
    when(sourceParent.getIdentifier()).thenReturn("10");
    when(destinationNode.getIdentifier()).thenReturn("2");
    when(sourceNode.getPath()).thenReturn("/Groups/spaces/legal/Documents/Reports");
    when(sourceParent.getPath()).thenReturn("/Groups/spaces/legal/Documents");
    when(destinationNode.getPath()).thenReturn("/Groups/spaces/legal/Documents/Archive");
    when(destinationNode.hasNode("reports")).thenReturn(true);
    when(destinationNode.addNode(anyString(), anyString())).thenReturn(copyNode);
    when(sourceNode.getNodes()).thenReturn(noChildren);

    jcrDocumentFileStorage.copyDocument("1", "2", userID);

    verify(destinationNode).addNode("reports (1)", NodeTypeConstants.NT_FOLDER);
  }

  /**
   * The copy counter takes a name as taken when the destination holds it as
   * it is or as the node is added under. An in-place duplicate of a
   * mixed-case file finds the source itself, then its earlier lower-cased
   * duplicate ({@code report (1).docx}), and is added as
   * {@code report (2).docx}; an escaped folder name ({@code 50%25 plan}) is
   * added URL-decoded, so it clashes with the decoded one.
   * Mutants: the probe on the name as it is only, on the added name only, the
   * in-place half of the lower-casing dropped, or the probe on the escaped
   * name.
   *
   * @throws Exception never
   */
  @Test
  public void theCopyCounterProbesTheDecodedLowerCasedName() throws Exception {
    Session systemSession = mock(Session.class);
    NodeImpl fileNode = mock(NodeImpl.class);
    NodeImpl folderNode = mock(NodeImpl.class);
    NodeImpl parentNode = mock(NodeImpl.class);
    NodeImpl destinationNode = mock(NodeImpl.class);
    NodeImpl copyNode = mock(NodeImpl.class);
    NodeType fileType = mock(NodeType.class);
    NodeType folderType = mock(NodeType.class);
    NodeIterator noChildren = mock(NodeIterator.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity("username");
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(systemSession);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID))
                      .thenReturn(sessionProvider);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "1")).thenReturn(fileNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "3")).thenReturn(folderNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "2")).thenReturn(destinationNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNewIndexedName(anyString(), anyString())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isFolder(any(Node.class))).thenCallRealMethod();

    when(fileNode.getName()).thenReturn("Report.docx");
    when(fileNode.getPrimaryNodeType()).thenReturn(fileType);
    when(fileType.getName()).thenReturn(NodeTypeConstants.NT_FILE);
    when(fileNode.getParent()).thenReturn(parentNode);
    when(fileNode.getPath()).thenReturn("/Groups/spaces/legal/Documents/Report.docx");
    when(parentNode.getIdentifier()).thenReturn("10");
    when(parentNode.getPath()).thenReturn("/Groups/spaces/legal/Documents");
    when(parentNode.hasNode("Report.docx")).thenReturn(true);
    when(parentNode.hasNode("report (1).docx")).thenReturn(true);
    when(parentNode.addNode(anyString(), anyString())).thenReturn(copyNode);

    jcrDocumentFileStorage.duplicateDocument(1L, "1", "", userID);

    verify(parentNode).addNode("report (2).docx", NodeTypeConstants.NT_FILE);

    when(folderNode.getName()).thenReturn("50%25 plan");
    when(folderNode.getPrimaryNodeType()).thenReturn(folderType);
    when(folderType.getName()).thenReturn(NodeTypeConstants.NT_FOLDER);
    when(folderNode.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderNode.getParent()).thenReturn(parentNode);
    when(folderNode.getPath()).thenReturn("/Groups/spaces/legal/Documents/50%25 plan");
    when(folderNode.getNodes()).thenReturn(noChildren);
    when(destinationNode.getIdentifier()).thenReturn("2");
    when(destinationNode.getPath()).thenReturn("/Groups/spaces/legal/Documents/Archive");
    when(destinationNode.hasNode("50% plan")).thenReturn(true);
    when(destinationNode.addNode(anyString(), anyString())).thenReturn(copyNode);

    jcrDocumentFileStorage.copyDocument("3", "2", userID);

    verify(destinationNode).addNode("50% plan (1)", NodeTypeConstants.NT_FOLDER);
  }

  /**
   * A file copied into another folder keeps its name and title when that
   * folder holds only its lower-cased name: a file copied out of place is
   * added under its name as it is, so the lower-cased one is no clash.
   * Mutant: every copy probed lower-cased.
   *
   * @throws Exception never
   */
  @Test
  public void aFileCopiedNextToItsLowerCasedNameKeepsItsName() throws Exception {
    Session systemSession = mock(Session.class);
    NodeImpl sourceNode = mock(NodeImpl.class);
    NodeImpl sourceParent = mock(NodeImpl.class);
    NodeImpl destinationNode = mock(NodeImpl.class);
    NodeImpl copyNode = mock(NodeImpl.class);
    Property property = mock(Property.class);
    NodeType nodeType = mock(NodeType.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    org.exoplatform.services.security.Identity userID = new org.exoplatform.services.security.Identity("username");
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(systemSession);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, userID))
                      .thenReturn(sessionProvider);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "1")).thenReturn(sourceNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "2")).thenReturn(destinationNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNewIndexedName(anyString(), anyString())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isFolder(any(Node.class))).thenCallRealMethod();

    when(sourceNode.getName()).thenReturn("Report.docx");
    when(sourceNode.hasProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(true);
    when(sourceNode.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(property);
    when(property.getString()).thenReturn("Report.docx");
    when(sourceNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn(NodeTypeConstants.NT_FILE);
    when(sourceNode.getParent()).thenReturn(sourceParent);
    when(sourceParent.getIdentifier()).thenReturn("10");
    when(destinationNode.getIdentifier()).thenReturn("2");
    when(sourceNode.getPath()).thenReturn("/Groups/spaces/legal/Documents/Report.docx");
    when(sourceParent.getPath()).thenReturn("/Groups/spaces/legal/Documents");
    when(destinationNode.getPath()).thenReturn("/Groups/spaces/legal/Documents/Archive");
    when(destinationNode.hasNode("report.docx")).thenReturn(true);
    when(destinationNode.hasNode("Report.docx")).thenReturn(false);
    when(destinationNode.addNode(anyString(), anyString())).thenReturn(copyNode);

    jcrDocumentFileStorage.copyDocument("1", "2", userID);

    verify(destinationNode).addNode("Report.docx", NodeTypeConstants.NT_FILE);
    verify(copyNode).setProperty(NodeTypeConstants.EXO_TITLE, "Report.docx");
  }

  @Test
  public void getFolderChildNodes() throws Exception {
    Node parentNode = mock(Node.class);
    Session userSession = mock(Session.class);
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Identity ownerIdentity = mock(Identity.class);
    when(identity.getUserId()).thenReturn("user");
    DocumentFolderFilter filter = new DocumentFolderFilter("12e2", null, 1L, "");

    // mock session
    SessionProvider sessionProvider = mock(SessionProvider.class);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, identity)).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession(manageableRepository.getConfiguration().getDefaultWorkspaceName(),
                                    manageableRepository)).thenReturn(userSession);

    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(userSession, filter.getParentFolderId())).thenReturn(parentNode);
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
    NodeImpl fileNode = mock(NodeImpl.class);
    NodeImpl folderNode1 = mock(NodeImpl.class);
    NodeImpl folderNode2 = mock(NodeImpl.class);
    NodeImpl folderNode3 = mock(NodeImpl.class);
    NodeImpl folderNode4 = mock(NodeImpl.class);
    NodeImpl folderNode5 = mock(NodeImpl.class);
    when(fileNode.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
    AccessControlList accessControlList = mock(AccessControlList.class);
    when(accessControlList.getPermissionEntries()).thenReturn(new ArrayList<>());
    when(((ExtendedNode)folderNode1).getACL()).thenReturn(accessControlList);
    when(((ExtendedNode)folderNode2).getACL()).thenReturn(accessControlList);
    when(((ExtendedNode)folderNode3).getACL()).thenReturn(accessControlList);
    when(((ExtendedNode)folderNode4).getACL()).thenReturn(accessControlList);
    when(((ExtendedNode)folderNode5).getACL()).thenReturn(accessControlList);
    when(folderNode1.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderNode2.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderNode3.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderNode4.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(folderNode5.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);

    when(nodeIterator.nextNode()).thenReturn(folderNode2, folderNode1);
    when(nodeIterator.hasNext()).thenReturn(true, true, false);

    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.computeDocumentAcl(any(), any(), any(), any(), any())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFolderNode(identityManager, identity, folderNode1, "", spaceService)).thenReturn(folder1);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFolderNode(identityManager, identity, folderNode2, "", spaceService)).thenReturn(folder2);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFolderNode(identityManager, identity, folderNode3, "", spaceService)).thenReturn(folderWithNumericName);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFolderNode(identityManager, identity, folderNode4, "", spaceService)).thenReturn(folderWithSpecificName);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFolderNode(identityManager, identity, folderNode5, "", spaceService)).thenReturn(folderWithSpecificName1);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFileNode(identityManager, identity, fileNode, "", spaceService)).thenReturn(file);

    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toNodesInRowOrder(eq(identityManager),
                                                                     eq(userSession),
                                                                     any(NodeIterator.class),
                                                                     eq(identity),
                                                                     eq(spaceService),
                                                                     eq(false)))
                      .thenCallRealMethod();



    List<AbstractNode> nodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 2);
    assertEquals(2, nodes.size());
    when(nodeIterator.hasNext()).thenReturn(true, false);
    when(nodeIterator.nextNode()).thenReturn(folderNode2);
    // the folder holds one child: a page starting past it is empty
    nodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 2, 4);
    assertEquals(0, nodes.size());

    // case of folderNodeId empty
    filter.setParentFolderId(null);
    when(identityManager.getIdentity(String.valueOf(filter.getOwnerId()))).thenReturn(ownerIdentity);
    NodeImpl parentNodeImp = mock(NodeImpl.class);
    when(parentNodeImp.getName()).thenReturn("documents");
    when(parentNodeImp.getNode(filter.getFolderPath())).thenReturn(parentNodeImp);
    when(parentNodeImp.getPath()).thenReturn("/documents/path");
    when(parentNodeImp.getIdentifier()).thenReturn("parentNodeIdentifier");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService,
                                                                       nodeHierarchyCreator,
                                                                       "user",
                                                                       ownerIdentity,
                                                                       sessionProvider))
                      .thenReturn(parentNodeImp);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService,
                                                                       nodeHierarchyCreator,
                                                                       ownerIdentity,
                                                                       userSession))
                      .thenReturn(parentNodeImp); 
    NodeIterator nodeIterator1 = mock(NodeIterator.class);
    when(queryResult.getNodes()).thenReturn(nodeIterator1);
    when(nodeIterator1.hasNext()).thenReturn(true, true, false);
    when(nodeIterator1.nextNode()).thenReturn(folderNode2, folderNode1);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toNodesInRowOrder(eq(identityManager),
                                                                     eq(userSession),
                                                                     any(NodeIterator.class),
                                                                     eq(identity),
                                                                     eq(spaceService),
                                                                     eq(false)))
                      .thenCallRealMethod();
    List<AbstractNode> nodes1 = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 2);
    assertEquals(2, nodes1.size());
    when(nodeIterator1.hasNext()).thenReturn(true, false);
    when(nodeIterator1.nextNode()).thenReturn(folderNode2);
    nodes1 = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 2, 4);
    assertEquals(0, nodes1.size());

    // case folder with specific name
    NodeIterator nodeIterator2 = mock(NodeIterator.class);
    when(queryResult.getNodes()).thenReturn(nodeIterator2);
    when(nodeIterator2.hasNext()).thenReturn(true, true, true,false);
    when(nodeIterator2.nextNode()).thenReturn(folderNode3, folderNode4, folderNode5);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toNodesInRowOrder(eq(identityManager),
                                                                     eq(userSession),
                                                                     any(NodeIterator.class),
                                                                     eq(identity),
                                                                     eq(spaceService),
                                                                     eq(false)))
                      .thenCallRealMethod();

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
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getSortField(filter, false)).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getSortDirection(filter)).thenCallRealMethod();
    jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 0);
    verify(documentSearchServiceConnector,
           times(1)).search(identity, "collaboration", List.of("/documents/path"), filter, 0, 0, "lastUpdatedDate", "ASC");

  }

  @Test
  public void createShortcut() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session userSession = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(userSession);
    Throwable exception =
            assertThrows(IllegalStateException.class, () -> jcrDocumentFileStorage.createShortcut(null, null, "user", null));
    assertEquals("Error while creating a shortcut for document's id " + null + " to destination path" + null, exception.getMessage());

    Node rootNode = mock(Node.class);
    ExtendedNode currentNode = Mockito.mock(ExtendedNode.class);
    ExtendedNode linkNode = mock(ExtendedNode.class);
    Property property = mock(Property.class);
    NodeType nodeType =  mock(NodeType.class);
    AccessControlList acl = new AccessControlList();
    acl.setOwner("test_root");

    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(userSession, "11111111")).thenReturn(currentNode);
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
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getMimeType(currentNode)).thenReturn("testMimeType");
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
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
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
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
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
    doNothing().when(versionHistory).removeVersionLabel(anyString());
    doNothing().when(versionHistory).addVersionLabel(anyString(),anyString(),anyBoolean());
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
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
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
    doNothing().when(node).checkout();
    doNothing().when(node).restore(version, true);
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
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    Node node = mock(Node.class);
    NodeType nodeType = mock(NodeType.class);
    when(node.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn(NodeTypeConstants.NT_FILE);
    when(getNodeByIdentifier(session, "123")).thenReturn(node);
    when(identity.getUserId()).thenReturn("user");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isValidDocumentTitle(anyString())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.cleanName(anyString(), anyString())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.cleanNameWithAccents(anyString(), anyString())).thenCallRealMethod();
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
    when(nodeType.getName()).thenReturn("nt:file");
    when(existNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(node.getPrimaryNodeType()).thenReturn(nodeType);
    assertThrows(ObjectAlreadyExistsException.class,
            () -> this.jcrDocumentFileStorage.renameDocument(1L, "123", "exist", identity));
  }
  @Test
  public void hideDocument() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Throwable exception = assertThrows(IllegalArgumentException.class,
                                       () -> this.jcrDocumentFileStorage.setDocumentVisibility(1L, "123", null, identity));
    assertEquals("Hidden value should not be null", exception.getMessage());
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    Node node = mock(Node.class);
    NodeType nodeType = mock(NodeType.class);
    when(node.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn(NodeTypeConstants.NT_FILE);
    when(getNodeByIdentifier(session, "123")).thenReturn(node);
    when(identity.getUserId()).thenReturn("user");
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
    when(node.isNodeType(NodeTypeConstants.EXO_HIDDENABLE)).thenReturn(true);
    this.jcrDocumentFileStorage.setDocumentVisibility(1L, "123", true, identity);
    verify(node, times(0)).save();
    when(node.isNodeType(NodeTypeConstants.EXO_HIDDENABLE)).thenReturn(false);
    this.jcrDocumentFileStorage.setDocumentVisibility(1L, "123", true, identity);
    verify(node, times(1)).save();
    verify(sessionProvider, times(2)).close();
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
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    when(identityManager.getIdentity(String.valueOf(ownerId))).thenReturn(ownerIdentity);



    List<FullTreeItem> fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, null, identity, true, true);
    assertTrue("When node is null, return empty list", fullTreeItemList.isEmpty());

    Node folderNode = mock(NodeImpl.class);
    when(folderNode.getName()).thenReturn("myFolder");
    when(folderNode.getPath()).thenReturn("/root/folder");

    NodeIterator nodeIterator = mock(NodeIterator.class);
    when(nodeIterator.hasNext()).thenReturn(false);
    when(folderNode.getNodes()).thenReturn(nodeIterator);

    when(getNodeByIdentifier(session, folderId)).thenReturn(folderNode);

    // return list with just the parent folder when the node has no child nodes
    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, null, identity, true, true);
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
    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, null, identity, true, false);
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

    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, null, identity, true, false);
    assertEquals(1, fullTreeItemList.size());
    assertEquals(3, fullTreeItemList.get(0).getChildren().size());

    // withChildren is false, it should return list with just the parent folder children, sub folders should not have children
    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, null, identity, false, false);
    assertEquals(1, fullTreeItemList.size());
    assertEquals(0, fullTreeItemList.get(0).getChildren().size());

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

    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, null, identity, true, false);
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

    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, null, "", identity, true, false);
    assertEquals(1, fullTreeItemList.size());
    Node destinationNode = mock(NodeImpl.class);
    when(userHome.hasNode("/root/folder/ntFolderName/testFolder")).thenReturn(true);
    when(userHome.getNode("/root/folder/ntFolderName/testFolder")).thenReturn(destinationNode);
    when(destinationNode.getPath()).thenReturn("/root/folder/ntFolderName/testFolder");
    when(nodeIterator.hasNext()).thenReturn(true, true, false);
    when(nodeIterator.nextNode()).thenReturn(folderNTFolder, folderNTUnstructured);
    when(userHome.getNodes()).thenReturn(nodeIterator);
    WorkspaceImpl workspace = mock(WorkspaceImpl.class);
    SessionImpl nodeSession = mock(SessionImpl.class);
    when(folderNTFolder.getSession()).thenReturn(nodeSession);
    when(folderNTUnstructured.getSession()).thenReturn(nodeSession);
    when(nodeSession.getWorkspace()).thenReturn(workspace);
    QueryManager queryManager = mock(QueryManager.class);
    QueryImpl jcrQuery = mock(QueryImpl.class);
    NodeIterator subItemsIterator = mock(NodeIterator.class);
    QueryResult queryResult = mock(QueryResult.class);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    when(queryManager.createQuery(anyString(), any())).thenReturn(jcrQuery);
    when(jcrQuery.execute()).thenReturn(queryResult);
    when(queryResult.getNodes()).thenReturn(subItemsIterator);
    when(subItemsIterator.getSize()).thenReturn(0L);
    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, null, "/root/folder/ntFolderName/testFolder", identity, false, false);

    assertEquals(1, fullTreeItemList.size());
    assertEquals(2, fullTreeItemList.get(0).getChildren().size());


    // When symlink is a link of one of its parents, then we do not check its sub-folders
    when(symlinkUUID.getString()).thenReturn(folderId);
    when(symlinkFolder.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(symlinkUUID);
    when(nodeIterator.hasNext()).thenReturn(true, false);
    when(nodeIterator.nextNode()).thenReturn(symlinkFolder);
    when(folderNode.getNodes()).thenReturn(nodeIterator);


    fullTreeItemList = jcrDocumentFileStorage.getFullTreeData(ownerId, folderId, null, identity, true, false);
    assertEquals(1, fullTreeItemList.size());
    assertTrue(fullTreeItemList.get(0).getChildren().isEmpty());

  }

  @Test
  public void updateDocumentDescription() throws RepositoryException {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identity.getUserId()).thenReturn("user");
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    lenient().when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, identity))
                      .thenReturn(sessionProvider);
    lenient().when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    ExtendedNode node = mock(ExtendedNode.class);
    Node contentNode = mock(Node.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "123")).thenReturn(node);
    lenient().when(node.canAddMixin(NodeTypeConstants.EXO_MODIFY)).thenReturn(true);
    lenient().when(node.canAddMixin(NodeTypeConstants.DC_ELEMENT_SET)).thenReturn(true);
    lenient().when(node.hasProperty(NodeTypeConstants.DC_DESCRIPTION)).thenReturn(false);
    lenient().when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    lenient().when(node.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(contentNode);
    lenient().when(node.getSession()).thenReturn(session);
    COMMONS_UTILS_UTIL.when(() -> CommonsUtils.getService(TagService.class)).thenReturn(tagService);
    Set<TagName> tagNames = new HashSet<>();
    tagNames.add(new TagName("test"));
    lenient().when(tagService.detectTagNames(anyString())).thenReturn(tagNames);
    lenient().when(node.getPath()).thenReturn("path");
    Identity audienceIdentity = mock(Identity.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getOwnerIdentityFromNodePath("path", identityManager, spaceService))
                      .thenReturn(audienceIdentity);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.hasEditPermission(session, node))
            .thenReturn(true);
    lenient().when(audienceIdentity.getProviderId()).thenReturn("space");
    Space space = new Space();
    space.setId("1");
    lenient().when(audienceIdentity.getRemoteId()).thenReturn("testSpace");
    lenient().when(audienceIdentity.getId()).thenReturn("1");
    Identity userIdentity = mock(Identity.class);
    lenient().when(userIdentity.getId()).thenReturn("1");
    lenient().when(identityManager.getOrCreateUserIdentity("user")).thenReturn(userIdentity);
    lenient().when(spaceService.getSpaceByPrettyName("testSpace")).thenReturn(space);
    lenient().when(node.getIdentifier()).thenReturn("123");
    this.jcrDocumentFileStorage.updateDocumentDescription(1L, "123", "test description", identity);
    verify(session, times(1)).save();
    verify(sessionProvider, times(1)).close();
  }

  @Test
  public void testFoldersThenFilesLoading() throws RepositoryException, ObjectNotFoundException, IllegalAccessException {
    Node parentNode = mock(Node.class);
    Session userSession = mock(Session.class);
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identity.getUserId()).thenReturn("user");
    DocumentFolderFilter filter = new DocumentFolderFilter("12e2", "", 1L, "");

    // mock session
    SessionProvider sessionProvider = mock(SessionProvider.class);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, identity))
                      .thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(userSession);

    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(userSession, filter.getParentFolderId()))
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

    // Explicitly spaced out timestamps (rather than successive
    // Calendar.getInstance() calls) so the created/modified date sort
    // assertions below don't depend on the clock resolution of the
    // machine running the test.
    Calendar folderAbcDate = Calendar.getInstance();
    Calendar folderXyzDate = (Calendar) folderAbcDate.clone();
    folderXyzDate.add(Calendar.MINUTE, 1);
    Calendar folderEfgDate = (Calendar) folderAbcDate.clone();
    folderEfgDate.add(Calendar.MINUTE, 2);

    Node folderAbc = createFolderMock("Abc", folderAbcDate, userSession);
    Node folderXyz = createFolderMock("Xyz", folderXyzDate, userSession);
    Node folderEfg = createFolderMock("Efg", folderEfgDate, userSession);
    Node file1 = createFileMock("file1", Calendar.getInstance(), userSession);
    Node file2 = createFileMock("file2", Calendar.getInstance(), userSession);
    Node symlinkFile2 = createSymlinkMock("file2FileIdentifier", "file2");

    Node symlinkFolderEfg = createSymlinkMock("EfgIdentifier", "Efg", folderEfgDate);

    when(subItemsIterator.hasNext()).thenReturn(true, true, true, true, true, false);
    when(subItemsIterator.nextNode()).thenReturn(file1, symlinkFile2, folderXyz, folderAbc, symlinkFolderEfg);

    when(queryResult.getNodes()).thenReturn(subItemsIterator);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    when(queryManager.createQuery(anyString(), anyString())).thenReturn(jcrQuery);
    when(jcrQuery.execute()).thenReturn(queryResult);

    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toNodesInRowOrder(any(), any(), any(), any(), any(), anyBoolean()))
                      .thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFolderNode(any(), any(), any(), any(), any())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getTitle(any()))
                      .thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.retrieveFileProperties(any(), any(), any(), any(), any()))
                      .thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFileNode(any(IdentityManager.class),
                                                              any(org.exoplatform.services.security.Identity.class),
                                                              any(Node.class),
                                                              anyString(),
                                                              any(SpaceService.class)))
                      .thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFileNode(any(IdentityManager.class),
                                                              any(org.exoplatform.services.security.Identity.class),
                                                              any(Node.class),
                                                              any(FileNode.class),
                                                              any(SpaceService.class)))
                      .thenCallRealMethod();

    // each sort: the rows come in the order the query serves them, and the
    // listing keeps it, with no sort of its own
    filter.setSortField(DocumentSortField.CREATED_DATE);
    filter.setAscending(true);
    List<AbstractNode> fileNodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 5);
    assertEquals(List.of("Xyz", "Abc", "Efg.lnk"), fileNodes.stream().map(AbstractNode::getName).toList());

    Map<DocumentSortField, List<Node>> ascendingRows = Map.of(DocumentSortField.CREATED_DATE,
                                                              List.of(folderAbc, folderXyz, symlinkFolderEfg),
                                                              DocumentSortField.MODIFIED_DATE,
                                                              List.of(folderAbc, folderXyz, symlinkFolderEfg),
                                                              DocumentSortField.NAME,
                                                              List.of(folderAbc, symlinkFolderEfg, folderXyz));
    for (DocumentSortField sortField : List.of(DocumentSortField.CREATED_DATE,
                                               DocumentSortField.MODIFIED_DATE,
                                               DocumentSortField.NAME)) {
      for (boolean ascending : List.of(true, false)) {
        List<Node> rows = new ArrayList<>(ascendingRows.get(sortField));
        if (!ascending) {
          Collections.reverse(rows);
        }
        when(subItemsIterator.hasNext()).thenReturn(true, true, true, false);
        when(subItemsIterator.nextNode()).thenReturn(rows.get(0), rows.get(1), rows.get(2));
        filter.setSortField(sortField);
        filter.setAscending(ascending);

        fileNodes = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 5);

        assertEquals(sortField + (ascending ? " ASC" : " DESC"),
                     rows.stream().map(row -> row == symlinkFolderEfg ? "Efg.lnk" : getNodeName(row)).toList(),
                     fileNodes.stream().map(AbstractNode::getName).toList());
      }
    }
    verify(jcrQuery, atLeastOnce()).setCaseInsensitiveOrder(true);
    verify(jcrQuery, never()).setCaseInsensitiveOrder(false);

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

  /**
   * Reads a mocked node's name, wrapping the repository's checked exception.
   *
   * @param node the node
   * @return its name
   */
  private static String getNodeName(Node node) {
    try {
      return node.getName();
    } catch (RepositoryException e) {
      throw new IllegalStateException(e);
    }
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
    when(folderMock.getName()).thenReturn(name);
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
    return createSymlinkMock(nodeIdentifier, nodeName, Calendar.getInstance());
  }

  private Node createSymlinkMock(String nodeIdentifier, String nodeName, Calendar createdDate) throws RepositoryException {
    Node symlink = mock(NodeImpl.class);
    when(symlink.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true);
    Property symlinkUUIDProperty = mock(Property.class);
    when(symlinkUUIDProperty.getString()).thenReturn(nodeIdentifier);
    when(symlink.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(symlinkUUIDProperty);
    when(symlink.getName()).thenReturn(nodeName + ".lnk");
    when(symlink.getPath()).thenReturn("/path/to/" + nodeName);
    Property createdDateProperty = mock(Property.class);
    when(createdDateProperty.getDate()).thenReturn(createdDate);
    when(symlink.getProperty(NodeTypeConstants.EXO_DATE_CREATED)).thenReturn(createdDateProperty);
    when(symlink.hasProperty(NodeTypeConstants.EXO_DATE_CREATED)).thenReturn(true);
    when(((NodeImpl)symlink).getIdentifier()).thenReturn(nodeName + "LinkIdentifier");
    return symlink;
  }

  @After
  public void tearDown() throws Exception {
    JCR_DOCUMENTS_UTIL.reset();
  }

  @Test
  public void countNodeAccessListTest() throws RepositoryException {
    ExtendedNode extendedNode = mock(ExtendedNode.class);
    org.exoplatform.services.security.Identity aclIdentity = mock(org.exoplatform.services.security.Identity.class);
    lenient().when(aclIdentity.getUserId()).thenReturn("john");
    AccessControlEntry accessControlEntry = new AccessControlEntry("*:/spaces/testspace", PermissionType.READ);
    AccessControlList acl1 = new AccessControlList("john", Arrays.asList(accessControlEntry));
    lenient().when(aclIdentity.isMemberOf(accessControlEntry.getMembershipEntry())).thenReturn(true);
    lenient().when(extendedNode.getACL()).thenReturn(acl1);
    //when
    Map<String, Boolean> accessList = jcrDocumentFileStorage.countNodeAccessList(extendedNode,aclIdentity);
    //then
    assertEquals(false, accessList.isEmpty());
    assertEquals(true, accessList.get("canAccess"));
    assertEquals(false, accessList.get("canEdit"));
    assertEquals(false, accessList.get("canDelete"));

    AccessControlEntry accessControlEntry1 = new AccessControlEntry("*:/spaces/testspace", PermissionType.SET_PROPERTY);
    AccessControlList acl2 = new AccessControlList("john", Arrays.asList(accessControlEntry,accessControlEntry1));
    lenient().when(aclIdentity.isMemberOf(accessControlEntry1.getMembershipEntry())).thenReturn(true);
    lenient().when(extendedNode.getACL()).thenReturn(acl2);

    //when
    Map<String, Boolean> accessList1 = jcrDocumentFileStorage.countNodeAccessList(extendedNode,aclIdentity);

    //then
    assertEquals(false, accessList1.isEmpty());
    assertEquals(true, accessList1.get("canAccess"));
    assertEquals(true, accessList1.get("canEdit"));
  }
  
  @Test
  public void createNewVersion() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    Node node = mock(Node.class);
    Node contentNode = mock(Node.class);
    Version version = mock(Version.class);
    when(session.getNodeByUUID("123")).thenReturn(node);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(true);
    when(node.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(contentNode);
    when(contentNode.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(true);
    when(node.isNodeType(NodeTypeConstants.EXO_MODIFY)).thenReturn(true);
    when(node.isCheckedOut()).thenReturn(false);
    when(node.checkin()).thenReturn(version);
    when(node.getSession()).thenReturn(session);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFileVersion(version, node, identityManager)).thenReturn(new FileVersion());
    jcrDocumentFileStorage.createNewVersion("123", "user", new ByteArrayInputStream("test".getBytes()));
    verify(node, times(1)).save();
    VERSION_HISTORY_UTILS.verify(() -> VersionHistoryUtils.createVersion(node), times(1));

  }

  @Test
  public void createNewVersionAddsVersionableMixinWhenMissing() throws Exception {
    // A file that isn't yet mix:versionable must be made versionable (and checked
    // out) before writing, otherwise the whole version write was silently skipped.
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    Node node = mock(Node.class);
    Node contentNode = mock(Node.class);
    when(session.getNodeByUUID("123")).thenReturn(node);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    // Not versionable initially, then versionable after the mixin is added.
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(false, true);
    when(node.canAddMixin(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(true);
    when(node.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(contentNode);
    when(contentNode.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(true);
    when(node.isCheckedOut()).thenReturn(true);
    when(node.getSession()).thenReturn(session);

    jcrDocumentFileStorage.createNewVersion("123", "user", new ByteArrayInputStream("test".getBytes()));

    verify(node, times(1)).addMixin(NodeTypeConstants.MIX_VERSIONABLE);
    VERSION_HISTORY_UTILS.verify(() -> VersionHistoryUtils.createVersion(node), times(1));
  }

  @Test
  public void moveDocument() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService,identity)).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);

    Node node = mock(Node.class);
    when(identity.getUserId()).thenReturn("user");
    Identity ownerIdentity = mock(Identity.class);
    when(identityManager.getIdentity(anyString())).thenReturn(ownerIdentity);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService,
                                                                       nodeHierarchyCreator,
                                                                       "user",
                                                                       ownerIdentity,
                                                                       sessionProvider))
                      .thenReturn(node);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "123")).thenReturn(node);
    when(node.canAddMixin(NodeTypeConstants.EXO_MODIFY)).thenReturn(true);
    when(node.getPath()).thenReturn("path");
    when(node.getName()).thenReturn("test.docx");
    NodeType nodeType = mock(NodeType.class);
    when(nodeType.getName()).thenReturn("nt:file");
    when(node.getPrimaryNodeType()).thenReturn(nodeType);
    when(session.itemExists(anyString())).thenReturn(false);
    when(node.getSession()).thenReturn(session);
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
    jcrDocumentFileStorage.moveDocument(1L, "123", "destPath", identity, null);
    verify(workspace, times(1)).move("path", "destPath/test.docx");
    verify(node, times(2)).save();

    clearInvocations(workspace);
    when(session.itemExists("destPath/test.docx")).thenReturn(true);
    Node destNode = mock(Node.class);
    when(session.getItem("destPath/test.docx")).thenReturn(destNode);
    when(destNode.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(true);
    assertThrows(ObjectAlreadyExistsException.class,
                 () -> jcrDocumentFileStorage.moveDocument(1L, "123", "destPath", identity, null));
    verify(workspace, times(0)).move(anyString(), anyString());

    when(session.itemExists("destPath/test.docx")).thenReturn(true);
    when(session.getItem("destPath/test(1).docx")).thenReturn(destNode);
    when(destNode.hasProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(true);
    Property jcrProperty = mock(Property.class);
    when(destNode.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(jcrProperty);
    Value jcrValue = mock(Value.class);
    when(jcrProperty.getValue()).thenReturn(jcrValue);
    when(jcrValue.getString()).thenReturn("test.docx");
    when(destNode.getSession()).thenReturn(session);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.increaseNameIndex(anyString(), anyInt())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNewIndexedName(anyString(), anyString())).thenCallRealMethod();
    jcrDocumentFileStorage.moveDocument(1L, "123", "destPath", identity, "keepBoth");
    verify(workspace, times(1)).move("path", "destPath/test(1).docx");
    verify(session, times(1)).save();

    Node srcNode = mock(Node.class);
    Node destContentNode = mock(Node.class);
    Node srcContentNode = mock(Node.class);
    when(session.getItem("destPath/test.docx")).thenReturn(destNode);
    when(session.getItem("path")).thenReturn(srcNode);
    when(destNode.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(true);
    when(destNode.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(destContentNode);
    when(srcNode.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(srcContentNode);
    when(srcContentNode.getProperty(NodeTypeConstants.JCR_DATA)).thenReturn(jcrProperty);
    when(jcrProperty.getValue()).thenReturn(jcrValue);
    when(jcrValue.getStream()).thenReturn(mock(InputStream.class));
    when(destNode.isNodeType(NodeTypeConstants.EXO_MODIFY)).thenReturn(true);
    when(destNode.isCheckedOut()).thenReturn(false);
    clearInvocations(session);
    jcrDocumentFileStorage.moveDocument(1L, "123", "destPath", identity, "createNewVersion");
    verify(destNode, times(1)).save();
    verify(srcNode, times(1)).remove();
    verify(destNode, times(1)).checkin();
    verify(destNode, times(2)).checkout();
    verify(session, times(1)).save();

    clearInvocations(workspace, session);
    jcrDocumentFileStorage.moveDocument(session,1L, "123", "destPath", identity, "keepBoth");
    verify(workspace, times(1)).move("path", "destPath/test(1).docx");
    verify(session, times(1)).save();
  }

  @Test
  public void moveDocuments() throws RepositoryException {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, identity))
                      .thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);

    AbstractNode abstractNode = mock(AbstractNode.class);
    List<AbstractNode> documents = List.of(abstractNode);
    Map<String, Object> params = new HashMap<>();
    params.put("destPath", "destPath");
    params.put("ownerId", 1L);
    ActionData actionData = new ActionData();
    actionData.setActionType(ActionType.MOVE.name());
    actionData.setActionId("1");
    actionData.setIdentity(identity);
    actionData.setNumberOfItems(1);

    jcrDocumentFileStorage.moveDocuments(1, 1L, documents, "destPath", identity, 1L);
    verify(bulkStorageActionService, times(1)).executeBulkAction(session,
                                                                 jcrDocumentFileStorage,
                                                                 null,
                                                                 listenerService,
                                                                 null,
                                                                 documents,
                                                                 actionData,
                                                                 null,
                                                                 params,
                                                                 1L);


    Mockito.doThrow(new RuntimeException())
           .when(bulkStorageActionService)
           .executeBulkAction(session,
                              jcrDocumentFileStorage,
                              null,
                              listenerService,
                              null,
                              documents,
                              actionData,
                              null,
                              params,
                              1L);
    assertThrows(Exception.class, () -> jcrDocumentFileStorage.moveDocuments(1, 1L, documents, "destPath", identity, 1L));
  }

  @Test
  public void importDocuments() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    Identity ownerIdentity = mock(Identity.class);
    UploadResource uploadResource = mock(UploadResource.class);
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, identity))
                      .thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);

    ActionData actionData = new ActionData();
    actionData.setActionType(ActionType.IMPORT_ZIP.name());
    actionData.setActionId("1");
    actionData.setIdentity(identity);
    actionData.setParentFolderName("documents");
    actionData.setTempFolderPath(System.getProperty("java.io.tmpdir") + File.separator + "temp_import1");
    when(JCRDocumentsUtil.getFolderLink(any(), any())).thenReturn(null);
    when(identityManager.getIdentity("1")).thenReturn(ownerIdentity);
    NodeImpl parentNodeImp = mock(NodeImpl.class);
    when(parentNodeImp.getName()).thenReturn("documents");
    when(parentNodeImp.getPath()).thenReturn("/documents/path");
    when(parentNodeImp.getNode(any())).thenReturn(parentNodeImp);
    when(uploadService.getUploadResource(any())).thenReturn(uploadResource);
    when(uploadResource.getEstimatedSize()).thenReturn(123456D);
    actionData.setSize(123456D);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService,
                                                                       nodeHierarchyCreator,
                                                                       null,
                                                                       ownerIdentity,
                                                                       sessionProvider))
                      .thenReturn(parentNodeImp);
    jcrDocumentFileStorage.importFiles("1", null, null, null, null, null, identity, "1", 1L);
    verify(bulkStorageActionService, times(1)).executeBulkAction(session,
                                                                 jcrDocumentFileStorage,
                                                                 null,
                                                                 listenerService,
                                                                 uploadService,
                                                                 null,
                                                                 actionData,
                                                                 parentNodeImp,
                                                                 null,
                                                                 1L);

  }

  @Test
  public void TestUpdatePermission() throws RepositoryException {
    org.exoplatform.services.security.Identity aclIdentity = mock(org.exoplatform.services.security.Identity.class);
    org.exoplatform.social.core.identity.model.Identity identity = mock(org.exoplatform.social.core.identity.model.Identity.class);
    org.exoplatform.social.core.identity.model.Identity identity1 = mock(org.exoplatform.social.core.identity.model.Identity.class);
    org.exoplatform.social.core.identity.model.Identity spaceIdentity = mock(org.exoplatform.social.core.identity.model.Identity.class);
    Space space = mock(Space.class);
    when(identity.getProviderId()).thenReturn("group");
    when(identity.getRemoteId()).thenReturn("/platform/users");
    when(identity1.getProviderId()).thenReturn("user");
    when(identity1.getRemoteId()).thenReturn("John");
    when(aclIdentity.getUserId()).thenReturn("user");
    when(spaceIdentity.getProviderId()).thenReturn("space");
    when(spaceIdentity.getRemoteId()).thenReturn("spaceTest");
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    lenient().when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, aclIdentity))
                      .thenReturn(sessionProvider);
    lenient().when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    ExtendedNode node = mock(ExtendedNode.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "123")).thenReturn(node);
    lenient().when(spaceService.getSpaceByPrettyName(spaceIdentity.getRemoteId())).thenReturn(space);
    lenient().when(spaceService.getSpaceByGroupId(anyString())).thenReturn(space);
    lenient().when(identityManager.getOrCreateUserIdentity(anyString())).thenReturn(identity1);
    lenient().when(identity1.getId()).thenReturn("1");
    lenient().when(space.getGroupId()).thenReturn("/spaces/spaceTest");
    lenient().when(space.getId()).thenReturn("2");
    lenient().when(node.canAddMixin(NodeTypeConstants.EXO_DATE_MODIFIED)).thenReturn(true);
    lenient().when(node.canAddMixin(NodeTypeConstants.EXO_LAST_MODIFIED_DATE)).thenReturn(true);
    lenient().when(node.canAddMixin(NodeTypeConstants.EXO_PRIVILEGEABLE)).thenReturn(true);
    lenient().when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    NodePermission nodePermission = new NodePermission();
    // permissionsList including group permission
    List<PermissionEntry> permissionsList = new ArrayList<>();
    permissionsList.add(new PermissionEntry(identity, "read", null));
    permissionsList.add(new PermissionEntry(identity1, "edit", null));
    // permissionsList including space manager and redactor permission
    permissionsList.add(new PermissionEntry(spaceIdentity, "edit", PermissionRole.MANAGERS_REDACTORS_PUBLISHERS.name()));
    nodePermission.setPermissions(permissionsList);
    when(node.getACL()).thenReturn(new AccessControlList("root", new ArrayList<>()));
    //When
    jcrDocumentFileStorage.updatePermissions("123",  nodePermission, aclIdentity);
    //Then
    verify(node).setPermissions(argThat((Map<String, String[]> map) -> map.containsKey("*:/platform/administrators") && Arrays.equals(map.get("*:/platform/administrators"),PermissionType.ALL)));
    verify(node).setPermissions(argThat((Map<String, String[]> map) -> map.containsKey("*:/platform/users") && Arrays.equals(map.get("*:/platform/users"),new String[]{"read"})));
    verify(node).setPermissions(argThat((Map<String, String[]> map) -> map.containsKey("John") && Arrays.equals(map.get("John"),PermissionType.ALL)));
    //
    verify(node).setPermissions(argThat((Map<String, String[]> map) -> map.containsKey("redactor:/spaces/spaceTest") && Arrays.equals(map.get("redactor:/spaces/spaceTest"),PermissionType.ALL)));
    verify(node).setPermissions(argThat((Map<String, String[]> map) -> map.containsKey("manager:/spaces/spaceTest") && Arrays.equals(map.get("redactor:/spaces/spaceTest"),PermissionType.ALL)));

    AccessControlList existingAccessList = new AccessControlList("root", new ArrayList<>());
    existingAccessList.addPermissions("removedUserCollaborator", new String[]{"read"});
    String spaceGroupId = "/spaces/removedSpaceCollaborator";
    existingAccessList.addPermissions("*:" + spaceGroupId, new String[]{"read"});
    when(node.getACL()).thenReturn(existingAccessList);
    when(spaceService.getSpaceByGroupId(spaceGroupId)).thenReturn(space);
    when(space.getPrettyName()).thenReturn("removedSpaceCollaborator");
    when(identityManager.getOrCreateSpaceIdentity(space.getPrettyName())).thenReturn(spaceIdentity);
    when(spaceIdentity.getId()).thenReturn("3");
    Identity userIdentity = mock(Identity.class);
    when(identityManager.getOrCreateUserIdentity("removedUserCollaborator")).thenReturn(userIdentity);
    when(userIdentity.getId()).thenReturn("4");
    //when
    jcrDocumentFileStorage.updatePermissions("123",  nodePermission, aclIdentity);
    //
    assertEquals(2, nodePermission.getToUnShare().size());
    assertTrue(nodePermission.getToUnShare().containsKey(3L));
    assertTrue(nodePermission.getToUnShare().containsKey(4L));
  }
  
  private Session getMockedSession(org.exoplatform.services.security.Identity identity) throws RepositoryException {
    SessionProvider systemSessionprovider = mock(SessionProvider.class);
    SESSION_PROVIDER.when(SessionProvider::createSystemProvider).thenReturn(systemSessionprovider);

    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    SessionProvider sessionProvider = mock(SessionProvider.class);

    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, identity))
                      .thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    when(systemSessionprovider.getSession("collaboration", manageableRepository)).thenReturn(session);
    return session;
  }

  @Test
  public void hasEditPermissions() throws RepositoryException {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identityRegistry.getIdentity("user")).thenReturn(identity);
    Session session = getMockedSession(identity);
    Node node = mock(Node.class);
    when(node.getPath()).thenReturn("path");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "123")).thenReturn(node);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.hasEditPermission(session, node)).thenReturn(true, false);
    assertTrue(jcrDocumentFileStorage.hasEditPermissions("123", identity));
    assertFalse(jcrDocumentFileStorage.hasEditPermissions("123", identity));
  }
  
  @Test
  public void getDocumentDownloadItem() throws RepositoryException {
    Session session = getMockedSession(null);
    NodeImpl node = mock(NodeImpl.class);
    Node contentNode = mock(Node.class);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(node.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(contentNode);
    when(contentNode.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(true);
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    Property dataProperty = mock(Property.class);
    Value dataValue = mock(Value.class);
    when(dataProperty.getValue()).thenReturn(dataValue);
    Property mimeTypeProperty = mock(Property.class);
    Value mimeTypeValue = mock(Value.class);
    when(dataProperty.getValue()).thenReturn(dataValue);
    when(dataValue.getStream()).thenReturn(inputStream);
    when(dataProperty.getStream()).thenReturn(inputStream);
    when(mimeTypeProperty.getValue()).thenReturn(mimeTypeValue);
    when(mimeTypeValue.getString()).thenReturn("application/pdf");
    when(contentNode.getProperty(NodeTypeConstants.JCR_DATA)).thenReturn(dataProperty);
    when(contentNode.getProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(mimeTypeProperty);
    when(node.getIdentifier()).thenReturn("123");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "123")).thenReturn(node);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toDownloadItem(node)).thenCallRealMethod();
    DownloadItem downloadItem = jcrDocumentFileStorage.getDocumentDownloadItem("123");
    assertNotNull(downloadItem);
  }

  @Test
  public void downloadFolder() throws RepositoryException {
    Session session = getMockedSession(null);
    Node node = mock(Node.class);
    Node parentNode = mock(Node.class);
    when(parentNode.getPath()).thenReturn("parentPath");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "123")).thenReturn(node);
    when(node.getParent()).thenReturn(parentNode);
    String zipPath = jcrDocumentFileStorage.downloadFolder("123");
    assertNotNull(zipPath);
  }
  @Test
  public void isDocumentSharedWithSamePermissionsTest() throws RepositoryException {
    ExtendedNode currentNode = mock(ExtendedNode.class);
    ExtendedNode linkNode = mock(ExtendedNode.class);
    Identity identity = mock(Identity.class);
    when(identity.getProviderId()).thenReturn("USER");
    AccessControlEntry accessControlEntry = new AccessControlEntry("john", "edit");
    AccessControlList acl = new AccessControlList("john", Arrays.asList(accessControlEntry));
    when(currentNode.getACL()).thenReturn(acl);
    when(linkNode.getACL()).thenReturn(acl);
    when(identity.getRemoteId()).thenReturn("john");
    //
    assertTrue(jcrDocumentFileStorage.isDocumentSharedWithSamePermissions(currentNode, linkNode, identity));

    when(identity.getProviderId()).thenReturn("space");
    when(identity.getRemoteId()).thenReturn("spaceTest");
    Space spaceTest = mock(Space.class);
    when(spaceService.getSpaceByPrettyName(identity.getRemoteId())).thenReturn(spaceTest);
    when(spaceTest.getGroupId()).thenReturn("/space/Test");
    AccessControlEntry accessControlEntry1 = new AccessControlEntry("*:/space/Test", "edit");
    AccessControlList acl1 = new AccessControlList("*:/space/Test", Arrays.asList(accessControlEntry1));
    when(currentNode.getACL()).thenReturn(acl1);
    when(linkNode.getACL()).thenReturn(acl1);
    //
    assertTrue(jcrDocumentFileStorage.isDocumentSharedWithSamePermissions(currentNode, linkNode, identity));

    AccessControlEntry accessControlEntry2 = new AccessControlEntry("*:/space/Test", "read");
    AccessControlList acl2 = new AccessControlList("*:/space/Test", Arrays.asList(accessControlEntry2));
    when(currentNode.getACL()).thenReturn(acl1);
    when(linkNode.getACL()).thenReturn(acl2);
    //
    assertFalse(jcrDocumentFileStorage.isDocumentSharedWithSamePermissions(currentNode, linkNode, identity));

  }

  @Test
  public void unShareDocumetTest() throws RepositoryException{
    Session systemSession = mock(Session.class);
    Identity identity = mock(Identity.class);
    Node rootNode = mock(Node.class);
    Node sharedNode = mock(Node.class);
    Node currentNode = Mockito.mock(ExtendedNode.class);
    ExtendedNode linkNode = mock(ExtendedNode.class);
    when(currentNode.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(false);
    when(linkNode.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(SessionProvider.createSystemProvider()).thenReturn(sessionProvider);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    RepositoryEntry repositoryEntry = mock(RepositoryEntry.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    when(manageableRepository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession(manageableRepository.getConfiguration().getDefaultWorkspaceName(),
            manageableRepository)).thenReturn(systemSession);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(systemSession, "1")).thenReturn(currentNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService, nodeHierarchyCreator,identity, systemSession)).thenReturn(rootNode);
    when(identity.getProviderId()).thenReturn("USER");
    when(rootNode.getNode("Documents")).thenReturn(rootNode);
    when(identityManager.getIdentity("1")).thenReturn(null);

    assertThrows(IllegalStateException.class, () -> jcrDocumentFileStorage.unShareDocument("1", 1L));

    when(identityManager.getIdentity("1")).thenReturn(identity);
    when(rootNode.hasNode("Shared")).thenReturn(false);

    verify(linkNode, times(0)).remove();
    verify(sessionProvider, times(1)).close();

    when(rootNode.hasNode("Shared")).thenReturn(true);
    when(rootNode.getNode("Shared")).thenReturn(sharedNode);
    when(sharedNode.hasNode(currentNode.getName())).thenReturn(true);
    when(sharedNode.getNode(currentNode.getName())).thenReturn(linkNode);

    jcrDocumentFileStorage.unShareDocument("1", 1L);

    verify(linkNode, times(1)).remove();
    verify(sessionProvider, atLeast(1)).close();
  }

  @Test
  public void getDocumentCategoryIdsReturnsEmptyListWhenIdentityNotFound() {
    org.exoplatform.services.security.Identity aclIdentity = mock(org.exoplatform.services.security.Identity.class);
    when(identityManager.getIdentity(99L)).thenReturn(null);

    assertEquals(Collections.emptyList(), jcrDocumentFileStorage.getDocumentCategoryIds(99L, aclIdentity));
  }

  @Test
  public void getDocumentCategoryIdsCollectsDistinctIdsFromSpaceScopedQuery() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = mock(org.exoplatform.services.security.Identity.class);
    Identity ownerIdentity = mock(Identity.class);
    when(identityManager.getIdentity(2L)).thenReturn(ownerIdentity);
    when(ownerIdentity.getRemoteId()).thenReturn("test_space");
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, aclIdentity))
                      .thenReturn(sessionProvider);
    Node identityRootNode = mock(Node.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService,
                                                                       nodeHierarchyCreator,
                                                                       "test_space",
                                                                       ownerIdentity,
                                                                       sessionProvider))
                      .thenReturn(identityRootNode);
    Session session = mock(Session.class);
    when(identityRootNode.getSession()).thenReturn(session);
    when(identityRootNode.getPath()).thenReturn("/Groups/spaces/test_space/Documents");
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
    QueryManager queryManager = mock(QueryManager.class);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    Query jcrQuery = mock(Query.class);
    when(queryManager.createQuery("SELECT exo:categoryIds FROM nt:base " +
        "WHERE 'mix:documentsCategory' IN jcr:mixinTypes " +
        "AND jcr:path LIKE '/Groups/spaces/test_space/Documents/%' " +
        "AND (jcr:primaryType = 'nt:file' OR jcr:primaryType = 'exo:symlink')",
                                  Query.SQL)).thenReturn(jcrQuery);
    QueryResult queryResult = mock(QueryResult.class);
    when(jcrQuery.execute()).thenReturn(queryResult);
    NodeIterator nodeIterator = mock(NodeIterator.class);
    when(queryResult.getNodes()).thenReturn(nodeIterator);
    when(nodeIterator.hasNext()).thenReturn(true, true, false);
    Node firstDocument = mock(Node.class);
    Node secondDocument = mock(Node.class);
    when(nodeIterator.nextNode()).thenReturn(firstDocument, secondDocument);
    when(firstDocument.hasProperty(NodeTypeConstants.DOCUMENT_CATEGORY_IDS)).thenReturn(true);
    when(secondDocument.hasProperty(NodeTypeConstants.DOCUMENT_CATEGORY_IDS)).thenReturn(true);
    Property firstProperty = mock(Property.class);
    Property secondProperty = mock(Property.class);
    when(firstDocument.getProperty(NodeTypeConstants.DOCUMENT_CATEGORY_IDS)).thenReturn(firstProperty);
    when(secondDocument.getProperty(NodeTypeConstants.DOCUMENT_CATEGORY_IDS)).thenReturn(secondProperty);
    Value[] firstValues = { categoryValue("3"), categoryValue("5"), categoryValue("not-a-number") };
    Value[] secondValues = { categoryValue("5") };
    when(firstProperty.getValues()).thenReturn(firstValues);
    when(secondProperty.getValues()).thenReturn(secondValues);

    List<Long> categoryIds = jcrDocumentFileStorage.getDocumentCategoryIds(2L, aclIdentity);

    assertEquals(2, categoryIds.size());
    assertTrue(categoryIds.containsAll(List.of(3L, 5L)));
    verify(sessionProvider, times(1)).close();
  }

  /**
   * The group id comes from the path of a node of a space's {@code Documents}
   * drive, the drive root included, and from nothing else: a personal drive,
   * another folder of the space, a name that only starts like the drive, a
   * same-name-sibling index, a nested space path and a null path give none.
   * Mutants: the {@code Documents} segment dropped from the pattern; the
   * pattern unanchored (a path that merely contains a drive).
   */
  @Test
  public void spaceGroupIdOfDrivePathAcceptsTheDriveOfASpaceOnly() {
    assertEquals("/spaces/team", JCRDocumentFileStorage.spaceGroupIdOfDrivePath("/Groups/spaces/team/Documents"));
    assertEquals("/spaces/team", JCRDocumentFileStorage.spaceGroupIdOfDrivePath("/Groups/spaces/team/Documents/a/b.txt"));
    assertNull(JCRDocumentFileStorage.spaceGroupIdOfDrivePath("/Groups/spaces/team"));
    assertNull(JCRDocumentFileStorage.spaceGroupIdOfDrivePath("/Groups/spaces/team/Pages/x"));
    assertNull(JCRDocumentFileStorage.spaceGroupIdOfDrivePath("/Groups/spaces/team/Documentsx"));
    assertNull(JCRDocumentFileStorage.spaceGroupIdOfDrivePath("/Groups/spaces/team/Documents[2]/a"));
    assertNull(JCRDocumentFileStorage.spaceGroupIdOfDrivePath("/Groups/spaces/team[2]/Documents/a"));
    assertNull(JCRDocumentFileStorage.spaceGroupIdOfDrivePath("/Groups/platform/administrators/Documents/a"));
    assertNull(JCRDocumentFileStorage.spaceGroupIdOfDrivePath("/Users/j___/jo___/john/Private/Documents/a"));
    assertNull(JCRDocumentFileStorage.spaceGroupIdOfDrivePath("/Trash/a/Groups/spaces/team/Documents/a"));
    assertNull(JCRDocumentFileStorage.spaceGroupIdOfDrivePath("/Groups/spaces//Documents/a"));
    assertNull(JCRDocumentFileStorage.spaceGroupIdOfDrivePath(null));
  }

  /**
   * A node of a space's drive tells its space, as the system. Mutant: the
   * path read from another node.
   */
  @Test
  public void documentSpaceGroupIdOfANodeOfASpaceDrive() throws Exception {
    Session session = systemSessionForSpaceGroupId();
    Node node = mock(Node.class);
    when(node.getPath()).thenReturn("/Groups/spaces/team/Documents/reports/q3.txt");
    JCR_DOCUMENTS_UTIL.when(() -> getNodeByIdentifier(session, "abc")).thenReturn(node);

    assertEquals("/spaces/team", jcrDocumentFileStorage.getDocumentSpaceGroupId("abc"));
  }

  /**
   * A shortcut or a share link inside a space's drive belongs to no space: it
   * stands for a node that may lie anywhere. Mutants: the {@code exo:symlink}
   * check removed; the {@code exo:taxonomyLink} check removed.
   */
  @Test
  public void documentSpaceGroupIdOfAShortcutIsNone() throws Exception {
    Session session = systemSessionForSpaceGroupId();
    Node shortcut = mock(Node.class);
    Node taxonomyLink = mock(Node.class);
    when(shortcut.getPath()).thenReturn("/Groups/spaces/team/Documents/Shared/q3.txt");
    when(shortcut.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true);
    when(taxonomyLink.getPath()).thenReturn("/Groups/spaces/team/Documents/links/q3.txt");
    when(taxonomyLink.isNodeType(NodeTypeConstants.TAXONOMY_LINK)).thenReturn(true);
    JCR_DOCUMENTS_UTIL.when(() -> getNodeByIdentifier(session, "shortcut")).thenReturn(shortcut);
    JCR_DOCUMENTS_UTIL.when(() -> getNodeByIdentifier(session, "taxonomy")).thenReturn(taxonomyLink);

    assertNull(jcrDocumentFileStorage.getDocumentSpaceGroupId("shortcut"));
    assertNull(jcrDocumentFileStorage.getDocumentSpaceGroupId("taxonomy"));
  }

  /**
   * A node of a personal drive, of the trash, outside any drive, a missing
   * node and a repository that fails give no space, and the session is closed
   * each time.
   */
  @Test
  public void documentSpaceGroupIdOfAnythingElseIsNone() throws Exception {
    Session session = systemSessionForSpaceGroupId();
    Node personal = mock(Node.class);
    Node trashed = mock(Node.class);
    Node failing = mock(Node.class);
    when(personal.getPath()).thenReturn("/Users/j___/jo___/john/Private/Documents/a");
    when(trashed.getPath()).thenReturn("/Trash/a");
    when(failing.getPath()).thenThrow(new RepositoryException("down"));
    JCR_DOCUMENTS_UTIL.when(() -> getNodeByIdentifier(session, "personal")).thenReturn(personal);
    JCR_DOCUMENTS_UTIL.when(() -> getNodeByIdentifier(session, "trashed")).thenReturn(trashed);
    JCR_DOCUMENTS_UTIL.when(() -> getNodeByIdentifier(session, "failing")).thenReturn(failing);
    JCR_DOCUMENTS_UTIL.when(() -> getNodeByIdentifier(session, "missing")).thenReturn(null);

    assertNull(jcrDocumentFileStorage.getDocumentSpaceGroupId("personal"));
    assertNull(jcrDocumentFileStorage.getDocumentSpaceGroupId("trashed"));
    assertNull(jcrDocumentFileStorage.getDocumentSpaceGroupId("failing"));
    assertNull(jcrDocumentFileStorage.getDocumentSpaceGroupId("missing"));
    verify(sessionProviderOfSpaceGroupId, times(4)).close();
  }

  private SessionProvider sessionProviderOfSpaceGroupId;

  /**
   * @return the system session the space group id is read with
   */
  private Session systemSessionForSpaceGroupId() throws Exception {
    Session session = mock(Session.class);
    sessionProviderOfSpaceGroupId = mock(SessionProvider.class);
    ManageableRepository repository = mock(ManageableRepository.class);
    when(SessionProvider.createSystemProvider()).thenReturn(sessionProviderOfSpaceGroupId);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(sessionProviderOfSpaceGroupId.getSession("collaboration", repository)).thenReturn(session);
    return session;
  }

  private Value categoryValue(String value) throws Exception {
    Value jcrValue = mock(Value.class);
    when(jcrValue.getString()).thenReturn(value);
    return jcrValue;
  }

  @Test
  public void getDocumentPathInDriveRefusesMissingOwnerDocumentOrDriveAndFollowsSpaceShortcuts() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("john");
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, aclIdentity))
                      .thenReturn(sessionProvider);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    Identity spaceIdentity = new Identity("space", "space_b");
    spaceIdentity.setId("2");

    assertThrows(ObjectNotFoundException.class, () -> jcrDocumentFileStorage.getDocumentPathInDrive("docA", 2L, aclIdentity));

    when(identityManager.getIdentity(2L)).thenReturn(spaceIdentity);
    assertThrows(ObjectNotFoundException.class, () -> jcrDocumentFileStorage.getDocumentPathInDrive("docA", 2L, aclIdentity));

    ExtendedNode docNode = mock(ExtendedNode.class);
    when(docNode.getPath()).thenReturn("/Groups/spaces/space_b/Documents/docA");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "docA")).thenReturn(docNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService,
                                                                      nodeHierarchyCreator,
                                                                      "john",
                                                                      spaceIdentity,
                                                                      sessionProvider))
                      .thenThrow(new AccessDeniedException());
    assertThrows(ObjectNotFoundException.class, () -> jcrDocumentFileStorage.getDocumentPathInDrive("docA", 2L, aclIdentity));

    Node driveRootNode = mock(Node.class);
    when(driveRootNode.getPath()).thenReturn("/Groups/spaces/space_b/Documents");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService,
                                                                      nodeHierarchyCreator,
                                                                      "john",
                                                                      spaceIdentity,
                                                                      sessionProvider))
                      .thenReturn(driveRootNode);
    assertEquals("/Groups/spaces/space_b/Documents/docA", jcrDocumentFileStorage.getDocumentPathInDrive("docA", 2L, aclIdentity));
    verify(sessionProvider, times(3)).close();

    // A space drive follows a shortcut to any ancestor, here the grand parent
    ExtendedNode folderA = mock(ExtendedNode.class);
    when(folderA.getIdentifier()).thenReturn("folderAId");
    when(folderA.getPath()).thenReturn("/Groups/spaces/space_a/Documents/folderA");
    when(folderA.getDepth()).thenReturn(5);
    ExtendedNode subFolder = mock(ExtendedNode.class);
    when(subFolder.getIdentifier()).thenReturn("subId");
    when(subFolder.getPath()).thenReturn("/Groups/spaces/space_a/Documents/folderA/sub");
    when(subFolder.getDepth()).thenReturn(6);
    when(subFolder.getParent()).thenReturn(folderA);
    ExtendedNode nestedDoc = mock(ExtendedNode.class);
    when(nestedDoc.getIdentifier()).thenReturn("nestedDocId");
    when(nestedDoc.getPath()).thenReturn("/Groups/spaces/space_a/Documents/folderA/sub/docA");
    when(nestedDoc.getDepth()).thenReturn(7);
    when(nestedDoc.getParent()).thenReturn(subFolder);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "nestedDocId")).thenReturn(nestedDoc);
    Node symlink = mock(Node.class);
    Property symlinkTarget = mock(Property.class);
    when(symlinkTarget.getString()).thenReturn("folderAId");
    when(symlink.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(symlinkTarget);
    when(symlink.getPath()).thenReturn("/Groups/spaces/space_b/Documents/folderA");
    NodeIterator symlinks = mock(NodeIterator.class);
    when(symlinks.hasNext()).thenReturn(true, false);
    when(symlinks.nextNode()).thenReturn(symlink);
    Workspace workspace = mock(Workspace.class);
    QueryManager queryManager = mock(QueryManager.class);
    Query query = mock(Query.class);
    QueryResult queryResult = mock(QueryResult.class);
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    when(queryManager.createQuery(anyString(), eq(Query.SQL))).thenReturn(query);
    when(query.execute()).thenReturn(queryResult);
    when(queryResult.getNodes()).thenReturn(symlinks);
    assertEquals("/Groups/spaces/space_b/Documents/folderA/sub/docA",
                 jcrDocumentFileStorage.getDocumentPathInDrive("nestedDocId", 2L, aclIdentity));

    // A user drive ignores the same grand parent shortcut: its folder URL would not open
    Identity userIdentity = new Identity(OrganizationIdentityProvider.NAME, "john");
    userIdentity.setId("3");
    when(identityManager.getIdentity(3L)).thenReturn(userIdentity);
    Node privateNode = mock(Node.class);
    when(privateNode.getPath()).thenReturn("/Users/j___/jo___/joh___/john/Private");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService,
                                                                      nodeHierarchyCreator,
                                                                      "john",
                                                                      userIdentity,
                                                                      sessionProvider))
                      .thenReturn(privateNode);
    when(symlinks.hasNext()).thenReturn(true, false);
    when(symlink.getPath()).thenReturn("/Users/j___/jo___/joh___/john/Private/folderA");
    assertNull(jcrDocumentFileStorage.getDocumentPathInDrive("nestedDocId", 3L, aclIdentity));
  }

  /**
   * A search sorted by a field reaches the index sorted by it, the latest
   * changes first for a descending last-change sort; a search with no sort
   * keeps the relevance order.
   * Mutants: the filter's sort ignored, its direction ignored, the relevance
   * order dropped when no sort is set.
   *
   * @throws Exception never
   */
  @Test
  public void searchSortsByTheFiltersFieldOrByRelevance() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("john");
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, aclIdentity)).thenReturn(sessionProvider);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getSortField(any(), anyBoolean())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getSortDirection(any())).thenCallRealMethod();
    ManageableRepository repository = mock(ManageableRepository.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration().getDefaultWorkspaceName()).thenReturn("collaboration");
    Session session = mock(Session.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    when(sessionProvider.getSession("collaboration", repository)).thenReturn(session);
    Node folder = mock(Node.class);
    when(folder.getPath()).thenReturn("/Groups/spaces/legal/Documents");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "folder")).thenReturn(folder);

    DocumentTimelineFilter latestFirst = new DocumentTimelineFilter();
    latestFirst.setParentFolderId("folder");
    latestFirst.setSortField(org.exoplatform.documents.constant.DocumentSortField.MODIFIED_DATE);
    latestFirst.setAscending(false);
    jcrDocumentFileStorage.search(latestFirst, aclIdentity, 0, 10);
    DocumentTimelineFilter oldestFirst = new DocumentTimelineFilter();
    oldestFirst.setParentFolderId("folder");
    oldestFirst.setSortField(org.exoplatform.documents.constant.DocumentSortField.MODIFIED_DATE);
    oldestFirst.setAscending(true);
    jcrDocumentFileStorage.search(oldestFirst, aclIdentity, 0, 10);
    DocumentTimelineFilter relevance = new DocumentTimelineFilter();
    relevance.setParentFolderId("folder");
    relevance.setQuery("contract");
    jcrDocumentFileStorage.search(relevance, aclIdentity, 0, 10);

    verify(documentSearchServiceConnector).search(eq(aclIdentity), eq("collaboration"), anyList(), eq(latestFirst), eq(0), eq(10),
                                                  eq("lastUpdatedDate"), eq("DESC"));
    verify(documentSearchServiceConnector).search(eq(aclIdentity), eq("collaboration"), anyList(), eq(oldestFirst), eq(0), eq(10),
                                                  eq("lastUpdatedDate"), eq("ASC"));
    verify(documentSearchServiceConnector).search(eq(aclIdentity), eq("collaboration"), anyList(), eq(relevance), eq(0), eq(10),
                                                  eq("_score"), eq("DESC"));
  }

  /**
   * A shortcut whose target the user's session can't read, or that is gone,
   * is answered as a missing document, as any id the user can't read is,
   * instead of failing on the missing target. {@code isFile} and
   * {@code isFolder} run their real code, so a target read without the null
   * check fails here as it does on the platform.
   */
  @Test
  public void getDocumentByIdAnswersAShortcutToAnUnreadableTargetAsMissing() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("john");
    when(identityRegistry.getIdentity("john")).thenReturn(aclIdentity);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, aclIdentity)).thenReturn(sessionProvider);
    ManageableRepository repository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    SessionImpl session = mock(SessionImpl.class);
    when(sessionProvider.getSession("collaboration", repository)).thenReturn(session);
    Node shortcut = createSymlinkMock("unreadableTargetId", "report");
    when(shortcut.getSession()).thenReturn(session);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "shortcutId")).thenReturn(shortcut);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "unreadableTargetId")).thenReturn(null);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isFile(any())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isFolder(any())).thenCallRealMethod();

    assertThrows(ItemNotFoundException.class, () -> jcrDocumentFileStorage.getDocumentById(session, "shortcutId", aclIdentity));
    assertThrows(ObjectNotFoundException.class, () -> jcrDocumentFileStorage.getDocumentById("shortcutId", "john"));
  }

  /**
   * A folder's children are paged over the listing, folders first, then files:
   * a shortcut to a file the folder query returns sorted among the folders is
   * listed with the files, and reading the listing page by page gives every
   * child once, in order. The queries serve their rows by the offset and the
   * limit they are given, as the repository does.
   */
  @Test
  public void getFolderChildNodesPagesOverTheListingWithAFileShortcutAmongTheFolders() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identity.getUserId()).thenReturn("user");
    SessionImpl session = mockFolderListingSession(identity);
    Node fileB = createFileMock("bTarget", Calendar.getInstance(), session);
    Node shortcutB = createSymlinkMock("bTargetFileIdentifier", "B");
    List<Node> folderRows = List.of(createFolderMock("A", Calendar.getInstance(), session),
                                    shortcutB,
                                    createFolderMock("C", Calendar.getInstance(), session),
                                    createFolderMock("D", Calendar.getInstance(), session),
                                    createFolderMock("E", Calendar.getInstance(), session),
                                    createFolderMock("F", Calendar.getInstance(), session));
    List<Node> fileRows = List.of(shortcutB, createFileMock("f1", Calendar.getInstance(), session));
    mockFolderListingQueries(session, folderRows, fileRows);
    assertNotNull(fileB);
    DocumentFolderFilter filter = new DocumentFolderFilter("12e2", "", 1L, "");
    filter.setSortField(DocumentSortField.NAME);
    filter.setAscending(true);

    List<String> listed = new ArrayList<>();
    for (int offset = 0; offset < 9; offset += 3) {
      jcrDocumentFileStorage.getFolderChildNodes(filter, identity, offset, 3).forEach(child -> listed.add(child.getName()));
    }

    assertEquals(List.of("A", "C", "D", "E", "F", "B.lnk", "f1"), listed);
    assertEquals(List.of("E", "F", "B.lnk"),
                 jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 3, 3).stream().map(AbstractNode::getName).toList());
  }

  /**
   * A page is filled up to its limit whenever the folder holds more children,
   * however many of the rows read first the listing leaves out (shortcuts to
   * files among the folders, a shortcut whose target is gone): the rows are
   * read again past the last one read. So a listing asked for one child more
   * than a bound gets it whenever the folder holds that many, and a shorter
   * listing means the folder holds no more.
   */
  @Test
  public void getFolderChildNodesFillsThePageWhenRowsAreLeftOut() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identity.getUserId()).thenReturn("user");
    SessionImpl session = mockFolderListingSession(identity);
    List<Node> folderRows = new ArrayList<>();
    createFileMock("target", Calendar.getInstance(), session);
    for (int i = 0; i < 4; i++) {
      folderRows.add(createSymlinkMock("targetFileIdentifier", "fileShortcut" + i));
    }
    folderRows.add(createSymlinkMock("goneIdentifier", "deadShortcut"));
    for (int i = 10; i < 32; i++) {
      folderRows.add(createFolderMock("folder" + i, Calendar.getInstance(), session));
    }
    mockFolderListingQueries(session, folderRows, List.of());
    DocumentFolderFilter filter = new DocumentFolderFilter("12e2", "", 1L, "");
    filter.setSortField(DocumentSortField.NAME);
    filter.setAscending(true);

    List<AbstractNode> page = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 21);
    assertEquals(21, page.size());
    assertEquals("folder10", page.get(0).getName());
    assertEquals("folder30", page.get(20).getName());

    assertEquals(22, jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 23).size());
    assertEquals(22, jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 0).size());
  }

  /**
   * A folder's children are listed in the order the query serves them, names
   * compared whatever their case by the query itself, and paged over that
   * order: reading the listing page by page gives the one-shot listing. The
   * rows come in the repository's case-insensitive order, which upper-cases
   * the names, so {@code _z} sorts after {@code B20}, where a lower-cased
   * comparison would put it first: a sort of the rows read, done again on
   * every longer read, would order a page otherwise than the one before it.
   * Mutants: the case-insensitive order not asked of the query, the rows read
   * sorted again.
   */
  @Test
  public void getFolderChildNodesPagesOverTheQuerysCaseInsensitiveOrder() throws Exception {
    org.exoplatform.services.security.Identity identity = mock(org.exoplatform.services.security.Identity.class);
    when(identity.getUserId()).thenReturn("user");
    SessionImpl session = mockFolderListingSession(identity);
    List<String> names = new ArrayList<>();
    names.add("a");
    for (int i = 1; i <= 20; i++) {
      names.add("B%02d".formatted(i));
    }
    names.add("_z");
    List<Node> folderRows = new ArrayList<>();
    for (String name : names) {
      folderRows.add(createFolderMock(name, Calendar.getInstance(), session));
    }
    List<QueryImpl> queries = mockFolderListingQueries(session, folderRows, List.of());
    DocumentFolderFilter filter = new DocumentFolderFilter("12e2", "", 1L, "");
    filter.setSortField(DocumentSortField.NAME);
    filter.setAscending(true);

    List<String> oneShot = jcrDocumentFileStorage.getFolderChildNodes(filter, identity, 0, 0)
                                                 .stream()
                                                 .map(AbstractNode::getName)
                                                 .toList();
    for (int limit : List.of(20, 5)) {
      List<String> paged = new ArrayList<>();
      for (int offset = 0; offset < names.size(); offset += limit) {
        jcrDocumentFileStorage.getFolderChildNodes(filter, identity, offset, limit).forEach(child -> paged.add(child.getName()));
      }
      assertEquals("pages of " + limit, oneShot, paged);
    }

    assertEquals(names, oneShot);
    assertFalse(queries.isEmpty());
    for (QueryImpl query : queries) {
      verify(query).setCaseInsensitiveOrder(true);
    }
  }

  /**
   * Opens the user's session on a parent folder whose children the listing
   * converts with the real conversion code.
   *
   * @param identity the user's identity
   * @return the user's session
   */
  private SessionImpl mockFolderListingSession(org.exoplatform.services.security.Identity identity) throws Exception {
    SessionProvider sessionProvider = mock(SessionProvider.class);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, identity)).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    SessionImpl session = mock(SessionImpl.class);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    Node parentNode = mock(Node.class);
    when(parentNode.getPath()).thenReturn("/documents/path");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "12e2")).thenReturn(parentNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toNodesInRowOrder(any(), any(), any(), any(), any(), anyBoolean()))
                      .thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toNodes(any(), any(), any(), any(), any(), anyBoolean(), any()))
                      .thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFolderNode(any(), any(), any(), any(), any())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getTitle(any())).thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.retrieveFileProperties(any(), any(), any(), any(), any()))
                      .thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFileNode(any(IdentityManager.class),
                                                              any(org.exoplatform.services.security.Identity.class),
                                                              any(Node.class),
                                                              anyString(),
                                                              any(SpaceService.class)))
                      .thenCallRealMethod();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFileNode(any(IdentityManager.class),
                                                              any(org.exoplatform.services.security.Identity.class),
                                                              any(Node.class),
                                                              any(FileNode.class),
                                                              any(SpaceService.class)))
                      .thenCallRealMethod();
    return session;
  }

  /**
   * Answers the folder query and the file query of a listing with the given
   * rows, served by the offset and the limit each query is given, no limit
   * serving every row past the offset.
   *
   * @param session the user's session
   * @param folderRows the rows of the folder query, in its order
   * @param fileRows the rows of the file query, in its order
   * @return the queries created, as they are created
   */
  private List<QueryImpl> mockFolderListingQueries(SessionImpl session, List<Node> folderRows, List<Node> fileRows) throws Exception {
    List<QueryImpl> queries = new ArrayList<>();
    WorkspaceImpl workspace = mock(WorkspaceImpl.class);
    QueryManager queryManager = mock(QueryManager.class);
    when(session.getWorkspace()).thenReturn(workspace);
    doReturn(queryManager).when(workspace).getQueryManager();
    when(queryManager.createQuery(anyString(), eq(Query.SQL))).thenAnswer(invocation -> {
      List<Node> rows = invocation.getArgument(0, String.class).contains("'nt:folder'") ? folderRows : fileRows;
      long[] offsetAndLimit = new long[2];
      QueryImpl query = mock(QueryImpl.class);
      queries.add(query);
      doAnswer(set -> offsetAndLimit[0] = set.getArgument(0, Long.class)).when(query).setOffset(anyLong());
      doAnswer(set -> offsetAndLimit[1] = set.getArgument(0, Long.class)).when(query).setLimit(anyLong());
      when(query.execute()).thenAnswer(execution -> {
        int from = (int) Math.min(offsetAndLimit[0], rows.size());
        int to = offsetAndLimit[1] <= 0 ? rows.size() : (int) Math.min(from + offsetAndLimit[1], rows.size());
        QueryResult result = mock(QueryResult.class);
        when(result.getNodes()).thenReturn(new org.exoplatform.services.jcr.impl.util.EntityCollection(new ArrayList<>(rows.subList(from,
                                                                                                                                          to))));
        return result;
      });
      return query;
    });
    return queries;
  }

  /** The user's session provider of the last upload folder mocked. */
  private SessionProvider                uploadSessionProvider;

  /**
   * Builds the handler's answer for a filed file: an XML document whose root
   * names the file's id, as {@code FileUploadHandler#getUploadedFile} writes
   * it.
   *
   * @param rootName the root element's name
   * @param uuid the id it carries, or null
   * @return the answer, with the status 200
   * @throws Exception never
   */
  private static javax.ws.rs.core.Response handlerAnswer(String rootName, String uuid) throws Exception {
    org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    org.w3c.dom.Element root = document.createElement(rootName);
    if (uuid != null) {
      root.setAttribute("UUID", uuid);
    }
    document.appendChild(root);
    javax.ws.rs.core.Response response = mock(javax.ws.rs.core.Response.class);
    when(response.getStatus()).thenReturn(200);
    when(response.getEntity()).thenReturn(new javax.xml.transform.dom.DOMSource(document));
    return response;
  }

  /**
   * Mocks the user's session on a folder "1" the user can add documents to,
   * and stores an upload "up-1"; the user's session provider is kept in
   * {@link #uploadSessionProvider}.
   *
   * @param aclIdentity the user
   * @param folderNode the folder
   * @return the user's session
   * @throws Exception never
   */
  private Session uploadFolder(org.exoplatform.services.security.Identity aclIdentity, ExtendedNode folderNode) throws Exception {
    SessionProvider sessionProvider = mock(SessionProvider.class);
    uploadSessionProvider = sessionProvider;
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, aclIdentity)).thenReturn(sessionProvider);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "1")).thenReturn(folderNode);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isFolder(folderNode)).thenReturn(true);
    AccessControlEntry editEntry = new AccessControlEntry(aclIdentity.getUserId(), PermissionType.ADD_NODE);
    when(folderNode.getACL()).thenReturn(new AccessControlList(aclIdentity.getUserId(), Arrays.asList(editEntry)));
    when(uploadService.getUploadResource("up-1")).thenReturn(new org.exoplatform.upload.UploadResource("up-1"));
    return session;
  }

  /**
   * An upload is filed by the handler the Documents app files its uploads
   * through, under the user's own session, with the app's source and the
   * user's id, and the file it names is returned; the session is closed. As
   * the app does, the filing asks the handler to post no stream activity, and
   * the thread's document context is left as it was found, absent or set.
   * Mutants: another workspace, folder, upload, name, source, user or action
   * passed, the file read from another id, the session left open, the skip
   * not set, or not restored.
   *
   * @throws Exception never
   */
  @Test
  public void saveUploadedDocumentFilesThroughTheUploadHandler() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("john");
    ExtendedNode folderNode = mock(ExtendedNode.class);
    Session session = uploadFolder(aclIdentity, folderNode);
    org.exoplatform.wcm.connector.FileUploadHandler handler = mock(org.exoplatform.wcm.connector.FileUploadHandler.class);
    JCRDocumentFileStorage storage = spy(jcrDocumentFileStorage);
    doReturn(handler).when(storage).getFileUploadHandler();
    doReturn(List.of()).when(storage).getUploadFileValidators();
    javax.ws.rs.core.Response answer = handlerAnswer("file", "file-1");
    List<Object> skipDuringFiling = new ArrayList<>();
    when(handler.saveAsNTFile("collaboration", folderNode, "up-1", "Report.pdf", "en", "newApp", null, "john", "keepBoth")).thenAnswer(invocation -> {
      skipDuringFiling.add(org.exoplatform.services.context.DocumentContext.getCurrent()
                                                                         .getAttributes()
                                                                         .get(org.exoplatform.services.context.DocumentContext.IS_SKIP_RAISE_ACT));
      return answer;
    });
    Node fileNode = mock(Node.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "file-1")).thenReturn(fileNode);
    FileNode expected = new FileNode();
    expected.setId("file-1");
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.toFileNode(identityManager, aclIdentity, fileNode, "", spaceService)).thenReturn(expected);

    java.util.Map<String, Object> documentAttributes = org.exoplatform.services.context.DocumentContext.getCurrent().getAttributes();
    documentAttributes.remove(org.exoplatform.services.context.DocumentContext.IS_SKIP_RAISE_ACT);

    FileNode result = storage.saveUploadedDocument("1", "up-1", "Report.pdf", null, aclIdentity);

    assertSame(expected, result);
    assertEquals(List.of(true), skipDuringFiling);
    assertFalse(documentAttributes.containsKey(org.exoplatform.services.context.DocumentContext.IS_SKIP_RAISE_ACT));

    documentAttributes.put(org.exoplatform.services.context.DocumentContext.IS_SKIP_RAISE_ACT, false);
    storage.saveUploadedDocument("1", "up-1", "Report.pdf", null, aclIdentity);
    assertEquals(List.of(true, true), skipDuringFiling);
    assertEquals(false, documentAttributes.remove(org.exoplatform.services.context.DocumentContext.IS_SKIP_RAISE_ACT));
    verify(handler, times(2)).saveAsNTFile("collaboration", folderNode, "up-1", "Report.pdf", "en", "newApp", null, "john", "keepBoth");
    verify(uploadSessionProvider, times(2)).close();
  }

  /**
   * An upload is refused before the handler runs when it isn't stored, its
   * folder isn't found, or the user can't add documents to the folder.
   * Mutants: any of the three checks dropped.
   *
   * @throws Exception never
   */
  @Test
  public void saveUploadedDocumentIsRefusedBeforeTheHandler() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("john");
    ExtendedNode folderNode = mock(ExtendedNode.class);
    Session session = uploadFolder(aclIdentity, folderNode);
    org.exoplatform.wcm.connector.FileUploadHandler handler = mock(org.exoplatform.wcm.connector.FileUploadHandler.class);
    JCRDocumentFileStorage storage = spy(jcrDocumentFileStorage);
    lenient().doReturn(handler).when(storage).getFileUploadHandler();
    lenient().doReturn(List.of()).when(storage).getUploadFileValidators();

    assertThrows(ObjectNotFoundException.class, () -> storage.saveUploadedDocument("1", "up-2", "a.pdf", null, aclIdentity));
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "9")).thenReturn(null);
    assertThrows(ObjectNotFoundException.class, () -> storage.saveUploadedDocument("9", "up-1", "a.pdf", null, aclIdentity));
    when(folderNode.getACL()).thenReturn(new AccessControlList("john",
                                                               Arrays.asList(new AccessControlEntry("john", PermissionType.READ))));
    assertThrows(IllegalAccessException.class, () -> storage.saveUploadedDocument("1", "up-1", "a.pdf", null, aclIdentity));
    verifyNoInteractions(handler);
  }

  /**
   * An upload's name and conflict action are checked before its file is sent
   * as when it is filed: a folder of that name refuses a new version, keeping
   * both opens no session, and a missing folder is not found.
   * Mutants: the check not run, a session opened for keep_both, the folder
   * unchecked, the session left open.
   *
   * @throws Exception never
   */
  @Test
  public void checkUploadConflictChecksAsTheFilingDoes() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("john");
    ExtendedNode folderNode = mock(ExtendedNode.class);
    Session session = uploadFolder(aclIdentity, folderNode);
    Node existing = mock(Node.class);
    when(folderNode.hasNode("report.pdf")).thenReturn(true);
    doReturn(existing).when(folderNode).getNode("report.pdf");

    jcrDocumentFileStorage.checkUploadConflict("1", "Report.pdf", org.exoplatform.documents.constant.UploadConflictAction.KEEP_BOTH, aclIdentity);
    verify(uploadSessionProvider, never()).getSession(anyString(), any());

    assertThrows(IllegalArgumentException.class,
                 () -> jcrDocumentFileStorage.checkUploadConflict("1",
                                                                  "Report.pdf",
                                                                  org.exoplatform.documents.constant.UploadConflictAction.NEW_VERSION,
                                                                  aclIdentity));
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "9")).thenReturn(null);
    assertThrows(ObjectNotFoundException.class,
                 () -> jcrDocumentFileStorage.checkUploadConflict("9",
                                                                  "Report.pdf",
                                                                  org.exoplatform.documents.constant.UploadConflictAction.NEW_VERSION,
                                                                  aclIdentity));
    verify(uploadSessionProvider, times(2)).close();
  }

  /**
   * The conflict action maps to the handler's, on the node name the handler
   * computes, lower-cased: a new version of a file that isn't there keeps
   * both, of a versioned file is passed on, and of a folder or of a file that
   * keeps no versions is refused, since the handler would overwrite it.
   * Mutants: the node name not lower-cased, the absence not checked, the
   * folder or the unversioned file not refused.
   *
   * @throws Exception never
   */
  @Test
  public void theConflictActionMapsToTheHandlersOnItsNodeName() throws Exception {
    Node folder = mock(Node.class);
    Node existing = mock(Node.class);
    org.exoplatform.documents.constant.UploadConflictAction newVersion =
                                                                       org.exoplatform.documents.constant.UploadConflictAction.NEW_VERSION;

    assertEquals("keepBoth", JCRDocumentFileStorage.toExistenceAction(folder, "Report.pdf", null));
    assertEquals("keepBoth",
                 JCRDocumentFileStorage.toExistenceAction(folder,
                                                          "Report.pdf",
                                                          org.exoplatform.documents.constant.UploadConflictAction.KEEP_BOTH));
    verifyNoInteractions(folder);
    when(folder.getNode(anyString())).thenThrow(new PathNotFoundException());
    assertEquals("keepBoth", JCRDocumentFileStorage.toExistenceAction(folder, "Report.pdf", newVersion));

    when(folder.hasNode("report.pdf")).thenReturn(true);
    doReturn(existing).when(folder).getNode("report.pdf");
    when(existing.isNodeType("nt:file")).thenReturn(true);
    assertThrows(IllegalArgumentException.class, () -> JCRDocumentFileStorage.toExistenceAction(folder, "Report.pdf", newVersion));
    when(existing.isNodeType("mix:versionable")).thenReturn(true);
    assertEquals("createVersion", JCRDocumentFileStorage.toExistenceAction(folder, "Report.pdf", newVersion));

    when(existing.isNodeType("nt:file")).thenReturn(false);
    assertThrows(IllegalArgumentException.class, () -> JCRDocumentFileStorage.toExistenceAction(folder, "Report.pdf", newVersion));
  }

  /**
   * The handler's answer gives the filed file's id; a message document is a
   * refusal, and a server error a failure whose message doesn't carry the
   * engine's text.
   * Mutants: the root element unchecked, the engine's text shown.
   *
   * @throws Exception never
   */
  @Test
  public void theHandlersAnswerGivesTheFiledFile() throws Exception {
    assertEquals("file-1", JCRDocumentFileStorage.getSavedFileId(handlerAnswer("file", "file-1"), "a.pdf"));
    assertThrows(IllegalAccessException.class,
                 () -> JCRDocumentFileStorage.getSavedFileId(handlerAnswer("Message", null), "a.pdf"));

    javax.ws.rs.core.Response failure = mock(javax.ws.rs.core.Response.class);
    when(failure.getStatus()).thenReturn(500);
    when(failure.getEntity()).thenReturn("javax.jcr.AccessDeniedException: /Users/j___/john/Private access denied for john");
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> JCRDocumentFileStorage.getSavedFileId(failure, "a.pdf"));
    assertFalse(e.getMessage().contains("john"));
    assertTrue(e.getMessage().contains("a.pdf"));
  }

  /**
   * An upload is filed into the folder a shortcut targets, as the Documents
   * app shows a shortcut, and refused before the handler when its id names a
   * file, whose parent the handler would otherwise file into.
   * Mutants: the shortcut not resolved, a file accepted as the folder.
   *
   * @throws Exception never
   */
  @Test
  public void anUploadIsFiledIntoAFolderOnly() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("john");
    ExtendedNode folderNode = mock(ExtendedNode.class);
    Session session = uploadFolder(aclIdentity, folderNode);
    org.exoplatform.wcm.connector.FileUploadHandler handler = mock(org.exoplatform.wcm.connector.FileUploadHandler.class);
    JCRDocumentFileStorage storage = spy(jcrDocumentFileStorage);
    doReturn(handler).when(storage).getFileUploadHandler();
    doReturn(List.of()).when(storage).getUploadFileValidators();
    javax.ws.rs.core.Response answer = handlerAnswer("file", "file-1");
    when(handler.saveAsNTFile("collaboration", folderNode, "up-1", "a.pdf", "en", "newApp", null, "john", "keepBoth")).thenReturn(answer);
    Node fileNode = mock(Node.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "file-1")).thenReturn(fileNode);

    Node shortcut = mock(Node.class);
    when(shortcut.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true);
    Property target = mock(Property.class);
    when(target.getString()).thenReturn("1");
    when(shortcut.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(target);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "shortcut")).thenReturn(shortcut);
    storage.saveUploadedDocument("shortcut", "up-1", "a.pdf", null, aclIdentity);
    verify(handler).saveAsNTFile("collaboration", folderNode, "up-1", "a.pdf", "en", "newApp", null, "john", "keepBoth");

    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isFolder(fileNode)).thenReturn(false);
    assertThrows(IllegalArgumentException.class, () -> storage.saveUploadedDocument("file-1", "up-1", "a.pdf", null, aclIdentity));
    assertThrows(IllegalArgumentException.class,
                 () -> storage.checkUploadConflict("file-1",
                                                   "a.pdf",
                                                   org.exoplatform.documents.constant.UploadConflictAction.NEW_VERSION,
                                                   aclIdentity));
    verify(handler, times(1)).saveAsNTFile(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  /**
   * An upload is run through the platform's upload validators before it is
   * filed, as a browser upload is: a validator that supports the file reads
   * its bytes from a stream of its own, and its refusal stops the filing with
   * its message; a validator that doesn't support the file is not run.
   * Mutants: the validators not run, an unsupporting validator run, the
   * refusal swallowed.
   *
   * @throws Exception never
   */
  @Test
  public void anUploadIsValidatedBeforeItIsFiled() throws Exception {
    org.exoplatform.services.security.Identity aclIdentity = new org.exoplatform.services.security.Identity("john");
    ExtendedNode folderNode = mock(ExtendedNode.class);
    uploadFolder(aclIdentity, folderNode);
    java.nio.file.Path stored = java.nio.file.Files.createTempFile("upload-", ".part");
    java.nio.file.Files.writeString(stored, "<svg onload='alert(1)'/>");
    org.exoplatform.upload.UploadResource resource = new org.exoplatform.upload.UploadResource("up-1");
    resource.setStoreLocation(stored.toString());
    resource.setMimeType("application/octet-stream");
    when(uploadService.getUploadResource("up-1")).thenReturn(resource);
    org.exoplatform.wcm.connector.FileUploadHandler handler = mock(org.exoplatform.wcm.connector.FileUploadHandler.class);
    org.exoplatform.upload.UploadFileValidator refusing = mock(org.exoplatform.upload.UploadFileValidator.class);
    org.exoplatform.upload.UploadFileValidator other = mock(org.exoplatform.upload.UploadFileValidator.class);
    when(refusing.supports("x.svg", "application/octet-stream")).thenReturn(true);
    List<String> read = new ArrayList<>();
    doAnswer(invocation -> {
      read.add(new String(((InputStream) invocation.getArgument(2)).readAllBytes()));
      throw new org.apache.commons.fileupload2.core.FileUploadException("Infected file detected. Uploading is forbidden");
    }).when(refusing).validate(eq("x.svg"), eq("application/octet-stream"), any(InputStream.class));
    JCRDocumentFileStorage storage = spy(jcrDocumentFileStorage);
    doReturn(handler).when(storage).getFileUploadHandler();
    doReturn(List.of(other, refusing)).when(storage).getUploadFileValidators();
    try {
      IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                      () -> storage.saveUploadedDocument("1", "up-1", "x.svg", null, aclIdentity));

      assertTrue(refusal.getMessage().contains("Infected file detected"));
      assertEquals(List.of("<svg onload='alert(1)'/>"), read);
      verify(other, never()).validate(any(), any(), any());
      verifyNoInteractions(handler);
    } finally {
      java.nio.file.Files.deleteIfExists(stored);
    }
  }

  /**
   * A file's content held in memory is checked by every validator that
   * supports it, each reading the whole content from its own stream, and a
   * refusal carries the validator's message.
   * Mutants: the content not validated, a validator handed a used stream,
   * the refusal swallowed.
   *
   * @throws Exception never
   */
  @Test
  public void aContentIsValidatedByEveryValidatorThatSupportsIt() throws Exception {
    org.exoplatform.upload.UploadFileValidator first = mock(org.exoplatform.upload.UploadFileValidator.class);
    org.exoplatform.upload.UploadFileValidator refusing = mock(org.exoplatform.upload.UploadFileValidator.class);
    when(first.supports("x.svg", "image/svg+xml")).thenReturn(true);
    when(refusing.supports("x.svg", "image/svg+xml")).thenReturn(true);
    List<String> read = new ArrayList<>();
    doAnswer(invocation -> read.add(new String(((InputStream) invocation.getArgument(2)).readAllBytes()))).when(first)
                                                                                                        .validate(any(), any(), any());
    doAnswer(invocation -> {
      read.add(new String(((InputStream) invocation.getArgument(2)).readAllBytes()));
      throw new org.apache.commons.fileupload2.core.FileUploadException("Infected file detected");
    }).when(refusing).validate(any(), any(), any());
    JCRDocumentFileStorage storage = spy(jcrDocumentFileStorage);
    doReturn(List.of(first, refusing)).when(storage).getUploadFileValidators();

    IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                    () -> storage.validateUploadedContent("x.svg", "image/svg+xml", "<svg/>".getBytes()));

    assertTrue(refusal.getMessage().contains("Infected file detected"));
    assertEquals(List.of("<svg/>", "<svg/>"), read);
  }

  /**
   * A file on disk is checked by every validator that supports it, under the
   * name and type given, each reading the whole file from its own stream
   * opened on the disk file, a validator that doesn't support it being
   * skipped; a refusal carries the validator's message, and a file that
   * can't be read is reported as not checked.
   * Mutants: the file not validated, a validator handed a used stream, the
   * name or type not passed on, an unsupporting validator called, the
   * refusal swallowed.
   *
   * @throws Exception never
   */
  @Test
  public void aFileOnDiskIsValidatedByEveryValidatorThatSupportsIt() throws Exception {
    org.exoplatform.upload.UploadFileValidator first = mock(org.exoplatform.upload.UploadFileValidator.class);
    org.exoplatform.upload.UploadFileValidator refusing = mock(org.exoplatform.upload.UploadFileValidator.class);
    org.exoplatform.upload.UploadFileValidator other = mock(org.exoplatform.upload.UploadFileValidator.class);
    when(first.supports("x.svg", "image/svg+xml")).thenReturn(true);
    when(refusing.supports("x.svg", "image/svg+xml")).thenReturn(true);
    List<String> read = new ArrayList<>();
    doAnswer(invocation -> read.add(new String(((InputStream) invocation.getArgument(2)).readAllBytes()))).when(first)
                                                                                                        .validate(eq("x.svg"),
                                                                                                                  eq("image/svg+xml"),
                                                                                                                  any());
    doAnswer(invocation -> {
      read.add(new String(((InputStream) invocation.getArgument(2)).readAllBytes()));
      throw new org.apache.commons.fileupload2.core.FileUploadException("Infected file detected");
    }).when(refusing).validate(eq("x.svg"), eq("image/svg+xml"), any());
    JCRDocumentFileStorage storage = spy(jcrDocumentFileStorage);
    doReturn(List.of(first, other, refusing)).when(storage).getUploadFileValidators();
    java.nio.file.Path file = java.nio.file.Files.createTempFile("upload", ".svg");
    try {
      java.nio.file.Files.writeString(file, "<svg/>");

      IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                      () -> storage.validateUploadedFile("x.svg", "image/svg+xml", file));

      assertTrue(refusal.getMessage().contains("Infected file detected"));
      assertEquals(List.of("<svg/>", "<svg/>"), read);
      verify(other, never()).validate(any(), any(), any());
    } finally {
      java.nio.file.Files.deleteIfExists(file);
    }
    assertThrows(IllegalStateException.class, () -> storage.validateUploadedFile("x.svg", "image/svg+xml", file));
  }

  @Test
  public void getTranscriptionStateReadsTheStoredStateWithTheViewersRights() throws Exception {
    org.exoplatform.services.security.Identity viewer = new org.exoplatform.services.security.Identity("john");
    Session session = mockViewerSession(viewer);
    Node node = mockMediaNode(session, "video/mp4");
    when(node.isNodeType(NodeTypeConstants.EXO_TRANSCRIPTION_STATE)).thenReturn(true);
    mockStringProperty(node, NodeTypeConstants.EXO_TRANSCRIPTION_STATUS, "PROCESSING");
    mockStringProperty(node, NodeTypeConstants.EXO_TRANSCRIPTION_PROGRESS, "3/9");
    mockStringProperty(node, NodeTypeConstants.EXO_TRANSCRIPTION_LANGUAGE, "en");
    Property duration = mock(Property.class);
    when(duration.getLong()).thenReturn(5400L);
    when(node.hasProperty(NodeTypeConstants.EXO_TRANSCRIPTION_DURATION)).thenReturn(true);
    when(node.getProperty(NodeTypeConstants.EXO_TRANSCRIPTION_DURATION)).thenReturn(duration);
    Calendar updated = Calendar.getInstance();
    Property updatedProperty = mock(Property.class);
    when(updatedProperty.getDate()).thenReturn(updated);
    when(node.hasProperty(NodeTypeConstants.EXO_TRANSCRIPTION_UPDATED)).thenReturn(true);
    when(node.getProperty(NodeTypeConstants.EXO_TRANSCRIPTION_UPDATED)).thenReturn(updatedProperty);

    TranscriptionState state = jcrDocumentFileStorage.getTranscriptionState("1", viewer);

    assertEquals(TranscriptionStatus.PROCESSING, state.status());
    assertEquals("3/9", state.progress());
    assertEquals("en", state.language());
    assertEquals(Long.valueOf(5400L), state.durationSeconds());
    assertEquals(Long.valueOf(updated.getTimeInMillis()), state.updated());
    assertNull(state.error());
    assertFalse(state.hasSegments());
    assertTrue(state.media());
  }

  @Test
  public void getTranscriptionStateReadsAnOldTranscriptAsDoneAndAnEmptyPlaceholderAsNeverTranscribed() throws Exception {
    org.exoplatform.services.security.Identity viewer = new org.exoplatform.services.security.Identity("john");
    Session session = mockViewerSession(viewer);
    Node node = mockMediaNode(session, "audio/ogg");
    when(node.isNodeType(NodeTypeConstants.EXO_TRANSCRIPTION)).thenReturn(true);
    when(node.hasProperty(NodeTypeConstants.EXO_TRANSCRIPTION)).thenReturn(true);
    Property transcript = mock(Property.class);
    when(node.getProperty(NodeTypeConstants.EXO_TRANSCRIPTION)).thenReturn(transcript);

    when(transcript.getLength()).thenReturn(42L);
    assertEquals(TranscriptionStatus.DONE, jcrDocumentFileStorage.getTranscriptionState("1", viewer).status());

    when(transcript.getLength()).thenReturn(0L);
    TranscriptionState placeholder = jcrDocumentFileStorage.getTranscriptionState("1", viewer);
    assertNull("Expected the empty placeholder of the former listener to read as never transcribed", placeholder.status());
    assertTrue(placeholder.media());
  }

  @Test
  public void getTranscriptionStateTellsAFileThatIsNoMedia() throws Exception {
    org.exoplatform.services.security.Identity viewer = new org.exoplatform.services.security.Identity("john");
    Session session = mockViewerSession(viewer);
    Node node = mockMediaNode(session, "application/pdf");

    assertFalse(jcrDocumentFileStorage.getTranscriptionState("1", viewer).media());
    when(node.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(false);
    assertFalse(jcrDocumentFileStorage.getTranscriptionState("1", viewer).media());
  }

  @Test
  public void getTranscriptionStateOfAFileTheViewerCannotSeeIsNotFound() throws Exception {
    org.exoplatform.services.security.Identity viewer = new org.exoplatform.services.security.Identity("john");
    Session session = mockViewerSession(viewer);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "1")).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> jcrDocumentFileStorage.getTranscriptionState("1", viewer));
  }

  @Test
  public void updateTranscriptionStateWritesEveryFieldAsTheSystem() throws Exception {
    Session session = mockSystemSession();
    Node node = mockMediaNode(session, "video/mp4");
    when(node.isNodeType(NodeTypeConstants.EXO_TRANSCRIPTION_STATE)).thenReturn(false);
    Property duration = mock(Property.class);
    when(node.hasProperty(NodeTypeConstants.EXO_TRANSCRIPTION_DURATION)).thenReturn(true);
    when(node.getProperty(NodeTypeConstants.EXO_TRANSCRIPTION_DURATION)).thenReturn(duration);

    jcrDocumentFileStorage.updateTranscriptionState("1",
                                                    TranscriptionState.builder()
                                                                      .status(TranscriptionStatus.FAILED)
                                                                      .error("provider.timeout")
                                                                      .build());

    verify(node).addMixin(NodeTypeConstants.EXO_TRANSCRIPTION_STATE);
    verify(node).setProperty(NodeTypeConstants.EXO_TRANSCRIPTION_STATUS, "FAILED");
    verify(node).setProperty(NodeTypeConstants.EXO_TRANSCRIPTION_ERROR, "provider.timeout");
    verify(node).setProperty(NodeTypeConstants.EXO_TRANSCRIPTION_PROGRESS, (String) null);
    verify(node).setProperty(NodeTypeConstants.EXO_TRANSCRIPTION_LANGUAGE, (String) null);
    verify(duration).remove();
    verify(node).setProperty(eq(NodeTypeConstants.EXO_TRANSCRIPTION_UPDATED), any(Calendar.class));
    verify(session).save();
  }

  @Test
  public void markTranscriptionQueuedWritesOnlyAFileWithNoStateYet() throws Exception {
    Session session = mockSystemSession();
    Node node = mockMediaNode(session, "video/mp4");

    when(node.isNodeType(NodeTypeConstants.EXO_TRANSCRIPTION_STATE)).thenReturn(true);
    assertFalse(jcrDocumentFileStorage.markTranscriptionQueued("1"));
    verify(node, never()).setProperty(eq(NodeTypeConstants.EXO_TRANSCRIPTION_STATUS), anyString());
    verify(session, never()).save();

    when(node.isNodeType(NodeTypeConstants.EXO_TRANSCRIPTION_STATE)).thenReturn(false);
    assertTrue(jcrDocumentFileStorage.markTranscriptionQueued("1"));
    verify(node).addMixin(NodeTypeConstants.EXO_TRANSCRIPTION_STATE);
    verify(node).setProperty(NodeTypeConstants.EXO_TRANSCRIPTION_STATUS, "QUEUED");
    verify(session).save();
  }

  @Test
  public void markTranscriptionQueuedOfAFileNotSavedYetIsNotFound() throws Exception {
    Session session = mockSystemSession();
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "1")).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> jcrDocumentFileStorage.markTranscriptionQueued("1"));
  }

  @Test
  public void completeTranscriptionWritesTheTextTheSegmentsAndTheStateInOneSave() throws Exception {
    Session session = mockSystemSession();
    Node node = mockMediaNode(session, "video/mp4");

    jcrDocumentFileStorage.completeTranscription("1",
                                                 "bonjour à tous",
                                                 "[{\"start\":0.0,\"end\":2.0,\"text\":\"bonjour\"}]",
                                                 TranscriptionState.builder()
                                                                   .status(TranscriptionStatus.DONE)
                                                                   .language("fr")
                                                                   .durationSeconds(5400L)
                                                                   .build());

    verify(node).addMixin(NodeTypeConstants.EXO_TRANSCRIPTION);
    verify(node).addMixin(NodeTypeConstants.EXO_TRANSCRIPTION_STATE);
    ArgumentCaptor<InputStream> text = ArgumentCaptor.forClass(InputStream.class);
    verify(node).setProperty(eq(NodeTypeConstants.EXO_TRANSCRIPTION), text.capture());
    assertEquals("bonjour à tous", new String(text.getValue().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    verify(node).setProperty(eq(NodeTypeConstants.EXO_TRANSCRIPTION_SEGMENTS), any(InputStream.class));
    verify(node).setProperty(NodeTypeConstants.EXO_TRANSCRIPTION_STATUS, "DONE");
    verify(node).setProperty(NodeTypeConstants.EXO_TRANSCRIPTION_LANGUAGE, "fr");
    verify(node).setProperty(NodeTypeConstants.EXO_TRANSCRIPTION_DURATION, 5400L);
    verify(session, times(1)).save();
  }

  @Test
  public void completeTranscriptionWithoutSegmentsRemovesThePreviousOnes() throws Exception {
    Session session = mockSystemSession();
    Node node = mockMediaNode(session, "video/mp4");
    Property segments = mock(Property.class);
    when(node.hasProperty(NodeTypeConstants.EXO_TRANSCRIPTION_SEGMENTS)).thenReturn(true);
    when(node.getProperty(NodeTypeConstants.EXO_TRANSCRIPTION_SEGMENTS)).thenReturn(segments);

    jcrDocumentFileStorage.completeTranscription("1",
                                                 "hello",
                                                 null,
                                                 TranscriptionState.builder().status(TranscriptionStatus.DONE).build());

    verify(segments).remove();
    verify(node, never()).setProperty(eq(NodeTypeConstants.EXO_TRANSCRIPTION_SEGMENTS), any(InputStream.class));
  }

  private Session mockViewerSession(org.exoplatform.services.security.Identity viewer) throws Exception {
    SessionProvider sessionProvider = mock(SessionProvider.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, viewer)).thenReturn(sessionProvider);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    return session;
  }

  private Session mockSystemSession() throws Exception {
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(SessionProvider.createSystemProvider()).thenReturn(sessionProvider);
    ManageableRepository manageableRepository = mock(ManageableRepository.class);
    when(repositoryService.getCurrentRepository()).thenReturn(manageableRepository);
    Session session = mock(Session.class);
    when(sessionProvider.getSession("collaboration", manageableRepository)).thenReturn(session);
    return session;
  }

  private Node mockMediaNode(Session session, String mimeType) throws Exception {
    Node node = mock(Node.class);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "1")).thenReturn(node);
    when(node.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    Node content = mock(Node.class);
    when(node.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(content);
    when(content.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(true);
    Property mimeTypeProperty = mock(Property.class);
    when(mimeTypeProperty.getString()).thenReturn(mimeType);
    when(content.getProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(mimeTypeProperty);
    return node;
  }

  private void mockStringProperty(Node node, String name, String value) throws Exception {
    Property property = mock(Property.class);
    when(property.getString()).thenReturn(value);
    when(node.hasProperty(name)).thenReturn(true);
    when(node.getProperty(name)).thenReturn(property);
  }

}
