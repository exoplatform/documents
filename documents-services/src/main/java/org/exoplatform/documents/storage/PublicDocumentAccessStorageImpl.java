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
package org.exoplatform.documents.storage;

import org.exoplatform.documents.dao.PublicDocumentAccessDAO;
import org.exoplatform.documents.entity.PublicDocumentAccessEntity;
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.documents.rest.util.EntityBuilder;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

public class PublicDocumentAccessStorageImpl implements PublicDocumentAccessStorage {

  private PublicDocumentAccessDAO publicDocumentAccessDAO;

  private IdentityManager  identityManager;

  public PublicDocumentAccessStorageImpl(PublicDocumentAccessDAO publicDocumentAccessDAO, IdentityManager identityManager) {
    this.publicDocumentAccessDAO = publicDocumentAccessDAO;
    this.identityManager = identityManager;
  }

  @Override
  public PublicDocumentAccess getPublicDocumentAccessByNodeId(String nodeId) {
    return EntityBuilder.toDocumentToken(publicDocumentAccessDAO.getPublicDocumentAccessByNodeId(nodeId));
  }

  @Override
  public void removePublicDocumentAccess(String nodeId) {
    PublicDocumentAccess publicDocumentAccess = getPublicDocumentAccessByNodeId(nodeId);
    if (publicDocumentAccess != null) {
      publicDocumentAccessDAO.delete(EntityBuilder.toDocumentTokenEntity(publicDocumentAccess));
    }
  }
  @Override
  public PublicDocumentAccess savePublicDocumentAccess(PublicDocumentAccess publicDocumentAccess, long userId) {
    if (publicDocumentAccess == null) {
      throw new IllegalArgumentException("documentToken argument is null");
    }
    Identity identity = identityManager.getIdentity(String.valueOf(userId));
    if (identity == null) {
      throw new IllegalArgumentException("identity is not exist");
    }
    PublicDocumentAccessEntity publicDocumentAccessEntity = EntityBuilder.toDocumentTokenEntity(publicDocumentAccess);
    if (publicDocumentAccessEntity.getId() == 0L) {
      publicDocumentAccessEntity.setId(null);
      return EntityBuilder.toDocumentToken(publicDocumentAccessDAO.create(publicDocumentAccessEntity));
    }
    return EntityBuilder.toDocumentToken(publicDocumentAccessDAO.update(publicDocumentAccessEntity));
  }

}
