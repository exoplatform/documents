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
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.document.cleanup.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.constant.CleanupScanUnitState;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupScanUnit;
import org.exoplatform.document.cleanup.service.CleanupScanService.ScanRun;
import org.exoplatform.document.cleanup.storage.CleanupCampaignStorage;
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage;
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage.ScanBatchConsumer;
import org.exoplatform.document.cleanup.storage.CleanupScanUnitStorage;
import org.exoplatform.document.cleanup.websocket.CleanupWebSocketService;
import org.exoplatform.document.cleanup.websocket.CleanupWsMessage;

import io.meeds.common.ContainerTransactional;

/**
 * Parallel scan worker tests, pinning the four phases (plan / estimate in
 * parallel / readers-plus-one-writer / complete) and — above everything else —
 * the invariant the whole design rests on: ONLY THE WRITER THREAD TOUCHES JPA
 * while readers are alive. Every storage mock records the thread it was called
 * from, so a reader gaining a write is a test failure and not a review remark.
 * <p>
 * The worker body is driven SYNCHRONOUSLY: the service's real single-thread
 * executor is replaced by a mock and {@link CleanupScanService#scan(long)} is
 * invoked directly, which returns only once every reader terminated and the
 * writer was joined — so the verifications below are deterministic despite the
 * threads. Going through the scheduled {@code scanTransactional} instead would
 * run the {@code @ContainerTransactional} aspect, which boots a real
 * PortalContainer in a plain JUnit run; the same reason makes the writer's own
 * transactional entry point stubbed out on a spy, delegating to the un-annotated
 * {@link CleanupScanService#drainQueue(ScanRun)}. Both annotations are pinned by
 * reflection at the end of the class.
 * <p>
 * EVERY wait in this class is BOUNDED ({@code await(timeout, unit)}, bounded
 * joins) and asserted on: a producer/consumer bug must fail a test, never hang a
 * build.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CleanupScanServiceTest {

  private static final long        CAMPAIGN_ID       = 12L;

  private static final int         BATCH_SIZE        = 2;

  private static final long        AWAIT_TIMEOUT_MS  = 10000L;

  private static final long        STAY_BLOCKED_MS   = 500L;

  private static final int         READER_MAX_LOOPS  = 50;

  private static final String      USERS_UNIT        = "/Users/j___";                     // NOSONAR

  private static final String      SPACES_UNIT       = "/Groups/spaces/marketing";        // NOSONAR

  private static final String      TRASH_UNIT        = "/Trash";                          // NOSONAR

  private static final String      PATH_A            = "/Users/j___/john/Private/a.pdf";  // NOSONAR

  private static final String      PATH_B            = "/Users/j___/john/Private/b.pdf";  // NOSONAR

  private static final String      PATH_C            = "/Users/j___/john/Private/c.pdf";  // NOSONAR

  private static final String      SPACES_PATH       = "/Groups/spaces/marketing/x.pdf";  // NOSONAR

  private static final String      READER_THREAD     = "cleanup-scan-reader";

  private static final String      WRITER_THREAD     = "cleanup-scan-writer";

  @Mock
  private CleanupCampaignStorage   campaignStorage;

  @Mock
  private CleanupCampaignLifecycle campaignLifecycle;

  @Mock
  private CleanupJcrStorage        cleanupJcrStorage;

  @Mock
  private CleanupScanUnitStorage   scanUnitStorage;

  @Mock
  private CleanupSettingService    settingService;

  @Mock
  private CleanupWebSocketService  webSocketService;

  @InjectMocks
  private CleanupScanService       scanService;

  @Mock
  private ExecutorService          workerExecutor;

  private CleanupCampaign          campaign;

  /** Thread names every storage write was issued from, in call order. */
  private final List<String>       writingThreads    = Collections.synchronizedList(new ArrayList<>());

  /** Thread name the JCR walk of a unit ran on. */
  private final AtomicReference<String> readingThread = new AtomicReference<>();

  @BeforeEach
  void setUp() throws ReflectiveOperationException {
    // Replace the service's REAL single-thread executor with a mock: the tests
    // drive the worker body themselves (see the class comment), so no background
    // thread — and no container-booting transactional aspect — is involved
    Field executorField = CleanupScanService.class.getDeclaredField("executorService");
    executorField.setAccessible(true); // NOSONAR test wiring
    executorField.set(scanService, workerExecutor); // NOSONAR
    // Spied AFTER the field wiring, which spy() copies over: the writer's
    // transactional entry point is redirected to its un-annotated body, and the
    // per-batch commit neutralized — neither can run without a PortalContainer
    scanService = spy(scanService);
    doNothing().when(scanService).restartTransaction();
    doAnswer(invocation -> {
      scanService.drainQueue(invocation.getArgument(0));
      return null;
    }).when(scanService).drainQueueTransactional(any());

    campaign = new CleanupCampaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setName("Scan me");
    campaign.setState(CleanupCampaignState.DRY_RUN_RUNNING);
    campaign.setParams(new CleanupParams(6, 1024L, 7, 5, List.of(), BATCH_SIZE));
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(settingService.getBatchSize()).thenReturn(BATCH_SIZE);
    // ONE reader by default: a deterministic pool size keeps the assertions on
    // call ORDER meaningful; the parallelism itself is pinned by the tests that
    // ask for 2 readers explicitly
    when(settingService.getScanThreads()).thenReturn(1);
    // The lifecycle bean owns the state machine: emulate the state change so
    // the worker's re-reads observe it
    when(campaignLifecycle.transition(any(CleanupCampaign.class), any(CleanupCampaignState.class))).thenAnswer(invocation -> {
      CleanupCampaign transitioned = invocation.getArgument(0);
      transitioned.setState(invocation.getArgument(1));
      return transitioned;
    });
    recordWritingThreads();
  }

  @Test
  void startScanRejectsUnknownCampaign() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> scanService.startScan(CAMPAIGN_ID));
  }

  @Test
  void scanPlansUnitsEstimatesInParallelThenPersistsThroughTheWriterAndCompletes() {
    // Two units: /Users/j___ holds 3 files (2 batches of BATCH_SIZE=2), /Trash
    // is empty
    planned(TRASH_UNIT, USERS_UNIT);
    CleanupScanUnit trashUnit = unit(1L, TRASH_UNIT);
    CleanupScanUnit usersUnit = unit(2L, USERS_UNIT);
    unitsToProcess(trashUnit, usersUnit);
    when(cleanupJcrStorage.countFiles(TRASH_UNIT)).thenReturn(0L);
    when(cleanupJcrStorage.countFiles(USERS_UNIT)).thenReturn(3L);
    unitAggregates(3L, 0L);
    unitOutcomes(2L, 2L, 0L);
    emitBatches(USERS_UNIT, batch(PATH_B, 2, candidate("uuid-0", PATH_A)), batch(PATH_C, 1, candidate("uuid-2", PATH_C)));

    scanService.scan(CAMPAIGN_ID);

    // (a) PLAN — the enumerated units are planned as they came, verbatim
    verify(scanUnitStorage).planUnits(CAMPAIGN_ID, List.of(TRASH_UNIT, USERS_UNIT));
    // (b) ESTIMATE — one count per unit, each persisted on ITS unit row, and the
    // campaign denominator is their SUM as the database computes it
    verify(cleanupJcrStorage).countFiles(TRASH_UNIT);
    verify(cleanupJcrStorage).countFiles(USERS_UNIT);
    verify(scanUnitStorage).updateUnitTotal(1L, 0L);
    verify(scanUnitStorage).updateUnitTotal(2L, 3L);
    verify(campaignStorage).updateProgress(CAMPAIGN_ID, 3L, 0L, 0L, null, 0L);
    // (c) SCAN — every unit is marked RUNNING by the coordinator before a reader
    // may touch it, and a fresh unit carries no resume path
    verify(scanUnitStorage).updateUnitState(1L, CleanupScanUnitState.RUNNING);
    verify(scanUnitStorage).updateUnitState(2L, CleanupScanUnitState.RUNNING);
    verify(cleanupJcrStorage).scanRoot(eq(USERS_UNIT), isNull(), eq(BATCH_SIZE), any(), any());
    verify(cleanupJcrStorage).scanRoot(eq(TRASH_UNIT), isNull(), eq(BATCH_SIZE), any(), any());
    // Streamed candidates are persisted per batch, and the per-unit checkpoint
    // carries the ABSOLUTE scanned count of THAT unit
    verify(campaignStorage).saveCandidates(eq(CAMPAIGN_ID),
                                          argThat(candidates -> candidates.size() == 1
                                                                && "uuid-0".equals(candidates.get(0).getNodeUuid())));
    verify(campaignStorage).saveCandidates(eq(CAMPAIGN_ID),
                                          argThat(candidates -> "uuid-2".equals(candidates.get(0).getNodeUuid())));
    verify(scanUnitStorage).updateUnitProgress(2L, PATH_B, 2L);
    verify(scanUnitStorage).updateUnitProgress(2L, PATH_C, 3L);
    // The writer records the outcome of the unit each reader finished
    verify(scanUnitStorage).updateUnitState(1L, CleanupScanUnitState.DONE);
    verify(scanUnitStorage).updateUnitState(2L, CleanupScanUnitState.DONE);
    // Aggregate progress + ETA per batch. The ETA is asserted EXACTLY, not with
    // anyLong(): 2 of 3 nodes are done, so the last one is estimated at half the
    // elapsed time, i.e. 0 s for any run under 2 s. The arithmetic itself is
    // pinned by CleanupEtaUtilTest; what this pins is that the value reaching the
    // storage (and the push below) really is that computation's result
    verify(campaignStorage).updateProgress(CAMPAIGN_ID, 3L, 2L, 0L, null, 0L);
    verify(campaignStorage).updateProgress(CAMPAIGN_ID, 3L, 3L, 0L, null, 0L);
    // Each batch commits on its own, like the purge worker
    verify(scanService, times(4)).restartTransaction();
    // Administrators are notified per batch with counters only
    ArgumentCaptor<CleanupWsMessage> messageCaptor = ArgumentCaptor.forClass(CleanupWsMessage.class);
    verify(webSocketService, times(2)).sendToAdministrators(messageCaptor.capture());
    assertEquals(List.of(2L, 3L),
                 messageCaptor.getAllValues().stream().map(CleanupWsMessage::getProcessed).toList(),
                 "One push per batch, carrying that batch's cumulated count");
    assertEquals(CleanupWsMessage.PROGRESS_EVENT, messageCaptor.getValue().getWsEventName());
    assertEquals(3L, messageCaptor.getValue().getTotal());
    assertEquals(0L, messageCaptor.getValue().getEtaSeconds());
    // (d) COMPLETE — terminal counters frozen before the final transition
    verify(campaignLifecycle).transition(campaign, CleanupCampaignState.SIMULATED);
    assertEquals(3, campaign.getTotalCount());
    assertEquals(3, campaign.getProcessedCount());
    assertEquals(0, campaign.getEtaSeconds());
    assertEquals(CleanupCampaignState.SIMULATED, campaign.getState());
  }

  @Test
  void onlyTheWriterThreadEverWritesToTheDatabase() {
    planned(USERS_UNIT);
    unitsToProcess(unit(1L, USERS_UNIT));
    when(cleanupJcrStorage.countFiles(USERS_UNIT)).thenReturn(2L);
    unitAggregates(2L, 0L);
    unitOutcomes(1L, 1L, 0L);
    emitBatches(USERS_UNIT, batch(PATH_B, 2, candidate("uuid-0", PATH_A)));

    scanService.scan(CAMPAIGN_ID);

    // The walk really did run on a reader thread — without this the assertion
    // below would pass on a scan that never parallelised anything
    assertNotNull(readingThread.get(), "The unit walk must have run");
    assertTrue(readingThread.get().startsWith(READER_THREAD),
               "The unit walk must run on a reader thread, was: " + readingThread.get());
    // THE invariant: not one write escaped to a reader thread. The writes issued
    // while readers are alive all come from the writer thread; the coordinator's
    // own (planning, per-unit totals, RUNNING, terminal transition) come from the
    // test thread, which is the coordinator here
    List<String> readerWrites = writingThreads.stream().filter(name -> name.startsWith(READER_THREAD)).toList();
    assertTrue(readerWrites.isEmpty(), "No reader thread may write to the database, found: " + readerWrites);
    assertTrue(writingThreads.stream().anyMatch(name -> name.startsWith(WRITER_THREAD)),
               "The streamed batches must be written by the writer thread, threads were: " + writingThreads);
  }

  @Test
  void legacyCheckpointCampaignIsReplannedAndItsCheckpointIgnored() {
    // Interrupted under the OLD sequential scheme: a campaign-level checkpoint,
    // and no unit row at all
    campaign.setCheckpointPath(SPACES_PATH);
    campaign.setCheckpointOffset(7);
    planned(SPACES_UNIT);
    unitsToProcess(unit(1L, SPACES_UNIT));
    when(cleanupJcrStorage.countFiles(SPACES_UNIT)).thenReturn(1L);
    unitAggregates(1L, 0L);
    // No unit row yet — THE legacy shape: a campaign-level checkpoint and
    // nothing partitioned. Planned first, counted as one unit afterwards
    when(scanUnitStorage.countUnits(CAMPAIGN_ID)).thenReturn(0L, 1L);
    unitStates(1L, 0L);
    emitBatches(SPACES_UNIT, batch(SPACES_PATH, 1));

    scanService.scan(CAMPAIGN_ID);

    verify(scanUnitStorage, times(2)).countUnits(CAMPAIGN_ID);
    verify(scanUnitStorage).planUnits(CAMPAIGN_ID, List.of(SPACES_UNIT));
    // The stale campaign checkpoint is CLEARED by the progress write: nothing
    // downstream may position a walk from it anymore
    verify(campaignStorage).updateProgress(CAMPAIGN_ID, 1L, 0L, 0L, null, 0L);
    // The legacy checkpoint NEVER positions the new walk: the freshly planned
    // unit carries no resume path, so the tree is re-walked (saveCandidates
    // de-duplicates, so the replay costs work and duplicates nothing)
    verify(cleanupJcrStorage).scanRoot(eq(SPACES_UNIT), isNull(), eq(BATCH_SIZE), any(), any());
    verify(cleanupJcrStorage, never()).scanRoot(anyString(), eq(SPACES_PATH), anyInt(), any(), any());
    verify(campaignLifecycle).transition(campaign, CleanupCampaignState.SIMULATED);
  }

  @Test
  void estimationSkipsTheUnitsAlreadyCountedAndSumsTheRestIntoTheCampaignTotal() {
    planned(TRASH_UNIT, USERS_UNIT);
    CleanupScanUnit countedUnit = unit(1L, TRASH_UNIT);
    // Counted by a previous run: never counted again
    countedUnit.setTotalCount(7);
    unitsToProcess(countedUnit, unit(2L, USERS_UNIT));
    when(cleanupJcrStorage.countFiles(USERS_UNIT)).thenReturn(5L);
    unitAggregates(12L, 0L);
    unitOutcomes(2L, 2L, 0L);

    scanService.scan(CAMPAIGN_ID);

    verify(cleanupJcrStorage, never()).countFiles(TRASH_UNIT);
    verify(scanUnitStorage, never()).updateUnitTotal(eq(1L), anyLong());
    verify(cleanupJcrStorage).countFiles(USERS_UNIT);
    verify(scanUnitStorage).updateUnitTotal(2L, 5L);
    // The campaign denominator is the SUM over the units, the already-counted
    // one included — that is what keeps the ETA comparable with the campaigns
    // scanned by the sequential worker
    verify(campaignStorage).updateProgress(CAMPAIGN_ID, 12L, 0L, 0L, null, 0L);
    assertEquals(12, campaign.getTotalCount());
  }

  @Test
  void aUnitResumesFromItsOwnPersistedPathAndKeepsCountingFromIt() {
    CleanupScanUnit resumedUnit = unit(1L, USERS_UNIT);
    resumedUnit.setLastScannedPath(PATH_A);
    resumedUnit.setScannedCount(4);
    resumedUnit.setTotalCount(6);
    planned(USERS_UNIT);
    unitsToProcess(resumedUnit);
    unitAggregates(6L, 4L);
    unitOutcomes(1L, 1L, 0L);
    emitBatches(USERS_UNIT, batch(PATH_C, 2, candidate("uuid-2", PATH_C)));

    scanService.scan(CAMPAIGN_ID);

    // Positioned by the unit's OWN path checkpoint, never by a campaign one
    verify(cleanupJcrStorage).scanRoot(eq(USERS_UNIT), eq(PATH_A), eq(BATCH_SIZE), any(), any());
    // The persisted scanned count is ABSOLUTE: it continues from 4, it does not
    // restart at the batch size
    verify(scanUnitStorage).updateUnitProgress(1L, PATH_C, 6L);
    // The numerator this run starts from is the persisted sum, so the aggregate
    // progress stays continuous across the interruption
    verify(campaignStorage).updateProgress(CAMPAIGN_ID, 6L, 6L, 0L, null, 0L);
  }

  @Test
  void everyBatchIsCheckpointedOnTheUnitItCameFrom() {
    planned(SPACES_UNIT, USERS_UNIT);
    unitsToProcess(unit(1L, SPACES_UNIT), unit(2L, USERS_UNIT));
    when(cleanupJcrStorage.countFiles(anyString())).thenReturn(1L);
    unitAggregates(2L, 0L);
    unitOutcomes(2L, 2L, 0L);
    emitBatches(SPACES_UNIT, batch(SPACES_PATH, 1, candidate("uuid-space", SPACES_PATH)));
    emitBatches(USERS_UNIT, batch(PATH_A, 1, candidate("uuid-user", PATH_A)));

    scanService.scan(CAMPAIGN_ID);

    // The envelope carries its unit id precisely so the writer cannot checkpoint
    // 'the current unit' — with several readers there is no such thing
    verify(scanUnitStorage).updateUnitProgress(1L, SPACES_PATH, 1L);
    verify(scanUnitStorage).updateUnitProgress(2L, PATH_A, 1L);
    verify(scanUnitStorage, never()).updateUnitProgress(1L, PATH_A, 1L);
    verify(scanUnitStorage, never()).updateUnitProgress(2L, SPACES_PATH, 1L);
  }

  @Test
  void aFullQueueBlocksTheReaderInsteadOfGrowing() throws InterruptedException {
    // ONE reader, so a queue capacity of max(1 * 2, 4) = 4. The writer is held
    // inside its first save, so it can absorb one envelope and no more: the
    // reader gets 4 (queued) + 1 (in flight) out, and the 6th MUST block
    planned(USERS_UNIT);
    unitsToProcess(unit(1L, USERS_UNIT));
    when(cleanupJcrStorage.countFiles(USERS_UNIT)).thenReturn(12L);
    unitAggregates(12L, 0L);
    unitOutcomes(1L, 1L, 0L);
    CountDownLatch writerGate = new CountDownLatch(1);
    CountDownLatch writerReached = new CountDownLatch(1);
    CountDownLatch sixthEmitted = new CountDownLatch(6);
    doAnswer(invocation -> {
      writerReached.countDown();
      assertTrue(writerGate.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS), "The writer gate must be opened by the test");
      return null;
    }).when(campaignStorage).saveCandidates(anyLong(), anyList());
    doAnswer(invocation -> {
      ScanBatchConsumer batchConsumer = invocation.getArgument(4);
      for (int i = 0; i < 6; i++) {
        if (!batchConsumer.onBatch(List.of(candidate("uuid-" + i, PATH_A)), PATH_A, 2)) {
          break;
        }
        sixthEmitted.countDown();
      }
      return null;
    }).when(cleanupJcrStorage).scanRoot(eq(USERS_UNIT), isNull(), anyInt(), any(), any());

    Thread coordinator = coordinatorThread();
    coordinator.start();

    assertTrue(writerReached.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS), "The writer must have reached its first save");
    // BOUNDED negative wait: with the writer held, the reader cannot get its 6th
    // envelope out — the bound is what makes an unbounded queue fail this test
    assertFalse(sixthEmitted.await(STAY_BLOCKED_MS, TimeUnit.MILLISECONDS),
                "A full queue must BLOCK the reader instead of growing");
    assertTrue(sixthEmitted.getCount() <= 2,
               "The reader must have filled the queue up to its capacity, missing: " + sixthEmitted.getCount());
    writerGate.countDown();

    assertTrue(sixthEmitted.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS), "The reader must resume once the writer drains");
    coordinator.join(AWAIT_TIMEOUT_MS);
    assertFalse(coordinator.isAlive(), "The coordinator must terminate once the readers and the writer are done");
    verify(campaignStorage, times(6)).saveCandidates(anyLong(), anyList());
  }

  @Test
  void aReaderFailureMarksOnlyItsOwnUnitFailedAndTheOtherUnitsGoOn() {
    planned(SPACES_UNIT, USERS_UNIT);
    unitsToProcess(unit(1L, SPACES_UNIT), unit(2L, USERS_UNIT));
    when(cleanupJcrStorage.countFiles(anyString())).thenReturn(1L);
    unitAggregates(2L, 1L);
    unitOutcomes(2L, 1L, 1L);
    doThrow(new IllegalStateException("JCR failure")).when(cleanupJcrStorage)
                                                     .scanRoot(eq(SPACES_UNIT), isNull(), anyInt(), any(), any());
    emitBatches(USERS_UNIT, batch(PATH_A, 1, candidate("uuid-user", PATH_A)));

    scanService.scan(CAMPAIGN_ID);

    // The failed unit is recorded FAILED — by the WRITER, from the envelope its
    // reader posted — with a localizable code and nothing else
    verify(scanUnitStorage).updateUnitFailure(1L, "cleanup.scanUnitFailed");
    verify(scanUnitStorage, never()).updateUnitState(1L, CleanupScanUnitState.DONE);
    // One unreadable subtree must not deny the whole simulation: the other unit
    // completes and the campaign is still reported SIMULATED
    verify(scanUnitStorage).updateUnitState(2L, CleanupScanUnitState.DONE);
    verify(campaignStorage).saveCandidates(eq(CAMPAIGN_ID), anyList());
    verify(campaignLifecycle).transition(campaign, CleanupCampaignState.SIMULATED);
  }

  @Test
  void aWriterFailureStopsTheReadersAndLeavesTheCampaignResumable() throws InterruptedException {
    planned(USERS_UNIT);
    unitsToProcess(unit(1L, USERS_UNIT));
    when(cleanupJcrStorage.countFiles(USERS_UNIT)).thenReturn(100L);
    unitAggregates(100L, 0L);
    unitOutcomes(1L, 0L, 0L);
    doThrow(new IllegalStateException("Database down")).when(campaignStorage).saveCandidates(anyLong(), anyList());
    AtomicInteger emitted = boundedEmitter(USERS_UNIT);

    // Driven from a DAEMON thread joined with a BOUND: a failed flag that never
    // reaches the readers leaves them blocked on a queue nothing drains, and the
    // coordinator waiting on them — that must FAIL this test, never hang it
    Thread coordinator = coordinatorThread();
    coordinator.start();
    coordinator.join(AWAIT_TIMEOUT_MS);

    assertFalse(coordinator.isAlive(), "A writer failure must stop the readers instead of leaving the coordinator waiting");
    // The readers observe the failed flag and stop: they would otherwise block
    // forever on a queue nothing drains anymore
    assertTrue(emitted.get() < READER_MAX_LOOPS,
               "The reader must have been stopped by the writer failure, it emitted " + emitted.get() + " batches");
    // Nothing terminal is recorded, so the campaign stays DRY_RUN_RUNNING and
    // the watchdog resumes it from the unit checkpoints
    verify(scanUnitStorage, never()).updateUnitState(anyLong(), eq(CleanupScanUnitState.DONE));
    verify(campaignLifecycle, never()).transition(any(), eq(CleanupCampaignState.SIMULATED));
    assertEquals(CleanupCampaignState.DRY_RUN_RUNNING, campaign.getState());
  }

  @Test
  void abortMidRunStopsEveryReaderAndSkipsTheSimulation() {
    planned(USERS_UNIT);
    unitsToProcess(unit(1L, USERS_UNIT));
    when(cleanupJcrStorage.countFiles(USERS_UNIT)).thenReturn(100L);
    unitAggregates(100L, 0L);
    unitOutcomes(1L, 0L, 0L);
    // The campaign is cancelled while the first batch is being pushed: the
    // writer re-reads the state before writing anything else
    doAnswer(invocation -> {
      campaign.setState(CleanupCampaignState.CANCELLED);
      return null;
    }).when(webSocketService).sendToAdministrators(any());
    AtomicInteger emitted = boundedEmitter(USERS_UNIT);

    scanService.scan(CAMPAIGN_ID);

    assertTrue(emitted.get() < READER_MAX_LOOPS,
               "Every reader must poll the abort flag, the reader emitted " + emitted.get() + " batches");
    // Exactly ONE batch was persisted: the abort is checked BEFORE the write, so
    // no further candidate row lands on a cancelled campaign
    verify(campaignStorage, times(1)).saveCandidates(eq(CAMPAIGN_ID), anyList());
    verify(scanUnitStorage, never()).updateUnitState(anyLong(), eq(CleanupScanUnitState.DONE));
    verify(campaignLifecycle, never()).transition(any(), eq(CleanupCampaignState.SIMULATED));
  }

  @Test
  void everyUnitFailedIsNeverReportedAsASuccessfulSimulation() {
    planned(SPACES_UNIT, USERS_UNIT);
    unitsToProcess(unit(1L, SPACES_UNIT), unit(2L, USERS_UNIT));
    when(cleanupJcrStorage.countFiles(anyString())).thenReturn(1L);
    unitAggregates(2L, 0L);
    // Both units ended FAILED
    unitOutcomes(2L, 0L, 2L);
    doThrow(new IllegalStateException("JCR failure")).when(cleanupJcrStorage)
                                                     .scanRoot(anyString(), isNull(), anyInt(), any(), any());

    scanService.scan(CAMPAIGN_ID);

    verify(scanUnitStorage).updateUnitFailure(1L, "cleanup.scanUnitFailed");
    verify(scanUnitStorage).updateUnitFailure(2L, "cleanup.scanUnitFailed");
    // A simulation over a tree NOTHING could be read from is not a simulation:
    // the campaign is left as it is, resumable from its unit checkpoints
    verify(campaignLifecycle, never()).transition(any(), eq(CleanupCampaignState.SIMULATED));
    assertEquals(CleanupCampaignState.DRY_RUN_RUNNING, campaign.getState());
  }

  @Test
  void scanIsNoOpWhileTheWorkerIsAlive() throws ReflectiveOperationException {
    runningCampaigns().add(CAMPAIGN_ID);

    scanService.scan(CAMPAIGN_ID);

    // The double-start guard makes the watchdog-triggered worker a no-op while
    // the campaign id is in the running set: the scan never even plans
    verify(scanUnitStorage, never()).planUnits(anyLong(), anyList());
    verify(cleanupJcrStorage, never()).countFiles(anyString());
  }

  @Test
  void scanFailureLeavesTheCampaignResumableAndTheWorkerRestartable() {
    // The enumeration itself fails: no unit, no scan, and the running-campaign
    // id is still released so the watchdog can relaunch the worker
    when(cleanupJcrStorage.listScanUnits()).thenThrow(new IllegalStateException("JCR failure"));

    scanService.scan(CAMPAIGN_ID);

    verify(campaignLifecycle, never()).transition(any(), eq(CleanupCampaignState.SIMULATED));
    assertEquals(CleanupCampaignState.DRY_RUN_RUNNING, campaign.getState());
    scanService.scan(CAMPAIGN_ID);
    verify(cleanupJcrStorage, times(2)).listScanUnits();
  }

  @Test
  void startScanOnlyHandsTheWorkerToTheExecutorNeverRunsItInline() throws ObjectNotFoundException {
    scanService.startScan(CAMPAIGN_ID);

    // The endpoint answers 202 and follows up on CometD: the walk must NOT run
    // on the caller's thread
    verify(workerExecutor).execute(any());
    verify(cleanupJcrStorage, never()).listScanUnits();
  }

  @Test
  void startScanSchedulesTheTransactionalWorkerEntryPoint() throws ObjectNotFoundException {
    // Verifying execute(any()) alone never RUNS the scheduled Runnable, so
    // scheduling scan() instead of scanTransactional() — dropping the whole
    // coordinator out of its container transaction — would go unnoticed. Pinned
    // by running the captured Runnable against a spy whose entry point is
    // stubbed out, so the woven aspect never boots a container
    doNothing().when(scanService).scanTransactional(anyLong());

    scanService.startScan(CAMPAIGN_ID);

    ArgumentCaptor<Runnable> workerCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(workerExecutor).execute(workerCaptor.capture());
    workerCaptor.getValue().run();
    verify(scanService).scanTransactional(CAMPAIGN_ID);
  }

  @Test
  void theScheduledEntryPointsRunInAContainerTransaction() throws NoSuchMethodException {
    // The tests drive scan() and drainQueue() directly, so nothing else would
    // notice either annotation disappearing — and the candidate rows of a whole
    // batch would stop being committed under a container transaction
    assertNotNull(CleanupScanService.class.getMethod("scanTransactional", long.class)
                                         .getAnnotation(ContainerTransactional.class),
                  "scanTransactional must stay annotated @ContainerTransactional");
    assertNotNull(CleanupScanService.class.getMethod("drainQueueTransactional", ScanRun.class)
                                         .getAnnotation(ContainerTransactional.class),
                  "drainQueueTransactional must stay annotated @ContainerTransactional");
  }

  /**
   * A reader emitting batches until it is told to stop, HARD-BOUNDED at
   * {@link #READER_MAX_LOOPS} iterations: a stop flag that never reaches the
   * reader must fail the assertion on the returned counter, never spin.
   */
  private AtomicInteger boundedEmitter(String unitPath) {
    AtomicInteger emitted = new AtomicInteger();
    doAnswer(invocation -> {
      ScanBatchConsumer batchConsumer = invocation.getArgument(4);
      while (emitted.get() < READER_MAX_LOOPS
             && batchConsumer.onBatch(List.of(candidate("uuid-" + emitted.get(), PATH_A)), PATH_A, 1)) {
        emitted.incrementAndGet();
      }
      return null;
    }).when(cleanupJcrStorage).scanRoot(eq(unitPath), isNull(), anyInt(), any(), any());
    return emitted;
  }

  private Thread coordinatorThread() {
    // DAEMON, and always joined with a bound by the test: a coordinator left
    // behind must not hold the JVM — nor the build — back
    Thread coordinator = new Thread(() -> scanService.scan(CAMPAIGN_ID), "test-scan-coordinator");
    coordinator.setDaemon(true);
    return coordinator;
  }

  /** Records the thread every storage WRITE was issued from. */
  private void recordWritingThreads() {
    doAnswer(this::recordWritingThread).when(campaignStorage).saveCandidates(anyLong(), anyList());
    doAnswer(this::recordWritingThread).when(campaignStorage)
                                       .updateProgress(anyLong(), anyLong(), anyLong(), anyLong(), any(), anyLong());
    doAnswer(this::recordWritingThread).when(scanUnitStorage).planUnits(anyLong(), anyList());
    doAnswer(this::recordWritingThread).when(scanUnitStorage).updateUnitProgress(anyLong(), any(), anyLong());
    doAnswer(this::recordWritingThread).when(scanUnitStorage).updateUnitTotal(anyLong(), anyLong());
    doAnswer(this::recordWritingThread).when(scanUnitStorage).updateUnitState(anyLong(), any());
    doAnswer(this::recordWritingThread).when(scanUnitStorage).updateUnitFailure(anyLong(), any());
  }

  private Object recordWritingThread(InvocationOnMock invocation) {
    writingThreads.add(Thread.currentThread().getName());
    return null;
  }

  private void planned(String... unitPaths) {
    when(cleanupJcrStorage.listScanUnits()).thenReturn(List.of(unitPaths));
  }

  private void unitsToProcess(CleanupScanUnit... units) {
    when(scanUnitStorage.getUnitsToProcess(CAMPAIGN_ID)).thenReturn(List.of(units));
  }

  private void unitAggregates(long totalCount, long scannedCount) {
    when(scanUnitStorage.sumTotalCount(CAMPAIGN_ID)).thenReturn(totalCount);
    when(scanUnitStorage.sumScannedCount(CAMPAIGN_ID)).thenReturn(scannedCount);
  }

  private void unitOutcomes(long unitCount, long doneCount, long failedCount) {
    when(scanUnitStorage.countUnits(CAMPAIGN_ID)).thenReturn(unitCount);
    unitStates(doneCount, failedCount);
  }

  private void unitStates(long doneCount, long failedCount) {
    when(scanUnitStorage.countUnitsByState(CAMPAIGN_ID, CleanupScanUnitState.DONE)).thenReturn(doneCount);
    when(scanUnitStorage.countUnitsByState(CAMPAIGN_ID, CleanupScanUnitState.FAILED)).thenReturn(failedCount);
  }

  /**
   * Streams the given batches through the walk of one unit, recording the thread
   * the walk ran on.
   */
  private void emitBatches(String unitPath, EmittedBatch... batches) {
    doAnswer(invocation -> {
      readingThread.set(Thread.currentThread().getName());
      ScanBatchConsumer batchConsumer = invocation.getArgument(4);
      for (EmittedBatch emitted : batches) {
        if (!batchConsumer.onBatch(emitted.candidates, emitted.lastScannedPath, emitted.scannedCount)) {
          break;
        }
      }
      return null;
    }).when(cleanupJcrStorage).scanRoot(eq(unitPath), any(), anyInt(), any(), any());
  }

  private EmittedBatch batch(String lastScannedPath, int scannedCount, CleanupCandidate... candidates) {
    return new EmittedBatch(List.of(candidates), lastScannedPath, scannedCount);
  }

  private CleanupScanUnit unit(long id, String unitPath) {
    CleanupScanUnit unit = new CleanupScanUnit();
    unit.setId(id);
    unit.setCampaignId(CAMPAIGN_ID);
    unit.setUnitPath(unitPath);
    unit.setState(CleanupScanUnitState.PENDING);
    return unit;
  }

  private CleanupCandidate candidate(String nodeUuid, String path) {
    return new CleanupCandidate(nodeUuid, path, 7L, 2048, 0, CleanupAction.DELETE, 100, 200);
  }

  @SuppressWarnings("unchecked")
  private Set<Long> runningCampaigns() throws ReflectiveOperationException {
    Field runningCampaignsField = CleanupScanService.class.getDeclaredField("runningCampaigns");
    runningCampaignsField.setAccessible(true); // NOSONAR test wiring
    return (Set<Long>) runningCampaignsField.get(scanService);
  }

  /** One batch a stubbed walk hands to the scan consumer. */
  private static class EmittedBatch {

    private final List<CleanupCandidate> candidates;

    private final String                 lastScannedPath;

    private final int                    scannedCount;

    private EmittedBatch(List<CleanupCandidate> candidates, String lastScannedPath, int scannedCount) {
      this.candidates = candidates;
      this.lastScannedPath = lastScannedPath;
      this.scannedCount = scannedCount;
    }

  }

}
