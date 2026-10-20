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
package org.exoplatform.documents.storage.jcr.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Opens a file's content from its start, as many times as it is read.
 */
@FunctionalInterface
public interface ContentSource {

  /**
   * Opens the content.
   *
   * @return a stream at the start of the content, closed by the caller
   * @throws IOException when the content can't be opened
   */
  InputStream open() throws IOException;

}
