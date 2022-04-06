package org.exoplatform.documents.listener;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.documents.notification.plugin.AddDocumentCollaboratorPlugin;
import org.exoplatform.documents.notification.utils.NotificationConstants;
import org.exoplatform.documents.notification.utils.NotificationUtils;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;

import javax.jcr.Node;

public class ShareDocumentNotificationListener extends Listener<Identity, Node> {

  @Override
  public void onEvent(Event<Identity, Node> event) throws Exception {
    Node targetNode = event.getData();
    Identity targetIdentity = event.getSource();
    String currentUser = ConversationState.getCurrent().getIdentity().getUserId();
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    String documentLink = NotificationUtils.getSharedDocumentLink(targetNode.getUUID(), null);
    if (targetIdentity.getProviderId().equals(SpaceIdentityProvider.NAME)) {
      documentLink = NotificationUtils.getSharedDocumentLink(targetNode.getUUID(), targetIdentity.getRemoteId());
    }
    ctx.append(NotificationConstants.FROM_USER, currentUser);
    ctx.append(NotificationConstants.DOCUMENT_URL, documentLink);
    ctx.append(NotificationConstants.DOCUMENT_NAME, NotificationUtils.getDocumentTitle(targetNode));
    ctx.append(NotificationConstants.RECEIVERS, targetIdentity.getRemoteId());
    ctx.getNotificationExecutor().with(ctx.makeCommand(PluginKey.key(AddDocumentCollaboratorPlugin.ID))).execute(ctx);

  }
}
