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
import org.exoplatform.commons.utils.HTMLEntityEncoder;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.documents.notification.plugin.AddDocumentCollaboratorPlugin;
import org.exoplatform.documents.notification.utils.NotificationConstants;
import org.exoplatform.documents.notification.utils.NotificationUtils;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.notification.LinkProviderUtils;
import org.exoplatform.webui.utils.TimeConvertUtils;

import java.io.Writer;
import java.util.Calendar;
import java.util.Locale;

@TemplateConfigs(templates = {
    @TemplateConfig(pluginId = AddDocumentCollaboratorPlugin.ID, template = "war:/notification/templates/mail/AddDocumentCollaboratorPlugin.gtmpl") })
public class MailTemplateProvider extends TemplateProvider {

  private final IdentityManager identityManager;

  public MailTemplateProvider(InitParams initParams, IdentityManager identityManager) {
    super(initParams);
    this.templateBuilders.put(PluginKey.key(AddDocumentCollaboratorPlugin.ID), new TemplateBuilder());
    this.identityManager = identityManager;
  }

  private class TemplateBuilder extends AbstractTemplateBuilder {

    @Override
    protected MessageInfo makeMessage(NotificationContext notificationContext) {
      NotificationInfo notificationInfo = notificationContext.getNotificationInfo();
      String pluginId = notificationInfo.getKey().getId();
      HTMLEntityEncoder encoder = HTMLEntityEncoder.getInstance();
      String fromUser = notificationInfo.getValueOwnerParameter(NotificationConstants.FROM_USER.getKey());
      String documentUrl = notificationInfo.getValueOwnerParameter(NotificationConstants.DOCUMENT_URL.getKey());
      String documentName = notificationInfo.getValueOwnerParameter(NotificationConstants.DOCUMENT_NAME.getKey());
      String language = getLanguage(notificationInfo);
      TemplateContext templateContext = TemplateContext.newChannelInstance(getChannelKey(), pluginId, language);
      templateContext.put("DOCUMENT_URL", documentUrl);
      templateContext.put("DOCUMENT_NAME", documentName);
      Profile userProfile = NotificationUtils.getUserProfile(identityManager, fromUser);
      templateContext.put("USER", encoder.encode(userProfile.getFullName()));
      templateContext.put("PROFILE_URL", encoder.encode(userProfile.getUrl()));
      templateContext.put("AVATAR", encoder.encode(LinkProviderUtils.getUserAvatarUrl(userProfile)));

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

      Identity receiver = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, notificationInfo.getTo());
      templateContext.put("FIRST_NAME", encoder.encode(receiver.getProfile().getProperty(Profile.FIRST_NAME).toString()));
      // Footer
      templateContext.put("FOOTER_LINK", LinkProviderUtils.getRedirectUrl("notification_settings", receiver.getRemoteId()));
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
