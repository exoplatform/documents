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
import lombok.NoArgsConstructor;

/**
 * Item aggregates of one campaign (candidate count/bytes, reclaimed bytes,
 * item-rows existence), computed by a single grouped query for a whole
 * campaigns list instead of per-campaign aggregate queries.
 */
@Data
@NoArgsConstructor
public class CleanupCampaignAggregates {

  private boolean itemsRetained;

  private long    candidateCount;

  private long    reclaimableBytes;

  private long    reclaimedBytes;

}
