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

import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.service.DocumentWebSocketService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

public class BulkOperationsDocumentNotificationListener extends Listener<Identity, ActionData> {

  private final IdentityManager           identityManager;

  private final DocumentWebSocketService documentWebSocketService;

  public BulkOperationsDocumentNotificationListener(IdentityManager identityManager,
                                                    DocumentWebSocketService documentWebSocketService) {
    this.identityManager = identityManager;
    this.documentWebSocketService = documentWebSocketService;
  }

  @Override
  public void onEvent(Event<Identity, ActionData> event) throws Exception {
    ActionData actionData = event.getData();
    Identity identity = event.getSource();
    documentWebSocketService.sendMessage(actionData.getActionType(), actionData, identity);
  }
}
