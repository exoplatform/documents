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

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

  /**
   * The per-user half of this row's properties — see
   * {@link WebDavItemUserPropertiesEntity}. Its usernames are also the row's
   * "who has already read this item" set, exposed as {@link #getUsernames()}.
   */
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  @Field(type = FieldType.Object)
  private List<WebDavItemUserPropertiesEntity> userProperties;

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
         webDavItem.isFile(),
         webDavItem.getProperties());
  }

  public WebDavItemEntity(String webDavPath,
                          String jcrPath,
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
    this.file = file;
    if (properties != null) {
      this.properties = properties.stream().map(WebDavItemPropertyEntity::new).toList();
    }
  }

  /**
   * @return the users for whom this row already holds computed properties. A
   *         user absent from it has never had this item computed against their
   *         own JCR session, so the row must be refreshed before it is served
   *         to them ({@code CachedJcrWebDavService#isMustRefreshItem}).
   */
  public Set<String> getUsernames() {
    return userProperties == null ? Set.of()
                                  : userProperties.stream()
                                                  .map(WebDavItemUserPropertiesEntity::getUsername)
                                                  .collect(Collectors.toSet());
  }

  /**
   * @param username the reading user
   * @return that user's own properties for this item, empty when the row does
   *         not hold them yet
   */
  public List<WebDavItemPropertyEntity> getUserProperties(String username) {
    return userProperties == null ? List.of()
                                  : userProperties.stream()
                                                  .filter(u -> Objects.equals(u.getUsername(), username))
                                                  .findFirst()
                                                  .map(WebDavItemUserPropertiesEntity::getProperties)
                                                  .orElseGet(List::of);
  }

  /**
   * @return the cached item, with a <b>null</b> identifier: the absolute href
   *         is not persisted here and is rebuilt by
   *         {@code CachedJcrWebDavService} from the base URI of the current
   *         request. This row is keyed by the drive-relative WebDAV path only,
   *         so it is shared by the drive-list mount and the single-drive mount,
   *         which do not have the same base URI.
   */
  public WebDavItem toWebDavItem() {
    return new WebDavItem(webDavPath,
                          jcrPath,
                          null,
                          file,
                          properties == null ? null :
                                             properties.stream().map(WebDavItemPropertyEntity::toWebDavItemProperty).toList(),
                          null);
  }

}
