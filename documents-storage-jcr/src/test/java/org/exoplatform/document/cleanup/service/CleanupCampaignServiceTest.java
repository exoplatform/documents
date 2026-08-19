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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
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

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.constant.CleanupExemptionResult;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignAggregates;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupCandidate;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupRevalidation;
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
    assertEquals(published.getPublishedDate(), published.getLockDate(),
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
    mockPublishedCampaignWithItem(CleanupItemState.CANDIDATE);
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
    mockPublishedCampaignWithItem(CleanupItemState.CANDIDATE);
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
    when(campaignStorage.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CleanupCampaignState.LOCKED));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                      () -> campaignService.keepItem(ITEM_ID, USERNAME));

    assertEquals("cleanup.campaignNotPublished", exception.getMessage());
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
    mockPublishedCampaignWithItem(CleanupItemState.EXEMPTED);
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

    campaignService.unkeepItems(List.of(ITEM_ID, 11L), USERNAME);

    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(11L, captor.getValue().getId(), "The failing item must not prevent the remaining un-keeps");
    assertEquals(CleanupItemState.SPARED_BY_MODIFICATION, captor.getValue().getState());
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
    when(campaignStorage.getItemsTouchedByPath(CAMPAIGN_ID, "/Users/john/Private/docs")).thenReturn(List.of(keptItem));

    campaignService.refreshCandidate("/Users/john/Private/docs", "PROPERTY_CHANGED");

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
    when(campaignStorage.getItemsTouchedByPath(CAMPAIGN_ID, "/Users/john/Private/docs")).thenReturn(List.of(goneItem,
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

    campaignService.refreshCandidate("/Users/john/Private/docs", "NODE_REMOVED");

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
                                                                                 CleanupCampaignState.COMPLETED,
                                                                                 Set.of(),
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
        {"candidateCount":3,"reclaimableBytes":1024,"reclaimedBytes":123456,"purgedCount":42}""");
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
        {"reclaimedBytes":123456}""");
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

    campaignService.keepItems(List.of(ITEM_ID, 11L), USERNAME);

    ArgumentCaptor<CleanupCampaignItem> captor = ArgumentCaptor.forClass(CleanupCampaignItem.class);
    verify(campaignStorage).saveItem(captor.capture());
    assertEquals(11L, captor.getValue().getId(), "The failing item must not prevent the remaining keeps");
    assertEquals(CleanupItemState.EXEMPTED, captor.getValue().getState());
  }

  @Test
  void shouldRefreshFileItemOnPropertyChangeEventBelowIt() {
    // A PROPERTY_CHANGED event carries the PROPERTY's path, a DESCENDANT of
    // the file item's path: the bidirectional touched-by query must be fed the
    // raw event path so the ancestor-chain match finds the file item above it
    CleanupCampaign campaign = campaign(CleanupCampaignState.PUBLISHED);
    when(campaignStorage.getCampaignsByStates(List.of(CleanupCampaignState.PUBLISHED))).thenReturn(List.of(campaign));
    String propertyPath = "/Users/j___/john/Private/file.pdf/jcr:content/jcr:data";
    CleanupCampaignItem fileItem = item(CleanupItemState.CANDIDATE);
    fileItem.setPath("/Users/j___/john/Private/file.pdf");
    when(campaignStorage.getItemsTouchedByPath(CAMPAIGN_ID, propertyPath)).thenReturn(List.of(fileItem));
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID), any())).thenReturn(CleanupRevalidation.of(null));
    when(campaignStorage.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));

    campaignService.refreshCandidate(propertyPath, "PROPERTY_CHANGED");

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
    String folderPath = "/Users/j___/john/Private/docs";
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
    when(campaignStorage.getItemsTouchedByPath(CAMPAIGN_ID, "/Users/john/Private/docs"))
                                                                                        .thenReturn(List.of(candidateItem));
    when(cleanupJcrStorage.revalidate(eq(NODE_UUID), any())).thenReturn(CleanupRevalidation.unknown());

    campaignService.refreshCandidate("/Users/john/Private/docs", "PROPERTY_CHANGED");

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
        {"candidateCount":3,"reclaimableBytes":1024,"reclaimedBytes":123456}""");
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
