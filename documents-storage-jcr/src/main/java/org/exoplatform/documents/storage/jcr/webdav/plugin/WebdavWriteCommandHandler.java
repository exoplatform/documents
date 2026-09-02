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

import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.*;
import static org.exoplatform.documents.storage.jcr.util.Utils.encodeNodeName;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.getStatusDescription;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.AccessControlException;
import java.util.*;
import java.util.Map.Entry;

import javax.jcr.*;
import javax.jcr.lock.Lock;
import javax.jcr.lock.LockException;
import javax.jcr.version.Version;
import javax.xml.namespace.QName;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.stereotype.Component;

import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.utils.MimeTypeResolver;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.documents.webdav.model.WebDavItemOrder;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.model.WebDavLockResponse;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.core.nodetype.NodeTypeDataManager;
import org.exoplatform.services.jcr.core.nodetype.PropertyDefinitionData;
import org.exoplatform.services.jcr.core.nodetype.PropertyDefinitionDatas;
import org.exoplatform.services.jcr.datamodel.InternalQName;
import org.exoplatform.services.jcr.datamodel.NodeData;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.utils.VersionHistoryUtils;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.WorkspaceImpl;
import org.exoplatform.services.jcr.webdav.util.InitParamsDefaults;
import org.exoplatform.services.jcr.webdav.util.PropertyConstants;
import org.exoplatform.services.jcr.webdav.util.TextUtil;
import org.exoplatform.services.jcr.webdav.xml.WebDavNamespaceContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;

@Component
public class WebdavWriteCommandHandler {

  protected static final Log      LOG                            = ExoLogger.getLogger(WebdavWriteCommandHandler.class);

  private static final Set<QName> READ_ONLY_PROPS                = Collections.singleton(PropertyConstants.JCR_DATA);

  private static final Set<QName> NON_REMOVING_PROPS             = new HashSet<>(Arrays.asList(PropertyConstants.CREATIONDATE,
                                                                                               PropertyConstants.DISPLAYNAME,
                                                                                               PropertyConstants.GETCONTENTLANGUAGE,
                                                                                               PropertyConstants.GETCONTENTLENGTH,
                                                                                               PropertyConstants.GETCONTENTTYPE,
                                                                                               PropertyConstants.GETLASTMODIFIED,
                                                                                               PropertyConstants.JCR_DATA));

  private static final String     RESOURCE_WITH_PATH_S_NOT_FOUND = "Resource with path '%s' not found";

  private static final String     PATH_AND_NAME_PATTERN          = "%s/%s";

  private static final Context    LOCK_CONTEXT                   = Context.GLOBAL.id("WebDav");

  private static final Scope      LOCK_SCOPE                     = Scope.APPLICATION.id("WebDavLock");

  private MimeTypeResolver        mimeTypeResolver               = new MimeTypeResolver();

  private RepositoryService       repositoryService;

  private TrashStorage            trashStorage;

  private SessionProviderService  sessionProviderService;

  private SettingService          settingService;

  private PathCommandHandler      pathCommandHandler;

  public WebdavWriteCommandHandler(SessionProviderService sessionProviderService,
                                   RepositoryService repositoryService,
                                   TrashStorage trashStorage,
                                   SettingService settingService,
                                   PathCommandHandler pathCommandHandler) {
    this.sessionProviderService = sessionProviderService;
    this.repositoryService = repositoryService;
    this.trashStorage = trashStorage;
    this.settingService = settingService;
    this.pathCommandHandler = pathCommandHandler;
  }

  @PostConstruct
  public void init() {
    this.mimeTypeResolver.setDefaultMimeType(InitParamsDefaults.FILE_MIME_TYPE);
  }

