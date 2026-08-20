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
package org.exoplatform.document.cleanup.model;

import org.exoplatform.document.cleanup.constant.CleanupScanUnitState;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One partition of the dry-run scan: the JCR subtree ONE reader thread walks,
 * with its own state, its own path checkpoint and its own counters. The unit is
 * what makes the parallel scan resumable per subtree instead of per campaign,
 * and what makes an unreadable subtree cost its own unit only.
 */
@Data
@NoArgsConstructor
public class CleanupScanUnit {

  private long                 id;

  private long                 campaignId;

  private String               unitPath;

  private CleanupScanUnitState state;

  /** Resume position in this unit, null while it was never started. */
  private String               lastScannedPath;

  private long                 scannedCount;

  private long                 totalCount;

  /** Localizable message code of the unit failure, never an exception message. */
  private String               failureReason;

}
