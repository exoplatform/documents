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
package org.exoplatform.document.cleanup.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.document.cleanup.constant.CleanupAction;
import org.exoplatform.document.cleanup.constant.CleanupCampaignState;
import org.exoplatform.document.cleanup.constant.CleanupItemState;
import org.exoplatform.document.cleanup.model.CleanupCampaign;
import org.exoplatform.document.cleanup.model.CleanupCampaignItem;
import org.exoplatform.document.cleanup.model.CleanupComparison;
import org.exoplatform.document.cleanup.model.CleanupFailureGroup;
import org.exoplatform.document.cleanup.model.CleanupScanUnit;
import org.exoplatform.document.cleanup.model.CleanupScanUnitProgress;
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupUserSummary;
import org.exoplatform.document.cleanup.rest.model.CampaignComparisonRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignFailureGroupRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignScanUnitProgressRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignScanUnitRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignItemRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignRestEntity;
import org.exoplatform.document.cleanup.rest.model.MyItemsSummaryRestEntity;
import org.exoplatform.document.cleanup.rest.model.PagedResult;
import org.exoplatform.document.cleanup.rest.model.UpdateCampaignRestEntity;
import org.exoplatform.document.cleanup.service.CleanupCampaignService;
import org.exoplatform.document.cleanup.util.CleanupConstants;
import org.exoplatform.social.core.manager.IdentityManager;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Direct-call controller tests pinning the delegation to
 * {@link CleanupCampaignService}, the query-parameter mapping and the
 * exception-to-HTTP-status contract (404/400 as
 * {@link ResponseStatusException}, message code as reason).
 */
@ExtendWith(MockitoExtension.class)
class CleanupCampaignRestTest {

  private static final String    NOT_FOUND_CODE   = "cleanup.campaignNotFound";

  private static final String    BAD_REQUEST_CODE = "cleanup.invalidState";

  private static final long      CAMPAIGN_ID      = 12L;

  private static final String    FAILURE_DETAIL   =
                                                "javax.jcr.ReferentialIntegrityException: referenced by /Groups/spaces/hr/Documents/link";

  /**
   * The ordering BOTH item tables must end up with by default: the size-first
   * business ordering, made total by the primary-key 'id' tiebreaker.
   */
  private static final Sort      FILE_SIZE_DESC_THEN_ID   = Sort.by(Sort.Direction.DESC, "fileSize")
                                                                .and(Sort.by(Sort.Direction.ASC, "id"));

  /**
   * What the USER review list opens on: the computed reclaimable key — the same
   * figure the list displays and the campaign totals — made total by the id
   * tiebreaker. The Storage layer turns that key into a query-level ORDER BY.
   */
  private static final Sort      RECLAIMABLE_DESC_THEN_ID = Sort.by(Sort.Direction.DESC, CleanupConstants.RECLAIMABLE_SORT_KEY)
                                                                .and(Sort.by(Sort.Direction.ASC, "id"));

  @Mock
  private CleanupCampaignService campaignService;

  @Mock
  private IdentityManager        identityManager;

  @Mock
  private HttpServletRequest     request;

  @InjectMocks
  private CleanupCampaignRest    campaignRest;

  @Test
  void getCampaignsMapsEveryCampaign() {
    when(campaignService.getCampaigns()).thenReturn(List.of(campaign(1, "first"), campaign(2, "second")));

    List<CampaignRestEntity> campaigns = campaignRest.getCampaigns();

    assertEquals(2, campaigns.size());
    assertEquals(1, campaigns.get(0).getId());
    assertEquals("first", campaigns.get(0).getName());
    assertEquals(2, campaigns.get(1).getId());
    assertEquals("second", campaigns.get(1).getName());
  }

  @Test
  void getDefaultsCarriesThePlatformValuesAndTheCeilingTheFormMustRespect() {
    CleanupParams defaults = new CleanupParams(6, 1048576L, 7, 5, List.of(), 200, 4);
    when(campaignService.getDefaultParams()).thenReturn(defaults);
    when(campaignService.getMaxScanThreads()).thenReturn(20);

    CampaignRestEntity served = campaignRest.getDefaults();

    assertEquals(6, served.getPeriodMonths());
    assertEquals(1048576L, served.getMinFileSizeBytes());
    assertEquals(7, served.getGraceDays());
    assertEquals(5, served.getMaxVersionsPerFile());
    assertEquals(4, served.getScanThreads(), "The platform default fan-out pre-fills the form");
    // The form must bound its input on the number the SERVER validates against: a
    // form inventing its own bound is a form that disagrees the day one changes
    assertEquals(20, served.getMaxScanThreads(), "The ceiling must travel with the defaults");
  }

  @Test
  void createCampaignMapsBodyToParamOverrides() {
    CampaignRestEntity body = new CampaignRestEntity();
    body.setName("Quarterly cleanup");
    body.setPeriodMonths(12);
    body.setMinFileSizeBytes(2048L);
    body.setGraceDays(10);
    body.setMaxVersionsPerFile(3);
    body.setExcludedPaths(List.of("/Users/root"));
    when(campaignService.createCampaign(eq("Quarterly cleanup"), any())).thenReturn(campaign(CAMPAIGN_ID, "Quarterly cleanup"));

    CampaignRestEntity created = campaignRest.createCampaign(body);

    assertEquals(CAMPAIGN_ID, created.getId());
    ArgumentCaptor<CleanupParams> overridesCaptor = ArgumentCaptor.forClass(CleanupParams.class);
    verify(campaignService).createCampaign(eq("Quarterly cleanup"), overridesCaptor.capture());
    CleanupParams overrides = overridesCaptor.getValue();
    assertEquals(12, overrides.getPeriodMonths());
    assertEquals(2048L, overrides.getMinFileSizeBytes());
    assertEquals(10, overrides.getGraceDays());
    assertEquals(3, overrides.getMaxVersionsPerFile());
    assertEquals(List.of("/Users/root"), overrides.getExcludedPaths());
    assertNull(overrides.getBatchSize(), "The creation body must never override the batch size");
  }

