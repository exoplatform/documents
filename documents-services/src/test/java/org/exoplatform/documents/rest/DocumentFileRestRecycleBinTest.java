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
package org.exoplatform.documents.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.RecycleBinDrive;
import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.model.RecycleBinScope;
import org.exoplatform.documents.model.RecycleBinSortField;
import org.exoplatform.documents.rest.model.RecycleBinDriveEntity;
import org.exoplatform.documents.rest.model.RecycleBinItemEntity;
import org.exoplatform.documents.rest.model.RecycleBinSettingsEntity;
import org.exoplatform.documents.service.RecycleBinService;
import org.exoplatform.portal.rest.CollectionEntity;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.portal.thumbnail.model.FileContent;

/**
 * The entries of the Recycle Bin: which bin and which caller each one names
 * to the service, what it answers, how a refusal of the service is told, and
 * that no answer is kept by a cache. Who may do what is the service's to
 * decide and to test.
 */
class DocumentFileRestRecycleBinTest {

  private static final String ITEM_ID = "11111111-1111-1111-1111-111111111111";

  private RecycleBinService   service;

  private IdentityManager     identityManager;

  private SpaceService        spaceService;

  private DocumentFileRest    rest;

  @BeforeEach
  void setUp() {
    service = mock(RecycleBinService.class);
    identityManager = mock(IdentityManager.class);
    spaceService = mock(SpaceService.class);
    rest = new DocumentFileRest(null, spaceService, identityManager, null, null, null, null, null) {
      @Override
      RecycleBinService getRecycleBinService() {
        return service;
      }
    };
    ConversationState.setCurrent(new ConversationState(new Identity("john")));
  }

  @AfterEach
  void tearDown() {
    ConversationState.setCurrent(null);
  }

  @Test
  void aPageOfTheItemsOfTheUsersBinIsListedWithItsSize() throws Exception {
    RecycleBinItem item = new RecycleBinItem();
    item.setId(ITEM_ID);
    item.setDocumentId("document-id");
    item.setName("Report.docx");
    RecycleBinFilter filter = new RecycleBinFilter(0, "rep", RecycleBinSortField.SIZE, true, 40, 10);
    when(service.getItems(RecycleBinScope.user(), filter, "john")).thenReturn(List.of(item));
    when(service.countItems(RecycleBinScope.user(), filter, "john")).thenReturn(41);

    Response response = rest.getRecycleBinItems(null, null, null, "rep", "size", "ASC", 40, 10);

    assertEquals(200, response.getStatus());
    assertNotCached(response);
    @SuppressWarnings("unchecked")
    CollectionEntity<RecycleBinItemEntity> page = (CollectionEntity<RecycleBinItemEntity>) response.getEntity();
    assertEquals(41, page.getSize());
    assertEquals(40, page.getOffset());
    assertEquals(10, page.getLimit());
    assertEquals(1, page.getEntities().size());
    assertEquals(ITEM_ID, page.getEntities().get(0).getId());
    // The user's own bin is one its items are opened from.
    assertTrue(page.getEntities().get(0).isCanView());
    assertEquals("document-id", page.getEntities().get(0).getDocumentId());
  }

  @Test
  void theNewestDeletionComesFirstAndAPageHoldsTwentyItemsWhenNothingElseIsAsked() throws Exception {
    rest.getRecycleBinItems(" user ", null, null, null, null, null, 0, 0);
    verify(service).getItems(RecycleBinScope.user(), new RecycleBinFilter(0, null, RecycleBinSortField.DELETED_DATE, false, 0, 20), "john");

    String[] names = { "name", "originalLocation", "deletedBy", "size", "deletedDate", "unknown", " name " };
    RecycleBinSortField[] fields = { RecycleBinSortField.NAME, RecycleBinSortField.ORIGINAL_LOCATION, RecycleBinSortField.DELETED_BY,
        RecycleBinSortField.SIZE, RecycleBinSortField.DELETED_DATE, RecycleBinSortField.DELETED_DATE, RecycleBinSortField.NAME };
    for (int i = 0; i < names.length; i++) {
      ArgumentCaptor<RecycleBinFilter> filter = ArgumentCaptor.forClass(RecycleBinFilter.class);
      RecycleBinScope bin = RecycleBinScope.space(i + 100L);
      rest.getRecycleBinItems("SPACE", i + 100L, null, null, names[i], "desc", 0, 5);
      verify(service).getItems(eq(bin), filter.capture(), eq("john"));
      assertSame(fields[i], filter.getValue().getSortField(), names[i]);
      assertFalse(filter.getValue().isAscending());
    }
  }

