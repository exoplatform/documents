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
package org.exoplatform.documents.storage.jcr.cache.elasticsearch.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;

import org.exoplatform.documents.webdav.model.WebDavItemProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebDavItemPropertyEntity {

  protected String                         name;

  protected String                         value;

  protected Map<String, String>            attributes = new HashMap<>();

  @EqualsAndHashCode.Exclude
  protected List<WebDavItemPropertyEntity> children   = new ArrayList<>();

  public WebDavItemPropertyEntity(WebDavItemProperty property) {
    this.name = String.format("%s:%s", property.getName().getNamespaceURI(), property.getName().getLocalPart());
    this.value = property.getValue();
    this.attributes = property.getAttributes();
    if (CollectionUtils.isNotEmpty(property.getChildren())) {
      this.children = property.getChildren().stream().map(WebDavItemPropertyEntity::new).toList();
    }
  }

  public WebDavItemProperty toWebDavItemProperty() {
    WebDavItemProperty webDavItemProperty = new WebDavItemProperty(name, value);
    webDavItemProperty.setAttributes(attributes);
    if (children != null) {
      webDavItemProperty.setChildren(children.stream().map(WebDavItemPropertyEntity::toWebDavItemProperty).toList());
    }
    return webDavItemProperty;
  }

}
