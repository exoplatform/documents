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
import org.springframework.transaction.annotation.Transactional;

import org.exoplatform.document.cleanup.entity.CleanupScanUnitEntity;

public interface CleanupScanUnitDAO extends JpaRepository<CleanupScanUnitEntity, Long> {

  /**
   * The units of a campaign still to process, oldest id first: everything but
   * DONE, MINUS the settled-failed ones — a unit that FAILED after spending
   * every walk attempt it had. So a unit left RUNNING by an interrupted run and a
   * FAILED one with attempts LEFT are both picked up again, each from its own
   * persisted path checkpoint, while a subtree already proved unreadable is never
   * walked again.
   * <p>
   * The exclusion is STATE-AWARE — {@code state = FAILED AND attemptCount >=
   * :maxAttemptCount}, never a bare {@code attemptCount < :maxAttemptCount} — and
   * that is the trap of this query, not a stylistic choice: a unit interrupted
   * three times is RUNNING with three attempts spent, so a state-blind bound
   * would drop it from the work list while it is neither DONE nor
   * settled-FAILED. It could then never reach an outcome, and
   * {@code CleanupScanService#completeCampaign} would hold the dry-run open
   * forever over a unit nothing walks.
   */
  @Query("SELECT u FROM CleanupScanUnit u WHERE u.campaignId = :campaignId AND u.state <> :doneState"
      + " AND NOT (u.state = :failedState AND u.attemptCount >= :maxAttemptCount) ORDER BY u.id ASC")
  List<CleanupScanUnitEntity> findUnitsToProcess(@Param("campaignId")
  long campaignId, @Param("doneState")
  String doneState, @Param("failedState")
  String failedState, @Param("maxAttemptCount")
  long maxAttemptCount);

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
   * <p>
   * Its {@code >=} comparison must stay the EXACT complement of the exclusion in
   * {@link #findUnitsToProcess}: the units this counts as settled are the units
   * that one stops handing out, so a unit falling between the two would be
   * neither walked nor settled — a dry-run nothing can ever finish.
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
   * Drops every unit row of a campaign. Only ever called when the campaign itself
   * is being deleted, and only from a state no worker can be walking in.
   */
  @Transactional
  void deleteByCampaignId(long campaignId);

  /**
   * Per-STATE unit counts of a campaign, in ONE grouped query (rows: state, unit
   * count) — never by loading the unit rows, whose number is one per space plus
   * one per {@code /Users} bucket.
   */
  @Query("SELECT u.state, COUNT(u) FROM CleanupScanUnit u WHERE u.campaignId = :campaignId GROUP BY u.state")
  List<Object[]> countByState(@Param("campaignId")
  long campaignId);

  /**
   * Deepest walk attempt any unit of a campaign has spent — how many times the
   * most-retried subtree was re-walked, which is what tells an administrator that
   * a scan is going round in circles rather than progressing.
   */
  @Query("SELECT COALESCE(MAX(u.attemptCount), 0) FROM CleanupScanUnit u WHERE u.campaignId = :campaignId")
  long maxAttemptCount(@Param("campaignId")
  long campaignId);

  /**
   * The units of a campaign in one given state, oldest id first. Used for the
   * RUNNING ones only, so the result is bounded by the reader count — do NOT
   * reach for it with DONE on a large campaign.
   */
  @Query("SELECT u FROM CleanupScanUnit u WHERE u.campaignId = :campaignId AND u.state = :state ORDER BY u.id ASC")
  List<CleanupScanUnitEntity> findByState(@Param("campaignId")
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
