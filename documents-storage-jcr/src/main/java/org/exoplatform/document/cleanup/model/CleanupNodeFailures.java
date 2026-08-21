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

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.document.cleanup.util.CleanupThrowableUtil;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nodes a scan batch could not EVALUATE, and the first failure that did it.
 * <p>
 * Not the same thing as a failed scan unit: the subtree was walked fine, and
 * individual files inside it could not be judged — a
 * {@code NullPointerException} on a {@code mix:versionable} node with no base
 * version, a transient read error on one property. Each one used to be a WARN in
 * the log and a file silently missing from the report, while the unit finished
 * DONE and the campaign claimed a complete scan.
 * <p>
 * ONE failure is kept, the FIRST, plus a count of the rest. Keeping every stack
 * trace of a subtree that fails on ten thousand nodes would be a column nobody
 * can read and a row nobody can load; the first failure of a unit is what
 * diagnoses the cause, and the count is what says how much the report lost.
 */
@Data
@NoArgsConstructor
public class CleanupNodeFailures {

  private long   count;

  /** Short label of the FIRST failure: its exception class and message. */
  private String reason;

  /** Trimmed stack trace of that same first failure. */
  private String detail;

  /**
   * Records one node the scan could not evaluate.
   *
   * @param throwable what stopped the evaluation
   */
  public void record(Throwable throwable) {
    count++;
    if (reason != null || throwable == null) {
      return;
    }
    // The class name is carried because these are BUGS as often as environment
    // problems, and a bare message is frequently null — the NullPointerException
    // that started this had none
    reason = StringUtils.abbreviate(throwable.getClass().getSimpleName()
        + (StringUtils.isBlank(throwable.getMessage()) ? "" : ": " + throwable.getMessage()), MAX_REASON_LENGTH);
    detail = CleanupThrowableUtil.formatFailureDetail(throwable);
  }

  public boolean isEmpty() {
    return count == 0;
  }

  /** Fits the NVARCHAR(255) the reason column is. */
  private static final int MAX_REASON_LENGTH = 255;

}
