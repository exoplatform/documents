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

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CHECKEDIN;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CHECKEDOUT;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CHILDCOUNT;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CREATIONDATE;
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
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.OWNER;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.PARENTNAME;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.PREDECESSORSET;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.RESOURCETYPE;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.SUCCESSORSET;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.SUPPORTEDLOCK;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.SUPPORTEDMETHODSET;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.VERSIONHISTORY;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.VERSIONNAME;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;

import org.exoplatform.documents.storage.jcr.util.ACLProperties;
import org.exoplatform.documents.storage.jcr.util.Utils;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import lombok.SneakyThrows;

public abstract class CommandHandler {

  protected static final Log         LOG                                =
                                         ExoLogger.getLogger(CommandHandler.class);

  protected static final List<QName> PROPERTY_NAMES                     =
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

  protected static final String      PATHS_CONCAT_FORMAT                = "%s/%s";

  private static final String        WEBDAV_JCR_PATH_CACHE_NAME         = "webdav.jcrPath";

  private static final String        WEBDAV_IDENTITY_ID_PATH_CACHE_NAME = "webdav.identityIdByPath";

  private static final String        GROUPS_PATH                        = "groupsPath";

  private static final String        USERS_PATH                         = "usersPath";

  @Autowired
  protected IdentityManager          identityManager;

  @Autowired
  protected SpaceService             spaceService;

  @Autowired
  private NodeHierarchyCreator       nodeHierarchyCreator;

  @Autowired
  private SessionProviderService     sessionProviderService;

  private String                     usersJcrBasePath;

  private String                     groupsJcrBasePath;

  @SneakyThrows
  @Cacheable(WEBDAV_JCR_PATH_CACHE_NAME)
  protected String getIdentityBaseJcrPath(String webDavPath) {
    Long identityId = getIdentityIdFromWebDavPath(webDavPath);
    if (identityId == null) {
      throw new WebDavException(HttpStatus.SC_BAD_REQUEST, String.format("Can't read identity id from path: %s", webDavPath));
    } else {
      return getIdentityBaseJcrPath(identityId);
    }
  }

  @SneakyThrows
  @Cacheable(WEBDAV_JCR_PATH_CACHE_NAME)
  protected String getIdentityBaseJcrPath(long identityId) {
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

  @Cacheable(WEBDAV_IDENTITY_ID_PATH_CACHE_NAME)
  protected Long getIdentityIdFromJcrPath(String jcrPath, String username) {
    if (jcrPath.startsWith(getGroupsBaseJcrPath() + "/spaces")) {
      String[] pathParts = jcrPath.replaceFirst(getGroupsBaseJcrPath() + "/spaces", "").split("/");
      String spaceGroupId = String.format("/spaces/%s", StringUtils.firstNonBlank(pathParts[0], pathParts[1]));
      Space space = spaceService.getSpaceByGroupId(spaceGroupId);
      Identity spaceIdentity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
      return spaceIdentity.getIdentityId();
    } else if (jcrPath.startsWith(getUsersBaseJcrPath()) && jcrPath.contains(String.format("/%s/", username))) {
      Identity userIdentity = identityManager.getOrCreateUserIdentity(username);
      return userIdentity.getIdentityId();
    } else {
      return null;
    }
  }

  @Cacheable(WEBDAV_IDENTITY_ID_PATH_CACHE_NAME)
  protected Long getIdentityIdFromWebDavPath(String webDavPath) {
    if (StringUtils.isBlank(webDavPath) || StringUtils.equals(webDavPath, "/")) {
      return null;
    } else {
      return Long.parseLong(StringUtils.firstNonBlank(webDavPath.split("/")[0],
                                                      webDavPath.split("/")[1]));
    }
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

  protected Identity getIdentityFromWebDavPath(String webDavPath) {
    Long identityId = getIdentityIdFromWebDavPath(webDavPath);
    return identityId == null ? null : identityManager.getIdentity(identityId);
  }

  protected boolean isIdentityRootWebDavPath(String webDavPath) {
    String identityRelativeJcrPath = getIdentityRelativeJcrPath(webDavPath);
    return StringUtils.isBlank(identityRelativeJcrPath);
  }

  private String getIdentityRelativeJcrPath(String webDavPath) {
    String[] pathParts = webDavPath.split("/");
    return Arrays.stream(pathParts)
                 .filter(StringUtils::isNotBlank)
                 .map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
                 .map(Utils::encodeNodeName)
                 .skip(1)
                 .collect(Collectors.joining("/"));
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
