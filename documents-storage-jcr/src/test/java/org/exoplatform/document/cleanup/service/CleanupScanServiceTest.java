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
package org.exoplatform.document.cleanup.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.storage.CleanupCampaignStorage;
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage;
import org.exoplatform.document.cleanup.websocket.CleanupWebSocketService;
import org.exoplatform.document.cleanup.websocket.CleanupWsMessage;

/**
 * Scan worker tests pinning the count-first denominator, the per-batch
 * candidate persistence + checkpoint/ETA updates + progress push, the resume
 * from a persisted checkpoint, the abort check between batches and the
 * checkpoint-resumable error handling. The JCR walking itself is mocked
 * through {@link CleanupJcrStorage}'s streaming callback.
 */
@ExtendWith(MockitoExtension.class)
class CleanupScanServiceTest {

  private static final long       CAMPAIGN_ID = 12L;

  private static final int        BATCH_SIZE  = 2;

  @Mock
  private CleanupCampaignStorage  campaignStorage;

  @Mock
  private CleanupCampaignLifecycle campaignLifecycle;

  @Mock
  private CleanupJcrStorage       cleanupJcrStorage;

  @Mock
  private CleanupSettingService   settingService;

  @Mock
  private CleanupWebSocketService webSocketService;

  @InjectMocks
  private CleanupScanService      scanService;

  private CleanupCampaign         campaign;

  @BeforeEach
  void setUp() {
    campaign = new CleanupCampaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setName("Scan me");
    campaign.setState(CleanupCampaignState.DRAFT);
    campaign.setParams(new CleanupParams(6, 1024L, 7, 5, List.of(), BATCH_SIZE));
    lenient().when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    lenient().when(settingService.getBatchSize()).thenReturn(BATCH_SIZE);
    // The lifecycle bean owns the state machine: emulate the state change so
    // the worker's re-reads observe it
    lenient().when(campaignLifecycle.transition(any(CleanupCampaign.class), any(CleanupCampaignState.class)))
             .thenAnswer(invocation -> {
               CleanupCampaign transitioned = invocation.getArgument(0);
               transitioned.setState(invocation.getArgument(1));
               return transitioned;
             });
  }

