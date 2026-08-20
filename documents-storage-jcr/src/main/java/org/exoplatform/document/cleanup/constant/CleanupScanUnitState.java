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
 * ONE reader thread). Only DONE is terminal for the planner: a unit left RUNNING
 * by an interrupted run, and a FAILED one, are both picked up again by the next
 * run — from their own persisted path checkpoint.
 */
public enum CleanupScanUnitState {

  PENDING,

  RUNNING,

  DONE,

  FAILED;

}
