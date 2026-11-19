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

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import org.exoplatform.documents.dao.PublicDocumentLinkDAO;
import org.exoplatform.documents.model.PublicDocumentLink;

/**
 * Reads the platform's public links page by page, by keyset on their
 * identifier, so that a link deleted meanwhile never shifts a later one out of
 * the walk, and deletes them one by one
 */
@Component
public class PublicDocumentLinkStorage {

  @Autowired
  private PublicDocumentLinkDAO publicDocumentLinkDAO;

  /**
   * @param lastId the identifier of the last link already read, 0 to start
   * @param limit the maximum count of links to read, positive
   * @return the next links, in ascending identifier order; fewer than
   *         {@code limit} only at the end of the walk
   */
  public List<PublicDocumentLink> getLinksAfter(long lastId, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    return publicDocumentLinkDAO.findLinksAfter(lastId, Limit.of(limit));
  }

  /**
   * @param id a link's identifier
   * @return true when this call deleted the link, false when no link had this
   *         identifier any more
   */
  public boolean deleteLink(long id) {
    return publicDocumentLinkDAO.deleteLink(id) > 0;
  }

}
