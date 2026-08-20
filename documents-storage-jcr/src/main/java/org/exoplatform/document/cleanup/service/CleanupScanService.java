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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.constant.CleanupScanUnitState;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignSummary;
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupFailureGroup;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupScanUnit;
import org.exoplatform.document.cleanup.storage.CleanupCampaignStorage;
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage;
import org.exoplatform.document.cleanup.storage.CleanupScanUnitStorage;
import org.exoplatform.document.cleanup.util.CleanupEtaUtil;
import org.exoplatform.document.cleanup.websocket.CleanupWebSocketService;
import org.exoplatform.document.cleanup.websocket.CleanupWsMessage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.common.ContainerTransactional;
import io.meeds.social.util.JsonUtils;

import jakarta.annotation.PreDestroy;

/**
 * Asynchronous dry-run scan of the collaboration workspace, PARALLELISED over
 * scan UNITS: the tree is partitioned once (see
 * {@code CleanupConstants.SPLIT_SCAN_ROOTS}), several reader threads walk their
 * own unit each, and ONE writer thread persists everything they stream through a
 * bounded queue. Nothing is ever deleted here.
 * <p>
 * THE INVARIANT of the whole design is ONLY THE WRITER TOUCHES JPA while readers
 * are alive. It is held by construction, not by discipline — and it survives a
 * reader outliving the coordinator (see {@link #awaitReaders}), a reader having
 * no JPA call to make in the first place:
 * <ul>
 * <li>a reader NEVER calls a storage bean other than
 * {@link CleanupJcrStorage#scanRoot} — no read, no write. It posts envelopes on
 * the queue and reads volatile flags, nothing else</li>
 * <li>the coordinator writes only BEFORE the readers start (planning, per-unit
 * totals, claiming the units) and AFTER they all finished (the terminal
 * transition) — never in between</li>
 * <li>the terminal state of a unit (DONE / FAILED) is therefore NOT written by
 * the reader that finished it: the reader posts it as the last envelope of its
 * unit and the WRITER records it</li>
 * <li>the abort flag itself is refreshed from the database by the writer, so
 * even the {@code isAborted} campaign re-read stays on the writer's thread; the
 * readers poll the volatile flag it publishes</li>
 * </ul>
 * <p>
 * A FAILED UNIT IS RETRIED, BOUNDEDLY. A unit whose walk failed is not terminal:
 * {@code getUnitsToProcess} hands it back to the next run, so the campaign is
 * deliberately left DRY_RUN_RUNNING and the watchdog
 * ({@code CleanupCampaignService#resumeStalledWorkers}) re-walks it — a transient
 * JCR failure heals itself with no human in the loop. The bound is
 * {@link #MAX_SCAN_UNIT_ATTEMPTS}, and it bounds the WALK and not merely a
 * counter: past it the subtree has proved unreadable, the unit is SETTLED-failed,
 * {@code CleanupScanUnitStorage#getUnitsToProcess} stops handing it out — so no
 * later run claims it, spends an attempt on it or re-walks it — and it stops
 * holding the dry-run back. The report is then marked INCOMPLETE rather than
 * silently partial (see {@link #completeCampaign(long, long)}).
 * <p>
 * A campaign whose EVERY unit settled-failed (a JCR outage fails them all) is
 * therefore not a spin either: its work list is empty, every watchdog tick is a
 * cheap no-op that walks nothing, and the campaign stays visibly stuck in
 * DRY_RUN_RUNNING — refused as a simulation, loudly logged, but never re-walked.
 * <p>
 * LEGACY CHECKPOINT — a campaign interrupted under the old SEQUENTIAL scheme
 * carries a {@code CHECKPOINT_PATH} on its campaign row and has no unit row. On
 * resume it is re-planned into units and that legacy checkpoint is IGNORED, so
 * the scan re-walks the tree under the new partitioning. Deliberate and safe:
 * {@code saveCandidates} de-duplicates on the campaign+nodeUuid unique
 * constraint, so the replay costs work and can never duplicate a row.
 */
@Service
public class CleanupScanService {

  private static final Log             LOG                        = ExoLogger.getLogger(CleanupScanService.class);

  /**
   * Localizable message code recorded on a unit whose walk failed — the console
   * looks it up in an i18n bundle, so it must never carry an exception message.
   */
  private static final String          SCAN_UNIT_FAILED_REASON    = "cleanup.scanUnitFailed";

  /**
   * Walk attempts a scan unit may spend IN TOTAL, the first walk included —
   * THREE walks, not one walk plus three retries. Bounds the whole unit retry:
   * past the third failure the subtree has proved unreadable whatever the cause
   * was, and the watchdog must not re-walk it on every tick until the end of
   * time. Bounds the unit retry exactly as
   * {@code CleanupCampaignService#MAX_RETRY_ATTEMPTS} bounds the item retry, but
   * counted from a DIFFERENT origin, deliberately: an attempt is spent here by
   * the coordinator CLAIMING the unit, first walk included, whereas an item
   * spends one only on a requeue — so the same value means three walks here and
   * three retries after the initial attempt there.
   */
  public static final long             MAX_SCAN_UNIT_ATTEMPTS     = 3;

