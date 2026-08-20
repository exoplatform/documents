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
package org.exoplatform.document.cleanup.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.exoplatform.document.cleanup.entity.CleanupScanUnitEntity;

public interface CleanupScanUnitDAO extends JpaRepository<CleanupScanUnitEntity, Long> {

  /**
   * The units of a campaign still to process, oldest id first: everything but
   * DONE, so a unit left RUNNING by an interrupted run and a FAILED one are both
   * picked up again — each from its own persisted path checkpoint.
   */
  List<CleanupScanUnitEntity> findByCampaignIdAndStateNotOrderByIdAsc(long campaignId, String state);

  /**
   * Unit PATHS of a campaign, as a projection: planning only needs to know which
   * paths already exist, and loading whole rows to read one column of each would
   * be wasted work on every resume.
   */
  @Query("SELECT u.unitPath FROM CleanupScanUnit u WHERE u.campaignId = :campaignId")
  List<String> findUnitPathsByCampaignId(@Param("campaignId")
  long campaignId);

  long countByCampaignId(long campaignId);

  long countByCampaignIdAndState(long campaignId, String state);

  /**
   * Units of a campaign whose walk failed and which spent every attempt they
   * had: they are SETTLED, so they no longer hold the dry-run's completion back
   * — and they are exactly what makes the produced report INCOMPLETE.
   */
  @Query("SELECT COUNT(u) FROM CleanupScanUnit u WHERE u.campaignId = :campaignId AND u.state = :state"
      + " AND u.attemptCount >= :maxAttemptCount")
  long countSettledFailures(@Param("campaignId")
  long campaignId, @Param("state")
  String state, @Param("maxAttemptCount")
  long maxAttemptCount);

  /**
   * Per-reason unit counts of a campaign's failed subtrees, in ONE grouped query
   * (rows: failure reason, unit count) — the very shape
   * {@code CleanupCampaignItemDAO#countFailuresByReason} answers for items, so
   * the console renders both through the same block.
   */
  @Query("SELECT u.failureReason, COUNT(u) FROM CleanupScanUnit u" +
      " WHERE u.campaignId = :campaignId AND u.state = :state GROUP BY u.failureReason")
  List<Object[]> countFailuresByReason(@Param("campaignId")
  long campaignId, @Param("state")
  String state);

  /**
   * Scanned-nodes sum over the units of a campaign — the progress numerator,
   * computed by the database. NEVER by loading the unit rows: the numerator is
   * re-read on every resume, and a run only ever needs the total.
   */
  @Query("SELECT COALESCE(SUM(u.scannedCount), 0) FROM CleanupScanUnit u WHERE u.campaignId = :campaignId")
  long sumScannedCount(@Param("campaignId")
  long campaignId);

  /** Counted-nodes sum over the units of a campaign — the ETA denominator. */
  @Query("SELECT COALESCE(SUM(u.totalCount), 0) FROM CleanupScanUnit u WHERE u.campaignId = :campaignId")
  long sumTotalCount(@Param("campaignId")
  long campaignId);

}
