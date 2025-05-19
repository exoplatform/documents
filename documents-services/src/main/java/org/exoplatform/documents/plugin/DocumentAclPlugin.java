/**
 * Copyright (C) 2025 eXo Platform SAS.
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
 */
package org.exoplatform.documents.plugin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Identity;

import io.meeds.portal.plugin.AclPlugin;

import jakarta.annotation.PostConstruct;

@Component
public class DocumentAclPlugin implements AclPlugin {

  public static final String  OBJECT_TYPE = "document";

  private static final Log    LOG         = ExoLogger.getLogger(DocumentAclPlugin.class);

  @Autowired
  private DocumentFileService documentFileService;

  @Autowired
  private PortalContainer     container;

  @PostConstruct
  public void init() {
    container.getComponentInstanceOfType(UserACL.class).addAclPlugin(this);
  }

  @Override
  public String getObjectType() {
    return OBJECT_TYPE;
  }

  @Override
  public boolean hasPermission(String objectId,
                               String permissionType,
                               Identity identity) {
    try {
      return switch (permissionType) {
      case VIEW_PERMISSION_TYPE: {
        yield documentFileService.canAccess(objectId, identity);
      }
      case EDIT_PERMISSION_TYPE, DELETE_PERMISSION_TYPE: {
        yield documentFileService.hasEditPermissionOnDocument(objectId, identity);
      }
      default:
        yield false;
      };
    } catch (Exception e) {
      LOG.warn("Error while checking permission '{}' on document '{}' for user '{}'",
               permissionType,
               objectId,
               identity == null ? "" : identity.getUserId());
      return false;
    }
  }

}
