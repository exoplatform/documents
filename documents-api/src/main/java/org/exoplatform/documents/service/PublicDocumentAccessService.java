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
   * Creates a public access for a document
   *
   * @param docOwnerId     document owner
   * @param nodeId         document node id
   * @param password       password of the public access
   * @param expirationDate public access expiration date
   * @param hasPassword document has already old password
   * @return {@link PublicDocumentAccess}
   */
  PublicDocumentAccess createPublicDocumentAccess(long docOwnerId,
                                                  String nodeId,
                                                  String password,
                                                  Long expirationDate,
                                                  boolean hasPassword);

  /**
   * Gets a document public access by its node id
   *
   * @param documentId node id
   * @return {@link PublicDocumentAccess}
   */
  PublicDocumentAccess getPublicDocumentAccess(String documentId);

  /**
   * Checks if public access expired
   *
   * @param documentId node id
   * @return true or false
   */
  boolean isPublicDocumentAccessExpired(String documentId);

  /**
   * Checks the validity of a public access
   * 
   * @param documentId node id
   * @param password password of the document public access
   * @return true if access is valid or false else
   */
  boolean isDocumentPublicAccessValid(String documentId, String password);

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
