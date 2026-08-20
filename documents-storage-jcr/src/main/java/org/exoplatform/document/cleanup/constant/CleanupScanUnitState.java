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
package org.exoplatform.document.cleanup.constant;

/**
 * State of a single dry-run scan unit (a partition of the scanned tree walked by
 * ONE reader thread). DONE is terminal for the planner, and it is the only state
 * that is terminal ON ITS OWN: a unit left RUNNING by an interrupted run, and a
 * FAILED one, are both picked up again by the next run — from their own persisted
 * path checkpoint.
 * <p>
 * FAILED is terminal only TOGETHER WITH its attempt count: a unit that spent
 * {@code CleanupScanService#MAX_SCAN_UNIT_ATTEMPTS} walks is settled-failed, so
 * {@code CleanupScanUnitStorage#getUnitsToProcess} stops handing it out and it
 * stops holding the dry-run's completion back. That pairing is why the exclusion
 * is state-AWARE: an attempt count alone would also settle a RUNNING unit, which
 * must never be settled — nothing else would ever give it an outcome.
 */
public enum CleanupScanUnitState {

  PENDING,

  RUNNING,

  DONE,

  FAILED;

}
