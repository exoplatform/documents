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
package org.exoplatform.document.cleanup.entity;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Data;

@Entity(name = "CleanupCampaign")
@Table(name = "DOCUMENTS_CLEANUP_CAMPAIGN")
@Data
public class CleanupCampaignEntity implements Serializable {

  private static final long serialVersionUID = 4728820912354523L;

  @Id
  @SequenceGenerator(name = "SEQ_DOCUMENTS_CLEANUP_CAMPAIGN_ID", sequenceName = "SEQ_DOCUMENTS_CLEANUP_CAMPAIGN_ID", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_DOCUMENTS_CLEANUP_CAMPAIGN_ID")
  @Column(name = "ID", nullable = false)
  private Long              id;

  @Column(name = "NAME", nullable = false)
  private String            name;

  @Column(name = "STATE", nullable = false)
  private String            state;

  @Column(name = "PERIOD_MONTHS")
  private int               periodMonths;

  @Column(name = "MIN_FILE_SIZE_BYTES")
  private long              minFileSizeBytes;

  @Column(name = "GRACE_DAYS")
  private int               graceDays;

  @Column(name = "MAX_VERSIONS_PER_FILE")
  private int               maxVersionsPerFile;

  @Column(name = "EXCLUDED_PATHS")
  private String            excludedPaths;

  /**
   * Reader threads this campaign's scan may use. 0 means 'never set', i.e. use
   * the platform default — the column is added with that default, so every
   * campaign predating it keeps the behaviour it ran with.
   */
  @Column(name = "SCAN_THREADS")
  private int               scanThreads;

  @Column(name = "STARTED_DATE")
  private Date              startedDate;

  @Column(name = "PUBLISHED_DATE")
  private Date              publishedDate;

  @Column(name = "LOCK_DATE")
  private Date              lockDate;

  @Column(name = "COMPLETED_DATE")
  private Date              completedDate;

  @Column(name = "TOTAL_COUNT")
  private long              totalCount;

  @Column(name = "PROCESSED_COUNT")
  private long              processedCount;

  @Column(name = "ETA_SECONDS")
  private long              etaSeconds;

  @Column(name = "CHECKPOINT_OFFSET")
  private long              checkpointOffset;

  @Column(name = "CHECKPOINT_PATH")
  private String            checkpointPath;

  @Column(name = "SUMMARY_JSON")
  private String            summaryJson;

  @Column(name = "ARCHIVE_FILE_ID")
  private Long              archiveFileId;

}
