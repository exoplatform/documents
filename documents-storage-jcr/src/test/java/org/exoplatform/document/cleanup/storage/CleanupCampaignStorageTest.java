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
package org.exoplatform.document.cleanup.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.dao.CleanupCampaignDAO;
import org.exoplatform.document.cleanup.dao.CleanupCampaignItemDAO;
import org.exoplatform.document.cleanup.entity.CleanupCampaignEntity;
import org.exoplatform.document.cleanup.entity.CleanupCampaignItemEntity;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupParams;

/**
 * Storage tests pinning the entity/model mapping round-trip, the candidate
 * de-duplication on scan resume, the checkpoint/progress updates and the
 * ancestor-chain / LIKE-escaping helpers of the JCR-event path matching.
 */
@ExtendWith(MockitoExtension.class)
class CleanupCampaignStorageTest {

  private static final long      CAMPAIGN_ID = 3L;

  @Mock
  private CleanupCampaignDAO     campaignDAO;

  @Mock
  private CleanupCampaignItemDAO itemDAO;

  @InjectMocks
  private CleanupCampaignStorage storage;

  @Test
  void saveCampaignRoundTripsModelThroughEntity() {
    when(campaignDAO.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    CleanupCampaign campaign = campaign();

    CleanupCampaign saved = storage.saveCampaign(campaign);

    ArgumentCaptor<CleanupCampaignEntity> entityCaptor = ArgumentCaptor.forClass(CleanupCampaignEntity.class);
    verify(campaignDAO).save(entityCaptor.capture());
    CleanupCampaignEntity entity = entityCaptor.getValue();
    assertEquals(CAMPAIGN_ID, entity.getId());
    assertEquals(CleanupCampaignState.SIMULATED.name(), entity.getState());
    assertEquals(6, entity.getPeriodMonths());
    assertEquals(1048576L, entity.getMinFileSizeBytes());
    assertEquals(7, entity.getGraceDays());
    assertEquals(5, entity.getMaxVersionsPerFile());
    assertEquals(new Date(1000L), entity.getStartedDate());
    assertNull(entity.getPublishedDate(), "A zero-millis date must persist as null");

    assertEquals(campaign.getId(), saved.getId());
    assertEquals(campaign.getName(), saved.getName());
    assertEquals(campaign.getState(), saved.getState());
    assertEquals(campaign.getParams().getPeriodMonths(), saved.getParams().getPeriodMonths());
    assertEquals(campaign.getParams().getMinFileSizeBytes(), saved.getParams().getMinFileSizeBytes());
    assertEquals(campaign.getParams().getGraceDays(), saved.getParams().getGraceDays());
    assertEquals(campaign.getParams().getMaxVersionsPerFile(), saved.getParams().getMaxVersionsPerFile());
    assertEquals(campaign.getParams().getExcludedPaths(), saved.getParams().getExcludedPaths());
    assertEquals(campaign.getStartedDate(), saved.getStartedDate());
    assertEquals(campaign.getPublishedDate(), saved.getPublishedDate());
    assertEquals(campaign.getTotalCount(), saved.getTotalCount());
    assertEquals(campaign.getProcessedCount(), saved.getProcessedCount());
    assertEquals(campaign.getEtaSeconds(), saved.getEtaSeconds());
    assertEquals(campaign.getCheckpointPath(), saved.getCheckpointPath());
    assertEquals(campaign.getCheckpointOffset(), saved.getCheckpointOffset());
    assertEquals(campaign.getSummaryJson(), saved.getSummaryJson());
    assertEquals(campaign.getArchiveFileId(), saved.getArchiveFileId());
  }

  @Test
  void createCampaignAlwaysInsertsANewRow() {
    when(campaignDAO.save(any())).thenAnswer(invocation -> {
      CleanupCampaignEntity entity = invocation.getArgument(0);
      assertNull(entity.getId(), "Creation must never overwrite an existing row");
      entity.setId(99L);
      return entity;
    });
    CleanupCampaign campaign = campaign();
    campaign.setId(CAMPAIGN_ID);

    assertEquals(99L, storage.createCampaign(campaign).getId());
  }

  @Test
  void getCampaignReturnsNullWhenMissing() {
    when(campaignDAO.findById(CAMPAIGN_ID)).thenReturn(Optional.empty());

    assertNull(storage.getCampaign(CAMPAIGN_ID));
  }

  @Test
  void getCampaignsByStatesQueriesByStateNames() {
    when(campaignDAO.findByStateIn(List.of("PUBLISHED", "LOCKED"))).thenReturn(List.of(entity()));

    List<CleanupCampaign> campaigns = storage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED,
                                                                           CleanupCampaignState.LOCKED));

