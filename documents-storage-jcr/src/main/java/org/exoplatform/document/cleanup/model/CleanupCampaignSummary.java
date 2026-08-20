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
 */
@Data
public class CleanupCampaignSummary {

  private long candidateCount;

  private long reclaimableBytes;

  private long reclaimedBytes;

  private long purgedCount;

  private long exemptedCount;

  private long sparedCount;

  private long goneCount;

  private long skippedCount;

}
