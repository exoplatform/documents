/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.websocket;

import java.util.Set;

import org.mortbay.cometd.continuation.EXoContinuationBayeux;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.document.cleanup.util.CleanupConstants;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.ws.frameworks.cometd.ContinuationService;

import io.meeds.social.util.JsonUtils;

import jakarta.annotation.PostConstruct;

/**
 * Push-only CometD channel notifying connected administrators of campaign
 * progress and state changes. Errors are swallowed: WebSocket freshness is a UX
 * enhancement, never a correctness requirement (tolerates the Seti warm-up
 * no-op window).
 */
@Service
public class CleanupWebSocketService {

  public static final String    COMETD_CHANNEL = "/eXo/Application/CleanupCampaign";

  private static final Log      LOG            = ExoLogger.getLogger(CleanupWebSocketService.class);

  @Autowired
  private ContinuationService   continuationService;

  @Autowired
  private EXoContinuationBayeux continuationBayeux;

  @Autowired
  private IdentityRegistry      identityRegistry;

  @PostConstruct
  public void init() {
    try {
      continuationBayeux.createChannelIfAbsent(COMETD_CHANNEL);
    } catch (Exception e) {
      LOG.warn("Error creating cleanup campaign CometD channel", e);
    }
  }

  /**
   * Pushes a message to every connected member of /platform/administrators.
   *
   * @param message flat progress/state payload
   */
  public void sendToAdministrators(CleanupWsMessage message) {
    try {
      String wsMessage = JsonUtils.toJsonString(message);
      Set<String> connectedUserIds = continuationBayeux.getConnectedUserIds();
      for (String username : connectedUserIds) {
        if (isAdministrator(username)) {
          continuationService.sendMessage(username, COMETD_CHANNEL, wsMessage);
        }
      }
    } catch (Exception e) {
      // Never fail a cleanup operation for a WebSocket push (Seti warm-up)
      LOG.debug("Error sending cleanup campaign WebSocket message", e);
    }
  }

  private boolean isAdministrator(String username) {
    Identity identity = identityRegistry.getIdentity(username);
    return identity != null && identity.isMemberOf(CleanupConstants.ADMINISTRATORS_GROUP);
  }

}
