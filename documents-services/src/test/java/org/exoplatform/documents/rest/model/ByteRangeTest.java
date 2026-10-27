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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Pins how a Range header is read against the size of a content.
 */
class ByteRangeTest {

  /**
   * A closed range is served as asked, its end bounded by the content.
   */
  @Test
  void closedRangeIsServedWithinTheContent() {
    assertEquals(new ByteRange(2, 5), ByteRange.parse("bytes=2-5", 10));
    assertEquals(new ByteRange(2, 9), ByteRange.parse("bytes=2-50", 10));
    assertEquals(4, ByteRange.parse("bytes=2-5", 10).length());
    assertEquals(new ByteRange(9, 9), ByteRange.parse("bytes=9-9", 10));
  }

  /**
   * An open range runs to the end, a suffix range takes the last bytes.
   */
  @Test
  void openAndSuffixRangesRunToTheEnd() {
    assertEquals(new ByteRange(3, 9), ByteRange.parse("bytes=3-", 10));
    assertEquals(new ByteRange(0, 9), ByteRange.parse("bytes=0-", 10));
    assertEquals(new ByteRange(7, 9), ByteRange.parse("bytes=-3", 10));
    assertEquals(new ByteRange(0, 9), ByteRange.parse("bytes=-30", 10));
  }

  /**
   * A range starting at or beyond the end, or an empty suffix, cannot be
   * served.
   */
  @Test
  void rangeBeyondTheContentIsNotSatisfiable() {
    assertSame(ByteRange.NOT_SATISFIABLE, ByteRange.parse("bytes=10-", 10));
    assertSame(ByteRange.NOT_SATISFIABLE, ByteRange.parse("bytes=12-20", 10));
    assertSame(ByteRange.NOT_SATISFIABLE, ByteRange.parse("bytes=-0", 10));
  }

  /**
   * No header, an empty content, a malformed header or several ranges serve
   * the whole content.
   */
  @Test
  void anythingElseServesTheWholeContent() {
    assertNull(ByteRange.parse(null, 10));
    assertNull(ByteRange.parse("bytes=0-1", 0));
    assertNull(ByteRange.parse("bytes=5-2", 10));
    assertNull(ByteRange.parse("bytes=-", 10));
    assertNull(ByteRange.parse("items=0-1", 10));
    assertNull(ByteRange.parse("bytes=0-1,4-5", 10));
    assertNull(ByteRange.parse("bytes=99999999999999999999-", 10));
  }

}
