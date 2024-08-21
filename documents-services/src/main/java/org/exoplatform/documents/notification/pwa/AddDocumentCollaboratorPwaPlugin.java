/*
 * Copyright (C) 2024 eXo Platform SAS.
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
package org.exoplatform.documents.notification.pwa;

import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.PwaNotificationPlugin;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.notification.utils.NotificationConstants;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.manager.IdentityManager;

public class AddDocumentCollaboratorPwaPlugin implements PwaNotificationPlugin {

  public static final String ID = "AddDocumentCollaboratorPlugin";

  private ResourceBundleService resourceBundleService;

  private static final String   TITLE_LABEL_KEY = "pwa.notification.AddDocumentCollaboratorPwaPlugin.title";
  private IdentityManager identityManager;


  public AddDocumentCollaboratorPwaPlugin(ResourceBundleService resourceBundleService, IdentityManager identityManager) {
    this.resourceBundleService = resourceBundleService;
    this.identityManager = identityManager;

  }

  @Override
  public IdentityManager getIdentityManager() {
    return this.identityManager;
  }

  @Override
  public String getId() {
    return ID ;
  }


  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();

    String userFullName = getFullName(notification.getValueOwnerParameter(NotificationConstants.FROM_USER.getKey()));

    String title = resourceBundleService.getSharedString(TITLE_LABEL_KEY, localeConfig.getLocale()).replace("{0}",userFullName)
                                        .replace("{1}",notification.getValueOwnerParameter(NotificationConstants.DOCUMENT_NAME.getKey()));

    notificationMessage.setTitle(title);
    String url = notification.getValueOwnerParameter(NotificationConstants.DOCUMENT_URL.getKey());
    url = url.replace(CommonsUtils.getCurrentDomain(), "");
    notificationMessage.setUrl(url);
    return notificationMessage;
  }
}
