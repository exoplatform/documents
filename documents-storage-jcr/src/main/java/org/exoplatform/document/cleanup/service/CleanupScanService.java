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
import org.exoplatform.document.cleanup.model.CleanupNodeFailures;
import org.exoplatform.document.cleanup.model.CleanupScanUnit;
import org.exoplatform.document.cleanup.model.CleanupScanUnitProgress;
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
 * THE INVARIANT of the whole design is ONLY THE WRITER WRITES while readers are
 * alive. It was first stated as "only the writer TOUCHES JPA", which was WRONG,
 * and the mistake cost a Tribe-Dev dry run: a reader calls no cleanup storage
 * bean but {@link CleanupJcrStorage#scanRoot} — yet scanRoot resolves each
 * CANDIDATE's owner through Social ({@code getOwnerIdentityFromNodePath} ->
 * {@code SpaceService} / {@code IdentityManager}), and a cache MISS there runs a
 * JPA query. On a reader thread with no container transaction that query failed
 * with {@code Session/EntityManager is closed}; the per-node handler inside
 * scanRoot swallowed it as a WARN, the unit still finished DONE, and every
 * candidate whose space was not already cache-warm was silently dropped from the
 * report. "No call to OUR storage" is not "no JPA": a Service of another domain
 * is free to hit its own DAO, and this one does.
 * <p>
 * So each reader now opens its OWN read-only container transaction
 * ({@link #readUnitTransactional}), and the invariant that actually holds — the
 * one worth defending, because it is what keeps write ordering and the
 * checkpoint consistent — is about WRITES:
 * <ul>
 * <li>a reader never calls a cleanup storage bean other than
 * {@link CleanupJcrStorage#scanRoot}, and never WRITES anything anywhere: it
 * posts envelopes on the queue and reads volatile flags. Its transaction exists
 * only so the reads scanRoot makes THROUGH ANOTHER DOMAIN have a session to run
 * in</li>
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
 * TERMINATION — what is guaranteed, and by WHICH mechanism. Nothing here bounds
 * the DURATION of a scan: a dry-run over the target corpus spans hours to DAYS,
 * is interrupted and resumed, and no mechanism below may ever cut a run short
 * merely because it took long. What is bounded is BLOCKING WITHOUT PROGRESS, by
 * four independent mechanisms, each covering what the previous one cannot:
 * <ul>
 * <li>a reader blocked on a full queue re-checks the stop flags between two
 * bounded offers ({@link #QUEUE_TIMEOUT_MILLIS}), so it stops as soon as the
 * writer raised one — the fast path, and it covers only the failures that DO
 * raise a flag</li>
 * <li>a writer thread that VANISHED raises no flag, so the coordinator does not
 * INFER it from a timer: it observes {@code Thread#isAlive()} on the writer
 * thread itself, at every slice of the reader wait ({@link #awaitReaders}). A
 * dead writer stops the readers immediately, with nothing presumed</li>
 * <li>a writer that is ALIVE but permanently wedged (blocked on a database lock,
 * say) is caught by an INACTIVITY watchdog: nothing drained for
 * {@link #writerInactivityMillis} WHILE batches wait in the queue. A silence
 * bound and not a duration cap, and it can only fire while the writer has work
 * it is not taking — a healthy scan, however long, never reaches it</li>
 * <li>the writer is told to stop by TWO independent signals, deliberately: the
 * poison pill, delivered UNCONDITIONALLY (see {@link #postPoisonPill}), and
 * {@code ScanRun#isStopped()}, which the drain loop exits on even if no pill
 * ever lands. Neither depends on the other</li>
 * </ul>
 * Every one of them leaves the campaign DRY_RUN_RUNNING and resumable from its
 * unit checkpoints, lets the writer's frame unwind — so the container
 * transaction it opened is closed — and releases the campaign id (the
 * coordinator's {@code finally}) so the watchdog can relaunch the worker.
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
   * This bound guarantees NOTHING on its own: it only makes the stop flags
   * OBSERVABLE, and a reader whose writer died without raising one would retry
   * forever. What bounds that case is the coordinator watching the writer thread
   * itself — see the TERMINATION section of the class comment for which
   * mechanism guarantees what.
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
   * Bound of the LAST-RESORT wait on a writer that overran
   * {@link #WRITER_JOIN_MILLIS} and was then told to stop and interrupted. Short
   * on purpose: it is a courtesy wait on a thread already declared lost, and the
   * coordinator must not add another five minutes to a scan it is abandoning —
   * the daemon writer holds no JVM shutdown back, and the campaign is resumable
   * whether or not that thread has unwound by now.
   */
  private static final long            WRITER_STOP_JOIN_MILLIS    = 5000L;

  /**
   * INACTIVITY bound of the writer: how long the writer may drain NOTHING while
   * batches are waiting for it in the queue, past which it is presumed
   * permanently wedged and the readers are stopped. It is NOT a cap on the
   * duration of anything — measuring silence rather than elapsed time is the
   * whole point.
   * <p>
   * IT REPLACES A TOTAL-DURATION DEADLINE, WHICH WAS A DESIGN ERROR. Every unit
   * is submitted to the reader pool before {@link #awaitReaders} is called, so
   * that wait IS the scan: any wall-clock deadline on it was a cap on the whole
   * dry-run's duration. A dry-run over the target corpus spans hours to days, so
   * exceeding two hours is the EXPECTED case and the cap made the parallel scan
   * unable to ever finish — killed at the deadline, restarted by the watchdog,
   * killed again, with no error ever reported. A scan that keeps making progress
   * must NEVER be stopped, whatever its duration.
   * <p>
   * WHY A HEALTHY SCAN CANNOT REACH IT, on three independent counts:
   * <ol>
   * <li>it measures SILENCE: the marker is reset by every envelope the writer
   * drains ({@code ScanRun#markDrained()}), and a reader posts one every
   * {@code batchSize} SCANNED nodes — not per candidate — so batches arrive
   * throughout a walk, over a subtree holding no candidate at all included</li>
   * <li>it is only ever evaluated while the queue is NOT EMPTY, i.e. while the
   * writer has work in front of it and is not taking it. Readers that are merely
   * slow (a huge unit's query, a long resume fast-forward) leave the queue empty,
   * and an empty queue can never trip this bound</li>
   * <li>the value itself is DERIVED, not picked: {@code scanRoot} holds ONE JCR
   * session per unit walk, stamped with
   * {@code documents.cleanup.jcr.session.timeout} (one hour by default), so a
   * reader still inside a walk longer than that timeout is already doomed. Twice
   * that hour of total silence WITH work queued therefore means nothing is
   * moving, whatever the readers are doing — and it is overridable for the
   * deployments that raise the session timeout.</li>
   * </ol>
   */
  @Value("${documents.cleanup.scan.writer.inactivity.timeout:7200000}")
  private long                         writerInactivityMillis;

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
   * — that is what keeps a reader from ever WRITING (see the class comment; a
   * reader still READS through Social's owner resolution, hence its own
   * transaction) — and the claim is what SPENDS a walk attempt, so a unit
   * re-walked by the watchdog can never spend an unbounded number of them. The writer is started right after
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
      readerPool.execute(() -> readUnitTransactional(run, unit));
    }
    Thread writerThread = threadFactory("cleanup-scan-writer-" + campaignId).newThread(() -> runWriter(run));
    writerThread.start();

    awaitReaders(readerPool, run, writerThread);
    // Handed over only once EVERY reader is done, so it can never be drained
    // before the last batch of the last unit — and UNCONDITIONALLY, never
    // through post(), which answers false on a stopped run (see postPoisonPill)
    postPoisonPill(run);
    writerThread.join(WRITER_JOIN_MILLIS);
    if (!run.writerFinished) {
      // The pill could not reach a writer that is not draining. Raise the flag
      // its drain loop exits on WITHOUT any pill, and interrupt whatever it is
      // waiting on, so the frame unwinds and the container transaction it opened
      // is closed instead of being leaked with the thread
      run.writerFailed = true;
      writerThread.interrupt();
      writerThread.join(WRITER_STOP_JOIN_MILLIS);
      LOG.error("The writer thread of the cleanup campaign {} scan did not finish within {} ms: it was told to stop and"
          + " interrupted, and the scan stays resumable from its unit checkpoints.", campaignId, WRITER_JOIN_MILLIS);
    }
  }

  /**
   * Transactional entry point of a READER thread, self-invoked from the runnable
   * submitted by {@link #scanUnits} — same AspectJ self-call property as
   * {@link #drainQueueTransactional}.
   * <p>
   * A reader needs a container transaction even though it writes NOTHING, because
   * {@link CleanupJcrStorage#scanRoot} resolves every candidate's owner through
   * Social and a cache miss there is a JPA read (see the class comment for the
   * failure this cost us). The transaction is therefore READ-ONLY, which is what
   * makes {@link #restartReaderTransaction} safe to fail: a reader has no write
   * to lose at commit.
   *
   * @param run  state of the current scan run
   * @param unit the unit this reader walks
   */
  @ContainerTransactional
  public void readUnitTransactional(ScanRun run, CleanupScanUnit unit) {
    readUnit(run, unit);
  }

  /**
   * Reader body: walks ONE unit and posts everything it finds on the queue,
   * WRITING nothing (it reads, through Social, to resolve owners — see
   * {@link #readUnitTransactional}). The unit's own {@code LAST_SCANNED_PATH} is
   * the resume position (null when it was never started).
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
  protected void readUnit(ScanRun run, CleanupScanUnit unit) {
    long unitId = unit.getId();
    try {
      cleanupJcrStorage.scanRoot(unit.getUnitPath(),
                                 unit.getLastScannedPath(),
                                 run.batchSize,
                                 run.params,
                                 (candidates, lastScannedPath, scannedCount, nodeFailures) -> commitThenPost(run,
                                                                                                            ScanBatch.progress(unitId,
                                                                                                                               candidates,
                                                                                                                               lastScannedPath,
                                                                                                                               scannedCount,
                                                                                                                               nodeFailures)));
      if (!run.isStopped()) {
        commitThenPost(run, ScanBatch.terminal(unitId, CleanupScanUnitState.DONE, null));
      }
    } catch (Exception e) {
      LOG.warn("Error scanning the cleanup unit {} of campaign {}: this unit is marked failed, the other units go on",
               unit.getUnitPath(),
               run.campaignId,
               e);
      commitThenPost(run, ScanBatch.terminal(unitId, CleanupScanUnitState.FAILED, SCAN_UNIT_FAILED_REASON));
    }
  }

  /**
   * Commits the reader's read-only transaction and THEN posts — in that order,
   * which is the whole point.
   * <p>
   * {@link #post} BLOCKS on a full queue, for as long as the writer takes to
   * drain one: that is the backpressure working as designed, and it is measured
   * in minutes on a saturated writer. A reader must not sit through it holding an
   * open transaction, because behind that transaction is a connection from the
   * pool THE WHOLE PLATFORM shares — ten readers each pinning one for minutes is
   * the kind of starvation that takes interactive users down with it. Committing
   * first also bounds the Hibernate persistence context to ONE batch, so a reader
   * walking a million-node bucket cannot accumulate every Space and Identity it
   * ever resolved (the memory failure mode this queue exists to prevent).
   * <p>
   * A commit failure is logged and the batch posted anyway: the reader's
   * transaction is READ-ONLY, so there is nothing to lose at commit, and dropping
   * the batch would lose candidates the walk already found.
   */
  private boolean commitThenPost(ScanRun run, ScanBatch batch) {
    try {
      restartReaderTransaction();
    } catch (Exception e) {
      LOG.warn("Error committing the read-only transaction of a cleanup scan reader of campaign {}:"
          + " the batch it already walked is posted anyway", run.campaignId, e);
    }
    return post(run, batch);
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
   * <p>
   * The {@code finally} is load-bearing too, and it is what
   * {@code ScanRun#writerFinished} means: reaching it proves
   * {@link #drainQueueTransactional} RETURNED, hence that the container
   * transaction the woven aspect opened around it was closed by the same
   * unwinding. A drain loop that never returns leaks that transaction with the
   * thread — one per killed scan — which is why the loop must always have a way
   * out that does not depend on a poison pill reaching it, and why the
   * coordinator reads this flag rather than presuming the writer unwound.
   */
  private void runWriter(ScanRun run) {
    try {
      drainQueueTransactional(run);
    } catch (Throwable e) { // NOSONAR
      run.writerFailed = true;
      LOG.error("The writer of the cleanup campaign {} scan failed to start: the readers are stopped", run.campaignId, e);
    } finally {
      run.writerFinished = true;
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
   * It exits on FOUR signals, and the first two are deliberately redundant: the
   * poison pill, {@code isStopped()} — the run's own stop flags, so the writer
   * stops even if no pill ever lands on a full queue — the abort re-read, and a
   * failure. The pill alone was not enough: the coordinator raised
   * {@code writerFailed} to unblock the readers, {@code post} then refused to
   * enqueue the pill precisely BECAUSE the run was stopped, and this loop, which
   * consulted the pill and the abort but never the stop flags, saw a campaign
   * still legitimately DRY_RUN_RUNNING and looped forever — leaking the thread
   * and the container transaction its entry point had opened.
   * <p>
   * Every drained envelope also stamps {@code ScanRun#markDrained()}, the marker
   * the coordinator's inactivity watchdog reads ({@link #awaitReaders}): draining
   * IS the progress signal of this design, and a writer that stops stamping it
   * while batches wait for it is a wedged writer.
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
        if (batch == ScanRun.POISON_PILL) {
          return;
        } else if (run.isStopped()) {
          // SECOND, INDEPENDENT terminal signal: the readers are gone and
          // nothing more is expected on this queue, pill or no pill. Without it
          // the writer spun here for good — the campaign is deliberately still
          // DRY_RUN_RUNNING, so the abort re-read below answers false forever
          return;
        } else if (batch == null) {
          // Idle: re-check the abort flag rather than block forever on a pill
          // that a crashed coordinator may never post
          if (refreshAborted(run)) {
            return;
          }
          continue;
        } else if (refreshAborted(run)) {
          // Checked BEFORE anything is written, exactly where the sequential
          // worker checked it: an aborted campaign persists no further batch,
          // and no unit is recorded DONE either
          return;
        }
        // An envelope was taken off the queue: THE progress signal of this
        // design, and what the coordinator's inactivity watchdog resets on. It
        // is stamped for a terminal envelope too — draining one is progress just
        // as much as persisting a batch of candidates
        run.markDrained();
        if (batch.terminalState != null) {
          recordUnitOutcome(batch);
          restartTransaction();
          continue;
        }
        campaignStorage.saveCandidates(run.campaignId, batch.candidates);
        scanUnitStorage.updateUnitProgress(batch.unitId, batch.lastScannedPath, run.addScanned(batch), batch.nodeFailures);
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
      long skippedNodeCount = scanUnitStorage.sumEvalFailureCount(campaignId);
      if (skippedNodeCount > 0) {
        LOG.error("The dry-run of cleanup campaign {} could not EVALUATE {} node(s) it walked: those files are missing from the"
            + " report even though their subtrees finished. The report is flagged INCOMPLETE and the per-unit failures name the"
            + " cause. Whoever publishes it must know it does not cover every file it visited.", campaignId, skippedNodeCount);
      }
      if (settledFailedCount > 0 || skippedNodeCount > 0) {
        LOG.error("The dry-run of cleanup campaign {} is reported as simulated but INCOMPLETE: {} of its {} scan units could"
            + " not be walked in {} attempts, so {} of the {} counted nodes are MISSING from the report. Whoever publishes it"
            + " must know the report does not cover the whole tree.",
                  campaignId,
                  settledFailedCount,
                  unitCount,
                  MAX_SCAN_UNIT_ATTEMPTS,
                  total - walked,
                  total);
        campaign.setSummaryJson(buildScanSummaryJson(settledFailedCount, skippedNodeCount));
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
  private String buildScanSummaryJson(long settledFailedCount, long skippedNodeCount) {
    CleanupCampaignSummary summary = new CleanupCampaignSummary();
    summary.setScanIncomplete(true);
    summary.setFailedScanUnitCount(settledFailedCount);
    summary.setSkippedNodeCount(skippedNodeCount);
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
   * Per-unit breakdown of a campaign's dry run.
   * <p>
   * Deliberately NOT gated on anything, unlike {@link #getScanFailures}: that one
   * reports subtrees definitively missing from a finished report, so it must wait
   * for the verdict; this one exists to be read WHILE the scan runs, and gating it
   * would remove it exactly when it is needed. A scan whose node percentage sits
   * at 100% with the campaign still DRY_RUN_RUNNING is either re-walking a unit or
   * held open by one, and only these counts tell an administrator which.
   *
   * @param campaign campaign whose unit breakdown is wanted
   * @return the breakdown, zeroed for a campaign whose units were never planned
   */
  public CleanupScanUnitProgress getScanUnitProgress(CleanupCampaign campaign) {
    if (campaign == null) {
      return new CleanupScanUnitProgress(0, 0, 0, 0, 0, 0, 0, false, 0, List.of(), List.of());
    }
    return scanUnitStorage.getUnitProgress(campaign.getId(), MAX_SCAN_UNIT_ATTEMPTS);
  }

  /**
   * Waits for every reader to finish, in bounded slices so the stop flag stays
   * observable: a writer failure (or an abort) interrupts the readers, which may
   * otherwise sit inside a long blocking JCR call.
   * <p>
   * THIS WAIT IS THE SCAN — every unit was submitted to the pool before it — so
   * it must NOT be bounded by any duration: a dry-run spanning days is the
   * expected case, and a wall-clock deadline here was a cap on the whole dry-run
   * (see {@link #writerInactivityMillis}). What is watched instead is the
   * WRITER's liveness, on two signals, primary first:
   * <ol>
   * <li>the writer THREAD ITSELF, {@code Thread#isAlive()}. The failure this
   * whole check exists for is 'the writer vanished and nobody can tell', and that
   * is directly observable rather than something to presume from a timer: a dead
   * writer while the readers still wait stops them at once, with no timer
   * involved and nothing inferred. The flag path only covers the abnormal exits
   * somebody enumerated; a writer that vanishes some other way raises nothing,
   * the readers retry their offers forever, this loop's escape hatch is
   * unreachable, the coordinator's {@code finally} never runs — so the campaign
   * id is never released — and every later scan of every campaign queues behind
   * the hung task of a single-thread coordinator</li>
   * <li>an INACTIVITY watchdog for a writer that is ALIVE but permanently wedged,
   * which no liveness check can see: nothing drained for
   * {@link #writerInactivityMillis} WHILE the queue holds batches waiting for it.
   * Silence with work queued, never elapsed time — a scan that keeps making
   * progress resets the marker and one whose readers are merely slow leaves the
   * queue empty, so neither can ever trip it</li>
   * </ol>
   * <p>
   * A reader that ignores its interrupt outlives the coordinator, which does NOT
   * break the write invariant: a reader has no storage call to make but
   * {@code scanRoot} and WRITES nothing (its own transaction is read-only), and
   * {@code post} answers false as soon as the run is stopped. And nothing terminal is recorded, so the campaign stays
   * DRY_RUN_RUNNING and the watchdog re-launches the scan from the unit
   * checkpoints.
   */
  private void awaitReaders(ExecutorService readerPool, ScanRun run, Thread writerThread) throws InterruptedException {
    // No new task accepted; the submitted ones run to completion
    readerPool.shutdown();
    boolean interrupted = false;
    while (!readerPool.awaitTermination(READERS_AWAIT_MILLIS, TimeUnit.MILLISECONDS)) {
      if (run.isStopped()) {
        if (!interrupted) {
          readerPool.shutdownNow();
          interrupted = true;
          // ONE grace slice, so the ordinary cancel — every reader parked in
          // post(), which answers false the moment the run is stopped — still
          // ends tidily with its threads gone
          continue;
        }
        // BOUNDED, and it has to be: an interrupt does nothing to a
        // query.execute() in flight or to a long resume fast-forward, which is
        // the very reader isWriterWedged exists to protect. Waiting for it here
        // would hold the single-thread coordinator for as long as that walk
        // takes, with the campaign id still held — so every later scan of every
        // campaign would queue behind a cancelled one. Abandoning it is exactly
        // as safe as the class javadoc argues: a daemon thread, no storage call
        // but scanRoot, and post() already refuses it the queue
        LOG.warn("The readers of the cleanup campaign {} scan did not all stop within {} ms of their interrupt: they are"
            + " abandoned rather than waited for, the campaign stays DRY_RUN_RUNNING and the watchdog re-launches it from"
            + " the unit checkpoints.", run.campaignId, READERS_AWAIT_MILLIS);
        return;
      } else if (!writerThread.isAlive()) {
        // PRIMARY signal, and it is a fact and not a presumption: the only
        // consumer of the queue is gone, so the readers can only block on it
        stopReaders(readerPool, run);
        LOG.error("The writer thread of the cleanup campaign {} scan is DEAD while its readers are still walking: nothing"
            + " drains the queue anymore, so the readers are stopped at once and the campaign stays DRY_RUN_RUNNING —"
            + " the watchdog re-launches it from the unit checkpoints.", run.campaignId);
        return;
      } else if (run.queue.isEmpty()) {
        // NOTHING waits for the writer, so it cannot be late — and the progress
        // marker must be REFRESHED here, not merely left alone. It is compared
        // against WALL-CLOCK time, so a legitimate silence over an empty queue
        // (a huge unit's query, a long resume fast-forward — both walk for hours
        // WITHOUT emitting anything) would otherwise age the marker past the
        // bound while the writer sat blamelessly idle, and the FIRST batch to
        // arrive after that quiet stretch would find a NON-EMPTY queue against
        // an ALREADY EXPIRED marker: the scan would be declared wedged
        // microseconds after that batch was posted, on a repository that never
        // missed a beat. On a 14-year corpus that is not a corner case
        run.markNothingWaiting();
      } else if (isWriterWedged(run)) {
        // SECONDARY signal: alive, but it has not taken one envelope off a
        // NON-EMPTY queue for the whole inactivity bound
        stopReaders(readerPool, run);
        LOG.error("The writer of the cleanup campaign {} scan drained nothing for {} ms while {} batch(es) waited in its"
            + " queue: it is presumed permanently wedged, the readers are stopped and the campaign stays DRY_RUN_RUNNING —"
            + " the watchdog re-launches it from the unit checkpoints.",
                  run.campaignId,
                  writerInactivityMillis,
                  run.queue.size());
        return;
      }
    }
  }

  /**
   * Stops the readers of a run whose writer is gone: the flag is published FIRST
   * so {@code post} stops retrying rather than the readers being merely
   * interrupted — they must UNBLOCK, not just carry an interrupt flag into their
   * next offer.
   */
  private void stopReaders(ExecutorService readerPool, ScanRun run) {
    run.writerFailed = true;
    readerPool.shutdownNow();
  }

  /**
   * Call ONLY with a NON-EMPTY queue. Both callers guarantee it differently: the
   * watchdog tests emptiness first (and refreshes the progress marker when the
   * queue is empty, which is what makes the elapsed time below mean "how long
   * work has been WAITING" rather than "how long since the last drain"), and
   * {@link #postPoisonPill} only asks after an offer FAILED, so its queue is
   * full. The two readings coincide only while something stayed queued the whole
   * time; where they diverge, it is a healthy scan that pays.
   *
   * @return true when the writer has drained NOTHING for
   *         {@link #writerInactivityMillis} while batches were waiting for it
   */
  private boolean isWriterWedged(ScanRun run) {
    return System.currentTimeMillis() - run.lastDrainedTime >= writerInactivityMillis;
  }

  /**
   * Posts a READER's envelope, blocking while the queue is full — THAT is the
   * backpressure. Bounded offers rather than a plain {@code put}: a writer that
   * died must never leave a reader blocked forever, so the stop flags are
   * re-checked between two attempts. A writer that died WITHOUT raising one is
   * caught by the coordinator watching the writer thread instead (see
   * {@link #awaitReaders}) — this loop is the fast path, not the guarantee.
   * <p>
   * It answers FALSE on a stopped run, which is why the poison pill must never go
   * through here: the terminal signal is needed exactly when the run is stopped
   * (see {@link #postPoisonPill}).
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
   * Hands the writer its terminal signal, UNCONDITIONALLY. It must not go
   * through {@link #post}, which gives up as soon as {@code isStopped()} answers
   * true: the flag raised to unblock the readers was then the very flag that kept
   * the writer from ever being told to stop — the pill was never enqueued, and
   * the writer spun on an empty queue forever, holding the container transaction
   * its {@code @ContainerTransactional} entry point had opened. One leaked
   * transaction per killed scan.
   * <p>
   * The queue MAY BE FULL here, so the delivery must not block forever either.
   * Two cases, and they are not symmetric:
   * <ul>
   * <li>the run is STOPPED: the queued batches are abandoned whatever happens —
   * nothing will checkpoint them, and their nodes are simply re-walked on the
   * next resume — so the queue is CLEARED to make room. Dropping work that is
   * already forfeit is the cheap half of the trade</li>
   * <li>the run is NOT stopped: those batches are real work the writer is still
   * expected to persist, so they are never dropped. Every reader has finished by
   * now, so the queue can only SHRINK — one drained envelope is all the pill
   * needs — and the offers are therefore retried for as long as the writer keeps
   * DRAINING, on the very definition of wedged the watchdog uses
   * ({@link #isWriterWedged}) rather than on a deadline of its own. A healthy but
   * slow writer is never given up on; a wedged one is, and the drain loop's
   * {@code isStopped()} exit stops it instead (see {@link #drainQueue})</li>
   * </ul>
   *
   * @param run state of the current scan run
   */
  protected void postPoisonPill(ScanRun run) { // NOSONAR visible for tests
    try {
      while (!run.queue.offer(ScanRun.POISON_PILL, QUEUE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        if (run.isStopped()) {
          run.queue.clear();
        } else if (isWriterWedged(run)) {
          LOG.warn("The poison pill of the cleanup campaign {} scan could not be handed to its writer: its queue stayed"
              + " full and nothing was drained for {} ms. The writer is stopped by the run's stopped flag instead.",
                   run.campaignId,
                   writerInactivityMillis);
          return;
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Re-reads the campaign state and publishes the abort to the readers. Called
   * from the WRITER thread only: it is the one component allowed to WRITE
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
   * The READER's twin of {@link #restartTransaction}, kept as a separate seam on
   * purpose rather than reusing that one: the two commit on different threads for
   * different reasons — the writer to make its batch durable, a reader only to
   * let go of a pooled connection before it blocks (see {@link #commitThenPost})
   * — and one shared seam would make every assertion on the writer's commit count
   * silently depend on how many batches the readers happened to emit.
   */
  protected void restartReaderTransaction() {
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
    static final ScanBatch         POISON_PILL        = new ScanBatch(0, null, null, 0, null, null, null);

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

    /**
     * The writer's runnable reached its {@code finally}, so its transactional
     * entry point RETURNED and the container transaction it opened was closed.
     * Read by the coordinator, which must never presume that instead of a killed
     * scan leaking one open transaction per run — see {@link #runWriter}.
     */
    volatile boolean               writerFinished;

    /**
     * When the writer last took an envelope off the queue. It is the run's
     * PROGRESS marker — not a start time and not a deadline — and the only thing
     * the inactivity watchdog compares against, which is what keeps a scan
     * spanning DAYS alive as long as it keeps moving (see
     * {@link #writerInactivityMillis}). Seeded at construction so the first
     * window is measured from the run's own start.
     */
    volatile long                  lastDrainedTime    = System.currentTimeMillis();

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

    /** Stamps the progress marker the inactivity watchdog resets on. */
    void markDrained() {
      lastDrainedTime = System.currentTimeMillis();
    }

    /**
     * The SAME stamp, for the other reason it must be refreshed: the watchdog
     * observed an EMPTY queue, so nothing is waiting and the writer cannot be
     * late. Deliberately not {@code markDrained()} — the marker's whole bug was
     * about WHAT it measures, so the two reasons for moving it are named apart:
     * the writer took work off the queue, or there was no work to take.
     */
    void markNothingWaiting() {
      lastDrainedTime = System.currentTimeMillis();
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

    /** Nodes this batch could not evaluate — persisted with its checkpoint. */
    final CleanupNodeFailures    nodeFailures;

    ScanBatch(long unitId,
              List<CleanupCandidate> candidates,
              String lastScannedPath,
              long scannedCount,
              CleanupScanUnitState terminalState,
              String failureReason,
              CleanupNodeFailures nodeFailures) {
      this.unitId = unitId;
      this.candidates = candidates;
      this.lastScannedPath = lastScannedPath;
      this.scannedCount = scannedCount;
      this.terminalState = terminalState;
      this.failureReason = failureReason;
      this.nodeFailures = nodeFailures;
    }

    static ScanBatch progress(long unitId,
                              List<CleanupCandidate> candidates,
                              String lastScannedPath,
                              long scannedCount,
                              CleanupNodeFailures nodeFailures) {
      return new ScanBatch(unitId, new ArrayList<>(candidates), lastScannedPath, scannedCount, null, null, nodeFailures);
    }

    static ScanBatch terminal(long unitId, CleanupScanUnitState terminalState, String failureReason) {
      return new ScanBatch(unitId, null, null, 0, terminalState, failureReason, null);
    }

  }

}
