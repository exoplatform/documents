/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.listener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.exoplatform.document.cleanup.util.CleanupConstants;
import org.exoplatform.services.jcr.observation.ExtendedEvent;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * JCR observation listener registered only while a cleanup campaign is
 * PUBLISHED. Pure glue: filters events under the scanned roots and
 * asynchronously forwards (path, eventType) pairs to the given callback, with
 * no business logic.
 */
public class CleanupJcrObservationListener implements EventListener {

  private static final Log                 LOG = ExoLogger.getLogger(CleanupJcrObservationListener.class);

  private final BiConsumer<String, String> callback;

  public CleanupJcrObservationListener(BiConsumer<String, String> callback) {
    this.callback = callback;
  }

  @Override
  public void onEvent(EventIterator events) {
    // De-duplicate the whole event bundle by path (a single save can fire
    // many property events on one node) and submit ONE async task per bundle
    // instead of one per event
    Map<String, String> pathsToEventType = new LinkedHashMap<>();
    while (events.hasNext()) {
      Event event = events.nextEvent();
      try {
        String path = event.getPath();
        if (isWatched(path)) {
          pathsToEventType.putIfAbsent(path, eventTypeName(event.getType()));
        }
      } catch (Exception e) {
        LOG.debug("Error handling JCR observation event for cleanup campaign refresh", e);
      }
    }
    if (!pathsToEventType.isEmpty()) {
      CompletableFuture.runAsync(() -> pathsToEventType.forEach((path, eventType) -> {
        try {
          callback.accept(path, eventType);
        } catch (Exception e) {
          LOG.debug("Error refreshing cleanup candidates for JCR event {} on {}", eventType, path, e);
        }
      }));
    }
  }

  private boolean isWatched(String path) {
    for (String root : CleanupConstants.SCAN_ROOTS) {
      if (path != null && path.startsWith(root + "/")) {
        return true;
      }
    }
    return false;
  }

  private String eventTypeName(int eventType) {
    return switch (eventType) {
    case Event.NODE_REMOVED -> "NODE_REMOVED";
    case ExtendedEvent.NODE_MOVED -> "NODE_MOVED";
    case Event.PROPERTY_CHANGED -> "PROPERTY_CHANGED";
    default -> String.valueOf(eventType);
    };
  }

}
