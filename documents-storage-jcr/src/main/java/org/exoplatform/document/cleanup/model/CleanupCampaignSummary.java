/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.model;

import lombok.Data;

/**
 * Aggregates of a cleanup campaign snapshotted at completion into its
 * summaryJson. Written by CleanupExecutionService when the campaign completes;
 * served by CleanupCampaignService once the retention job purged the item rows
 * (the live aggregates would then all be 0). The JSON keys are the field names:
 * writer and reader share this single class so they can never drift.
 * <p>
 * It also carries the DRY-RUN's own verdict ({@link #scanIncomplete},
 * {@link #failedScanUnitCount}), written by CleanupScanService at the SIMULATED
 * transition — a typed field of an existing column rather than a new one, and
 * the campaign row rather than a live re-count of the unit rows: whether the
 * report an administrator is about to publish covers the whole tree is a fact
 * DECIDED ONCE, by the coordinator that owns the completion rule, and it must
 * still read the same later on. CleanupExecutionService therefore CARRIES BOTH
 * FORWARD when it snapshots the purge aggregates over the same column.
 */
@Data
public class CleanupCampaignSummary {

  private long    candidateCount;

  private long    reclaimableBytes;

  private long    reclaimedBytes;

  private long    purgedCount;

  private long    exemptedCount;

  private long    sparedCount;

  private long    goneCount;

  private long    skippedCount;

  /**
   * Whether the dry-run reached SIMULATED with at least one subtree it could
   * never walk: the produced report is then a PARTIAL picture of the tree, and
   * says so instead of reading complete.
   */
  private boolean scanIncomplete;

  /** Scan units that settled failed, and are missing from the report. */
  private long    failedScanUnitCount;

}
