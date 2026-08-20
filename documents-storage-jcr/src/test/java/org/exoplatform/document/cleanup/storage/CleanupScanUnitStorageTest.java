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
import org.exoplatform.document.cleanup.model.CleanupScanUnit;

/**
 * Scan-unit storage tests pinning what the parallel scan's resumability rests
 * on: planning is IDEMPOTENT (an already-planned path is skipped, never
 * re-inserted and never reset), only DONE units are considered finished, and the
 * per-unit checkpoint / state / failure updates each touch their own row.
 */
@ExtendWith(MockitoExtension.class)
class CleanupScanUnitStorageTest {

  private static final long          CAMPAIGN_ID  = 3L;

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
  void getUnitsToProcessExcludesOnlyTheDoneUnits() {
    when(scanUnitDAO.findByCampaignIdAndStateNotOrderByIdAsc(CAMPAIGN_ID,
                                                             CleanupScanUnitState.DONE.name())).thenReturn(List.of(entity(CleanupScanUnitState.FAILED),
                                                                                                                   entity(CleanupScanUnitState.RUNNING)));

    List<CleanupScanUnit> units = storage.getUnitsToProcess(CAMPAIGN_ID);

    // A unit left RUNNING by an interrupted run and a FAILED one are BOTH
    // retried, each from its own persisted path
    assertEquals(List.of(CleanupScanUnitState.FAILED, CleanupScanUnitState.RUNNING),
                 units.stream().map(CleanupScanUnit::getState).toList());
    CleanupScanUnit unit = units.get(0);
    assertEquals(UNIT_ID, unit.getId());
    assertEquals(CAMPAIGN_ID, unit.getCampaignId());
    assertEquals(USERS_UNIT, unit.getUnitPath());
    assertEquals(SCANNED_PATH, unit.getLastScannedPath());
    assertEquals(12, unit.getScannedCount());
    assertEquals(40, unit.getTotalCount());
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
    assertEquals(99, entity.getTotalCount());
    assertEquals(SCANNED_PATH, entity.getLastScannedPath(), "The checkpoint must survive a state or total update");
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
    verify(scanUnitDAO, never()).findByCampaignIdAndStateNotOrderByIdAsc(anyLong(), any());
  }

  @Test
  void aFreshlyPlannedUnitMapsBackWithoutACheckpoint() {
    CleanupScanUnitEntity entity = new CleanupScanUnitEntity();
    entity.setId(UNIT_ID);
    entity.setCampaignId(CAMPAIGN_ID);
    entity.setUnitPath(TRASH_UNIT);
    entity.setState(CleanupScanUnitState.PENDING.name());
    when(scanUnitDAO.findByCampaignIdAndStateNotOrderByIdAsc(CAMPAIGN_ID,
                                                             CleanupScanUnitState.DONE.name())).thenReturn(List.of(entity));

    CleanupScanUnit unit = storage.getUnitsToProcess(CAMPAIGN_ID).get(0);

    assertNull(unit.getLastScannedPath(), "A never-started unit must resume from the beginning of its subtree");
    assertEquals(0, unit.getScannedCount());
    assertEquals(0, unit.getTotalCount());
  }

  private CleanupScanUnitEntity entity(CleanupScanUnitState state) {
    CleanupScanUnitEntity entity = new CleanupScanUnitEntity();
    entity.setId(UNIT_ID);
    entity.setCampaignId(CAMPAIGN_ID);
    entity.setUnitPath(USERS_UNIT);
    entity.setState(state.name());
    entity.setLastScannedPath(SCANNED_PATH);
    entity.setScannedCount(12);
    entity.setTotalCount(40);
    entity.setFailureReason("cleanup.scanUnitFailed");
    return entity;
  }

}