  @SneakyThrows
  public void createFolder(Session session,
                           String webDavPath,
                           List<String> mixinTypes) {
    checkNotRoot(webDavPath);
    String parentJcrPath = pathCommandHandler.resolveToJcrPath(session, getParentWebDavPath(webDavPath));
    String visibleName = pathCommandHandler.getLastVisibleSegment(webDavPath);
    String technicalName = pathCommandHandler.allocateTechnicalName(session, parentJcrPath, visibleName);
    String jcrPath = String.format(PATH_AND_NAME_PATTERN, parentJcrPath, technicalName);
    Node node = addNode(session, jcrPath, NT_FOLDER);
    if (node.canAddMixin(EXO_SORTABLE)) {
      node.addMixin(EXO_SORTABLE);
    }
    node.setProperty(EXO_NAME, getNodeName(node));
    node.setProperty(EXO_TITLE, visibleName);
    pathCommandHandler.saveMapping(session, webDavPath, visibleName, node);
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
    String jcrPath = null;
    Node node = null;
    try {
      jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
    } catch (WebDavException e) {
      String parentJcrPath = pathCommandHandler.resolveToJcrPath(session, getParentWebDavPath(webDavPath));
      String visibleName = pathCommandHandler.getLastVisibleSegment(webDavPath);
      String technicalName = pathCommandHandler.allocateTechnicalName(session, parentJcrPath, visibleName);
      jcrPath = String.format(PATH_AND_NAME_PATTERN, parentJcrPath, technicalName);
    }
    if (session.itemExists(jcrPath)) {
      Node existingNode = (Node) session.getItem(jcrPath);
      String existingNodeWebDavPath = pathCommandHandler.getOrCreateWebDavPath(existingNode);
      if (StringUtils.equals(pathCommandHandler.decodeUrlString(existingNodeWebDavPath),
                             webDavPath)) {
        node = existingNode;
      } else {
        String parentJcrPath = pathCommandHandler.resolveToJcrPath(session, getParentWebDavPath(webDavPath));
        String visibleName = pathCommandHandler.getLastVisibleSegment(webDavPath);
        String technicalName = pathCommandHandler.allocateTechnicalName(session, parentJcrPath, visibleName);
        jcrPath = String.format(PATH_AND_NAME_PATTERN, parentJcrPath, technicalName);
      }
    }
    Calendar now = Calendar.getInstance();
    if (node == null) {
      node = addNode(session, jcrPath, NT_FILE);
      if (!node.hasNode(JCR_CONTENT)) {
        Node content = node.addNode(JCR_CONTENT, NT_RESOURCE);
        content.setProperty(JCR_LAST_MODIFIED, now);
      }
      if (node.canAddMixin(VersionHistoryUtils.MIX_VERSIONABLE)) {
        node.addMixin(VersionHistoryUtils.MIX_VERSIONABLE);
      }
      if (node.canAddMixin(EXO_SORTABLE)) {
        node.addMixin(EXO_SORTABLE);
      }
      String visibleName = pathCommandHandler.getLastVisibleSegment(webDavPath);
      node.setProperty(EXO_NAME, getNodeName(node));
      node.setProperty(EXO_TITLE, visibleName);
      pathCommandHandler.saveMapping(session, webDavPath, visibleName, node);
    } else {
      forceUnlock(node);
      VersionHistoryUtils.createVersion(node);
    }
    updateContent(node, mediaType, inputStream);
    addMixins(node, mixinTypes);
    session.save();
  }

