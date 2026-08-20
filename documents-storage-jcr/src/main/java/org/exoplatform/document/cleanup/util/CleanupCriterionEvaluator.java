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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
 * <li>PURGE_VERSIONS: the file owns at least one PURGEABLE version — see
 * {@link #selectVersionsToRemove} for the union of the age rule and the count
 * rule that defines the removal set — and the size of that removal set is above
 * the floor</li>
 * </ul>
 * An exemption (exo:cleanupExemption mixin) is deliberately NOT evaluated here:
 * an exempted node still qualifying by these criteria is emitted as an
 * exempted-flagged
 * {@link org.exoplatform.document.cleanup.model.CleanupCandidate}, so it stays
 * visible as 'Kept' in every campaign.
 * <p>
 * The criterion is split in two: a CHEAP decision (path exclusion, the two
 * dates, the size of the removal set) and the EXPENSIVE size measurements,
 * supplied lazily. {@link #evaluateLazily} pulls a size only once the cheap
 * decision needs it to answer, so a scan pays a size read only for the nodes
 * that reach the size floor test at all — see the ordering rationale there. It
 * is the ONE entry point, for the scan and the revalidation paths alike (both
 * reach it through {@code CleanupJcrStorage#toCandidate}) and for the tests: a
 * caller already holding a size passes {@code () -> size}, which keeps the
 * policy defined exactly once instead of behind an eager facade no production
 * caller could have.
 */
public class CleanupCriterionEvaluator {

  private CleanupCriterionEvaluator() {
    // static utility
  }

  /**
   * Selects the versions of a versionable file to remove, as the UNION of the
   * TWO purge rules — an age rule and a count rule — the architect ruled on:
   * neither replaces the other. This removal set is the ONE definition of the
   * purge policy: {@link #evaluateLazily} counts it to decide candidacy at scan
   * and at revalidation time, and the execution removes exactly it, so the
   * three can never disagree.
   * <ol>
   * <li>the ROOT version and the BASE (current) version are NEVER removed —
   * skipped by name, and JCR forbids removing them anyway</li>
   * <li>every remaining version whose OWN creation date is older than the
   * campaign period ({@code nowMillis - periodMonths}) is removed; the
   * version's own date, never the file's — a file touched yesterday can still
   * carry versions from three years ago, and those are the PO's actual
   * target</li>
   * <li>of the versions still standing after that — all of them inside the
   * period — the OLDEST are removed while their count STILL exceeds
   * {@code maxVersionsPerFile}</li>
   * </ol>
   * So what survives is AT MOST {@code maxVersionsPerFile} versions, all inside
   * the period, plus the base and root versions.
   * <p>
   * THE CASE A READER WILL WONDER ABOUT: the base version may itself be older
   * than the period, and it survives regardless — it is the file's CURRENT
   * content, not an old revision. Reclaiming it would destroy the file, which
   * is the DELETE action's job (and its own dates decide that), never this
   * one's.
   * <p>
   * A version whose creation date is unknown (non-positive) is never removed BY
   * AGE — the same doubt-favours-the-file rule as {@link #isAged} — but it does
   * count against the cap, and being the oldest by sort order it is the first
   * the count rule trims.
   *
   * @param versionCreationDates creation date (epoch millis) of EVERY version
   *          of the file, keyed by version name, root and base versions
   *          included: they are skipped here so the exclusion stays defined
   *          once
   * @param rootVersionName name of the version history's root version
   * @param baseVersionName name of the file's base (current) version
   * @param params campaign parameters snapshot
   * @param nowMillis evaluation time (epoch millis)
   * @return the names of the versions to remove, OLDEST FIRST, empty when the
   *         file has nothing to purge
   */
  public static List<String> selectVersionsToRemove(Map<String, Long> versionCreationDates,
                                                    String rootVersionName,
                                                    String baseVersionName,
                                                    CleanupParams params,
                                                    long nowMillis) {
    if (versionCreationDates == null || versionCreationDates.isEmpty()) {
      return List.of();
    }
    long cutoffTime = computeCutoffTime(nowMillis, params.getPeriodMonths());
    List<String> versionsToRemove = new ArrayList<>();
    List<Entry<String, Long>> insidePeriod = new ArrayList<>();
    // Oldest first, so both rules below consume the history in the only order
    // that makes 'remove the oldest' mean anything
    versionCreationDates.entrySet()
                        .stream()
                        .filter(entry -> !StringUtils.equals(entry.getKey(), rootVersionName)
                            && !StringUtils.equals(entry.getKey(), baseVersionName))
                        .sorted(Entry.comparingByValue())
                        .forEach(entry -> {
                          if (entry.getValue() > 0 && entry.getValue() < cutoffTime) {
                            // RULE 1 — older than the campaign period, by its OWN date
                            versionsToRemove.add(entry.getKey());
                          } else {
                            insidePeriod.add(entry);
                          }
                        });
    // RULE 2 — the survivors are all inside the period; trim the oldest of them
    // while they still exceed the cap
    int excess = insidePeriod.size() - params.getMaxVersionsPerFile();
    for (int i = 0; i < excess; i++) {
      versionsToRemove.add(insidePeriod.get(i).getKey());
    }
    return versionsToRemove;
  }

  /**
   * The candidate criterion, with the two sizes supplied lazily: a supplier is
   * invoked ONLY when the decision cannot be reached without it. A caller
   * already holding a size hands over {@code () -> size} rather than reaching
   * for a second, eager entry point — there is none, on purpose.
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
   * Cost that REMAINS: the version HISTORY still has to be read for every
   * versionable file, because the count rule of
   * {@link #selectVersionsToRemove} is age-INDEPENDENT and a file created last
   * week can exceed the cap — that is what keeps pressuring the 'system'
   * workspace cache, and this split does NOT remove it. What that walk reads is
   * now only each version's NAME and OWN creation date, never a frozen node, so
   * the ~3 nodes per version are charged solely to the versions actually being
   * removed, on a file already known to be a candidate.
   *
   * @param createdTime node creation time (epoch millis, 0 = unknown)
   * @param lastModifiedTime node last modification time (epoch millis, 0 =
   *          unknown, falls back to createdTime)
   * @param purgeableVersionCount number of versions the purge would remove, as
   *          selected by {@link #selectVersionsToRemove} (0 for a
   *          non-versionable file, which has no version history at all)
   * @param path node path
   * @param params campaign parameters snapshot
   * @param nowMillis evaluation time (epoch millis)
   * @param fileSizeSupplier content size in bytes, measured on demand
   * @param purgeableVersionsSizeSupplier cumulated size of the versions the
   *          purge would remove, in bytes, measured on demand
   * @return the qualifying {@link CleanupAction}, or null when the node is not
   *         a candidate
   */
  public static CleanupAction evaluateLazily(long createdTime, // NOSONAR
                                             long lastModifiedTime,
                                             int purgeableVersionCount,
                                             String path,
                                             CleanupParams params,
                                             long nowMillis,
                                             LongSupplier fileSizeSupplier,
                                             LongSupplier purgeableVersionsSizeSupplier) {
    if (isExcluded(path, params.getExcludedPaths())) {
      return null;
    }
    boolean aged = isAged(createdTime, lastModifiedTime, params, nowMillis);
    boolean purgeable = purgeableVersionCount > 0;
    if (!aged && !purgeable) {
      // EXPOSITORY, not load-bearing: it states where the overwhelming majority
      // of scanned files leave — having cost their two dates and (when
      // versionable) the name-and-date walk of their version history, and NOT a
      // single size read. What ACTUALLY prevents the size reads is the
      // short-circuit of each test below, whose cheap operand ('aged' /
      // 'purgeable') is evaluated first, so removing this gate changes no result
      // and saves no read. The pair is the coverage; do not trust either half
      // alone, and do not expect a test to pin this one
      return null;
    }
    // DELIBERATE DIVERGENCE, do NOT 'align' it — pending a PO decision.
    // The floor is applied to the CONTENT size alone, while what a DELETE row
    // REPORTS as reclaimable is content + the whole version history
    // (CleanupCampaignItemDAO.RECLAIMABLE_BYTES, which the hard delete really
    // frees). §3.4 words the floor as 'the action's reclaimable bytes', so
    // testing the corrected figure here would be arguably truer — and would
    // make a 3 MB file carrying 10 MB of history clear a 5 MB floor where it
    // does not today. That WIDENS CANDIDACY, i.e. changes which files a
    // campaign proposes to destroy: a functional decision belonging to the PO,
    // explicitly withheld from this correction, which only fixed the REPORTED
    // figure. Pinned by CleanupJcrStorageTest, so nobody widens it silently
    if (aged && fileSizeSupplier.getAsLong() >= params.getMinFileSizeBytes()) {
      return CleanupAction.DELETE;
    } else if (purgeable && purgeableVersionsSizeSupplier.getAsLong() >= params.getMinFileSizeBytes()) {
      // Reached either directly (a file whose versions are purgeable while the
      // file itself is not aged) or by the fall-through of an aged file whose
      // content sits UNDER the floor: the eager criterion had exactly that
      // fall-through and it is preserved here
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
