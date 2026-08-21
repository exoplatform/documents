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
package org.exoplatform.document.cleanup.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.document.cleanup.constant.CleanupScanUnitState;
import org.exoplatform.document.cleanup.dao.CleanupScanUnitDAO;
import org.exoplatform.document.cleanup.entity.CleanupScanUnitEntity;
import org.exoplatform.document.cleanup.model.CleanupFailureGroup;
import org.exoplatform.document.cleanup.model.CleanupScanUnit;
import org.exoplatform.document.cleanup.model.CleanupScanUnitProgress;

/**
 * Scan-unit storage tests pinning what the parallel scan's resumability rests
 * on: planning is IDEMPOTENT (an already-planned path is skipped, never
 * re-inserted and never reset), only DONE units and the SETTLED-failed ones are considered finished, and the
 * per-unit checkpoint / state / failure updates each touch their own row.
 */
@ExtendWith(MockitoExtension.class)
class CleanupScanUnitStorageTest {

  private static final long          CAMPAIGN_ID  = 3L;

  /** Walk attempts a unit may spend, as {@code MAX_SCAN_UNIT_ATTEMPTS} sets it. */
  private static final long          MAX_ATTEMPTS = 3L;

  private static final long          UNIT_ID      = 17L;

  private static final String        USERS_UNIT   = "/Users/j___";               // NOSONAR

  private static final String        SPACES_UNIT  = "/Groups/spaces/marketing";  // NOSONAR

  private static final String        TRASH_UNIT   = "/Trash";                    // NOSONAR

  private static final String        SCANNED_PATH = "/Users/j___/john/a.pdf";    // NOSONAR

  @Mock
  private CleanupScanUnitDAO         scanUnitDAO;

  @InjectMocks
  private CleanupScanUnitStorage     storage;

  @Test
  @SuppressWarnings("unchecked")
  void planUnitsInsertsOnlyTheMissingPathsAsPending() {
    when(scanUnitDAO.findUnitPathsByCampaignId(CAMPAIGN_ID)).thenReturn(List.of(USERS_UNIT));

    storage.planUnits(CAMPAIGN_ID, List.of(USERS_UNIT, SPACES_UNIT, TRASH_UNIT));

    ArgumentCaptor<List<CleanupScanUnitEntity>> entitiesCaptor = ArgumentCaptor.forClass(List.class);
    verify(scanUnitDAO).saveAll(entitiesCaptor.capture());
    List<CleanupScanUnitEntity> saved = entitiesCaptor.getValue();
    // The already-planned path is SKIPPED: re-planning a resumed campaign must
    // never duplicate a unit, nor reset the checkpoint of a walked subtree
    assertEquals(List.of(SPACES_UNIT, TRASH_UNIT), saved.stream().map(CleanupScanUnitEntity::getUnitPath).toList());
    assertTrue(saved.stream().allMatch(entity -> CleanupScanUnitState.PENDING.name().equals(entity.getState())),
               "A freshly planned unit must start PENDING");
    assertTrue(saved.stream().allMatch(entity -> entity.getCampaignId() == CAMPAIGN_ID));
    assertTrue(saved.stream().allMatch(entity -> entity.getLastScannedPath() == null),
               "A freshly planned unit carries no checkpoint");
    // The unique key is the HASH, never the path: a unique constraint on
    // UNIT_PATH NVARCHAR(2000) is 6008 bytes under the table's CHARSET=UTF8,
    // past InnoDB's 3072-byte ceiling, so MySQL fails the changeset at DDL time
    // and the addon does not deploy. An unset hash would violate NOT NULL on
    // every insert, so this is pinned rather than assumed
    assertEquals(List.of(CleanupScanUnitEntity.hashUnitPath(SPACES_UNIT), CleanupScanUnitEntity.hashUnitPath(TRASH_UNIT)),
                 saved.stream().map(CleanupScanUnitEntity::getUnitPathHash).toList());
    assertTrue(saved.stream().allMatch(entity -> entity.getUnitPathHash().length() == 64),
               "A SHA-256 hex digest is 64 characters, which is what the column is sized for");
  }

