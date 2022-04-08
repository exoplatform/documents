/*
 * Copyright (C) 2022 eXo Platform SAS.
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
package org.exoplatform.documents.notification.utils;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.service.LinkProvider;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

public class NotificationUtils {


  public static String getSharedDocumentLink(String nodeUuid, String spacePrettyName) {
    StringBuilder stringBuilder = new StringBuilder();
    String portalOwner = CommonsUtils.getCurrentPortalOwner();
    String domain = CommonsUtils.getCurrentDomain();
    stringBuilder.append(domain)
                 .append("/")
                 .append(LinkProvider.getPortalName(null)).append("/");
    if (spacePrettyName != null) {
      stringBuilder.append("g/:spaces:space/")
                   .append(spacePrettyName)
                   .append("/documents/Shared?documentPreviewId=");
    } else {
      stringBuilder.append(portalOwner)
                   .append("/documents/Private/Documents/Shared?documentPreviewId=");
    }
    stringBuilder.append(nodeUuid);
    return stringBuilder.toString();
  }

  public static String getDocumentTitle(Node node) throws RepositoryException {
    String title = null;
    if (node.hasProperty("exo:title")) {
      title = node.getProperty("exo:title").getValue().getString();
    }
    if (title == null && node.hasNode("jcr:content")) {
      Node content = node.getNode("jcr:content");
      if (content.hasProperty("dc:title")) {
        title = content.getProperty("dc:title").getValue().getString();
      }
    }
    if (title == null) {
      title = node.getName();
    }
    return title;
  }

  public static Profile getUserProfile(IdentityManager identityManager, String userName) {
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, userName);
    return identity.getProfile();
  }
}
