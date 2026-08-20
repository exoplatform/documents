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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.document.cleanup.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.exoplatform.document.cleanup.util.CleanupConstants;

class CleanupConstantsTest {

  @Test
  void shouldStopSweepAtSpaceDocumentsFolder() {
    // /Groups/spaces/<space>/Documents is always at depth 4
    assertEquals(4, CleanupConstants.getDriveMinDepth("/Groups/spaces/marketing/Documents/reports/q3/report.pdf"));
    assertEquals(CleanupConstants.SPACE_DRIVE_MIN_DEPTH,
                 CleanupConstants.getDriveMinDepth("/Groups/spaces/marketing/Documents/file.pdf"));
  }

  @Test
  void shouldStopSweepAtUserDriveFolderWhateverTheUserHomeNesting() {
    // Flat layout: /Users/<user>/Private at depth 3
    assertEquals(3, CleanupConstants.getDriveMinDepth("/Users/john/Private/docs/file.pdf"));
    assertEquals(3, CleanupConstants.getDriveMinDepth("/Users/john/Public/file.pdf"));
    // Hierarchical layout: user homes nested at a variable depth
    assertEquals(6, CleanupConstants.getDriveMinDepth("/Users/t___/te___/tes___/testuser1/Private/docs/file.pdf"));
    assertEquals(4, CleanupConstants.getDriveMinDepth("/Users/r/root/Private/file.pdf"));
  }

  @Test
  void shouldSplitTheUserAndSpaceRootsByTheirChildrenAndTakeTrashWhole() {
    // The per-root partitioning policy of the parallel scan, in ONE place:
    // /Users and /Groups/spaces are split ONE level down, /Trash never is
    assertEquals(List.of(CleanupConstants.USERS_ROOT, CleanupConstants.SPACES_ROOT), CleanupConstants.SPLIT_SCAN_ROOTS);
    assertEquals(List.of(CleanupConstants.TRASH_ROOT), CleanupConstants.UNSPLIT_SCAN_ROOTS);
    assertFalse(CleanupConstants.SPLIT_SCAN_ROOTS.contains(CleanupConstants.TRASH_ROOT),
                "/Trash must never be partitioned: a dedicated trash cleaner already exists on the platform");
    assertFalse(CleanupConstants.UNSPLIT_SCAN_ROOTS.contains(CleanupConstants.USERS_ROOT),
                "/Users taken whole would serialise the biggest tree of the platform onto one reader");
  }

  @Test
  void shouldPartitionExactlyTheScannedRoots() {
    // Neither policy set may drift from the scanned roots: a root missing from
    // both would silently drop out of every simulation
    assertEquals(CleanupConstants.SCAN_ROOTS.size(),
                 CleanupConstants.SPLIT_SCAN_ROOTS.size() + CleanupConstants.UNSPLIT_SCAN_ROOTS.size());
    assertTrue(Stream.concat(CleanupConstants.SPLIT_SCAN_ROOTS.stream(), CleanupConstants.UNSPLIT_SCAN_ROOTS.stream())
                     .allMatch(CleanupConstants.SCAN_ROOTS::contains),
               "Every partitioned root must be a scanned root");
  }

  @Test
  void shouldSweepNothingOnUnknownLayout() {
    assertEquals(Integer.MAX_VALUE, CleanupConstants.getDriveMinDepth(null));
    assertEquals(Integer.MAX_VALUE, CleanupConstants.getDriveMinDepth("/Trash/file.pdf"));
    assertEquals(Integer.MAX_VALUE, CleanupConstants.getDriveMinDepth("/Users/john/file.pdf"));
  }

}
