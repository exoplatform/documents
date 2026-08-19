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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

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
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage.ScanBatchConsumer;
import org.exoplatform.document.cleanup.websocket.CleanupWebSocketService;
import org.exoplatform.document.cleanup.websocket.CleanupWsMessage;

/**
 * Scan worker tests pinning the count-first denominator, the per-batch
 * candidate persistence + path checkpoint/ETA updates + progress push, the
 * path-based resume from a persisted checkpoint (never a positional offset),
 * the abort check between batches, the checkpoint-resumable error handling and
 * the running-campaign guard the watchdog depends on. The JCR walking itself
 * is mocked through {@link CleanupJcrStorage}'s streaming callback.
 */
@ExtendWith(MockitoExtension.class)
class CleanupScanServiceTest {

  private static final long        CAMPAIGN_ID = 12L;

  private static final int         BATCH_SIZE  = 2;

  @Mock
  private CleanupCampaignStorage   campaignStorage;

  @Mock
  private CleanupCampaignLifecycle campaignLifecycle;

  @Mock
  private CleanupJcrStorage        cleanupJcrStorage;

  @Mock
  private CleanupSettingService    settingService;

  @Mock
  private CleanupWebSocketService  webSocketService;

  @InjectMocks
  private CleanupScanService       scanService;

