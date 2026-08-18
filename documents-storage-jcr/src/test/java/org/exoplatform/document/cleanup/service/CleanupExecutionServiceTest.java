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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupPurgeResult;
import org.exoplatform.document.cleanup.model.CleanupRevalidation;
import org.exoplatform.document.cleanup.storage.CleanupCampaignStorage;
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage;
import org.exoplatform.document.cleanup.websocket.CleanupWebSocketService;

@ExtendWith(MockitoExtension.class)
class CleanupExecutionServiceTest {

  private static final String      NODE_UUID_VERSIONS      = "uuid-versions";

  private static final String      NODE_UUID_DELETED       = "uuid-deleted";

  private static final String      NODE_UUID_SKIPPED       = "uuid-skipped";

  private static final String      NODE_UUID_SPARED        = "uuid-spared";

  private static final String      NODE_UUID_EXEMPTED      = "uuid-exempted";

  private static final String      NODE_UUID_GONE          = "uuid-gone";

  private static final long        CAMPAIGN_ID             = 1L;

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

  @BeforeEach
  void injectLifecycle() throws ReflectiveOperationException {
    // Mockito doesn't inject a @Spy @InjectMocks field into another
    // @InjectMocks: wire the real lifecycle (fed with the mocks) manually
    Field lifecycleField = CleanupExecutionService.class.getDeclaredField("campaignLifecycle");
    lifecycleField.setAccessible(true); // NOSONAR test wiring
    lifecycleField.set(executionService, campaignLifecycle); // NOSONAR
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

    Page<CleanupCampaignItem> batch = new PageImpl<>(List.of(goneItem,
                                                             exemptedItem,
                                                             sparedItem,
                                                             skippedItem,
                                                             deletedItem,
                                                             purgedVersionsItem));
    when(campaignStorage.getItemsByState(eq(CAMPAIGN_ID), eq(CleanupItemState.CANDIDATE), any()))
                                                                                                 .thenReturn(batch)
                                                                                                 .thenReturn(new PageImpl<>(List.of()));

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
    when(cleanupJcrStorage.deleteNode(NODE_UUID_SKIPPED)).thenReturn(CleanupPurgeResult.skipped("cleanup.referentialIntegrity: referenced"));
    when(cleanupJcrStorage.deleteNode(NODE_UUID_DELETED)).thenReturn(CleanupPurgeResult.purged(100L));
    when(cleanupJcrStorage.purgeVersions(NODE_UUID_VERSIONS, 5)).thenReturn(CleanupPurgeResult.purged(50L));

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
  void shouldAbortExecutionWhenCampaignNoLongerExecuting() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.CANCELLED));

    executionService.executeCampaign(CAMPAIGN_ID);

    verify(campaignStorage, org.mockito.Mockito.never()).saveItem(any());
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
