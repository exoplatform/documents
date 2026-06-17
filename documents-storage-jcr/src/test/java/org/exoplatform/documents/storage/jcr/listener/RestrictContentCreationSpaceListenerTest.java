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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent;

@RunWith(MockitoJUnitRunner.class)
public class RestrictContentCreationSpaceListenerTest {

  @Mock
  private DocumentFileService                documentFileService;

  @Mock
  private SpaceLifeCycleEvent                event;

  private RestrictContentCreationSpaceListener listener;

  @Before
  public void setUp() {
    listener = new RestrictContentCreationSpaceListener(documentFileService);
  }

  @Test
  public void testSpaceCreated() {
    Space space = new Space();
    space.setId("1");
    when(event.getSpace()).thenReturn(space);

    listener.spaceCreated(event);

    verify(documentFileService).synchronizeSpacePermissions(space);
  }

  @Test
  public void testAddRedactorUser() {
    Space space = new Space();
    space.setId("1");
    when(event.getSpace()).thenReturn(space);

    listener.addRedactorUser(event);

    verify(documentFileService).synchronizeSpacePermissions(space);
  }

  @Test
  public void testRemoveRedactorUser() {
    Space space = new Space();
    space.setId("1");
    when(event.getSpace()).thenReturn(space);

    listener.removeRedactorUser(event);

    verify(documentFileService).synchronizeSpacePermissions(space);
  }

  @Test
  public void testSpaceCreatedWithNullSpace() {
    when(event.getSpace()).thenReturn(null);

    listener.spaceCreated(event);

    verify(documentFileService).synchronizeSpacePermissions((Space) isNull());
  }

  @Test
  public void testAddRedactorUserWithNullSpace() {
    when(event.getSpace()).thenReturn(null);

    listener.addRedactorUser(event);

    verify(documentFileService).synchronizeSpacePermissions((Space) isNull());
  }

  @Test
  public void testRemoveRedactorUserWithNullSpace() {
    when(event.getSpace()).thenReturn(null);

    listener.removeRedactorUser(event);

    verify(documentFileService).synchronizeSpacePermissions((Space) isNull());
  }

  @Test
  public void testAllHandlersDelegateToService() {
    Space space = new Space();
    space.setId("1");
    when(event.getSpace()).thenReturn(space);

    listener.spaceCreated(event);
    listener.addRedactorUser(event);
    listener.removeRedactorUser(event);

    verify(documentFileService, times(3)).synchronizeSpacePermissions(space);
  }
}
