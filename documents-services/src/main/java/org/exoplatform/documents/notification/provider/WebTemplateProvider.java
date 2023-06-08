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
package org.exoplatform.documents.notification.provider;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.NotificationMessageUtils;
import org.exoplatform.commons.api.notification.annotation.TemplateConfig;
import org.exoplatform.commons.api.notification.annotation.TemplateConfigs;
import org.exoplatform.commons.api.notification.channel.template.AbstractTemplateBuilder;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.commons.api.notification.model.MessageInfo;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.documents.notification.plugin.AddDocumentCollaboratorPlugin;
import org.exoplatform.documents.notification.plugin.ImportDocumentsPlugin;
import org.exoplatform.documents.notification.utils.NotificationConstants;
import org.exoplatform.documents.notification.utils.NotificationUtils;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.webui.utils.TimeConvertUtils;

import java.io.Writer;
import java.util.Calendar;
import java.util.Locale;

@TemplateConfigs(templates = {
    @TemplateConfig(pluginId = AddDocumentCollaboratorPlugin.ID, template = "war:/notification/templates/web/AddDocumentCollaboratorPlugin.gtmpl"),
        @TemplateConfig(pluginId = ImportDocumentsPlugin.ID, template = "war:/notification/templates/web/ImportDocumentsPlugin.gtmpl") })

public class WebTemplateProvider extends TemplateProvider {

  private final IdentityManager identityManager;

  public WebTemplateProvider(InitParams initParams, IdentityManager identityManager) {
    super(initParams);
    this.templateBuilders.put(PluginKey.key(AddDocumentCollaboratorPlugin.ID), new TemplateBuilder());
    this.templateBuilders.put(PluginKey.key(ImportDocumentsPlugin.ID), new TemplateBuilder());
    this.identityManager = identityManager;
  }

  private class TemplateBuilder extends AbstractTemplateBuilder {

    @Override
    protected MessageInfo makeMessage(NotificationContext notificationContext) {
      NotificationInfo notificationInfo = notificationContext.getNotificationInfo();
      String pluginId = notificationInfo.getKey().getId();
      String fromUser = notificationInfo.getValueOwnerParameter(NotificationConstants.FROM_USER.getKey());
      String documentUrl = notificationInfo.getValueOwnerParameter(NotificationConstants.DOCUMENT_URL.getKey());
      String documentName = notificationInfo.getValueOwnerParameter(NotificationConstants.DOCUMENT_NAME.getKey());
      String folderUrl = notificationInfo.getValueOwnerParameter(NotificationConstants.FOLDER_URL.getKey());
      String folderName = notificationInfo.getValueOwnerParameter(NotificationConstants.FOLDER_NAME.getKey());
      String totalNumber = notificationInfo.getValueOwnerParameter(NotificationConstants.TOTAL_NUMBER.getKey());
      String language = getLanguage(notificationInfo);
      TemplateContext templateContext = TemplateContext.newChannelInstance(getChannelKey(), pluginId, language);

      templateContext.put("DOCUMENT_URL", documentUrl);
      templateContext.put("DOCUMENT_NAME", documentName);
      templateContext.put("FOLDER_URL", folderUrl);
      templateContext.put("FOLDER_NAME", folderName);
      templateContext.put("TOTAL_NUMBER", totalNumber);
      Profile userProfile = NotificationUtils.getUserProfile(identityManager, fromUser);
      templateContext.put("USER", userProfile.getFullName());
      templateContext.put("PROFILE_URL", userProfile.getUrl());
      templateContext.put("AVATAR", userProfile.getAvatarUrl());
      Calendar lastModified = Calendar.getInstance();
      lastModified.setTimeInMillis(notificationInfo.getLastModifiedDate());
      templateContext.put("LAST_UPDATED_TIME",
                          TimeConvertUtils.convertXTimeAgoByTimeServer(lastModified.getTime(),
                                                                       "EE, dd yyyy",
                                                                       new Locale(language),
                                                                       TimeConvertUtils.YEAR));
      boolean isRead =
                     Boolean.parseBoolean(notificationInfo.getValueOwnerParameter(NotificationMessageUtils.READ_PORPERTY.getKey()));
      templateContext.put("READ", isRead ? "read" : "unread");
      templateContext.put("NOTIFICATION_ID", notificationInfo.getId());
      String body = TemplateUtils.processGroovy(templateContext);
      notificationContext.setException(templateContext.getException());
      MessageInfo messageInfo = new MessageInfo();
      return messageInfo.body(body).end();
    }

    @Override
    protected boolean makeDigest(NotificationContext notificationContext, Writer writer) {
      return false;
    }
  }
}
