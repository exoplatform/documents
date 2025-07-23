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

import org.springframework.stereotype.Component;

import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.plugin.WebDavMethodHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * WebDAV ACL method according to protocol extension - Access Control Protocol:
 * RFC3744 More details here:
 * <a href='http://www.webdav.org/specs/rfc3744.html'>Web Distributed Authoring
 * and Versioning (WebDAV) Access Control Protocol</a>
 */
@Component
public class AclWebDavHandler extends WebDavMethodHandler {

  public AclWebDavHandler() {
    super("ACL");
  }

  @Override
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException {
    String path = getResourcePath(httpRequest);
    List<String> lockTokens = getLockTokens(httpRequest);
    WebDavItemProperty hierarchicalProperty = parseRequestBodyAsWebDavItemProperty(httpRequest);

    documentWebDavService.changeAcl(path,
                                    hierarchicalProperty,
                                    lockTokens,
                                    httpRequest.getRemoteUser());

    httpResponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
  }

}
