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
 * REST representation of a cleanup campaign. Dates are epoch millis or null.
 * Also used as creation request body (name + optional parameter overrides).
 */
@Data
public class CampaignRestEntity {

  private long         id;

  private String       name;

  private String       state;

  private Integer      periodMonths;

  private Long         minFileSizeBytes;

  private Integer      graceDays;

  private Integer      maxVersionsPerFile;

  /**
   * Reader threads the dry-run scan may use, null meaning 'the platform default'.
   * Client-settable and therefore BOUNDED server-side
   * ({@code CleanupCampaignService#validateParams}): the fan-out is load on a
   * shared repository, so it is not a number a form gets to choose freely.
   */
  private Integer      scanThreads;

  /**
   * Highest {@link #scanThreads} the server will accept, served with the platform
   * DEFAULTS so the creation form can bound its own input and say what the bound
   * is. Read-only: it is ignored on the way in, the server validating against its
   * own ceiling whatever a client claims.
   */
  private Integer      maxScanThreads;

  private List<String> excludedPaths;

  private Long         startedDate;

  private Long         publishedDate;

  private Long         lockDate;

  private Long         completedDate;

  private long         totalCount;

  private long         processedCount;

  private Long         etaSeconds;

  private long         candidateCount;

  private long         reclaimableBytes;

  private long         reclaimedBytes;

  private boolean      archiveAvailable;

  /**
   * SERVER-COMPUTED: whether an execution request would be accepted right now
   * (campaign LOCKED, or PUBLISHED with its grace deadline already elapsed). The
   * UI must gate its Execute button on this instead of comparing
   * {@link #lockDate} to the browser clock: a skewed client would otherwise
   * enable the button early and surface a 400 'cleanup.graceNotElapsed'.
   */
  private boolean      executable;

  /**
   * SERVER-COMPUTED: milliseconds left before the grace deadline, 0 once it
   * elapsed (or when there is no deadline). A DURATION, not an instant, so the
   * UI counts it down locally — re-synced on every refresh — without ever
   * subtracting a server epoch from its own clock.
   */
  private long         remainingMillis;

}
