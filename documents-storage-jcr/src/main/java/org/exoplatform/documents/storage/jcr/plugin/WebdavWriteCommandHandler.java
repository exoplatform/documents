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

import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_OWNEABLE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_PRIVILEGEABLE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_CONTENT;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_DATA;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_ENCODING;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_LAST_MODIFIED;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_MIME_TYPE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.MIX_LOCKABLE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.MIX_VERSIONABLE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_FILE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_FOLDER;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.NT_RESOURCE;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.getStatusDescription;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.lock.Lock;
import javax.jcr.lock.LockException;
import javax.jcr.version.Version;
import javax.xml.namespace.QName;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.commons.utils.MimeTypeResolver;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavItemOrder;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.model.WebDavLockResponse;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.nodetype.NodeTypeDataManager;
import org.exoplatform.services.jcr.core.nodetype.PropertyDefinitionData;
import org.exoplatform.services.jcr.core.nodetype.PropertyDefinitionDatas;
import org.exoplatform.services.jcr.datamodel.InternalQName;
import org.exoplatform.services.jcr.datamodel.NodeData;
import org.exoplatform.services.jcr.ext.utils.VersionHistoryUtils;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.WorkspaceImpl;
import org.exoplatform.services.jcr.webdav.command.acl.ACLProperties;
import org.exoplatform.services.jcr.webdav.util.InitParamsDefaults;
import org.exoplatform.services.jcr.webdav.util.PropertyConstants;
import org.exoplatform.services.jcr.webdav.util.TextUtil;
import org.exoplatform.services.jcr.webdav.xml.WebDavNamespaceContext;
import org.exoplatform.services.security.IdentityConstants;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;

@Service
public class WebdavWriteCommandHandler extends CommandHandler {

  private static final String     RESOURCE_WITH_PATH_S_NOT_FOUND = "Resource with path '%s' not found";

  private static final Set<QName> NON_REMOVING_PROPS             = new HashSet<>();

  private static final Set<QName> READ_ONLY_PROPS                = new HashSet<>();

  static {
    READ_ONLY_PROPS.add(PropertyConstants.JCR_DATA);
    NON_REMOVING_PROPS.add(PropertyConstants.CREATIONDATE);
    NON_REMOVING_PROPS.add(PropertyConstants.DISPLAYNAME);
    NON_REMOVING_PROPS.add(PropertyConstants.GETCONTENTLANGUAGE);
    NON_REMOVING_PROPS.add(PropertyConstants.GETCONTENTLENGTH);
    NON_REMOVING_PROPS.add(PropertyConstants.GETCONTENTTYPE);
    NON_REMOVING_PROPS.add(PropertyConstants.GETLASTMODIFIED);
    NON_REMOVING_PROPS.add(PropertyConstants.JCR_DATA);
  }

  private MimeTypeResolver mimeTypeResolver = new MimeTypeResolver();

  @PostConstruct
  @Override
  public void init() {
    super.init();
    this.mimeTypeResolver.setDefaultMimeType(InitParamsDefaults.FILE_MIME_TYPE);
  }

  @SneakyThrows
  public void createFolder(Session session,
                           String webDavPath,
                           List<String> mixinTypes) {
    checkNotRoot(webDavPath);
    String jcrPath = transformToJcrPath(webDavPath);
    Node node = session.getRootNode().addNode(TextUtil.relativizePath(jcrPath), NT_FOLDER);
    addMixins(node, mixinTypes);
    session.save();
  }

  @SneakyThrows
  public void saveFile(Session session,
                       String webDavPath,
                       String mediaType,
                       List<String> mixinTypes,
                       InputStream inputStream) {
    checkNotRoot(webDavPath);
    String jcrPath = transformToJcrPath(webDavPath);
    Node node = session.itemExists(jcrPath) ? (Node) session.getItem(jcrPath) : null;
    if (node == null) {
      List<String> pathParts = Arrays.stream(jcrPath.split("/")).filter(StringUtils::isNotBlank).toList();
      String fileName = pathParts.getLast();
      String filePath = StringUtils.join(pathParts.subList(0, pathParts.size() - 1), "/");
      checkResourceExists(session, "/" + filePath);
      node = session.getRootNode().getNode(filePath).addNode(fileName, NT_FILE);
      node.addNode(JCR_CONTENT, NT_RESOURCE);
      if (node.canAddMixin(VersionHistoryUtils.MIX_VERSIONABLE)) {
        node.addMixin(VersionHistoryUtils.MIX_VERSIONABLE);
      }
    } else {
      VersionHistoryUtils.createVersion(node);
    }
    updateContent(node, mediaType, inputStream, mixinTypes);
    session.save();
  }

