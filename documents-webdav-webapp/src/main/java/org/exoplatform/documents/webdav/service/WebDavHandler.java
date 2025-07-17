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
package org.exoplatform.documents.webdav.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.plugin.WebDavMethodHandler;
import org.exoplatform.documents.webdav.plugin.impl.WebDavErrorHandler;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

@Service
public class WebDavHandler {

  protected static final Log               LOG = ExoLogger.getLogger(WebDavHandler.class);

  @Autowired
  private List<WebDavMethodHandler>        handlers;

  @Autowired
  private WebDavErrorHandler               errorHandler;

  private Map<String, WebDavMethodHandler> handlersByMethod;

  @PostConstruct
  protected void init() {
    handlersByMethod = handlers.stream()
                               .collect(Collectors.toMap(WebDavMethodHandler::getMethod,
                                                         Function.identity()));
  }

  /**
   * Handles All WebDav Requests. A main operations which will dispatch the
   * request into the adequate {@link WebDavMethodHandler}
   * 
   * @param httpRequest {@link HttpServletRequest}
   * @param httpResponse {@link HttpServletResponse}
   */
  @SneakyThrows
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    try {
      handlersByMethod.getOrDefault(httpRequest.getMethod().toUpperCase(), errorHandler)
                      .handle(httpRequest, httpResponse);
    } catch (WebDavException e) {
      if (e.getHttpError() != HttpStatus.SC_NOT_FOUND
          && e.getHttpError() != HttpStatus.SC_FORBIDDEN) {
        LOG.warn("Bad Request sent to WebDav using method '{}' and URI '{}'",
                 httpRequest.getMethod(),
                 httpRequest.getRequestURI(),
                 e);
      }
      httpResponse.sendError(e.getHttpError(), e.getMessage());
    } catch (Exception e) {
      LOG.warn("Unknown error while handling WebDav method '{}' and URI '{}'",
               httpRequest.getMethod(),
               httpRequest.getRequestURI(),
               e);
      httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

}
