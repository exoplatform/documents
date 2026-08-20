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

import java.util.Calendar;
import java.util.List;
import java.util.function.LongSupplier;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.model.CleanupParams;

/**
 * Pure, JCR-free evaluation of the cleanup candidate criterion:
 * <ul>
 * <li>a node under an excluded path prefix is never a candidate</li>
 * <li>DELETE: created AND last modified older than the campaign period, and
 * content size above the floor</li>
 * <li>PURGE_VERSIONS: version count above the campaign maximum (independent of
 * content dates), and versions size above the floor</li>
 * </ul>
 * An exemption (exo:cleanupExemption mixin) is deliberately NOT evaluated here:
 * an exempted node still qualifying by these criteria is emitted as an
 * exempted-flagged
 * {@link org.exoplatform.document.cleanup.model.CleanupCandidate}, so it stays
 * visible as 'Kept' in every campaign.
 * <p>
 * The criterion is split in two: a CHEAP decision (path exclusion, the two
 * dates, the version count) and the EXPENSIVE size measurements, supplied
 * lazily. {@link #evaluateLazily} pulls a size only once the cheap decision
 * needs it to answer, so a scan pays a size read only for the nodes that reach
 * the size floor test at all — see the ordering rationale on
 * {@link #evaluateLazily}. {@link #evaluate} is the eager facade over the very
 * same policy (both sizes already at hand), so the policy stays defined ONCE
 * for the scan and the revalidation paths alike.
 */
public class CleanupCriterionEvaluator {

  private CleanupCriterionEvaluator() {
    // static utility
  }

  /**
   * Eager facade over {@link #evaluateLazily}, for callers already holding both
   * sizes (typically a test, or a caller that measured them for another
   * purpose). Delegates so the policy is defined exactly once.
   *
   * @param createdTime node creation time (epoch millis, 0 = unknown)
   * @param lastModifiedTime node last modification time (epoch millis, 0 =
   *          unknown, falls back to createdTime)
   * @param fileSize content size in bytes
   * @param versionsSize cumulated versions size in bytes
   * @param versionCount number of versions (excluding the root version)
   * @param path node path
   * @param params campaign parameters snapshot
   * @param nowMillis evaluation time (epoch millis)
   * @return the qualifying {@link CleanupAction}, or null when the node is not
   *         a candidate
   */
  public static CleanupAction evaluate(long createdTime, // NOSONAR
                                       long lastModifiedTime,
                                       long fileSize,
                                       long versionsSize,
                                       int versionCount,
                                       String path,
                                       CleanupParams params,
                                       long nowMillis) {
    return evaluateLazily(createdTime,
                          lastModifiedTime,
                          versionCount,
                          path,
                          params,
                          nowMillis,
                          () -> fileSize,
                          () -> versionsSize);
  }

  /**
   * Same criterion as {@link #evaluate}, with the two sizes supplied lazily:
   * a supplier is invoked ONLY when the decision cannot be reached without it.
   * <p>
   * WHY the ordering matters (do not 'simplify' it away): a sequential dry-run
   * measuring both sizes up-front for every nt:file was measured saturating
   * BOTH Infinispan JCR caches at their 1,000,000-entry cap — 'collaboration'
   * (the files) and 'system' (the version histories) — with the JVM heap
   * climbing from ~5 GB to ~20 GB. Measuring the versions size alone costs
   * about THREE nodes per version (jcr:frozenNode -> jcr:content -> jcr:data),
   * so an eager measurement charges that to every scanned file instead of only
   * to the ones whose candidacy actually turns on it.
   * <p>
   * Cost that REMAINS: while PURGE_VERSIONS stays age-independent, the version
   * HISTORY still has to be read for every versionable file to obtain
   * {@code versionCount} — that is what keeps pressuring the 'system'
   * workspace cache, and this split does NOT remove it. What it removes is the
   * per-version frozen-node walk for every file that is not a candidate.
   * Age-gating PURGE_VERSIONS is what would remove the rest; that is an open
   * policy question (a recent but heavily-versioned file is a candidate today)
   * and is deliberately NOT decided here.
   *
   * @param createdTime node creation time (epoch millis, 0 = unknown)
   * @param lastModifiedTime node last modification time (epoch millis, 0 =
   *          unknown, falls back to createdTime)
   * @param versionCount number of versions (excluding the root version)
   * @param path node path
   * @param params campaign parameters snapshot
   * @param nowMillis evaluation time (epoch millis)
   * @param fileSizeSupplier content size in bytes, measured on demand
   * @param versionsSizeSupplier cumulated versions size in bytes, measured on
   *          demand
   * @return the qualifying {@link CleanupAction}, or null when the node is not
   *         a candidate
   */
  public static CleanupAction evaluateLazily(long createdTime, // NOSONAR
                                             long lastModifiedTime,
                                             int versionCount,
                                             String path,
                                             CleanupParams params,
                                             long nowMillis,
                                             LongSupplier fileSizeSupplier,
                                             LongSupplier versionsSizeSupplier) {
    if (isExcluded(path, params.getExcludedPaths())) {
      return null;
    }
    boolean aged = isAged(createdTime, lastModifiedTime, params, nowMillis);
    boolean overVersioned = versionCount > params.getMaxVersionsPerFile();
    if (!aged && !overVersioned) {
      // THE cheap gate: the overwhelming majority of scanned files leave here,
      // having cost their two dates and (when versionable) their version count
      // — and NOT a single size read
      return null;
    }
    if (aged && fileSizeSupplier.getAsLong() >= params.getMinFileSizeBytes()) {
      return CleanupAction.DELETE;
    } else if (overVersioned && versionsSizeSupplier.getAsLong() >= params.getMinFileSizeBytes()) {
      // Reached either directly (recent but over-versioned file) or by the
      // fall-through of an aged file whose content sits UNDER the floor: the
      // eager criterion had exactly that fall-through and it is preserved here
      return CleanupAction.PURGE_VERSIONS;
    } else {
      return null;
    }
  }

  private static boolean isAged(long createdTime, long lastModifiedTime, CleanupParams params, long nowMillis) {
    long cutoffTime = computeCutoffTime(nowMillis, params.getPeriodMonths());
    long effectiveLastModified = lastModifiedTime > 0 ? lastModifiedTime : createdTime;
    return createdTime > 0
           && createdTime < cutoffTime
           && effectiveLastModified > 0
           && effectiveLastModified < cutoffTime;
  }

  private static boolean isExcluded(String path, List<String> excludedPaths) {
    if (StringUtils.isBlank(path) || excludedPaths == null || excludedPaths.isEmpty()) {
      return false;
    }
    return excludedPaths.stream()
                        .filter(StringUtils::isNotBlank)
                        .map(prefix -> StringUtils.removeEnd(prefix.trim(), "/"))
                        .anyMatch(prefix -> StringUtils.equals(path, prefix) || path.startsWith(prefix + "/"));
  }

  private static long computeCutoffTime(long nowMillis, int periodMonths) {
    Calendar cutoff = Calendar.getInstance();
    cutoff.setTimeInMillis(nowMillis);
    cutoff.add(Calendar.MONTH, -periodMonths);
    return cutoff.getTimeInMillis();
  }

}
