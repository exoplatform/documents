/**
 * Copyright (C) 2026 eXo Platform SAS
 *
 * This program is free software: you can redistribute it and/or modify
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
package org.exoplatform.documents.storage.jcr.webdav.entity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Mapping.Detection;
import org.springframework.data.elasticsearch.annotations.Setting;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@Data
@NoArgsConstructor
@Document(indexName = "webdav_item_path_mappings", createIndex = true)
@Mapping(dateDetection = Detection.FALSE, numericDetection = Detection.FALSE)
@Setting(replicas = 0, shards = 1)
public class WebDavPathMappingEntity {

  @Id
  private String  id;

  @Field(type = FieldType.Keyword)
  private String  identityId;

  @Field(type = FieldType.Keyword)
  private String  parentJcrPath;

  @Field(type = FieldType.Keyword)
  private String  visibleName;

  @Field(type = FieldType.Keyword)
  private String  normalizedVisibleName;

  @Field(type = FieldType.Keyword)
  private String  webDavPath;

  @Field(type = FieldType.Keyword)
  private String  parentWebDavPath;

  @Field(type = FieldType.Keyword)
  private String  jcrPath;

  @Field(type = FieldType.Keyword)
  private String  nodeIdentifier;

  @Field(type = FieldType.Keyword)
  private String  technicalName;

  private boolean fallbackName;

  private boolean collisionResolved;

  private String  createdDate;

  private String  updatedDate;

  public WebDavPathMappingEntity(String identityId, // NOSONAR
                                 String parentJcrPath,
                                 String visibleName,
                                 String webDavPath,
                                 String parentWebDavPath,
                                 String jcrPath,
                                 String nodeIdentifier,
                                 String technicalName,
                                 boolean fallbackName,
                                 boolean collisionResolved) {
    this.identityId = identityId;
    this.parentJcrPath = parentJcrPath;
    this.visibleName = visibleName;
    this.normalizedVisibleName = normalize(visibleName);
    this.webDavPath = webDavPath;
    this.parentWebDavPath = parentWebDavPath;
    this.jcrPath = jcrPath;
    this.nodeIdentifier = nodeIdentifier;
    this.technicalName = technicalName;
    this.fallbackName = fallbackName;
    this.collisionResolved = collisionResolved;
    this.id = buildId(parentJcrPath, this.normalizedVisibleName);
    this.createdDate = Instant.now().toString();
    this.updatedDate = this.createdDate;
  }

  public void touch() {
    this.updatedDate = Instant.now().toString();
  }

  public static String normalize(String name) {
    return StringUtils.defaultString(name).trim();
  }

  @SneakyThrows
  public static String buildId(String parentJcrPath, String normalizedVisibleName) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest((StringUtils.defaultString(parentJcrPath) + "::" + StringUtils.defaultString(normalizedVisibleName))
                                                                                                                         .getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash);
  }
}
