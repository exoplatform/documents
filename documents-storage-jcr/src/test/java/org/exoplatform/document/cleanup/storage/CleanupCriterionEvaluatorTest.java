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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Calendar;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.util.CleanupCriterionEvaluator;

class CleanupCriterionEvaluatorTest {

  private static final long   NOW           = System.currentTimeMillis();

  private static final long   MIN_FILE_SIZE = 1048576L;

  private static final String PATH          = "/Groups/spaces/marketing/Documents/report.pdf";

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
  void shouldReturnPurgeVersionsWhenVersionCountAboveMaxEvenOnRecentFile() {
    assertEquals(CleanupAction.PURGE_VERSIONS,
                 evaluate(monthsAgo(1), monthsAgo(1), MIN_FILE_SIZE, MIN_FILE_SIZE, 6, PATH));
  }

  @Test
  void shouldNotReturnPurgeVersionsWhenVersionsSizeUnderFloor() {
    assertNull(evaluate(monthsAgo(1), monthsAgo(1), MIN_FILE_SIZE, MIN_FILE_SIZE - 1, 6, PATH));
  }

  @Test
  void shouldNotReturnPurgeVersionsWhenVersionCountAtMax() {
    assertNull(evaluate(monthsAgo(1), monthsAgo(1), MIN_FILE_SIZE, MIN_FILE_SIZE, 5, PATH));
  }

  @Test
  void shouldPreferDeleteOverPurgeVersionsWhenBothQualify() {
    assertEquals(CleanupAction.DELETE,
                 evaluate(monthsAgo(12), monthsAgo(8), MIN_FILE_SIZE, MIN_FILE_SIZE, 6, PATH));
  }

  @Test
  void shouldNeverReturnCandidateWhenUnderExcludedPrefix() {
    CleanupParams params = params(List.of("/Groups/spaces/marketing"));
    assertNull(CleanupCriterionEvaluator.evaluate(monthsAgo(12),
                                                  monthsAgo(8),
                                                  MIN_FILE_SIZE,
                                                  0,
                                                  0,
                                                  PATH,
                                                  params,
                                                  NOW));
  }

  @Test
  void shouldNotExcludeSiblingPathSharingThePrefixString() {
    CleanupParams params = params(List.of("/Groups/spaces/market"));
    assertEquals(CleanupAction.DELETE,
                 CleanupCriterionEvaluator.evaluate(monthsAgo(12),
                                                    monthsAgo(8),
                                                    MIN_FILE_SIZE,
                                                    0,
                                                    0,
                                                    PATH,
                                                    params,
                                                    NOW));
  }

  @Test
  void shouldExcludeExactPathAndTolerateTrailingSlashInPrefix() {
    CleanupParams params = params(List.of("/Groups/spaces/marketing/Documents/report.pdf/"));
    assertNull(CleanupCriterionEvaluator.evaluate(monthsAgo(12),
                                                  monthsAgo(8),
                                                  MIN_FILE_SIZE,
                                                  0,
                                                  0,
                                                  PATH,
                                                  params,
                                                  NOW));
  }

  private CleanupAction evaluate(long createdTime,
                                 long lastModifiedTime,
                                 long fileSize,
                                 long versionsSize,
                                 int versionCount,
                                 String path) {
    return CleanupCriterionEvaluator.evaluate(createdTime,
                                              lastModifiedTime,
                                              fileSize,
                                              versionsSize,
                                              versionCount,
                                              path,
                                              params(List.of()),
                                              NOW);
  }

  private CleanupParams params(List<String> excludedPaths) {
    return new CleanupParams(6, MIN_FILE_SIZE, 7, 5, excludedPaths, 200);
  }

  private long monthsAgo(int months) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(NOW);
    calendar.add(Calendar.MONTH, -months);
    return calendar.getTimeInMillis();
  }

}
