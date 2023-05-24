/*
 * Copyright (C) 2023 eXo Platform SAS
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
package org.exoplatform.documents.service;

import org.exoplatform.documents.model.PublicDocumentAccess;

public interface PublicDocumentAccessService {

  /**
   * Generates a token for public access of a document
   *
   * @param docOwnerId     document owner
   * @param nodeId         document node id
   * @param isFolder       if the document is a folder
   * @param password       password to sign the token with
   * @param expirationDate token expiration date
   * @return a generated jwt token
   */
  String createPublicDocumentAccess(long docOwnerId, String nodeId, boolean isFolder, String password, Long expirationDate);

  /**
   * Gets a document token by its token id
   *
   * @param documentId token id
   * @return {@link PublicDocumentAccess}
   */
  PublicDocumentAccess getPublicDocumentAccess(String documentId);

  /**
   * Checks if token expired
   *
   * @param documentId token id
   * @return true or false
   */
  boolean isPublicDocumentAccessExpired(String documentId);

  /**
   * Checks the validity of a given token
   * 
   * @param token token string
   * @param password password of the document used to sign the token beside the
   *          secret key
   * @return true if token is valid or false else
   */
  boolean isDocumentPublicAccessValid(String token, String password);

  /**
   * Checks if a document has public access
   *
   * @param documentId document id
   * @return true or false
   */
  boolean hasDocumentPublicAccess(String documentId);

  /**
   * Revokes a document public access
   *
   * @param documentId document id
   */
  void revokeDocumentPublicAccess(String documentId);

}
