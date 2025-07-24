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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.SneakyThrows;

@RestController("drives")
@Tag(name = "/webdav/drives/", description = "Managing WebDav Files")
@CrossOrigin("*")
public class WebDavRest {

  protected static final Log         LOG = ExoLogger.getLogger(WebDavRest.class);

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
    if (httpRequest.getRequestURI().contains(CONTEXT_PATH)) {
      ExoContainerContext.setCurrentContainer(container);
      RequestLifeCycle.begin(container);
      try { // NOSONAR
        if (LOG.isDebugEnabled()) {
          String reqUuid = UUID.randomUUID().toString();
          LOG.debug("[{}] Request: {} - {}", reqUuid, httpRequest.getMethod(), httpRequest.getRequestURI());
          if (LOG.isTraceEnabled()) {
            Enumeration<String> headerNames = httpRequest.getHeaderNames();
            while (headerNames.hasMoreElements()) {
              String h = headerNames.nextElement();
              LOG.trace("[{}] - Request Header: {}: {}", reqUuid, h, httpRequest.getHeader(h));
            }
          }
          ByteArrayInputStream arrayInputStream = null;
          try (InputStream inputStream = httpRequest.getInputStream()) {
            arrayInputStream = new ByteArrayInputStream(IOUtils.toByteArray(inputStream));
          }

          if (arrayInputStream.available() > 0
              && (StringUtils.contains(httpRequest.getContentType(), "text/")
                  || StringUtils.equals(httpRequest.getContentType(), "application/xml")
                  || StringUtils.equals(httpRequest.getContentType(), "application.json/"))) {
            byte[] bytes = arrayInputStream.readAllBytes();
            arrayInputStream.reset();
            LOG.trace("[{}] + Request Body: {}", reqUuid, new String(bytes));
          }

          ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
          webDavMethodDispatcher.handle(newHttpServletRequestWrapper(httpRequest, arrayInputStream),
                                        newHttpServletResponseWrapper(httpResponse, arrayOutputStream));
          LOG.debug("[{}] Response Status: {}", reqUuid, httpResponse.getStatus());
          if (LOG.isTraceEnabled()) {
            Collection<String> headerNames = httpResponse.getHeaderNames();
            for (String h : headerNames) {
              LOG.trace("[{}] + Response Header: {}: {}", reqUuid, h, httpResponse.getHeader(h));
            }
          }
          byte[] bytes = arrayOutputStream.toByteArray();
          if (LOG.isTraceEnabled()
              && StringUtils.contains(httpResponse.getContentType(), "text/")
              && bytes.length > 0) {
            LOG.trace("[{}] + Response Body: {}", reqUuid, new String(bytes));
          }
          if (bytes.length > 0) {
            try (ServletOutputStream responseOutputStream = httpResponse.getOutputStream()) {
              responseOutputStream.write(bytes);
            }
          }
        } else {
          webDavMethodDispatcher.handle(httpRequest, httpResponse);
        }
      } finally {
        RequestLifeCycle.end();
        ExoContainerContext.setCurrentContainer(null);
      }
    } else {
      try {
        httpResponse.sendRedirect(CONTEXT_PATH);
      } catch (IOException e) {
        LOG.error("Error while redirecting to context path {}", CONTEXT_PATH, e);
      }
    }
  }

  private HttpServletRequestWrapper newHttpServletRequestWrapper(HttpServletRequest httpRequest,
                                                                 ByteArrayInputStream arrayInputStream) {
    return new HttpServletRequestWrapper(httpRequest) {
      @Override
      public ServletInputStream getInputStream() throws IOException {
        return newServletInputStreamWrapper(arrayInputStream);
      }

    };
  }

  private HttpServletResponseWrapper newHttpServletResponseWrapper(HttpServletResponse httpResponse,
                                                                   ByteArrayOutputStream arrayOutputStream) {
    return new HttpServletResponseWrapper(httpResponse) {
      @Override
      public ServletOutputStream getOutputStream() throws IOException {
        return newServletOutputStreamWrapper(arrayOutputStream);
      }
    };
  }

  private ServletOutputStream newServletOutputStreamWrapper(ByteArrayOutputStream arrayOutputStream) {
    return new ServletOutputStream() {

      @Override
      public void write(int b) throws IOException {
        arrayOutputStream.write(b);
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setWriteListener(WriteListener writeListener) {
        // Noop
      }

    };
  }

  private ServletInputStream newServletInputStreamWrapper(ByteArrayInputStream arrayInputStream) {
    return new ServletInputStream() {

      @Override
      public boolean isFinished() {
        return arrayInputStream.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {
        // Noop
      }

      @Override
      public int read() throws IOException {
        return arrayInputStream.read();
      }

      @Override
      public int available() throws IOException {
        return arrayInputStream.available();
      }
    };
  }
}
