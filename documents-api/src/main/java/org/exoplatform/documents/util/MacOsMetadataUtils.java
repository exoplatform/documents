/*
 * Copyright (C) 2026 eXo Platform SAS.
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
package org.exoplatform.documents.util;

/**
 * The macOS metadata names that the Documents import does not create.
 */
public final class MacOsMetadataUtils {

  private static final String MACOS_METADATA_FOLDER      = "__MACOSX";

  private static final String MACOS_FOLDER_SETTINGS_FILE = ".DS_Store";

  private static final String MACOS_APPLE_DOUBLE_PREFIX  = "._";

  private MacOsMetadataUtils() {
    // Utility class
  }

  /**
   * Tells whether a path is macOS metadata rather than user content: a path
   * under a {@code __MACOSX} folder, an AppleDouble {@code ._<name>} file, or a
   * Finder {@code .DS_Store} file. Other dot-files are user content.
   *
   * @param path a non-blank path, with '/' or '\' separators, or a single name
   * @return true when the path is macOS metadata and must not be imported
   */
  public static boolean isMacOsMetadata(String path) {
    String[] segments = path.replace('\\', '/').split("/");
    for (String segment : segments) {
      if (MACOS_METADATA_FOLDER.equals(segment)) {
        return true;
      }
    }
    String fileName = segments.length == 0 ? "" : segments[segments.length - 1];
    return MACOS_FOLDER_SETTINGS_FILE.equals(fileName) || fileName.startsWith(MACOS_APPLE_DOUBLE_PREFIX);
  }

}
