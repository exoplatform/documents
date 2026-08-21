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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import org.exoplatform.document.cleanup.entity.CleanupCampaignItemEntity;

public interface CleanupCampaignItemDAO extends JpaRepository<CleanupCampaignItemEntity, Long> {

  /**
   * The persisted reclaimable figure of an item — the bytes the item's OWN action
   * frees:
   * <ul>
   * <li>DELETE: the content size PLUS {@code versionsSize}. A hard delete
   * destroys the file's whole version history along with its content, and
   * {@code CleanupJcrStorage#deleteNode} reports exactly that sum back as
   * {@code reclaimedBytes} — summing content alone under-reported every DELETE
   * candidate by the entire weight of its versions, on the very number an
   * administrator publishes a campaign on</li>
   * <li>PURGE_VERSIONS: {@code versionsSize} alone, the content being
   * untouched</li>
   * </ul>
   * This works as a plain sum because {@code versionsSize} means 'the version
   * bytes THIS action reclaims', not 'the whole history' nor 'the purgeable
   * subset': the scan writes the whole history on a DELETE row and the removal
   * set on a PURGE_VERSIONS one (see {@code CleanupJcrStorage#toCandidate}), so
   * prediction and execution report the same figure.
   * <p>
   * IT WAS A JPQL {@code CASE} EXPRESSION AND IS NOW A COLUMN, because no index
   * can serve an ORDER BY over an expression: the purge consumes its candidates
   * biggest first, which turned every batch into a filesort over the campaign's
   * remaining rows — 318 ms against 4 ms per batch on 200k rows, tens of
   * thousands of batches per run, and the cost growing with the square of the
   * item count. Exactly the shape keyset paging replaced offset paging to avoid,
   * reintroduced through the ordering. Stored, ONE index
   * (IDX_DOC_CLEANUP_ITEM_RECLAIMABLE) serves the keyset predicate and its order
   * together.
   * <p>
   * The denormalization removes a duplicate rather than adding one: the
   * definition lives in {@code CleanupSizeUtil}, applied by
   * {@code CleanupCampaignStorage#toEntity} on every save, and this column is now
   * the single thing SQL and the in-memory comparator both read — where the CASE
   * expression and the Java method used to mirror each other and could drift.
   * <p>
   * Named as the ENTITY ATTRIBUTE here (no query alias), which is also the
   * logical sort key the REST layer accepts
   * ({@code CleanupConstants#RECLAIMABLE_SORT_KEY}) — an equality pinned by a
   * test, since it is what lets the review list be ordered by an ordinary
   * property sort instead of an unsafe one.
   */
  String RECLAIMABLE_BYTES_PROPERTY = "reclaimableBytes";

  /**
   * The same column spliced into JPQL text, i.e. WITH the query alias — and the
   * alias is why these are two constants rather than one. Spring Data appends the
   * alias itself to a safe {@code Sort} property, so handing it
   * {@code i.reclaimableBytes} renders {@code i.i.reclaimableBytes} and the query
   * does not parse; JPQL text, conversely, needs the alias. Derived by
   * concatenation so the column can only be renamed in one place (and so both
   * stay compile-time constants, which the switch over sort keys requires).
   */
  String RECLAIMABLE_BYTES = "i." + RECLAIMABLE_BYTES_PROPERTY;

  Page<CleanupCampaignItemEntity> findByCampaignId(long campaignId, Pageable pageable);

  Page<CleanupCampaignItemEntity> findByCampaignIdAndState(long campaignId, String state, Pageable pageable);

