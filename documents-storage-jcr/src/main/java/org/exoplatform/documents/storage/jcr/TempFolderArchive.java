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
package org.exoplatform.documents.storage.jcr;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

import org.exoplatform.documents.model.FolderArchive;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;

/**
 * A folder copied into a temp folder of its own, zipped while it is written:
 * two downloads of the same folder at once never share it, and closing it
 * deletes the copy.
 */
final class TempFolderArchive implements FolderArchive {

  private final Path folder;

  TempFolderArchive(Path folder) {
    this.folder = folder;
  }

  @Override
  public void writeZip(OutputStream outputStream) throws IOException {
    JCRDocumentsUtil.zipFiles(folder.toFile(), outputStream);
  }

  @Override
  public void close() {
    JCRDocumentsUtil.cleanFiles(folder.toFile());
  }

  Path getFolder() {
    return folder;
  }

}
