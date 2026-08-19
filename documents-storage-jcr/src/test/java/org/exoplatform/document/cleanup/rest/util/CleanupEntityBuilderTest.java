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
package org.exoplatform.document.cleanup.rest.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupBulkResult;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupComparison;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupUserSummary;
import org.exoplatform.document.cleanup.rest.model.CampaignItemRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignRestEntity;
import org.exoplatform.document.cleanup.rest.model.KeepItemsResultRestEntity;
import org.exoplatform.document.cleanup.rest.model.MyItemsSummaryRestEntity;
import org.exoplatform.document.cleanup.rest.model.PagedResult;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Model to REST DTO mapping tests: field mapping, zero-date to null conversion,
 * archive availability, owner resolution through {@link IdentityManager} and
 * partial-override extraction.
 */
@ExtendWith(MockitoExtension.class)
class CleanupEntityBuilderTest {

  private static final long OWNER_IDENTITY_ID = 55L;

  private static final long ONE_HOUR_MILLIS   = 3600000L;

  @Mock
  private IdentityManager   identityManager;

  @Mock
  private Identity          identity;

  @Mock
  private Profile           profile;

  @Test
  void buildCampaignMapsFieldsAndParams() {
    CleanupCampaign campaign = campaign();

    CampaignRestEntity entity = CleanupEntityBuilder.build(campaign);

    assertEquals(3L, entity.getId());
    assertEquals("Spring cleanup", entity.getName());
    assertEquals(CleanupCampaignState.SIMULATED.name(), entity.getState());
    assertEquals(6, entity.getPeriodMonths());
    assertEquals(1048576L, entity.getMinFileSizeBytes());
    assertEquals(7, entity.getGraceDays());
    assertEquals(5, entity.getMaxVersionsPerFile());
    assertEquals(List.of("/Users/root"), entity.getExcludedPaths());
    assertEquals(1000L, entity.getStartedDate());
    assertNull(entity.getPublishedDate(), "A zero date must map to null, not to the epoch");
    assertNull(entity.getLockDate());
    assertNull(entity.getCompletedDate());
    assertEquals(100, entity.getTotalCount());
    assertEquals(40, entity.getProcessedCount());
    assertEquals(60L, entity.getEtaSeconds());
    assertEquals(25, entity.getCandidateCount());
    assertEquals(2048, entity.getReclaimableBytes());
    assertEquals(512, entity.getReclaimedBytes());
  }

  @Test
  void buildCampaignToleratesMissingParams() {
    CleanupCampaign campaign = campaign();
    campaign.setParams(null);

    CampaignRestEntity entity = CleanupEntityBuilder.build(campaign);

    assertNull(entity.getPeriodMonths());
    assertNull(entity.getMinFileSizeBytes());
    assertNull(entity.getExcludedPaths());
  }

  @Test
  void archiveAvailableWhenArchiveStoredOrItemsRetained() {
    CleanupCampaign campaign = campaign();

    campaign.setArchiveFileId(99L);
    campaign.setItemsRetained(false);
    assertTrue(CleanupEntityBuilder.build(campaign).isArchiveAvailable(), "Archived report must be downloadable");

    campaign.setArchiveFileId(null);
    campaign.setItemsRetained(true);
    assertTrue(CleanupEntityBuilder.build(campaign).isArchiveAvailable(),
               "Live report must be downloadable while items are retained");

    campaign.setArchiveFileId(null);
    campaign.setItemsRetained(false);
    assertFalse(CleanupEntityBuilder.build(campaign).isArchiveAvailable(),
                "No report without an archive nor retained items");
  }

