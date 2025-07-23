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
package org.exoplatform.documents.storage.jcr.cache.listener;

import static javax.jcr.observation.Event.NODE_ADDED;
import static javax.jcr.observation.Event.NODE_REMOVED;
import static javax.jcr.observation.Event.PROPERTY_ADDED;
import static javax.jcr.observation.Event.PROPERTY_CHANGED;
import static javax.jcr.observation.Event.PROPERTY_REMOVED;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_CONTENT;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_FILE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_FOLDER;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_RESOURCE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_UNSTRUCTURED;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.ADD_MIXIN;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.CHECKIN;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.CHECKOUT;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.LOCK;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.NODE_MOVED;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.PERMISSION_CHANGED;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.REMOVE_MIXIN;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.UNLOCK;

import java.util.Arrays;
import java.util.List;

import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.documents.storage.jcr.cache.CachedJcrWebDavService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import lombok.SneakyThrows;

public class WebDavCacheUpdaterAction implements EventListener {

  public static final List<String> SUPPORTED_NODE_TYPES  = Arrays.asList(NT_RESOURCE,                          // NOSONAR
                                                                         NT_FILE,
                                                                         NT_FOLDER,
                                                                         NT_UNSTRUCTURED);

  public static final List<String> SUPPORTED_PATHS       = Arrays.asList("/Users",                             // NOSONAR
                                                                         "/Groups/spaces");

  public static final int          SUPPORTED_EVENT_TYPES = Arrays.asList(NODE_ADDED,                           // NOSONAR
                                                                         NODE_REMOVED,
                                                                         PERMISSION_CHANGED,
                                                                         NODE_MOVED,
                                                                         ADD_MIXIN,
                                                                         REMOVE_MIXIN,
                                                                         LOCK,
                                                                         UNLOCK,
                                                                         CHECKIN,
                                                                         CHECKOUT,
                                                                         PROPERTY_ADDED,
                                                                         PROPERTY_CHANGED,
                                                                         PROPERTY_REMOVED)
                                                                 .stream()
                                                                 .reduce(0, (a, b) -> a | b);

  private static final Log         LOG                   = ExoLogger.getLogger(WebDavCacheUpdaterAction.class);

  private CachedJcrWebDavService   cachedJcrWebDavService;

  public WebDavCacheUpdaterAction(CachedJcrWebDavService cachedJcrWebDavService) {
    this.cachedJcrWebDavService = cachedJcrWebDavService;
  }

  @Override
  @SneakyThrows
  public void onEvent(EventIterator eventIterator) {
    while (eventIterator.hasNext()) {
      Event event = eventIterator.nextEvent();

      int eventType = event.getType();
      String path = event.getPath();
      switch (eventType) { // NOSONAR
      case NODE_ADDED, NODE_REMOVED, PERMISSION_CHANGED, NODE_MOVED, ADD_MIXIN, REMOVE_MIXIN, LOCK, UNLOCK, CHECKIN, CHECKOUT:
        path = path.endsWith(JCR_CONTENT) ? path.substring(0, path.lastIndexOf("/")) : path;
        clearCache(path,
                   eventType == NODE_ADDED
                         || eventType == NODE_MOVED
                         || eventType == NODE_REMOVED
                         || eventType == PERMISSION_CHANGED);
        break;
      case PROPERTY_ADDED, PROPERTY_CHANGED, PROPERTY_REMOVED:
        clearCache(path.substring(0, path.lastIndexOf("/")), false);
        break;
      }
    }
  }

  private void clearCache(String path, boolean dropEntry) {
    try {
      getCachedJcrWebDavService().clearCache(path, dropEntry);
    } catch (Exception e) {
      LOG.warn("Error while clearing cache entry with path '{}' and drop option = '{}'",
               path,
               dropEntry,
               e);
    }
  }

  private CachedJcrWebDavService getCachedJcrWebDavService() {
    if (cachedJcrWebDavService == null) {
      cachedJcrWebDavService = ExoContainerContext.getService(CachedJcrWebDavService.class);
    }
    return cachedJcrWebDavService;
  }
}
