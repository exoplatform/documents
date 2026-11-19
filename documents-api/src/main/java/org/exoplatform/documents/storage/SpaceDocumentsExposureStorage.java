/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
*/
package org.exoplatform.documents.storage;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import org.exoplatform.documents.model.ExposedDocument;
import org.exoplatform.social.core.space.model.Space;

/**
 * Reads what a space's documents expose outside the space, and takes the
 * everyone access off a document. Every method reads and writes through a
 * system session, whatever the caller's permissions: the caller checks, before
 * it calls, that the user may manage the space.
 */
public interface SpaceDocumentsExposureStorage {

  /**
   * Keeps, among documents, those that still exist and belong to the space:
   * stored in its drive, or trashed or quarantined from it.
   *
   * @param space a space
   * @param documentIds document identifiers
   * @return the documents of the space, in the order of {@code documentIds}
   * @throws IllegalStateException when a document cannot be read or its space
   *           cannot be resolved
   */
  List<ExposedDocument> getSpaceDocuments(Space space, Collection<String> documentIds);

  /**
   * Lists the documents and folders of the space, in its drive or trashed or
   * quarantined from it, whose own access list holds at least one entry
   * outside the space. A folder's children that inherit its access list are
   * not listed; shortcuts are not listed. An entry's place is decided by
   * {@link org.exoplatform.documents.util.SpaceAccessClassifier}, with the
   * identities of the access list of the space's drive root.
   *
   * @param space a space
   * @param isMember tells whether a user is a member of the space, from the
   *          source the caller's decision reads
   * @return the documents shared outside the space, in no particular order
   * @throws IllegalStateException when the documents cannot be read
   */
  List<ExposedDocument> getSharedOutsideDocuments(Space space, Predicate<String> isMember);

  /**
   * Reads a document again through a fresh system session before its public
   * link is removed and, when asked, takes the everyone entry off its own
   * access list through its permission API; a document with no access list of
   * its own has none to take off.
   *
   * @param space the space the document must still belong to
   * @param documentId the document's identifier
   * @param removeEveryone whether to take the everyone access off the
   *          document
   * @return the document, or null when it no longer exists or no longer
   *         belongs to the space: then nothing was changed. A move committed
   *         after this read is not seen by the caller's next step.
   * @throws IllegalStateException when the document cannot be read or written
   */
  ExposedDocument prepareLinkRemoval(Space space, String documentId, boolean removeEveryone);

}
