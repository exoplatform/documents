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
package org.exoplatform.documents.service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.ExposedDocumentPage;
import org.exoplatform.documents.model.SpaceDocumentsExposure;
import org.exoplatform.social.core.space.model.Space;

/**
 * What a space's documents expose outside the space: their public links and
 * their access entries outside the space, read for the space's managers, and
 * the removal of the public links when the space keeps its documents inside.
 */
public interface SpaceDocumentsExposureService {

  /**
   * @param spaceId a space's identifier
   * @param username the user asking
   * @return the counts of the space's documents carrying a public link and of
   *         those shared outside the space
   * @throws ObjectNotFoundException when the space does not exist
   * @throws IllegalAccessException when the user may not manage the space
   */
  SpaceDocumentsExposure getExposure(long spaceId, String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * @param spaceId a space's identifier
   * @param offset the index of the first document of the page, from 0
   * @param limit the maximum count of documents of the page, positive
   * @param username the user asking
   * @return a page of the space's documents carrying a public link, sorted by
   *         name then identifier, with the size of the whole list
   * @throws ObjectNotFoundException when the space does not exist
   * @throws IllegalAccessException when the user may not manage the space
   * @throws IllegalArgumentException when offset or limit is out of range
   */
  ExposedDocumentPage getPublicLinkDocuments(long spaceId,
                                             int offset,
                                             int limit,
                                             String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Removes the public links of the space's documents before the space's
   * "Keep documents inside the space" setting changes, the user's right to
   * change it being already checked. On a turn-on, a document whose link is
   * removed also loses the everyone entry of its own access list: one it
   * inherits from its folder stays on that folder, and the children of a
   * folder that inherit its access list lose the folder's own entry with it. Each link is checked again, and
   * its document read again, just before its removal, and logged once
   * removed; a link removed before a failure stays removed. The window that
   * remains is that of one link: its document moved out of the space, or the
   * link removed by another actor, between that check and its deletion.
   *
   * @param space the space whose setting changes
   * @param documentsKeptInside the value about to be stored
   * @param username the user changing the setting
   * @throws IllegalStateException when a link's document cannot be read or
   *           written: the setting must not change
   */
  void removePublicLinks(Space space, boolean documentsKeptInside, String username);

}
