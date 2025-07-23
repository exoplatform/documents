/*
 * Copyright (C) 2009 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.documents.webdav.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.collections4.CollectionUtils;

import org.exoplatform.documents.webdav.model.WebDavItemProperty;

public class PropertyWriteUtil {

  private PropertyWriteUtil() {
    // Utils class
  }

  /**
   * Writes the statuses of properties into XML.
   * 
   * @param xmlStreamWriter XML writer
   * @param propStatuses properties statuses
   * @throws XMLStreamException {@link XMLStreamException}
   */
  public static void writePropStats(XMLStreamWriter xmlStreamWriter,
                                    Map<String, Collection<WebDavItemProperty>> propStatuses) throws XMLStreamException {
    for (Map.Entry<String, Collection<WebDavItemProperty>> stat : propStatuses.entrySet()) {
      xmlStreamWriter.writeStartElement("DAV:", "propstat");

      xmlStreamWriter.writeStartElement("DAV:", "prop");
      for (WebDavItemProperty prop : propStatuses.get(stat.getKey())) {
        writeProperty(xmlStreamWriter, prop);
      }
      xmlStreamWriter.writeEndElement();

      xmlStreamWriter.writeStartElement("DAV:", "status");
      xmlStreamWriter.writeCharacters(stat.getKey());
      xmlStreamWriter.writeEndElement();

      // D:propstat
      xmlStreamWriter.writeEndElement();
    }
  }

  /**
   * Writes the statuses of property into XML.
   * 
   * @param xmlStreamWriter XML writer
   * @param prop property
   * @throws XMLStreamException {@link XMLStreamException}
   */
  public static void writeProperty(XMLStreamWriter xmlStreamWriter, WebDavItemProperty prop) throws XMLStreamException { // NOSONAR
    if (prop == null) {
      return;
    }
    String uri = prop.getName().getNamespaceURI();
    String prefix = xmlStreamWriter.getNamespaceContext().getPrefix(uri);
    if (prefix == null) {
      prefix = "";
    }
    String local = prop.getName().getLocalPart();
    if (prop.getValue() == null) {
      if (CollectionUtils.isNotEmpty(prop.getChildren())) {
        xmlStreamWriter.writeStartElement(prefix, local, uri);
        if (!uri.equalsIgnoreCase("DAV:")) {
          xmlStreamWriter.writeNamespace(prefix, uri);
        }
        writeAttributes(xmlStreamWriter, prop);
        for (int i = 0; i < prop.getChildren().size(); i++) {
          WebDavItemProperty property = prop.getChildren().get(i);
          writeProperty(xmlStreamWriter, property);
        }
        xmlStreamWriter.writeEndElement();
      } else {
        xmlStreamWriter.writeEmptyElement(prefix, local, uri);
        if (!uri.equalsIgnoreCase("DAV:")) {
          xmlStreamWriter.writeNamespace(prefix, uri);
        }
        writeAttributes(xmlStreamWriter, prop);
      }
    } else {
      xmlStreamWriter.writeStartElement(prefix, local, uri);
      if (!uri.equalsIgnoreCase("DAV:")) {
        xmlStreamWriter.writeNamespace(prefix, uri);
      }
      writeAttributes(xmlStreamWriter, prop);
      xmlStreamWriter.writeCharacters(prop.getValue());
      xmlStreamWriter.writeEndElement();
    }
  }

  /**
   * Writes property attributes into XML.
   * 
   * @param xmlStreamWriter XML writer
   * @param property property
   * @throws XMLStreamException {@link XMLStreamException}
   */
  public static void writeAttributes(XMLStreamWriter xmlStreamWriter, WebDavItemProperty property)
                                                                                                   throws XMLStreamException {
    Map<String, String> attributes = property.getAttributes();
    Iterator<String> keyIter = attributes.keySet().iterator();
    while (keyIter.hasNext()) {
      String attrName = keyIter.next();
      String attrValue = attributes.get(attrName);
      xmlStreamWriter.writeAttribute(attrName, attrValue);
    }
  }
}
