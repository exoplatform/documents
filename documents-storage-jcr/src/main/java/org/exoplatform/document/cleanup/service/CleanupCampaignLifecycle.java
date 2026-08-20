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
package org.exoplatform.document.cleanup.service;

import static org.exoplatform.document.cleanup.constant.CleanupCampaignState.CANCELLED;
import static org.exoplatform.document.cleanup.constant.CleanupCampaignState.COMPLETED;
import static org.exoplatform.document.cleanup.constant.CleanupCampaignState.DRAFT;
import static org.exoplatform.document.cleanup.constant.CleanupCampaignState.DRY_RUN_RUNNING;
import static org.exoplatform.document.cleanup.constant.CleanupCampaignState.EXECUTING;
import static org.exoplatform.document.cleanup.constant.CleanupCampaignState.LOCKED;
import static org.exoplatform.document.cleanup.constant.CleanupCampaignState.PUBLISHED;
import static org.exoplatform.document.cleanup.constant.CleanupCampaignState.SIMULATED;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.storage.CleanupCampaignStorage;
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage;
import org.exoplatform.document.cleanup.websocket.CleanupWebSocketService;
import org.exoplatform.document.cleanup.websocket.CleanupWsMessage;

/**
 * Single authority over campaign state transitions and their side effects: the
 * allowed-transitions guard, persisting the new state, the WebSocket
 * stateChanged event, and registering/unregistering the JCR freshness
 * observation listener on entry/exit of PUBLISHED. Every state change of the
 * cleanup services goes through {@link #transition}. Depends only on Storage
 * and the WebSocket service, never on the cleanup services (no bean cycle).
 */
@Component
public class CleanupCampaignLifecycle {

  private static final Map<CleanupCampaignState, Set<CleanupCampaignState>> ALLOWED_TRANSITIONS =
                                                                                                Map.of(DRAFT,
                                                                                                       Set.of(DRY_RUN_RUNNING,
                                                                                                              CANCELLED),
                                                                                                       DRY_RUN_RUNNING,
                                                                                                       Set.of(SIMULATED,
                                                                                                              CANCELLED),
                                                                                                       SIMULATED,
                                                                                                       Set.of(PUBLISHED,
                                                                                                              CANCELLED),
                                                                                                       PUBLISHED,
                                                                                                       Set.of(LOCKED, CANCELLED),
                                                                                                       LOCKED,
                                                                                                       Set.of(EXECUTING,
                                                                                                              CANCELLED),
                                                                                                       EXECUTING,
                                                                                                       Set.of(COMPLETED,
                                                                                                              CANCELLED),
                                                                                                       COMPLETED,
                                                                                                       Set.of(),
                                                                                                       CANCELLED,
                                                                                                       Set.of());

  @Autowired
  private CleanupCampaignStorage                                            campaignStorage;

  @Autowired
  private CleanupJcrStorage                                                 cleanupJcrStorage;

  @Autowired
  private CleanupWebSocketService                                           webSocketService;

  /**
   * Variant of
   * {@link #transition(CleanupCampaign, CleanupCampaignState, BiConsumer)} for
   * transitions never entering PUBLISHED.
   *
   * @param campaign campaign to transition, its non-state fields already set
   * @param targetState target state
   * @return the persisted campaign
   */
  public CleanupCampaign transition(CleanupCampaign campaign, CleanupCampaignState targetState) {
    return transition(campaign, targetState, null);
  }

  /**
   * Applies a campaign state transition with all its side effects: guards it
   * against the allowed-transitions map, persists the new state,
   * registers/unregisters the JCR freshness observation listener on entry/exit
   * of PUBLISHED (a campaign leaving PUBLISHED or getting cancelled never leaks
   * the listener) and broadcasts the stateChanged WebSocket event.
   *
   * @param campaign campaign to transition, its non-state fields already set
   * @param targetState target state
   * @param refreshCallback observation callback, mandatory when entering
   *          PUBLISHED
   * @return the persisted campaign
   * @throws IllegalArgumentException "cleanup.invalidState" on an illegal
   *           transition
   */
  public CleanupCampaign transition(CleanupCampaign campaign,
                                    CleanupCampaignState targetState,
                                    BiConsumer<String, String> refreshCallback) {
    CleanupCampaignState fromState = campaign.getState();
    if (fromState == null || !ALLOWED_TRANSITIONS.get(fromState).contains(targetState)) {
      throw new IllegalArgumentException("cleanup.invalidState");
    }
    campaign.setState(targetState);
    CleanupCampaign savedCampaign = campaignStorage.saveCampaign(campaign);
    if (targetState == PUBLISHED) {
      if (refreshCallback == null) {
        throw new IllegalArgumentException("cleanup.observationCallbackMandatory");
      }
      cleanupJcrStorage.registerObservationListener(refreshCallback);
    } else if (fromState == PUBLISHED || targetState == CANCELLED) {
      // Exit of PUBLISHED; unregistering is idempotent, so any cancellation
      // defensively clears a possibly leaked listener too
      cleanupJcrStorage.unregisterObservationListener();
    }
    sendStateChanged(savedCampaign);
    return savedCampaign;
  }

  private void sendStateChanged(CleanupCampaign campaign) {
    webSocketService.sendToAdministrators(new CleanupWsMessage(CleanupWsMessage.STATE_CHANGED_EVENT,
                                                               campaign.getId(),
                                                               campaign.getState().name(),
                                                               campaign.getProcessedCount(),
                                                               campaign.getTotalCount(),
                                                               campaign.getEtaSeconds()));
  }

}