  @Test
  void unitPathHashDistinguishesPathsAndIsStable() {
    // Stable across calls — the hash IS the identity of a unit for the unique
    // constraint, so a differing digest for the same path would let a resume
    // plan the same subtree twice
    assertEquals(CleanupScanUnitEntity.hashUnitPath(USERS_UNIT), CleanupScanUnitEntity.hashUnitPath(USERS_UNIT));
    assertNotEquals(CleanupScanUnitEntity.hashUnitPath(USERS_UNIT), CleanupScanUnitEntity.hashUnitPath(SPACES_UNIT));
    // Two long paths sharing a 3072-byte prefix must still differ — the whole
    // point of hashing rather than truncating the path
    String prefix = "/Users/" + "a".repeat(3000);
    assertNotEquals(CleanupScanUnitEntity.hashUnitPath(prefix + "/one"), CleanupScanUnitEntity.hashUnitPath(prefix + "/two"));
    assertEquals(64, CleanupScanUnitEntity.hashUnitPath(null).length(), "A null path still hashes, so NOT NULL can never be violated");
  }

  @Test
  void planUnitsIsANoOpWhenEveryPathIsAlreadyPlanned() {
    when(scanUnitDAO.findUnitPathsByCampaignId(CAMPAIGN_ID)).thenReturn(List.of(USERS_UNIT, TRASH_UNIT));

    storage.planUnits(CAMPAIGN_ID, List.of(TRASH_UNIT, USERS_UNIT));

    verify(scanUnitDAO, never()).saveAll(anyList());
  }

  @Test
  void planUnitsIgnoresAnEmptyPlan() {
    storage.planUnits(CAMPAIGN_ID, List.of());
    storage.planUnits(CAMPAIGN_ID, null);

    verify(scanUnitDAO, never()).findUnitPathsByCampaignId(anyLong());
    verify(scanUnitDAO, never()).saveAll(anyList());
  }

  @Test
  void getUnitsToProcessAsksForEverythingButDoneAndTheSettledFailures() {
    when(scanUnitDAO.findUnitsToProcess(CAMPAIGN_ID,
                                        CleanupScanUnitState.DONE.name(),
                                        CleanupScanUnitState.FAILED.name(),
                                        MAX_ATTEMPTS)).thenReturn(List.of(entity(CleanupScanUnitState.FAILED),
                                                                          entity(CleanupScanUnitState.RUNNING)));

    List<CleanupScanUnit> units = storage.getUnitsToProcess(CAMPAIGN_ID, MAX_ATTEMPTS);

    // The two state names and the bound all reach the query: the exclusion is
    // state-AWARE (settled-FAILED only), and the rows it really filters are
    // pinned against a database by CleanupScanUnitDAOTest
    verify(scanUnitDAO).findUnitsToProcess(CAMPAIGN_ID,
                                          CleanupScanUnitState.DONE.name(),
                                          CleanupScanUnitState.FAILED.name(),
                                          MAX_ATTEMPTS);
    // A unit left RUNNING by an interrupted run and a FAILED one with attempts
    // left are BOTH retried, each from its own persisted path
    assertEquals(List.of(CleanupScanUnitState.FAILED, CleanupScanUnitState.RUNNING),
                 units.stream().map(CleanupScanUnit::getState).toList());
    CleanupScanUnit unit = units.get(0);
    assertEquals(UNIT_ID, unit.getId());
    assertEquals(CAMPAIGN_ID, unit.getCampaignId());
    assertEquals(USERS_UNIT, unit.getUnitPath());
    assertEquals(SCANNED_PATH, unit.getLastScannedPath());
    assertEquals(12, unit.getScannedCount());
    assertEquals(40L, unit.getTotalCount().longValue());
    assertEquals(2, unit.getAttemptCount());
    assertEquals("cleanup.scanUnitFailed", unit.getFailureReason());
  }

  @Test
  void updateUnitProgressCheckpointsThePathVerbatim() {
    CleanupScanUnitEntity entity = entity(CleanupScanUnitState.RUNNING);
    when(scanUnitDAO.findById(UNIT_ID)).thenReturn(Optional.of(entity));

    storage.updateUnitProgress(UNIT_ID, "/Users/j___/john/z.pdf", 44);

    verify(scanUnitDAO).save(entity);
    // Never truncated: a truncated resume path would silently re-walk or skip a
    // whole subtree
    assertEquals("/Users/j___/john/z.pdf", entity.getLastScannedPath());
    assertEquals(44, entity.getScannedCount());
  }

