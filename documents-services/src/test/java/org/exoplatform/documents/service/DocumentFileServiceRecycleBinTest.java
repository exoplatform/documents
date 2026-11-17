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
package org.exoplatform.documents.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.AccessDeniedException;
import javax.jcr.PathNotFoundException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.model.RecycleBinScope;
import org.exoplatform.documents.model.RecycleBinSortField;
import org.exoplatform.documents.model.TrashElementNode;
import org.exoplatform.documents.model.TrashElementNodeFilter;
import org.exoplatform.documents.storage.BulkItemAction;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.documents.storage.RecycleBinStorage;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * The former trash methods of the documents service, which name no caller:
 * each one acts for the user of the current conversation state, on a Recycle
 * Bin that user holds, through the Recycle Bin service.
 */
class DocumentFileServiceRecycleBinTest {

  private static final String     PATH    = "/Trash/Users/j___/jo___/joh___/john/report.docx";

  private static final String     ITEM_ID = "11111111-1111-1111-1111-111111111111";

  private RecycleBinService       recycleBinService;

  private RecycleBinStorage       recycleBinStorage;

  private IdentityManager         identityManager;

  private JCRDeleteFileStorage    jcrDeleteFileStorage;

  private IdentityRegistry        identityRegistry;

  private final org.exoplatform.services.security.Identity johnAcl = new org.exoplatform.services.security.Identity("john");

  private DocumentFileServiceImpl service;

