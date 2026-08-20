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
package org.exoplatform.document.cleanup.storage;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.document.cleanup.constant.CleanupScanUnitState;
import org.exoplatform.document.cleanup.dao.CleanupScanUnitDAO;
import org.exoplatform.document.cleanup.entity.CleanupScanUnitEntity;
import org.exoplatform.document.cleanup.model.CleanupFailureGroup;
import org.exoplatform.document.cleanup.model.CleanupScanUnit;

/**
 * RDBMS storage of the dry-run scan units: the only layer touching the scan-unit
 * DAO, mapping entities to simple domain models. No business logic — the
 * partitioning policy lives in {@code CleanupConstants}, the orchestration in
 * {@code CleanupScanService}.
 */
@Component
public class CleanupScanUnitStorage {

  /**
   * Width of the FAILURE_REASON column. A reason longer than that is a message
   * code gone wrong, and an oversized value would fail the whole writer batch —
   * so it is truncated here rather than allowed to abort the scan.
   */
  static final int           MAX_FAILURE_REASON_LENGTH = 2000;

  @Autowired
  private CleanupScanUnitDAO scanUnitDAO;

  /**
   * Plans the units of a campaign, inserting as PENDING the paths not recorded
   * yet and SKIPPING those already there: one paths query plus one saveAll,
   * exactly like {@code CleanupCampaignStorage#saveCandidates}, backed by the
   * UK_DOC_CLEANUP_SCAN_UNIT_PATH unique constraint. Safe — and meant — to be
   * called on every resume: a re-plan never duplicates a unit, and never resets
   * the checkpoint of a unit already walked.
   *
   * @param campaignId campaign identifier
   * @param unitPaths planned unit paths
   */
  public void planUnits(long campaignId, List<String> unitPaths) {
    if (unitPaths == null || unitPaths.isEmpty()) {
      return;
    }
    Set<String> existingUnitPaths = Set.copyOf(scanUnitDAO.findUnitPathsByCampaignId(campaignId));
    List<CleanupScanUnitEntity> newEntities = unitPaths.stream()
                                                       .filter(unitPath -> !existingUnitPaths.contains(unitPath))
                                                       .map(unitPath -> toEntity(campaignId, unitPath))
                                                       .toList();
    if (!newEntities.isEmpty()) {
      scanUnitDAO.saveAll(newEntities);
    }
  }

  /**
   * @param campaignId campaign identifier
   * @return the units of the campaign still to process (everything but DONE),
   *         oldest id first
   */
  public List<CleanupScanUnit> getUnitsToProcess(long campaignId) {
    return scanUnitDAO.findByCampaignIdAndStateNotOrderByIdAsc(campaignId, CleanupScanUnitState.DONE.name())
                      .stream()
                      .map(this::toModel)
                      .toList();
  }

  public long countUnits(long campaignId) {
    return scanUnitDAO.countByCampaignId(campaignId);
  }

  public long countUnitsByState(long campaignId, CleanupScanUnitState state) {
    return scanUnitDAO.countByCampaignIdAndState(campaignId, state.name());
  }

  /**
   * Failed units of a campaign that spent every walk attempt they had — the
   * SETTLED ones, which no longer hold the completion of the dry-run back.
   *
   * @param campaignId campaign identifier
   * @param maxAttemptCount attempts a unit may spend, the first walk included
   * @return the number of settled-failed units
   */
  public long countSettledFailedUnits(long campaignId, long maxAttemptCount) {
    return scanUnitDAO.countSettledFailures(campaignId, CleanupScanUnitState.FAILED.name(), maxAttemptCount);
  }

  /**
   * Per-reason counts of a campaign's FAILED units, from ONE grouped aggregate
   * query — the unit-level twin of
   * {@code CleanupCampaignStorage#countFailuresByReason}. The {@code retryable}
   * flag of each group is left to its default: what is worth re-attempting is a
   * Service rule, not a query's (see {@link CleanupFailureGroup}).
   *
   * @param campaignId campaign identifier
   * @return one group per distinct failure reason, empty when no unit of the
   *         campaign failed
   */
  public List<CleanupFailureGroup> countFailuresByReason(long campaignId) {
    return scanUnitDAO.countFailuresByReason(campaignId, CleanupScanUnitState.FAILED.name())
                      .stream()
                      .map(row -> new CleanupFailureGroup((String) row[0], ((Number) row[1]).longValue(), false))
                      .toList();
  }