  /**
   * Floor of the batch-queue capacity, which is otherwise twice the reader
   * count. The bound IS the backpressure: a reader that cannot post blocks until
   * the writer catches up, so the readers can never outrun the database. An
   * unbounded buffer would simply be an OOM at 800 GB scale — millions of
   * candidate envelopes waiting on one writer.
   */
  private static final int             MIN_QUEUE_CAPACITY         = 4;

  /**
   * Bound of every blocking queue operation: a reader re-checks the abort/failed
   * flags between two offers (a dead writer must not leave it blocked forever),
   * and the writer re-checks them between two polls — a lost poison pill would
   * otherwise hang the worker for good.
   * <p>
   * Neither side ever blocks indefinitely, but this bound alone is NOT what
   * guarantees it: it only makes the stop flags observable, and a reader whose
   * writer died without raising one would retry forever. The guarantee is
   * {@link #readersDeadlineMillis}, which does not depend on any flag.
   */
  private static final long            QUEUE_TIMEOUT_MILLIS       = 200L;

  /** Bound of each {@code awaitTermination} slice while the readers run. */
  private static final long            READERS_AWAIT_MILLIS       = 500L;

  /**
   * Bound of the final wait on the writer thread. Generous on purpose: once the
   * poison pill is in, the writer only has the queue's remaining capacity to
   * flush, so overrunning this means something is wrong — and it is then logged
   * rather than waited on forever.
   */
  private static final long            WRITER_JOIN_MILLIS         = 300000L;

  /**
   * WALL-CLOCK deadline of the whole reader wait, past which the readers are
   * interrupted UNCONDITIONALLY — the structural half of the no-indefinite-block
   * guarantee. The flag-based half only covers the abnormal exits somebody
   * enumerated; this one covers a writer thread that vanished for ANY reason,
   * including one nobody thought of, because it never consults a flag at all.
   * <p>
   * DERIVED, not picked: {@code scanRoot} holds ONE JCR session for a unit's
   * whole lazy walk and stamps it with
   * {@code documents.cleanup.jcr.session.timeout} (one hour by default), so a
   * reader that has been inside its walk for longer than that timeout is already
   * doomed — its session is gone. The deadline is TWICE that hour, which leaves a
   * legitimately long walk a full session lifetime of slack before it can ever be
   * reached, and it is overridable for the deployments that raise the session
   * timeout.
   */
  @Value("${documents.cleanup.scan.readers.deadline:7200000}")
  private long                         readersDeadlineMillis;

  @Autowired
  private CleanupCampaignStorage       campaignStorage;

  @Autowired
  private CleanupCampaignLifecycle     campaignLifecycle;

  @Autowired
  private CleanupJcrStorage            cleanupJcrStorage;

  @Autowired
  private CleanupScanUnitStorage       scanUnitStorage;

  @Autowired
  private CleanupSettingService        settingService;

  @Autowired
  private CleanupWebSocketService      webSocketService;

  private ExecutorService              executorService            = Executors.newSingleThreadExecutor();

  /**
   * Campaign ids whose scan worker is currently running: guards against a
   * double-start (e.g. the startup recovery racing a manual start).
   */
  private final Set<Long>              runningCampaigns           = ConcurrentHashMap.newKeySet();

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

  /**
   * Transactional entry point of the COORDINATOR: planning, per-unit estimation
   * and the terminal transition run in ONE container transaction, exactly like
   * the execution purge
   * ({@link CleanupExecutionService#executeCampaignTransactional(long)}). The
   * streamed candidate rows are NOT written here — they are written by the
   * writer thread, which opens its own transaction (see
   * {@link #drainQueueTransactional(ScanRun)}) and commits per batch.
   * <p>
   * It is a thin wrapper on purpose: the annotation is woven around THIS method,
   * so the tests drive {@link #scan(long)} directly and never boot a container
   * to exercise the worker body — the annotation itself is pinned by reflection
   * in {@code CleanupScanServiceTest}.
   *
   * @param campaignId campaign identifier
   */
  @ContainerTransactional
  public void scanTransactional(long campaignId) {
    scan(campaignId);
  }

