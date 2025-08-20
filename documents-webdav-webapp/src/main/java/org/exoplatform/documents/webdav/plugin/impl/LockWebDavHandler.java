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

import java.io.OutputStream;

import javax.ws.rs.core.HttpHeaders;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.model.WebDavLockResponse;
import org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin;
import org.exoplatform.documents.webdav.util.PropertyWriteUtil;
import org.exoplatform.services.rest.ExtHttpHeaders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

@Component
public class LockWebDavHandler extends WebDavHttpMethodPlugin {

  @Value("${webdav.lockTimeout:86400}")
  private int lockTimeout;

  public LockWebDavHandler() {
    super("LOCK");
  }

  @Override
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException {
    WebDavItemProperty itemProperty = parseRequestBodyAsWebDavItemProperty(httpRequest);

    WebDavLockResponse lockResponse = documentWebDavService.lock(getResourcePath(httpRequest),
                                                                 getDepthInt(httpRequest),
                                                                 lockTimeout,
                                                                 itemProperty == null,
                                                                 getLockTokens(httpRequest),
                                                                 httpRequest.getRemoteUser());

    writeResponse(String.format("%s:%s", OPAQUE_LOCK_TOKEN, lockResponse.getToken()),
                  lockResponse.getOwner(),
                  itemProperty == null,
                  httpResponse);
  }

  @SneakyThrows
  public void writeResponse(String lockToken,
                            String lockOwner,
                            boolean bodyIsEmpty,
                            HttpServletResponse httpResponse) {
    httpResponse.setHeader(HttpHeaders.CONTENT_TYPE, MimeTypeUtils.TEXT_XML_VALUE);
    if (bodyIsEmpty) {
      httpResponse.setHeader(ExtHttpHeaders.LOCKTOKEN, "<" + lockToken + ">");
    }
    httpResponse.setStatus(HttpServletResponse.SC_OK);
    try (OutputStream outputStream = httpResponse.getOutputStream()) {
      XMLStreamWriter xmlStreamWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream, DEFAULT_XML_ENCODING);
      try {
        xmlStreamWriter.setNamespaceContext(documentWebDavService.getNamespaceContext());
        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("D", "prop", "DAV:");
        xmlStreamWriter.writeNamespace("D", "DAV:");
        WebDavItemProperty lockDiscovery = lockDiscovery(lockToken, lockOwner, lockTimeout);
        PropertyWriteUtil.writeProperty(xmlStreamWriter, lockDiscovery);
        xmlStreamWriter.writeEndElement();
        xmlStreamWriter.writeEndDocument();
      } finally {
        xmlStreamWriter.close();
      }
    }
  }

  public WebDavItemProperty lockDiscovery(String token, String lockOwner, long timeOut) {
    WebDavItemProperty lockDiscovery = new WebDavItemProperty(new QName("DAV:", "lockdiscovery"));
    WebDavItemProperty activeLock = lockDiscovery.addChild(new WebDavItemProperty(new QName("DAV:", "activelock")));
    WebDavItemProperty lockType = activeLock.addChild(new WebDavItemProperty(new QName("DAV:", "locktype")));
    lockType.addChild(new WebDavItemProperty(new QName("DAV:", "write")));
    WebDavItemProperty lockScope = activeLock.addChild(new WebDavItemProperty(new QName("DAV:", "lockscope")));
    lockScope.addChild(new WebDavItemProperty(new QName("DAV:", "exclusive")));
    WebDavItemProperty depth = activeLock.addChild(new WebDavItemProperty(new QName("DAV:", "depth")));
    depth.setValue(INFINITY_DEPTH);
    if (lockOwner != null) {
      WebDavItemProperty owner = activeLock.addChild(new WebDavItemProperty(new QName("DAV:", "owner")));
      owner.setValue(lockOwner);
    }
    WebDavItemProperty timeout = activeLock.addChild(new WebDavItemProperty(new QName("DAV:", "timeout")));
    timeout.setValue("Second-" + timeOut);
    if (token != null) {
      WebDavItemProperty lockToken = activeLock.addChild(new WebDavItemProperty(new QName("DAV:", "locktoken")));
      WebDavItemProperty lockHref = lockToken.addChild(new WebDavItemProperty(new QName("DAV:", "href")));
      lockHref.setValue(token);
    }
    return lockDiscovery;
  }

}
