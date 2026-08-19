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
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.document.cleanup.websocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mortbay.cometd.continuation.EXoContinuationBayeux;

import org.exoplatform.document.cleanup.util.CleanupConstants;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.ws.frameworks.cometd.ContinuationService;

/**
 * WebSocket push tests pinning the channel bootstrap, the administrators-only
 * fan-out, the path/owner-free payload and the never-fail contract (a WebSocket
 * error must never break a cleanup operation, e.g. during the Seti warm-up
 * window).
 */
@ExtendWith(MockitoExtension.class)
class CleanupWebSocketServiceTest {

  private static final String     ADMIN_USER   = "admin";

  private static final String     REGULAR_USER = "bob";

  private static final String     UNKNOWN_USER = "ghost";

  @Mock
  private ContinuationService     continuationService;

  @Mock
  private EXoContinuationBayeux   continuationBayeux;

  @Mock
  private IdentityRegistry        identityRegistry;

  @Mock
  private Identity                adminIdentity;

  @Mock
  private Identity                regularIdentity;

  @InjectMocks
  private CleanupWebSocketService webSocketService;

  @Test
  void initCreatesTheCometdChannel() {
    webSocketService.init();

    verify(continuationBayeux).createChannelIfAbsent(CleanupWebSocketService.COMETD_CHANNEL);
  }

  @Test
  void initSwallowsChannelCreationErrors() {
    doThrow(new IllegalStateException("cometd not started")).when(continuationBayeux)
                                                            .createChannelIfAbsent(anyString());

    assertDoesNotThrow(() -> webSocketService.init());
  }

  @Test
  void sendToAdministratorsFiltersOnPlatformAdministratorsMembership() {
    Set<String> connectedUsers = new LinkedHashSet<>(Set.of(ADMIN_USER, REGULAR_USER, UNKNOWN_USER));
    when(continuationBayeux.getConnectedUserIds()).thenReturn(connectedUsers);
    when(identityRegistry.getIdentity(ADMIN_USER)).thenReturn(adminIdentity);
    when(identityRegistry.getIdentity(REGULAR_USER)).thenReturn(regularIdentity);
    when(identityRegistry.getIdentity(UNKNOWN_USER)).thenReturn(null);
    when(adminIdentity.isMemberOf(CleanupConstants.ADMINISTRATORS_GROUP)).thenReturn(true);
    when(regularIdentity.isMemberOf(CleanupConstants.ADMINISTRATORS_GROUP)).thenReturn(false);

    webSocketService.sendToAdministrators(progressMessage());

    verify(continuationService).sendMessage(eq(ADMIN_USER), eq(CleanupWebSocketService.COMETD_CHANNEL), any());
    verify(continuationService, never()).sendMessage(eq(REGULAR_USER), anyString(), any());
    verify(continuationService, never()).sendMessage(eq(UNKNOWN_USER), anyString(), any());
  }

  @Test
  void payloadCarriesEventNameAndCountersButNeverPathsOrOwners() {
    when(continuationBayeux.getConnectedUserIds()).thenReturn(Set.of(ADMIN_USER));
    when(identityRegistry.getIdentity(ADMIN_USER)).thenReturn(adminIdentity);
    when(adminIdentity.isMemberOf(CleanupConstants.ADMINISTRATORS_GROUP)).thenReturn(true);

    webSocketService.sendToAdministrators(progressMessage());

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(continuationService).sendMessage(eq(ADMIN_USER), eq(CleanupWebSocketService.COMETD_CHANNEL), payloadCaptor.capture());
    String payload = String.valueOf(payloadCaptor.getValue());
    assertTrue(payload.contains(CleanupWsMessage.PROGRESS_EVENT), "The payload must name its wsEventName");
    assertTrue(payload.contains("\"campaignId\":12"), "The payload must identify the campaign");
    assertTrue(payload.contains("\"processed\":40"), "The payload must carry the progress counters");
    assertTrue(payload.contains("\"total\":100"), "The payload must carry the progress counters");
    assertFalse(payload.toLowerCase().contains("path"), "The payload must never leak file paths");
    assertFalse(payload.toLowerCase().contains("owner"), "The payload must never leak file owners");
  }

  @Test
  void sendToAdministratorsSwallowsErrors() {
    when(continuationBayeux.getConnectedUserIds()).thenThrow(new IllegalStateException("Seti warm-up"));

    assertDoesNotThrow(() -> webSocketService.sendToAdministrators(progressMessage()));
    verify(continuationService, never()).sendMessage(anyString(), anyString(), any());
  }

  private CleanupWsMessage progressMessage() {
    return new CleanupWsMessage(CleanupWsMessage.PROGRESS_EVENT, 12L, "DRY_RUN_RUNNING", 40, 100, 60L);
  }

}
