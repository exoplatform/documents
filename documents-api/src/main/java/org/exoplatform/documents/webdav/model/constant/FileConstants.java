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
package org.exoplatform.documents.webdav.model.constant;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FileConstants {

  private FileConstants() {
    // Utils class
  }

  public static final List<String> BLOCKED_FILES         = Arrays.asList("\\.DS_Store",                                                   // NOSONAR
                                                                         "\\._.*",
                                                                         "\\.Spotlight-V100",
                                                                         "\\.Trashes",
                                                                         "\\.fseventsd",
                                                                         "\\.TemporaryItems",
                                                                         "\\.VolumeIcon\\.icns",
                                                                         "\\.DocumentRevisions-V100",
                                                                         "Thumbs\\.db",
                                                                         "ehthumbs\\.db",
                                                                         "desktop\\.ini",
                                                                         "\\.Trash-.*",
                                                                         "\\.lnk",
                                                                         "\\.localized",
                                                                         "\\.directory",
                                                                         "\\.hidden",
                                                                         "\\.cache",
                                                                         "\\.config",
                                                                         "lost\\+found",
                                                                         "pagefile.sys",
                                                                         "hiberfil.sys",
                                                                         "\\$Recycle\\.[Bb]in",
                                                                         "System Volume Information",
                                                                         "\\.nfs.*",
                                                                         "\\.X0-lock",
                                                                         "\\.Xauthority",
                                                                         "\\.gvfs",
                                                                         "\\.bash_history",
                                                                         "\\.zsh_history");

  public static final Pattern      BLOCKED_FILES_PATTERN = Pattern.compile(String.format("(?i)(?:^|/)(?:%s)$",
                                                                                         BLOCKED_FILES.stream()
                                                                                                      .collect(Collectors.joining("|"))));

}
