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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.utils.Tools;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WebDavItemProperty {

  protected QName                    name;

  protected String                   value;

  protected Map<String, String>      attributes = new HashMap<>();

  protected List<WebDavItemProperty> children   = new ArrayList<>();

  public WebDavItemProperty(QName name) {
    this(name, null);
  }

  public WebDavItemProperty(String name, String value) {
    String[] tmp = name.split(":");
    if (tmp.length > 1) {
      this.name = new QName(tmp[0], tmp[1]);
    } else {
      this.name = new QName(tmp[0]);
    }
    this.value = value;
  }

  public WebDavItemProperty(QName name, String value) {
    this.name = name;
    this.value = value;
  }

  public WebDavItemProperty(QName name, Calendar dateValue, String formatPattern) {
    this(name, null);
    SimpleDateFormat dateFormat = new SimpleDateFormat(formatPattern, Locale.ENGLISH);
    dateFormat.setTimeZone(Tools.getTimeZone("GMT"));
    this.value = dateFormat.format(dateValue.getTime());
  }

  public String getStringName() {
    if (StringUtils.isNotBlank(name.getPrefix())) {
      return String.format("%s:%s", name.getPrefix(), name.getLocalPart());
    } else {
      return name.getLocalPart();
    }
  }

  public WebDavItemProperty addChild(WebDavItemProperty prop) {
    children.add(prop);
    return prop;
  }

  public WebDavItemProperty getChild(QName name) {
    for (WebDavItemProperty child : children) {
      if (child.getName().equals(name))
        return child;
    }
    return null;
  }

  public WebDavItemProperty getChild(int index) {
    return children.get(index);
  }

  public void setAttribute(String attributeName, String attributeValue) {
    attributes.put(attributeName, attributeValue);
  }

  public String getAttribute(String attributeName) {
    return attributes.get(attributeName);
  }

}
