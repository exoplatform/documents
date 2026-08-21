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

import java.util.List;

/**
 * Single definition of the physical drive layout swept by cleanup campaigns
 * (scan roots, drive-root depths) and of the cleanup-wide static constants.
 */
public class CleanupConstants {

  /**
   * Logical sort key naming the RECLAIMABLE ordering of campaign items — the one
   * and only name for it, shared by the REST sortable-field allowlist and by the
   * Storage translating it into an ORDER BY on
   * {@code CleanupCampaignItemDAO#RECLAIMABLE_BYTES} (the very column every
   * reclaimable aggregate sums).
   * <p>
   * Lives HERE, and not with the JPQL it ends up as, because it crosses layers:
   * the REST layer must accept it without importing a DAO, the Storage must
   * recognize it, and a second literal in either place would let the two drift
   * apart silently. It happens to equal the entity's attribute name now that the
   * figure is a persisted column — pinned by a test, since that equality is what
   * lets the Storage translate this into an ordinary property sort instead of an
   * unsafe one.
   */
  public static final String       RECLAIMABLE_SORT_KEY  = "reclaimableBytes";

  /** Root of the user drives in the collaboration workspace. */
  public static final String       USERS_ROOT            = "/Users";

  /** Root of the space drives in the collaboration workspace. */
  public static final String       SPACES_ROOT           = "/Groups/spaces";

  public static final String       TRASH_ROOT            = "/Trash";

  /** Roots scanned (and watched) by cleanup campaigns. */
  public static final List<String> SCAN_ROOTS            = List.of(USERS_ROOT, SPACES_ROOT, TRASH_ROOT);

  /**
   * Scan roots whose DIRECT CHILDREN are the parallel scan units — depth 1, and
   * deliberately not deeper.
   * <p>
   * NOTE what a direct child of /Users actually is: under eXo's {@code READABLE}
   * data distribution ({@code depth = 4}, suffix {@code ___}) the children of
   * /Users are FIRST-LETTER BUCKETS ({@code /Users/r___}), NOT user homes — a
   * home sits at {@code /Users/r___/ro___/roo___/root}. So this yields ~26-40
   * units of very UNEQUAL size, and the largest bucket alone bounds the
   * wall-clock of the whole scan.
   * <p>
   * That imbalance is ACCEPTED, on purpose: enumeration stays a single
   * one-level listing with no knowledge of the distribution's depth or suffix
   * baked in, and the per-unit rows make the long tail measurable before anyone
   * pays for a smarter split. Do NOT "improve" it by walking deeper.
   */
  public static final List<String> SPLIT_SCAN_ROOTS      = List.of(USERS_ROOT, SPACES_ROOT);

  /**
   * Scan roots taken WHOLE as a single unit, never partitioned: /Trash already
   * has a dedicated trash cleaner on the platform, so splitting it buys nothing
   * — it is scanned for the report only.
   */
  public static final List<String> UNSPLIT_SCAN_ROOTS    = List.of(TRASH_ROOT);

  /** Name of the user drive-root folder holding private documents. */
  public static final String       USER_PRIVATE_FOLDER   = "Private";

  /** Name of the user drive-root folder holding public documents. */
  public static final String       USER_PUBLIC_FOLDER    = "Public";

  /**
   * Depth (JCR semantics: '/' = 0) of the space drive-root node
   * {@code /Groups/spaces/<space>/Documents}: the empty-ancestors sweep of a
   * deleted space file must stop at (and never remove) this node.
   */
  public static final int          SPACE_DRIVE_MIN_DEPTH = 4;

  /**
   * Administrators group receiving cleanup campaign WebSocket notifications.
   * Static by design decision — see the cleanup tech spec: this group is
   * deliberately NOT configurable and NOT resolved through UserACL.
   */
  public static final String       ADMINISTRATORS_GROUP  = "/platform/administrators";

  private CleanupConstants() {
    // constants and static helpers only
  }

  /**
   * Depth of the drive-root node the empty-ancestors sweep of a deleted file
   * must stop at (the drive root itself is preserved, only deeper empty folders
   * may be removed):
   * <ul>
   * <li>space drive: {@code /Groups/spaces/<space>/Documents}, always at
   * {@link #SPACE_DRIVE_MIN_DEPTH}</li>
   * <li>user drive: the user home's {@code Private} (or {@code Public}) folder.
   * User homes are nested at a VARIABLE depth under /Users (e.g.
   * {@code /Users/j___/jo___/joh___/john/Private}), so no constant depth
   * exists: it is computed from the deleted node path, as the depth of its
   * first {@code Private}/{@code Public} segment</li>
   * </ul>
   *
   * @param deletedNodePath absolute path of the deleted file node
   * @return the drive-root depth, or {@link Integer#MAX_VALUE} (sweep nothing)
   *         when the path matches no known drive layout
   */
  public static int getDriveMinDepth(String deletedNodePath) {
    if (deletedNodePath == null) {
      return Integer.MAX_VALUE;
    } else if (deletedNodePath.startsWith(SPACES_ROOT + "/")) {
      return SPACE_DRIVE_MIN_DEPTH;
    } else if (deletedNodePath.startsWith(USERS_ROOT + "/")) {
      int driveFolderIndex = indexOfSegment(deletedNodePath, USER_PRIVATE_FOLDER);
      if (driveFolderIndex < 0) {
        driveFolderIndex = indexOfSegment(deletedNodePath, USER_PUBLIC_FOLDER);
      }
      if (driveFolderIndex >= 0) {
        return depthAt(deletedNodePath, driveFolderIndex);
      }
    }
    return Integer.MAX_VALUE;
  }

  private static int indexOfSegment(String path, String segment) {
    return path.indexOf("/" + segment + "/");
  }

  private static int depthAt(String path, int segmentStartIndex) {
    // Depth of the segment starting after path.charAt(segmentStartIndex) '/':
    // number of '/' characters up to and including that one
    int depth = 0;
    for (int i = 0; i <= segmentStartIndex; i++) {
      if (path.charAt(i) == '/') {
        depth++;
      }
    }
    return depth;
  }

}
