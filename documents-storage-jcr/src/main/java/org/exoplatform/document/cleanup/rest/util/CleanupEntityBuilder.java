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
package org.exoplatform.document.cleanup.rest.util;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;

import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.model.CleanupBulkResult;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupComparison;
import org.exoplatform.document.cleanup.model.CleanupFailureGroup;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupScanUnit;
import org.exoplatform.document.cleanup.model.CleanupScanUnitProgress;
import org.exoplatform.document.cleanup.model.CleanupUserSummary;
import org.exoplatform.document.cleanup.rest.model.CampaignComparisonRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignFailureGroupRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignItemRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignScanUnitProgressRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignScanUnitRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignRestEntity;
import org.exoplatform.document.cleanup.rest.model.KeepItemsResultRestEntity;
import org.exoplatform.document.cleanup.rest.model.MyItemsSummaryRestEntity;
import org.exoplatform.document.cleanup.rest.model.PagedResult;
import org.exoplatform.document.cleanup.util.CleanupIdentityUtil;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Domain model to REST DTO mapping. No business logic.
 */
public class CleanupEntityBuilder {

  private CleanupEntityBuilder() {
    // static utility
  }

  public static CampaignRestEntity build(CleanupCampaign campaign) {
    CampaignRestEntity entity = new CampaignRestEntity();
    entity.setId(campaign.getId());
    entity.setName(campaign.getName());
    entity.setState(campaign.getState().name());
    CleanupParams params = campaign.getParams();
    if (params != null) {
      entity.setPeriodMonths(params.getPeriodMonths());
      entity.setMinFileSizeBytes(params.getMinFileSizeBytes());
      entity.setGraceDays(params.getGraceDays());
      entity.setMaxVersionsPerFile(params.getMaxVersionsPerFile());
      entity.setExcludedPaths(params.getExcludedPaths());
    }
    entity.setStartedDate(toNullable(campaign.getStartedDate()));
    entity.setPublishedDate(toNullable(campaign.getPublishedDate()));
    entity.setLockDate(toNullable(campaign.getLockDate()));
    entity.setCompletedDate(toNullable(campaign.getCompletedDate()));
    entity.setTotalCount(campaign.getTotalCount());
    entity.setProcessedCount(campaign.getProcessedCount());
    entity.setEtaSeconds(toNullable(campaign.getEtaSeconds()));
    entity.setCandidateCount(campaign.getCandidateCount());
    entity.setReclaimableBytes(campaign.getReclaimableBytes());
    entity.setReclaimedBytes(campaign.getReclaimedBytes());
    // A report is downloadable while the item rows are retained (CSV built
    // live) or once archived to the FileService (CSV served from the archive)
    entity.setArchiveAvailable(campaign.getArchiveFileId() != null || campaign.isItemsRetained());
    // The Execute gate and the grace countdown are computed HERE, against the
    // server clock, and shipped as a boolean + a duration: the client never
    // compares a server epoch to its own (possibly skewed) clock
    long now = System.currentTimeMillis();
    boolean graceElapsed = campaign.getLockDate() > 0 && campaign.getLockDate() <= now;
    entity.setExecutable(campaign.getState() == CleanupCampaignState.LOCKED
                         || (campaign.getState() == CleanupCampaignState.PUBLISHED && graceElapsed));
    entity.setRemainingMillis(remainingMillis(campaign.getLockDate(), now));
    return entity;
  }

  /**
   * Milliseconds left before an epoch-millis deadline, floored at 0 — which also
   * covers 'no deadline set yet' (a campaign before publication).
   */
  private static long remainingMillis(long deadline, long now) {
    return deadline > now ? deadline - now : 0;
  }

  /**
   * Item DTO WITHOUT its failure detail — the safe default, see
   * {@link #build(CleanupCampaignItem, IdentityManager, boolean)}.
   *
   * @param item item to map
   * @param identityManager identity manager resolving the owner
   * @return the item DTO, its failureDetail left null
   */
  public static CampaignItemRestEntity build(CleanupCampaignItem item, IdentityManager identityManager) {
    return build(item, identityManager, false);
  }

