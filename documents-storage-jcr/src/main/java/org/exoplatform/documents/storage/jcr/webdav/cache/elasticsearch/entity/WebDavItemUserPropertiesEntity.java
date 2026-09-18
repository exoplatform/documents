/**
 * Copyright (C) 2026 eXo Platform SAS
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

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The properties of one cached WebDAV item that depend on <b>who asked</b>, held
 * per user inside the item's single cache row.
 * <p>
 * A row is keyed by the drive-relative WebDAV path alone, so it is shared by
 * every user who has read that path; the properties computed from the reading
 * user's own JCR session must therefore not live in the row's shared property
 * list, where the last reader would impose them on everyone else (EXO-90128).
 * They are carried here instead, and overlaid on the shared list at read time
 * by {@code CachedJcrWebDavService#resolveUserProperties}.
 * <p>
 * Modelled as a list of (username, properties) pairs rather than a map keyed by
 * username on purpose: an Elasticsearch document with usernames as field names
 * would add a mapping field per user and eventually hit the index field limit.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebDavItemUserPropertiesEntity {

  private String                         username;

  private List<WebDavItemPropertyEntity> properties = new ArrayList<>();

}