  private CleanupCampaign          campaign;

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
      ScanBatchConsumer batchConsumer = invocation.getArgument(4);
      batchConsumer.onBatch(List.of(candidate("uuid-0", "/Users/j___/john/Private/a.pdf")),
                            "/Users/j___/john/Private/b.pdf",
                            2);
      batchConsumer.onBatch(List.of(candidate("uuid-2", "/Users/j___/john/Private/c.pdf")),
                            "/Users/j___/john/Private/c.pdf",
                            1);
      return null;
    }).when(cleanupJcrStorage).scanRoot(eq("/Users"), isNull(), anyInt(), any(), any());

    scanService.startScan(CAMPAIGN_ID);

    verify(campaignLifecycle, timeout(5000)).transition(campaign, CleanupCampaignState.SIMULATED);
    // DRAFT campaigns first transition into DRY_RUN_RUNNING
    verify(campaignLifecycle).transition(campaign, CleanupCampaignState.DRY_RUN_RUNNING);
    // The denominator is counted once per root, before any batch
    verify(cleanupJcrStorage).countFiles("/Users");
    verify(cleanupJcrStorage).countFiles("/Groups/spaces");
    verify(cleanupJcrStorage).countFiles("/Trash");
    // A fresh scan never carries a resume path
    verify(cleanupJcrStorage).scanRoot(eq("/Users"), isNull(), eq(BATCH_SIZE), any(), any());
    // Streamed candidates are persisted per batch
    verify(campaignStorage).saveCandidates(eq(CAMPAIGN_ID), argThat(candidates -> candidates.size() == 1
                                                                                  && "uuid-0".equals(candidates.get(0)
                                                                                                               .getNodeUuid())));
    verify(campaignStorage).saveCandidates(eq(CAMPAIGN_ID), argThat(candidates -> "uuid-2".equals(candidates.get(0)
                                                                                                            .getNodeUuid())));
    // Progress after each batch carries the last processed PATH as checkpoint
    // (the numeric scanned count is for progress display only)
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(3L), eq(2L), anyLong(), eq("/Users/j___/john/Private/b.pdf"),
                                           eq(2L));
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(3L), eq(3L), anyLong(), eq("/Users/j___/john/Private/c.pdf"),
                                           eq(3L));
    // Once a root completes, the checkpoint advances to the NEXT root so a
    // crash between roots never re-iterates the completed one
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(3L), eq(3L), anyLong(), eq("/Groups/spaces"), eq(0L));
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(3L), eq(3L), anyLong(), eq("/Trash"), eq(0L));
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
  void scanResumesAfterCheckpointPathNeverByOffset() throws ObjectNotFoundException {
    campaign.setState(CleanupCampaignState.DRY_RUN_RUNNING);
    campaign.setCheckpointPath("/Groups/spaces/marketing/Documents/b.pdf");
    campaign.setCheckpointOffset(2);
    when(cleanupJcrStorage.countFiles("/Users")).thenReturn(5L);
    when(cleanupJcrStorage.countFiles("/Groups/spaces")).thenReturn(4L);
    when(cleanupJcrStorage.countFiles("/Trash")).thenReturn(1L);
    doAnswer(invocation -> {
      ScanBatchConsumer batchConsumer = invocation.getArgument(4);
      batchConsumer.onBatch(List.of(), "/Groups/spaces/marketing/Documents/d.pdf", 2);
      return null;
    }).when(cleanupJcrStorage).scanRoot(eq("/Groups/spaces"), anyString(), anyInt(), any(), any());
    doAnswer(invocation -> {
      ScanBatchConsumer batchConsumer = invocation.getArgument(4);
      batchConsumer.onBatch(List.of(), "/Trash/x.pdf", 1);
      return null;
    }).when(cleanupJcrStorage).scanRoot(eq("/Trash"), isNull(), anyInt(), any(), any());

    scanService.startScan(CAMPAIGN_ID);

    verify(campaignLifecycle, timeout(5000)).transition(campaign, CleanupCampaignState.SIMULATED);
    // A campaign already DRY_RUN_RUNNING is resumed, not re-transitioned
    verify(campaignLifecycle, never()).transition(campaign, CleanupCampaignState.DRY_RUN_RUNNING);
    // The checkpoint path identifies the in-progress root: completed roots are
    // never re-walked
    verify(cleanupJcrStorage, never()).scanRoot(eq("/Users"), any(), anyInt(), any(), any());
    // The in-progress root resumes strictly after the checkpoint PATH
    verify(cleanupJcrStorage).scanRoot(eq("/Groups/spaces"),
                                       eq("/Groups/spaces/marketing/Documents/b.pdf"),
                                       eq(BATCH_SIZE),
                                       any(),
                                       any());
    // The following root starts from scratch
    verify(cleanupJcrStorage).scanRoot(eq("/Trash"), isNull(), eq(BATCH_SIZE), any(), any());
    // Progress counts include the skipped roots and the pre-resume scanned
    // count in the numerator; the checkpoint carries the new last path
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(10L), eq(9L), anyLong(),
                                           eq("/Groups/spaces/marketing/Documents/d.pdf"), eq(4L));
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(10L), eq(9L), anyLong(), eq("/Trash"), eq(0L));
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(10L), eq(10L), anyLong(), eq("/Trash/x.pdf"), eq(1L));
  }

  @Test
  void scanResumesRootFreshWhenCheckpointIsBareRootMarker() throws ObjectNotFoundException {
    // Bare-root checkpoint: root recorded but not started yet — also the
    // legacy (offset-based) checkpoint shape, resumed as a fresh root scan
    campaign.setState(CleanupCampaignState.DRY_RUN_RUNNING);
    campaign.setCheckpointPath("/Groups/spaces");
    campaign.setCheckpointOffset(2);
    when(cleanupJcrStorage.countFiles("/Users")).thenReturn(5L);
    when(cleanupJcrStorage.countFiles("/Groups/spaces")).thenReturn(4L);
    when(cleanupJcrStorage.countFiles("/Trash")).thenReturn(0L);
    doAnswer(invocation -> {
      ScanBatchConsumer batchConsumer = invocation.getArgument(4);
      batchConsumer.onBatch(List.of(), "/Groups/spaces/marketing/Documents/b.pdf", 2);
      return null;
    }).when(cleanupJcrStorage).scanRoot(eq("/Groups/spaces"), isNull(), anyInt(), any(), any());

    scanService.startScan(CAMPAIGN_ID);

    verify(campaignLifecycle, timeout(5000)).transition(campaign, CleanupCampaignState.SIMULATED);
    verify(cleanupJcrStorage, never()).scanRoot(eq("/Users"), any(), anyInt(), any(), any());
    // No resume path, and the stale legacy offset is NEVER used to position:
    // the scanned-in-root count restarts from zero
    verify(cleanupJcrStorage).scanRoot(eq("/Groups/spaces"), isNull(), eq(BATCH_SIZE), any(), any());
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(9L), eq(7L), anyLong(),
                                           eq("/Groups/spaces/marketing/Documents/b.pdf"), eq(2L));
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
    doAnswer(invocation -> {
      ScanBatchConsumer batchConsumer = invocation.getArgument(4);
      // The worker's abort check makes the consumer stop the root scan
      assertFalse(batchConsumer.onBatch(List.of(candidate("uuid-0", "/Users/j___/john/Private/a.pdf")),
                                        "/Users/j___/john/Private/a.pdf",
                                        2),
                  "The batch consumer must return false once the campaign left DRY_RUN_RUNNING");
      return null;
    }).when(cleanupJcrStorage).scanRoot(eq("/Users"), isNull(), anyInt(), any(), any());

    scanService.startScan(CAMPAIGN_ID);

    verify(cleanupJcrStorage, timeout(5000)).scanRoot(eq("/Users"), isNull(), anyInt(), any(), any());
    // The aborted batch is neither persisted nor checkpointed, and the
    // remaining roots are never scanned
    verify(campaignStorage, after(200).never()).saveCandidates(anyLong(), anyList());
    verify(campaignStorage, never()).updateProgress(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyLong());
    verify(cleanupJcrStorage, never()).scanRoot(eq("/Groups/spaces"), any(), anyInt(), any(), any());
    verify(cleanupJcrStorage, never()).scanRoot(eq("/Trash"), any(), anyInt(), any(), any());
    verify(campaignLifecycle, never()).transition(any(), eq(CleanupCampaignState.SIMULATED));
  }

  @Test
  void scanFailureLeavesTheCampaignResumableAndTheWorkerRestartable() throws ObjectNotFoundException {
    campaign.setState(CleanupCampaignState.DRY_RUN_RUNNING);
    when(cleanupJcrStorage.countFiles("/Users")).thenReturn(4L);
    when(cleanupJcrStorage.countFiles("/Groups/spaces")).thenReturn(0L);
    when(cleanupJcrStorage.countFiles("/Trash")).thenReturn(0L);
    doAnswer(invocation -> {
      throw new IllegalStateException("JCR failure");
    }).when(cleanupJcrStorage).scanRoot(eq("/Users"), isNull(), anyInt(), any(), any());

    scanService.startScan(CAMPAIGN_ID);

    verify(cleanupJcrStorage, timeout(5000)).scanRoot(eq("/Users"), isNull(), anyInt(), any(), any());
    // The worker swallows the failure: no terminal transition, state untouched
    verify(campaignLifecycle, after(200).never()).transition(any(), eq(CleanupCampaignState.SIMULATED));
    assertEquals(CleanupCampaignState.DRY_RUN_RUNNING, campaign.getState());
    verify(campaignStorage, never()).updateProgress(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyLong());
    assertTrue(campaign.getCheckpointOffset() == 0, "The persisted checkpoint stays untouched for the resume");
    // The running-campaign id was removed in the finally block even on fatal
    // error — the property the watchdog resume depends on: a new startScan
    // relaunches the worker instead of no-oping
    scanService.startScan(CAMPAIGN_ID);
    verify(cleanupJcrStorage, timeout(5000).times(2)).scanRoot(eq("/Users"), isNull(), anyInt(), any(), any());
  }

  @Test
  void scanIsNoOpWhileTheWorkerIsAlive() throws ReflectiveOperationException, ObjectNotFoundException {
    campaign.setState(CleanupCampaignState.DRY_RUN_RUNNING);
    runningCampaigns().add(CAMPAIGN_ID);

    scanService.startScan(CAMPAIGN_ID);

    // The double-start guard makes the watchdog-triggered worker a no-op while
    // the campaign id is in the running set: the scan never even counts
    verify(cleanupJcrStorage, after(500).never()).countFiles(anyString());
    verify(campaignStorage, never()).updateProgress(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyLong());
  }

  @SuppressWarnings("unchecked")
  private Set<Long> runningCampaigns() throws ReflectiveOperationException {
    Field runningCampaignsField = CleanupScanService.class.getDeclaredField("runningCampaigns");
    runningCampaignsField.setAccessible(true); // NOSONAR test wiring
    return (Set<Long>) runningCampaignsField.get(scanService);
  }

  private CleanupCandidate candidate(String nodeUuid, String path) {
    return new CleanupCandidate(nodeUuid, path, 7L, 2048, 0, CleanupAction.DELETE, 100, 200);
  }

}
