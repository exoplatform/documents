/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Parameters of a cleanup campaign. Boxed types allow partial overrides: a null
 * field means 'use the platform default'. An effective params object (as
 * snapshot at campaign launch) has all fields set.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupParams {

  private Integer      periodMonths;

  private Long         minFileSizeBytes;

  private Integer      graceDays;

  private Integer      maxVersionsPerFile;

  private List<String> excludedPaths;

  private Integer      batchSize;

}
