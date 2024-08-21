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

public class ImportDocumentsPwaPlugin implements PwaNotificationPlugin {

  public static final String ID = "ImportDocumentsPlugin";

  private ResourceBundleService resourceBundleService;

  private static final String   TITLE_LABEL_KEY = "pwa.notification.ImportDocumentsPwaPlugin.title";

  public ImportDocumentsPwaPlugin(ResourceBundleService resourceBundleService) {
    this.resourceBundleService = resourceBundleService;
  }

  @Override
  public String getId() {
    return ID;
  }


  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();

    String title = resourceBundleService.getSharedString(TITLE_LABEL_KEY, localeConfig.getLocale()).replace("{0}",notification.getValueOwnerParameter(
                                            NotificationConstants.TOTAL_NUMBER.getKey()))
                                        .replace("{1}",notification.getValueOwnerParameter(NotificationConstants.FOLDER_NAME.getKey()));

    notificationMessage.setTitle(title);
    String url = notification.getValueOwnerParameter(NotificationConstants.FOLDER_URL.getKey());
    url = url.replace(CommonsUtils.getCurrentDomain(), "");
    notificationMessage.setUrl(url);
    return notificationMessage;  }
}