  @Test
  void updateUnitStateAndTotalTouchOnlyTheirOwnColumn() {
    CleanupScanUnitEntity entity = entity(CleanupScanUnitState.PENDING);
    when(scanUnitDAO.findById(UNIT_ID)).thenReturn(Optional.of(entity));

    storage.updateUnitState(UNIT_ID, CleanupScanUnitState.RUNNING);
    storage.updateUnitTotal(UNIT_ID, 99);

    assertEquals(CleanupScanUnitState.RUNNING.name(), entity.getState());
    assertEquals(99L, entity.getTotalCount().longValue());
    assertEquals(SCANNED_PATH, entity.getLastScannedPath(), "The checkpoint must survive a state or total update");
    // updateUnitState is NOT the claim: it must never spend a walk attempt, or
    // the writer recording a unit DONE would consume one of the three
    assertEquals(2, entity.getAttemptCount());
  }

  @Test
  void claimingAUnitMarksItRunningAndSpendsOneWalkAttempt() {
    CleanupScanUnitEntity entity = entity(CleanupScanUnitState.FAILED);
    when(scanUnitDAO.findById(UNIT_ID)).thenReturn(Optional.of(entity));
    when(scanUnitDAO.save(entity)).thenReturn(entity);

    long attempts = storage.claimUnit(UNIT_ID);

    // The two writes are ONE operation on purpose: an attempt counted anywhere
    // else than where the unit is handed to a reader is an attempt that can be
    // skipped, and a permanently failing subtree would then be re-walked forever
    assertEquals(CleanupScanUnitState.RUNNING.name(), entity.getState());
    assertEquals(3, entity.getAttemptCount());
    assertEquals(3, attempts, "The claim answers the attempts spent, this one included");
    assertEquals(SCANNED_PATH, entity.getLastScannedPath(), "A claim must not reset the resume checkpoint");
    verify(scanUnitDAO).save(entity);
  }

  @Test
  void claimingAnUnknownUnitIsANoOpAnsweringNoAttempt() {
    when(scanUnitDAO.findById(UNIT_ID)).thenReturn(Optional.empty());

    assertEquals(0, storage.claimUnit(UNIT_ID));
    verify(scanUnitDAO, never()).save(any());
  }

  @Test
  void settledFailuresAndGroupedReasonsAreBothDatabaseAggregates() {
    when(scanUnitDAO.countSettledFailures(CAMPAIGN_ID,
                                          CleanupScanUnitState.FAILED.name(),
                                          3L)).thenReturn(4L);
    when(scanUnitDAO.countFailuresByReason(CAMPAIGN_ID,
                                           CleanupScanUnitState.FAILED.name()))
                                                                             .thenReturn(List.<Object[]>of(new Object[] {
                                                                                 "cleanup.scanUnitFailed", 4L }));

    assertEquals(4L, storage.countSettledFailedUnits(CAMPAIGN_ID, 3L));
    List<CleanupFailureGroup> groups = storage.countFailuresByReason(CAMPAIGN_ID);
    assertEquals(1, groups.size());
    assertEquals("cleanup.scanUnitFailed", groups.get(0).getReason());
    assertEquals(4L, groups.get(0).getCount());
    // Retryability is a SERVICE rule, never the query's: the grouped rows carry
    // it false and the Service is the only place allowed to decide otherwise
    assertFalse(groups.get(0).isRetryable());
    // Never by loading the unit rows: a campaign holds one row per subtree of the
    // whole tree, and only the counts are ever displayed
    verify(scanUnitDAO, never()).findUnitsToProcess(anyLong(), any(), any(), anyLong());
  }

  @Test
  void updateUnitFailureRecordsFailedWithATruncatedReason() {
    CleanupScanUnitEntity entity = entity(CleanupScanUnitState.RUNNING);
    when(scanUnitDAO.findById(UNIT_ID)).thenReturn(Optional.of(entity));

    storage.updateUnitFailure(UNIT_ID, "x".repeat(CleanupScanUnitStorage.MAX_FAILURE_REASON_LENGTH + 500));

    assertEquals(CleanupScanUnitState.FAILED.name(), entity.getState());
    // An oversized value would fail the writer's whole batch: truncated here
    // rather than allowed to abort the scan
    assertEquals(CleanupScanUnitStorage.MAX_FAILURE_REASON_LENGTH, entity.getFailureReason().length());
  }