  /**
   * Item DTO, its administrator-only failure DETAIL included only when asked for.
   * <p>
   * THE FLAG IS A SECURITY BOUNDARY, not a convenience: this very DTO is served
   * both to administrators ({@code GET {id}/items}) and to end users
   * ({@code GET published/my-items}, {@code @Secured("users")}). The failure
   * detail carries exception messages and stack frames that can name nodes and
   * paths OUTSIDE the calling user's visibility — a
   * {@code ReferentialIntegrityException} names the REFERENCING node, e.g. a
   * shortcut living in a space the user is not a member of. So the admin endpoint
   * passes true and the user one passes false, explicitly, and the default
   * overload above omits it.
   * <p>
   * {@code failureReason} stays on BOTH paths: it is now a bare, stable message
   * code, so it leaks nothing.
   *
   * @param item item to map
   * @param identityManager identity manager resolving the owner
   * @param includeFailureDetail true ONLY on an administrator-restricted endpoint
   * @return the item DTO
   */
  public static CampaignItemRestEntity build(CleanupCampaignItem item,
                                             IdentityManager identityManager,
                                             boolean includeFailureDetail) {
    CampaignItemRestEntity entity = new CampaignItemRestEntity();
    entity.setId(item.getId());
    entity.setCampaignId(item.getCampaignId());
    entity.setNodeUuid(item.getNodeUuid());
    entity.setPath(item.getPath());
    entity.setName(StringUtils.substringAfterLast(item.getPath(), "/"));
    entity.setOwnerIdentityId(item.getOwnerIdentityId());
    fillOwner(entity, item.getOwnerIdentityId(), identityManager);
    entity.setFileSize(item.getFileSize());
    entity.setVersionsSize(item.getVersionsSize());
    entity.setLastModifiedDate(toNullable(item.getLastModifiedDate()));
    entity.setCreatedDate(toNullable(item.getCreatedDate()));
    entity.setAction(item.getAction().name());
    entity.setState(item.getState().name());
    entity.setComputedAt(toNullable(item.getComputedAt()));
    entity.setDecidedBy(item.getDecidedBy());
    entity.setDecidedAt(toNullable(item.getDecidedAt()));
    entity.setPurgedAt(toNullable(item.getPurgedAt()));
    entity.setReclaimedBytes(item.getReclaimedBytes());
    entity.setFailureReason(item.getFailureReason());
    entity.setAttemptCount(item.getAttemptCount());
    if (includeFailureDetail) {
      entity.setFailureDetail(item.getFailureDetail());
    }
    return entity;
  }

  /**
   * Page of item DTOs WITHOUT their failure detail — the safe default, see
   * {@link #build(Page, IdentityManager, boolean)}.
   *
   * @param page page of items to map
   * @param identityManager identity manager resolving the owners
   * @return the paged DTOs, their failureDetail left null
   */
  public static PagedResult<CampaignItemRestEntity> build(Page<CleanupCampaignItem> page, IdentityManager identityManager) {
    return build(page, identityManager, false);
  }

  /**
   * Page of item DTOs, their administrator-only failure detail included only when
   * asked for — same security boundary as
   * {@link #build(CleanupCampaignItem, IdentityManager, boolean)}, read that
   * javadoc before touching this flag.
   *
   * @param page page of items to map
   * @param identityManager identity manager resolving the owners
   * @param includeFailureDetail true ONLY on an administrator-restricted endpoint
   * @return the paged DTOs
   */
  public static PagedResult<CampaignItemRestEntity> build(Page<CleanupCampaignItem> page,
                                                          IdentityManager identityManager,
                                                          boolean includeFailureDetail) {
    return new PagedResult<>(page.getContent()
                                 .stream()
                                 .map(item -> build(item, identityManager, includeFailureDetail))
                                 .toList(),
                             page.getNumber(),
                             page.getSize(),
                             page.getTotalElements());
  }

  /**
   * @param failureGroup grouped failure to map
   * @return the grouped-failure DTO
   */
  /**
   * @param progress per-unit breakdown of a dry run
   * @return its REST representation, with the in-flight units mapped along
   */
  public static CampaignScanUnitProgressRestEntity build(CleanupScanUnitProgress progress) {
    CampaignScanUnitProgressRestEntity entity = new CampaignScanUnitProgressRestEntity();
    entity.setUnitCount(progress.getUnitCount());
    entity.setPendingCount(progress.getPendingCount());
    entity.setRunningCount(progress.getRunningCount());
    entity.setDoneCount(progress.getDoneCount());
    entity.setFailedCount(progress.getFailedCount());
    entity.setSettledCount(progress.getSettledCount());
    entity.setMaxAttemptCount(progress.getMaxAttemptCount());
    entity.setScanComplete(progress.isScanComplete());
    entity.setInFlightUnits(progress.getInFlightUnits() == null ? List.of()
                                                               : progress.getInFlightUnits()
                                                                         .stream()
                                                                         .map(CleanupEntityBuilder::build)
                                                                         .toList());
    return entity;
  }

