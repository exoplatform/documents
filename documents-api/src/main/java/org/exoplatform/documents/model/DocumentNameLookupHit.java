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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A file that a name matches in another add-on's data.
 *
 * @param fileId the file's node identifier
 * @param matchedBy what the name matched, in the user's language, such as
 *          « form QA-92501 Contract »
 * @param values what that data holds for the file, label to value as plain
 *          text, in display order; empty when it shows nothing more
 */
public record DocumentNameLookupHit(String fileId, String matchedBy, Map<String, String> values) {

  /**
   * Copies the values, so the record stays immutable.
   *
   * @param fileId the file's node identifier
   * @param matchedBy what the name matched
   * @param values what that data holds for the file
   */
  public DocumentNameLookupHit {
    values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

}
