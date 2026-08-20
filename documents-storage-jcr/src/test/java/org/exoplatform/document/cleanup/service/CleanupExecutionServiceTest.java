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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupCampaignSummary;
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupPurgeResult;
import org.exoplatform.document.cleanup.model.CleanupRevalidation;
import org.exoplatform.document.cleanup.storage.CleanupCampaignStorage;
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage;
import org.exoplatform.document.cleanup.websocket.CleanupWebSocketService;
import org.exoplatform.document.cleanup.websocket.CleanupWsMessage;

import io.meeds.common.ContainerTransactional;
import io.meeds.social.util.JsonUtils;

/**
 * Execution worker tests. Like the scan ones, they drive the worker body
 * SYNCHRONOUSLY through {@link CleanupExecutionService#executeCampaign(long)},
 * the service's real executor being replaced by a mock: going through the
 * scheduled {@code executeCampaignTransactional} would run the
 * {@code @ContainerTransactional} aspect and boot a real PortalContainer in a
 * plain JUnit run. The transactional wrapper is covered by the two contract tests
 * at the end instead — that it is what gets scheduled, and that it still carries
 * the annotation.
 */
@ExtendWith(MockitoExtension.class)
class CleanupExecutionServiceTest {

  private static final String      NODE_UUID_VERSIONS      = "uuid-versions";

  private static final String      NODE_UUID_DELETED       = "uuid-deleted";

  private static final String      NODE_UUID_SKIPPED       = "uuid-skipped";

  private static final String      NODE_UUID_SPARED        = "uuid-spared";

  private static final String      NODE_UUID_EXEMPTED      = "uuid-exempted";

  private static final String      NODE_UUID_GONE          = "uuid-gone";

  private static final long        CAMPAIGN_ID             = 1L;

  private static final String      JCR_ERROR_MESSAGE       = "JCR repository unreachable";

  private static final long        CANDIDATE_FILE_SIZE     = 2097152L;

  private static final long        CANDIDATE_VERSIONS_SIZE = 512L;

  @Mock
  private CleanupCampaignStorage   campaignStorage;

  @Mock
  private CleanupJcrStorage        cleanupJcrStorage;

  @Mock
  private CleanupSettingService    settingService;

  @Mock
  private CleanupWebSocketService  webSocketService;

  @Spy
  @InjectMocks
  private CleanupCampaignLifecycle campaignLifecycle;

  @InjectMocks
  private CleanupExecutionService  executionService;

  @Mock
  private ExecutorService          workerExecutor;

  @BeforeEach
  void injectLifecycle() throws ReflectiveOperationException {
    // Mockito doesn't inject a @Spy @InjectMocks field into another
    // @InjectMocks: wire the real lifecycle (fed with the mocks) manually
    Field lifecycleField = CleanupExecutionService.class.getDeclaredField("campaignLifecycle");
    lifecycleField.setAccessible(true); // NOSONAR test wiring
    lifecycleField.set(executionService, campaignLifecycle); // NOSONAR
    // Replace the service's REAL single-thread executor with a mock: a live
    // background worker would race the test with unstubbed collaborators
    // (e.g. getBatchSize() = 0 -> PageRequest.of(0, 0) errors in every CI
    // run); worker-body tests invoke executeCampaign() directly instead
    Field executorField = CleanupExecutionService.class.getDeclaredField("executorService");
    executorField.setAccessible(true); // NOSONAR test wiring
    executorField.set(executionService, workerExecutor); // NOSONAR
    // The worker commits each batch through RequestLifeCycle.restartTransaction(),
    // which resolves the CURRENT CONTAINER: in a plain JUnit run there is none,
    // and asking for it would boot a RootContainer. The production path keeps the
    // single static call; the test neutralizes it on a spy — lenient, since the
    // tests that never reach a batch boundary never call it
    executionService = spy(executionService);
    lenient().doNothing().when(executionService).restartTransaction();
  }

