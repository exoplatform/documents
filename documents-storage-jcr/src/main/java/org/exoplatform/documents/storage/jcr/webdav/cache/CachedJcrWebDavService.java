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
package org.exoplatform.documents.storage.jcr.webdav.cache;

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GETLASTMODIFIED;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.MODIFICATION_PATTERN;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.REQUEST_ALL_PROPS;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jcr.Session;
import javax.jcr.observation.ObservationManager;
import javax.xml.namespace.QName;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.utils.Tools;
import org.exoplatform.documents.storage.jcr.webdav.JcrWebDavService;
import org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.entity.WebDavItemEntity;
import org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.entity.WebDavItemPropertyEntity;
import org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.repository.WebDavItemRepository;
import org.exoplatform.documents.storage.jcr.webdav.cache.listener.WebDavCacheUpdaterAction;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavReadCommandHandler;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavWriteCommandHandler;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.jcr.impl.RepositoryServiceImpl;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;

@Service
public class CachedJcrWebDavService extends JcrWebDavService {

  protected static final Log   LOG = ExoLogger.getLogger(CachedJcrWebDavService.class);

  private WebDavItemRepository webDavItemRepository;

  public CachedJcrWebDavService(WebdavReadCommandHandler readCommandHandler,
                                WebdavWriteCommandHandler writeCommandHandler,
                                RepositoryServiceImpl repositoryService,
                                UserACL userAcl,
                                WebDavItemRepository webDavItemRepository) {
    super(readCommandHandler, writeCommandHandler, repositoryService, userAcl);
    this.webDavItemRepository = webDavItemRepository;
  }

  @PostConstruct
  public void init() {
    // Clear stored Cache on startup
    webDavItemRepository.deleteAll();
    // Add Cache Clear Event Listener
    addCacheEventListener();
  }

  @Override
  public boolean isFile(String webDavPath) {
    if (StringUtils.isBlank(webDavPath)
        || StringUtils.equals(webDavPath, "/")) {
      return false;
    } else {
      WebDavItemEntity webDavItemEntity = findCacheEntry(webDavPath);
      if (webDavItemEntity == null) {
        return super.isFile(webDavPath);
      } else {
        return webDavItemEntity.isFile();
      }
    }
  }

  @Override
  public long getLastModifiedDate(String webDavPath, String version) throws WebDavException {
    if (StringUtils.isBlank(webDavPath) || StringUtils.equals(webDavPath, "/")) {
      return 0l;
    } else {
      WebDavItemEntity webDavItemEntity = findCacheEntry(webDavPath);
      if (webDavItemEntity == null) {
        return super.getLastModifiedDate(webDavPath, version);
      } else {
        return webDavItemEntity.getProperties() == null ? 0l :
                                                        webDavItemEntity.getProperties()
                                                                        .stream()
                                                                        .filter(p -> GETLASTMODIFIED.equals(WebDavItemProperty.toQname(p.getName())))
                                                                        .map(WebDavItemPropertyEntity::getValue)
                                                                        .filter(StringUtils::isNotBlank)
                                                                        .map(this::getModifiedDateMillis)
                                                                        .findFirst()
                                                                        .orElse(0l);
      }
    }
  }

  @Override
  public WebDavItem get(String webDavPath,
                        String propRequestType,
                        Set<QName> requestedPropertyNames,
                        boolean requestPropertyNamesOnly,
                        int depth,
                        String baseUri,
                        String username) throws WebDavException {
    if (StringUtils.isBlank(webDavPath) || StringUtils.equals(webDavPath, "/")) {
      return super.get(webDavPath, propRequestType, requestedPropertyNames, requestPropertyNamesOnly, depth, baseUri, username);
    } else {
      WebDavItemEntity webDavItemEntity = findCacheEntry(webDavPath);
      if (isMustRefreshItem(webDavItemEntity, username, depth)) {
        WebDavItem webDavItem = super.get(webDavPath,
                                          propRequestType,
                                          null, // Use Null in order to return
                                                // all properties, filter will
                                                // be applied in upper layer
                                          false, // Use false in order to return
                                          // all properties values
                                          depth,
                                          baseUri,
                                          username);
        if (webDavItem != null) {
          webDavItemEntity = saveWebDavItem(webDavItem,
                                            username,
                                            isMustReloadUsers(webDavItemEntity),
                                            depth);
        }
      }
      if (webDavItemEntity == null) {
        return null;
      } else {
        WebDavItem webDavItem = webDavItemEntity.toWebDavItem();
        if (depth > 0) {
          addChildren(webDavItem, depth, baseUri, username);
        }
        return webDavItem;
      }
    }
  }

  @SneakyThrows
  public void clearCache(String jcrPath, boolean drop) {
    WebDavItemEntity webDavItemEntity = webDavItemRepository.findByJcrPath(jcrPath);
    if (webDavItemEntity != null) {
      LOG.debug("Clear WebDav Item from ES Cache with path '{}' and option drop = '{}'", jcrPath, drop);
      if (drop) {
        webDavItemRepository.delete(webDavItemEntity);
      } else if (!webDavItemEntity.isModified()) {
        webDavItemEntity.setModified(true);
        webDavItemRepository.save(webDavItemEntity);
      }
    }
    String parentJcrPath = jcrPath.substring(0, jcrPath.lastIndexOf("/"));
    WebDavItemEntity parentWebDavItemEntity = webDavItemRepository.findByJcrPath(parentJcrPath);
    if (parentWebDavItemEntity != null && !parentWebDavItemEntity.isModified() && drop) {
      LOG.debug("Clear WebDav Item from ES Cache with path '{}'", jcrPath);
      parentWebDavItemEntity.setModified(true);
      parentWebDavItemEntity.setDeep(false);
      webDavItemRepository.save(parentWebDavItemEntity);
    }
  }