  /**
   * Scan coordinator, running as system (no conversation state needed), in four
   * phases: PLAN the units, ESTIMATE their sizes in parallel, SCAN them with
   * readers + one writer, then COMPLETE. Visible for tests, which invoke it
   * directly instead of going through the transactional wrapper above.
   */
  protected void scan(long campaignId) { // NOSONAR
    if (!runningCampaigns.add(campaignId)) {
      // Already running: never double-start a campaign's scan worker
      return;
    }
    ExecutorService readerPool = null;
    try {
      CleanupCampaign campaign = campaignStorage.getCampaign(campaignId);
      if (campaign == null || campaign.getState() != CleanupCampaignState.DRY_RUN_RUNNING) {
        return;
      }

      // (a) PLAN — idempotent, so a resume re-plans harmlessly
      planUnits(campaign);
      List<CleanupScanUnit> units = scanUnitStorage.getUnitsToProcess(campaignId, MAX_SCAN_UNIT_ATTEMPTS);

      // (b) ESTIMATE — in parallel, on the very pool the readers will use
      int readerCount = Math.max(1, Math.min(settingService.getScanThreads(), units.size()));
      readerPool = Executors.newFixedThreadPool(readerCount, threadFactory("cleanup-scan-reader-" + campaignId));
      long total = estimateUnits(campaignId, units, readerPool);
      long processedAtStart = scanUnitStorage.sumScannedCount(campaignId);
      campaignStorage.updateProgress(campaignId, total, Math.min(processedAtStart, total), 0, null, 0);

      // (c) SCAN — readers stream to ONE writer through a bounded queue
      if (!units.isEmpty()) {
        scanUnits(campaignId, campaign.getParams(), units, readerPool, readerCount, total, processedAtStart);
      }

      // (d) COMPLETE
      completeCampaign(campaignId, total);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("The scan of cleanup campaign {} was interrupted. It can be resumed from its unit checkpoints.", campaignId, e);
    } catch (Exception e) {
      // Leave the campaign DRY_RUN_RUNNING: every unit carries its own path
      // checkpoint, so the scan is resumable unit by unit
      LOG.warn("Error while scanning cleanup campaign {}. The scan can be resumed from its unit checkpoints.", campaignId, e);
    } finally {
      if (readerPool != null) {
        // No thread leak, on ANY exit path — normal end, abort or failure
        readerPool.shutdownNow();
      }
      runningCampaigns.remove(campaignId);
    }
  }

  /**
   * Enumerates the units and inserts the missing ones. A campaign carrying a
   * legacy sequential checkpoint but no unit is re-planned from scratch, its
   * checkpoint ignored — logged once, naming the campaign (see the class
   * comment for why that replay is safe).
   */
  private void planUnits(CleanupCampaign campaign) {
    long campaignId = campaign.getId();
    if (StringUtils.isNotBlank(campaign.getCheckpointPath()) && scanUnitStorage.countUnits(campaignId) == 0) {
      LOG.warn("Cleanup campaign {} was interrupted by the sequential scan at checkpoint {}:"
          + " it is re-planned into scan units and that checkpoint is IGNORED,"
          + " so the tree is re-walked (candidates are de-duplicated, never duplicated).",
               campaignId,
               campaign.getCheckpointPath());
    }
    scanUnitStorage.planUnits(campaignId, cleanupJcrStorage.listScanUnits());
  }

  /**
   * Counts, IN PARALLEL on the reader pool, the units not counted yet, then
   * persists each count and returns the campaign denominator.
   * <p>
   * The counting phase is kept although the units already bound the work: it is
   * what keeps the ETA denominator (and the console's percentage) COMPARABLE
   * with the campaigns scanned by the sequential worker, which counted the whole
   * tree up front too.
   * <p>
   * The reader threads only COUNT here: the counts come back to the coordinator,
   * which is the one persisting them — see the class comment's invariant.
   */
  private long estimateUnits(long campaignId,
                             List<CleanupScanUnit> units,
                             ExecutorService readerPool) throws InterruptedException, ExecutionException {
    Map<CleanupScanUnit, Future<Long>> countings = new HashMap<>();
    for (CleanupScanUnit unit : units) {
      // NULL is 'never counted', and 0 is 'counted, and empty'. Testing for 0
      // re-counted every genuinely empty bucket — a first-letter bucket of /Users
      // holding no file — on EVERY resume, for a count already known to be 0
      if (unit.getTotalCount() == null) {
        countings.put(unit, readerPool.submit(() -> cleanupJcrStorage.countFiles(unit.getUnitPath())));
      }
    }
    for (Map.Entry<CleanupScanUnit, Future<Long>> counting : countings.entrySet()) {
      CleanupScanUnit unit = counting.getKey();
      long counted = counting.getValue().get();
      unit.setTotalCount(counted);
      scanUnitStorage.updateUnitTotal(unit.getId(), counted);
    }
    // Summed by the database over EVERY unit, the ones already DONE included:
    // the denominator is the whole tree, not what is left to walk
    return scanUnitStorage.sumTotalCount(campaignId);
  }

