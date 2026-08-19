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
package org.exoplatform.document.cleanup.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.dao.CleanupCampaignDAO;
import org.exoplatform.document.cleanup.dao.CleanupCampaignItemDAO;
import org.exoplatform.document.cleanup.entity.CleanupCampaignEntity;
import org.exoplatform.document.cleanup.entity.CleanupCampaignItemEntity;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignAggregates;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupComparisonBucket;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.util.CleanupConstants;

import io.meeds.social.util.JsonUtils;

/**
 * RDBMS storage of cleanup campaigns and their items: the only layer touching
 * the cleanup DAOs, mapping entities to simple domain models. No business
 * logic.
 */
@Component
public class CleanupCampaignStorage {

  /**
   * Maximum number of owner identity ids sent in a single {@code IN} list.
   * Oracle rejects an expression list longer than 1000 entries (ORA-01795), and
   * a user managing that many spaces is the one whose OWN review page would
   * break — so every owner-scoped query below is chunked, with a margin under
   * the hard cap.
   */
  static final int               MAX_IN_CLAUSE_SIZE = 900;

  @Autowired
  private CleanupCampaignDAO     campaignDAO;

  @Autowired
  private CleanupCampaignItemDAO itemDAO;

  public CleanupCampaign createCampaign(CleanupCampaign campaign) {
    CleanupCampaignEntity entity = toEntity(campaign);
    entity.setId(null);
    return toModel(campaignDAO.save(entity));
  }

  public CleanupCampaign saveCampaign(CleanupCampaign campaign) {
    return toModel(campaignDAO.save(toEntity(campaign)));
  }

  public CleanupCampaign getCampaign(long campaignId) {
    return campaignDAO.findById(campaignId).map(this::toModel).orElse(null);
  }

  public List<CleanupCampaign> getCampaigns(Pageable pageable) {
    return campaignDAO.findAll(pageable).map(this::toModel).getContent();
  }

  public List<CleanupCampaign> getCampaignsByStates(List<CleanupCampaignState> states) {
    List<String> stateNames = states.stream().map(CleanupCampaignState::name).toList();
    return campaignDAO.findByStateIn(stateNames).stream().map(this::toModel).toList();
  }

  /**
   * Persists the scan/execution progress counters and the scan resume
   * checkpoint: {@code checkpointPath} is the last processed node path (or a
   * bare scan-root path marking a root not started yet), the ONLY positioning
   * information; {@code checkpointOffset} is the scanned-in-root count, kept
   * for progress/ETA display only.
   */
  public void updateProgress(long campaignId,
                             long totalCount,
                             long processedCount,
                             long etaSeconds,
                             String checkpointPath,
                             long checkpointOffset) {
    campaignDAO.findById(campaignId).ifPresent(entity -> {
      entity.setTotalCount(totalCount);
      entity.setProcessedCount(processedCount);
      entity.setEtaSeconds(etaSeconds);
      entity.setCheckpointPath(checkpointPath);
      entity.setCheckpointOffset(checkpointOffset);
      campaignDAO.save(entity);
    });
  }

