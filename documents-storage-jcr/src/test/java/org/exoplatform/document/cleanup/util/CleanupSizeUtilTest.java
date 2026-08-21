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
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.document.cleanup.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.exoplatform.document.cleanup.constant.CleanupAction;

/**
 * The one definition of what an action frees. Pinned here because three consumers
 * read it — the JPQL the aggregates sum, the comparator that merges owner-chunked
 * pages, and the REST row every console displays — and they were drifting apart by
 * construction: the review list had its own copy of the CASE in JavaScript.
 */
class CleanupSizeUtilTest {

  @Test
  void aDeleteFreesItsContentAndTheWholeHistoryItDestroys() {
    assertEquals(1_500L, CleanupSizeUtil.reclaimableBytes(CleanupAction.DELETE, 1_000L, 500L));
    assertEquals(1_500L, CleanupSizeUtil.reclaimableBytes(CleanupAction.DELETE.name(), 1_000L, 500L));
  }

  @Test
  void aVersionPurgeFreesTheRemovalSetAlone() {
    // The CONTENT survives a purge, so counting it here would promise bytes the
    // campaign never frees — and the totals would stop matching the rows
    assertEquals(500L, CleanupSizeUtil.reclaimableBytes(CleanupAction.PURGE_VERSIONS, 1_000L, 500L));
    assertEquals(500L, CleanupSizeUtil.reclaimableBytes(CleanupAction.PURGE_VERSIONS.name(), 1_000L, 500L));
  }

  @Test
  void anUnknownOrAbsentActionIsTreatedAsAPurgeNeverAsADelete() {
    // Fail-safe on the conservative side: the smaller figure. Answering the DELETE
    // sum for an action nobody recognises would over-promise what is freed
    assertEquals(500L, CleanupSizeUtil.reclaimableBytes((String) null, 1_000L, 500L));
    assertEquals(500L, CleanupSizeUtil.reclaimableBytes((CleanupAction) null, 1_000L, 500L));
  }

}
