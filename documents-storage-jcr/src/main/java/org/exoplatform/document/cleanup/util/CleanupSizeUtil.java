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
package org.exoplatform.document.cleanup.util;

import org.exoplatform.document.cleanup.constant.CleanupAction;

/**
 * The ONE definition of what an item's action reclaims, in Java.
 * <p>
 * There are three consumers of this rule and there must be one rule: the JPQL
 * expression the aggregates sum and the item lists order by
 * ({@code CleanupCampaignItemDAO#RECLAIMABLE_BYTES}), the in-memory comparator
 * that merges the owner-chunked pages, and the REST DTO every console row
 * displays. They were drifting apart by construction — the review list had
 * re-implemented the CASE in JavaScript — and a list that ranks by one definition
 * of <em>reclaimable</em> while the totals sum another is a quiet wrong number on
 * the one screen whose purpose is triage.
 * <p>
 * A DELETE frees its content AND the whole version history it destroys; a
 * PURGE_VERSIONS frees the removal set alone, which is what {@code versionsSize}
 * already holds for that action.
 */
public class CleanupSizeUtil {

  private CleanupSizeUtil() {
  }

  /**
   * @param action       the item's action, as its enum name
   * @param fileSize     content bytes of the file
   * @param versionsSize version bytes THIS action reclaims
   * @return bytes the action frees
   */
  public static long reclaimableBytes(String action, long fileSize, long versionsSize) {
    return CleanupAction.DELETE.name().equals(action) ? fileSize + versionsSize : versionsSize;
  }

  /**
   * @param action       the item's action
   * @param fileSize     content bytes of the file
   * @param versionsSize version bytes THIS action reclaims
   * @return bytes the action frees
   */
  public static long reclaimableBytes(CleanupAction action, long fileSize, long versionsSize) {
    return reclaimableBytes(action == null ? null : action.name(), fileSize, versionsSize);
  }

}
