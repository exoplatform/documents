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
package org.exoplatform.documents.webdav.plugin.impl;

import org.springframework.stereotype.Component;

import org.exoplatform.documents.webdav.plugin.WebDavMethodHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

@Component
public class WebDavErrorHandler extends WebDavMethodHandler {

  public WebDavErrorHandler() {
    super("");
  }

  @Override
  @SneakyThrows
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    LOG.warn("WebDav Request with method {} and parameters {} not handled",
             httpRequest.getMethod(),
             httpRequest.getParameterMap() == null ? "" : httpRequest.getParameterMap().keySet());
    httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Handler MEthod not handled");
  }

}
