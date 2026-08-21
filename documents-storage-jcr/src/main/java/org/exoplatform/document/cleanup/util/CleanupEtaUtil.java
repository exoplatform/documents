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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.util;

/**
 * Single definition of the throughput ETA of a cleanup worker, shared by the
 * dry-run scan and the execution purge — the two workers used to carry the very
 * same arithmetic verbatim, so a change (or a regression) in one of them silently
 * diverged from the other.
 * <p>
 * The estimate is deliberately based on the throughput observed SINCE THIS RUN
 * STARTED, not since the campaign started: a resumed worker must not inherit the
 * throughput of the run that was interrupted (a machine that was slow yesterday
 * says nothing about today's remaining time). It is CUMULATIVE over that run and
 * not windowed — it was once described here as 'rolling', which it never was.
 * <p>
 * UNIT-AGNOSTIC on purpose: the arithmetic is 'work done over time elapsed', and
 * the caller chooses what a unit of work is. The scan counts NODES, whose cost is
 * near-uniform. The purge counts BYTES ({@code CleanupExecutionService#purgeEtaSeconds}),
 * because its items differ in cost by orders of magnitude and a count-based
 * average predicts the items already met rather than the ones left.
 */
public class CleanupEtaUtil {

  private CleanupEtaUtil() {
    // static utility
  }

  /**
   * Same as
   * {@link #computeEtaSeconds(long, long, long, long, long)} evaluated against
   * the current server clock.
   *
   * @param startTime epoch millis at which THIS run started
   * @param processedAtStart work already done when this run started (a resumed
   *          worker starts above zero)
   * @param processed work done so far, including {@code processedAtStart}
   * @param total denominator of the run, in the SAME unit
   * @return estimated remaining seconds, 0 when it cannot be estimated yet
   */
  public static long computeEtaSeconds(long startTime, long processedAtStart, long processed, long total) {
    return computeEtaSeconds(startTime, processedAtStart, processed, total, System.currentTimeMillis());
  }

  /**
   * Remaining time of a run, from the throughput measured since it started.
   * Short-circuits to 0 — 'unknown', never a negative or infinite value —
   * whenever the throughput isn't measurable yet: no time elapsed, or nothing
   * processed since the run started (the very first batch of a resumed worker).
   * A numerator that already reached (or passed) the denominator also yields 0.
   *
   * @param startTime epoch millis at which THIS run started
   * @param processedAtStart items already processed when this run started
   * @param processed items processed so far, including {@code processedAtStart}
   * @param total denominator of the run
   * @param nowMillis evaluation time (epoch millis)
   * @return estimated remaining seconds, 0 when it cannot be estimated yet
   */
  public static long computeEtaSeconds(long startTime, long processedAtStart, long processed, long total, long nowMillis) {
    long elapsedMillis = nowMillis - startTime;
    long processedSinceStart = processed - processedAtStart;
    if (elapsedMillis <= 0 || processedSinceStart <= 0 || processed >= total) {
      return 0;
    }
    double throughputPerMilli = (double) processedSinceStart / elapsedMillis;
    return (long) ((total - processed) / throughputPerMilli / 1000);
  }

}
