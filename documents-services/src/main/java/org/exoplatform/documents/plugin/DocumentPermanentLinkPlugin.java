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

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.security.Identity;

import io.meeds.portal.permlink.model.PermanentLinkObject;
import io.meeds.portal.permlink.plugin.PermanentLinkPlugin;
import io.meeds.portal.permlink.service.PermanentLinkService;

import jakarta.annotation.PostConstruct;

@Component
public class DocumentPermanentLinkPlugin implements PermanentLinkPlugin {

  public static final String      OBJECT_TYPE = DocumentAclPlugin.OBJECT_TYPE;

  public static final String      URL_FORMAT  = "/portal/%s/documents?documentPreviewId=%s";

  @Autowired
  private UserACL                 userAcl;

  @Autowired
  private UserPortalConfigService portalConfigService;

  @Autowired
  private PortalContainer         container;

  @PostConstruct
  public void init() {
    container.getComponentInstanceOfType(PermanentLinkService.class).addPlugin(this);
  }

  @Override
  public String getObjectType() {
    return OBJECT_TYPE;
  }

  @Override
  public boolean canAccess(PermanentLinkObject object, Identity identity) throws ObjectNotFoundException {
    return userAcl.hasAccessPermission(OBJECT_TYPE, object.getObjectId(), identity);
  }

  @Override
  public String getDirectAccessUrl(PermanentLinkObject object) throws ObjectNotFoundException {
    return String.format(URL_FORMAT, portalConfigService.getMetaPortal(), object.getObjectId());
  }

}
