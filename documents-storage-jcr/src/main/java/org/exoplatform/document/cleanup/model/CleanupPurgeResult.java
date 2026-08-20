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

  /**
   * A skip that reclaimed nothing at all.
   *
   * @param failureReason message code of the failure
   * @return a SKIPPED result with zero reclaimed bytes
   */
  public static CleanupPurgeResult skipped(String failureReason) {
    return skipped(failureReason, 0);
  }

  /**
   * A PARTIAL purge: the item stays SKIPPED and keeps its failure reason (an
   * administrator must still see that the file needs attention), but the bytes
   * really reclaimed before the failure are CARRIED, so the campaign's reclaimed
   * total reports the work actually done instead of silently under-reporting it.
   *
   * @param failureReason message code of the failure that interrupted the purge
   * @param reclaimedBytes bytes effectively reclaimed before that failure
   * @return a SKIPPED result carrying the bytes already reclaimed
   */
  public static CleanupPurgeResult skipped(String failureReason, long reclaimedBytes) {
    return new CleanupPurgeResult(CleanupItemState.SKIPPED, reclaimedBytes, failureReason);
  }

}