  @Test
  void theItemsOfTheBinOfADriveAreListedWithoutWhatOpensThem() throws Exception {
    RecycleBinItem item = new RecycleBinItem();
    item.setId(ITEM_ID);
    item.setDocumentId("document-id");
    when(service.getItems(eq(RecycleBinScope.drive(7)), any(), eq("john"))).thenReturn(List.of(item));
    when(service.getItems(eq(RecycleBinScope.space(3)), any(), eq("john"))).thenReturn(List.of(item));

    @SuppressWarnings("unchecked")
    CollectionEntity<RecycleBinItemEntity> ofDrive = (CollectionEntity<RecycleBinItemEntity>) rest.getRecycleBinItems("drive", null, 7L, null, null, null, 0, 10)
                                                                                                    .getEntity();
    assertFalse(ofDrive.getEntities().get(0).isCanView());
    assertNull(ofDrive.getEntities().get(0).getDocumentId());

    @SuppressWarnings("unchecked")
    CollectionEntity<RecycleBinItemEntity> ofSpace = (CollectionEntity<RecycleBinItemEntity>) rest.getRecycleBinItems("space", 3L, null, null, null, null, 0, 10)
                                                                                                    .getEntity();
    assertTrue(ofSpace.getEntities().get(0).isCanView());
  }

  @Test
  void aBinThatIsNotNamedInFullOrAPageThatIsNoneIsRefusedBeforeTheServiceIsAsked() {
    assertEquals(400, rest.getRecycleBinItems("SPACE", null, 7L, null, null, null, 0, 10).getStatus());
    assertEquals(400, rest.getRecycleBinItems("DRIVE", 3L, null, null, null, null, 0, 10).getStatus());
    assertEquals(400, rest.getRecycleBinItems("EVERYONE", 3L, 7L, null, null, null, 0, 10).getStatus());
    assertEquals(400, rest.getRecycleBinItems(null, null, null, null, null, null, -1, 10).getStatus());
    assertEquals(400, rest.getRecycleBinItems(null, null, null, null, null, null, 0, -1).getStatus());
    assertEquals(400, rest.getRecycleBinDrives(-1, 10).getStatus());
    assertEquals(400, rest.getRecycleBinDrives(0, -1).getStatus());
    assertEquals(400, rest.restoreRecycleBinItem(ITEM_ID, "SPACE", null, null).getStatus());
    assertEquals(400, rest.deleteRecycleBinItem(ITEM_ID, "DRIVE", null, null).getStatus());
    assertEquals(400, rest.restoreRecycleBinItems("1", "nowhere", null, null, List.of(ITEM_ID)).getStatus());
    assertEquals(400, rest.deleteRecycleBinItems("1", "SPACE", null, null, List.of(ITEM_ID)).getStatus());
    Response response = rest.emptyRecycleBin("1", "DRIVE", null, null);
    assertEquals(400, response.getStatus());
    assertNotCached(response);
    assertNull(response.getEntity());

    verifyNoInteractions(service);
  }

  @Test
  void anItemIsRestoredAndRemovedInTheBinTheRequestNames() throws Exception {
    Response restored = rest.restoreRecycleBinItem(ITEM_ID, "SPACE", 3L, null);
    Response removed = rest.deleteRecycleBinItem(ITEM_ID, "DRIVE", null, 7L);

    assertEquals(204, restored.getStatus());
    assertEquals(204, removed.getStatus());
    assertNotCached(restored);
    assertNotCached(removed);
    verify(service).restoreItem(ITEM_ID, RecycleBinScope.space(3), "john");
    verify(service).deleteItem(ITEM_ID, RecycleBinScope.drive(7), "john");
  }

  @Test
  void theBulkActionsAndTheEmptyingAreStartedInTheBinTheRequestNames() throws Exception {
    List<String> ids = List.of(ITEM_ID, "other");

    assertEquals(204, rest.restoreRecycleBinItems("12", null, null, null, ids).getStatus());
    assertEquals(204, rest.deleteRecycleBinItems("13", "SPACE", 3L, null, ids).getStatus());
    Response emptied = rest.emptyRecycleBin("14", "DRIVE", null, 7L);

    assertEquals(204, emptied.getStatus());
    assertNotCached(emptied);
    verify(service).restoreItems("12", ids, RecycleBinScope.user(), "john");
    verify(service).deleteItems("13", ids, RecycleBinScope.space(3), "john");
    verify(service).emptyBin("14", RecycleBinScope.drive(7), "john");
  }

