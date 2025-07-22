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
package org.exoplatform.documents.storage.jcr.plugin;

import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_CONTENT;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_CREATED_DATE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_DATA;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_ENCODING;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_LAST_MODIFIED;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_MIME_TYPE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.MIX_LOCKABLE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_FOLDER;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_UNSTRUCTURED;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.ALLOW_METHODS_LIST;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CHECKEDIN;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CHECKEDOUT;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CHILDCOUNT;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CREATIONDATE;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CREATION_PATTERN;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.DISPLAYNAME;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GETCONTENTLENGTH;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GETCONTENTTYPE;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GETLASTMODIFIED;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GET_ETAG;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.HASCHILDREN;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.ISCOLLECTION;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.ISFOLDER;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.ISROOT;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.ISVERSIONED;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.LOCKDISCOVERY;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.MODIFICATION_PATTERN;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.OWNER;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.PARENTNAME;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.PREDECESSORSET;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.RESOURCETYPE;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.SUCCESSORSET;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.SUPPORTEDLOCK;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.SUPPORTEDMETHODSET;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.VERSIONHISTORY;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.VERSIONNAME;

import java.io.Serializable;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.version.Version;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;

import org.exoplatform.commons.cache.future.FutureCache;
import org.exoplatform.commons.cache.future.FutureExoCache;
import org.exoplatform.commons.cache.future.Loader;
import org.exoplatform.documents.storage.jcr.model.JcrNamespaceContext;
import org.exoplatform.documents.storage.jcr.util.ACLProperties;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import lombok.SneakyThrows;

public abstract class CommandHandler {

  protected static final Log                        LOG                                =
                                                        ExoLogger.getLogger(CommandHandler.class);

  protected static final List<QName>                PROPERTY_NAMES                     =
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

  protected static final String                     PATHS_CONCAT_FORMAT                = "%s/%s";

  private static final String                       SUPPORTED_METHOD                   = "supported-method";

  private static final String                       WEBDAV_JCR_PATH_CACHE_NAME         = "webdav.jcrPath";

  private static final String                       WEBDAV_IDENTITY_ID_PATH_CACHE_NAME = "webdav.identityIdByPath";

  private static final String                       GROUPS_PATH                        = "groupsPath";

  private static final String                       USERS_PATH                         = "usersPath";

  @Autowired
  protected IdentityManager                         identityManager;

  @Autowired
  protected SpaceService                            spaceService;

  @Autowired
  private NodeHierarchyCreator                      nodeHierarchyCreator;

  @Autowired
  private SessionProviderService                    sessionProviderService;

  @Autowired
  private CacheService                              cacheService;

  private String                                    usersJcrBasePath;

  private String                                    groupsJcrBasePath;

  private FutureCache<Serializable, String, Object> pathFutureCache;

  private FutureCache<String, Long, String>         identityIdFutureCache;

  protected void init() {
    this.pathFutureCache = new FutureExoCache<>(new Loader<Serializable, String, Object>() {
      @Override
      public String retrieve(Object context, Serializable objectId) throws Exception {
        return switch (objectId) {
        case Long identityId -> getJcrBasePath(identityId);
        case String webDavPath -> getJcrBasePath(webDavPath);
        default -> null;
        };
      }
    }, cacheService.getCacheInstance(WEBDAV_JCR_PATH_CACHE_NAME));
    this.identityIdFutureCache = new FutureExoCache<>(new Loader<String, Long, String>() {
      @Override
      public Long retrieve(String username, String path) throws Exception {
        if (StringUtils.isBlank(username)) {
          return getIdentityId(path);
        } else {
          return getIdentityId(path, username);
        }
      }
    }, cacheService.getCacheInstance(WEBDAV_IDENTITY_ID_PATH_CACHE_NAME));
  }

  @SneakyThrows
  protected WebDavItemProperty getWebDavPropertyNoException(Node node, URI nodeIdentifier, Version version, QName name) {
    try {
      return getWebDavProperty(node, nodeIdentifier, version, name);
    } catch (Exception e) {
      if (LOG.isTraceEnabled()) {
        LOG.debug("Error retrieving property from path '{}' with name '{}'",
                  node.getPath(),
                  name,
                  e);
      } else {
        LOG.trace("Error retrieving property from path '{}' with name '{}': {}",
                  node.getPath(),
                  name,
                  e.getMessage());
      }
      return null;
    }
  }

