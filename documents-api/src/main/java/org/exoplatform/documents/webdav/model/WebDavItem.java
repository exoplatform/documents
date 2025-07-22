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
package org.exoplatform.documents.webdav.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WebDavItem {

  private String                   webDavPath;

  private String                   jcrPath;

  private URI                      identifier;

  private boolean                  file;

  private List<WebDavItemProperty> properties;

  private List<WebDavItem>         children;

  public WebDavItemProperty getProperty(QName name) {
    return properties == null ? null :
                              properties.stream()
                                        .filter(p -> name.equals(p.getName()))
                                        .findFirst()
                                        .orElse(null);
  }

  public List<WebDavItemProperty> getProperties(boolean namesOnly) { // NOSONAR
    return properties;
  }

  public void addProperty(WebDavItemProperty property) {
    if (properties == null) {
      properties = new ArrayList<>();
    }
    properties.add(property);
  }

  public void addChild(WebDavItem webDavItem) {
    if (children == null) {
      children = new ArrayList<>();
    }
    children.add(webDavItem);
  }

}
