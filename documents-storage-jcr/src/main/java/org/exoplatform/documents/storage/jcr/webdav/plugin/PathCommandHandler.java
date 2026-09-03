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
package org.exoplatform.documents.storage.jcr.webdav.plugin;

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.*;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.jcr.*;
import javax.jcr.observation.ObservationManager;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import org.exoplatform.documents.storage.jcr.util.ACLProperties;
import org.exoplatform.documents.storage.jcr.util.Utils;
import org.exoplatform.documents.storage.jcr.webdav.entity.WebDavPathMappingEntity;
import org.exoplatform.documents.storage.jcr.webdav.listener.WebDavPathMappingUpdaterAction;
import org.exoplatform.documents.storage.jcr.webdav.storage.WebDavPathMappingStorage;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.util.Text;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;

@Component
public class PathCommandHandler {

  public static final List<QName>    PROPERTY_NAMES                      =                                               // NOSONAR
                                                    Arrays.asList(VERSIONNAME,
                                                                  VERSIONHISTORY,
                                                                  DISPLAYNAME,
                                                                  CHECKEDIN,
                                                                  CHECKEDOUT,
                                                                  PREDECESSORSET,
                                                                  SUCCESSORSET,
                                                                  RESOURCETYPE,
                                                                  GETCONTENTLENGTH,
                                                                  GETCONTENTTYPE,
                                                                  CREATIONDATE,
                                                                  GETLASTMODIFIED,
                                                                  GET_ETAG,
                                                                  CHILDCOUNT,
                                                                  HASCHILDREN,
                                                                  ISCOLLECTION,
                                                                  ISFOLDER,
                                                                  ISROOT,
                                                                  PARENTNAME,
                                                                  SUPPORTEDLOCK,
                                                                  LOCKDISCOVERY,
                                                                  ISVERSIONED,
                                                                  SUPPORTEDMETHODSET,
                                                                  ACLProperties.ACL,
                                                                  OWNER);

  public static final String         IDENTITY_PATHS_FORMAT               = "%s/%s%s%s%s";

  public static final String         PATHS_CONCAT_FORMAT                 = "%s/%s";

  public static final String         SEGMENT_CHAR_REPLACEMENT            = "_";

  /**
   * Characters a name cannot carry into a WebDAV path segment: the two path
   * separators, and the characters whose percent-encoded form is in
   * {@code StrictHttpFirewall}'s blocklist.
   */
  private static final Pattern       UNSAFE_SEGMENT_CHARS                = Pattern.compile("[/\\\\%;\\p{Cntrl}]");

  protected static final Log         LOG                                 = ExoLogger.getLogger(PathCommandHandler.class);

  private static final String        WEBDAV_IDENTITY_JCR_PATH_CACHE_NAME = "webdav.identityJcrBasePath";

  private static final String        WEBDAV_IDENTITY_ID_PATH_CACHE_NAME  = "webdav.identityIdByPath";

  private static final String        GROUPS_PATH                         = "groupsPath";

  private static final String        USERS_PATH                          = "usersPath";

  private static final String        DC_TITLE                            = "dc:title";

  private static final String        JCR_CONTENT                         = "jcr:content";

  private static final String        EXO_TITLE                           = "exo:title";

  private static final String        EXO_NAME                            = "exo:name";

  private static final String        PRIVATE_NODE_NAME                   = "Private";

  private static final String        IDENTITY_ID_PREFIX                  = "%20%28";

  private static final String        IDENTITY_ID_SUFFIX                  = "%29";

  @Autowired
  protected IdentityManager          identityManager;

  @Autowired
  protected SpaceService             spaceService;

  @Autowired
  protected NodeHierarchyCreator     nodeHierarchyCreator;

  @Autowired
  protected SessionProviderService   sessionProviderService;

  @Autowired
  protected RepositoryService        repositoryService;

  @Autowired
  protected WebDavPathMappingStorage webDavPathMappingStorage;

  private String                     usersJcrBasePath;

  private String                     groupsJcrBasePath;

  @PostConstruct
  public void init() {
    addMappingEventListener();
  }