  @Test
  void createCampaignMapsIllegalArgumentTo400() {
    when(campaignService.createCampaign(any(), any())).thenThrow(new IllegalArgumentException("cleanup.nameRequired"));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                     () -> campaignRest.createCampaign(new CampaignRestEntity()));
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("cleanup.nameRequired", exception.getReason());
  }

  @Test
  void updateCampaignDelegatesBothBodyFieldsAndAnswersTheUpdatedDto() throws ObjectNotFoundException {
    UpdateCampaignRestEntity body = new UpdateCampaignRestEntity();
    body.setName("  Renamed campaign  ");
    body.setGraceDays(21);
    when(campaignService.updateCampaign(eq(CAMPAIGN_ID), any(), any())).thenReturn(campaign(CAMPAIGN_ID, "Renamed campaign"));

    CampaignRestEntity updated = campaignRest.updateCampaign(CAMPAIGN_ID, body);

    // The DTO the list and the header re-render from
    assertEquals(CAMPAIGN_ID, updated.getId());
    assertEquals("Renamed campaign", updated.getName());
    // Both fields passed through VERBATIM: trimming them, validating them and
    // deciding which state may edit which is the Service's business, this layer
    // carries none — see the service tests pinning the trim and the state guard
    verify(campaignService).updateCampaign(CAMPAIGN_ID, "  Renamed campaign  ", 21);
  }

  /**
   * A partial body reaches the Service as a partial one: an absent field stays
   * NULL rather than being defaulted here, null being what means 'leave that
   * attribute unchanged'. Zero is forwarded as zero — a MEANINGFUL grace period,
   * not an absent one.
   */
  @Test
  void updateCampaignForwardsAnAbsentFieldAsNullAndAZeroGraceAsZero() throws ObjectNotFoundException {
    when(campaignService.updateCampaign(eq(CAMPAIGN_ID), any(), any())).thenReturn(campaign(CAMPAIGN_ID, "Q3 cleanup"));

    UpdateCampaignRestEntity nameOnly = new UpdateCampaignRestEntity();
    nameOnly.setName("Q4 cleanup");
    campaignRest.updateCampaign(CAMPAIGN_ID, nameOnly);
    verify(campaignService).updateCampaign(CAMPAIGN_ID, "Q4 cleanup", null);

    UpdateCampaignRestEntity graceOnly = new UpdateCampaignRestEntity();
    graceOnly.setGraceDays(0);
    campaignRest.updateCampaign(CAMPAIGN_ID, graceOnly);
    verify(campaignService).updateCampaign(CAMPAIGN_ID, null, 0);
  }

  @Test
  void updateCampaignMapsNotFoundTo404() throws ObjectNotFoundException {
    when(campaignService.updateCampaign(anyLong(), any(), any())).thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                     () -> campaignRest.updateCampaign(CAMPAIGN_ID,
                                                                                       new UpdateCampaignRestEntity()));
    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    assertEquals(NOT_FOUND_CODE, exception.getReason());
  }

  @Test
  void updateCampaignMapsIllegalArgumentTo400WithTheMessageCode() throws ObjectNotFoundException {
    when(campaignService.updateCampaign(anyLong(), any(), any())).thenThrow(new IllegalArgumentException("cleanup.nameTooLong"));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                     () -> campaignRest.updateCampaign(CAMPAIGN_ID,
                                                                                       new UpdateCampaignRestEntity()));
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("cleanup.nameTooLong", exception.getReason());
  }

  /**
   * Every message code the Service can refuse a patch with reaches the client as
   * the 400 REASON: the console localizes it, so it must never be swallowed nor
   * replaced by a generic sentence.
   */
  @Test
  void updateCampaignCarriesEveryRefusalMessageCode() throws ObjectNotFoundException {
    for (String messageCode : List.of("cleanup.nothingToUpdate",
                                      "cleanup.nameMandatory",
                                      "cleanup.invalidState",
                                      "cleanup.invalidGraceDays")) {
      // doThrow, not when/thenThrow: re-stubbing an already-throwing mock through
      // when() would invoke it — and throw — while stubbing it
      doThrow(new IllegalArgumentException(messageCode)).when(campaignService).updateCampaign(anyLong(), any(), any());

      ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                       () -> campaignRest.updateCampaign(CAMPAIGN_ID,
                                                                                         new UpdateCampaignRestEntity()));
      assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
      assertEquals(messageCode, exception.getReason());
    }
  }

  @Test
  void getCampaignMapsNotFoundTo404() throws ObjectNotFoundException {
    when(campaignService.getCampaign(CAMPAIGN_ID)).thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> campaignRest.getCampaign(CAMPAIGN_ID));
    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    assertEquals(NOT_FOUND_CODE, exception.getReason());
  }

  @Test
  void getCampaignBuildsRestEntity() throws ObjectNotFoundException {
    when(campaignService.getCampaign(CAMPAIGN_ID)).thenReturn(campaign(CAMPAIGN_ID, "My campaign"));

    CampaignRestEntity entity = campaignRest.getCampaign(CAMPAIGN_ID);

    assertEquals(CAMPAIGN_ID, entity.getId());
    assertEquals("My campaign", entity.getName());
    assertEquals(CleanupCampaignState.DRAFT.name(), entity.getState());
  }

  @Test
  void cancelCampaignDelegatesAndMapsStatuses() throws ObjectNotFoundException {
    campaignRest.cancelCampaign(CAMPAIGN_ID);
    verify(campaignService).cancelCampaign(CAMPAIGN_ID);

    ObjectNotFoundException notFound = new ObjectNotFoundException(NOT_FOUND_CODE);
    org.mockito.Mockito.doThrow(notFound).when(campaignService).cancelCampaign(404L);
    ResponseStatusException notFoundException = assertThrows(ResponseStatusException.class,
                                                             () -> campaignRest.cancelCampaign(404L));
    assertEquals(HttpStatus.NOT_FOUND, notFoundException.getStatusCode());

    org.mockito.Mockito.doThrow(new IllegalArgumentException(BAD_REQUEST_CODE)).when(campaignService).cancelCampaign(400L);
    ResponseStatusException badRequestException = assertThrows(ResponseStatusException.class,
                                                               () -> campaignRest.cancelCampaign(400L));
    assertEquals(HttpStatus.BAD_REQUEST, badRequestException.getStatusCode());
    assertEquals(BAD_REQUEST_CODE, badRequestException.getReason());
  }

  @Test
  void publishCampaignDelegatesAndMapsStatuses() throws ObjectNotFoundException {
    when(campaignService.publishCampaign(CAMPAIGN_ID)).thenReturn(campaign(CAMPAIGN_ID, "published"));
    assertEquals(CAMPAIGN_ID, campaignRest.publishCampaign(CAMPAIGN_ID).getId());

    when(campaignService.publishCampaign(404L)).thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));
    assertEquals(HttpStatus.NOT_FOUND,
                 assertThrows(ResponseStatusException.class, () -> campaignRest.publishCampaign(404L)).getStatusCode());

    when(campaignService.publishCampaign(400L)).thenThrow(new IllegalArgumentException(BAD_REQUEST_CODE));
    assertEquals(HttpStatus.BAD_REQUEST,
                 assertThrows(ResponseStatusException.class, () -> campaignRest.publishCampaign(400L)).getStatusCode());
  }

  @Test
  void executeCampaignDelegatesAndMapsStatuses() throws ObjectNotFoundException {
    when(campaignService.executeCampaign(CAMPAIGN_ID)).thenReturn(campaign(CAMPAIGN_ID, "executing"));
    assertEquals(CAMPAIGN_ID, campaignRest.executeCampaign(CAMPAIGN_ID).getId());

    when(campaignService.executeCampaign(404L)).thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));
    assertEquals(HttpStatus.NOT_FOUND,
                 assertThrows(ResponseStatusException.class, () -> campaignRest.executeCampaign(404L)).getStatusCode());

    when(campaignService.executeCampaign(400L)).thenThrow(new IllegalArgumentException(BAD_REQUEST_CODE));
    assertEquals(HttpStatus.BAD_REQUEST,
                 assertThrows(ResponseStatusException.class, () -> campaignRest.executeCampaign(400L)).getStatusCode());
  }

  @Test
  void getCampaignItemsMapsPagingSortAndFilters() throws ObjectNotFoundException {
    when(campaignService.getCampaignItems(eq(CAMPAIGN_ID),
                                          eq(7L),
                                          eq(CleanupItemState.CANDIDATE),
                                          eq(CleanupAction.DELETE),
                                          eq(1024L),
                                          eq("report"),
                                          any())).thenReturn(new PageImpl<>(List.of(item(3))));

    PagedResult<CampaignItemRestEntity> result = campaignRest.getCampaignItems(CAMPAIGN_ID,
                                                                               7L,
                                                                               "candidate",
                                                                               "delete",
                                                                               1024L,
                                                                               "report",
                                                                               2,
                                                                               5,
                                                                               "path,asc");

    assertEquals(1, result.getItems().size());
    assertEquals(3, result.getItems().get(0).getId());
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(campaignService).getCampaignItems(eq(CAMPAIGN_ID),
                                             eq(7L),
                                             eq(CleanupItemState.CANDIDATE),
                                             eq(CleanupAction.DELETE),
                                             eq(1024L),
                                             eq("report"),
                                             pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();
    // 'path' is NOT unique (the only uniqueness on the table is on the node
    // uuid, and PATH is nullable), so it gets the id tiebreaker like any other
    // non-unique key
    assertEquals(PageRequest.of(2, 5, Sort.by(Sort.Direction.ASC, "path").and(Sort.by(Sort.Direction.ASC, "id"))), pageable);
  }

  @Test
  void getCampaignItemsDefaultsToFileSizeDescendingSortWithIdTiebreaker() throws ObjectNotFoundException {
    when(campaignService.getCampaignItems(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

    campaignRest.getCampaignItems(CAMPAIGN_ID, null, null, null, null, null, 0, 20, null);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(campaignService).getCampaignItems(eq(CAMPAIGN_ID),
                                             eq((Long) null),
                                             eq((CleanupItemState) null),
                                             eq((CleanupAction) null),
                                             eq((Long) null),
                                             eq((String) null),
                                             pageableCaptor.capture());
    assertEquals(PageRequest.of(0, 20, FILE_SIZE_DESC_THEN_ID), pageableCaptor.getValue());
  }

  @Test
  void getCampaignItemsAcceptsTheReclaimableSortKeyWithoutDefaultingToIt() throws ObjectNotFoundException {
    when(campaignService.getCampaignItems(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

    // The ADMIN table's size column shows the CONTENT size (and its minSize filter
    // narrows on that same column), so it keeps defaulting to 'fileSize' — the
    // reclaimable ordering stays available on request, never imposed here
    campaignRest.getCampaignItems(CAMPAIGN_ID,
                                  null,
                                  null,
                                  null,
                                  null,
                                  null,
                                  0,
                                  20,
                                  CleanupConstants.RECLAIMABLE_SORT_KEY + ",desc");

    assertEquals(PageRequest.of(0, 20, RECLAIMABLE_DESC_THEN_ID), captureItemsPageable());
  }

  @Test
  void getCampaignItemsAppendsTheIdTiebreakerToANonUniqueRequestedSort() throws ObjectNotFoundException {
    when(campaignService.getCampaignItems(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

    campaignRest.getCampaignItems(CAMPAIGN_ID, null, null, null, null, null, 0, 20, "fileSize,desc");

    // fileSize is NOT unique: without the appended 'id ASC' an offset-paged
    // query over a block of equal sizes could repeat a row on one page and skip
    // another one entirely
    assertEquals(PageRequest.of(0, 20, FILE_SIZE_DESC_THEN_ID), captureItemsPageable());
  }

  @Test
  void getCampaignItemsRejectsUnknownSortField() {
    ResponseStatusException exception =
                                      assertThrows(ResponseStatusException.class,
                                                   () -> campaignRest.getCampaignItems(CAMPAIGN_ID,
                                                                                       null,
                                                                                       null,
                                                                                       null,
                                                                                       null,
                                                                                       null,
                                                                                       0,
                                                                                       20,
                                                                                       "name'); DROP TABLE items;--,asc"));
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("cleanup.invalidSortField", exception.getReason());
  }

  @Test
  void getCampaignItemsRejectsUnknownFilterValue() {
    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                     () -> campaignRest.getCampaignItems(CAMPAIGN_ID,
                                                                                         null,
                                                                                         "notAState",
                                                                                         null,
                                                                                         null,
                                                                                         null,
                                                                                         0,
                                                                                         20,
                                                                                         null));
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("cleanup.invalidFilterValue", exception.getReason());
  }

  @Test
  void getCampaignItemsMapsNotFoundTo404() throws ObjectNotFoundException {
    when(campaignService.getCampaignItems(anyLong(), any(), any(), any(), any(), any(), any()))
                                                                                               .thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                     () -> campaignRest.getCampaignItems(CAMPAIGN_ID,
                                                                                         null,
                                                                                         null,
                                                                                         null,
                                                                                         null,
                                                                                         null,
                                                                                         0,
                                                                                         20,
                                                                                         null));
    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
  }

  @Test
  void compareCampaignsDelegatesAndMaps404() throws ObjectNotFoundException {
    when(campaignService.compareCampaigns(CAMPAIGN_ID, 13L)).thenReturn(new CleanupComparison(CAMPAIGN_ID,
                                                                                              13L,
                                                                                              1,
                                                                                              2,
                                                                                              3,
                                                                                              10,
                                                                                              20,
                                                                                              30));

    CampaignComparisonRestEntity comparison = campaignRest.compareCampaigns(CAMPAIGN_ID, 13L);
    assertEquals(CAMPAIGN_ID, comparison.getBaseCampaignId());
    assertEquals(13L, comparison.getOtherCampaignId());
    assertEquals(1, comparison.getNewCount());

    when(campaignService.compareCampaigns(CAMPAIGN_ID, 404L)).thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));
    assertEquals(HttpStatus.NOT_FOUND,
                 assertThrows(ResponseStatusException.class,
                              () -> campaignRest.compareCampaigns(CAMPAIGN_ID, 404L)).getStatusCode());
  }

  @Test
  void getCampaignArchiveStreamsCsvAttachment() throws Exception {
    ResponseEntity<StreamingResponseBody> response = campaignRest.getCampaignArchive(CAMPAIGN_ID);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
    assertNotNull(contentDisposition);
    assertTrue(contentDisposition.contains("cleanup-campaign-" + CAMPAIGN_ID + ".csv"));
    assertNotNull(response.getHeaders().getContentType());
    assertEquals("text/csv", response.getHeaders().getContentType().toString());

    // Availability is settled BEFORE the body is written: nothing was streamed
    // yet at this point
    verify(campaignService).checkArchiveAvailable(CAMPAIGN_ID);
    verify(campaignService, never()).writeArchiveCsv(anyLong(), any());

    // The body only writes to the response stream when the container drains it
    assertNotNull(response.getBody());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    response.getBody().writeTo(outputStream);
    verify(campaignService).writeArchiveCsv(CAMPAIGN_ID, outputStream);
  }

  @Test
  void getCampaignArchiveMapsNotFoundTo404BeforeStreaming() throws Exception {
    org.mockito.Mockito.doThrow(new ObjectNotFoundException(NOT_FOUND_CODE))
                       .when(campaignService)
                       .checkArchiveAvailable(CAMPAIGN_ID);

    assertEquals(HttpStatus.NOT_FOUND,
                 assertThrows(ResponseStatusException.class, () -> campaignRest.getCampaignArchive(CAMPAIGN_ID)).getStatusCode());
    verify(campaignService, never()).writeArchiveCsv(anyLong(), any());
  }

  @Test
  void launchEndpointsAnswerAcceptedBecauseTheyHandOffToAWorker() throws NoSuchMethodException {
    // Both launch endpoints do no long work on the request thread: the dry-run
    // scan and the purge run in a worker and are followed on the CometD
    // channel,
    // so the HTTP contract has to be 202, not 200
    assertEquals(HttpStatus.ACCEPTED,
                 CleanupCampaignRest.class.getMethod("createCampaign", CampaignRestEntity.class)
                                          .getAnnotation(ResponseStatus.class)
                                          .value());
    assertEquals(HttpStatus.ACCEPTED,
                 CleanupCampaignRest.class.getMethod("executeCampaign", long.class)
                                          .getAnnotation(ResponseStatus.class)
                                          .value());
  }

  @Test
  void getMyItemsPassesRemoteUserSearchAndPageable() throws ObjectNotFoundException {
    when(request.getRemoteUser()).thenReturn("john");
    when(campaignService.getMyItems(eq("john"), eq("invoice"), any())).thenReturn(new PageImpl<>(List.of(item(5))));

    PagedResult<CampaignItemRestEntity> result = campaignRest.getMyItems(request, "invoice", 1, 10, "fileSize,desc");

    assertEquals(1, result.getItems().size());
    assertEquals(5, result.getItems().get(0).getId());
    // The USER endpoint builds the Pageable exactly like the admin one, stable
    // tiebreaker included — the service must not order on its own anymore
    assertEquals(PageRequest.of(1, 10, FILE_SIZE_DESC_THEN_ID), captureMyItemsPageable());
  }

  @Test
  void getMyItemsDefaultsToTheReclaimableSortWithIdTiebreaker() throws ObjectNotFoundException {
    when(request.getRemoteUser()).thenReturn("john");
    when(campaignService.getMyItems(eq("john"), any(), any())).thenReturn(new PageImpl<>(List.of()));

    campaignRest.getMyItems(request, null, 0, 20, null);

    verify(campaignService).getMyItems(eq("john"), eq((String) null), any());
    // The review list DISPLAYS what each row frees — content plus the version
    // history a delete destroys — so that is what it must be ranked by. Defaulting
    // to 'fileSize' showed 501 MB on a row it sorted as 1 MB, burying the biggest
    // win of a list the UI labels 'sorted by size'
    assertEquals(PageRequest.of(0, 20, RECLAIMABLE_DESC_THEN_ID), captureMyItemsPageable());
  }

  @Test
  void getMyItemsHonoursAnExplicitlyRequestedReclaimableSort() throws ObjectNotFoundException {
    when(request.getRemoteUser()).thenReturn("john");
    when(campaignService.getMyItems(eq("john"), any(), any())).thenReturn(new PageImpl<>(List.of()));

    // The default is not the only way in: the key is in the allowlist, so a client
    // can ask for it — ascending here, which the ordering must honour
    campaignRest.getMyItems(request, null, 0, 20, CleanupConstants.RECLAIMABLE_SORT_KEY + ",asc");

    assertEquals(PageRequest.of(0,
                                20,
                                Sort.by(Sort.Direction.ASC, CleanupConstants.RECLAIMABLE_SORT_KEY)
                                    .and(Sort.by(Sort.Direction.ASC, "id"))),
                 captureMyItemsPageable());
  }

  @Test
  void getMyItemsHonoursARequestedUniqueSortWithoutPilingUpATiebreaker() throws ObjectNotFoundException {
    when(request.getRemoteUser()).thenReturn("john");
    when(campaignService.getMyItems(eq("john"), any(), any())).thenReturn(new PageImpl<>(List.of()));

    // 'id' is the PRIMARY KEY, the only sortable field that already yields a
    // total order: it must not get a second key piled on top of it
    campaignRest.getMyItems(request, null, 0, 20, "id,asc");

    assertEquals(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id")), captureMyItemsPageable());
  }

  @Test
  void getMyItemsAppendsTheIdTiebreakerToAPathSortToo() throws ObjectNotFoundException {
    when(request.getRemoteUser()).thenReturn("john");
    when(campaignService.getMyItems(eq("john"), any(), any())).thenReturn(new PageImpl<>(List.of()));

    campaignRest.getMyItems(request, null, 0, 20, "path,asc");

    // PATH is nullable and only unique in practice — the sole uniqueness on the
    // table is UK_DOC_CLEANUP_ITEM_NODE (CAMPAIGN_ID, NODE_UUID). A file deleted
    // and recreated at the same path across a resumed scan yields two rows
    // sharing a path, so ordering on it alone is NOT total
    assertEquals(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "path").and(Sort.by(Sort.Direction.ASC, "id"))),
                 captureMyItemsPageable());
  }

  @Test
  void getMyItemsRejectsUnknownSortField() {
    // Same allowlist and same 400 message code as the admin endpoint
    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                     () -> campaignRest.getMyItems(request,
                                                                                   null,
                                                                                   0,
                                                                                   20,
                                                                                   "ownerFullName,asc"));
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("cleanup.invalidSortField", exception.getReason());
  }

  @Test
  void getMyItemsMapsNotFoundTo404() throws ObjectNotFoundException {
    when(request.getRemoteUser()).thenReturn("john");
    when(campaignService.getMyItems(eq("john"), any(), any())).thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                     () -> campaignRest.getMyItems(request, null, 0, 20, null));
    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    assertEquals(NOT_FOUND_CODE, exception.getReason());
  }

  @Test
  void getMyItemsSummaryPassesRemoteUserAndMaps404() throws ObjectNotFoundException {
    when(request.getRemoteUser()).thenReturn("mary");
    CleanupUserSummary summary = new CleanupUserSummary();
    summary.setCampaignId(CAMPAIGN_ID);
    summary.setState(CleanupCampaignState.PUBLISHED);
    when(campaignService.getMyItemsSummary("mary")).thenReturn(summary);

    MyItemsSummaryRestEntity entity = campaignRest.getMyItemsSummary(request);
    assertEquals(CAMPAIGN_ID, entity.getCampaignId());
    assertEquals(CleanupCampaignState.PUBLISHED.name(), entity.getState());

    when(campaignService.getMyItemsSummary("mary")).thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));
    assertEquals(HttpStatus.NOT_FOUND,
                 assertThrows(ResponseStatusException.class, () -> campaignRest.getMyItemsSummary(request)).getStatusCode());
  }

  private Pageable captureItemsPageable() throws ObjectNotFoundException {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(campaignService).getCampaignItems(anyLong(), any(), any(), any(), any(), any(), pageableCaptor.capture());
    return pageableCaptor.getValue();
  }

  private Pageable captureMyItemsPageable() throws ObjectNotFoundException {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(campaignService).getMyItems(any(), any(), pageableCaptor.capture());
    return pageableCaptor.getValue();
  }

  private CleanupCampaign campaign(long id, String name) {
    CleanupCampaign campaign = new CleanupCampaign();
    campaign.setId(id);
    campaign.setName(name);
    campaign.setState(CleanupCampaignState.DRAFT);
    return campaign;
  }

  @Test
  void retryCampaignDelegatesAndMapsStatuses() throws ObjectNotFoundException {
    when(campaignService.retryCampaign(CAMPAIGN_ID)).thenReturn(campaign(CAMPAIGN_ID, "retried"));
    assertEquals(CAMPAIGN_ID, campaignRest.retryCampaign(CAMPAIGN_ID).getId());

    when(campaignService.retryCampaign(404L)).thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));
    ResponseStatusException notFound = assertThrows(ResponseStatusException.class, () -> campaignRest.retryCampaign(404L));
    assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
    assertEquals(NOT_FOUND_CODE, notFound.getReason(), "The message code must travel as the status reason");

    when(campaignService.retryCampaign(400L)).thenThrow(new IllegalArgumentException("cleanup.noRetryableFailures"));
    ResponseStatusException badRequest = assertThrows(ResponseStatusException.class, () -> campaignRest.retryCampaign(400L));
    assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatusCode());
    assertEquals("cleanup.noRetryableFailures", badRequest.getReason());
  }

  @Test
  void retryCampaignAnswersAcceptedLikeEveryAsynchronousAction() throws NoSuchMethodException {
    // The purge is handed off to a worker thread: answering 200 would tell the
    // console the work is done
    assertEquals(HttpStatus.ACCEPTED,
                 CleanupCampaignRest.class.getMethod("retryCampaign", long.class)
                                          .getAnnotation(ResponseStatus.class)
                                          .value());
  }

  @Test
  void getCampaignFailuresMapsEveryGroupAndItsRetryableFlag() throws ObjectNotFoundException {
    when(campaignService.getCampaignFailures(CAMPAIGN_ID)).thenReturn(List.of(new CleanupFailureGroup("cleanup.deleteError",
                                                                                                     12L,
                                                                                                     true),
                                                                             new CleanupFailureGroup("cleanup.referentialIntegrity",
                                                                                                     3L,
                                                                                                     false)));

    List<CampaignFailureGroupRestEntity> failures = campaignRest.getCampaignFailures(CAMPAIGN_ID);

    assertEquals(2, failures.size());
    assertEquals("cleanup.deleteError", failures.get(0).getReason());
    assertEquals(12L, failures.get(0).getCount());
    assertTrue(failures.get(0).isRetryable());
    assertFalse(failures.get(1).isRetryable());
  }

  @Test
  void getCampaignFailuresMapsNotFound() throws ObjectNotFoundException {
    when(campaignService.getCampaignFailures(404L)).thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));

    assertEquals(HttpStatus.NOT_FOUND,
                 assertThrows(ResponseStatusException.class, () -> campaignRest.getCampaignFailures(404L)).getStatusCode());
  }

  @Test
  void everyMappingOnTheControllerCarriesARoleCheck() {
    // The invariant, pinned by reflection because a MISSING annotation is
    // invisible in a diff: this webapp declares no security-constraint, so
    // @Secured is the WHOLE control on a method here, and campaign management is
    // /platform/administrators only. {id}/scan-failures shipped without it and
    // survived eleven review rounds precisely because nothing failed when it was
    // absent — reading the diff hunks can never catch that, only enumerating the
    // class can
    List<String> unsecured = Arrays.stream(CleanupCampaignRest.class.getDeclaredMethods())
                                   .filter(method -> Modifier.isPublic(method.getModifiers()))
                                   .filter(CleanupCampaignRestTest::isRequestMapping)
                                   .filter(method -> method.getAnnotation(Secured.class) == null)
                                   .map(Method::getName)
                                   .sorted()
                                   .toList();

    assertEquals(List.of(), unsecured, "Every request mapping must carry a @Secured role check");
  }

  @Test
  void theRoleCheckInvariantActuallySeesTheMappings() {
    // Guards the test above against passing vacuously: were the annotation scan
    // ever to stop matching (a mapping annotation swapped, the reflection
    // broken), an EMPTY list of unsecured methods would look like success
    long mappings = Arrays.stream(CleanupCampaignRest.class.getDeclaredMethods())
                          .filter(method -> Modifier.isPublic(method.getModifiers()))
                          .filter(CleanupCampaignRestTest::isRequestMapping)
                          .count();

    assertTrue(mappings >= 18, "The mapping scan found only " + mappings + " endpoints: it is no longer seeing the class");
  }

  private static boolean isRequestMapping(Method method) {
    return method.getAnnotation(GetMapping.class) != null || method.getAnnotation(PostMapping.class) != null
        || method.getAnnotation(PutMapping.class) != null || method.getAnnotation(PatchMapping.class) != null
        || method.getAnnotation(DeleteMapping.class) != null || method.getAnnotation(RequestMapping.class) != null;
  }

  @Test
  void deleteCampaignDeletesAndCancelDoesNotShareItsVerb() throws NoSuchMethodException {
    // Pinned by reflection because nothing else would notice them being swapped
    // back: DELETE cancelling a campaign reads fine to whoever wrote it and
    // destroys a run for whoever did not
    assertNotNull(CleanupCampaignRest.class.getMethod("deleteCampaign", long.class).getAnnotation(DeleteMapping.class),
                  "DELETE {id} must really delete");
    assertNull(CleanupCampaignRest.class.getMethod("cancelCampaign", long.class).getAnnotation(DeleteMapping.class),
               "cancel must NOT be mapped on DELETE any more");
    assertNotNull(CleanupCampaignRest.class.getMethod("cancelCampaign", long.class).getAnnotation(PostMapping.class),
                  "cancel must be a POST on its own path");
  }

  @Test
  void deleteCampaignDelegatesToTheService() throws ObjectNotFoundException {
    campaignRest.deleteCampaign(CAMPAIGN_ID);

    verify(campaignService).deleteCampaign(CAMPAIGN_ID);
  }

  @Test
  void deleteCampaignMapsARefusedStateToBadRequest() throws ObjectNotFoundException {
    doThrow(new IllegalArgumentException("cleanup.invalidState")).when(campaignService).deleteCampaign(CAMPAIGN_ID);

    // What a COMPLETED campaign answers, and what the console must show as a
    // reason rather than swallow
    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                    () -> campaignRest.deleteCampaign(CAMPAIGN_ID));
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getMessage().contains("cleanup.invalidState"));
  }

  @Test
  void deleteCampaignMapsNotFound() throws ObjectNotFoundException {
    doThrow(new ObjectNotFoundException(NOT_FOUND_CODE)).when(campaignService).deleteCampaign(404L);

    assertEquals(HttpStatus.NOT_FOUND,
                 assertThrows(ResponseStatusException.class, () -> campaignRest.deleteCampaign(404L)).getStatusCode());
  }

  @Test
  void getCampaignScanUnitsCarriesTheLostFilesAndTheirStackTrace() throws ObjectNotFoundException {
    CleanupScanUnit lossy = new CleanupScanUnit();
    lossy.setUnitPath("/Users/j___");
    lossy.setEvalFailureCount(37);
    lossy.setEvalFailureReason("NullPointerException: Cannot invoke Version.getName()");
    lossy.setEvalFailureDetail("java.lang.NullPointerException\n\tat CleanupJcrStorage.selectVersionsToRemove");
    when(campaignService.getCampaignScanUnitProgress(CAMPAIGN_ID)).thenReturn(new CleanupScanUnitProgress(40,
                                                                                                         0,
                                                                                                         0,
                                                                                                         40,
                                                                                                         0,
                                                                                                         40,
                                                                                                         1,
                                                                                                         true,
                                                                                                         37,
                                                                                                         List.of(),
                                                                                                         List.of(lossy)));

    CampaignScanUnitProgressRestEntity progress = campaignRest.getCampaignScanUnits(CAMPAIGN_ID);

    // Every subtree settled AND 37 files lost inside them: the two accountings are
    // independent, and the console needs both to stop reading 100% as "complete"
    assertTrue(progress.isScanComplete());
    assertEquals(37L, progress.getSkippedNodeCount());
    assertEquals(1, progress.getEvaluationFailures().size());
    CampaignScanUnitRestEntity failing = progress.getEvaluationFailures().get(0);
    assertEquals("/Users/j___", failing.getUnitPath());
    assertEquals(37L, failing.getEvalFailureCount());
    // The trace travels to the console: diagnosing this must not require the
    // server log, which is what made the NullPointerException behind it invisible
    assertTrue(failing.getEvalFailureDetail().contains("NullPointerException"));
  }

  @Test
  void getCampaignScanUnitsMapsTheBreakdownAndItsInFlightSubtrees() throws ObjectNotFoundException {
    CleanupScanUnit inFlight = new CleanupScanUnit();
    inFlight.setId(17L);
    inFlight.setUnitPath("/Users/j___");
    inFlight.setLastScannedPath("/Users/j___/john/a.pdf");
    inFlight.setScannedCount(12);
    inFlight.setTotalCount(40L);
    inFlight.setAttemptCount(2);
    when(campaignService.getCampaignScanUnitProgress(CAMPAIGN_ID)).thenReturn(new CleanupScanUnitProgress(540,
                                                                                                         0,
                                                                                                         1,
                                                                                                         537,
                                                                                                         2,
                                                                                                         538,
                                                                                                         3,
                                                                                                         false,
                                                                                                         0,
                                                                                                         List.of(inFlight),
                                                                                                         List.of()));

    CampaignScanUnitProgressRestEntity progress = campaignRest.getCampaignScanUnits(CAMPAIGN_ID);

    assertEquals(540L, progress.getUnitCount());
    assertEquals(538L, progress.getSettledCount());
    assertEquals(3L, progress.getMaxAttemptCount());
    // The flag the console shows completion from, INSTEAD of the node percentage
    // that would read 100% on this very campaign
    assertFalse(progress.isScanComplete());
    assertEquals(1, progress.getInFlightUnits().size());
    assertEquals("/Users/j___", progress.getInFlightUnits().get(0).getUnitPath());
    assertEquals("/Users/j___/john/a.pdf", progress.getInFlightUnits().get(0).getLastScannedPath());
    assertEquals(2L, progress.getInFlightUnits().get(0).getAttemptCount());
  }

  @Test
  void getCampaignScanUnitsMapsNotFound() throws ObjectNotFoundException {
    when(campaignService.getCampaignScanUnitProgress(404L)).thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));

    assertEquals(HttpStatus.NOT_FOUND,
                 assertThrows(ResponseStatusException.class,
                              () -> campaignRest.getCampaignScanUnits(404L)).getStatusCode());
  }

  @Test
  void getCampaignScanFailuresMapsEveryGroupOfSubtreesTheScanCouldNotWalk() throws ObjectNotFoundException {
    when(campaignService.getCampaignScanFailures(CAMPAIGN_ID)).thenReturn(List.of(new CleanupFailureGroup("cleanup.scanUnitFailed",
                                                                                                         4L,
                                                                                                         false)));

    List<CampaignFailureGroupRestEntity> scanFailures = campaignRest.getCampaignScanFailures(CAMPAIGN_ID);

    // The SAME shape as the item failures, so one console block renders both
    assertEquals(1, scanFailures.size());
    assertEquals("cleanup.scanUnitFailed", scanFailures.get(0).getReason());
    assertEquals(4L, scanFailures.get(0).getCount());
    // No console retry for a settled subtree, and the SERVER is the one saying so
    assertFalse(scanFailures.get(0).isRetryable());
  }

  @Test
  void getCampaignScanFailuresAnswersAnEmptyListForAScanCoveringTheWholeTree() throws ObjectNotFoundException {
    when(campaignService.getCampaignScanFailures(CAMPAIGN_ID)).thenReturn(List.of());

    // The normal case, and the one the console keeps its block hidden on
    assertTrue(campaignRest.getCampaignScanFailures(CAMPAIGN_ID).isEmpty());
  }

  @Test
  void getCampaignScanFailuresMapsNotFound() throws ObjectNotFoundException {
    when(campaignService.getCampaignScanFailures(404L)).thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));

    assertEquals(HttpStatus.NOT_FOUND,
                 assertThrows(ResponseStatusException.class,
                              () -> campaignRest.getCampaignScanFailures(404L)).getStatusCode());
  }

  @Test
  void getCampaignItemsServesTheFailureDetailToAdministrators() throws ObjectNotFoundException {
    CleanupCampaignItem failedItem = item(3);
    failedItem.setFailureReason("cleanup.deleteError");
    failedItem.setFailureDetail(FAILURE_DETAIL);
    when(campaignService.getCampaignItems(anyLong(), any(), any(), any(), any(), any(), any()))
                                                                                              .thenReturn(new PageImpl<>(List.of(failedItem)));

    PagedResult<CampaignItemRestEntity> result = campaignRest.getCampaignItems(CAMPAIGN_ID,
                                                                               null,
                                                                               null,
                                                                               null,
                                                                               null,
                                                                               null,
                                                                               0,
                                                                               20,
                                                                               null);

    // @Secured("administrators"): this is the ONE path allowed to expose a stack
    // trace that can name nodes outside a given user's visibility
    assertEquals(FAILURE_DETAIL, result.getItems().get(0).getFailureDetail());
  }

  @Test
  void getMyItemsWithholdsTheFailureDetailFromEndUsers() throws ObjectNotFoundException {
    CleanupCampaignItem failedItem = item(3);
    failedItem.setFailureReason("cleanup.deleteError");
    failedItem.setFailureDetail(FAILURE_DETAIL);
    when(request.getRemoteUser()).thenReturn("john");
    when(campaignService.getMyItems(eq("john"), any(), any())).thenReturn(new PageImpl<>(List.of(failedItem)));

    PagedResult<CampaignItemRestEntity> result = campaignRest.getMyItems(request, null, 0, 20, null);

    // @Secured("users"): a referential-integrity dump names the REFERENCING node,
    // e.g. a shortcut in a space the caller is not a member of
    assertNull(result.getItems().get(0).getFailureDetail(),
               "The user-facing endpoint must never serialize the failure detail");
    assertEquals("cleanup.deleteError",
                 result.getItems().get(0).getFailureReason(),
                 "The bare reason code stays: it is a localizable message code, it leaks nothing");
  }

  private CleanupCampaignItem item(long id) {
    CleanupCampaignItem item = new CleanupCampaignItem();
    item.setId(id);
    item.setCampaignId(CAMPAIGN_ID);
    item.setPath("/Users/j___/john/Private/file.pdf");
    item.setAction(CleanupAction.DELETE);
    item.setState(CleanupItemState.CANDIDATE);
    return item;
  }

}
