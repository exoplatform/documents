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
package org.exoplatform.document.cleanup.service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.constant.CleanupExemptionResult;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupCampaignSummary;
import org.exoplatform.document.cleanup.model.CleanupComparison;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupRevalidation;
import org.exoplatform.document.cleanup.model.CleanupUserSummary;
import org.exoplatform.document.cleanup.storage.CleanupCampaignStorage;
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage;
import org.exoplatform.document.cleanup.util.CleanupRevalidationUtil;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.social.util.JsonUtils;

import jakarta.annotation.PostConstruct;

/**
 * Cleanup campaigns lifecycle: dry-run launch, publication (single active
 * campaign platform-wide), grace-deadline locking, execution trigger,
 * cancellation, user exemptions ('keep'), candidate freshness refresh, campaign
 * comparison, report retention and CSV archiving.
 */
@Service
public class CleanupCampaignService {

  public static final String                      FILE_NAMESPACE     = "documentsCleanup";

  private static final Log                        LOG                = ExoLogger.getLogger(CleanupCampaignService.class);

  private static final List<CleanupCampaignState> ACTIVE_STATES      = List.of(CleanupCampaignState.PUBLISHED,
                                                                               CleanupCampaignState.LOCKED,
                                                                               CleanupCampaignState.EXECUTING);

  private static final List<CleanupCampaignState> TERMINAL_STATES    = List.of(CleanupCampaignState.COMPLETED,
                                                                               CleanupCampaignState.CANCELLED);

  private static final int                        MAX_CAMPAIGNS      = 200;

  private static final int                        MAX_MANAGED_SPACES = 100;

  private static final int                        CSV_PAGE_SIZE      = 1000;

  private static final int                        LISTENER_RETRY_MAX_ATTEMPTS  = 10;

  private static final long                       LISTENER_RETRY_DELAY_MILLIS  = TimeUnit.SECONDS.toMillis(30);

  /**
   * Guards the check-then-transition of {@link #publishCampaign(long)}: the
   * single-active-campaign invariant would otherwise be racy (TOCTOU) between
   * two concurrent publish requests. In-JVM lock only: cluster-wide exclusion
   * is out of scope by spec assumption (single-node deployment).
   */
  private final Object                            publishLock        = new Object();

  @Autowired
  private CleanupCampaignStorage                  campaignStorage;

  @Autowired
  private CleanupSettingService                   settingService;

  @Autowired
  private CleanupScanService                      scanService;

  @Autowired
  private CleanupExecutionService                 executionService;

  @Autowired
  private CleanupJcrStorage                       cleanupJcrStorage;

  @Autowired
  private CleanupCampaignLifecycle                campaignLifecycle;

  @Autowired
  private IdentityManager                         identityManager;

  @Autowired
  private SpaceService                            spaceService;

  @Autowired
  private FileService                             fileService;

  @PostConstruct
  public void init() {
    // Asynchronously: JCR may not be ready yet at Spring context startup
    CompletableFuture.runAsync(this::recoverAfterRestart);
  }

