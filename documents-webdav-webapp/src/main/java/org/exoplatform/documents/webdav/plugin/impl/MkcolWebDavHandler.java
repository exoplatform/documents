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

import java.util.List;

import org.apache.http.HttpHeaders;
import org.springframework.stereotype.Component;

import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin;
import org.exoplatform.services.rest.ExtHttpHeaders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class MkcolWebDavHandler extends WebDavHttpMethodPlugin {

  public MkcolWebDavHandler() {
    super("MKCOL");
  }

  @Override
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException {
    String resourcePath = getResourcePath(httpRequest);
    List<String> lockTokens = getLockTokens(httpRequest);
    String folderType = httpRequest.getHeader(ExtHttpHeaders.FOLDER_NODETYPE);
    String contentNodeType = httpRequest.getHeader(ExtHttpHeaders.CONTENT_NODETYPE);
    String mixinTypes = httpRequest.getHeader(ExtHttpHeaders.CONTENT_MIXINTYPES);
    documentWebDavService.createFolder(resourcePath,
                                       folderType,
                                       contentNodeType,
                                       mixinTypes,
                                       lockTokens,
                                       httpRequest.getRemoteUser());
    httpResponse.setHeader(HttpHeaders.LOCATION, getResourceUri(httpRequest).toASCIIString());
    httpResponse.setStatus(HttpServletResponse.SC_CREATED);
  }

}
