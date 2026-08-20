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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import lombok.SneakyThrows;

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

  /**
   * SHA-256 hex of {@link #unitPath}, persisted for ONE purpose: being the
   * unique key. The constraint cannot be keyed on the path itself — on MySQL the
   * table's {@code CHARSET=UTF8} makes an {@code NVARCHAR(2000)} key 6008 bytes
   * against InnoDB's 3072-byte ceiling, so the changeset fails at DDL time and
   * the addon does not deploy at all. The path stays as the unindexed readable
   * column.
   */
  @Column(name = "UNIT_PATH_HASH", nullable = false)
  private String            unitPathHash;

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
   * Nodes counted in this unit by the estimation phase, NULL while never
   * counted. The campaign denominator is the SUM over the units, so it stays
   * comparable with the counts of the campaigns scanned by the sequential
   * worker.
   * <p>
   * A boxed {@code Long} and not a {@code long}, which is the whole point: 0 is
   * a legitimate COUNT — an empty first-letter bucket of /Users — and with a
   * primitive it was indistinguishable from 'not counted yet', so every resume
   * re-counted every empty bucket for nothing.
   */
  @Column(name = "TOTAL_COUNT")
  private Long              totalCount;

  /**
   * Walk attempts already spent on this unit, incremented when the coordinator
   * CLAIMS it for a walk. A unit that spent them all is settled-failed: it stops
   * being re-walked and stops holding the dry-run's completion back.
   */
  @Column(name = "ATTEMPT_COUNT")
  private long              attemptCount;

  /**
   * Localizable message code of the unit failure, and NOTHING else — same
   * contract as the campaign item's, the console looks it up in an i18n bundle.
   */
  @Column(name = "FAILURE_REASON")
  private String            failureReason;

  /**
   * Hashes a unit path into the value of {@link #unitPathHash}. Same idiom as
   * {@code WebDavPathMappingEntity#buildId} in this module, which keys a column
   * on a JCR path for the very same reason.
   *
   * @param unitPath absolute JCR path of the unit
   * @return its SHA-256 hex digest, 64 characters
   */
  @SneakyThrows
  public static String hashUnitPath(String unitPath) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(StringUtils.defaultString(unitPath).getBytes(StandardCharsets.UTF_8)));
  }

}
