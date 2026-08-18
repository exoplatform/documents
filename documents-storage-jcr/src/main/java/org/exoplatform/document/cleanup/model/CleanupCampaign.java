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

import org.exoplatform.document.cleanup.constant.CleanupCampaignState;

import lombok.Data;

/**
 * Domain model of a cleanup campaign. Dates are epoch millis, 0 meaning 'not
 * set'.
 */
@Data
public class CleanupCampaign {

  private long                 id;

  private String               name;

  private CleanupCampaignState state;

  private CleanupParams        params;

  private long                 startedDate;

  private long                 publishedDate;

  private long                 lockDate;

  private long                 completedDate;

  private long                 totalCount;

  private long                 processedCount;

  private long                 etaSeconds;

  private long                 checkpointOffset;

  private String               checkpointPath;

  private String               summaryJson;

  private Long                 archiveFileId;

  // Aggregates computed from campaign items, filled by the Service layer
  private long                 candidateCount;

  private long                 reclaimableBytes;

  private long                 reclaimedBytes;

  /**
   * Whether item detail rows are still retained for this campaign (false once
   * the retention job archived and purged them). Filled by the Service layer.
   */
  private boolean              itemsRetained;

}
