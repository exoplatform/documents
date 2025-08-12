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

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.ALLOWED_REQUEST_PROP_TYPES;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.DAV_ALLPROP_INCLUDE;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.REQUEST_SINGLE_PROP_NAME;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.getStatusDescription;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.plugin.WebDavHttpMethodPlugin;
import org.exoplatform.documents.webdav.util.PropertyWriteUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

@Component
public class PropFindWebDavHandler extends WebDavHttpMethodPlugin {

  public PropFindWebDavHandler() {
    super("PROPFIND");
  }

  @Override
  public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws WebDavException {
    WebDavItemProperty body = parseRequestBodyAsWebDavItemProperty(httpRequest);
    Set<QName> requestedPropertyNames = getRequestPropertyNames(body);

    String resourcePath = getResourcePath(httpRequest);
    int depth = getDepthInt(httpRequest);

    String propRequestType = getRequestPropertyType(body);
    if (!ALLOWED_REQUEST_PROP_TYPES.contains(propRequestType)) {
      httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    boolean requestPropertyNamesOnly = REQUEST_SINGLE_PROP_NAME.equals(propRequestType);
    WebDavItem resource = documentWebDavService.get(resourcePath,
                                                    propRequestType,
                                                    requestedPropertyNames,
                                                    requestPropertyNamesOnly,
                                                    depth,
                                                    getBaseUrl(httpRequest),
                                                    httpRequest.getRemoteUser());
    writeResponse(resource, requestedPropertyNames, requestPropertyNamesOnly, depth, httpResponse);
  }

  @SneakyThrows
  public void writeResponse(WebDavItem rootResource,
                            Set<QName> requestedPropertyNames,
                            boolean requestPropertyNamesOnly,
                            int depth,
                            HttpServletResponse httpResponse) {
    try (OutputStream outputStream = httpResponse.getOutputStream()) {
      XMLStreamWriter xmlStreamWriter = XMLOutputFactory.newInstance()
                                                        .createXMLStreamWriter(outputStream, DEFAULT_XML_ENCODING);
      try {
        xmlStreamWriter.setNamespaceContext(documentWebDavService.getNamespaceContext());
        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("D", "multistatus", "DAV:");
        xmlStreamWriter.writeNamespace("D", "DAV:");
        xmlStreamWriter.writeAttribute("xmlns:b", "urn:uuid:c2f41010-65b3-11d1-a29f-00aa00c14882/");
        traverseResources(rootResource,
                          requestedPropertyNames,
                          requestPropertyNamesOnly,
                          0,
                          depth,
                          xmlStreamWriter);
        // D:multistatus
        xmlStreamWriter.writeEndElement();
        xmlStreamWriter.writeEndDocument();
      } finally {
        xmlStreamWriter.close();
      }
    }
    httpResponse.setHeader(HttpHeaders.CONTENT_TYPE, MimeTypeUtils.TEXT_XML_VALUE);
    httpResponse.setStatus(207); // MULTISTATUS exclusively used in WebDav
  }

  @SneakyThrows
  private void traverseResources(WebDavItem resource,
                                 Set<QName> requestedPropertyNames,
                                 boolean requestPropertyNamesOnly,
                                 int counter,
                                 int depth,
                                 XMLStreamWriter xmlStreamWriter) {
    xmlStreamWriter.writeStartElement("DAV:", "response");
    xmlStreamWriter.writeStartElement("DAV:", "href");
    String href = resource.getIdentifier().toASCIIString();
    if (!resource.isFile() && !href.endsWith("/")) {
      xmlStreamWriter.writeCharacters(href + "/");
    } else {
      xmlStreamWriter.writeCharacters(href);
    }
    xmlStreamWriter.writeEndElement();

    PropertyWriteUtil.writePropStats(xmlStreamWriter,
                                     getPropStats(resource,
                                                  requestedPropertyNames,
                                                  requestPropertyNamesOnly));
    xmlStreamWriter.writeEndElement();
    if (CollectionUtils.isNotEmpty(resource.getChildren()) && counter < depth) {
      for (WebDavItem childResource : resource.getChildren()) {
        traverseResources(childResource,
                          requestedPropertyNames,
                          requestPropertyNamesOnly,
                          counter + 1,
                          depth,
                          xmlStreamWriter);
      }
    }
  }

  public Map<String, Collection<WebDavItemProperty>> getPropStats(WebDavItem resource,
                                                                  Set<QName> requestedPropertyNames,
                                                                  boolean propertyNamesOnly) {
    Map<String, Collection<WebDavItemProperty>> propStats = new HashMap<>();
    if (requestedPropertyNames == null) {
      String statname = getStatusDescription(HTTPStatus.OK);
      propStats.put(statname, resource.getProperties(propertyNamesOnly));
    } else {
      for (QName name : requestedPropertyNames) {
        if (name.equals(DAV_ALLPROP_INCLUDE)) {
          String statname = getStatusDescription(HTTPStatus.OK);
          propStats.put(statname, resource.getProperties(propertyNamesOnly));
        } else {
          WebDavItemProperty result = resource.getProperty(name);
          if (result == null) {
            String statname = getStatusDescription(HTTPStatus.NOT_FOUND);
            propStats.computeIfAbsent(statname, k -> new ArrayList<>()).add(new WebDavItemProperty(name));
          } else {
            String statname = getStatusDescription(HTTPStatus.OK);
            propStats.computeIfAbsent(statname, k -> new ArrayList<>()).add(result);
          }
        }
      }
    }
    return propStats;
  }

}
