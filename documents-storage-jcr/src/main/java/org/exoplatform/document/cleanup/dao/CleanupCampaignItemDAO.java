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
package org.exoplatform.document.cleanup.dao;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import org.exoplatform.document.cleanup.entity.CleanupCampaignItemEntity;

public interface CleanupCampaignItemDAO extends JpaRepository<CleanupCampaignItemEntity, Long> {

  /**
   * Reclaimable bytes of an item: content size for a DELETE action, versions
   * size for a PURGE_VERSIONS one. The 'DELETE' literal must stay equal to
   * {@code CleanupAction.DELETE.name()} (the entity stores the action as a
   * plain string) — guarded by CleanupCampaignItemDAOTest. Defined once and
   * concatenated (compile-time constant) into the queries below.
   */
  String RECLAIMABLE_BYTES = "CASE WHEN i.action = 'DELETE' THEN i.fileSize ELSE i.versionsSize END";

  Page<CleanupCampaignItemEntity> findByCampaignId(long campaignId, Pageable pageable);

  Page<CleanupCampaignItemEntity> findByCampaignIdAndState(long campaignId, String state, Pageable pageable);

  /**
   * KEYSET page of the items of a campaign in a given state: the ones whose id
   * is strictly greater than the last one seen, oldest id first. This is what
   * makes the execution worker's forward progress STRUCTURAL — an offset page 0
   * re-read relies on every processed item leaving the state, so a single item
   * that can never be persisted feeds it back forever (a poison pill). Returns a
   * plain List: the worker drives its loop from the last id it saw, it has no
   * use for a total count, and skipping it spares one count query per batch.
   */
  List<CleanupCampaignItemEntity> findByCampaignIdAndStateAndIdGreaterThanOrderByIdAsc(long campaignId,
                                                                                       String state,
                                                                                       long lastId,
                                                                                       Pageable pageable);

  /**
   * KEYSET page of the RETRYABLE failures of a campaign: SKIPPED items whose
   * failure reason belongs to the allowlist the Service owns and whose attempt
   * count is still under the bound. Keyset-paged for the same reason as above,
   * and one that bites harder here: the requeue MUTATES the very state the
   * filter matches on, so an offset page would skip rows as the result set
   * shrinks underneath it.
   */
  @Query("""
      SELECT i FROM CleanupCampaignItem i
      WHERE i.campaignId = :campaignId
      AND i.state = :state
      AND i.failureReason IN :failureReasons
      AND i.attemptCount < :maxAttemptCount
      AND i.id > :lastId
      """)
  List<CleanupCampaignItemEntity> findRetryableFailures(@Param("campaignId")
  long campaignId,
                                                        @Param("state")
                                                        String state,
                                                        @Param("failureReasons")
                                                        Collection<String> failureReasons,
                                                        @Param("maxAttemptCount")
                                                        long maxAttemptCount,
                                                        @Param("lastId")
                                                        long lastId,
                                                        Pageable pageable);

  /**
   * Per-reason item counts of a campaign's failures, in ONE grouped query (rows:
   * failure reason, item count). Never by loading the SKIPPED rows: a campaign
   * can hold hundreds of thousands of them, and the console only ever displays
   * the handful of distinct reasons behind them.
   */
  @Query("SELECT i.failureReason, COUNT(i) FROM CleanupCampaignItem i" +
      " WHERE i.campaignId = :campaignId AND i.state = :state GROUP BY i.failureReason")
  List<Object[]> countFailuresByReason(@Param("campaignId")
  long campaignId, @Param("state")
  String state);

  List<CleanupCampaignItemEntity> findByCampaignIdAndNodeUuidIn(long campaignId, Collection<String> nodeUuids);