  @SneakyThrows
  protected WebDavItemProperty getWebDavProperty(Node node, URI nodeIdentifier, Version version, QName name) { // NOSONAR
    if (name.equals(DISPLAYNAME)) {
      return version == null ? new WebDavItemProperty(name,
                                                      String.format("%s%s",
                                                                    decodeValue(node.getName()),
                                                                    getNodeIndexSuffix(node))) :
                             new WebDavItemProperty(name, decodeValue(version.getName()));
    } else if (VERSIONNAME.equals(name)) {
      return version == null ? null : new WebDavItemProperty(name, version.getName());
    } else if (VERSIONHISTORY.equals(name)) {
      return new WebDavItemProperty(name);
    } else if (CHECKEDIN.equals(name)) {
      WebDavItemProperty checkedInProperty = new WebDavItemProperty(name);
      WebDavItemProperty href = checkedInProperty.addChild(new WebDavItemProperty(new QName("DAV:", "href")));
      href.setValue(nodeIdentifier.toASCIIString());
      return checkedInProperty;
    } else if (PREDECESSORSET.equals(name)) {
      Version[] predecessors = version.getPredecessors();
      WebDavItemProperty predecessorsProperty = new WebDavItemProperty(name);
      for (Version curVersion : predecessors) {
        if ("jcr:rootVersion".equals(curVersion.getName())) {
          continue;
        }
        String versionHref = nodeIdentifier.toASCIIString() + "/?version=" + curVersion.getName();
        WebDavItemProperty href = predecessorsProperty.addChild(new WebDavItemProperty(new QName("DAV:", "href")));
        href.setValue(versionHref);
      }
      return predecessorsProperty;
    } else if (SUCCESSORSET.equals(name)) {
      Version[] successors = version.getSuccessors();
      WebDavItemProperty successorsProperty = new WebDavItemProperty(name);
      for (Version curVersion : successors) {
        String versionHref = nodeIdentifier.toASCIIString() + "/?version=" + curVersion.getName();
        WebDavItemProperty href = successorsProperty.addChild(new WebDavItemProperty(new QName("DAV:", "href")));
        href.setValue(versionHref);
      }
      return successorsProperty;
    } else if (name.equals(CREATIONDATE)) {
      WebDavItemProperty creationDate = new WebDavItemProperty(name,
                                                               node.getProperty(JCR_CREATED_DATE).getDate(),
                                                               CREATION_PATTERN);
      creationDate.setAttribute("b:dt", "dateTime.tz");
      return creationDate;
    } else if (name.equals(CHILDCOUNT)) {
      return new WebDavItemProperty(name, String.valueOf(node.getNodes().getSize()));
    } else if (name.equals(GETCONTENTLENGTH)) {
      return new WebDavItemProperty(name, String.valueOf(node.getProperty(JCR_DATA).getLength()));
    } else if (name.equals(GETCONTENTTYPE)) {
      Node contentNode = node.getNode(JCR_CONTENT);
      String mimeType = contentNode.getProperty(JCR_MIME_TYPE).getString();
      if (contentNode.hasProperty(JCR_ENCODING)) {
        String encoding = contentNode.getProperty(JCR_ENCODING).getString();
        if (!encoding.isEmpty()) {
          return new WebDavItemProperty(name, mimeType + "; charset=" + encoding);
        }
      }
      return new WebDavItemProperty(name, mimeType);
    } else if (name.equals(GETLASTMODIFIED)) {
      Calendar modified;
      try {
        modified = node.getProperty(JCR_LAST_MODIFIED).getDate();
      } catch (PathNotFoundException e) {
        modified = node.getProperty(JCR_CREATED_DATE).getDate();
      }
      WebDavItemProperty lastModified = new WebDavItemProperty(name, modified, MODIFICATION_PATTERN);
      lastModified.setAttribute("b:dt", "dateTime.rfc1123");
      return lastModified;
    } else if (name.equals(GET_ETAG)) {
      try {
        Calendar modified = node.getProperty(JCR_LAST_MODIFIED).getDate();
        return modified == null ? null : new WebDavItemProperty(name, String.format("W/%s", modified.getTimeInMillis()));
      } catch (PathNotFoundException e) {
        return null;
      }
    } else if (name.equals(HASCHILDREN)) {
      return new WebDavItemProperty(name, node.hasNodes() ? "1" : "0");
    } else if (name.equals(ISCOLLECTION)) {
      return new WebDavItemProperty(name, isFolder(node) ? "1" : "0");
    } else if (name.equals(ISFOLDER)) {
      return new WebDavItemProperty(name, isFolder(node) ? "1" : "0");
    } else if (name.equals(ISROOT)) {
      return new WebDavItemProperty(name, node.getPath().equals("/") ? "1" : "0");
    } else if (name.equals(PARENTNAME)) {
      return new WebDavItemProperty(name, node.getParent().getName());
    } else if (name.equals(RESOURCETYPE)) {
      if (isFolder(node)) {
        return getIsFolderItemProperty();
      } else {
        return new WebDavItemProperty(name);
      }
    } else if (name.equals(SUPPORTEDLOCK)) {
      if (node.canAddMixin(MIX_LOCKABLE)) {
        return supportedLock();
      }
    } else if (name.equals(LOCKDISCOVERY)) {
      if (node.isLocked()) {
        String token = node.getLock().getLockToken();
        String owner = node.getLock().getLockOwner();
        return lockDiscovery(token, owner, "86400");
      }
    } else if (name.equals(ISVERSIONED)) {
      return new WebDavItemProperty(name, "0");
    } else if (name.equals(SUPPORTEDMETHODSET)) {
      return supportedMethodSet();
    } else if (name.equals(ACLProperties.ACL)) {
      return ACLProperties.getACL((NodeImpl) node);
    } else if (name.equals(OWNER)) {
      return ACLProperties.getOwner((NodeImpl) node);
    } else {
      String propName = JcrNamespaceContext.createName(name);
      LOG.debug("Prop with name '{}' not recognized, attempt to retrieve it from Node as prop name '{}'",
                name,
                propName);
      if (node.hasProperty(propName)) {
        return new WebDavItemProperty(name, getValue(node.getProperty(propName)));
      } else if (node.getNode(JCR_CONTENT).hasProperty(propName)) {
        return new WebDavItemProperty(name, getValue(node.getNode(JCR_CONTENT).getProperty(propName)));
      }
    }
    return null;
  }

