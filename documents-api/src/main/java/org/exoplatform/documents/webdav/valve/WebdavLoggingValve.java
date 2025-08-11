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
package org.exoplatform.documents.webdav.valve;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import jakarta.servlet.ServletException;

public class WebdavLoggingValve extends ValveBase {

  private static final Log LOG = ExoLogger.getLogger(WebdavLoggingValve.class);

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    if (!LOG.isDebugEnabled()) {
      getNext().invoke(request, response);
    } else {
      UUID reqUuid = UUID.randomUUID();
      try { // NOSONAR
        LOG.debug("[{}] URI: {} - Method {}", reqUuid, request.getRequestURI(), request.getMethod());
        getNext().invoke(request, response);
      } finally {
        LOG.debug("[{}] - Response Status: {}", reqUuid, response.getStatus());
        Collection<String> headerNames = response.getHeaderNames();
        for (String h : headerNames) {
          LOG.trace("[{}] - Response Header: {}: {}", reqUuid, h, response.getHeader(h));
        }
      }
    }
  }
}