  /**
   * Runs the parallel walk: {@code readerCount} readers, ONE writer, a bounded
   * queue between them.
   * <p>
   * The units are ALL claimed here, by the coordinator, BEFORE any reader starts
   * — that is what keeps a reader out of JPA (see the class comment) — and the
   * claim is what SPENDS a walk attempt, so a unit re-walked by the watchdog can
   * never spend an unbounded number of them. The writer is started right after
   * the readers are submitted and never before: a bounded queue means the readers
   * block until it drains.
   */
  private void scanUnits(long campaignId, // NOSONAR
                         CleanupParams params,
                         List<CleanupScanUnit> units,
                         ExecutorService readerPool,
                         int readerCount,
                         long total,
                         long processedAtStart) throws InterruptedException {
    for (CleanupScanUnit unit : units) {
      scanUnitStorage.claimUnit(unit.getId());
    }
    ScanRun run = new ScanRun(campaignId, params, settingService.getBatchSize(), total, processedAtStart, readerCount, units);
    for (CleanupScanUnit unit : units) {
      readerPool.execute(() -> readUnit(run, unit));
    }
    Thread writerThread = threadFactory("cleanup-scan-writer-" + campaignId).newThread(() -> runWriter(run));
    writerThread.start();

    awaitReaders(readerPool, run);
    // Posted only once EVERY reader is done, so it can never be drained before
    // the last batch of the last unit
    post(run, ScanRun.POISON_PILL);
    writerThread.join(WRITER_JOIN_MILLIS);
    if (writerThread.isAlive()) {
      LOG.warn("The writer thread of the cleanup campaign {} scan did not finish within {} ms: the scan stays resumable",
               campaignId,
               WRITER_JOIN_MILLIS);
    }
  }

  /**
   * Reader body: walks ONE unit and posts everything it finds on the queue,
   * without ever touching JPA. The unit's own {@code LAST_SCANNED_PATH} is the
   * resume position (null when it was never started).
   * <p>
   * A JCR {@code Session} is NOT thread-safe, which is why per-thread sessions
   * are MANDATORY here: {@code scanRoot} opens its own system session per call
   * and holds it for its whole lazy walk, so every reader gets its own — never
   * change that to share one.
   * <p>
   * A failure marks THIS unit failed and nothing else: one unreadable subtree
   * must not deny the whole simulation. The failure is posted, not written —
   * only the writer writes.
   */
  private void readUnit(ScanRun run, CleanupScanUnit unit) {
    long unitId = unit.getId();
    try {
      cleanupJcrStorage.scanRoot(unit.getUnitPath(),
                                 unit.getLastScannedPath(),
                                 run.batchSize,
                                 run.params,
                                 (candidates, lastScannedPath, scannedCount) -> post(run,
                                                                                     ScanBatch.progress(unitId,
                                                                                                        candidates,
                                                                                                        lastScannedPath,
                                                                                                        scannedCount)));
      if (!run.isStopped()) {
        post(run, ScanBatch.terminal(unitId, CleanupScanUnitState.DONE, null));
      }
    } catch (Exception e) {
      LOG.warn("Error scanning the cleanup unit {} of campaign {}: this unit is marked failed, the other units go on",
               unit.getUnitPath(),
               run.campaignId,
               e);
      post(run, ScanBatch.terminal(unitId, CleanupScanUnitState.FAILED, SCAN_UNIT_FAILED_REASON));
    }
  }

  /**
   * Writer runnable. The catch-all is LOAD-BEARING: should the transactional
   * entry point below blow up before the drain loop's own handler can run, the
   * readers would be left blocked forever on a queue nothing drains — so the
   * failed flag is raised on EVERY abnormal exit, not only on a persistence
   * error.
   * <p>
   * {@code Throwable} and NOT {@code Exception}, which is the whole point: the
   * failure this bounded queue exists to prevent is an {@code OutOfMemoryError}
   * on a multi-million-node walk, and a deep traversal can equally raise a
   * {@code StackOverflowError}. Catching only {@code Exception} let exactly those
   * escape with the flag still false, and every future scan of every campaign
   * then queued behind the hung task of a single-thread coordinator.
   * <p>
   * The error is swallowed deliberately rather than rethrown: it is logged here,
   * the flag it raises IS the recovery (the readers stop, the campaign stays
   * resumable), and rethrowing would only reprint the trace on an uncaught
   * handler. The reader wait no longer DEPENDS on this handler anyway — see
   * {@link #awaitReaders}.
   */
  private void runWriter(ScanRun run) {
    try {
      drainQueueTransactional(run);
    } catch (Throwable e) { // NOSONAR
      run.writerFailed = true;
      LOG.error("The writer of the cleanup campaign {} scan failed to start: the readers are stopped", run.campaignId, e);
    }
  }

