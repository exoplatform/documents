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
package org.exoplatform.documents.storage.jcr.bulkactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.FileNode;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;

/**
 * A bulk deletion moves its items to the trash as the user it was started
 * for: the repository records who last changed a document from the user the
 * thread acts as.
 */
class ActionThreadDeleteTest {

  private final BulkStorageActionService service       = mock(BulkStorageActionService.class);

  private final JCRDeleteFileStorage     deleteStorage = mock(JCRDeleteFileStorage.class);

  private final Identity                 john          = new Identity("john");

  private ConversationState              before;

  private ActionData                     actionData;

  @BeforeEach
  void setUp() {
    before = new ConversationState(new Identity("mary"));
    ConversationState.setCurrent(before);
    actionData = new ActionData();
    actionData.setActionId("12");
    actionData.setActionType("DELETE");
    actionData.setIdentity(john);
    actionData.setStatus("STARTED");
    when(service.getActionDataById("12")).thenReturn(actionData);
  }

  @AfterEach
  void tearDown() {
    ConversationState.setCurrent(null);
  }

  @Test
  void eachItemIsMovedToTheTrashAsTheUserOfTheActionAndTheThreadGetsItsStateBack() throws Exception {
    List<String> seen = new ArrayList<>();
    doAnswer(invocation -> {
      seen.add(invocation.getArgument(2) + " as " + ConversationState.getCurrent().getIdentity().getUserId());
      return null;
    }).when(deleteStorage).deleteDocument(any(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyLong(), any(), anyLong());

    new ActionThread(null,
                     deleteStorage,
                     service,
                     mock(ListenerService.class),
                     null,
                     actionData,
                     null,
                     null,
                     null,
                     List.of(item("a"), item("b")),
                     7L).processAction();

    assertEquals(List.of("a as john", "b as john"), seen);
    assertEquals("DONE_SUCCESSFULLY", actionData.getStatus());
    assertSame(before, ConversationState.getCurrent());
  }

  private static AbstractNode item(String id) {
    FileNode file = new FileNode();
    file.setId(id);
    file.setPath("/Groups/spaces/marketing/Documents/" + id + ".txt");
    return file;
  }

}
