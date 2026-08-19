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
package org.exoplatform.document.cleanup.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupExemptionResult;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupPurgeResult;
import org.exoplatform.document.cleanup.model.CleanupRevalidation;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Pins the path-based resume semantics of the scan walk: nodes whose path is
 * not strictly greater than the checkpoint path are fast-forwarded WITHOUT any
 * processing, positioning stays correct when the checkpointed node no longer
 * exists (strictly-greater comparison), batches carry the last scanned path as
 * the next checkpoint, and a JCR failure propagates so the scan stays
 * resumable.
 * <p>
 * Also pins the system-session discipline: sessions are opened on the
 * COLLABORATION workspace with the configured timeout, every per-item primitive
 * releases its own session, the streamed walk releases ONE session at the end,
 * and the observation-listener session survives the registration to be released
 * only by the unregistration.
 */
@ExtendWith(MockitoExtension.class)
class CleanupJcrStorageTest {

  private static final String    JCR_DOWN_ERROR_MSG  = "JCR down";

  private static final String    NODE_UUID_A         = "uuid-a";

  private static final String    NODE_UUID_PLAIN     = "uuid-plain";

  private static final String    NODE_UUID_DOOMED    = "uuid-doomed";

  private static final String    NODE_UUID_GONE      = "uuid-gone";

  private static final String    NODE_UUID_KEPT      = "uuid-kept";

  private static final String    NODE_UUID_VERSIONED = "uuid-versioned";

  private static final String    NODE_UUID_FLAKY     = "uuid-flaky";

  private static final String    JCR_ROOT_VERSION    = "jcr:rootVersion";

  private static final String    USERS_PATH          = "/Users";                              // NOSONAR

  private static final String    PATH_A              = "/Users/j___/john/Private/a.pdf";      // NOSONAR

  private static final String    PATH_B              = "/Users/j___/john/Private/b.pdf";      // NOSONAR

  private static final String    PATH_C              = "/Users/j___/john/Private/c.pdf";      // NOSONAR

  private static final String    PATH_D              = "/Users/j___/john/Private/d.pdf";      // NOSONAR

  private static final String    PATH_E              = "/Users/j___/john/Private/e.pdf";      // NOSONAR

  private static final String    PATH_F              = "/Users/j___/john/Private/docs/a.pdf"; // NOSONAR

  private static final long      SESSION_TIMEOUT     = 3600000L;

  @Mock
  private RepositoryService      repositoryService;

  @Mock
  private TrashStorage           trashStorage;

  @Mock
  private IdentityManager        identityManager;

  @Mock
  private SpaceService           spaceService;

  @Mock
  private ManageableRepository   repository;

  @Mock
  private ExtendedSession        session;

  @Mock
  private Workspace              workspace;

  @Mock
  private QueryManager           queryManager;

  @Mock
  private Query                  query;

  @Mock
  private QueryResult            queryResult;

  @InjectMocks
  private CleanupJcrStorage      cleanupJcrStorage;

  private Node                   nodeA;

  private Node                   nodeB;

  private Node                   nodeC;

  private Node                   nodeD;

  private Node                   nodeE;

  private CleanupParams          params;

  @BeforeEach
  void setUp() throws Exception {
    params = new CleanupParams(6, 1024L, 7, 5, List.of(), 100);
    // Property value normally injected by Spring through the @Value fallback
    setField("jcrSessionTimeout", SESSION_TIMEOUT);
    when(repositoryService.getDefaultRepository()).thenReturn(repository);
    // The cleanup roots live in the collaboration workspace: the session is
    // asked for it by name, never for the deployment's default workspace
    when(repository.getSystemSession(CleanupJcrStorage.COLLABORATION)).thenReturn(session);
    // Lenient: the exemption-mixin tests never query the workspace
    org.mockito.Mockito.lenient().when(session.getWorkspace()).thenReturn(workspace);
    org.mockito.Mockito.lenient().when(workspace.getQueryManager()).thenReturn(queryManager);
    org.mockito.Mockito.lenient().when(queryManager.createQuery(anyString(), anyString())).thenReturn(query);
    // Lenient: the JCR-failure test overrides it with a throwing stub
    org.mockito.Mockito.lenient().when(query.execute()).thenReturn(queryResult);
    nodeA = node(PATH_A);
    nodeB = node(PATH_B);
    nodeC = node(PATH_C);
    nodeD = node(PATH_D);
    nodeE = node(PATH_E);
  }

