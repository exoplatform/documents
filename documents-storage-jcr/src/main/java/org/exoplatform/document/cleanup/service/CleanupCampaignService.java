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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.exoplatform.commons.file.services.NameSpaceService;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.constant.CleanupExemptionResult;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupBulkResult;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignAggregates;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupCampaignSummary;
import org.exoplatform.document.cleanup.model.CleanupComparison;
import org.exoplatform.document.cleanup.model.CleanupComparisonBucket;
import org.exoplatform.document.cleanup.model.CleanupFailureGroup;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupScanUnitProgress;
import org.exoplatform.document.cleanup.model.CleanupRevalidation;
import org.exoplatform.document.cleanup.model.CleanupUserSummary;
import org.exoplatform.document.cleanup.storage.CleanupCampaignStorage;
import org.exoplatform.document.cleanup.storage.CleanupScanUnitStorage;
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage;
import org.exoplatform.document.cleanup.util.CleanupIdentityUtil;
import org.exoplatform.document.cleanup.util.CleanupRevalidationUtil;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.common.ContainerTransactional;
import io.meeds.social.util.JsonUtils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Cleanup campaigns lifecycle: dry-run launch, publication (single active
 * campaign platform-wide), grace-deadline locking, execution trigger,
 * cancellation, user exemptions ('keep'/'un-keep'), candidate freshness
 * refresh, campaign comparison, report retention and CSV archiving.
 */
@Service
public class CleanupCampaignService {

  /**
   * File-store namespace the archived CSV reports live in.
   * <p>
   * It MUST be registered with {@code NameSpaceService} before a file is written
   * under it: {@code DataStorage#create} looks the namespace up by name and
   * dereferences the result without a guard, so an unregistered name fails with a
   * raw NullPointerException wrapped in a FileStorageException — 'Error while
   * writing file cleanup-campaign-N.csv', once per retention tick, forever. Every
   * archive attempt failed that way, which also meant the retention job never
   * dropped a single item row: it archives BEFORE purging, deliberately, and the
   * archive never succeeded. See {@link #registerFileNamespace()}.
   */
  public static final String                      FILE_NAMESPACE              = "documentsCleanup";

  /**
   * Localizable message code reported for an ACL refusal in a bulk outcome, in
   * place of the {@link IllegalAccessException} raw English message.
   */
  public static final String                      NOT_OWNER_FAILURE_CODE      = "cleanup.notOwner";

  /**
   * Maximum campaign name length, mirroring the NAME column of
   * DOCUMENTS_CLEANUP_CAMPAIGN — {@code NVARCHAR(250) NOT NULL}. Nothing used to
   * check it, so a longer name reached the INSERT and failed with a raw database
   * error instead of a 400 carrying a message code the console can localize.
   */
  public static final int                         MAX_NAME_LENGTH             = 250;

  /**
   * Failure message codes a retry may re-attempt. TRANSIENT causes only: a JCR
   * read that failed once may succeed now, a delete that hit a locked node may
   * not, an unexpected error is by definition unclassified. Deliberately ABSENT:
   * {@code cleanup.referentialIntegrity} (another node points at the file — the
   * repository will refuse identically) and {@code cleanup.notVersionable} (the
   * node has no version history — it never will grow one by itself). Re-running
   * those is guaranteed wasted work on possibly hundreds of thousands of rows.
   * <p>
   * Public because the REST layer flags each grouped failure with it and the
   * tests assert against it: what is retryable is a SERVER decision, and the
   * client must never be the authority on it.
   */
  public static final Set<String>                 RETRYABLE_FAILURE_REASONS   = Set.of("cleanup.revalidationFailed",
                                                                                       "cleanup.deleteError",
                                                                                       "cleanup.unexpectedError",
                                                                                       "cleanup.purgeVersionsError");

  /**
   * States in which the GRACE PERIOD of a campaign may still be edited. The
   * grace period is the ONE parameter that is not a candidacy criterion — it is
   * read in a single place, to derive the lock date from the publication date —
   * so editing it can never invalidate the dry-run the administrator is
   * reviewing, and the guard is deliberately narrower than 'any state' rather
   * than absent.
   * <p>
   * LOCKED is REJECTED on purpose: extending the grace of a locked campaign
   * would mean going back to PUBLISHED, an edge that does NOT exist in
   * {@code CleanupCampaignLifecycle.ALLOWED_TRANSITIONS} — and exiting PUBLISHED
   * unregisters the freshness observation listener, so a reverse edge would have
   * to re-register it. Out of scope; do NOT 'fix' this casually. EXECUTING,
   * COMPLETED and CANCELLED are rejected because the grace period is MEANINGLESS
   * once the purge ran.
   * <p>
   * Public because the tests and any future caller must share this rule rather
   * than restate it.
   */
  public static final Set<CleanupCampaignState>   GRACE_EDITABLE_STATES       = Set.of(CleanupCampaignState.DRAFT,
                                                                                       CleanupCampaignState.SIMULATED,
                                                                                       CleanupCampaignState.PUBLISHED);

  /**
   * RETRIES an item may spend AFTER its initial purge attempt — three of them,
   * so a doomed item is purge-attempted four times in all. The initial attempt
   * spends no attempt: only the requeue increments the counter, and the requeue
   * filter is {@code attemptCount < 3}.
   * <p>
   * Deliberately NOT the same arithmetic as
   * {@code CleanupScanService#MAX_SCAN_UNIT_ATTEMPTS}, which bounds THREE walks
   * in total: a scan unit spends an attempt on its very first walk, because the
   * coordinator claiming a unit is what increments it. Same value, same purpose
   * — bounding a retry so it cannot loop forever — counted from a different
   * origin, and neither is a typo of the other.
   * <p>
   * Bounds the whole item retry mechanism: past it, a failure has proved itself
   * deterministic whatever its code says, and an administrator re-clicking Retry
   * must not be able to loop on it forever.
   */
  public static final long                        MAX_RETRY_ATTEMPTS          = 3;

  private static final Log                        LOG                         = ExoLogger.getLogger(CleanupCampaignService.class);

  private static final List<CleanupCampaignState> ACTIVE_STATES               = List.of(CleanupCampaignState.PUBLISHED,
                                                                                        CleanupCampaignState.LOCKED,
                                                                                        CleanupCampaignState.EXECUTING);

  /**
   * At most ONE of these platform-wide: the states in which a campaign owns a
   * JCR-heavy worker. Distinct from {@link #ACTIVE_STATES}, which guards the
   * single-PUBLISHED invariant — a published campaign in its grace period runs
   * nothing, and blocking simulations for the two weeks it lasts would break the
   * repeat-and-compare workflow the feature is specified around.
   * <p>
   * Two scans at once is ten more reader threads on the same repository, on a
   * corpus where ONE sequential walk already saturated both JCR caches at their
   * million-entry cap. A scan next to a purge is worse than slow: the scan reads a
   * tree the purge is deleting under it, and reports a simulation nobody can act
   * on.
   * <p>
   * WHAT THIS GUARD CANNOT SEE IS THREADS, and that gap is closed elsewhere. A
   * cancel takes the campaign out of {@code DRY_RUN_RUNNING} at once, so this
   * check opens while that run's readers may still be walking — the scan
   * coordinator deliberately abandons readers that do not answer their interrupt.
   * The fan-out is therefore bounded by counting the walking threads themselves
   * ({@code CleanupScanService#activeReaders}, read by
   * {@code CleanupScanService#readerCountFor}), and this state guard remains what
   * it is good at: keeping a scan and a purge apart, and refusing a second run to
   * the administrator up front instead of letting them queue.
   */
  private static final List<CleanupCampaignState> WORKER_STATES               = List.of(CleanupCampaignState.DRY_RUN_RUNNING,
                                                                                        CleanupCampaignState.EXECUTING);

  /**
   * The states a campaign may be DELETED from: nothing was promised to a user and
   * nothing was destroyed, so the row is a draft or a discarded simulation.
   * <p>
   * COMPLETED is deliberately absent. It is the only record that an irreversible
   * mass deletion happened, and the answer to "where did my file go?" months
   * later. Ageing those out is the retention job's business — it archives the CSV
   * before dropping the detail rows — not a delete button's.
   */
  private static final List<CleanupCampaignState> DELETABLE_STATES            = List.of(CleanupCampaignState.DRAFT,
                                                                                        CleanupCampaignState.SIMULATED,
                                                                                        CleanupCampaignState.CANCELLED);

  private static final List<CleanupCampaignState> TERMINAL_STATES             = List.of(CleanupCampaignState.COMPLETED,
                                                                                        CleanupCampaignState.CANCELLED);

  private static final int                        MAX_CAMPAIGNS               = 200;

  private static final int                        MANAGED_SPACES_PAGE_SIZE    = 100;

  private static final int                        CSV_PAGE_SIZE               = 1000;

  /**
   * Column order of the CSV report. The historical columns keep their POSITION
   * — a consumer parsing by index must not break — and the ones added since are
   * APPENDED after them.
   */
  private static final String                     CSV_HEADER                  =
                                                              "nodeUuid,path,ownerIdentityId,action,state,fileSize,versionsSize,reclaimedBytes,failureReason,ownerName,lastModifiedDate,createdDate,attemptCount,failureDetail\n";

