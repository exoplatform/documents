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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.springframework.web.server.ResponseStatusException;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.document.cleanup.rest.model.KeepItemsRestEntity;
import org.exoplatform.document.cleanup.service.CleanupCampaignService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Direct-call controller tests pinning the delegation of the keep endpoints to
 * {@link CleanupCampaignService} (with the authenticated user, never a
 * client-supplied one) and the full exception contract:
 * {@link ObjectNotFoundException} to 404, {@link IllegalAccessException} to
 * 403, {@link IllegalArgumentException} to 400 with the message code.
 */
@ExtendWith(MockitoExtension.class)
class CleanupItemRestTest {

  private static final long      ITEM_ID = 42L;

  private static final String    USER    = "john";

  @Mock
  private CleanupCampaignService campaignService;

  @Mock
  private HttpServletRequest     request;

  @InjectMocks
  private CleanupItemRest        itemRest;

  @BeforeEach
  void setUp() {
    when(request.getRemoteUser()).thenReturn(USER);
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
  void keepItemMapsIllegalAccessTo403() throws Exception {
    doThrow(new IllegalAccessException("cleanup.notOwner")).when(campaignService).keepItem(ITEM_ID, USER);

    ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> itemRest.keepItem(request, ITEM_ID));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    assertEquals("cleanup.notOwner", exception.getReason());
  }

  @Test
  void keepItemMapsIllegalArgumentTo400() throws Exception {
    doThrow(new IllegalArgumentException("cleanup.invalidState")).when(campaignService).keepItem(ITEM_ID, USER);

    ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> itemRest.keepItem(request, ITEM_ID));
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("cleanup.invalidState", exception.getReason());
  }

  @Test
  void keepItemsDelegatesWithAuthenticatedUser() throws Exception {
    KeepItemsRestEntity body = new KeepItemsRestEntity();
    body.setItemIds(List.of(1L, 2L, 3L));

    itemRest.keepItems(request, body);

    verify(campaignService).keepItems(List.of(1L, 2L, 3L), USER);
  }

  @Test
  void keepItemsMapsExceptionContract() throws Exception {
    KeepItemsRestEntity body = new KeepItemsRestEntity();
    body.setItemIds(List.of(ITEM_ID));

    doThrow(new ObjectNotFoundException("cleanup.itemNotFound")).when(campaignService).keepItems(anyList(), eq(USER));
    assertEquals(HttpStatus.NOT_FOUND,
                 assertThrows(ResponseStatusException.class, () -> itemRest.keepItems(request, body)).getStatusCode());

    doThrow(new IllegalAccessException("cleanup.notOwner")).when(campaignService).keepItems(anyList(), eq(USER));
    assertEquals(HttpStatus.FORBIDDEN,
                 assertThrows(ResponseStatusException.class, () -> itemRest.keepItems(request, body)).getStatusCode());

    doThrow(new IllegalArgumentException("cleanup.invalidState")).when(campaignService).keepItems(anyList(), eq(USER));
    ResponseStatusException badRequest = assertThrows(ResponseStatusException.class, () -> itemRest.keepItems(request, body));
    assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatusCode());
    assertEquals("cleanup.invalidState", badRequest.getReason());
  }

}
