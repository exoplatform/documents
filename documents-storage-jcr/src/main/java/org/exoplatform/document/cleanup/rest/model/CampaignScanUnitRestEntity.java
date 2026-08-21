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
 * REST representation of ONE scan unit in flight: the subtree a reader is walking,
 * its own resume checkpoint and its own counts.
 * <p>
 * A unit reported here is not proof a reader is alive: RUNNING means "claimed and
 * not finished", and a run interrupted mid-unit leaves that state behind. Read
 * together with {@code attemptCount}, which says how many times this very subtree
 * has been walked, that is precisely what distinguishes a scan making progress
 * from one going round in circles.
 */
@Data
public class CampaignScanUnitRestEntity {

  private String unitPath;

  private String lastScannedPath;

  private long   scannedCount;

  private Long   totalCount;

  private long   attemptCount;

  /** Nodes of this subtree the scan walked but could not evaluate. */
  private long   evalFailureCount;

  /** Short label of the FIRST such failure — its class and message. */
  private String evalFailureReason;

  /**
   * Trimmed stack trace of that same failure, for the console to show and let an
   * administrator copy. Present only when something actually failed.
   */
  private String evalFailureDetail;

}
