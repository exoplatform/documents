/**
 * Copyright (C) 2025 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.storage.jcr.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;

public class JcrNamespaceContext implements NamespaceContext {

  private Map<String, String> prefixes   = new HashMap<>();

  private Map<String, String> namespaces = new HashMap<>();

  public JcrNamespaceContext(Map<String, String> prefixes, Map<String, String> namespaces) {
    this.prefixes = prefixes;
    this.namespaces = namespaces;
  }

  public static String createName(QName qName) {
    return qName.getPrefix() + ":" + qName.getLocalPart();
  }

  public QName createQName(String strName) {
    String[] parts = strName.split(":");
    if (parts.length > 1) {
      return new QName(getNamespaceURI(parts[0]), parts[1], parts[0]);
    } else {
      return new QName(parts[0]);
    }
  }

  public String getNamespaceURI(String prefix) {
    return namespaces.get(prefix);
  }

  public String getPrefix(String namespaceURI) {
    return prefixes.get(namespaceURI);
  }

  public Iterator<String> getPrefixes(String namespaceURI) {
    String prefix = getPrefix(namespaceURI);
    return prefix == null ? Collections.emptyIterator() : Collections.singleton(prefix).iterator();
  }

}