  @Test
  void executableIsComputedFromTheServerClockNeverLeftToTheClient() {
    CleanupCampaign campaign = campaign();

    // PUBLISHED with a deadline still in the future: the server would refuse the
    // execution with cleanup.graceNotElapsed, so the UI must find executable=false
    campaign.setState(CleanupCampaignState.PUBLISHED);
    campaign.setLockDate(System.currentTimeMillis() + ONE_HOUR_MILLIS);
    assertFalse(CleanupEntityBuilder.build(campaign).isExecutable(),
                "The grace period is still running: Execute must be refused");

    // PUBLISHED past its deadline: the locking cron may not have run yet, but the
    // service DOES accept the execution (it locks the campaign itself)
    campaign.setLockDate(System.currentTimeMillis() - ONE_HOUR_MILLIS);
    assertTrue(CleanupEntityBuilder.build(campaign).isExecutable(),
               "Past the grace deadline the execution is accepted, even while still PUBLISHED");

    // LOCKED: executable whatever the deadline says
    campaign.setState(CleanupCampaignState.LOCKED);
    campaign.setLockDate(System.currentTimeMillis() + ONE_HOUR_MILLIS);
    assertTrue(CleanupEntityBuilder.build(campaign).isExecutable(), "A LOCKED campaign is always executable");

    // Anything before publication is never executable
    campaign.setState(CleanupCampaignState.SIMULATED);
    campaign.setLockDate(0);
    assertFalse(CleanupEntityBuilder.build(campaign).isExecutable());
    campaign.setState(CleanupCampaignState.COMPLETED);
    campaign.setLockDate(System.currentTimeMillis() - ONE_HOUR_MILLIS);
    assertFalse(CleanupEntityBuilder.build(campaign).isExecutable(), "A completed campaign is not executable anymore");
  }

  @Test
  void remainingMillisIsAServerComputedDurationFlooredAtZero() {
    CleanupCampaign campaign = campaign();

    // A DURATION, so the client counts it down instead of comparing the deadline
    // to its own clock
    campaign.setLockDate(System.currentTimeMillis() + ONE_HOUR_MILLIS);
    long remainingMillis = CleanupEntityBuilder.build(campaign).getRemainingMillis();
    assertTrue(remainingMillis > ONE_HOUR_MILLIS - 5000 && remainingMillis <= ONE_HOUR_MILLIS,
               "An hour of grace left must be reported as ~an hour, got " + remainingMillis);

    // Elapsed, and never-set, both mean zero — never a negative countdown
    campaign.setLockDate(System.currentTimeMillis() - ONE_HOUR_MILLIS);
    assertEquals(0, CleanupEntityBuilder.build(campaign).getRemainingMillis());
    campaign.setLockDate(0);
    assertEquals(0, CleanupEntityBuilder.build(campaign).getRemainingMillis());
  }

  @Test
  void buildItemResolvesUserOwner() {
    when(identityManager.getIdentity(OWNER_IDENTITY_ID)).thenReturn(identity);
    when(identity.isSpace()).thenReturn(false);
    when(identity.getRemoteId()).thenReturn("john");
    when(identity.getProfile()).thenReturn(profile);
    when(profile.getFullName()).thenReturn("John Smith");

    CampaignItemRestEntity entity = CleanupEntityBuilder.build(item(), identityManager);

    assertEquals(9L, entity.getId());
    assertEquals(3L, entity.getCampaignId());
    assertEquals("uuid-9", entity.getNodeUuid());
    assertEquals("/Users/j___/john/Private/report.pdf", entity.getPath());
    assertEquals("report.pdf", entity.getName(), "Item name must be the last path segment");
    assertEquals(OWNER_IDENTITY_ID, entity.getOwnerIdentityId());
    assertEquals("user", entity.getOwnerType());
    assertEquals("john", entity.getOwnerRemoteId());
    assertEquals("John Smith", entity.getOwnerFullName());
    assertEquals(2048, entity.getFileSize());
    assertEquals(4096, entity.getVersionsSize());
    assertEquals(CleanupAction.DELETE.name(), entity.getAction());
    assertEquals(CleanupItemState.EXEMPTED.name(), entity.getState());
    assertEquals(5000L, entity.getComputedAt());
    assertEquals("john", entity.getDecidedBy());
    assertEquals(6000L, entity.getDecidedAt());
    assertNull(entity.getPurgedAt(), "A zero purge date must map to null");
    assertEquals(128, entity.getReclaimedBytes());
    assertEquals("some.failure", entity.getFailureReason());
  }

  @Test
  void buildItemResolvesSpaceOwner() {
    when(identityManager.getIdentity(OWNER_IDENTITY_ID)).thenReturn(identity);
    when(identity.isSpace()).thenReturn(true);
    when(identity.getRemoteId()).thenReturn("marketing");
    when(identity.getProfile()).thenReturn(profile);
    when(profile.getFullName()).thenReturn("Marketing Team");

    CampaignItemRestEntity entity = CleanupEntityBuilder.build(item(), identityManager);

    assertEquals("space", entity.getOwnerType());
    assertEquals("marketing", entity.getOwnerRemoteId());
    assertEquals("Marketing Team", entity.getOwnerFullName());
  }

