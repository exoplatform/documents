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
package org.exoplatform.document.cleanup.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;

import org.junit.jupiter.api.Test;

import org.exoplatform.services.jcr.observation.ExtendedEvent;

/**
 * Observation-glue tests pinning the per-bundle path de-duplication, the
 * scan-root filtering, the single asynchronous forward per bundle, the event
 * type naming and the per-event / per-callback error tolerance.
 */
class CleanupJcrObservationListenerTest {

  @Test
  void forwardsWatchedPathsOncePerBundleKeepingTheFirstEventType() throws InterruptedException {
    Map<String, String> forwarded = new ConcurrentHashMap<>();
    List<Long> callbackThreads = new CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);
    CleanupJcrObservationListener listener = new CleanupJcrObservationListener((path, eventType) -> {
      forwarded.put(path, eventType);
      callbackThreads.add(Thread.currentThread().threadId());
      latch.countDown();
    });

    String userFilePath = "/Users/j___/john/Private/file.pdf";
    String spaceFilePath = "/Groups/spaces/marketing/Documents/report.pdf";
    listener.onEvent(events(event(userFilePath, Event.PROPERTY_CHANGED),
                            // Same node fires many events on one save: deduplicated
                            event(userFilePath, Event.NODE_REMOVED),
                            event(spaceFilePath, ExtendedEvent.NODE_MOVED),
                            // Outside every scan root: filtered out
                            event("/exo:applications/some/node", Event.NODE_REMOVED)));

    assertTrue(latch.await(5, TimeUnit.SECONDS), "The bundle must be forwarded asynchronously");
    assertEquals(2, forwarded.size(), "One forward per distinct watched path");
    assertEquals("PROPERTY_CHANGED", forwarded.get(userFilePath), "The first event type of a path wins");
    assertEquals("NODE_MOVED", forwarded.get(spaceFilePath));
    assertTrue(callbackThreads.stream().noneMatch(threadId -> threadId == Thread.currentThread().threadId()),
               "The callback must never run on the JCR observation thread");
  }

  @Test
  void ignoresBundlesWithoutWatchedPaths() throws InterruptedException {
    Map<String, String> forwarded = new ConcurrentHashMap<>();
    CountDownLatch latch = new CountDownLatch(1);
    CleanupJcrObservationListener listener = new CleanupJcrObservationListener((path, eventType) -> {
      forwarded.put(path, eventType);
      latch.countDown();
    });

    listener.onEvent(events(event("/exo:applications/a", Event.NODE_REMOVED),
                            event("/Trash", Event.PROPERTY_CHANGED), // the root itself, not below it
                            event(null, Event.NODE_REMOVED)));
    // A later watched bundle proves the earlier one forwarded nothing
    listener.onEvent(events(event("/Trash/deleted.pdf", 9999)));

    assertTrue(latch.await(5, TimeUnit.SECONDS));
    assertEquals(Map.of("/Trash/deleted.pdf", "9999"), forwarded, "Unknown event types are forwarded by numeric value");
  }

  @Test
  void toleratesFailingEventsAndFailingCallback() throws InterruptedException, RepositoryException {
    Map<String, String> forwarded = new ConcurrentHashMap<>();
    CountDownLatch latch = new CountDownLatch(1);
    CleanupJcrObservationListener listener = new CleanupJcrObservationListener((path, eventType) -> {
      if (path.endsWith("poison.pdf")) {
        throw new IllegalStateException("Callback failure");
      }
      forwarded.put(path, eventType);
      latch.countDown();
    });

    Event failingEvent = mock(Event.class);
    when(failingEvent.getPath()).thenThrow(new RepositoryException("Broken event"));
    // Bundle order: failing event, poison path, then a healthy one
    listener.onEvent(events(failingEvent,
                            event("/Users/j___/john/Private/poison.pdf", Event.NODE_REMOVED),
                            event("/Users/j___/john/Private/ok.pdf", Event.NODE_REMOVED)));

    assertTrue(latch.await(5, TimeUnit.SECONDS), "Paths after a failing event/callback must still be forwarded");
    assertEquals(Map.of("/Users/j___/john/Private/ok.pdf", "NODE_REMOVED"), forwarded);
  }

  private Event event(String path, int eventType) {
    Event event = mock(Event.class);
    try {
      when(event.getPath()).thenReturn(path);
      org.mockito.Mockito.lenient().when(event.getType()).thenReturn(eventType);
    } catch (RepositoryException e) {
      throw new IllegalStateException(e);
    }
    return event;
  }

  private EventIterator events(Event... bundleEvents) {
    EventIterator iterator = mock(EventIterator.class);
    Boolean[] nextHasNext = new Boolean[bundleEvents.length];
    for (int i = 0; i < bundleEvents.length; i++) {
      nextHasNext[i] = i < bundleEvents.length - 1;
    }
    when(iterator.hasNext()).thenReturn(bundleEvents.length > 0, nextHasNext);
    if (bundleEvents.length > 0) {
      Event[] nextEvents = new Event[bundleEvents.length - 1];
      System.arraycopy(bundleEvents, 1, nextEvents, 0, bundleEvents.length - 1);
      when(iterator.nextEvent()).thenReturn(bundleEvents[0], nextEvents);
    }
    return iterator;
  }

}
