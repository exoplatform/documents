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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Pins the path-based resume semantics of the scan walk: nodes whose path is
 * not strictly greater than the checkpoint path are fast-forwarded WITHOUT any
 * processing, positioning stays correct when the checkpointed node no longer
 * exists (strictly-greater comparison), batches carry the last scanned path as
 * the next checkpoint, and a JCR failure propagates so the scan stays
 * resumable.
 */
@ExtendWith(MockitoExtension.class)
class CleanupJcrStorageTest {

  private static final String    PATH_A = "/Users/j___/john/Private/a.pdf";

  private static final String    PATH_B = "/Users/j___/john/Private/b.pdf";

  private static final String    PATH_C = "/Users/j___/john/Private/c.pdf";

  private static final String    PATH_D = "/Users/j___/john/Private/d.pdf";

  private static final String    PATH_E = "/Users/j___/john/Private/e.pdf";

  @Mock
  private RepositoryService      repositoryService;

  @Mock
  private SessionProviderService sessionProviderService;

  @Mock
  private TrashStorage           trashStorage;

  @Mock
  private IdentityManager        identityManager;

  @Mock
  private SpaceService           spaceService;

  @Mock
  private ManageableRepository   repository;

  @Mock
  private SessionProvider        sessionProvider;

  @Mock
  private Session                session;

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
  void setUp() throws RepositoryException {
    params = new CleanupParams(6, 1024L, 7, 5, List.of(), 100);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    when(sessionProvider.getSession(CleanupJcrStorage.COLLABORATION, repository)).thenReturn(session);
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    when(queryManager.createQuery(anyString(), anyString())).thenReturn(query);
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

    cleanupJcrStorage.scanRoot("/Users", PATH_B, 2, params, (candidates, lastScannedPath, scannedCount) -> {
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

    cleanupJcrStorage.scanRoot("/Users", PATH_B, 2, params, (candidates, lastScannedPath, scannedCount) -> {
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

    cleanupJcrStorage.scanRoot("/Users", null, 2, params, (candidates, lastScannedPath, scannedCount) -> {
      batchCheckpoints.add(lastScannedPath);
      batchScannedCounts.add(scannedCount);
      return true;
    });

    assertEquals(List.of(PATH_B, PATH_D, PATH_E), batchCheckpoints);
    assertEquals(List.of(2, 2, 1), batchScannedCounts);
    verify(nodeA, atLeastOnce()).isNodeType(anyString());
  }

  @Test
  void scanRootStopsWhenTheConsumerAborts() throws RepositoryException {
    NodeIterator nodes = nodeIterator(nodeA, nodeB, nodeC, nodeD, nodeE);
    when(queryResult.getNodes()).thenReturn(nodes);
    List<String> batchCheckpoints = new ArrayList<>();

    cleanupJcrStorage.scanRoot("/Users", null, 2, params, (candidates, lastScannedPath, scannedCount) -> {
      batchCheckpoints.add(lastScannedPath);
      return false;
    });

    assertEquals(List.of(PATH_B), batchCheckpoints);
    verify(nodeC, never()).getPath();
  }

  @Test
  void scanRootPropagatesJcrFailureToStayResumable() throws RepositoryException {
    when(query.execute()).thenThrow(new RepositoryException("JCR down"));

    assertThrows(IllegalStateException.class,
                 () -> cleanupJcrStorage.scanRoot("/Users", PATH_B, 2, params, (candidates, lastScannedPath,
                                                                                scannedCount) -> true));
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
