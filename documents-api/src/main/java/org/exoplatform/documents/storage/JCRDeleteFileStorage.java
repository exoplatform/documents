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
package org.exoplatform.documents.storage;

import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.TrashElementNode;
import org.exoplatform.documents.model.TrashElementNodeFilter;
import org.exoplatform.services.security.Identity;

public interface JCRDeleteFileStorage {

  Map<String, String> getDocumentsToDelete();

  /**
   * Delete document (Move to trash)
   * 
   * @param documentPath
   * @param documentId
   * @param favorite
   * @param checkToMoveToTrash
   * @param delay
   * @param acIdentity
   * @param userIdentityId
   * @throws IllegalAccessException if the acting user may not move this document to trash
   */
  void deleteDocument(String documentPath, String documentId, boolean favorite, boolean checkToMoveToTrash, long delay, Identity acIdentity, long userIdentityId) throws IllegalAccessException;

  /**
   * Undo delete document
   *
   * @param documentId
   * @param userIdentityId
   */
  void undoDelete(String documentId, long userIdentityId);

  /**
   * Delete document (Move to trash)
   *
   * @param session current session
   * @param folderPath folder path
   * @param documentId document id
   * @param favorite favorite or not
   * @param checkToMoveToTrash check whether to move to trash
   * @param delay waiting delay
   * @param identity user identity
   * @param userIdentityId user identity id
   */
  void deleteDocument(Session session,
                      String folderPath,
                      String documentId,
                      boolean favorite,
                      boolean checkToMoveToTrash,
                      long delay,
                      Identity identity,
                      long userIdentityId) throws ObjectNotFoundException, RepositoryException;

  /**
   * Delete a list of document (Move to trash)
   *
   * @param actionId action id
   * @param items list of items to delete
   * @param identity user identity
   * @param authenticatedUserId current authenticated user id 
   */
  void deleteDocuments(int actionId, List<AbstractNode> items, Identity identity, long authenticatedUserId);

  /**
   * Retrieves a list of trash elements that match the specified filter.
   *
   * @param trashElementNodeFilter the filter used to find trash elements
   * @return a list of nodes representing the trash elements
   * @throws RepositoryException if an error occurs while accessing the repository
   */
  List<TrashElementNode> getDeletedDocuments(TrashElementNodeFilter trashElementNodeFilter) throws RepositoryException;

  /**
   * Counts the total number of deleted documents in the trash.
   *
   * @return the total count of deleted documents
   */
  int countDeletedDocuments();

  /**
   * Restores a document from the trash based on the specified trash node path.
   *
   * @param trashNodePath the path of the trash node to restore
   * @throws RepositoryException if an error occurs during the restoration process
   */
  void restoreFromTrash(String trashNodePath) throws RepositoryException;

  /**
   * Delete a document from the trash based on the specified trash node path.
   *
   * @param trashNodePath the path of the trash node to delete
   * @throws ObjectNotFoundException if a document not exist in the trash
   * @throws RepositoryException if an error occurs during the delete process
   */
  void deleteDocumentPermanently(String trashNodePath) throws ObjectNotFoundException, RepositoryException;

  /**
   * Delete a list of documents from the trash.
   *
   * @param actionId the delete permanently action identifier
   * @param trashElementNodes the list of the trash nodes to delete
   * @param aclUserIdentity the user identity
   */
  void deleteDocumentsPermanently(int actionId, List<AbstractNode> trashElementNodes, Identity aclUserIdentity);
  
  /**
   * Delete a document from the trash based on the specified trash node path.
   *
   * @param trashNodePath the path of the trash node to delete
   * @param trashNodeId the identifier of the trash node to delete
   * @throws ObjectNotFoundException if a document not exist in the trash
   * @throws RepositoryException if an error occurs during the delete process
   */
  void deleteDocumentPermanently(String trashNodePath, String trashNodeId) throws ObjectNotFoundException, RepositoryException;

  /**
   * Restore a list of documents from the trash.
   *
   * @param actionId the restore action identifier
   * @param trashElementNodes the list of the trash nodes to restore
   * @param aclUserIdentity the user identity
   */
  void restoreDocuments(int actionId, List<AbstractNode> trashElementNodes, Identity aclUserIdentity);
}