  /**
   * Last-resort guard keeping a drive name inside a single WebDAV path segment.
   * The names fed to it — a Space pretty name, a username — are already URL-safe
   * by construction (see {@link #getIdentitySegmentName(Identity)}); this only
   * makes sure an identity store that yields something unexpected cannot emit a
   * '%2F', which is rejected before the request reaches any handler and which,
   * once decoded, would split the drive into two segments so that the identity
   * id can no longer be read back from the path.
   *
   * @param segmentName drive name, may be null
   * @return the name with every character unusable in a path segment replaced
   *         by {@link #SEGMENT_CHAR_REPLACEMENT}
   */
  public static String toWebDavSegment(String segmentName) {
    return UNSAFE_SEGMENT_CHARS.matcher(StringUtils.defaultString(segmentName)).replaceAll(SEGMENT_CHAR_REPLACEMENT);
  }

  @SneakyThrows
  @Cacheable(WEBDAV_IDENTITY_JCR_PATH_CACHE_NAME)
  public String getIdentityBaseJcrPath(String webDavPath) {
    Long identityId = getIdentityIdFromWebDavPath(webDavPath);
    if (identityId == null) {
      throw new WebDavException(HttpStatus.SC_NOT_FOUND, String.format("Can't read identity id from path: %s", webDavPath));
    } else {
      return getIdentityBaseJcrPath(identityId);
    }
  }

  @SneakyThrows
  @Cacheable(WEBDAV_IDENTITY_JCR_PATH_CACHE_NAME)
  public String getIdentityBaseFromJcrPath(String jcrPath, String username) {
    Long identityId = getIdentityIdFromJcrPath(jcrPath, username);
    if (identityId == null) {
      throw new WebDavException(HttpStatus.SC_NOT_FOUND, String.format("Can't read identity id from path: %s", jcrPath));
    } else {
      return getIdentityBaseJcrPath(identityId);
    }
  }

  @SneakyThrows
  @Cacheable(WEBDAV_IDENTITY_JCR_PATH_CACHE_NAME)
  public String getIdentityBaseJcrPath(long identityId) {
    Identity identity = identityManager.getIdentity(identityId);
    if (identity == null) {
      throw new WebDavException(HttpStatus.SC_NOT_FOUND,
                                String.format("Identity with id %s not found", identityId));
    } else if (identity.isUser()) {
      SessionProvider systemSessionProvider = sessionProviderService.getSystemSessionProvider(null);
      Node userNode = nodeHierarchyCreator.getUserNode(systemSessionProvider, identity.getRemoteId());
      Node userPrivateNode = userNode.getNode(PRIVATE_NODE_NAME);
      return userPrivateNode.getPath();
    } else if (identity.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
      if (space == null) {
        throw new WebDavException(HttpStatus.SC_NOT_FOUND,
                                  String.format("Space with pretty name %s not found", identity.getRemoteId()));
      }
      return String.format("%s%s/Documents",
                           getGroupsBaseJcrPath(),
                           space.getGroupId());
    } else {
      throw new WebDavException(HttpStatus.SC_BAD_REQUEST,
                                String.format("Identity with type %s not supported", identity.getProviderId()));
    }
  }

  @Cacheable(WEBDAV_IDENTITY_ID_PATH_CACHE_NAME)
  public Long getIdentityIdFromJcrPath(String jcrPath, String username) {
    if (jcrPath.startsWith(getGroupsBaseJcrPath() + "/spaces")) {
      String[] pathParts = jcrPath.replaceFirst(getGroupsBaseJcrPath() + "/spaces", "").split("/");
      String spaceGroupId = String.format("/spaces/%s", StringUtils.firstNonBlank(pathParts[0], pathParts[1]));
      Space space = spaceService.getSpaceByGroupId(spaceGroupId);
      Identity spaceIdentity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
      return spaceIdentity.getIdentityId();
    } else if (StringUtils.isNotBlank(username)
               && jcrPath.startsWith(getUsersBaseJcrPath())
               && jcrPath.contains(String.format("/%s/", username))) {
      Identity userIdentity = identityManager.getOrCreateUserIdentity(username);
      return userIdentity.getIdentityId();
    } else {
      return null;
    }
  }

