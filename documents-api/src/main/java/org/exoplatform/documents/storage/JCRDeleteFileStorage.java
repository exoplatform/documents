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
   */
  void deleteDocument(String documentPath, String documentId, boolean favorite, boolean checkToMoveToTrash, long delay, Identity acIdentity, long userIdentityId);

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
}