  @Test
  void buildItemFallsBackToRemoteIdWithoutFullName() {
    when(identityManager.getIdentity(OWNER_IDENTITY_ID)).thenReturn(identity);
    when(identity.getRemoteId()).thenReturn("john");
    when(identity.getProfile()).thenReturn(profile);
    when(profile.getFullName()).thenReturn(" ");

    assertEquals("john", CleanupEntityBuilder.build(item(), identityManager).getOwnerFullName());

    when(identity.getProfile()).thenReturn(null);
    assertEquals("john", CleanupEntityBuilder.build(item(), identityManager).getOwnerFullName());
  }

  @Test
  void buildItemToleratesUnknownOwner() {
    when(identityManager.getIdentity(OWNER_IDENTITY_ID)).thenReturn(null);

    CampaignItemRestEntity entity = CleanupEntityBuilder.build(item(), identityManager);

    assertEquals(OWNER_IDENTITY_ID, entity.getOwnerIdentityId());
    assertNull(entity.getOwnerType());
    assertNull(entity.getOwnerRemoteId());
    assertNull(entity.getOwnerFullName());
  }

  @Test
  void buildPageMapsPagingMetadata() {
    when(identityManager.getIdentity(OWNER_IDENTITY_ID)).thenReturn(null);
    PageImpl<CleanupCampaignItem> page = new PageImpl<>(List.of(item()), PageRequest.of(2, 1), 7);

    PagedResult<CampaignItemRestEntity> result = CleanupEntityBuilder.build(page, identityManager);

    assertEquals(1, result.getItems().size());
    assertEquals(9L, result.getItems().get(0).getId());
    assertEquals(2, result.getPage());
    assertEquals(1, result.getSize());
    assertEquals(7, result.getTotalItems());
  }

  @Test
  void buildComparisonMapsAllCounters() {
    CleanupComparison comparison = new CleanupComparison(1L, 2L, 3, 4, 5, 30, 40, 50);

    var entity = CleanupEntityBuilder.build(comparison);

    assertEquals(1L, entity.getBaseCampaignId());
    assertEquals(2L, entity.getOtherCampaignId());
    assertEquals(3, entity.getNewCount());
    assertEquals(4, entity.getGoneCount());
    assertEquals(5, entity.getPersistingCount());
    assertEquals(30, entity.getNewBytes());
    assertEquals(40, entity.getGoneBytes());
    assertEquals(50, entity.getPersistingBytes());
  }

  @Test
  void buildUserSummaryMapsOutcomeOnlyWhenPresent() {
    CleanupUserSummary summary = new CleanupUserSummary();
    summary.setCampaignId(3L);
    summary.setState(CleanupCampaignState.PUBLISHED);
    summary.setDeadline(7000L);
    summary.setCandidateCount(4);
    summary.setKeptCount(1);
    summary.setCandidateBytes(400);
    summary.setKeptBytes(100);

    MyItemsSummaryRestEntity entity = CleanupEntityBuilder.build(summary);
    assertEquals(3L, entity.getCampaignId());
    assertEquals(CleanupCampaignState.PUBLISHED.name(), entity.getState());
    assertEquals(7000L, entity.getDeadline());
    // A deadline long past (epoch + 7 s) leaves no review time at all
    assertEquals(0, entity.getRemainingMillis());
    assertEquals(4, entity.getCandidateCount());
    assertEquals(1, entity.getKeptCount());
    assertEquals(400, entity.getCandidateBytes());
    assertEquals(100, entity.getKeptBytes());
    assertNull(entity.getOutcome(), "No outcome before the campaign completes");

    CleanupUserSummary.CleanupUserOutcome outcome = new CleanupUserSummary.CleanupUserOutcome();
    outcome.setDeletedCount(2);
    outcome.setFreedBytes(300);
    outcome.setKeptCount(1);
    summary.setOutcome(outcome);
    summary.setDeadline(0);

    entity = CleanupEntityBuilder.build(summary);
    assertNull(entity.getDeadline(), "A zero deadline must map to null");
    assertEquals(0, entity.getRemainingMillis(), "No deadline means no remaining review time");
    assertNotNull(entity.getOutcome());
    assertEquals(2, entity.getOutcome().getDeletedCount());
    assertEquals(300, entity.getOutcome().getFreedBytes());
    assertEquals(1, entity.getOutcome().getKeptCount());
  }