  /**
   * Campaign items matching every provided filter, each one null-tolerant so a
   * single query serves any combination.
   * <p>
   * {@code searchPattern} is a case-insensitive contains match on the item
   * PATH. The item table has NO name column: a
   * {@code CleanupCampaignItem#getName()} is derived from the path's last
   * segment, so matching the path covers the file name AND the folders above it
   * — which is what a cleanup reviewer looks for. The pattern is built by the
   * Storage layer (lower-cased, wildcards escaped with the '|' escape
   * character, wrapped in '%'), never by the caller.
   * <p>
   * The leading '%' makes the search unindexable: it scans the campaign's rows,
   * bounded by the CAMPAIGN_ID index (and, for the user-side query below, by
   * OWNER_IDENTITY_ID too).
   */
  @Query("""
      SELECT i FROM CleanupCampaignItem i
      WHERE i.campaignId = :campaignId
      AND (:ownerIdentityId IS NULL OR i.ownerIdentityId = :ownerIdentityId)
      AND (:state IS NULL OR i.state = :state)
      AND (:action IS NULL OR i.action = :action)
      AND (:minSize IS NULL OR i.fileSize >= :minSize)
      AND (:searchPattern IS NULL OR LOWER(i.path) LIKE :searchPattern ESCAPE '|')
      """)
  Page<CleanupCampaignItemEntity> findByFilters(@Param("campaignId")
  long campaignId,
                                                @Param("ownerIdentityId")
                                                Long ownerIdentityId,
                                                @Param("state")
                                                String state,
                                                @Param("action")
                                                String action,
                                                @Param("minSize")
                                                Long minSize,
                                                @Param("searchPattern")
                                                String searchPattern,
                                                Pageable pageable);

  /**
   * The user-review counterpart of {@link #findByFilters}: the items of the
   * campaign owned by the given identities (the user plus the spaces they
   * manage), optionally narrowed by the same null-tolerant path search — see
   * that method's javadoc for the pattern contract and the index note.
   */
  @Query("""
      SELECT i FROM CleanupCampaignItem i
      WHERE i.campaignId = :campaignId
      AND i.ownerIdentityId IN :ownerIdentityIds
      AND (:searchPattern IS NULL OR LOWER(i.path) LIKE :searchPattern ESCAPE '|')
      """)
  Page<CleanupCampaignItemEntity> findByOwnersAndSearch(@Param("campaignId")
  long campaignId,
                                                        @Param("ownerIdentityIds")
                                                        List<Long> ownerIdentityIds,
                                                        @Param("searchPattern")
                                                        String searchPattern,
                                                        Pageable pageable);

  /**
   * Items touched by a JCR event path, in two index-friendly directions (the
   * column always stays on the LEFT of the LIKE, the parameter on the right):
   * <ul>
   * <li>items that are the event node or one of its ancestors: exact match
   * against the ancestor chain computed in Java by the Storage layer</li>
   * <li>items below the event node (a deleted/moved FOLDER fires a single JCR
   * event for the top-most node only): prefix LIKE on the event path, with its
   * '_'/'%' wildcards escaped by the Storage layer ('|' escape character), so
   * padded segments like /Users/j___/ never wildcard-match unrelated rows</li>
   * </ul>
   */
  @Query("""
      SELECT i FROM CleanupCampaignItem i
      WHERE i.campaignId = :campaignId
      AND (i.path IN :ancestorPaths
           OR i.path LIKE CONCAT(:escapedEventPath, '/%') ESCAPE '|')
      """)
  List<CleanupCampaignItemEntity> findByCampaignIdAndPathTouchedBy(@Param("campaignId")
  long campaignId,
                                                                   @Param("ancestorPaths")
                                                                   List<String> ancestorPaths,
                                                                   @Param("escapedEventPath")
                                                                   String escapedEventPath);

  /**
   * Per-campaign, per-state item aggregates of a whole campaigns list in ONE
   * grouped query (rows: campaignId, state, item count, reclaimable bytes sum,
   * reclaimed bytes sum), folded by the Storage layer into one aggregate per
   * campaign — replaces up to 4 aggregate queries PER campaign on the list
   * endpoint.
   */
  @Query("SELECT i.campaignId, i.state, COUNT(i), COALESCE(SUM(" + RECLAIMABLE_BYTES + "), 0)," +
      " COALESCE(SUM(i.reclaimedBytes), 0)" +
      " FROM CleanupCampaignItem i WHERE i.campaignId IN :campaignIds GROUP BY i.campaignId, i.state")
  List<Object[]> findAggregatesByCampaignIds(@Param("campaignIds")
  List<Long> campaignIds);

