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

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;

import org.exoplatform.document.cleanup.model.CleanupBulkResult;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupComparison;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupUserSummary;
import org.exoplatform.document.cleanup.rest.model.CampaignComparisonRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignItemRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignRestEntity;
import org.exoplatform.document.cleanup.rest.model.KeepItemsResultRestEntity;
import org.exoplatform.document.cleanup.rest.model.MyItemsSummaryRestEntity;
import org.exoplatform.document.cleanup.rest.model.PagedResult;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
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
    return entity;
  }

  public static CampaignItemRestEntity build(CleanupCampaignItem item, IdentityManager identityManager) {
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
    entity.setAction(item.getAction().name());
    entity.setState(item.getState().name());
    entity.setComputedAt(toNullable(item.getComputedAt()));
    entity.setDecidedBy(item.getDecidedBy());
    entity.setDecidedAt(toNullable(item.getDecidedAt()));
    entity.setPurgedAt(toNullable(item.getPurgedAt()));
    entity.setReclaimedBytes(item.getReclaimedBytes());
    entity.setFailureReason(item.getFailureReason());
    return entity;
  }

  public static PagedResult<CampaignItemRestEntity> build(Page<CleanupCampaignItem> page, IdentityManager identityManager) {
    return new PagedResult<>(page.getContent().stream().map(item -> build(item, identityManager)).toList(),
                             page.getNumber(),
                             page.getSize(),
                             page.getTotalElements());
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
    Profile profile = identity.getProfile();
    entity.setOwnerFullName(profile == null || StringUtils.isBlank(profile.getFullName()) ? identity.getRemoteId() :
                                                                                          profile.getFullName());
  }

}