  @Test
  void scanRootFastForwardsPastCheckpointPathWithoutProcessing() throws RepositoryException {
    NodeIterator nodes = nodeIterator(nodeA, nodeB, nodeC, nodeD, nodeE);
    when(queryResult.getNodes()).thenReturn(nodes);
    List<String> batchCheckpoints = new ArrayList<>();
    List<Integer> batchScannedCounts = new ArrayList<>();

    cleanupJcrStorage.scanRoot(USERS_PATH, PATH_B, 2, params, (candidates, lastScannedPath, scannedCount) -> {
      batchCheckpoints.add(lastScannedPath);
      batchScannedCounts.add(scannedCount);
      return true;
    });

    // Nodes up to (and including) the checkpoint path are skipped without any
    // evaluation; the later ones are processed in batches carrying the last
    // scanned path as the next checkpoint
    assertEquals(List.of(PATH_D, PATH_E), batchCheckpoints);
    assertEquals(List.of(2, 1), batchScannedCounts);
    verify(nodeA, never()).isNodeType(anyString());
    verify(nodeB, never()).isNodeType(anyString());
    verify(nodeC, atLeastOnce()).isNodeType(anyString());
    verify(nodeD, atLeastOnce()).isNodeType(anyString());
    verify(nodeE, atLeastOnce()).isNodeType(anyString());
  }

  @Test
  void scanRootResumesCorrectlyWhenCheckpointedNodeIsGone() throws RepositoryException {
    // The checkpointed node was deleted during the interruption: the
    // strictly-greater comparison still positions on the first node after it
    NodeIterator nodes = nodeIterator(nodeA, nodeC, nodeD, nodeE);
    when(queryResult.getNodes()).thenReturn(nodes);
    List<String> batchCheckpoints = new ArrayList<>();

    cleanupJcrStorage.scanRoot(USERS_PATH, PATH_B, 2, params, (candidates, lastScannedPath, scannedCount) -> {
      batchCheckpoints.add(lastScannedPath);
      return true;
    });

    assertEquals(List.of(PATH_D, PATH_E), batchCheckpoints);
    verify(nodeA, never()).isNodeType(anyString());
    verify(nodeC, atLeastOnce()).isNodeType(anyString());
  }

  @Test
  void scanRootWithoutCheckpointProcessesEverything() throws RepositoryException {
    NodeIterator nodes = nodeIterator(nodeA, nodeB, nodeC, nodeD, nodeE);
    when(queryResult.getNodes()).thenReturn(nodes);
    List<String> batchCheckpoints = new ArrayList<>();
    List<Integer> batchScannedCounts = new ArrayList<>();

    cleanupJcrStorage.scanRoot(USERS_PATH, null, 2, params, (candidates, lastScannedPath, scannedCount) -> {
      batchCheckpoints.add(lastScannedPath);
      batchScannedCounts.add(scannedCount);
      return true;
    });

    assertEquals(List.of(PATH_B, PATH_D, PATH_E), batchCheckpoints);
    assertEquals(List.of(2, 2, 1), batchScannedCounts);
    verify(nodeA, atLeastOnce()).isNodeType(anyString());
    // The lazy NodeIterator needs the session for the WHOLE walk: exactly ONE
    // session, opened once and logged out after the last batch, never per batch
    verify(repository).getSystemSession(CleanupJcrStorage.COLLABORATION);
    verify(session).logout();
  }

  @Test
  void scanRootStopsWhenTheConsumerAborts() throws RepositoryException {
    NodeIterator nodes = nodeIterator(nodeA, nodeB, nodeC, nodeD, nodeE);
    when(queryResult.getNodes()).thenReturn(nodes);
    List<String> batchCheckpoints = new ArrayList<>();

    cleanupJcrStorage.scanRoot(USERS_PATH, null, 2, params, (candidates, lastScannedPath, scannedCount) -> {
      batchCheckpoints.add(lastScannedPath);
      return false;
    });

    assertEquals(List.of(PATH_B), batchCheckpoints);
    verify(nodeC, never()).getPath();
  }

  @Test
  void scanRootPropagatesJcrFailureToStayResumable() throws RepositoryException {
    when(query.execute()).thenThrow(new RepositoryException(JCR_DOWN_ERROR_MSG));

    assertThrows(IllegalStateException.class,
                 () -> cleanupJcrStorage.scanRoot(USERS_PATH,
                                                  PATH_B,
                                                  2,
                                                  params,
                                                  (candidates,
                                                   lastScannedPath,
                                                   scannedCount) -> true));
  }