  @Test
  void userSummaryCarriesTheServerComputedReviewTimeLeft() {
    // The review window is the SERVER's: shipping it as a remaining duration is
    // what stops a skewed browser from closing the review while the service would
    // still accept a keep (or from offering actions it already refuses)
    CleanupUserSummary summary = new CleanupUserSummary();
    summary.setState(CleanupCampaignState.PUBLISHED);
    summary.setDeadline(System.currentTimeMillis() + ONE_HOUR_MILLIS);

    long remainingMillis = CleanupEntityBuilder.build(summary).getRemainingMillis();

    assertTrue(remainingMillis > ONE_HOUR_MILLIS - 5000 && remainingMillis <= ONE_HOUR_MILLIS,
               "An hour of review left must be reported as ~an hour, got " + remainingMillis);
  }

  @Test
  void toParamOverridesNeverCarriesBatchSize() {
    CampaignRestEntity body = new CampaignRestEntity();
    body.setPeriodMonths(12);
    body.setMinFileSizeBytes(2048L);
    body.setGraceDays(10);
    body.setMaxVersionsPerFile(3);
    body.setExcludedPaths(List.of("/Trash"));

    CleanupParams overrides = CleanupEntityBuilder.toParamOverrides(body);

    assertEquals(12, overrides.getPeriodMonths());
    assertEquals(2048L, overrides.getMinFileSizeBytes());
    assertEquals(10, overrides.getGraceDays());
    assertEquals(3, overrides.getMaxVersionsPerFile());
    assertEquals(List.of("/Trash"), overrides.getExcludedPaths());
    assertNull(overrides.getBatchSize(), "The batch size is a platform setting, never a client override");
  }

  private CleanupCampaign campaign() {
    CleanupCampaign campaign = new CleanupCampaign();
    campaign.setId(3L);
    campaign.setName("Spring cleanup");
    campaign.setState(CleanupCampaignState.SIMULATED);
    campaign.setParams(new CleanupParams(6, 1048576L, 7, 5, List.of("/Users/root"), 200));
    campaign.setStartedDate(1000L);
    campaign.setPublishedDate(0);
    campaign.setLockDate(0);
    campaign.setCompletedDate(0);
    campaign.setTotalCount(100);
    campaign.setProcessedCount(40);
    campaign.setEtaSeconds(60);
    campaign.setCandidateCount(25);
    campaign.setReclaimableBytes(2048);
    campaign.setReclaimedBytes(512);
    return campaign;
  }

  @Test
  void buildsBulkKeepOutcomesWithEveryFailure() {
    CleanupBulkResult result = new CleanupBulkResult();
    result.setSucceeded(2);
    result.addFailure(7L, "cleanup.itemNotFound");
    result.addFailure(8L, "cleanup.reviewClosed");

    KeepItemsResultRestEntity entity = CleanupEntityBuilder.build(result);

    assertEquals(2, entity.getSucceeded());
    assertEquals(2, entity.getFailures().size());
    assertEquals(7L, entity.getFailures().get(0).getItemId());
    assertEquals("cleanup.itemNotFound", entity.getFailures().get(0).getReason());
    assertEquals(8L, entity.getFailures().get(1).getItemId());
    assertEquals("cleanup.reviewClosed", entity.getFailures().get(1).getReason());
  }

  @Test
  void buildsBulkKeepOutcomesWithAnEmptyFailureListWhenEverythingSucceeded() {
    CleanupBulkResult result = new CleanupBulkResult();
    result.setSucceeded(3);

    KeepItemsResultRestEntity entity = CleanupEntityBuilder.build(result);

    assertEquals(3, entity.getSucceeded());
    assertNotNull(entity.getFailures(), "The failures list must never be null in the response body");
    assertTrue(entity.getFailures().isEmpty());
  }

  private CleanupCampaignItem item() {
    CleanupCampaignItem item = new CleanupCampaignItem();
    item.setId(9L);
    item.setCampaignId(3L);
    item.setNodeUuid("uuid-9");
    item.setPath("/Users/j___/john/Private/report.pdf");
    item.setOwnerIdentityId(OWNER_IDENTITY_ID);
    item.setFileSize(2048);
    item.setVersionsSize(4096);
    item.setAction(CleanupAction.DELETE);
    item.setState(CleanupItemState.EXEMPTED);
    item.setComputedAt(5000L);
    item.setDecidedBy("john");
    item.setDecidedAt(6000L);
    item.setPurgedAt(0);
    item.setReclaimedBytes(128);
    item.setFailureReason("some.failure");
    return item;
  }

}
