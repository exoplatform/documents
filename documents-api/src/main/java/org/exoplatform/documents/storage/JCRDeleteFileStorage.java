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

import org.exoplatform.services.security.Identity;

import java.util.Map;

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

}
