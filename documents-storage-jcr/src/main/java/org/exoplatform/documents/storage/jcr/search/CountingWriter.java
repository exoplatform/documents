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
package org.exoplatform.documents.storage.jcr.search;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Writer;

/**
 * A writer counting the characters written, refusing any past a limit or a
 * deadline, so a huge or slow file stops being read at either.
 */
class CountingWriter extends Writer {

  private final Writer out;

  private final long   maxChars;

  private final long   deadlineNanos;

  private long         count;

  private boolean      truncated;

  /**
   * Wraps a writer.
   *
   * @param out the writer receiving the text
   * @param maxChars the most characters written
   * @param deadlineNanos the {@link System#nanoTime()} past which writing fails
   */
  CountingWriter(Writer out, long maxChars, long deadlineNanos) {
    this.out = out;
    this.maxChars = maxChars;
    this.deadlineNanos = deadlineNanos;
  }

  /**
   * Tells how many characters were written.
   *
   * @return the count
   */
  long count() {
    return count;
  }

  /**
   * Tells whether text was refused for being past the limit.
   *
   * @return true when the text written is cut
   */
  boolean isTruncated() {
    return truncated;
  }

  /**
   * Fails once the deadline is passed or the thread is interrupted, so a walk
   * of a file that writes no text stops at the deadline too.
   *
   * @throws InterruptedIOException when the deadline is passed or the thread
   *           is interrupted
   */
  void checkDeadline() throws InterruptedIOException {
    if (System.nanoTime() > deadlineNanos) {
      throw new InterruptedIOException("Reading the file took too long");
    }
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedIOException("Text extraction interrupted");
    }
  }

  /**
   * Writes characters up to the limit; past it, writes what fits and fails
   * with {@link TextLimitReachedException}.
   *
   * @param chars the characters
   * @param offset the first one
   * @param length how many
   * @throws IOException when the limit or the deadline is passed, or the
   *           writer fails
   */
  @Override
  public void write(char[] chars, int offset, int length) throws IOException {
    checkDeadline();
    long room = maxChars - count;
    int written = (int) Math.min(room, length);
    if (written > 0) {
      out.write(chars, offset, written);
      count += written;
    }
    if (written < length) {
      truncated = true;
      throw new TextLimitReachedException();
    }
  }

  /**
   * Flushes the writer.
   *
   * @throws IOException when it fails
   */
  @Override
  public void flush() throws IOException {
    out.flush();
  }

  /**
   * Closes the writer.
   *
   * @throws IOException when it fails
   */
  @Override
  public void close() throws IOException {
    out.close();
  }
}