  @Test
  void updatesOnAnUnknownUnitAreNoOps() {
    when(scanUnitDAO.findById(UNIT_ID)).thenReturn(Optional.empty());

    storage.updateUnitState(UNIT_ID, CleanupScanUnitState.DONE);
    storage.updateUnitTotal(UNIT_ID, 5);
    storage.claimUnit(UNIT_ID);
    storage.updateUnitProgress(UNIT_ID, SCANNED_PATH, 5);
    storage.updateUnitFailure(UNIT_ID, "cleanup.scanUnitFailed");

    verify(scanUnitDAO, never()).save(any());
  }

  @Test
  void countsAndSumsAreDelegatedToTheDatabase() {
    when(scanUnitDAO.countByCampaignId(CAMPAIGN_ID)).thenReturn(29L);
    when(scanUnitDAO.countByCampaignIdAndState(CAMPAIGN_ID, CleanupScanUnitState.FAILED.name())).thenReturn(2L);
    when(scanUnitDAO.sumScannedCount(CAMPAIGN_ID)).thenReturn(1234L);
    when(scanUnitDAO.sumTotalCount(CAMPAIGN_ID)).thenReturn(5678L);

    assertEquals(29L, storage.countUnits(CAMPAIGN_ID));
    assertEquals(2L, storage.countUnitsByState(CAMPAIGN_ID, CleanupScanUnitState.FAILED));
    // Aggregates, never a row load: a campaign's units are re-summed on every
    // resume and the worker only ever needs the totals
    assertEquals(1234L, storage.sumScannedCount(CAMPAIGN_ID));
    assertEquals(5678L, storage.sumTotalCount(CAMPAIGN_ID));
    verify(scanUnitDAO, never()).findUnitsToProcess(anyLong(), any(), any(), anyLong());
  }

  @Test
  void aFreshlyPlannedUnitMapsBackWithoutACheckpoint() {
    CleanupScanUnitEntity entity = new CleanupScanUnitEntity();
    entity.setId(UNIT_ID);
    entity.setCampaignId(CAMPAIGN_ID);
    entity.setUnitPath(TRASH_UNIT);
    entity.setState(CleanupScanUnitState.PENDING.name());
    when(scanUnitDAO.findUnitsToProcess(CAMPAIGN_ID,
                                        CleanupScanUnitState.DONE.name(),
                                        CleanupScanUnitState.FAILED.name(),
                                        MAX_ATTEMPTS)).thenReturn(List.of(entity));

    CleanupScanUnit unit = storage.getUnitsToProcess(CAMPAIGN_ID, MAX_ATTEMPTS).get(0);

    assertNull(unit.getLastScannedPath(), "A never-started unit must resume from the beginning of its subtree");
    assertEquals(0, unit.getScannedCount());
    // NULL and not 0: 'never counted' must stay distinguishable from 'counted,
    // and empty', or the estimation phase re-counts every empty bucket on every
    // single resume
    assertNull(unit.getTotalCount(), "A never-counted unit must carry no total at all");
    assertEquals(0, unit.getAttemptCount());
  }

  @Test
  void aCountedButEmptyUnitMapsBackAsZeroAndNotAsUncounted() {
    CleanupScanUnitEntity entity = new CleanupScanUnitEntity();
    entity.setId(UNIT_ID);
    entity.setCampaignId(CAMPAIGN_ID);
    entity.setUnitPath(TRASH_UNIT);
    entity.setState(CleanupScanUnitState.PENDING.name());
    // Counted, and legitimately empty — an unused first-letter bucket of /Users
    entity.setTotalCount(0L);
    when(scanUnitDAO.findUnitsToProcess(CAMPAIGN_ID,
                                        CleanupScanUnitState.DONE.name(),
                                        CleanupScanUnitState.FAILED.name(),
                                        MAX_ATTEMPTS)).thenReturn(List.of(entity));

    CleanupScanUnit unit = storage.getUnitsToProcess(CAMPAIGN_ID, MAX_ATTEMPTS).get(0);

    assertNotNull(unit.getTotalCount(), "An empty bucket was COUNTED: it must never look uncounted again");
    assertEquals(0L, unit.getTotalCount().longValue());
  }

