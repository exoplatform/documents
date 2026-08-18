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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupCampaignSummary;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupPurgeResult;
import org.exoplatform.document.cleanup.model.CleanupRevalidation;
import org.exoplatform.document.cleanup.storage.CleanupCampaignStorage;
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage;
import org.exoplatform.document.cleanup.util.CleanupRevalidationUtil;
import org.exoplatform.document.cleanup.websocket.CleanupWebSocketService;
import org.exoplatform.document.cleanup.websocket.CleanupWsMessage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.social.util.JsonUtils;

import jakarta.annotation.PreDestroy;

/**
 * Asynchronous batched purge of a LOCKED campaign, under a JCR system session.
 * The correctness guarantee is the per-item revalidation at delete time: a node
 * that disappeared, got exempted, was excluded or no longer matches the
 * criteria is spared, whatever the dry-run said.
 */
@Service
public class CleanupExecutionService {

  private static final Log         LOG             = ExoLogger.getLogger(CleanupExecutionService.class);

  @Autowired
  private CleanupCampaignStorage   campaignStorage;

  @Autowired
  private CleanupCampaignLifecycle campaignLifecycle;

  @Autowired
  private CleanupJcrStorage        cleanupJcrStorage;

  @Autowired
  private CleanupSettingService    settingService;

  @Autowired
  private CleanupWebSocketService  webSocketService;

  private ExecutorService          executorService  = Executors.newSingleThreadExecutor();

  /**
   * Campaign ids whose execution worker is currently running: guards against a
   * double-start (e.g. the startup recovery racing a manual trigger).
   */
  private final Set<Long>          runningCampaigns = ConcurrentHashMap.newKeySet();

  @PreDestroy
  public void shutdown() {
    executorService.shutdownNow();
  }

  /**
   * Switches a LOCKED campaign to EXECUTING and launches the asynchronous
   * purge.
   *
   * @param campaignId campaign identifier
   * @return the updated campaign
   * @throws ObjectNotFoundException when the campaign doesn't exist
   */
  public CleanupCampaign startExecution(long campaignId) throws ObjectNotFoundException {
    CleanupCampaign campaign = campaignStorage.getCampaign(campaignId);
    if (campaign == null) {
      throw new ObjectNotFoundException("cleanup.campaignNotFound");
    }
    // Progress denominators now measure execution over the candidate set
    campaign.setTotalCount(campaignStorage.countItemsByState(campaignId, CleanupItemState.CANDIDATE));
    campaign.setProcessedCount(0);
    campaign.setEtaSeconds(0);
    // Guarded by the lifecycle: only LOCKED may enter EXECUTING
    campaign = campaignLifecycle.transition(campaign, CleanupCampaignState.EXECUTING);
    executorService.execute(() -> executeCampaign(campaignId));
    return campaign;
  }

  /**
   * Resumes after a restart the purge of a campaign left EXECUTING: the worker
   * is naturally resumable, it iterates the remaining CANDIDATE items. No-op
   * when this campaign's worker is already running.
   *
   * @param campaignId campaign identifier
   */
  public void resumeExecution(long campaignId) {
    executorService.execute(() -> executeCampaign(campaignId));
  }

  /**
   * Execution worker, running as system (no conversation state needed). Package
   * visible for tests.
   */
  protected void executeCampaign(long campaignId) { // NOSONAR
    if (!runningCampaigns.add(campaignId)) {
      // Already running: never double-start a campaign's execution worker
      return;
    }
    try {
      CleanupCampaign campaign = campaignStorage.getCampaign(campaignId);
      if (campaign == null || campaign.getState() != CleanupCampaignState.EXECUTING) {
        return;
      }
      CleanupParams params = campaign.getParams();
      int batchSize = settingService.getBatchSize();
      long total = campaign.getTotalCount();
      // Resume-aware: after a restart, already processed items are counted in
      long processed = campaign.getProcessedCount();
      long processedAtStart = processed;
      long startTime = System.currentTimeMillis();

      Page<CleanupCampaignItem> batch = campaignStorage.getItemsByState(campaignId,
                                                                        CleanupItemState.CANDIDATE,
                                                                        PageRequest.of(0, batchSize, Sort.by("id")));
      while (!batch.isEmpty()) {
        if (isAborted(campaignId)) {
          return;
        }
        for (CleanupCampaignItem item : batch.getContent()) {
          processItem(item, params);
        }
        processed += batch.getNumberOfElements();
        long etaSeconds = computeEtaSeconds(startTime, processedAtStart, processed, total);
        campaignStorage.updateProgress(campaignId, total, processed, etaSeconds, null, 0);
        webSocketService.sendToAdministrators(new CleanupWsMessage(CleanupWsMessage.PROGRESS_EVENT,
                                                                   campaignId,
                                                                   CleanupCampaignState.EXECUTING.name(),
                                                                   processed,
                                                                   total,
                                                                   etaSeconds));
        // Processed items always leave the CANDIDATE state, so page 0 shrinks
        batch = campaignStorage.getItemsByState(campaignId,
                                                CleanupItemState.CANDIDATE,
                                                PageRequest.of(0, batchSize, Sort.by("id")));
      }

      completeCampaign(campaignId, total, processed);
    } catch (Exception e) {
      LOG.error("Error while executing cleanup campaign {}", campaignId, e);
    } finally {
      runningCampaigns.remove(campaignId);
    }
  }