  /**
   * Saves scan candidates as campaign items, ignoring node uuids already
   * recorded for the campaign (scan resume may replay a batch): one bulk
   * existence query plus one saveAll per batch, backed by the
   * UK_DOC_CLEANUP_ITEM_NODE unique constraint.
   */
  public void saveCandidates(long campaignId, List<CleanupCandidate> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return;
    }
    List<String> nodeUuids = candidates.stream().map(CleanupCandidate::getNodeUuid).toList();
    Set<String> existingNodeUuids = itemDAO.findByCampaignIdAndNodeUuidIn(campaignId, nodeUuids)
                                           .stream()
                                           .map(CleanupCampaignItemEntity::getNodeUuid)
                                           .collect(Collectors.toSet());
    List<CleanupCampaignItemEntity> newEntities = candidates.stream()
                                                            .filter(candidate -> !existingNodeUuids.contains(candidate.getNodeUuid()))
                                                            .map(candidate -> toEntity(campaignId, candidate))
                                                            .toList();
    if (!newEntities.isEmpty()) {
      itemDAO.saveAll(newEntities);
    }
  }

  public CleanupCampaignItem saveItem(CleanupCampaignItem item) {
    return toModel(itemDAO.save(toEntity(item)));
  }

  public CleanupCampaignItem getItem(long itemId) {
    return itemDAO.findById(itemId).map(this::toModel).orElse(null);
  }

  /**
   * Items touched by a JCR event path: the event node itself, its ancestors
   * (exact match against the ancestor chain, computed here) and its descendants
   * (escaped prefix LIKE, so a deleted/moved folder event matches the candidate
   * files below it).
   */
  public List<CleanupCampaignItem> getItemsTouchedByPath(long campaignId, String eventPath) {
    return itemDAO.findByCampaignIdAndPathTouchedBy(campaignId, ancestorChain(eventPath), escapeLike(eventPath))
                  .stream()
                  .map(this::toModel)
                  .toList();
  }

  /**
   * Campaign items matching the provided filters, any of them nullable.
   *
   * @param campaignId campaign identifier
   * @param ownerIdentityId optional owner filter
   * @param state optional item state filter
   * @param action optional action filter
   * @param minSize optional minimal content size filter
   * @param search optional case-insensitive search on the item PATH — which
   *          covers the file name (the path's last segment) as well as the
   *          folders above it, the item table having no name column
   * @param pageable page, size and sort
   * @return page of items
   */
  public Page<CleanupCampaignItem> getItems(long campaignId, // NOSONAR
                                            Long ownerIdentityId,
                                            CleanupItemState state,
                                            CleanupAction action,
                                            Long minSize,
                                            String search,
                                            Pageable pageable) {
    return itemDAO.findByFilters(campaignId,
                                 ownerIdentityId,
                                 state == null ? null : state.name(),
                                 action == null ? null : action.name(),
                                 minSize,
                                 searchPattern(search),
                                 pageable)
                  .map(this::toModel);
  }

  public Page<CleanupCampaignItem> getItemsByState(long campaignId, CleanupItemState state, Pageable pageable) {
    return itemDAO.findByCampaignIdAndState(campaignId, state.name(), pageable).map(this::toModel);
  }

  /**
   * Items of the campaign owned by the given identities, optionally narrowed by
   * the same path search as {@link #getItems}.
   * <p>
   * The owner list is CHUNKED (see {@link #MAX_IN_CLAUSE_SIZE}), so a user
   * managing a thousand spaces doesn't get an ORA-01795 on their own review page.
   * With a single chunk — the overwhelmingly common case — the DAO page is
   * returned untouched. With several, each chunk is queried with the SAME total
   * ordering as requested, {@code (page + 1) * size} rows are taken from each,
   * they are merged with a comparator mirroring that ordering and the requested
   * page is sliced out of the merge.
   * <p>
   * TRADE-OFF of the multi-chunk path: taking {@code (page + 1) * size} rows per
   * chunk is what makes the merge exact (the requested page can, at worst, be
   * filled entirely from one chunk), so deep paging costs more rows per chunk —
   * bounded, since the review UI pages at 20/50/100. The requested ordering must
   * be TOTAL for the slice to be stable; the REST layer guarantees that by
   * appending {@code id ASC} (the primary key) to every ordering. The total
   * element count is the exact sum of the chunks' counts, the owner sets being
   * disjoint by construction.
   *
   * @param campaignId campaign identifier
   * @param ownerIdentityIds owner identities (the user and the spaces they
   *          manage)
   * @param search optional case-insensitive search on the item PATH
   * @param pageable page, size and sort
   * @return page of items
   */
  public Page<CleanupCampaignItem> getItemsByOwners(long campaignId,
                                                    List<Long> ownerIdentityIds,
                                                    String search,
                                                    Pageable pageable) {
    String pattern = searchPattern(search);
    List<List<Long>> chunks = chunkOwnerIds(ownerIdentityIds);
    if (chunks.size() == 1) {
      return itemDAO.findByOwnersAndSearch(campaignId, chunks.get(0), pattern, pageable).map(this::toModel);
    }
    int upToRequestedPage = (pageable.getPageNumber() + 1) * pageable.getPageSize();
    Pageable chunkPageable = PageRequest.of(0, upToRequestedPage, pageable.getSort());
    List<CleanupCampaignItemEntity> merged = new ArrayList<>();
    long totalElements = 0;
    for (List<Long> chunk : chunks) {
      Page<CleanupCampaignItemEntity> chunkPage = itemDAO.findByOwnersAndSearch(campaignId, chunk, pattern, chunkPageable);
      merged.addAll(chunkPage.getContent());
      totalElements += chunkPage.getTotalElements();
    }
    merged.sort(mergeComparator(pageable.getSort()));
    int fromIndex = Math.min(pageable.getPageNumber() * pageable.getPageSize(), merged.size());
    int toIndex = Math.min(upToRequestedPage, merged.size());
    List<CleanupCampaignItem> items = merged.subList(fromIndex, toIndex).stream().map(this::toModel).toList();
    return new PageImpl<>(items, pageable, totalElements);
  }

  public Page<CleanupCampaignItem> getItemsPage(long campaignId, Pageable pageable) {
    return itemDAO.findByCampaignId(campaignId, pageable).map(this::toModel);
  }

  /**
   * Item aggregates of a whole campaigns list, computed by ONE grouped query
   * (see the DAO) instead of per-campaign aggregate queries: a campaign absent
   * from the returned map simply has no item rows anymore.
   *
   * @param campaignIds campaign identifiers
   * @return map of campaign id to its item aggregates
   */
  public Map<Long, CleanupCampaignAggregates> getItemAggregates(List<Long> campaignIds) {
    Map<Long, CleanupCampaignAggregates> aggregatesByCampaignId = new HashMap<>();
    if (campaignIds == null || campaignIds.isEmpty()) {
      return aggregatesByCampaignId;
    }
    for (Object[] row : itemDAO.findAggregatesByCampaignIds(campaignIds)) {
      long campaignId = ((Number) row[0]).longValue();
      CleanupCampaignAggregates aggregates = aggregatesByCampaignId.computeIfAbsent(campaignId,
                                                                                    id -> new CleanupCampaignAggregates());
      aggregates.setItemsRetained(true);
      if (CleanupItemState.CANDIDATE.name().equals(row[1])) {
        aggregates.setCandidateCount(((Number) row[2]).longValue());
        aggregates.setReclaimableBytes(((Number) row[3]).longValue());
      }
      aggregates.setReclaimedBytes(aggregates.getReclaimedBytes() + ((Number) row[4]).longValue());
    }
    return aggregatesByCampaignId;
  }

  public long countItemsByState(long campaignId, CleanupItemState state) {
    return itemDAO.countByCampaignIdAndState(campaignId, state.name());
  }

  /**
   * Chunked like {@link #getItemsByOwners}, but merging is trivial and EXACT
   * here: the owner sets are disjoint, so the per-chunk counts simply add up.
   */
  public long countItemsByOwnersAndState(long campaignId, List<Long> ownerIdentityIds, CleanupItemState state) {
    long count = 0;
    for (List<Long> chunk : chunkOwnerIds(ownerIdentityIds)) {
      count += itemDAO.countByCampaignIdAndOwnerIdentityIdInAndState(campaignId, chunk, state.name());
    }
    return count;
  }

  public long sumReclaimableBytesByState(long campaignId, CleanupItemState state) {
    return itemDAO.sumReclaimableBytesByState(campaignId, state.name());
  }

  /** Chunked and additively merged, see {@link #countItemsByOwnersAndState}. */
  public long sumReclaimableBytesByOwnersAndState(long campaignId, List<Long> ownerIdentityIds, CleanupItemState state) {
    long sum = 0;
    for (List<Long> chunk : chunkOwnerIds(ownerIdentityIds)) {
      sum += itemDAO.sumReclaimableBytesByOwnersAndState(campaignId, chunk, state.name());
    }
    return sum;
  }

  public long sumReclaimedBytes(long campaignId) {
    return itemDAO.sumReclaimedBytes(campaignId);
  }

  /** Chunked and additively merged, see {@link #countItemsByOwnersAndState}. */
  public long sumReclaimedBytesByOwners(long campaignId, List<Long> ownerIdentityIds) {
    long sum = 0;
    for (List<Long> chunk : chunkOwnerIds(ownerIdentityIds)) {
      sum += itemDAO.sumReclaimedBytesByOwners(campaignId, chunk);
    }
    return sum;
  }

  /**
   * Items of the base campaign whose node uuid is ALSO a candidate of the other
   * campaign ('persisting' bucket of a comparison).
   *
   * @param baseCampaignId base campaign identifier
   * @param otherCampaignId compared campaign identifier
   * @return count and reclaimable bytes of the bucket
   */
  public CleanupComparisonBucket getPersistingItems(long baseCampaignId, long otherCampaignId) {
    return toBucket(itemDAO.aggregateItemsSharedWithCampaign(baseCampaignId, otherCampaignId));
  }

  /**
   * Items of the base campaign absent from the other one ('new' bucket of a
   * comparison).
   *
   * @param baseCampaignId base campaign identifier
   * @param otherCampaignId compared campaign identifier
   * @return count and reclaimable bytes of the bucket
   */
  public CleanupComparisonBucket getNewItems(long baseCampaignId, long otherCampaignId) {
    return toBucket(itemDAO.aggregateItemsAbsentFromCampaign(baseCampaignId, otherCampaignId));
  }

  /**
   * Items of the other campaign absent from the base one ('gone' bucket of a
   * comparison): the very same query as {@link #getNewItems(long, long)} with
   * the campaigns swapped, so the bytes are read from the other campaign's
   * rows.
   *
   * @param baseCampaignId base campaign identifier
   * @param otherCampaignId compared campaign identifier
   * @return count and reclaimable bytes of the bucket
   */
  public CleanupComparisonBucket getGoneItems(long baseCampaignId, long otherCampaignId) {
    return toBucket(itemDAO.aggregateItemsAbsentFromCampaign(otherCampaignId, baseCampaignId));
  }

  /**
   * Folds the single aggregate row of a comparison bucket query: an empty
   * result (no item row at all) is an empty bucket, never an error.
   */
  private CleanupComparisonBucket toBucket(List<Object[]> rows) {
    if (rows == null || rows.isEmpty() || rows.get(0) == null) {
      return new CleanupComparisonBucket();
    }
    Object[] row = rows.get(0);
    return new CleanupComparisonBucket(row[0] == null ? 0 : ((Number) row[0]).longValue(),
                                       row[1] == null ? 0 : ((Number) row[1]).longValue());
  }

  public boolean hasItems(long campaignId) {
    return itemDAO.existsByCampaignId(campaignId);
  }

  public void deleteItems(long campaignId) {
    itemDAO.deleteByCampaignId(campaignId);
  }

  /**
   * Ancestor chain of an event path within the scan roots: every prefix of the
   * path ending at a '/' boundary strictly below the containing scan root, plus
   * the path itself. Computed in Java so the DAO can match paths EXACTLY
   * ({@code IN}) instead of an unindexable column-in-pattern LIKE. Package
   * visible for tests.
   */
  static List<String> ancestorChain(String eventPath) {
    List<String> paths = new ArrayList<>();
    for (String root : CleanupConstants.SCAN_ROOTS) {
      if (eventPath.startsWith(root + "/")) {
        int slashIndex = eventPath.indexOf('/', root.length() + 1);
        while (slashIndex > 0) {
          paths.add(eventPath.substring(0, slashIndex));
          slashIndex = eventPath.indexOf('/', slashIndex + 1);
        }
        break;
      }
    }
    paths.add(eventPath);
    return paths;
  }

  /**
   * Escapes the '_'/'%' LIKE wildcards (and the '|' escape character itself) of
   * a literal path, matching the {@code ESCAPE '|'} clause of the DAO query:
   * eXo pads user-home segments (e.g. /Users/j___/jo___/) with underscores that
   * must never wildcard-match unrelated rows. Package visible for tests.
   */
  static String escapeLike(String value) {
    return value.replace("|", "||").replace("_", "|_").replace("%", "|%");
  }

  /**
   * Contains-match LIKE pattern of a free-text search term: lower-cased (the
   * queries compare {@code LOWER(i.path)}) and run through the very same
   * {@link #escapeLike(String)} helper as the JCR-event queries, so a term
   * holding '%', '_' or '|' matches those characters literally instead of
   * wildcarding. A blank term means NO filter at all, hence the null. Package
   * visible for tests.
   */
  static String searchPattern(String search) {
    return StringUtils.isBlank(search) ? null : "%" + escapeLike(search.trim()).toLowerCase(Locale.ROOT) + "%";
  }

  /**
   * Splits an owner-identity list into {@code IN}-clause-sized chunks. A null or
   * empty list yields ONE empty chunk, so every caller keeps issuing its single
   * query (and keeps returning that query's empty result) instead of
   * short-circuiting to a hand-made zero. Package visible for tests.
   */
  static List<List<Long>> chunkOwnerIds(List<Long> ownerIdentityIds) {
    if (ownerIdentityIds == null || ownerIdentityIds.isEmpty()) {
      return List.of(List.of());
    }
    List<List<Long>> chunks = new ArrayList<>();
    for (int fromIndex = 0; fromIndex < ownerIdentityIds.size(); fromIndex += MAX_IN_CLAUSE_SIZE) {
      chunks.add(ownerIdentityIds.subList(fromIndex, Math.min(fromIndex + MAX_IN_CLAUSE_SIZE, ownerIdentityIds.size())));
    }
    return chunks;
  }

  /**
   * In-memory mirror of a requested {@link Sort}, used to merge the per-chunk
   * pages of {@link #getItemsByOwners} in the very same order the database
   * applied inside each chunk. Any key the DAO can be asked to sort on (the REST
   * layer validates them against its allowlist) has its counterpart here;
   * anything else is ignored, and an ordering left with no usable key falls back
   * to the id — the primary key, hence the only column whose uniqueness the
   * schema really enforces (the path is nullable and only unique in practice).
   * <p>
   * Every key of the requested ordering is CHAINED, tiebreaker included: honouring
   * only the first one would leave a block of ties ordered by whatever order the
   * chunks happened to be read in, and the page sliced out of the merge would
   * repeat a row while skipping another — the very row-skipping the total ordering
   * exists to prevent.
   */
  private static Comparator<CleanupCampaignItemEntity> mergeComparator(Sort sort) {
    Comparator<CleanupCampaignItemEntity> comparator = null;
    for (Sort.Order order : sort) {
      Comparator<CleanupCampaignItemEntity> keyComparator = sortKeyComparator(order.getProperty());
      if (keyComparator == null) {
        continue;
      }
      if (order.isDescending()) {
        keyComparator = keyComparator.reversed();
      }
      comparator = comparator == null ? keyComparator : comparator.thenComparing(keyComparator);
    }
    return comparator == null ? sortKeyComparator("id") : comparator;
  }

  private static Comparator<CleanupCampaignItemEntity> sortKeyComparator(String property) {
    return switch (property) {
      case "id" -> Comparator.comparing(CleanupCampaignItemEntity::getId, Comparator.nullsLast(Long::compareTo));
      case "path" -> Comparator.comparing(CleanupCampaignItemEntity::getPath, Comparator.nullsLast(String::compareTo));
      case "ownerIdentityId" -> Comparator.comparingLong(CleanupCampaignItemEntity::getOwnerIdentityId);
      case "fileSize" -> Comparator.comparingLong(CleanupCampaignItemEntity::getFileSize);
      case "versionsSize" -> Comparator.comparingLong(CleanupCampaignItemEntity::getVersionsSize);
      case "state" -> Comparator.comparing(CleanupCampaignItemEntity::getState, Comparator.nullsLast(String::compareTo));
      case "action" -> Comparator.comparing(CleanupCampaignItemEntity::getAction, Comparator.nullsLast(String::compareTo));
      case "reclaimedBytes" -> Comparator.comparingLong(CleanupCampaignItemEntity::getReclaimedBytes);
      default -> null;
    };
  }

  private CleanupCampaignItemEntity toEntity(long campaignId, CleanupCandidate candidate) {
    CleanupCampaignItemEntity entity = new CleanupCampaignItemEntity();
    entity.setCampaignId(campaignId);
    entity.setNodeUuid(candidate.getNodeUuid());
    entity.setPath(candidate.getPath());
    entity.setOwnerIdentityId(candidate.getOwnerIdentityId());
    entity.setFileSize(candidate.getFileSize());
    entity.setVersionsSize(candidate.getVersionsSize());
    entity.setAction(candidate.getAction().name());
    if (candidate.isExempted()) {
      // A previously-exempted file stays visible as 'Kept' in every campaign,
      // carrying the mixin's decision metadata when readable
      entity.setState(CleanupItemState.EXEMPTED.name());
      entity.setDecidedBy(candidate.getExemptedBy());
      entity.setDecidedAt(toDate(candidate.getExemptedDate()));
    } else {
      entity.setState(CleanupItemState.CANDIDATE.name());
    }
    entity.setComputedAt(new Date());
    return entity;
  }

  private CleanupCampaign toModel(CleanupCampaignEntity entity) {
    CleanupCampaign campaign = new CleanupCampaign();
    campaign.setId(entity.getId());
    campaign.setName(entity.getName());
    campaign.setState(CleanupCampaignState.valueOf(entity.getState()));
    campaign.setParams(new CleanupParams(entity.getPeriodMonths(),
                                         entity.getMinFileSizeBytes(),
                                         entity.getGraceDays(),
                                         entity.getMaxVersionsPerFile(),
                                         fromJsonArray(entity.getExcludedPaths()),
                                         null));
    campaign.setStartedDate(toMillis(entity.getStartedDate()));
    campaign.setPublishedDate(toMillis(entity.getPublishedDate()));
    campaign.setLockDate(toMillis(entity.getLockDate()));
    campaign.setCompletedDate(toMillis(entity.getCompletedDate()));
    campaign.setTotalCount(entity.getTotalCount());
    campaign.setProcessedCount(entity.getProcessedCount());
    campaign.setEtaSeconds(entity.getEtaSeconds());
    campaign.setCheckpointOffset(entity.getCheckpointOffset());
    campaign.setCheckpointPath(entity.getCheckpointPath());
    campaign.setSummaryJson(entity.getSummaryJson());
    campaign.setArchiveFileId(entity.getArchiveFileId());
    return campaign;
  }

  private CleanupCampaignEntity toEntity(CleanupCampaign campaign) {
    CleanupCampaignEntity entity = new CleanupCampaignEntity();
    entity.setId(campaign.getId() == 0 ? null : campaign.getId());
    entity.setName(campaign.getName());
    entity.setState(campaign.getState().name());
    CleanupParams params = campaign.getParams();
    if (params != null) {
      entity.setPeriodMonths(params.getPeriodMonths() == null ? 0 : params.getPeriodMonths());
      entity.setMinFileSizeBytes(params.getMinFileSizeBytes() == null ? 0 : params.getMinFileSizeBytes());
      entity.setGraceDays(params.getGraceDays() == null ? 0 : params.getGraceDays());
      entity.setMaxVersionsPerFile(params.getMaxVersionsPerFile() == null ? 0 : params.getMaxVersionsPerFile());
      entity.setExcludedPaths(toJsonArray(params.getExcludedPaths()));
    }
    entity.setStartedDate(toDate(campaign.getStartedDate()));
    entity.setPublishedDate(toDate(campaign.getPublishedDate()));
    entity.setLockDate(toDate(campaign.getLockDate()));
    entity.setCompletedDate(toDate(campaign.getCompletedDate()));
    entity.setTotalCount(campaign.getTotalCount());
    entity.setProcessedCount(campaign.getProcessedCount());
    entity.setEtaSeconds(campaign.getEtaSeconds());
    entity.setCheckpointOffset(campaign.getCheckpointOffset());
    entity.setCheckpointPath(campaign.getCheckpointPath());
    entity.setSummaryJson(campaign.getSummaryJson());
    entity.setArchiveFileId(campaign.getArchiveFileId());
    return entity;
  }

  private CleanupCampaignItem toModel(CleanupCampaignItemEntity entity) {
    CleanupCampaignItem item = new CleanupCampaignItem();
    item.setId(entity.getId());
    item.setCampaignId(entity.getCampaignId());
    item.setNodeUuid(entity.getNodeUuid());
    item.setPath(entity.getPath());
    item.setOwnerIdentityId(entity.getOwnerIdentityId());
    item.setFileSize(entity.getFileSize());
    item.setVersionsSize(entity.getVersionsSize());
    item.setAction(CleanupAction.valueOf(entity.getAction()));
    item.setState(CleanupItemState.valueOf(entity.getState()));
    item.setComputedAt(toMillis(entity.getComputedAt()));
    item.setDecidedBy(entity.getDecidedBy());
    item.setDecidedAt(toMillis(entity.getDecidedAt()));
    item.setPurgedAt(toMillis(entity.getPurgedAt()));
    item.setReclaimedBytes(entity.getReclaimedBytes());
    item.setFailureReason(entity.getFailureReason());
    return item;
  }

  private CleanupCampaignItemEntity toEntity(CleanupCampaignItem item) {
    CleanupCampaignItemEntity entity = new CleanupCampaignItemEntity();
    entity.setId(item.getId() == 0 ? null : item.getId());
    entity.setCampaignId(item.getCampaignId());
    entity.setNodeUuid(item.getNodeUuid());
    entity.setPath(item.getPath());
    entity.setOwnerIdentityId(item.getOwnerIdentityId());
    entity.setFileSize(item.getFileSize());
    entity.setVersionsSize(item.getVersionsSize());
    entity.setAction(item.getAction().name());
    entity.setState(item.getState().name());
    entity.setComputedAt(toDate(item.getComputedAt()));
    entity.setDecidedBy(item.getDecidedBy());
    entity.setDecidedAt(toDate(item.getDecidedAt()));
    entity.setPurgedAt(toDate(item.getPurgedAt()));
    entity.setReclaimedBytes(item.getReclaimedBytes());
    entity.setFailureReason(item.getFailureReason());
    return entity;
  }

  private long toMillis(Date date) {
    return date == null ? 0 : date.getTime();
  }

  private Date toDate(long millis) {
    return millis == 0 ? null : new Date(millis);
  }

  private String toJsonArray(List<String> values) {
    return values == null ? null : JsonUtils.toJsonString(values);
  }

  private List<String> fromJsonArray(String json) {
    return json == null ? List.of() : Arrays.asList(JsonUtils.fromJsonString(json, String[].class));
  }

}