    assertEquals(1, campaigns.size());
    assertEquals(CAMPAIGN_ID, campaigns.get(0).getId());
  }

  @Test
  void updateProgressPersistsCheckpoint() {
    CleanupCampaignEntity entity = entity();
    when(campaignDAO.findById(CAMPAIGN_ID)).thenReturn(Optional.of(entity));

    storage.updateProgress(CAMPAIGN_ID, 100, 40, 60, "/Groups/spaces", 400);

    verify(campaignDAO).save(entity);
    assertEquals(100, entity.getTotalCount());
    assertEquals(40, entity.getProcessedCount());
    assertEquals(60, entity.getEtaSeconds());
    assertEquals("/Groups/spaces", entity.getCheckpointPath());
    assertEquals(400, entity.getCheckpointOffset());
  }

  @Test
  void updateProgressIgnoresMissingCampaign() {
    when(campaignDAO.findById(CAMPAIGN_ID)).thenReturn(Optional.empty());

    storage.updateProgress(CAMPAIGN_ID, 100, 40, 60, "/Users", 200);

    verify(campaignDAO, never()).save(any());
  }

  @Test
  void saveCandidatesSkipsAlreadyRecordedNodes() {
    CleanupCampaignItemEntity existing = new CleanupCampaignItemEntity();
    existing.setNodeUuid("uuid-existing");
    when(itemDAO.findByCampaignIdAndNodeUuidIn(eq(CAMPAIGN_ID), anyCollection())).thenReturn(List.of(existing));

    storage.saveCandidates(CAMPAIGN_ID,
                           List.of(candidate("uuid-existing", "/Users/j___/john/Private/old.pdf"),
                                   candidate("uuid-new", "/Users/j___/john/Private/new.pdf")));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CleanupCampaignItemEntity>> savedCaptor = ArgumentCaptor.forClass(List.class);
    verify(itemDAO).saveAll(savedCaptor.capture());
    List<CleanupCampaignItemEntity> saved = savedCaptor.getValue();
    assertEquals(1, saved.size(), "A replayed batch must only insert the not-yet-recorded nodes");
    CleanupCampaignItemEntity savedEntity = saved.get(0);
    assertEquals("uuid-new", savedEntity.getNodeUuid());
    assertEquals(CAMPAIGN_ID, savedEntity.getCampaignId());
    assertEquals("/Users/j___/john/Private/new.pdf", savedEntity.getPath());
    assertEquals(7L, savedEntity.getOwnerIdentityId());
    assertEquals(2048, savedEntity.getFileSize());
    assertEquals(1024, savedEntity.getVersionsSize());
    assertEquals(CleanupAction.DELETE.name(), savedEntity.getAction());
    assertEquals(CleanupItemState.CANDIDATE.name(), savedEntity.getState(), "A new candidate always starts as CANDIDATE");
  }

  @Test
  void saveCandidatesSavesNothingWhenAllReplayed() {
    CleanupCampaignItemEntity existing = new CleanupCampaignItemEntity();
    existing.setNodeUuid("uuid-existing");
    when(itemDAO.findByCampaignIdAndNodeUuidIn(eq(CAMPAIGN_ID), anyCollection())).thenReturn(List.of(existing));

    storage.saveCandidates(CAMPAIGN_ID, List.of(candidate("uuid-existing", "/Users/j___/john/Private/old.pdf")));

    verify(itemDAO, never()).saveAll(anyList());
  }

  @Test
  void saveCandidatesIgnoresEmptyBatches() {
    storage.saveCandidates(CAMPAIGN_ID, null);
    storage.saveCandidates(CAMPAIGN_ID, List.of());

    verifyNoInteractions(itemDAO);
  }

  @Test
  void saveItemRoundTripsModelThroughEntity() {
    when(itemDAO.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    CleanupCampaignItem item = item();

    CleanupCampaignItem saved = storage.saveItem(item);

    ArgumentCaptor<CleanupCampaignItemEntity> entityCaptor = ArgumentCaptor.forClass(CleanupCampaignItemEntity.class);
    verify(itemDAO).save(entityCaptor.capture());
    CleanupCampaignItemEntity entity = entityCaptor.getValue();
    assertEquals(CleanupAction.PURGE_VERSIONS.name(), entity.getAction());
    assertEquals(CleanupItemState.PURGED.name(), entity.getState());
    assertEquals(new Date(5000L), entity.getComputedAt());
    assertNull(entity.getDecidedAt());

    assertEquals(item.getId(), saved.getId());
    assertEquals(item.getCampaignId(), saved.getCampaignId());
    assertEquals(item.getNodeUuid(), saved.getNodeUuid());
    assertEquals(item.getPath(), saved.getPath());
    assertEquals(item.getOwnerIdentityId(), saved.getOwnerIdentityId());
    assertEquals(item.getFileSize(), saved.getFileSize());
    assertEquals(item.getVersionsSize(), saved.getVersionsSize());
    assertEquals(item.getAction(), saved.getAction());
    assertEquals(item.getState(), saved.getState());
    assertEquals(item.getComputedAt(), saved.getComputedAt());
    assertEquals(item.getDecidedBy(), saved.getDecidedBy());
    assertEquals(item.getDecidedAt(), saved.getDecidedAt());
    assertEquals(item.getPurgedAt(), saved.getPurgedAt());
    assertEquals(item.getReclaimedBytes(), saved.getReclaimedBytes());
    assertEquals(item.getFailureReason(), saved.getFailureReason());
  }

  @Test
  void getItemsTouchedByPathMatchesAncestorsExactlyAndDescendantsEscaped() {
    String eventPath = "/Groups/spaces/marketing/Documents/reports_2026/q1.pdf";
    when(itemDAO.findByCampaignIdAndPathTouchedBy(eq(CAMPAIGN_ID), anyList(), any())).thenReturn(List.of());

    storage.getItemsTouchedByPath(CAMPAIGN_ID, eventPath);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> ancestorsCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<String> escapedCaptor = ArgumentCaptor.forClass(String.class);
    verify(itemDAO).findByCampaignIdAndPathTouchedBy(eq(CAMPAIGN_ID), ancestorsCaptor.capture(), escapedCaptor.capture());
    assertEquals(List.of("/Groups/spaces/marketing",
                         "/Groups/spaces/marketing/Documents",
                         "/Groups/spaces/marketing/Documents/reports_2026",
                         eventPath),
                 ancestorsCaptor.getValue());
    assertEquals("/Groups/spaces/marketing/Documents/reports|_2026/q1.pdf", escapedCaptor.getValue());
  }

  @Test
  void ancestorChainStopsBelowTheContainingScanRoot() {
    assertEquals(List.of("/Users/j___", "/Users/j___/john", "/Users/j___/john/Private", "/Users/j___/john/Private/a.pdf"),
                 CleanupCampaignStorage.ancestorChain("/Users/j___/john/Private/a.pdf"));
    assertEquals(List.of("/Trash/deleted.pdf"),
                 CleanupCampaignStorage.ancestorChain("/Trash/deleted.pdf"),
                 "A node directly under a root has no intermediate ancestor");
    assertEquals(List.of("/exo:applications/some/node"),
                 CleanupCampaignStorage.ancestorChain("/exo:applications/some/node"),
                 "A path outside every scan root only matches itself");
  }

  @Test
  void escapeLikeNeutralizesWildcardsAndEscapeCharacter() {
    assertEquals("/Users/j|_|_|_/john", CleanupCampaignStorage.escapeLike("/Users/j___/john"));
    assertEquals("100|%", CleanupCampaignStorage.escapeLike("100%"));
    assertEquals("a||b", CleanupCampaignStorage.escapeLike("a|b"));
    assertEquals("plain/path", CleanupCampaignStorage.escapeLike("plain/path"));
  }

  @Test
  void getItemsMapsNullableFiltersToDao() {
    when(itemDAO.findByFilters(eq(CAMPAIGN_ID), eq(7L), eq("CANDIDATE"), eq("DELETE"), eq(1024L), any()))
                                                                                                         .thenReturn(new PageImpl<>(List.of()));

    storage.getItems(CAMPAIGN_ID, 7L, CleanupItemState.CANDIDATE, CleanupAction.DELETE, 1024L, PageRequest.of(0, 10));
    verify(itemDAO).findByFilters(eq(CAMPAIGN_ID), eq(7L), eq("CANDIDATE"), eq("DELETE"), eq(1024L), any());

    when(itemDAO.findByFilters(eq(CAMPAIGN_ID), eq((Long) null), eq((String) null), eq((String) null), eq((Long) null), any()))
                                                                                                                               .thenReturn(new PageImpl<>(List.of()));
    storage.getItems(CAMPAIGN_ID, null, null, null, null, PageRequest.of(0, 10));
    verify(itemDAO).findByFilters(eq(CAMPAIGN_ID), eq((Long) null), eq((String) null), eq((String) null), eq((Long) null), any());
  }

  @Test
  void getNodeUuidToReclaimableBytesDrainsEveryPage() {
    Pageable firstPage = PageRequest.of(0, 1000, org.springframework.data.domain.Sort.by("id"));
    when(itemDAO.findNodeUuidAndReclaimableBytes(eq(CAMPAIGN_ID), any(Pageable.class)))
                                                                                       .thenReturn(new PageImpl<>(List.of(new Object[][] {
                                                                                                                           { "uuid-1",
                                                                                                                             100L } }),
                                                                                                                  firstPage,
                                                                                                                  1001),
                                                                                                   new PageImpl<>(List.of(new Object[][] {
                                                                                                                           { "uuid-2",
                                                                                                                             200L } }),
                                                                                                                  firstPage.next(),
                                                                                                                  1001));

    Map<String, Long> result = storage.getNodeUuidToReclaimableBytes(CAMPAIGN_ID);

    assertEquals(Map.of("uuid-1", 100L, "uuid-2", 200L), result);
    verify(itemDAO, org.mockito.Mockito.times(2)).findNodeUuidAndReclaimableBytes(eq(CAMPAIGN_ID), any(Pageable.class));
  }

  @Test
  void countsAndSumsDelegateWithStateNames() {
    when(itemDAO.countByCampaignIdAndState(CAMPAIGN_ID, "CANDIDATE")).thenReturn(4L);
    assertEquals(4L, storage.countItemsByState(CAMPAIGN_ID, CleanupItemState.CANDIDATE));

    when(itemDAO.sumReclaimableBytesByState(CAMPAIGN_ID, "EXEMPTED")).thenReturn(2048L);
    assertEquals(2048L, storage.sumReclaimableBytesByState(CAMPAIGN_ID, CleanupItemState.EXEMPTED));

    when(itemDAO.countByCampaignIdAndOwnerIdentityIdInAndState(CAMPAIGN_ID, List.of(7L), "PURGED")).thenReturn(2L);
    assertEquals(2L, storage.countItemsByOwnersAndState(CAMPAIGN_ID, List.of(7L), CleanupItemState.PURGED));

    when(itemDAO.existsByCampaignId(CAMPAIGN_ID)).thenReturn(true);
    assertTrue(storage.hasItems(CAMPAIGN_ID));

    storage.deleteItems(CAMPAIGN_ID);
    verify(itemDAO).deleteByCampaignId(CAMPAIGN_ID);
  }

  @Test
  void excludedPathsRoundTripAsJson() {
    when(campaignDAO.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    CleanupCampaign campaign = campaign();
    campaign.getParams().setExcludedPaths(List.of("/Users/root", "/Groups/spaces/admin"));

    CleanupCampaign saved = storage.saveCampaign(campaign);
    assertEquals(List.of("/Users/root", "/Groups/spaces/admin"), saved.getParams().getExcludedPaths());

    campaign.getParams().setExcludedPaths(null);
    saved = storage.saveCampaign(campaign);
    assertTrue(saved.getParams().getExcludedPaths().isEmpty(), "A null persisted JSON must map to an empty list");
  }

  private CleanupCampaign campaign() {
    CleanupCampaign campaign = new CleanupCampaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setName("Spring cleanup");
    campaign.setState(CleanupCampaignState.SIMULATED);
    campaign.setParams(new CleanupParams(6, 1048576L, 7, 5, List.of("/Users/root"), null));
    campaign.setStartedDate(1000L);
    campaign.setPublishedDate(0);
    campaign.setTotalCount(100);
    campaign.setProcessedCount(40);
    campaign.setEtaSeconds(60);
    campaign.setCheckpointPath("/Users");
    campaign.setCheckpointOffset(200);
    campaign.setSummaryJson("{\"candidateCount\":25}");
    campaign.setArchiveFileId(9L);
    return campaign;
  }

  private CleanupCampaignEntity entity() {
    CleanupCampaignEntity entity = new CleanupCampaignEntity();
    entity.setId(CAMPAIGN_ID);
    entity.setName("Spring cleanup");
    entity.setState(CleanupCampaignState.PUBLISHED.name());
    return entity;
  }

  private CleanupCampaignItem item() {
    CleanupCampaignItem item = new CleanupCampaignItem();
    item.setId(9L);
    item.setCampaignId(CAMPAIGN_ID);
    item.setNodeUuid("uuid-9");
    item.setPath("/Users/j___/john/Private/report.pdf");
    item.setOwnerIdentityId(7L);
    item.setFileSize(2048);
    item.setVersionsSize(1024);
    item.setAction(CleanupAction.PURGE_VERSIONS);
    item.setState(CleanupItemState.PURGED);
    item.setComputedAt(5000L);
    item.setDecidedBy("john");
    item.setDecidedAt(0);
    item.setPurgedAt(7000L);
    item.setReclaimedBytes(128);
    item.setFailureReason("some.failure");
    return item;
  }

  private CleanupCandidate candidate(String nodeUuid, String path) {
    return new CleanupCandidate(nodeUuid, path, 7L, 2048, 1024, CleanupAction.DELETE, 100, 200);
  }

}
