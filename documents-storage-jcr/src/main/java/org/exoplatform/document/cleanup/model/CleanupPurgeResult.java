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

  /**
   * BARE message code of the failure, never concatenated with an exception
   * message: the console localizes it and the grouped-failures aggregate groups
   * on it. The exception text belongs to {@link #failureDetail}.
   */
  private String           failureReason;

  /**
   * Compact diagnostic of the failure (see
   * {@code CleanupThrowableUtil#formatFailureDetail}), null when the skip has no
   * exception behind it. ADMINISTRATOR-only downstream: it can name nodes the
   * item's owner may not see.
   */
  private String           failureDetail;

  public static CleanupPurgeResult purged(long reclaimedBytes) {
    return new CleanupPurgeResult(CleanupItemState.PURGED, reclaimedBytes, null, null);
  }

  public static CleanupPurgeResult gone() {
    return new CleanupPurgeResult(CleanupItemState.GONE, 0, null, null);
  }

  /**
   * A skip with NO exception behind it — a deterministic refusal such as
   * {@code cleanup.notVersionable}, whose message code already says everything
   * there is to say. Kept as an overload precisely so those call sites are not
   * forced to pass a null detail.
   *
   * @param failureReason bare message code of the failure
   * @return a SKIPPED result with zero reclaimed bytes and no detail
   */
  public static CleanupPurgeResult skipped(String failureReason) {
    return skipped(failureReason, null, 0);
  }

  /**
   * A skip that reclaimed nothing at all.
   *
   * @param failureReason bare message code of the failure
   * @param failureDetail compact diagnostic of the exception behind it
   * @return a SKIPPED result with zero reclaimed bytes
   */
  public static CleanupPurgeResult skipped(String failureReason, String failureDetail) {
    return skipped(failureReason, failureDetail, 0);
  }

  /**
   * A PARTIAL purge: the item stays SKIPPED and keeps its failure reason (an
   * administrator must still see that the file needs attention), but the bytes
   * really reclaimed before the failure are CARRIED, so the campaign's reclaimed
   * total reports the work actually done instead of silently under-reporting it.
   *
   * @param failureReason bare message code of the failure that interrupted the
   *          purge
   * @param failureDetail compact diagnostic of the exception behind it
   * @param reclaimedBytes bytes effectively reclaimed before that failure
   * @return a SKIPPED result carrying the bytes already reclaimed
   */
  public static CleanupPurgeResult skipped(String failureReason, String failureDetail, long reclaimedBytes) {
    return new CleanupPurgeResult(CleanupItemState.SKIPPED, reclaimedBytes, failureReason, failureDetail);
  }

}
