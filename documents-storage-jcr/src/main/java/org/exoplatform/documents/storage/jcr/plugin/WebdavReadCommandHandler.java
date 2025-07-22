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

import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_HIDDENABLE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_CONTENT;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_CREATED_DATE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_DATA;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_FROZEN_NODE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_LAST_MODIFIED;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_MIME_TYPE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_ROOT_VERSION;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.MIX_VERSIONABLE;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CREATIONDATE;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.DISPLAYNAME;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GETCONTENTLENGTH;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GETCONTENTTYPE;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GETLASTMODIFIED;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.RESOURCETYPE;

import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.Version;
import javax.jcr.version.VersionIterator;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavFileDownload;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.space.model.Space;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;

@Service
public class WebdavReadCommandHandler extends CommandHandler {

  private static final String     VERSION_QUERY_PARAM = "?version=";

  private static final Set<QName> SEARCH_PROPERTIES   = new HashSet<>(Arrays.asList(DISPLAYNAME,
                                                                                    RESOURCETYPE,
                                                                                    CREATIONDATE,
                                                                                    GETLASTMODIFIED,
                                                                                    GETCONTENTLENGTH,
                                                                                    GETCONTENTTYPE));

  @PostConstruct
  @Override
  public void init() {
    super.init();
  }

  @SneakyThrows
  public WebDavItem get(Session session, // NOSONAR
                        String webDavPath,
                        Set<QName> requestedPropertyNames,
                        boolean requestPropertyNamesOnly,
                        int depth,
                        String baseUri,
                        String username) {
    if (StringUtils.equals("/", webDavPath)) {
      WebDavItem result = new WebDavItem();
      result.setJcrPath("/");
      result.setWebDavPath("/");
      result.setFile(false);
      result.setIdentifier(new URI(baseUri + "/"));
      result.addProperty(getIsFolderItemProperty());
      if (depth > 0) {
        addWebDavUserItem(session, requestedPropertyNames, requestPropertyNamesOnly, depth - 1, baseUri, username, result);
        addWebDavSpaceItems(session, requestedPropertyNames, requestPropertyNamesOnly, depth - 1, baseUri, username, result);
      }
      return result;
    } else if (isIdentityRootWebDavPath(webDavPath)) {
      Identity identity = getIdentityFromWebDavPath(webDavPath);
      if (identity != null) {
        return getWebDavIdentityItem(session,
                                     identity.getIdentityId(),
                                     identity.getProfile().getFullName(),
                                     requestedPropertyNames,
                                     requestPropertyNamesOnly,
                                     depth,
                                     baseUri);
      } else {
        throw new WebDavException(HttpStatus.SC_NOT_FOUND, String.format("Can't find an identity Id from path %s", webDavPath));
      }
    } else {
      return get(getNode(session, transformToJcrPath(webDavPath)),
                 getIdentityBaseJcrPath(webDavPath),
                 requestedPropertyNames,
                 requestPropertyNamesOnly,
                 depth,
                 getIdentityBaseUri(baseUri, webDavPath));
    }
  }

  @SneakyThrows
  public WebDavFileDownload download(Session session,
                                     String webDavPath,
                                     String version) {
    Node node = getNode(session, transformToJcrPath(webDavPath), version);
    long lastModifiedDate = getLastModifiedDate(node);
    String mimeType = node.getNode(JCR_CONTENT).getProperty(JCR_MIME_TYPE).getString();
    InputStream inputStream = node.getNode(JCR_CONTENT).getProperty(JCR_DATA).getStream();

    return new WebDavFileDownload(node.getName(),
                                  inputStream.available(),
                                  lastModifiedDate,
                                  mimeType,
                                  inputStream);
  }

