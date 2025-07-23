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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.springframework.stereotype.Component;

import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin;
import org.exoplatform.documents.webdav.util.PropertyWriteUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

@Component
public class SearchWebDavHandler extends WebDavHttpMethodPlugin {

  private static final Set<QName> REQUESTED_PROPERTIES = new HashSet<>();

  static {
    REQUESTED_PROPERTIES.add(new QName("DAV:", "displayname"));
    REQUESTED_PROPERTIES.add(new QName("DAV:", "resourcetype"));
    REQUESTED_PROPERTIES.add(new QName("DAV:", "creationdate"));
    REQUESTED_PROPERTIES.add(new QName("DAV:", "getlastmodified"));
    REQUESTED_PROPERTIES.add(new QName("DAV:", "getcontentlength"));
    REQUESTED_PROPERTIES.add(new QName("DAV:", "getcontenttype"));
  }

  public SearchWebDavHandler() {
    super("SEARCH");
  }

  @Override
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException {
    String resourcePath = getResourcePath(httpRequest);
    WebDavItemProperty body = parseRequestBodyAsWebDavItemProperty(httpRequest);

    List<WebDavItem> searchResultList = documentWebDavService.search(resourcePath,
                                                                     getQueryLanguage(body),
                                                                     getQuery(body),
                                                                     getBaseUrl(httpRequest),
                                                                     httpRequest.getRemoteUser());
    writeResponse(searchResultList, httpResponse);
  }

  @SneakyThrows
  public void writeResponse(List<WebDavItem> searchResultList, HttpServletResponse httpResponse) {
    try (OutputStream outputStream = httpResponse.getOutputStream()) {
      XMLStreamWriter xmlStreamWriter = XMLOutputFactory.newInstance()
                                                        .createXMLStreamWriter(outputStream, DEFAULT_XML_ENCODING);
      try {
        xmlStreamWriter.setNamespaceContext(documentWebDavService.getNamespaceContext());
        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("D", "multistatus", "DAV:");
        xmlStreamWriter.writeNamespace("D", "DAV:");
        xmlStreamWriter.writeAttribute("xmlns:b", "urn:uuid:c2f41010-65b3-11d1-a29f-00aa00c14882/");

        for (WebDavItem searchResult : searchResultList) {
          // if URI is new, and wasn't previously being written, then write it
          xmlStreamWriter.writeStartElement("DAV:", "response");
          xmlStreamWriter.writeStartElement("DAV:", "href");
          xmlStreamWriter.writeCharacters(searchResult.getIdentifier().toASCIIString());
          xmlStreamWriter.writeEndElement();
          PropertyWriteUtil.writePropStats(xmlStreamWriter, getRequestedPropertyStats(searchResult, REQUESTED_PROPERTIES));
          xmlStreamWriter.writeEndElement();
        }
        // D:multistatus
        xmlStreamWriter.writeEndElement();
        xmlStreamWriter.writeEndDocument();
      } finally {
        xmlStreamWriter.close();
      }
    }
  }

  public String getQueryLanguage(WebDavItemProperty body) {
    if (body.getChild(0).getName().getNamespaceURI().equals("DAV:")) {
      return body.getChild(0).getName().getLocalPart();
    } else {
      return null;
    }
  }

  public String getQuery(WebDavItemProperty body) {
    if (body.getChild(0).getName().getNamespaceURI().equals("DAV:")) {
      return body.getChild(0).getValue();
    } else {
      return null;
    }
  }

}