  protected WebDavItemProperty getIsFolderItemProperty() {
    WebDavItemProperty collectionProp = new WebDavItemProperty(new QName("DAV:", "collection"));
    WebDavItemProperty resourceType = new WebDavItemProperty(RESOURCETYPE);
    resourceType.addChild(collectionProp);
    return resourceType;
  }

  @SneakyThrows
  protected boolean isFolder(Node node) {
    return node.isNodeType(NT_UNSTRUCTURED) || node.isNodeType(NT_FOLDER);
  }

  @SneakyThrows
  protected boolean isFile(Node node) {
    return node.isNodeType(NodeTypeConstants.NT_FILE);
  }

  @SneakyThrows
  private String getValue(Property property) {
    if (property != null) {
      if (property.getDefinition().isMultiple()) {
        if (property.getValues().length >= 1) {
          return property.getValues()[0].getString();
        }
      } else {
        return property.getString();
      }
    }
    return "";
  }

  private String decodeValue(String value) {
    String currentValue;
    do {
      currentValue = value;
      try {
        value = URLDecoder.decode(value, StandardCharsets.UTF_8);
      } catch (IllegalArgumentException e) {
        LOG.warn("Unable to decode value: ", e.getMessage());
        return value;
      }
    } while (!StringUtils.equals(currentValue, value));
    return value;
  }

  private WebDavItemProperty supportedLock() {
    WebDavItemProperty supportedLock = new WebDavItemProperty(new QName("DAV:", "supportedlock"));

    WebDavItemProperty lockEntry = new WebDavItemProperty(new QName("DAV:", "lockentry"));
    supportedLock.addChild(lockEntry);

    WebDavItemProperty lockScope = new WebDavItemProperty(new QName("DAV:", "lockscope"));
    lockScope.addChild(new WebDavItemProperty(new QName("DAV:", "exclusive")));
    lockEntry.addChild(lockScope);

    WebDavItemProperty lockType = new WebDavItemProperty(new QName("DAV:", "locktype"));
    lockType.addChild(new WebDavItemProperty(new QName("DAV:", "write")));
    lockEntry.addChild(lockType);

    return supportedLock;
  }