  @Test
  void whatTheServiceRefusesIsToldByItsStatusWithTheCodeAScreenHasALabelForAndNothingElse() throws Exception {
    doThrow(new ObjectNotFoundException("recycleBin.item.notFound"),
            new IllegalAccessException("recycleBin.restore.targetForbidden"),
            new IllegalArgumentException("recycleBin.restore.locationMissing"),
            new IllegalAccessException("User john neither manages nor publishes in the space 3"),
            new ObjectNotFoundException("Space with id 3 is not found"),
            new IllegalStateException("The item cannot be restored")).when(service).restoreItem(anyString(), any(), anyString());

    int[] statuses = { 404, 403, 400, 403, 404, 500 };
    String[] entities = { "recycleBin.item.notFound", "recycleBin.restore.targetForbidden", "recycleBin.restore.locationMissing", null, null,
        null };
    for (int i = 0; i < statuses.length; i++) {
      Response response = rest.restoreRecycleBinItem(ITEM_ID, null, null, null);
      assertEquals(statuses[i], response.getStatus());
      assertEquals(entities[i], response.getEntity());
      assertNotCached(response);
    }
  }

  @Test
  void aRequestOfNoUserNamesNoCallerToTheService() throws Exception {
    ConversationState.setCurrent(null);
    doThrow(new IllegalAccessException("A Recycle Bin is opened by a user of the platform only")).when(service).deleteItem(any(), any(), isNull());

    assertEquals(403, rest.deleteRecycleBinItem(ITEM_ID, null, null, null).getStatus());

    verify(service).deleteItem(ITEM_ID, RecycleBinScope.user(), null);
  }

  @Test
  void theContentOfAnItemIsServedWithItsNameAndItsTypeAndIsNotKept() throws Exception {
    InputStream stream = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
    when(service.getItemContent(ITEM_ID, "john")).thenReturn(new FileContent("document-id", "Bilan 2026 é.docx", "application/msword", stream, null),
                                                             new FileContent("document-id", null, " ", stream, null));

    Response response = rest.getRecycleBinItemContent(ITEM_ID);

    assertEquals(200, response.getStatus());
    assertSame(stream, response.getEntity());
    assertEquals("application/msword", String.valueOf(response.getMetadata().getFirst("Content-Type")));
    assertEquals("filename=\"Bilan%202026%20%C3%A9.docx\"; filename*=UTF-8''Bilan%202026%20%C3%A9.docx",
                 response.getMetadata().getFirst("Content-Disposition"));
    assertEquals("nosniff", response.getMetadata().getFirst("X-Content-Type-Options"));
    assertNotCached(response);
    // Neither a tag nor a date a browser would ask again with.
    assertNull(response.getMetadata().getFirst("ETag"));
    assertNull(response.getMetadata().getFirst("Last-Modified"));
    assertNull(response.getMetadata().getFirst("Expires"));

    Response untyped = rest.getRecycleBinItemContent(ITEM_ID);
    assertEquals("application/octet-stream", String.valueOf(untyped.getMetadata().getFirst("Content-Type")));
    assertEquals("filename=\"\"; filename*=UTF-8''", untyped.getMetadata().getFirst("Content-Disposition"));
  }

  @Test
  void aContentTheCallerMayNotReadOrThatIsGoneIsRefused() throws Exception {
    when(service.getItemContent(ITEM_ID, "john")).thenThrow(new IllegalAccessException("User john may not read the item"),
                                                             new ObjectNotFoundException("recycleBin.item.notFound"));

    Response forbidden = rest.getRecycleBinItemContent(ITEM_ID);
    assertEquals(403, forbidden.getStatus());
    assertNull(forbidden.getEntity());
    assertNotCached(forbidden);
    assertEquals(404, rest.getRecycleBinItemContent(ITEM_ID).getStatus());
  }