  /**
   * Page size of the retry requeue. Keyset-paged like the execution worker, so
   * hundreds of thousands of failed items are never loaded into one List.
   */
  private static final int                        RETRY_PAGE_SIZE             = 1000;

  private static final int                        LISTENER_RETRY_MAX_ATTEMPTS = 10;

  private static final long                       LISTENER_RETRY_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(30);

  /**
   * Guards the check-then-transition of {@link #publishCampaign(long)}: the
   * single-active-campaign invariant would otherwise be racy (TOCTOU) between
   * two concurrent publish requests. In-JVM lock only: cluster-wide exclusion
   * is out of scope by spec assumption (single-node deployment).
   */
  private final Object                            publishLock                 = new Object();

  @Autowired
  private CleanupCampaignStorage                  campaignStorage;

  /**
   * Same domain's storage, injected directly rather than reached through
   * {@code CleanupScanService}: the only thing this Service asks of it is dropping
   * the unit rows of a campaign it is deleting.
   */
  @Autowired
  private CleanupScanUnitStorage                  scanUnitStorage;

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

  @Autowired
  private NameSpaceService                        nameSpaceService;

  /**
   * Off-request worker for the row collection that follows a campaign delete. Its
   * OWN executor and not the purge one: a delete must not queue behind a purge
   * that runs for hours, and two deletes are better serialized than concurrent —
   * one bulk DELETE at a time on a shared database.
   */
  private final ExecutorService                   deleteExecutor              = Executors.newSingleThreadExecutor();

  @PreDestroy
  public void shutdown() {
    // In-flight row collection is dropped rather than waited for; the startup
    // sweep is what finishes it (see sweepOrphanRows)
    deleteExecutor.shutdownNow();
  }

  @PostConstruct
  public void init() {
    // Asynchronously: JCR may not be ready yet at Spring context startup
    CompletableFuture.runAsync(this::recoverAfterRestart);
  }

