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

@Entity(name = "CleanupCampaignItem")
@Table(name = "DOCUMENTS_CLEANUP_CAMPAIGN_ITEM")
@Data
public class CleanupCampaignItemEntity implements Serializable {

  private static final long serialVersionUID = 87351249172354529L;

  @Id
  @SequenceGenerator(name = "SEQ_DOCUMENTS_CLEANUP_CAMPAIGN_ITEM_ID", sequenceName = "SEQ_DOCUMENTS_CLEANUP_CAMPAIGN_ITEM_ID", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_DOCUMENTS_CLEANUP_CAMPAIGN_ITEM_ID")
  @Column(name = "ID", nullable = false)
  private Long              id;

  @Column(name = "CAMPAIGN_ID", nullable = false)
  private long              campaignId;

  @Column(name = "NODE_UUID", nullable = false)
  private String            nodeUuid;

  @Column(name = "PATH")
  private String            path;

  @Column(name = "OWNER_IDENTITY_ID")
  private long              ownerIdentityId;

  @Column(name = "FILE_SIZE")
  private long              fileSize;

  /**
   * Version bytes this item's ACTION reclaims — the whole version history for a
   * DELETE, the removal set for a PURGE_VERSIONS. Action-dependent by design,
   * see {@link org.exoplatform.document.cleanup.dao.CleanupCampaignItemDAO#RECLAIMABLE_BYTES}.
   */
  @Column(name = "VERSIONS_SIZE")
  private long              versionsSize;

  /**
   * Last modification date of the candidate file, as read by the scan. Nullable:
   * rows recorded before this column existed keep a NULL, and so does a file
   * whose date was unreadable.
   */
  @Column(name = "LAST_MODIFIED_DATE")
  private Date              lastModifiedDate;

  /**
   * Creation date of the candidate file, as read by the scan. Persisted although
   * only the last-modified one is displayed: BOTH dates being older than the
   * campaign period is what made the file a candidate, so a report carrying only
   * one of them cannot explain its own rows. Nullable, see above.
   */
  @Column(name = "CREATED_DATE")
  private Date              createdDate;

  @Column(name = "ACTION", nullable = false)
  private String            action;

  /**
   * What this item's OWN action frees: {@code fileSize + versionsSize} for a
   * DELETE, {@code versionsSize} alone for a PURGE_VERSIONS.
   * <p>
   * DERIVED, and persisted anyway — the one deliberate denormalization of this
   * schema. It was a JPQL {@code CASE} expression, which no index can serve: the
   * purge consumes its candidates biggest first, and sorting on an expression
   * made every batch a filesort over the campaign's remaining rows (measured at
   * 318 ms against 4 ms per batch on 200k rows, and the batch count is in the
   * tens of thousands). Stored, it lets ONE index serve both the keyset predicate
   * and its order.
   * <p>
   * It cannot drift: every entity reaching the database is built by
   * {@code CleanupCampaignStorage#toEntity}, which recomputes this from
   * {@code CleanupSizeUtil} — still the single DEFINITION — on every save. This
   * column is the single READ, replacing the CASE that the Java comparator used
   * to mirror.
   */
  @Column(name = "RECLAIMABLE_BYTES")
  private long              reclaimableBytes;

  @Column(name = "STATE", nullable = false)
  private String            state;

  @Column(name = "COMPUTED_AT")
  private Date              computedAt;

  @Column(name = "DECIDED_BY")
  private String            decidedBy;

  @Column(name = "DECIDED_AT")
  private Date              decidedAt;

  @Column(name = "PURGED_AT")
  private Date              purgedAt;

  @Column(name = "RECLAIMED_BYTES")
  private long              reclaimedBytes;

  /**
   * Localizable message code of the failure, and NOTHING else: the console looks
   * it up in an i18n bundle and the grouped-failures aggregate groups on it, so
   * it must never carry an exception message.
   */
  @Column(name = "FAILURE_REASON")
  private String            failureReason;

  /**
   * Compact diagnostic of the failure (head + root exception, see
   * {@code CleanupThrowableUtil}), mapped as a plain String over the
   * FAILURE_DETAIL CLOB column — the platform precedent for a CLOB is a bare
   * {@code @Column} String, no {@code @Lob} and no columnDefinition (cf.
   * {@code io.meeds.social.space.template.entity.SpaceTemplateEntity}).
   */
  @Column(name = "FAILURE_DETAIL")
  private String            failureDetail;

  /** Purge attempts already spent on this item, incremented by every retry. */
  @Column(name = "ATTEMPT_COUNT")
  private long              attemptCount;

}
