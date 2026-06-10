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
package org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.entity;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Mapping.Detection;
import org.springframework.data.elasticsearch.annotations.Setting;

import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;

@Data
@NoArgsConstructor
@Document(indexName = "webdav_items_cache", createIndex = true)
@Mapping(dateDetection = Detection.FALSE, numericDetection = Detection.FALSE)
@Setting(replicas = 0, shards = 1)
public class WebDavItemEntity {

  @Id
  private String                         webDavPath;

  @Field(type = FieldType.Keyword)
  private String                         jcrPath;

  @Field(type = FieldType.Keyword)
  private String                         parentWebDavPath;

  private String                         identifier;

  private Set<String>                    usernames;

  private boolean                        file;

  private boolean                        deep;

  private boolean                        modified;

  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  @Field(type = FieldType.Nested)
  private List<WebDavItemPropertyEntity> properties;

  public WebDavItemEntity(WebDavItem webDavItem) {
    this(webDavItem.getWebDavPath(),
         webDavItem.getJcrPath(),
         webDavItem.getIdentifier(),
         webDavItem.isFile(),
         webDavItem.getProperties());
  }

  public WebDavItemEntity(String webDavPath,
                          String jcrPath,
                          URI identifier,
                          boolean file,
                          List<WebDavItemProperty> properties) {
    if (webDavPath.endsWith("/")) {
      webDavPath = webDavPath.substring(0, webDavPath.length() - 1);
    }
    this.webDavPath = webDavPath;
    this.jcrPath = jcrPath;
    if (webDavPath.lastIndexOf("/") > 0) {
      this.parentWebDavPath = webDavPath.substring(0, webDavPath.lastIndexOf("/"));
    }
    this.identifier = identifier == null ? null : identifier.toASCIIString();
    this.file = file;
    if (properties != null) {
      this.properties = properties.stream().map(WebDavItemPropertyEntity::new).toList();
    }
  }

  @SneakyThrows
  public WebDavItem toWebDavItem() {
    return new WebDavItem(webDavPath,
                          jcrPath,
                          identifier == null ? null : new URI(identifier),
                          file,
                          properties == null ? null :
                                             properties.stream().map(WebDavItemPropertyEntity::toWebDavItemProperty).toList(),
                          null);
  }

}
