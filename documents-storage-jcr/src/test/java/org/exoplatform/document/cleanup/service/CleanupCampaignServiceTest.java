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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.constant.CleanupExemptionResult;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupBulkFailure;
import org.exoplatform.document.cleanup.model.CleanupBulkResult;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignAggregates;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupComparison;
import org.exoplatform.document.cleanup.model.CleanupComparisonBucket;
import org.exoplatform.document.cleanup.model.CleanupFailureGroup;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupRevalidation;
import org.exoplatform.document.cleanup.model.CleanupUserSummary;
import org.exoplatform.document.cleanup.rest.util.CleanupEntityBuilder;
import org.exoplatform.document.cleanup.storage.CleanupCampaignStorage;
import org.exoplatform.document.cleanup.storage.CleanupJcrStorage;
import org.exoplatform.document.cleanup.websocket.CleanupWebSocketService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

@ExtendWith(MockitoExtension.class)
class CleanupCampaignServiceTest {

  private static final String      PATH                        = "/Users/john/Private/docs"; // NOSONAR

  private static final String      PROPERTY_CHANGED_EVENT      = "PROPERTY_CHANGED";

  private static final String      CLEANUP_INVALID_STATE_ERROR = "cleanup.invalidState";

  private static final String      NODE_UUID_GONE              = "uuid-gone";

  private static final String      NODE_UUID_EXEMPTED          = "uuid-exempted";

  private static final String      NODE_UUID_SPARED            = "uuid-spared";

  private static final String      NODE_UUID_REFRESHED         = "uuid-refreshed";

  private static final String      NODE_UUID                   = "node-uuid-1";

  private static final long        CAMPAIGN_ID                 = 1L;

  private static final long        ITEM_ID                     = 10L;

  private static final String      USERNAME                    = "john";

  private static final String      SPACE_NAME                  = "marketing";

  /**
   * The states {@code CleanupCampaignService.getUserVisibleCampaign} looks up
   * FIRST, mirroring the service's own private ACTIVE_STATES. Stubbing this exact
   * list — instead of anyList() — is what keeps the COMPLETED fallback query
   * distinguishable in the tests below.
   */
  private static final List<CleanupCampaignState> ACTIVE_STATES =
                                                               List.of(CleanupCampaignState.PUBLISHED,
                                                                       CleanupCampaignState.LOCKED,
                                                                       CleanupCampaignState.EXECUTING);

  private static final List<CleanupCampaignState> COMPLETED_STATES = List.of(CleanupCampaignState.COMPLETED);

  /**
   * The report's column contract, mirroring the service's own private
   * CSV_HEADER: the historical columns come first, in their original order, and
   * every column added since is appended after them.
   */
  private static final String                     CSV_HEADER       =
                                                               "nodeUuid,path,ownerIdentityId,action,state,fileSize,versionsSize,reclaimedBytes,failureReason,ownerName,lastModifiedDate,createdDate,attemptCount,failureDetail\n";

  @Mock
  private CleanupCampaignStorage   campaignStorage;

  @Mock
  private CleanupSettingService    settingService;

  @Mock
  private CleanupScanService       scanService;

  @Mock
  private CleanupExecutionService  executionService;

  @Mock
  private CleanupJcrStorage        cleanupJcrStorage;

  @Mock
  private IdentityManager          identityManager;

  @Mock
  private SpaceService             spaceService;

  @Mock
  private FileService              fileService;

  @Mock
  private CleanupWebSocketService  webSocketService;

  @Spy
  @InjectMocks
  private CleanupCampaignLifecycle campaignLifecycle;

  @InjectMocks
  private CleanupCampaignService   campaignService;

  @BeforeEach
  void injectLifecycle() throws ReflectiveOperationException {
    // Mockito doesn't inject a @Spy @InjectMocks field into another
    // @InjectMocks: wire the real lifecycle (fed with the mocks) manually
    Field lifecycleField = CleanupCampaignService.class.getDeclaredField("campaignLifecycle");
    lifecycleField.setAccessible(true); // NOSONAR test wiring
    lifecycleField.set(campaignService, campaignLifecycle); // NOSONAR
  }

