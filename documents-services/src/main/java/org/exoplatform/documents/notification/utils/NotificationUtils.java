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
