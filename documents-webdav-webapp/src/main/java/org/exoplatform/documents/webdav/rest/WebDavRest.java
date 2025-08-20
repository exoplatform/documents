/**
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.documents.webdav.rest;

import static org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin.CONTEXT_PATH;
import static org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin.CONTEXT_PATH_ROOT;
import static org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin.CONTEXT_PATH_SINGLE_DRIVE;
import static org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin.CONTEXT_PATH_SINGLE_DRIVE_ROOT;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.documents.webdav.service.WebDavHttpMethodDispatcher;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

@RestController("drives")
@Tag(name = CONTEXT_PATH, description = "Managing WebDav Files")
@CrossOrigin("*")
public class WebDavRest {

  protected static final Log         LOG                   = ExoLogger.getLogger(WebDavRest.class);

  private static final List<String>  BLOCKED_FILES         = Arrays.asList("\\.DS_Store",
                                                                           "\\._.*",
                                                                           "\\.Spotlight-V100",
                                                                           "\\.Trashes",
                                                                           "\\.fseventsd",
                                                                           "\\.TemporaryItems",
                                                                           "\\.VolumeIcon\\.icns",
                                                                           "\\.DocumentRevisions-V100",
                                                                           "Thumbs\\.db",
                                                                           "ehthumbs\\.db",
                                                                           "desktop\\.ini",
                                                                           "\\.Trash-.*",
                                                                           "\\.nfs.*",
                                                                           "\\.X0-lock",
                                                                           "\\.Xauthority",
                                                                           "\\.gvfs",
                                                                           "\\.bash_history",
                                                                           "\\.zsh_history");

  private static final Pattern       BLOCKED_FILES_PATTERN = Pattern.compile(String.format("(?i)(?:^|/)(?:%s)$",
                                                                                           BLOCKED_FILES.stream()
                                                                                                        .collect(Collectors.joining("|"))));

  @Autowired
  private PortalContainer            container;

  @Autowired
  private WebDavHttpMethodDispatcher webDavMethodDispatcher;

  @Secured("users")
  @RequestMapping(path = "/**", produces = MediaType.ALL_VALUE, headers = "Connection!=Upgrade")
  @Operation(summary = "Handles All WebDav requests")
  public void webdav(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    handle(httpRequest, httpResponse);
  }

  @RequestMapping(path = "/**", produces = MediaType.ALL_VALUE, method = RequestMethod.OPTIONS, headers = "Connection!=Upgrade")
  @Operation(summary = "Handles OPTIONS Http Method WebDav requests")
  public void options(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    handle(httpRequest, httpResponse);
  }

  @SneakyThrows
  protected void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) { // NOSONAR
    if (BLOCKED_FILES_PATTERN.matcher(httpRequest.getRequestURI()).find()) {
      httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
    } else if (httpRequest.getRequestURI().contains(CONTEXT_PATH)
               && !httpRequest.getRequestURI().endsWith(CONTEXT_PATH_SINGLE_DRIVE)
               && !httpRequest.getRequestURI().endsWith(CONTEXT_PATH_SINGLE_DRIVE_ROOT)) {
      ExoContainerContext.setCurrentContainer(container);
      RequestLifeCycle.begin(container);
      try { // NOSONAR
        // Remove Header redundancy
        httpResponse.setHeader("Vary", "Origin");
        webDavMethodDispatcher.handle(httpRequest, httpResponse);
      } finally {
        RequestLifeCycle.end();
        ExoContainerContext.setCurrentContainer(null);
      }
    } else {
      try {
        LOG.warn("Redirect to main path {}", CONTEXT_PATH_ROOT);
        httpResponse.sendRedirect(CONTEXT_PATH_ROOT);
      } catch (IOException e) {
        LOG.error("Error while redirecting to context path {}", CONTEXT_PATH_ROOT, e);
      }
    }
  }

}
