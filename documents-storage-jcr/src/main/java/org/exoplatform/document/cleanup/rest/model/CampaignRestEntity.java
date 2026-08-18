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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.document.cleanup.rest.model;

import java.util.List;

import lombok.Data;

/**
 * REST representation of a cleanup campaign. Dates are epoch millis or null.
 * Also used as creation request body (name + optional parameter overrides).
 */
@Data
public class CampaignRestEntity {

  private long         id;

  private String       name;

  private String       state;

  private Integer      periodMonths;

  private Long         minFileSizeBytes;

  private Integer      graceDays;

  private Integer      maxVersionsPerFile;

  private List<String> excludedPaths;

  private Long         startedDate;

  private Long         publishedDate;

  private Long         lockDate;

  private Long         completedDate;

  private long         totalCount;

  private long         processedCount;

  private Long         etaSeconds;

  private long         candidateCount;

  private long         reclaimableBytes;

  private long         reclaimedBytes;

  private boolean      archiveAvailable;

}
