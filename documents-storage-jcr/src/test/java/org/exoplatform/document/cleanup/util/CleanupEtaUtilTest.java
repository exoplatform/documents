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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Direct arithmetic tests of the workers' ETA. Both the scan and the execution
 * used to carry this computation inline and verbatim, and every caller-side
 * assertion matched it with {@code anyLong()}: replacing the whole formula with a
 * constant kept the suite green. It is now ONE function, pinned here with exact
 * expected values.
 */
class CleanupEtaUtilTest {

  private static final long START = 1_700_000_000_000L;

  @Test
  void etaExtrapolatesTheRemainingItemsFromTheThroughputOfThisRun() {
    // 2 of 10 items in 4 s -> 0.5 item/s -> the remaining 8 take 16 s
    assertEquals(16, CleanupEtaUtil.computeEtaSeconds(START, 0, 2, 10, START + 4000));
    // Half the throughput doubles the estimate...
    assertEquals(32, CleanupEtaUtil.computeEtaSeconds(START, 0, 2, 10, START + 8000));
    // ...and twice the remaining work doubles it too
    assertEquals(32, CleanupEtaUtil.computeEtaSeconds(START, 0, 2, 18, START + 4000));
  }

  @Test
  void etaMeasuresTheThroughputSinceThisRunStartedNeverSinceTheCampaignStarted() {
    // A resumed worker restarted at 100 already-processed items and has done 2
    // more in 4 s: the estimate for the remaining 8 must be those 16 s, NOT the
    // 102-items-in-4-s throughput the campaign counters alone would suggest
    assertEquals(16, CleanupEtaUtil.computeEtaSeconds(START, 100, 102, 110, START + 4000));
  }

  @Test
  void etaIsZeroWhileTheThroughputIsNotMeasurableYet() {
    // No time elapsed yet (or a clock that went backwards)
    assertEquals(0, CleanupEtaUtil.computeEtaSeconds(START, 0, 2, 10, START));
    assertEquals(0, CleanupEtaUtil.computeEtaSeconds(START, 0, 2, 10, START - 1000));
    // Nothing processed since this run started: the very first batch of a
    // resumed worker, whose progress still equals the persisted counter
    assertEquals(0, CleanupEtaUtil.computeEtaSeconds(START, 100, 100, 110, START + 4000));
  }

  @Test
  void etaIsZeroOnceTheDenominatorIsReachedInsteadOfGoingNegative() {
    assertEquals(0, CleanupEtaUtil.computeEtaSeconds(START, 0, 10, 10, START + 4000));
    // A denominator counted before the walk can be passed by the walk itself:
    // that must read as 'done', never as a negative remaining time
    assertEquals(0, CleanupEtaUtil.computeEtaSeconds(START, 0, 12, 10, START + 4000));
  }

  @Test
  void etaTruncatesToWholeSecondsRatherThanRounding() {
    // 2 of 3 in 4 s -> the remaining 1 takes 2 s
    assertEquals(2, CleanupEtaUtil.computeEtaSeconds(START, 0, 2, 3, START + 4000));
    // 2 of 3 in 1 s -> 500 ms left, truncated to 0 s: a sub-second ETA is
    // displayed as 'no time left', never rounded up to a whole second
    assertEquals(0, CleanupEtaUtil.computeEtaSeconds(START, 0, 2, 3, START + 1000));
  }

  @Test
  void theClockDefaultingOverloadEvaluatesAgainstTheCurrentServerTime() {
    // The overload the workers actually call: seeded 4 s in the past, 2 of 10
    // processed -> the same ~16 s as the explicit-clock variant above. Bounded
    // rather than exact, the elapsed time being the real one (a slow agent adds
    // 4 ms of ETA per elapsed millisecond)
    long etaSeconds = CleanupEtaUtil.computeEtaSeconds(System.currentTimeMillis() - 4000, 0, 2, 10);

    assertTrue(etaSeconds >= 16 && etaSeconds <= 18,
               "A run seeded 4 s in the past with 2 of 10 items done must estimate ~16 s, got " + etaSeconds);
  }

}
