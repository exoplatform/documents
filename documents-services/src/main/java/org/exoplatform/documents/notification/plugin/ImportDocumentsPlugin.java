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
package org.exoplatform.documents.notification.plugin;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.documents.notification.utils.NotificationConstants;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class ImportDocumentsPlugin extends BaseNotificationPlugin {

  public static final String ID = "ImportDocumentsPlugin";

  private final SpaceService spaceService;

  public ImportDocumentsPlugin(InitParams initParams, SpaceService spaceService) {
    super(initParams);
    this.spaceService = spaceService;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean isValid(NotificationContext notificationContext) {
    return true;
  }

  @Override
  protected NotificationInfo makeNotification(NotificationContext notificationContext) {
    String fromUser = notificationContext.value(NotificationConstants.FROM_USER);
    String target = notificationContext.value(NotificationConstants.RECEIVERS);
    List<String> receivers;
    Space space = spaceService.getSpaceByPrettyName(target);
    if (space != null) {
      receivers = new LinkedList<>(Arrays.asList(space.getMembers()));
      receivers.remove(fromUser);
    } else {
      receivers = new LinkedList<>(List.of(target));
    }
    String documentUrl = notificationContext.value(NotificationConstants.FOLDER_URL);
    String folderName = notificationContext.value(NotificationConstants.FOLDER_NAME);
    String duration = notificationContext.value(NotificationConstants.DURATION);
    String status = notificationContext.value(NotificationConstants.STATUS);
    String total = notificationContext.value(NotificationConstants.TOTAL_NUMBER);
    String filesCreated = notificationContext.value(NotificationConstants.FILES_CREATED);
    String filesDuplicated = notificationContext.value(NotificationConstants.FILES_DUPLICATED);
    String filesUpdated = notificationContext.value(NotificationConstants.FILES_UPDATED);
    String filesIgnored = notificationContext.value(NotificationConstants.FILES_IGNORED);
    String filesFailed = notificationContext.value(NotificationConstants.FILES_FAILED);
    return NotificationInfo.instance()
                           .setFrom(fromUser)
                           .to(receivers)
                           .with(NotificationConstants.FROM_USER.getKey(), fromUser)
                           .with(NotificationConstants.FOLDER_URL.getKey(), documentUrl)
                           .with(NotificationConstants.FOLDER_NAME.getKey(), folderName)
                           .with(NotificationConstants.DURATION.getKey(), duration)
                           .with(NotificationConstants.STATUS.getKey(), status)
                           .with(NotificationConstants.TOTAL_NUMBER.getKey(), total)
                           .with(NotificationConstants.FILES_CREATED.getKey(), filesCreated)
                           .with(NotificationConstants.FILES_DUPLICATED.getKey(), filesDuplicated)
                           .with(NotificationConstants.FILES_UPDATED.getKey(), filesUpdated)
                           .with(NotificationConstants.FILES_IGNORED.getKey(), filesIgnored)
                           .with(NotificationConstants.FILES_FAILED.getKey(), filesFailed)
                           .key(getKey())
                           .end();
  }
}
