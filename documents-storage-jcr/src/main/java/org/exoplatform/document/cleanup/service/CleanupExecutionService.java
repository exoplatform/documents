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

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.component.RequestLifeCycle;
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
import org.exoplatform.document.cleanup.util.CleanupEtaUtil;
import org.exoplatform.document.cleanup.util.CleanupRevalidationUtil;
import org.exoplatform.document.cleanup.util.CleanupThrowableUtil;
import org.exoplatform.document.cleanup.websocket.CleanupWebSocketService;
import org.exoplatform.document.cleanup.websocket.CleanupWsMessage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.common.ContainerTransactional;
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

  private static final Log         LOG              = ExoLogger.getLogger(CleanupExecutionService.class);

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
   * Switches a campaign to EXECUTING and launches the asynchronous purge. TWO
   * states may enter it, and the retry is the reason there are two: LOCKED (the
   * normal run, once the grace deadline elapsed) and COMPLETED (the retry of
   * {@code CleanupCampaignService#retryCampaign}, which requeues the retryable
   * failures and then reuses this very method verbatim).
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
    // Guarded by the lifecycle: LOCKED and COMPLETED are the only states from
    // which EXECUTING is reachable — a normal run and a retry, nothing else
    campaign = campaignLifecycle.transition(campaign, CleanupCampaignState.EXECUTING);
    executorService.execute(() -> executeCampaignTransactional(campaignId));
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
    executorService.execute(() -> executeCampaignTransactional(campaignId));
  }

  @ContainerTransactional
  public void executeCampaignTransactional(long campaignId) { // NOSONAR
    executeCampaign(campaignId);
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

      // KEYSET paging, never an offset page-0 re-read: the loop walks the ids
      // upwards, so the worker CANNOT revisit an item within a run whatever
      // happens to it. Re-reading page 0 relied on every processed item leaving
      // CANDIDATE — one item that can never be persisted then fed itself back
      // forever, and the stalled-worker watchdog kept relaunching the same run.
      // Resumability is untouched: a fresh run restarts from id 0 and the
      // already-processed items are simply no longer CANDIDATE
      long lastId = 0;
      List<CleanupCampaignItem> batch = campaignStorage.getItemsByStateAfterId(campaignId,
                                                                              CleanupItemState.CANDIDATE,
                                                                              lastId,
                                                                              batchSize);
      while (!batch.isEmpty()) {
        if (isAborted(campaignId)) {
          return;
        }
        for (CleanupCampaignItem item : batch) {
          processItem(item, params);
        }
        // The cursor MUST advance here, and from the batch itself: the query
        // answers ids ascending, so the last element carries the highest id met.
        // Leaving it at its previous value would re-read the very same window and
        // hand back the poison pill the keyset paging exists to defuse
        lastId = batch.get(batch.size() - 1).getId();
        processed += batch.size();
        long etaSeconds = CleanupEtaUtil.computeEtaSeconds(startTime, processedAtStart, processed, total);
        campaignStorage.updateProgress(campaignId, total, processed, etaSeconds, null, 0);
        webSocketService.sendToAdministrators(new CleanupWsMessage(CleanupWsMessage.PROGRESS_EVENT,
                                                                   campaignId,
                                                                   CleanupCampaignState.EXECUTING.name(),
                                                                   processed,
                                                                   total,
                                                                   etaSeconds));
        // Per-batch transaction boundary: commits this batch and opens a fresh
        // one, so a database failure inside a later batch can no longer roll
        // back the batches that already completed
        restartTransaction();
        batch = campaignStorage.getItemsByStateAfterId(campaignId, CleanupItemState.CANDIDATE, lastId, batchSize);
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
   * purge. Any error skips the item and continues; an UNKNOWN revalidation
   * (transient JCR read failure) skips the item too — never spared, never
   * deleted on doubt — with a distinct failure reason.
   */
  private void processItem(CleanupCampaignItem item, CleanupParams params) {
    try {
      CleanupRevalidation revalidation = cleanupJcrStorage.revalidate(item.getNodeUuid(), params);
      if (revalidation.isUnknown()) {
        item.setState(CleanupItemState.SKIPPED);
        item.setFailureReason("cleanup.revalidationFailed");
      } else if (CleanupRevalidationUtil.applyRevalidation(item, revalidation)) {
        CleanupAction action = item.getAction();
        CleanupPurgeResult result = action == CleanupAction.DELETE ?
                                                                   cleanupJcrStorage.deleteNode(item.getNodeUuid()) :
                                                                   cleanupJcrStorage.purgeVersions(item.getNodeUuid(),
                                                                                                   params.getMaxVersionsPerFile());
        item.setState(result.getState());
        item.setReclaimedBytes(result.getReclaimedBytes());
        item.setFailureReason(result.getFailureReason());
        item.setFailureDetail(result.getFailureDetail());
        if (result.getState() == CleanupItemState.PURGED) {
          item.setPurgedAt(System.currentTimeMillis());
        }
      }
      // The save is INSIDE the guarded region: it used to sit outside it, so a
      // failing save (an oversized value, a dead connection) escaped the batch
      // loop and the whole worker, leaving the item CANDIDATE in the database
      // with its SKIPPED state set in memory only — the watchdog then restarted
      // the worker on the very same item, forever
      saveItem(item);
    } catch (Exception e) {
      // LOG first, persist the compact detail second: if the persist fails in
      // turn, the full stack trace is already in the server log
      LOG.warn("Error purging cleanup campaign item {} ({}), skipping it", item.getId(), item.getPath(), e);
      item.setState(CleanupItemState.SKIPPED);
      item.setFailureReason("cleanup.unexpectedError");
      item.setFailureDetail(CleanupThrowableUtil.formatFailureDetail(e));
      saveItem(item);
    }
  }

  /**
   * Persists an item's outcome, NEVER rethrowing into the batch loop: a single
   * unsavable item must not abort the purge of every item after it.
   * <p>
   * On failure, ONE minimal fallback save is attempted, carrying the state and
   * the bare failure code with a null detail — the detail is by far the likeliest
   * cause of an oversized-value failure, so dropping it is what has a chance of
   * getting the row through. If even that fails, it is logged and the worker
   * moves on: the item stays CANDIDATE, and the keyset paging guarantees this run
   * will not meet it again.
   */
  private void saveItem(CleanupCampaignItem item) {
    try {
      campaignStorage.saveItem(item);
    } catch (Exception e) {
      LOG.error("Error saving cleanup campaign item {} ({}), retrying without its failure detail",
                item.getId(),
                item.getPath(),
                e);
      item.setFailureDetail(null);
      try {
        campaignStorage.saveItem(item);
      } catch (Exception fallbackException) {
        LOG.error("Error saving cleanup campaign item {} ({}) even without its failure detail, moving on",
                  item.getId(),
                  item.getPath(),
                  fallbackException);
      }
    }
  }

  /**
   * Commits the current batch and opens a fresh transaction. Extracted as a
   * single static call so the worker tests — which drive the worker body directly,
   * with no PortalContainer booted — can neutralize it.
   * <p>
   * The NO-ARG form is the correct one here: {@code ContainerTransactionalAspect}
   * sets the current container to the PortalContainer before its
   * {@code RequestLifeCycle.begin} and restores it afterwards, so
   * {@code ExoContainerContext.getCurrentContainer()} inside the worker IS the
   * right container. And {@code restartTransaction} ends then begins the SAME
   * number of nested lifecycles, so the aspect's own {@code end()} in its finally
   * block stays balanced.
   */
  protected void restartTransaction() {
    RequestLifeCycle.restartTransaction();
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
    campaign.setSummaryJson(buildSummaryJson(campaignId, campaign.getSummaryJson()));
    campaignLifecycle.transition(campaign, CleanupCampaignState.COMPLETED);
  }

  /**
   * Snapshots the campaign aggregates at completion, so they can still be
   * served once the retention job purges the item rows. Writer and reader share
   * {@link CleanupCampaignSummary}, keeping the JSON keys aligned.
   * <p>
   * The DRY-RUN's own verdict is CARRIED FORWARD from the summary the scan wrote
   * at SIMULATED, never rebuilt: a report produced from an incomplete scan stays
   * flagged incomplete for good, and overwriting this one column at completion
   * must not be what quietly erases that.
   *
   * @param campaignId campaign identifier
   * @param scanSummaryJson summary the scan left on the campaign, possibly blank
   */
  private String buildSummaryJson(long campaignId, String scanSummaryJson) {
    CleanupCampaignSummary summary = StringUtils.isBlank(scanSummaryJson) ? new CleanupCampaignSummary()
                                                                         : JsonUtils.fromJsonString(scanSummaryJson,
                                                                                                    CleanupCampaignSummary.class);
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

}
