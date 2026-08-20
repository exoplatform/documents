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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.document.cleanup.model.CleanupBulkResult;
import org.exoplatform.document.cleanup.rest.model.KeepItemsRestEntity;
import org.exoplatform.document.cleanup.rest.model.KeepItemsResultRestEntity;
import org.exoplatform.document.cleanup.service.CleanupCampaignService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Direct-call controller tests pinning the delegation of the keep endpoints to
 * {@link CleanupCampaignService} (with the authenticated user, never a
 * client-supplied one) and the full exception contract:
 * {@link ObjectNotFoundException} to 404, {@link IllegalAccessException} to 403
 * with {@link CleanupCampaignService#NOT_OWNER_FAILURE_CODE} (never its own raw
 * message), {@link IllegalArgumentException} to 400 with the message code.
 */
@ExtendWith(MockitoExtension.class)
class CleanupItemRestTest {

  private static final long      ITEM_ID                     = 42L;

  private static final String    USER                        = "john";

  /**
   * Verbatim shape of what {@code CleanupCampaignService.checkOwnership} raises:
   * a raw English sentence carrying the username. Nothing of it may reach the
   * client.
   */
  private static final String    NOT_OWNER_INTERNAL_SENTENCE = "User %s isn't the owner of the file".formatted(USER);

  @Mock
  private CleanupCampaignService campaignService;

  @Mock
  private HttpServletRequest     request;

  @InjectMocks
  private CleanupItemRest        itemRest;

  @BeforeEach
  void setUp() {
    // Lenient: the annotation-contract test never goes through the request
    org.mockito.Mockito.lenient().when(request.getRemoteUser()).thenReturn(USER);
  }

  @Test
  void keepItemDelegatesWithAuthenticatedUser() throws Exception {
    itemRest.keepItem(request, ITEM_ID);

    verify(campaignService).keepItem(ITEM_ID, USER);
  }

