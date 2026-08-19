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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.storage.CleanupCampaignStorage;
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage;
import org.exoplatform.document.cleanup.util.CleanupConstants;
import org.exoplatform.document.cleanup.websocket.CleanupWebSocketService;
import org.exoplatform.document.cleanup.websocket.CleanupWsMessage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.common.ContainerTransactional;

import jakarta.annotation.PreDestroy;

/**
 * Asynchronous dry-run scan of the collaboration workspace: counts the
 * denominator first, then walks path-ordered nt:file batches, persisting
 * candidates, a path-based resume checkpoint (root in progress + last processed
 * path) and a rolling-throughput ETA per batch. Nothing is ever deleted here.
 */
@Service
public class CleanupScanService {

  private static final List<String> SCAN_ROOTS       = CleanupConstants.SCAN_ROOTS;

  private static final Log          LOG              = ExoLogger.getLogger(CleanupScanService.class);

  @Autowired
  private CleanupCampaignStorage    campaignStorage;

  @Autowired
  private CleanupCampaignLifecycle  campaignLifecycle;

  @Autowired
  private CleanupJcrStorage         cleanupJcrStorage;

  @Autowired
  private CleanupSettingService     settingService;

  @Autowired
  private CleanupWebSocketService   webSocketService;

  private ExecutorService           executorService  = Executors.newSingleThreadExecutor();

  /**
   * Campaign ids whose scan worker is currently running: guards against a
   * double-start (e.g. the startup recovery racing a manual start).
   */
  private final Set<Long>           runningCampaigns = ConcurrentHashMap.newKeySet();

  @PreDestroy
  public void shutdown() {
    executorService.shutdownNow();
  }

  /**
   * Starts (or resumes after a restart) the dry-run scan of a campaign.
   *
   * @param campaignId campaign identifier
   * @throws ObjectNotFoundException when the campaign doesn't exist
   */
  public void startScan(long campaignId) throws ObjectNotFoundException {
    CleanupCampaign campaign = campaignStorage.getCampaign(campaignId);
    if (campaign == null) {
      throw new ObjectNotFoundException("cleanup.campaignNotFound");
    }
    if (campaign.getState() != CleanupCampaignState.DRY_RUN_RUNNING) {
      // Guarded by the lifecycle: only DRAFT may enter DRY_RUN_RUNNING
      campaignLifecycle.transition(campaign, CleanupCampaignState.DRY_RUN_RUNNING);
    }
    executorService.execute(() -> scanTransactional(campaignId));
  }

  @ContainerTransactional
  public void scanTransactional(long campaignId) {
    scan(campaignId);
  }