  private WebDavItemProperty lockDiscovery(String token, String lockOwner, String timeOut) {
    WebDavItemProperty lockDiscovery = new WebDavItemProperty(new QName("DAV:", "lockdiscovery"));

    WebDavItemProperty activeLock =
                                  lockDiscovery.addChild(new WebDavItemProperty(new QName("DAV:", "activelock")));

    WebDavItemProperty lockType = activeLock.addChild(new WebDavItemProperty(new QName("DAV:", "locktype")));
    lockType.addChild(new WebDavItemProperty(new QName("DAV:", "write")));

    WebDavItemProperty lockScope = activeLock.addChild(new WebDavItemProperty(new QName("DAV:", "lockscope")));
    lockScope.addChild(new WebDavItemProperty(new QName("DAV:", "exclusive")));

    WebDavItemProperty depth = activeLock.addChild(new WebDavItemProperty(new QName("DAV:", "depth")));
    depth.setValue("Infinity");

    if (lockOwner != null) {
      WebDavItemProperty owner = activeLock.addChild(new WebDavItemProperty(new QName("DAV:", "owner")));
      owner.setValue(lockOwner);
    }

    WebDavItemProperty timeout = activeLock.addChild(new WebDavItemProperty(new QName("DAV:", "timeout")));
    timeout.setValue("Second-" + timeOut);

    if (token != null) {
      WebDavItemProperty lockToken = activeLock.addChild(new WebDavItemProperty(new QName("DAV:", "locktoken")));
      WebDavItemProperty lockHref = lockToken.addChild(new WebDavItemProperty(new QName("DAV:", "href")));
      lockHref.setValue(token);
    }

    return lockDiscovery;
  }

  /**
   * The information about supported methods.
   * 
   * @return information about supported methods
   */
  private WebDavItemProperty supportedMethodSet() {
    WebDavItemProperty supportedMethodProp = new WebDavItemProperty(SUPPORTEDMETHODSET);
    ALLOW_METHODS_LIST.forEach(m -> supportedMethodProp.addChild(new WebDavItemProperty(new QName("DAV:", SUPPORTED_METHOD)))
                                                       .setAttribute("name", m));
    return supportedMethodProp;
  }

  protected String getGroupsBaseJcrPath() {
    if (groupsJcrBasePath == null) {
      groupsJcrBasePath = nodeHierarchyCreator.getJcrPath(GROUPS_PATH);
    }
    return groupsJcrBasePath;
  }

  protected String getUsersBaseJcrPath() {
    if (usersJcrBasePath == null) {
      usersJcrBasePath = nodeHierarchyCreator.getJcrPath(USERS_PATH);
    }
    return usersJcrBasePath;
  }

  protected String transformToJcrPath(String webDavPath) {
    Long identityId = getIdentityIdFromWebDavPath(webDavPath);
    if (identityId == null) {
      return "/";
    } else {
      String identityRelativeJcrPath = getIdentityRelativeJcrPath(webDavPath);
      if (StringUtils.isBlank(identityRelativeJcrPath)) {
        return getIdentityBaseJcrPath(identityId);
      } else {
        return String.format(PATHS_CONCAT_FORMAT,
                             getIdentityBaseJcrPath(identityId),
                             identityRelativeJcrPath);
      }
    }
  }

  protected String getIdentityRelativeJcrPath(String webDavPath) {
    String[] pathParts = webDavPath.split("/");
    return Arrays.stream(pathParts)
                 .filter(StringUtils::isNotBlank)
                 .skip(1)
                 .collect(Collectors.joining("/"));
  }

  @SneakyThrows
  protected String getIdentityBaseJcrPath(String webDavPath) {
    return this.pathFutureCache.get(null, webDavPath);
  }

  @SneakyThrows
  protected String getIdentityBaseJcrPath(long identityId) {
    return this.pathFutureCache.get(null, identityId);
  }

