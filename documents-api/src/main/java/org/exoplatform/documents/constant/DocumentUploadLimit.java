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
package org.exoplatform.documents.constant;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import org.exoplatform.commons.utils.PropertyManager;

/**
 * The size limit of a file uploaded into Documents: the one the platform's
 * upload forms apply, read from the same property with the same default.
 */
public final class DocumentUploadLimit {

  /** The property holding the limit, in MB. */
  public static final String PROPERTY_NAME          = "exo.social.composer.maxFileSizeInMB";

  /** The limit when the property is not set, in MB. */
  public static final long   DEFAULT_MAX_SIZE_IN_MB = 200;

  /**
   * Not instantiated.
   */
  private DocumentUploadLimit() {
  }

  /**
   * The size limit of an uploaded file.
   *
   * @return the limit in MB: the property's value, or
   *         {@link #DEFAULT_MAX_SIZE_IN_MB} when it is blank, not a number or
   *         not positive
   */
  public static long getMaxSizeInMB() {
    long value = NumberUtils.toLong(StringUtils.trim(PropertyManager.getProperty(PROPERTY_NAME)), 0);
    return value > 0 ? value : DEFAULT_MAX_SIZE_IN_MB;
  }

  /**
   * The size limit of an uploaded file, in bytes.
   *
   * @return {@link #getMaxSizeInMB()} in bytes
   */
  public static long getMaxBytes() {
    return getMaxSizeInMB() * 1024 * 1024;
  }

}
