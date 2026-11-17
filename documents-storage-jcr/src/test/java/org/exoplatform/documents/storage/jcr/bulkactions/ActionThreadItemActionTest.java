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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.storage.BulkItemAction;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;

/**
 * A bulk action that applies one operation to each of its items: the order,
 * the user the operation runs as, what an item left out, a failure and a
 * cancellation do, and what is told at the end.
 */
class ActionThreadItemActionTest {

  private final BulkStorageActionService service  = mock(BulkStorageActionService.class);

  private final ListenerService          listener = mock(ListenerService.class);

  private final Identity                 john     = new Identity("john");

  private ConversationState              before;

  private ActionData                     actionData;

  @BeforeEach
  void setUp() {
    before = new ConversationState(new Identity("mary"));
    ConversationState.setCurrent(before);
    actionData = new ActionData();
    actionData.setActionId("12");
    actionData.setActionType("RESTORE");
    actionData.setIdentity(john);
    actionData.setStatus("STARTED");
  }

  @AfterEach
  void tearDown() {
    ConversationState.setCurrent(null);
  }

  @Test
  void eachItemIsTreatedInTheOrderGivenAsTheUserOfTheActionAndTheEndIsToldOnce() throws Exception {
    List<String> seen = new ArrayList<>();
    BulkItemAction action = itemId -> {
      seen.add(itemId + " as " + ConversationState.getCurrent().getIdentity().getUserId() + " " + actionData.getStatus());
      return true;
    };

    new ActionThread(service, listener, actionData, List.of("a", "b", "c"), action).processAction();

    assertEquals(List.of("a as john IN_PROGRESS", "b as john IN_PROGRESS", "c as john IN_PROGRESS"), seen);
    assertEquals("DONE_SUCCESSFULLY", actionData.getStatus());
    assertEquals(List.of("a", "b", "c"), actionData.getTreatedItemsIds());
    verify(listener, times(1)).broadcast("bulk_actions_document_event", john, actionData);
    // The thread gets back the state it had, and the action is forgotten.
    assertSame(before, ConversationState.getCurrent());
    verify(service).releaseActionData(actionData);
    // The action is the one given: it is not looked for by its id.
    verify(service, never()).getActionDataById(anyString());
  }

  @Test
  void anItemThatIsNoLongerThereIsLeftOutAndIsNoFailure() throws Exception {
    new ActionThread(service, listener, actionData, List.of("a", "gone", "c"), itemId -> !"gone".equals(itemId)).processAction();

    assertEquals("DONE_SUCCESSFULLY", actionData.getStatus());
    assertEquals(List.of("a", "c"), actionData.getTreatedItemsIds());
  }

  @Test
  void anItemThatFailsIsCountedAndTheOthersGoOn() throws Exception {
    BulkItemAction action = itemId -> {
      if ("refused".equals(itemId)) {
        throw new IllegalAccessException("The user may not act on the item");
      }
      if ("broken".equals(itemId)) {
        throw new IllegalStateException("The repository is not available");
      }
      return true;
    };

    new ActionThread(service, listener, actionData, List.of("refused", "a", "broken", "c"), action).processAction();

    assertEquals("DONE_WITH_ERRORS", actionData.getStatus());
    assertEquals(List.of("a", "c"), actionData.getTreatedItemsIds());
    verify(listener, times(1)).broadcast(anyString(), any(), any());
    verify(service).releaseActionData(actionData);
  }

  @Test
  void aCancellationLeavesTheItemsNotYetReachedAndStaysTheStatusTold() throws Exception {
    List<String> seen = new ArrayList<>();
    BulkItemAction action = itemId -> {
      seen.add(itemId);
      if ("b".equals(itemId)) {
        // Cancelled by its id while the second item is treated.
        actionData.setStatus("CANCELED");
      }
      return true;
    };

    new ActionThread(service, listener, actionData, List.of("a", "b", "c", "d"), action).processAction();

    assertEquals(List.of("a", "b"), seen);
    assertEquals("CANCELED", actionData.getStatus());
    assertEquals(List.of("a", "b"), actionData.getTreatedItemsIds());
    verify(listener, times(1)).broadcast("bulk_actions_document_event", john, actionData);
    verify(service).releaseActionData(actionData);
  }

  @Test
  void anActionStartedForNoOneRunsAsNoOne() throws Exception {
    actionData.setIdentity(null);
    List<ConversationState> states = new ArrayList<>();

    new ActionThread(service, listener, actionData, List.of("a"), itemId -> states.add(ConversationState.getCurrent())).processAction();

    assertEquals(1, states.size());
    assertNull(states.get(0));
    assertSame(before, ConversationState.getCurrent());
  }

  @Test
  void aRunThatBreaksOffGivesTheThreadItsStateBackAndForgetsTheAction() {
    ActionThread thread = new ActionThread(service, listener, actionData, List.of("a", "b"), itemId -> {
      throw new StackOverflowError("The run breaks off");
    });

    assertThrows(StackOverflowError.class, thread::processAction);

    assertSame(before, ConversationState.getCurrent());
    verify(service).releaseActionData(actionData);
  }

}