  /**
   * Transactional entry point of the WRITER thread, self-invoked from the
   * runnable started by {@link #scanUnits}: the project weaves
   * {@code @ContainerTransactional} with AspectJ, which advises self-calls too —
   * the same property {@code executorService.execute(() -> scanTransactional(id))}
   * already relies on.
   *
   * @param run state of the current scan run
   */
  @ContainerTransactional
  public void drainQueueTransactional(ScanRun run) {
    drainQueue(run);
  }

  /**
   * Writer body — the ONLY component writing what the scan produces. Drains the
   * queue until the poison pill, and per batch: persists the candidates,
   * checkpoints THE UNIT THE BATCH CAME FROM, recomputes the aggregate progress
   * and its ETA, pushes the progress event, then commits.
   * <p>
   * The aggregate numerator is kept IN MEMORY (seeded from the persisted
   * per-unit counts) rather than re-summed per batch: the sum is already known
   * exactly, and a database aggregate per batch would be pure overhead on a
   * multi-million-node walk.
   * <p>
   * Visible for tests, which drive it directly so the woven aspect above never
   * boots a container.
   */
  protected void drainQueue(ScanRun run) { // NOSONAR
    long processed = run.processedAtStart;
    try {
      while (true) {
        ScanBatch batch = run.queue.poll(QUEUE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        if (batch == null) {
          // Idle: re-check the abort flag rather than block forever on a pill
          // that a crashed coordinator may never post
          if (refreshAborted(run)) {
            return;
          }
          continue;
        } else if (batch == ScanRun.POISON_PILL) {
          return;
        } else if (refreshAborted(run)) {
          // Checked BEFORE anything is written, exactly where the sequential
          // worker checked it: an aborted campaign persists no further batch,
          // and no unit is recorded DONE either
          return;
        } else if (batch.terminalState != null) {
          recordUnitOutcome(batch);
          restartTransaction();
          continue;
        }
        campaignStorage.saveCandidates(run.campaignId, batch.candidates);
        scanUnitStorage.updateUnitProgress(batch.unitId, batch.lastScannedPath, run.addScanned(batch));
        processed += batch.scannedCount;
        long reported = Math.min(processed, run.total);
        long etaSeconds = CleanupEtaUtil.computeEtaSeconds(run.startTime, run.processedAtStart, reported, run.total);
        campaignStorage.updateProgress(run.campaignId, run.total, reported, etaSeconds, null, 0);
        webSocketService.sendToAdministrators(new CleanupWsMessage(CleanupWsMessage.PROGRESS_EVENT,
                                                                   run.campaignId,
                                                                   CleanupCampaignState.DRY_RUN_RUNNING.name(),
                                                                   reported,
                                                                   run.total,
                                                                   etaSeconds));
        // Per-batch transaction boundary, mirroring the purge worker: commits
        // this batch and opens a fresh one, so a failure in a later batch can no
        // longer roll back the batches that already completed
        restartTransaction();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      run.writerFailed = true;
    } catch (Throwable e) { // NOSONAR
      // The readers MUST observe this: they are blocked on a queue nothing
      // drains anymore, and would otherwise never stop. Throwable and not
      // Exception: an OOM on a multi-million-node walk escaped this handler with
      // the flag still false, which is precisely the hang this catch prevents
      run.writerFailed = true;
      LOG.error("Error persisting the scan of cleanup campaign {}: the readers are stopped and the scan stays resumable",
                run.campaignId,
                e);
    }
  }

  private void recordUnitOutcome(ScanBatch batch) {
    if (batch.terminalState == CleanupScanUnitState.FAILED) {
      scanUnitStorage.updateUnitFailure(batch.unitId, batch.failureReason);
    } else {
      scanUnitStorage.updateUnitState(batch.unitId, batch.terminalState);
    }
  }

  /**
   * Terminal transition, only when the run was not aborted and every unit
   * SETTLED — DONE, or failed with every walk attempt spent. Two refusals, in
   * this order (the order matters: the second one is about units that ARE all
   * settled, so it may only be reached once the first let them through):
   * <ol>
   * <li>a unit that is not settled yet blocks the transition: one that FAILED
   * with attempts LEFT, and one that never reached an outcome at all (an
   * interrupted run). The campaign deliberately stays DRY_RUN_RUNNING so the
   * watchdog re-walks that subtree — those units are still in the work list
   * ({@code CleanupScanUnitStorage#getUnitsToProcess}). Transitioning to
   * SIMULATED here was the whole bug — it was the LAST run of the campaign, so a
   * transient failure became permanent silently</li>
   * <li>EVERY unit settled-FAILED is not a simulation at all: a report covering
   * nothing is not a dry-run, and marking it INCOMPLETE over an empty report
   * would be a lie rather than a warning. So the campaign is left exactly as it
   * is — DRY_RUN_RUNNING, its unit checkpoints untouched — and loudly logged.
   * <p>
   * It is refused WITHOUT being re-walked, which is the point: the settled units
   * are out of the work list, so the watchdog's next tick plans nothing, claims
   * nothing and reads no JCR node. The campaign stays VISIBLY stuck instead of
   * burning an 800 GB repository every ten minutes to rediscover the same
   * failure. Re-attempting the tree used to be this guard's justification, and it
   * was the opposite of the bound {@link #MAX_SCAN_UNIT_ATTEMPTS} promises:
   * an outage fails ALL the units, so it was precisely the case the bound most
   * had to cover</li>
   * </ol>
   * <p>
   * Past the refusals the report is published, but never as if it were complete:
   * {@code processedCount} is what the units ACTUALLY walked — never the
   * denominator, which used to pin the console at 100% on a report missing whole
   * subtrees — and a settled failure marks the summary INCOMPLETE (see
   * {@link CleanupCampaignSummary}), which is what the console reads to say so.
   */
  private void completeCampaign(long campaignId, long total) { // NOSONAR
    if (isAborted(campaignId)) {
      return;
    }
    long unitCount = scanUnitStorage.countUnits(campaignId);
    long failedCount = scanUnitStorage.countUnitsByState(campaignId, CleanupScanUnitState.FAILED);
    long doneCount = scanUnitStorage.countUnitsByState(campaignId, CleanupScanUnitState.DONE);
    long settledFailedCount = scanUnitStorage.countSettledFailedUnits(campaignId, MAX_SCAN_UNIT_ATTEMPTS);
    if (doneCount + settledFailedCount < unitCount) {
      LOG.warn("Only {} of the {} scan units of cleanup campaign {} settled ({} failed with attempts left of the {} allowed):"
          + " the dry-run is NOT reported as simulated, the campaign stays DRY_RUN_RUNNING and the watchdog re-walks them.",
               doneCount + settledFailedCount,
               unitCount,
               campaignId,
               failedCount - settledFailedCount,
               MAX_SCAN_UNIT_ATTEMPTS);
      return;
    } else if (unitCount > 0 && settledFailedCount == unitCount) {
      LOG.error("Every one of the {} scan units of cleanup campaign {} failed its {} walks: the dry-run is NOT reported as"
          + " simulated — a report covering NOTHING is not a simulation — and the campaign stays DRY_RUN_RUNNING with its unit"
          + " checkpoints untouched. It is NOT re-walked either: every unit is settled, so the watchdog now finds an empty work"
          + " list. Whoever owns this campaign must fix the repository and start a new dry-run.",
                unitCount,
                campaignId,
                MAX_SCAN_UNIT_ATTEMPTS);
      return;
    }
    CleanupCampaign campaign = campaignStorage.getCampaign(campaignId);
    if (campaign != null && campaign.getState() == CleanupCampaignState.DRY_RUN_RUNNING) {
      // What was really walked, capped by the denominator exactly like the
      // per-batch progress writes: a partial scan must READ partial
      long walked = Math.min(scanUnitStorage.sumScannedCount(campaignId), total);
      campaign.setTotalCount(total);
      campaign.setProcessedCount(walked);
      campaign.setEtaSeconds(0);
      if (settledFailedCount > 0) {
        LOG.error("The dry-run of cleanup campaign {} is reported as simulated but INCOMPLETE: {} of its {} scan units could"
            + " not be walked in {} attempts, so {} of the {} counted nodes are MISSING from the report. Whoever publishes it"
            + " must know the report does not cover the whole tree.",
                  campaignId,
                  settledFailedCount,
                  unitCount,
                  MAX_SCAN_UNIT_ATTEMPTS,
                  total - walked,
                  total);
        campaign.setSummaryJson(buildScanSummaryJson(settledFailedCount));
      }
      campaignLifecycle.transition(campaign, CleanupCampaignState.SIMULATED);
    }
  }

  /**
   * Snapshots the dry-run's INCOMPLETE verdict on the campaign row, reusing the
   * summaryJson column and its typed {@link CleanupCampaignSummary} rather than
   * adding a column of its own: the purge aggregates it otherwise carries are all
   * still 0 at this point, and
   * {@code CleanupExecutionService#buildSummaryJson(long, String)} carries these
   * two fields FORWARD when it overwrites the column at COMPLETED — so the
   * verdict outlives the purge instead of being erased by it.
   */
  private String buildScanSummaryJson(long settledFailedCount) {
    CleanupCampaignSummary summary = new CleanupCampaignSummary();
    summary.setScanIncomplete(true);
    summary.setFailedScanUnitCount(settledFailedCount);
    return JsonUtils.toJsonString(summary);
  }

  /**
   * Grouped failures of a campaign's SCAN, one entry per distinct failure message
   * code with the number of subtrees that carry it — the unit-level twin of
   * {@code CleanupCampaignService#getCampaignFailures}, so the console renders
   * both through the same block.
   * <p>
   * Gated on the campaign's own INCOMPLETE verdict, and NOT on the live unit
   * rows: a unit that failed while attempts remain is a transient failure the
   * watchdog is already re-walking, and surfacing it would report a subtree as
   * missing from a report that is not even finished. Only a scan the coordinator
   * RECORDED as incomplete has anything to show here.
   * <p>
   * Every group is {@code retryable = false}, which is the honest answer: these
   * subtrees exhausted {@link #MAX_SCAN_UNIT_ATTEMPTS} walks, and there is
   * deliberately no console retry for them — re-running the scan of a campaign
   * that already left DRY_RUN_RUNNING is not an edge this lifecycle has.
   *
   * @param campaign campaign whose scan failures are wanted
   * @return the groups, EMPTY when the campaign's scan covered the whole tree
   */
  public List<CleanupFailureGroup> getScanFailures(CleanupCampaign campaign) {
    if (campaign == null || StringUtils.isBlank(campaign.getSummaryJson())
        || !JsonUtils.fromJsonString(campaign.getSummaryJson(), CleanupCampaignSummary.class).isScanIncomplete()) {
      return List.of();
    }
    return scanUnitStorage.countFailuresByReason(campaign.getId());
  }

  /**
   * Waits for every reader to finish, in bounded slices so the stop flag stays
   * observable: a writer failure (or an abort) interrupts the readers, which may
   * otherwise sit inside a long blocking JCR call.
   * <p>
   * And it waits under a WALL-CLOCK DEADLINE ({@link #readersDeadlineMillis}),
   * which is what makes the no-indefinite-block guarantee STRUCTURAL instead of
   * flag-dependent. The flag path only covers the abnormal writer exits somebody
   * enumerated: a writer that vanishes some other way raises nothing, the readers
   * retry their offers forever, this loop's escape hatch is unreachable, the
   * coordinator's {@code finally} never runs — so the campaign id is never
   * released — and every later scan of every campaign queues behind the hung task
   * of a single-thread coordinator, with no state change for the console or the
   * watchdog to notice. Past the deadline the readers are therefore interrupted
   * UNCONDITIONALLY, no flag consulted.
   * <p>
   * A reader that ignores its interrupt outlives the coordinator, which does NOT
   * break the JPA invariant: a reader has no storage call to make but
   * {@code scanRoot}, and {@code post} answers false as soon as the run is
   * stopped. And nothing terminal is recorded, so the campaign stays
   * DRY_RUN_RUNNING and the watchdog re-launches the scan from the unit
   * checkpoints.
   */
  private void awaitReaders(ExecutorService readerPool, ScanRun run) throws InterruptedException {
    // No new task accepted; the submitted ones run to completion
    readerPool.shutdown();
    long deadline = System.currentTimeMillis() + readersDeadlineMillis;
    boolean interrupted = false;
    while (!readerPool.awaitTermination(READERS_AWAIT_MILLIS, TimeUnit.MILLISECONDS)) {
      if (System.currentTimeMillis() >= deadline) {
        // Published so post() stops retrying rather than merely being
        // interrupted: the readers must UNBLOCK, not just get a flag set
        run.writerFailed = true;
        readerPool.shutdownNow();
        LOG.error("The readers of the cleanup campaign {} scan did not finish within {} ms: the writer thread is presumed"
            + " DEAD whatever the reason, the readers are interrupted unconditionally and the campaign stays"
            + " DRY_RUN_RUNNING — the watchdog re-launches it from the unit checkpoints.",
                  run.campaignId,
                  readersDeadlineMillis);
        return;
      }
      if (run.isStopped() && !interrupted) {
        readerPool.shutdownNow();
        interrupted = true;
      }
    }
  }

  /**
   * Posts an envelope, blocking while the queue is full — THAT is the
   * backpressure. Bounded offers rather than a plain {@code put}: a writer that
   * died must never leave a reader blocked forever, so the stop flags are
   * re-checked between two attempts. A writer that died WITHOUT raising one is
   * caught by the coordinator's deadline instead (see {@link #awaitReaders}) —
   * this loop is the fast path, not the guarantee.
   *
   * @return true when the envelope was handed over, false when the run stopped
   */
  private boolean post(ScanRun run, ScanBatch batch) {
    try {
      while (!run.isStopped()) {
        if (run.queue.offer(batch, QUEUE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
          return true;
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return false;
  }

  /**
   * Re-reads the campaign state and publishes the abort to the readers. Called
   * from the WRITER thread only: it is the one component allowed to touch JPA
   * while the readers run.
   */
  private boolean refreshAborted(ScanRun run) {
    if (run.aborted) {
      return true;
    } else if (isAborted(run.campaignId)) {
      run.aborted = true;
      return true;
    }
    return false;
  }

  private boolean isAborted(long campaignId) {
    CleanupCampaign campaign = campaignStorage.getCampaign(campaignId);
    return campaign == null || campaign.getState() != CleanupCampaignState.DRY_RUN_RUNNING;
  }

  /**
   * Commits the current batch and opens a fresh transaction. Extracted as a
   * single call so the worker tests — which drive the writer body directly, with
   * no PortalContainer booted — can neutralize it, exactly like
   * {@link CleanupExecutionService#restartTransaction()}.
   */
  protected void restartTransaction() {
    RequestLifeCycle.restartTransaction();
  }

  /**
   * DAEMON threads, named after the campaign: a scan in flight must never hold a
   * JVM shutdown back, and a thread dump of a slow platform must name what is
   * walking the repository.
   */
  private ThreadFactory threadFactory(String namePrefix) {
    AtomicInteger threadCount = new AtomicInteger();
    return runnable -> {
      Thread thread = new Thread(runnable, namePrefix + "-" + threadCount.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }

  /**
   * State shared by the coordinator, the readers and the writer of ONE scan run.
   * Package visible so the tests can drive the writer body directly.
   */
  static class ScanRun {

    /**
     * Sentinel closing the queue, posted by the coordinator once every reader
     * finished. Compared by IDENTITY, never by value.
     */
    static final ScanBatch         POISON_PILL        = new ScanBatch(0, null, null, 0, null, null);

    final long                     campaignId;

    final CleanupParams            params;

    final int                      batchSize;

    /** Denominator of the run: nodes counted over EVERY unit. */
    final long                     total;

    /** Numerator this run starts from: nodes already scanned before it. */
    final long                     processedAtStart;

    final long                     startTime          = System.currentTimeMillis();

    final BlockingQueue<ScanBatch> queue;

    /**
     * Per-unit cumulated scanned counts, seeded from the persisted ones so a
     * resumed unit checkpoints an ABSOLUTE count. Only the writer thread mutates
     * it; the coordinator seeds it before that thread even starts.
     */
    private final Map<Long, Long>  scannedCountByUnit = new ConcurrentHashMap<>();

    /** Campaign no longer DRY_RUN_RUNNING: published by the writer. */
    volatile boolean               aborted;

    /** Writer gave up: the readers MUST stop, nothing drains the queue. */
    volatile boolean               writerFailed;

    ScanRun(long campaignId, // NOSONAR
            CleanupParams params,
            int batchSize,
            long total,
            long processedAtStart,
            int readerCount,
            List<CleanupScanUnit> units) {
      this.campaignId = campaignId;
      this.params = params;
      this.batchSize = batchSize;
      this.total = total;
      this.processedAtStart = processedAtStart;
      // Twice the readers, floored: see MIN_QUEUE_CAPACITY on why the bound
      // itself is the point
      this.queue = new ArrayBlockingQueue<>(Math.max(readerCount * 2, MIN_QUEUE_CAPACITY));
      units.forEach(unit -> scannedCountByUnit.put(unit.getId(), unit.getScannedCount()));
    }

    boolean isStopped() {
      return aborted || writerFailed;
    }

    /**
     * @return the unit's cumulated scanned count, after adding the batch's own
     */
    long addScanned(ScanBatch batch) {
      return scannedCountByUnit.merge(batch.unitId, batch.scannedCount, Long::sum);
    }

  }

  /**
   * One envelope streamed from a reader to the writer. It carries the UNIT ID
   * because the writer checkpoints the row the batch came from — never 'the'
   * current unit, of which there is no such thing with several readers.
   */
  static class ScanBatch {

    final long                   unitId;

    final List<CleanupCandidate> candidates;

    final String                 lastScannedPath;

    final long                   scannedCount;

    /** Non-null on the LAST envelope of a unit: DONE or FAILED. */
    final CleanupScanUnitState   terminalState;

    final String                 failureReason;

    ScanBatch(long unitId,
              List<CleanupCandidate> candidates,
              String lastScannedPath,
              long scannedCount,
              CleanupScanUnitState terminalState,
              String failureReason) {
      this.unitId = unitId;
      this.candidates = candidates;
      this.lastScannedPath = lastScannedPath;
      this.scannedCount = scannedCount;
      this.terminalState = terminalState;
      this.failureReason = failureReason;
    }

    static ScanBatch progress(long unitId, List<CleanupCandidate> candidates, String lastScannedPath, long scannedCount) {
      return new ScanBatch(unitId, new ArrayList<>(candidates), lastScannedPath, scannedCount, null, null);
    }

    static ScanBatch terminal(long unitId, CleanupScanUnitState terminalState, String failureReason) {
      return new ScanBatch(unitId, null, null, 0, terminalState, failureReason);
    }

  }

}
