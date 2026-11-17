/*
 * Copyright (C) 2003-2022 eXo Platform SAS.
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
package org.exoplatform.documents.storage.jcr;

import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.documents.model.TrashElementNodeFilter;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.ActivityTypeUtils;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.services.jcr.impl.core.ItemImpl;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.gatein.pc.api.PortletInvoker;
import org.gatein.pc.api.PortletInvokerException;
import org.gatein.pc.api.info.PortletInfo;
import org.gatein.pc.api.info.PreferencesInfo;

import java.security.AccessControlException;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.*;

public class TrashStorageImpl implements TrashStorage {

  private static final String    FILE_EXPLORER_PORTLET   = "FileExplorerPortlet";

  private static final String    TRASH_WORKSPACE         = "trashWorkspace";

  private static final String    TRASH_HOME_PATH         = "trashHomeNodePath";

  private static final Pattern   TRASH_ID_PATTERN        =
                                                  Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

  private RepositoryService      repositoryService;

  private SessionProviderService sessionProviderService;

  private ListenerService        listenerService;

  private NodeHierarchyCreator   nodeHierarchyCreator;

  private IdentityManager        identityManager;

  private SpaceService           spaceService;

  private String                 trashWorkspace;

  private String                 trashHome;

  /** The log. */
  private static final Log LOG = ExoLogger.getLogger(TrashStorageImpl.class.getName());

  public TrashStorageImpl(RepositoryService repositoryService,
                          SessionProviderService sessionProviderService,
                          ListenerService listenerService,
                          NodeHierarchyCreator nodeHierarchyCreator,
                          IdentityManager identityManager,
                          SpaceService spaceService,
                          InitParams initParams) throws PortletInvokerException {
    this.repositoryService = repositoryService;
    this.sessionProviderService = sessionProviderService;
    this.listenerService = listenerService;
    this.nodeHierarchyCreator = nodeHierarchyCreator;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.trashWorkspace = initParams.getValueParam(TRASH_WORKSPACE).getValue();
    this.trashHome = initParams.getValueParam(TRASH_HOME_PATH).getValue();
    ExoContainer manager = ExoContainerContext.getCurrentContainer();
    PortletInvoker portletInvoker = (PortletInvoker)manager.getComponentInstance(PortletInvoker.class);
    if (portletInvoker != null) {
      Set<org.gatein.pc.api.Portlet> portlets = portletInvoker.getPortlets();
      for (org.gatein.pc.api.Portlet portlet : portlets) {
        PortletInfo info = portlet.getInfo();
        String portletName = info.getName();
        if (FILE_EXPLORER_PORTLET.equalsIgnoreCase(portletName)) {
          PreferencesInfo prefs = info.getPreferences();
          String trashWorkspaceParam = prefs.getPreference(TRASH_WORKSPACE).getDefaultValue().get(0);
          String trashHomeParam = prefs.getPreference(TRASH_HOME_PATH).getDefaultValue().get(0);
          if (trashWorkspaceParam != null && !trashWorkspaceParam.equals(this.trashWorkspace)) {
            this.trashWorkspace = trashWorkspaceParam;
          }

          if (trashHomeParam != null && !trashHomeParam.equals(this.trashHome)) {
            this.trashHome = trashHomeParam;
          }
          break;
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Deprecated(since = "7.3.0", forRemoval = true)
  public String moveToTrash(Node node, SessionProvider sessionProvider) throws RepositoryException {
    return moveToTrash(node, sessionProvider, null, 0);
  }

  /**
   *{@inheritDoc}
   */
  @Override
  @Deprecated(since = "7.3.0", forRemoval = true)
  public String moveToTrash(Node node,
                            SessionProvider sessionProvider,
                            int deep) throws RepositoryException {
    return moveToTrash(node, sessionProvider, null, deep);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String moveToTrash(Node node, SessionProvider sessionProvider, String deletedBy) throws RepositoryException {
    return moveToTrash(node, sessionProvider, deletedBy, 0);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String moveToTrash(Node node,
                            SessionProvider sessionProvider,
                            String deletedBy,
                            int deep) throws RepositoryException {
    Session nodeSession = node.getSession();
    checkRemovePermission(node, nodeSession);
    ((SessionImpl) nodeSession).getActionHandler().preRemoveItem((ItemImpl) node);
    if (node.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION) && isInTrash(node)) {
      return null;
    }
    String nodePath = node.getPath();
    String nodeWorkspaceName = nodeSession.getWorkspace().getName();
    String deleter = deletedBy == null ? getSessionUser(nodeSession) : deletedBy;
    String taxonomyLinkUUID = node.isNodeType(NodeTypeConstants.TAXONOMY_LINK) ? node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString() : null;
    String taxonomyLinkWS = node.isNodeType(NodeTypeConstants.TAXONOMY_LINK) ? node.getProperty(NodeTypeConstants.EXO_WORKSPACE).getString() : null;
    ManageableRepository manageableRepository = repositoryService.getCurrentRepository();

    String trashId;
    // A session of its own, so that a failed move leaves nothing pending for a
    // later save of the same thread.
    SessionProvider trashProvider = createSystemProvider();
    try {
      Session trashSession = trashProvider.getSession(this.trashWorkspace, manageableRepository);
      if (trashSession.getWorkspace().getName().equals(nodeWorkspaceName)) {
        trashId = moveToTrashInOneSave(trashSession, trashProvider, nodePath, nodeWorkspaceName, deleter);
      } else {
        trashId = cloneToTrash(node, trashSession, trashProvider, manageableRepository, nodeWorkspaceName, deleter);
      }
    } finally {
      trashProvider.close();
    }

    //check and delete target node when there is no its symlink
    if (deep == 0 && taxonomyLinkUUID != null && taxonomyLinkWS != null) {
      Session targetNodeSession = sessionProvider.getSession(taxonomyLinkWS, manageableRepository);
      Node targetNode = null;
      try {
        targetNode = targetNodeSession.getNodeByUUID(taxonomyLinkUUID);
      } catch (Exception e) {
        if (LOG.isWarnEnabled()) {
          LOG.warn(e.getMessage());
        }
      }
      if (targetNode != null) {
        List<Node> symlinks = getAllLinks(targetNode, NodeTypeConstants.EXO_SYMLINK, sessionProvider);
        boolean found = false;
        for (Node symlink : symlinks)
          if (!symlink.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION)) {
            found = true;
            break;
          }
        if (!found) {
          this.moveToTrash(targetNode, sessionProvider, deleter);
        }
      }
    }
    return trashId;
  }

  /**
   * Moves a node of the trash workspace, and the shortcuts pointing at it or
   * at one of its descendants, to the trash, and records what the Recycle Bin
   * keeps of them, in one save of the given session. On a failure the pending
   * changes are discarded and nothing has moved.
   */
  private String moveToTrashInOneSave(Session trashSession,
                                      SessionProvider trashProvider,
                                      String nodePath,
                                      String nodeWorkspaceName,
                                      String deleter) throws RepositoryException {
    String trashId = java.util.UUID.randomUUID().toString();
    List<Node> trashedNodes = new ArrayList<>();
    try {
      Node source = (Node) trashSession.getItem(nodePath);
      String restorePath = fixRestorePath(nodePath);
      String ownerId = JCRDocumentsUtil.resolveRecycleBinOwner(restorePath,
                                                              nodeHierarchyCreator,
                                                              identityManager,
                                                              spaceService,
                                                              trashProvider);
      Node destination = getDestination(ownerId, trashSession, trashProvider);
      Calendar deletedDate = Calendar.getInstance();
      boolean broadcastRemoval = isRemovalBroadcast(source);
      List<String> permissions = readInheritedPermissions(source);
      String parentId = getReferenceableParentId(source);
      String name = JCRDocumentsUtil.getTitle(source).toLowerCase();
      Map<String, Node> shortcuts = source.isNodeType(NodeTypeConstants.EXO_SYMLINK) ? null : new TreeMap<>(Comparator.reverseOrder());
      long size = measureItem(source, nodePath, nodeWorkspaceName, trashSession, shortcuts, true);

      Node trashed = moveInto(trashSession, nodePath, destination, source.getName());
      writeRestoreLocation(trashed, restorePath, nodeWorkspaceName, trashId);
      if (!trashed.isNodeType(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM)) {
        trashed.addMixin(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM);
      }
      setOrRemoveProperty(trashed, NodeTypeConstants.RECYCLE_BIN_OWNER, ownerId);
      setOrRemoveProperty(trashed, NodeTypeConstants.RESTORE_PARENT_ID, parentId);
      setOrRemoveProperty(trashed, NodeTypeConstants.DELETED_BY, deleter);
      setOrRemoveProperty(trashed, NodeTypeConstants.RECYCLE_BIN_OF, null);
      trashed.setProperty(NodeTypeConstants.DELETED_DATE, deletedDate);
      trashed.setProperty(NodeTypeConstants.RECYCLE_BIN_SIZE, size);
      trashed.setProperty(NodeTypeConstants.RECYCLE_BIN_NAME, name);
      if (permissions == null) {
        if (trashed.hasProperty(NodeTypeConstants.RECYCLE_BIN_PERMISSIONS)) {
          trashed.getProperty(NodeTypeConstants.RECYCLE_BIN_PERMISSIONS).remove();
        }
      } else {
        trashed.setProperty(NodeTypeConstants.RECYCLE_BIN_PERMISSIONS, permissions.toArray(new String[0]));
      }
      if (broadcastRemoval) {
        trashedNodes.add(trashed);
      }

      if (shortcuts != null) {
        // The paths are walked in descending order, so that moving a shortcut
        // never renumbers a same-name sibling still to move.
        for (Node shortcut : shortcuts.values()) {
          Node trashedShortcut = moveShortcutToTrash(trashSession, shortcut, destination, nodeWorkspaceName, trashId, deleter, deletedDate);
          trashedNodes.add(trashedShortcut);
        }
      }
      trashSession.save();
    } catch (RepositoryException | RuntimeException e) {
      trashSession.refresh(false);
      throw e;
    }
    // The listeners are told once the move is saved: a failing one is logged
    // and does not undo it.
    for (Node trashedNode : trashedNodes) {
      try {
        listenerService.broadcast(FILE_REMOVE_ACTIVITY, null, trashedNode);
      } catch (Exception e) {
        LOG.error(e.getMessage());
      }
    }
    return trashId;
  }

  /**
   * The trash workspace is not the node's: the node is cloned into the trash
   * then removed from its workspace, as two operations. A failure between them
   * leaves the node in both.
   */
  private String cloneToTrash(Node node,
                              Session trashSession,
                              SessionProvider trashProvider,
                              ManageableRepository manageableRepository,
                              String nodeWorkspaceName,
                              String deleter) throws RepositoryException {
    String trashId = java.util.UUID.randomUUID().toString();
    String nodePath = node.getPath();
    String nodeName = node.getName();
    String restorePath = fixRestorePath(nodePath);
    try {
      if (isRemovalBroadcast(node)) {
        listenerService.broadcast(FILE_REMOVE_ACTIVITY, null, node);
      }
    } catch (Exception e) {
      LOG.error(e.getMessage());
    }
    try {
      Session sourceSession = trashProvider.getSession(nodeWorkspaceName, manageableRepository);
      Node source = (Node) sourceSession.getItem(nodePath);
      String ownerId = JCRDocumentsUtil.resolveRecycleBinOwner(restorePath,
                                                              nodeHierarchyCreator,
                                                              identityManager,
                                                              spaceService,
                                                              trashProvider);
      Node destination = getDestination(ownerId, trashSession, trashProvider);
      List<String> permissions = readInheritedPermissions(source);
      String parentId = getReferenceableParentId(source);
      String name = JCRDocumentsUtil.getTitle(source).toLowerCase();
      long size = measureItem(source, nodePath, nodeWorkspaceName, sourceSession, null, false);

      String destinationPath = destination.getPath() + "/" + nodeName;
      trashSession.getWorkspace().clone(nodeWorkspaceName, nodePath, destinationPath, true);
      node.remove();
      Node trashed = getLastSameNameNode(destination, nodeName);
      writeRestoreLocation(trashed, restorePath, nodeWorkspaceName, trashId);
      if (!trashed.isNodeType(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM)) {
        trashed.addMixin(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM);
      }
      setOrRemoveProperty(trashed, NodeTypeConstants.RECYCLE_BIN_OWNER, ownerId);
      setOrRemoveProperty(trashed, NodeTypeConstants.RESTORE_PARENT_ID, parentId);
      setOrRemoveProperty(trashed, NodeTypeConstants.DELETED_BY, deleter);
      trashed.setProperty(NodeTypeConstants.DELETED_DATE, Calendar.getInstance());
      trashed.setProperty(NodeTypeConstants.RECYCLE_BIN_SIZE, size);
      trashed.setProperty(NodeTypeConstants.RECYCLE_BIN_NAME, name);
      if (permissions != null) {
        trashed.setProperty(NodeTypeConstants.RECYCLE_BIN_PERMISSIONS, permissions.toArray(new String[0]));
      }
      trashSession.save();
    } catch (RepositoryException | RuntimeException e) {
      trashSession.refresh(false);
      throw e;
    }
    return trashId;
  }

  private void checkRemovePermission(Node node, Session nodeSession) throws RepositoryException {
    try {
      nodeSession.checkPermission(node.getPath(), PermissionType.REMOVE);
    } catch (AccessControlException e) {
      // A user must always be able to clear their own Personal Documents space, even when
      // a node inside it carries an ACL that does not grant them REMOVE (e.g. a node
      // created there on somebody else's behalf) — see JCRDocumentsUtil#isInUserPrivateSpace.
      // The caller's own canRemoveNode already allows for this; this is the same rule
      // applied at the actual mutation, which checks the node's real ACL independently.
      if (!JCRDocumentsUtil.isInUserPrivateSpace(node, nodeSession.getUserID())) {
        throw e;
      }
    }
  }

  private String getSessionUser(Session session) {
    String userId = session.getUserID();
    if (userId == null || userId.isBlank() || IdentityConstants.SYSTEM.equals(userId) || IdentityConstants.ANONIM.equals(userId)) {
      return null;
    }
    return userId;
  }

  private boolean isRemovalBroadcast(Node node) throws RepositoryException {
    if (node.getPrimaryNodeType().getName().equals(NodeTypeConstants.NT_FILE) || node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
      return isBroadcastNTFileEvents(node);
    }
    return true;
  }

  /**
   * The bin folder of the drive's owner, created when it does not exist yet,
   * or the trash home for a node deleted from no personal or space drive.
   */
  private Node getDestination(String ownerId, Session trashSession, SessionProvider trashProvider) throws RepositoryException {
    Node destination = null;
    if (ownerId != null) {
      Identity ownerIdentity = identityManager.getIdentity(ownerId);
      if (ownerIdentity != null) {
        destination = JCRDocumentsUtil.getRecycleBinFolder(ownerIdentity,
                                                          trashSession,
                                                          this.trashHome,
                                                          nodeHierarchyCreator,
                                                          spaceService,
                                                          trashProvider,
                                                          true);
      }
    }
    return destination == null ? (Node) trashSession.getItem(this.trashHome) : destination;
  }

  /**
   * The effective permission entries of a node that carries none of its own:
   * once in the trash it takes the entries of its bin folder, so the ones it
   * had are kept for its restore. A node with permissions of its own keeps
   * them through the move.
   */
  private List<String> readInheritedPermissions(Node node) throws RepositoryException {
    if (node.isNodeType(NodeTypeConstants.EXO_PRIVILEGEABLE)) {
      return null; // NOSONAR null tells "none recorded" from "no entry"
    }
    List<String> permissions = new ArrayList<>();
    for (AccessControlEntry entry : ((ExtendedNode) node).getACL().getPermissionEntries()) {
      permissions.add(entry.getIdentity() + " " + entry.getPermission());
    }
    return permissions;
  }

  private String getReferenceableParentId(Node node) throws RepositoryException {
    try {
      Node parent = node.getParent();
      return parent.isNodeType(NodeTypeConstants.MIX_REFERENCEABLE) ? parent.getUUID() : null;
    } catch (ItemNotFoundException e) {
      return null;
    }
  }

  /**
   * Walks an item about to be trashed and returns its size: the content length
   * of a file, the sum of its files for a folder, versions left out. On the
   * way it collects the shortcuts pointing at the item or at a descendant
   * (when a map is given) and removes the Recycle Bin marks a descendant still
   * carries from an earlier stay in the trash (when asked to).
   */
  private long measureItem(Node item,
                           String itemPath,
                           String workspaceName,
                           Session session,
                           Map<String, Node> shortcuts,
                           boolean removeStaleMarks) throws RepositoryException {
    long size = 0;
    Deque<Node> nodes = new ArrayDeque<>();
    nodes.add(item);
    boolean descendant = false;
    while (!nodes.isEmpty()) {
      Node node = nodes.poll();
      if (descendant && removeStaleMarks) {
        if (node.isNodeType(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM)) {
          node.removeMixin(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM);
        }
        if (node.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION)) {
          node.removeMixin(NodeTypeConstants.EXO_RESTORE_LOCATION);
        }
      }
      descendant = true;
      if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        continue;
      }
      if (shortcuts != null) {
        collectShortcuts(node, itemPath, workspaceName, session, shortcuts);
      }
      if (node.isNodeType(NodeTypeConstants.NT_FILE)) {
        size += getContentLength(node);
      } else if (JCRDocumentsUtil.isFolder(node)) {
        for (NodeIterator children = node.getNodes(); children.hasNext();) {
          nodes.add(children.nextNode());
        }
      }
    }
    return size;
  }

  private long getContentLength(Node file) throws RepositoryException {
    if (file.hasNode(NodeTypeConstants.JCR_CONTENT)) {
      Node content = file.getNode(NodeTypeConstants.JCR_CONTENT);
      if (content.hasProperty(NodeTypeConstants.JCR_DATA)) {
        return content.getProperty(NodeTypeConstants.JCR_DATA).getLength();
      }
    }
    return 0;
  }

  /**
   * Adds the shortcuts pointing at a node, keyed by their path. A shortcut
   * already in the trash is left there, and one stored inside the item moves
   * with it as any descendant.
   */
  private void collectShortcuts(Node target,
                                String itemPath,
                                String workspaceName,
                                Session session,
                                Map<String, Node> shortcuts) throws RepositoryException {
    String statement = "SELECT * FROM " + NodeTypeConstants.EXO_SYMLINK + " WHERE " + NodeTypeConstants.EXO_SYMLINK_UUID + "='"
        + ((ExtendedNode) target).getIdentifier() + "' AND " + NodeTypeConstants.EXO_WORKSPACE + "='" + workspaceName.replace("'", "''")
        + "'";
    Query query = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
    for (NodeIterator links = query.execute().getNodes(); links.hasNext();) {
      Node link = links.nextNode();
      String linkPath = link.getPath();
      if (!linkPath.startsWith(this.trashHome + "/") && !linkPath.startsWith(itemPath + "/")) {
        shortcuts.put(linkPath, link);
      }
    }
  }

  /**
   * Moves a shortcut to the trash with the item whose deletion causes it. It
   * is stored beside that item and named after it by exo:recycleBinOf: it
   * belongs to no bin and has no size, and comes back or leaves with the item.
   */
  private Node moveShortcutToTrash(Session trashSession,
                                   Node shortcut,
                                   Node destination,
                                   String workspaceName,
                                   String itemTrashId,
                                   String deleter,
                                   Calendar deletedDate) throws RepositoryException {
    String shortcutPath = shortcut.getPath();
    String parentId = getReferenceableParentId(shortcut);
    Node trashed = moveInto(trashSession, shortcutPath, destination, shortcut.getName());
    writeRestoreLocation(trashed, fixRestorePath(shortcutPath), workspaceName, java.util.UUID.randomUUID().toString());
    if (!trashed.isNodeType(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM)) {
      trashed.addMixin(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM);
    }
    trashed.setProperty(NodeTypeConstants.RECYCLE_BIN_OF, itemTrashId);
    trashed.setProperty(NodeTypeConstants.DELETED_DATE, deletedDate);
    setOrRemoveProperty(trashed, NodeTypeConstants.DELETED_BY, deleter);
    setOrRemoveProperty(trashed, NodeTypeConstants.RESTORE_PARENT_ID, parentId);
    setOrRemoveProperty(trashed, NodeTypeConstants.RECYCLE_BIN_OWNER, null);
    return trashed;
  }

  private Node moveInto(Session session, String sourcePath, Node destination, String name) throws RepositoryException {
    session.move(sourcePath, destination.getPath() + "/" + name);
    return getLastSameNameNode(destination, name);
  }

  /**
   * Several items of one bin folder may bear the same name: the one just
   * moved in is the last of them.
   */
  private Node getLastSameNameNode(Node parent, String name) throws RepositoryException {
    Node last = null;
    for (NodeIterator nodes = parent.getNodes(name); nodes.hasNext();) {
      Node node = nodes.nextNode();
      if (last == null || last.getIndex() < node.getIndex()) {
        last = node;
      }
    }
    if (last == null) {
      throw new ItemNotFoundException("No node named " + name + " under " + parent.getPath());
    }
    return last;
  }

  private void writeRestoreLocation(Node node, String restorePath, String workspaceName, String trashId) throws RepositoryException {
    if (!node.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION)) {
      node.addMixin(NodeTypeConstants.EXO_RESTORE_LOCATION);
    }
    node.setProperty(NodeTypeConstants.RESTORE_PATH, restorePath);
    node.setProperty(NodeTypeConstants.RESTORE_WORKSPACE, workspaceName);
    node.setProperty(NodeTypeConstants.TRASH_ID, trashId);
  }

  private void setOrRemoveProperty(Node node, String name, String value) throws RepositoryException {
    if (value != null) {
      node.setProperty(name, value);
    } else if (node.hasProperty(name)) {
      node.getProperty(name).remove();
    }
  }

  /**
   * A system session provider of its own for one operation on the trash,
   * closed by its caller.
   */
  SessionProvider createSystemProvider() {
    return SessionProvider.createSystemProvider();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int normalizeRecycleBinItems() throws RepositoryException {
    int normalized = 0;
    SessionProvider trashProvider = createSystemProvider();
    try {
      Session trashSession = trashProvider.getSession(this.trashWorkspace, repositoryService.getCurrentRepository());
      // The mixin is also carried outside the trash: the path keeps the
      // statement to the trash home and its bin folders.
      String statement = "SELECT * FROM " + NodeTypeConstants.EXO_RESTORE_LOCATION + " WHERE jcr:path LIKE '" + this.trashHome
          + "/%'";
      Query query = trashSession.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
      List<String> itemIds = new ArrayList<>();
      for (NodeIterator nodes = query.execute().getNodes(); nodes.hasNext();) {
        Node node = nodes.nextNode();
        if (lacksRecycleBinFacts(node)) {
          itemIds.add(((ExtendedNode) node).getIdentifier());
        }
      }
      for (String itemId : itemIds) {
        if (normalizeRecycleBinItem(trashSession, trashProvider, itemId)) {
          normalized++;
        }
      }
    } finally {
      trashProvider.close();
    }
    return normalized;
  }

  /**
   * Whether a node is an entry of the trash, listed as one item: it carries
   * the restore location, is not a bin folder, and sits right under the trash
   * home or under a bin folder. What a deleted folder holds is no entry.
   */
  private boolean isTrashEntry(Node node) throws RepositoryException {
    if (!node.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION) || node.isNodeType(NodeTypeConstants.EXO_RECYCLE_BIN_FOLDER)) {
      return false;
    }
    Node parent = node.getParent();
    return this.trashHome.equals(parent.getPath()) || parent.isNodeType(NodeTypeConstants.EXO_RECYCLE_BIN_FOLDER);
  }

  private boolean lacksRecycleBinFacts(Node node) throws RepositoryException {
    return !node.isNodeType(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM) && isTrashEntry(node);
  }

  private boolean normalizeRecycleBinItem(Session trashSession, SessionProvider trashProvider, String itemId) {
    try {
      Node item = ((ExtendedSession) trashSession).getNodeByIdentifier(itemId);
      if (!lacksRecycleBinFacts(item)) {
        return false;
      }
      String workspaceName = item.hasProperty(NodeTypeConstants.RESTORE_WORKSPACE) ? item.getProperty(NodeTypeConstants.RESTORE_WORKSPACE).getString()
                                                                                   : trashSession.getWorkspace().getName();
      String ownerId = item.hasProperty(NodeTypeConstants.RESTORE_PATH) ? JCRDocumentsUtil.resolveRecycleBinOwner(item.getProperty(NodeTypeConstants.RESTORE_PATH)
                                                                                                                     .getString(),
                                                                                                                 nodeHierarchyCreator,
                                                                                                                 identityManager,
                                                                                                                 spaceService,
                                                                                                                 trashProvider)
                                                                        : null;
      // The date the item went to the trash was not kept: its last
      // modification is the closest date it carries, read before it is written.
      Calendar deletedDate = item.hasProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE) ? item.getProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE).getDate()
                                                                                         : Calendar.getInstance();
      long size = measureItem(item, item.getPath(), workspaceName, trashSession, null, false);
      String name = JCRDocumentsUtil.getTitle(item).toLowerCase();

      item.addMixin(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM);
      setOrRemoveProperty(item, NodeTypeConstants.RECYCLE_BIN_OWNER, ownerId);
      item.setProperty(NodeTypeConstants.DELETED_DATE, deletedDate);
      item.setProperty(NodeTypeConstants.RECYCLE_BIN_SIZE, size);
      item.setProperty(NodeTypeConstants.RECYCLE_BIN_NAME, name);
      if (!item.hasProperty(NodeTypeConstants.TRASH_ID)) {
        item.setProperty(NodeTypeConstants.TRASH_ID, java.util.UUID.randomUUID().toString());
      }
      trashSession.save();
      return true;
    } catch (RepositoryException | RuntimeException e) {
      discardAndReportNormalizationFailure(trashSession, itemId, e);
      return false;
    }
  }

  /**
   * A run on another node may have given the item its facts first, which
   * fails this run's save: the item is then read again, and only an item
   * still lacking its facts is a failure worth a warning.
   */
  private void discardAndReportNormalizationFailure(Session trashSession, String itemId, Exception failure) {
    try {
      trashSession.refresh(false);
      Node item = ((ExtendedSession) trashSession).getNodeByIdentifier(itemId);
      if (item.isNodeType(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM)) {
        LOG.debug("The item {} of the trash was given its Recycle Bin facts meanwhile", itemId);
        return;
      }
    } catch (ItemNotFoundException e) {
      LOG.debug("The item {} of the trash no longer exists", itemId);
      return;
    } catch (RepositoryException | RuntimeException e) {
      LOG.debug("The item {} of the trash could not be read again", itemId, e);
    }
    LOG.warn("The item {} of the trash could not be given its Recycle Bin facts", itemId, failure);
  }

  /**
   * {@inheritDoc}
   */

  @Override
  public void restoreFromTrash(String trashNodePath,
                               SessionProvider sessionProvider) throws RepositoryException {
    restoreFromTrash(trashNodePath, sessionProvider, 0);
  }

  /**
   * {@inheritDoc}
   */
  public List<Node> getAllNodeInTrash(SessionProvider sessionProvider) throws RepositoryException {
    return selectNodesByQuery(sessionProvider, selectFromTrash(), Query.SQL);
  }

  /**
   * {@inheritDoc}
   */
  public List<Node> getAllNodeInTrashByUser(SessionProvider sessionProvider,
                                            String userName) throws RepositoryException {
    StringBuilder query = new StringBuilder(selectFromTrash()).append(" AND exo:lastModifier='")
                                                              .append(userName.replace("'", "''"))
                                                              .append("'");
    return selectNodesByQuery(sessionProvider, query.toString(), Query.SQL);
  }

  public void removeRelations(Node node, SessionProvider sessionProvider) throws RepositoryException {
    ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
    String[] workspaces = manageableRepository.getWorkspaceNames();

    String queryString = "SELECT * FROM exo:relationable WHERE exo:relation IS NOT NULL";
    boolean error = false;

    for (String ws : workspaces) {
      Session session = sessionProvider.getSession(ws, manageableRepository);
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(queryString, Query.SQL);
      QueryResult queryResult = query.execute();

      NodeIterator iter = queryResult.getNodes();
      while (iter.hasNext()) {
        try {
          iter.nextNode().removeMixin("exo:relationable");
          session.save();
        } catch (Exception e) {
          error = true;
        }
      }
    }
    if (error) throw new RepositoryException("Can't remove exo:relationable of all related nodes");
  }

  public boolean isInTrash(Node node) throws RepositoryException {
    return node.getPath().startsWith(this.trashHome) && !node.getPath().equals(this.trashHome);
  }

  /**
   * {@inheritDoc}
   */
  public Node getTrashHomeNode() {
    try {
      Session session = sessionProviderService.getSystemSessionProvider(null).getSession(trashWorkspace,
                      repositoryService.getCurrentRepository());
      return (Node) session.getItem(trashHome);
    } catch (Exception e) {
      return null;
    }

  }

  public Node getNodeByTrashId(String trashId) throws RepositoryException {
    // A trash id is a UUID: anything else names no node and is not written into a statement.
    if (trashId == null || !TRASH_ID_PATTERN.matcher(trashId).matches()) {
      return null;
    }
    QueryResult queryResult;
    NodeIterator iter;
    SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
    Session session = sessionProvider.getSession(trashWorkspace,
                    repositoryService.getCurrentRepository());
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    StringBuilder sb = new StringBuilder();
    sb.append("SELECT * FROM exo:restoreLocation WHERE jcr:path LIKE '").append(this.trashHome).append("/%' AND exo:trashId = '").append(trashId).append("'");
    QueryImpl query = (QueryImpl) queryManager.createQuery(sb.toString(), Query.SQL);
    query.setLimit(1);
    queryResult = query.execute();
    iter = queryResult.getNodes();
    if(iter.hasNext()) return iter.nextNode();
    else return null;
  }

  public List<Node> getAllLinks(Node targetNode, String linkType, SessionProvider sessionProvider) {
    try {
      List<Node> result = new ArrayList<>();
      if(sessionProvider!=null) {
        ManageableRepository repository = repositoryService.getCurrentRepository();
        String workspace = targetNode.getSession().getWorkspace().getName();
        Session session = sessionProvider.getSession(workspace, repository);
        String queryString = new StringBuilder().append("SELECT * FROM ").
                append(linkType).
                append(" WHERE exo:uuid='").
                append(((ExtendedNode) targetNode).getIdentifier()).append("'").
                append(" AND exo:workspace='").
                append(workspace).
                append("'").toString();
        QueryManager queryManager = session.getWorkspace().getQueryManager();
        Query query = queryManager.createQuery(queryString, Query.SQL);
        QueryResult queryResult = query.execute();
        NodeIterator iter = queryResult.getNodes();
        while (iter.hasNext()) {
          result.add(iter.nextNode());
        }
      }
      return result;
    } catch (RepositoryException e) {
      // return empty node list if there are errors in execution or user has no right to access nodes
      return new ArrayList<>();
    }
  }

  @Override
  public List<Node> getTrashElements(TrashElementNodeFilter trashElementNodeFilter) throws RepositoryException {
    String mappedSortField = switch (trashElementNodeFilter.getSortField()) {
      case NAME -> EXO_TITLE;
      default -> EXO_DATE_MODIFIED;
    };
    String sortDirection = trashElementNodeFilter.isAscending() ? "ASC" : "DESC";
    // all trash elements has the property exo:restorePath, it is added during the move te trash
    StringBuilder sb = new StringBuilder(selectFromTrash());
    sb.append(" ORDER BY ").append(mappedSortField).append(" ").append(sortDirection);

    List<Node> result = new ArrayList<>();

    ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
    SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
    Session session = sessionProvider.getSession(this.trashWorkspace, manageableRepository);
    QueryManager queryManager = session.getWorkspace().getQueryManager();

    QueryImpl query = (QueryImpl) queryManager.createQuery(sb.toString(), Query.SQL);
    query.setLimit(trashElementNodeFilter.getLimit());
    query.setOffset(trashElementNodeFilter.getOffset());
    QueryResult queryResult = query.execute();

    NodeIterator iter = queryResult.getNodes();
    while (iter.hasNext()) {
      result.add(iter.nextNode());
    }
    return result;
  }

  @Override
  public List<Node> getAllLinks(Node targetNode, String linkType) {
    return getAllLinks(targetNode, linkType, sessionProviderService.getSessionProvider(null));
  }

  @Override
  public int countDeletedDocuments() {
    try {
      StringBuilder sb = new StringBuilder(selectFromTrash());
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      Session session = sessionProvider.getSession(this.trashWorkspace, manageableRepository);
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(sb.toString(), Query.SQL);
      QueryResult queryResult = query.execute();
      return (int) queryResult.getNodes().getSize();
    } catch (Exception e) {
      LOG.error("Error occurred when counting trash elements", e.getMessage());
      return 0;
    }
  }

  /**
   * The statement every listing of the trash starts with. The restore path is
   * also carried by nodes outside the trash: the path keeps the statement to
   * the trash home and what it holds.
   */
  private String selectFromTrash() {
    return "SELECT * FROM nt:base WHERE jcr:path LIKE '" + this.trashHome + "/%' AND exo:restorePath IS NOT NULL";
  }

  private boolean isDocumentNodeType(Node node) throws RepositoryException {
    return !(node.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED) || node.isNodeType(NodeTypeConstants.NT_FOLDER));
  }

  private boolean isBroadcastNTFileEvents(Node node) throws RepositoryException {
    boolean result = true;
    while(result && !((NodeImpl)node).isRoot()) {
      try{
        node = node.getParent();
        result = !isDocumentNodeType(node);
      }catch (AccessDeniedException ex){
        return result;
      }catch (RepositoryException ex) {
        return !isDocumentNodeType(node);
      }
    }
    return result;
  }

  private List<Node> selectNodesByQuery(SessionProvider sessionProvider,
                                        String queryString,
                                        String language) throws RepositoryException {
    List<Node> ret = new ArrayList<>();
    ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
    Session session = sessionProvider.getSession(this.trashWorkspace, manageableRepository);
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    Query query = queryManager.createQuery(queryString, language);
    QueryResult queryResult = query.execute();

    NodeIterator iter = queryResult.getNodes();
    while (iter.hasNext()) {
      ret.add(iter.nextNode());
    }

    return ret;
  }

  private String fixRestorePath(String path) {
    int leftBracket = path.lastIndexOf('[');
    int rightBracket = path.lastIndexOf(']');
    if (leftBracket == -1 || rightBracket == -1 ||
            (leftBracket >= rightBracket)) return path;

    try {
      Integer.parseInt(path.substring(leftBracket+1, rightBracket));
    } catch (Exception ex) {
      return path;
    }
    return path.substring(0, leftBracket);
  }

  private void removeMixinRestoreLocation(Session session, String restorePath) throws RepositoryException {
    Node sameNameNode = ((Node) session.getItem(restorePath));
    Node parent = sameNameNode.getParent();
    String name = sameNameNode.getName();
    NodeIterator nodeIter = parent.getNodes(name);
    while (nodeIter.hasNext()) {
      Node node = nodeIter.nextNode();
      if (node.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION))
        node.removeMixin(NodeTypeConstants.EXO_RESTORE_LOCATION);
    }
  }

  private void restoreFromTrash(String trashNodePath,
                                SessionProvider sessionProvider, int deep) throws RepositoryException {

    Node trashHomeNode = this.getTrashHomeNode();
    Session trashNodeSession = trashHomeNode.getSession();
    Node trashNode = (Node)trashNodeSession.getItem(trashNodePath);
    String trashWorkspaceName = trashNodeSession.getWorkspace().getName();
    String restoreWorkspace = trashNode.getProperty(NodeTypeConstants.RESTORE_WORKSPACE).getString();
    String restorePath = trashNode.getProperty(NodeTypeConstants.RESTORE_PATH).getString();
    String nodeUUID = trashNode.isNodeType(NodeTypeConstants.MIX_REFERENCEABLE) ? trashNode.getUUID() : null;
    if (trashNode.isNodeType(NodeTypeConstants.EXO_SYMLINK)) nodeUUID = null;
    String taxonomyLinkUUID = trashNode.isNodeType(NodeTypeConstants.TAXONOMY_LINK) ? trashNode.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString() : null;
    String taxonomyLinkWS = trashNode.isNodeType(NodeTypeConstants.TAXONOMY_LINK) ? trashNode.getProperty(NodeTypeConstants.EXO_WORKSPACE).getString() : null;

    ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
    Session restoreSession = sessionProvider.getSession(restoreWorkspace,  manageableRepository);

    if (restoreWorkspace.equals(trashWorkspaceName)) {
      trashNodeSession.getWorkspace().move(trashNodePath, restorePath);
    } else {
      //clone node
      restoreSession.getWorkspace().clone(trashWorkspaceName, trashNodePath, restorePath, true);
      trashNodeSession.getItem(trashNodePath).remove();
    }

    removeMixinRestoreLocation(restoreSession, restorePath);
    trashNodeSession.save();
    restoreSession.save();

    if (deep == 0 && nodeUUID != null) {
      while (true) {
        boolean found = false;
        NodeIterator iter = trashHomeNode.getNodes();
        while (iter.hasNext()) {
          Node trashChild = iter.nextNode();
          if (trashChild.isNodeType(NodeTypeConstants.TAXONOMY_LINK) && trashChild.hasProperty(NodeTypeConstants.EXO_SYMLINK_UUID)
                  && trashChild.hasProperty(NodeTypeConstants.EXO_WORKSPACE)
                  && nodeUUID.equals(trashChild.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString())) {
            try {
              restoreFromTrash(trashChild.getPath(), sessionProvider, deep + 1);
              found = true;
              break;
            } catch (Exception e) {
              if (LOG.isWarnEnabled()) {
                LOG.warn(e.getMessage());
              }
            }
          }
        }
        if (!found) break;
      }
    }

    trashNodeSession.save();
    restoreSession.save();
    //restore target node of the restored categories.
    if (deep == 0 && taxonomyLinkUUID != null && taxonomyLinkWS != null) {
      while (true) {
        boolean found = false;
        NodeIterator iter = trashHomeNode.getNodes();
        while (iter.hasNext()) {
          Node trashChild = iter.nextNode();
          if (trashChild.isNodeType(NodeTypeConstants.MIX_REFERENCEABLE)
                  && taxonomyLinkUUID.equals(trashChild.getUUID())
                  && taxonomyLinkWS.equals(trashChild.getProperty(NodeTypeConstants.RESTORE_WORKSPACE).getString())) {
            try {
              restoreFromTrash(trashChild.getPath(),
                      sessionProvider,
                      deep + 1);
              found = true;
              break;
            } catch (Exception e) {
              if (LOG.isWarnEnabled()) {
                LOG.warn(e.getMessage());
              }
            }
          }
        }
        if (!found) break;
      }
    }

    trashNodeSession.save();
    restoreSession.save();
  }

  @Override
  public void updateRestorePath(String oldPath, String newPath) throws RepositoryException {
    SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
    Session session = sessionProvider.getSession(this.trashWorkspace, repositoryService.getCurrentRepository());

    String safePath = oldPath.startsWith("/") ? oldPath : "/" + oldPath;
    String queryStr = """
                        SELECT * FROM exo:restoreLocation
                        WHERE jcr:path LIKE '%s/%%'
                        AND exo:restorePath LIKE '%s%%'
                        """.formatted(
                          trashHome.replace("'", "''"),
                          safePath.replace("'", "''"));
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    Query query = queryManager.createQuery(queryStr, Query.SQL);
    QueryResult result = query.execute();

    NodeIterator nodes = result.getNodes();
    while (nodes.hasNext()) {
      Node node = nodes.nextNode();
      if (node.hasProperty(RESTORE_PATH)) {
        String restorePath = node.getProperty(RESTORE_PATH).getString();

        //Update path prefix
        String updatedPath = restorePath.replaceFirst("^" + oldPath, newPath);
        node.setProperty(RESTORE_PATH, updatedPath);
      }
    }
    session.save();
  }

}
