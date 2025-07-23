/**
 * Copyright (C) 2025 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
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
package org.exoplatform.documents.webdav.model;

import java.io.IOException;
import java.io.InputStream;

public class RangedInputStream extends InputStream {

  private InputStream inputStream;

  private long        end;

  private long        start = 0;

  public RangedInputStream(InputStream inputStream, long startRange, long endRange) throws IOException {
    this.inputStream = inputStream;
    this.end = endRange;
    this.start = inputStream.skip(startRange);
  }

  @Override
  public int read() throws IOException {
    if (start > end) {
      return -1;
    }
    int curReaded = inputStream.read();
    if (curReaded >= 0) {
      start++;
    }
    return curReaded;
  }

  @Override
  public int read(byte[] buffer) throws IOException {
    return read(buffer, 0, buffer.length);
  }

  @Override
  public int read(byte[] buffer, int offset, int size) throws IOException {
    long needsToRead = size;
    if (needsToRead > (end - start + 1)) {
      needsToRead = end - start + 1;
    }
    if (needsToRead == 0) {
      return -1;
    }
    int curReaded = inputStream.read(buffer, offset, (int) needsToRead);
    start += curReaded;
    return curReaded;
  }

  @Override
  public long skip(long skipVal) throws IOException {
    return inputStream.skip(skipVal);
  }

  @Override
  public int available() throws IOException {
    return inputStream.available();
  }

  @Override
  public void close() throws IOException {
    inputStream.close();
  }

  @Override
  public synchronized void mark(int markVal) {
    inputStream.mark(markVal);
  }

  @Override
  public synchronized void reset() throws IOException {
    inputStream.reset();
  }

  @Override
  public boolean markSupported() {
    return inputStream.markSupported();
  }

}