  @Cacheable(WEBDAV_IDENTITY_ID_PATH_CACHE_NAME)
  public Long getIdentityIdFromWebDavPath(String webDavPath) {
    if (StringUtils.isBlank(webDavPath) || StringUtils.equals(webDavPath, "/")) {
      return null;
    } else {
      String[] pathParts = webDavPath.split("/");
      String identityPart = Arrays.stream(pathParts)
                                  .filter(StringUtils::isNotBlank)
                                  .map(this::decodeUrlString)
                                  .findFirst()
                                  .orElse(null);
      String identityId = null;
      if (identityPart != null
          && identityPart.endsWith(")")
          && identityPart.contains("(")) {
        identityId = identityPart.substring(identityPart.lastIndexOf("(") + 1, identityPart.lastIndexOf(")"));
      }
      if (identityId != null) {
        return Long.parseLong(identityId);
      }
      return null;
    }
  }

  @SneakyThrows
  public String resolveToJcrPath(Session session, String webDavPath) throws WebDavException {
    Long identityId = getIdentityIdFromWebDavPath(webDavPath);
    if (identityId == null) {
      return "/";
    }

    String identityBaseJcrPath = getIdentityBaseJcrPath(identityId);
    String identityRelativeWebDavPath = getIdentityRelativeWebDavPath(webDavPath);
    if (StringUtils.isBlank(identityRelativeWebDavPath)) {
      return identityBaseJcrPath;
    }

    String currentParentJcrPath = identityBaseJcrPath;
    for (String visibleSegment : splitDecodedSegments(identityRelativeWebDavPath)) { // NOSONAR
      String mappedJcrPath = webDavPathMappingStorage.findJcrPath(currentParentJcrPath, visibleSegment);
      if (StringUtils.isNotBlank(mappedJcrPath)) {
        currentParentJcrPath = mappedJcrPath;
        continue;
      }

      String legacyChildJcrPath = String.format(PATHS_CONCAT_FORMAT,
                                                currentParentJcrPath,
                                                Utils.encodeNodeName(visibleSegment));
      if (session.itemExists(legacyChildJcrPath)) {
        Item existingItem = session.getItem(legacyChildJcrPath);
        if (existingItem instanceof Node existingNode) {
          // Compare the identity-relative parts only: the drive segment is
          // addressed by its id, so a client may legitimately hold an older
          // spelling of the drive name in the path it sends
          String existingRelativePath = getIdentityRelativeDecodedWebDavPath(getOrCreateWebDavPath(existingNode));
          if (StringUtils.isBlank(existingRelativePath)
              || StringUtils.equals(identityRelativeWebDavPath, existingRelativePath)
              || StringUtils.startsWith(identityRelativeWebDavPath, existingRelativePath + "/")) {
            currentParentJcrPath = legacyChildJcrPath;
            continue;
          }
        }
      }

      throw new WebDavException(HttpStatus.SC_NOT_FOUND,
                                String.format("No WebDAV mapping found for segment '%s' under '%s'",
                                              visibleSegment,
                                              currentParentJcrPath));
    }
    return currentParentJcrPath;
  }

  public String getLastVisibleSegment(String webDavPath) {
    return Arrays.stream(webDavPath.split("/"))
                 .filter(StringUtils::isNotBlank)
                 .reduce((first, second) -> second)
                 .map(this::decodeUrlString)
                 .orElse("");
  }

  public boolean isIdentityRootWebDavPath(String webDavPath) {
    String identityRelativeJcrPath = getIdentityRelativeJcrPath(webDavPath);
    return StringUtils.isBlank(identityRelativeJcrPath);
  }

  @SneakyThrows
  public String getOrCreateWebDavPath(Node node) {
    Long identityId = getIdentityIdFromJcrPath(node.getPath(), node.getSession().getUserID());
    if (identityId == null) {
      return null;
    }
    Identity identity = identityManager.getIdentity(identityId);
    if (identity == null) {
      return null;
    }
    String identityBaseJcrPath = getIdentityBaseJcrPath(identityId);
    String identityRootWebDavPath = getIdentityRootWebDavPath(identityId, getIdentitySegmentName(identity));
    return getOrCreateWebDavPath(String.valueOf(identityId),
                                 identityBaseJcrPath,
                                 identityRootWebDavPath,
                                 node,
                                 getVisibleName(node));
  }