  @Test
  void startScanRejectsUnknownCampaign() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> scanService.startScan(CAMPAIGN_ID));
  }

  @Test
  void scanCountsFirstThenPersistsBatchesCheckpointsAndCompletes() throws ObjectNotFoundException {
    // 3 files under /Users, none elsewhere: 2 batches of BATCH_SIZE=2
    when(cleanupJcrStorage.countFiles("/Users")).thenReturn(3L);
    when(cleanupJcrStorage.countFiles("/Groups/spaces")).thenReturn(0L);
    when(cleanupJcrStorage.countFiles("/Trash")).thenReturn(0L);
    doAnswer(invocation -> {
      Function<List<CleanupCandidate>, Boolean> batchConsumer = invocation.getArgument(4);
      batchConsumer.apply(List.of(candidate("uuid-" + invocation.getArgument(1))));
      return null;
    }).when(cleanupJcrStorage).scanRoot(eq("/Users"), anyLong(), anyInt(), any(), any());

    scanService.startScan(CAMPAIGN_ID);

    verify(campaignLifecycle, timeout(5000)).transition(campaign, CleanupCampaignState.SIMULATED);
    // DRAFT campaigns first transition into DRY_RUN_RUNNING
    verify(campaignLifecycle).transition(campaign, CleanupCampaignState.DRY_RUN_RUNNING);
    // The denominator is counted once per root, before any batch
    verify(cleanupJcrStorage).countFiles("/Users");
    verify(cleanupJcrStorage).countFiles("/Groups/spaces");
    verify(cleanupJcrStorage).countFiles("/Trash");
    // Streamed candidates are persisted per batch
    verify(campaignStorage).saveCandidates(eq(CAMPAIGN_ID), argThat(candidates -> candidates.size() == 1
                                                                                  && "uuid-0".equals(candidates.get(0)
                                                                                                               .getNodeUuid())));
    verify(campaignStorage).saveCandidates(eq(CAMPAIGN_ID), argThat(candidates -> "uuid-2".equals(candidates.get(0)
                                                                                                            .getNodeUuid())));
    // Progress/checkpoint after each batch: processed capped at the root total
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(3L), eq(2L), anyLong(), eq("/Users"), eq(2L));
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(3L), eq(3L), anyLong(), eq("/Users"), eq(4L));
    // Administrators are notified per batch with counters only
    ArgumentCaptor<CleanupWsMessage> messageCaptor = ArgumentCaptor.forClass(CleanupWsMessage.class);
    verify(webSocketService, org.mockito.Mockito.times(2)).sendToAdministrators(messageCaptor.capture());
    assertEquals(CleanupWsMessage.PROGRESS_EVENT, messageCaptor.getValue().getWsEventName());
    assertEquals(3L, messageCaptor.getValue().getTotal());
    assertEquals(3L, messageCaptor.getValue().getProcessed());
    // Terminal counters are frozen on the campaign before the final transition
    assertEquals(3, campaign.getTotalCount());
    assertEquals(3, campaign.getProcessedCount());
    assertEquals(0, campaign.getEtaSeconds());
    assertEquals(CleanupCampaignState.SIMULATED, campaign.getState());
  }

  @Test
  void scanResumesFromPersistedCheckpoint() throws ObjectNotFoundException {
    campaign.setState(CleanupCampaignState.DRY_RUN_RUNNING);
    campaign.setCheckpointPath("/Groups/spaces");
    campaign.setCheckpointOffset(2);
    when(cleanupJcrStorage.countFiles("/Users")).thenReturn(5L);
    when(cleanupJcrStorage.countFiles("/Groups/spaces")).thenReturn(4L);
    when(cleanupJcrStorage.countFiles("/Trash")).thenReturn(1L);

    scanService.startScan(CAMPAIGN_ID);

    verify(campaignLifecycle, timeout(5000)).transition(campaign, CleanupCampaignState.SIMULATED);
    // A campaign already DRY_RUN_RUNNING is resumed, not re-transitioned
    verify(campaignLifecycle, never()).transition(campaign, CleanupCampaignState.DRY_RUN_RUNNING);
    // The already-scanned root is never re-walked
    verify(cleanupJcrStorage, never()).scanRoot(eq("/Users"), anyLong(), anyInt(), any(), any());
    // The checkpointed root resumes from the persisted offset
    verify(cleanupJcrStorage).scanRoot(eq("/Groups/spaces"), eq(2L), eq(BATCH_SIZE), any(), any());
    verify(cleanupJcrStorage, never()).scanRoot(eq("/Groups/spaces"), eq(0L), anyInt(), any(), any());
    // The following root starts from scratch
    verify(cleanupJcrStorage).scanRoot(eq("/Trash"), eq(0L), eq(BATCH_SIZE), any(), any());
    // Processed counts include the skipped roots in the denominator
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(10L), eq(9L), anyLong(), eq("/Groups/spaces"), eq(4L));
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(10L), eq(10L), anyLong(), eq("/Trash"), eq(2L));
  }

  @Test
  void scanAbortsBetweenBatchesWhenCampaignLeavesDryRun() throws ObjectNotFoundException {
    CleanupCampaign canceled = new CleanupCampaign();
    canceled.setId(CAMPAIGN_ID);
    canceled.setState(CleanupCampaignState.CANCELLED);
    canceled.setParams(campaign.getParams());
    campaign.setState(CleanupCampaignState.DRY_RUN_RUNNING);
    // startScan read, worker entry read, then the abort check sees CANCELED
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign, campaign, canceled);
    when(cleanupJcrStorage.countFiles(anyString())).thenReturn(4L);

    scanService.startScan(CAMPAIGN_ID);

    verify(campaignStorage, timeout(5000).times(3)).getCampaign(CAMPAIGN_ID);
    verify(cleanupJcrStorage, timeout(1000).times(3)).countFiles(anyString());
    verify(cleanupJcrStorage, never()).scanRoot(anyString(), anyLong(), anyInt(), any(), any());
    verify(campaignStorage, never()).updateProgress(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyLong());
    verify(campaignLifecycle, never()).transition(any(), eq(CleanupCampaignState.SIMULATED));
  }

  @Test
  void scanFailureLeavesTheCampaignResumable() throws ObjectNotFoundException {
    campaign.setState(CleanupCampaignState.DRY_RUN_RUNNING);
    when(cleanupJcrStorage.countFiles("/Users")).thenReturn(4L);
    when(cleanupJcrStorage.countFiles("/Groups/spaces")).thenReturn(0L);
    when(cleanupJcrStorage.countFiles("/Trash")).thenReturn(0L);
    doAnswer(invocation -> {
      throw new IllegalStateException("JCR failure");
    }).when(cleanupJcrStorage).scanRoot(eq("/Users"), anyLong(), anyInt(), any(), any());

    scanService.startScan(CAMPAIGN_ID);

    verify(cleanupJcrStorage, timeout(5000)).scanRoot(eq("/Users"), eq(0L), anyInt(), any(), any());
    // The worker swallows the failure: no terminal transition, state untouched
    verify(campaignLifecycle, org.mockito.Mockito.after(200).never()).transition(any(), eq(CleanupCampaignState.SIMULATED));
    assertEquals(CleanupCampaignState.DRY_RUN_RUNNING, campaign.getState());
    verify(campaignStorage, never()).updateProgress(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyLong());
    assertTrue(campaign.getCheckpointOffset() == 0, "The persisted checkpoint stays untouched for the resume");
  }

  private CleanupCandidate candidate(String nodeUuid) {
    return new CleanupCandidate(nodeUuid, "/Users/j___/john/Private/file.pdf", 7L, 2048, 0, CleanupAction.DELETE, 100, 200);
  }

}
