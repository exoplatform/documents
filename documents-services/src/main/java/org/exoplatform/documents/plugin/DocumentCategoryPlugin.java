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
package org.exoplatform.documents.plugin;

import java.util.List;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.portal.config.UserACL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.meeds.social.category.plugin.CategoryPlugin;
import io.meeds.social.category.service.CategoryLinkService;
import io.meeds.social.category.service.CategoryPluginService;

import jakarta.annotation.PostConstruct;

@Service
public class DocumentCategoryPlugin implements CategoryPlugin {

  public static final String  OBJECT_TYPE = "document";

  @Autowired
  private UserACL             userAcl;

  @Autowired
  private DocumentFileService documentFileService;

  @Autowired
  private PortalContainer     container;

  private CategoryLinkService categoryLinkService;

  @PostConstruct
  public void init() {
    container.getComponentInstanceOfType(CategoryPluginService.class).addPlugin(this);
  }

  @Override
  public String getType() {
    return OBJECT_TYPE;
  }

  @Override
  public boolean canAccess(String documentId, String username) {
    AbstractNode document = documentFileService.getDocumentById(documentId);
    return document != null && (userAcl.hasAccessPermission(OBJECT_TYPE, documentId, userAcl.getUserIdentity(username))
        || canEdit(documentId, username));
  }

  @Override
  public boolean canEdit(String documentId, String username) {
    return userAcl.hasEditPermission(OBJECT_TYPE, documentId, userAcl.getUserIdentity(username));
  }

  @Override
  public List<Long> getCategoryIds(long spaceIdentityId, String username) {
    if (spaceIdentityId <= 0) {
      return getCategoryLinkService().getLinkedIds(OBJECT_TYPE);
    }
    return documentFileService.getDocumentCategoryIds(spaceIdentityId, username);
  }

  private CategoryLinkService getCategoryLinkService() {
    if (categoryLinkService == null) {
      categoryLinkService = container.getComponentInstanceOfType(CategoryLinkService.class);
    }
    return categoryLinkService;
  }

}
