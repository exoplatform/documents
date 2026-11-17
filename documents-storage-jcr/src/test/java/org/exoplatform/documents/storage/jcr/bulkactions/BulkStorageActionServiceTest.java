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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;

import org.exoplatform.documents.model.ActionData;
import org.exoplatform.services.listener.ListenerService;

/**
 * The start of a bulk action that applies one operation to each of its
 * items: what is handed to the pool, and how long the action is known.
 */
class BulkStorageActionServiceTest {

  private final ListenerService listener = mock(ListenerService.class);

  private final List<Runnable>  handed   = new ArrayList<>();

  @Test
  void anItemActionIsHandedToThePoolAndKnownByItsIdUntilItIsOver() throws Exception {
    BulkStorageActionService service = new BulkStorageActionService() {
      @Override
      void execute(Runnable action) {
        handed.add(action);
      }
    };
    ActionData actionData = action("item-action-known");
    List<String> ids = new ArrayList<>(List.of("a", "b"));
    List<String> treated = new ArrayList<>();

    service.executeItemAction(listener, actionData, ids, treated::add);

    assertEquals("STARTED", actionData.getStatus());
    assertSame(actionData, service.getActionDataById("item-action-known"));
    assertEquals(1, handed.size());
    assertEquals(List.of(), treated);
    // The items are the ones given when the action started.
    ids.add("c");
    assertInstanceOf(ActionThread.class, handed.get(0)).processAction();
    assertEquals(List.of("a", "b"), treated);
    assertEquals(List.of("a", "b"), actionData.getTreatedItemsIds());
    assertNull(service.getActionDataById("item-action-known"));
  }

  @Test
  void anActionThePoolDoesNotTakeIsForgotten() {
    BulkStorageActionService service = new BulkStorageActionService() {
      @Override
      void execute(Runnable action) {
        throw new RejectedExecutionException("The pool is stopping");
      }
    };
    ActionData actionData = action("item-action-refused");

    assertThrows(RejectedExecutionException.class, () -> service.executeItemAction(listener, actionData, List.of("a"), itemId -> true));

    assertNull(service.getActionDataById("item-action-refused"));
  }

  private static ActionData action(String id) {
    ActionData actionData = new ActionData();
    actionData.setActionId(id);
    actionData.setActionType("PERMANENTLY_DELETE");
    return actionData;
  }

}
