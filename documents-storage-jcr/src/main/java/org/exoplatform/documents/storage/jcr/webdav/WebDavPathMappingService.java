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
package org.exoplatform.documents.storage.jcr.webdav;

import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_NAME;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_TITLE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_CONTENT;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.documents.storage.jcr.util.Utils;
import org.exoplatform.documents.storage.jcr.webdav.dao.WebDavPathMappingRepository;
import org.exoplatform.documents.storage.jcr.webdav.entity.WebDavPathMappingEntity;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.util.Text;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WebDavPathMappingService {

  private static final String         DC_TITLE = "dc:title";

  @Autowired
  private WebDavPathMappingRepository repository;

  public String findJcrPath(String parentJcrPath, String visibleName) {
    return repository.findByParentJcrPathAndNormalizedVisibleName(parentJcrPath, WebDavPathMappingEntity.normalize(visibleName))
                     .map(WebDavPathMappingEntity::getJcrPath)
                     .orElse(null);
  }

  public String findVisibleNameByJcrPath(String jcrPath) {
    return repository.findByJcrPath(jcrPath)
                     .map(WebDavPathMappingEntity::getVisibleName)
                     .orElse(null);
  }

  @SneakyThrows
  public String findParentVisibleByJcrPath(String jcrPath) {
    String parentJcrPath = getParentJcrPath(jcrPath);
    String visibleName = repository.findByJcrPath(parentJcrPath)
                                   .map(WebDavPathMappingEntity::getVisibleName)
                                   .orElse(null);
    if (StringUtils.isNotBlank(visibleName)) {
      return visibleName;
    }

    return repository.findByJcrPath(jcrPath)
                     .map(WebDavPathMappingEntity::getParentWebDavPath)
                     .map(this::lastDecodedWebDavSegment)
                     .orElse(null);
  }

  public String getVisibleName(Node node) throws RepositoryException {
    String mappedVisibleName = findVisibleNameByJcrPath(node.getPath());
    if (StringUtils.isNotBlank(mappedVisibleName)) {
      return mappedVisibleName;
    }
    return getPreferredVisibleName(node);
  }

  public String getParentVisibleNodeName(Node node) throws RepositoryException {
    try {
      Node parentNode = node.getParent();
      String visibleName = findVisibleNameByJcrPath(parentNode.getPath());
      if (StringUtils.isNotBlank(visibleName)) {
        return visibleName;
      }
      return getVisibleName(parentNode);
    } catch (AccessDeniedException e) {
      return findParentVisibleByJcrPath(node.getPath());
    } catch (RepositoryException e) {
      String visibleName = findParentVisibleByJcrPath(node.getPath());
      if (StringUtils.isNotBlank(visibleName)) {
        return visibleName;
      }
      throw e;
    }
  }

  @SneakyThrows
  public String allocateTechnicalName(Session session, String parentJcrPath, String visibleName) {
    String candidateVisibleName = visibleName;
    int counter = 1;
    while (true) {
      String technicalName = Utils.encodeNodeName(candidateVisibleName);
      String candidateJcrPath = appendJcrPath(parentJcrPath, technicalName);
      if (!session.itemExists(candidateJcrPath)) {
        return technicalName;
      }
      candidateVisibleName = addSuffix(visibleName, counter++);
    }
  }

  @SneakyThrows
  public WebDavPathMappingEntity saveMapping(Session session, String webDavPath, String visibleName, Node node) {
    String normalizedWebDavPath = normalizeWebDavPath(webDavPath);
    String normalizedVisibleName = WebDavPathMappingEntity.normalize(visibleName);
    String parentJcrPath = getParentJcrPath(node.getPath());
    String parentWebDavPath = parentWebDavPath(normalizedWebDavPath);

    Optional<WebDavPathMappingEntity> existingByJcrPath = repository.findByJcrPath(node.getPath());
    Optional<WebDavPathMappingEntity> existingByVisibleName =
                                                            repository.findByParentJcrPathAndNormalizedVisibleName(parentJcrPath,
                                                                                                                   normalizedVisibleName);

    WebDavPathMappingEntity entity = existingByJcrPath.or(() -> existingByVisibleName).orElseGet(WebDavPathMappingEntity::new);
    entity.setIdentityId(extractIdentityId(normalizedWebDavPath));
    entity.setParentJcrPath(parentJcrPath);
    entity.setVisibleName(visibleName);
    entity.setNormalizedVisibleName(normalizedVisibleName);
    entity.setWebDavPath(normalizedWebDavPath);
    entity.setParentWebDavPath(parentWebDavPath);
    entity.setJcrPath(node.getPath());
    entity.setNodeIdentifier(((NodeImpl) node).getIdentifier());
    entity.setTechnicalName(node.getName());
    entity.setFallbackName(StringUtils.equals(visibleName, node.getName()));
    entity.setCollisionResolved(false);
    entity.setId(WebDavPathMappingEntity.buildId(parentJcrPath, normalizedVisibleName));
    if (StringUtils.isBlank(entity.getCreatedDate())) {
      entity.setCreatedDate(java.time.Instant.now().toString());
    }
    entity.touch();

    if (node.hasProperty(EXO_TITLE)) {
      node.setProperty(EXO_TITLE, visibleName);
    }
    if (node.hasProperty(EXO_NAME)) {
      node.setProperty(EXO_NAME, node.getName());
    }

    return repository.save(entity);
  }

  @SneakyThrows
  public String getOrCreateWebDavPath(String identityId,
                                      String identityBaseJcrPath,
                                      String identityRootWebDavPath,
                                      Node node,
                                      String preferredVisibleName) {
    if (StringUtils.equals(node.getPath(), identityBaseJcrPath)) {
      return normalizeWebDavPath(identityRootWebDavPath);
    }

    Optional<WebDavPathMappingEntity> byIdentifier = findByNodeIdentifier(node);
    if (byIdentifier.isPresent()) {
      return byIdentifier.get().getWebDavPath();
    }

    Optional<WebDavPathMappingEntity> byJcrPath = repository.findByJcrPath(node.getPath());
    if (byJcrPath.isPresent()) {
      return byJcrPath.get().getWebDavPath();
    }

    String parentPath = getParentJcrPath(node.getPath());
    String parentWebDavPath = StringUtils.equals(parentPath, identityBaseJcrPath) ?
                                                                                  normalizeWebDavPath(identityRootWebDavPath) :
                                                                                  getOrCreateWebDavPath(identityId,
                                                                                                        identityBaseJcrPath,
                                                                                                        identityRootWebDavPath,
                                                                                                        node.getParent(),
                                                                                                        getPreferredVisibleName(node.getParent()));

    String requestedVisibleName =
                                cleanVisibleName(StringUtils.defaultIfBlank(preferredVisibleName, getPreferredVisibleName(node)));
    String visibleName = allocateVisibleName(parentPath, requestedVisibleName, node.getPath());
    boolean collisionResolved = !StringUtils.equals(visibleName, requestedVisibleName);
    String webDavPath = appendWebDavPath(parentWebDavPath, visibleName);

    WebDavPathMappingEntity entity = new WebDavPathMappingEntity(identityId,
                                                                 parentPath,
                                                                 visibleName,
                                                                 webDavPath,
                                                                 parentWebDavPath,
                                                                 node.getPath(),
                                                                 ((NodeImpl) node).getIdentifier(),
                                                                 node.getName(),
                                                                 StringUtils.equals(visibleName, node.getName()),
                                                                 collisionResolved);
    return repository.save(entity).getWebDavPath();
  }

  public void deleteMapping(String jcrPath) {
    repository.findByJcrPath(jcrPath).ifPresent(repository::delete);
  }

  public void deleteMappingTree(String jcrPath) {
    if (StringUtils.isBlank(jcrPath)) {
      return;
    }

    deleteMapping(jcrPath);

    String descendantsPrefix = StringUtils.removeEnd(jcrPath, "/") + "/";
    List<WebDavPathMappingEntity> descendants = repository.findByJcrPathStartingWith(descendantsPrefix);
    if (descendants != null && !descendants.isEmpty()) {
      repository.deleteAll(descendants);
    }
  }

  public void invalidateMapping(String jcrPath) {
    if (StringUtils.isBlank(jcrPath)) {
      return;
    }

    log.debug("Invalidate WebDAV path mapping for JCR path '{}'", jcrPath);
    deleteMapping(jcrPath);
  }

  public void invalidateMappingTree(String jcrPath) {
    if (StringUtils.isBlank(jcrPath)) {
      return;
    }

    log.debug("Invalidate WebDAV path mapping tree for JCR path '{}'", jcrPath);
    deleteMappingTree(jcrPath);
  }

  public void invalidateParentMappingChildren(String jcrPath) {
    String parentJcrPath = getParentJcrPath(jcrPath);
    if (StringUtils.isBlank(parentJcrPath)) {
      return;
    }

    log.debug("Invalidate parent WebDAV mapping children for JCR parent path '{}'", parentJcrPath);
    repository.findByParentJcrPath(parentJcrPath)
              .forEach(repository::delete);
  }

  public void invalidateMappingByIdentifier(String identifier) {
    repository.findByNodeIdentifier(identifier)
              .ifPresent(repository::delete);
  }

  public boolean isTitlePropertyPath(String eventPath) {
    if (StringUtils.isBlank(eventPath)) {
      return false;
    }

    return StringUtils.endsWith(eventPath, "/" + EXO_TITLE)
           || StringUtils.endsWith(eventPath, "/" + JCR_CONTENT + "/" + DC_TITLE);
  }

  public String getNodePathFromPropertyPath(String propertyPath) {
    if (StringUtils.isBlank(propertyPath)) {
      return propertyPath;
    }

    if (StringUtils.endsWith(propertyPath, "/" + EXO_TITLE)) {
      return StringUtils.substringBeforeLast(propertyPath, "/");
    }

    if (StringUtils.endsWith(propertyPath, "/" + JCR_CONTENT + "/" + DC_TITLE)) {
      return StringUtils.substringBeforeLast(propertyPath, "/" + JCR_CONTENT + "/" + DC_TITLE);
    }

    return StringUtils.substringBeforeLast(propertyPath, "/");
  }

  public Optional<WebDavPathMappingEntity> findByNodeIdentifier(Node node) throws RepositoryException {
    String identifier = ((NodeImpl) node).getIdentifier();
    return StringUtils.isBlank(identifier) ? Optional.empty() : repository.findByNodeIdentifier(identifier);
  }

  private String allocateVisibleName(String parentJcrPath, String requestedVisibleName, String targetJcrPath) {
    String visibleName = requestedVisibleName;
    int counter = 1;
    while (true) {
      Optional<WebDavPathMappingEntity> existing = repository.findByParentJcrPathAndNormalizedVisibleName(parentJcrPath,
                                                                                                          WebDavPathMappingEntity.normalize(visibleName));
      if (existing.isEmpty() || StringUtils.equals(existing.get().getJcrPath(), targetJcrPath)) {
        return visibleName;
      }
      visibleName = addSuffix(requestedVisibleName, counter++);
    }
  }

  private String getPreferredVisibleName(Node node) throws RepositoryException {
    String title = null;
    if (node.hasProperty(EXO_TITLE)) {
      title = node.getProperty(EXO_TITLE).getString();
    } else if (node.hasProperty(JCR_CONTENT + "/" + DC_TITLE)) {
      title = node.getProperty(JCR_CONTENT + "/" + DC_TITLE).getString();
    }
    if (StringUtils.isBlank(title)) {
      title = node.getName();
    }
    return cleanVisibleName(title);
  }

  private String cleanVisibleName(String visibleName) {
    return StringEscapeUtils.unescapeHtml4(Text.unescapeIllegalJcrChars(StringUtils.defaultString(visibleName)));
  }

  private String addSuffix(String fileName, int index) {
    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex > 0) {
      return fileName.substring(0, dotIndex) + " (" + index + ")" + fileName.substring(dotIndex);
    }
    return fileName + " (" + index + ")";
  }

  private String appendJcrPath(String parentJcrPath, String childName) {
    return StringUtils.removeEnd(parentJcrPath, "/") + "/" + childName;
  }

  private String appendWebDavPath(String parentWebDavPath, String visibleName) {
    String encodedVisibleName = encodeUrlString(visibleName);
    return StringUtils.removeEnd(parentWebDavPath, "/") + "/" + encodedVisibleName;
  }

  private String encodeUrlString(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private String normalizeWebDavPath(String webDavPath) {
    if (StringUtils.isBlank(webDavPath) || StringUtils.equals(webDavPath, "/")) {
      return "/";
    }
    String normalized = Arrays.stream(webDavPath.split("/"))
                              .filter(StringUtils::isNotBlank)
                              .map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
                              .map(this::encodeUrlString)
                              .collect(Collectors.joining("/"));
    return "/" + normalized;
  }

  private String parentWebDavPath(String webDavPath) {
    String normalized = StringUtils.removeEnd(normalizeWebDavPath(webDavPath), "/");
    int index = normalized.lastIndexOf('/');
    return index <= 0 ? "/" : normalized.substring(0, index);
  }

  private String extractIdentityId(String webDavPath) {
    String firstSegment = Arrays.stream(webDavPath.split("/"))
                                .filter(StringUtils::isNotBlank)
                                .findFirst()
                                .map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
                                .orElse(null);
    if (firstSegment != null && firstSegment.endsWith(")") && firstSegment.contains("(")) {
      return firstSegment.substring(firstSegment.lastIndexOf('(') + 1, firstSegment.lastIndexOf(')'));
    }
    return null;
  }

  private String getParentJcrPath(String jcrPath) {
    if (StringUtils.isBlank(jcrPath) || StringUtils.equals(jcrPath, "/")) {
      return null;
    }

    int index = jcrPath.lastIndexOf('/');
    return index <= 0 ? "/" : jcrPath.substring(0, index);
  }

  private String lastDecodedWebDavSegment(String webDavPath) {
    return Arrays.stream(StringUtils.defaultString(webDavPath).split("/"))
                 .filter(StringUtils::isNotBlank)
                 .reduce((first, second) -> second)
                 .map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
                 .orElse(null);
  }

}