  public void updateUnitState(long unitId, CleanupScanUnitState state) {
    scanUnitDAO.findById(unitId).ifPresent(entity -> {
      entity.setState(state.name());
      scanUnitDAO.save(entity);
    });
  }

  /**
   * CLAIMS a unit for a walk: RUNNING, and ONE more attempt spent. The two are
   * written together on purpose — an attempt counted anywhere else than where the
   * unit is handed to a reader is an attempt that can be skipped, and the bound
   * on a permanently failing subtree would then never be reached.
   *
   * @param unitId unit identifier
   * @return the attempts spent by the unit, this claim included
   */
  public long claimUnit(long unitId) {
    return scanUnitDAO.findById(unitId).map(entity -> {
      entity.setState(CleanupScanUnitState.RUNNING.name());
      entity.setAttemptCount(entity.getAttemptCount() + 1);
      return scanUnitDAO.save(entity).getAttemptCount();
    }).orElse(0L);
  }

  /**
   * Marks a unit FAILED with its localizable reason, truncated to the column
   * width (see {@link #MAX_FAILURE_REASON_LENGTH}).
   *
   * @param unitId unit identifier
   * @param failureReason localizable message code of the failure
   */
  public void updateUnitFailure(long unitId, String failureReason) {
    scanUnitDAO.findById(unitId).ifPresent(entity -> {
      entity.setState(CleanupScanUnitState.FAILED.name());
      entity.setFailureReason(StringUtils.left(failureReason, MAX_FAILURE_REASON_LENGTH));
      scanUnitDAO.save(entity);
    });
  }

  /**
   * Persists the resume checkpoint of a unit: {@code lastScannedPath} is the
   * path of the last node scanned in it — the ONLY positioning information —
   * and {@code scannedCount} the cumulated scanned nodes, kept for
   * progress/ETA display only.
   * <p>
   * The path is persisted VERBATIM, deliberately never truncated: a truncated
   * resume path would silently re-walk (or skip) a whole subtree, so an
   * oversized one must fail loudly and leave the campaign resumable instead.
   *
   * @param unitId unit identifier
   * @param lastScannedPath path of the last node scanned in the unit
   * @param scannedCount nodes scanned in the unit so far
   */
  public void updateUnitProgress(long unitId, String lastScannedPath, long scannedCount) {
    scanUnitDAO.findById(unitId).ifPresent(entity -> {
      entity.setLastScannedPath(lastScannedPath);
      entity.setScannedCount(scannedCount);
      scanUnitDAO.save(entity);
    });
  }

  public void updateUnitTotal(long unitId, long totalCount) {
    scanUnitDAO.findById(unitId).ifPresent(entity -> {
      entity.setTotalCount(totalCount);
      scanUnitDAO.save(entity);
    });
  }

  public long sumScannedCount(long campaignId) {
    return scanUnitDAO.sumScannedCount(campaignId);
  }

  public long sumTotalCount(long campaignId) {
    return scanUnitDAO.sumTotalCount(campaignId);
  }

  private CleanupScanUnitEntity toEntity(long campaignId, String unitPath) {
    CleanupScanUnitEntity entity = new CleanupScanUnitEntity();
    entity.setCampaignId(campaignId);
    entity.setUnitPath(unitPath);
    entity.setUnitPathHash(CleanupScanUnitEntity.hashUnitPath(unitPath));
    entity.setState(CleanupScanUnitState.PENDING.name());
    return entity;
  }

  private CleanupScanUnit toModel(CleanupScanUnitEntity entity) {
    CleanupScanUnit unit = new CleanupScanUnit();
    unit.setId(entity.getId());
    unit.setCampaignId(entity.getCampaignId());
    unit.setUnitPath(entity.getUnitPath());
    unit.setState(CleanupScanUnitState.valueOf(entity.getState()));
    unit.setLastScannedPath(entity.getLastScannedPath());
    unit.setScannedCount(entity.getScannedCount());
    unit.setTotalCount(entity.getTotalCount());
    unit.setAttemptCount(entity.getAttemptCount());
    unit.setFailureReason(entity.getFailureReason());
    return unit;
  }

}