  @SneakyThrows
  public String getOrCreateWebDavPath(String identityId,
                                      String identityBaseJcrPath,
                                      String identityRootWebDavPath,
                                      Node node,
                                      String preferredVisibleName) {
    String normalizedIdentityRootWebDavPath = normalizeWebDavPath(identityRootWebDavPath);
    if (StringUtils.equals(node.getPath(), identityBaseJcrPath)) {
      return normalizedIdentityRootWebDavPath;
    }

    String parentJcrPath = getParentJcrPath(node.getPath());
    String parentWebDavPath = StringUtils.equals(parentJcrPath, identityBaseJcrPath) ?
                                                                                     normalizedIdentityRootWebDavPath :
                                                                                     getOrCreateWebDavPath(identityId,
                                                                                                           identityBaseJcrPath,
                                                                                                           normalizedIdentityRootWebDavPath,
                                                                                                           node.getParent(),
                                                                                                           getPreferredVisibleName(node.getParent()));

    String requestedVisibleName =
                                cleanVisibleName(StringUtils.defaultIfBlank(preferredVisibleName, getPreferredVisibleName(node)));
    String visibleName = allocateVisibleName(parentJcrPath, requestedVisibleName, node.getPath());
    String webDavPath = appendWebDavPath(parentWebDavPath, visibleName);

    Optional<WebDavPathMappingEntity> byIdentifier = findByNodeIdentifier(node);
    Optional<WebDavPathMappingEntity> byJcrPath = webDavPathMappingStorage.findByJcrPath(node.getPath());
    WebDavPathMappingEntity entity = byIdentifier.or(() -> byJcrPath).orElse(null);
    if (entity != null && isMappingUpToDate(entity, parentJcrPath, parentWebDavPath, visibleName, webDavPath, node)) {
      return entity.getWebDavPath();
    }

    return saveMappingEntity(entity,
                             identityId,
                             parentJcrPath,
                             visibleName,
                             parentWebDavPath,
                             webDavPath,
                             node,
                             requestedVisibleName).getWebDavPath();
  }

  public String getVisibleName(Node node) throws RepositoryException {
    String mappedVisibleName = webDavPathMappingStorage.findByJcrPath(node.getPath())
                                                       .map(WebDavPathMappingEntity::getVisibleName)
                                                       .orElse(null);
    if (StringUtils.isNotBlank(mappedVisibleName)) {
      return mappedVisibleName;
    }
    return getPreferredVisibleName(node);
  }

