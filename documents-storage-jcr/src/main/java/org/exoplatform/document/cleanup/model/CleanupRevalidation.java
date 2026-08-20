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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of re-evaluating a former candidate against the campaign criteria,
 * distinguishing a node that disappeared, one that got exempted, one that no
 * longer qualifies, one still qualifying (candidate != null), and an outcome
 * that could NOT be computed (transient JCR read failure): an unknown outcome
 * must never be mistaken for a spared/gone node — the execution skips the item
 * (never deletes on doubt) and the freshness refresh leaves it untouched.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupRevalidation {

  private boolean          unknown;

  private boolean          exists;

  private boolean          exempted;

  private CleanupCandidate candidate;

  public static CleanupRevalidation gone() {
    return new CleanupRevalidation(false, false, false, null);
  }

  public static CleanupRevalidation exempted() {
    return new CleanupRevalidation(false, true, true, null);
  }

  public static CleanupRevalidation of(CleanupCandidate candidate) {
    return new CleanupRevalidation(false, true, false, candidate);
  }

  public static CleanupRevalidation unknown() {
    return new CleanupRevalidation(true, false, false, null);
  }

}
