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
package org.exoplatform.document.cleanup.rest.model;

import java.util.List;

import lombok.Data;

/**
 * REST representation of a dry run's per-UNIT progress: how many subtrees settled,
 * how deep the retries went, and which ones are in flight.
 * <p>
 * {@code scanComplete} is the field the console must trust for completion, NOT the
 * node percentage: that percentage comes from the per-unit scanned counts already
 * persisted, so an interrupted run whose nodes were all counted reads 100% while a
 * unit is still being re-walked from its checkpoint.
 */
@Data
public class CampaignScanUnitProgressRestEntity {

  private long                              unitCount;

  private long                              pendingCount;

  private long                              runningCount;

  private long                              doneCount;

  private long                              failedCount;

  private long                              settledCount;

  private long                              maxAttemptCount;

  private boolean                           scanComplete;

  /**
   * Nodes walked but not evaluable, summed over every unit: files missing from the
   * report although their subtree finished. Non-zero means this report does not
   * cover every file it visited, whatever the percentage reads.
   */
  private long                              skippedNodeCount;

  /** RUNNING units only — bounded by the reader count, never by the unit count. */
  private List<CampaignScanUnitRestEntity>  inFlightUnits;

  /** The units that lost nodes, worst first and bounded, each with its trace. */
  private List<CampaignScanUnitRestEntity>  evaluationFailures;

}
