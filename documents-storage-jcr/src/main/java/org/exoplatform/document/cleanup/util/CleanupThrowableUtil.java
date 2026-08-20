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

import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Renders a purge failure into the compact diagnostic persisted in the item's
 * FAILURE_DETAIL column — never a full {@code printStackTrace}.
 * <p>
 * TWO links of the cause chain are kept, and two only: the HEAD exception (the
 * one thrown) and the DEEPEST cause (the root cause). In ~99% of diagnoses
 * those are the two that matter; every link in between repeats the same
 * wrapping story and is redundant bulk. The intermediate links are never
 * dropped SILENTLY though — their number is stated on its own line, so a reader
 * knows exactly what they are not being shown.
 * <p>
 * Everything here is bounded on purpose. A deep JCR failure carrying an
 * embedded SQL error runs to hundreds of KILOBYTES; at 800 GB of documents and
 * hundreds of thousands of items per campaign, persisting that verbatim is a
 * storage problem of its own. Hence {@link #MAX_FRAMES} frames per block and,
 * as the LAST step of the rendering — so no long single message can bypass it —
 * a hard {@link #MAX_DETAIL_LENGTH} character cap.
 * <p>
 * The chain WALK itself is delegated to commons-lang3 ({@link ExceptionUtils}),
 * which carries a visited-set guard: a CYCLIC chain — A caused by B caused by A,
 * which a re-wrapping layer can genuinely build — is cut, not spun on. A
 * hand-rolled walk guarding only the self-cause spun forever on such a cycle,
 * and it spun inside the purge worker, on a thread nothing interrupts. Only the
 * frame rendering and the caps are ours.
 */
public class CleanupThrowableUtil {

  /** Stack frames kept per rendered block, the rest being counted, not shown. */
  public static final int     MAX_FRAMES        = 12;

  /** Hard cap of the WHOLE rendered detail, enforced last. */
  public static final int     MAX_DETAIL_LENGTH = 8000;

  private static final String TRUNCATED_MARKER  = "\n... [truncated]";

  private static final String ROOT_CAUSE_PREFIX = "Caused by (root cause): ";

  private CleanupThrowableUtil() {
    // static utility
  }

  /**
   * Compact diagnostic of a failure: the head exception block, the omitted
   * intermediate-cause count when the chain had any, then the root-cause block —
   * the whole thing capped at {@link #MAX_DETAIL_LENGTH} characters.
   *
   * @param throwable failure to render, may be null
   * @return the diagnostic to persist, or null when the throwable is null
   */
  public static String formatFailureDetail(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    StringBuilder detail = new StringBuilder();
    appendBlock(detail, "", throwable);
    Throwable rootCause = ExceptionUtils.getRootCause(throwable);
    if (rootCause == null) {
      // A cause-less throwable: older commons-lang3 answers null there, newer
      // ones answer the throwable itself. Normalized, because the identity
      // comparison below is what decides whether a root block is rendered at all
      rootCause = throwable;
    }
    if (rootCause != throwable) {
      // Two links are always rendered — the head and the root — so the omitted
      // ones are what the chain holds beyond them. Never negative here: a chain
      // reaching this branch holds at least those two
      int intermediateCount = ExceptionUtils.getThrowableCount(throwable) - 2;
      if (intermediateCount > 0) {
        // Never a silent drop: the reader is told how many links they are not
        // being shown
        detail.append("\n... ").append(intermediateCount).append(" intermediate cause(s) omitted");
      }
      detail.append('\n');
      appendBlock(detail, ROOT_CAUSE_PREFIX, rootCause);
    }
    return truncate(detail.toString());
  }

  /**
   * One rendered block: the fully qualified exception class, its message, then
   * at most {@link #MAX_FRAMES} frames — followed by the number of frames cut,
   * for the same no-silent-truncation reason as the omitted causes.
   */
  private static void appendBlock(StringBuilder detail, String prefix, Throwable throwable) {
    detail.append(prefix).append(throwable.getClass().getName()).append(": ").append(throwable.getMessage());
    StackTraceElement[] frames = throwable.getStackTrace();
    int shownFrames = Math.min(frames.length, MAX_FRAMES);
    for (int i = 0; i < shownFrames; i++) {
      detail.append("\n\tat ").append(frames[i]);
    }
    if (frames.length > shownFrames) {
      detail.append("\n... ").append(frames.length - shownFrames).append(" more frames");
    }
  }

  /**
   * The LAST step of the rendering, so nothing — not even a single message
   * longer than the whole budget — can reach the column uncapped. The marker
   * itself fits INSIDE the cap: a truncated detail is never longer than an
   * untruncated one.
   */
  private static String truncate(String detail) {
    if (detail.length() <= MAX_DETAIL_LENGTH) {
      return detail;
    }
    return detail.substring(0, MAX_DETAIL_LENGTH - TRUNCATED_MARKER.length()) + TRUNCATED_MARKER;
  }

}
