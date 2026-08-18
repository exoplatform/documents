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

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.model.CleanupParams;

/**
 * Pure, JCR-free evaluation of the cleanup candidate criterion:
 * <ul>
 * <li>an exempted node (exo:cleanupExemption mixin) is never a candidate</li>
 * <li>a node under an excluded path prefix is never a candidate</li>
 * <li>DELETE: created AND last modified older than the campaign period, and
 * content size above the floor</li>
 * <li>PURGE_VERSIONS: version count above the campaign maximum (independent of
 * content dates), and versions size above the floor</li>
 * </ul>
 */
public class CleanupCriterionEvaluator {

  private CleanupCriterionEvaluator() {
    // static utility
  }

  /**
   * @param createdTime node creation time (epoch millis, 0 = unknown)
   * @param lastModifiedTime node last modification time (epoch millis, 0 =
   *          unknown, falls back to createdTime)
   * @param fileSize content size in bytes
   * @param versionsSize cumulated versions size in bytes
   * @param versionCount number of versions (excluding the root version)
   * @param path node path
   * @param hasExemptionMixin whether the node carries exo:cleanupExemption
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
                                       boolean hasExemptionMixin,
                                       CleanupParams params,
                                       long nowMillis) {
    if (hasExemptionMixin || isExcluded(path, params.getExcludedPaths())) {
      return null;
    }
    long cutoffTime = computeCutoffTime(nowMillis, params.getPeriodMonths());
    long effectiveLastModified = lastModifiedTime > 0 ? lastModifiedTime : createdTime;
    boolean aged = createdTime > 0
                   && createdTime < cutoffTime
                   && effectiveLastModified > 0
                   && effectiveLastModified < cutoffTime;
    if (aged && fileSize >= params.getMinFileSizeBytes()) {
      return CleanupAction.DELETE;
    } else if (versionCount > params.getMaxVersionsPerFile() && versionsSize >= params.getMinFileSizeBytes()) {
      return CleanupAction.PURGE_VERSIONS;
    } else {
      return null;
    }
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
