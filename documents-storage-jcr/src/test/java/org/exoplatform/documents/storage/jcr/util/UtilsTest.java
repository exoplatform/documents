/*
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.storage.jcr.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class UtilsTest {

  @Test
  public void decodeUrlPreservingPlusKeepsLiteralPlus() {
    // A literal "+" must survive: plain URLDecoder would turn it into a space.
    assertEquals("test + test.test.pdf", Utils.decodeUrlPreservingPlus("test%20%2B%20test.test.pdf"));
    assertEquals("a+b", Utils.decodeUrlPreservingPlus("a+b"));
  }

  @Test
  public void decodeUrlPreservingPlusStillDecodesPercentSequences() {
    assertEquals("report[1].pdf", Utils.decodeUrlPreservingPlus("report%5b1%5d.pdf"));
    assertEquals("é à.pdf", Utils.decodeUrlPreservingPlus("%C3%A9%20%C3%A0.pdf"));
  }

  @Test
  public void decodeUrlPreservingPlusIsIdempotentForPlus() {
    // The WebDAV path pipeline decodes segments at several layers; decoding an
    // already-decoded value must not corrupt the "+".
    String once = Utils.decodeUrlPreservingPlus("test%20%2B%20test.test.pdf");
    assertEquals("test + test.test.pdf", once);
    assertEquals("test + test.test.pdf", Utils.decodeUrlPreservingPlus(once));
  }

  @Test
  public void decodeUrlPreservingPlusHandlesNull() {
    assertNull(Utils.decodeUrlPreservingPlus(null));
  }
}
