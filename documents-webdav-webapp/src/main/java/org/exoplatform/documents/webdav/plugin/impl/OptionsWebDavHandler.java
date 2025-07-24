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

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.ALLOW_METHODS;

import org.apache.http.HttpHeaders;
import org.springframework.stereotype.Component;

import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin;
import org.exoplatform.services.rest.ExtHttpHeaders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OptionsWebDavHandler extends WebDavHttpMethodPlugin {

  public OptionsWebDavHandler() {
    super("OPTIONS");
  }

  @Override
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException {
    httpResponse.setHeader(ExtHttpHeaders.ALLOW, ALLOW_METHODS);
    httpResponse.setHeader(ExtHttpHeaders.DAV, "1, 2, ordered-collections, access-control");
    httpResponse.setHeader(ExtHttpHeaders.DASL, documentWebDavService.getDaslValue());
    httpResponse.setHeader(ExtHttpHeaders.MSAUTHORVIA, "DAV");
    httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600,public");
    httpResponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
  }

}
