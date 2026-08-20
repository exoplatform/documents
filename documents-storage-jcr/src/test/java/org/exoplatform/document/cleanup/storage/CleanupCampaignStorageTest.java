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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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
import org.exoplatform.document.cleanup.model.CleanupComparisonBucket;
import org.exoplatform.document.cleanup.model.CleanupFailureGroup;
import org.exoplatform.document.cleanup.model.CleanupParams;

/**
 * Storage tests pinning the entity/model mapping round-trip, the candidate
 * de-duplication on scan resume, the checkpoint/progress updates and the
 * ancestor-chain / LIKE-escaping helpers of the JCR-event path matching.
 */
@ExtendWith(MockitoExtension.class)
class CleanupCampaignStorageTest {

  private static final String    NODE_UUID_EXISTING = "uuid-existing";

  private static final String    EXEMPTED_STATE     = "EXEMPTED";

  private static final String    PURGED_STATE       = "PURGED";

  private static final String    CANDIDATE_STATE    = "CANDIDATE";

  private static final String    USERS_ROOT_PATH    = "/Users/root";  // NOSONAR

  private static final long      CAMPAIGN_ID        = 3L;

  private static final long      OTHER_CAMPAIGN_ID  = 4L;

  private static final String    SKIPPED_STATE      = "SKIPPED";

  private static final String    FAILURE_DETAIL     = "javax.jcr.RepositoryException: boom";

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
    existing.setNodeUuid(NODE_UUID_EXISTING);
    when(itemDAO.findByCampaignIdAndNodeUuidIn(eq(CAMPAIGN_ID), anyCollection())).thenReturn(List.of(existing));