  private void addChildren(WebDavItem webDavItem, int depth, String baseUri, String username) { // NOSONAR
    List<WebDavItemEntity> children = webDavItemRepository.findByParentWebDavPath(webDavItem.getWebDavPath());
    int childrenDepth = depth - 1;
    children.stream()
            .map(c -> {
              WebDavItem childWebDavItem = null;
              if (!c.isModified()
                  && c.getUsernames().contains(username)
                  && (c.isDeep() || childrenDepth == 0)) {
                childWebDavItem = c.toWebDavItem();
              } else {
                try {
                  childWebDavItem = get(c.getWebDavPath(),
                                        REQUEST_ALL_PROPS,
                                        null,
                                        false,
                                        childrenDepth,
                                        baseUri,
                                        username);
                } catch (Exception e) {
                  if (LOG.isTraceEnabled()) {
                    LOG.trace("It seems that user isn't allowed to access {}. Continue for other child nodes",
                              c.getWebDavPath(),
                              e);
                  } else if (LOG.isDebugEnabled()) {
                    LOG.debug("It seems that user isn't allowed to access {}. Continue for other child nodes. Error: {}",
                              c.getWebDavPath(),
                              e.getMessage());
                  }
                  return null;
                }
              }
              if (childrenDepth > 0) {
                addChildren(childWebDavItem, childrenDepth, baseUri, username);
              }
              return childWebDavItem;
            })
            .filter(Objects::nonNull)
            .forEach(webDavItem::addChild);
  }

  private WebDavItemEntity saveWebDavItem(WebDavItem webDavItem, String username, boolean forceRefreshUsers, int depth) {
    LOG.debug("Save WebDav Item with path '{}' in ES Cache", webDavItem.getWebDavPath());
    WebDavItemEntity webDavItemEntity = new WebDavItemEntity(webDavItem);
    webDavItemEntity.setDeep(depth > 0);
    if (forceRefreshUsers) {
      webDavItemEntity.setUsernames(Collections.singleton(username));
    } else {
      WebDavItemEntity existingWebDavItemEntity = webDavItemRepository.findById(webDavItemEntity.getIdentifier()).orElse(null);
      if (existingWebDavItemEntity == null) {
        webDavItemEntity.setUsernames(Collections.singleton(username));
      } else {
        Set<String> usernames = new HashSet<>(existingWebDavItemEntity.getUsernames());
        usernames.add(username);
        webDavItemEntity.setUsernames(usernames);
        webDavItemEntity.setDeep(existingWebDavItemEntity.isDeep() || depth > 0);
      }
    }
    webDavItemEntity = webDavItemRepository.save(webDavItemEntity);
    if (CollectionUtils.isNotEmpty(webDavItem.getChildren())) {
      int childrenDepth = depth - 1;
      webDavItem.getChildren().forEach(c -> saveWebDavItem(c, username, forceRefreshUsers, childrenDepth));
    }
    return webDavItemEntity;
  }

  private Long getModifiedDateMillis(String modifiedDateString) {
    try {
      SimpleDateFormat dateFormat = new SimpleDateFormat(MODIFICATION_PATTERN, Locale.ENGLISH);
      dateFormat.setTimeZone(Tools.getTimeZone("GMT"));
      return dateFormat.parse(modifiedDateString).getTime();
    } catch (Exception e) {
      LOG.warn("Error while parsing modified date value {}", modifiedDateString, e);
      return 0l;
    }
  }

  private WebDavItemEntity findCacheEntry(String webDavPath) {
    String id = Arrays.stream(webDavPath.split("/"))
                      .filter(StringUtils::isNotBlank)
                      .map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
                      .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8)
                                          .replace("+", "%20"))
                      .collect(Collectors.joining("/"));
    return webDavItemRepository.findById("/" + id).orElse(null);
  }

  private boolean isMustReloadUsers(WebDavItemEntity webDavItemEntity) {
    return webDavItemEntity == null || webDavItemEntity.isModified();
  }

  private boolean isMustRefreshItem(WebDavItemEntity webDavItemEntity, String username, int depth) {
    return webDavItemEntity == null
           || webDavItemEntity.isModified()
           || (depth > 0 && !webDavItemEntity.isDeep())
           || !webDavItemEntity.getUsernames().contains(username);
  }

  @SneakyThrows
  private void addCacheEventListener() {
    Session session = getSession();
    try {
      ObservationManager observation = session.getWorkspace().getObservationManager();
      WebDavCacheUpdaterAction.SUPPORTED_PATHS.forEach(path -> addCacheEventListener(observation,
                                                                                     createCacheListenerInstance(),
                                                                                     path));
    } finally {
      session.logout();
    }
  }

  @SneakyThrows
  private void addCacheEventListener(ObservationManager observation,
                                     WebDavCacheUpdaterAction cacheUpdaterAction,
                                     String path) {
    observation.addEventListener(cacheUpdaterAction,
                                 WebDavCacheUpdaterAction.SUPPORTED_EVENT_TYPES,
                                 path,
                                 true,
                                 null,
                                 WebDavCacheUpdaterAction.SUPPORTED_NODE_TYPES.toArray(String[]::new),
                                 false);
  }

  private WebDavCacheUpdaterAction createCacheListenerInstance() {
    return new WebDavCacheUpdaterAction(this);
  }

}
