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

import static javax.jcr.observation.Event.NODE_ADDED;
import static javax.jcr.observation.Event.NODE_REMOVED;
import static javax.jcr.observation.Event.PROPERTY_ADDED;
import static javax.jcr.observation.Event.PROPERTY_CHANGED;
import static javax.jcr.observation.Event.PROPERTY_REMOVED;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_FILE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_FOLDER;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_RESOURCE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_UNSTRUCTURED;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.NODE_MOVED;
import static org.exoplatform.services.jcr.observation.ExtendedEvent.PERMISSION_CHANGED;

import java.util.Arrays;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.documents.storage.jcr.webdav.JcrWebDavService;
import org.exoplatform.documents.storage.jcr.webdav.WebDavPathMappingService;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
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

  public static final int          SUPPORTED_EVENT_TYPES = Arrays.asList(NODE_ADDED,                                 // NOSONAR
                                                                         NODE_REMOVED,
                                                                         NODE_MOVED,
                                                                         PROPERTY_ADDED,
                                                                         PROPERTY_CHANGED,
                                                                         PROPERTY_REMOVED,
                                                                         PERMISSION_CHANGED)
                                                                 .stream()
                                                                 .reduce(0, (a, b) -> a | b);

  private static final Log         LOG                   = ExoLogger.getLogger(WebDavPathMappingUpdaterAction.class);

  private WebDavPathMappingService webDavPathMappingService;

  private JcrWebDavService         jcrWebDavService;

  public WebDavPathMappingUpdaterAction(WebDavPathMappingService webDavPathMappingService,
                                        JcrWebDavService jcrWebDavService) {
    this.webDavPathMappingService = webDavPathMappingService;
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

      case NODE_ADDED:
        handleNodeAdded(path);
        break;

      case PROPERTY_ADDED, PROPERTY_CHANGED, PROPERTY_REMOVED:
        handlePropertyChanged(path);
        break;

      case PERMISSION_CHANGED:
        handlePermissionChanged(path);
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

    getWebDavPathMappingService().invalidateMappingTree(path);
    getWebDavPathMappingService().invalidateParentMappingChildren(path);
  }

  private void handleNodeAdded(String path) {
    if (StringUtils.isBlank(path)) {
      return;
    }
    getWebDavPathMappingService().invalidateParentMappingChildren(path);
  }

  private void handleNodeMoved(Event event, String newPath) throws RepositoryException {
    String oldPath = event.getPath();
    if (StringUtils.isNotBlank(oldPath)) {
      getWebDavPathMappingService().invalidateMappingTree(oldPath);
      getWebDavPathMappingService().invalidateParentMappingChildren(oldPath);
    }
    if (StringUtils.isNotBlank(newPath)) {
      getWebDavPathMappingService().invalidateMappingTree(newPath);
      getWebDavPathMappingService().invalidateParentMappingChildren(newPath);
    }
    Session session = jcrWebDavService.getSystemSession();
    try {
      NodeImpl node = (NodeImpl) session.getItem(newPath);
      String identifier = node.getIdentifier();
      webDavPathMappingService.invalidateMappingByIdentifier(identifier);
    } finally {
      session.logout();
    }
  }

  private void handlePropertyChanged(String propertyPath) {
    if (StringUtils.isBlank(propertyPath)) {
      return;
    }

    if (getWebDavPathMappingService().isTitlePropertyPath(propertyPath)) {
      String nodePath = getWebDavPathMappingService().getNodePathFromPropertyPath(propertyPath);
      getWebDavPathMappingService().invalidateMapping(nodePath);
      getWebDavPathMappingService().invalidateParentMappingChildren(nodePath);
    }
  }

  private void handlePermissionChanged(String path) {
    if (StringUtils.isBlank(path)) {
      return;
    }
    getWebDavPathMappingService().invalidateMapping(path);
  }

  private WebDavPathMappingService getWebDavPathMappingService() {
    if (webDavPathMappingService == null) {
      webDavPathMappingService = ExoContainerContext.getService(WebDavPathMappingService.class);
    }
    return webDavPathMappingService;
  }
}
