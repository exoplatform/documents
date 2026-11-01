/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 * This program is free software: you can redistribute it and/or modify
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
package org.exoplatform.documents.constant;

/**
 * What a repository node is, as {@link org.exoplatform.documents.model.DocumentNodeInfo}
 * describes it.
 */
public enum DocumentNodeType {
  /** A file: a node of primary type {@code nt:file}. */
  FILE,
  /** A folder: a node of primary type {@code nt:folder} or {@code nt:unstructured}. */
  FOLDER,
  /** A shortcut: a node of type {@code exo:symlink}. */
  SYMLINK,
  /** Any other node, such as a version or a file's content. */
  OTHER
}