  @Test
  void unitProgressFoldsTheStateCountsAndAgreesWithTheTerminalTransitionOnSettled() {
    when(scanUnitDAO.countByState(CAMPAIGN_ID)).thenReturn(List.of(new Object[] { "DONE", 537L },
                                                                  new Object[] { "RUNNING", 1L },
                                                                  new Object[] { "FAILED", 2L }));
    // Of the two FAILED units, ONE spent its attempts: the other is still being
    // re-walked by the watchdog and must NOT count as settled
    when(scanUnitDAO.countSettledFailures(CAMPAIGN_ID, CleanupScanUnitState.FAILED.name(), MAX_ATTEMPTS)).thenReturn(1L);
    when(scanUnitDAO.maxAttemptCount(CAMPAIGN_ID)).thenReturn(3L);
    when(scanUnitDAO.findByState(CAMPAIGN_ID,
                                CleanupScanUnitState.RUNNING.name())).thenReturn(List.of(entity(CleanupScanUnitState.RUNNING)));

    CleanupScanUnitProgress progress = storage.getUnitProgress(CAMPAIGN_ID, MAX_ATTEMPTS);

    // The unit total is the SUM of the grouped counts, never a separate count
    // query that could disagree with them
    assertEquals(540L, progress.getUnitCount());
    assertEquals(537L, progress.getDoneCount());
    assertEquals(1L, progress.getRunningCount());
    assertEquals(2L, progress.getFailedCount());
    assertEquals(0L, progress.getPendingCount(), "A state absent from the grouped rows counts 0, not null");
    assertEquals(538L, progress.getSettledCount(), "Settled is DONE plus the failures that spent their attempts");
    assertEquals(3L, progress.getMaxAttemptCount());
    // 538 of 540: the report is NOT complete, and this is the flag the console
    // trusts instead of the node percentage — which would read 100% here
    assertFalse(progress.isScanComplete());
    assertEquals(1, progress.getInFlightUnits().size());
    assertEquals(USERS_UNIT, progress.getInFlightUnits().get(0).getUnitPath());
    assertEquals(SCANNED_PATH, progress.getInFlightUnits().get(0).getLastScannedPath(),
                 "The in-flight unit carries its own checkpoint: it is what shows a re-walk standing still");
  }

  @Test
  void unitProgressIsCompleteOnlyWhenEveryUnitSettled() {
    when(scanUnitDAO.countByState(CAMPAIGN_ID)).thenReturn(List.of(new Object[] { "DONE", 39L },
                                                                  new Object[] { "FAILED", 1L }));
    when(scanUnitDAO.countSettledFailures(CAMPAIGN_ID, CleanupScanUnitState.FAILED.name(), MAX_ATTEMPTS)).thenReturn(1L);

    assertTrue(storage.getUnitProgress(CAMPAIGN_ID, MAX_ATTEMPTS).isScanComplete(),
               "A settled FAILED unit completes the scan as surely as a DONE one — incompletely, but terminally");
  }

  @Test
  void unitProgressOfACampaignWithoutUnitsIsNotComplete() {
    when(scanUnitDAO.countByState(CAMPAIGN_ID)).thenReturn(List.of());

    CleanupScanUnitProgress progress = storage.getUnitProgress(CAMPAIGN_ID, MAX_ATTEMPTS);

    assertEquals(0L, progress.getUnitCount());
    // 0 settled of 0 planned is arithmetically 'all of them' and semantically
    // nothing walked. Reporting it complete would put back the very false 100%
    // this breakdown exists to remove
    assertFalse(progress.isScanComplete(), "A campaign with no planned unit has scanned NOTHING");
    assertTrue(progress.getInFlightUnits().isEmpty());
  }

  private CleanupScanUnitEntity entity(CleanupScanUnitState state) {
    CleanupScanUnitEntity entity = new CleanupScanUnitEntity();
    entity.setId(UNIT_ID);
    entity.setCampaignId(CAMPAIGN_ID);
    entity.setUnitPath(USERS_UNIT);
    entity.setState(state.name());
    entity.setLastScannedPath(SCANNED_PATH);
    entity.setScannedCount(12);
    entity.setTotalCount(40L);
    entity.setAttemptCount(2);
    entity.setFailureReason("cleanup.scanUnitFailed");
    return entity;
  }

}