  @Test
  void scanRootEmitsQualifyingExemptedFileFlaggedWithMixinDecision() throws RepositoryException {
    Calendar exemptedDate = Calendar.getInstance();
    Node exemptedNode = qualifyingNode(PATH_A, NODE_UUID_A, true, "mary", exemptedDate);
    NodeIterator nodes = nodeIterator(exemptedNode);
    when(queryResult.getNodes()).thenReturn(nodes);
    List<CleanupCandidate> emitted = new ArrayList<>();

    cleanupJcrStorage.scanRoot(USERS_PATH, null, 10, params, (candidates, lastScannedPath, scannedCount) -> {
      emitted.addAll(candidates);
      return true;
    });

    assertEquals(1, emitted.size(), "A qualifying exempted file must be emitted, no more skipped");
    CleanupCandidate candidate = emitted.get(0);
    assertEquals(NODE_UUID_A, candidate.getNodeUuid());
    assertEquals(CleanupAction.DELETE, candidate.getAction());
    assertEquals(2048L, candidate.getFileSize());
    assertTrue(candidate.isExempted());
    assertEquals("mary", candidate.getExemptedBy());
    assertEquals(exemptedDate.getTimeInMillis(), candidate.getExemptedDate());
  }

  @Test
  void scanRootEmitsQualifyingNonExemptedFileUnflagged() throws RepositoryException {
    Node plainNode = qualifyingNode(PATH_A, NODE_UUID_A, false, null, null);
    NodeIterator nodes = nodeIterator(plainNode);
    when(queryResult.getNodes()).thenReturn(nodes);
    List<CleanupCandidate> emitted = new ArrayList<>();

    cleanupJcrStorage.scanRoot(USERS_PATH, null, 10, params, (candidates, lastScannedPath, scannedCount) -> {
      emitted.addAll(candidates);
      return true;
    });

    assertEquals(1, emitted.size());
    assertFalse(emitted.get(0).isExempted());
    assertNull(emitted.get(0).getExemptedBy());
  }

  @Test
  void removeExemptionMixinReturnsNotFoundWhenNodeIsGone() throws RepositoryException {
    when(session.getNodeByIdentifier(NODE_UUID_GONE)).thenThrow(new PathNotFoundException("gone"));

    assertEquals(CleanupExemptionResult.NOT_FOUND, cleanupJcrStorage.removeExemptionMixin(NODE_UUID_GONE));
  }

