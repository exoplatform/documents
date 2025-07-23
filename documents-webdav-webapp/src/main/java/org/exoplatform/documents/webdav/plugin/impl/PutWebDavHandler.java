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

import java.io.InputStream;
import java.util.List;

import org.apache.http.HttpHeaders;
import org.springframework.stereotype.Component;

import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.plugin.WebDavMethodHandler;
import org.exoplatform.services.rest.ExtHttpHeaders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

@Component
public class PutWebDavHandler extends WebDavMethodHandler {

  public PutWebDavHandler() {
    super("PUT");
  }

  @Override
  @SneakyThrows
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException {
    String resourcePath = getResourcePath(httpRequest);
    List<String> lockTokens = getLockTokens(httpRequest);
    String fileType = httpRequest.getHeader(ExtHttpHeaders.FILE_NODETYPE);
    String contentNodeType = httpRequest.getHeader(ExtHttpHeaders.CONTENT_NODETYPE);
    String mixinTypes = httpRequest.getHeader(ExtHttpHeaders.CONTENT_MIXINTYPES);
    String mediaType = httpRequest.getHeader(HttpHeaders.CONTENT_TYPE);
    InputStream inputStream = httpRequest.getInputStream();

    documentWebDavService.saveFile(resourcePath,
                                 fileType,
                                 contentNodeType,
                                 mediaType,
                                 mixinTypes,
                                 inputStream,
                                 lockTokens,
                                 httpRequest.getRemoteUser());
    httpResponse.setStatus(HttpServletResponse.SC_CREATED);
  }

}
