/*
 * Copyright (C) 2026 eXo Platform SAS
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
package org.exoplatform.documents.model;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * The content of a folder prepared for a download, zipped while it is written:
 * no zip is built before the first byte is sent, and none is held in memory.
 * Closing it deletes what it prepared; the caller closes it, whether it was
 * written or not.
 */
public interface FolderArchive extends Closeable {

  /**
   * Writes the folder as a zip. The stream is finished, not closed.
   *
   * @param outputStream where the zip is written
   * @throws IOException when the content or the stream cannot be read or
   *           written
   */
  void writeZip(OutputStream outputStream) throws IOException;

}
