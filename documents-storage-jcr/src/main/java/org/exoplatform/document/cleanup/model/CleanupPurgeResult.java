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

import org.exoplatform.document.cleanup.constant.CleanupItemState;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of a purge operation (hard delete or versions purge) on one item.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupPurgeResult {

  private CleanupItemState state;

  private long             reclaimedBytes;

  private String           failureReason;

  public static CleanupPurgeResult purged(long reclaimedBytes) {
    return new CleanupPurgeResult(CleanupItemState.PURGED, reclaimedBytes, null);
  }

  public static CleanupPurgeResult gone() {
    return new CleanupPurgeResult(CleanupItemState.GONE, 0, null);
  }

  public static CleanupPurgeResult skipped(String failureReason) {
    return new CleanupPurgeResult(CleanupItemState.SKIPPED, 0, failureReason);
  }

}