  @SneakyThrows
  protected String getNodeUri(Node node, String identityBaseJcrPath, String identityBaseUri) {
    String nodeRelativePath = node.getPath().replaceFirst(identityBaseJcrPath, "");
    if (StringUtils.isBlank(nodeRelativePath)) {
      return identityBaseUri;
    } else {
      String encodedNodeRelativePath = Arrays.stream(nodeRelativePath.split("/"))
                                             .filter(StringUtils::isNotBlank)
                                             .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8)
                                                                 .replace("+", "%20"))
                                             .collect(Collectors.joining("/"));
      return String.format(PATHS_CONCAT_FORMAT, identityBaseUri, encodedNodeRelativePath);
    }
  }

  @SneakyThrows
  protected String getRelativeNodeUri(Node node, String identityBaseJcrPath, long identityId) {
    String nodeRelativePath = node.getPath().replaceFirst(identityBaseJcrPath, "");
    if (StringUtils.isBlank(nodeRelativePath)) {
      return String.format("/%s", identityId);
    } else {
      String encodedNodeRelativePath = Arrays.stream(nodeRelativePath.split("/"))
                                             .filter(StringUtils::isNotBlank)
                                             .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8)
                                                                 .replace("+", "%20"))
                                             .collect(Collectors.joining("/"));
      return String.format("/%s/%s", identityId, encodedNodeRelativePath);
    }
  }

  protected Long getIdentityIdFromJcrPath(String jcrPath, String username) {
    return this.identityIdFutureCache.get(username, jcrPath);
  }

  protected Long getIdentityIdFromWebDavPath(String webDavPath) {
    return this.identityIdFutureCache.get(null, webDavPath);
  }

  protected Identity getIdentityFromWebDavPath(String webDavPath) {
    Long identityId = getIdentityIdFromWebDavPath(webDavPath);
    return identityId == null ? null : identityManager.getIdentity(identityId);
  }

  protected boolean isIdentityRootWebDavPath(String webDavPath) {
    String identityRelativeJcrPath = getIdentityRelativeJcrPath(webDavPath);
    return StringUtils.isBlank(identityRelativeJcrPath);
  }

  protected String getIdentityBaseUri(String baseUri, String webDavPath) {
    return String.format(PATHS_CONCAT_FORMAT, baseUri, getIdentityIdFromWebDavPath(webDavPath));
  }

  protected String getIdentityBaseUri(String baseUri, long identityId) {
    return String.format(PATHS_CONCAT_FORMAT, baseUri, identityId);
  }

  @SneakyThrows
  private String getJcrBasePath(String webDavPath) {
    Long identityId = getIdentityIdFromWebDavPath(webDavPath);
    if (identityId == null) {
      throw new WebDavException(HttpStatus.SC_BAD_REQUEST, String.format("Can't read identity id from path: %s", webDavPath));
    } else {
      return getIdentityBaseJcrPath(identityId);
    }
  }

  @SneakyThrows
  private String getJcrBasePath(long identityId) throws WebDavException {
    Identity identity = identityManager.getIdentity(identityId);
    if (identity == null) {
      throw new WebDavException(HttpStatus.SC_NOT_FOUND,
                                String.format("Identity with id %s not found", identityId));
    } else if (identity.isUser()) {
      SessionProvider systemSessionProvider = sessionProviderService.getSystemSessionProvider(null);
      Node userNode = nodeHierarchyCreator.getUserNode(systemSessionProvider, identity.getRemoteId());
      Node userPrivateNode = userNode.getNode("Private");
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

  private Long getIdentityId(String jcrPath, String username) {
    long identityId;
    if (jcrPath.startsWith(getGroupsBaseJcrPath() + "/spaces")) {
      String[] pathParts = jcrPath.replaceFirst(getGroupsBaseJcrPath() + "/spaces", "").split("/");
      String spaceGroupId = String.format("/spaces/%s", StringUtils.firstNonBlank(pathParts[0], pathParts[1]));
      Space space = spaceService.getSpaceByGroupId(spaceGroupId);
      Identity spaceIdentity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
      identityId = spaceIdentity.getIdentityId();
    } else if (jcrPath.startsWith(getUsersBaseJcrPath()) && jcrPath.contains(String.format("/%s/", username))) {
      Identity userIdentity = identityManager.getOrCreateUserIdentity(username);
      identityId = userIdentity.getIdentityId();
    } else {
      return null;
    }
    return identityId;
  }

  private Long getIdentityId(String webDavPath) {
    if (StringUtils.isBlank(webDavPath) || StringUtils.equals(webDavPath, "/")) {
      return null;
    } else {
      return Long.parseLong(StringUtils.firstNonBlank(webDavPath.split("/")[0],
                                                      webDavPath.split("/")[1]));
    }
  }

  private String getNodeIndexSuffix(Node node) throws RepositoryException {
    return node.getIndex() > 1 ? String.format("[%s]", node.getIndex()) : "";
  }

}
