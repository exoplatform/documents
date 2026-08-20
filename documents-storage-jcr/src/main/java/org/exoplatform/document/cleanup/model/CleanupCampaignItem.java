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

import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupItemState;

import lombok.Data;

/**
 * Domain model of a cleanup campaign item (one candidate file and its
 * lifecycle). Dates are epoch millis, 0 meaning 'not set'.
 */
@Data
public class CleanupCampaignItem {

  private long             id;

  private long             campaignId;

  private String           nodeUuid;

  private String           path;

  private long             ownerIdentityId;

  private long             fileSize;

  private long             versionsSize;

  /** Last modification date of the file, as read by the scan. */
  private long             lastModifiedDate;

  /**
   * Creation date of the file, as read by the scan. Carried along the
   * last-modified one — both being older than the campaign period is what made
   * the file a candidate.
   */
  private long             createdDate;

  private CleanupAction    action;

  private CleanupItemState state;

  private long             computedAt;

  private String           decidedBy;

  private long             decidedAt;

  private long             purgedAt;

  private long             reclaimedBytes;

  /**
   * Localizable message code of the failure, never concatenated with an
   * exception message: the console localizes it and the grouped-failures
   * aggregate groups on it.
   */
  private String           failureReason;

  /**
   * Compact diagnostic of the failure, for an ADMINISTRATOR only — it names
   * nodes and paths the item's own owner may not see. Served on the admin item
   * endpoint and the CSV report exclusively, see the REST layer.
   */
  private String           failureDetail;

  /** Purge attempts already spent on this item, incremented by every retry. */
  private long             attemptCount;

}