  @SneakyThrows
  @SuppressWarnings("unchecked")
  public List<WebDavItem> search(Session session,
                                 String queryLanguage,
                                 String query,
                                 String baseUri,
                                 String username) {
    NodeIterator nodes = session.getWorkspace()
                                .getQueryManager()
                                .createQuery(query, queryLanguage)
                                .execute()
                                .getNodes();
    Iterable<Node> iterable = () -> nodes;
    return StreamSupport.stream(iterable.spliterator(), false)
                        .map(node -> get(node, SEARCH_PROPERTIES, baseUri, username))
                        .filter(Objects::nonNull)
                        .toList();
  }

  @SneakyThrows
  @SuppressWarnings("unchecked")
  public List<WebDavItem> getVersions(Session session, String webDavPath, Set<QName> requestedPropertyNames, String baseUri) {
    String jcrPath = transformToJcrPath(webDavPath);
    Node node = getNode(session, jcrPath);
    if (node.isNodeType(MIX_VERSIONABLE)) {
      VersionIterator versions = node.getVersionHistory().getAllVersions();
      Iterable<Version> iterable = () -> versions;
      String identityBaseJcrPath = getIdentityBaseJcrPath(webDavPath);
      String identityBaseUri = getIdentityBaseUri(baseUri, webDavPath);
      return StreamSupport.stream(iterable.spliterator(), false)
                          .filter(version -> !isRootVersion(version))
                          .map(version -> get(getVersionNode(version),
                                              identityBaseJcrPath,
                                              requestedPropertyNames,
                                              identityBaseUri))
                          .filter(Objects::nonNull)
                          .toList();

    } else {
      return Collections.emptyList();
    }
  }

  @SneakyThrows
  public boolean isFile(Session session, String webDavPath) {
    String jcrPath = transformToJcrPath(webDavPath);
    if (session.itemExists(jcrPath)) {
      Item item = session.getItem(jcrPath);
      if (item instanceof Node node) {
        return isFile(node);
      }
    }
    return false;
  }

  @SneakyThrows
  public long getLastModifiedDate(Session session,
                                  String webDavPath,
                                  String version) {
    String jcrPath = transformToJcrPath(webDavPath);
    Node node = getNode(session, jcrPath, version);
    return getLastModifiedDate(node);
  }

  @SneakyThrows
  private long getLastModifiedDate(Node node) {
    try {
      return node.getNode(JCR_CONTENT).getProperty(JCR_LAST_MODIFIED).getDate().getTimeInMillis();
    } catch (Exception e) {
      return node.hasProperty(JCR_CREATED_DATE) ? node.getProperty(JCR_CREATED_DATE).getDate().getTimeInMillis() : 0;
    }
  }

  private Node getNode(Session session, String jcrPath) {
    return getNode(session, jcrPath, null);
  }

  @SneakyThrows
  private Node getNode(Session session, String jcrPath, String version) {
    if (version == null && jcrPath.contains(VERSION_QUERY_PARAM)) {
      version = jcrPath.substring(jcrPath.indexOf(VERSION_QUERY_PARAM) + VERSION_QUERY_PARAM.length());
      jcrPath = jcrPath.substring(0, jcrPath.indexOf(VERSION_QUERY_PARAM));
    }
    if (!session.itemExists(jcrPath)) {
      throw new WebDavException(HttpStatus.SC_NOT_FOUND,
                                String.format("Resource with path '%s' not found", jcrPath));
    }
    Item item = session.getItem(jcrPath);
    if (!(item instanceof Node node)) {
      throw new WebDavException(HttpStatus.SC_BAD_REQUEST,
                                String.format("Resource with path '%s' isn't a node", jcrPath));
    }
    if (version != null) {
      Version nodeVersion = node.getVersionHistory().getVersion(version);
      node = getVersionNode(nodeVersion);
    }
    return node;
  }

  @SneakyThrows
  private WebDavItem get(Node node,
                         Set<QName> requestedPropertyNames,
                         String baseUri,
                         String username) {
    String jcrPath = node.getPath();
    Long identityId = getIdentityIdFromJcrPath(jcrPath, username);
    return identityId == null ? null :
                              get(node,
                                  getIdentityBaseJcrPath(identityId),
                                  requestedPropertyNames,
                                  0,
                                  getIdentityBaseUri(baseUri, identityId));
  }

