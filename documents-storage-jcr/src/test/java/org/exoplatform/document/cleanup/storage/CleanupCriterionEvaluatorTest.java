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
package org.exoplatform.document.cleanup.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.util.CleanupCriterionEvaluator;

class CleanupCriterionEvaluatorTest {

  private static final long   NOW           = System.currentTimeMillis();

  private static final long   MIN_FILE_SIZE = 1048576L;

  private static final String PATH          = "/Groups/spaces/marketing/Documents/report.pdf";

  private static final String ROOT_VERSION  = "jcr:rootVersion";

  private static final String BASE_VERSION  = "base";

  @Test
  void shouldReturnDeleteWhenBothDatesOlderThanPeriodAndSizeAboveFloor() {
    assertEquals(CleanupAction.DELETE,
                 evaluate(monthsAgo(12), monthsAgo(8), MIN_FILE_SIZE, 0, 0, PATH));
  }

  @Test
  void shouldNotReturnDeleteWhenCreatedRecent() {
    assertNull(evaluate(monthsAgo(2), monthsAgo(2), MIN_FILE_SIZE, 0, 0, PATH));
  }

  @Test
  void shouldNotReturnDeleteWhenLastModifiedRecent() {
    assertNull(evaluate(monthsAgo(12), monthsAgo(1), MIN_FILE_SIZE, 0, 0, PATH));
  }

  @Test
  void shouldNotReturnDeleteWhenCreatedUnknown() {
    assertNull(evaluate(0, monthsAgo(12), MIN_FILE_SIZE, 0, 0, PATH));
  }

  @Test
  void shouldFallBackToCreatedDateWhenLastModifiedUnknown() {
    assertEquals(CleanupAction.DELETE, evaluate(monthsAgo(12), 0, MIN_FILE_SIZE, 0, 0, PATH));
  }

  @Test
  void shouldNotReturnDeleteWhenSizeUnderFloor() {
    assertNull(evaluate(monthsAgo(12), monthsAgo(8), MIN_FILE_SIZE - 1, 0, 0, PATH));
  }

  @Test
  void shouldReturnPurgeVersionsWhenTheFileHasPurgeableVersionsEvenWhenItIsRecent() {
    assertEquals(CleanupAction.PURGE_VERSIONS,
                 evaluate(monthsAgo(1), monthsAgo(1), MIN_FILE_SIZE, MIN_FILE_SIZE, 1, PATH));
  }

  @Test
  void shouldNotReturnPurgeVersionsWhenPurgeableVersionsSizeUnderFloor() {
    assertNull(evaluate(monthsAgo(1), monthsAgo(1), MIN_FILE_SIZE, MIN_FILE_SIZE - 1, 1, PATH));
  }

  @Test
  void shouldNotReturnPurgeVersionsWhenTheRemovalSetIsEmpty() {
    // The removal set — NOT the version count — is what candidacy turns on: a
    // file with versions the policy spares has nothing to purge
    assertNull(evaluate(monthsAgo(1), monthsAgo(1), MIN_FILE_SIZE, MIN_FILE_SIZE, 0, PATH));
  }

  @Test
  void shouldPreferDeleteOverPurgeVersionsWhenBothQualify() {
    assertEquals(CleanupAction.DELETE,
                 evaluate(monthsAgo(12), monthsAgo(8), MIN_FILE_SIZE, MIN_FILE_SIZE, 1, PATH));
  }

  @Test
  void shouldRemoveAVersionOlderThanThePeriod() {
    Map<String, Long> versions = versions(monthsAgo(12));

    assertEquals(List.of("1"), select(versions), "A version older than the 6-month period goes, by its OWN date");
  }

  @Test
  void shouldKeepAVersionInsideThePeriodWhenTheCountIsWithinTheCap() {
    Map<String, Long> versions = versions(monthsAgo(3), monthsAgo(2), monthsAgo(1));

    assertTrue(select(versions).isEmpty(), "3 versions inside the period, cap 5: nothing to purge");
  }

