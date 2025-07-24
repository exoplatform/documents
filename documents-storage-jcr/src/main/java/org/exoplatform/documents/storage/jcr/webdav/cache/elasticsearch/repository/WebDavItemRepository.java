/*
 * Copyright (C) 2003 - 2025 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.repository;

import java.util.List;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.model.WebDavItemEntity;

@Repository
public interface WebDavItemRepository extends ElasticsearchRepository<WebDavItemEntity, String> {

  List<WebDavItemEntity> findByParentWebDavPath(String parentWebDavPath);

  WebDavItemEntity findByJcrPath(String jcrPath);

}
