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

import static org.exoplatform.documents.storage.jcr.util.ACLProperties.getReadOnlyACL;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_DATE_CREATED;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_DATE_MODIFIED;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_HIDDENABLE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_LAST_MODIFIED_DATE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_CONTENT;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_CREATED_DATE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_DATA;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_ENCODING;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_FROZEN_NODE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_LAST_MODIFIED;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_MIME_TYPE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_ROOT_VERSION;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.MIX_LOCKABLE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.MIX_VERSIONABLE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_FOLDER;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_UNSTRUCTURED;
import static org.exoplatform.documents.storage.jcr.util.Utils.decodeString;
import static org.exoplatform.documents.storage.jcr.util.Utils.getStringProperty;
import static org.exoplatform.documents.storage.jcr.webdav.plugin.PathCommandHandler.IDENTITY_PATHS_FORMAT;
import static org.exoplatform.documents.storage.jcr.webdav.plugin.PathCommandHandler.LOG;
import static org.exoplatform.documents.storage.jcr.webdav.plugin.PathCommandHandler.PROPERTY_NAMES;
import static org.exoplatform.documents.storage.jcr.webdav.plugin.PathCommandHandler.getIdentitySegmentName;
import static org.exoplatform.documents.storage.jcr.webdav.plugin.PathCommandHandler.toWebDavSegment;
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
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.HREF;
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
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.getIsFolderItemProperty;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.getLockDiscovery;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.getSupportedLock;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.getSupportedMethodSet;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
import org.springframework.stereotype.Component;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.documents.storage.jcr.util.ACLProperties;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.documents.storage.jcr.webdav.model.JcrNamespaceContext;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavFileDownload;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.model.constant.FileConstants;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import lombok.SneakyThrows;

@Component
public class WebdavReadCommandHandler {

  private static final String     VERSION_QUERY_PARAM     = "?version=";

  private static final String     IDENTITY_ID_PREFIX      = "%20%28";

  private static final String     IDENTITY_ID_SUFFIX      = "%29";

  private static final String     EXO_HIDDEN_PROPERTY     = "exo:hidden";

  private static final String     WINDOWS_HIDDEN_PROPERTY = "lp1:win32FileAttributes";

  private static final Set<QName> SEARCH_PROPERTIES       = new HashSet<>(Arrays.asList(DISPLAYNAME,
                                                                                        RESOURCETYPE,
                                                                                        CREATIONDATE,
                                                                                        GETLASTMODIFIED,
                                                                                        GETCONTENTLENGTH,
                                                                                        GETCONTENTTYPE));

  private PathCommandHandler      pathCommandHandler;

  private IdentityManager         identityManager;

  private SpaceService            spaceService;

  public WebdavReadCommandHandler(IdentityManager identityManager,
                                  SpaceService spaceService,
                                  PathCommandHandler pathCommandHandler) {
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.pathCommandHandler = pathCommandHandler;
  }

  public WebDavItem get(Session session, // NOSONAR
                        String webDavPath,
                        Set<QName> requestedPropertyNames,
                        boolean requestPropertyNamesOnly,
                        int depth,
                        String baseUri,
                        String username) throws WebDavException {
    if (StringUtils.equals("/", webDavPath)) {
      WebDavItem result = new WebDavItem();
      result.setJcrPath("/");
      result.setWebDavPath("/");
      result.setFile(false);
      result.setIdentifier(getBaseUri(baseUri));
      result.addProperty(getIsFolderItemProperty());
      result.addProperty(getReadOnlyACL());
      if (depth > 0) {
        addWebDavUserItem(session, requestedPropertyNames, requestPropertyNamesOnly, depth - 1, baseUri, username, result);
        addWebDavSpaceItems(session, requestedPropertyNames, requestPropertyNamesOnly, depth - 1, baseUri, username, result);
      }
      return result;
    } else if (pathCommandHandler.isIdentityRootWebDavPath(webDavPath)) {
      Identity identity = getIdentityFromWebDavPath(webDavPath);
      if (identity == null) {
        throw new WebDavException(HttpStatus.SC_NOT_FOUND, String.format("Can't find an identity Id from path %s", webDavPath));
      } else {
        return getWebDavIdentityItem(session,
                                     identity.getIdentityId(),
                                     identity.getProfile().getFullName(),
                                     requestedPropertyNames,
                                     requestPropertyNamesOnly,
                                     depth,
                                     baseUri);
      }
    } else {
      Identity identity = getIdentityFromWebDavPath(webDavPath);
      if (identity == null) {
        throw new WebDavException(HttpStatus.SC_NOT_FOUND, String.format("Can't find an identity Id from path %s", webDavPath));
      } else {
        return get(getNode(session, pathCommandHandler.resolveToJcrPath(session, webDavPath)),
                   pathCommandHandler.getIdentityBaseJcrPath(webDavPath),
                   identity.getIdentityId(),
                   getIdentitySegmentName(identity),
                   requestedPropertyNames,
                   requestPropertyNamesOnly,
                   depth,
                   getIdentityBaseUri(baseUri, webDavPath));
      }
    }
  }


