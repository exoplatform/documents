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

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.exception.DocumentSharingRefusedException;
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.documents.model.PublicDocumentInfo;

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
   * @deprecated use
   *             {@link #createDocumentPublicAccess(long, String, String, Long, boolean)},
   *             which applies the document's space settings to the user
   *             creating the link; a refusal is thrown here as an
   *             {@link IllegalStateException}
   */
  @Deprecated(forRemoval = true, since = "7.3.0")
  PublicDocumentAccess createPublicDocumentAccess(long docOwnerId,
                                                  String nodeId,
                                                  String password,
                                                  Long expirationDate,
                                                  boolean hasPassword);

  /**
   * Creates or updates a document public access on behalf of a user.
   *
   * @param userIdentityId social identity id of the user creating the link
   * @param documentId document node id
   * @param password password of the public access
   * @param expirationDate public access expiration date
   * @param hasPassword document has already old password
   * @return {@link PublicDocumentAccess}, null when it could not be saved
   * @throws DocumentSharingRefusedException when the document's space refuses
   *           the user to manage its access
   * @throws IllegalAccessException when the user is unknown
   */
  PublicDocumentAccess createDocumentPublicAccess(long userIdentityId,
                                                  String documentId,
                                                  String password,
                                                  Long expirationDate,
                                                  boolean hasPassword) throws IllegalAccessException;

  /**
   * Gets a document public access by its node id
   *
   * @param documentId node id
   * @return {@link PublicDocumentAccess}
   */
  PublicDocumentAccess getPublicDocumentAccess(String documentId);

  /**
   * Checks if public access expired. A document whose space keeps its
   * documents inside is answered as a document with no link: expired.
   *
   * @param documentId node id
   * @return true or false
   */
  boolean isPublicDocumentAccessExpired(String documentId);

  /**
   * Checks the validity of a public access. A document whose space keeps its
   * documents inside is answered as a document with no link: not valid.
   * 
   * @param documentId node id
   * @param password password of the document public access
   * @return true if access is valid or false else
   */
  boolean isDocumentPublicAccessValid(String documentId, String password);

  /**
   * Whether the document's space keeps its documents inside, on the space's
   * committed settings: its public link, if it has one, then serves nothing.
   * A document whose space or settings cannot be read is protected.
   *
   * @param documentId document id
   * @return true when the document's public link must serve nothing
   */
  boolean isProtectedBySpace(String documentId);

  /**
   * Checks if a document has public access
   *
   * @param documentId document id
   * @return true or false
   */
  boolean hasDocumentPublicAccess(String documentId);

  /**
   * Gets what the public link page shows of a document that has a public
   * access link: whether the link asks for a password or has expired, and the
   * document's name and mime type, without reading its content. The link is
   * read once and its password is never decoded. Nothing of the document is
   * read when it has no public access link. A document whose space keeps its
   * documents inside is answered as a document with no link.
   *
   * @param documentId document id
   * @return {@link PublicDocumentInfo}
   * @throws ObjectNotFoundException when the document has no public access
   *           link, no longer exists or cannot be read
   */
  PublicDocumentInfo getPublicDocumentInfo(String documentId) throws ObjectNotFoundException;

  /**
   * Revokes a document public access, with no check of the caller.
   *
   * @param documentId document id
   * @deprecated use {@link #revokeDocumentPublicAccess(String, long)}, which
   *             applies the document's space settings to the user revoking
   */
  @Deprecated(forRemoval = true, since = "7.3.0")
  void revokeDocumentPublicAccess(String documentId);

  /**
   * Revokes a document public access on behalf of a user.
   *
   * @param documentId document id
   * @param userIdentityId social identity id of the user revoking the link
   * @throws DocumentSharingRefusedException when the document's space refuses
   *           the user to manage its access
   * @throws IllegalAccessException when the user is unknown
   */
  void revokeDocumentPublicAccess(String documentId, long userIdentityId) throws IllegalAccessException;

}
