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

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Outcome of a bulk keep / un-keep: how many items were decided, and which ones
 * failed with which reason. A bulk request continues past individual failures,
 * so the caller (and the UI) needs the per-item outcomes to avoid reporting a
 * success when nothing was actually kept.
 */
@Data
public class CleanupBulkResult {

  private int                      succeeded;

  private List<CleanupBulkFailure> failures = new ArrayList<>();

  /**
   * @param itemId item that could not be decided
   * @param reason message code explaining the failure
   */
  public void addFailure(long itemId, String reason) {
    failures.add(new CleanupBulkFailure(itemId, reason));
  }

}
