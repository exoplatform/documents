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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Failure-detail rendering tests: the TWO-LINK cause chain (head + deepest
 * cause), the honesty markers that must accompany every drop (omitted
 * intermediate causes, cut frames, truncated detail) and the hard length cap.
 */
class CleanupThrowableUtilTest {

  private static final String ROOT_MESSAGE = "ORA-01461: value too large for column";

  private static final String HEAD_MESSAGE = "Error while saving the node";

  @Test
  void formatFailureDetailIsNullSafe() {
    // A null throwable must never render "null" into the column: the item simply
    // has no detail
    assertNull(CleanupThrowableUtil.formatFailureDetail(null));
  }

  @Test
  void formatFailureDetailRendersTheHeadOnlyWithoutACause() {
    String detail = CleanupThrowableUtil.formatFailureDetail(thrown(new IllegalStateException(HEAD_MESSAGE)));

    assertTrue(detail.startsWith("java.lang.IllegalStateException: " + HEAD_MESSAGE),
               "The block must open with the FULLY QUALIFIED class and the message: " + detail);
    assertFalse(detail.contains("Caused by"), "With no cause there is no second block: " + detail);
    assertFalse(detail.contains("intermediate cause(s) omitted"), "Nothing was omitted: " + detail);
  }

  @Test
  void formatFailureDetailRendersTheHeadAndTheRootWithoutADuplicateBlock() {
    IllegalStateException head = thrown(new IllegalStateException(HEAD_MESSAGE, new IllegalArgumentException(ROOT_MESSAGE)));

    String detail = CleanupThrowableUtil.formatFailureDetail(head);

    assertTrue(detail.startsWith("java.lang.IllegalStateException: " + HEAD_MESSAGE), detail);
    assertTrue(detail.contains("Caused by (root cause): java.lang.IllegalArgumentException: " + ROOT_MESSAGE),
               "The second block must be LABELLED as the root cause: " + detail);
    assertEquals(1,
                 countOf(detail, "java.lang.IllegalStateException: " + HEAD_MESSAGE),
                 "A single-link chain must not render the head twice");
    assertFalse(detail.contains("intermediate cause(s) omitted"),
                "Head and root are adjacent: there is no intermediate link to report on: " + detail);
  }

  @Test
  void formatFailureDetailStatesHowManyIntermediateCausesWereDropped() {
    // Deliberately NOT rendered: the two middle links repeat the same wrapping
    // story. Deliberately COUNTED: a silent drop would leave a reader unable to
    // tell a two-link chain from a five-link one
    Throwable root = new IllegalArgumentException(ROOT_MESSAGE);
    Throwable head = thrown(new IllegalStateException(HEAD_MESSAGE,
                                                     new RuntimeException("second wrapper",
                                                                          new RuntimeException("first wrapper", root))));

    String detail = CleanupThrowableUtil.formatFailureDetail(head);

    assertTrue(detail.contains("... 2 intermediate cause(s) omitted"),
               "The two dropped links must be counted, never silently dropped: " + detail);
    assertFalse(detail.contains("first wrapper"), "An intermediate link is not rendered: " + detail);
    assertFalse(detail.contains("second wrapper"), "An intermediate link is not rendered: " + detail);
    assertTrue(detail.contains("Caused by (root cause): java.lang.IllegalArgumentException: " + ROOT_MESSAGE), detail);
  }

  @Test
  void formatFailureDetailKeepsAtMostMaxFramesAndCountsTheRest() {
    IllegalStateException head = new IllegalStateException(HEAD_MESSAGE);
    int frameCount = CleanupThrowableUtil.MAX_FRAMES + 7;
    head.setStackTrace(frames(frameCount));

    String detail = CleanupThrowableUtil.formatFailureDetail(head);

    assertEquals(CleanupThrowableUtil.MAX_FRAMES,
                 countOf(detail, "\n\tat "),
                 "Exactly MAX_FRAMES frames must be rendered, no more: " + detail);
    assertTrue(detail.contains("... 7 more frames"), "The cut frames must be counted, never silently dropped: " + detail);
    assertTrue(detail.contains("at org.exoplatform.Frame0.run(Frame0.java:0)"), "The TOP frames are the ones kept: " + detail);
    assertFalse(detail.contains("Frame" + (frameCount - 1) + "."), "The deepest frames are the ones cut: " + detail);
  }

  @Test
  void formatFailureDetailRendersEveryFrameWithoutAMoreFramesMarker() {
    IllegalStateException head = new IllegalStateException(HEAD_MESSAGE);
    head.setStackTrace(frames(CleanupThrowableUtil.MAX_FRAMES));

    String detail = CleanupThrowableUtil.formatFailureDetail(head);

    assertEquals(CleanupThrowableUtil.MAX_FRAMES, countOf(detail, "\n\tat "));
    assertFalse(detail.contains("more frames"), "Nothing was cut: the marker must not appear: " + detail);
  }

  @Test
  void formatFailureDetailCapsTheWholeDetailEvenOnASingleHugeMessage() {
    // The cap is the LAST step of the rendering precisely so a single enormous
    // message — a JCR failure carrying an embedded SQL error runs to hundreds of
    // KB — cannot bypass it. At hundreds of thousands of items per campaign this
    // is a storage problem, not a cosmetic one
    IllegalStateException head = new IllegalStateException("x".repeat(CleanupThrowableUtil.MAX_DETAIL_LENGTH * 3));
    head.setStackTrace(frames(2));

    String detail = CleanupThrowableUtil.formatFailureDetail(head);

    assertEquals(CleanupThrowableUtil.MAX_DETAIL_LENGTH,
                 detail.length(),
                 "The capped detail must be EXACTLY the cap, marker included");
    assertTrue(detail.endsWith("... [truncated]"), "A truncated detail must say so: " + detail);
  }

  @Test
  void formatFailureDetailLeavesAShortDetailUntouched() {
    String detail = CleanupThrowableUtil.formatFailureDetail(thrown(new IllegalStateException(HEAD_MESSAGE)));

    assertTrue(detail.length() <= CleanupThrowableUtil.MAX_DETAIL_LENGTH);
    assertFalse(detail.contains("[truncated]"), "Nothing was truncated: the marker must not appear: " + detail);
  }

  @Test
  void formatFailureDetailSurvivesASelfReferencingCause() {
    // A cause chain looping on itself would spin the root-cause walk forever
    IllegalStateException head = new IllegalStateException(HEAD_MESSAGE) {
      private static final long serialVersionUID = 1L;

      @Override
      public synchronized Throwable getCause() {
        return this;
      }
    };
    head.setStackTrace(frames(1));

    String detail = CleanupThrowableUtil.formatFailureDetail(head);

    assertTrue(detail.contains(HEAD_MESSAGE), detail);
    assertFalse(detail.contains("Caused by"), "A self-referencing cause is not a second link: " + detail);
  }

  /**
   * Pins a REPRODUCIBLE stack trace on a throwable: the real one depends on the
   * JUnit runner's own frames, which no assertion may depend on.
   */
  private <T extends Throwable> T thrown(T throwable) {
    throwable.setStackTrace(frames(3));
    return throwable;
  }

  private StackTraceElement[] frames(int count) {
    StackTraceElement[] frames = new StackTraceElement[count];
    for (int i = 0; i < count; i++) {
      frames[i] = new StackTraceElement("org.exoplatform.Frame" + i, "run", "Frame" + i + ".java", i);
    }
    return frames;
  }

  private int countOf(String value, String token) {
    return value.split(java.util.regex.Pattern.quote(token), -1).length - 1;
  }

}
