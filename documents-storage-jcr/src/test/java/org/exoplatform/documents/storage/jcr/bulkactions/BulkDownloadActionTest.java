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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.jcr.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.ActionStatus;
import org.exoplatform.documents.model.ActionType;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.Identity;

class BulkDownloadActionTest {

  private BulkStorageActionService service;

  private final List<ActionData>   actions = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
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
      if (action.getDownloadZipPath() != null) {
        Files.deleteIfExists(Path.of(action.getDownloadZipPath()));
      }
    }
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
    ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads + 1);
    try {
      for (int round = 100; round < 160; round++) {
        String id = String.valueOf(round);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<?>> running = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
          ActionData action = newAction(id, "alice", ActionType.DELETE);
          actions.add(action);
          running.add(executor.submit(() -> {
            go.await();
            service.executeBulkAction(null, null, null, null, null, Collections.emptyList(), action, null,
                                      Collections.emptyMap(), 1L);
            return null;
          }));
        }
        ActionData other = startAction("1", "bob", ActionType.DELETE);
        running.add(executor.submit(() -> {
          go.await();
          service.removeActionData(other);
          return null;
        }));
        go.countDown();
        for (java.util.concurrent.Future<?> future : running) {
          future.get(30, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(1, listedActions().stream().filter(a -> id.equals(a.getActionId())).count());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void aStartWaitingBetweenTheRemovalAndTheAddCannotLetAnotherStartInterleave() throws Exception {
    java.util.concurrent.CountDownLatch parked = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch secondDone = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean firstCall = new java.util.concurrent.atomic.AtomicBoolean(true);
    service.afterEarlierEntriesRemoved = () -> {
      if (firstCall.compareAndSet(true, false)) {
        parked.countDown();
        try {
          // a second start can only complete here when nothing makes it wait for this one
          secondDone.await(1, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };
    ActionData first = newAction("7", "alice", ActionType.DELETE);
    ActionData second = newAction("7", "alice", ActionType.DELETE);
    actions.add(first);
    actions.add(second);
    Thread firstStarter = new Thread(() -> service.executeBulkAction(null, null, null, null, null, Collections.emptyList(),
                                                                     first, null, Collections.emptyMap(), 1L));
    firstStarter.start();
    assertTrue(parked.await(10, java.util.concurrent.TimeUnit.SECONDS));

    service.executeBulkAction(null, null, null, null, null, Collections.emptyList(), second, null, Collections.emptyMap(), 1L);
    secondDone.countDown();
    firstStarter.join(10000);

    assertEquals(1, listedActions().stream().filter(a -> "7".equals(a.getActionId())).count());
    assertSame(second, service.getActionData("7", "alice"));
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
    new ActionThread(null,
                     null,
                     service,
                     mock(ListenerService.class),
                     null,
                     action,
                     null,
                     Collections.emptyMap(),
                     mock(Session.class),
                     Collections.emptyList(),
                     1L).processAction();
  }
}