  @Test
  void theDrivesAreListedByPageWithWhatThePlatformLetsItsAdministratorsDo() throws Exception {
    when(service.getDrives(20, 10, "john")).thenReturn(List.of(new RecycleBinDrive(7, 150, 3)));
    when(service.getDrives(0, 20, "john")).thenReturn(List.of());
    when(service.countDrives("john")).thenReturn(21L);
    when(service.isAdministratorAccessEnabled()).thenReturn(true, false);

    Response response = rest.getRecycleBinDrives(20, 10);

    assertEquals(200, response.getStatus());
    assertNotCached(response);
    @SuppressWarnings("unchecked")
    CollectionEntity<RecycleBinDriveEntity> page = (CollectionEntity<RecycleBinDriveEntity>) response.getEntity();
    assertEquals(21, page.getSize());
    assertEquals(20, page.getOffset());
    assertEquals(10, page.getLimit());
    assertEquals(1, page.getEntities().size());
    assertEquals(7, page.getEntities().get(0).getOwnerIdentityId());
    assertEquals(150, page.getEntities().get(0).getSize());
    assertEquals(Boolean.TRUE, page.get("administratorAccessEnabled"));

    // A page of twenty drives when no size is asked.
    @SuppressWarnings("unchecked")
    CollectionEntity<RecycleBinDriveEntity> first = (CollectionEntity<RecycleBinDriveEntity>) rest.getRecycleBinDrives(0, 0).getEntity();
    assertEquals(20, first.getLimit());
    assertEquals(Boolean.FALSE, first.get("administratorAccessEnabled"));

    when(service.getDrives(anyLong(), anyInt(), anyString())).thenThrow(new IllegalAccessException("User john is no administrator of the platform"));
    assertEquals(403, rest.getRecycleBinDrives(0, 10).getStatus());
  }

  @Test
  void theRetentionPeriodIsReadAndChanged() throws Exception {
    when(service.getRetentionDays()).thenReturn(45L);

    Response read = rest.getRecycleBinSettings();
    assertEquals(200, read.getStatus());
    assertNotCached(read);
    assertEquals(Long.valueOf(45), assertInstanceOf(RecycleBinSettingsEntity.class, read.getEntity()).getRetentionDays());

    Response changed = rest.updateRecycleBinSettings(new RecycleBinSettingsEntity(10L));
    assertEquals(204, changed.getStatus());
    assertNotCached(changed);
    verify(service).setRetentionDays(10, "john");
  }

  @Test
  void aPeriodThatIsMissingOrRefusedIsToldWithItsCode() throws Exception {
    Response missing = rest.updateRecycleBinSettings(null);
    assertEquals(400, missing.getStatus());
    assertEquals("recycleBin.retention.invalid", missing.getEntity());
    assertEquals(400, rest.updateRecycleBinSettings(new RecycleBinSettingsEntity()).getStatus());
    verifyNoInteractions(service);

    doThrow(new IllegalArgumentException("recycleBin.retention.invalid"), new IllegalAccessException("User john is no administrator of the platform"))
                                                                                                                                                 .when(service)
                                                                                                                                                 .setRetentionDays(anyLong(),
                                                                                                                                                                   anyString());
    Response refused = rest.updateRecycleBinSettings(new RecycleBinSettingsEntity(400L));
    assertEquals(400, refused.getStatus());
    assertEquals("recycleBin.retention.invalid", refused.getEntity());
    assertEquals(403, rest.updateRecycleBinSettings(new RecycleBinSettingsEntity(10L)).getStatus());
  }

  @Test
  void theDrivesAndTheChangeOfTheSettingsAreOpenToTheAdministratorsAndTheRestToTheUsers() throws Exception {
    assertRole("administrators", "getRecycleBinDrives", int.class, int.class);
    assertRole("administrators", "updateRecycleBinSettings", RecycleBinSettingsEntity.class);
    assertRole("users", "getRecycleBinSettings");
    assertRole("users", "getRecycleBinItems", String.class, Long.class, Long.class, String.class, String.class, String.class, int.class, int.class);
    assertRole("users", "getRecycleBinItemContent", String.class);
    assertRole("users", "restoreRecycleBinItem", String.class, String.class, Long.class, Long.class);
    assertRole("users", "deleteRecycleBinItem", String.class, String.class, Long.class, Long.class);
    assertRole("users", "restoreRecycleBinItems", String.class, String.class, Long.class, Long.class, List.class);
    assertRole("users", "deleteRecycleBinItems", String.class, String.class, Long.class, Long.class, List.class);
    assertRole("users", "emptyRecycleBin", String.class, String.class, Long.class, Long.class);
  }

  private static void assertRole(String role, String method, Class<?>... parameters) throws Exception {
    RolesAllowed allowed = DocumentFileRest.class.getMethod(method, parameters).getAnnotation(RolesAllowed.class);
    assertEquals(List.of(role), List.of(allowed.value()), method);
  }

  private static void assertNotCached(Response response) {
    CacheControl cacheControl = assertInstanceOf(CacheControl.class, response.getMetadata().getFirst("Cache-Control"));
    assertTrue(cacheControl.isNoStore());
    assertTrue(cacheControl.isPrivate());
  }

}
