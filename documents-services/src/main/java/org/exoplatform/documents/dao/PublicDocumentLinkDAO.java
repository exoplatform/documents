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
package org.exoplatform.documents.dao;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import org.exoplatform.documents.entity.PublicDocumentAccessEntity;
import org.exoplatform.documents.model.PublicDocumentLink;

/**
 * Walks the platform's public links: the rows are read by keyset on their
 * identifier and deleted one by one, without loading any entity into the
 * persistence context
 */
public interface PublicDocumentLinkDAO extends Repository<PublicDocumentAccessEntity, Long> {

  /**
   * @param lastId the identifier of the last link already read, 0 to start
   * @param limit the maximum count of links to read
   * @return the links whose identifier is greater than {@code lastId}, in
   *         ascending identifier order
   */
  @Query("SELECT new org.exoplatform.documents.model.PublicDocumentLink(p.id, p.nodeId) FROM PublicDocumentAccess p"
      + " WHERE p.id > :lastId ORDER BY p.id ASC")
  List<PublicDocumentLink> findLinksAfter(@Param("lastId") long lastId, Limit limit);

  /**
   * @param id a link's identifier
   * @return the count of links deleted: 0 when no link has this identifier
   *         any more
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM PublicDocumentAccess p WHERE p.id = :id")
  int deleteLink(@Param("id") long id);

}
