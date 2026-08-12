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

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GETCONTENTLENGTH;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GETCONTENTTYPE;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GETLASTMODIFIED;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.REQUEST_INCLUDED_PROPS;

import java.util.HashSet;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class HeadWebDavHandler extends WebDavHttpMethodPlugin {

  private static final Set<QName> REQUESTED_PROPERTIES = new HashSet<>();

  static {
    REQUESTED_PROPERTIES.add(GETLASTMODIFIED);
    REQUESTED_PROPERTIES.add(GETCONTENTTYPE);
    REQUESTED_PROPERTIES.add(GETCONTENTLENGTH);
  }

  public HeadWebDavHandler() {
    super("HEAD");
  }

  @Override
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException {
    String resourcePath = getResourcePath(httpRequest);
    WebDavItem resource = documentWebDavService.get(resourcePath,
                                                    REQUEST_INCLUDED_PROPS,
                                                    REQUESTED_PROPERTIES,
                                                    false,
                                                    0,
                                                    getBaseUrl(httpRequest),
                                                    httpRequest.getRemoteUser());
    setHeaderIfPresent(httpResponse, HttpHeaders.LAST_MODIFIED, resource.getProperty(GETLASTMODIFIED));
    if (resource.isFile()) {
      // A file has an actual content: mirror the type/length GET would return for it
      setHeaderIfPresent(httpResponse, HttpHeaders.CONTENT_TYPE, resource.getProperty(GETCONTENTTYPE));
      setHeaderIfPresent(httpResponse, HttpHeaders.CONTENT_LENGTH, resource.getProperty(GETCONTENTLENGTH));
    } else {
      // A folder has no content-type/content-length of its own: GET renders it as an HTML listing
      httpResponse.setHeader(HttpHeaders.CONTENT_TYPE, MimeTypeUtils.TEXT_HTML_VALUE);
    }
    httpResponse.setStatus(HttpServletResponse.SC_OK);
  }

  private void setHeaderIfPresent(HttpServletResponse httpResponse, String header, WebDavItemProperty property) {
    if (property != null) {
      httpResponse.setHeader(header, property.getValue());
    }
  }

}