  /**
   * Restart recovery: re-registers the freshness observation listener while a
   * campaign is PUBLISHED (bounded backoff: JCR may not be ready yet), then
   * resumes the interrupted workers — the dry-run scan of a DRY_RUN_RUNNING
   * campaign (from its persisted checkpoint) and the purge of an EXECUTING one
   * (naturally resumable: it iterates the remaining CANDIDATE items). Both
   * workers no-op when that campaign's worker is already running. Package
   * visible for tests.
   */
  void recoverAfterRestart() {
    try {
      if (!campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED)).isEmpty()) {
        registerObservationListenerWithRetry();
      }
      for (CleanupCampaign campaign : campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.DRY_RUN_RUNNING))) {
        try {
          scanService.startScan(campaign.getId());
        } catch (Exception e) {
          LOG.warn("Error resuming the interrupted dry-run scan of cleanup campaign {}", campaign.getId(), e);
        }
      }
      for (CleanupCampaign campaign : campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.EXECUTING))) {
        try {
          executionService.resumeExecution(campaign.getId());
        } catch (Exception e) {
          LOG.warn("Error resuming the interrupted execution of cleanup campaign {}", campaign.getId(), e);
        }
      }
    } catch (Exception e) {
      LOG.warn("Error recovering cleanup campaigns after restart", e);
    }
  }

  /**
   * @return the platform default campaign parameters
   */
  public CleanupParams getDefaultParams() {
    return settingService.getDefaultParams();
  }

  /**
   * @return most recent campaigns, with their item aggregates
   */
  public List<CleanupCampaign> getCampaigns() {
    return campaignStorage.getCampaigns(PageRequest.of(0, MAX_CAMPAIGNS, Sort.by(Sort.Direction.DESC, "id")))
                          .stream()
                          .map(this::withAggregates)
                          .toList();
  }

  /**
   * @param campaignId campaign identifier
   * @return the campaign with its item aggregates
   * @throws ObjectNotFoundException when the campaign doesn't exist
   */
  public CleanupCampaign getCampaign(long campaignId) throws ObjectNotFoundException {
    CleanupCampaign campaign = campaignStorage.getCampaign(campaignId);
    if (campaign == null) {
      throw new ObjectNotFoundException("cleanup.campaignNotFound");
    }
    return withAggregates(campaign);
  }

  /**
   * Creates a campaign snapshotting the effective parameters, then launches its
   * dry-run scan.
   *
   * @param name campaign name, mandatory
   * @param overrides partial parameter overrides, null fields defaulted
   * @return the created campaign
   */
  public CleanupCampaign createCampaign(String name, CleanupParams overrides) {
    if (StringUtils.isBlank(name)) {
      throw new IllegalArgumentException("cleanup.nameMandatory");
    }
    CleanupCampaign campaign = new CleanupCampaign();
    campaign.setName(name);
    campaign.setState(CleanupCampaignState.DRAFT);
    campaign.setParams(settingService.getEffectiveParams(overrides));
    campaign.setStartedDate(System.currentTimeMillis());
    campaign = campaignStorage.createCampaign(campaign);
    try {
      scanService.startScan(campaign.getId());
    } catch (ObjectNotFoundException e) {
      throw new IllegalStateException("Freshly created cleanup campaign not found", e);
    }
    return withAggregates(campaignStorage.getCampaign(campaign.getId()));
  }

  /**
   * Publishes a SIMULATED campaign, starting its grace period. At most one
   * campaign can be PUBLISHED/LOCKED/EXECUTING platform-wide.
   *
   * @param campaignId campaign identifier
   * @return the published campaign
   * @throws ObjectNotFoundException when the campaign doesn't exist
   */
  public CleanupCampaign publishCampaign(long campaignId) throws ObjectNotFoundException {
    // Check + transition are made mutually exclusive (see publishLock javadoc):
    // without the lock, two concurrent publishes could both pass the
    // single-active check before either transitions (TOCTOU)
    synchronized (publishLock) {
      CleanupCampaign campaign = getCampaign(campaignId);
      // State guard delegated to the lifecycle; the platform-wide single-active
      // invariant is a business rule of this Service
      if (!campaignStorage.getCampaignsByStates(ACTIVE_STATES).isEmpty()) {
        throw new IllegalArgumentException("cleanup.campaignAlreadyActive");
      }
      long now = System.currentTimeMillis();
      campaign.setPublishedDate(now);
      campaign.setLockDate(now + TimeUnit.DAYS.toMillis(campaign.getParams().getGraceDays()));
      campaign = campaignLifecycle.transition(campaign, CleanupCampaignState.PUBLISHED, this::refreshCandidate);
      return withAggregates(campaign);
    }
  }

  /**
   * Triggers the batched purge of a LOCKED campaign.
   *
   * @param campaignId campaign identifier
   * @return the campaign, now EXECUTING
   * @throws ObjectNotFoundException when the campaign doesn't exist
   */
  public CleanupCampaign executeCampaign(long campaignId) throws ObjectNotFoundException {
    return withAggregates(executionService.startExecution(campaignId));
  }

  /**
   * Cancels a campaign from any non-terminal state.
   *
   * @param campaignId campaign identifier
   * @throws ObjectNotFoundException when the campaign doesn't exist
   */
  public void cancelCampaign(long campaignId) throws ObjectNotFoundException {
    CleanupCampaign campaign = getCampaign(campaignId);
    campaign.setCompletedDate(System.currentTimeMillis());
    campaignLifecycle.transition(campaign, CleanupCampaignState.CANCELLED);
  }

  /**
   * Scheduled-glue entry point: flips the PUBLISHED campaign to LOCKED once its
   * grace deadline elapsed.
   */
  public void lockExpiredPublishedCampaign() {
    List<CleanupCampaign> published = campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED));
    long now = System.currentTimeMillis();
    for (CleanupCampaign campaign : published) {
      if (campaign.getLockDate() > 0 && campaign.getLockDate() <= now) {
        campaignLifecycle.transition(campaign, CleanupCampaignState.LOCKED);
      }
    }
  }

  /**
   * User decision to keep a candidate file: checks ownership (own file, or
   * manager of the owning space), adds the exemption mixin and marks the item
   * EXEMPTED.
   *
   * @param itemId campaign item identifier
   * @param username user requesting to keep the file
   * @throws ObjectNotFoundException when the item or its node doesn't exist
   * @throws IllegalAccessException when the user doesn't own the file
   */
  public void keepItem(long itemId, String username) throws ObjectNotFoundException, IllegalAccessException {
    CleanupCampaignItem item = campaignStorage.getItem(itemId);
    if (item == null) {
      throw new ObjectNotFoundException("cleanup.itemNotFound");
    }
    CleanupCampaign campaign = campaignStorage.getCampaign(item.getCampaignId());
    if (campaign == null || campaign.getState() != CleanupCampaignState.PUBLISHED) {
      throw new IllegalArgumentException("cleanup.campaignNotPublished");
    }
    checkOwnership(item.getOwnerIdentityId(), username);
    if (item.getState() != CleanupItemState.CANDIDATE && item.getState() != CleanupItemState.EXEMPTED) {
      throw new IllegalArgumentException("cleanup.itemNotCandidate");
    }
    if (cleanupJcrStorage.addExemptionMixin(item.getNodeUuid(), username) != CleanupExemptionResult.ADDED) {
      item.setState(CleanupItemState.GONE);
      campaignStorage.saveItem(item);
      throw new ObjectNotFoundException("cleanup.nodeNotFound");
    }
    item.setState(CleanupItemState.EXEMPTED);
    item.setDecidedBy(username);
    item.setDecidedAt(System.currentTimeMillis());
    campaignStorage.saveItem(item);
  }

  /**
   * Bulk variant of {@link #keepItem(long, String)}.
   *
   * @param itemIds campaign item identifiers
   * @param username user requesting to keep the files
   * @throws ObjectNotFoundException when an item or its node doesn't exist
   * @throws IllegalAccessException when the user doesn't own one of the files
   */
  public void keepItems(List<Long> itemIds, String username) throws ObjectNotFoundException, IllegalAccessException {
    if (itemIds == null || itemIds.isEmpty()) {
      throw new IllegalArgumentException("cleanup.itemIdsMandatory");
    }
    for (Long itemId : itemIds) {
      keepItem(itemId, username);
    }
  }

  /**
   * Freshness refresh (UX only, not the correctness guarantee), called by the
   * JCR observation glue while a campaign is PUBLISHED.
   *
   * @param itemPath JCR event path (node or property path)
   * @param eventType JCR event type name
   */
  public void refreshCandidate(String itemPath, String eventType) {
    List<CleanupCampaign> published = campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED));
    if (published.isEmpty()) {
      return;
    }
    CleanupCampaign campaign = published.get(0);
    List<CleanupCampaignItem> items = campaignStorage.getItemsByPathPrefixOf(campaign.getId(), itemPath);
    for (CleanupCampaignItem item : items) {
      if (item.getState() != CleanupItemState.CANDIDATE) {
        continue;
      }
      try {
        CleanupRevalidation revalidation = cleanupJcrStorage.revalidate(item.getNodeUuid(), campaign.getParams());
        CleanupRevalidationUtil.applyRevalidation(item, revalidation);
        campaignStorage.saveItem(item);
      } catch (Exception e) {
        LOG.debug("Error refreshing cleanup candidate {} after JCR event {}", item.getNodeUuid(), eventType, e);
      }
    }
  }

  /**
   * Campaign items with optional filters, for the admin console.
   *
   * @param campaignId campaign identifier
   * @param ownerIdentityId optional owner filter
   * @param state optional item state filter
   * @param action optional action filter
   * @param minSize optional minimal content size filter
   * @param pageable page, size and sort
   * @return page of items
   * @throws ObjectNotFoundException when the campaign doesn't exist
   */
  public Page<CleanupCampaignItem> getCampaignItems(long campaignId, // NOSONAR
                                                    Long ownerIdentityId,
                                                    CleanupItemState state,
                                                    CleanupAction action,
                                                    Long minSize,
                                                    Pageable pageable) throws ObjectNotFoundException {
    getCampaign(campaignId);
    return campaignStorage.getItems(campaignId, ownerIdentityId, state, action, minSize, pageable);
  }

  /**
   * Delta between two campaigns' candidate sets, matched by node uuid.
   *
   * @param baseCampaignId base campaign identifier
   * @param otherCampaignId compared campaign identifier
   * @return comparison counters and bytes
   * @throws ObjectNotFoundException when either campaign doesn't exist
   */
  public CleanupComparison compareCampaigns(long baseCampaignId, long otherCampaignId) throws ObjectNotFoundException {
    getCampaign(baseCampaignId);
    getCampaign(otherCampaignId);
    Map<String, Long> baseItems = campaignStorage.getNodeUuidToReclaimableBytes(baseCampaignId);
    Map<String, Long> otherItems = campaignStorage.getNodeUuidToReclaimableBytes(otherCampaignId);
    CleanupComparison comparison = new CleanupComparison();
    comparison.setBaseCampaignId(baseCampaignId);
    comparison.setOtherCampaignId(otherCampaignId);
    baseItems.forEach((nodeUuid, bytes) -> {
      if (otherItems.containsKey(nodeUuid)) {
        comparison.setPersistingCount(comparison.getPersistingCount() + 1);
        comparison.setPersistingBytes(comparison.getPersistingBytes() + bytes);
      } else {
        comparison.setNewCount(comparison.getNewCount() + 1);
        comparison.setNewBytes(comparison.getNewBytes() + bytes);
      }
    });
    otherItems.forEach((nodeUuid, bytes) -> {
      if (!baseItems.containsKey(nodeUuid)) {
        comparison.setGoneCount(comparison.getGoneCount() + 1);
        comparison.setGoneBytes(comparison.getGoneBytes() + bytes);
      }
    });
    return comparison;
  }

  /**
   * Items of the currently relevant campaign owned by the user (own files and
   * files of spaces they manage), sorted by size descending.
   *
   * @param username user
   * @param page page index
   * @param size page size
   * @return page of items
   * @throws ObjectNotFoundException when no relevant campaign exists
   */
  public Page<CleanupCampaignItem> getMyItems(String username, int page, int size) throws ObjectNotFoundException {
    CleanupCampaign campaign = getUserVisibleCampaign();
    List<Long> ownerIdentityIds = getUserOwnedIdentityIds(username);
    return campaignStorage.getItemsByOwners(campaign.getId(),
                                            ownerIdentityIds,
                                            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fileSize")));
  }

  /**
   * Per-user summary of the currently relevant campaign.
   *
   * @param username user
   * @return summary (candidates/kept during grace, outcome once completed)
   * @throws ObjectNotFoundException when no relevant campaign exists
   */
  public CleanupUserSummary getMyItemsSummary(String username) throws ObjectNotFoundException {
    CleanupCampaign campaign = getUserVisibleCampaign();
    List<Long> ownerIdentityIds = getUserOwnedIdentityIds(username);
    long campaignId = campaign.getId();
    CleanupUserSummary summary = new CleanupUserSummary();
    summary.setCampaignId(campaignId);
    summary.setState(campaign.getState());
    summary.setDeadline(campaign.getLockDate());
    summary.setCandidateCount(campaignStorage.countItemsByOwnersAndState(campaignId,
                                                                         ownerIdentityIds,
                                                                         CleanupItemState.CANDIDATE));
    summary.setKeptCount(campaignStorage.countItemsByOwnersAndState(campaignId, ownerIdentityIds, CleanupItemState.EXEMPTED));
    summary.setCandidateBytes(campaignStorage.sumReclaimableBytesByOwnersAndState(campaignId,
                                                                                  ownerIdentityIds,
                                                                                  CleanupItemState.CANDIDATE));
    summary.setKeptBytes(campaignStorage.sumReclaimableBytesByOwnersAndState(campaignId,
                                                                             ownerIdentityIds,
                                                                             CleanupItemState.EXEMPTED));
    if (campaign.getState() == CleanupCampaignState.COMPLETED) {
      CleanupUserSummary.CleanupUserOutcome outcome = new CleanupUserSummary.CleanupUserOutcome();
      outcome.setDeletedCount(campaignStorage.countItemsByOwnersAndState(campaignId,
                                                                         ownerIdentityIds,
                                                                         CleanupItemState.PURGED));
      outcome.setFreedBytes(campaignStorage.sumReclaimedBytesByOwners(campaignId, ownerIdentityIds));
      outcome.setKeptCount(summary.getKeptCount());
      summary.setOutcome(outcome);
    }
    return summary;
  }

  /**
   * CSV report of a campaign: generated live while item detail is retained,
   * served from the {@link FileService} archive afterwards.
   *
   * @param campaignId campaign identifier
   * @return CSV content
   * @throws ObjectNotFoundException when the campaign or its report doesn't
   *           exist anymore
   */
  public byte[] getArchiveCsv(long campaignId) throws ObjectNotFoundException {
    CleanupCampaign campaign = getCampaign(campaignId);
    if (campaignStorage.hasItems(campaignId)) {
      return buildCsv(campaignId);
    } else if (campaign.getArchiveFileId() != null) {
      try {
        FileItem fileItem = fileService.getFile(campaign.getArchiveFileId());
        if (fileItem != null) {
          return fileItem.getAsByte();
        }
      } catch (Exception e) {
        LOG.warn("Error reading cleanup campaign {} archive file {}", campaignId, campaign.getArchiveFileId(), e);
      }
    }
    throw new ObjectNotFoundException("cleanup.archiveNotFound");
  }

  /**
   * Scheduled-glue entry point: keeps item detail for the last N terminal
   * campaigns only; older reports are archived as CSV (FileService) then their
   * item detail is purged. Campaign headers are retained forever.
   */
  public void applyRetention() {
    int retention = settingService.getReportRetentionCampaigns();
    List<CleanupCampaign> terminalCampaigns =
                                            new ArrayList<>(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.COMPLETED,
                                                                                                         CleanupCampaignState.CANCELLED)));
    terminalCampaigns.sort(Comparator.comparingLong(CleanupCampaign::getCompletedDate).reversed());
    for (int i = retention; i < terminalCampaigns.size(); i++) {
      CleanupCampaign campaign = terminalCampaigns.get(i);
      if (campaignStorage.hasItems(campaign.getId())) {
        archiveAndPurgeItems(campaign);
      }
    }
  }

  private void archiveAndPurgeItems(CleanupCampaign campaign) {
    try {
      byte[] csv = buildCsv(campaign.getId());
      FileItem fileItem = new FileItem(null,
                                       "cleanup-campaign-" + campaign.getId() + ".csv",
                                       "text/csv",
                                       FILE_NAMESPACE,
                                       csv.length,
                                       new Date(),
                                       "system",
                                       false,
                                       new ByteArrayInputStream(csv));
      fileItem = fileService.writeFile(fileItem);
      campaign.setArchiveFileId(fileItem.getFileInfo().getId());
      campaignStorage.saveCampaign(campaign);
      campaignStorage.deleteItems(campaign.getId());
    } catch (Exception e) {
      LOG.warn("Error archiving cleanup campaign {} report, keeping its item detail", campaign.getId(), e);
    }
  }

  private byte[] buildCsv(long campaignId) {
    StringBuilder csv =
                      new StringBuilder("nodeUuid,path,ownerIdentityId,action,state,fileSize,versionsSize,reclaimedBytes,failureReason\n");
    int pageIndex = 0;
    Page<CleanupCampaignItem> page;
    do {
      page = campaignStorage.getItemsPage(campaignId, PageRequest.of(pageIndex++, CSV_PAGE_SIZE, Sort.by("id")));
      for (CleanupCampaignItem item : page.getContent()) {
        csv.append(escapeCsv(item.getNodeUuid()))
           .append(',')
           .append(escapeCsv(item.getPath()))
           .append(',')
           .append(item.getOwnerIdentityId())
           .append(',')
           .append(item.getAction().name())
           .append(',')
           .append(item.getState().name())
           .append(',')
           .append(item.getFileSize())
           .append(',')
           .append(item.getVersionsSize())
           .append(',')
           .append(item.getReclaimedBytes())
           .append(',')
           .append(escapeCsv(item.getFailureReason()))
           .append('\n');
      }
    } while (page.hasNext());
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  private String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  private void checkOwnership(long ownerIdentityId, String username) throws IllegalAccessException {
    Identity ownerIdentity = identityManager.getIdentity(ownerIdentityId);
    if (ownerIdentity == null) {
      throw new IllegalAccessException("User %s can't decide for unknown owner identity %s".formatted(username, ownerIdentityId));
    }
    if (ownerIdentity.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(ownerIdentity.getRemoteId());
      if (space == null || !spaceService.isManager(space, username)) {
        throw new IllegalAccessException("User %s isn't manager of space owning the file".formatted(username));
      }
    } else if (!StringUtils.equals(ownerIdentity.getRemoteId(), username)) {
      throw new IllegalAccessException("User %s isn't the owner of the file".formatted(username));
    }
  }

  private CleanupCampaign getUserVisibleCampaign() throws ObjectNotFoundException {
    List<CleanupCampaign> activeCampaigns = campaignStorage.getCampaignsByStates(ACTIVE_STATES);
    if (!activeCampaigns.isEmpty()) {
      return activeCampaigns.get(0);
    }
    return campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.COMPLETED))
                          .stream()
                          .max(Comparator.comparingLong(CleanupCampaign::getCompletedDate))
                          .orElseThrow(() -> new ObjectNotFoundException("cleanup.noRelevantCampaign"));
  }

  private List<Long> getUserOwnedIdentityIds(String username) {
    List<Long> ownerIdentityIds = new ArrayList<>();
    Identity userIdentity = identityManager.getOrCreateUserIdentity(username);
    if (userIdentity != null) {
      ownerIdentityIds.add(Long.parseLong(userIdentity.getId()));
    }
    try {
      Space[] managedSpaces = spaceService.getManagerSpaces(username).load(0, MAX_MANAGED_SPACES);
      for (Space space : managedSpaces) {
        Identity spaceIdentity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
        if (spaceIdentity != null) {
          ownerIdentityIds.add(Long.parseLong(spaceIdentity.getId()));
        }
      }
    } catch (Exception e) {
      LOG.warn("Error retrieving managed spaces of user {}", username, e);
    }
    return ownerIdentityIds;
  }

  private CleanupCampaign withAggregates(CleanupCampaign campaign) {
    long campaignId = campaign.getId();
    campaign.setItemsRetained(campaignStorage.hasItems(campaignId));
    if (!campaign.isItemsRetained() && TERMINAL_STATES.contains(campaign.getState())
        && StringUtils.isNotBlank(campaign.getSummaryJson())) {
      // The retention job purged the item rows: the live aggregates would all
      // be 0, serve the summary snapshotted at campaign completion instead
      CleanupCampaignSummary summary = JsonUtils.fromJsonString(campaign.getSummaryJson(), CleanupCampaignSummary.class);
      campaign.setCandidateCount(summary.getCandidateCount());
      campaign.setReclaimableBytes(summary.getReclaimableBytes());
      campaign.setReclaimedBytes(summary.getReclaimedBytes());
    } else {
      campaign.setCandidateCount(campaignStorage.countItemsByState(campaignId, CleanupItemState.CANDIDATE));
      campaign.setReclaimableBytes(campaignStorage.sumReclaimableBytesByState(campaignId, CleanupItemState.CANDIDATE));
      campaign.setReclaimedBytes(campaignStorage.sumReclaimedBytes(campaignId));
    }
    return campaign;
  }

  /**
   * Bounded backoff around the observation listener registration: at startup
   * JCR may not be ready yet, so a single attempt would silently leave a
   * PUBLISHED campaign without its freshness listener until the next restart.
   */
  private void registerObservationListenerWithRetry() {
    for (int attempt = 1; attempt <= LISTENER_RETRY_MAX_ATTEMPTS; attempt++) {
      if (cleanupJcrStorage.registerObservationListener(this::refreshCandidate)) {
        return;
      }
      if (attempt < LISTENER_RETRY_MAX_ATTEMPTS) {
        try {
          Thread.sleep(LISTENER_RETRY_DELAY_MILLIS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
    // Warn only after the final failure: transient unavailability is expected
    LOG.warn("Error re-registering cleanup campaign observation listener at startup after {} attempts",
             LISTENER_RETRY_MAX_ATTEMPTS);
  }

}
