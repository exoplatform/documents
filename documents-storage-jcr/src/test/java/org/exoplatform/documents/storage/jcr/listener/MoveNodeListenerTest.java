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
package org.exoplatform.documents.storage.jcr.listener;

import static org.mockito.Mockito.*;

import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MoveNodeListenerTest {

    private TrashStorage trashStorage;
    private ListenerService listenerService;
    private MoveNodeListener moveNodeListener;

    @Before
    public void setUp() {
        trashStorage = mock(TrashStorage.class);
        listenerService = mock(ListenerService.class);
        moveNodeListener = new MoveNodeListener(trashStorage, listenerService);
    }

    @Test
    public void testInitRegistersListener() {
        moveNodeListener.init();
        verify(listenerService, times(1))
                .addListener(eq("exo-document-moved"), eq(moveNodeListener));
    }

    @Test
    public void testOnEventCallsUpdateRestorePath() throws Exception {
        String oldPath = "/old/folder";
        String newPath = "/new/folder";

        @SuppressWarnings("unchecked")
        Event<String, String> event = mock(Event.class);

        when(event.getSource()).thenReturn(oldPath);
        when(event.getData()).thenReturn(newPath);

        moveNodeListener.onEvent(event);
        verify(trashStorage, times(1))
                .updateRestorePath(oldPath, newPath);
    }
}