  @SneakyThrows
  private WebDavItem get(Node node,
                         String identityBaseJcrPath,
                         Set<QName> requestedPropertyNames,
                         String identityBaseUri) {
    return get(node, identityBaseJcrPath, requestedPropertyNames, 0, identityBaseUri);
  }

  @SneakyThrows
  private WebDavItem get(Node node,
                         String identityBaseJcrPath,
                         Set<QName> requestedPropertyNames,
                         int depth,
                         String identityBaseUri) {
    return get(node, identityBaseJcrPath, requestedPropertyNames, false, depth, identityBaseUri);
  }

  @SneakyThrows
  private WebDavItem get(Node node,
                         String identityBaseJcrPath,
                         Set<QName> requestedPropertyNames,
                         boolean requestPropertyNamesOnly,
                         int depth,
                         String identityBaseUri) {
    WebDavItem result = new WebDavItem();
    result.setFile(isFile(node));
    result.setIdentifier(new URI(getNodeUri(node, identityBaseJcrPath, identityBaseUri)));
    List<String> pathParts = Arrays.stream(identityBaseUri.split("/")).filter(StringUtils::isNotBlank).toList();
    String identityId = pathParts.getLast();
    result.setJcrPath(node.getPath());
    result.setWebDavPath(getRelativeNodeUri(node, identityBaseJcrPath, Long.parseLong(identityId)));
    addChildren(result,
                node,
                identityBaseJcrPath,
                requestedPropertyNames,
                requestPropertyNamesOnly,
                depth,
                identityBaseUri);
    addProperties(result,
                  node,
                  requestedPropertyNames,
                  requestPropertyNamesOnly);
    return result;
  }

  @SuppressWarnings("unchecked")
  private void addChildren(WebDavItem webDavItem,
                           Node node,
                           String identityBaseJcrPath,
                           Set<QName> requestedPropertyNames,
                           boolean requestPropertyNamesOnly,
                           int depth,
                           String identityBaseUri) throws RepositoryException {
    if (depth > 0 && node.hasNodes()) {
      NodeIterator nodes = node.getNodes();
      Iterable<Node> iterable = () -> nodes;
      StreamSupport.stream(iterable.spliterator(), false)
                   .filter(childNode -> (isFile(childNode) || isFolder(childNode)) && !isHidden(childNode))
                   .map(childNode -> get(childNode,
                                         identityBaseJcrPath,
                                         requestedPropertyNames,
                                         requestPropertyNamesOnly,
                                         depth - 1,
                                         identityBaseUri))
                   .forEach(webDavItem::addChild);
    }
  }

  private void addProperties(WebDavItem result,
                             Node node,
                             Set<QName> requestedPropertyNames,
                             boolean requestPropertyNamesOnly) {
    Collection<QName> propertyNames = requestedPropertyNames == null ? PROPERTY_NAMES : requestedPropertyNames;
    propertyNames.stream()
                 .map(name -> requestPropertyNamesOnly ? new WebDavItemProperty(name) :
                                                       getWebDavPropertyNoException(node, result.getIdentifier(), null, name))
                 .filter(Objects::nonNull)
                 .forEach(result::addProperty);
  }

  @SneakyThrows
  private Node getVersionNode(Version version) {
    return version.getNode(JCR_FROZEN_NODE);
  }

  @SneakyThrows
  private boolean isRootVersion(Version version) {
    return JCR_ROOT_VERSION.equals(version.getName());
  }

  protected void addWebDavUserItem(Session session,
                                   Set<QName> requestedPropertyNames,
                                   boolean requestPropertyNamesOnly,
                                   int depth,
                                   String baseUri,
                                   String username,
                                   WebDavItem result) {
    Identity userIdentity = identityManager.getOrCreateUserIdentity(username);
    WebDavItem webDavIdentityItem = getWebDavIdentityItem(session,
                                                          userIdentity.getIdentityId(),
                                                          userIdentity.getProfile().getFullName(),
                                                          requestedPropertyNames,
                                                          requestPropertyNamesOnly,
                                                          depth,
                                                          baseUri);
    if (webDavIdentityItem != null) {
      result.addChild(webDavIdentityItem);
    }
  }

