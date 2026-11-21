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

  private ActionData startDownload(String actionId, String userId) {
    return startAction(actionId, userId, ActionType.DOWNLOAD);
  }

  private ActionData startAction(String actionId, String userId, ActionType actionType) {
    ActionData action = new ActionData();
    action.setActionId(actionId);
    action.setActionType(actionType.name());
    action.setIdentity(new Identity(userId));
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