  @Test
  void removeExemptionMixinChecksOutCheckedInVersionableNodeThenRemovesAndSaves() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier(NODE_UUID_KEPT)).thenReturn(node);
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(true);
    when(node.isCheckedOut()).thenReturn(false);
    when(node.isNodeType(CleanupJcrStorage.EXO_CLEANUP_EXEMPTION)).thenReturn(true);

    assertEquals(CleanupExemptionResult.ADDED, cleanupJcrStorage.removeExemptionMixin(NODE_UUID_KEPT));

    verify(node).checkout();
    verify(node).removeMixin(CleanupJcrStorage.EXO_CLEANUP_EXEMPTION);
    verify(session).save();
  }

  @Test
  void removeExemptionMixinIsIdempotentWhenMixinAbsent() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier(NODE_UUID_PLAIN)).thenReturn(node);

    assertEquals(CleanupExemptionResult.ADDED, cleanupJcrStorage.removeExemptionMixin(NODE_UUID_PLAIN));

    verify(node, never()).removeMixin(anyString());
    verify(session, never()).save();
  }

  @Test
  void removeExemptionMixinReturnsFailedAndRefreshesSessionOnJcrWriteFailure() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier(NODE_UUID_KEPT)).thenReturn(node);
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(false);
    when(node.isNodeType(CleanupJcrStorage.EXO_CLEANUP_EXEMPTION)).thenReturn(true);
    doThrow(new RepositoryException(JCR_DOWN_ERROR_MSG)).when(node).removeMixin(CleanupJcrStorage.EXO_CLEANUP_EXEMPTION);

    assertEquals(CleanupExemptionResult.FAILED, cleanupJcrStorage.removeExemptionMixin(NODE_UUID_KEPT));

    verify(session, never()).save();
    verify(session).refresh(false);
  }

  @Test
  void deleteNodeRemovesPointingSymlinksFirstAndReportsContentSizeAsReclaimedBytes() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier(NODE_UUID_DOOMED)).thenReturn(node);
    when(node.getPath()).thenReturn(PATH_F);
    Node content = mock(Node.class);
    Property dataProperty = mock(Property.class);
    when(dataProperty.getLength()).thenReturn(2048L);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(node.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(content);
    when(content.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(true);
    when(content.getProperty(NodeTypeConstants.JCR_DATA)).thenReturn(dataProperty);
    Node symlink = mock(Node.class);
    Session symlinkSession = mock(Session.class);
    when(symlink.getSession()).thenReturn(symlinkSession);
    when(trashStorage.getAllLinks(node, NodeTypeConstants.EXO_SYMLINK)).thenReturn(List.of(symlink));
    Node parentFolder = mock(Node.class);
    when(node.getParent()).thenReturn(parentFolder);
    // Non-empty parent: the empty-ancestors sweep removes nothing
    when(parentFolder.getDepth()).thenReturn(5);
    when(parentFolder.hasNodes()).thenReturn(true);

    CleanupPurgeResult result = cleanupJcrStorage.deleteNode(NODE_UUID_DOOMED);

    assertEquals(CleanupItemState.PURGED, result.getState());
    assertEquals(2048L, result.getReclaimedBytes(), "Reclaimed bytes must report the content size");
    // The pointing symlinks are removed BEFORE the node itself
    org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(symlink, node);
    inOrder.verify(symlink).remove();
    inOrder.verify(node).remove();
    verify(symlinkSession).save();
    verify(session).save();
    verify(parentFolder, never()).remove();
  }

  @Test
  void deleteNodeSweepsEmptyAncestorsStoppingAtTheDriveRoot() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier(NODE_UUID_DOOMED)).thenReturn(node);
    // Drive root = the user's Private folder, at depth 4 here
    when(node.getPath()).thenReturn(PATH_F);
    Node docsFolder = mock(Node.class);
    Node privateFolder = mock(Node.class);
    when(node.getParent()).thenReturn(docsFolder);
    when(docsFolder.getDepth()).thenReturn(5);
    when(docsFolder.hasNodes()).thenReturn(false);
    when(docsFolder.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(docsFolder.getParent()).thenReturn(privateFolder);
    when(privateFolder.getDepth()).thenReturn(4);

    CleanupPurgeResult result = cleanupJcrStorage.deleteNode(NODE_UUID_DOOMED);

    assertEquals(CleanupItemState.PURGED, result.getState());
    // The now-empty docs folder is swept, the drive root itself never is
    verify(docsFolder).remove();
    verify(privateFolder, never()).remove();
    verify(privateFolder, never()).hasNodes();
  }

  @Test
  void deleteNodeSkipsAndRefreshesSessionOnReferentialIntegrityFailure() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier("uuid-referenced")).thenReturn(node);
    when(node.getPath()).thenReturn(PATH_A);
    Node parentFolder = mock(Node.class);
    when(node.getParent()).thenReturn(parentFolder);
    doThrow(new javax.jcr.ReferentialIntegrityException("still referenced")).when(session).save();

    CleanupPurgeResult result = cleanupJcrStorage.deleteNode("uuid-referenced");

    assertEquals(CleanupItemState.SKIPPED, result.getState());
    assertTrue(result.getFailureReason().startsWith("cleanup.referentialIntegrity"),
               "A referenced node must be SKIPPED with the referential-integrity reason");
    verify(session).refresh(false);
    verify(parentFolder, never()).remove();
  }

  @Test
  void deleteNodeReturnsGoneWhenNodeMissing() throws RepositoryException {
    when(session.getNodeByIdentifier(NODE_UUID_GONE)).thenThrow(new PathNotFoundException("gone"));

    CleanupPurgeResult result = cleanupJcrStorage.deleteNode(NODE_UUID_GONE);

    assertEquals(CleanupItemState.GONE, result.getState());
    verify(session, never()).save();
  }

  @Test
  void purgeVersionsRemovesOldestFirstDownToMaxSkippingRootAndBaseVersions() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier(NODE_UUID_VERSIONED)).thenReturn(node);
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(true);
    VersionHistory versionHistory = mock(VersionHistory.class);
    when(node.getVersionHistory()).thenReturn(versionHistory);
    Version baseVersion = mock(Version.class);
    when(baseVersion.getName()).thenReturn("4");
    when(node.getBaseVersion()).thenReturn(baseVersion);
    Version rootVersion = version(JCR_ROOT_VERSION, 0L);
    Version firstVersion = version("1", 100L);
    Version secondVersion = version("2", 200L);
    Version thirdVersion = version("3", 300L);
    Version fourthVersion = version("4", 400L);
    // getAllVersions is read twice: once to count, once to walk oldest-first
    VersionIterator countIterator = mock(VersionIterator.class);
    when(countIterator.getSize()).thenReturn(5L); // root + 4 versions
    VersionIterator walkIterator = versionIterator(rootVersion, firstVersion, secondVersion, thirdVersion, fourthVersion);
    when(versionHistory.getAllVersions()).thenReturn(countIterator, walkIterator);

    CleanupPurgeResult result = cleanupJcrStorage.purgeVersions(NODE_UUID_VERSIONED, 2);

    assertEquals(CleanupItemState.PURGED, result.getState());
    assertEquals(300L, result.getReclaimedBytes(), "Reclaimed bytes must sum the removed version sizes");
    // 4 versions, max 2: the two OLDEST are removed, never the root version
    // (skipped by name) nor the base (current) version
    verify(versionHistory).removeVersion("1");
    verify(versionHistory).removeVersion("2");
    verify(versionHistory, never()).removeVersion(JCR_ROOT_VERSION);
    verify(versionHistory, never()).removeVersion("3");
    verify(versionHistory, never()).removeVersion("4");
  }

  @Test
  void purgeVersionsSkipsWhenAVersionRemovalFails() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier(NODE_UUID_VERSIONED)).thenReturn(node);
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(true);
    VersionHistory versionHistory = mock(VersionHistory.class);
    when(node.getVersionHistory()).thenReturn(versionHistory);
    Version baseVersion = mock(Version.class);
    when(baseVersion.getName()).thenReturn("3");
    when(node.getBaseVersion()).thenReturn(baseVersion);
    VersionIterator countIterator = mock(VersionIterator.class);
    when(countIterator.getSize()).thenReturn(4L); // root + 3 versions
    VersionIterator walkIterator = versionIterator(version(JCR_ROOT_VERSION, 0L),
                                                   version("1", 100L),
                                                   version("2", 200L),
                                                   version("3", 300L));
    when(versionHistory.getAllVersions()).thenReturn(countIterator, walkIterator);
    doThrow(new RepositoryException("version in use")).when(versionHistory).removeVersion("1");

    CleanupPurgeResult result = cleanupJcrStorage.purgeVersions(NODE_UUID_VERSIONED, 1);

    assertEquals(CleanupItemState.SKIPPED, result.getState());
    assertTrue(result.getFailureReason().startsWith("cleanup.purgeVersionsError"));
  }

  @Test
  void purgeVersionsSkipsNonVersionableNode() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier(NODE_UUID_PLAIN)).thenReturn(node);
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(false);

    CleanupPurgeResult result = cleanupJcrStorage.purgeVersions(NODE_UUID_PLAIN, 2);

    assertEquals(CleanupItemState.SKIPPED, result.getState());
    assertEquals("cleanup.notVersionable", result.getFailureReason());
  }

  @Test
  void addExemptionMixinChecksOutCheckedInVersionableNodeBeforeWriting() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier(NODE_UUID_KEPT)).thenReturn(node);
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(true);
    when(node.isCheckedOut()).thenReturn(false);
    when(node.isNodeType(CleanupJcrStorage.EXO_CLEANUP_EXEMPTION)).thenReturn(false);

    assertEquals(CleanupExemptionResult.ADDED, cleanupJcrStorage.addExemptionMixin(NODE_UUID_KEPT, "john"));

    // A checked-in versionable node rejects property writes: checkout FIRST
    org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(node, session);
    inOrder.verify(node).checkout();
    inOrder.verify(node).addMixin(CleanupJcrStorage.EXO_CLEANUP_EXEMPTION);
    inOrder.verify(session).save();
    verify(node).setProperty(org.mockito.ArgumentMatchers.eq(CleanupJcrStorage.EXO_CLEANUP_EXEMPTED_DATE),
                             org.mockito.ArgumentMatchers.any(Calendar.class));
    verify(node).setProperty(CleanupJcrStorage.EXO_CLEANUP_EXEMPTED_BY, "john");
  }

  @Test
  void addExemptionMixinReturnsNotFoundWhenNodeIsGone() throws RepositoryException {
    when(session.getNodeByIdentifier(NODE_UUID_GONE)).thenThrow(new PathNotFoundException("gone"));

    assertEquals(CleanupExemptionResult.NOT_FOUND, cleanupJcrStorage.addExemptionMixin(NODE_UUID_GONE, "john"));
  }

  @Test
  void addExemptionMixinReturnsFailedAndRefreshesSessionOnJcrWriteFailure() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier(NODE_UUID_KEPT)).thenReturn(node);
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(false);
    when(node.isNodeType(CleanupJcrStorage.EXO_CLEANUP_EXEMPTION)).thenReturn(false);
    when(node.setProperty(org.mockito.ArgumentMatchers.eq(CleanupJcrStorage.EXO_CLEANUP_EXEMPTED_DATE),
                          org.mockito.ArgumentMatchers.any(Calendar.class)))
                                                                            .thenThrow(new RepositoryException(JCR_DOWN_ERROR_MSG));

    assertEquals(CleanupExemptionResult.FAILED, cleanupJcrStorage.addExemptionMixin(NODE_UUID_KEPT, "john"));

    verify(session, never()).save();
    verify(session).refresh(false);
  }

  @Test
  void revalidateReturnsGoneWhenNodeMissing() throws RepositoryException {
    when(session.getNodeByIdentifier(NODE_UUID_GONE)).thenThrow(new PathNotFoundException("gone"));

    CleanupRevalidation revalidation = cleanupJcrStorage.revalidate(NODE_UUID_GONE, params);

    assertFalse(revalidation.isUnknown());
    assertFalse(revalidation.isExists());
  }

  @Test
  void revalidateReturnsUnknownOnJcrReadFailure() throws RepositoryException {
    when(session.getNodeByIdentifier(NODE_UUID_FLAKY)).thenThrow(new RepositoryException(JCR_DOWN_ERROR_MSG));

    CleanupRevalidation revalidation = cleanupJcrStorage.revalidate(NODE_UUID_FLAKY, params);

    // A transient read failure is a distinct UNKNOWN outcome: never 'gone',
    // never 'no longer a candidate' (which would permanently spare the item)
    assertTrue(revalidation.isUnknown());
    assertNull(revalidation.getCandidate());
  }

  @Test
  void revalidateReturnsExemptedWhenMixinPresent() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier(NODE_UUID_KEPT)).thenReturn(node);
    when(node.isNodeType(CleanupJcrStorage.EXO_CLEANUP_EXEMPTION)).thenReturn(true);

    CleanupRevalidation revalidation = cleanupJcrStorage.revalidate(NODE_UUID_KEPT, params);

    assertFalse(revalidation.isUnknown());
    assertTrue(revalidation.isExempted());
  }

  @Test
  void deleteNodeReportsFailedReadAsSkippedNeverAsGone() throws RepositoryException {
    when(session.getNodeByIdentifier(NODE_UUID_FLAKY)).thenThrow(new RepositoryException(JCR_DOWN_ERROR_MSG));

    CleanupPurgeResult result = cleanupJcrStorage.deleteNode(NODE_UUID_FLAKY);

    // A transient repository failure must NEVER be reported as 'the file
    // disappeared': GONE is a definitive outcome recorded on the item
    assertEquals(CleanupItemState.SKIPPED, result.getState());
    assertTrue(result.getFailureReason().startsWith("cleanup.deleteError"));
    verify(session, never()).save();
  }

  @Test
  void purgeVersionsReportsFailedReadAsSkippedNeverAsGone() throws RepositoryException {
    when(session.getNodeByIdentifier(NODE_UUID_FLAKY)).thenThrow(new RepositoryException(JCR_DOWN_ERROR_MSG));

    CleanupPurgeResult result = cleanupJcrStorage.purgeVersions(NODE_UUID_FLAKY, 2);

    assertEquals(CleanupItemState.SKIPPED, result.getState());
    assertTrue(result.getFailureReason().startsWith("cleanup.purgeVersionsError"));
  }

  @Test
  void purgeVersionsReturnsGoneOnlyWhenNodeReallyMissing() throws RepositoryException {
    when(session.getNodeByIdentifier(NODE_UUID_GONE)).thenThrow(new PathNotFoundException("gone"));

    assertEquals(CleanupItemState.GONE, cleanupJcrStorage.purgeVersions(NODE_UUID_GONE, 2).getState());
  }

  @Test
  void addExemptionMixinReportsFailedReadAsFailedNeverAsNotFound() throws RepositoryException {
    when(session.getNodeByIdentifier(NODE_UUID_FLAKY)).thenThrow(new RepositoryException(JCR_DOWN_ERROR_MSG));

    // NOT_FOUND would make the Service mark the item GONE and durably discard
    // the user's keep decision; FAILED keeps it retryable
    assertEquals(CleanupExemptionResult.FAILED, cleanupJcrStorage.addExemptionMixin(NODE_UUID_FLAKY, "john"));
    verify(session, never()).save();
  }

  @Test
  void removeExemptionMixinReportsFailedReadAsFailedNeverAsNotFound() throws RepositoryException {
    when(session.getNodeByIdentifier(NODE_UUID_FLAKY)).thenThrow(new RepositoryException(JCR_DOWN_ERROR_MSG));

    assertEquals(CleanupExemptionResult.FAILED, cleanupJcrStorage.removeExemptionMixin(NODE_UUID_FLAKY));
    verify(session, never()).save();
  }

  @Test
  void deleteNodeReclaimedBytesSumContentSizeAndVersionsSizeOfAVersionableNode() throws RepositoryException {
    Node node = mock(Node.class);
    when(session.getNodeByIdentifier(NODE_UUID_DOOMED)).thenReturn(node);
    when(node.getPath()).thenReturn(PATH_F);
    Node content = mock(Node.class);
    Property dataProperty = mock(Property.class);
    when(dataProperty.getLength()).thenReturn(2048L);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(node.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(content);
    when(content.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(true);
    when(content.getProperty(NodeTypeConstants.JCR_DATA)).thenReturn(dataProperty);
    // Versionable: the version history bytes are freed by the delete too and
    // must be counted in the reclaimed total
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(true);
    VersionHistory versionHistory = mock(VersionHistory.class);
    when(node.getVersionHistory()).thenReturn(versionHistory);
    // Built before the stubbing: the iterator helper stubs its own mock
    VersionIterator versions = versionIterator(version(JCR_ROOT_VERSION, 0L), version("1", 100L), version("2", 200L));
    when(versionHistory.getAllVersions()).thenReturn(versions);
    Node parentFolder = mock(Node.class);
    when(node.getParent()).thenReturn(parentFolder);
    when(parentFolder.getDepth()).thenReturn(5);
    when(parentFolder.hasNodes()).thenReturn(true);

    CleanupPurgeResult result = cleanupJcrStorage.deleteNode(NODE_UUID_DOOMED);

    assertEquals(CleanupItemState.PURGED, result.getState());
    assertEquals(2348L,
                 result.getReclaimedBytes(),
                 "Reclaimed bytes must sum the content size AND the versions size of a versionable node");
  }

  @Test
  void perItemPrimitiveOpensACollaborationSessionWithTheConfiguredTimeoutAndReleasesIt() throws RepositoryException {
    when(session.getNodeByIdentifier(NODE_UUID_GONE)).thenThrow(new PathNotFoundException("gone"));

    cleanupJcrStorage.revalidate(NODE_UUID_GONE, params);

    // The scan roots (/Users, /Groups/spaces) live in the collaboration
    // workspace: it is named explicitly, never taken from the repository
    // configuration whose default workspace is deployment-dependent
    verify(repository).getSystemSession(CleanupJcrStorage.COLLABORATION);
    verify(repository, never()).getConfiguration();
    verify(session).setTimeout(SESSION_TIMEOUT);
    // getSystemSession opens a BRAND-NEW session per call and the per-item
    // primitives run once per campaign item: not releasing it would leak tens
    // of thousands of sessions on a large campaign
    verify(session).logout();
  }

  @Test
  void observationSessionSurvivesRegistrationAndIsReleasedOnlyByUnregistration() throws RepositoryException {
    ObservationManager observationManager = mock(ObservationManager.class);
    when(workspace.getObservationManager()).thenReturn(observationManager);

    assertTrue(cleanupJcrStorage.registerObservationListener((path, eventType) -> {
      /* no-op callback */
    }));

    verify(observationManager).addEventListener(any(EventListener.class),
                                                anyInt(),
                                                eq("/"),
                                                eq(true),
                                                isNull(),
                                                isNull(),
                                                eq(false));
    // A JCR listener is bound to the session that registered it: logging that
    // session out here would silently kill the listener
    verify(session, never()).logout();

    cleanupJcrStorage.unregisterObservationListener();

    // ONE dedicated long-lived session for the whole listener lifetime, logged
    // out only AFTER the listener has been removed
    verify(repository).getSystemSession(CleanupJcrStorage.COLLABORATION);
    org.mockito.InOrder inOrder = inOrder(observationManager, session);
    inOrder.verify(observationManager).removeEventListener(any(EventListener.class));
    inOrder.verify(session).logout();
  }

  @Test
  void failedObservationRegistrationReleasesTheSessionItOpened() throws RepositoryException {
    ObservationManager observationManager = mock(ObservationManager.class);
    when(workspace.getObservationManager()).thenReturn(observationManager);
    doThrow(new RepositoryException(JCR_DOWN_ERROR_MSG)).when(observationManager)
                                                        .addEventListener(any(EventListener.class),
                                                                          anyInt(),
                                                                          anyString(),
                                                                          eq(true),
                                                                          isNull(),
                                                                          isNull(),
                                                                          eq(false));

    assertFalse(cleanupJcrStorage.registerObservationListener((path, eventType) -> {
      /* no-op callback */
    }));

    // A failed registration owns no listener: its session must not be leaked
    // until a later retry succeeds
    verify(session).logout();
    // ... and the listener field is cleared, so unregister is a no-op
    cleanupJcrStorage.unregisterObservationListener();
    verify(observationManager, never()).removeEventListener(any(EventListener.class));
  }

  private void setField(String name, Object value) throws ReflectiveOperationException {
    Field field = CleanupJcrStorage.class.getDeclaredField(name);
    field.setAccessible(true); // NOSONAR
    field.set(cleanupJcrStorage, value);
  }

  /**
   * A version mock carrying a frozen content of the given size. Lenient
   * stubbing: versions past the removal budget are never visited.
   */
  private Version version(String name, long size) throws RepositoryException {
    Version version = mock(Version.class);
    org.mockito.Mockito.lenient().when(version.getName()).thenReturn(name);
    Node frozen = mock(Node.class);
    Node frozenContent = mock(Node.class);
    Property frozenData = mock(Property.class);
    org.mockito.Mockito.lenient().when(frozenData.getLength()).thenReturn(size);
    org.mockito.Mockito.lenient().when(version.hasNode(NodeTypeConstants.JCR_FROZEN_NODE)).thenReturn(true);
    org.mockito.Mockito.lenient().when(version.getNode(NodeTypeConstants.JCR_FROZEN_NODE)).thenReturn(frozen);
    org.mockito.Mockito.lenient().when(frozen.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    org.mockito.Mockito.lenient().when(frozen.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(frozenContent);
    org.mockito.Mockito.lenient().when(frozenContent.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(true);
    org.mockito.Mockito.lenient().when(frozenContent.getProperty(NodeTypeConstants.JCR_DATA)).thenReturn(frozenData);
    return version;
  }

  private VersionIterator versionIterator(Version... versions) {
    Iterator<Version> iterator = Arrays.asList(versions).iterator();
    VersionIterator versionIterator = mock(VersionIterator.class);
    when(versionIterator.hasNext()).thenAnswer(invocation -> iterator.hasNext());
    org.mockito.Mockito.lenient().when(versionIterator.nextVersion()).thenAnswer(invocation -> iterator.next());
    return versionIterator;
  }

  /**
   * A node qualifying for DELETE (created a year ago, content above the size
   * floor), optionally carrying the exemption mixin with its decision
   * properties.
   */
  private Node qualifyingNode(String path, // NOSONAR
                              String uuid,
                              boolean exempted,
                              String exemptedBy,
                              Calendar exemptedDate) throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    when(node.getPath()).thenReturn(path);
    when(node.getIdentifier()).thenReturn(uuid);
    Calendar created = Calendar.getInstance();
    created.add(Calendar.MONTH, -12);
    Property createdProperty = mock(Property.class);
    when(createdProperty.getDate()).thenReturn(created);
    when(node.hasProperty(NodeTypeConstants.JCR_CREATED_DATE)).thenReturn(true);
    when(node.getProperty(NodeTypeConstants.JCR_CREATED_DATE)).thenReturn(createdProperty);
    Node content = mock(Node.class);
    Property dataProperty = mock(Property.class);
    when(dataProperty.getLength()).thenReturn(2048L);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(node.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(content);
    when(content.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(true);
    when(content.getProperty(NodeTypeConstants.JCR_DATA)).thenReturn(dataProperty);
    Identity ownerIdentity = new Identity("organization", "john");
    ownerIdentity.setId("7");
    when(identityManager.getOrCreateUserIdentity("john")).thenReturn(ownerIdentity);
    if (exempted) {
      // Lenient: toCandidate also asks isNodeType("mix:versionable"), and a
      // strict
      // stub here would turn that call into an argument mismatch
      org.mockito.Mockito.lenient().when(node.isNodeType(CleanupJcrStorage.EXO_CLEANUP_EXEMPTION)).thenReturn(true);
      // Lenient: while these mixin-property stubbings are still unsatisfied,
      // the
      // date reads of JCRDocumentsUtil.getLastModifiedDate (hasProperty on
      // jcr:content/jcr:lastModified & co) would be rejected as argument
      // mismatches
      Property exemptedByProperty = mock(Property.class);
      org.mockito.Mockito.lenient().when(exemptedByProperty.getString()).thenReturn(exemptedBy);
      org.mockito.Mockito.lenient().when(node.hasProperty(CleanupJcrStorage.EXO_CLEANUP_EXEMPTED_BY)).thenReturn(true);
      org.mockito.Mockito.lenient()
                         .when(node.getProperty(CleanupJcrStorage.EXO_CLEANUP_EXEMPTED_BY))
                         .thenReturn(exemptedByProperty);
      Property exemptedDateProperty = mock(Property.class);
      org.mockito.Mockito.lenient().when(exemptedDateProperty.getDate()).thenReturn(exemptedDate);
      org.mockito.Mockito.lenient().when(node.hasProperty(CleanupJcrStorage.EXO_CLEANUP_EXEMPTED_DATE)).thenReturn(true);
      org.mockito.Mockito.lenient()
                         .when(node.getProperty(CleanupJcrStorage.EXO_CLEANUP_EXEMPTED_DATE))
                         .thenReturn(exemptedDateProperty);
    }
    return node;
  }

  private Node node(String path) throws RepositoryException {
    Node node = mock(Node.class);
    // Every non-stubbed check (hasProperty/hasNode/isNodeType) defaults to
    // false: the node is scanned but never qualifies as a candidate, which is
    // all these positioning tests need
    org.mockito.Mockito.lenient().when(node.getPath()).thenReturn(path);
    return node;
  }

  private NodeIterator nodeIterator(Node... nodes) {
    Iterator<Node> iterator = Arrays.asList(nodes).iterator();
    NodeIterator nodeIterator = mock(NodeIterator.class);
    when(nodeIterator.hasNext()).thenAnswer(invocation -> iterator.hasNext());
    org.mockito.Mockito.lenient().when(nodeIterator.nextNode()).thenAnswer(invocation -> iterator.next());
    return nodeIterator;
  }

}
