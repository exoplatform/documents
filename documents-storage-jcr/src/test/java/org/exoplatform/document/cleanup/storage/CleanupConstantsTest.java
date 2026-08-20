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
  void shouldSweepNothingOnUnknownLayout() {
    assertEquals(Integer.MAX_VALUE, CleanupConstants.getDriveMinDepth(null));
    assertEquals(Integer.MAX_VALUE, CleanupConstants.getDriveMinDepth("/Trash/file.pdf"));
    assertEquals(Integer.MAX_VALUE, CleanupConstants.getDriveMinDepth("/Users/john/file.pdf"));
  }

}
