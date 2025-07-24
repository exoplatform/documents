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

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.getStatusDescription;

import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.apache.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavItemOrder;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

@Component
public class OrderPatchWebDavHandler extends WebDavHttpMethodPlugin {

  public OrderPatchWebDavHandler() {
    super("ORDERPATCH");
  }

  @Override
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException {
    String resourcePath = getResourcePath(httpRequest);
    WebDavItemProperty body = parseRequestBodyAsWebDavItemProperty(httpRequest);
    List<WebDavItemOrder> members = getMembers(body);
    if (documentWebDavService.order(resourcePath,
                                    members,
                                    getLockTokens(httpRequest),
                                    httpRequest.getRemoteUser())) {
      httpResponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
    } else {
      writeResponse(resourcePath, members, httpResponse);
    }
  }

  @SneakyThrows
  private void writeResponse(String resourcePath, List<WebDavItemOrder> members, HttpServletResponse httpResponse) {
    try (OutputStream outputStream = httpResponse.getOutputStream()) {
      URI uri = getResourceUri(resourcePath);

      XMLStreamWriter xmlStreamWriter = XMLOutputFactory.newInstance()
                                                        .createXMLStreamWriter(outputStream, DEFAULT_XML_ENCODING);
      try {
        xmlStreamWriter.setNamespaceContext(documentWebDavService.getNamespaceContext());

        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("D", "multistatus", "DAV:");
        xmlStreamWriter.writeNamespace("D", "DAV:");
        xmlStreamWriter.writeAttribute("xmlns:b", "urn:uuid:c2f41010-65b3-11d1-a29f-00aa00c14882/");
        for (int i = 0; i < members.size(); i++) {
          WebDavItemOrder member = members.get(i);
          xmlStreamWriter.writeStartElement("DAV:", "response");
          xmlStreamWriter.writeStartElement("DAV:", "href");
          String href = String.format("%s/%s", uri.toASCIIString(), member.getSegment());
          xmlStreamWriter.writeCharacters(href);
          xmlStreamWriter.writeEndElement();
          xmlStreamWriter.writeStartElement("DAV:", "status");
          xmlStreamWriter.writeCharacters(getStatusDescription(member.getStatus()));
          xmlStreamWriter.writeEndElement();
          xmlStreamWriter.writeEndElement();
        }
        xmlStreamWriter.writeEndElement();
        xmlStreamWriter.writeEndDocument();
      } finally {
        xmlStreamWriter.close();
      }
      httpResponse.setHeader(HttpHeaders.CONTENT_TYPE, MimeTypeUtils.TEXT_XML_VALUE);
      httpResponse.setStatus(207); // MULTISTATUS exclusively used in WebDav
    }
  }

  private List<WebDavItemOrder> getMembers(WebDavItemProperty body) {
    List<WebDavItemOrder> members = new ArrayList<>();
    List<WebDavItemProperty> childs = body.getChildren();
    for (int i = 0; i < childs.size(); i++) {
      WebDavItemOrder member = new WebDavItemOrder(childs.get(i));
      members.add(member);
    }
    return members;
  }

}
