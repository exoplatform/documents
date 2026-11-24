/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.storage.jcr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.FileVersion;
import org.exoplatform.documents.storage.jcr.bulkactions.BulkStorageActionService;
import org.exoplatform.documents.storage.jcr.search.DocumentSearchServiceConnector;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.upload.UploadService;

/**
 * The repository sessions a document operation opens are the ones its own
 * session provider holds: each operation that builds a provider closes it,
 * on the success path and on the failure path.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class SessionProviderCloseTest {

  private static final String                            USER = "user";

  @Mock
  private SpaceService                                   spaceService;

  @Mock
  private RepositoryService                              repositoryService;

  @Mock
  private IdentityManager                                identityManager;

  @Mock
  private NodeHierarchyCreator                           nodeHierarchyCreator;

  @Mock
  private ListenerService                                listenerService;

  @Mock
  private UploadService                                  uploadService;

  @Mock
  private IdentityRegistry                               identityRegistry;

  @Mock
  private ActivityManager                                activityManager;

  @Mock
  private BulkStorageActionService                       bulkStorageActionService;

  @Mock
  private org.exoplatform.services.cms.documents.DocumentService documentService;

  @Mock
  private DocumentSearchServiceConnector documentSearchServiceConnector;

  @Mock
  private ManageableRepository                           repository;

  @Mock
  private RepositoryEntry                                repositoryEntry;

  @Mock
  private SessionProvider                                sessionProvider;

  @Mock
  private Session                                        session;

  private org.exoplatform.services.security.Identity     aclIdentity;

  private MockedStatic<JCRDocumentsUtil>                 jcrDocumentsUtil;

  private MockedStatic<SessionProvider>                  sessionProviderStatic;

  private JCRDocumentFileStorage                         storage;

  /** Set once the provider is closed: its sessions are logged out and read nothing. */
  private final AtomicBoolean                            providerClosed = new AtomicBoolean();

  @Before
  public void setUp() throws Exception {
    jcrDocumentsUtil = mockStatic(JCRDocumentsUtil.class);
    sessionProviderStatic = mockStatic(SessionProvider.class);
    aclIdentity = mock(org.exoplatform.services.security.Identity.class);
    when(aclIdentity.getUserId()).thenReturn(USER);
    when(identityRegistry.getIdentity(USER)).thenReturn(aclIdentity);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    jcrDocumentsUtil.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, aclIdentity))
                    .thenReturn(sessionProvider);
    sessionProviderStatic.when(SessionProvider::createSystemProvider).thenReturn(sessionProvider);
    when(sessionProvider.getSession("collaboration", repository)).thenReturn(session);
    doAnswer(invocation -> {
      providerClosed.set(true);
      return null;
    }).when(sessionProvider).close();
    storage = new JCRDocumentFileStorage(nodeHierarchyCreator,
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

  @After
  public void tearDown() {
    jcrDocumentsUtil.close();
    sessionProviderStatic.close();
  }

  @Test
  public void getRootFolderOwnerIdClosesTheSystemProvider() throws Exception {
    org.exoplatform.social.core.identity.model.Identity owner = mock(org.exoplatform.social.core.identity.model.Identity.class);
    when(owner.getIdentityId()).thenReturn(42L);
    Node node = mock(Node.class);
    when(node.getPath()).thenReturn("/Users/u");
    jcrDocumentsUtil.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "doc")).thenReturn(node);
    jcrDocumentsUtil.when(() -> JCRDocumentsUtil.getOwnerIdentityFromNodePath("/Users/u", identityManager, spaceService))
                    .thenReturn(owner);

    assertEquals(42L, storage.getRootFolderOwnerId("doc"));

    verify(sessionProvider).close();
  }

  @Test
  public void getRootFolderOwnerIdClosesTheSystemProviderWhenTheNodeIsMissing() {
    assertEquals(0L, storage.getRootFolderOwnerId("missing"));

    verify(sessionProvider).close();
  }

  @Test
  public void getFileVersionsClosesTheUserProvider() throws Exception {
    Node node = mock(Node.class);
    VersionHistory history = mock(VersionHistory.class);
    Version root = mock(Version.class);
    VersionIterator versions = mock(VersionIterator.class);
    when(session.getNodeByUUID("file")).thenAnswer(invocation -> {
      assertFalse("The session is read after its provider closed", providerClosed.get());
      return node;
    });
    when(node.getVersionHistory()).thenAnswer(invocation -> {
      assertFalse("The node is read after its provider closed", providerClosed.get());
      return history;
    });
    when(history.getRootVersion()).thenReturn(root);
    when(history.getAllVersions()).thenReturn(versions);
    when(versions.hasNext()).thenReturn(false);

    assertTrue(storage.getFileVersions("file", USER).isEmpty());

    verify(sessionProvider).close();
  }

  @Test
  public void getFileVersionsClosesTheUserProviderOnFailure() throws Exception {
    when(session.getNodeByUUID("file")).thenThrow(new RepositoryException("down"));

    assertThrows(IllegalStateException.class, () -> storage.getFileVersions("file", USER));

    verify(sessionProvider).close();
  }

  @Test
  public void getDocumentByIdClosesTheUserProvider() throws Exception {
    AbstractNode expected = mock(AbstractNode.class);
    JCRDocumentFileStorage spied = spy(storage);
    doReturn(expected).when(spied).getDocumentById(eq(session), eq("doc"), eq(aclIdentity));

    assertEquals(expected, spied.getDocumentById("doc", USER));

    verify(sessionProvider).close();
  }

  @Test
  public void getDocumentByIdClosesTheUserProviderWhenTheDocumentIsMissing() throws Exception {
    JCRDocumentFileStorage spied = spy(storage);
    doReturn(null).when(spied).getDocumentById(eq(session), eq("doc"), eq(aclIdentity));

    assertThrows(org.exoplatform.commons.exception.ObjectNotFoundException.class, () -> spied.getDocumentById("doc", USER));

    verify(sessionProvider).close();
  }

  @Test
  public void updateVersionSummaryClosesTheUserProvider() throws Exception {
    Node node = mock(Node.class);
    Version version = mock(Version.class);
    Node frozen = mock(Node.class);
    VersionHistory history = mock(VersionHistory.class);
    when(session.getNodeByUUID("file")).thenReturn(node);
    when(session.getNodeByUUID("version")).thenReturn(version);
    when(version.getNode(NodeTypeConstants.JCR_FROZEN_NODE)).thenReturn(frozen);
    when(version.getName()).thenReturn("1");
    when(node.getVersionHistory()).thenReturn(history);
    when(history.getVersionLabels(version)).thenReturn(new String[0]);

    FileVersion result = storage.updateVersionSummary("file", "version", "summary", USER);

    assertEquals("version", result.getId());
    assertEquals("summary", result.getSummary());
    verify(session).save();
    verify(sessionProvider).close();
  }

  @Test
  public void restoreVersionClosesTheUserProvider() throws Exception {
    Version version = mock(Version.class);
    when(session.getNodeByUUID("version")).thenReturn(version);
    when(version.getNode(NodeTypeConstants.JCR_FROZEN_NODE)).thenReturn(null);

    assertTrue(storage.restoreVersion("version", USER) != null);

    verify(session).save();
    verify(sessionProvider).close();
  }

  @Test
  public void createNewVersionClosesTheUserProvider() throws Exception {
    Node node = mock(Node.class);
    when(session.getNodeByUUID("file")).thenReturn(node);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(false);

    assertTrue(storage.createNewVersion("file", USER, new java.io.ByteArrayInputStream(new byte[0])) != null);

    verify(sessionProvider).close();
  }

  @Test
  public void createNewVersionClosesTheUserProviderOnFailure() throws Exception {
    when(session.getNodeByUUID("file")).thenThrow(new RepositoryException("down"));

    assertThrows(IllegalStateException.class,
                 () -> storage.createNewVersion("file", USER, new java.io.ByteArrayInputStream(new byte[0])));

    verify(sessionProvider).close();
  }

  @Test
  public void hasEditPermissionsClosesTheUserProvider() throws Exception {
    Node node = mock(Node.class);
    jcrDocumentsUtil.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "doc")).thenAnswer(invocation -> {
      assertFalse("The session is read after its provider closed", providerClosed.get());
      return node;
    });
    jcrDocumentsUtil.when(() -> JCRDocumentsUtil.hasEditPermission(session, node)).thenAnswer(invocation -> {
      assertFalse("The permission is read after its provider closed", providerClosed.get());
      return true;
    });

    assertTrue(storage.hasEditPermissions("doc", aclIdentity));

    verify(sessionProvider).close();
  }

  @Test
  public void hasEditPermissionsClosesTheUserProviderOnFailure() throws Exception {
    jcrDocumentsUtil.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "doc"))
                    .thenThrow(new IllegalStateException("down"));

    assertFalse(storage.hasEditPermissions("doc", aclIdentity));

    verify(sessionProvider).close();
  }

  @Test
  public void moveDocumentClosesTheUserProviderOfTheDriveRoot() throws Exception {
    org.exoplatform.social.core.identity.model.Identity owner = mock(org.exoplatform.social.core.identity.model.Identity.class);
    when(identityManager.getIdentity("7")).thenReturn(owner);
    jcrDocumentsUtil.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService,
                                                                     nodeHierarchyCreator,
                                                                     USER,
                                                                     owner,
                                                                     sessionProvider))
                    .thenReturn(null);

    storage.moveDocument(session, 7L, "", "dest", aclIdentity, null);

    verify(sessionProvider).close();
  }

  @Test
  public void moveDocumentClosesTheUserProviderOnFailure() throws Exception {
    org.exoplatform.social.core.identity.model.Identity owner = mock(org.exoplatform.social.core.identity.model.Identity.class);
    when(identityManager.getIdentity("7")).thenReturn(owner);
    Node node = mock(Node.class);
    when(node.canAddMixin(NodeTypeConstants.EXO_MODIFY)).thenThrow(new RepositoryException("down"));
    jcrDocumentsUtil.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService,
                                                                     nodeHierarchyCreator,
                                                                     USER,
                                                                     owner,
                                                                     sessionProvider))
                    .thenReturn(node);

    assertThrows(RepositoryException.class, () -> storage.moveDocument(session, 7L, "", "dest", aclIdentity, null));

    verify(sessionProvider).close();
  }

  @Test
  public void shareDocumentWithASpaceClosesBothProviders() throws Exception {
    SessionProvider userProvider = mock(SessionProvider.class);
    Session userSession = mock(Session.class);
    jcrDocumentsUtil.when(() -> JCRDocumentsUtil.getUserSessionProvider(repositoryService, aclIdentity))
                    .thenReturn(userProvider);
    when(userProvider.getSession("collaboration", repository)).thenReturn(userSession);
    org.exoplatform.social.core.identity.model.Identity dest = mock(org.exoplatform.social.core.identity.model.Identity.class);
    when(identityManager.getIdentity("5")).thenReturn(dest);
    when(dest.getProviderId()).thenReturn("space");
    when(dest.isSpace()).thenReturn(true);
    Node current = mock(Node.class);
    org.exoplatform.services.jcr.core.ExtendedNode shared = mock(org.exoplatform.services.jcr.core.ExtendedNode.class);
    Node root = mock(Node.class);
    when(root.hasNode("Shared")).thenReturn(true);
    when(root.getNode("Shared")).thenReturn(shared);
    when(shared.getIdentifier()).thenReturn("sharedId");
    jcrDocumentsUtil.when(() -> JCRDocumentsUtil.getNodeByIdentifier(session, "doc")).thenReturn(current);
    jcrDocumentsUtil.when(() -> JCRDocumentsUtil.getIdentityRootNode(spaceService, nodeHierarchyCreator, dest, session))
                    .thenReturn(root);
    // the shared folder is not visible to the sharing user: the share fails after both providers are open
    jcrDocumentsUtil.when(() -> JCRDocumentsUtil.getNodeByIdentifier(userSession, "sharedId")).thenReturn(null);

    assertThrows(IllegalStateException.class, () -> storage.shareDocument("doc", 5L, aclIdentity, false));

    verify(userProvider).close();
    verify(sessionProvider).close();
  }
}