  /**
   * @param unit one scan unit
   * @return its REST representation. The unit id is deliberately NOT carried: the
   *         console has no endpoint taking one, and a subtree path is what an
   *         administrator can act on
   */
  public static CampaignScanUnitRestEntity build(CleanupScanUnit unit) {
    CampaignScanUnitRestEntity entity = new CampaignScanUnitRestEntity();
    entity.setUnitPath(unit.getUnitPath());
    entity.setLastScannedPath(unit.getLastScannedPath());
    entity.setScannedCount(unit.getScannedCount());
    entity.setTotalCount(unit.getTotalCount());
    entity.setAttemptCount(unit.getAttemptCount());
    return entity;
  }

  public static CampaignFailureGroupRestEntity build(CleanupFailureGroup failureGroup) {
    CampaignFailureGroupRestEntity entity = new CampaignFailureGroupRestEntity();
    entity.setReason(failureGroup.getReason());
    entity.setCount(failureGroup.getCount());
    entity.setRetryable(failureGroup.isRetryable());
    return entity;
  }

  public static CampaignComparisonRestEntity build(CleanupComparison comparison) {
    CampaignComparisonRestEntity entity = new CampaignComparisonRestEntity();
    entity.setBaseCampaignId(comparison.getBaseCampaignId());
    entity.setOtherCampaignId(comparison.getOtherCampaignId());
    entity.setNewCount(comparison.getNewCount());
    entity.setGoneCount(comparison.getGoneCount());
    entity.setPersistingCount(comparison.getPersistingCount());
    entity.setNewBytes(comparison.getNewBytes());
    entity.setGoneBytes(comparison.getGoneBytes());
    entity.setPersistingBytes(comparison.getPersistingBytes());
    return entity;
  }

  public static KeepItemsResultRestEntity build(CleanupBulkResult result) {
    KeepItemsResultRestEntity entity = new KeepItemsResultRestEntity();
    entity.setSucceeded(result.getSucceeded());
    entity.setFailures(result.getFailures().stream().map(failure -> {
      KeepItemsResultRestEntity.KeepItemFailureRestEntity failureEntity =
                                                                        new KeepItemsResultRestEntity.KeepItemFailureRestEntity();
      failureEntity.setItemId(failure.getItemId());
      failureEntity.setReason(failure.getReason());
      return failureEntity;
    }).toList());
    return entity;
  }

  public static MyItemsSummaryRestEntity build(CleanupUserSummary summary) {
    MyItemsSummaryRestEntity entity = new MyItemsSummaryRestEntity();
    entity.setCampaignId(summary.getCampaignId());
    entity.setState(summary.getState().name());
    entity.setDeadline(toNullable(summary.getDeadline()));
    // Same server-clock discipline as the campaign DTO: the review window is
    // shipped as a remaining DURATION the UI counts down, never as an instant
    // the browser has to compare to its own clock
    entity.setRemainingMillis(remainingMillis(summary.getDeadline(), System.currentTimeMillis()));
    entity.setCandidateCount(summary.getCandidateCount());
    entity.setKeptCount(summary.getKeptCount());
    entity.setCandidateBytes(summary.getCandidateBytes());
    entity.setKeptBytes(summary.getKeptBytes());
    if (summary.getOutcome() != null) {
      MyItemsSummaryRestEntity.OutcomeRestEntity outcome = new MyItemsSummaryRestEntity.OutcomeRestEntity();
      outcome.setDeletedCount(summary.getOutcome().getDeletedCount());
      outcome.setFreedBytes(summary.getOutcome().getFreedBytes());
      outcome.setKeptCount(summary.getOutcome().getKeptCount());
      entity.setOutcome(outcome);
    }
    return entity;
  }

  /**
   * @param entity creation request body
   * @return partial parameter overrides (null fields defaulted downstream)
   */
  public static CleanupParams toParamOverrides(CampaignRestEntity entity) {
    return new CleanupParams(entity.getPeriodMonths(),
                             entity.getMinFileSizeBytes(),
                             entity.getGraceDays(),
                             entity.getMaxVersionsPerFile(),
                             entity.getExcludedPaths(),
                             null);
  }

  private static Long toNullable(long millis) {
    return millis == 0 ? null : millis;
  }

  private static void fillOwner(CampaignItemRestEntity entity, long ownerIdentityId, IdentityManager identityManager) {
    Identity identity = identityManager.getIdentity(ownerIdentityId);
    if (identity == null) {
      return;
    }
    entity.setOwnerType(identity.isSpace() ? "space" : "user");
    entity.setOwnerRemoteId(identity.getRemoteId());
    // Same resolution as the CSV report's owner name, defined once
    entity.setOwnerFullName(CleanupIdentityUtil.displayName(identity));
  }

}
