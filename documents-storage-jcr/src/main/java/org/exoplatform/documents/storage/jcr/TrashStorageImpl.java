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

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.ActivityTypeUtils;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
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

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.util.*;

import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getUserSessionProvider;

public class TrashStorageImpl implements TrashStorage {

  private static final String FILE_EXPLORER_PORTLET = "FileExplorerPortlet";
  private static final String TRASH_WORKSPACE = "trashWorkspace";
  private static final String TRASH_HOME_PATH = "trashHomeNodePath";

  private RepositoryService repositoryService;
  private String trashWorkspace;
  private String trashHome;

  /** The log. */
  private static final Log LOG = ExoLogger.getLogger(TrashStorageImpl.class.getName());

  public TrashStorageImpl(RepositoryService repositoryService, InitParams initParams) throws PortletInvokerException {
    this.repositoryService = repositoryService;
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
  public String moveToTrash(Node node, SessionProvider sessionProvider) throws RepositoryException {
    return moveToTrash(node, sessionProvider, 0);
  }

  /**
   *{@inheritDoc}
   */
  @Override
  public String moveToTrash(Node node,
                          SessionProvider sessionProvider,
                          int deep) throws RepositoryException {
    ((SessionImpl)node.getSession()).getActionHandler().preRemoveItem((ItemImpl)node);
    String trashId = null;
    String nodeName = node.getName();
    Session nodeSession = node.getSession();
    nodeSession.checkPermission(node.getPath(), PermissionType.REMOVE);
    if (deep == 0 && !node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
      try {
        removeDeadSymlinks(node);
      } catch (Exception e) {
        if (LOG.isWarnEnabled()) {
          LOG.warn(e.getMessage());
        }
      }
    }
    ListenerService listenerService =  CommonsUtils.getService(ListenerService.class);
    try {
      if (node.getPrimaryNodeType().getName().equals(NodeTypeConstants.NT_FILE) || node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        if (isBroadcastNTFileEvents(node)) {
          listenerService.broadcast(FILE_REMOVE_ACTIVITY, null, node);
        }
      } else{
        listenerService.broadcast(FILE_REMOVE_ACTIVITY, null, node);
      }
    } catch (Exception e) {
      LOG.error(e.getMessage());
    }
    String nodeWorkspaceName = nodeSession.getWorkspace().getName();
    if (!node.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION)) {
      String restorePath = fixRestorePath(node.getPath());
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      Session trashSession = JCRDocumentsUtil.getSystemSessionProvider().getSession(this.trashWorkspace, manageableRepository);
      String actualTrashPath = this.trashHome + (this.trashHome.endsWith("/") ? "" : "/")
          + fixRestorePath(nodeName);
      if (trashSession.getWorkspace().getName().equals(
          nodeSession.getWorkspace().getName())) {
        trashSession.getWorkspace().move(node.getPath(),
            actualTrashPath);
      } else {
        //clone node in trash folder
        trashSession.getWorkspace().clone(nodeWorkspaceName,
            node.getPath(), actualTrashPath, true);
        node.remove();
      }
      trashId = addRestorePathInfo(nodeName, restorePath, nodeWorkspaceName);
      trashSession.save();

      //check and delete target node when there is no its symlink
      String taxonomyLinkUUID = node.isNodeType(NodeTypeConstants.TAXONOMY_LINK) ? node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString() : null;
      String taxonomyLinkWS = node.isNodeType(NodeTypeConstants.TAXONOMY_LINK) ? node.getProperty(NodeTypeConstants.EXO_WORKSPACE).getString() : null;
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
            this.moveToTrash(targetNode, sessionProvider);
          }
        }
      }

      trashSession.save();
    }
    return trashId;
  }

  /**
   * {@inheritDoc}
   */
  public void restoreFromTrash(String trashNodePath,
                               SessionProvider sessionProvider) throws RepositoryException {
    restoreFromTrash(trashNodePath, sessionProvider, 0);
  }

  /**
   * {@inheritDoc}
   */
  public List<Node> getAllNodeInTrash(SessionProvider sessionProvider) throws RepositoryException {
    StringBuilder query = new StringBuilder("SELECT * FROM nt:base WHERE exo:restorePath IS NOT NULL");
    return selectNodesByQuery(sessionProvider, query.toString(), Query.SQL);
  }

  /**
   * {@inheritDoc}
   */
  public List<Node> getAllNodeInTrashByUser(SessionProvider sessionProvider,
                                            String userName) throws RepositoryException {
    StringBuilder query = new StringBuilder(
        "SELECT * FROM nt:base WHERE exo:restorePath IS NOT NULL AND exo:lastModifier='").append(userName).append("'");
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
      Session session = JCRDocumentsUtil.getSystemSessionProvider()
                                    .getSession(trashWorkspace,
                                                repositoryService.getCurrentRepository());
      return (Node) session.getItem(trashHome);
    } catch (Exception e) {
      return null;
    }

  }

  public Node getNodeByTrashId(String trashId) throws RepositoryException {
    QueryResult queryResult;
    NodeIterator iter;
    SessionProvider sessionProvider = JCRDocumentsUtil.getSystemSessionProvider();
    Session session = sessionProvider.getSession(trashWorkspace,
                    repositoryService.getCurrentRepository());
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    StringBuilder sb = new StringBuilder();
    sb.append("SELECT * from exo:restoreLocation WHERE exo:trashId = '").append(trashId).append("'");
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
      ManageableRepository repository  = JCRDocumentsUtil.getRepository();
      String[] workspaces = repository.getWorkspaceNames();
      String queryString = new StringBuilder().append("SELECT * FROM ").
              append(linkType).
              append(" WHERE exo:uuid='").
              append(targetNode.getUUID()).append("'").
              append(" AND exo:workspace='").
              append(targetNode.getSession().getWorkspace().getName()).
              append("'").toString();

      for (String workspace : workspaces) {
        Session session = sessionProvider.getSession(workspace, repository);
        //Continue In the case cannot access to a workspace
        if(session == null) continue;
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

  /**
   * Remove deleted Symlink from Trash
   * @param node
   * @throws Exception
   */
  private void removeDeadSymlinksFromTrash(Node node) throws RepositoryException {
    List<Node> symlinks = getAllLinks(node, NodeTypeConstants.EXO_SYMLINK);
    ListenerService listenerService =  CommonsUtils.getService(ListenerService.class);
    for (Node symlink : symlinks) {
      symlink.remove();
      try {
        listenerService.broadcast(FILE_REMOVE_ACTIVITY, null, symlink);
      } catch (Exception e) {
        LOG.error(e.getMessage());
      }
    }
  }

  public void removeDeadSymlinks(Node node) throws RepositoryException {
    removeDeadSymlinks(node, true);
  }

  @Override
  public List<Node> getAllLinks(Node targetNode, String linkType) {
    return getAllLinks(targetNode, linkType, getUserSessionProvider());
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

  /**
   * Remove all the link of a deleted node
   * @param node
   * @param keepInTrash true if the link will be move to trash, otherwise set by false
   * @throws RepositoryException
   */
  private void removeDeadSymlinks(Node node, boolean keepInTrash) throws RepositoryException {
    if (isInTrash(node)) {
      removeDeadSymlinksFromTrash(node);
      return;
    }
    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    Queue<Node> queue = new LinkedList<>();
    queue.add(node);

    try {
      while (!queue.isEmpty()) {
        node = queue.poll();
        if (!node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
          try {
            List<Node> symlinks = getAllLinks(node, NodeTypeConstants.EXO_SYMLINK, sessionProvider);
            // Before removing symlinks, We order symlinks by name descending, index descending.
            // Example: symlink[3],symlink[2], symlink[1] to avoid the case that
            // the index of same name symlink automatically changed to increasing one by one
            Collections.sort(symlinks, new Comparator<Node>()
            {
              @Override
              public int compare(Node node1, Node node2) {
                try {
                  String name1 = node1.getName();
                  String name2 = node2.getName();
                  if (name1.equals(name2)) {
                    int index1 = node1.getIndex();
                    int index2 = node2.getIndex();
                    return -1 * ((Integer)index1).compareTo(index2);
                  }
                  return -1 * name1.compareTo(name2);
                } catch (RepositoryException e) {
                  return 0;
                }
              }
            });

            for (Node symlink : symlinks) {
              synchronized (symlink) {
                if (keepInTrash) {
                  moveToTrash(symlink, sessionProvider, 1);
                } else {
                  if (symlink.isNodeType(ActivityTypeUtils.EXO_ACTIVITY_INFO) && node.hasProperty(ActivityTypeUtils.EXO_ACTIVITY_ID)) {
                    ListenerService listenerService =  CommonsUtils.getService(ListenerService.class);
                    listenerService.broadcast(FILE_REMOVE_ACTIVITY, null, symlink);
                  }
                  Session nodeSession = symlink.getSession();
                  symlink.remove();
                  nodeSession.save();
                }
              }
            }
          } catch (Exception e) {
            if (LOG.isWarnEnabled()) {
              LOG.warn(e.getMessage());
            }
          }
          for (NodeIterator iter = node.getNodes(); iter.hasNext(); ) {
            queue.add(iter.nextNode());
          }
        }
      }
    } catch (Exception e) {
      if (LOG.isWarnEnabled()) {
        LOG.warn(e.getMessage());
      }
    } finally {
      sessionProvider.close();
    }
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

  /** Store original path of deleted node.
   * Return restore_id of deleted node. Use when find node in trash to undo
   * @param nodeName name of removed node
   * @param restorePath path of node before removing
   * @param nodeWs node workspace before removing
   * @throws RepositoryException
   */
  private String addRestorePathInfo(String nodeName, String restorePath, String nodeWs) throws RepositoryException {
    String restoreId = java.util.UUID.randomUUID().toString();
    NodeIterator nodes = this.getTrashHomeNode().getNodes(nodeName);
    Node node = null;
    while (nodes.hasNext()) {
      Node currentNode = nodes.nextNode();
      if (node == null) {
        node = currentNode;
      } else {
        if (node.getIndex() < currentNode.getIndex()) {
          node = currentNode;
        }
      }
    }
    if (node != null) {
      node.addMixin(NodeTypeConstants.EXO_RESTORE_LOCATION);
      node.setProperty(NodeTypeConstants.RESTORE_PATH, restorePath);
      node.setProperty(NodeTypeConstants.RESTORE_WORKSPACE, nodeWs);
      node.setProperty(NodeTypeConstants.TRASH_ID, restoreId);
      node.save();
    }
    return restoreId;
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

}
