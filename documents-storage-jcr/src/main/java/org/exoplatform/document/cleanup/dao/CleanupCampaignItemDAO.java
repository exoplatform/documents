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

  Page<CleanupCampaignItemEntity> findByCampaignIdAndOwnerIdentityIdIn(long campaignId,
                                                                       List<Long> ownerIdentityIds,
                                                                       Pageable pageable);

  List<CleanupCampaignItemEntity> findByCampaignIdAndNodeUuidIn(long campaignId, Collection<String> nodeUuids);

  @Query("""
      SELECT i FROM CleanupCampaignItem i
      WHERE i.campaignId = :campaignId
      AND (:ownerIdentityId IS NULL OR i.ownerIdentityId = :ownerIdentityId)
      AND (:state IS NULL OR i.state = :state)
      AND (:action IS NULL OR i.action = :action)
      AND (:minSize IS NULL OR i.fileSize >= :minSize)
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
  @Query("SELECT i.campaignId, i.state, COUNT(i), COALESCE(SUM(" + RECLAIMABLE_BYTES + "), 0),"
      + " COALESCE(SUM(i.reclaimedBytes), 0)"
      + " FROM CleanupCampaignItem i WHERE i.campaignId IN :campaignIds GROUP BY i.campaignId, i.state")
  List<Object[]> findAggregatesByCampaignIds(@Param("campaignIds")
  List<Long> campaignIds);

  @Query("SELECT i.nodeUuid, " + RECLAIMABLE_BYTES + " FROM CleanupCampaignItem i WHERE i.campaignId = :campaignId")
  Page<Object[]> findNodeUuidAndReclaimableBytes(@Param("campaignId")
  long campaignId, Pageable pageable);

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
