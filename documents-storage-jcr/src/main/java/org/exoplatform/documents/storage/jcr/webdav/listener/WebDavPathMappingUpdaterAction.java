/**
 * Copyright (C) 2026 eXo Platform SAS
 *
 * This program is free software: you can redistribute it and/or modify
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
package org.exoplatform.documents.storage.jcr.webdav.listener;

import static javax.jcr.observation.Event.NODE_REMOVED;
import static javax.jcr.observation.Event.PROPERTY_ADDED;
import static javax.jcr.observation.Event.PROPERTY_CHANGED;
import static javax.jcr.observation.Event.PROPERTY_REMOVED;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_FILE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_FOLDER;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_RESOURCE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_UNSTRUCTURED;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.NODE_MOVED;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.documents.storage.jcr.webdav.JcrWebDavService;
import org.exoplatform.documents.storage.jcr.webdav.plugin.PathCommandHandler;
import org.exoplatform.services.jcr.impl.core.observation.EventImpl;
import org.exoplatform.services.jcr.observation.ExtendedEvent;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import lombok.SneakyThrows;

public class WebDavPathMappingUpdaterAction implements EventListener {

  public static final List<String> SUPPORTED_NODE_TYPES  = Arrays.asList(NT_RESOURCE,                                // NOSONAR
                                                                         NT_FILE,
                                                                         NT_FOLDER,
                                                                         NT_UNSTRUCTURED);

  public static final List<String> SUPPORTED_PATHS       = Arrays.asList("/Users",                                   // NOSONAR
                                                                         "/Groups/spaces");

  public static final int          SUPPORTED_EVENT_TYPES = Arrays.asList(NODE_REMOVED,                               // NOSONAR
                                                                         NODE_MOVED,
                                                                         PROPERTY_ADDED,
                                                                         PROPERTY_CHANGED,
                                                                         PROPERTY_REMOVED)
                                                                 .stream()
                                                                 .reduce(0, (a, b) -> a | b);

  private static final Log         LOG                   = ExoLogger.getLogger(WebDavPathMappingUpdaterAction.class);

  private PathCommandHandler       pathCommandHandler;

  private JcrWebDavService         jcrWebDavService;

  public WebDavPathMappingUpdaterAction(PathCommandHandler pathCommandHandler) {
    this(pathCommandHandler, null);
  }

  public WebDavPathMappingUpdaterAction(PathCommandHandler pathCommandHandler,
                                        JcrWebDavService jcrWebDavService) {
    this.pathCommandHandler = pathCommandHandler;
    this.jcrWebDavService = jcrWebDavService;
  }

  @Override
  @SneakyThrows
  public void onEvent(EventIterator eventIterator) {
    while (eventIterator.hasNext()) {
      Event event = eventIterator.nextEvent();
      handleEvent(event);
    }
  }

  private void handleEvent(Event event) {
    try {
      int eventType = event.getType();
      String path = event.getPath();
      switch (eventType) {
      case NODE_REMOVED:
        handleNodeRemoved(path);
        break;
      case NODE_MOVED:
        handleNodeMoved(event, path);
        break;
      case PROPERTY_ADDED, PROPERTY_CHANGED, PROPERTY_REMOVED:
        handlePropertyChanged(path);
        break;
      default:
        break;
      }
    } catch (Exception e) {
      LOG.warn("Error while updating WebDAV path mapping from JCR event", e);
    }
  }

  private void handleNodeRemoved(String path) {
    if (StringUtils.isBlank(path)) {
      return;
    }

    getPathCommandHandler().deleteMapping(path);
  }

  private void handleNodeMoved(Event event, String newPath) {
    String oldPath = Stream.of(ExtendedEvent.SRC_ABS_PATH,
                               ExtendedEvent.SRC_CHILD_REL_PATH,
                               "oldPath")
                           .map(p -> getEventInfo(event, p))
                           .filter(StringUtils::isNotBlank)
                           .findFirst()
                           .orElse(null);

    // Refresh first so an existing mapping can still be found by node
    // identifier after the move.
    refreshMappingOrDelete(newPath);

    if (StringUtils.isNotBlank(oldPath)) {
      getPathCommandHandler().deleteMapping(oldPath);
    }
  }

  private void handlePropertyChanged(String propertyPath) {
    if (StringUtils.isBlank(propertyPath)) {
      return;
    }

    if (getPathCommandHandler().isTitlePropertyPath(propertyPath)) {
      String nodePath = getPathCommandHandler().getNodePathFromPropertyPath(propertyPath);
      refreshMappingOrDelete(nodePath);
    }
  }

  private void refreshMappingOrDelete(String jcrPath) {
    if (StringUtils.isBlank(jcrPath)) {
      return;
    }
    Session session = getJcrWebDavService().getSystemSession();
    try {
      getPathCommandHandler().refreshMappingOrDelete(session, jcrPath);
    } finally {
      session.logout();
    }
  }

  private String getEventInfo(Event event, String key) {
    try {
      Object value = event instanceof EventImpl eventImpl ? eventImpl.getInfo().get(key) : null;
      return value == null ? null : value.toString();
    } catch (Exception e) {
      return null;
    }
  }

  private PathCommandHandler getPathCommandHandler() {
    if (pathCommandHandler == null) {
      pathCommandHandler = ExoContainerContext.getService(PathCommandHandler.class);
    }
    return pathCommandHandler;
  }

  private JcrWebDavService getJcrWebDavService() {
    if (jcrWebDavService == null) {
      jcrWebDavService = ExoContainerContext.getService(JcrWebDavService.class);
    }
    return jcrWebDavService;
  }
}