  @SneakyThrows
  public String getWebDavPath(Session session, String jcrPath, String username) throws WebDavException {
    Node node = getNode(session, jcrPath);
    String webDavPath = pathCommandHandler.getOrCreateWebDavPath(node);
    if (StringUtils.isBlank(webDavPath)) {
      throw new WebDavException(HttpStatus.SC_NOT_FOUND,
                                String.format("Cannot compute WebDAV path for JCR path '%s'", jcrPath));
    }
    return webDavPath;
  }

  @SneakyThrows
  public WebDavFileDownload download(Session session,
                                     String webDavPath,
                                     String version) {
    Node node = getNode(session, pathCommandHandler.resolveToJcrPath(session, webDavPath), version);
    Calendar lastModifiedDate = getLastModifiedDate(node);
    String mimeType = node.getNode(JCR_CONTENT).getProperty(JCR_MIME_TYPE).getString();
    InputStream inputStream = node.getNode(JCR_CONTENT).getProperty(JCR_DATA).getStream();
    return new WebDavFileDownload(getVisibleNodeName(node),
                                  inputStream.available(),
                                  lastModifiedDate == null ? 0l : lastModifiedDate.getTimeInMillis(),
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
    String jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
    Node node = getNode(session, jcrPath);
    if (node.isNodeType(MIX_VERSIONABLE)) {
      VersionIterator versions = node.getVersionHistory().getAllVersions();
      Iterable<Version> iterable = () -> versions;
      Identity identity = getIdentityFromWebDavPath(webDavPath);
      String identityBaseJcrPath = pathCommandHandler.getIdentityBaseJcrPath(webDavPath);
      String identityBaseUri = getIdentityBaseUri(baseUri, webDavPath);
      return StreamSupport.stream(iterable.spliterator(), false)
                          .filter(version -> !isRootVersion(version))
                          .map(version -> get(getVersionNode(version),
                                              identityBaseJcrPath,
                                              identity.getIdentityId(),
                                              getIdentitySegmentName(identity),
                                              requestedPropertyNames,
                                              false,
                                              0,
                                              identityBaseUri))
                          .filter(Objects::nonNull)
                          .toList();

    } else {
      return Collections.emptyList();
    }
  }

  @SneakyThrows
  public boolean isFile(Session session, String webDavPath) {
    String jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
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
    String jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
    Node node = getNode(session, jcrPath, version);
    Calendar lastModifiedDate = getLastModifiedDate(node);
    return lastModifiedDate == null ? 0l : lastModifiedDate.getTimeInMillis();
  }

  @SneakyThrows
  private Calendar getLastModifiedDate(Node node) {
    Calendar modifiedCalendar = null;
    if (isFile(node)) {
      Node contentNode = node.getNode(JCR_CONTENT);
      if (contentNode.hasProperty(JCR_LAST_MODIFIED)) {
        modifiedCalendar = contentNode.getProperty(JCR_LAST_MODIFIED).getDate();
      }
    }
    if (modifiedCalendar == null) {
      if (node.hasProperty(EXO_DATE_MODIFIED)) {
        modifiedCalendar = node.getProperty(EXO_DATE_MODIFIED).getDate();
      } else if (node.hasProperty(EXO_LAST_MODIFIED_DATE)) {
        modifiedCalendar = node.getProperty(EXO_LAST_MODIFIED_DATE).getDate();
      }
    }
    return modifiedCalendar;
  }

  @SneakyThrows
  private Calendar getCreatedDate(Node node) {
    Calendar createdCalendar = null;
    if (node.hasProperty(JCR_CREATED_DATE)) {
      createdCalendar = node.getProperty(JCR_CREATED_DATE).getDate();
    } else if (node.hasProperty(EXO_DATE_CREATED)) {
      createdCalendar = node.getProperty(EXO_DATE_CREATED).getDate();
    }
    return createdCalendar;
  }

  private Node getNode(Session session, String jcrPath) throws WebDavException {
    return getNode(session, jcrPath, null);
  }

  @SneakyThrows
  private Node getNode(Session session, String jcrPath, String version) throws WebDavException {
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
    Long identityId = pathCommandHandler.getIdentityIdFromJcrPath(node.getPath(), username);
    if (identityId == null) {
      return null;
    } else {
      Identity identity = identityManager.getIdentity(identityId);
      return get(node,
                 pathCommandHandler.getIdentityBaseJcrPath(identityId),
                 identity.getIdentityId(),
                 getIdentitySegmentName(identity),
                 requestedPropertyNames,
                 false,
                 0,
                 getIdentityBaseUri(baseUri, identityId));
    }
  }

  @SneakyThrows
  private WebDavItem get(Node node, // NOSONAR
                         String identityBaseJcrPath,
                         long identityId,
                         String segmentName,
                         Set<QName> requestedPropertyNames,
                         boolean requestPropertyNamesOnly,
                         int depth,
                         String identityBaseUri) {
    if (FileConstants.BLOCKED_FILES_PATTERN.matcher(node.getName()).find()) {
      return null;
    }
    WebDavItem result = new WebDavItem();
    result.setFile(isFile(node));
    String webDavPath = getMappedWebDavPath(node, identityBaseJcrPath, identityId, segmentName);
    String identityRootWebDavPath = getIdentityRootWebDavPath(identityId, segmentName);
    String identifier = identityBaseUri;
    if (!StringUtils.equals(webDavPath, identityRootWebDavPath)) {
      identifier = identityBaseUri + webDavPath.substring(identityRootWebDavPath.length());
    }
    result.setIdentifier(new URI(identifier));
    result.setWebDavPath(webDavPath);
    result.setJcrPath(node.getPath());
    addChildren(result,
                node,
                identityBaseJcrPath,
                identityId,
                segmentName,
                requestedPropertyNames,
                requestPropertyNamesOnly,
                depth,
                identityBaseUri);
    addProperties(result,
                  node,
                  requestedPropertyNames,
                  null,
                  requestPropertyNamesOnly);
    return result;
  }

  @SuppressWarnings("unchecked")
  private void addChildren(WebDavItem webDavItem, // NOSONAR
                           Node node,
                           String identityBaseJcrPath,
                           long identityId,
                           String segmentName,
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
                                         identityId,
                                         segmentName,
                                         requestedPropertyNames,
                                         requestPropertyNamesOnly,
                                         depth - 1,
                                         identityBaseUri))
                   .filter(Objects::nonNull)
                   .forEach(webDavItem::addChild);
    }
  }

  @SneakyThrows
  protected WebDavItemProperty getWebDavPropertyNoException(Node node, URI nodeIdentifier, Version version, QName name) {
    try {
      return getWebDavProperty(node, nodeIdentifier, version, name);
    } catch (Exception e) {
      if (LOG.isTraceEnabled()) {
        LOG.warn("Error retrieving property from path '{}' with name '{}'",
                 node.getPath(),
                 name,
                 e);
      } else {
        LOG.warn("Error retrieving property from path '{}' with name '{}': {}",
                 node.getPath(),
                 name,
                 e.getMessage());
      }
      return null;
    }
  }

  @SneakyThrows
  private WebDavItemProperty getWebDavProperty(Node node, URI nodeIdentifier, Version version, QName name) { // NOSONAR
    if (name.equals(DISPLAYNAME)) {
      return version == null ? new WebDavItemProperty(name,
                                                      String.format("%s%s",
                                                                    getVisibleNodeName(node),
                                                                    getNodeIndexSuffix(node))) :
                             new WebDavItemProperty(name, decodeString(version.getName()));
    } else if (VERSIONNAME.equals(name)) {
      if (version != null) {
        return new WebDavItemProperty(name, version.getName());
      }
    } else if (VERSIONHISTORY.equals(name)) {
      return new WebDavItemProperty(name);
    } else if (CHECKEDIN.equals(name)) {
      WebDavItemProperty checkedInProperty = new WebDavItemProperty(name);
      WebDavItemProperty href = checkedInProperty.addChild(new WebDavItemProperty(HREF));
      href.setValue(nodeIdentifier.toASCIIString());
      return checkedInProperty;
    } else if (CHECKEDOUT.equals(name)) {
      if (node.isCheckedOut()) {
        return new WebDavItemProperty(name);
      }
    } else if (PREDECESSORSET.equals(name)) {
      if (version != null) {
        Version[] predecessors = version.getPredecessors();
        WebDavItemProperty predecessorsProperty = new WebDavItemProperty(name);
        for (Version curVersion : predecessors) {
          if ("jcr:rootVersion".equals(curVersion.getName())) {
            continue;
          }
          String versionHref = nodeIdentifier.toASCIIString() + "/?version=" + curVersion.getName();
          WebDavItemProperty href = predecessorsProperty.addChild(new WebDavItemProperty(HREF));
          href.setValue(versionHref);
        }
        return predecessorsProperty;
      }
    } else if (SUCCESSORSET.equals(name)) {
      if (version != null) {
        Version[] successors = version.getSuccessors();
        WebDavItemProperty successorsProperty = new WebDavItemProperty(name);
        for (Version curVersion : successors) {
          String versionHref = nodeIdentifier.toASCIIString() + "/?version=" + curVersion.getName();
          WebDavItemProperty href = successorsProperty.addChild(new WebDavItemProperty(HREF));
          href.setValue(versionHref);
        }
        return successorsProperty;
      }
    } else if (name.equals(CREATIONDATE)) {
      Calendar createdDate = getCreatedDate(node);
      if (createdDate != null) {
        WebDavItemProperty creationDate = new WebDavItemProperty(name, createdDate, CREATION_PATTERN);
        creationDate.setAttribute("b:dt", "dateTime.tz");
        return creationDate;
      }
    } else if (name.equals(GETLASTMODIFIED)) {
      Calendar lastModifiedDate = getLastModifiedDate(node);
      if (lastModifiedDate != null) {
        return getWebDavDateProperty(name, lastModifiedDate);
      }
    } else if (name.equals(GET_ETAG)) {
      Calendar lastModifiedDate = getLastModifiedDate(node);
      if (lastModifiedDate != null) {
        return new WebDavItemProperty(name, String.format("W/%s", lastModifiedDate.getTimeInMillis()));
      }
    } else if (name.equals(CHILDCOUNT)) {
      if (isFolder(node)) {
        return new WebDavItemProperty(name, String.valueOf(node.getNodes().getSize()));
      }
    } else if (name.equals(GETCONTENTLENGTH)) {
      if (isFile(node)) {
        return new WebDavItemProperty(name,
                                      String.valueOf(node.getNode(JCR_CONTENT).getProperty(JCR_DATA).getLength()));
      }
    } else if (name.equals(GETCONTENTTYPE)) {
      if (isFile(node)) {
        Node contentNode = node.getNode(JCR_CONTENT);
        if (contentNode.hasProperty(JCR_MIME_TYPE)) {
          String mimeType = contentNode.getProperty(JCR_MIME_TYPE).getString();
          String encoding = null;
          if (contentNode.hasProperty(JCR_ENCODING)) {
            encoding = contentNode.getProperty(JCR_ENCODING).getString();
          }
          return StringUtils.isBlank(encoding) ? new WebDavItemProperty(name, mimeType) :
                                               new WebDavItemProperty(name, mimeType + "; charset=" + encoding);
        }
      }
    } else if (name.equals(HASCHILDREN)) {
      return new WebDavItemProperty(name, isFolder(node) && node.hasNodes() ? "1" : "0");
    } else if (name.equals(ISCOLLECTION)) {
      return new WebDavItemProperty(name, isFolder(node) ? "1" : "0");
    } else if (name.equals(ISFOLDER)) {
      return new WebDavItemProperty(name, isFolder(node) ? "1" : "0");
    } else if (name.equals(ISROOT)) {
      String identityJcrPath = pathCommandHandler.getIdentityBaseFromJcrPath(node.getPath(), node.getSession().getUserID());
      return new WebDavItemProperty(name, node.getPath().equals("/") || node.getPath().equals(identityJcrPath) ? "1" : "0");
    } else if (name.equals(PARENTNAME)) {
      return new WebDavItemProperty(name, getParentVisibleNodeName(node));
    } else if (name.equals(RESOURCETYPE)) {
      if (isFolder(node)) {
        return getIsFolderItemProperty();
      } else {
        return new WebDavItemProperty(name);
      }
    } else if (name.equals(SUPPORTEDLOCK)) {
      if (node.canAddMixin(MIX_LOCKABLE)) {
        return getSupportedLock();
      }
    } else if (name.equals(LOCKDISCOVERY)) {
      if (node.isLocked()) {
        String token = node.getLock().getLockToken();
        String owner = node.getLock().getLockOwner();
        return getLockDiscovery(token, owner, "86400");
      }
    } else if (name.equals(ISVERSIONED)) {
      return new WebDavItemProperty(name, "0");
    } else if (name.equals(SUPPORTEDMETHODSET)) {
      return getSupportedMethodSet();
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
        return new WebDavItemProperty(name, getStringProperty(node, propName));
      } else if (isFile(node) && node.getNode(JCR_CONTENT).hasProperty(propName)) {
        return new WebDavItemProperty(name, getStringProperty(node.getNode(JCR_CONTENT), propName));
      }
    }
    return null;
  }

  @SneakyThrows
  private WebDavItemProperty getWebDavDateProperty(QName name, Calendar lastModifiedDate) {
    WebDavItemProperty lastModified = new WebDavItemProperty(name, lastModifiedDate, MODIFICATION_PATTERN);
    lastModified.setAttribute("b:dt", "dateTime.rfc1123");
    return lastModified;
  }

  @SneakyThrows
  private boolean isFolder(Node node) {
    return node.isNodeType(NT_UNSTRUCTURED) || node.isNodeType(NT_FOLDER);
  }

  @SneakyThrows
  private boolean isFile(Node node) {
    return node.isNodeType(NodeTypeConstants.NT_FILE);
  }

  @SneakyThrows
  private Node getVersionNode(Version version) {
    return version.getNode(JCR_FROZEN_NODE);
  }

  @SneakyThrows
  private boolean isRootVersion(Version version) {
    return JCR_ROOT_VERSION.equals(version.getName());
  }

  @SneakyThrows
  private boolean isHidden(Node node) {
    return node.isNodeType(EXO_HIDDENABLE);
  }

  private void addWebDavUserItem(Session session,
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
  private void addWebDavSpaceItems(Session session,
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
        Identity spaceIdentity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
        // Ensure that old Spaces considers using the Space Display Name
        spaceIdentity.getProfile().setProperty(Profile.FULL_NAME, space.getDisplayName());
        WebDavItem webDavIdentityItem = getWebDavIdentityItem(session,
                                                              spaceIdentity,
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

  private WebDavItem getWebDavIdentityItem(Session session,
                                           Identity identity,
                                           Set<QName> requestedPropertyNames,
                                           boolean requestPropertyNamesOnly,
                                           int depth,
                                           String baseUri) {
    return getWebDavIdentityItem(session,
                                 identity.getIdentityId(),
                                 identity.getProfile().getFullName(),
                                 requestedPropertyNames,
                                 requestPropertyNamesOnly,
                                 depth,
                                 baseUri);
  }

  @SneakyThrows
  private WebDavItem getWebDavIdentityItem(Session session, // NOSONAR
                                           long identityId,
                                           String displayName,
                                           Set<QName> requestedPropertyNames,
                                           boolean requestPropertyNamesOnly,
                                           int depth,
                                           String baseUri) {
    if (StringUtils.isBlank(displayName)) {
      displayName = getDisplayName(identityId);
    }
    WebDavItem identityWebDavItem = new WebDavItem();
    // The drive is addressed by its segment name (Space pretty name / username)
    // and only presented under its display name
    String segmentName = getIdentitySegmentName(identityManager.getIdentity(identityId));
    String identityBaseUri = String.format(IDENTITY_PATHS_FORMAT,
                                           baseUri,
                                           encodeUrlString(toWebDavSegment(segmentName)),
                                           IDENTITY_ID_PREFIX,
                                           identityId,
                                           IDENTITY_ID_SUFFIX);
    identityWebDavItem.setIdentifier(new URI(identityBaseUri));
    identityWebDavItem.setFile(false);
    identityWebDavItem.addProperty(new WebDavItemProperty(DISPLAYNAME, displayName));

    String identityBaseJcrPath = pathCommandHandler.getIdentityBaseJcrPath(identityId);
    if (session.itemExists(identityBaseJcrPath)) {
      Node identityParentNode = (Node) session.getItem(identityBaseJcrPath);
      identityWebDavItem.setJcrPath(identityParentNode.getPath());
      identityWebDavItem.setWebDavPath(getMappedWebDavPath(identityParentNode,
                                                           identityBaseJcrPath,
                                                           identityId,
                                                           segmentName));
      addProperties(identityWebDavItem,
                    identityParentNode,
                    requestedPropertyNames,
                    Collections.singleton(DISPLAYNAME),
                    requestPropertyNamesOnly);
      addChildren(identityWebDavItem,
                  identityParentNode,
                  identityBaseJcrPath,
                  identityId,
                  segmentName,
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
  private void addProperties(WebDavItem result,
                             Node node,
                             Set<QName> requestedPropertyNames,
                             Set<QName> excludePropertyNames,
                             boolean requestPropertyNamesOnly) {
    Collection<QName> propertyNames = requestedPropertyNames == null ? PROPERTY_NAMES : requestedPropertyNames;
    propertyNames.stream()
                 .filter(name -> excludePropertyNames == null || !excludePropertyNames.contains(name))
                 .map(name -> requestPropertyNamesOnly ? new WebDavItemProperty(name) :
                                                       getWebDavPropertyNoException(node, result.getIdentifier(), null, name))
                 .filter(Objects::nonNull)
                 .forEach(result::addProperty);
    if (node.isNodeType(EXO_HIDDENABLE)) {
      // Windows
      result.addProperty(new WebDavItemProperty(WINDOWS_HIDDEN_PROPERTY, "2"));
      // Custom Property
      result.addProperty(new WebDavItemProperty(EXO_HIDDEN_PROPERTY, "1"));
    }
  }

  private String getIdentityRootWebDavPath(long identityId, String segmentName) {
    return String.format("/%s%s%s%s",
                         encodeUrlString(toWebDavSegment(segmentName)),
                         IDENTITY_ID_PREFIX,
                         identityId,
                         IDENTITY_ID_SUFFIX);
  }

  @SneakyThrows
  private String getMappedWebDavPath(Node node,
                                     String identityBaseJcrPath,
                                     long identityId,
                                     String segmentName) {
    String identityRootWebDavPath = getIdentityRootWebDavPath(identityId, segmentName);
    if (StringUtils.equals(node.getPath(), identityBaseJcrPath)) {
      return identityRootWebDavPath;
    } else {
      return pathCommandHandler.getOrCreateWebDavPath(node);
    }
  }

  private Identity getIdentityFromWebDavPath(String webDavPath) {
    Long identityId = pathCommandHandler.getIdentityIdFromWebDavPath(webDavPath);
    return identityId == null ? null : identityManager.getIdentity(identityId);
  }

  private String getIdentityBaseUri(String baseUri, String webDavPath) {
    Long identityId = pathCommandHandler.getIdentityIdFromWebDavPath(webDavPath);
    return getIdentityBaseUri(baseUri, identityId);
  }

  private String getIdentityBaseUri(String baseUri, long identityId) {
    Identity identity = identityManager.getIdentity(identityId);
    return getIdentityBaseUri(baseUri, identity);
  }

  private String getIdentityBaseUri(String baseUri, Identity identity) {
    return String.format(IDENTITY_PATHS_FORMAT,
                         baseUri,
                         encodeUrlString(toWebDavSegment(getIdentitySegmentName(identity))),
                         IDENTITY_ID_PREFIX,
                         identity.getId(),
                         IDENTITY_ID_SUFFIX);
  }

  private String getNodeIndexSuffix(Node node) throws RepositoryException {
    return node.getIndex() > 1 ? String.format("[%s]", node.getIndex()) : "";
  }

  @SneakyThrows
  private URI getBaseUri(String baseUri) {
    return new URI(baseUri + "/"); // NOSONAR
  }

  private String encodeUrlString(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8)
                     .replace("+", "%20");
  }

  private String getDisplayName(long identityId) {
    Identity identity = identityManager.getIdentity(identityId);
    if (identity.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
      return space.getDisplayName();
    } else {
      return identity.getProfile().getFullName();
    }
  }

  private String getVisibleNodeName(Node node) throws RepositoryException {
    return pathCommandHandler.getVisibleName(node);
  }

  private String getParentVisibleNodeName(Node node) throws RepositoryException {
    return pathCommandHandler.getParentVisibleNodeName(node);
  }

}
