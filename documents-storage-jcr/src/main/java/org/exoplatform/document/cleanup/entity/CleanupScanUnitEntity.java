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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Data;

@Entity(name = "CleanupScanUnit")
@Table(name = "DOCUMENTS_CLEANUP_CAMPAIGN_SCAN_UNIT")
@Data
public class CleanupScanUnitEntity implements Serializable {

  private static final long serialVersionUID = 41827365109283741L;

  @Id
  @SequenceGenerator(name = "SEQ_DOCUMENTS_CLEANUP_SCAN_UNIT_ID", sequenceName = "SEQ_DOCUMENTS_CLEANUP_SCAN_UNIT_ID", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_DOCUMENTS_CLEANUP_SCAN_UNIT_ID")
  @Column(name = "ID", nullable = false)
  private Long              id;

  @Column(name = "CAMPAIGN_ID", nullable = false)
  private long              campaignId;

  /**
   * Absolute JCR path of the partition walked by ONE reader thread. Unique per
   * campaign (UK_DOC_CLEANUP_SCAN_UNIT_PATH): that constraint is what makes unit
   * planning idempotent across resumes, exactly as the campaign+nodeUuid one
   * makes candidate saving idempotent.
   */
  @Column(name = "UNIT_PATH", nullable = false)
  private String            unitPath;

  @Column(name = "STATE", nullable = false)
  private String            state;

  /**
   * Path of the last node scanned in this unit — the ONLY positioning
   * information of a resume, per unit instead of per campaign. NULL while the
   * unit was never started.
   */
  @Column(name = "LAST_SCANNED_PATH")
  private String            lastScannedPath;

  /** Nodes scanned in this unit so far: progress/ETA display only. */
  @Column(name = "SCANNED_COUNT")
  private long              scannedCount;

  /**
   * Nodes counted in this unit by the estimation phase, 0 while never counted.
   * The campaign denominator is the SUM over the units, so it stays comparable
   * with the counts of the campaigns scanned by the sequential worker.
   */
  @Column(name = "TOTAL_COUNT")
  private long              totalCount;

  /**
   * Localizable message code of the unit failure, and NOTHING else — same
   * contract as the campaign item's, the console looks it up in an i18n bundle.
   */
  @Column(name = "FAILURE_REASON")
  private String            failureReason;

}
