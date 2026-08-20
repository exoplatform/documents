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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupRevalidation;

/**
 * Revalidation-outcome to item-state mapping, and the freshness of what a
 * still-candidate row carries afterwards.
 */
class CleanupRevalidationUtilTest {

  private static final String NODE_UUID = "uuid-1";

  @Test
  void applyRevalidationRefreshesTheCandidacyDatesOfAStillCandidateItem() {
    CleanupCampaignItem item = item();

    boolean stillCandidate = CleanupRevalidationUtil.applyRevalidation(item, CleanupRevalidation.of(candidate(8000L, 9000L)));

    assertTrue(stillCandidate);
    // A re-scanned row keeping the date of the PREVIOUS scan would show the
    // report reader a file as untouched while it was modified since
    assertEquals(9000L, item.getLastModifiedDate(), "The last-modified date must be re-read from the revalidated node");
    assertEquals(8000L, item.getCreatedDate(), "The creation date must be re-read from the revalidated node");
    assertEquals(CleanupAction.PURGE_VERSIONS, item.getAction());
    assertEquals(4096L, item.getFileSize());
    assertEquals(512L, item.getVersionsSize());
    assertTrue(item.getComputedAt() > 0);
  }

  @Test
  void applyRevalidationLeavesTheDatesUntouchedWhenTheItemLeavesTheCandidateState() {
    CleanupCampaignItem sparedItem = item();
    assertFalse(CleanupRevalidationUtil.applyRevalidation(sparedItem, CleanupRevalidation.of(null)));
    assertEquals(CleanupItemState.SPARED_BY_MODIFICATION, sparedItem.getState());
    assertEquals(2000L, sparedItem.getLastModifiedDate(), "The scan-time dates stay as recorded");
    assertEquals(1000L, sparedItem.getCreatedDate());

    CleanupCampaignItem unknownItem = item();
    assertFalse(CleanupRevalidationUtil.applyRevalidation(unknownItem, CleanupRevalidation.unknown()));
    assertEquals(CleanupItemState.CANDIDATE, unknownItem.getState(), "An unknown outcome leaves the item untouched");
    assertEquals(2000L, unknownItem.getLastModifiedDate());
    assertEquals(1000L, unknownItem.getCreatedDate());
  }

  private CleanupCampaignItem item() {
    CleanupCampaignItem item = new CleanupCampaignItem();
    item.setId(9L);
    item.setNodeUuid(NODE_UUID);
    item.setAction(CleanupAction.DELETE);
    item.setState(CleanupItemState.CANDIDATE);
    item.setFileSize(2048L);
    item.setVersionsSize(256L);
    item.setCreatedDate(1000L);
    item.setLastModifiedDate(2000L);
    return item;
  }

  private CleanupCandidate candidate(long createdTime, long lastModifiedTime) {
    return new CleanupCandidate(NODE_UUID,
                                "/Users/j___/john/Private/report.pdf",
                                5L,
                                4096L,
                                512L,
                                CleanupAction.PURGE_VERSIONS,
                                createdTime,
                                lastModifiedTime);
  }

}
