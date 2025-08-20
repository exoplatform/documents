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
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.apache.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin;
import org.exoplatform.documents.webdav.util.PropertyWriteUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

@Component
public class PropPatchWebDavHandler extends WebDavHttpMethodPlugin {

  public PropPatchWebDavHandler() {
    super("PROPPATCH");
  }

  @Override
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException {
    String resourcePath = getResourcePath(httpRequest);
    WebDavItemProperty body = parseRequestBodyAsWebDavItemProperty(httpRequest);

    Map<String, Collection<WebDavItemProperty>> result = documentWebDavService.saveProperties(resourcePath,
                                                                                              getPropertiesToSave(body),
                                                                                              getPropertiesToRemove(body),
                                                                                              getLockTokens(httpRequest),
                                                                                              httpRequest.getRemoteUser());
    writeResponse(result, getResourceUri(httpRequest), httpResponse);
  }

  @SneakyThrows
  private void writeResponse(Map<String, Collection<WebDavItemProperty>> result,
                             URI uri,
                             HttpServletResponse httpResponse) {
    httpResponse.setHeader(HttpHeaders.CONTENT_TYPE, MimeTypeUtils.TEXT_XML_VALUE);
    httpResponse.setStatus(207); // MULTISTATUS exclusively used in WebDav
    try (OutputStream outputStream = httpResponse.getOutputStream()) {
      XMLStreamWriter xmlStreamWriter = XMLOutputFactory.newInstance()
                                                        .createXMLStreamWriter(outputStream, DEFAULT_XML_ENCODING);
      try {
        xmlStreamWriter.setNamespaceContext(documentWebDavService.getNamespaceContext());
        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("D", "multistatus", "DAV:");
        xmlStreamWriter.writeNamespace("D", "DAV:");
        xmlStreamWriter.writeAttribute("xmlns:b", "urn:uuid:c2f41010-65b3-11d1-a29f-00aa00c14882/");

        xmlStreamWriter.writeStartElement("DAV:", "response");
        xmlStreamWriter.writeStartElement("DAV:", "href");
        xmlStreamWriter.writeCharacters(uri.toASCIIString());
        xmlStreamWriter.writeEndElement();
        PropertyWriteUtil.writePropStats(xmlStreamWriter, result);
        xmlStreamWriter.writeEndElement();

        // D:multistatus
        xmlStreamWriter.writeEndElement();
        xmlStreamWriter.writeEndDocument();
      } finally {
        xmlStreamWriter.close();
      }
    }
  }

  private List<WebDavItemProperty> getPropertiesToSave(WebDavItemProperty body) {
    WebDavItemProperty requestPropSave = body.getChild(new QName("DAV:", "set"));
    return requestPropSave == null ? Collections.emptyList() :
                                   requestPropSave.getChild(new QName("DAV:", "prop"))
                                                  .getChildren();
  }

  private List<WebDavItemProperty> getPropertiesToRemove(WebDavItemProperty body) {
    WebDavItemProperty requestPropRemove = body.getChild(new QName("DAV:", "remove"));
    return requestPropRemove == null ? Collections.emptyList() :
                                     requestPropRemove.getChild(new QName("DAV:", "prop"))
                                                      .getChildren();
  }

}