  @BeforeEach
  void setUp() {
    recycleBinService = mock(RecycleBinService.class);
    recycleBinStorage = mock(RecycleBinStorage.class);
    identityManager = mock(IdentityManager.class);
    SpaceService spaceService = mock(SpaceService.class);
    Identity john = new Identity("organization", "john");
    Identity sales = new Identity("space", "sales");
    when(identityManager.getIdentity("7")).thenReturn(john);
    when(identityManager.getIdentity("20")).thenReturn(sales);
    when(identityManager.getIdentity(7L)).thenReturn(john);
    jcrDeleteFileStorage = mock(JCRDeleteFileStorage.class);
    identityRegistry = mock(IdentityRegistry.class);
    when(identityRegistry.getIdentity("john")).thenReturn(johnAcl);
    Space space = new Space();
    space.setId("3");
    when(spaceService.getSpaceByPrettyName("sales")).thenReturn(space);
    service = new DocumentFileServiceImpl(null,
                                          jcrDeleteFileStorage,
                                          null,
                                          spaceService,
                                          identityManager,
                                          identityRegistry,
                                          null,
                                          null,
                                          null,
                                          null,
                                          null,
                                          null) {
      @Override
      RecycleBinService getRecycleBinService() {
        return recycleBinService;
      }

      @Override
      RecycleBinStorage getRecycleBinStorage() {
        return recycleBinStorage;
      }
    };
    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity("john")));
  }

  @AfterEach
  void tearDown() {
    ConversationState.setCurrent(null);
  }

  @Test
  void withNoUserActingEveryFormerTrashMethodIsRefusedAndNothingIsRead() {
    for (ConversationState nobody : new ConversationState[] { null,
                                                              new ConversationState(new org.exoplatform.services.security.Identity("__anonim")) }) {
      ConversationState.setCurrent(nobody);

      assertThrows(AccessDeniedException.class, () -> service.getDeletedDocuments(new TrashElementNodeFilter()));
      assertTrue(assertThrows(IllegalStateException.class, () -> service.countDeletedDocuments()).getCause() instanceof AccessDeniedException);
      assertThrows(AccessDeniedException.class, () -> service.restoreDocumentFromTrash(PATH));
      assertThrows(AccessDeniedException.class, () -> service.deleteDocumentPermanently(PATH));
    }
    verifyNoInteractions(recycleBinService, recycleBinStorage);
  }

  @Test
  void theDeletedDocumentsListedAndCountedAreTheOnesOfTheUsersOwnBin() throws Exception {
    RecycleBinItem item = item(7L);
    item.setDocumentId("document-id");
    item.setName("Report.docx");
    item.setRestorePath("/Users/j___/jo___/joh___/john/Private/report.docx");
    item.setMimeType("application/msword");
    item.setSize(5);
    item.setDeletedDate(1234L);
    RecycleBinFilter byName = new RecycleBinFilter(0, null, RecycleBinSortField.NAME, true, 20, 10);
    RecycleBinFilter byDate = new RecycleBinFilter(0, null, RecycleBinSortField.DELETED_DATE, false, 0, 5);
    when(recycleBinService.getItems(RecycleBinScope.user(), byName, "john")).thenReturn(List.of(item));
    when(recycleBinService.getItems(RecycleBinScope.user(), byDate, "john")).thenReturn(List.of());
    when(recycleBinService.countItems(RecycleBinScope.user(), null, "john")).thenReturn(4);

    List<TrashElementNode> listed = service.getDeletedDocuments(new TrashElementNodeFilter(DocumentSortField.NAME, true, 10, 20));

    assertEquals(1, listed.size());
    TrashElementNode element = listed.get(0);
    assertEquals("document-id", element.getId());
    assertEquals("Report.docx", element.getName());
    assertEquals(PATH, element.getPath());
    assertEquals("/Users/j___/jo___/joh___/john/Private/report.docx", element.getRestorePath());
    assertEquals("application/msword", element.getMimeType());
    assertEquals(5, element.getSize());
    assertEquals(1234L, element.getModifiedDate());
    assertFalse(element.isFolder());
    // A sort the bin does not know is the date of the deletion.
    assertTrue(service.getDeletedDocuments(new TrashElementNodeFilter(DocumentSortField.MODIFIED_DATE, false, 5, 0)).isEmpty());
    verify(recycleBinService).getItems(RecycleBinScope.user(), byDate, "john");
    assertEquals(4, service.countDeletedDocuments());
  }

  @Test
  void anItemIsRestoredAndRemovedThroughTheBinItsHolderNames() throws Exception {
    when(recycleBinStorage.getItemAt(PATH)).thenReturn(item(7L), item(20L), item(7L), item(20L));

    service.restoreDocumentFromTrash(PATH);
    service.restoreDocumentFromTrash(PATH);
    service.deleteDocumentPermanently(PATH);
    service.deleteDocumentPermanently(PATH);

    verify(recycleBinService).restoreItem(ITEM_ID, RecycleBinScope.user(), "john");
    verify(recycleBinService).restoreItem(ITEM_ID, RecycleBinScope.space(3), "john");
    verify(recycleBinService).deleteItem(ITEM_ID, RecycleBinScope.user(), "john");
    verify(recycleBinService).deleteItem(ITEM_ID, RecycleBinScope.space(3), "john");
  }

  @Test
  void aPathThatIsNoItemOfABinAndAnItemOfNoBinAUserHoldsAreNotActedOn() throws Exception {
    when(recycleBinStorage.getItemAt(PATH)).thenReturn(null, null, item(null), item(null), item(99L), item(99L));

    assertThrows(PathNotFoundException.class, () -> service.restoreDocumentFromTrash(PATH));
    assertThrows(ObjectNotFoundException.class, () -> service.deleteDocumentPermanently(PATH));
    // An item of no drive, then one whose owner is gone.
    assertThrows(AccessDeniedException.class, () -> service.restoreDocumentFromTrash(PATH));
    assertThrows(AccessDeniedException.class, () -> service.deleteDocumentPermanently(PATH));
    assertThrows(AccessDeniedException.class, () -> service.restoreDocumentFromTrash(PATH));
    assertThrows(AccessDeniedException.class, () -> service.deleteDocumentPermanently(PATH));

    verify(recycleBinService, never()).restoreItem(anyString(), any(), anyString());
    verify(recycleBinService, never()).deleteItem(anyString(), any(), anyString());
  }

  @Test
  void whatTheRecycleBinRefusesIsToldAsARefusalOfTheRepository() throws Exception {
    when(recycleBinStorage.getItemAt(PATH)).thenReturn(item(7L));
    doThrow(new IllegalAccessException("recycleBin.restore.targetForbidden"), new ObjectNotFoundException("recycleBin.item.notFound"))
                                                                                                                                  .when(recycleBinService)
                                                                                                                                  .restoreItem(ITEM_ID,
                                                                                                                                               RecycleBinScope.user(),
                                                                                                                                               "john");
    ObjectNotFoundException gone = new ObjectNotFoundException("recycleBin.item.notFound");
    doThrow(new IllegalAccessException("not the holder"), gone).when(recycleBinService).deleteItem(ITEM_ID, RecycleBinScope.user(), "john");
    when(recycleBinService.getItems(any(), any(), anyString())).thenThrow(new IllegalAccessException("no identity"));
    when(recycleBinService.countItems(any(), any(), anyString())).thenThrow(new ObjectNotFoundException("no identity"));

    assertEquals("recycleBin.restore.targetForbidden",
                 assertThrows(AccessDeniedException.class, () -> service.restoreDocumentFromTrash(PATH)).getMessage());
    assertThrows(PathNotFoundException.class, () -> service.restoreDocumentFromTrash(PATH));
    assertThrows(AccessDeniedException.class, () -> service.deleteDocumentPermanently(PATH));
    assertEquals(gone, assertThrows(ObjectNotFoundException.class, () -> service.deleteDocumentPermanently(PATH)));
    assertThrows(AccessDeniedException.class, () -> service.getDeletedDocuments(null));
    assertThrows(IllegalStateException.class, () -> service.countDeletedDocuments());
  }

  @Test
  void theFormerBulkActionsActOnEachItemAtItsTurnThroughTheBinItsHolderNames() throws Exception {
    String otherPath = "/Trash/Users/j___/jo___/joh___/john/plan.docx";

    service.restoreDocuments(5, nodes("document-id", PATH, null, otherPath), 7L);

    ArgumentCaptor<ActionData> data = ArgumentCaptor.forClass(ActionData.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<BulkItemAction> action = ArgumentCaptor.forClass(BulkItemAction.class);
    verify(jcrDeleteFileStorage).executeBulkAction(data.capture(), keys.capture(), action.capture());
    assertEquals("5", data.getValue().getActionId());
    assertEquals("RESTORE", data.getValue().getActionType());
    assertSame(johnAcl, data.getValue().getIdentity());
    assertEquals("john", data.getValue().getUserName());
    assertEquals(2, data.getValue().getNumberOfItems());
    // Each item reports under the id the screen gave it, or under its path when it gave none.
    assertEquals(List.of("document-id", otherPath), keys.getValue());
    verifyNoInteractions(recycleBinService, recycleBinStorage);

    when(recycleBinStorage.getItemAt(PATH)).thenReturn(item(7L), item(20L), item(null), item(7L));
    assertTrue(action.getValue().apply("document-id"));
    verify(recycleBinService).restoreItem(ITEM_ID, RecycleBinScope.user(), "john");
    assertTrue(action.getValue().apply("document-id"));
    verify(recycleBinService).restoreItem(ITEM_ID, RecycleBinScope.space(3), "john");
    // In no bin a user holds: a failure of the item.
    assertThrows(AccessDeniedException.class, () -> action.getValue().apply("document-id"));
    // No longer an item, at its path or for the bin: left out.
    assertFalse(action.getValue().apply(otherPath));
    doThrow(new ObjectNotFoundException("recycleBin.item.notFound")).when(recycleBinService).restoreItem(ITEM_ID, RecycleBinScope.user(), "john");
    assertFalse(action.getValue().apply("document-id"));
    verify(recycleBinService, never()).deleteItem(anyString(), any(), anyString());
  }

  @Test
  void theFormerBulkRemovalRemovesEachItemForGoodThroughItsBin() throws Exception {
    service.deleteDocumentsPermanently(6, nodes("document-id", PATH), 7L);

    ArgumentCaptor<ActionData> data = ArgumentCaptor.forClass(ActionData.class);
    ArgumentCaptor<BulkItemAction> action = ArgumentCaptor.forClass(BulkItemAction.class);
    verify(jcrDeleteFileStorage).executeBulkAction(data.capture(), any(), action.capture());
    assertEquals("6", data.getValue().getActionId());
    assertEquals("PERMANENTLY_DELETE", data.getValue().getActionType());

    when(recycleBinStorage.getItemAt(PATH)).thenReturn(item(20L));
    assertTrue(action.getValue().apply("document-id"));
    verify(recycleBinService).deleteItem(ITEM_ID, RecycleBinScope.space(3), "john");
    doThrow(new ObjectNotFoundException("recycleBin.item.notFound")).when(recycleBinService).deleteItem(ITEM_ID, RecycleBinScope.space(3), "john");
    assertFalse(action.getValue().apply("document-id"));
    verify(recycleBinService, never()).restoreItem(anyString(), any(), anyString());
  }

  @Test
  void theFormerBulkActionsAreStartedForAUserOfThePlatformOnly() throws Exception {
    Identity guest = new Identity("organization", "__anonim");
    when(identityManager.getIdentity(9L)).thenReturn(guest);
    when(identityRegistry.getIdentity("__anonim")).thenReturn(new org.exoplatform.services.security.Identity("__anonim"));

    assertThrows(IllegalAccessException.class, () -> service.restoreDocuments(5, nodes("document-id", PATH), 99L));
    assertThrows(IllegalAccessException.class, () -> service.deleteDocumentsPermanently(5, nodes("document-id", PATH), 99L));
    assertThrows(IllegalAccessException.class, () -> service.restoreDocuments(5, nodes("document-id", PATH), 9L));
    assertThrows(IllegalAccessException.class, () -> service.deleteDocumentsPermanently(5, nodes("document-id", PATH), 9L));

    verifyNoInteractions(jcrDeleteFileStorage, recycleBinService, recycleBinStorage);
  }

  /** Trash elements as the former screen sends them: an id, which may be missing, and a path. */
  private static List<AbstractNode> nodes(String... idsAndPaths) {
    List<AbstractNode> nodes = new ArrayList<>();
    for (int i = 0; i < idsAndPaths.length; i += 2) {
      TrashElementNode node = new TrashElementNode();
      node.setId(idsAndPaths[i]);
      node.setPath(idsAndPaths[i + 1]);
      nodes.add(node);
    }
    return nodes;
  }

  private static RecycleBinItem item(Long ownerId) {
    RecycleBinItem item = new RecycleBinItem();
    item.setId(ITEM_ID);
    item.setOwnerId(ownerId);
    item.setPath(PATH);
    return item;
  }

}