  @SneakyThrows
  public Map<String, Collection<WebDavItemProperty>> saveProperties(Session session, // NOSONAR
                                                                    String webDavPath,
                                                                    List<WebDavItemProperty> propertiesToSave,
                                                                    List<WebDavItemProperty> propertiesToRemove) throws WebDavException {
    checkNotReadOnly(webDavPath);
    String jcrPath = transformToJcrPath(webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = (Node) session.getItem(jcrPath);
    Map<String, Collection<WebDavItemProperty>> result = new HashMap<>();
    for (int i = 0; i < propertiesToSave.size(); i++) {
      WebDavItemProperty property = propertiesToSave.get(i);
      String statname = getStatusDescription(HTTPStatus.OK);
      try {
        if (property.getStringName().equals(JCR_CONTENT)) {
          for (WebDavItemProperty child : property.getChildren()) {
            if (child.getChildren().isEmpty()) {
              if (node.isNodeType(MIX_VERSIONABLE) && !node.isCheckedOut()) {
                node.checkout();
                node.save();
              }
              Node content = node.getNode(JCR_CONTENT);
              statname = setProperty(content, child);
              Collection<WebDavItemProperty> propSet = result.computeIfAbsent(statname, k -> new HashSet<>());
              WebDavItemProperty jcrContentProp = new WebDavItemProperty(PropertyConstants.JCR_CONTENT);
              jcrContentProp.addChild(new WebDavItemProperty(child.getName()));
              propSet.add(jcrContentProp);
            }
          }
        } else {
          statname = setProperty(node, property);
          Collection<WebDavItemProperty> propSet = result.computeIfAbsent(statname, k -> new HashSet<>());
          propSet.add(new WebDavItemProperty(property.getName()));
        }
      } catch (RepositoryException e) {
        statname = getStatusDescription(HTTPStatus.CONFLICT);
        Collection<WebDavItemProperty> propSet = result.computeIfAbsent(statname, k -> new HashSet<>());
        propSet.add(new WebDavItemProperty(property.getName()));
      }
    }

    for (int i = 0; i < propertiesToRemove.size(); i++) {
      WebDavItemProperty removeProperty = propertiesToRemove.get(i);

      if (NON_REMOVING_PROPS.contains(removeProperty.getName())) {
        String statname = getStatusDescription(HTTPStatus.CONFLICT);
        Collection<WebDavItemProperty> propSet = result.computeIfAbsent(statname, k -> new HashSet<>());
        propSet.add(new WebDavItemProperty(removeProperty.getName()));
      } else if (removeProperty.getStringName().equals(JCR_CONTENT)) {
        for (WebDavItemProperty child : removeProperty.getChildren()) {
          Node content = node.getNode(JCR_CONTENT);
          String statname = removeProperty(content, child);
          Collection<WebDavItemProperty> propSet = result.computeIfAbsent(statname, k -> new HashSet<>());
          WebDavItemProperty jcrContentProp = new WebDavItemProperty(new QName(JCR_CONTENT));
          jcrContentProp.addChild(new WebDavItemProperty(child.getName()));
          propSet.add(jcrContentProp);
        }
      } else {
        String statname = removeProperty(node, removeProperty);
        Collection<WebDavItemProperty> propSet = result.computeIfAbsent(statname, k -> new HashSet<>());
        propSet.add(new WebDavItemProperty(removeProperty.getName()));
      }
    }
    return result;
  }

  @SneakyThrows
  public void delete(Session session, String webDavPath) throws WebDavException {
    checkNotReadOnly(webDavPath);
    String jcrPath = transformToJcrPath(webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = (Node) session.getItem(jcrPath);
    node.remove();
    session.save();
  }

  @SneakyThrows
  public boolean move(Session session,
                      String webDavSourcePath,
                      String webDavTargetPath,
                      boolean overwrite) throws WebDavException {
    checkNotReadOnly(webDavSourcePath);
    checkNotRoot(webDavTargetPath);
    String sourceJcrPath = transformToJcrPath(webDavSourcePath);
    String targetJcrPath = transformToJcrPath(webDavTargetPath);
    checkResourceExists(session, sourceJcrPath);
    boolean itemExists = session.itemExists(targetJcrPath);
    if (itemExists && !overwrite) {
      throw new WebDavException(HttpStatus.SC_CONFLICT, String.format("Resource with path '%s' already exists", targetJcrPath));
    }
    session.move(sourceJcrPath, targetJcrPath);
    session.save();
    return itemExists;
  }

  @SneakyThrows
  public void copy(Session session,
                   String webDavSourcePath,
                   String webDavTargetPath,
                   boolean overwrite,
                   boolean removeDestination) throws WebDavException {
    checkNotRoot(webDavSourcePath);
    checkNotRoot(webDavTargetPath);
    String sourceJcrPath = transformToJcrPath(webDavSourcePath);
    String targetJcrPath = transformToJcrPath(webDavTargetPath);
    checkResourceExists(session, sourceJcrPath);
    boolean itemExists = session.itemExists(targetJcrPath);
    if (itemExists && !overwrite) {
      throw new WebDavException(HttpStatus.SC_CONFLICT, String.format("Resource with path '%s' already exists", targetJcrPath));
    }
    Workspace workspace = session.getWorkspace();
    workspace.copy(sourceJcrPath, targetJcrPath);
  }

  @SneakyThrows
  public void enableVersioning(Session session, String webDavPath) {
    checkNotReadOnly(webDavPath);
    String jcrPath = transformToJcrPath(webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = (Node) session.getItem(jcrPath);
    if (!node.isNodeType(MIX_VERSIONABLE)) {
      node.addMixin(MIX_VERSIONABLE);
      session.save();
    }
  }

  @SneakyThrows
  public void checkin(Session session, String webDavPath) {
    checkNotReadOnly(webDavPath);
    String jcrPath = transformToJcrPath(webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = session.getRootNode().getNode(TextUtil.relativizePath(jcrPath));
    node.checkin();
  }

  @SneakyThrows
  public void checkout(Session session, String webDavPath) throws WebDavException {
    checkNotReadOnly(webDavPath);
    String jcrPath = transformToJcrPath(webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = session.getRootNode().getNode(TextUtil.relativizePath(jcrPath));
    node.checkout();
  }

  @SneakyThrows
  public void uncheckout(Session session, String webDavPath) throws WebDavException {
    checkNotReadOnly(webDavPath);
    String jcrPath = transformToJcrPath(webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = session.getRootNode().getNode(TextUtil.relativizePath(jcrPath));
    Version restoreVersion = node.getBaseVersion();
    node.restore(restoreVersion, true);
  }

  @SneakyThrows
  public WebDavLockResponse lock(Session session,
                                 String webDavPath,
                                 int depth,
                                 boolean bodyIsEmpty,
                                 String username) {
    checkNotReadOnly(webDavPath);
    String jcrPath = transformToJcrPath(webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = (Node) session.getItem(jcrPath);
    if (!node.isNodeType(MIX_LOCKABLE) && node.canAddMixin(MIX_LOCKABLE)) {
      node.addMixin(MIX_LOCKABLE);
      session.save();
    }

    Lock lock;
    if (bodyIsEmpty) {
      lock = node.getLock();
      lock.refresh();
    } else {
      lock = node.lock(depth > 1, false);
    }
    return new WebDavLockResponse(lock.getLockToken(), username);
  }

  @SneakyThrows
  public void unlock(Session session, String webDavPath) {
    checkNotReadOnly(webDavPath);
    String jcrPath = transformToJcrPath(webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = (Node) session.getItem(jcrPath);
    if (node.isLocked()) {
      node.unlock();
      session.save();
    }
  }

  @SneakyThrows
  public boolean order(Session session, // NOSONAR
                       String webDavPath,
                       List<WebDavItemOrder> members) throws WebDavException {
    checkNotReadOnly(webDavPath);
    String jcrPath = transformToJcrPath(webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = (Node) session.getItem(jcrPath);

    for (int i = 0; i < members.size(); i++) {
      WebDavItemOrder member = members.get(i);
      int status;
      try {
        if (node.hasNode(member.getSegment())) {
          if (new QName("DAV:", "last").equals(member.getPosition())) {
            status = HTTPStatus.OK;
          } else {
            String positionedNodeName = getPositionnedNode(node, member);
            if (positionedNodeName != null) {
              session.refresh(false);
              node.orderBefore(member.getSegment(), positionedNodeName);
              session.save();
              status = HTTPStatus.OK;
            } else {
              status = HTTPStatus.NOT_FOUND;
            }
          }
        } else {
          status = HTTPStatus.NOT_FOUND;
        }
      } catch (LockException e) {
        status = HTTPStatus.LOCKED;
      } catch (PathNotFoundException e) {
        status = HTTPStatus.NOT_FOUND;
      } catch (AccessDeniedException e) {
        status = HTTPStatus.FORBIDDEN;
      } catch (RepositoryException e) {
        status = HTTPStatus.INTERNAL_ERROR;
        LOG.warn("Error while ordering member '{}' inside path '{}'. Continue ordering other items.", member, e);
      }
      member.setStatus(status);
    }
    return members.stream().allMatch(m -> m.getStatus() == HTTPStatus.OK);
  }

  @SneakyThrows
  public void changeAcl(Session session, // NOSONAR
                        String webDavPath,
                        WebDavItemProperty requestBody) throws WebDavException {
    checkNotReadOnly(webDavPath);
    String jcrPath = transformToJcrPath(webDavPath);
    checkResourceExists(session, jcrPath);
    NodeImpl node = (NodeImpl) session.getItem(jcrPath);

    boolean isSessionToBeSaved = false;

    boolean nodeIsNotCheckedOut = node.isNodeType(MIX_VERSIONABLE) && !node.isCheckedOut();

    // to set ACL the node necessarily must be exo:owneable
    if (!node.isNodeType(EXO_OWNEABLE)) {
      if (nodeIsNotCheckedOut) {
        node.checkout();
      }
      node.addMixin(EXO_OWNEABLE);
      isSessionToBeSaved = true;
    }

    // to set ACL the node necessarily must be exo:privilegeable
    if (!node.isNodeType(EXO_PRIVILEGEABLE)) {
      if (nodeIsNotCheckedOut) {
        node.checkout();
      }
      node.addMixin(EXO_PRIVILEGEABLE);
      isSessionToBeSaved = true;
    }

    if (isSessionToBeSaved) {
      session.save();
      if (nodeIsNotCheckedOut) {
        node.checkin();
        session.save();
      }
    }

    Map<String, String[]> permissionsToGrant = new HashMap<>();
    Map<String, String[]> permissionsToDeny = new HashMap<>();
    for (WebDavItemProperty ace : requestBody.getChildren()) {
      WebDavItemProperty principalProperty = ace.getChild(ACLProperties.PRINCIPAL);
      if (principalProperty == null) {
        throw new IllegalArgumentException("Malformed ace element (seems that no principal element specified)");
      }

      String principal;
      if (principalProperty.getChild(ACLProperties.HREF) != null) {
        principal = principalProperty.getChild(ACLProperties.HREF).getValue();
      } else if (principalProperty.getChild(ACLProperties.ALL) != null) {
        principal = IdentityConstants.ANY;
      } else {
        throw new IllegalArgumentException("Malformed principal element");
      }

      WebDavItemProperty denyProperty = ace.getChild(ACLProperties.DENY);
      WebDavItemProperty grantProperty = ace.getChild(ACLProperties.GRANT);
      if (denyProperty == null && grantProperty == null) {
        throw new IllegalArgumentException("Malformed ace element (seems that no deny|grant element specified)");
      }
      if (denyProperty != null) {
        permissionsToDeny.put(principal, getPermissions(denyProperty));
      }
      if (grantProperty != null) {
        permissionsToGrant.put(principal, getPermissions(grantProperty));
      }

      // request must not grant and deny the same privilege in a single ace
      // http://www.webdav.org/specs/rfc3744.html#rfc.section.8.1.5
      if (!permissionsToDeny.isEmpty() && !permissionsToGrant.isEmpty()) {
        for (String denyPermission : permissionsToDeny.get(principal)) {
          for (String grantPermission : permissionsToGrant.get(principal)) {
            if (denyPermission.equals(grantPermission)) {
              throw new IllegalArgumentException("Malformed ace element (seems that a client is trying to grant and denay the same privilege in a single ace)");

            }
          }
        }
      }
    }
    for (Entry<String, String[]> entry : permissionsToGrant.entrySet()) {
      node.setPermission(entry.getKey(), entry.getValue());
    }
    for (Entry<String, String[]> entry : permissionsToDeny.entrySet()) {
      for (String p : entry.getValue()) {
        node.removePermission(entry.getKey(), p);
      }
    }
    session.save();
  }

  private void checkNotReadOnly(String webDavPath) throws WebDavException {
    checkNotRoot(webDavPath);
    checkNotIdentityRoot(webDavPath);
  }

  protected void checkNotRoot(String webDavPath) throws WebDavException {
    if (StringUtils.isBlank(webDavPath) || "/".equals(webDavPath)) {
      throw new WebDavException(HttpStatus.SC_FORBIDDEN,
                                String.format("Resource with path '%s' is in ReadOnly state",
                                              webDavPath));
    }
  }

  protected void checkNotIdentityRoot(String webDavPath) throws WebDavException {
    if (isIdentityRootWebDavPath(webDavPath)) {
      throw new WebDavException(HttpStatus.SC_FORBIDDEN,
                                String.format("Resource with path '%s' is in ReadOnly state",
                                              webDavPath));
    }
  }

  protected void checkResourceExists(Session session, String jcrPath) throws RepositoryException, WebDavException {
    if (!session.itemExists(jcrPath)) {
      throw new WebDavException(HttpStatus.SC_NOT_FOUND, String.format(RESOURCE_WITH_PATH_S_NOT_FOUND, jcrPath));
    }
  }

  private void updateContent(Node node,
                             String mediaType,
                             InputStream inputStream,
                             List<String> mixinTypes) throws RepositoryException {
    Node content = node.getNode(JCR_CONTENT);
    String encoding = null;
    if (StringUtils.contains(mediaType, ";")) {
      mediaType = mediaType.split(";")[0].trim();
      encoding = mediaType.split(";")[1].replace("charset", "").replace("=", "").trim();
    }
    if (StringUtils.isNotBlank(mediaType)) {
      content.setProperty(JCR_MIME_TYPE, mediaType);
    } else if (!content.hasProperty(JCR_MIME_TYPE)) {
      content.setProperty(JCR_MIME_TYPE, this.mimeTypeResolver.getMimeType(node.getName()));
    }
    if (StringUtils.isNotBlank(encoding)) {
      content.setProperty(JCR_ENCODING, encoding);
    }
    content.setProperty(JCR_LAST_MODIFIED, Calendar.getInstance());
    content.setProperty(JCR_DATA, inputStream);
    addMixins(node, mixinTypes);
  }

  @SneakyThrows
  private void addMixins(Node node, List<String> mixinTypes) {
    if (CollectionUtils.isNotEmpty(mixinTypes)) {
      for (int i = 0; i < mixinTypes.size(); i++) {
        String mixinType = mixinTypes.get(i);
        if (node.canAddMixin(mixinType)) {
          node.addMixin(mixinType);
        } else {
          LOG.warn("Can't add mixin '{}' in node '{}'. Ignore it.", mixinType, node.getPath());
        }
      }
    }
  }

  @SneakyThrows
  private String getPositionnedNode(Node node, WebDavItemOrder member) {
    NodeIterator nodeIter = node.getNodes();
    Node previousNode = null;
    while (nodeIter.hasNext()) {
      Node currentNode = nodeIter.nextNode();
      if (new QName("DAV:", "first").equals(member.getPosition())) {
        return currentNode.getName();
      } else if (new QName("DAV:", "before").equals(member.getPosition())
                 && previousNode != null
                 && currentNode.getName().equals(member.getPositionSegment())) {
        return previousNode.getName();
      } else if (new QName("DAV:", "after").equals(member.getPosition())
                 && currentNode.getName().equals(member.getPositionSegment())
                 && nodeIter.hasNext()) {
        return nodeIter.nextNode().getName();
      }
      previousNode = currentNode;
    }
    return null;
  }

  private String setProperty(Node node, WebDavItemProperty property) {
    String propertyName = WebDavNamespaceContext.createName(property.getName());
    if (READ_ONLY_PROPS.contains(property.getName())) {
      return getStatusDescription(HTTPStatus.CONFLICT);
    }

    try {
      Workspace ws = node.getSession().getWorkspace();
      NodeTypeDataManager nodeTypeHolder = ((WorkspaceImpl) ws).getNodeTypesHolder();
      NodeData data = (NodeData) ((NodeImpl) node).getData();
      InternalQName propName = ((SessionImpl) node.getSession()).getLocationFactory()
                                                                .parseJCRName(propertyName)
                                                                .getInternalName();
      PropertyDefinitionDatas propdefs =
                                       nodeTypeHolder.getPropertyDefinitions(propName,
                                                                             data.getPrimaryTypeName(),
                                                                             data.getMixinTypeNames());
      if (propdefs == null) {
        throw new RepositoryException();
      }

      PropertyDefinitionData propertyDefinitionData = propdefs.getAnyDefinition();
      if (propertyDefinitionData == null) {
        throw new RepositoryException();
      }
      boolean isMultiValued = propertyDefinitionData.isMultiple();
      if (node.isNodeType(MIX_VERSIONABLE) && !node.isCheckedOut()) {
        node.checkout();
        node.save();
      }
      if (!isMultiValued) {
        node.setProperty(propertyName, property.getValue());
      } else {
        String[] value = new String[1];
        value[0] = property.getValue();
        node.setProperty(propertyName, value);
      }
      node.save();
      return getStatusDescription(HTTPStatus.OK);
    } catch (AccessDeniedException e) {
      return getStatusDescription(HTTPStatus.FORBIDDEN);
    } catch (ItemNotFoundException | PathNotFoundException e) {
      return getStatusDescription(HTTPStatus.NOT_FOUND);
    } catch (RepositoryException e) {
      return getStatusDescription(HTTPStatus.CONFLICT);
    }
  }

  private String removeProperty(Node node, WebDavItemProperty property) {
    try {
      node.getProperty(property.getStringName()).remove();
      node.save();
      return getStatusDescription(HTTPStatus.OK);
    } catch (AccessDeniedException e) {
      return getStatusDescription(HTTPStatus.FORBIDDEN);
    } catch (PathNotFoundException | ItemNotFoundException e) {
      return getStatusDescription(HTTPStatus.NOT_FOUND);
    } catch (RepositoryException e) {
      return getStatusDescription(HTTPStatus.CONFLICT);
    }
  }

  private String[] getPermissions(WebDavItemProperty body) {
    Set<String> permissionsToBeChanged = new HashSet<>();
    if (CollectionUtils.isEmpty(body.getChildren())) {
      throw new IllegalArgumentException("Malformed grant|deny element (seems that no privilige is specified)");
    }
    for (WebDavItemProperty property : body.getChildren()) {
      WebDavItemProperty permissionProperty;
      if (ACLProperties.PRIVILEGE.equals(property.getName())) {
        if (property.getChildren().size() > 1) {
          throw new IllegalArgumentException(
                                             "Malformed privilege name (element privilege must contain only one element)");
        }
        permissionProperty = property.getChild(0);
      } else {
        permissionProperty = property;
      }
      if (ACLProperties.READ.equals(permissionProperty.getName())) {
        permissionsToBeChanged.add(PermissionType.READ);
      } else if (ACLProperties.WRITE.equals(permissionProperty.getName())) {
        permissionsToBeChanged.add(PermissionType.ADD_NODE);
        permissionsToBeChanged.add(PermissionType.SET_PROPERTY);
        permissionsToBeChanged.add(PermissionType.REMOVE);
      } else if (ACLProperties.ALL.equals(permissionProperty.getName())) {
        permissionsToBeChanged.add(PermissionType.READ);
        permissionsToBeChanged.add(PermissionType.ADD_NODE);
        permissionsToBeChanged.add(PermissionType.SET_PROPERTY);
        permissionsToBeChanged.add(PermissionType.REMOVE);
      } else {
        throw new IllegalArgumentException("Malformed privilege element (unsupported privilege name)");
      }
    }
    return permissionsToBeChanged.toArray(new String[0]);
  }

}