  @Test
  void shouldPublishSimulatedCampaignWhenNoOtherActive() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.SIMULATED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.getCampaignsByStates(anyList())).thenReturn(List.of());
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupCampaign published = campaignService.publishCampaign(CAMPAIGN_ID);

    assertEquals(CleanupCampaignState.PUBLISHED, published.getState());
    assertEquals(published.getPublishedDate() + TimeUnit.DAYS.toMillis(7), published.getLockDate());
    verify(cleanupJcrStorage).registerObservationListener(any());
    verify(webSocketService).sendToAdministrators(any());
  }

  @Test
  void shouldRejectPublishWhenCampaignNotSimulated() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.DRAFT));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.publishCampaign(CAMPAIGN_ID));

    assertEquals(CLEANUP_INVALID_STATE_ERROR, exception.getMessage());
  }

  @Test
  void shouldRejectPublishWhenAnotherCampaignIsActive() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.SIMULATED));
    CleanupCampaign activeCampaign = campaign(CleanupCampaignState.PUBLISHED);
    activeCampaign.setId(99L);
    when(campaignStorage.getCampaignsByStates(anyList())).thenReturn(List.of(activeCampaign));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.publishCampaign(CAMPAIGN_ID));

    assertEquals("cleanup.campaignAlreadyActive", exception.getMessage());
    verify(campaignStorage, never()).saveCampaign(any());
  }

  @Test
  void shouldThrowNotFoundWhenPublishingUnknownCampaign() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> campaignService.publishCampaign(CAMPAIGN_ID));
  }

  @Test
  void shouldDelegateExecutionToExecutionServiceWhenCampaignLocked() throws ObjectNotFoundException {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.LOCKED));
    when(executionService.startExecution(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.EXECUTING));

    CleanupCampaign executing = campaignService.executeCampaign(CAMPAIGN_ID);

    assertEquals(CleanupCampaignState.EXECUTING, executing.getState());
    verify(executionService).startExecution(CAMPAIGN_ID);
    // A LOCKED campaign is executed directly, no extra transition
    verify(campaignStorage, never()).saveCampaign(any());
  }

  @Test
  void shouldLockThenExecutePublishedCampaignOnceGraceDeadlineElapsed() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    campaign.setLockDate(System.currentTimeMillis() - 1000);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(executionService.startExecution(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.EXECUTING));

    CleanupCampaign executing = campaignService.executeCampaign(CAMPAIGN_ID);

    assertEquals(CleanupCampaignState.EXECUTING, executing.getState());
    // PUBLISHED -> LOCKED through the regular lifecycle (same path as the
    // scheduled grace-deadline lock, unregistering the freshness listener),
    // then LOCKED -> EXECUTING
    InOrder inOrder = inOrder(campaignLifecycle, executionService);
    inOrder.verify(campaignLifecycle).transition(campaign, CleanupCampaignState.LOCKED);
    inOrder.verify(executionService).startExecution(CAMPAIGN_ID);
    verify(cleanupJcrStorage).unregisterObservationListener();
  }

  @Test
  void shouldRejectExecutionOfPublishedCampaignBeforeGraceDeadline() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    campaign.setLockDate(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.executeCampaign(CAMPAIGN_ID));

    assertEquals("cleanup.graceNotElapsed", exception.getMessage());
    verify(executionService, never()).startExecution(anyLong());
    verify(campaignStorage, never()).saveCampaign(any());
    assertEquals(CleanupCampaignState.PUBLISHED, campaign.getState());
  }

  @Test
  void shouldPublishGraceZeroCampaignWithImmediateDeadlineThenLockIt() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.SIMULATED);
    campaign.setParams(new CleanupParams(6, 1048576L, 0, 5, List.of(), 200));
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.getCampaignsByStates(anyList())).thenReturn(List.of());
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupCampaign published = campaignService.publishCampaign(CAMPAIGN_ID);

    assertEquals(CleanupCampaignState.PUBLISHED, published.getState());
    assertEquals(published.getPublishedDate(),
                 published.getLockDate(),
                 "A zero grace period must make the deadline elapse at publication");

    // The next scheduler tick locks the campaign right away
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED))).thenReturn(List.of(published));
    campaignService.lockExpiredPublishedCampaign();
    assertEquals(CleanupCampaignState.LOCKED, published.getState());
  }

  @Test
  void shouldRejectCancelOfTerminalCampaign() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.COMPLETED));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.cancelCampaign(CAMPAIGN_ID));

    assertEquals(CLEANUP_INVALID_STATE_ERROR, exception.getMessage());
  }

  @Test
  void shouldCancelPublishedCampaignAndUnregisterObservation() throws ObjectNotFoundException {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.PUBLISHED));
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.cancelCampaign(CAMPAIGN_ID);

    ArgumentCaptor<CleanupCampaign> captor = ArgumentCaptor.forClass(CleanupCampaign.class);
    verify(campaignStorage).saveCampaign(captor.capture());
    assertEquals(CleanupCampaignState.CANCELLED, captor.getValue().getState());
    verify(cleanupJcrStorage).unregisterObservationListener();
  }

  @Test
  void shouldCreateCampaignSnapshottingParamsAndLaunchScan() throws ObjectNotFoundException {
    CleanupParams effectiveParams = new CleanupParams(6, 1048576L, 7, 5, List.of(), 200);
    when(settingService.getEffectiveParams(any())).thenReturn(effectiveParams);
    CleanupCampaign createdCampaign = campaign(CleanupCampaignState.DRAFT);
    when(campaignStorage.createCampaign(any())).thenReturn(createdCampaign);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(createdCampaign);

    CleanupCampaign campaign = campaignService.createCampaign("Q3 cleanup", new CleanupParams());

    assertNotNull(campaign);
    verify(scanService).startScan(CAMPAIGN_ID);
  }

  @Test
  void shouldRejectCampaignCreationWithoutName() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.createCampaign(" ", null));

    assertEquals("cleanup.nameMandatory", exception.getMessage());
  }

  /**
   * The NAME column is NVARCHAR(250): before the shared validation, a longer
   * name reached the INSERT and failed with a raw database error. Creation and
   * rename go through the SAME check, so neither path can regress alone.
   */
  @Test
  void shouldRejectCampaignCreationWithNameLongerThanColumn() {
    IllegalArgumentException exception =
                                       assertThrows(IllegalArgumentException.class,
                                                    () -> campaignService.createCampaign(tooLongName(), new CleanupParams()));

    assertEquals("cleanup.nameTooLong", exception.getMessage());
    verify(campaignStorage, never()).createCampaign(any());
  }

  @Test
  void shouldUpdateCampaignNameInARunningState() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupCampaign updated = campaignService.updateCampaign(CAMPAIGN_ID, "Q4 cleanup", null);

    assertEquals("Q4 cleanup", updated.getName());
    // Pure metadata: no lifecycle transition, so no state change and no
    // stateChanged WebSocket event
    assertEquals(CleanupCampaignState.PUBLISHED, updated.getState());
    verify(campaignLifecycle, never()).transition(any(), any());
    verify(campaignLifecycle, never()).transition(any(), any(), any());
  }

  /**
   * A rename is allowed in a TERMINAL state too: correcting the label of an
   * already-completed report is a legitimate need, and the name keys nothing.
   */
  @Test
  void shouldUpdateNameOfCompletedCampaign() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupCampaign updated = campaignService.updateCampaign(CAMPAIGN_ID, "Q3 cleanup (final)", null);

    assertEquals("Q3 cleanup (final)", updated.getName());
    assertEquals(CleanupCampaignState.COMPLETED, updated.getState());
  }

  /**
   * Existence FIRST: the 404 is answered before any field is even looked at, so
   * a PATCH on an unknown campaign never reports a validation problem instead.
   */
  @Test
  void shouldThrowNotFoundWhenUpdatingUnknownCampaign() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(null);

    ObjectNotFoundException exception = assertThrows(ObjectNotFoundException.class,
                                                      () -> campaignService.updateCampaign(CAMPAIGN_ID, null, null));

    assertEquals("cleanup.campaignNotFound", exception.getMessage());
    verify(campaignStorage, never()).saveCampaign(any());
  }

  /**
   * An empty patch is REFUSED, never silently accepted as a no-op: the console
   * must be able to say why nothing happened.
   */
  @Test
  void shouldRejectUpdateCarryingNeitherField() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.PUBLISHED));
    // Lenient: unused while the refusal holds, but it makes a REGRESSION fail on
    // the missing refusal instead of on a downstream NPE
    lenient().when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.updateCampaign(CAMPAIGN_ID, null, null));

    assertEquals("cleanup.nothingToUpdate", exception.getMessage());
    verify(campaignStorage, never()).saveCampaign(any());
  }

  @Test
  void shouldRejectUpdateWithoutName() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.SIMULATED));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.updateCampaign(CAMPAIGN_ID, " ", null));

    assertEquals("cleanup.nameMandatory", exception.getMessage());
    verify(campaignStorage, never()).saveCampaign(any());
  }

  @Test
  void shouldRejectUpdateWithNameLongerThanColumn() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.SIMULATED));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.updateCampaign(CAMPAIGN_ID, tooLongName(), null));

    assertEquals("cleanup.nameTooLong", exception.getMessage());
    verify(campaignStorage, never()).saveCampaign(any());
  }

  /**
   * The name is trimmed BEFORE being persisted, and a name-only update touches
   * nothing else: every other field of the campaign row is carried over
   * untouched — the grace period and the grace DEADLINE included.
   */
  @Test
  void shouldTrimTheNameAndKeepEveryOtherFieldWhenUpdatingTheNameOnly() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    campaign.setPublishedDate(1234L);
    campaign.setLockDate(5678L);
    campaign.setTotalCount(42);
    campaign.setProcessedCount(7);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.updateCampaign(CAMPAIGN_ID, "  Q4 cleanup  ", null);

    ArgumentCaptor<CleanupCampaign> captor = ArgumentCaptor.forClass(CleanupCampaign.class);
    verify(campaignStorage).saveCampaign(captor.capture());
    CleanupCampaign saved = captor.getValue();
    assertEquals("Q4 cleanup", saved.getName());
    assertEquals(CAMPAIGN_ID, saved.getId());
    assertEquals(CleanupCampaignState.PUBLISHED, saved.getState());
    assertEquals(1234L, saved.getPublishedDate());
    // A name-only patch must move NEITHER the grace period nor the deadline
    assertEquals(5678L, saved.getLockDate());
    assertEquals(42, saved.getTotalCount());
    assertEquals(7, saved.getProcessedCount());
    assertNotNull(saved.getParams());
    assertEquals(7, saved.getParams().getGraceDays());
  }

  /**
   * Symmetric case: a grace-only patch must leave the NAME alone. The two fields
   * are strictly independent, which is the whole point of a partial update.
   */
  @Test
  void shouldKeepTheNameWhenUpdatingTheGracePeriodOnly() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.SIMULATED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupCampaign updated = campaignService.updateCampaign(CAMPAIGN_ID, null, 21);

    assertEquals("Q3 cleanup", updated.getName());
    assertEquals(21, updated.getParams().getGraceDays());
  }

  @Test
  void shouldUpdateTheNameAndTheGracePeriodAtOnce() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.DRAFT);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupCampaign updated = campaignService.updateCampaign(CAMPAIGN_ID, "  Q4 cleanup  ", 3);

    assertEquals("Q4 cleanup", updated.getName());
    assertEquals(3, updated.getParams().getGraceDays());
    // ONE save for the whole patch, not one per field
    verify(campaignStorage, times(1)).saveCampaign(any());
  }

  /**
   * The shared rule, exposed rather than restated: LOCKED is deliberately ABSENT
   * — extending the grace of a locked campaign would need a LOCKED to PUBLISHED
   * edge the lifecycle doesn't have, and re-registering the freshness listener
   * that exiting PUBLISHED unregistered.
   */
  @Test
  void shouldExposeTheGraceEditableStates() {
    assertEquals(Set.of(CleanupCampaignState.DRAFT, CleanupCampaignState.SIMULATED, CleanupCampaignState.PUBLISHED),
                 CleanupCampaignService.GRACE_EDITABLE_STATES);
    assertFalse(CleanupCampaignService.GRACE_EDITABLE_STATES.contains(CleanupCampaignState.LOCKED));
  }

  /**
   * The guard is per FIELD, not per request — the whole point of a partial
   * update: in every state that refuses a grace edit, a NAME change still
   * succeeds on the very same campaign.
   */
  @Test
  void shouldRefuseGraceEditOutsideTheEditableStatesWhileStillAllowingARename() throws ObjectNotFoundException {
    for (CleanupCampaignState state : List.of(CleanupCampaignState.LOCKED,
                                              CleanupCampaignState.EXECUTING,
                                              CleanupCampaignState.COMPLETED,
                                              CleanupCampaignState.CANCELLED)) {
      reset(campaignStorage);
      CleanupCampaign campaign = campaign(state);
      when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
      // Stubbed BEFORE the refusal, so a lost guard fails on the missing refusal
      // rather than on a downstream NPE; used by the rename half below anyway
      when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                        () -> campaignService.updateCampaign(CAMPAIGN_ID, null, 14),
                                                        "The grace period must not be editable in " + state);

      assertEquals(CLEANUP_INVALID_STATE_ERROR, exception.getMessage());
      assertEquals(7, campaign.getParams().getGraceDays(), "The refused grace edit must not have been applied in " + state);
      verify(campaignStorage, never()).saveCampaign(any());

      // Same state, same campaign: the NAME is pure metadata, so it is editable
      // there anyway
      assertEquals("Renamed in " + state,
                   campaignService.updateCampaign(CAMPAIGN_ID, "Renamed in " + state, null).getName());
    }
  }

  @Test
  void shouldAcceptGraceEditInEveryEditableState() throws ObjectNotFoundException {
    for (CleanupCampaignState state : CleanupCampaignService.GRACE_EDITABLE_STATES) {
      reset(campaignStorage);
      CleanupCampaign campaign = campaign(state);
      when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
      when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

      CleanupCampaign updated = campaignService.updateCampaign(CAMPAIGN_ID, null, 14);

      assertEquals(14, updated.getParams().getGraceDays(), "The grace period must be editable in " + state);
      assertEquals(state, updated.getState(), "Editing the grace period must trigger no transition, in " + state);
    }
  }

  @Test
  void shouldRejectNegativeGracePeriod() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.SIMULATED));
    lenient().when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.updateCampaign(CAMPAIGN_ID, null, -1));

    // The SAME message code the creation path already uses for that bound
    assertEquals("cleanup.invalidGraceDays", exception.getMessage());
    verify(campaignStorage, never()).saveCampaign(any());
  }

  /**
   * ZERO is a valid grace period, not an absent one: the deadline then elapses
   * at publication (see the publication test pinning the same rule).
   */
  @Test
  void shouldAcceptZeroGracePeriod() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.SIMULATED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupCampaign updated = campaignService.updateCampaign(CAMPAIGN_ID, null, 0);

    assertEquals(0, updated.getParams().getGraceDays());
  }

  /**
   * The regression this anchor exists to prevent: the recomputed deadline is
   * {@code publishedDate + graceDays}, NEVER {@code now + graceDays}. The
   * publication is pinned 30 days in the past, so the two differ unmistakably —
   * and saving the SAME value twice must be idempotent instead of pushing the
   * deadline out twice.
   */
  @Test
  void shouldRecomputeTheLockDateFromThePublishedDateWhenPublished() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    long publishedDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
    campaign.setPublishedDate(publishedDate);
    campaign.setLockDate(publishedDate + TimeUnit.DAYS.toMillis(7));
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupCampaign updated = campaignService.updateCampaign(CAMPAIGN_ID, null, 40);

    assertEquals(publishedDate + TimeUnit.DAYS.toMillis(40), updated.getLockDate());
    assertTrue(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(40) - updated.getLockDate() > TimeUnit.DAYS.toMillis(29),
               "The grace deadline must be anchored on the publication date, NEVER on now");

    // Idempotent: saving the same value again must not slide the deadline
    CleanupCampaign resaved = campaignService.updateCampaign(CAMPAIGN_ID, null, 40);

    assertEquals(publishedDate + TimeUnit.DAYS.toMillis(40), resaved.getLockDate());
  }

  /**
   * A recomputed deadline landing in the PAST is allowed and is not an error: it
   * closes the review window immediately, and the grace-deadline cron locks the
   * campaign at its next tick. This method transitions NOTHING itself — the
   * single PUBLISHED to LOCKED authority stays the scheduler and the manual
   * execution trigger.
   */
  @Test
  void shouldAllowAGraceDeadlineRecomputedIntoThePastWithoutLockingTheCampaign() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    long publishedDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
    campaign.setPublishedDate(publishedDate);
    campaign.setLockDate(publishedDate + TimeUnit.DAYS.toMillis(60));
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupCampaign updated = campaignService.updateCampaign(CAMPAIGN_ID, null, 1);

    assertEquals(publishedDate + TimeUnit.DAYS.toMillis(1), updated.getLockDate());
    assertTrue(updated.getLockDate() < System.currentTimeMillis(), "The recomputed deadline is expected to be in the past");
    assertEquals(CleanupCampaignState.PUBLISHED, updated.getState());
    verify(campaignLifecycle, never()).transition(any(), any());
    verify(campaignLifecycle, never()).transition(any(), any(), any());
  }

  /**
   * Before publication there is no deadline to recompute: the lock date is left
   * ALONE, publication deriving it from the edited value on its own. Pinned with
   * a sentinel so 'untouched' is asserted, not merely 'still zero'.
   */
  @Test
  void shouldNotTouchTheLockDateWhenTheCampaignIsNotPublishedYet() throws ObjectNotFoundException {
    for (CleanupCampaignState state : List.of(CleanupCampaignState.DRAFT, CleanupCampaignState.SIMULATED)) {
      reset(campaignStorage);
      CleanupCampaign campaign = campaign(state);
      campaign.setPublishedDate(0);
      campaign.setLockDate(4242L);
      when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
      when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

      CleanupCampaign updated = campaignService.updateCampaign(CAMPAIGN_ID, null, 14);

      assertEquals(14, updated.getParams().getGraceDays());
      assertEquals(4242L, updated.getLockDate(), "The lock date must not be recomputed in " + state);
    }
  }

  /**
   * Defensive path: a campaign carrying no snapshotted parameters must not lose
   * the edit — the holder is created rather than the write silently skipped.
   */
  @Test
  void shouldUpdateTheGracePeriodOfACampaignWithoutParams() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.DRAFT);
    campaign.setParams(null);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupCampaign updated = campaignService.updateCampaign(CAMPAIGN_ID, null, 5);

    assertNotNull(updated.getParams());
    assertEquals(5, updated.getParams().getGraceDays());
  }

  /**
   * One character past the NAME column width — the boundary the check has to
   * refuse, whatever the exact limit is written as.
   */
  private String tooLongName() {
    return "n".repeat(CleanupCampaignService.MAX_NAME_LENGTH + 1);
  }

  @Test
  void shouldKeepOwnItem() throws ObjectNotFoundException, IllegalAccessException {
    mockPublishedCampaignWithItem(CleanupItemState.CANDIDATE);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(cleanupJcrStorage.addExemptionMixin(NODE_UUID, USERNAME)).thenReturn(CleanupExemptionResult.ADDED);
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.keepItem(ITEM_ID, USERNAME);

    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(CleanupItemState.EXEMPTED, captor.getValue().getState());
    assertEquals(USERNAME, captor.getValue().getDecidedBy());
  }

  @Test
  void shouldRejectKeepOfForeignItem() {
    // No campaign stub: the ownership check runs BEFORE the campaign is loaded
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(item(CleanupItemState.CANDIDATE));
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", "mary"));

    assertThrows(IllegalAccessException.class, () -> campaignService.keepItem(ITEM_ID, USERNAME));
    verify(cleanupJcrStorage, never()).addExemptionMixin(any(), any());
  }

  @Test
  void shouldKeepSpaceItemWhenUserManagesTheSpace() throws ObjectNotFoundException, IllegalAccessException {
    mockPublishedCampaignWithItem(CleanupItemState.CANDIDATE);
    when(identityManager.getIdentity(5l)).thenReturn(spaceIdentity("5", SPACE_NAME));
    Space space = new Space();
    when(spaceService.getSpaceByPrettyName(SPACE_NAME)).thenReturn(space);
    when(spaceService.isManager(space, USERNAME)).thenReturn(true);
    when(cleanupJcrStorage.addExemptionMixin(NODE_UUID, USERNAME)).thenReturn(CleanupExemptionResult.ADDED);
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.keepItem(ITEM_ID, USERNAME);

    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(CleanupItemState.EXEMPTED, captor.getValue().getState());
  }

  @Test
  void shouldRejectKeepOfSpaceItemWhenUserIsNotManager() {
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(item(CleanupItemState.CANDIDATE));
    when(identityManager.getIdentity(5l)).thenReturn(spaceIdentity("5", SPACE_NAME));
    Space space = new Space();
    when(spaceService.getSpaceByPrettyName(SPACE_NAME)).thenReturn(space);
    when(spaceService.isManager(space, USERNAME)).thenReturn(false);

    assertThrows(IllegalAccessException.class, () -> campaignService.keepItem(ITEM_ID, USERNAME));
  }

  @Test
  void shouldRejectKeepWhenCampaignNotPublished() {
    CleanupCampaignItem item = item(CleanupItemState.CANDIDATE);
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(item);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.LOCKED));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.keepItem(ITEM_ID, USERNAME));

    assertEquals("cleanup.campaignNotPublished", exception.getMessage());
  }

  @Test
  void shouldRejectKeepOfForeignItemBeforeRevealingTheCampaignState() {
    // Ownership is checked FIRST: probing item ids must never let a non-owner
    // learn that the item exists nor which state its campaign is in
    CleanupCampaignItem item = item(CleanupItemState.PURGED);
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(item);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", "mary"));

    assertThrows(IllegalAccessException.class, () -> campaignService.keepItem(ITEM_ID, USERNAME));

    // The campaign was never even loaded, so no state could leak through the
    // 400 message codes
    verify(campaignStorage, never()).getCampaign(anyLong());
    verify(cleanupJcrStorage, never()).addExemptionMixin(any(), any());
  }

  @Test
  void shouldRejectUnkeepOfForeignItemBeforeRevealingTheCampaignState() {
    CleanupCampaignItem item = item(CleanupItemState.CANDIDATE);
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(item);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", "mary"));

    assertThrows(IllegalAccessException.class, () -> campaignService.unkeepItem(ITEM_ID, USERNAME));

    verify(campaignStorage, never()).getCampaign(anyLong());
    verify(cleanupJcrStorage, never()).removeExemptionMixin(any());
  }

  @Test
  void shouldFailClosedOnKeepAndUnkeepWhenOwnerIdentityUnresolvable() {
    CleanupCampaignItem item = item(CleanupItemState.CANDIDATE);
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(item);
    when(identityManager.getIdentity(5l)).thenReturn(null);

    // An unresolvable owner identity denies access, it never falls through to
    // the decision
    assertThrows(IllegalAccessException.class, () -> campaignService.keepItem(ITEM_ID, USERNAME));
    assertThrows(IllegalAccessException.class, () -> campaignService.unkeepItem(ITEM_ID, USERNAME));
    verify(cleanupJcrStorage, never()).addExemptionMixin(any(), any());
    verify(cleanupJcrStorage, never()).removeExemptionMixin(any());
  }

  @Test
  void shouldFailClosedOnKeepAndUnkeepWhenOwningSpaceUnresolvable() {
    CleanupCampaignItem item = item(CleanupItemState.EXEMPTED);
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(item);
    when(identityManager.getIdentity(5l)).thenReturn(spaceIdentity("5", SPACE_NAME));
    when(spaceService.getSpaceByPrettyName(SPACE_NAME)).thenReturn(null);

    // A space that can't be resolved anymore denies access too (fail closed),
    // never grants it
    assertThrows(IllegalAccessException.class, () -> campaignService.keepItem(ITEM_ID, USERNAME));
    assertThrows(IllegalAccessException.class, () -> campaignService.unkeepItem(ITEM_ID, USERNAME));
    verify(cleanupJcrStorage, never()).addExemptionMixin(any(), any());
    verify(cleanupJcrStorage, never()).removeExemptionMixin(any());
  }

  @Test
  void shouldRejectUnkeepOfSpaceItemWhenUserIsNotManager() {
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(item(CleanupItemState.EXEMPTED));
    when(identityManager.getIdentity(5l)).thenReturn(spaceIdentity("5", SPACE_NAME));
    Space space = new Space();
    when(spaceService.getSpaceByPrettyName(SPACE_NAME)).thenReturn(space);
    when(spaceService.isManager(space, USERNAME)).thenReturn(false);

    assertThrows(IllegalAccessException.class, () -> campaignService.unkeepItem(ITEM_ID, USERNAME));
    verify(cleanupJcrStorage, never()).removeExemptionMixin(any());
  }

  @Test
  void shouldRejectKeepAndUnkeepOnceGraceDeadlineElapsedEvenWhileStillPublished() {
    // The locking cron runs every 10 minutes, so a campaign stays PUBLISHED for
    // a while past its deadline (always so with a zero grace period): the
    // review
    // window freezes on the DEADLINE, not on the LOCKED transition
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    campaign.setLockDate(System.currentTimeMillis() - 1000);
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(item(CleanupItemState.CANDIDATE))
                                          .thenReturn(item(CleanupItemState.EXEMPTED));
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));

    assertEquals("cleanup.reviewClosed",
                 assertThrows(IllegalArgumentException.class, () -> campaignService.keepItem(ITEM_ID, USERNAME)).getMessage());
    assertEquals("cleanup.reviewClosed",
                 assertThrows(IllegalArgumentException.class,
                              () -> campaignService.unkeepItem(ITEM_ID, USERNAME)).getMessage());
    verify(cleanupJcrStorage, never()).addExemptionMixin(any(), any());
    verify(cleanupJcrStorage, never()).removeExemptionMixin(any());
    verify(campaignStorage, never()).saveItem(any());
  }

  @Test
  void shouldThrowNotFoundWhenKeepingUnknownItem() {
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> campaignService.keepItem(ITEM_ID, USERNAME));
  }

  @Test
  void shouldUnkeepOwnItemBackToCandidateWhenStillQualifying() throws ObjectNotFoundException, IllegalAccessException {
    mockPublishedCampaignWithItem(CleanupItemState.EXEMPTED);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(cleanupJcrStorage.removeExemptionMixin(NODE_UUID)).thenReturn(CleanupExemptionResult.ADDED);
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID), any()))
                                                            .thenReturn(CleanupRevalidation.of(new CleanupCandidate(NODE_UUID,
                                                                                                                    "/Users/j___/john/Private/file.pdf",
                                                                                                                    5L,
                                                                                                                    4096L,
                                                                                                                    0L,
                                                                                                                    CleanupAction.DELETE,
                                                                                                                    0L,
                                                                                                                    0L)));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.unkeepItem(ITEM_ID, USERNAME);

    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(CleanupItemState.CANDIDATE, captor.getValue().getState());
    assertEquals(4096L, captor.getValue().getFileSize(), "The revalidation must refresh the item sizes");
    assertEquals(USERNAME, captor.getValue().getDecidedBy());
    assertTrue(captor.getValue().getDecidedAt() > 0);
  }

  @Test
  void shouldUnkeepItemToSparedWhenModifiedMeanwhile() throws ObjectNotFoundException, IllegalAccessException {
    mockPublishedCampaignWithItem(CleanupItemState.EXEMPTED);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(cleanupJcrStorage.removeExemptionMixin(NODE_UUID)).thenReturn(CleanupExemptionResult.ADDED);
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID), any())).thenReturn(CleanupRevalidation.of(null));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.unkeepItem(ITEM_ID, USERNAME);

    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(CleanupItemState.SPARED_BY_MODIFICATION, captor.getValue().getState());
    assertEquals(USERNAME, captor.getValue().getDecidedBy());
  }

  @Test
  void shouldUnkeepItemToGoneWhenNodeDisappearedAfterMixinRemoval() throws ObjectNotFoundException, IllegalAccessException {
    mockPublishedCampaignWithItem(CleanupItemState.EXEMPTED);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(cleanupJcrStorage.removeExemptionMixin(NODE_UUID)).thenReturn(CleanupExemptionResult.ADDED);
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID), any())).thenReturn(CleanupRevalidation.gone());
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.unkeepItem(ITEM_ID, USERNAME);

    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(CleanupItemState.GONE, captor.getValue().getState());
  }

  @Test
  void shouldRejectUnkeepOfForeignItem() {
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(item(CleanupItemState.EXEMPTED));
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", "mary"));

    assertThrows(IllegalAccessException.class, () -> campaignService.unkeepItem(ITEM_ID, USERNAME));
    verify(cleanupJcrStorage, never()).removeExemptionMixin(any());
  }

  @Test
  void shouldUnkeepSpaceItemWhenUserManagesTheSpace() throws ObjectNotFoundException, IllegalAccessException {
    mockPublishedCampaignWithItem(CleanupItemState.EXEMPTED);
    when(identityManager.getIdentity(5l)).thenReturn(spaceIdentity("5", SPACE_NAME));
    Space space = new Space();
    when(spaceService.getSpaceByPrettyName(SPACE_NAME)).thenReturn(space);
    when(spaceService.isManager(space, USERNAME)).thenReturn(true);
    when(cleanupJcrStorage.removeExemptionMixin(NODE_UUID)).thenReturn(CleanupExemptionResult.ADDED);
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID), any())).thenReturn(CleanupRevalidation.of(null));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.unkeepItem(ITEM_ID, USERNAME);

    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(CleanupItemState.SPARED_BY_MODIFICATION, captor.getValue().getState());
  }

  @Test
  void shouldRejectUnkeepWhenCampaignNotPublished() {
    CleanupCampaignItem item = item(CleanupItemState.EXEMPTED);
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(item);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.LOCKED));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.unkeepItem(ITEM_ID, USERNAME));

    assertEquals("cleanup.reviewClosed", exception.getMessage());
    verify(cleanupJcrStorage, never()).removeExemptionMixin(any());
  }

  @Test
  void shouldRejectUnkeepOfNonKeptItem() {
    mockPublishedCampaignWithItem(CleanupItemState.CANDIDATE);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.unkeepItem(ITEM_ID, USERNAME));

    assertEquals("cleanup.itemNotKept", exception.getMessage());
    verify(cleanupJcrStorage, never()).removeExemptionMixin(any());
  }

  @Test
  void shouldMarkItemGoneAndThrowNotFoundWhenUnkeptNodeMissing() {
    mockPublishedCampaignWithItem(CleanupItemState.EXEMPTED);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(cleanupJcrStorage.removeExemptionMixin(NODE_UUID)).thenReturn(CleanupExemptionResult.NOT_FOUND);
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    assertThrows(ObjectNotFoundException.class, () -> campaignService.unkeepItem(ITEM_ID, USERNAME));

    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(CleanupItemState.GONE, captor.getValue().getState());
  }

  @Test
  void shouldLeaveItemKeptOnTransientUnkeepFailure() {
    mockPublishedCampaignWithItem(CleanupItemState.EXEMPTED);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(cleanupJcrStorage.removeExemptionMixin(NODE_UUID)).thenReturn(CleanupExemptionResult.FAILED);

    assertThrows(IllegalStateException.class, () -> campaignService.unkeepItem(ITEM_ID, USERNAME));

    // No state change: the un-keep stays retryable
    verify(campaignStorage, never()).saveItem(any());
    verify(cleanupJcrStorage, never()).revalidate(any(), any());
  }

  @Test
  void shouldThrowNotFoundWhenUnkeepingUnknownItem() {
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> campaignService.unkeepItem(ITEM_ID, USERNAME));
  }

  @Test
  void shouldContinueBulkUnkeepPastIndividualFailures() {
    // Item 10 is unknown (fails), item 11 is un-kept normally
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(null);
    CleanupCampaignItem otherItem = item(CleanupItemState.EXEMPTED);
    otherItem.setId(11L);
    when(campaignStorage.getItem(11L)).thenReturn(otherItem);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.PUBLISHED));
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(cleanupJcrStorage.removeExemptionMixin(NODE_UUID)).thenReturn(CleanupExemptionResult.ADDED);
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID), any())).thenReturn(CleanupRevalidation.of(null));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupBulkResult result = campaignService.unkeepItems(List.of(ITEM_ID, 11L), USERNAME);

    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(11L, captor.getValue().getId(), "The failing item must not prevent the remaining un-keeps");
    assertEquals(CleanupItemState.SPARED_BY_MODIFICATION, captor.getValue().getState());
    assertEquals(1, result.getSucceeded());
    assertEquals(1, result.getFailures().size());
    assertEquals(ITEM_ID, result.getFailures().get(0).getItemId());
  }

  @Test
  void shouldRejectBulkUnkeepWithoutItemIds() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.unkeepItems(List.of(), USERNAME));

    assertEquals("cleanup.itemIdsMandatory", exception.getMessage());
  }

  @Test
  void shouldNeverChangeExemptedItemStateOnFreshnessRefresh() {
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED))).thenReturn(List.of(campaign));
    CleanupCampaignItem keptItem = item(CleanupItemState.EXEMPTED);
    when(campaignStorage.getItemsTouchedByPath(CAMPAIGN_ID, PATH)).thenReturn(List.of(keptItem));

    campaignService.refreshCandidate(PATH, PROPERTY_CHANGED_EVENT);

    // A modification never un-keeps: the freshness refresh skips decided items
    assertEquals(CleanupItemState.EXEMPTED, keptItem.getState());
    verify(cleanupJcrStorage, never()).revalidate(any(), any());
    verify(campaignStorage, never()).saveItem(any());
  }

  @Test
  void shouldLockPublishedCampaignOnceGraceDeadlineElapsed() {
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    campaign.setLockDate(System.currentTimeMillis() - 1000);
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED))).thenReturn(List.of(campaign));
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.lockExpiredPublishedCampaign();

    ArgumentCaptor<CleanupCampaign> captor = ArgumentCaptor.forClass(CleanupCampaign.class);
    verify(campaignStorage).saveCampaign(captor.capture());
    assertEquals(CleanupCampaignState.LOCKED, captor.getValue().getState());
    verify(cleanupJcrStorage).unregisterObservationListener();
  }

  @Test
  void shouldNotLockPublishedCampaignBeforeGraceDeadline() {
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    campaign.setLockDate(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED))).thenReturn(List.of(campaign));

    campaignService.lockExpiredPublishedCampaign();

    verify(campaignStorage, never()).saveCampaign(any());
  }

  @Test
  void shouldRefreshCandidatesWithTheSharedRevalidationMapping() {
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED))).thenReturn(List.of(campaign));

    CleanupCampaignItem goneItem = item(CleanupItemState.CANDIDATE);
    goneItem.setId(11L);
    goneItem.setNodeUuid(NODE_UUID_GONE);
    CleanupCampaignItem exemptedItem = item(CleanupItemState.CANDIDATE);
    exemptedItem.setId(12L);
    exemptedItem.setNodeUuid(NODE_UUID_EXEMPTED);
    CleanupCampaignItem sparedItem = item(CleanupItemState.CANDIDATE);
    sparedItem.setId(13L);
    sparedItem.setNodeUuid(NODE_UUID_SPARED);
    CleanupCampaignItem refreshedItem = item(CleanupItemState.CANDIDATE);
    refreshedItem.setId(14L);
    refreshedItem.setNodeUuid(NODE_UUID_REFRESHED);
    CleanupCampaignItem decidedItem = item(CleanupItemState.EXEMPTED);
    decidedItem.setId(15L);
    when(campaignStorage.getItemsTouchedByPath(CAMPAIGN_ID, PATH)).thenReturn(List.of(goneItem,
                                                                                      exemptedItem,
                                                                                      sparedItem,
                                                                                      refreshedItem,
                                                                                      decidedItem));
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_GONE), any())).thenReturn(CleanupRevalidation.gone());
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_EXEMPTED), any())).thenReturn(CleanupRevalidation.exempted());
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_SPARED), any())).thenReturn(CleanupRevalidation.of(null));
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID_REFRESHED), any()))
                                                                      .thenReturn(CleanupRevalidation.of(new CleanupCandidate(NODE_UUID_REFRESHED,
                                                                                                                              "/Users/john/Private/docs/file.pdf",
                                                                                                                              5L,
                                                                                                                              2097152L,
                                                                                                                              512L,
                                                                                                                              CleanupAction.PURGE_VERSIONS,
                                                                                                                              0L,
                                                                                                                              0L)));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.refreshCandidate(PATH, "NODE_REMOVED");

    assertEquals(CleanupItemState.GONE, goneItem.getState());
    assertEquals(CleanupItemState.EXEMPTED, exemptedItem.getState());
    assertEquals(CleanupItemState.SPARED_BY_MODIFICATION, sparedItem.getState());
    assertEquals(CleanupItemState.CANDIDATE, refreshedItem.getState());
    assertEquals(CleanupAction.PURGE_VERSIONS, refreshedItem.getAction());
    assertEquals(2097152L, refreshedItem.getFileSize());
    assertEquals(512L, refreshedItem.getVersionsSize());
    assertTrue(refreshedItem.getComputedAt() > 0);
    // Already-decided items are never revalidated
    assertEquals(CleanupItemState.EXEMPTED, decidedItem.getState());
    verify(cleanupJcrStorage, never()).revalidate(eq(decidedItem.getNodeUuid()), any());
    verify(campaignStorage, org.mockito.Mockito.times(4)).saveItem(any());
  }

  @Test
  void shouldAllowExactlyTheLegalTransitionsThroughLifecycle() {
    Map<CleanupCampaignState, Set<CleanupCampaignState>> legalTransitions =
                                                                          Map.of(CleanupCampaignState.DRAFT,
                                                                                 Set.of(CleanupCampaignState.DRY_RUN_RUNNING,
                                                                                        CleanupCampaignState.CANCELLED),
                                                                                 CleanupCampaignState.DRY_RUN_RUNNING,
                                                                                 Set.of(CleanupCampaignState.SIMULATED,
                                                                                        CleanupCampaignState.CANCELLED),
                                                                                 CleanupCampaignState.SIMULATED,
                                                                                 Set.of(CleanupCampaignState.PUBLISHED,
                                                                                        CleanupCampaignState.CANCELLED),
                                                                                 CleanupCampaignState.PUBLISHED,
                                                                                 Set.of(CleanupCampaignState.LOCKED,
                                                                                        CleanupCampaignState.CANCELLED),
                                                                                 CleanupCampaignState.LOCKED,
                                                                                 Set.of(CleanupCampaignState.EXECUTING,
                                                                                        CleanupCampaignState.CANCELLED),
                                                                                 CleanupCampaignState.EXECUTING,
                                                                                 Set.of(CleanupCampaignState.COMPLETED,
                                                                                        CleanupCampaignState.CANCELLED),
                                                                                 // COMPLETED is no longer terminal:
                                                                                 // a RETRY re-enters EXECUTING
                                                                                 CleanupCampaignState.COMPLETED,
                                                                                 Set.of(CleanupCampaignState.EXECUTING),
                                                                                 CleanupCampaignState.CANCELLED,
                                                                                 Set.of());
    lenient().when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    for (CleanupCampaignState fromState : CleanupCampaignState.values()) {
      for (CleanupCampaignState targetState : CleanupCampaignState.values()) {
        CleanupCampaign campaign = campaign(fromState);
        if (legalTransitions.get(fromState).contains(targetState)) {
          CleanupCampaign transitioned = campaignLifecycle.transition(campaign, targetState, (path, eventType) -> {
          });
          assertEquals(targetState, transitioned.getState(), fromState + " -> " + targetState + " should be legal");
        } else {
          IllegalArgumentException exception =
                                             assertThrows(IllegalArgumentException.class,
                                                          () -> campaignLifecycle.transition(campaign,
                                                                                             targetState,
                                                                                             (path, eventType) -> {
                                                                                             }),
                                                          fromState + " -> " + targetState + " should be illegal");
          assertEquals(CLEANUP_INVALID_STATE_ERROR, exception.getMessage());
          assertEquals(fromState, campaign.getState(), "an illegal transition must not change the campaign state");
        }
      }
    }
  }

  @Test
  void shouldApplyTransitionSideEffectsThroughLifecycle() {
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // Entry of PUBLISHED: persist, register the observation listener, notify
    campaignLifecycle.transition(campaign(CleanupCampaignState.SIMULATED), CleanupCampaignState.PUBLISHED, (path, type) -> {
    });
    verify(campaignStorage).saveCampaign(any());
    verify(cleanupJcrStorage).registerObservationListener(any());
    verify(cleanupJcrStorage, never()).unregisterObservationListener();
    verify(webSocketService).sendToAdministrators(any());
    reset(campaignStorage, cleanupJcrStorage, webSocketService);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // Exit of PUBLISHED (grace deadline): unregister the observation listener
    campaignLifecycle.transition(campaign(CleanupCampaignState.PUBLISHED), CleanupCampaignState.LOCKED);
    verify(cleanupJcrStorage).unregisterObservationListener();
    verify(cleanupJcrStorage, never()).registerObservationListener(any());
    verify(webSocketService).sendToAdministrators(any());
    reset(campaignStorage, cleanupJcrStorage, webSocketService);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // Cancellation from PUBLISHED: unregister the observation listener
    campaignLifecycle.transition(campaign(CleanupCampaignState.PUBLISHED), CleanupCampaignState.CANCELLED);
    verify(cleanupJcrStorage).unregisterObservationListener();
    reset(campaignStorage, cleanupJcrStorage, webSocketService);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // Cancellation from LOCKED: never leaks the observation listener either
    campaignLifecycle.transition(campaign(CleanupCampaignState.LOCKED), CleanupCampaignState.CANCELLED);
    verify(cleanupJcrStorage).unregisterObservationListener();
    reset(campaignStorage, cleanupJcrStorage, webSocketService);
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // Plain transition: persisted and notified, no listener side effect
    campaignLifecycle.transition(campaign(CleanupCampaignState.EXECUTING), CleanupCampaignState.COMPLETED);
    verify(campaignStorage).saveCampaign(any());
    verify(webSocketService).sendToAdministrators(any());
    verify(cleanupJcrStorage, never()).registerObservationListener(any());
    verify(cleanupJcrStorage, never()).unregisterObservationListener();
  }

  @Test
  void shouldRejectEnteringPublishedWithoutObservationCallback() {
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));
    CleanupCampaign campaign = campaign(CleanupCampaignState.SIMULATED);

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignLifecycle.transition(campaign,
                                                                                         CleanupCampaignState.PUBLISHED));

    assertEquals("cleanup.observationCallbackMandatory", exception.getMessage());
  }

  @Test
  void shouldUnregisterObservationWhenCancellingLockedCampaign() throws ObjectNotFoundException {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.LOCKED));
    when(campaignStorage.saveCampaign(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.cancelCampaign(CAMPAIGN_ID);

    ArgumentCaptor<CleanupCampaign> captor = ArgumentCaptor.forClass(CleanupCampaign.class);
    verify(campaignStorage).saveCampaign(captor.capture());
    assertEquals(CleanupCampaignState.CANCELLED, captor.getValue().getState());
    assertTrue(captor.getValue().getCompletedDate() > 0);
    verify(cleanupJcrStorage).unregisterObservationListener();
  }

  @Test
  void shouldResumeInterruptedWorkersAtRestartRecovery() throws ObjectNotFoundException {
    CleanupCampaign scanningCampaign = campaign(CleanupCampaignState.DRY_RUN_RUNNING);
    CleanupCampaign executingCampaign = campaign(CleanupCampaignState.EXECUTING);
    executingCampaign.setId(2L);
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED))).thenReturn(List.of());
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.DRY_RUN_RUNNING)))
                                                                                             .thenReturn(List.of(scanningCampaign));
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.EXECUTING)))
                                                                                       .thenReturn(List.of(executingCampaign));

    campaignService.recoverAfterRestart();

    verify(scanService).startScan(CAMPAIGN_ID);
    verify(executionService).resumeExecution(2L);
    verify(cleanupJcrStorage, never()).registerObservationListener(any());
  }

  @Test
  void shouldResumeStalledWorkersOnWatchdogTick() throws ObjectNotFoundException {
    CleanupCampaign scanningCampaign = campaign(CleanupCampaignState.DRY_RUN_RUNNING);
    CleanupCampaign executingCampaign = campaign(CleanupCampaignState.EXECUTING);
    executingCampaign.setId(2L);
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.DRY_RUN_RUNNING)))
                                                                                             .thenReturn(List.of(scanningCampaign));
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.EXECUTING)))
                                                                                       .thenReturn(List.of(executingCampaign));

    campaignService.resumeStalledWorkers();

    // The watchdog re-invokes the exact resume path used at startup recovery:
    // the workers' running-set guard makes it a no-op when the worker is alive
    verify(scanService).startScan(CAMPAIGN_ID);
    verify(executionService).resumeExecution(2L);
  }

  @Test
  void shouldKeepResumingRemainingWorkersWhenOneResumeFails() throws ObjectNotFoundException {
    CleanupCampaign scanningCampaign = campaign(CleanupCampaignState.DRY_RUN_RUNNING);
    CleanupCampaign otherScanningCampaign = campaign(CleanupCampaignState.DRY_RUN_RUNNING);
    otherScanningCampaign.setId(2L);
    CleanupCampaign executingCampaign = campaign(CleanupCampaignState.EXECUTING);
    executingCampaign.setId(3L);
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.DRY_RUN_RUNNING)))
                                                                                             .thenReturn(List.of(scanningCampaign,
                                                                                                                 otherScanningCampaign));
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.EXECUTING)))
                                                                                       .thenReturn(List.of(executingCampaign));
    doThrow(new IllegalStateException("Resume failed")).when(scanService).startScan(CAMPAIGN_ID);

    campaignService.resumeStalledWorkers();

    // A failing resume never prevents the remaining stalled workers' resume
    verify(scanService).startScan(2L);
    verify(executionService).resumeExecution(3L);
  }

  @Test
  void shouldReRegisterObservationListenerAtRestartRecoveryWhenCampaignPublished() throws ObjectNotFoundException {
    when(campaignStorage.getCampaignsByStates(anyList())).thenReturn(List.of());
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED)))
                                                                                       .thenReturn(List.of(campaign(CleanupCampaignState.PUBLISHED)));
    when(cleanupJcrStorage.registerObservationListener(any())).thenReturn(true);

    campaignService.recoverAfterRestart();

    verify(cleanupJcrStorage).registerObservationListener(any());
    verify(scanService, never()).startScan(anyLong());
    verify(executionService, never()).resumeExecution(anyLong());
  }

  @Test
  void shouldServeTerminalAggregatesFromSummaryOnceItemsPurged() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    campaign.setSummaryJson("""
                {"candidateCount":3,"reclaimableBytes":1024,"reclaimedBytes":123456,"purgedCount":42}
        """);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.hasItems(CAMPAIGN_ID)).thenReturn(false);

    CleanupCampaign served = campaignService.getCampaign(CAMPAIGN_ID);

    assertFalse(served.isItemsRetained());
    assertEquals(3L, served.getCandidateCount());
    assertEquals(1024L, served.getReclaimableBytes());
    assertEquals(123456L, served.getReclaimedBytes());
    verify(campaignStorage, never()).sumReclaimedBytes(CAMPAIGN_ID);
  }

  @Test
  void shouldServeLiveAggregatesWhileItemsRetained() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    campaign.setSummaryJson("""
                {"reclaimedBytes":123456}
        """);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.hasItems(CAMPAIGN_ID)).thenReturn(true);
    when(campaignStorage.sumReclaimedBytes(CAMPAIGN_ID)).thenReturn(99L);

    CleanupCampaign served = campaignService.getCampaign(CAMPAIGN_ID);

    assertTrue(served.isItemsRetained());
    assertEquals(99L, served.getReclaimedBytes());
  }

  @Test
  void shouldExposeArchiveAvailableWhileItemsRetainedOrArchived() {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    campaign.setItemsRetained(true);
    assertTrue(CleanupEntityBuilder.build(campaign).isArchiveAvailable());
    campaign.setItemsRetained(false);
    assertFalse(CleanupEntityBuilder.build(campaign).isArchiveAvailable());
    campaign.setArchiveFileId(3L);
    assertTrue(CleanupEntityBuilder.build(campaign).isArchiveAvailable());
  }

  @Test
  void shouldMarkItemGoneAndThrowNotFoundWhenKeptNodeMissing() {
    mockPublishedCampaignWithItem(CleanupItemState.CANDIDATE);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(cleanupJcrStorage.addExemptionMixin(NODE_UUID, USERNAME)).thenReturn(CleanupExemptionResult.NOT_FOUND);
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    assertThrows(ObjectNotFoundException.class, () -> campaignService.keepItem(ITEM_ID, USERNAME));

    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(CleanupItemState.GONE, captor.getValue().getState());
  }

  @Test
  void shouldLeaveItemStateUntouchedOnTransientKeepFailure() {
    mockPublishedCampaignWithItem(CleanupItemState.CANDIDATE);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(cleanupJcrStorage.addExemptionMixin(NODE_UUID, USERNAME)).thenReturn(CleanupExemptionResult.FAILED);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                   () -> campaignService.keepItem(ITEM_ID, USERNAME));

    assertEquals("cleanup.keepFailed", exception.getMessage());
    // A transient JCR write failure never discards the user's keep decision:
    // no state change (never GONE), so the keep stays retryable
    verify(campaignStorage, never()).saveItem(any());
  }

  @Test
  void shouldContinueBulkKeepPastIndividualFailures() {
    // Item 10 is unknown (fails), item 11 is kept normally
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(null);
    CleanupCampaignItem otherItem = item(CleanupItemState.CANDIDATE);
    otherItem.setId(11L);
    when(campaignStorage.getItem(11L)).thenReturn(otherItem);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.PUBLISHED));
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(cleanupJcrStorage.addExemptionMixin(NODE_UUID, USERNAME)).thenReturn(CleanupExemptionResult.ADDED);
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CleanupBulkResult result = campaignService.keepItems(List.of(ITEM_ID, 11L), USERNAME);

    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(11L, captor.getValue().getId(), "The failing item must not prevent the remaining keeps");
    assertEquals(CleanupItemState.EXEMPTED, captor.getValue().getState());
    // The partial failure is REPORTED, never swallowed into a blanket success
    assertEquals(1, result.getSucceeded());
    assertEquals(1, result.getFailures().size());
    assertEquals(ITEM_ID, result.getFailures().get(0).getItemId());
    assertEquals("cleanup.itemNotFound", result.getFailures().get(0).getReason());
  }

  @Test
  void shouldReportEveryFailureWhenNoBulkKeepSucceeds() {
    // Nothing was kept: the UI must be able to warn instead of telling the user
    // their files are safe
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(null);
    when(campaignStorage.getItem(11L)).thenReturn(null);

    CleanupBulkResult result = campaignService.keepItems(List.of(ITEM_ID, 11L), USERNAME);

    assertEquals(0, result.getSucceeded());
    assertEquals(2, result.getFailures().size());
    assertEquals(List.of(ITEM_ID, 11L), result.getFailures().stream().map(CleanupBulkFailure::getItemId).toList());
    verify(campaignStorage, never()).saveItem(any());
  }

  @Test
  void shouldReportTheAclFailureReasonOfARefusedBulkKeep() {
    CleanupCampaignItem foreignItem = item(CleanupItemState.CANDIDATE);
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(foreignItem);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", "mary"));

    CleanupBulkResult result = campaignService.keepItems(List.of(ITEM_ID), USERNAME);

    assertEquals(0, result.getSucceeded());
    assertEquals(1, result.getFailures().size());
    String reason = result.getFailures().get(0).getReason();
    assertNotNull(reason, "A refused keep must carry a reason");
    // A LOCALIZABLE message code, not the IllegalAccessException message: that
    // one is a raw English sentence naming the user and the owning space —
    // internal detail the client must never receive, and the UI can't translate
    assertEquals(CleanupCampaignService.NOT_OWNER_FAILURE_CODE, reason);
    assertFalse(reason.contains(USERNAME), "No internal sentence naming the user may reach the client");
  }

  @Test
  void shouldReportEveryFailureWhenNoBulkUnkeepSucceeds() {
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(null);

    CleanupBulkResult result = campaignService.unkeepItems(List.of(ITEM_ID), USERNAME);

    assertEquals(0, result.getSucceeded());
    assertEquals(1, result.getFailures().size());
    assertEquals("cleanup.itemNotFound", result.getFailures().get(0).getReason());
  }

  @Test
  void shouldRefreshFileItemOnPropertyChangeEventBelowIt() {
    // A PROPERTY_CHANGED event carries the PROPERTY's path, a DESCENDANT of
    // the file item's path: the bidirectional touched-by query must be fed the
    // raw event path so the ancestor-chain match finds the file item above it
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED))).thenReturn(List.of(campaign));
    String propertyPath = "/Users/j___/john/Private/file.pdf/jcr:content/jcr:data"; // NOSONAR
    CleanupCampaignItem fileItem = item(CleanupItemState.CANDIDATE);
    fileItem.setPath("/Users/j___/john/Private/file.pdf");
    when(campaignStorage.getItemsTouchedByPath(CAMPAIGN_ID, propertyPath)).thenReturn(List.of(fileItem));
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID), any())).thenReturn(CleanupRevalidation.of(null));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.refreshCandidate(propertyPath, PROPERTY_CHANGED_EVENT);

    assertEquals(CleanupItemState.SPARED_BY_MODIFICATION, fileItem.getState());
    verify(campaignStorage).getItemsTouchedByPath(CAMPAIGN_ID, propertyPath);
    verify(campaignStorage).saveItem(fileItem);
  }

  @Test
  void shouldRefreshItemsBelowFolderEvent() {
    // A removed/moved FOLDER fires a single JCR event for the top-most node:
    // the touched-by query returns the candidate items BELOW the event path
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED))).thenReturn(List.of(campaign));
    String folderPath = "/Users/j___/john/Private/docs"; // NOSONAR
    CleanupCampaignItem firstItem = item(CleanupItemState.CANDIDATE);
    firstItem.setId(21L);
    firstItem.setNodeUuid("uuid-below-1");
    CleanupCampaignItem secondItem = item(CleanupItemState.CANDIDATE);
    secondItem.setId(22L);
    secondItem.setNodeUuid("uuid-below-2");
    when(campaignStorage.getItemsTouchedByPath(CAMPAIGN_ID, folderPath)).thenReturn(List.of(firstItem, secondItem));
    when(cleanupJcrStorage.revalidate(eq("uuid-below-1"), any())).thenReturn(CleanupRevalidation.gone());
    when(cleanupJcrStorage.revalidate(eq("uuid-below-2"), any())).thenReturn(CleanupRevalidation.gone());
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.refreshCandidate(folderPath, "NODE_REMOVED");

    assertEquals(CleanupItemState.GONE, firstItem.getState());
    assertEquals(CleanupItemState.GONE, secondItem.getState());
    verify(campaignStorage, org.mockito.Mockito.times(2)).saveItem(any());
  }

  @Test
  void shouldLeaveCandidateUntouchedWhenRefreshRevalidationUnknown() {
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED))).thenReturn(List.of(campaign));
    CleanupCampaignItem candidateItem = item(CleanupItemState.CANDIDATE);
    when(campaignStorage.getItemsTouchedByPath(CAMPAIGN_ID, PATH))
                                                                  .thenReturn(List.of(candidateItem));
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID), any())).thenReturn(CleanupRevalidation.unknown());

    campaignService.refreshCandidate(PATH, PROPERTY_CHANGED_EVENT);

    // A transient JCR read failure never spares the item: left untouched
    assertEquals(CleanupItemState.CANDIDATE, candidateItem.getState());
    verify(campaignStorage, never()).saveItem(any());
  }

  @Test
  void shouldUnkeepItemBackToCandidateWhenRevalidationUnknown() throws ObjectNotFoundException, IllegalAccessException {
    mockPublishedCampaignWithItem(CleanupItemState.EXEMPTED);
    when(identityManager.getIdentity(5l)).thenReturn(userIdentity("5", USERNAME));
    when(cleanupJcrStorage.removeExemptionMixin(NODE_UUID)).thenReturn(CleanupExemptionResult.ADDED);
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID), any())).thenReturn(CleanupRevalidation.unknown());
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.unkeepItem(ITEM_ID, USERNAME);

    // The mixin IS removed, so an unknown revalidation puts the item back
    // under cleanup (never SPARED/GONE on doubt): the execution-time
    // revalidation remains the correctness guarantee
    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(CleanupItemState.CANDIDATE, captor.getValue().getState());
  }

  @Test
  void shouldRejectCampaignCreationOnOutOfBoundsParams() {
    assertCreateRejected(new CleanupParams(0, 1048576L, 7, 5, List.of(), 200), "cleanup.invalidPeriodMonths");
    assertCreateRejected(new CleanupParams(6, -1L, 7, 5, List.of(), 200), "cleanup.invalidMinFileSize");
    assertCreateRejected(new CleanupParams(6, 1048576L, -1, 5, List.of(), 200), "cleanup.invalidGraceDays");
    assertCreateRejected(new CleanupParams(6, 1048576L, 7, 0, List.of(), 200), "cleanup.invalidMaxVersionsPerFile");
    verify(campaignStorage, never()).createCampaign(any());
  }

  @Test
  void shouldAcceptCampaignCreationWithZeroGraceDays() throws ObjectNotFoundException {
    // Architect decision: a zero grace period IS valid (deadline elapses at
    // publication), the bounds validation must never forbid it
    when(settingService.getEffectiveParams(any())).thenReturn(new CleanupParams(6, 1048576L, 0, 5, List.of(), 200));
    CleanupCampaign createdCampaign = campaign(CleanupCampaignState.DRAFT);
    when(campaignStorage.createCampaign(any())).thenReturn(createdCampaign);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(createdCampaign);

    assertNotNull(campaignService.createCampaign("Zero grace", new CleanupParams()));
    verify(scanService).startScan(CAMPAIGN_ID);
  }

  @Test
  void shouldBatchItemAggregatesOnCampaignsList() {
    CleanupCampaign liveCampaign = campaign(CleanupCampaignState.PUBLISHED);
    CleanupCampaign purgedCampaign = campaign(CleanupCampaignState.COMPLETED);
    purgedCampaign.setId(2L);
    purgedCampaign.setSummaryJson("""
                {"candidateCount":3,"reclaimableBytes":1024,"reclaimedBytes":123456}
        """);
    when(campaignStorage.getCampaigns(any())).thenReturn(List.of(liveCampaign, purgedCampaign));
    CleanupCampaignAggregates liveAggregates = new CleanupCampaignAggregates();
    liveAggregates.setItemsRetained(true);
    liveAggregates.setCandidateCount(7L);
    liveAggregates.setReclaimableBytes(2048L);
    liveAggregates.setReclaimedBytes(99L);
    // The purged campaign has no item rows anymore: absent from the map
    when(campaignStorage.getItemAggregates(List.of(CAMPAIGN_ID, 2L))).thenReturn(Map.of(CAMPAIGN_ID, liveAggregates));

    List<CleanupCampaign> campaigns = campaignService.getCampaigns();

    assertEquals(2, campaigns.size());
    assertTrue(campaigns.get(0).isItemsRetained());
    assertEquals(7L, campaigns.get(0).getCandidateCount());
    assertEquals(2048L, campaigns.get(0).getReclaimableBytes());
    assertEquals(99L, campaigns.get(0).getReclaimedBytes());
    // Terminal campaign without item rows: served from its summary snapshot
    assertFalse(campaigns.get(1).isItemsRetained());
    assertEquals(3L, campaigns.get(1).getCandidateCount());
    assertEquals(1024L, campaigns.get(1).getReclaimableBytes());
    assertEquals(123456L, campaigns.get(1).getReclaimedBytes());
    // No per-campaign aggregate query on the list path (N+1 fixed)
    verify(campaignStorage, never()).hasItems(anyLong());
    verify(campaignStorage, never()).countItemsByState(anyLong(), any());
    verify(campaignStorage, never()).sumReclaimableBytesByState(anyLong(), any());
    verify(campaignStorage, never()).sumReclaimedBytes(anyLong());
  }

  @Test
  void shouldAssembleComparisonFromTheThreeSetBasedBuckets() throws ObjectNotFoundException {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.SIMULATED));
    CleanupCampaign otherCampaign = campaign(CleanupCampaignState.COMPLETED);
    otherCampaign.setId(2L);
    when(campaignStorage.getCampaign(2L)).thenReturn(otherCampaign);
    // Deliberately distinct values per bucket: a bucket wired to the wrong DTO
    // field must fail this test
    when(campaignStorage.getPersistingItems(CAMPAIGN_ID, 2L)).thenReturn(new CleanupComparisonBucket(3L, 300L));
    when(campaignStorage.getNewItems(CAMPAIGN_ID, 2L)).thenReturn(new CleanupComparisonBucket(1L, 100L));
    when(campaignStorage.getGoneItems(CAMPAIGN_ID, 2L)).thenReturn(new CleanupComparisonBucket(2L, 200L));

    CleanupComparison comparison = campaignService.compareCampaigns(CAMPAIGN_ID, 2L);

    assertEquals(CAMPAIGN_ID, comparison.getBaseCampaignId());
    assertEquals(2L, comparison.getOtherCampaignId());
    assertEquals(3L, comparison.getPersistingCount());
    assertEquals(300L, comparison.getPersistingBytes());
    assertEquals(1L, comparison.getNewCount());
    assertEquals(100L, comparison.getNewBytes());
    assertEquals(2L, comparison.getGoneCount());
    assertEquals(200L, comparison.getGoneBytes());
  }

  @Test
  void shouldThrowNotFoundWhenComparingWithAnUnknownCampaign() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.SIMULATED));
    when(campaignStorage.getCampaign(404L)).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> campaignService.compareCampaigns(CAMPAIGN_ID, 404L));
    // The existence of both campaigns is settled BEFORE any aggregate query
    verify(campaignStorage, never()).getPersistingItems(anyLong(), anyLong());
    verify(campaignStorage, never()).getNewItems(anyLong(), anyLong());
    verify(campaignStorage, never()).getGoneItems(anyLong(), anyLong());
  }

  @Test
  void shouldArchiveOnlyTheCampaignsBeyondRetentionOrderedByCompletionDate() throws Exception {
    when(settingService.getReportRetentionCampaigns()).thenReturn(2);
    // Fed in a scrambled order on purpose: a wrong sort would archive (and
    // purge
    // the item rows of) the WRONG campaigns' reports
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.COMPLETED, CleanupCampaignState.CANCELLED)))
                                                                                                                       .thenReturn(List.of(terminalCampaign(101L,
                                                                                                                                                            1000L),
                                                                                                                                           terminalCampaign(104L,
                                                                                                                                                            4000L),
                                                                                                                                           terminalCampaign(102L,
                                                                                                                                                            2000L),
                                                                                                                                           terminalCampaign(103L,
                                                                                                                                                            3000L)));
    when(campaignStorage.hasItems(anyLong())).thenReturn(true);
    when(campaignStorage.getItemsPage(anyLong(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
    when(fileService.writeFile(any())).thenAnswer(invocation -> archiveFileItem());

    campaignService.applyRetention();

    // The 2 most recently completed keep their item detail; the 2 oldest are
    // archived then purged
    ArgumentCaptor<CleanupCampaign> captor = ArgumentCaptor.forClass(CleanupCampaign.class);
    verify(campaignStorage, org.mockito.Mockito.times(2)).saveCampaign(captor.capture());
    assertEquals(List.of(102L, 101L), captor.getAllValues().stream().map(CleanupCampaign::getId).toList());
    verify(campaignStorage).deleteItems(102L);
    verify(campaignStorage).deleteItems(101L);
    verify(campaignStorage, never()).deleteItems(103L);
    verify(campaignStorage, never()).deleteItems(104L);
  }

  @Test
  void shouldArchiveThenReferenceThenPurgeItemRowsInThatOrder() throws Exception {
    when(settingService.getReportRetentionCampaigns()).thenReturn(0);
    CleanupCampaign campaign = terminalCampaign(101L, 1000L);
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.COMPLETED, CleanupCampaignState.CANCELLED)))
                                                                                                                       .thenReturn(List.of(campaign));
    when(campaignStorage.hasItems(101L)).thenReturn(true);
    when(campaignStorage.getItemsPage(eq(101L), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
    when(fileService.writeFile(any())).thenReturn(archiveFileItem());

    campaignService.applyRetention();

    // The item rows are only dropped AFTER the archive is written and its id
    // referenced by the campaign: no window where the report is unreachable
    InOrder inOrder = inOrder(fileService, campaignStorage);
    inOrder.verify(fileService).writeFile(any());
    inOrder.verify(campaignStorage).saveCampaign(campaign);
    inOrder.verify(campaignStorage).deleteItems(101L);
    assertEquals(77L, campaign.getArchiveFileId());
  }

  @Test
  void shouldKeepItemRowsWhenTheArchiveWriteFails() throws Exception {
    when(settingService.getReportRetentionCampaigns()).thenReturn(0);
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.COMPLETED, CleanupCampaignState.CANCELLED)))
                                                                                                                       .thenReturn(List.of(terminalCampaign(101L,
                                                                                                                                                            1000L)));
    when(campaignStorage.hasItems(101L)).thenReturn(true);
    when(campaignStorage.getItemsPage(eq(101L), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
    when(fileService.writeFile(any())).thenThrow(new IllegalStateException("Binary storage down"));

    campaignService.applyRetention();

    // NO DATA LOSS: a failed archive write never purges the item detail it was
    // supposed to preserve
    verify(campaignStorage, never()).deleteItems(anyLong());
    verify(campaignStorage, never()).saveCampaign(any());
  }

  @Test
  void shouldStreamTheLiveCsvReportOfARetainedCampaign() throws Exception {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.hasItems(CAMPAIGN_ID)).thenReturn(true);
    CleanupCampaignItem item = item(CleanupItemState.PURGED);
    item.setPath("/Users/j___/john/Private/report,final.pdf");
    item.setReclaimedBytes(4096L);
    when(campaignStorage.getItemsPage(eq(CAMPAIGN_ID),
                                      any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(item)));

    campaignService.checkArchiveAvailable(CAMPAIGN_ID);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    campaignService.writeArchiveCsv(CAMPAIGN_ID, outputStream);

    String csv = outputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
    // The historical columns keep their POSITION and the ones added since are
    // APPENDED: a consumer parsing the report by index must not break
    assertTrue(csv.startsWith(CSV_HEADER), "The streamed report must open with the CSV header");
    assertTrue(CSV_HEADER.startsWith("nodeUuid,path,ownerIdentityId,action,state,fileSize,versionsSize,reclaimedBytes,failureReason,"),
               "The historical columns must keep their position");
    assertTrue(CSV_HEADER.endsWith(",ownerName,lastModifiedDate,createdDate,attemptCount,failureDetail\n"),
               "The columns added since must be appended, in that order");
    assertTrue(csv.contains("\"/Users/j___/john/Private/report,final.pdf\""),
               "A comma-carrying path must stay quoted in the streamed rows");
    assertTrue(csv.contains(",4096,"));
    // Nothing is read from the FileService while the item rows are retained
    verify(fileService, never()).getFileInfo(anyLong());
  }

  @Test
  void shouldStreamTheStoredArchiveOnceItemRowsArePurged() throws Exception {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    campaign.setArchiveFileId(77L);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.hasItems(CAMPAIGN_ID)).thenReturn(false);
    when(fileService.getFileInfo(77L)).thenReturn(archiveFileItem().getFileInfo());
    when(fileService.getFile(77L)).thenReturn(new org.exoplatform.commons.file.model.FileItem(77L,
                                                                                              "archive.csv",
                                                                                              "text/csv",
                                                                                              "documentsCleanup",
                                                                                              8,
                                                                                              new java.util.Date(),
                                                                                              "system",
                                                                                              false,
                                                                                              new java.io.ByteArrayInputStream("archived".getBytes(java.nio.charset.StandardCharsets.UTF_8))));

    campaignService.checkArchiveAvailable(CAMPAIGN_ID);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    campaignService.writeArchiveCsv(CAMPAIGN_ID, outputStream);

    assertEquals("archived", outputStream.toString(java.nio.charset.StandardCharsets.UTF_8));
    verify(campaignStorage, never()).getItemsPage(anyLong(), any());
  }

  @Test
  void shouldRejectTheArchiveDownloadBeforeStreamingWhenNoReportLeft() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.hasItems(CAMPAIGN_ID)).thenReturn(false);

    // Settled BEFORE a single byte is written, so the 404 can still be sent
    assertEquals("cleanup.archiveNotFound",
                 assertThrows(ObjectNotFoundException.class,
                              () -> campaignService.checkArchiveAvailable(CAMPAIGN_ID)).getMessage());
  }

  @Test
  void shouldRejectTheArchiveDownloadWhenTheStoredFileIsGone() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    campaign.setArchiveFileId(77L);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.hasItems(CAMPAIGN_ID)).thenReturn(false);
    when(fileService.getFileInfo(77L)).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> campaignService.checkArchiveAvailable(CAMPAIGN_ID));
  }

  @Test
  void shouldResolveEveryManagedSpaceAcrossPagesForMyItems() throws Exception {
    // REGRESSION: the managed spaces used to be read with a single bounded
    // load(0, 100), so a user managing more spaces never saw the candidates of
    // the spaces past the cap — silently, with no error anywhere
    mockPublishedCampaignForUserReview();
    ListAccess<Space> managerSpaces = mockManagedSpaces(250);
    when(campaignStorage.getItemsByOwners(eq(CAMPAIGN_ID), anyList(), any(), any()))
                                                                                   .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(item(CleanupItemState.CANDIDATE))));

    Page<CleanupCampaignItem> page = campaignService.getMyItems(USERNAME, null, PageRequest.of(0, 20));

    assertEquals(1, page.getContent().size());
    // 250 spaces, 100 per page: three loads, the last one asking for the
    // remainder only, never past the reported total
    InOrder loadOrder = inOrder(managerSpaces);
    loadOrder.verify(managerSpaces).load(0, 100);
    loadOrder.verify(managerSpaces).load(100, 100);
    loadOrder.verify(managerSpaces).load(200, 50);
    verify(managerSpaces, never()).load(eq(250), anyInt());
    List<Long> ownerIdentityIds = captureMyItemsOwnerIds();
    assertEquals(251,
                 ownerIdentityIds.size(),
                 "The user's own identity plus EVERY managed space identity, none truncated at the page size");
    assertTrue(ownerIdentityIds.contains(5L), "The user's own identity must be resolved");
    assertTrue(ownerIdentityIds.contains(1249L), "A space beyond the first page must be resolved too");
  }

  @Test
  void shouldHandTheWholeOwnerListToTheStorageEvenBeyondTheInClauseCap() throws Exception {
    // The service must NEVER trim the resolved owner list to fit a database's IN
    // limit — trimming would silently hide the candidates of the spaces past the
    // cut. Chunking is the Storage layer's job (CleanupCampaignStorageTest pins
    // the per-chunk queries and the merge)
    mockPublishedCampaignForUserReview();
    mockManagedSpaces(1000);
    when(campaignStorage.getItemsByOwners(eq(CAMPAIGN_ID), anyList(), any(), any()))
                                                                                   .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(item(CleanupItemState.CANDIDATE))));

    campaignService.getMyItems(USERNAME, null, PageRequest.of(0, 20));

    assertEquals(1001, captureMyItemsOwnerIds().size(), "The user's own identity plus the 1000 managed spaces, untrimmed");
  }

  @Test
  void shouldServeTheLatestCompletedCampaignWhenNoneIsActive() throws Exception {
    // No active campaign: the user still gets their outcome, from the MOST
    // RECENTLY completed campaign — the older one must never win (a reversed
    // comparator picks id 71 here)
    when(campaignStorage.getCampaignsByStates(ACTIVE_STATES)).thenReturn(List.of());
    when(campaignStorage.getCampaignsByStates(COMPLETED_STATES)).thenReturn(List.of(terminalCampaign(71L, 1_000L),
                                                                                   terminalCampaign(72L, 5_000L)));
    when(identityManager.getOrCreateUserIdentity(USERNAME)).thenReturn(userIdentity("5", USERNAME));
    mockNoManagedSpaces();

    CleanupUserSummary summary = campaignService.getMyItemsSummary(USERNAME);

    assertEquals(72L, summary.getCampaignId(), "The campaign completed LAST is the relevant one");
    assertEquals(CleanupCampaignState.COMPLETED, summary.getState());
    assertNotNull(summary.getOutcome(), "A completed campaign carries the user's personal outcome");
  }

  @Test
  void shouldThrowNotFoundWhenNoCampaignIsRelevantForTheUser() {
    // Neither active nor ever completed: the review endpoints must answer 404
    // with a localizable code, not an empty page pretending nothing is scheduled
    when(campaignStorage.getCampaignsByStates(ACTIVE_STATES)).thenReturn(List.of());
    when(campaignStorage.getCampaignsByStates(COMPLETED_STATES)).thenReturn(List.of());

    ObjectNotFoundException exception = assertThrows(ObjectNotFoundException.class,
                                                     () -> campaignService.getMyItemsSummary(USERNAME));

    assertEquals("cleanup.noRelevantCampaign", exception.getMessage());
    // The owner identities are never even resolved: the campaign lookup fails first
    verify(campaignStorage, never()).countItemsByOwnersAndState(anyLong(), anyList(), any());
  }

  @Test
  void shouldHandTheCallersPageableAndSearchStraightToTheStorageForMyItems() throws Exception {
    // The review table is server-sorted: the ordering is the REST layer's
    // (validated field + stable tiebreaker), so the service must forward the
    // Pageable UNTOUCHED instead of imposing a fileSize DESC of its own
    mockPublishedCampaignForUserReview();
    mockManagedSpaces(1);
    Pageable pageable = PageRequest.of(3, 50, Sort.by(Sort.Direction.ASC, "state").and(Sort.by(Sort.Direction.ASC, "path")));
    when(campaignStorage.getItemsByOwners(eq(CAMPAIGN_ID), anyList(), eq("invoice"), eq(pageable)))
                                                                                                  .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(item(CleanupItemState.CANDIDATE))));

    Page<CleanupCampaignItem> page = campaignService.getMyItems(USERNAME, "invoice", pageable);

    assertEquals(1, page.getContent().size());
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(campaignStorage).getItemsByOwners(eq(CAMPAIGN_ID), anyList(), eq("invoice"), pageableCaptor.capture());
    assertEquals(pageable, pageableCaptor.getValue());
  }

  @Test
  void shouldForwardTheSearchTermToTheStorageForCampaignItems() throws Exception {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.SIMULATED));
    Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "fileSize"));
    when(campaignStorage.getItems(eq(CAMPAIGN_ID),
                                  eq((Long) null),
                                  eq(CleanupItemState.CANDIDATE),
                                  eq((CleanupAction) null),
                                  eq((Long) null),
                                  eq("q1"),
                                  eq(pageable))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(item(CleanupItemState.CANDIDATE))));

    Page<CleanupCampaignItem> page = campaignService.getCampaignItems(CAMPAIGN_ID,
                                                                     null,
                                                                     CleanupItemState.CANDIDATE,
                                                                     null,
                                                                     null,
                                                                     "q1",
                                                                     pageable);

    assertEquals(1, page.getContent().size());
    // The search composes with the other filters, all of them reaching the
    // single storage query
    verify(campaignStorage).getItems(eq(CAMPAIGN_ID),
                                     eq((Long) null),
                                     eq(CleanupItemState.CANDIDATE),
                                     eq((CleanupAction) null),
                                     eq((Long) null),
                                     eq("q1"),
                                     eq(pageable));
  }

  @Test
  void shouldScopeMyItemsSummaryToTheResolvedOwnerIdentityIds() throws Exception {
    mockPublishedCampaignForUserReview();
    mockManagedSpaces(150);
    when(campaignStorage.countItemsByOwnersAndState(eq(CAMPAIGN_ID), anyList(), eq(CleanupItemState.CANDIDATE))).thenReturn(4L);
    when(campaignStorage.countItemsByOwnersAndState(eq(CAMPAIGN_ID), anyList(), eq(CleanupItemState.EXEMPTED))).thenReturn(1L);
    when(campaignStorage.sumReclaimableBytesByOwnersAndState(eq(CAMPAIGN_ID),
                                                             anyList(),
                                                             eq(CleanupItemState.CANDIDATE))).thenReturn(8192L);

    CleanupUserSummary summary = campaignService.getMyItemsSummary(USERNAME);

    assertEquals(CAMPAIGN_ID, summary.getCampaignId());
    assertEquals(4, summary.getCandidateCount());
    assertEquals(1, summary.getKeptCount());
    assertEquals(8192L, summary.getCandidateBytes());
    // Every counter is scoped to the SAME resolved owner ids: a summary computed
    // on a truncated list would contradict the list the user is shown
    ArgumentCaptor<List<Long>> ownersCaptor = ArgumentCaptor.forClass(List.class);
    verify(campaignStorage, atLeastOnce()).countItemsByOwnersAndState(eq(CAMPAIGN_ID), ownersCaptor.capture(), any());
    assertEquals(151, ownersCaptor.getValue().size());
    assertTrue(ownersCaptor.getValue().contains(1149L), "A space beyond the first page must be counted too");
  }

  @Test
  void shouldStreamTheLiveCsvReportPageByPageUntilTheLastPage() throws Exception {
    // The streaming loop is a do/while over getItemsPage: with a single-page
    // stub it iterates exactly once and the paging is never actually exercised
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.hasItems(CAMPAIGN_ID)).thenReturn(true);
    CleanupCampaignItem firstPageItem = item(CleanupItemState.PURGED);
    firstPageItem.setNodeUuid("uuid-page-1");
    CleanupCampaignItem secondPageItem = item(CleanupItemState.PURGED);
    secondPageItem.setNodeUuid("uuid-page-2");
    when(campaignStorage.getItemsPage(eq(CAMPAIGN_ID), any())).thenAnswer(invocation -> {
      org.springframework.data.domain.Pageable pageable = invocation.getArgument(1);
      // 1500 rows over pages of 1000: page 0 hasNext, page 1 is the last
      return new org.springframework.data.domain.PageImpl<>(List.of(pageable.getPageNumber() == 0 ? firstPageItem :
                                                                                                 secondPageItem),
                                                            pageable,
                                                            1500L);
    });

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    campaignService.writeArchiveCsv(CAMPAIGN_ID, outputStream);

    String csv = outputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(csv.contains("uuid-page-1"), "The first page rows must be streamed");
    assertTrue(csv.contains("uuid-page-2"), "The loop must keep reading while the page has a next one");
    verify(campaignStorage, times(2)).getItemsPage(eq(CAMPAIGN_ID), any());
  }

  @Test
  void shouldQuoteAndDoubleTheQuotesOfCsvValues() throws Exception {
    // Paths are USER-CONTROLLED: an unescaped quote or newline would break the
    // row structure of the whole report
    CleanupCampaignItem item = item(CleanupItemState.SKIPPED);
    item.setPath("/Users/j___/john/Private/say \"hi\".pdf");
    item.setFailureReason("cleanup.deleteError: it went \"wrong\"");

    String csv = streamCsvOf(item);

    assertTrue(csv.contains("\"/Users/j___/john/Private/say \"\"hi\"\".pdf\""),
               "A quote-carrying value must be quoted with its quotes DOUBLED");
    assertTrue(csv.contains("\"cleanup.deleteError: it went \"\"wrong\"\"\""),
               "The failure reason goes through the same escaping");
  }

  @Test
  void shouldQuoteCsvValuesCarryingANewline() throws Exception {
    CleanupCampaignItem item = item(CleanupItemState.PURGED);
    item.setPath("/Users/j___/john/Private/line1\nline2.pdf");

    String csv = streamCsvOf(item);

    assertTrue(csv.contains("\"/Users/j___/john/Private/line1\nline2.pdf\""),
               "A newline-carrying value must be quoted, so the embedded newline can't be read as a row separator");
  }

  @Test
  void shouldLeaveCsvValuesWithoutSpecialCharactersUnquoted() throws Exception {
    CleanupCampaignItem item = item(CleanupItemState.PURGED);
    item.setPath("/Users/j___/john/Private/plain.pdf");

    String csv = streamCsvOf(item);

    assertTrue(csv.contains(",/Users/j___/john/Private/plain.pdf,"), "A plain value must be written as-is");
  }

  @Test
  void shouldAppendTheResolvedOwnerNameAndIsoCandidacyDatesToTheCsvRows() throws Exception {
    CleanupCampaignItem item = item(CleanupItemState.CANDIDATE);
    item.setPath("/Users/j___/john/Private/report.pdf");
    item.setLastModifiedDate(1600000000000L);
    item.setCreatedDate(1500000000000L);
    Identity owner = userIdentity("5", USERNAME);
    // A display name is user data: it can carry the separator itself
    owner.getProfile().setProperty(org.exoplatform.social.core.identity.model.Profile.FULL_NAME, "John, Smith");
    when(identityManager.getIdentity(5L)).thenReturn(owner);

    String csv = streamCsvOf(item);

    assertTrue(csv.contains("\"John, Smith\""), "The owner display name must be exported, escaped like any other value");
    // ISO-8601 UTC, never a localized string: the CSV is a machine-readable
    // export, unlike the dates the UI renders
    assertTrue(csv.contains(",2020-09-13T12:26:40Z,2017-07-14T02:40:00Z,0,\n"),
               "The last-modified and creation dates must be appended as ISO-8601 UTC, in that order");
  }

  @Test
  void shouldResolveEachCsvOwnerNameOnlyOnceForTheWholeExport() throws Exception {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.hasItems(CAMPAIGN_ID)).thenReturn(true);
    CleanupCampaignItem firstPageItem = item(CleanupItemState.PURGED);
    firstPageItem.setNodeUuid("uuid-page-1");
    CleanupCampaignItem secondPageItem = item(CleanupItemState.PURGED);
    secondPageItem.setNodeUuid("uuid-page-2");
    // Both rows share owner 5: the export streams pages over potentially
    // millions of rows, so the identity lookups must be bounded by the number of
    // DISTINCT owners — and the memo must span the pages, not restart on each
    when(campaignStorage.getItemsPage(eq(CAMPAIGN_ID), any())).thenAnswer(invocation -> {
      Pageable pageable = invocation.getArgument(1);
      return new org.springframework.data.domain.PageImpl<>(List.of(pageable.getPageNumber() == 0 ? firstPageItem :
                                                                 secondPageItem),
                                                            pageable,
                                                            1500L);
    });
    when(identityManager.getIdentity(5L)).thenReturn(userIdentity("5", USERNAME));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    campaignService.writeArchiveCsv(CAMPAIGN_ID, outputStream);

    String csv = outputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
    assertEquals(2,
                 csv.split("," + USERNAME + ",", -1).length - 1,
                 "Both rows must carry the resolved owner name, the memo serving the second one");
    verify(identityManager, times(1)).getIdentity(5L);
  }

  @Test
  void shouldDegradeAnUnresolvableCsvOwnerToAnEmptyNameWithoutFailingTheExport() throws Exception {
    CleanupCampaignItem item = item(CleanupItemState.CANDIDATE);
    item.setPath("/Users/j___/john/Private/report.pdf");
    when(identityManager.getIdentity(5L)).thenThrow(new IllegalStateException("Identity storage unreachable"));

    String csv = streamCsvOf(item);

    String[] cells = csv.substring(csv.indexOf('\n') + 1).trim().split(",", -1);
    assertEquals(14, cells.length, "The row must keep every column of the header");
    assertEquals("", cells[9], "An unresolvable owner degrades to an EMPTY name, it never fails the export");
    assertEquals("", cells[10], "An unset date is exported empty");
    assertEquals("", cells[11]);
    assertEquals("0", cells[12], "An item never retried carries a zero attempt count");
    assertEquals("", cells[13], "An item with no failure detail exports an empty last column");
  }

  private String streamCsvOf(CleanupCampaignItem item) throws Exception {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.COMPLETED));
    when(campaignStorage.hasItems(CAMPAIGN_ID)).thenReturn(true);
    when(campaignStorage.getItemsPage(eq(CAMPAIGN_ID),
                                      any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(item)));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    campaignService.writeArchiveCsv(CAMPAIGN_ID, outputStream);
    return outputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Stubs the ACTIVE-states query EXACTLY, never anyList(): a lenient anyList()
   * matched the COMPLETED query too, so the latest-completed fallback and the
   * 404 branch of getUserVisibleCampaign were unreachable from every user-review
   * test — and this stubbing also pins that the active campaign is looked up
   * FIRST, by the active states.
   */
  private void mockPublishedCampaignForUserReview() {
    when(campaignStorage.getCampaignsByStates(ACTIVE_STATES)).thenReturn(List.of(campaign(CleanupCampaignState.PUBLISHED)));
    when(identityManager.getOrCreateUserIdentity(USERNAME)).thenReturn(userIdentity("5", USERNAME));
  }

  /**
   * A {@link ListAccess} of the given total, answering each {@code load(offset,
   * limit)} with exactly that window of distinct spaces. Each space resolves to
   * its own identity, numbered 1000, 1001... in resolution order, so an id past
   * the first page (e.g. 1249 of 250 spaces) proves the pagination really walked
   * that far.
   */
  @SuppressWarnings("unchecked")
  private ListAccess<Space> mockManagedSpaces(int total) throws Exception {
    ListAccess<Space> managerSpaces = org.mockito.Mockito.mock(ListAccess.class);
    when(spaceService.getManagerSpaces(USERNAME)).thenReturn(managerSpaces);
    when(managerSpaces.getSize()).thenReturn(total);
    when(managerSpaces.load(anyInt(), anyInt())).thenAnswer(invocation -> {
      int offset = invocation.getArgument(0);
      int limit = invocation.getArgument(1);
      Space[] spaces = new Space[limit];
      for (int i = 0; i < limit; i++) {
        Space space = new Space();
        // Space.setPrettyName runs the name through Utils.cleanString: no
        // separator to parse back, the identity ids are numbered on resolution
        space.setPrettyName("managed" + (offset + i));
        spaces[i] = space;
      }
      return spaces;
    });
    Map<String, Long> resolvedSpaceIdentityIds = new HashMap<>();
    when(identityManager.getOrCreateSpaceIdentity(anyString())).thenAnswer(invocation -> {
      String prettyName = invocation.getArgument(0);
      long identityId = resolvedSpaceIdentityIds.computeIfAbsent(prettyName,
                                                                 name -> 1000L + resolvedSpaceIdentityIds.size());
      return spaceIdentity(String.valueOf(identityId), prettyName);
    });
    return managerSpaces;
  }

  /** A user managing no space at all: only their own identity is resolved. */
  @SuppressWarnings("unchecked")
  private void mockNoManagedSpaces() throws Exception {
    ListAccess<Space> managerSpaces = org.mockito.Mockito.mock(ListAccess.class);
    when(spaceService.getManagerSpaces(USERNAME)).thenReturn(managerSpaces);
    when(managerSpaces.getSize()).thenReturn(0);
  }

  @SuppressWarnings("unchecked")
  private List<Long> captureMyItemsOwnerIds() {
    ArgumentCaptor<List<Long>> ownersCaptor = ArgumentCaptor.forClass(List.class);
    verify(campaignStorage).getItemsByOwners(eq(CAMPAIGN_ID), ownersCaptor.capture(), any(), any());
    return ownersCaptor.getValue();
  }

  private CleanupCampaign terminalCampaign(long id, long completedDate) {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    campaign.setId(id);
    campaign.setCompletedDate(completedDate);
    return campaign;
  }

  private org.exoplatform.commons.file.model.FileItem archiveFileItem() throws Exception {
    return new org.exoplatform.commons.file.model.FileItem(77L,
                                                           "cleanup-campaign.csv",
                                                           "text/csv",
                                                           "documentsCleanup",
                                                           0,
                                                           new java.util.Date(),
                                                           "system",
                                                           false,
                                                           null);
  }

  private void assertCreateRejected(CleanupParams effectiveParams, String expectedMessage) {
    when(settingService.getEffectiveParams(any())).thenReturn(effectiveParams);
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.createCampaign("Bad bounds",
                                                                                           new CleanupParams()));
    assertEquals(expectedMessage, exception.getMessage());
  }

  private void mockPublishedCampaignWithItem(CleanupItemState itemState) {
    when(campaignStorage.getItem(ITEM_ID)).thenReturn(item(itemState));
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.PUBLISHED));
  }

  @Test
  void shouldRequeueOnlyRetryableFailuresBelowTheAttemptBoundOnRetry() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.getCampaignsByStates(ACTIVE_STATES)).thenReturn(List.of());
    CleanupCampaignItem failedItem = skippedItem(51L, "cleanup.deleteError", 1L);
    failedItem.setReclaimedBytes(4096L);
    failedItem.setPurgedAt(7000L);
    when(campaignStorage.getRetryableFailures(eq(CAMPAIGN_ID), any(), anyLong(), anyLong(), anyInt()))
                                                                                                     .thenReturn(List.of(failedItem))
                                                                                                     .thenReturn(List.of());
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(executionService.startExecution(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.EXECUTING));

    campaignService.retryCampaign(CAMPAIGN_ID);

    // The ALLOWLIST and the bound are the SERVER's, passed to the query — never
    // the client's, and never re-derived per call site
    ArgumentCaptor<Set<String>> reasonsCaptor = ArgumentCaptor.forClass(Set.class);
    ArgumentCaptor<Long> boundCaptor = ArgumentCaptor.forClass(Long.class);
    verify(campaignStorage, atLeastOnce()).getRetryableFailures(eq(CAMPAIGN_ID),
                                                               reasonsCaptor.capture(),
                                                               boundCaptor.capture(),
                                                               anyLong(),
                                                               anyInt());
    assertEquals(CleanupCampaignService.RETRYABLE_FAILURE_REASONS, reasonsCaptor.getValue());
    assertEquals(CleanupCampaignService.MAX_RETRY_ATTEMPTS, boundCaptor.getValue());
    // A deterministic failure is NOT in the allowlist: re-running it would be
    // guaranteed wasted work on possibly hundreds of thousands of rows
    assertFalse(CleanupCampaignService.RETRYABLE_FAILURE_REASONS.contains("cleanup.referentialIntegrity"),
                "A referenced node will be refused identically: never retryable");
    assertFalse(CleanupCampaignService.RETRYABLE_FAILURE_REASONS.contains("cleanup.notVersionable"),
                "A non-versionable node never grows a version history by itself: never retryable");
    assertEquals(Set.of("cleanup.revalidationFailed",
                        "cleanup.deleteError",
                        "cleanup.unexpectedError",
                        "cleanup.purgeVersionsError"),
                 CleanupCampaignService.RETRYABLE_FAILURE_REASONS);
  }

  @Test
  void shouldResetTheRequeuedItemFieldsButKeepItsReclaimedBytesOnRetry() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.getCampaignsByStates(ACTIVE_STATES)).thenReturn(List.of());
    CleanupCampaignItem failedItem = skippedItem(52L, "cleanup.unexpectedError", 2L);
    failedItem.setFailureDetail("java.lang.IllegalStateException: boom");
    failedItem.setReclaimedBytes(4096L);
    failedItem.setPurgedAt(7000L);
    when(campaignStorage.getRetryableFailures(eq(CAMPAIGN_ID), any(), anyLong(), anyLong(), anyInt()))
                                                                                                     .thenReturn(List.of(failedItem))
                                                                                                     .thenReturn(List.of());
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(executionService.startExecution(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.EXECUTING));

    campaignService.retryCampaign(CAMPAIGN_ID);

    ArgumentCaptor<CleanupCampaignItem> itemCaptor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(itemCaptor.capture());
    CleanupCampaignItem requeued = itemCaptor.getValue();
    assertEquals(CleanupItemState.CANDIDATE, requeued.getState(), "A requeued item goes back to CANDIDATE");
    assertEquals(3L, requeued.getAttemptCount(), "The attempt count must be incremented, that is what bounds the retries");
    assertNull(requeued.getFailureReason(), "A stale reason on a requeued item would be a lie");
    assertNull(requeued.getFailureDetail(), "...and so would a stale detail");
    assertEquals(4096L,
                 requeued.getReclaimedBytes(),
                 "A partially reclaimed delete already reported REAL bytes: the campaign total must not lose them");
    assertEquals(7000L, requeued.getPurgedAt(), "The purge date is left alone too");
  }

  @Test
  void shouldResetTheProgressAndRelaunchTheWorkerThroughStartExecutionOnRetry() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.getCampaignsByStates(ACTIVE_STATES)).thenReturn(List.of());
    when(campaignStorage.getRetryableFailures(eq(CAMPAIGN_ID), any(), anyLong(), anyLong(), anyInt()))
                                                                                                     .thenReturn(List.of(skippedItem(53L,
                                                                                                                                     "cleanup.deleteError",
                                                                                                                                     0L)))
                                                                                                     .thenReturn(List.of());
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
    CleanupCampaign executing = campaign(CleanupCampaignState.EXECUTING);
    when(executionService.startExecution(CAMPAIGN_ID)).thenReturn(executing);

    CleanupCampaign retried = campaignService.retryCampaign(CAMPAIGN_ID);

    // The requeue happens BEFORE the execution start: startExecution counts the
    // CANDIDATE items to set its denominators, so requeueing after it would leave
    // the progress bar at zero out of zero
    InOrder inOrder = inOrder(campaignStorage, executionService);
    inOrder.verify(campaignStorage).saveItem(any());
    inOrder.verify(executionService).startExecution(CAMPAIGN_ID);
    assertEquals(CleanupCampaignState.EXECUTING, retried.getState());
  }

  @Test
  void shouldRejectRetryOfACampaignThatIsNotCompleted() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.EXECUTING));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.retryCampaign(CAMPAIGN_ID));

    assertEquals(CLEANUP_INVALID_STATE_ERROR, exception.getMessage());
    verify(campaignStorage, never()).saveItem(any());
  }

  @Test
  void shouldRejectRetryWhenAnotherCampaignIsActive() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.COMPLETED));
    CleanupCampaign activeCampaign = campaign(CleanupCampaignState.PUBLISHED);
    activeCampaign.setId(99L);
    when(campaignStorage.getCampaignsByStates(ACTIVE_STATES)).thenReturn(List.of(activeCampaign));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.retryCampaign(CAMPAIGN_ID));

    // The SAME single-active-campaign invariant as a publication, honoured
    // through the same guard: a retry deletes files, it is an execution
    assertEquals("cleanup.campaignAlreadyActive", exception.getMessage());
    verify(campaignStorage, never()).saveItem(any());
    verify(campaignStorage, never()).getRetryableFailures(anyLong(), any(), anyLong(), anyLong(), anyInt());
  }

  @Test
  void shouldRejectRetryWhenNothingIsRequeued() throws ObjectNotFoundException {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.COMPLETED));
    when(campaignStorage.getCampaignsByStates(ACTIVE_STATES)).thenReturn(List.of());
    when(campaignStorage.getRetryableFailures(eq(CAMPAIGN_ID), any(), anyLong(), anyLong(), anyInt())).thenReturn(List.of());

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.retryCampaign(CAMPAIGN_ID));

    // Never a silent no-op run: the console must be able to explain the refusal
    assertEquals("cleanup.noRetryableFailures", exception.getMessage());
    verify(executionService, never()).startExecution(anyLong());
  }

  @Test
  void shouldThrowNotFoundWhenRetryingAnUnknownCampaign() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(null);

    ObjectNotFoundException exception = assertThrows(ObjectNotFoundException.class,
                                                     () -> campaignService.retryCampaign(CAMPAIGN_ID));

    assertEquals("cleanup.campaignNotFound", exception.getMessage());
  }

  @Test
  void shouldRequeueEveryPageOfFailuresWithoutLoadingThemAllAtOnce() throws ObjectNotFoundException {
    CleanupCampaign campaign = campaign(CleanupCampaignState.COMPLETED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign);
    when(campaignStorage.getCampaignsByStates(ACTIVE_STATES)).thenReturn(List.of());
    CleanupCampaignItem firstPageItem = skippedItem(61L, "cleanup.deleteError", 0L);
    CleanupCampaignItem secondPageItem = skippedItem(62L, "cleanup.unexpectedError", 0L);
    when(campaignStorage.getRetryableFailures(eq(CAMPAIGN_ID), any(), anyLong(), anyLong(), anyInt()))
                                                                                                     .thenReturn(List.of(firstPageItem))
                                                                                                     .thenReturn(List.of(secondPageItem))
                                                                                                     .thenReturn(List.of());
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(executionService.startExecution(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.EXECUTING));

    campaignService.retryCampaign(CAMPAIGN_ID);

    // EVERY page is requeued, and the paging is KEYSET-driven: the second query
    // asks for the ids past the last one seen, so the requeue cannot walk past
    // rows as the SKIPPED set shrinks underneath it
    verify(campaignStorage, times(2)).saveItem(any());
    ArgumentCaptor<Long> lastIdCaptor = ArgumentCaptor.forClass(Long.class);
    verify(campaignStorage, times(3)).getRetryableFailures(eq(CAMPAIGN_ID),
                                                          any(),
                                                          anyLong(),
                                                          lastIdCaptor.capture(),
                                                          anyInt());
    assertEquals(List.of(0L, 61L, 62L), lastIdCaptor.getAllValues(), "Each page must resume from the last id seen");
  }

  @Test
  void shouldFlagEachGroupedFailureWithTheServerSideRetryableRule() throws ObjectNotFoundException {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.COMPLETED));
    when(campaignStorage.countFailuresByReason(CAMPAIGN_ID))
                                                            .thenReturn(List.of(new CleanupFailureGroup("cleanup.deleteError",
                                                                                                        12L,
                                                                                                        false),
                                                                                new CleanupFailureGroup("cleanup.referentialIntegrity",
                                                                                                        3L,
                                                                                                        false)));

    List<CleanupFailureGroup> failures = campaignService.getCampaignFailures(CAMPAIGN_ID);

    assertEquals(2, failures.size());
    assertEquals(12L, failures.get(0).getCount());
    assertTrue(failures.get(0).isRetryable(), "A transient delete failure is worth re-attempting");
    assertFalse(failures.get(1).isRetryable(), "A referential-integrity failure will be refused identically");
  }

  @Test
  void shouldReturnNoGroupedFailureOnceTheItemRowsWerePurged() throws ObjectNotFoundException {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.COMPLETED));
    when(campaignStorage.countFailuresByReason(CAMPAIGN_ID)).thenReturn(List.of());

    // The groups are computed over the ITEM ROWS and are NOT part of the summary
    // snapshotted at completion: once the retention job archived them, there is
    // nothing left to group
    assertTrue(campaignService.getCampaignFailures(CAMPAIGN_ID).isEmpty());
  }

  @Test
  void shouldThrowNotFoundWhenGroupingTheFailuresOfAnUnknownCampaign() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> campaignService.getCampaignFailures(CAMPAIGN_ID));
  }

  @Test
  void shouldServeTheGroupedScanFailuresOfACampaignFromTheScanService() throws ObjectNotFoundException {
    CleanupCampaign simulatedCampaign = campaign(CleanupCampaignState.SIMULATED);
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(simulatedCampaign);
    when(scanService.getScanFailures(simulatedCampaign)).thenReturn(List.of(new CleanupFailureGroup("cleanup.scanUnitFailed",
                                                                                                    4L,
                                                                                                    false)));

    List<CleanupFailureGroup> scanFailures = campaignService.getCampaignScanFailures(CAMPAIGN_ID);

    // Delegated to the service that OWNS the scan and its unit rows, on the very
    // campaign this one resolved — so the 404 stays this layer's job and the
    // verdict stays the scan's
    assertEquals(1, scanFailures.size());
    assertEquals("cleanup.scanUnitFailed", scanFailures.get(0).getReason());
    assertEquals(4L, scanFailures.get(0).getCount());
    verify(scanService).getScanFailures(simulatedCampaign);
  }

  @Test
  void shouldThrowNotFoundWhenGroupingTheScanFailuresOfAnUnknownCampaign() {
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> campaignService.getCampaignScanFailures(CAMPAIGN_ID));
    verify(scanService, never()).getScanFailures(any());
  }

  @Test
  void shouldKeepTheCsvRowIntactWhenExportingAMultiLineFailureDetail() throws Exception {
    CleanupCampaignItem item = item(CleanupItemState.SKIPPED);
    item.setPath("/Users/j___/john/Private/report.pdf");
    item.setFailureReason("cleanup.deleteError");
    // Both line-break flavours, CRLF included: a Windows-produced dump must not
    // yield two flattened sequences where one is expected
    item.setFailureDetail("javax.jcr.RepositoryException: boom\r\n at Storage.delete(Storage.java:42)\n at Worker.run(Worker.java:7)");
    item.setAttemptCount(2L);

    String csv = streamCsvOf(item);

    // ONE ROW PER ITEM, whatever a stack trace looks like: every line break is
    // flattened to the literal two-character sequence and the field is quoted
    assertEquals(2, csv.split("\n", -1).length - 1, "The report must hold exactly the header row and ONE item row");
    assertTrue(csv.contains(",cleanup.deleteError,"), "The bare reason column is unaffected: " + csv);
    assertTrue(csv.trim()
                  .endsWith(",2,\"javax.jcr.RepositoryException: boom\\n at Storage.delete(Storage.java:42)\\n at Worker.run(Worker.java:7)\""),
               "attemptCount then the QUOTED, flattened detail must be the LAST two columns: " + csv);
  }

  @Test
  void shouldExportAnEmptyFailureDetailAsAPlainEmptyColumn() throws Exception {
    CleanupCampaignItem item = item(CleanupItemState.PURGED);
    item.setPath("/Users/j___/john/Private/report.pdf");

    String csv = streamCsvOf(item);

    // Quoting emptiness would say nothing and only widen a report that can hold
    // millions of rows
    assertTrue(csv.trim().endsWith(",0,"), "A purged item exports a zero attempt count and an empty detail: " + csv);
  }

  private CleanupCampaignItem skippedItem(long id, String failureReason, long attemptCount) {
    CleanupCampaignItem item = item(CleanupItemState.SKIPPED);
    item.setId(id);
    item.setFailureReason(failureReason);
    item.setAttemptCount(attemptCount);
    return item;
  }

  private CleanupCampaign campaign(CleanupCampaignState state) {
    CleanupCampaign campaign = new CleanupCampaign();
    campaign.setId(CAMPAIGN_ID);
    campaign.setName("Q3 cleanup");
    campaign.setState(state);
    campaign.setParams(new CleanupParams(6, 1048576L, 7, 5, List.of(), 200));
    return campaign;
  }

  private CleanupCampaignItem item(CleanupItemState state) {
    CleanupCampaignItem item = new CleanupCampaignItem();
    item.setId(ITEM_ID);
    item.setCampaignId(CAMPAIGN_ID);
    item.setNodeUuid(NODE_UUID);
    item.setOwnerIdentityId(5L);
    item.setAction(CleanupAction.DELETE);
    item.setState(state);
    return item;
  }

  private Identity userIdentity(String id, String remoteId) {
    Identity identity = new Identity("organization", remoteId);
    identity.setId(id);
    return identity;
  }

  private Identity spaceIdentity(String id, String remoteId) {
    Identity identity = new Identity("space", remoteId);
    identity.setId(id);
    return identity;
  }

}
