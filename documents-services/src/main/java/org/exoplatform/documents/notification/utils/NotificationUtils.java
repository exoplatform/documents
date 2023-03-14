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
import org.exoplatform.documents.rest.util.EntityBuilder;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

public class NotificationUtils {

  public static final String JCR_CONTENT      = "jcr:content";

  public static final String EXO_SYMLINK_UUID = "exo:uuid";

  public static final String NT_FILE          = "nt:file";

  public static String getDocumentLink(Node node, SpaceService spaceService, IdentityManager identityManager) throws RepositoryException {
    StringBuilder stringBuilder = new StringBuilder();
    Identity identity = EntityBuilder.getOwnerIdentityFromNodePath(node.getPath(), identityManager, spaceService);
    Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
    String groupId = space.getGroupId().replace("/", ":");
    String domain = CommonsUtils.getCurrentDomain();
    stringBuilder.append(domain).append("/").append(LinkProvider.getPortalName(null)).append("/");
    stringBuilder.append("g/").append(groupId).append("/").append(space.getPrettyName());
    if (node.hasNode(JCR_CONTENT)) {
      stringBuilder.append("/documents?documentPreviewId=");
    } else {
      stringBuilder.append("/documents?folderId=");
    }
    stringBuilder.append(((ExtendedNode) node).getIdentifier());

    return stringBuilder.toString() ;
  }

  public static String getSharedDocumentLink(Node sharedNode, SpaceService spaceService, String spacePrettyName) throws RepositoryException {
    StringBuilder stringBuilder = new StringBuilder();
    String portalOwner = CommonsUtils.getCurrentPortalOwner();
    String domain = CommonsUtils.getCurrentDomain();
    stringBuilder.append(domain)
                 .append("/")
                 .append(LinkProvider.getPortalName(null)).append("/");
    if (spaceService!= null && spacePrettyName != null) {
      Space space = spaceService.getSpaceByPrettyName(spacePrettyName);
      String groupId = space.getGroupId().replace("/", ":");
      stringBuilder.append("g/").append(groupId).append("/").append(spacePrettyName).append("/documents");
    } else {
      stringBuilder.append(portalOwner).append("/documents/Private/Documents");
    }
    boolean isTargetNodeFile = isNodeFile(sharedNode);
    if (isTargetNodeFile) {
      stringBuilder.append("?documentPreviewId=");
    } else {
      stringBuilder.append("?folderId=");
    }
    stringBuilder.append(((NodeImpl) sharedNode).getIdentifier());
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

  public static boolean isNodeFile(Node node) throws RepositoryException {
      Session session = node.getSession();
      Node targetNode = session.getNodeByUUID(node.getProperty(EXO_SYMLINK_UUID).getString());
      return targetNode.isNodeType(NT_FILE);

  }
}
