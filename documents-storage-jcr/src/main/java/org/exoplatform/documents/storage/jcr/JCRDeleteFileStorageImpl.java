/*
 * Copyright (C) 2022 eXo Platform SAS
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
package org.exoplatform.documents.storage.jcr;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.metadata.favorite.FavoriteService;
import org.exoplatform.social.metadata.favorite.model.Favorite;
import org.picocontainer.Startable;

import javax.jcr.*;
import javax.jcr.lock.LockException;
import javax.jcr.version.VersionException;
import java.security.AccessControlException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JCRDeleteFileStorageImpl implements JCRDeleteFileStorage, Startable {

  private static final Log LOG = ExoLogger.getLogger(JCRDeleteFileStorageImpl.class.getName());

  private RepositoryService repositoryService;

  private IdentityManager identityManager;

  private TrashStorage trashStorage;

  private FavoriteService favoriteService;

  private ScheduledExecutorService scheduledExecutor;

  private PortalContainer container;

  private SessionProviderService sessionProviderService;

  private ListenerService listenerService;

  public static final  Map<String, String> documentsToDeleteQueue = new HashMap<>();

  public JCRDeleteFileStorageImpl(RepositoryService repositoryService, IdentityManager identityManager, TrashStorage trashStorage, FavoriteService favoriteService, PortalContainer container, SessionProviderService sessionProviderService, ListenerService listenerService) {
    this.repositoryService = repositoryService;
    this.identityManager = identityManager;
    this.trashStorage = trashStorage;
    this.favoriteService = favoriteService;
    this.container = container;
    this.sessionProviderService = sessionProviderService;
    this.listenerService = listenerService;
  }

  @Override
  public void start() {
    scheduledExecutor = Executors.newScheduledThreadPool(1);
  }

  @Override
  public void stop() {
    if (scheduledExecutor != null) {
      scheduledExecutor.shutdown();
    }
  }

  @Override
  public void undoDelete(String documentId, long userIdentityId) {
    if (documentsToDeleteQueue.containsKey(documentId)) {
      String originalModifierUser = documentsToDeleteQueue.get(documentId);
      if (!originalModifierUser.equals(String.valueOf(userIdentityId))) {
        LOG.warn("User {} attempts to cancel deletion of a document deleted by user {}", userIdentityId, originalModifierUser);
        return;
      }
      documentsToDeleteQueue.remove(documentId);
    }
  }

  @Override
  public void deleteDocument(String folderPath, String documentId, boolean favorite, boolean checkToMoveToTrash, long delay, Identity identity, long userIdentityId) {
    SessionProvider sessionProvider = null;
    try {
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      sessionProvider = JCRDocumentsUtil.getUserSessionProvider(repositoryService, identity);
      Session session = sessionProvider.getSession(manageableRepository.getConfiguration().getDefaultWorkspaceName(), manageableRepository);
      if (folderPath == null){
        folderPath = JCRDocumentsUtil.getNodeByIdentifier(session, documentId).getPath();
      }
      if (delay > 0) {
        documentsToDeleteQueue.put(documentId, String.valueOf(userIdentityId));
        String finalFolderPath = folderPath;
        scheduledExecutor.schedule(() -> {
          if (documentsToDeleteQueue.containsKey(documentId)) {
            ExoContainerContext.setCurrentContainer(container);
            RequestLifeCycle.begin(container);
            try {
              documentsToDeleteQueue.remove(documentId);
              moveToTrash(finalFolderPath, session, userIdentityId, favorite, checkToMoveToTrash);
            } catch (Exception e) {
              LOG.error("Error when deleting the document with path" + finalFolderPath, e);
            } finally {
              RequestLifeCycle.end();
            }
          }
        }, delay, TimeUnit.SECONDS);
      } else {
        moveToTrash(folderPath, session, userIdentityId, favorite, checkToMoveToTrash);
      }
    } catch (PathNotFoundException path) {
      LOG.error("The document with this path is not found" + folderPath, path);
    } catch (Exception e) {
      LOG.error("Error when deleting the document" + folderPath, e);
    }
  }

  private void moveToTrash(String folderPath, Session session, long userIdentityId, boolean favorite, boolean checkToMoveToTrash) throws RepositoryException, ObjectNotFoundException  {
    Node node = null;
    String trashId;
    if(StringUtils.isNotBlank(folderPath)){
      node = JCRDocumentsUtil.getNodeByPath(session, folderPath);
    }
    if (node != null) {
      if(favorite) {
        Favorite favoriteDocument = new Favorite("file", node.getUUID(), null, userIdentityId);
        favoriteService.deleteFavorite(favoriteDocument);
      }
      trashId = processRemoveOrMoveToTrash(node, checkToMoveToTrash);
      if (trashId.equals("-1")) {
        LOG.error("an unexpected error occurs while removing or moving the node to trash");
      }
    }
  }

  /**
   * Remove or MoveToTrash
   *
   * @param node
   * @param checkToMoveToTrash
   * @return
   *  0: node removed
   * -1: move to trash failed
   * trashId: moved to trash successfully
   * @throws RepositoryException
   */
  private String processRemoveOrMoveToTrash(Node node, boolean checkToMoveToTrash) throws RepositoryException {
    String trashId;
    if (!checkToMoveToTrash || trashStorage.isInTrash(node)) {
      processRemoveNode( node);
      return "0";
    }else {
      trashId = moveToTrash(node);
      if (!trashId.equals("-1")) {
        //Broadcast the event when delete folder, in case deleting file, Thrash service will broadcast event
        node = trashStorage.getNodeByTrashId(trashId);
        if(!node.getPrimaryNodeType().getName().equals(NodeTypeConstants.NT_FILE)){
          Queue<Node> queue = new LinkedList<>();
          queue.add(node);

          //Broadcast event to remove file activities
          Node tempNode = null;
          try {
            while (!queue.isEmpty()) {
              tempNode = queue.poll();
              if (tempNode.getPrimaryNodeType().getName().equals(NodeTypeConstants.NT_FILE)) {
                listenerService.broadcast(TrashStorage.FILE_REMOVE_ACTIVITY, tempNode.getParent(), tempNode);
              } else {
                for (NodeIterator iter = tempNode.getNodes(); iter.hasNext(); ) {
                  Node childNode = iter.nextNode();
                  if(childNode.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED) ||
                          childNode.isNodeType(NodeTypeConstants.NT_FOLDER))
                    queue.add(childNode);
                }
              }
            }
          } catch (Exception e) {
            if (LOG.isWarnEnabled()) {
              LOG.warn(e.getMessage());
            }
          }
        }
      }
    }
    return trashId;
  }

  private void processRemoveNode(Node node)
          throws RepositoryException {
    Node parentNode = node.getParent();
    try {
      //Remove symlinks
      if(!node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        for(Node symlink : trashStorage.getAllLinks(node, NodeTypeConstants.EXO_SYMLINK)) {
          symlink.remove();
          symlink.getSession().save();
        }
      }
      node.remove();
      parentNode.save();
    } catch (Exception e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("an unexpected error occurs while removing the node", e);
      }
    }
  }


  /**
   * Move Node to Trash
   * Return -1: move failed
   * Return trashId: move successfully with trashId
   * @param node
   * @return
   * @throws RepositoryException
   */
  private String moveToTrash(Node node) throws RepositoryException {
    boolean ret = true;
    String trashId = null;
    try {
      if (!node.isCheckedOut())
        throw new VersionException("node is locked, can't move to trash node :" + node.getPath());
      if (!canRemoveNode(node))
        throw new AccessDeniedException("access denied, can't move to trash node:" + node.getPath());
      SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      trashId = trashStorage.moveToTrash(node, sessionProvider);

    } catch (PathNotFoundException e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("Error to find node with the path :" + node.getPath());
      }
      ret = false;
    } catch (LockException e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("node is locked, can't move to trash node :" + node.getPath());
      }
      ret = false;
    } catch (VersionException e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("node is checked in, can't move to trash node:" + node.getPath());
      }
      removeMixinRestoreLocation(node);
      ret = false;
    } catch (AccessDeniedException e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("access denied, can't move to trash node:" + node.getPath());
      }
      ret = false;
    } catch (Exception e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("an unexpected error occurs", e);
      }
      ret = false;
    }
    return (ret)?trashId:"-1";
  }

  public static boolean canRemoveNode(Node node) throws RepositoryException {
    return checkPermission(node, PermissionType.REMOVE);
  }

  private static boolean checkPermission(Node node,String permissionType) throws RepositoryException {
    try {
      ((ExtendedNode)node).checkPermission(permissionType);
      return true;
    } catch(AccessControlException e) {
      return false;
    }
  }

  private void removeMixinRestoreLocation(Node node) throws RepositoryException {
    if (node.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION)) {
      node.removeMixin(NodeTypeConstants.EXO_RESTORE_LOCATION);
      node.save();
    }
  }

}
