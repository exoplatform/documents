/*
 * Copyright (C) 2023 eXo Platform SAS.
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
package org.exoplatform.documents.listener;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.ActionStatus;
import org.exoplatform.documents.model.ActionType;
import org.exoplatform.documents.notification.plugin.ImportDocumentsPlugin;
import org.exoplatform.documents.notification.utils.NotificationConstants;
import org.exoplatform.documents.service.DocumentWebSocketService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.security.Identity;

public class BulkActionDocumentListener extends Listener<Identity, ActionData> {

  private final DocumentWebSocketService documentWebSocketService;

  public BulkActionDocumentListener(DocumentWebSocketService documentWebSocketService) {
    this.documentWebSocketService = documentWebSocketService;
  }

  @Override
  public void onEvent(Event<Identity, ActionData> event) throws Exception {
    ActionData actionData = event.getData();
    Identity identity = event.getSource();
    documentWebSocketService.sendMessage(actionData.getActionType(), actionData, identity);
    if (actionData.getActionType().equals(ActionType.IMPORT_ZIP.name()) && actionData.getStatus().equals(ActionStatus.DONE_SUCCESSFULLY.name())) {
      sendNotification(actionData, identity);
    }
  }

  private void sendNotification(ActionData importData, Identity identity) {
    String filesCreated = "";
    String filesDuplicated = "";
    String filesUpdated = "";
    String filesIgnored = "";
    String filesFailed = "";
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(NotificationConstants.FROM_USER, identity.getUserId());
    ctx.append(NotificationConstants.FOLDER_URL, importData.getParentFolder());
    ctx.append(NotificationConstants.FOLDER_NAME, importData.getParentFolderName());
    ctx.append(NotificationConstants.TOTAL_NUMBER, String.valueOf(importData.getImportedFilesCount()));
    ctx.append(NotificationConstants.DURATION, String.valueOf((int) (importData.getDuration() / 1000)));
    if (importData.getCreatedFiles().size() > 0) {
      filesCreated = "<li>" + String.join("</li><li>", importData.getCreatedFiles()) + "</li>";
    }
    if (importData.getDuplicatedFiles().size() > 0) {
      filesDuplicated = "<li>" + String.join("</li><li>", importData.getDuplicatedFiles()) + "</li>";
    }
    if (importData.getUpdatedFiles().size() > 0) {
      filesUpdated = "<li>" + String.join("</li><li>", importData.getUpdatedFiles()) + "</li>";
    }
    if (importData.getIgnoredFiles().size() > 0) {
      filesIgnored = "<li>" + String.join("</li><li>", importData.getIgnoredFiles()) + "</li>";
    }
    if (importData.getFailedFiles().size() > 0) {
      filesFailed = "<li>" + String.join("</li><li>", importData.getFailedFiles()) + "</li>";
    }
    ctx.append(NotificationConstants.FILES_CREATED, filesCreated);
    ctx.append(NotificationConstants.FILES_DUPLICATED, filesDuplicated);
    ctx.append(NotificationConstants.FILES_UPDATED, filesUpdated);
    ctx.append(NotificationConstants.FILES_IGNORED, filesIgnored);
    ctx.append(NotificationConstants.FILES_FAILED, filesFailed);
    ctx.append(NotificationConstants.RECEIVERS, identity.getUserId());
    ctx.getNotificationExecutor().with(ctx.makeCommand(PluginKey.key(ImportDocumentsPlugin.ID))).execute(ctx);
  }

}
