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

import lombok.Data;

/**
 * REST representation of a cleanup campaign item. Dates are epoch millis or
 * null.
 */
@Data
public class CampaignItemRestEntity {

  private long   id;

  private long   campaignId;

  private String nodeUuid;

  private String path;

  private String name;

  private long   ownerIdentityId;

  private String ownerType;

  private String ownerRemoteId;

  private String ownerFullName;

  private long   fileSize;

  /**
   * Version bytes this item's ACTION reclaims — the whole version history for a
   * DELETE, the removal set for a PURGE_VERSIONS. The console renders it as
   * 'Versions size', which holds for both readings.
   */
  private long   versionsSize;

  private Long   lastModifiedDate;

  private Long   createdDate;

  private String action;

  private String state;

  private Long   computedAt;

  private String decidedBy;

  private Long   decidedAt;

  private Long   purgedAt;

  private long   reclaimedBytes;

  /** Bare, localizable failure message code. Served on EVERY path — it leaks nothing. */
  private String failureReason;

  /**
   * Compact failure diagnostic (stack frames, exception messages). Serialized on
   * the ADMINISTRATOR path only, and null everywhere else: it can name nodes
   * outside the calling user's visibility. See
   * {@code CleanupEntityBuilder#build(CleanupCampaignItem, IdentityManager, boolean)}.
   */
  private String failureDetail;

  /** Purge attempts already spent on this item, retries included. */
  private long   attemptCount;

}
