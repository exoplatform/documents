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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
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
import org.exoplatform.document.cleanup.model.CleanupParams;
import org.exoplatform.document.cleanup.model.CleanupUserSummary;
import org.exoplatform.document.cleanup.rest.model.CampaignComparisonRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignItemRestEntity;
import org.exoplatform.document.cleanup.rest.model.CampaignRestEntity;
import org.exoplatform.document.cleanup.rest.model.MyItemsSummaryRestEntity;
import org.exoplatform.document.cleanup.rest.model.PagedResult;
import org.exoplatform.document.cleanup.service.CleanupCampaignService;
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
  void getDefaultsDelegatesToService() {
    CleanupParams defaults = new CleanupParams(6, 1048576L, 7, 5, List.of(), 200);
    when(campaignService.getDefaultParams()).thenReturn(defaults);

    assertEquals(defaults, campaignRest.getDefaults());
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
                                          any())).thenReturn(new PageImpl<>(List.of(item(3))));

    PagedResult<CampaignItemRestEntity> result = campaignRest.getCampaignItems(CAMPAIGN_ID,
                                                                               7L,
                                                                               "candidate",
                                                                               "delete",
                                                                               1024L,
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
                                             pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();
    assertEquals(PageRequest.of(2, 5, Sort.by(Sort.Direction.ASC, "path")), pageable);
  }

  @Test
  void getCampaignItemsDefaultsToFileSizeDescendingSort() throws ObjectNotFoundException {
    when(campaignService.getCampaignItems(anyLong(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

    campaignRest.getCampaignItems(CAMPAIGN_ID, null, null, null, null, 0, 20, null);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(campaignService).getCampaignItems(eq(CAMPAIGN_ID),
                                             eq((Long) null),
                                             eq((CleanupItemState) null),
                                             eq((CleanupAction) null),
                                             eq((Long) null),
                                             pageableCaptor.capture());
    assertEquals(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "fileSize")), pageableCaptor.getValue());
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
                                                                                         0,
                                                                                         20,
                                                                                         null));
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("cleanup.invalidFilterValue", exception.getReason());
  }

  @Test
  void getCampaignItemsMapsNotFoundTo404() throws ObjectNotFoundException {
    when(campaignService.getCampaignItems(anyLong(), any(), any(), any(), any(), any()))
                                                                                        .thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                     () -> campaignRest.getCampaignItems(CAMPAIGN_ID,
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
    // scan and the purge run in a worker and are followed on the CometD channel,
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
  void getMyItemsPassesRemoteUserAndPaging() throws ObjectNotFoundException {
    when(request.getRemoteUser()).thenReturn("john");
    when(campaignService.getMyItems("john", 1, 10)).thenReturn(new PageImpl<>(List.of(item(5))));

    PagedResult<CampaignItemRestEntity> result = campaignRest.getMyItems(request, 1, 10);

    assertEquals(1, result.getItems().size());
    assertEquals(5, result.getItems().get(0).getId());
    verify(campaignService).getMyItems("john", 1, 10);
  }

  @Test
  void getMyItemsMapsNotFoundTo404() throws ObjectNotFoundException {
    when(request.getRemoteUser()).thenReturn("john");
    when(campaignService.getMyItems(eq("john"), anyInt(), anyInt())).thenThrow(new ObjectNotFoundException(NOT_FOUND_CODE));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                                     () -> campaignRest.getMyItems(request, 0, 20));
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

  private CleanupCampaign campaign(long id, String name) {
    CleanupCampaign campaign = new CleanupCampaign();
    campaign.setId(id);
    campaign.setName(name);
    campaign.setState(CleanupCampaignState.DRAFT);
    return campaign;
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