  @Test
  void shouldUnionTheAgeRuleAndTheCountRule() {
    // 3 versions past the period + 7 inside it, cap 5: the age rule takes the 3,
    // then the count rule takes 2 more from the 7 survivors — and what is left
    // is exactly the cap, all of it inside the period
    Map<String, Long> versions = versions(monthsAgo(12),
                                          monthsAgo(11),
                                          monthsAgo(10),
                                          monthsAgo(5),
                                          monthsAgo(5),
                                          monthsAgo(4),
                                          monthsAgo(3),
                                          monthsAgo(2),
                                          monthsAgo(2),
                                          monthsAgo(1));

    List<String> removed = select(versions);

    assertEquals(List.of("1", "2", "3", "4", "5"), removed, "Aged ones first, then the oldest survivors, OLDEST FIRST");
    assertEquals(5, versions.size() - 2 - removed.size(), "5 survivors past the root and base versions: exactly the cap");
  }

  @Test
  void shouldRemoveTheAgedVersionsTheOldCountRuleSkippedEntirely() {
    // THE PO's actual ask: 3 aged versions on a file whose count (3) sits WITHIN
    // the cap (5). The count rule alone saw nothing here and skipped the file
    Map<String, Long> versions = versions(monthsAgo(36), monthsAgo(24), monthsAgo(12));

    assertEquals(List.of("1", "2", "3"), select(versions));
  }

  @Test
  void shouldRemoveTheTenVersionsOfAFileWhoseWholeHistoryPredatesThePeriod() {
    Map<String, Long> versions = new LinkedHashMap<>();
    versions.put(ROOT_VERSION, monthsAgo(120));
    for (int i = 1; i <= 10; i++) {
      versions.put(String.valueOf(i), monthsAgo(30 - i));
    }
    versions.put(BASE_VERSION, monthsAgo(1));

    assertEquals(10, select(versions).size(), "Every version past the period goes, cap or no cap");
  }

  @Test
  void shouldTrimAFileOfFortyRecentVersionsDownToTheCap() {
    // THE OnlyOffice case, and the reason the scan query is NOT narrowed on
    // jcr:created (see CleanupJcrStorage#buildScanQuery): every one of these
    // versions is inside the period, so the age rule alone would spare the file
    Map<String, Long> versions = new LinkedHashMap<>();
    versions.put(ROOT_VERSION, monthsAgo(1));
    for (int i = 1; i <= 40; i++) {
      versions.put(String.valueOf(i), NOW - (41L - i) * 3600000L);
    }
    versions.put(BASE_VERSION, NOW);

    List<String> removed = select(versions);

    assertEquals(35, removed.size(), "40 versions inside the period, cap 5: the 35 oldest go");
    assertEquals(List.of("1", "2", "3"), removed.subList(0, 3), "Oldest first");
    assertFalse(removed.contains("36"), "The 5 newest survive: 36 to 40");
    assertFalse(removed.contains("40"));
  }

  @Test
  void shouldNeverRemoveTheBaseVersionEvenWhenItIsOlderThanThePeriod() {
    // The base version is the file's CURRENT content, not an old revision:
    // reclaiming it would destroy the file, which is the DELETE action's job
    Map<String, Long> versions = new LinkedHashMap<>();
    versions.put(ROOT_VERSION, monthsAgo(120));
    versions.put(BASE_VERSION, monthsAgo(24));
    versions.put("1", monthsAgo(12));

    assertEquals(List.of("1"), select(versions), "The 24-month-old BASE version stays, the 12-month-old one goes");
  }

  @Test
  void shouldNeverRemoveTheRootVersion() {
    Map<String, Long> versions = versions(monthsAgo(12), monthsAgo(11));

    assertFalse(select(versions).contains(ROOT_VERSION), "jcr:rootVersion is skipped BY NAME, and JCR forbids it anyway");
  }

  @Test
  void shouldNotRemoveAVersionOfUnknownCreationDateByAge() {
    // Doubt favours the file, exactly as an unknown jcr:created does for DELETE
    Map<String, Long> versions = versions(0L);

    assertTrue(select(versions).isEmpty());
  }