  @Test
  void shouldStartExecutionFromLockedOnly() throws ObjectNotFoundException {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.LOCKED));
    when(campaignStorage.countItemsByState(CAMPAIGN_ID, CleanupItemState.CANDIDATE)).thenReturn(5L);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupCampaign executing = executionService.startExecution(CAMPAIGN_ID);

    assertEquals(CleanupCampaignState.EXECUTING, executing.getState());
    assertEquals(5L, executing.getTotalCount());
    assertEquals(0L, executing.getProcessedCount());
    // The purge worker is scheduled (and only scheduled: no live thread leaks
    // out of the test)
    verify(workerExecutor).execute(any());
  }

  @Test
  void shouldScheduleTheWorkerOnResumeExecution() {
    executionService.resumeExecution(CAMPAIGN_ID);

    verify(workerExecutor).execute(any());
  }

  @Test
  void shouldRejectExecutionWhenCampaignNotLocked() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.PUBLISHED));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> executionService.startExecution(CAMPAIGN_ID));

    assertEquals("cleanup.invalidState", exception.getMessage());
  }

  @Test
  void shouldThrowNotFoundWhenExecutingUnknownCampaign() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> executionService.startExecution(CAMPAIGN_ID));
  }

  @Test
  void shouldRevalidateEveryItemAtDeleteTimeAndContinueOnFailure() {
    CleanupCampaign campaign = campaign(CleanupCampaignState.EXECUTING);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(200);

    CleanupCampaignItem goneItem = item(11L, NODE_UUID_GONE, CleanupAction.DELETE);
    CleanupCampaignItem exemptedItem = item(12L, NODE_UUID_EXEMPTED, CleanupAction.DELETE);
    CleanupCampaignItem sparedItem = item(13L, NODE_UUID_SPARED, CleanupAction.DELETE);
    CleanupCampaignItem skippedItem = item(14L, NODE_UUID_SKIPPED, CleanupAction.DELETE);
    CleanupCampaignItem deletedItem = item(15L, NODE_UUID_DELETED, CleanupAction.DELETE);
    CleanupCampaignItem purgedVersionsItem = item(16L, NODE_UUID_VERSIONS, CleanupAction.PURGE_VERSIONS);

    List<CleanupCampaignItem> batch = List.of(goneItem,
                                                  exemptedItem,
                                                  sparedItem,
                                                  skippedItem,
                                                  deletedItem,
                                                  purgedVersionsItem);
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenReturn(batch)
                                                .thenReturn(List.of());

    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_GONE), any())).thenReturn(CleanupRevalidation.gone());
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_EXEMPTED), any())).thenReturn(CleanupRevalidation.exempted());
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_SPARED), any())).thenReturn(CleanupRevalidation.of(null));
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_SKIPPED), any()))
                                                                    .thenReturn(CleanupRevalidation.of(candidate(NODE_UUID_SKIPPED,
                                                                                                                 CleanupAction.DELETE)));
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_DELETED), any()))
                                                                    .thenReturn(CleanupRevalidation.of(candidate(NODE_UUID_DELETED,
                                                                                                                 CleanupAction.DELETE)));
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_VERSIONS), any()))
                                                                     .thenReturn(CleanupRevalidation.of(candidate(NODE_UUID_VERSIONS,
                                                                                                                  CleanupAction.PURGE_VERSIONS)));
    when(cleanupJcrStorage.deleteNode(NODE_UUID_SKIPPED)).thenReturn(CleanupPurgeResult.skipped("cleanup.referentialIntegrity", "javax.jcr.ReferentialIntegrityException: referenced"));
    when(cleanupJcrStorage.deleteNode(NODE_UUID_DELETED)).thenReturn(CleanupPurgeResult.purged(100L));
    when(cleanupJcrStorage.purgeVersions(eq(NODE_UUID_VERSIONS), any())).thenReturn(CleanupPurgeResult.purged(50L));

    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(campaignStorage.countItemsByState(anyLong(), any())).thenReturn(0L);
    lenient().when(campaignStorage.sumReclaimedBytes(anyLong())).thenReturn(150L);

    executionService.executeCampaign(CAMPAIGN_ID);

    ArgumentCaptor<CleanupCampaignItem> itemCaptor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage, org.mockito.Mockito.times(6)).saveItem(itemCaptor.capture());
    Map<Long, CleanupCampaignItem> savedItems = itemCaptor.getAllValues()
                                                          .stream()
                                                          .collect(java.util.stream.Collectors.toMap(CleanupCampaignItem::getId,
                                                                                                     Function.identity()));
    assertEquals(CleanupItemState.GONE, savedItems.get(11L).getState());
    assertEquals(CleanupItemState.EXEMPTED, savedItems.get(12L).getState());
    assertEquals(CleanupItemState.SPARED_BY_MODIFICATION, savedItems.get(13L).getState());
    assertEquals(CleanupItemState.SKIPPED, savedItems.get(14L).getState());
    assertNotNull(savedItems.get(14L).getFailureReason());
    assertEquals(CleanupItemState.PURGED, savedItems.get(15L).getState());
    assertEquals(100L, savedItems.get(15L).getReclaimedBytes());
    assertTrue(savedItems.get(15L).getPurgedAt() > 0);
    // Still-candidate revalidation refreshes sizes and computedAt too (shared
    // CleanupRevalidationUtil mapping, identical to the freshness refresh)
    assertEquals(CANDIDATE_FILE_SIZE, savedItems.get(15L).getFileSize());
    assertEquals(CANDIDATE_VERSIONS_SIZE, savedItems.get(15L).getVersionsSize());
    assertTrue(savedItems.get(15L).getComputedAt() > 0);
    assertEquals(CleanupItemState.PURGED, savedItems.get(16L).getState());
    assertEquals(50L, savedItems.get(16L).getReclaimedBytes());
    assertEquals(CANDIDATE_FILE_SIZE, savedItems.get(16L).getFileSize());
    assertEquals(CANDIDATE_VERSIONS_SIZE, savedItems.get(16L).getVersionsSize());
    assertTrue(savedItems.get(16L).getComputedAt() > 0);

    ArgumentCaptor<CleanupCampaign> campaignCaptor = ArgumentCaptor.forClass(CleanupCampaign.class);
    verify(campaignStorage).saveCampaign(campaignCaptor.capture());
    assertEquals(CleanupCampaignState.COMPLETED, campaignCaptor.getValue().getState());
    assertTrue(campaignCaptor.getValue().getCompletedDate() > 0);
    assertNotNull(campaignCaptor.getValue().getSummaryJson());
    assertTrue(campaignCaptor.getValue().getSummaryJson().contains("reclaimedBytes"));
  }

  @Test
  void shouldCarryTheIncompleteScanVerdictForwardIntoTheCompletionSnapshot() {
    CleanupCampaign campaign = campaign(CleanupCampaignState.EXECUTING);
    // The verdict the DRY RUN left on this very column at SIMULATED: two subtrees
    // it could never walk, so the report was never a complete picture
    CleanupCampaignSummary scanSummary = new CleanupCampaignSummary();
    scanSummary.setScanIncomplete(true);
    scanSummary.setFailedScanUnitCount(2);
    campaign.setSummaryJson(JsonUtils.toJsonString(scanSummary));
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(200);
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenReturn(List.of());
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    executionService.executeCampaign(CAMPAIGN_ID);

    ArgumentCaptor<CleanupCampaign> campaignCaptor = ArgumentCaptor.forClass(CleanupCampaign.class);
    verify(campaignStorage).saveCampaign(campaignCaptor.capture());
    CleanupCampaignSummary saved = JsonUtils.fromJsonString(campaignCaptor.getValue().getSummaryJson(),
                                                            CleanupCampaignSummary.class);
    // Completion OVERWRITES this one column with the purge aggregates: rebuilding
    // it from scratch is what would quietly erase the fact that the report the
    // purge just ran from did not cover the whole tree
    assertTrue(saved.isScanIncomplete(), "The dry run's incomplete verdict must survive the completion snapshot");
    assertEquals(2, saved.getFailedScanUnitCount());
  }

  @Test
  void shouldSkipItemWhenRevalidationOutcomeIsUnknown() {
    CleanupCampaign campaign = campaign(CleanupCampaignState.EXECUTING);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(200);
    CleanupCampaignItem unknownItem = item(17L, "uuid-unknown", CleanupAction.DELETE);
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenReturn(List.of(unknownItem))
                                                .thenReturn(List.of());
    when(cleanupJcrStorage.revalidate(eq("uuid-unknown"), any())).thenReturn(CleanupRevalidation.unknown());
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    executionService.executeCampaign(CAMPAIGN_ID);

    // A transient JCR read failure at revalidation time must NEVER let the
    // item be deleted, nor mark it permanently spared: SKIPPED with a
    // distinct reason
    assertEquals(CleanupItemState.SKIPPED, unknownItem.getState());
    assertEquals("cleanup.revalidationFailed", unknownItem.getFailureReason());
    verify(cleanupJcrStorage, org.mockito.Mockito.never()).deleteNode(any());
    verify(cleanupJcrStorage, org.mockito.Mockito.never()).purgeVersions(any(), any());
  }

  @Test
  void shouldRecordTheBytesReclaimedByAPartiallyPurgedSkippedItem() {
    // A versions purge that fails PARTWAY returns SKIPPED while carrying the
    // bytes its successful removals already freed. Recording those bytes only for
    // PURGED items would silently drop them from the campaign's reclaimed total
    // (summed over the item rows), under-reporting the work really done
    CleanupCampaign campaign = campaign(CleanupCampaignState.EXECUTING);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(200);
    CleanupCampaignItem partialItem = item(18L, NODE_UUID_VERSIONS, CleanupAction.PURGE_VERSIONS);
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenReturn(List.of(partialItem))
                                                .thenReturn(List.of());
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_VERSIONS), any()))
                                                                     .thenReturn(CleanupRevalidation.of(candidate(NODE_UUID_VERSIONS,
                                                                                                                  CleanupAction.PURGE_VERSIONS)));
    when(cleanupJcrStorage.purgeVersions(eq(NODE_UUID_VERSIONS), any()))
                                                                .thenReturn(CleanupPurgeResult.skipped("cleanup.purgeVersionsError",
                                                                                                       "javax.jcr.RepositoryException: version in use",
                                                                                                       300L));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    executionService.executeCampaign(CAMPAIGN_ID);

    ArgumentCaptor<CleanupCampaignItem> itemCaptor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(itemCaptor.capture());
    CleanupCampaignItem saved = itemCaptor.getValue();
    assertEquals(CleanupItemState.SKIPPED, saved.getState(), "The item must stay SKIPPED: it still needs attention");
    assertEquals("cleanup.purgeVersionsError", saved.getFailureReason(), "The reason must stay a BARE message code");
    assertEquals("javax.jcr.RepositoryException: version in use",
                 saved.getFailureDetail(),
                 "The exception text belongs to the detail, never to the reason");
    assertEquals(300L, saved.getReclaimedBytes(), "The bytes reclaimed before the failure must be persisted");
    assertEquals(0L, saved.getPurgedAt(), "A partial purge is not a purge: no purge date");
  }

  @Test
  void shouldHandTheWholeCampaignParamsSnapshotToThePurge() {
    // The purge policy unions an AGE rule and a COUNT rule, so the period is as
    // load-bearing as the version cap: handing the purge the cap alone would
    // make the execution remove a DIFFERENT set from the one the revalidation
    // just counted, right under the administrator's eyes
    CleanupCampaign campaign = campaign(CleanupCampaignState.EXECUTING);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(200);
    CleanupCampaignItem versionsItem = item(19L, NODE_UUID_VERSIONS, CleanupAction.PURGE_VERSIONS);
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenReturn(List.of(versionsItem))
                                                .thenReturn(List.of());
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_VERSIONS), any()))
                                                                     .thenReturn(CleanupRevalidation.of(candidate(NODE_UUID_VERSIONS,
                                                                                                                  CleanupAction.PURGE_VERSIONS)));
    when(cleanupJcrStorage.purgeVersions(eq(NODE_UUID_VERSIONS), any())).thenReturn(CleanupPurgeResult.purged(50L));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    executionService.executeCampaign(CAMPAIGN_ID);

    verify(cleanupJcrStorage).revalidate(NODE_UUID_VERSIONS, campaign.getParams());
    verify(cleanupJcrStorage).purgeVersions(NODE_UUID_VERSIONS, campaign.getParams());
  }

  @Test
  void shouldSplitTheUnexpectedErrorCodeFromItsExceptionText() {
    // THE localization contract: the reason is a BARE message code the console
    // looks up in its bundle, so concatenating the exception message into it used
    // to leave the UI displaying a raw untranslated key
    CleanupCampaign campaign = campaign(CleanupCampaignState.EXECUTING);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(200);
    CleanupCampaignItem explodingItem = item(19L, NODE_UUID_SKIPPED, CleanupAction.DELETE);
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenReturn(List.of(explodingItem))
                                                .thenReturn(List.of());
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_SKIPPED), any())).thenThrow(new IllegalStateException(JCR_ERROR_MESSAGE));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    executionService.executeCampaign(CAMPAIGN_ID);

    assertEquals(CleanupItemState.SKIPPED, explodingItem.getState());
    assertEquals("cleanup.unexpectedError", explodingItem.getFailureReason(), "The reason must stay a BARE message code");
    assertTrue(explodingItem.getFailureDetail().startsWith("java.lang.IllegalStateException: " + JCR_ERROR_MESSAGE),
               "The exception text belongs to the detail: " + explodingItem.getFailureDetail());
  }

  @Test
  void shouldFallBackToADetaillessSaveWhenTheFirstSaveFails() {
    // An oversized FAILURE_DETAIL is by far the likeliest reason a save of a
    // failed item blows up, so the fallback drops exactly that and keeps the
    // state and the bare code — which is what the console and the retry need
    CleanupCampaign campaign = campaign(CleanupCampaignState.EXECUTING);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(200);
    CleanupCampaignItem failedItem = item(20L, NODE_UUID_SKIPPED, CleanupAction.DELETE);
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenReturn(List.of(failedItem))
                                                .thenReturn(List.of());
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_SKIPPED), any())).thenThrow(new IllegalStateException(JCR_ERROR_MESSAGE));
    when(campaignStorage.saveItem(any())).thenThrow(new IllegalStateException("value too long for column FAILURE_DETAIL"))
                                         .thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    executionService.executeCampaign(CAMPAIGN_ID);

    ArgumentCaptor<CleanupCampaignItem> itemCaptor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage, org.mockito.Mockito.times(2)).saveItem(itemCaptor.capture());
    CleanupCampaignItem fallbackSaved = itemCaptor.getAllValues().get(1);
    assertEquals(CleanupItemState.SKIPPED, fallbackSaved.getState(), "The fallback save must still carry the state");
    assertEquals("cleanup.unexpectedError", fallbackSaved.getFailureReason(), "...and the bare failure code");
    assertNull(fallbackSaved.getFailureDetail(), "...but NOT the detail: it is the likeliest cause of the failure");
    // The run still completes: one unsavable item never aborts the campaign
    ArgumentCaptor<CleanupCampaign> campaignCaptor = ArgumentCaptor.forClass(CleanupCampaign.class);
    verify(campaignStorage).saveCampaign(campaignCaptor.capture());
    assertEquals(CleanupCampaignState.COMPLETED, campaignCaptor.getValue().getState());
  }

  @Test
  void shouldMoveOnWhenEvenTheDetaillessFallbackSaveFails() {
    CleanupCampaign campaign = campaign(CleanupCampaignState.EXECUTING);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(200);
    CleanupCampaignItem unsavableItem = item(21L, NODE_UUID_DELETED, CleanupAction.DELETE);
    CleanupCampaignItem nextItem = purgeableItem(22L, "uuid-next");
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenReturn(List.of(unsavableItem, nextItem))
                                                .thenReturn(List.of());
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_DELETED), any()))
                                                                    .thenReturn(CleanupRevalidation.of(candidate(NODE_UUID_DELETED,
                                                                                                                 CleanupAction.DELETE)));
    when(cleanupJcrStorage.deleteNode(NODE_UUID_DELETED)).thenReturn(CleanupPurgeResult.purged(100L));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> {
      CleanupCampaignItem item = invocation.getArgument(0);
      if (item.getId() == 21L) {
        throw new IllegalStateException("Database gone");
      }
      return item;
    });
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    executionService.executeCampaign(CAMPAIGN_ID);

    // NEVER rethrown into the batch loop: the items AFTER the unsavable one are
    // still processed, and the campaign still completes
    assertEquals(CleanupItemState.PURGED, nextItem.getState(), "The item after the unsavable one must still be processed");
    ArgumentCaptor<CleanupCampaign> campaignCaptor = ArgumentCaptor.forClass(CleanupCampaign.class);
    verify(campaignStorage).saveCampaign(campaignCaptor.capture());
    assertEquals(CleanupCampaignState.COMPLETED, campaignCaptor.getValue().getState());
  }

  /**
   * Bound on the keyset queries the poison-pill test answers before it starts
   * handing back empty pages. Two are enough for a correct worker (one full
   * page, then the empty one ending the run); the slack lets a broken cursor be
   * CAUGHT by an assertion rather than spin until the machine gives up.
   */
  private static final int QUERY_COUNT_BOUND = 4;

  @Test
  @Timeout(30)
  void shouldPageByKeysetSoAnUnsavableItemCannotLoopForever() {
    // The poison pill: the loop used to re-read page 0 of the CANDIDATE items and
    // relied on every processed item LEAVING that state. An item whose save can
    // never succeed stays CANDIDATE, so page 0 kept returning it — forever, and
    // the stalled-worker watchdog kept relaunching the same run. Keyset paging
    // makes the forward progress structural: the worker asks for ids PAST the last
    // one it saw, so it cannot revisit an item within a run whatever happens to it
    CleanupCampaign campaign = campaign(CleanupCampaignState.EXECUTING);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(2);
    CleanupCampaignItem unsavableItem = item(30L, NODE_UUID_DELETED, CleanupAction.DELETE);
    CleanupCampaignItem savableItem = purgeableItem(31L, "uuid-31");
    // The storage is stubbed as a REAL keyset store: it answers the ids strictly
    // above the requested one, and the unsavable item never leaves CANDIDATE.
    // The query count is BOUNDED and the bound answers an empty page: a worker
    // whose cursor does not advance must fail this test in milliseconds, on the
    // assertion below, instead of spinning. It used to prove the fix by LOOPING —
    // and @Timeout does not interrupt a spinning loop, it only reports once the
    // loop returns, so the run filled the disk with the failure logging first
    AtomicInteger queryCount = new AtomicInteger();
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenAnswer(invocation -> queryCount.incrementAndGet() > QUERY_COUNT_BOUND ?
                                                                                                       List.of() :
                                                                                                       Stream.of(unsavableItem, savableItem)
                                                                                                             .filter(item -> item.getState() == CleanupItemState.CANDIDATE)
                                                                                                             .filter(item -> item.getId() > (long) invocation.getArgument(2))
                                                                                                             .toList());
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_DELETED), any()))
                                                                    .thenReturn(CleanupRevalidation.of(candidate(NODE_UUID_DELETED,
                                                                                                                 CleanupAction.DELETE)));
    when(cleanupJcrStorage.deleteNode(NODE_UUID_DELETED)).thenReturn(CleanupPurgeResult.purged(100L));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> {
      CleanupCampaignItem item = invocation.getArgument(0);
      if (item.getId() == 30L) {
        // Never persisted: the item stays CANDIDATE in the store
        item.setState(CleanupItemState.CANDIDATE);
        throw new IllegalStateException("Database gone");
      }
      return item;
    });
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    executionService.executeCampaign(CAMPAIGN_ID);

    // The run TERMINATES, and each item was met exactly once
    assertEquals(2,
                 queryCount.get(),
                 "One full page then the empty page ending the run: more means the cursor stalled");
    verify(cleanupJcrStorage, org.mockito.Mockito.times(1)).deleteNode(NODE_UUID_DELETED);
    verify(cleanupJcrStorage, org.mockito.Mockito.times(1)).deleteNode("uuid-31");
    ArgumentCaptor<CleanupCampaign> campaignCaptor = ArgumentCaptor.forClass(CleanupCampaign.class);
    verify(campaignStorage).saveCampaign(campaignCaptor.capture());
    assertEquals(CleanupCampaignState.COMPLETED, campaignCaptor.getValue().getState());
  }

  @Test
  void shouldRestartTheTransactionOnceAfterEachBatch() {
    // Per-batch commit boundary: without it, a database failure inside one batch
    // rolls back every batch before it — a whole purge run's worth of recorded
    // outcomes lost for one late failure
    CleanupCampaign campaign = campaign(CleanupCampaignState.EXECUTING);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(1);
    CleanupCampaignItem firstItem = purgeableItem(41L, "uuid-41");
    CleanupCampaignItem secondItem = purgeableItem(42L, "uuid-42");
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenReturn(List.of(firstItem))
                                                .thenReturn(List.of(secondItem))
                                                .thenReturn(List.of());
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    executionService.executeCampaign(CAMPAIGN_ID);

    // Once per BATCH, after its progress update — not once per item, not once
    // per run
    verify(executionService, org.mockito.Mockito.times(2)).restartTransaction();
    InOrder inOrder = inOrder(campaignStorage, executionService);
    inOrder.verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), anyLong(), eq(1L), anyLong(), isNull(), eq(0L));
    inOrder.verify(executionService).restartTransaction();
    inOrder.verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), anyLong(), eq(2L), anyLong(), isNull(), eq(0L));
    inOrder.verify(executionService).restartTransaction();
  }

  @Test
  void shouldAbortExecutionWhenCampaignNoLongerExecuting() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.CANCELLED));

    executionService.executeCampaign(CAMPAIGN_ID);

    verify(campaignStorage, org.mockito.Mockito.never()).saveItem(any());
  }

  @Test
  void shouldNoOpWhileTheWorkerIsAlive() throws ReflectiveOperationException {
    runningCampaigns().add(CAMPAIGN_ID);

    executionService.executeCampaign(CAMPAIGN_ID);

    // Double-start guard: a watchdog- or recovery-triggered worker is a no-op
    // while the campaign id is in the running set
    verify(campaignStorage, org.mockito.Mockito.never()).getCampaign(CAMPAIGN_ID);
    verify(campaignStorage, org.mockito.Mockito.never()).saveItem(any());
  }

  @Test
  void shouldRemoveRunningIdAndStayResumableOnFatalError() throws ReflectiveOperationException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.EXECUTING);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(200);
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenThrow(new IllegalStateException("Database gone"));

    executionService.executeCampaign(CAMPAIGN_ID);

    // No terminal transition: the campaign stays EXECUTING, resumable
    assertEquals(CleanupCampaignState.EXECUTING, campaign.getState());
    verify(campaignStorage, org.mockito.Mockito.never()).saveCampaign(any());
    // The running id was removed in the finally block even on fatal error —
    // the property the watchdog resume depends on to relaunch the worker
    assertTrue(runningCampaigns().isEmpty(), "The running-campaign id must be removed even on fatal error");
  }

  @Test
  void shouldAbortBetweenBatchesWhenCampaignLeavesExecutingMidRun() {
    CleanupCampaign executingCampaign = campaign(CleanupCampaignState.EXECUTING);
    // Worker entry and the check before batch 1 see EXECUTING; the check
    // BETWEEN
    // batch 1 and batch 2 sees the cancellation
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(executingCampaign,
                                                              executingCampaign,
                                                              campaign(CleanupCampaignState.CANCELLED));
    when(settingService.getBatchSize()).thenReturn(1);
    CleanupCampaignItem firstItem = item(11L, NODE_UUID_DELETED, CleanupAction.DELETE);
    CleanupCampaignItem secondItem = item(12L, NODE_UUID_SKIPPED, CleanupAction.DELETE);
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenReturn(List.of(firstItem))
                                                .thenReturn(List.of(secondItem));
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_DELETED), any()))
                                                                    .thenReturn(CleanupRevalidation.of(candidate(NODE_UUID_DELETED,
                                                                                                                 CleanupAction.DELETE)));
    when(cleanupJcrStorage.deleteNode(NODE_UUID_DELETED)).thenReturn(CleanupPurgeResult.purged(100L));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    executionService.executeCampaign(CAMPAIGN_ID);

    // Batch 1 is fully processed and its progress persisted...
    assertEquals(CleanupItemState.PURGED, firstItem.getState());
    verify(campaignStorage, org.mockito.Mockito.times(1)).saveItem(any());
    verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), anyLong(), eq(1L), anyLong(), isNull(), eq(0L));
    // ...batch 2 is abandoned: a cancelled campaign never keeps deleting files
    assertEquals(CleanupItemState.CANDIDATE, secondItem.getState());
    verify(cleanupJcrStorage, org.mockito.Mockito.never()).deleteNode(NODE_UUID_SKIPPED);
    // And no terminal transition is forced on the campaign
    verify(campaignStorage, org.mockito.Mockito.never()).saveCampaign(any());
  }

  @Test
  void shouldProcessEveryBatchOfAMultiPageRunPersistingProgressPerBatch() {
    CleanupCampaign campaign = campaign(CleanupCampaignState.EXECUTING);
    campaign.setTotalCount(4);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(2);
    CleanupCampaignItem firstItem = purgeableItem(11L, "uuid-1");
    CleanupCampaignItem secondItem = purgeableItem(12L, "uuid-2");
    CleanupCampaignItem thirdItem = purgeableItem(13L, "uuid-3");
    CleanupCampaignItem fourthItem = purgeableItem(14L, "uuid-4");
    // Two full pages of candidates, then the empty page ending the run
    when(campaignStorage.getItemsByStateAfterId(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), anyLong(), anyInt()))
                                                .thenReturn(List.of(firstItem, secondItem))
                                                .thenReturn(List.of(thirdItem, fourthItem))
                                                .thenReturn(List.of());
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    executionService.executeCampaign(CAMPAIGN_ID);

    // EVERY item of EVERY page is processed (not only the first page)
    verify(campaignStorage, org.mockito.Mockito.times(4)).saveItem(any());
    // The keyset cursor is asserted EXACTLY, not with anyLong(): a cursor left at
    // its initial value re-reads the same window, which is precisely the poison
    // pill keyset paging is here to defuse. The stubbed pages above would keep
    // this test green either way — only pinning the argument catches it
    ArgumentCaptor<Long> lastIdCaptor = ArgumentCaptor.forClass(Long.class);
    verify(campaignStorage, org.mockito.Mockito.times(3)).getItemsByStateAfterId(eq(CAMPAIGN_ID),
                                                                                eq(CleanupItemState.CANDIDATE),
                                                                                lastIdCaptor.capture(),
                                                                                anyInt());
    assertEquals(List.of(0L, 12L, 14L),
                 lastIdCaptor.getAllValues(),
                 "Each batch must resume PAST the highest id of the previous one");
    assertEquals(CleanupItemState.PURGED, firstItem.getState());
    assertEquals(CleanupItemState.PURGED, secondItem.getState());
    assertEquals(CleanupItemState.PURGED, thirdItem.getState());
    assertEquals(CleanupItemState.PURGED, fourthItem.getState());
    // Progress is persisted AFTER EACH BATCH, cumulatively: a crash mid-run
    // resumes from the last persisted count, never from zero. The ETA is asserted
    // EXACTLY, not with anyLong(): 2 of the 4 items are done after batch 1, so
    // the remaining 2 are estimated at the elapsed time itself — 0 s for any run
    // under a second (the arithmetic is pinned by CleanupEtaUtilTest)
    InOrder inOrder = inOrder(campaignStorage);
    inOrder.verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(4L), eq(2L), eq(0L), isNull(), eq(0L));
    inOrder.verify(campaignStorage).updateProgress(eq(CAMPAIGN_ID), eq(4L), eq(4L), eq(0L), isNull(), eq(0L));
    // EXACTLY one progress push per batch on the CometD channel, plus the single
    // stateChanged the real lifecycle emits on COMPLETED. atLeast(2) was
    // unfalsifiable here: that stateChanged alone satisfied it, so moving the
    // per-batch push out of the loop kept the assertion green
    ArgumentCaptor<CleanupWsMessage> messageCaptor = ArgumentCaptor.forClass(CleanupWsMessage.class);
    verify(webSocketService, org.mockito.Mockito.times(3)).sendToAdministrators(messageCaptor.capture());
    List<CleanupWsMessage> progressMessages = messageCaptor.getAllValues()
                                                           .stream()
                                                           .filter(message -> CleanupWsMessage.PROGRESS_EVENT.equals(message.getWsEventName()))
                                                           .toList();
    assertEquals(2, progressMessages.size(), "One PROGRESS push per batch, no more, no less");
    assertEquals(List.of(2L, 4L),
                 progressMessages.stream().map(CleanupWsMessage::getProcessed).toList(),
                 "Each push carries its own batch's cumulated count, in order");
    assertEquals(List.of(4L, 4L), progressMessages.stream().map(CleanupWsMessage::getTotal).toList());
    assertEquals(List.of(0L, 0L),
                 progressMessages.stream().map(CleanupWsMessage::getEtaSeconds).toList(),
                 "The pushed ETA is the same computed value as the persisted one");
    assertEquals(CleanupWsMessage.STATE_CHANGED_EVENT,
                 messageCaptor.getAllValues().get(2).getWsEventName(),
                 "The terminal transition is the LAST push, after both batches");
    ArgumentCaptor<CleanupCampaign> captor = ArgumentCaptor.forClass(CleanupCampaign.class);
    verify(campaignStorage).saveCampaign(captor.capture());
    assertEquals(CleanupCampaignState.COMPLETED, captor.getValue().getState());
    assertEquals(4L, captor.getValue().getProcessedCount());
  }

  @Test
  void bothStartAndResumeScheduleTheTransactionalWorkerEntryPoint() throws ObjectNotFoundException {
    // The purge used to run OUTSIDE any container transaction while the scan ran
    // inside one. Both launch paths must now go through the SAME transactional
    // entry point — pinned by running the scheduled Runnable against a spy whose
    // entry point is stubbed out (so the woven aspect never boots a container)
    org.mockito.Mockito.doNothing().when(executionService).executeCampaignTransactional(anyLong());
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.LOCKED));
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    executionService.startExecution(CAMPAIGN_ID);
    executionService.resumeExecution(CAMPAIGN_ID);

    ArgumentCaptor<Runnable> workerCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(workerExecutor, org.mockito.Mockito.times(2)).execute(workerCaptor.capture());
    workerCaptor.getAllValues().forEach(Runnable::run);
    verify(executionService, org.mockito.Mockito.times(2)).executeCampaignTransactional(CAMPAIGN_ID);
  }

  @Test
  void theScheduledExecutionEntryPointRunsInAContainerTransaction() throws NoSuchMethodException {
    // The tests drive executeCampaign() directly, so nothing else would notice
    // the annotation disappearing from the method the executor schedules — and
    // the whole purge would go back to running outside any transaction
    assertNotNull(CleanupExecutionService.class.getMethod("executeCampaignTransactional", long.class)
                                              .getAnnotation(ContainerTransactional.class),
                  "executeCampaignTransactional must stay annotated @ContainerTransactional");
  }

  private CleanupCampaignItem purgeableItem(long id, String nodeUuid) {
    CleanupCampaignItem item = item(id, nodeUuid, CleanupAction.DELETE);
    when(cleanupJcrStorage.revalidate(eq(nodeUuid), any())).thenReturn(CleanupRevalidation.of(candidate(nodeUuid,
                                                                                                        CleanupAction.DELETE)));
    when(cleanupJcrStorage.deleteNode(nodeUuid)).thenReturn(CleanupPurgeResult.purged(10L));
    return item;
  }

  @SuppressWarnings("unchecked")
  private Set<Long> runningCampaigns() throws ReflectiveOperationException {
    Field runningCampaignsField = CleanupExecutionService.class.getDeclaredField("runningCampaigns");
    runningCampaignsField.setAccessible(true); // NOSONAR test wiring
    return (Set<Long>) runningCampaignsField.get(executionService);
  }

  private CleanupCampaign campaign(CleanupCampaignState state) {
    CleanupCampaign campaign = new CleanupCampaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setName("Q3 cleanup");
    campaign.setState(state);
    campaign.setTotalCount(6);
    campaign.setParams(new CleanupParams(6, 1048576L, 7, 5, List.of(), 200));
    return campaign;
  }

  private CleanupCampaignItem item(long id, String nodeUuid, CleanupAction action) {
    CleanupCampaignItem item = new CleanupCampaignItem();
    item.setId(id);
    item.setCampaignId(CAMPAIGN_ID);
    item.setNodeUuid(nodeUuid);
    item.setAction(action);
    item.setState(CleanupItemState.CANDIDATE);
    item.setFileSize(1048576L);
    return item;
  }

  private CleanupCandidate candidate(String nodeUuid, CleanupAction action) {
    return new CleanupCandidate(nodeUuid,
                                "/Users/john/Private/file.pdf",
                                5L,
                                CANDIDATE_FILE_SIZE,
                                CANDIDATE_VERSIONS_SIZE,
                                action,
                                0L,
                                0L);
  }

}
