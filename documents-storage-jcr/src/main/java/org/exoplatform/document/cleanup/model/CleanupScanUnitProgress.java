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

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-UNIT breakdown of a campaign's dry run: the state counts, the deepest
 * attempt any unit has spent, and the units currently in flight.
 * <p>
 * This exists because the node percentage ALONE cannot distinguish a scan that is
 * resuming from one that is stuck, and on a resume it is actively misleading. The
 * percentage is derived from the per-unit scanned counts already persisted, so a
 * run that was interrupted with every node counted reads <strong>100%</strong>
 * while a unit is still being re-walked — the resumed reader must fast-forward to
 * its checkpoint before it emits anything, and emits nothing meanwhile. An
 * administrator then sees a full bar and a "dry run in progress" chip and has no
 * way to tell which of the two it is. That was the gap this model closes: the
 * per-unit rows were persisted from the start precisely so the straggler would be
 * VISIBLE rather than inferred from a stalled percentage, and nothing surfaced
 * them.
 * <p>
 * {@link #scanComplete} is the honest completion signal and the one the console
 * must trust over the percentage: it is true only when every unit is SETTLED —
 * DONE, or FAILED with its walk attempts spent — which is the very condition the
 * terminal transition itself requires.
 * <p>
 * {@link #inFlightUnits} is bounded by the reader count, not by the unit count: it
 * carries the RUNNING units only, so it stays a handful of rows on a corpus whose
 * unit total is one per space.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupScanUnitProgress {

  /** Every planned unit of the campaign. */
  private long                 unitCount;

  /** Planned, never claimed by a reader yet. */
  private long                 pendingCount;

  /**
   * Claimed by a reader. A unit left RUNNING by an interrupted run keeps this
   * state until a later run finishes it: RUNNING does NOT mean a reader is alive.
   */
  private long                 runningCount;

  /** Walked to its end — the only state that is terminal on its own. */
  private long                 doneCount;

  /** Failed its last walk, whether or not its attempts are spent. */
  private long                 failedCount;

  /** DONE plus FAILED-with-attempts-spent: what the terminal transition counts. */
  private long                 settledCount;

  /** Deepest attempt count any unit of the campaign has spent. */
  private long                 maxAttemptCount;

  /**
   * True when every unit settled. The console shows completion from THIS, never
   * from the node percentage, which can read 100% while a unit is still walking.
   */
  private boolean              scanComplete;

  /** The RUNNING units, with their own checkpoint and counts. */
  private List<CleanupScanUnit> inFlightUnits;
}