  @SneakyThrows
  protected void addWebDavSpaceItems(Session session,
                                     Set<QName> requestedPropertyNames,
                                     boolean requestPropertyNamesOnly,
                                     int depth,
                                     String baseUri,
                                     String username,
                                     WebDavItem result) {
    ListAccess<Space> memberSpacesListAccess = spaceService.getMemberSpaces(username);
    int memberSpacesSize = memberSpacesListAccess.getSize();
    if (memberSpacesSize > 0) {
      List<String> memberSpacesIds = spaceService.getMemberSpacesIds(username, 0, memberSpacesSize);
      for (String spaceId : memberSpacesIds) {
        Space space = spaceService.getSpaceById(spaceId);
        WebDavItem webDavIdentityItem = getWebDavIdentityItem(session,
                                                              identityManager.getOrCreateSpaceIdentity(space.getPrettyName()),
                                                              requestedPropertyNames,
                                                              requestPropertyNamesOnly,
                                                              depth,
                                                              baseUri);
        if (webDavIdentityItem != null) {
          result.addChild(webDavIdentityItem);
        }
      }
    }
  }

  @SneakyThrows
  protected WebDavItem getWebDavIdentityItem(Session session,
                                             Identity identity,
                                             Set<QName> requestedPropertyNames,
                                             boolean requestPropertyNamesOnly,
                                             int depth,
                                             String baseUri) {
    long identityId = identity.getIdentityId();
    String displayName = identity.getProfile().getFullName();
    return getWebDavIdentityItem(session,
                                 identityId,
                                 displayName,
                                 requestedPropertyNames,
                                 requestPropertyNamesOnly,
                                 depth,
                                 baseUri);
  }

  @SneakyThrows
  protected WebDavItem getWebDavIdentityItem(Session session, // NOSONAR
                                             long identityId,
                                             String displayName,
                                             Set<QName> requestedPropertyNames,
                                             boolean requestPropertyNamesOnly,
                                             int depth,
                                             String baseUri) {
    WebDavItem identityWebDavItem = new WebDavItem();
    String identityBaseUri = String.format(PATHS_CONCAT_FORMAT, baseUri, identityId);
    identityWebDavItem.setIdentifier(new URI(identityBaseUri));
    identityWebDavItem.setFile(false);
    identityWebDavItem.addProperty(new WebDavItemProperty(DISPLAYNAME, displayName));
    String identityBaseJcrPath = getIdentityBaseJcrPath(identityId);
    if (session.itemExists(identityBaseJcrPath)) {
      Node identityParentNode = (Node) session.getItem(identityBaseJcrPath);
      Set<QName> identityRequestedPropertyNames = null;
      if (requestedPropertyNames != null) {
        identityRequestedPropertyNames = requestedPropertyNames.stream()
                                                               .filter(p -> !DISPLAYNAME.equals(p))
                                                               .collect(Collectors.toSet());
      }
      identityWebDavItem.setJcrPath(identityParentNode.getPath());
      identityWebDavItem.setWebDavPath(getRelativeNodeUri(identityParentNode, identityBaseJcrPath, identityId));
      addProperties(identityWebDavItem,
                    identityParentNode,
                    identityRequestedPropertyNames,
                    requestPropertyNamesOnly);
      addChildren(identityWebDavItem,
                  identityParentNode,
                  identityBaseJcrPath,
                  requestedPropertyNames,
                  requestPropertyNamesOnly,
                  depth,
                  identityBaseUri);
      return identityWebDavItem;
    } else {
      return null;
    }
  }

  @SneakyThrows
  private boolean isHidden(Node node) {
    return node.isNodeType(EXO_HIDDENABLE);
  }

}
