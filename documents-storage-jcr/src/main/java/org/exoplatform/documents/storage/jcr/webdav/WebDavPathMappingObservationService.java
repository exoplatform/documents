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
package org.exoplatform.documents.storage.jcr.webdav;

import javax.jcr.Session;
import javax.jcr.observation.ObservationManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.documents.storage.jcr.webdav.listener.WebDavPathMappingUpdaterAction;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WebDavPathMappingObservationService {

  @Autowired
  private WebDavPathMappingService webDavPathMappingService;

  @Autowired
  private JcrWebDavService         jcrWebDavService;

  @PostConstruct
  public void init() {
    addMappingEventListener();
  }

  @SneakyThrows
  private void addMappingEventListener() {
    Session session = jcrWebDavService.getSystemSession();
    try {
      ObservationManager observation = session.getWorkspace().getObservationManager();
      WebDavPathMappingUpdaterAction.SUPPORTED_PATHS.forEach(path -> addMappingEventListener(observation,
                                                                                             createMappingListenerInstance(),
                                                                                             path));
    } finally {
      session.logout();
    }
  }

  @SneakyThrows
  private void addMappingEventListener(ObservationManager observation,
                                       WebDavPathMappingUpdaterAction mappingUpdaterAction,
                                       String path) {
    log.info("Register WebDAV path mapping listener on '{}'", path);
    observation.addEventListener(mappingUpdaterAction,
                                 WebDavPathMappingUpdaterAction.SUPPORTED_EVENT_TYPES,
                                 path,
                                 true,
                                 null,
                                 WebDavPathMappingUpdaterAction.SUPPORTED_NODE_TYPES.toArray(String[]::new),
                                 false);
  }

  private WebDavPathMappingUpdaterAction createMappingListenerInstance() {
    return new WebDavPathMappingUpdaterAction(webDavPathMappingService,
                                              jcrWebDavService);
  }
}
