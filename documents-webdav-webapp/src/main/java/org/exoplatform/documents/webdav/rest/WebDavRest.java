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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

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
import org.exoplatform.documents.webdav.rest.model.ServletOutputStreamWrapper;
import org.exoplatform.documents.webdav.service.WebDavHandler;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.SneakyThrows;

@RestController
@Tag(name = "/webdav/drives/", description = "Managing WebDav Files")
@CrossOrigin("*")
public class WebDavRest {

  protected static final Log LOG = ExoLogger.getLogger(WebDavRest.class);

  @Autowired
  private PortalContainer    container;

  @Autowired
  private WebDavHandler      webDavHandler;

  @Secured("users")
  @RequestMapping(path = "/drives/**", produces = MediaType.ALL_VALUE, headers = "Connection!=Upgrade")
  @Operation(summary = "Handles All WebDav requests")
  public void webdav(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    handle(httpRequest, httpResponse);
  }

  @Secured("users")
  @RequestMapping(path = "/drives/**", produces = MediaType.ALL_VALUE, method = {
    RequestMethod.OPTIONS }, headers = "Connection!=Upgrade")
  @Operation(summary = "Handles All WebDav requests")
  public void options(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    handle(httpRequest, httpResponse);
  }

  @SneakyThrows
  protected void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
    try { // NOSONAR
      if (LOG.isDebugEnabled()) {
        String reqUuid = UUID.randomUUID().toString();
        LOG.debug("[{}] URI: {} - Method {}", reqUuid, httpRequest.getRequestURI(), httpRequest.getMethod());
        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        webDavHandler.handle(httpRequest, new HttpServletResponseWrapper(httpResponse) {
          @Override
          public ServletOutputStream getOutputStream() throws IOException {
            return new ServletOutputStreamWrapper(arrayOutputStream);
          }
        });
        byte[] bytes = arrayOutputStream.toByteArray();
        if (LOG.isTraceEnabled()) {
          LOG.trace("[{}] - Response Body: {}", reqUuid, new String(bytes));
        } else {
          LOG.debug("[{}] - Response Body Length: {}", reqUuid, bytes.length);
        }
        LOG.debug("[{}] - Response Status: {}", reqUuid, httpResponse.getStatus());
        if (LOG.isTraceEnabled()) {
          Collection<String> headerNames = httpResponse.getHeaderNames();
          for (String h : headerNames) {
            LOG.trace("[{}] - Header: {}: {}", reqUuid, h, httpResponse.getHeader(h));
          }
        }
        if (bytes.length > 0) {
          try (ServletOutputStream responseOutputStream = httpResponse.getOutputStream()) {
            responseOutputStream.write(bytes);
          }
        }
      } else {
        webDavHandler.handle(httpRequest, httpResponse);
      }
    } finally {
      RequestLifeCycle.end();
      ExoContainerContext.setCurrentContainer(null);
    }
  }

}
