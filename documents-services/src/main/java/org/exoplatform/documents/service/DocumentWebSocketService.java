/*
 * This file is part of the Meeds project (https://meeds.io/).
 * Copyright (C) 2023 Meeds Association
 * contact@meeds.io
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.exoplatform.documents.service;

import org.exoplatform.documents.model.ActionData;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.websocket.entity.WebSocketMessage;
import org.exoplatform.ws.frameworks.cometd.ContinuationService;
import org.mortbay.cometd.continuation.EXoContinuationBayeux;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class DocumentWebSocketService {

    private static final Log LOG            = ExoLogger.getLogger(DocumentWebSocketService.class);

    public static final String    COMETD_CHANNEL = "/eXo/Application/Addons/Documents";

    private IdentityManager identityManager;

    private ContinuationService   continuationService;

    private String                cometdContextName;

    public DocumentWebSocketService(
                                  IdentityManager identityManager,
                                  ContinuationService continuationService,
                                  EXoContinuationBayeux continuationBayeux) {
        this.identityManager = identityManager;
        this.continuationService = continuationService;
        this.cometdContextName = continuationBayeux.getCometdContextName();
    }

    public void sendMessage(String wsEventName, ActionData actionData, Identity identity ) {
        Set<String> recipientUsers = new HashSet<>();
        String remoteId = identity.getUserId();
        recipientUsers.add(remoteId);
        sendMessage(wsEventName, recipientUsers, actionData);
    }

    /**
     * Propagate an event from Backend to frontend to add dynamism in pages
     *
     * @param wsEventName event name that will allow Browser to distinguish which
     *          behavior to adopt in order to update UI
     * @param recipientUsers {@link Collection} of usernames of receivers
     * @param params an Array of parameters to include in message sent via
     *          WebSocket
     */
    public void sendMessage(String wsEventName, Collection<String> recipientUsers, Object... params) {
        WebSocketMessage messageObject = new WebSocketMessage(wsEventName, params);
        sendMessage(messageObject, recipientUsers);
    }

    /**
     * Propagate an event from Backend to frontend to add dynamism in pages
     *
     * @param messageObject {@link WebSocketMessage} to transmit via WebSocket
     * @param recipientUsers {@link Collection} of usernames of receivers
     */
    public void sendMessage(WebSocketMessage messageObject, Collection<String> recipientUsers) {
        String message = messageObject.toString();
        for (String recipientUser : recipientUsers) {
            if (continuationService.isPresent(recipientUser)) {
                continuationService.sendMessage(recipientUser, COMETD_CHANNEL, message);
            }
        }
    }

    /**
     * @return 'cometd' webapp context name
     */
    public String getCometdContextName() {
        return cometdContextName;
    }

    /**
     * Generate a cometd Token for each user
     *
     * @param username user name for whom the token will be generated
     * @return generated cometd token
     */
    public String getUserToken(String username) {
        try {
            return continuationService.getUserToken(username);
        } catch (Exception e) {
            LOG.warn("Could not retrieve continuation token for user " + username, e);
            return "";
        }
    }
}