  @Test
  void keepItemMapsNotFoundTo404() throws Exception {
    doThrow(new ObjectNotFoundException("cleanup.itemNotFound")).when(campaignService).keepItem(ITEM_ID, USER);

    ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> itemRest.keepItem(request, ITEM_ID));
    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    assertEquals("cleanup.itemNotFound", exception.getReason());
  }

  @Test
  void keepItemMapsIllegalAccessTo403WithTheMessageCodeNeverTheInternalSentence() throws Exception {
    // The REAL exception checkOwnership raises: a raw English sentence naming the
    // user and the owning space. Forwarding e.getMessage() put that internal
    // detail on the wire AND matched no bundle key, so the user read 'an
    // unexpected error occurred' and cleanup.notOwner could never render
    doThrow(new IllegalAccessException(NOT_OWNER_INTERNAL_SENTENCE)).when(campaignService).keepItem(ITEM_ID, USER);

    ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> itemRest.keepItem(request, ITEM_ID));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    // The very same code the BULK path reports for this exact refusal
    assertEquals(CleanupCampaignService.NOT_OWNER_FAILURE_CODE, exception.getReason());
    assertFalse(exception.getReason().contains(USER), "No internal sentence naming the user may reach the client");
  }

  @Test
  void keepItemMapsIllegalArgumentTo400() throws Exception {
    doThrow(new IllegalArgumentException("cleanup.invalidState")).when(campaignService).keepItem(ITEM_ID, USER);

    ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> itemRest.keepItem(request, ITEM_ID));
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("cleanup.invalidState", exception.getReason());
  }

  @Test
  void keepItemsDelegatesWithAuthenticatedUserAndReturnsTheOutcomes() throws Exception {
    KeepItemsRestEntity body = new KeepItemsRestEntity();
    body.setItemIds(List.of(1L, 2L, 3L));
    CleanupBulkResult result = new CleanupBulkResult();
    result.setSucceeded(2);
    result.addFailure(3L, "cleanup.itemNotFound");
    when(campaignService.keepItems(List.of(1L, 2L, 3L), USER)).thenReturn(result);

    KeepItemsResultRestEntity response = itemRest.keepItems(request, body);

    verify(campaignService).keepItems(List.of(1L, 2L, 3L), USER);
    // 200 with the per-item outcomes, never a blanket 204: the UI must be able
    // to warn when part of the bulk keep failed
    assertEquals(2, response.getSucceeded());
    assertEquals(1, response.getFailures().size());
    assertEquals(3L, response.getFailures().get(0).getItemId());
    assertEquals("cleanup.itemNotFound", response.getFailures().get(0).getReason());
  }

  @Test
  void bulkEndpointsNeverAnswerNoContent() throws NoSuchMethodException {
    // A 204 would make a fully-failed bulk keep indistinguishable from a
    // success
    assertNull(CleanupItemRest.class.getMethod("keepItems", HttpServletRequest.class, KeepItemsRestEntity.class)
                                    .getAnnotation(ResponseStatus.class));
    assertNull(CleanupItemRest.class.getMethod("unkeepItems", HttpServletRequest.class, KeepItemsRestEntity.class)
                                    .getAnnotation(ResponseStatus.class));
  }

  @Test
  void keepItemsMapsIllegalArgumentTo400() {
    // The bulk keep continues past individual failures (like the bulk
    // un-keep): only the empty-ids validation surfaces as a 400
    KeepItemsRestEntity body = new KeepItemsRestEntity();
    body.setItemIds(List.of());

    doThrow(new IllegalArgumentException("cleanup.itemIdsMandatory")).when(campaignService).keepItems(anyList(), eq(USER));

    ResponseStatusException badRequest = assertThrows(ResponseStatusException.class, () -> itemRest.keepItems(request, body));
    assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatusCode());
    assertEquals("cleanup.itemIdsMandatory", badRequest.getReason());
  }

  @Test
  void unkeepItemDelegatesWithAuthenticatedUser() throws Exception {
    itemRest.unkeepItem(request, ITEM_ID);

    verify(campaignService).unkeepItem(ITEM_ID, USER);
  }

  @Test
  void unkeepItemMapsExceptionContract() throws Exception {
    doThrow(new ObjectNotFoundException("cleanup.itemNotFound")).when(campaignService).unkeepItem(ITEM_ID, USER);
    assertEquals(HttpStatus.NOT_FOUND,
                 assertThrows(ResponseStatusException.class, () -> itemRest.unkeepItem(request, ITEM_ID)).getStatusCode());

    // Same authz contract as keepItem: the localizable code, never the raw
    // English sentence checkOwnership builds with the username in it
    doThrow(new IllegalAccessException(NOT_OWNER_INTERNAL_SENTENCE)).when(campaignService).unkeepItem(ITEM_ID, USER);
    ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                                                     () -> itemRest.unkeepItem(request, ITEM_ID));
    assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    assertEquals(CleanupCampaignService.NOT_OWNER_FAILURE_CODE, forbidden.getReason());
    assertFalse(forbidden.getReason().contains(USER), "No internal sentence naming the user may reach the client");

    doThrow(new IllegalArgumentException("cleanup.reviewClosed")).when(campaignService).unkeepItem(ITEM_ID, USER);
    ResponseStatusException badRequest = assertThrows(ResponseStatusException.class,
                                                      () -> itemRest.unkeepItem(request, ITEM_ID));
    assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatusCode());
    assertEquals("cleanup.reviewClosed", badRequest.getReason());
  }

  @Test
  void unkeepItemsDelegatesWithAuthenticatedUserAndReturnsTheOutcomes() {
    KeepItemsRestEntity body = new KeepItemsRestEntity();
    body.setItemIds(List.of(1L, 2L, 3L));
    CleanupBulkResult result = new CleanupBulkResult();
    result.setSucceeded(3);
    when(campaignService.unkeepItems(List.of(1L, 2L, 3L), USER)).thenReturn(result);

    KeepItemsResultRestEntity response = itemRest.unkeepItems(request, body);

    verify(campaignService).unkeepItems(List.of(1L, 2L, 3L), USER);
    assertEquals(3, response.getSucceeded());
    assertTrue(response.getFailures().isEmpty());
  }

  @Test
  void unkeepItemsMapsIllegalArgumentTo400() {
    KeepItemsRestEntity body = new KeepItemsRestEntity();
    body.setItemIds(List.of());

    doThrow(new IllegalArgumentException("cleanup.itemIdsMandatory")).when(campaignService).unkeepItems(anyList(), eq(USER));

    ResponseStatusException badRequest = assertThrows(ResponseStatusException.class,
                                                      () -> itemRest.unkeepItems(request, body));
    assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatusCode());
    assertEquals("cleanup.itemIdsMandatory", badRequest.getReason());
  }

}