  /**
   * Scan worker, running as system (no conversation state needed).
   */
  private void scan(long campaignId) { // NOSONAR
    if (!runningCampaigns.add(campaignId)) {
      // Already running: never double-start a campaign's scan worker
      return;
    }
    try {
      CleanupCampaign campaign = campaignStorage.getCampaign(campaignId);
      if (campaign == null || campaign.getState() != CleanupCampaignState.DRY_RUN_RUNNING) {
        return;
      }
      CleanupParams params = campaign.getParams();
      int batchSize = settingService.getBatchSize();

      long[] rootTotals = new long[SCAN_ROOTS.size()];
      long countedTotal = 0;
      for (int i = 0; i < SCAN_ROOTS.size(); i++) {
        rootTotals[i] = cleanupJcrStorage.countFiles(SCAN_ROOTS.get(i));
        countedTotal += rootTotals[i];
      }
      long total = countedTotal;

      // The persisted checkpoint path identifies WHICH root is in progress
      // (completed roots are never re-walked) and WHERE to resume in it: a
      // node path resumes its root strictly after that path, a bare root path
      // marks a root not started yet (also the legacy checkpoint shape)
      String checkpointPath = campaign.getCheckpointPath();
      int resumeRootIndex = 0;
      String resumeAfterPath = null;
      if (StringUtils.isNotBlank(checkpointPath)) {
        for (int i = 0; i < SCAN_ROOTS.size(); i++) { // NOSONAR
          if (StringUtils.equals(checkpointPath, SCAN_ROOTS.get(i))) {
            resumeRootIndex = i;
            break;
          } else if (checkpointPath.startsWith(SCAN_ROOTS.get(i) + "/")) {
            resumeRootIndex = i;
            resumeAfterPath = checkpointPath;
            break;
          }
        }
      }
      long processedBase = 0;
      for (int i = 0; i < resumeRootIndex; i++) {
        processedBase += rootTotals[i];
      }
      // Scanned-in-root count: progress/ETA display only, NEVER positioning
      long resumeScannedInRoot = resumeAfterPath == null ? 0 : campaign.getCheckpointOffset();
      long startTime = System.currentTimeMillis();
      long processedAtStart = processedBase + resumeScannedInRoot;

      for (int rootIndex = resumeRootIndex; rootIndex < SCAN_ROOTS.size(); rootIndex++) {
        String root = SCAN_ROOTS.get(rootIndex);
        long rootTotal = rootTotals[rootIndex];
        long rootProcessedBase = processedBase;
        AtomicLong scannedInRoot = new AtomicLong(rootIndex == resumeRootIndex ? resumeScannedInRoot : 0);
        cleanupJcrStorage.scanRoot(root,
                                   rootIndex == resumeRootIndex ? resumeAfterPath : null,
                                   batchSize,
                                   params,
                                   (candidates, lastScannedPath, scannedCount) -> {
                                     if (isAborted(campaignId)) {
                                       return false;
                                     }
                                     campaignStorage.saveCandidates(campaignId, candidates);
                                     long scanned = scannedInRoot.addAndGet(scannedCount);
                                     long processed = rootProcessedBase + Math.min(scanned, rootTotal);
                                     long etaSeconds = computeEtaSeconds(startTime, processedAtStart, processed, total);
                                     campaignStorage.updateProgress(campaignId,
                                                                    total,
                                                                    processed,
                                                                    etaSeconds,
                                                                    lastScannedPath,
                                                                    scanned);
                                     webSocketService.sendToAdministrators(new CleanupWsMessage(CleanupWsMessage.PROGRESS_EVENT,
                                                                                                campaignId,
                                                                                                CleanupCampaignState.DRY_RUN_RUNNING.name(),
                                                                                                processed,
                                                                                                total,
                                                                                                etaSeconds));
                                     return true;
                                   });
        if (isAborted(campaignId)) {
          return;
        }
        processedBase += rootTotal;
        if (rootIndex < SCAN_ROOTS.size() - 1) {
          // Advance the checkpoint to the next root, so a crash right here
          // never re-iterates the root just completed on resume
          campaignStorage.updateProgress(campaignId,
                                         total,
                                         processedBase,
                                         computeEtaSeconds(startTime, processedAtStart, processedBase, total),
                                         SCAN_ROOTS.get(rootIndex + 1),
                                         0);
        }
      }

      campaign = campaignStorage.getCampaign(campaignId);
      if (campaign != null && campaign.getState() == CleanupCampaignState.DRY_RUN_RUNNING) {
        campaign.setTotalCount(total);
        campaign.setProcessedCount(total);
        campaign.setEtaSeconds(0);
        campaignLifecycle.transition(campaign, CleanupCampaignState.SIMULATED);
      }
    } catch (Exception e) {
      // Leave the campaign DRY_RUN_RUNNING: the scan is checkpoint-resumable
      LOG.warn("Error while scanning cleanup campaign {}. The scan can be resumed from its checkpoint.", campaignId, e);
    } finally {
      runningCampaigns.remove(campaignId);
    }
  }

  private boolean isAborted(long campaignId) {
    CleanupCampaign campaign = campaignStorage.getCampaign(campaignId);
    return campaign == null || campaign.getState() != CleanupCampaignState.DRY_RUN_RUNNING;
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
