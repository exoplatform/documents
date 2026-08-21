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
import org.springframework.beans.factory.annotation.Value;
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
import org.exoplatform.document.cleanup.util.CleanupSizeUtil;
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

  /**
   * Floor on the interval between two progress PUSHES, independent of the
   * checkpoint cadence beside it.
   * <p>
   * Two seconds keeps the freshness the console needs — a bar that moves while an
   * administrator watches it — while bounding the event rate whatever the batch
   * size becomes. It has to be bounded separately because a push does not cost
   * what a checkpoint costs: {@code sendToAdministrators} enumerates every
   * CONNECTED user and resolves each identity to test administrator membership,
   * so its cost scales with the audience and is the one per-batch cost that does
   * NOT shrink when the batch does.
   * <p>
   * A property rather than a constant for the same reason the scan's inactivity
   * bound is one: it makes the two cadences independently testable, so the
   * throttle can be pinned without a test depending on wall-clock timing.
   */
  @Value("${documents.cleanup.purge.progress.push.interval:2000}")
  private long                     progressPushIntervalMillis;

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
      // The PURGE batch, not the scan's: a checkpoint boundary rather than a
      // queue envelope (see CleanupSettingService#getPurgeBatchSize)
      int batchSize = settingService.getPurgeBatchSize();
      long total = campaign.getTotalCount();
      // ETA DENOMINATOR IN BYTES, read once for this run: what is left to
      // reclaim when it starts, so a resumed worker measures its own remaining
      // work and not the interrupted run's
      long remainingBytesAtStart = campaignStorage.sumReclaimableBytesByState(campaignId, CleanupItemState.CANDIDATE);
      long reclaimableBytesDone = 0;
      // ZERO so the FIRST batch always pushes: the bar has to move as soon as
      // something has been purged, whatever the throttle below
      long lastPushedAt = 0;
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
      Long lastReclaimableBytes = null;
      List<CleanupCampaignItem> batch = campaignStorage.getItemsByStateBiggestFirst(campaignId,
                                                                                    CleanupItemState.CANDIDATE,
                                                                                    lastReclaimableBytes,
                                                                                    lastId,
                                                                                    batchSize);
      while (!batch.isEmpty()) {
        if (isAborted(campaignId)) {
          return;
        }
        CleanupCampaignItem lastOfBatch = batch.get(batch.size() - 1);
        long batchLastReclaimableBytes = reclaimableBytesOf(lastOfBatch);
        long batchLastId = lastOfBatch.getId();
        for (CleanupCampaignItem item : batch) {
          // PREDICTED bytes, not reclaimed: the denominator above is built from
          // what the report says each item frees, so the numerator has to speak
          // the same unit — an item skipped or failed still consumed its share
          // of the work and must leave the remaining estimate
          reclaimableBytesDone += reclaimableBytesOf(item);
          processItem(item, params);
        }
        // The cursor MUST advance here, and from the batch itself: the query
        // answers biggest first, so the last element carries the position reached.
        // Leaving it at its previous value would re-read the very same window and
        // hand back the poison pill the keyset paging exists to defuse.
        //
        // Read from the row as the QUERY returned it — which is why the pair is
        // captured before the loop above mutated anything: a revalidation
        // refreshes an item's action and sizes, i.e. the very key this cursor is
        // computed from, so taking it afterwards would move the cursor to a
        // position the database never held
        lastReclaimableBytes = batchLastReclaimableBytes;
        lastId = batchLastId;
        processed += batch.size();
        long etaSeconds = purgeEtaSeconds(startTime,
                                          processedAtStart,
                                          processed,
                                          total,
                                          remainingBytesAtStart,
                                          reclaimableBytesDone);
        campaignStorage.updateProgress(campaignId, total, processed, etaSeconds, null, 0);
        // CHECKPOINT EVERY BATCH, PUSH AT MOST EVERY PUSH_INTERVAL: the two
        // cadences are decoupled because they do not cost the same thing. A
        // checkpoint is one row update, invisible next to the deletion it
        // records. A push is not: sendToAdministrators enumerates every CONNECTED
        // user and resolves each identity to test administrator membership, so
        // its cost scales with the audience and NOT with the batch — the one
        // per-batch cost that does not shrink when the batch does. Tying it to a
        // checkpointing decision multiplied the event rate by forty the day the
        // purge batch went from 200 to 5, and a campaign of small version purges
        // can settle five items several times a second. The console loses
        // nothing: it also derives the numerator from the campaign's own
        // aggregates on every load
        long nowMillis = System.currentTimeMillis();
        if (nowMillis - lastPushedAt >= progressPushIntervalMillis) {
          lastPushedAt = nowMillis;
          webSocketService.sendToAdministrators(new CleanupWsMessage(CleanupWsMessage.PROGRESS_EVENT,
                                                                     campaignId,
                                                                     CleanupCampaignState.EXECUTING.name(),
                                                                     processed,
                                                                     total,
                                                                     etaSeconds));
        }
        // Per-batch transaction boundary: commits this batch and opens a fresh
        // one, so a database failure inside a later batch can no longer roll
        // back the batches that already completed
        restartTransaction();
        batch = campaignStorage.getItemsByStateBiggestFirst(campaignId,
                                                            CleanupItemState.CANDIDATE,
                                                            lastReclaimableBytes,
                                                            lastId,
                                                            batchSize);
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
  /**
   * What a row frees, through the ONE definition the report, the ordering and the
   * ETA all share — so the cursor this drives can never disagree with the SQL
   * expression the query ordered by.
   *
   * @param item campaign item, as the query returned it
   * @return bytes this item's own action reclaims
   */
  private long reclaimableBytesOf(CleanupCampaignItem item) {
    return CleanupSizeUtil.reclaimableBytes(item.getAction() == null ? null : item.getAction().name(),
                                            item.getFileSize(),
                                            item.getVersionsSize());
  }

  /**
   * Remaining time of a purge, measured in BYTES rather than in items.
   * <p>
   * WHY THE ITEM COUNT LIES HERE. A purge's items differ in cost by orders of
   * magnitude — a 5 GB file carrying five hundred versions against a 5 MB one —
   * and what a deletion actually costs is dominated by the bytes it destroys in
   * JCR and in the file store. So a count-based average predicted the remaining
   * time of the items it had already met, not of the ones left: a run that
   * started on small files kept promising their rate for hours. Weighting by the
   * bytes each item is expected to free is the same arithmetic against the unit
   * that drives the cost.
   * <p>
   * Falls back to the item count when the byte denominator is 0 — a campaign
   * whose candidates free nothing measurable (versions-only rows with tiny
   * histories) would otherwise get NO estimate at all, where counting items is
   * exactly as good as anything else.
   * <p>
   * The estimate stays CUMULATIVE over the run rather than windowed, on purpose:
   * a rolling window over five-item checkpoints oscillates with every large file
   * met, and an estimate that jumps is read as broken faster than one that is
   * merely smooth and late.
   *
   * @param startTime              epoch millis at which this run started
   * @param processedAtStart       items already processed when this run started
   * @param processed              items processed so far
   * @param total                  item denominator of the run
   * @param remainingBytesAtStart  bytes left to reclaim when this run started
   * @param reclaimableBytesDone   bytes this run has worked through
   * @return estimated remaining seconds, 0 when it cannot be estimated yet
   */
  private long purgeEtaSeconds(long startTime,
                               long processedAtStart,
                               long processed,
                               long total,
                               long remainingBytesAtStart,
                               long reclaimableBytesDone) {
    if (remainingBytesAtStart <= 0) {
      return CleanupEtaUtil.computeEtaSeconds(startTime, processedAtStart, processed, total);
    }
    return CleanupEtaUtil.computeEtaSeconds(startTime, 0, reclaimableBytesDone, remainingBytesAtStart);
  }

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
                                                                   cleanupJcrStorage.purgeVersions(item.getNodeUuid(), params);
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
