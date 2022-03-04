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
package org.exoplatform.documents.storage;

import org.exoplatform.services.jcr.ext.common.SessionProvider;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.List;

/**
 * This service used to move documents to trash foder or restore
 */

public interface TrashStorage {

  public static String FILE_REMOVE_ACTIVITY          = "FileActivityNotify.event.FileRemoved";

  /**
   * Move node to trash location
   * @param node Node will be moved to trash
   * @param sessionProvider User session provider which will be used to get session
   * @throws RepositoryException
   * @return -1: move failed.
   *          trashId if moved succesfully
   */
  public String moveToTrash(Node node, SessionProvider sessionProvider) throws RepositoryException;

  /**
   * Move node to trash location with deep
   * @param node
   * @param sessionProvider
   * @param deep
   * @return -1: move failed.
   *          trashId if moved succesfully
   * @throws RepositoryException
   */
  public String moveToTrash(Node node, SessionProvider sessionProvider, int deep) throws RepositoryException;
  
  /**
   * Restore node from trash
   * 
   * @param trashNodePath The path.
   * @param sessionProvider The session provider.
   * @throws RepositoryException
   */
  public void restoreFromTrash(String trashNodePath, SessionProvider sessionProvider) throws RepositoryException;


  /**
   * Get all nodes in trash location
   *
   * @param sessionProvider
   * @return All nodes in trash
   * @throws RepositoryException
   */
  public List<Node> getAllNodeInTrash(SessionProvider sessionProvider) throws RepositoryException;


  /**
   * Get all nodes by user in trash location
   * @param sessionProvider
   * @param userName
   * @return all node in trash which moved by user
   * @throws RepositoryException
   */
  public List<Node> getAllNodeInTrashByUser(SessionProvider sessionProvider,
                                            String userName) throws RepositoryException;


  /**
   * Removes all 'relationable' property of nodes that have relation to this node
   * @param node
   * @param sessionProvider
   * @throws RepositoryException
   */
  public void removeRelations(Node node, SessionProvider sessionProvider) throws RepositoryException;
  
  /**
   * Check whether a given node is in Trash or not
   * @param node a specify node
   * @return <code>true</code> if node is in Trash, <code>false</code> otherwise.
   * @throws RepositoryException
   */
  public boolean isInTrash(Node node) throws RepositoryException;
  
  /**
   * Get the trash hone's node
   * @return <code>Node</code> the node of trash home
   */
  public Node getTrashHomeNode();

  /**
   * Get <code>Node</code> in trash folder by trashId
   * @param trashId ID of node will return
   * @return <code>Node</code> in trash folder with thrashId, <code>null</code> if thrashId doesn't exist in trash folder
   * @throws RepositoryException
   * */
  public Node getNodeByTrashId(String trashId) throws RepositoryException;

  /**
   * Get all links
   * @param node
   * @param linkType
   * @return {@link List}  of {@link Node}
   * */
  public List<Node> getAllLinks(Node node, String linkType);
}
