/*
 * Copyright (C) 2025 eXo Platform SAS
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
package org.exoplatform.documents.plugins;

import java.util.List;

import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.portal.config.UserACL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.meeds.social.category.plugin.CategoryPlugin;

@Service
public class DocumentCategoryPlugin implements CategoryPlugin {

  public static final String  OBJECT_TYPE = "document";

  @Autowired
  private UserACL             userAcl;

  @Autowired
  private DocumentFileService documentFileService;

  @Override
  public String getType() {
    return OBJECT_TYPE;
  }

  @Override
  public boolean canAccess(String documentId, String username) {
    AbstractNode document = documentFileService.getDocumentById(documentId);
    return document != null
        && (documentFileService.canAccess(documentId, userAcl.getUserIdentity(username)) || canEdit(documentId, username));
  }

  @Override
  public boolean canEdit(String documentId, String username) {
    return documentFileService.hasEditPermissionOnDocument(documentId, userAcl.getUserIdentity(username));
  }

  @Override
  public List<Long> getCategoryIds(long spaceId) {
    return documentFileService.getDocumentCategoryIds(spaceId, userAcl.getSuperUser());
  }

}
