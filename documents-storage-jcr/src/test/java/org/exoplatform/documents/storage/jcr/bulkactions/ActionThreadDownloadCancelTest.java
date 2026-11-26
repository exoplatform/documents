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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.ActionStatus;
import org.exoplatform.documents.model.ActionType;
import org.exoplatform.documents.model.FileNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.listener.ListenerService;

class ActionThreadDownloadCancelTest {

  @TempDir
  Path tempDir;

  /**
   * A bulk download cancelled while its items are copied stops there: it stays
   * cancelled and writes no zip. Its temp folder is already deleted, so zipping
   * it would fail and mark the action as failed.
   */
  @Test
  void aDownloadCancelledDuringItsCopyStaysCancelledAndWritesNoZip() throws Exception {
    ActionData actionData = new ActionData();
    actionData.setActionId("cancelled-download");
    actionData.setActionType(ActionType.DOWNLOAD.name());
    actionData.setStatus(ActionStatus.CANCELED.name());
    BulkStorageActionService bulkStorageActionService = mock(BulkStorageActionService.class);
    when(bulkStorageActionService.getActionDataById("cancelled-download")).thenReturn(actionData);
    FileNode first = new FileNode();
    first.setId("first");
    FileNode second = new FileNode();
    second.setId("second");
    List<AbstractNode> items = List.of(first, second);
    ActionThread thread = new ActionThread(null,
                                           null,
                                           bulkStorageActionService,
                                           mock(ListenerService.class),
                                           null,
                                           actionData,
                                           null,
                                           null,
                                           mock(ExtendedSession.class),
                                           items,
                                           1L);

    String tempDirectory = System.getProperty("java.io.tmpdir");
    System.setProperty("java.io.tmpdir", tempDir.toString());
    try {
      thread.processAction();
    } finally {
      System.setProperty("java.io.tmpdir", tempDirectory);
    }

    assertEquals(ActionStatus.CANCELED.name(), actionData.getStatus());
    assertFalse(Files.exists(tempDir.resolve("downloadzipcancelled-download.zip")), "no zip is written");
  }
}
