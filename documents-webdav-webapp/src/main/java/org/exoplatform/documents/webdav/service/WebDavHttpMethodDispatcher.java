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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.PathNotFoundException;
import javax.jcr.lock.LockException;

import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin;
import org.exoplatform.documents.webdav.plugin.impl.WebDavErrorHandler;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

@Component
public class WebDavHttpMethodDispatcher {

  protected static final Log                  LOG = ExoLogger.getLogger(WebDavHttpMethodDispatcher.class);

  @Autowired
  private List<WebDavHttpMethodPlugin>        handlers;

  @Autowired
  private WebDavErrorHandler                  errorHandler;

  private Map<String, WebDavHttpMethodPlugin> handlersByMethod;

  @PostConstruct
  protected void init() {
    handlersByMethod = handlers.stream()
                               .collect(Collectors.toMap(WebDavHttpMethodPlugin::getMethod,
                                                         Function.identity()));
  }

  /**
   * Handles All WebDav Requests. A main operations which will dispatch the
   * request into the adequate {@link WebDavHttpMethodPlugin}
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
      handleWebDavException(httpRequest, httpResponse, e);
    } catch (Exception e) {
      WebDavException webDavException = getWebDavException(e);
      if (webDavException == null) {
        LOG.warn("Unknown error while handling WebDav method '{}' and URI '{}'",
                 httpRequest.getMethod(),
                 httpRequest.getRequestURI(),
                 e);
        httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
      } else {
        handleWebDavException(httpRequest, httpResponse, webDavException);
      }
    }
  }

  private void handleWebDavException(HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse,
                                     WebDavException e) throws IOException {
    if (e.getHttpError() != HttpStatus.SC_NOT_FOUND
        && e.getHttpError() != HttpStatus.SC_FORBIDDEN) {
      LOG.warn("Bad Request sent to WebDav using method '{}' and URI '{}'",
               httpRequest.getMethod(),
               httpRequest.getRequestURI(),
               e);
    } else if (LOG.isDebugEnabled()) {
      // not an incident, but a refused request now leaves no other trace
      LOG.debug("WebDav method '{}' on URI '{}' refused with status {}",
                httpRequest.getMethod(),
                httpRequest.getRequestURI(),
                e.getHttpError(),
                e);
    }
    httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
    httpResponse.sendError(e.getHttpError(), e.getMessage());
  }

  /**
   * Finds the HTTP status a failure should carry, by walking the cause chain for
   * a {@link WebDavException} and, failing that, for one of the repository's own
   * exceptions.
   * <p>
   * The repository refuses an operation the user has no right to with a JCR
   * {@link AccessDeniedException}, which carries no
   * <code>WebDavException</code> anywhere in its chain — so before this mapping
   * existed it fell through to the generic handler and the user renaming a file
   * they may not write got a <b>500 plus a WARN</b> rather than a 403. That is
   * the wrong answer twice over: it tells the client the server broke rather
   * than that the request was refused, and it files a normal, expected refusal
   * as an incident in the log (<code>backend-spring.md</code> §5: existence
   * -&gt; 404, ACL -&gt; 403, validation -&gt; 400, and do not log an expected
   * exception as an error).
   * <p>
   * Mapping here rather than in each verb handler covers every verb at once —
   * the same refusal reaches this point from MOVE, PUT, DELETE, MKCOL and COPY.
   *
   * @param throwable the failure a handler raised
   * @return the exception to answer with, or null to fall back to a 500
   */
  private WebDavException getWebDavException(Throwable throwable) {
    // Two passes, not one interleaved walk: a WebDavException anywhere in the
    // chain is a status the code chose deliberately and outranks one inferred
    // from a repository failure wrapping it, however far down it sits. Testing
    // both at each level would let the outermost match win and mask it.
    for (Throwable e = throwable; e != null; e = e.getCause()) {
      if (e instanceof WebDavException webDavException) {
        return webDavException;
      }
    }
    for (Throwable e = throwable; e != null; e = e.getCause()) {
      Integer httpStatus = getJcrHttpStatus(e);
      if (httpStatus != null) {
        return new WebDavException(httpStatus, e.getMessage());
      }
    }
    return null;
  }

  /**
   * @param e a failure from the repository
   * @return the HTTP status it means, or null when it is not one the contract
   *         covers — in which case it really is a 500
   */
  private Integer getJcrHttpStatus(Throwable e) {
    if (e instanceof AccessDeniedException) {
      return HttpStatus.SC_FORBIDDEN;
    } else if (e instanceof PathNotFoundException || e instanceof ItemNotFoundException) {
      return HttpStatus.SC_NOT_FOUND;
    } else if (e instanceof LockException) {
      return HttpStatus.SC_LOCKED;
    } else if (e instanceof ItemExistsException) {
      return HttpStatus.SC_CONFLICT;
    } else {
      return null;
    }
  }

}
