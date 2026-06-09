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
import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getPathWithTitles;
import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getTitle;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.*;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_CONTENT;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_DATA;
import static org.exoplatform.documents.storage.jcr.util.Utils.decodeString;
import static org.exoplatform.documents.storage.jcr.util.Utils.getStringProperty;
import static org.exoplatform.documents.storage.jcr.webdav.plugin.PathCommandHandler.*;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.*;

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.jcr.*;
import javax.jcr.version.Version;
import javax.jcr.version.VersionIterator;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.documents.storage.jcr.util.ACLProperties;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.documents.storage.jcr.webdav.model.JcrNamespaceContext;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavFileDownload;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.model.constant.FileConstants;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.util.Text;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import lombok.SneakyThrows;

@Component
public class WebdavReadCommandHandler {

  private static final String     VERSION_QUERY_PARAM     = "?version=";

  private static final String     IDENTITY_ID_PREFIX      = "%20(";

  private static final String     IDENTITY_ID_SUFFIX      = ")";

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
        return get(getNode(session, pathCommandHandler.transformToJcrPath(webDavPath)),
                   pathCommandHandler.getIdentityBaseJcrPath(webDavPath),
                   identity.getIdentityId(),
                   identity.getProfile().getFullName(),
                   requestedPropertyNames,
                   requestPropertyNamesOnly,
                   depth,
                   getIdentityBaseUri(baseUri, webDavPath));
      }
    }
  }

  @SneakyThrows
  public WebDavFileDownload download(Session session,
                                     String webDavPath,
                                     String version) {
    Node node = getNode(session, pathCommandHandler.transformToJcrPath(webDavPath), version);
    Calendar lastModifiedDate = getLastModifiedDate(node);
    String mimeType = node.getNode(JCR_CONTENT).getProperty(JCR_MIME_TYPE).getString();
    InputStream inputStream = node.getNode(JCR_CONTENT).getProperty(JCR_DATA).getStream();

    return new WebDavFileDownload(getTitle(node),
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
    String jcrPath = pathCommandHandler.transformToJcrPath(webDavPath);
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
                                              identity.getProfile().getFullName(),
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
    String jcrPath = pathCommandHandler.transformToJcrPath(webDavPath);
    jcrPath = checkResourceExists(session, jcrPath);
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
    String jcrPath = pathCommandHandler.transformToJcrPath(webDavPath);
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
    jcrPath = checkResourceExists(session, jcrPath);
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
                 identity.getProfile().getFullName(),
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
                         String displayName,
                         Set<QName> requestedPropertyNames,
                         boolean requestPropertyNamesOnly,
                         int depth,
                         String identityBaseUri) {
    if (FileConstants.BLOCKED_FILES_PATTERN.matcher(node.getName()).find()) {
      return null;
    }
    WebDavItem result = new WebDavItem();
    result.setFile(isFile(node));
    result.setIdentifier(new URI(getNodeUri(node, identityBaseJcrPath, identityBaseUri)));
    result.setWebDavPath(getRelativeNodeUri(node, identityBaseJcrPath, identityId, displayName));
    result.setJcrPath(node.getPath());
    addChildren(result,
                node,
                identityBaseJcrPath,
                identityId,
                displayName,
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
                           String displayName,
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
                                         displayName,
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
                                                                    decodeString(getTitle(node)),
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

  @SneakyThrows
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
    String identityBaseUri = String.format(IDENTITY_PATHS_FORMAT,
                                           baseUri,
                                           encodeUrlString(displayName),
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
      identityWebDavItem.setWebDavPath(getRelativeNodeUri(identityParentNode,
                                                          identityBaseJcrPath,
                                                          identityId,
                                                          displayName));
      addProperties(identityWebDavItem,
                    identityParentNode,
                    requestedPropertyNames,
                    Collections.singleton(DISPLAYNAME),
                    requestPropertyNamesOnly);
      addChildren(identityWebDavItem,
                  identityParentNode,
                  identityBaseJcrPath,
                  identityId,
                  displayName,
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

  @SneakyThrows
  private String getNodeUri(Node node, String identityBaseJcrPath, String identityBaseUri) {
    String jcrPath = node.getPath();
    if (node.hasProperty(EXO_TITLE)) {
      String[] parts = jcrPath.split("/");
      parts[parts.length - 1] = node.getProperty(EXO_TITLE).getString();
      jcrPath = Arrays.stream(parts).collect(Collectors.joining("/"));
    }
    String nodeRelativePath = jcrPath.replaceFirst(identityBaseJcrPath, "");
    if (StringUtils.isBlank(nodeRelativePath)) {
      return identityBaseUri;
    } else {
      String encodedNodeRelativePath = Arrays.stream(nodeRelativePath.split("/"))
                                             .filter(StringUtils::isNotBlank)
                                             .map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
                                             .map(this::encodeUrlString)
                                             .collect(Collectors.joining("/"));
      return String.format(PATHS_CONCAT_FORMAT, identityBaseUri, encodedNodeRelativePath);
    }
  }

  @SneakyThrows
  private String getRelativeNodeUri(Node node,
                                    String identityBaseJcrPath,
                                    long identityId,
                                    String displayName) {

    String nodeRelativePath = getPathWithTitles(node).replaceFirst(identityBaseJcrPath, "");
    if (StringUtils.isBlank(nodeRelativePath)) {
      return String.format("/%s%s%s%s",
                           encodeUrlString(displayName),
                           IDENTITY_ID_PREFIX,
                           identityId,
                           IDENTITY_ID_SUFFIX);
    } else {
      String encodedNodeRelativePath = Arrays.stream(nodeRelativePath.split("/"))
                                             .filter(StringUtils::isNotBlank)
                                             .map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8))
                                             .map(this::encodeUrlString)
                                             .collect(Collectors.joining("/"));
      return String.format("/%s%s%s%s/%s",
                           encodeUrlString(displayName),
                           IDENTITY_ID_PREFIX,
                           identityId,
                           IDENTITY_ID_SUFFIX,
                           encodedNodeRelativePath);
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
                         encodeUrlString(identity.getProfile().getFullName()),
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

  private String checkResourceExists(Session session, String path) throws RepositoryException, WebDavException {
    String[] parts = path.split("/");
    String jcrPath = "";
    for (int i = 1; i < parts.length; i++) {
      String parentPath = jcrPath + "/" + parts[i];
      if (!session.itemExists(parentPath)) {
        String cleanName = Text.escapeIllegalJcrChars(JCRDocumentsUtil.cleanNameWithAccents(parts[i].toLowerCase(), NodeTypeConstants.NT_FILE));
        parentPath = jcrPath + "/" + cleanName;
        if (!session.itemExists(parentPath)) {
          cleanName = Text.escapeIllegalJcrChars(JCRDocumentsUtil.cleanName(parts[i].toLowerCase()));
          parentPath = jcrPath + "/" + cleanName;
        }
        if (!session.itemExists(parentPath)) {
          throw new WebDavException(HttpStatus.SC_NOT_FOUND,
                  String.format("Resource with path '%s' not found", jcrPath));
        }
      }
      jcrPath = parentPath;
    }
    return jcrPath;
  }
}