  /**
   * Per-item revalidation at delete time — THE correctness guarantee — then
   * purge. Any error skips the item and continues.
   */
  private void processItem(CleanupCampaignItem item, CleanupParams params) {
    try {
      CleanupRevalidation revalidation = cleanupJcrStorage.revalidate(item.getNodeUuid(), params);
      if (CleanupRevalidationUtil.applyRevalidation(item, revalidation)) {
        CleanupAction action = item.getAction();
        CleanupPurgeResult result = action == CleanupAction.DELETE ?
                                                                   cleanupJcrStorage.deleteNode(item.getNodeUuid()) :
                                                                   cleanupJcrStorage.purgeVersions(item.getNodeUuid(),
                                                                                                   params.getMaxVersionsPerFile());
        item.setState(result.getState());
        item.setReclaimedBytes(result.getReclaimedBytes());
        item.setFailureReason(result.getFailureReason());
        if (result.getState() == CleanupItemState.PURGED) {
          item.setPurgedAt(System.currentTimeMillis());
        }
      }
    } catch (Exception e) {
      LOG.warn("Error purging cleanup campaign item {} ({}), skipping it", item.getId(), item.getPath(), e);
      item.setState(CleanupItemState.SKIPPED);
      item.setFailureReason("cleanup.unexpectedError: " + e.getMessage());
    }
    campaignStorage.saveItem(item);
  }

  private void completeCampaign(long campaignId, long total, long processed) {
    CleanupCampaign campaign = campaignStorage.getCampaign(campaignId);
    if (campaign == null || campaign.getState() != CleanupCampaignState.EXECUTING) {
      return;
    }
    campaign.setCompletedDate(System.currentTimeMillis());
    campaign.setTotalCount(total);
    campaign.setProcessedCount(processed);
    campaign.setEtaSeconds(0);
    campaign.setSummaryJson(buildSummaryJson(campaignId));
    campaignLifecycle.transition(campaign, CleanupCampaignState.COMPLETED);
  }

  /**
   * Snapshots the campaign aggregates at completion, so they can still be
   * served once the retention job purges the item rows. Writer and reader
   * share {@link CleanupCampaignSummary}, keeping the JSON keys aligned.
   */
  private String buildSummaryJson(long campaignId) {
    CleanupCampaignSummary summary = new CleanupCampaignSummary();
    summary.setCandidateCount(campaignStorage.countItemsByState(campaignId, CleanupItemState.CANDIDATE));
    summary.setReclaimableBytes(campaignStorage.sumReclaimableBytesByState(campaignId, CleanupItemState.CANDIDATE));
    summary.setReclaimedBytes(campaignStorage.sumReclaimedBytes(campaignId));
    summary.setPurgedCount(campaignStorage.countItemsByState(campaignId, CleanupItemState.PURGED));
    summary.setExemptedCount(campaignStorage.countItemsByState(campaignId, CleanupItemState.EXEMPTED));
    summary.setSparedCount(campaignStorage.countItemsByState(campaignId, CleanupItemState.SPARED_BY_MODIFICATION));
    summary.setGoneCount(campaignStorage.countItemsByState(campaignId, CleanupItemState.GONE));
    summary.setSkippedCount(campaignStorage.countItemsByState(campaignId, CleanupItemState.SKIPPED));
    return JsonUtils.toJsonString(summary);
  }

  private boolean isAborted(long campaignId) {
    CleanupCampaign campaign = campaignStorage.getCampaign(campaignId);
    return campaign == null || campaign.getState() != CleanupCampaignState.EXECUTING;
  }

  private long computeEtaSeconds(long startTime, long processedAtStart, long processed, long total) {
    long elapsedMillis = System.currentTimeMillis() - startTime;
    long processedSinceStart = processed - processedAtStart;
    if (elapsedMillis <= 0 || processedSinceStart <= 0) {
      return 0;
    }
    double throughputPerMilli = (double) processedSinceStart / elapsedMillis;
    return (long) ((total - processed) / throughputPerMilli / 1000);
  }

}
