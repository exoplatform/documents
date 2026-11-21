/*
 * Copyright (C) 2026 eXo Platform SAS.
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
package org.exoplatform.documents.storage.jcr.bulkactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.jcr.Node;
import javax.jcr.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.ActionStatus;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.ActionType;
import org.exoplatform.documents.model.FileNode;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.Identity;

class BulkDownloadActionTest {

  private BulkStorageActionService service;

  private final List<ActionData>   actions = new ArrayList<>();

  @TempDir
  Path                         tempDir;

  private String                   previousTempDirectory;

  private ExoContainer             container;

  @BeforeEach
  void setUp() throws Exception {
    previousTempDirectory = System.getProperty("java.io.tmpdir");
    // the archives of the tests are created here, nothing else shares this directory
    System.setProperty("java.io.tmpdir", tempDir.toString());
    // held by the test: the context does not keep a mock alive, and a collected one makes the
    // lookups fall back to the root container
    container = mock(ExoContainer.class);
    when(container.getComponentInstanceOfType(ListenerService.class)).thenReturn(mock(ListenerService.class));
    bindContainer();
    service = new BulkStorageActionService();
    // the threads are run by the tests themselves
    Field pool = BulkStorageActionService.class.getDeclaredField("bulkActionThreadPool");
    pool.setAccessible(true);
    pool.set(service, mock(ExecutorService.class));
  }

  @AfterEach
  void tearDown() throws Exception {
    for (ActionData action : actions) {
      service.removeActionData(action);
    }
    System.setProperty("java.io.tmpdir", previousTempDirectory);
    ExoContainerContext.setCurrentContainer(null);
  }

  @Test
  void aDownloadIsFoundOnlyForTheUserWhoStartedIt() {
    ActionData alicesDownload = startDownload("7", "alice");
    ActionData bobsDownload = startDownload("7", "bob");

    assertSame(alicesDownload, service.getActionData("7", "alice"));
    assertSame(bobsDownload, service.getActionData("7", "bob"));
    assertNull(service.getActionData("7", "carol"));
    assertNull(service.getActionData("8", "alice"));
  }

  @Test
  void aDownloadSharingAnotherUsersIdUpdatesOnlyItsOwnAction() throws Exception {
    ActionData alicesDownload = startDownload("7", "alice");
    ActionData bobsDownload = startDownload("7", "bob");

    runAction(bobsDownload);

    assertEquals(ActionStatus.DONE_SUCCESSFULLY.name(), bobsDownload.getStatus());
    assertNotNull(bobsDownload.getDownloadZipPath());
    assertEquals(ActionStatus.STARTED.name(), alicesDownload.getStatus());
    assertNull(alicesDownload.getDownloadZipPath());
  }

  @Test
  void twoDownloadsWithTheSameIdGetTwoArchives() throws Exception {
    ActionData alicesDownload = startDownload("7", "alice");
    ActionData bobsDownload = startDownload("7", "bob");

    runAction(alicesDownload);
    runAction(bobsDownload);

    assertNotEquals(alicesDownload.getDownloadZipPath(), bobsDownload.getDownloadZipPath());
    assertTrue(Files.exists(Path.of(alicesDownload.getDownloadZipPath())));
    assertTrue(Files.exists(Path.of(bobsDownload.getDownloadZipPath())));
  }

  @Test
  void aBulkDeleteSharingAnotherUsersIdUpdatesOnlyItsOwnAction() throws Exception {
    ActionData alicesDownload = startDownload("7", "alice");
    ActionData bobsDelete = startAction("7", "bob", ActionType.DELETE);

    runAction(bobsDelete);

    assertEquals(ActionStatus.DONE_SUCCESSFULLY.name(), bobsDelete.getStatus());
    assertEquals(ActionStatus.STARTED.name(), alicesDownload.getStatus());
    assertNull(alicesDownload.getDownloadZipPath());
  }

  @Test
  void aNewActionRunsItsOwnTypeWhenTheSameUserHasAnOlderActionWithTheSameId() throws Exception {
    ActionData olderDelete = startAction("7", "alice", ActionType.DELETE);
    ActionData newDownload = startDownload("7", "alice");

    runAction(newDownload);

    assertEquals(ActionStatus.DONE_SUCCESSFULLY.name(), newDownload.getStatus());
    assertNotNull(newDownload.getDownloadZipPath());
    assertEquals(ActionStatus.STARTED.name(), olderDelete.getStatus());
  }

  @Test
  void theNewestActionOfTheUserIsTheOneFoundForCancelAndFetch() throws Exception {
    ActionData olderDownload = startDownload("7", "alice");
    ActionData newDownload = startDownload("7", "alice");

    assertSame(newDownload, service.getActionData("7", "alice"));

    runAction(newDownload);
    assertEquals(ActionStatus.DONE_SUCCESSFULLY.name(), service.getActionData("7", "alice").getStatus());
    assertNotNull(service.getActionData("7", "alice").getDownloadZipPath());
    assertEquals(ActionStatus.STARTED.name(), olderDownload.getStatus());
  }

  @Test
  void aNewActionReplacesTheEarlierEntriesOfTheSameUserAndIdWhateverTheirType() throws Exception {
    ActionData alicesDownload = startDownload("7", "alice");
    ActionData bobsDownload = startDownload("7", "bob");
    ActionData alicesDelete = startAction("7", "alice", ActionType.DELETE);

    List<ActionData> listed = listedActions();
    assertEquals(2, listed.stream().filter(a -> a == alicesDelete || a == bobsDownload).count());
    assertTrue(listed.stream().noneMatch(a -> a == alicesDownload));
    assertSame(alicesDelete, service.getActionData("7", "alice"));
    assertSame(bobsDownload, service.getActionData("7", "bob"));
  }

  @Test
  void theOlderActionRemovingItselfKeepsTheNewerEqualEntry() throws Exception {
    ActionData older = startDownload("7", "alice");
    ActionData newer = startDownload("7", "alice");

    service.removeActionData(older);

    assertSame(newer, service.getActionData("7", "alice"));
  }

  @Test
  void aRunningImportKeepsItsEntryWhenAnotherActionOfTheUserHasTheSameId() throws Exception {
    ActionData alicesImport = startAction("7", "alice", ActionType.IMPORT_ZIP);
    ActionData alicesDownload = startDownload("7", "alice");

    assertTrue(listedActions().stream().anyMatch(a -> a == alicesImport));
    assertTrue(listedActions().stream().anyMatch(a -> a == alicesDownload));
    assertSame(alicesDownload, service.getActionData("7", "alice"));
  }

  @Test
  void concurrentStartsAndRemovalsLeaveOneEntryPerUserAndId() throws Exception {
    int threads = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threads + 1);
    try {
      for (int round = 100; round < 160; round++) {
        String id = String.valueOf(round);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> running = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
          ActionData action = newAction(id, "alice", ActionType.DELETE);
          actions.add(action);
          running.add(executor.submit(() -> {
            bindContainer();
            go.await();
            service.executeBulkAction(null, null, null, null, null, Collections.emptyList(), action, null,
                                      Collections.emptyMap(), 1L);
            return null;
          }));
        }
        ActionData other = startAction("1", "bob", ActionType.DELETE);
        running.add(executor.submit(() -> {
          bindContainer();
          go.await();
          service.removeActionData(other);
          return null;
        }));
        go.countDown();
        for (Future<?> future : running) {
          future.get(30, TimeUnit.SECONDS);
        }
        assertEquals(1, listedActions().stream().filter(a -> id.equals(a.getActionId())).count());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void aStartWaitingBetweenTheRemovalAndTheAddCannotLetAnotherStartInterleave() throws Exception {
    CountDownLatch parked = new CountDownLatch(1);
    CountDownLatch secondDone = new CountDownLatch(1);
    AtomicBoolean firstCall = new AtomicBoolean(true);
    service.afterEarlierEntriesRemoved = () -> {
      if (firstCall.compareAndSet(true, false)) {
        parked.countDown();
        try {
          // a second start can only complete here when nothing makes it wait for this one
          secondDone.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };
    ActionData first = newAction("7", "alice", ActionType.DELETE);
    ActionData second = newAction("7", "alice", ActionType.DELETE);
    actions.add(first);
    actions.add(second);
    Thread firstStarter = new Thread(() -> {
      bindContainer();
      service.executeBulkAction(null, null, null, null, null, Collections.emptyList(),
                                                                     first, null, Collections.emptyMap(), 1L);
    });
    firstStarter.start();
    assertTrue(parked.await(10, TimeUnit.SECONDS));

    service.executeBulkAction(null, null, null, null, null, Collections.emptyList(), second, null, Collections.emptyMap(), 1L);
    secondDone.countDown();
    firstStarter.join(10000);

    assertEquals(1, listedActions().stream().filter(a -> "7".equals(a.getActionId())).count());
    assertSame(second, service.getActionData("7", "alice"));
  }

  @Test
  void aReplacedDownloadDoesNotKeepItsArchive() throws Exception {
    ActionData replaced = startDownload("7", "alice");
    ActionData current = startDownload("7", "alice");
    Set<String> before = archivesInTempDirectory();

    runAction(replaced);

    assertNull(replaced.getDownloadZipPath());
    assertEquals(before, archivesInTempDirectory());

    runAction(current);
    assertNotNull(current.getDownloadZipPath());
    assertTrue(Files.exists(Path.of(current.getDownloadZipPath())));
  }

  @Test
  void aDownloadReplacedAfterItsArchiveIsFinishedDoesNotKeepItsArchive() throws Exception {
    ActionData finished = startDownload("7", "alice");
    runAction(finished);
    Path finishedArchive = Path.of(finished.getDownloadZipPath());
    assertTrue(Files.exists(finishedArchive));

    ActionData current = startDownload("7", "alice");

    assertNull(finished.getDownloadZipPath());
    assertFalse(Files.exists(finishedArchive));
    runAction(current);
    assertNotNull(current.getDownloadZipPath());
    assertTrue(Files.exists(Path.of(current.getDownloadZipPath())));
  }

  @Test
  void anArchiveThatCannotBeBuiltIsDeleted() throws Exception {
    ActionData download = startDownload("7", "alice");
    Set<String> before = archivesInTempDirectory();

    try (MockedStatic<JCRDocumentsUtil> util = mockStatic(JCRDocumentsUtil.class, CALLS_REAL_METHODS)) {
      util.when(() -> JCRDocumentsUtil.zipFiles(anyString(), anyString()))
          .thenThrow(new IOException("cannot build the archive"));
      runAction(download);
    }

    assertEquals(ActionStatus.FAILED.name(), download.getStatus());
    assertNull(download.getDownloadZipPath());
    assertEquals(before, archivesInTempDirectory());
  }

  @Test
  void theTemporaryCopiesOfADownloadWhoseArchiveCannotBeBuiltAreDeleted() throws Exception {
    ActionData download = startDownload("7", "alice");
    FileNode item = new FileNode();
    item.setId("1");
    ActionThread thread = newThread(download, List.of(item));
    Node node = mock(Node.class);
    when(node.getParent()).thenReturn(mock(Node.class));
    AtomicBoolean copiesWritten = new AtomicBoolean();

    try (MockedStatic<JCRDocumentsUtil> util = mockStatic(JCRDocumentsUtil.class, CALLS_REAL_METHODS)) {
      util.when(() -> JCRDocumentsUtil.getNodeByIdentifier(any(), anyString())).thenReturn(node);
      // a copy at the root of the download folder and one in a sub-folder
      util.when(() -> JCRDocumentsUtil.createFile(any(Node.class), anyString(), anyString(), anyString(), any(Node.class)))
          .thenAnswer(invocation -> {
            Path folder = Path.of((String) invocation.getArgument(3));
            Files.writeString(folder.resolve("copy.txt"), "content");
            Files.createDirectories(folder.resolve("sub"));
            Files.writeString(folder.resolve("sub").resolve("copy.txt"), "content");
            return null;
          });
      util.when(() -> JCRDocumentsUtil.zipFiles(anyString(), anyString())).thenAnswer(invocation -> {
        Path folder = Path.of((String) invocation.getArgument(1));
        copiesWritten.set(Files.exists(folder.resolve("copy.txt")) && Files.exists(folder.resolve("sub").resolve("copy.txt")));
        throw new IOException("cannot build the archive");
      });
      thread.processAction();
    }

    // the copies existed when the archive failed, so the folder was not empty
    assertTrue(copiesWritten.get());
    // the folder is created by Files.createTempDirectory, which may not follow the test's tmpdir
    Field folder = ActionThread.class.getDeclaredField("tempFolderPath");
    folder.setAccessible(true);
    String tempFolderPath = (String) folder.get(thread);
    assertNotNull(tempFolderPath);
    assertFalse(Files.exists(Path.of(tempFolderPath)));
  }

  @SuppressWarnings("unchecked")
  private List<ActionData> listedActions() throws Exception {
    Field list = BulkStorageActionService.class.getDeclaredField("actionList");
    list.setAccessible(true);
    return new ArrayList<>((List<ActionData>) list.get(null));
  }

  private ActionData startDownload(String actionId, String userId) {
    return startAction(actionId, userId, ActionType.DOWNLOAD);
  }

  private Set<String> archivesInTempDirectory() throws Exception {
    try (Stream<Path> files = Files.list(tempDir)) {
      return files.map(path -> path.getFileName().toString())
                  .filter(name -> name.startsWith("downloadzip") && name.endsWith(".zip"))
                  .collect(Collectors.toSet());
    }
  }

  private void bindContainer() {
    ExoContainerContext.setCurrentContainer(container);
  }

  private ActionData newAction(String actionId, String userId, ActionType actionType) {
    ActionData action = new ActionData();
    action.setActionId(actionId);
    action.setActionType(actionType.name());
    action.setIdentity(new Identity(userId));
    return action;
  }

  private ActionData startAction(String actionId, String userId, ActionType actionType) {
    ActionData action = newAction(actionId, userId, actionType);
    actions.add(action);
    service.executeBulkAction(null,
                              null,
                              null,
                              null,
                              null,
                              Collections.emptyList(),
                              action,
                              null,
                              Collections.emptyMap(),
                              1L);
    return action;
  }

  private void runAction(ActionData action) throws Exception {
    newThread(action, Collections.emptyList()).processAction();
  }

  private ActionThread newThread(ActionData action, List<AbstractNode> items) {
    return new ActionThread(null,
                     null,
                     service,
                     mock(ListenerService.class),
                     null,
                     action,
                     null,
                     Collections.emptyMap(),
                     mock(Session.class),
                     items,
                     1L);
  }
}
