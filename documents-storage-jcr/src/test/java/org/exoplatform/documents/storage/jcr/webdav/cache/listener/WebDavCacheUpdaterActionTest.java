/**
 * Copyright (C) 2025 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.storage.jcr.webdav.cache.listener;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.documents.storage.jcr.webdav.cache.CachedJcrWebDavService;
import org.exoplatform.services.jcr.observation.ExtendedEvent;

import lombok.SneakyThrows;

@RunWith(MockitoJUnitRunner.class)
public class WebDavCacheUpdaterActionTest {

  private static final String      FILE_PATH = "/Users/john/file.txt";

  @Mock
  private CachedJcrWebDavService   cachedService;

  @Mock
  private EventIterator            eventIterator;

  @Mock
  private Event                    event;

  @InjectMocks
  private WebDavCacheUpdaterAction action;

  @Before
  public void setUp() {
    when(eventIterator.hasNext()).thenReturn(true, false); // only one event
    when(eventIterator.nextEvent()).thenReturn(event);
  }

  @Test
  @SneakyThrows
  public void testOnEventNodeAddedDropTrue() {
    when(event.getType()).thenReturn(Event.NODE_ADDED);
    when(event.getPath()).thenReturn("/Groups/spaces/mySpace");

    action.onEvent(eventIterator);

    verify(cachedService).clearCache("/Groups/spaces/mySpace", true);
  }

  @Test
  @SneakyThrows
  public void testOnEventNodeRemovedWithJcrContent() {
    when(event.getType()).thenReturn(Event.NODE_REMOVED);
    when(event.getPath()).thenReturn("/Users/john/jcr:content");

    action.onEvent(eventIterator);

    verify(cachedService).clearCache("/Users/john", true);
  }

  @Test
  @SneakyThrows
  public void testOnEventPropertyChangedNoDrop() {
    when(event.getType()).thenReturn(Event.PROPERTY_CHANGED);
    when(event.getPath()).thenReturn("/Users/john/file.txt/jcr:data");

    action.onEvent(eventIterator);

    verify(cachedService).clearCache(FILE_PATH, false);
  }

  @Test
  @SneakyThrows
  public void testOnEventExceptionInClearCache() {
    when(event.getType()).thenReturn(ExtendedEvent.NODE_MOVED);
    when(event.getPath()).thenReturn(FILE_PATH);
    doThrow(new RuntimeException("fail")).when(cachedService).clearCache(anyString(), anyBoolean());

    action.onEvent(eventIterator);

    verify(cachedService).clearCache(FILE_PATH, true);
    // Exception is swallowed and logged — no rethrow
  }

  @Test
  @SneakyThrows
  public void testGetCachedJcrWebDavServiceFallbackToContainer() {
    WebDavCacheUpdaterAction actionNoService = new WebDavCacheUpdaterAction(null);

    CachedJcrWebDavService mockService = mock(CachedJcrWebDavService.class);
    // Static mock of ExoContainerContext
    try (var mockedCtx = Mockito.mockStatic(ExoContainerContext.class)) {
      mockedCtx.when(() -> ExoContainerContext.getService(CachedJcrWebDavService.class))
               .thenReturn(mockService);

      when(event.getType()).thenReturn(Event.PROPERTY_ADDED);
      when(event.getPath()).thenReturn("/Users/john/file.txt/jcr:data");
      when(eventIterator.hasNext()).thenReturn(true, false);
      when(eventIterator.nextEvent()).thenReturn(event);

      actionNoService.onEvent(eventIterator);

      verify(mockService).clearCache(FILE_PATH, false);
    }
  }
}
