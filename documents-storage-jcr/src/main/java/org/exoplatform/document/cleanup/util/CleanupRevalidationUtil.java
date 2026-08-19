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
package org.exoplatform.document.cleanup.util;

import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupRevalidation;

/**
 * Single definition of the revalidation-outcome to item-state mapping, shared
 * by the freshness refresh (observation glue) and the execution-time
 * revalidation so both stay strictly identical.
 */
public class CleanupRevalidationUtil {

  private CleanupRevalidationUtil() {
    // static utility
  }

  /**
   * Applies a revalidation outcome to a campaign item:
   * <ul>
   * <li>outcome unknown (transient JCR read failure): the item is left
   * UNTOUCHED — never spared, never gone, never deleted on doubt; callers
   * needing a distinct handling (e.g. the execution skipping the item) check
   * {@link CleanupRevalidation#isUnknown()} first</li>
   * <li>node gone: {@link CleanupItemState#GONE}</li>
   * <li>node exempted: {@link CleanupItemState#EXEMPTED}</li>
   * <li>node no longer a candidate:
   * {@link CleanupItemState#SPARED_BY_MODIFICATION}</li>
   * <li>node still a candidate: refreshes the item's action, fileSize,
   * versionsSize and computedAt</li>
   * </ul>
   * The item is mutated, never persisted here.
   *
   * @param item campaign item to update
   * @param revalidation revalidation outcome
   * @return true when the node is still a candidate (the item's action was
   *         refreshed), false when the item left the candidate state or the
   *         outcome is unknown
   */
  public static boolean applyRevalidation(CleanupCampaignItem item, CleanupRevalidation revalidation) {
    if (revalidation.isUnknown()) {
      return false;
    } else if (!revalidation.isExists()) {
      item.setState(CleanupItemState.GONE);
      return false;
    } else if (revalidation.isExempted()) {
      item.setState(CleanupItemState.EXEMPTED);
      return false;
    } else if (revalidation.getCandidate() == null) {
      item.setState(CleanupItemState.SPARED_BY_MODIFICATION);
      return false;
    } else {
      item.setAction(revalidation.getCandidate().getAction());
      item.setFileSize(revalidation.getCandidate().getFileSize());
      item.setVersionsSize(revalidation.getCandidate().getVersionsSize());
      item.setComputedAt(System.currentTimeMillis());
      return true;
    }
  }

}
