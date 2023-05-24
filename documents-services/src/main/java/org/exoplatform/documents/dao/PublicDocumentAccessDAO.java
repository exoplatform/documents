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
package org.exoplatform.documents.dao;

import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.documents.entity.PublicDocumentAccessEntity;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

public class PublicDocumentAccessDAO extends GenericDAOJPAImpl<PublicDocumentAccessEntity, Long> {

  public PublicDocumentAccessEntity getPublicDocumentAccessByNodeId(String nodeId) {
    TypedQuery<PublicDocumentAccessEntity> query = getEntityManager().createNamedQuery("PublicDocumentAccess.getTokenByNodeId",
                                                                                PublicDocumentAccessEntity.class);
    query.setParameter("nodeId", nodeId);
    try {
      return query.getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }
}
