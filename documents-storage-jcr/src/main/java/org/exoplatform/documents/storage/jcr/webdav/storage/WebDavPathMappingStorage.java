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
package org.exoplatform.documents.storage.jcr.webdav.storage;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.documents.storage.jcr.webdav.dao.WebDavPathMappingDao;
import org.exoplatform.documents.storage.jcr.webdav.entity.WebDavPathMappingEntity;

@Service
public class WebDavPathMappingStorage {

  @Autowired
  private WebDavPathMappingDao webDavPathMappingDao;

  public Optional<WebDavPathMappingEntity> findByJcrPath(String jcrPath) {
    return StringUtils.isBlank(jcrPath) ? Optional.empty() : webDavPathMappingDao.findByJcrPath(jcrPath);
  }

  public Optional<WebDavPathMappingEntity> findByNodeIdentifier(String nodeIdentifier) {
    return StringUtils.isBlank(nodeIdentifier) ? Optional.empty() : webDavPathMappingDao.findByNodeIdentifier(nodeIdentifier);
  }

  public Optional<WebDavPathMappingEntity> findByParentJcrPathAndNormalizedVisibleName(String parentJcrPath,
                                                                                       String normalizedVisibleName) {
    if (StringUtils.isBlank(parentJcrPath) || StringUtils.isBlank(normalizedVisibleName)) {
      return Optional.empty();
    }
    return webDavPathMappingDao.findByParentJcrPathAndNormalizedVisibleName(parentJcrPath, normalizedVisibleName);
  }

  public List<WebDavPathMappingEntity> findByParentJcrPath(String parentJcrPath) {
    return StringUtils.isBlank(parentJcrPath) ? List.of() : webDavPathMappingDao.findByParentJcrPath(parentJcrPath);
  }

  public List<WebDavPathMappingEntity> findByParentWebDavPath(String parentWebDavPath) {
    return StringUtils.isBlank(parentWebDavPath) ? List.of() : webDavPathMappingDao.findByParentWebDavPath(parentWebDavPath);
  }

  public Optional<WebDavPathMappingEntity> findByWebDavPath(String webDavPath) {
    return StringUtils.isBlank(webDavPath) ? Optional.empty() : webDavPathMappingDao.findByWebDavPath(webDavPath);
  }

  public String findJcrPath(String parentJcrPath, String visibleName) {
    return findByParentJcrPathAndNormalizedVisibleName(parentJcrPath,
                                                       WebDavPathMappingEntity.normalize(visibleName))
                                                                                                      .map(WebDavPathMappingEntity::getJcrPath)
                                                                                                      .orElse(null);
  }

  public WebDavPathMappingEntity save(WebDavPathMappingEntity entity) {
    return webDavPathMappingDao.save(entity);
  }

  public void delete(WebDavPathMappingEntity entity) {
    if (entity != null) {
      webDavPathMappingDao.delete(entity);
    }
  }

  public void deleteById(String id) {
    if (StringUtils.isNotBlank(id)) {
      webDavPathMappingDao.deleteById(id);
    }
  }

  public void deleteMapping(String jcrPath) {
    findByJcrPath(jcrPath).ifPresent(webDavPathMappingDao::delete);
  }

}
