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
package org.exoplatform.documents.rest.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A single byte range of a content, as asked by an HTTP Range header, its
 * bounds included.
 *
 * @param start the first byte served
 * @param end the last byte served
 */
public record ByteRange(long start, long end) {

  /** A range that lies wholly outside the content, answered with a 416. */
  public static final ByteRange NOT_SATISFIABLE = new ByteRange(-1, -1);

  private static final Pattern  SINGLE_RANGE    = Pattern.compile("^bytes=(\\d*)-(\\d*)$");

  /**
   * Reads the Range header of a request against the size of the content it
   * asks for.
   *
   * @param header the Range header, or null
   * @param size the size of the content, in bytes
   * @return the range to serve; {@link #NOT_SATISFIABLE} when it starts beyond
   *         the content; null when the whole content is to be served: no
   *         header, an empty content, a malformed header or several ranges
   */
  public static ByteRange parse(String header, long size) {
    if (header == null || size <= 0) {
      return null;
    }
    Matcher matcher = SINGLE_RANGE.matcher(header.trim());
    if (!matcher.matches() || (matcher.group(1).isEmpty() && matcher.group(2).isEmpty())) {
      return null;
    }
    try {
      if (matcher.group(1).isEmpty()) {
        long suffix = Long.parseLong(matcher.group(2));
        return suffix == 0 ? NOT_SATISFIABLE : new ByteRange(Math.max(0, size - suffix), size - 1);
      }
      long start = Long.parseLong(matcher.group(1));
      long end = matcher.group(2).isEmpty() ? Long.MAX_VALUE : Long.parseLong(matcher.group(2));
      if (end < start) {
        return null;
      }
      return start >= size ? NOT_SATISFIABLE : new ByteRange(start, Math.min(end, size - 1));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * The number of bytes the range serves.
   *
   * @return the range's length
   */
  public long length() {
    return end - start + 1;
  }

}
