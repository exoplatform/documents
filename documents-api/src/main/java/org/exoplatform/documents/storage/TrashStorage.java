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

import org.exoplatform.documents.model.TrashElementNodeFilter;
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
   * @deprecated use {@link #moveToTrash(Node, SessionProvider, String)}, which
   *             records who deleted the node. Since 7.3.0, for removal.
   */
  @Deprecated(since = "7.3.0", forRemoval = true)
  public String moveToTrash(Node node, SessionProvider sessionProvider) throws RepositoryException;

  /**
   * Move node to trash location with deep
   * @param node
   * @param sessionProvider
   * @param deep
   * @return -1: move failed.
   *          trashId if moved succesfully
   * @throws RepositoryException
   * @deprecated use {@link #moveToTrash(Node, SessionProvider, String, int)},
   *             which records who deleted the node. Since 7.3.0, for removal.
   */
  @Deprecated(since = "7.3.0", forRemoval = true)
  public String moveToTrash(Node node, SessionProvider sessionProvider, int deep) throws RepositoryException;

  /**
   * Moves a node to the trash and records what its Recycle Bin shows of it:
   * the drive it belongs to, the folder it was deleted from, who deleted it
   * and when, its size and its name. A node deleted from a personal or a space
   * drive goes to that drive's bin folder, any other one under the trash home.
   * The shortcuts pointing at the node or at one of its descendants are moved
   * with it. The move, the recorded facts and the shortcuts are saved together
   * or not at all. Two things are done before that save and stay when it
   * fails: the repository's removal actions on the node have run, and the bin
   * folder of the drive, when it did not exist, has been created. A node of
   * another workspace than the trash is copied to the trash and then removed
   * from its workspace, which is not one save: its shortcuts are not moved.
   *
   * @param node node to move, read in the session of the user deleting it
   * @param sessionProvider session provider of the caller
   * @param deletedBy username of the user deleting the node. When null, the
   *          user of the node's session is recorded, unless it is the system
   *          or the anonymous one
   * @return the trash id of the node, null when the node is itself a deleted
   *         item of the trash. A node a deleted folder holds is not one: it is
   *         moved as an item of its own
   * @throws RepositoryException when the node cannot be moved; a node of the
   *           trash workspace is then left where it was, with its shortcuts
   */
  public String moveToTrash(Node node, SessionProvider sessionProvider, String deletedBy) throws RepositoryException;

  /**
   * Same as {@link #moveToTrash(Node, SessionProvider, String)}, with the depth
   * of the call: a depth other than 0 is a move made on behalf of another one
   * and does not look for the target of a category link.
   *
   * @param node node to move
   * @param sessionProvider session provider of the caller
   * @param deletedBy username of the user deleting the node, may be null
   * @param deep depth of the call
   * @return the trash id of the node, null when the node is itself a deleted
   *         item of the trash
   * @throws RepositoryException when the node cannot be moved
   */
  public String moveToTrash(Node node, SessionProvider sessionProvider, String deletedBy, int deep) throws RepositoryException;
  
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

  /**
   * Retrieves a list of trash elements that match the specified filter.
   *
   * @param trashElementNodeFilter the filter used to find trash elements
   * @return a list of nodes representing the trash elements
   * @throws RepositoryException if an error occurs while accessing the repository
   */
  List<Node> getTrashElements(TrashElementNodeFilter trashElementNodeFilter) throws RepositoryException;

  /**
   * Counts the total number of deleted documents in the trash.
   *
   * @return the total count of deleted documents
   */
  int countDeletedDocuments();


  /**
   * Updates the restore paths of nodes in the trash workspace when a parent folder has been renamed.
   * <p>
   * This method searches for all nodes whose {@code exo:restoreWorkspace} property starts with the given
   * {@code oldPath}, and replaces that prefix with {@code newPath}. This ensures that nodes in the trash
   * can be restored correctly even after the original folder has been moved or renamed.
   * </p>
   *
   * @param oldPath the old path prefix to look for in the {@code exo:restoreWorkspace} property
   * @param newPath the new path prefix to replace the old path with
   * @throws RepositoryException if an error occurs while accessing or updating nodes in the repository
   */
  void updateRestorePath(String oldPath, String newPath) throws RepositoryException;

  /**
   * Gives the items of the trash that lack them the facts the Recycle Bin
   * lists: the drive they belong to, their deletion date, their size and
   * their name. Such items were deleted before those facts were recorded, or
   * by a mover that records none. Who deleted them, the folder they were
   * deleted from and the permissions they inherited are not known and are
   * left out; the deletion date is the item's last modification date. An item
   * is not moved. Each item is saved on its own, an item another run handled
   * meanwhile is skipped, and a failure on one item does not stop the others.
   *
   * @return the number of items given their facts by this run
   * @throws RepositoryException when the items of the trash cannot be read
   */
  int normalizeRecycleBinItems() throws RepositoryException;
}