  @SneakyThrows
  public Map<String, Collection<WebDavItemProperty>> saveProperties(Session session, // NOSONAR
                                                                    String webDavPath,
                                                                    List<WebDavItemProperty> propertiesToSave,
                                                                    List<WebDavItemProperty> propertiesToRemove) throws WebDavException {
    checkNotReadOnly(webDavPath);
    String jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
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
  public void delete(Session session,
                     String webDavPath) throws WebDavException {
    checkNotReadOnly(webDavPath);
    String jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
    checkResourceExists(session, jcrPath);

    Node node = (Node) session.getItem(jcrPath);
    forceUnlock(node);
    if (canRemoveNode(node)) {
      trashStorage.moveToTrash(node,
                               new SessionProvider(((SessionImpl) session).getUserState()));
      pathCommandHandler.deleteMapping(jcrPath);
    } else {
      throw new WebDavException(HttpStatus.SC_FORBIDDEN, String.format("Resource with path '%s' can't be removed", jcrPath));
    }
    session.save();
  }

  @SneakyThrows
  public boolean move(Session session,
                      String webDavSourcePath,
                      String webDavTargetPath,
                      boolean overwrite) throws WebDavException {
    checkNotReadOnly(webDavSourcePath);
    checkNotRoot(webDavTargetPath);
    String sourceJcrPath = pathCommandHandler.resolveToJcrPath(session, webDavSourcePath);
    String targetParentJcrPath = pathCommandHandler.resolveToJcrPath(session, getParentWebDavPath(webDavTargetPath));
    String targetVisibleName = pathCommandHandler.getLastVisibleSegment(webDavTargetPath);
    String targetTechnicalName = pathCommandHandler.allocateTechnicalName(session, targetParentJcrPath, targetVisibleName);
    String targetJcrPath = String.format(PATH_AND_NAME_PATTERN, targetParentJcrPath, targetTechnicalName);
    checkResourceExists(session, sourceJcrPath);
    boolean itemExists = session.itemExists(targetJcrPath);
    if (itemExists) {
      if (overwrite) {
        Node targetNodeToremove = (Node) session.getItem(targetJcrPath);
        targetNodeToremove.remove();
        session.save();
      } else {
        throw new WebDavException(HttpStatus.SC_CONFLICT, String.format("Resource with path '%s' already exists", targetJcrPath));
      }
    }
    forceUnlock((Node) session.getItem(sourceJcrPath));
    session.move(sourceJcrPath, targetJcrPath);
    session.save();
    Node targetNode = (Node) session.getItem(targetJcrPath);
    if (targetNode.isNodeType(EXO_SORTABLE)) {
      targetNode.setProperty(EXO_NAME, getNodeName(targetNode));
      targetNode.setProperty(EXO_TITLE, targetVisibleName);
    }
    pathCommandHandler.deleteMapping(sourceJcrPath);
    pathCommandHandler.saveMapping(session, webDavTargetPath, targetVisibleName, targetNode);
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
    String sourceJcrPath = pathCommandHandler.resolveToJcrPath(session, webDavSourcePath);
    String targetParentJcrPath = pathCommandHandler.resolveToJcrPath(session, getParentWebDavPath(webDavTargetPath));
    String targetVisibleName = pathCommandHandler.getLastVisibleSegment(webDavTargetPath);
    String targetTechnicalName = pathCommandHandler.allocateTechnicalName(session, targetParentJcrPath, targetVisibleName);
    String targetJcrPath = String.format(PATH_AND_NAME_PATTERN, targetParentJcrPath, targetTechnicalName);
    checkResourceExists(session, sourceJcrPath);
    boolean itemExists = session.itemExists(targetJcrPath);
    if (itemExists && removeDestination) {
      Item destItem = session.getItem(targetJcrPath);
      destItem.remove();
      session.save();
    } else if (itemExists && !overwrite) {
      throw new WebDavException(HttpStatus.SC_CONFLICT, String.format("Resource with path '%s' already exists", targetJcrPath));
    }
    Workspace workspace = session.getWorkspace();
    workspace.copy(sourceJcrPath, targetJcrPath);
    Node targetNode = (Node) session.getItem(targetJcrPath);
    if (targetNode.isNodeType(EXO_SORTABLE)) {
      targetNode.setProperty(EXO_NAME, getNodeName(targetNode));
      targetNode.setProperty(EXO_TITLE, targetVisibleName);
    }
    pathCommandHandler.saveMapping(session, webDavTargetPath, targetVisibleName, targetNode);
    session.save();
  }

  @SneakyThrows
  public void enableVersioning(Session session,
                               String webDavPath) {
    checkNotReadOnly(webDavPath);
    String jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = (Node) session.getItem(jcrPath);
    forceUnlock(node);
    if (!node.isNodeType(MIX_VERSIONABLE)) {
      node.addMixin(MIX_VERSIONABLE);
      session.save();
    }
  }

  @SneakyThrows
  public void checkin(Session session,
                      String webDavPath) {
    checkNotReadOnly(webDavPath);
    String jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = session.getRootNode().getNode(TextUtil.relativizePath(jcrPath));
    forceUnlock(node);
    node.checkin();
  }

  @SneakyThrows
  public void checkout(Session session,
                       String webDavPath) throws WebDavException {
    checkNotReadOnly(webDavPath);
    String jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = session.getRootNode().getNode(TextUtil.relativizePath(jcrPath));
    node.checkout();
  }

  @SneakyThrows
  public void uncheckout(Session session,
                         String webDavPath) throws WebDavException {
    checkNotReadOnly(webDavPath);
    String jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = session.getRootNode().getNode(TextUtil.relativizePath(jcrPath));
    Version restoreVersion = node.getBaseVersion();
    node.restore(restoreVersion, true);
  }

  @SneakyThrows
  public WebDavLockResponse lock(Session session,
                                 String webDavPath,
                                 int depth,
                                 int lockTimeout,
                                 boolean bodyIsEmpty,
                                 String username) {
    checkNotReadOnly(webDavPath);
    String jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
    checkResourceExists(session, jcrPath);
    Node node = (Node) session.getItem(jcrPath);
    if (!node.isNodeType(MIX_LOCKABLE) && node.canAddMixin(MIX_LOCKABLE)) {
      node.addMixin(MIX_LOCKABLE);
      session.save();
    }

    Lock lock;
    if (node.isLocked()) {
      lock = node.getLock();

      String lockOwner = lock.getLockOwner();
      if (StringUtils.equals(lockOwner, username)) {
        lock.refresh();
      } else {
        throw new WebDavException(HttpStatus.SC_LOCKED,
                                  String.format("Resource with path '%s' is already locked by a different owner %s",
                                                jcrPath,
                                                lockOwner));
      }
    } else {
      lock = node.lock(depth > 1, false);
    }
    saveLockTimeout(jcrPath, lockTimeout);
    return new WebDavLockResponse(lock.getLockToken(), username);
  }

  @SneakyThrows
  public void unlock(Session session, String webDavPath, List<String> lockTokens) {
    checkNotReadOnly(webDavPath);
    String jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
    checkResourceExists(session, jcrPath);
    try {
      unlockNode(session, jcrPath);
    } catch (Exception e) {
      if (CollectionUtils.isNotEmpty(lockTokens)) {
        lockTokens.forEach(t -> unlockNode(session, jcrPath, t));
      }
    }
  }

  @SneakyThrows
  public void unlockNode(Session session, String jcrPath) {
    if (session.itemExists(jcrPath)) {
      Node node = (Node) session.getItem(jcrPath);
      if (node.isLocked()) {
        node.unlock();
        session.save();
      }
    }
    removeLockTimeout(jcrPath);
  }

  @SneakyThrows
  public boolean order(Session session, // NOSONAR
                       String webDavPath,
                       List<WebDavItemOrder> members) throws WebDavException {
    checkNotReadOnly(webDavPath);
    String jcrPath = pathCommandHandler.resolveToJcrPath(session, webDavPath);
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
  public void removeLockTimeout(String path) {
    settingService.remove(LOCK_CONTEXT, LOCK_SCOPE, path);
  }

  public void saveLockTimeout(String path, long lockTimeout) {
    settingService.set(LOCK_CONTEXT,
                       LOCK_SCOPE,
                       path,
                       SettingValue.create(String.valueOf(System.currentTimeMillis() + lockTimeout * 1000)));
  }

  public List<String> getOutdatedLockedNodePaths() {
    Map<Scope, Map<String, SettingValue<String>>> settings = settingService.getSettingsByContext(LOCK_CONTEXT);
    Map<String, SettingValue<String>> locks = MapUtils.isEmpty(settings) ? Collections.emptyMap() : settings.get(LOCK_SCOPE);
    if (MapUtils.isNotEmpty(locks)) {
      return locks.entrySet()
                  .stream()
                  .filter(e -> Long.parseLong(e.getValue().getValue()) < System.currentTimeMillis())
                  .map(Entry::getKey)
                  .toList();
    } else {
      return Collections.emptyList();
    }
  }

  private void checkNotReadOnly(String webDavPath) throws WebDavException {
    checkNotRoot(webDavPath);
    checkNotIdentityRoot(webDavPath);
  }

  private void checkNotRoot(String webDavPath) throws WebDavException {
    if (StringUtils.isBlank(webDavPath) || "/".equals(webDavPath)) {
      throw new WebDavException(HttpStatus.SC_FORBIDDEN,
                                String.format("Resource with path '%s' is in ReadOnly state",
                                              webDavPath));
    }
  }

  private void checkNotIdentityRoot(String webDavPath) throws WebDavException {
    if (pathCommandHandler.isIdentityRootWebDavPath(webDavPath)) {
      throw new WebDavException(HttpStatus.SC_FORBIDDEN,
                                String.format("Resource with path '%s' is in ReadOnly state",
                                              webDavPath));
    }
  }

  private void checkResourceExists(Session session, String jcrPath) throws RepositoryException, WebDavException {
    if (!session.itemExists(jcrPath)) {
      throw new WebDavException(HttpStatus.SC_NOT_FOUND, String.format(RESOURCE_WITH_PATH_S_NOT_FOUND, jcrPath));
    }
  }

  @SneakyThrows
  private void updateContent(Node node,
                             String mediaType,
                             InputStream inputStream) throws RepositoryException {
    String path = node.getPath();
    Node content = node.getNode(JCR_CONTENT);
    String encoding = null;
    if (StringUtils.contains(mediaType, ";")) {
      String[] mediaTypeParts = mediaType.split(";");
      encoding = mediaTypeParts[1].replace("charset", "")
                                  .replace("=", "")
                                  .replace("\"", "")
                                  .toUpperCase()
                                  .trim();
      mediaType = mediaTypeParts[0].trim();
    }
    String resolvedMimeType = mimeTypeResolver.getMimeType(getVisibleNodeName(node));
    if (StringUtils.isNotBlank(resolvedMimeType)
        && !StringUtils.equals(resolvedMimeType, mimeTypeResolver.getDefaultMimeType())) {
      handleJcrOperation(() -> content.setProperty(JCR_MIME_TYPE, resolvedMimeType), path);
    } else {
      String mediaTypeConstant = StringUtils.firstNonBlank(mediaType, mimeTypeResolver.getDefaultMimeType());
      handleJcrOperation(() -> content.setProperty(JCR_MIME_TYPE, mediaTypeConstant), path);
    }
    if (StringUtils.isNotBlank(encoding)) {
      String encodingConstant = encoding;
      handleJcrOperation(() -> content.setProperty(JCR_ENCODING, encodingConstant), path);
    }
    handleJcrOperation(() -> content.setProperty(JCR_LAST_MODIFIED, Calendar.getInstance()), path);
    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(IOUtils.toByteArray(inputStream));
    handleJcrOperation(() -> content.setProperty(JCR_DATA, byteArrayInputStream), path);
  }

  @SneakyThrows
  private Node addNode(Session session, String jcrPath, String nodeType) {
    List<String> pathParts = Arrays.stream(jcrPath.split("/")).filter(StringUtils::isNotBlank).toList();
    String name = pathParts.getLast();
    String parentPath = StringUtils.join(pathParts.subList(0, pathParts.size() - 1), "/");
    return ((Node) session.getItem("/" + parentPath)).addNode(encodeNodeName(name), nodeType);
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
        return getVisibleNodeName(currentNode);
      } else if (new QName("DAV:", "before").equals(member.getPosition())
                 && previousNode != null
                 && getVisibleNodeName(currentNode).equals(member.getPositionSegment())) {
        return getVisibleNodeName(previousNode);
      } else if (new QName("DAV:", "after").equals(member.getPosition())
                 && getVisibleNodeName(currentNode).equals(member.getPositionSegment())
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
        throw new ItemNotFoundException();
      }

      PropertyDefinitionData propertyDefinitionData = propdefs.getAnyDefinition();
      if (propertyDefinitionData == null) {
        throw new ItemNotFoundException();
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
    } catch (RepositoryException e) {
      return getStatusDescription(HTTPStatus.NOT_FOUND);
    } catch (Exception e) {
      LOG.warn("Error while setting property '{}' value. Continue settings other properties", property.getName(), e);
      return getStatusDescription(HTTPStatus.NOT_FOUND);
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

  @SneakyThrows
  private void unlockNode(Session session, String path, String token) {
    try {
      session.removeLockToken(token);
      removeLockTimeout(path);
    } catch (Exception ex) {
      LOG.warn("Can't unlock file '{}'. Attempt to force unlocking", path, ex);
      Node node = (Node) session.getItem(path);
      forceUnlock(node);
    }
  }

  @SneakyThrows
  private boolean forceUnlock(Node node) {
    if (node.isLocked()) {
      try {
        node.unlock();
        node.getSession().save();
      } catch (Exception e) {
        SessionProvider systemSessionProvider = sessionProviderService.getSystemSessionProvider(null);
        ManageableRepository repository = repositoryService.getDefaultRepository();
        Session systemSession = systemSessionProvider.getSession(repository.getConfiguration().getDefaultWorkspaceName(),
                                                                 repository);
        try {
          Node nodeWithSystemSession = (Node) systemSession.getItem(node.getPath());
          nodeWithSystemSession.unlock();
          systemSession.save();
        } finally {
          systemSession.logout();
          node.getSession().refresh(false);
        }
      }
      removeLockTimeout(node.getPath());
      return true;
    } else {
      return false;
    }
  }

  public String getParentWebDavPath(String webDavPath) {
    if (StringUtils.isBlank(webDavPath) || StringUtils.equals(webDavPath, "/")) {
      return "/";
    }
    String normalizedPath = StringUtils.removeEnd(webDavPath, "/");
    int index = normalizedPath.lastIndexOf('/');
    return index <= 0 ? "/" : normalizedPath.substring(0, index);
  }

  @SneakyThrows
  private boolean canRemoveNode(Node node) {
    // A user must always be able to clear their own Personal Documents space, even when
    // a node inside it carries an ACL that does not grant them REMOVE (e.g. a node
    // created there on somebody else's behalf) — see JCRDocumentsUtil#isInUserPrivateSpace.
    return checkPermission(node, PermissionType.REMOVE)
        || JCRDocumentsUtil.isInUserPrivateSpace(node, node.getSession().getUserID());
  }

  private boolean checkPermission(Node node, String permissionType) {
    try {
      ((ExtendedNode) node).checkPermission(permissionType);
      return true;
    } catch (AccessControlException | RepositoryException e) {
      // ExtendedNode#checkPermission declares both: a denial normally surfaces as the
      // former, not the latter, and only catching RepositoryException let it escape
      // uncaught instead of being reported as a plain "can't remove".
      return false;
    }
  }

  @SneakyThrows
  private void handleJcrOperation(RunnableWithException r, String path) {
    try {
      r.run();
    } catch (RepositoryException e) {
      throw e;
    } catch (Exception e) {
      // Any other exception is a listener exception, thus not thrown
      LOG.warn("Unknown Error occurred while updating File '{}' Data. Continue storing file",
               path,
               e);
    }
  }

  private String getNodeName(Node node) throws RepositoryException {
    return node.getName();
  }

  private String getVisibleNodeName(Node node) throws RepositoryException {
    return pathCommandHandler.getVisibleName(node);
  }

  @FunctionalInterface
  public interface RunnableWithException {
    void run() throws RepositoryException;
  }

}