    storage.saveCandidates(CAMPAIGN_ID,
                           List.of(candidate(NODE_UUID_EXISTING, "/Users/j___/john/Private/old.pdf"),
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
    // The dates that MADE the file a candidate are what the report explains
    // itself with: dropping them here would leave the column empty forever
    assertEquals(new Date(200L), savedEntity.getLastModifiedDate());
    assertEquals(new Date(100L), savedEntity.getCreatedDate());
  }

  @Test
  void saveCandidatesPersistsUnreadableCandidacyDatesAsNull() {
    when(itemDAO.findByCampaignIdAndNodeUuidIn(eq(CAMPAIGN_ID), anyCollection())).thenReturn(List.of());
    CleanupCandidate undatedCandidate = new CleanupCandidate("uuid-undated",
                                                             "/Users/j___/john/Private/undated.pdf",
                                                             7L,
                                                             2048,
                                                             1024,
                                                             CleanupAction.DELETE,
                                                             0,
                                                             0);

    storage.saveCandidates(CAMPAIGN_ID, List.of(undatedCandidate));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CleanupCampaignItemEntity>> savedCaptor = ArgumentCaptor.forClass(List.class);
    verify(itemDAO).saveAll(savedCaptor.capture());
    CleanupCampaignItemEntity savedEntity = savedCaptor.getValue().get(0);
    assertNull(savedEntity.getLastModifiedDate(), "A zero-millis date must persist as NULL, never as the epoch");
    assertNull(savedEntity.getCreatedDate(), "A zero-millis date must persist as NULL, never as the epoch");
  }

  @Test
  void saveCandidatesPersistsExemptedCandidatesAsExemptedWithMixinDecision() {
    when(itemDAO.findByCampaignIdAndNodeUuidIn(eq(CAMPAIGN_ID), anyCollection())).thenReturn(List.of());
    CleanupCandidate exemptedCandidate = candidate("uuid-kept", "/Users/j___/john/Private/kept.pdf");
    exemptedCandidate.setExempted(true);
    exemptedCandidate.setExemptedBy("mary");
    exemptedCandidate.setExemptedDate(123456789L);

    storage.saveCandidates(CAMPAIGN_ID, List.of(exemptedCandidate));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CleanupCampaignItemEntity>> savedCaptor = ArgumentCaptor.forClass(List.class);
    verify(itemDAO).saveAll(savedCaptor.capture());
    CleanupCampaignItemEntity savedEntity = savedCaptor.getValue().get(0);
    assertEquals(CleanupItemState.EXEMPTED.name(),
                 savedEntity.getState(),
                 "A mixin-carrying file must be persisted as EXEMPTED, visible as 'Kept'");
    assertEquals("mary", savedEntity.getDecidedBy());
    assertEquals(new Date(123456789L), savedEntity.getDecidedAt());
    assertEquals(CleanupAction.DELETE.name(), savedEntity.getAction(), "The action stays computed as usual");
  }

  @Test
  void saveCandidatesLeavesDecisionEmptyWhenMixinMetadataUnreadable() {
    when(itemDAO.findByCampaignIdAndNodeUuidIn(eq(CAMPAIGN_ID), anyCollection())).thenReturn(List.of());
    CleanupCandidate exemptedCandidate = candidate("uuid-kept", "/Users/j___/john/Private/kept.pdf");
    exemptedCandidate.setExempted(true);

    storage.saveCandidates(CAMPAIGN_ID, List.of(exemptedCandidate));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CleanupCampaignItemEntity>> savedCaptor = ArgumentCaptor.forClass(List.class);
    verify(itemDAO).saveAll(savedCaptor.capture());
    CleanupCampaignItemEntity savedEntity = savedCaptor.getValue().get(0);
    assertEquals(CleanupItemState.EXEMPTED.name(), savedEntity.getState());
    assertNull(savedEntity.getDecidedBy());
    assertNull(savedEntity.getDecidedAt());
  }

  @Test
  void saveCandidatesSavesNothingWhenAllReplayed() {
    CleanupCampaignItemEntity existing = new CleanupCampaignItemEntity();
    existing.setNodeUuid(NODE_UUID_EXISTING);
    when(itemDAO.findByCampaignIdAndNodeUuidIn(eq(CAMPAIGN_ID), anyCollection())).thenReturn(List.of(existing));

    storage.saveCandidates(CAMPAIGN_ID, List.of(candidate(NODE_UUID_EXISTING, "/Users/j___/john/Private/old.pdf")));

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
    assertEquals(new Date(3000L), entity.getLastModifiedDate());
    assertEquals(new Date(2000L), entity.getCreatedDate());

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
    assertEquals(FAILURE_DETAIL, entity.getFailureDetail(), "The diagnostic must reach the CLOB column");
    assertEquals(item.getFailureDetail(), saved.getFailureDetail());
    assertEquals(2L, entity.getAttemptCount());
    assertEquals(item.getAttemptCount(), saved.getAttemptCount());
    // Back to the model: a report column read from a row it never came back on
    // would render empty for every item
    assertEquals(item.getLastModifiedDate(), saved.getLastModifiedDate());
    assertEquals(item.getCreatedDate(), saved.getCreatedDate());
  }

  @Test
  void saveItemMapsUnsetCandidacyDatesToNullAndBackToZero() {
    when(itemDAO.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    CleanupCampaignItem item = item();
    item.setLastModifiedDate(0);
    item.setCreatedDate(0);

    CleanupCampaignItem saved = storage.saveItem(item);

    ArgumentCaptor<CleanupCampaignItemEntity> entityCaptor = ArgumentCaptor.forClass(CleanupCampaignItemEntity.class);
    verify(itemDAO).save(entityCaptor.capture());
    assertNull(entityCaptor.getValue().getLastModifiedDate(), "A zero-millis date must persist as NULL");
    assertNull(entityCaptor.getValue().getCreatedDate(), "A zero-millis date must persist as NULL");
    // ... and a NULL column comes back as 0, the model's 'not set', so the DTO
    // maps it to a null date instead of the epoch
    assertEquals(0, saved.getLastModifiedDate());
    assertEquals(0, saved.getCreatedDate());
  }

  @Test
  void getItemsTouchedByPathMatchesAncestorsExactlyAndDescendantsEscaped() {
    String eventPath = "/Groups/spaces/marketing/Documents/reports_2026/q1.pdf"; // NOSONAR
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
  void ancestorChainOfPropertyEventPathContainsTheFileItemPath() {
    // A PROPERTY_CHANGED event path is the PROPERTY's path, a DESCENDANT of
    // the file node: the ancestor chain must contain the file item's path so
    // the exact-match branch of the touched-by query refreshes the file item
    List<String> chain = CleanupCampaignStorage.ancestorChain("/Users/j___/john/Private/file.pdf/jcr:content/jcr:data");
    assertTrue(chain.contains("/Users/j___/john/Private/file.pdf"),
               "The file path above the changed property must be in the ancestor chain");
  }

  @Test
  void getItemAggregatesFoldsGroupedRowsPerCampaign() {
    when(itemDAO.findAggregatesByCampaignIds(List.of(1L, 2L, 3L)))
                                                                  .thenReturn(List.of(new Object[] { 1L, CANDIDATE_STATE, 4L,
                                                                    2048L,
                                                                    0L },
                                                                                      new Object[] { 1L, PURGED_STATE, 2L, 512L,
                                                                                        999L },
                                                                                      new Object[] { 2L, EXEMPTED_STATE, 1L, 128L,
                                                                                        0L }));

    Map<Long, org.exoplatform.document.cleanup.model.CleanupCampaignAggregates> aggregates =
                                                                                           storage.getItemAggregates(List.of(1L,
                                                                                                                             2L,
                                                                                                                             3L));

    // Campaign 1: candidate count/bytes from the CANDIDATE row only, reclaimed
    // bytes summed across every state
    assertTrue(aggregates.get(1L).isItemsRetained());
    assertEquals(4L, aggregates.get(1L).getCandidateCount());
    assertEquals(2048L, aggregates.get(1L).getReclaimableBytes());
    assertEquals(999L, aggregates.get(1L).getReclaimedBytes());
    // Campaign 2: rows exist but none CANDIDATE
    assertTrue(aggregates.get(2L).isItemsRetained());
    assertEquals(0L, aggregates.get(2L).getCandidateCount());
    // Campaign 3: no item rows anymore, absent from the map
    assertNull(aggregates.get(3L));
    // A single grouped query serves the whole list
    verify(itemDAO, org.mockito.Mockito.times(1)).findAggregatesByCampaignIds(anyList());
    verify(itemDAO, never()).countByCampaignIdAndState(anyLong(), any());
    verify(itemDAO, never()).existsByCampaignId(anyLong());
  }

  @Test
  void getItemAggregatesOfEmptyListNeverQueries() {
    assertTrue(storage.getItemAggregates(List.of()).isEmpty());
    assertTrue(storage.getItemAggregates(null).isEmpty());
    verifyNoInteractions(itemDAO);
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
    when(itemDAO.findByFilters(eq(CAMPAIGN_ID),
                               eq(7L),
                               eq(CANDIDATE_STATE),
                               eq("DELETE"),
                               eq(1024L),
                               eq("%report%"),
                               any())).thenReturn(new PageImpl<>(List.of()));

    storage.getItems(CAMPAIGN_ID, 7L, CleanupItemState.CANDIDATE, CleanupAction.DELETE, 1024L, "Report", PageRequest.of(0, 10));
    // The search composes with every other filter, in ONE query
    verify(itemDAO).findByFilters(eq(CAMPAIGN_ID),
                                 eq(7L),
                                 eq(CANDIDATE_STATE),
                                 eq("DELETE"),
                                 eq(1024L),
                                 eq("%report%"),
                                 any());

    when(itemDAO.findByFilters(eq(CAMPAIGN_ID),
                               eq((Long) null),
                               eq((String) null),
                               eq((String) null),
                               eq((Long) null),
                               eq((String) null),
                               any())).thenReturn(new PageImpl<>(List.of()));
    storage.getItems(CAMPAIGN_ID, null, null, null, null, "   ", PageRequest.of(0, 10));
    // A blank term means NO filter at all, never an empty '%%' match
    verify(itemDAO).findByFilters(eq(CAMPAIGN_ID),
                                 eq((Long) null),
                                 eq((String) null),
                                 eq((String) null),
                                 eq((Long) null),
                                 eq((String) null),
                                 any());
  }

  @Test
  void getItemsByOwnersPassesTheEscapedSearchPatternAlongTheOwners() {
    Pageable pageable = PageRequest.of(1, 25);
    when(itemDAO.findByOwnersAndSearch(eq(CAMPAIGN_ID), eq(List.of(5L, 6L)), eq("%q1|_25|%%"), eq(pageable)))
                                                                                                             .thenReturn(new PageImpl<>(List.of()));

    storage.getItemsByOwners(CAMPAIGN_ID, List.of(5L, 6L), " Q1_25% ", pageable);

    // Trimmed, lower-cased and run through the SAME escaping as the JCR-event
    // queries: the user's own '_' and '%' must match literally, not wildcard
    verify(itemDAO).findByOwnersAndSearch(eq(CAMPAIGN_ID), eq(List.of(5L, 6L)), eq("%q1|_25|%%"), eq(pageable));

    when(itemDAO.findByOwnersAndSearch(eq(CAMPAIGN_ID), eq(List.of(5L)), eq((String) null), any()))
                                                                                                  .thenReturn(new PageImpl<>(List.of()));
    storage.getItemsByOwners(CAMPAIGN_ID, List.of(5L), null, pageable);
    verify(itemDAO).findByOwnersAndSearch(eq(CAMPAIGN_ID), eq(List.of(5L)), eq((String) null), any());
  }

  @Test
  void chunkOwnerIdsNeverExceedsTheInClauseCapAndKeepsEveryId() {
    List<Long> ownerIdentityIds = ownerIds(950);

    List<List<Long>> chunks = CleanupCampaignStorage.chunkOwnerIds(ownerIdentityIds);

    // Oracle rejects an IN list above 1000 expressions (ORA-01795): the cap is
    // what keeps a user managing a thousand spaces from breaking their own page
    assertEquals(2, chunks.size());
    assertEquals(CleanupCampaignStorage.MAX_IN_CLAUSE_SIZE, chunks.get(0).size());
    assertEquals(50, chunks.get(1).size());
    // No id is lost and none is duplicated by the split
    assertEquals(ownerIdentityIds, chunks.stream().flatMap(List::stream).toList());
    // Exactly at the cap: still a single query
    assertEquals(1, CleanupCampaignStorage.chunkOwnerIds(ownerIds(CleanupCampaignStorage.MAX_IN_CLAUSE_SIZE)).size());
    // Empty (or absent) means ONE empty chunk, so callers keep issuing their
    // single query and returning its result instead of faking a zero
    assertEquals(List.of(List.of()), CleanupCampaignStorage.chunkOwnerIds(List.of()));
    assertEquals(List.of(List.of()), CleanupCampaignStorage.chunkOwnerIds(null));
  }

  @Test
  void ownerScopedCountsAndSumsAreQueriedPerChunkAndAddedUp() {
    List<Long> ownerIdentityIds = ownerIds(950);
    List<Long> firstChunk = ownerIdentityIds.subList(0, CleanupCampaignStorage.MAX_IN_CLAUSE_SIZE);
    List<Long> secondChunk = ownerIdentityIds.subList(CleanupCampaignStorage.MAX_IN_CLAUSE_SIZE, 950);
    when(itemDAO.countByCampaignIdAndOwnerIdentityIdInAndState(CAMPAIGN_ID, firstChunk, CANDIDATE_STATE)).thenReturn(7L);
    when(itemDAO.countByCampaignIdAndOwnerIdentityIdInAndState(CAMPAIGN_ID, secondChunk, CANDIDATE_STATE)).thenReturn(3L);
    when(itemDAO.sumReclaimableBytesByOwnersAndState(CAMPAIGN_ID, firstChunk, CANDIDATE_STATE)).thenReturn(2048L);
    when(itemDAO.sumReclaimableBytesByOwnersAndState(CAMPAIGN_ID, secondChunk, CANDIDATE_STATE)).thenReturn(1024L);
    when(itemDAO.sumReclaimedBytesByOwners(CAMPAIGN_ID, firstChunk)).thenReturn(500L);
    when(itemDAO.sumReclaimedBytesByOwners(CAMPAIGN_ID, secondChunk)).thenReturn(100L);

    // Counts and sums merge ADDITIVELY across chunks, which is exact: the owner
    // sets are disjoint by construction
    assertEquals(10L, storage.countItemsByOwnersAndState(CAMPAIGN_ID, ownerIdentityIds, CleanupItemState.CANDIDATE));
    assertEquals(3072L,
                 storage.sumReclaimableBytesByOwnersAndState(CAMPAIGN_ID, ownerIdentityIds, CleanupItemState.CANDIDATE));
    assertEquals(600L, storage.sumReclaimedBytesByOwners(CAMPAIGN_ID, ownerIdentityIds));
    // One query per chunk, each one under the IN-list cap
    verify(itemDAO).countByCampaignIdAndOwnerIdentityIdInAndState(CAMPAIGN_ID, firstChunk, CANDIDATE_STATE);
    verify(itemDAO).countByCampaignIdAndOwnerIdentityIdInAndState(CAMPAIGN_ID, secondChunk, CANDIDATE_STATE);
  }

  @Test
  void getItemsByOwnersMergesTheChunkPagesInTheRequestedOrder() {
    List<Long> ownerIdentityIds = ownerIds(950);
    Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "fileSize").and(Sort.by(Sort.Direction.ASC, "path")));
    // Each chunk is asked for the rows up to the requested page, in the SAME
    // ordering, so the merge below can be exact
    Pageable expectedChunkPageable = PageRequest.of(0, 2, pageable.getSort());
    stubChunkPages(ownerIdentityIds, expectedChunkPageable);

    Page<CleanupCampaignItem> page = storage.getItemsByOwners(CAMPAIGN_ID, ownerIdentityIds, null, pageable);

    // Merged by fileSize DESCENDING across both chunks: a reversed comparator
    // would answer the two SMALLEST files here
    assertEquals(List.of("/a-500.pdf", "/b-300.pdf"), page.getContent().stream().map(CleanupCampaignItem::getPath).toList());
    // The total is the exact sum of the chunks' own totals (5 + 6), the owner
    // sets being disjoint
    assertEquals(11L, page.getTotalElements());
    assertEquals(0, page.getNumber());
    assertEquals(2, page.getSize());
    verify(itemDAO, times(2)).findByOwnersAndSearch(eq(CAMPAIGN_ID), anyList(), any(), eq(expectedChunkPageable));
  }

  @Test
  void getItemsByOwnersSlicesTheRequestedPageOutOfTheMergedChunks() {
    List<Long> ownerIdentityIds = ownerIds(950);
    Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "fileSize").and(Sort.by(Sort.Direction.ASC, "path")));
    // Page 1 of size 2: each chunk must yield the first FOUR rows, so the
    // requested page can be filled even when it comes entirely from one chunk
    Pageable expectedChunkPageable = PageRequest.of(0, 4, pageable.getSort());
    stubChunkPages(ownerIdentityIds, expectedChunkPageable);

