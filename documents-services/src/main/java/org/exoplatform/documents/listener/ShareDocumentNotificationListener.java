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

  private static final String EXO_SYMLINK_UUID = "exo:uuid";

  @Override
  public void onEvent(Event<Identity, Node> event) throws Exception {
    Node targetNode = event.getData();
    Identity targetIdentity = event.getSource();
    String currentUser = ConversationState.getCurrent().getIdentity().getUserId();
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    String documentLink = NotificationUtils.getSharedDocumentLink(targetNode.getProperty(EXO_SYMLINK_UUID).getString(), null);
    if (targetIdentity.getProviderId().equals(SpaceIdentityProvider.NAME)) {
      documentLink = NotificationUtils.getSharedDocumentLink(targetNode.getProperty(EXO_SYMLINK_UUID).getString(),
                                                             targetIdentity.getRemoteId());
    }
    ctx.append(NotificationConstants.FROM_USER, currentUser);
    ctx.append(NotificationConstants.DOCUMENT_URL, documentLink);
    ctx.append(NotificationConstants.DOCUMENT_NAME, NotificationUtils.getDocumentTitle(targetNode));
    ctx.append(NotificationConstants.RECEIVERS, targetIdentity.getRemoteId());
    ctx.getNotificationExecutor().with(ctx.makeCommand(PluginKey.key(AddDocumentCollaboratorPlugin.ID))).execute(ctx);

  }
}