  public String getParentVisibleNodeName(Node node) throws RepositoryException {
    try {
      Node parentNode = node.getParent();
      String visibleName = webDavPathMappingStorage.findByJcrPath(parentNode.getPath())
                                                   .map(WebDavPathMappingEntity::getVisibleName)
                                                   .orElse(null);
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

    Optional<WebDavPathMappingEntity> existingByJcrPath = webDavPathMappingStorage.findByJcrPath(node.getPath());
    Optional<WebDavPathMappingEntity> existingByVisibleName =
                                                            webDavPathMappingStorage.findByParentJcrPathAndNormalizedVisibleName(parentJcrPath,
                                                                                                                                 normalizedVisibleName);
    WebDavPathMappingEntity entity = existingByJcrPath.or(() -> existingByVisibleName).orElse(null);
    return saveMappingEntity(entity,
                             extractIdentityId(normalizedWebDavPath),
                             parentJcrPath,
                             visibleName,
                             parentWebDavPath,
                             normalizedWebDavPath,
                             node,
                             visibleName);
  }

  public void deleteMapping(String jcrPath) {
    webDavPathMappingStorage.deleteMapping(jcrPath);
  }

  public void refreshMappingOrDelete(Session session, String jcrPath) {
    if (StringUtils.isBlank(jcrPath)) {
      return;
    }
    try {
      Optional<WebDavPathMappingEntity> existing = webDavPathMappingStorage.findByJcrPath(jcrPath);
      if (!session.itemExists(jcrPath)) {
        deleteMapping(existing.map(WebDavPathMappingEntity::getJcrPath).orElse(jcrPath));
        return;
      }

      Item item = session.getItem(jcrPath);
      if (!(item instanceof Node node)) {
        deleteMapping(existing.map(WebDavPathMappingEntity::getJcrPath).orElse(jcrPath));
        return;
      }

      Optional<WebDavPathMappingEntity> byIdentifier = findByNodeIdentifier(node);
      WebDavPathMappingEntity entity = byIdentifier.or(() -> existing).orElse(null);
      if (entity == null) {
        // Unmapped item: keep lazy creation in the WebDAV read flow.
        return;
      }
      refreshExistingMapping(session, node, entity);
    } catch (Exception e) {
      LOG.warn("Cannot refresh WebDAV path mapping for JCR path '{}'. Delete stale mapping instead.", jcrPath, e);
      deleteMapping(jcrPath);
    }
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

  private String getIdentityRelativeWebDavPath(String webDavPath) {
    return Arrays.stream(webDavPath.split("/"))
                 .filter(StringUtils::isNotBlank)
                 .skip(1)
                 .collect(Collectors.joining("/"));
  }

  /**
   * @param encodedWebDavPath a WebDAV path as stored/emitted, percent-encoded
   * @return the same path without its drive segment, each remaining segment
   *         decoded — comparable with the decoded path a client sends
   */
  private String getIdentityRelativeDecodedWebDavPath(String encodedWebDavPath) {
    return Arrays.stream(StringUtils.defaultString(encodedWebDavPath).split("/"))
                 .filter(StringUtils::isNotBlank)
                 .skip(1)
                 .map(this::decodeUrlString)
                 .collect(Collectors.joining("/"));
  }

  private List<String> splitDecodedSegments(String relativeWebDavPath) {
    return Arrays.stream(relativeWebDavPath.split("/"))
                 .filter(StringUtils::isNotBlank)
                 .map(this::decodeUrlString)
                 .toList();
  }

  private String getIdentityRelativeJcrPath(String webDavPath) {
    String[] pathParts = webDavPath.split("/");
    return Arrays.stream(pathParts)
                 .filter(StringUtils::isNotBlank)
                 .map(this::decodeUrlString)
                 .map(Utils::encodeNodeName)
                 .skip(1)
                 .collect(Collectors.joining("/"));
  }

  @SneakyThrows
  private void refreshExistingMapping(Session session, Node node, WebDavPathMappingEntity entity) {
    Long identityId = getIdentityIdFromJcrPath(node.getPath());
    if (identityId == null && StringUtils.isNotBlank(entity.getIdentityId())) {
      identityId = Long.parseLong(entity.getIdentityId());
    }
    if (identityId == null) {
      LOG.debug("Cannot determine identity for JCR path '{}'. Delete mapping and let WebDAV read recreate it.", node.getPath());
      deleteMapping(entity.getJcrPath());
      return;
    }

    String identityBaseJcrPath = getIdentityBaseJcrPath(identityId);
    String identityRootWebDavPath = getIdentityRootWebDavPath(identityId);
    String refreshedWebDavPath = getOrCreateWebDavPath(String.valueOf(identityId),
                                                       identityBaseJcrPath,
                                                       identityRootWebDavPath,
                                                       node,
                                                       getPreferredVisibleName(node));
    LOG.debug("Refreshed WebDAV path mapping for JCR path '{}' to '{}'", node.getPath(), refreshedWebDavPath);
  }

  private boolean isMappingUpToDate(WebDavPathMappingEntity entity,
                                    String parentJcrPath,
                                    String parentWebDavPath,
                                    String visibleName,
                                    String webDavPath,
                                    Node node) throws RepositoryException {
    return StringUtils.equals(entity.getJcrPath(), node.getPath())
           && StringUtils.equals(entity.getParentJcrPath(), parentJcrPath)
           && StringUtils.equals(entity.getParentWebDavPath(), parentWebDavPath)
           && StringUtils.equals(entity.getVisibleName(), visibleName)
           && StringUtils.equals(entity.getWebDavPath(), webDavPath)
           && StringUtils.equals(entity.getTechnicalName(), node.getName());
  }

  private WebDavPathMappingEntity saveMappingEntity(WebDavPathMappingEntity entity, // NOSONAR
                                                    String identityId,
                                                    String parentJcrPath,
                                                    String visibleName,
                                                    String parentWebDavPath,
                                                    String webDavPath,
                                                    Node node,
                                                    String requestedVisibleName) throws RepositoryException {
    if (entity == null) {
      entity = new WebDavPathMappingEntity();
    }

    String oldId = entity.getId();
    String normalizedVisibleName = WebDavPathMappingEntity.normalize(visibleName);
    String newId = WebDavPathMappingEntity.buildId(parentJcrPath, normalizedVisibleName);

    entity.setId(newId);
    entity.setIdentityId(identityId);
    entity.setParentJcrPath(parentJcrPath);
    entity.setVisibleName(visibleName);
    entity.setNormalizedVisibleName(normalizedVisibleName);
    entity.setWebDavPath(normalizeWebDavPath(webDavPath));
    entity.setParentWebDavPath(normalizeWebDavPath(parentWebDavPath));
    entity.setJcrPath(node.getPath());
    entity.setNodeIdentifier(getNodeIdentifier(node));
    entity.setTechnicalName(node.getName());
    entity.setFallbackName(StringUtils.equals(visibleName, node.getName()));
    entity.setCollisionResolved(!StringUtils.equals(visibleName, requestedVisibleName));
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

    WebDavPathMappingEntity saved = webDavPathMappingStorage.save(entity);
    if (StringUtils.isNotBlank(oldId) && !StringUtils.equals(oldId, newId)) {
      webDavPathMappingStorage.deleteById(oldId);
    }
    return saved;
  }

  private String findParentVisibleByJcrPath(String jcrPath) {
    String parentJcrPath = getParentJcrPath(jcrPath);
    String visibleName = webDavPathMappingStorage.findByJcrPath(parentJcrPath)
                                                 .map(WebDavPathMappingEntity::getVisibleName)
                                                 .orElse(null);
    if (StringUtils.isNotBlank(visibleName)) {
      return visibleName;
    }
    return webDavPathMappingStorage.findByJcrPath(jcrPath)
                                   .map(WebDavPathMappingEntity::getParentWebDavPath)
                                   .map(this::lastDecodedWebDavSegment)
                                   .orElse(null);
  }

  private Optional<WebDavPathMappingEntity> findByNodeIdentifier(Node node) throws RepositoryException {
    return webDavPathMappingStorage.findByNodeIdentifier(getNodeIdentifier(node));
  }

  private String getNodeIdentifier(Node node) throws RepositoryException {
    return node instanceof NodeImpl nodeImpl ? nodeImpl.getIdentifier() : null;
  }

  private String allocateVisibleName(String parentJcrPath, String requestedVisibleName, String targetJcrPath) {
    String visibleName = requestedVisibleName;
    int counter = 1;
    while (true) {
      Optional<WebDavPathMappingEntity> existing =
                                                 webDavPathMappingStorage.findByParentJcrPathAndNormalizedVisibleName(parentJcrPath,
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
    return StringUtils.removeEnd(parentWebDavPath, "/") + "/" + encodeUrlString(visibleName);
  }

  private String encodeUrlString(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /**
   * Decodes a raw WebDAV path segment. Unlike {@link URLDecoder#decode(String, java.nio.charset.Charset)},
   * which implements {@code application/x-www-form-urlencoded} semantics and wrongly turns a literal
   * '+' into a space, this only unescapes percent-encoded sequences and leaves literal '+' untouched.
   */
  String decodeUrlString(String s) {
    return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  private String normalizeWebDavPath(String webDavPath) {
    if (StringUtils.isBlank(webDavPath) || StringUtils.equals(webDavPath, "/")) {
      return "/";
    }
    String normalized = Arrays.stream(webDavPath.split("/"))
                              .filter(StringUtils::isNotBlank)
                              .map(this::decodeUrlString)
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
                                .map(this::decodeUrlString)
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
                 .map(this::decodeUrlString)
                 .orElse(null);
  }

  private Long getIdentityIdFromJcrPath(String jcrPath) {
    Long identityId = getIdentityIdFromJcrPath(jcrPath, null);
    if (identityId != null) {
      return identityId;
    }

    String usersBasePath = getUsersBaseJcrPath();
    if (StringUtils.startsWith(jcrPath, usersBasePath + "/")) {
      String[] pathParts = StringUtils.removeStart(jcrPath, usersBasePath + "/").split("/"); // NOSONAR
      for (int i = 1; i < pathParts.length; i++) {
        if (StringUtils.equals(pathParts[i], PRIVATE_NODE_NAME)) {
          Identity userIdentity = identityManager.getOrCreateUserIdentity(pathParts[i - 1]);
          return userIdentity == null ? null : userIdentity.getIdentityId();
        }
      }
    }
    return null;
  }

  private String getIdentityRootWebDavPath(long identityId) {
    return getIdentityRootWebDavPath(identityId, getIdentitySegmentName(identityManager.getIdentity(identityId)));
  }

  private String getIdentityRootWebDavPath(long identityId, String segmentName) {
    return String.format("/%s%s%s%s",
                         encodeUrlString(toWebDavSegment(segmentName)),
                         IDENTITY_ID_PREFIX,
                         identityId,
                         IDENTITY_ID_SUFFIX);
  }

  /**
   * Returns the name an identity contributes to its WebDAV drive segment:
   * <ul>
   * <li>a Space is addressed by its <b>pretty name</b>, the very name its drive
   * is created under in JCR
   * (<code>/groups/spaces/&lt;prettyName&gt;/Documents</code>): URL-safe by
   * construction and frozen at creation, unlike the Space display name, which a
   * rename can change and which may carry a '/';</li>
   * <li>a personal drive keeps the user <b>full name</b>, which reads far better
   * than a username when the drive is mounted, and which
   * {@link #toWebDavSegment(String)} keeps inside a single path segment.</li>
   * </ul>
   *
   * @param identity {@link Identity} of the drive owner, may be null
   * @return the name the drive is addressed by, never null
   */
  public static String getIdentitySegmentName(Identity identity) {
    if (identity == null) {
      return "";
    }
    if (identity.isSpace()) {
      return StringUtils.defaultIfBlank(identity.getRemoteId(), identity.getId());
    }
    String fullName = identity.getProfile() == null ? null : identity.getProfile().getFullName();
    return StringUtils.firstNonBlank(fullName, identity.getRemoteId(), identity.getId(), "");
  }

  @SneakyThrows
  private void addMappingEventListener() {
    Session session = getSystemSession();
    try {
      ObservationManager observation = session.getWorkspace().getObservationManager();
      WebDavPathMappingUpdaterAction.SUPPORTED_PATHS.forEach(path -> addMappingEventListener(observation,
                                                                                             createMappingListenerInstance(),
                                                                                             path));
    } finally {
      session.logout();
    }
  }

  @SneakyThrows
  private void addMappingEventListener(ObservationManager observation,
                                       WebDavPathMappingUpdaterAction mappingUpdaterAction,
                                       String path) {
    LOG.info("Register WebDAV path mapping listener on '{}'", path);
    observation.addEventListener(mappingUpdaterAction,
                                 WebDavPathMappingUpdaterAction.SUPPORTED_EVENT_TYPES,
                                 path,
                                 true,
                                 null,
                                 WebDavPathMappingUpdaterAction.SUPPORTED_NODE_TYPES.toArray(String[]::new),
                                 false);
  }

  private WebDavPathMappingUpdaterAction createMappingListenerInstance() {
    return new WebDavPathMappingUpdaterAction(this);
  }

  @SneakyThrows
  private Session getSystemSession() {
    ManageableRepository repository = repositoryService.getDefaultRepository();
    return repository.getSystemSession(repository.getConfiguration().getDefaultWorkspaceName());
  }

  private String getGroupsBaseJcrPath() {
    if (groupsJcrBasePath == null) {
      groupsJcrBasePath = nodeHierarchyCreator.getJcrPath(GROUPS_PATH);
    }
    return groupsJcrBasePath;
  }

  private String getUsersBaseJcrPath() {
    if (usersJcrBasePath == null) {
      usersJcrBasePath = nodeHierarchyCreator.getJcrPath(USERS_PATH);
    }
    return usersJcrBasePath;
  }

}