    Page<CleanupCampaignItem> page = storage.getItemsByOwners(CAMPAIGN_ID, ownerIdentityIds, null, pageable);

    // Rows 3 and 4 of the merge, not rows 1-2 and not the raw chunk order
    assertEquals(List.of("/c-200.pdf", "/d-100.pdf"), page.getContent().stream().map(CleanupCampaignItem::getPath).toList());
    assertEquals(11L, page.getTotalElements());
    assertEquals(1, page.getNumber());
  }

  @Test
  void getItemsByOwnersMergeChainsEverySortKeyIncludingTheTiebreaker() {
    // Every row carries the SAME fileSize, and the two chunks INTERLEAVE on the
    // tiebreaker: a merge honouring only the FIRST sort key leaves the ties in
    // whatever order the chunks were read in (10, 40, 20, 30 here), and the page
    // sliced out of it repeats a row while skipping another — exactly the
    // row-skipping a total ordering exists to prevent. This is the one place the
    // requested ordering is re-implemented in Java.
    List<Long> ownerIdentityIds = ownerIds(950);
    List<Long> firstChunk = ownerIdentityIds.subList(0, CleanupCampaignStorage.MAX_IN_CLAUSE_SIZE);
    List<Long> secondChunk = ownerIdentityIds.subList(CleanupCampaignStorage.MAX_IN_CLAUSE_SIZE, ownerIdentityIds.size());
    Pageable pageable = PageRequest.of(0, 4, Sort.by(Sort.Direction.DESC, "fileSize").and(Sort.by(Sort.Direction.ASC, "id")));
    Pageable chunkPageable = PageRequest.of(0, 4, pageable.getSort());
    when(itemDAO.findByOwnersAndSearch(CAMPAIGN_ID, firstChunk, null, chunkPageable))
                                                                                    .thenReturn(new PageImpl<>(List.of(itemEntity("/a.pdf",
                                                                                                                                 500,
                                                                                                                                 10L),
                                                                                                                      itemEntity("/d.pdf",
                                                                                                                                 500,
                                                                                                                                 40L)),
                                                                                                               chunkPageable,
                                                                                                               2L));
    when(itemDAO.findByOwnersAndSearch(CAMPAIGN_ID, secondChunk, null, chunkPageable))
                                                                                     .thenReturn(new PageImpl<>(List.of(itemEntity("/b.pdf",
                                                                                                                                   500,
                                                                                                                                   20L),
                                                                                                                        itemEntity("/c.pdf",
                                                                                                                                   500,
                                                                                                                                   30L)),
                                                                                                                chunkPageable,
                                                                                                                2L));

    Page<CleanupCampaignItem> page = storage.getItemsByOwners(CAMPAIGN_ID, ownerIdentityIds, null, pageable);

    assertEquals(List.of(10L, 20L, 30L, 40L),
                 page.getContent().stream().map(CleanupCampaignItem::getId).toList(),
                 "Tied primary keys must be ordered by the requested tiebreaker, across chunks");
  }

  @Test
  void getItemsByOwnersMergeFallsBackToTheIdWhenNoRequestedKeyIsUsable() {
    // The REST allowlist makes this unreachable today, but an ordering left with
    // no usable key must still be TOTAL: the fallback is the primary key, the
    // only column whose uniqueness the schema enforces. The two rows are ordered
    // the OTHER way round by their paths, so a fallback on the (nullable, only
    // in-practice-unique) path cannot produce the expected order
    List<Long> ownerIdentityIds = ownerIds(950);
    List<Long> firstChunk = ownerIdentityIds.subList(0, CleanupCampaignStorage.MAX_IN_CLAUSE_SIZE);
    List<Long> secondChunk = ownerIdentityIds.subList(CleanupCampaignStorage.MAX_IN_CLAUSE_SIZE, ownerIdentityIds.size());
    Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "ownerFullName"));
    Pageable chunkPageable = PageRequest.of(0, 2, pageable.getSort());
    when(itemDAO.findByOwnersAndSearch(CAMPAIGN_ID, firstChunk, null, chunkPageable))
                                                                                    .thenReturn(new PageImpl<>(List.of(itemEntity("/a.pdf",
                                                                                                                                 500,
                                                                                                                                 9L)),
                                                                                                               chunkPageable,
                                                                                                               1L));
    when(itemDAO.findByOwnersAndSearch(CAMPAIGN_ID, secondChunk, null, chunkPageable))
                                                                                     .thenReturn(new PageImpl<>(List.of(itemEntity("/z.pdf",
                                                                                                                                   900,
                                                                                                                                   4L)),
                                                                                                                chunkPageable,
                                                                                                                1L));

    Page<CleanupCampaignItem> page = storage.getItemsByOwners(CAMPAIGN_ID, ownerIdentityIds, null, pageable);

    assertEquals(List.of(4L, 9L),
                 page.getContent().stream().map(CleanupCampaignItem::getId).toList(),
                 "With no usable requested key, the merge orders on the id");
  }

  @Test
  void getItemsByOwnersAppliesTheSearchToEveryChunkNotOnlyTheSingleChunkFastPath() {
    // The escaped pattern must reach EVERY chunk: dropping it on the multi-chunk
    // branch would silently stop filtering for exactly the users chunking was
    // written for (those managing hundreds of spaces)
    List<Long> ownerIdentityIds = ownerIds(950);
    Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "fileSize").and(Sort.by(Sort.Direction.ASC, "id")));
    Pageable chunkPageable = PageRequest.of(0, 2, pageable.getSort());
    // Answered for ANY pattern (leniently), so a chunk queried WITHOUT the term
    // still gets a page back and the verify below is what reports the drift
    org.mockito.Mockito.lenient()
                       .when(itemDAO.findByOwnersAndSearch(eq(CAMPAIGN_ID), anyList(), any(), eq(chunkPageable)))
                       .thenReturn(new PageImpl<>(List.of(), chunkPageable, 0L));

    storage.getItemsByOwners(CAMPAIGN_ID, ownerIdentityIds, " Q1 ", pageable);

    verify(itemDAO, times(2)).findByOwnersAndSearch(eq(CAMPAIGN_ID), anyList(), eq("%q1%"), eq(chunkPageable));
    verify(itemDAO, never()).findByOwnersAndSearch(eq(CAMPAIGN_ID), anyList(), eq((String) null), any());
  }

  /**
   * Two chunk pages whose rows INTERLEAVE once merged by fileSize descending
   * (500 and 100 in the first chunk, 300 and 200 in the second): a merge that
   * just concatenated the chunks, or sorted them the other way round, cannot
   * produce the expected slices.
   */
  private void stubChunkPages(List<Long> ownerIdentityIds, Pageable chunkPageable) {
    List<Long> firstChunk = ownerIdentityIds.subList(0, CleanupCampaignStorage.MAX_IN_CLAUSE_SIZE);
    List<Long> secondChunk = ownerIdentityIds.subList(CleanupCampaignStorage.MAX_IN_CLAUSE_SIZE, ownerIdentityIds.size());
    when(itemDAO.findByOwnersAndSearch(CAMPAIGN_ID, firstChunk, null, chunkPageable))
                                                                                    .thenReturn(new PageImpl<>(List.of(itemEntity("/a-500.pdf",
                                                                                                                                 500),
                                                                                                                      itemEntity("/d-100.pdf",
                                                                                                                                 100)),
                                                                                                               chunkPageable,
                                                                                                               5L));

    when(itemDAO.findByOwnersAndSearch(CAMPAIGN_ID, secondChunk, null, chunkPageable))
                                                                                     .thenReturn(new PageImpl<>(List.of(itemEntity("/b-300.pdf",
                                                                                                                                   300),
                                                                                                                        itemEntity("/c-200.pdf",
                                                                                                                                    200)),
                                                                                                                chunkPageable,
                                                                                                                6L));
  }

  @Test
  void getItemsByStateAfterIdAsksTheKeysetQueryForTheIdsPastTheLastOneSeen() {
    when(itemDAO.findByCampaignIdAndStateAndIdGreaterThanOrderByIdAsc(eq(CAMPAIGN_ID),
                                                                     eq(CANDIDATE_STATE),
                                                                     anyLong(),
                                                                     any())).thenReturn(List.of(itemEntity(USERS_ROOT_PATH,
                                                                                                           2048,
                                                                                                           42L)));

    List<CleanupCampaignItem> items = storage.getItemsByStateAfterId(CAMPAIGN_ID, CleanupItemState.CANDIDATE, 41L, 200);

    assertEquals(1, items.size());
    assertEquals(42L, items.get(0).getId());
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(itemDAO).findByCampaignIdAndStateAndIdGreaterThanOrderByIdAsc(eq(CAMPAIGN_ID),
                                                                        eq(CANDIDATE_STATE),
                                                                        eq(41L),
                                                                        pageableCaptor.capture());
    // The batch size is the page SIZE, and the page index is always 0: the
    // position comes from the id, never from an offset
    assertEquals(0, pageableCaptor.getValue().getPageNumber());
    assertEquals(200, pageableCaptor.getValue().getPageSize());
  }

  @Test
  void getRetryableFailuresPassesTheAllowlistTheBoundAndTheKeysetPosition() {
    when(itemDAO.findRetryableFailures(eq(CAMPAIGN_ID), eq(SKIPPED_STATE), anyCollection(), anyLong(), anyLong(), any()))
                                                                                                                        .thenReturn(List.of(itemEntity(USERS_ROOT_PATH,
                                                                                                                                                       2048,
                                                                                                                                                       55L)));

    List<CleanupCampaignItem> items = storage.getRetryableFailures(CAMPAIGN_ID,
                                                                  Set.of("cleanup.deleteError"),
                                                                  3L,
                                                                  54L,
                                                                  1000);

    assertEquals(1, items.size());
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(itemDAO).findRetryableFailures(eq(CAMPAIGN_ID),
                                         eq(SKIPPED_STATE),
                                         eq(Set.of("cleanup.deleteError")),
                                         eq(3L),
                                         eq(54L),
                                         pageableCaptor.capture());
    assertEquals(Sort.by("id"), pageableCaptor.getValue().getSort(), "The keyset walk must be ordered by id");
  }

  @Test
  void getRetryableFailuresShortCircuitsOnAnEmptyAllowlist() {
    // An empty IN list is invalid SQL on several databases, and the answer is
    // known without asking: nothing is retryable
    assertTrue(storage.getRetryableFailures(CAMPAIGN_ID, Set.of(), 3L, 0L, 1000).isEmpty());
    assertTrue(storage.getRetryableFailures(CAMPAIGN_ID, null, 3L, 0L, 1000).isEmpty());

    verifyNoInteractions(itemDAO);
  }

  @Test
  void countFailuresByReasonFoldsTheGroupedAggregateRows() {
    when(itemDAO.countFailuresByReason(CAMPAIGN_ID, SKIPPED_STATE))
                                                                   .thenReturn(List.of(new Object[] { "cleanup.deleteError",
                                                                                                      12L },
                                                                                       new Object[] {
                                                                                                      "cleanup.referentialIntegrity",
                                                                                                      3L }));

    List<CleanupFailureGroup> groups = storage.countFailuresByReason(CAMPAIGN_ID);

    assertEquals(2, groups.size());
    assertEquals("cleanup.deleteError", groups.get(0).getReason());
    assertEquals(12L, groups.get(0).getCount());
    assertEquals(3L, groups.get(1).getCount());
    // The retryable rule belongs to the Service: the Storage must never decide it
    assertFalse(groups.get(0).isRetryable(), "The Storage leaves the retryable flag to the Service");
    assertFalse(groups.get(1).isRetryable());
  }

  @Test
  void countFailuresByReasonReturnsNoGroupWithoutAnyFailedItem() {
    when(itemDAO.countFailuresByReason(CAMPAIGN_ID, SKIPPED_STATE)).thenReturn(List.of());

    assertTrue(storage.countFailuresByReason(CAMPAIGN_ID).isEmpty());
  }

  private List<Long> ownerIds(int total) {
    return java.util.stream.LongStream.rangeClosed(1, total).boxed().toList();
  }

  private CleanupCampaignItemEntity itemEntity(String path, long fileSize) {
    return itemEntity(path, fileSize, fileSize);
  }

  private CleanupCampaignItemEntity itemEntity(String path, long fileSize, long id) {
    CleanupCampaignItemEntity entity = new CleanupCampaignItemEntity();
    entity.setId(id);
    entity.setCampaignId(CAMPAIGN_ID);
    entity.setNodeUuid("uuid" + path);
    entity.setPath(path);
    entity.setFileSize(fileSize);
    entity.setVersionsSize(0);
    entity.setAction(CleanupAction.DELETE.name());
    entity.setState(CANDIDATE_STATE);
    return entity;
  }

  @Test
  void searchPatternEscapesWildcardsAndIgnoresBlankTerms() {
    assertEquals("%invoice%", CleanupCampaignStorage.searchPattern("Invoice"));
    assertEquals("%100|%%", CleanupCampaignStorage.searchPattern("100%"));
    assertEquals("%a|_b%", CleanupCampaignStorage.searchPattern("a_b"));
    assertEquals("%a||b%", CleanupCampaignStorage.searchPattern("a|b"));
    assertNull(CleanupCampaignStorage.searchPattern(null));
    assertNull(CleanupCampaignStorage.searchPattern(""));
    assertNull(CleanupCampaignStorage.searchPattern("   "));
  }

  @Test
  void comparisonBucketsFoldTheAggregateRowsWithoutLoadingAnyCandidateSet() {
    // The three buckets are computed by the database: NO node-uuid map is ever
    // built in memory (that was the unbounded-memory risk of the old diff)
    when(itemDAO.aggregateItemsSharedWithCampaign(CAMPAIGN_ID, OTHER_CAMPAIGN_ID))
                                                                                  .thenReturn(List.<Object[]> of(new Object[] {
                                                                                    3L,
                                                                                    300L }));
    when(itemDAO.aggregateItemsAbsentFromCampaign(CAMPAIGN_ID, OTHER_CAMPAIGN_ID))
                                                                                  .thenReturn(List.<Object[]> of(new Object[] {
                                                                                    1L,
                                                                                    100L }));
    when(itemDAO.aggregateItemsAbsentFromCampaign(OTHER_CAMPAIGN_ID, CAMPAIGN_ID))
                                                                                  .thenReturn(List.<Object[]> of(new Object[] {
                                                                                    2L,
                                                                                    200L }));

    assertEquals(new CleanupComparisonBucket(3L, 300L), storage.getPersistingItems(CAMPAIGN_ID, OTHER_CAMPAIGN_ID));
    assertEquals(new CleanupComparisonBucket(1L, 100L), storage.getNewItems(CAMPAIGN_ID, OTHER_CAMPAIGN_ID));
    // 'gone' reuses the same absence query with the campaigns SWAPPED, so its
    // bytes come from the other campaign's rows
    assertEquals(new CleanupComparisonBucket(2L, 200L), storage.getGoneItems(CAMPAIGN_ID, OTHER_CAMPAIGN_ID));
  }

  @Test
  void comparisonBucketsFoldEmptyAndNullAggregatesAsZero() {
    when(itemDAO.aggregateItemsSharedWithCampaign(CAMPAIGN_ID, OTHER_CAMPAIGN_ID)).thenReturn(List.of());
    when(itemDAO.aggregateItemsAbsentFromCampaign(CAMPAIGN_ID, OTHER_CAMPAIGN_ID))
                                                                                  .thenReturn(java.util.Collections.singletonList(new Object[] {
                                                                                    null, null }));

    assertEquals(new CleanupComparisonBucket(0L, 0L), storage.getPersistingItems(CAMPAIGN_ID, OTHER_CAMPAIGN_ID));
    assertEquals(new CleanupComparisonBucket(0L, 0L), storage.getNewItems(CAMPAIGN_ID, OTHER_CAMPAIGN_ID));
  }

  @Test
  void countsAndSumsDelegateWithStateNames() {
    when(itemDAO.countByCampaignIdAndState(CAMPAIGN_ID, CANDIDATE_STATE)).thenReturn(4L);
    assertEquals(4L, storage.countItemsByState(CAMPAIGN_ID, CleanupItemState.CANDIDATE));

    when(itemDAO.sumReclaimableBytesByState(CAMPAIGN_ID, EXEMPTED_STATE)).thenReturn(2048L);
    assertEquals(2048L, storage.sumReclaimableBytesByState(CAMPAIGN_ID, CleanupItemState.EXEMPTED));

    when(itemDAO.countByCampaignIdAndOwnerIdentityIdInAndState(CAMPAIGN_ID, List.of(7L), PURGED_STATE)).thenReturn(2L);
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
    campaign.getParams().setExcludedPaths(List.of(USERS_ROOT_PATH, "/Groups/spaces/admin"));

    CleanupCampaign saved = storage.saveCampaign(campaign);
    assertEquals(List.of(USERS_ROOT_PATH, "/Groups/spaces/admin"), saved.getParams().getExcludedPaths());

    campaign.getParams().setExcludedPaths(null);
    saved = storage.saveCampaign(campaign);
    assertTrue(saved.getParams().getExcludedPaths().isEmpty(), "A null persisted JSON must map to an empty list");
  }

  private CleanupCampaign campaign() {
    CleanupCampaign campaign = new CleanupCampaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setName("Spring cleanup");
    campaign.setState(CleanupCampaignState.SIMULATED);
    campaign.setParams(new CleanupParams(6, 1048576L, 7, 5, List.of(USERS_ROOT_PATH), null));
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
    item.setLastModifiedDate(3000L);
    item.setCreatedDate(2000L);
    item.setDecidedBy("john");
    item.setDecidedAt(0);
    item.setPurgedAt(7000L);
    item.setReclaimedBytes(128);
    item.setFailureReason("some.failure");
    item.setFailureDetail(FAILURE_DETAIL);
    item.setAttemptCount(2L);
    return item;
  }

  private CleanupCandidate candidate(String nodeUuid, String path) {
    return new CleanupCandidate(nodeUuid, path, 7L, 2048, 1024, CleanupAction.DELETE, 100, 200);
  }

}