  /**
   * KEYSET page of the items of a campaign in a given state, BIGGEST FIRST — the
   * order the purge worker consumes them in.
   * <p>
   * Ordered by what each row actually frees ({@link #RECLAIMABLE_BYTES}, the one
   * definition, so this can never disagree with the report the administrator
   * published on) and NOT by id. A purge of a 14-year corpus runs for hours, and
   * the id order is the order the scan happened to walk the tree in — so the
   * space came back in no particular order and an administrator watching 4 GB of
   * a promised 234 GB could not tell whether the big wins were still ahead. This
   * front-loads them: the first batches free the most, which is also what makes
   * stopping early a decision rather than a gamble.
   * <p>
   * THE CURSOR IS COMPOSITE and it has to be: ordering by a computed value means
   * the id alone no longer identifies a position. It is
   * {@code (reclaimableBytes DESC, id ASC)} — strictly monotonic over a total
   * order, so the worker cannot revisit a row within a run, which is the property
   * the id-ascending keyset was there for (an offset page-0 re-read relies on
   * every processed item leaving the state, so one item that can never be
   * persisted feeds it back forever). The tie branch on {@code id} is what makes
   * the order total: without it a block of equal-sized rows spanning a batch
   * boundary would repeat some rows and drop others.
   * <p>
   * SAFE because the key cannot move under the cursor mid-run: the values it is
   * computed from ({@code action}, {@code fileSize}, {@code versionsSize}) are
   * only ever refreshed on the item being processed, which then leaves the
   * CANDIDATE state; and the freshness listener that could refresh a pending one
   * is unregistered before the campaign reaches EXECUTING. A row whose save
   * FAILED keeps its stored key, and the cursor is already past it.
   *
   * @param campaignId           campaign identifier
   * @param state                item state
   * @param lastReclaimableBytes reclaimable bytes of the last row seen, NULL to
   *                             start from the biggest
   * @param lastId               id of the last row seen, breaking ties on equal
   *                             sizes
   * @param pageable             page size
   * @return the next items, biggest first, empty when the state is exhausted
   */
  @Query("SELECT i FROM CleanupCampaignItem i"
      + " WHERE i.campaignId = :campaignId AND i.state = :state"
      + " AND (:lastReclaimableBytes IS NULL"
      + " OR " + RECLAIMABLE_BYTES + " < :lastReclaimableBytes"
      + " OR (" + RECLAIMABLE_BYTES + " = :lastReclaimableBytes AND i.id > :lastId))"
      + " ORDER BY " + RECLAIMABLE_BYTES + " DESC, i.id ASC")
  List<CleanupCampaignItemEntity> findByStateOrderedByReclaimableBytes(@Param("campaignId")
  long campaignId, @Param("state")
  String state, @Param("lastReclaimableBytes")
  Long lastReclaimableBytes, @Param("lastId")
  long lastId, Pageable pageable);

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

  /**
   * Drops every item row of a campaign in ONE statement.
   *
   * DECLARED rather than derived, and that is the point: a derived
   * {@code deleteBy...} SELECTs the matching entities and removes them one at a
   * time, so dropping the report of a simulated campaign loaded every one of its
   * rows into a persistence context and issued one DELETE per row — hundreds of
   * thousands of both, on the corpus this feature exists for. One bulk statement
   * is one round trip and no persistence context at all.
   * <p>
   * Bulk JPQL bypasses the persistence context by design, so entities already
   * loaded in the calling transaction are NOT evicted. Harmless at both call
   * sites: the campaign is being deleted outright, or its report has just been
   * archived and nothing reads those rows again.
   */
  @Modifying
  @Transactional
  @Query("DELETE FROM CleanupCampaignItem i WHERE i.campaignId = :campaignId")
  void deleteByCampaignId(@Param("campaignId")
  long campaignId);

  /**
   * Campaign ids that have item rows but NO campaign row — rows nothing can ever
   * read again, every item query being scoped by campaign id.
   * <p>
   * They are what a JVM death in the middle of a delete leaves behind (the
   * campaign row goes first, so that a half-deleted campaign can never be acted
   * on), and they would otherwise sit there forever: invisible, and holding the
   * very space this feature exists to reclaim. Swept at startup.
   * <p>
   * Cheap despite the shape, for two reasons and NOT for the one first written
   * here: the subquery reads the campaign table, which holds one row per campaign
   * ever created — nothing trims it, {@code MAX_CAMPAIGNS} being the list
   * endpoint's page size and not a constraint — but a campaign is a deliberate
   * administrative act on a grace cycle measured in weeks, so it stays in the
   * hundreds for years; and the outer DISTINCT is served by
   * IDX_DOC_CLEANUP_ITEM_CAMPAIGN, which is a real index. If that table ever does
   * grow, this query is worth re-examining — a comment claiming an enforced bound
   * is what would stop someone doing so.
   */
  @Query("SELECT DISTINCT i.campaignId FROM CleanupCampaignItem i"
      + " WHERE i.campaignId NOT IN (SELECT c.id FROM CleanupCampaign c)")
  List<Long> findOrphanCampaignIds();

}