  @Test
  void shouldReturnAnEmptyRemovalSetForAFileCarryingNoVersionPastItsBase() {
    Map<String, Long> versions = new LinkedHashMap<>();
    versions.put(ROOT_VERSION, monthsAgo(120));
    versions.put(BASE_VERSION, monthsAgo(120));

    assertTrue(select(versions).isEmpty());
    assertTrue(CleanupCriterionEvaluator.selectVersionsToRemove(Map.of(), ROOT_VERSION, BASE_VERSION, params(List.of()), NOW)
                                        .isEmpty());
  }

  @Test
  void shouldNeverReturnCandidateWhenUnderExcludedPrefix() {
    assertNull(evaluate(monthsAgo(12), monthsAgo(8), MIN_FILE_SIZE, 0, 0, PATH, params(List.of("/Groups/spaces/marketing"))));
  }

  @Test
  void shouldNotExcludeSiblingPathSharingThePrefixString() {
    assertEquals(CleanupAction.DELETE,
                 evaluate(monthsAgo(12), monthsAgo(8), MIN_FILE_SIZE, 0, 0, PATH, params(List.of("/Groups/spaces/market"))));
  }

  @Test
  void shouldExcludeExactPathAndTolerateTrailingSlashInPrefix() {
    assertNull(evaluate(monthsAgo(12),
                        monthsAgo(8),
                        MIN_FILE_SIZE,
                        0,
                        0,
                        PATH,
                        params(List.of("/Groups/spaces/marketing/Documents/report.pdf/"))));
  }

  private CleanupAction evaluate(long createdTime,
                                 long lastModifiedTime,
                                 long fileSize,
                                 long purgeableVersionsSize,
                                 int purgeableVersionCount,
                                 String path) {
    return evaluate(createdTime, lastModifiedTime, fileSize, purgeableVersionsSize, purgeableVersionCount, path, params(List.of()));
  }

  /**
   * The evaluator has ONE entry point, the lazy one: these tests already hold
   * both sizes, so they hand them over as {@code () -> size} suppliers. There is
   * deliberately no eager facade to call instead — its stated audience ('a
   * caller already holding both sizes') could not exist in production, the
   * purgeable COUNT it also takes being producible only by a caller that has
   * already walked the version history, i.e. one holding the removal set and
   * wanting {@code evaluateLazily} anyway.
   */
  private CleanupAction evaluate(long createdTime, // NOSONAR
                                 long lastModifiedTime,
                                 long fileSize,
                                 long purgeableVersionsSize,
                                 int purgeableVersionCount,
                                 String path,
                                 CleanupParams params) {
    return CleanupCriterionEvaluator.evaluateLazily(createdTime,
                                                    lastModifiedTime,
                                                    purgeableVersionCount,
                                                    path,
                                                    params,
                                                    NOW,
                                                    () -> fileSize,
                                                    () -> purgeableVersionsSize);
  }

  private List<String> select(Map<String, Long> versionCreationDates) {
    return CleanupCriterionEvaluator.selectVersionsToRemove(versionCreationDates,
                                                            ROOT_VERSION,
                                                            BASE_VERSION,
                                                            params(List.of()),
                                                            NOW);
  }

  /**
   * A version history reduced to what the removal policy reads: the root
   * version, one version named after its 1-based index per given creation date,
   * and a RECENT base version — so a test states nothing but the versions whose
   * fate it is about.
   */
  private Map<String, Long> versions(long... creationDates) {
    Map<String, Long> versionCreationDates = new LinkedHashMap<>();
    versionCreationDates.put(ROOT_VERSION, monthsAgo(120));
    for (int i = 0; i < creationDates.length; i++) {
      versionCreationDates.put(String.valueOf(i + 1), creationDates[i]);
    }
    versionCreationDates.put(BASE_VERSION, NOW);
    return versionCreationDates;
  }

  private CleanupParams params(List<String> excludedPaths) {
    return new CleanupParams(6, MIN_FILE_SIZE, 7, 5, excludedPaths, 200, null);
  }

  private long monthsAgo(int months) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(NOW);
    calendar.add(Calendar.MONTH, -months);
    return calendar.getTimeInMillis();
  }

}
