package org.exoplatform.documents.notification.provider;

import org.exoplatform.commons.api.notification.NotificationContext;
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
import org.exoplatform.documents.notification.utils.NotificationConstants;
import org.exoplatform.documents.notification.utils.NotificationUtils;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;

import java.io.Writer;

@TemplateConfigs(templates = {
    @TemplateConfig(pluginId = AddDocumentCollaboratorPlugin.ID, template = "war:/notification/templates/push/AddDocumentCollaboratorPlugin.gtmpl") })
public class PushTemplateProvider extends TemplateProvider {

  private final IdentityManager identityManager;

  public PushTemplateProvider(InitParams initParams, IdentityManager identityManager) {
    super(initParams);
    this.templateBuilders.put(PluginKey.key(AddDocumentCollaboratorPlugin.ID), new TemplateBuilder());
    this.identityManager = identityManager;
  }

  private class TemplateBuilder extends AbstractTemplateBuilder {

    @Override
    protected MessageInfo makeMessage(NotificationContext notificationContext) {
      NotificationInfo notificationInfo = notificationContext.getNotificationInfo();
      String pluginId = notificationInfo.getKey().getId();
      String fromUser = notificationInfo.getValueOwnerParameter(NotificationConstants.FROM_USER.getKey());
      String documentName = notificationInfo.getValueOwnerParameter(NotificationConstants.DOCUMENT_NAME.getKey());
      String language = getLanguage(notificationInfo);
      TemplateContext templateContext = TemplateContext.newChannelInstance(getChannelKey(), pluginId, language);
      templateContext.put("DOCUMENT_NAME", documentName);
      Profile userProfile = NotificationUtils.getUserProfile(identityManager, fromUser);
      templateContext.put("USER", userProfile.getFullName());
      String subject = TemplateUtils.processSubject(templateContext);
      String body = TemplateUtils.processGroovy(templateContext);
      notificationContext.setException(templateContext.getException());
      MessageInfo messageInfo = new MessageInfo();
      return messageInfo.subject(subject).body(body).end();
    }

    @Override
    protected boolean makeDigest(NotificationContext notificationContext, Writer writer) {
      return false;
    }
  }
}