  /**
   * Count and reclaimable-bytes sum of the items of {@code campaignId} whose
   * node uuid ALSO belongs to {@code otherCampaignId} — the 'persisting' bucket
   * of a campaign comparison, computed set-based by the database (never by
   * loading both candidate sets in memory). The correlated sub-query hits the
   * (CAMPAIGN_ID, NODE_UUID) unique index.
   *
   * @return a single row: item count, reclaimable bytes sum
   */
  @Query("SELECT COUNT(i), COALESCE(SUM(" + RECLAIMABLE_BYTES + "), 0) FROM CleanupCampaignItem i" +
      " WHERE i.campaignId = :campaignId" +
      " AND EXISTS (SELECT o.id FROM CleanupCampaignItem o" +
      " WHERE o.campaignId = :otherCampaignId AND o.nodeUuid = i.nodeUuid)")
  List<Object[]> aggregateItemsSharedWithCampaign(@Param("campaignId")
  long campaignId, @Param("otherCampaignId")
  long otherCampaignId);

  /**
   * Count and reclaimable-bytes sum of the items of {@code campaignId} whose
   * node uuid is ABSENT from {@code otherCampaignId}. Serves BOTH asymmetric
   * buckets of a campaign comparison by swapping the two arguments: 'new' is
   * (base, other), 'gone' is (other, base) — the bytes are always those of the
   * campaign passed first, exactly as the previous in-memory diff computed
   * them.
   *
   * @return a single row: item count, reclaimable bytes sum
   */
  @Query("SELECT COUNT(i), COALESCE(SUM(" + RECLAIMABLE_BYTES + "), 0) FROM CleanupCampaignItem i" +
      " WHERE i.campaignId = :campaignId" +
      " AND NOT EXISTS (SELECT o.id FROM CleanupCampaignItem o" +
      " WHERE o.campaignId = :otherCampaignId AND o.nodeUuid = i.nodeUuid)")
  List<Object[]> aggregateItemsAbsentFromCampaign(@Param("campaignId")
  long campaignId, @Param("otherCampaignId")
  long otherCampaignId);

  long countByCampaignIdAndState(long campaignId, String state);

  long countByCampaignIdAndOwnerIdentityIdInAndState(long campaignId, List<Long> ownerIdentityIds, String state);

  @Query("SELECT COALESCE(SUM(" + RECLAIMABLE_BYTES + "), 0)" + " FROM CleanupCampaignItem i" +
      " WHERE i.campaignId = :campaignId AND i.state = :state")
  long sumReclaimableBytesByState(@Param("campaignId")
  long campaignId, @Param("state")
  String state);

  @Query("SELECT COALESCE(SUM(" + RECLAIMABLE_BYTES + "), 0)" + " FROM CleanupCampaignItem i" +
      " WHERE i.campaignId = :campaignId AND i.ownerIdentityId IN :ownerIdentityIds AND i.state = :state")
  long sumReclaimableBytesByOwnersAndState(@Param("campaignId")
  long campaignId,
                                           @Param("ownerIdentityIds")
                                           List<Long> ownerIdentityIds,
                                           @Param("state")
                                           String state);

  @Query("SELECT COALESCE(SUM(i.reclaimedBytes), 0) FROM CleanupCampaignItem i WHERE i.campaignId = :campaignId")
  long sumReclaimedBytes(@Param("campaignId")
  long campaignId);

  @Query("""
      SELECT COALESCE(SUM(i.reclaimedBytes), 0)
      FROM CleanupCampaignItem i
      WHERE i.campaignId = :campaignId AND i.ownerIdentityId IN :ownerIdentityIds
      """)
  long sumReclaimedBytesByOwners(@Param("campaignId")
  long campaignId,
                                 @Param("ownerIdentityIds")
                                 List<Long> ownerIdentityIds);

  boolean existsByCampaignId(long campaignId);

  @Transactional
  void deleteByCampaignId(long campaignId);

}