  /**
   * Restart recovery: re-registers the freshness observation listener while a
   * campaign is PUBLISHED (bounded backoff: JCR may not be ready yet), then
   * resumes the interrupted workers through {@link #resumeStalledWorkers()}.
   * Package visible for tests.
   */
  void recoverAfterRestart() {
    try {
      registerFileNamespace();
      if (!campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED)).isEmpty()) {
        registerObservationListenerWithRetry();
      }
      sweepOrphanRows();
      resumeStalledWorkers();
    } catch (Exception e) {
      LOG.warn("Error recovering cleanup campaigns after restart", e);
    }
  }

  /**
   * Resumes the workers of the campaigns left DRY_RUN_RUNNING or EXECUTING
   * without a live worker — the dry-run scan resumes from its persisted path
   * checkpoint, the purge is naturally resumable (it iterates the remaining
   * CANDIDATE items). Called at startup recovery AND on every watchdog tick, so
   * a worker thread that died mid-run is re-launched without a JVM restart.
   * Safe to call unconditionally: the workers' running-campaign guard (the id
   * is removed in a finally block even on fatal error) makes this a no-op while
   * a campaign's worker is alive.
   */
  public void resumeStalledWorkers() {
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
  }

  /**
   * @return the platform default campaign parameters
   */
  public CleanupParams getDefaultParams() {
    return settingService.getDefaultParams();
  }

  /**
   * @return most recent campaigns, with their item aggregates — computed by a
   *         single grouped query for the whole list, never per campaign
   */
  public List<CleanupCampaign> getCampaigns() {
    List<CleanupCampaign> campaigns =
                                    campaignStorage.getCampaigns(PageRequest.of(0,
                                                                                MAX_CAMPAIGNS,
                                                                                Sort.by(Sort.Direction.DESC, "id")));
    Map<Long, CleanupCampaignAggregates> aggregates =
                                                    campaignStorage.getItemAggregates(campaigns.stream()
                                                                                               .map(CleanupCampaign::getId)
                                                                                               .toList());
    return campaigns.stream()
                    .map(campaign -> withAggregates(campaign,
                                                    aggregates.getOrDefault(campaign.getId(),
                                                                            new CleanupCampaignAggregates())))
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
   * @throws IllegalArgumentException "cleanup.nameMandatory",
   *           "cleanup.nameTooLong" (past {@link #MAX_NAME_LENGTH} characters),
   *           "cleanup.invalidPeriodMonths" (must be &gt;= 1),
   *           "cleanup.invalidMinFileSize" (must be &gt;= 0),
   *           "cleanup.invalidGraceDays" (must be &gt;= 0 — ZERO IS a valid
   *           grace period, elapsing at publication),
   *           "cleanup.invalidMaxVersionsPerFile" (must be &gt;= 1)
   */
  public CleanupCampaign createCampaign(String name, CleanupParams overrides) {
    String validatedName = validateName(name);
    CleanupParams params = settingService.getEffectiveParams(overrides);
    validateParams(params);
    // Validated BEFORE the lock — nothing below it may block on user input — and
    // the row is created INSIDE it: creating outside would leave a DRAFT campaign
    // behind on every refusal, which is a row an administrator can neither run nor
    // understand
    synchronized (publishLock) {
      // The SAME lock as publishCampaign and retryCampaign, so every decision to
      // start work is serialized against every other: two administrators clicking
      // at once used to both pass a check neither had transitioned yet (TOCTOU)
      checkNoWorkerRunning();
      CleanupCampaign campaign = new CleanupCampaign();
      campaign.setName(validatedName);
      campaign.setState(CleanupCampaignState.DRAFT);
      campaign.setParams(params);
      campaign.setStartedDate(System.currentTimeMillis());
      campaign = campaignStorage.createCampaign(campaign);
      try {
        scanService.startScan(campaign.getId());
      } catch (ObjectNotFoundException e) {
        throw new IllegalStateException("Freshly created cleanup campaign not found", e);
      }
      return withAggregates(campaignStorage.getCampaign(campaign.getId()));
    }
  }

  /**
   * Deletes a campaign and everything hanging off it: its item rows, its scan unit
   * rows and its archived CSV.
   * <p>
   * Allowed from {@link #DELETABLE_STATES} only — a draft or a discarded
   * simulation, where nothing was promised to a user and nothing was destroyed.
   * Anything else answers {@code cleanup.invalidState}: a running campaign has a
   * worker writing the very rows this would drop, a PUBLISHED or LOCKED one has
   * told its users a date and collected their decisions, and a COMPLETED one is
   * the record of an irreversible deletion (see {@link #DELETABLE_STATES}). Cancel
   * first, then delete, is the path for the non-terminal ones.
   * <p>
   * The JCR exemption mixins are NOT touched, and that is the important half: a
   * user's "keep" is a standing decision on their own file, deliberately durable
   * in JCR and deliberately outliving the campaign that collected it. Deleting a
   * campaign must never silently un-keep a file — the next campaign has to see
   * those decisions again.
   *
   * @param campaignId campaign identifier
   * @throws ObjectNotFoundException when the campaign doesn't exist
   */
  public void deleteCampaign(long campaignId) throws ObjectNotFoundException {
    CleanupCampaign campaign = getCampaign(campaignId);
    if (!DELETABLE_STATES.contains(campaign.getState())) {
      throw new IllegalArgumentException("cleanup.invalidState");
    }
    // The archive FIRST: a failure here must not leave a campaign row pointing at
    // a file that is gone, and a binary orphaned in the file store is invisible
    // once the row naming it is deleted. O(1), like the row delete below — and a
    // delete that died in between is simply re-clicked: deleting an already-gone
    // archive is logged and swallowed
    deleteArchive(campaign);
    // Then the row, which is what makes the campaign GONE: every item and unit
    // query is scoped by campaign id, so nothing can reach — or act on — what is
    // left behind, and the caller is answered a truthful 204 rather than being
    // held for the report
    campaignStorage.deleteCampaign(campaignId);
    LOG.info("Cleanup campaign {} ({}) deleted from state {}: its archive is gone and the campaign is unreachable, its"
        + " report and scan unit rows are being dropped in the background."
        + " The exemption mixins its users had set are NOT removed.", campaignId, campaign.getName(), campaign.getState());
    deleteExecutor.execute(() -> deleteCampaignRowsTransactional(campaignId));
  }

  /**
   * Drops the report and scan unit rows of an already-deleted campaign, off the
   * request thread.
   * <p>
   * WHY IT IS NOT ON THE REQUEST THREAD: a SIMULATED campaign is the one carrying
   * a FULL report — nothing has been purged yet, so its item table is at its
   * maximum, hundreds of thousands of rows on the corpus this feature exists for.
   * No REST call may depend on work whose duration grows with the corpus (a
   * reverse proxy will cut it), which is why creation and execution answer 202 and
   * hand their progress to CometD. Deleting is the third such operation, and the
   * only one that used to answer synchronously.
   * <p>
   * The DELETE still answers 204 and not 202, because unlike those two nothing
   * about the campaign is still pending: the campaign row is gone before the
   * answer, so the resource really is deleted. What runs here is the collection of
   * rows nothing can reach any more.
   * <p>
   * A JVM death in this window leaves those rows behind — visible to nobody, and
   * holding the very space this feature reclaims — so they are swept at startup
   * ({@link #sweepOrphanRows()}) rather than left to accumulate. That is also why
   * the campaign row is deleted BEFORE these and not after: an orphaned item row
   * is recoverable garbage, whereas a campaign whose report is half gone is a row
   * an administrator can still publish.
   *
   * @param campaignId campaign identifier
   */
  @ContainerTransactional
  public void deleteCampaignRowsTransactional(long campaignId) {
    try {
      campaignStorage.deleteItems(campaignId);
      scanUnitStorage.deleteUnits(campaignId);
    } catch (Exception e) {
      LOG.warn("Error dropping the report and scan unit rows of the deleted cleanup campaign {}:"
          + " the campaign itself is gone, and those rows are swept at the next restart", campaignId, e);
    }
  }

  /**
   * Drops the item and scan unit rows whose campaign row no longer exists.
   * <p>
   * Their only source is a JVM death in the middle of a delete (see
   * {@link #deleteCampaignRowsTransactional(long)}), which is rare — and exactly
   * why it must be swept rather than watched for: nothing else would ever notice
   * rows that no query can reach, in a feature whose entire purpose is reclaiming
   * space. Logged at WARN naming the campaigns, never silently.
   */
  @ContainerTransactional
  public void sweepOrphanRows() {
    Set<Long> orphans = new HashSet<>(campaignStorage.getOrphanItemCampaignIds());
    orphans.addAll(scanUnitStorage.getOrphanUnitCampaignIds());
    if (orphans.isEmpty()) {
      return;
    }
    LOG.warn("Sweeping the rows of {} deleted cleanup campaign(s) whose deletion did not finish: {}."
        + " They were unreachable, a delete having been interrupted after the campaign row was dropped.",
             orphans.size(),
             orphans);
    for (Long campaignId : orphans) {
      try {
        campaignStorage.deleteItems(campaignId);
        scanUnitStorage.deleteUnits(campaignId);
      } catch (Exception e) {
        LOG.warn("Error sweeping the leftover rows of the deleted cleanup campaign {}", campaignId, e);
      }
    }
  }

  /**
   * Registers {@link #FILE_NAMESPACE} with the file store, idempotently.
   * <p>
   * Without it every {@code writeFile} under that namespace fails on a
   * NullPointerException raised inside {@code DataStorage#create}, which looks the
   * namespace up by name and never guards the miss. The failure was invisible in
   * the worst way: our own caller logs it as a WARN and keeps the item detail, so
   * the retention tick simply retried it every five minutes, and no report was
   * ever archived while the console showed nothing wrong.
   * <p>
   * Called from the restart recovery rather than from a {@code @PostConstruct}
   * for the same reason the observation listener is: the file store's own tables
   * may not be ready at bean-creation time, and this must not be able to fail a
   * WAR's startup. A failure here is logged and swallowed — the archive is a
   * retention concern, and the next restart tries again.
   */
  private void registerFileNamespace() {
    try {
      nameSpaceService.createNameSpace(FILE_NAMESPACE, "Documents cleanup campaign CSV reports");
    } catch (Exception e) {
      LOG.warn("Error registering the {} file namespace: archiving a cleanup campaign report will fail until it exists",
               FILE_NAMESPACE,
               e);
    }
  }

  /**
   * Drops the archived CSV of a campaign being deleted, if it has one. A failure is
   * logged and swallowed: an orphaned binary in the file store is a wasted block,
   * while a half-deleted campaign is a row nobody can act on.
   */
  private void deleteArchive(CleanupCampaign campaign) {
    if (campaign.getArchiveFileId() == null) {
      return;
    }
    try {
      fileService.deleteFile(campaign.getArchiveFileId());
    } catch (Exception e) {
      LOG.warn("Error deleting the archived report {} of cleanup campaign {}: the campaign is deleted anyway",
               campaign.getArchiveFileId(),
               campaign.getId(),
               e);
    }
  }

  /**
   * Refuses to start work while another campaign owns a worker.
   *
   * @throws IllegalArgumentException {@code cleanup.workerAlreadyRunning} when a
   *           scan or a purge is already in flight
   */
  private void checkNoWorkerRunning() {
    if (!campaignStorage.getCampaignsByStates(WORKER_STATES).isEmpty()) {
      throw new IllegalArgumentException("cleanup.workerAlreadyRunning");
    }
  }

  /**
   * PARTIAL update of a campaign's editable attributes: its name, its grace
   * period, or both. A null argument means 'leave that attribute unchanged', so
   * the two fields are strictly independent — and their guards are per FIELD,
   * not per request.
   * <p>
   * The NAME is editable in ANY state, terminal ones included: it is pure
   * METADATA, nothing keys off it and no lifecycle transition is involved, so
   * correcting the label of an already-completed report is a legitimate need.
   * Names are not unique (creation doesn't check either), so no uniqueness
   * constraint is enforced. It goes through the very SAME validation as the
   * creation path ({@link #validateName(String)}): the two cannot diverge, and
   * neither can let a name longer than the NAME column reach the database.
   * <p>
   * The GRACE PERIOD is state-guarded ({@link #GRACE_EDITABLE_STATES}) and
   * validated by the very same bound check as the creation path
   * ({@link #validateGraceDays(Integer)}), so the two cannot diverge either —
   * ZERO stays valid on both. Editing it can never invalidate the dry-run the
   * administrator is reviewing: it is NOT a candidacy criterion, the scan
   * selecting on the period, the minimum file size and the excluded paths alone.
   * <p>
   * Once the campaign is PUBLISHED the grace period may only be EXTENDED, never
   * shortened (architect decision W22) — see {@link #applyGraceDays}. Before
   * publication it is free in both directions, nothing having been promised
   * yet.
   * <p>
   * On a PUBLISHED campaign — and ONLY there — the lock date is recomputed from
   * the PUBLICATION date, never from now: anchoring on now would slide the
   * deadline forward on every save, so saving the same value twice would push it
   * out twice. Everything downstream reads the lock date (the end users'
   * remaining time included, through their summary's deadline), so that one
   * field propagates the new deadline with NO second code path.
   * <p>
   * A recomputed deadline landing in the PAST is allowed and is not an error: it
   * closes the review window immediately ({@code cleanup.reviewClosed}) and the
   * grace-deadline cron locks the campaign at its next tick — exactly what
   * already happens with a zero grace period, tolerated for up to the cron
   * period (see {@link #checkReviewWindowOpen(CleanupCampaign)}). This method
   * deliberately performs NO transition of its own: the single PUBLISHED to
   * LOCKED authority stays {@link #lockExpiredPublishedCampaign()} and the
   * manual execution trigger.
   * <p>
   * Persisted as a TARGETED write of the two or three columns this operation
   * owns ({@link CleanupCampaignStorage#updateEditableAttributes}), never as a
   * whole-row save of the snapshot read above: the name is editable in EVERY
   * state, so this write races the two schedule-driven writers of the same row
   * — the workers' progress updates and the grace-deadline cron — and a
   * read-modify-write would silently undo theirs.
   *
   * @param campaignId campaign identifier
   * @param name new campaign name, null to leave it unchanged — trimmed before
   *          being persisted
   * @param graceDays new grace period in days, null to leave it unchanged — 0 is
   *          a MEANINGFUL value, not an absent one
   * @return the updated campaign, with its item aggregates
   * @throws ObjectNotFoundException "cleanup.campaignNotFound" when the campaign
   *           doesn't exist
   * @throws IllegalArgumentException "cleanup.nothingToUpdate" when both
   *           arguments are null, "cleanup.nameMandatory" when the name is
   *           blank, "cleanup.nameTooLong" past {@link #MAX_NAME_LENGTH}
   *           characters, "cleanup.invalidState" when the grace period is edited
   *           outside {@link #GRACE_EDITABLE_STATES},
   *           "cleanup.invalidGraceDays" when it is negative,
   *           "cleanup.graceDaysCannotBeReduced" when it is LOWERED on a
   *           PUBLISHED campaign
   */
  public CleanupCampaign updateCampaign(long campaignId, String name, Integer graceDays) throws ObjectNotFoundException {
    // Existence first, validation second: the REST contract answers 404 before
    // 400, exactly like every sibling method here
    CleanupCampaign campaign = getCampaign(campaignId);
    if (name == null && graceDays == null) {
      // Never silently no-op: the console must be able to say WHY nothing
      // happened
      throw new IllegalArgumentException("cleanup.nothingToUpdate");
    }
    String validatedName = name == null ? null : validateName(name);
    if (validatedName != null) {
      campaign.setName(validatedName);
    }
    Long rederivedLockDate = graceDays == null ? null : applyGraceDays(campaign, graceDays);
    // TARGETED write, never a whole-row save of the snapshot read above: the
    // progress updates of the workers and the grace-deadline cron write this
    // very row on a schedule (see the Storage method's javadoc)
    campaignStorage.updateEditableAttributes(campaignId, validatedName, graceDays, rederivedLockDate);
    return withAggregates(campaign);
  }

  /**
   * Writes a new grace period into a campaign's snapshotted parameters, and
   * REDERIVES the lock date from it while the campaign is PUBLISHED — the very
   * same {@code publishedDate + graceDays} formula
   * {@link #publishCampaign(long)} establishes, so the invariant holds whether
   * the value was set at publication or edited afterwards. Before publication
   * there is no deadline to recompute yet: the lock date is left ALONE, and
   * publication will derive it from the edited value on its own.
   * <p>
   * A PUBLISHED grace period is ONE-WAY — it may only be EXTENDED (architect
   * decision W22). Publication PROMISES a deadline to the owners of the
   * candidate files, and lowering it (14 to 7 on day 8) closes their review on
   * the spot: every keep and un-keep answers {@code cleanup.reviewClosed}, the
   * cron LOCKS the campaign at its next tick, and files whose owners were
   * promised six more days are hard-deleted — no trash transit, so the only
   * recovery is a database/JCR snapshot. The announcement being manual, nobody
   * would even tell them the deadline moved. Extending is always allowed, and
   * re-saving the SAME value is NOT a reduction (it stays idempotent, like the
   * deadline rederivation itself). Before publication the value is free in both
   * directions: nothing has been promised yet.
   *
   * @return the rederived grace deadline to persist, or NULL when there is none
   *         to rederive — which the targeted write reads as 'do not touch the
   *         LOCK_DATE column', so a pre-publication edit cannot zero a deadline
   *         it has no business setting
   * @throws IllegalArgumentException "cleanup.invalidState" outside
   *           {@link #GRACE_EDITABLE_STATES}, "cleanup.invalidGraceDays" when
   *           the value is out of bounds, "cleanup.graceDaysCannotBeReduced"
   *           when it is lowered on a PUBLISHED campaign
   */
  private Long applyGraceDays(CleanupCampaign campaign, Integer graceDays) {
    if (!GRACE_EDITABLE_STATES.contains(campaign.getState())) {
      throw new IllegalArgumentException("cleanup.invalidState");
    }
    validateGraceDays(graceDays);
    checkGraceDaysNotReduced(campaign, graceDays);
    CleanupParams params = campaign.getParams();
    if (params == null) {
      // Defensive only: the Storage always maps a params object, whatever the
      // state. An in-memory campaign that never went through the Storage could
      // still carry none, and losing the edit silently would be worse than
      // creating the holder here
      params = new CleanupParams();
      campaign.setParams(params);
    }
    params.setGraceDays(graceDays);
    if (campaign.getState() != CleanupCampaignState.PUBLISHED) {
      return null;
    }
    long lockDate = campaign.getPublishedDate() + TimeUnit.DAYS.toMillis(graceDays);
    campaign.setLockDate(lockDate);
    return lockDate;
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
   * Triggers the batched purge of a LOCKED campaign. A PUBLISHED campaign whose
   * grace deadline elapsed is first locked through the regular lifecycle
   * transition (the same code path as {@link #lockExpiredPublishedCampaign()},
   * including the observation listener unregistration), then executed; before
   * the deadline the execution is rejected.
   *
   * @param campaignId campaign identifier
   * @return the campaign, now EXECUTING
   * @throws ObjectNotFoundException when the campaign doesn't exist
   * @throws IllegalArgumentException "cleanup.graceNotElapsed" when the
   *           campaign is PUBLISHED and its grace deadline hasn't elapsed yet
   */
  public CleanupCampaign executeCampaign(long campaignId) throws ObjectNotFoundException {
    CleanupCampaign campaign = getCampaign(campaignId);
    // No purge while a scan is walking: the purge would delete nodes the scan is
    // still reading, and the simulation it produces would describe a tree that no
    // longer exists. Serialized on the same lock as every other start decision
    synchronized (publishLock) {
      checkNoWorkerRunning();
    }
    if (campaign.getState() == CleanupCampaignState.PUBLISHED) {
      if (!isGraceDeadlineElapsed(campaign, System.currentTimeMillis())) {
        throw new IllegalArgumentException("cleanup.graceNotElapsed");
      }
      lockCampaign(campaign);
    }
    return withAggregates(executionService.startExecution(campaignId));
  }

  /**
   * Re-attempts the RETRYABLE failed items of a COMPLETED campaign: no new scan,
   * no new grace period, no new campaign. The requeued items go back to
   * CANDIDATE and the campaign re-enters EXECUTING, so the very same worker
   * processes them — and the worker needs NO change for that, its per-item
   * revalidation against JCR immediately before deleting being what makes a
   * requeued item safe (an item since exempted, modified or deleted is spared on
   * its own).
   * <p>
   * Lives HERE and not in {@link CleanupExecutionService}, which owns the worker,
   * because everything specific to a retry is business this Service already owns:
   * the platform-wide single-active-campaign invariant and the very
   * {@code publishLock} that makes its check-then-act atomic, plus the retryable
   * allowlist. The execution part is then delegated to
   * {@link CleanupExecutionService#startExecution(long)} verbatim — the requeue
   * having just made the CANDIDATE count equal to the requeued count, its
   * denominator reset, its lifecycle transition and its worker launch are exactly
   * what a retry needs, and duplicating them here would be a second code path to
   * keep in sync.
   * <p>
   * A REFUSAL never starts a run: not COMPLETED, another campaign active, or
   * nothing at all to retry all leave the campaign untouched.
   *
   * @param campaignId campaign identifier
   * @return the campaign, now EXECUTING again, with its item aggregates
   * @throws ObjectNotFoundException "cleanup.campaignNotFound" when the campaign
   *           doesn't exist
   * @throws IllegalArgumentException "cleanup.invalidState" when the campaign
   *           isn't COMPLETED, "cleanup.campaignAlreadyActive" when another
   *           campaign is PUBLISHED/LOCKED/EXECUTING,
   *           "cleanup.noRetryableFailures" when no item qualifies
   */
  public CleanupCampaign retryCampaign(long campaignId) throws ObjectNotFoundException {
    // The SAME lock as publishCampaign, for the same reason: without it two
    // concurrent retries (or a retry racing a publish) could both pass the
    // single-active check before either transitions (TOCTOU)
    synchronized (publishLock) {
      CleanupCampaign campaign = getCampaign(campaignId);
      if (campaign.getState() != CleanupCampaignState.COMPLETED) {
        throw new IllegalArgumentException("cleanup.invalidState");
      }
      if (!campaignStorage.getCampaignsByStates(ACTIVE_STATES).isEmpty()) {
        throw new IllegalArgumentException("cleanup.campaignAlreadyActive");
      }
      // ACTIVE_STATES does not hold DRY_RUN_RUNNING, so it alone would let a purge
      // start next to a running scan
      checkNoWorkerRunning();
      long requeuedCount = requeueRetryableFailures(campaignId);
      if (requeuedCount == 0) {
        // Never silently start a no-op run: the console must be able to say WHY
        throw new IllegalArgumentException("cleanup.noRetryableFailures");
      }
      return withAggregates(executionService.startExecution(campaignId));
    }
  }

  /**
   * Grouped failures of a campaign: one entry per distinct failure message code,
   * its SKIPPED item count, and whether a retry would re-attempt it — the flag
   * read from {@link #RETRYABLE_FAILURE_REASONS}, so the console never has to
   * hold its own copy of that rule.
   *
   * @param campaignId campaign identifier
   * @return the groups, EMPTY when the campaign has no failed item — or when the
   *         retention job already archived and purged its item rows, the grouped
   *         counts being computed over those rows and NOT snapshotted in the
   *         campaign summary
   * @throws ObjectNotFoundException when the campaign doesn't exist
   */
  public List<CleanupFailureGroup> getCampaignFailures(long campaignId) throws ObjectNotFoundException {
    getCampaign(campaignId);
    List<CleanupFailureGroup> failureGroups = campaignStorage.countFailuresByReason(campaignId);
    failureGroups.forEach(group -> group.setRetryable(RETRYABLE_FAILURE_REASONS.contains(group.getReason())));
    return failureGroups;
  }

  /**
   * Grouped failures of a campaign's dry-run SCAN: one entry per distinct failure
   * message code, with the number of SUBTREES that carry it. The unit-level twin
   * of {@link #getCampaignFailures(long)}, answering the same shape so the
   * console renders both through one block.
   * <p>
   * A dry run used to report SIMULATED at 100% over a report silently missing
   * whole subtrees — the only trace being a log line no administrator can read.
   * This is that trace, made readable: whoever is about to publish the report
   * sees how much of the tree it does NOT cover.
   * <p>
   * Delegates to {@link CleanupScanService}, which owns the scan and its unit
   * rows, after resolving the campaign here so an unknown id answers 404 exactly
   * like every other campaign endpoint.
   *
   * @param campaignId campaign identifier
   * @return the groups, EMPTY when the campaign's scan covered the whole tree —
   *         which is the case of every campaign scanned before this bound
   *         existed
   * @throws ObjectNotFoundException when the campaign doesn't exist
   */
  public List<CleanupFailureGroup> getCampaignScanFailures(long campaignId) throws ObjectNotFoundException {
    return scanService.getScanFailures(getCampaign(campaignId));
  }

  /**
   * Per-unit breakdown of a campaign's dry run, resolving the campaign here so an
   * unknown id answers 404 like every other campaign endpoint.
   *
   * @param campaignId campaign identifier
   * @return the state counts, the deepest attempt spent, and the units in flight
   * @throws ObjectNotFoundException when the campaign doesn't exist
   */
  public CleanupScanUnitProgress getCampaignScanUnitProgress(long campaignId) throws ObjectNotFoundException {
    return scanService.getScanUnitProgress(getCampaign(campaignId));
  }

  /**
   * Requeues the retryable failures of a campaign, KEYSET-paged: a campaign can
   * hold hundreds of thousands of failed items, none of which may be loaded into
   * a single List. Keyset and not offset because the requeue mutates the very
   * state the query filters on — an offset page would walk past rows as the
   * result set shrinks underneath it.
   * <p>
   * Per item: back to CANDIDATE, one more attempt spent, and BOTH failure fields
   * cleared — a stale reason on a requeued item would be a lie the console would
   * happily display. {@code reclaimedBytes} is deliberately LEFT ALONE: a
   * partially reclaimed delete already reported real bytes, and the campaign
   * total must not lose them. So is {@code purgedAt}.
   *
   * @return the number of items requeued
   */
  private long requeueRetryableFailures(long campaignId) {
    long requeuedCount = 0;
    long lastId = 0;
    List<CleanupCampaignItem> failedItems = campaignStorage.getRetryableFailures(campaignId,
                                                                                RETRYABLE_FAILURE_REASONS,
                                                                                MAX_RETRY_ATTEMPTS,
                                                                                lastId,
                                                                                RETRY_PAGE_SIZE);
    while (!failedItems.isEmpty()) {
      for (CleanupCampaignItem item : failedItems) {
        item.setState(CleanupItemState.CANDIDATE);
        item.setAttemptCount(item.getAttemptCount() + 1);
        item.setFailureReason(null);
        item.setFailureDetail(null);
        campaignStorage.saveItem(item);
        lastId = item.getId();
        requeuedCount++;
      }
      failedItems = campaignStorage.getRetryableFailures(campaignId,
                                                        RETRYABLE_FAILURE_REASONS,
                                                        MAX_RETRY_ATTEMPTS,
                                                        lastId,
                                                        RETRY_PAGE_SIZE);
    }
    return requeuedCount;
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
      if (isGraceDeadlineElapsed(campaign, now)) {
        lockCampaign(campaign);
      }
    }
  }

  /**
   * User decision to keep a candidate file: checks ownership (own file, or
   * manager of the owning space), adds the exemption mixin and marks the item
   * EXEMPTED.
   * <p>
   * The ownership check runs FIRST, before any campaign-state or item-state
   * check: probing item ids must never tell a non-owner whether an item exists
   * nor which state its campaign is in.
   *
   * @param itemId campaign item identifier
   * @param username user requesting to keep the file
   * @throws ObjectNotFoundException when the item or its node doesn't exist
   * @throws IllegalAccessException when the user doesn't own the file
   * @throws IllegalArgumentException "cleanup.campaignNotPublished" when the
   *           campaign isn't PUBLISHED, "cleanup.reviewClosed" once the grace
   *           deadline elapsed, "cleanup.itemNotCandidate" when the item isn't
   *           decidable
   * @throws IllegalStateException on a (possibly transient) JCR write failure,
   *           the item state left untouched so the keep can be retried
   */
  public void keepItem(long itemId, String username) throws ObjectNotFoundException, IllegalAccessException {
    CleanupCampaignItem item = campaignStorage.getItem(itemId);
    if (item == null) {
      throw new ObjectNotFoundException("cleanup.itemNotFound");
    }
    checkOwnership(item.getOwnerIdentityId(), username);
    CleanupCampaign campaign = campaignStorage.getCampaign(item.getCampaignId());
    if (campaign == null || campaign.getState() != CleanupCampaignState.PUBLISHED) {
      throw new IllegalArgumentException("cleanup.campaignNotPublished");
    }
    checkReviewWindowOpen(campaign);
    if (item.getState() != CleanupItemState.CANDIDATE && item.getState() != CleanupItemState.EXEMPTED) {
      throw new IllegalArgumentException("cleanup.itemNotCandidate");
    }
    CleanupExemptionResult result = cleanupJcrStorage.addExemptionMixin(item.getNodeUuid(), username);
    if (result == CleanupExemptionResult.NOT_FOUND) {
      item.setState(CleanupItemState.GONE);
      campaignStorage.saveItem(item);
      throw new ObjectNotFoundException("cleanup.nodeNotFound");
    } else if (result == CleanupExemptionResult.FAILED) {
      // No state change: the mixin write may be retried, never discard the
      // user's keep decision because of a transient JCR failure
      throw new IllegalStateException("cleanup.keepFailed");
    }
    item.setState(CleanupItemState.EXEMPTED);
    item.setDecidedBy(username);
    item.setDecidedAt(System.currentTimeMillis());
    campaignStorage.saveItem(item);
  }

  /**
   * Bulk variant of {@link #keepItem(long, String)}, continuing past individual
   * failures and REPORTING them: a caller that answered a blanket success would
   * tell the user their files are kept while none of them is.
   *
   * @param itemIds campaign item identifiers
   * @param username user requesting to keep the files
   * @return per-item outcomes: how many succeeded, and which ones failed with
   *         which reason
   */
  public CleanupBulkResult keepItems(List<Long> itemIds, String username) {
    return applyToItems(itemIds, itemId -> keepItem(itemId, username));
  }

  /**
   * User decision to un-keep a previously kept file (undo of
   * {@link #keepItem(long, String)}): checks ownership (own file, or manager of
   * the owning space), removes the exemption mixin, then revalidates the node
   * through the shared revalidation mapping — still qualifying goes back to
   * CANDIDATE, modified meanwhile to SPARED_BY_MODIFICATION, disappeared to
   * GONE. Only allowed while the review window is open (campaign PUBLISHED and
   * grace deadline not elapsed yet).
   * <p>
   * The ownership check runs FIRST, before any campaign-state or item-state
   * check: probing item ids must never tell a non-owner whether an item exists
   * nor which state its campaign is in.
   *
   * @param itemId campaign item identifier
   * @param username user requesting to un-keep the file
   * @throws ObjectNotFoundException when the item or its node doesn't exist
   * @throws IllegalAccessException when the user doesn't own the file
   * @throws IllegalArgumentException "cleanup.reviewClosed" when the campaign
   *           isn't PUBLISHED anymore or its grace deadline elapsed,
   *           "cleanup.itemNotKept" when the item isn't EXEMPTED
   * @throws IllegalStateException on a (possibly transient) JCR write failure,
   *           the item state left untouched so the un-keep can be retried
   */
  public void unkeepItem(long itemId, String username) throws ObjectNotFoundException, IllegalAccessException {
    CleanupCampaignItem item = campaignStorage.getItem(itemId);
    if (item == null) {
      throw new ObjectNotFoundException("cleanup.itemNotFound");
    }
    checkOwnership(item.getOwnerIdentityId(), username);
    CleanupCampaign campaign = campaignStorage.getCampaign(item.getCampaignId());
    if (campaign == null || campaign.getState() != CleanupCampaignState.PUBLISHED) {
      throw new IllegalArgumentException("cleanup.reviewClosed");
    }
    checkReviewWindowOpen(campaign);
    if (item.getState() != CleanupItemState.EXEMPTED) {
      throw new IllegalArgumentException("cleanup.itemNotKept");
    }
    CleanupExemptionResult result = cleanupJcrStorage.removeExemptionMixin(item.getNodeUuid());
    if (result == CleanupExemptionResult.NOT_FOUND) {
      item.setState(CleanupItemState.GONE);
      campaignStorage.saveItem(item);
      throw new ObjectNotFoundException("cleanup.nodeNotFound");
    } else if (result == CleanupExemptionResult.FAILED) {
      // No state change: the mixin may still be present, keep the item
      // EXEMPTED so the un-keep can be retried
      throw new IllegalStateException("cleanup.unkeepFailed");
    }
    CleanupRevalidation revalidation = cleanupJcrStorage.revalidate(item.getNodeUuid(), campaign.getParams());
    if (revalidation.isUnknown() || CleanupRevalidationUtil.applyRevalidation(item, revalidation)) {
      // Still qualifying — or outcome unknown (transient JCR read failure):
      // the mixin IS removed, so the item goes back under cleanup; the
      // execution-time revalidation remains the correctness guarantee
      item.setState(CleanupItemState.CANDIDATE);
    }
    item.setDecidedBy(username);
    item.setDecidedAt(System.currentTimeMillis());
    campaignStorage.saveItem(item);
  }

  /**
   * Bulk variant of {@link #unkeepItem(long, String)}, continuing past
   * individual failures and REPORTING them (see
   * {@link #keepItems(List, String)}).
   *
   * @param itemIds campaign item identifiers
   * @param username user requesting to un-keep the files
   * @return per-item outcomes: how many succeeded, and which ones failed with
   *         which reason
   */
  public CleanupBulkResult unkeepItems(List<Long> itemIds, String username) {
    return applyToItems(itemIds, itemId -> unkeepItem(itemId, username));
  }

  /**
   * Shared bulk loop of {@link #keepItems(List, String)} and
   * {@link #unkeepItems(List, String)}: never aborts on one item, collects each
   * failure's message code. The per-item failures are expected flow (not found,
   * not owned, review closed), so they are logged at DEBUG only — the caller
   * gets them in the returned result.
   * <p>
   * Every reported reason is a MESSAGE CODE the UI can localize: an ACL refusal
   * is mapped to {@link #NOT_OWNER_FAILURE_CODE} rather than forwarded as the
   * {@link IllegalAccessException} message, which is a raw English sentence
   * naming the user and the owning space — internal detail that must not reach
   * the client.
   */
  private CleanupBulkResult applyToItems(List<Long> itemIds, CleanupItemDecision decision) {
    if (itemIds == null || itemIds.isEmpty()) {
      throw new IllegalArgumentException("cleanup.itemIdsMandatory");
    }
    CleanupBulkResult result = new CleanupBulkResult();
    for (Long itemId : itemIds) {
      try {
        decision.apply(itemId);
        result.setSucceeded(result.getSucceeded() + 1);
      } catch (IllegalAccessException e) {
        LOG.debug("User isn't allowed to decide cleanup campaign item {}, continuing with the remaining items", itemId, e);
        result.addFailure(itemId, NOT_OWNER_FAILURE_CODE);
      } catch (Exception e) {
        LOG.debug("Error deciding cleanup campaign item {}, continuing with the remaining items", itemId, e);
        result.addFailure(itemId, StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
      }
    }
    return result;
  }

  @FunctionalInterface
  private interface CleanupItemDecision {

    void apply(long itemId) throws ObjectNotFoundException, IllegalAccessException;

  }

  /**
   * Freshness refresh (UX only, not the correctness guarantee), called by the
   * JCR observation glue while a campaign is PUBLISHED. The event path is
   * matched in BOTH directions so every JCR event shape refreshes the right
   * items: a PROPERTY_CHANGED path (e.g.
   * {@code /Users/.../file.pdf/jcr:content/jcr:data}) matches the file item
   * ABOVE it (ancestor-chain exact match), a folder removal/move matches the
   * candidate items BELOW it (escaped prefix LIKE), and an exact node event
   * matches the item itself.
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
    List<CleanupCampaignItem> items = campaignStorage.getItemsTouchedByPath(campaign.getId(), itemPath);
    for (CleanupCampaignItem item : items) {
      if (item.getState() != CleanupItemState.CANDIDATE) {
        continue;
      }
      try {
        CleanupRevalidation revalidation = cleanupJcrStorage.revalidate(item.getNodeUuid(), campaign.getParams());
        if (revalidation.isUnknown()) {
          // Transient JCR read failure: leave the item untouched, a later
          // event or the execution-time revalidation will settle it
          continue;
        }
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
   * @param search optional case-insensitive search on the item path (covers the
   *          file name and its folders alike)
   * @param pageable page, size and sort
   * @return page of items
   * @throws ObjectNotFoundException when the campaign doesn't exist
   */
  public Page<CleanupCampaignItem> getCampaignItems(long campaignId, // NOSONAR
                                                    Long ownerIdentityId,
                                                    CleanupItemState state,
                                                    CleanupAction action,
                                                    Long minSize,
                                                    String search,
                                                    Pageable pageable) throws ObjectNotFoundException {
    getCampaign(campaignId);
    return campaignStorage.getItems(campaignId, ownerIdentityId, state, action, minSize, search, pageable);
  }

  /**
   * Delta between two campaigns' candidate sets, matched by node uuid. The three
   * buckets are computed set-based by the database (three aggregate queries
   * hitting the (CAMPAIGN_ID, NODE_UUID) unique index): neither campaign's
   * candidate set is ever loaded in memory, so the endpoint stays bounded
   * whatever the campaign size.
   *
   * @param baseCampaignId base campaign identifier
   * @param otherCampaignId compared campaign identifier
   * @return comparison counters and bytes
   * @throws ObjectNotFoundException when either campaign doesn't exist
   */
  public CleanupComparison compareCampaigns(long baseCampaignId, long otherCampaignId) throws ObjectNotFoundException {
    getCampaign(baseCampaignId);
    getCampaign(otherCampaignId);
    CleanupComparisonBucket persisting = campaignStorage.getPersistingItems(baseCampaignId, otherCampaignId);
    CleanupComparisonBucket newItems = campaignStorage.getNewItems(baseCampaignId, otherCampaignId);
    CleanupComparisonBucket goneItems = campaignStorage.getGoneItems(baseCampaignId, otherCampaignId);
    CleanupComparison comparison = new CleanupComparison();
    comparison.setBaseCampaignId(baseCampaignId);
    comparison.setOtherCampaignId(otherCampaignId);
    comparison.setPersistingCount(persisting.getCount());
    comparison.setPersistingBytes(persisting.getReclaimableBytes());
    comparison.setNewCount(newItems.getCount());
    comparison.setNewBytes(newItems.getReclaimableBytes());
    comparison.setGoneCount(goneItems.getCount());
    comparison.setGoneBytes(goneItems.getReclaimableBytes());
    return comparison;
  }

  /**
   * Items of the currently relevant campaign owned by the user (own files and
   * files of spaces they manage). The paging AND the ordering are the caller's
   * (the REST layer validates the requested sort field against its allowlist and
   * appends the stable tiebreaker), so this method never imposes an order of its
   * own.
   *
   * @param username user
   * @param search optional case-insensitive search on the item path (covers the
   *          file name and its folders alike)
   * @param pageable page, size and sort
   * @return page of items
   * @throws ObjectNotFoundException when no relevant campaign exists
   */
  public Page<CleanupCampaignItem> getMyItems(String username,
                                              String search,
                                              Pageable pageable) throws ObjectNotFoundException {
    CleanupCampaign campaign = getUserVisibleCampaign();
    List<Long> ownerIdentityIds = getUserOwnedIdentityIds(username);
    return campaignStorage.getItemsByOwners(campaign.getId(), ownerIdentityIds, search, pageable);
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
   * Availability check of a campaign's CSV report, to run BEFORE a single byte
   * is streamed: once the response body started flowing the HTTP status is
   * already committed, so the not-found outcome has to be settled here. Only
   * metadata is read (never the archive content).
   *
   * @param campaignId campaign identifier
   * @throws ObjectNotFoundException when the campaign or its report doesn't
   *           exist anymore
   */
  public void checkArchiveAvailable(long campaignId) throws ObjectNotFoundException {
    CleanupCampaign campaign = getCampaign(campaignId);
    if (campaignStorage.hasItems(campaignId)) {
      // Item detail retained: the CSV is generated live
      return;
    }
    Long archiveFileId = campaign.getArchiveFileId();
    if (archiveFileId == null || fileService.getFileInfo(archiveFileId) == null) {
      throw new ObjectNotFoundException("cleanup.archiveNotFound");
    }
  }

  /**
   * Streams the CSV report of a campaign: generated page by page while item
   * detail is retained (each page is flushed, so the first rows reach the client
   * immediately and the whole report is never materialized in memory), copied
   * from the {@link FileService} archive afterwards. Availability must have been
   * settled by {@link #checkArchiveAvailable(long)} first.
   *
   * @param campaignId campaign identifier
   * @param outputStream stream to write the CSV to, left open for the container
   *          to close
   * @throws IOException when writing to the client stream fails
   */
  public void writeArchiveCsv(long campaignId, OutputStream outputStream) throws IOException {
    CleanupCampaign campaign = campaignStorage.getCampaign(campaignId);
    if (campaign == null) {
      // Deleted between the availability check and the streaming
      return;
    }
    if (campaignStorage.hasItems(campaignId)) {
      writeCsv(campaignId, outputStream);
    } else if (campaign.getArchiveFileId() != null) {
      writeArchiveFile(campaign, outputStream);
    }
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

  /**
   * Archives a campaign's report and drops its item rows.
   * <p>
   * The CSV goes through a TEMPORARY FILE rather than a {@code ByteArrayOutputStream}:
   * the report being archived is a whole campaign's item detail — hundreds of
   * thousands of rows on the target corpus — and building it in memory held it
   * SEVERAL times over (the growing buffer, then the array copied out of it, then
   * the file store's own copy). {@link #writeCsv} streams page by page into the
   * scratch file, so nothing this class holds grows with the report.
   * <p>
   * NOT a bounded path yet, and the remainder is not ours to fix here:
   * {@code FileItem}'s constructor runs {@code IOUtils.toByteArray} over whatever
   * stream it is handed, so the file store still materializes the whole CSV once,
   * however it is fed. Removing that needs a {@code FileService} able to take a
   * file or a stream without buffering it (portal-side). What this method can do
   * is not add its own copies on top, which is what it now does — from roughly
   * three resident copies of the report down to the one the file store imposes.
   * <p>
   * The temp file is removed on EVERY path: a retention tick that keeps its
   * scratch files would grow a second copy of every archived report on the disk.
   */
  private void archiveAndPurgeItems(CleanupCampaign campaign) {
    Path csvFile = null;
    try {
      csvFile = Files.createTempFile("cleanup-campaign-" + campaign.getId() + "-", ".csv");
      try (OutputStream csvOutput = new BufferedOutputStream(Files.newOutputStream(csvFile))) {
        writeCsv(campaign.getId(), csvOutput);
      }
      // Opened around writeFile and closed after it: the file store READS this
      // stream, so it must still be open when it does
      try (InputStream csvInput = new BufferedInputStream(Files.newInputStream(csvFile))) {
        FileItem fileItem = new FileItem(null,
                                         "cleanup-campaign-" + campaign.getId() + ".csv",
                                         "text/csv",
                                         FILE_NAMESPACE,
                                         Files.size(csvFile),
                                         new Date(),
                                         "system",
                                         false,
                                         csvInput);
        fileItem = fileService.writeFile(fileItem);
        campaign.setArchiveFileId(fileItem.getFileInfo().getId());
      }
      campaignStorage.saveCampaign(campaign);
      campaignStorage.deleteItems(campaign.getId());
    } catch (Exception e) {
      LOG.warn("Error archiving cleanup campaign {} report, keeping its item detail", campaign.getId(), e);
    } finally {
      deleteTempFile(csvFile);
    }
  }

  /**
   * Drops the scratch CSV of an archive, whether it was archived or not. Logged
   * and swallowed: a leftover temp file is a wasted block, while raising here
   * would hide the archiving failure that led to it.
   */
  private void deleteTempFile(Path csvFile) {
    if (csvFile == null) {
      return;
    }
    try {
      Files.deleteIfExists(csvFile);
    } catch (IOException e) {
      LOG.warn("Error deleting the temporary CSV {} of an archived cleanup campaign report", csvFile, e);
    }
  }

  /**
   * Writes the live CSV report page by page, flushing after each page: the
   * archive download starts streaming immediately instead of waiting for the
   * whole report to be built in memory.
   */
  private void writeCsv(long campaignId, OutputStream outputStream) throws IOException {
    Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    writer.write(CSV_HEADER);
    // Owner display names resolved ONCE per distinct owner for the whole export:
    // a report can hold millions of rows for a few thousand owners at most, so
    // one IdentityManager lookup per row would dominate the export cost. Local
    // to this export (never a shared cache): a name is read as of the download.
    Map<Long, String> ownerNames = new HashMap<>();
    int pageIndex = 0;
    Page<CleanupCampaignItem> page;
    do {
      page = campaignStorage.getItemsPage(campaignId, PageRequest.of(pageIndex++, CSV_PAGE_SIZE, Sort.by("id")));
      for (CleanupCampaignItem item : page.getContent()) {
        writer.write(toCsvRow(item, ownerNames));
      }
      // Never left buffered: the client gets each page as it is read
      writer.flush();
    } while (page.hasNext());
  }

  /**
   * Streams the stored archive of a campaign whose item rows were purged by the
   * retention job. A read failure is logged (the availability check already
   * passed, so the status is committed) rather than propagated as an error page
   * inside the CSV body.
   */
  private void writeArchiveFile(CleanupCampaign campaign, OutputStream outputStream) throws IOException {
    FileItem fileItem;
    try {
      fileItem = fileService.getFile(campaign.getArchiveFileId());
    } catch (Exception e) {
      LOG.warn("Error reading cleanup campaign {} archive file {}", campaign.getId(), campaign.getArchiveFileId(), e);
      return;
    }
    InputStream archiveStream = fileItem == null ? null : fileItem.getAsStream();
    if (archiveStream == null) {
      LOG.warn("Cleanup campaign {} archive file {} has no content anymore",
               campaign.getId(),
               campaign.getArchiveFileId());
      return;
    }
    try (InputStream inputStream = archiveStream) {
      inputStream.transferTo(outputStream);
      outputStream.flush();
    }
  }

  private String toCsvRow(CleanupCampaignItem item, Map<Long, String> ownerNames) {
    StringBuilder row = new StringBuilder();
    row.append(escapeCsv(item.getNodeUuid()))
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
       // Appended columns — a display name can carry a comma or a quote, so it
       // goes through the very same escaping as the path
       .append(',')
       .append(escapeCsv(ownerName(item.getOwnerIdentityId(), ownerNames)))
       .append(',')
       .append(toIsoUtc(item.getLastModifiedDate()))
       .append(',')
       .append(toIsoUtc(item.getCreatedDate()))
       // Administrators-only columns, appended LAST: the archive endpoint is
       // @Secured("administrators"), unlike the user-facing item DTO which never
       // carries the detail. The detail is a multi-LINE stack trace, so it goes
       // through the newline-collapsing escaping — the CSV must stay ONE ROW PER
       // ITEM whatever a JCR exception looks like
       .append(',')
       .append(item.getAttemptCount())
       .append(',')
       .append(escapeCsv(toSingleLine(item.getFailureDetail()), true))
       .append('\n');
    return row.toString();
  }

  /**
   * Owner display name of a row, memoized in the export-local map: the number of
   * {@link IdentityManager} lookups is bounded by the number of DISTINCT owners
   * of the campaign, not by its row count. An unresolvable identity is memoized
   * as an EMPTY name — it must degrade the row, never fail the download nor be
   * looked up again on every row.
   */
  private String ownerName(long ownerIdentityId, Map<Long, String> ownerNames) {
    return ownerNames.computeIfAbsent(ownerIdentityId, this::resolveOwnerName);
  }

  private String resolveOwnerName(long ownerIdentityId) {
    try {
      return CleanupIdentityUtil.displayName(identityManager.getIdentity(ownerIdentityId));
    } catch (Exception e) {
      LOG.debug("Error resolving the cleanup report owner name of identity {}", ownerIdentityId, e);
      return "";
    }
  }

  /**
   * A report date as ISO-8601 UTC (e.g. 2026-08-20T09:15:30Z), empty when unset:
   * the CSV is a MACHINE-readable export, so its dates are never localized —
   * unlike the ones the UI renders through the platform's date component.
   */
  private String toIsoUtc(long millis) {
    return millis == 0 ? "" : DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis));
  }

  /**
   * Materializes the CSV report for the retention archiving path only: the
   * {@link FileService} needs the content length up front. The download path
   * never goes through here — see {@link #writeCsv(long, OutputStream)}.
   */
  /**
   * The ONE CSV field escaping, used by every column that can carry a comma, a
   * quote or a line break — a carriage return counting as one too.
   */
  private String escapeCsv(String value) {
    return escapeCsv(value, false);
  }

  /**
   * Same escaping, with the option to quote UNCONDITIONALLY. The failure-detail
   * column takes that option: it is the one field whose content is a free-form
   * exception dump, so it is quoted whether or not this particular dump happens
   * to hold a separator. A NULL detail still exports as a plain empty field —
   * quoting emptiness would say nothing and only widen the report.
   *
   * @param value raw field value
   * @param alwaysQuote true to quote even a separator-free value
   * @return the field as it goes into the row
   */
  private String escapeCsv(String value, boolean alwaysQuote) {
    if (value == null) {
      return "";
    }
    if (alwaysQuote || value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  /**
   * Flattens the line breaks of a value into the literal two-character sequence
   * {@code \n}, so a multi-line stack trace can NEVER break the row structure of
   * the report: the CSV stays one row per item, whatever the exception looked
   * like. Applied on top of {@link #escapeCsv(String)}, never instead of it — the
   * flattened value still holds commas and quotes.
   */
  private String toSingleLine(String value) {
    return value == null ? null : value.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
  }

  /**
   * The ONE campaign-name validation, shared by the creation and the update
   * paths so they can never diverge: mandatory, and no longer than the NAME
   * column (see {@link #MAX_NAME_LENGTH}). The trimmed name is RETURNED rather
   * than validated in place — surrounding whitespace must not be persisted, and
   * a name that only fits once trimmed must not be refused.
   *
   * @param name raw campaign name
   * @return the trimmed name to persist
   * @throws IllegalArgumentException "cleanup.nameMandatory" when blank,
   *           "cleanup.nameTooLong" past {@link #MAX_NAME_LENGTH} characters
   */
  private String validateName(String name) {
    if (StringUtils.isBlank(name)) {
      throw new IllegalArgumentException("cleanup.nameMandatory");
    }
    String trimmedName = name.trim();
    if (trimmedName.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException("cleanup.nameTooLong");
    }
    return trimmedName;
  }

  /**
   * Bounds validation of the effective (fully populated) campaign parameters.
   * Grace days of ZERO is explicitly allowed (architect decision): the grace
   * deadline then elapses immediately at publication. The batch size is never
   * client-settable (the REST layer always passes a null batchSize override),
   * so it is not validated here.
   */
  private void validateParams(CleanupParams params) {
    if (params.getPeriodMonths() == null || params.getPeriodMonths() < 1) {
      throw new IllegalArgumentException("cleanup.invalidPeriodMonths");
    }
    if (params.getMinFileSizeBytes() == null || params.getMinFileSizeBytes() < 0) {
      throw new IllegalArgumentException("cleanup.invalidMinFileSize");
    }
    validateGraceDays(params.getGraceDays());
    if (params.getMaxVersionsPerFile() == null || params.getMaxVersionsPerFile() < 1) {
      throw new IllegalArgumentException("cleanup.invalidMaxVersionsPerFile");
    }
  }

  /**
   * The ONE grace-period bound check, shared by the creation path (through
   * {@link #validateParams(CleanupParams)}) and the update path so they can
   * never diverge — the same discipline {@link #validateName(String)} already
   * applies to the name. ZERO IS VALID: the grace deadline then elapses at
   * publication.
   *
   * @param graceDays grace period in days
   * @throws IllegalArgumentException "cleanup.invalidGraceDays" when unset or
   *           negative
   */
  private void validateGraceDays(Integer graceDays) {
    if (graceDays == null || graceDays < 0) {
      throw new IllegalArgumentException("cleanup.invalidGraceDays");
    }
  }

  /**
   * The DIRECTION guard on a PUBLISHED grace period (architect decision W22):
   * once published it may only grow. Deliberately narrow, so it forbids exactly
   * one thing and nothing more:
   * <ul>
   * <li>only in PUBLISHED — DRAFT and SIMULATED promised nothing, so the value
   * stays free there, ZERO included</li>
   * <li>a STRICT comparison, so re-saving the SAME value still succeeds: it
   * moves no deadline, and the console's partial update can legitimately carry
   * an unchanged field</li>
   * <li>no current value to compare against (the defensive no-params campaign
   * of {@link #applyGraceDays}) means no promise on record: the edit goes
   * through rather than being refused on a value nobody can read</li>
   * </ul>
   * Runs AFTER {@link #validateGraceDays(Integer)}, so an out-of-bounds value
   * still answers its own bound code rather than this one, and BEFORE the new
   * value is written into the snapshot — the comparison needs the value still
   * in place.
   *
   * @throws IllegalArgumentException "cleanup.graceDaysCannotBeReduced" when the
   *           new grace period is strictly shorter than the published one
   */
  private void checkGraceDaysNotReduced(CleanupCampaign campaign, Integer graceDays) {
    if (campaign.getState() != CleanupCampaignState.PUBLISHED || campaign.getParams() == null) {
      return;
    }
    Integer publishedGraceDays = campaign.getParams().getGraceDays();
    if (publishedGraceDays != null && graceDays < publishedGraceDays) {
      throw new IllegalArgumentException("cleanup.graceDaysCannotBeReduced");
    }
  }

  /**
   * @param campaign campaign to check
   * @param now evaluation time (epoch millis)
   * @return true once the grace deadline elapsed (a zero grace period makes it
   *         elapse immediately at publication)
   */
  private boolean isGraceDeadlineElapsed(CleanupCampaign campaign, long now) {
    return campaign.getLockDate() > 0 && campaign.getLockDate() <= now;
  }

  /**
   * The review window freezes at the GRACE DEADLINE, not at the LOCKED
   * transition: the locking cron runs every 10 minutes, so a campaign can stay
   * PUBLISHED for up to that long after its deadline elapsed (systematically so
   * with a zero grace period). Accepting a keep in that window would let a user
   * believe a file is spared while the purge is about to start.
   *
   * @throws IllegalArgumentException "cleanup.reviewClosed" once the grace
   *           deadline elapsed
   */
  private void checkReviewWindowOpen(CleanupCampaign campaign) {
    if (isGraceDeadlineElapsed(campaign, System.currentTimeMillis())) {
      throw new IllegalArgumentException("cleanup.reviewClosed");
    }
  }

  /**
   * Single PUBLISHED to LOCKED code path, shared by the scheduled
   * grace-deadline glue and the manual execution trigger: the lifecycle
   * transition unregisters the freshness observation listener on exit of
   * PUBLISHED.
   */
  private CleanupCampaign lockCampaign(CleanupCampaign campaign) {
    return campaignLifecycle.transition(campaign, CleanupCampaignState.LOCKED);
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

  /**
   * Owner identity ids the user may review: their own identity, plus the
   * identity of EVERY space they manage. The managed spaces are read PAGE BY
   * PAGE up to the reported total: a single bounded {@code load(0, N)} used to
   * silently truncate the list, so a user managing more than N spaces never saw
   * the candidates of the spaces past the cap — neither in their review list nor
   * in their summary counters.
   */
  private List<Long> getUserOwnedIdentityIds(String username) {
    List<Long> ownerIdentityIds = new ArrayList<>();
    Identity userIdentity = identityManager.getOrCreateUserIdentity(username);
    if (userIdentity != null) {
      ownerIdentityIds.add(Long.parseLong(userIdentity.getId()));
    }
    try {
      ListAccess<Space> managerSpaces = spaceService.getManagerSpaces(username);
      int total = managerSpaces.getSize();
      for (int offset = 0; offset < total; offset += MANAGED_SPACES_PAGE_SIZE) {
        Space[] managedSpaces = managerSpaces.load(offset, Math.min(MANAGED_SPACES_PAGE_SIZE, total - offset));
        for (Space space : managedSpaces) {
          Identity spaceIdentity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
          if (spaceIdentity != null) {
            ownerIdentityIds.add(Long.parseLong(spaceIdentity.getId()));
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Error retrieving managed spaces of user {}", username, e);
    }
    return ownerIdentityIds;
  }

  /**
   * Single-campaign detail path: unchanged per-campaign aggregate queries.
   */
  private CleanupCampaign withAggregates(CleanupCampaign campaign) {
    long campaignId = campaign.getId();
    campaign.setItemsRetained(campaignStorage.hasItems(campaignId));
    if (servesAggregatesFromSummary(campaign)) {
      applySummaryAggregates(campaign);
    } else {
      campaign.setCandidateCount(campaignStorage.countItemsByState(campaignId, CleanupItemState.CANDIDATE));
      campaign.setReclaimableBytes(campaignStorage.sumReclaimableBytesByState(campaignId, CleanupItemState.CANDIDATE));
      campaign.setReclaimedBytes(campaignStorage.sumReclaimedBytes(campaignId));
    }
    return withLiveExecutionProgress(campaign);
  }

  /**
   * Campaigns-list path: same aggregate mapping, fed from the batched grouped
   * query instead of per-campaign queries.
   */
  private CleanupCampaign withAggregates(CleanupCampaign campaign, CleanupCampaignAggregates aggregates) {
    campaign.setItemsRetained(aggregates.isItemsRetained());
    if (servesAggregatesFromSummary(campaign)) {
      applySummaryAggregates(campaign);
    } else {
      campaign.setCandidateCount(aggregates.getCandidateCount());
      campaign.setReclaimableBytes(aggregates.getReclaimableBytes());
      campaign.setReclaimedBytes(aggregates.getReclaimedBytes());
    }
    return withLiveExecutionProgress(campaign);
  }

  /**
   * Reconciles the purge numerator with the aggregates served beside it.
   * <p>
   * THE CONTRADICTION IT REMOVES: {@code PROCESSED_COUNT} is checkpointed once
   * per BATCH by the purge worker, while the candidate count and the reclaimed
   * total are aggregate queries recomputed on every read. So a console showing
   * all three at once showed a purge that had freed gigabytes, and had 105 fewer
   * candidates than it started with, above a bar reading '0% (0 / 5,083)' —
   * every number correct, and the three of them together impossible. The batch
   * size being 200 by default, that state is not a flicker: it lasts as long as
   * the first two hundred deletions take, on files large enough for the freed
   * total to be the first thing an administrator looks at.
   * <p>
   * The derived numerator is exact rather than an estimate: a purge's
   * denominator IS the CANDIDATE count taken when it started (see
   * {@code CleanupExecutionService#startExecution}), and an item leaves CANDIDATE
   * exactly once, whether it was purged, failed or skipped. So total minus the
   * live candidate count is precisely what the worker has settled.
   * <p>
   * Never LOWER than the checkpoint, which is what keeps the bar monotonic: the
   * observation listener may add candidates to a campaign mid-purge, and a bar
   * walking backwards while files are being deleted would be worse than one
   * lagging behind.
   * <p>
   * EXECUTING only. Anywhere else the persisted counter is the truth — a dry run
   * counts NODES walked, which no item aggregate can express.
   */
  private CleanupCampaign withLiveExecutionProgress(CleanupCampaign campaign) {
    if (campaign.getState() != CleanupCampaignState.EXECUTING || campaign.getTotalCount() <= 0) {
      return campaign;
    }
    long settled = campaign.getTotalCount() - campaign.getCandidateCount();
    campaign.setProcessedCount(Math.min(campaign.getTotalCount(), Math.max(campaign.getProcessedCount(), settled)));
    return campaign;
  }

  private boolean servesAggregatesFromSummary(CleanupCampaign campaign) {
    return !campaign.isItemsRetained() && TERMINAL_STATES.contains(campaign.getState())
           && StringUtils.isNotBlank(campaign.getSummaryJson());
  }

  /**
   * The retention job purged the item rows: the live aggregates would all be 0,
   * serve the summary snapshotted at campaign completion instead.
   */
  private void applySummaryAggregates(CleanupCampaign campaign) {
    CleanupCampaignSummary summary = JsonUtils.fromJsonString(campaign.getSummaryJson(), CleanupCampaignSummary.class);
    campaign.setCandidateCount(summary.getCandidateCount());
    campaign.setReclaimableBytes(summary.getReclaimableBytes());
    campaign.setReclaimedBytes(summary.getReclaimedBytes());
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
