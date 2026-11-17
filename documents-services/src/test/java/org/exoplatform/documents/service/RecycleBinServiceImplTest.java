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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.RecycleBinError;
import org.exoplatform.documents.model.RecycleBinDrive;
import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.model.RecycleBinScope;
import org.exoplatform.documents.model.RecycleBinSortField;
import org.exoplatform.documents.storage.RecycleBinDriveStorage;
import org.exoplatform.documents.storage.RecycleBinStorage;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Who may see the Recycle Bin of a drive and act on its items, and what each
 * action counts in or out of the drive's totals. The storages are mocked: what
 * a restore or a removal does to the repository is theirs to test.
 */
class RecycleBinServiceImplTest {

  private static final String                               ITEM_ID      = "11111111-1111-1111-1111-111111111111";

  private static final long                                 JOHN_ID      = 7;

  private static final long                                 SALES_ID     = 20;

  private static final long                                 SALES_SPACE  = 3;

  private RecycleBinStorage                                 storage;

  private RecycleBinDriveStorage                            driveStorage;

  private IdentityManager                                   identityManager;

  private SpaceService                                      spaceService;

  private UserACL                                           userAcl;

  private Space                                             sales;

  private org.exoplatform.services.security.Identity        maryAcl;

  private RecycleBinServiceImpl                             service;

  @BeforeEach
  void setUp() {
    storage = mock(RecycleBinStorage.class);
    driveStorage = mock(RecycleBinDriveStorage.class);
    identityManager = mock(IdentityManager.class);
    spaceService = mock(SpaceService.class);
    userAcl = mock(UserACL.class);

    Identity john = identity("organization", "john", JOHN_ID);
    Identity mary = identity("organization", "mary", 8);
    Identity root = identity("organization", "root", 1);
    Identity salesIdentity = identity("space", "sales", SALES_ID);
    lenient().when(identityManager.getOrCreateUserIdentity("john")).thenReturn(john);
    lenient().when(identityManager.getOrCreateUserIdentity("mary")).thenReturn(mary);
    lenient().when(identityManager.getOrCreateUserIdentity("root")).thenReturn(root);
    lenient().when(identityManager.getOrCreateSpaceIdentity("sales")).thenReturn(salesIdentity);
    lenient().when(identityManager.getIdentity(String.valueOf(JOHN_ID))).thenReturn(john);
    lenient().when(identityManager.getIdentity("8")).thenReturn(mary);
    lenient().when(identityManager.getIdentity(String.valueOf(SALES_ID))).thenReturn(salesIdentity);

    sales = new Space();
    sales.setId(String.valueOf(SALES_SPACE));
    sales.setPrettyName("sales");
    lenient().when(spaceService.getSpaceById(SALES_SPACE)).thenReturn(sales);
    lenient().when(spaceService.getSpaceByPrettyName("sales")).thenReturn(sales);
    lenient().when(spaceService.isManager(sales, "mary")).thenReturn(true);
    lenient().when(spaceService.isPublisher(sales, "paul")).thenReturn(true);
    // Root manages every space of the platform without being a manager of this one.
    lenient().when(spaceService.isSuperManager(anyString())).thenReturn(true);
    lenient().when(spaceService.canManageSpace(any(), anyString())).thenReturn(true);

    lenient().when(userAcl.getAdminGroups()).thenReturn("/platform/administrators");
    maryAcl = new org.exoplatform.services.security.Identity("mary");
    lenient().when(userAcl.getUserIdentity("mary")).thenReturn(maryAcl);
    lenient().when(userAcl.getUserIdentity("john")).thenReturn(new org.exoplatform.services.security.Identity("john"));
    lenient().when(userAcl.getUserIdentity("paul")).thenReturn(new org.exoplatform.services.security.Identity("paul"));
    lenient().when(userAcl.getUserIdentity("root"))
             .thenReturn(new org.exoplatform.services.security.Identity("root",
                                                                        Set.of(new MembershipEntry("/platform/administrators", "member"))));

    service = service(true);
  }

  @Test
  void aUserListsTheirOwnBinWhateverOwnerTheFilterNames() throws Exception {
    RecycleBinItem item = item(JOHN_ID, 5);
    item.setDocumentId("document-id");
    when(storage.getItems(any())).thenReturn(new ArrayList<>(List.of(item)));
    when(storage.countItems(any())).thenReturn(4);
    RecycleBinFilter asked = new RecycleBinFilter(SALES_ID, "plan", RecycleBinSortField.SIZE, true, 20, 10);

    List<RecycleBinItem> items = service.getItems(RecycleBinScope.user(), asked, "john");
    int count = service.countItems(RecycleBinScope.user(), asked, "john");

    assertEquals(List.of(item), items);
    assertEquals("document-id", items.get(0).getDocumentId());
    assertEquals(4, count);
    ArgumentCaptor<RecycleBinFilter> filter = ArgumentCaptor.forClass(RecycleBinFilter.class);
    verify(storage).getItems(filter.capture());
    assertEquals(new RecycleBinFilter(JOHN_ID, "plan", RecycleBinSortField.SIZE, true, 20, 10), filter.getValue());
    verify(storage).countItems(new RecycleBinFilter(JOHN_ID, "plan", RecycleBinSortField.SIZE, true, 20, 10));
    // The filter of the caller is left as it was given.
    assertEquals(SALES_ID, asked.getOwnerId());
  }

  @Test
  void theManagersAndThePublishersOfASpaceHoldItsBinAndNoOneElse() throws Exception {
    when(storage.getItems(any())).thenReturn(new ArrayList<>());

    service.getItems(RecycleBinScope.space(SALES_SPACE), null, "mary");
    service.countItems(RecycleBinScope.space(SALES_SPACE), new RecycleBinFilter(), "paul");

    verify(storage).getItems(new RecycleBinFilter(SALES_ID, null, RecycleBinSortField.DELETED_DATE, false, 0, 0));
    verify(storage).countItems(new RecycleBinFilter(SALES_ID, null, RecycleBinSortField.DELETED_DATE, false, 0, 0));
    // A member, and one who manages every space of the platform, do not.
    assertThrows(IllegalAccessException.class, () -> service.getItems(RecycleBinScope.space(SALES_SPACE), null, "john"));
    assertThrows(IllegalAccessException.class, () -> service.countItems(RecycleBinScope.space(SALES_SPACE), null, "root"));
    assertThrows(IllegalAccessException.class, () -> service.restoreItem(ITEM_ID, RecycleBinScope.space(SALES_SPACE), "john"));
    assertThrows(IllegalAccessException.class, () -> service.deleteItem(ITEM_ID, RecycleBinScope.space(SALES_SPACE), "root"));
    assertThrows(ObjectNotFoundException.class, () -> service.getItems(RecycleBinScope.space(99), null, "mary"));
    verify(storage, never()).getItem(any());
  }

  @Test
  void anAdministratorOfThePlatformOpensTheBinOfAnyDriveAndGetsNoDocumentId() throws Exception {
    RecycleBinItem item = item(JOHN_ID, 5);
    item.setDocumentId("document-id");
    when(storage.getItems(any())).thenReturn(new ArrayList<>(List.of(item)));

    List<RecycleBinItem> items = service.getItems(RecycleBinScope.drive(JOHN_ID), null, "root");

    assertEquals(1, items.size());
    assertNull(items.get(0).getDocumentId());
    verify(storage).getItems(new RecycleBinFilter(JOHN_ID, null, RecycleBinSortField.DELETED_DATE, false, 0, 0));
    // Who is no administrator learns nothing of a drive, existing or not.
    assertThrows(IllegalAccessException.class, () -> service.getItems(RecycleBinScope.drive(JOHN_ID), null, "mary"));
    assertThrows(IllegalAccessException.class, () -> service.getItems(RecycleBinScope.drive(99), null, "mary"));
    assertThrows(ObjectNotFoundException.class, () -> service.getItems(RecycleBinScope.drive(99), null, "root"));
  }

  @Test
  void thePlatformMayCloseTheBinsOfTheDrivesToItsAdministrators() throws Exception {
    service = service(false);

    assertFalse(service.isAdministratorAccessEnabled());
    assertThrows(IllegalAccessException.class, () -> service.getItems(RecycleBinScope.drive(JOHN_ID), null, "root"));
    assertThrows(IllegalAccessException.class, () -> service.restoreItem(ITEM_ID, RecycleBinScope.drive(JOHN_ID), "root"));
    assertThrows(IllegalAccessException.class, () -> service.deleteItem(ITEM_ID, RecycleBinScope.drive(JOHN_ID), "root"));
    verifyNoInteractions(storage);
    // The overview of the drives stays theirs.
    when(driveStorage.countDrives()).thenReturn(2L);
    assertEquals(2, service.countDrives("root"));
    assertTrue(service(true).isAdministratorAccessEnabled());
  }

  @Test
  void noOneActsWithoutANameAndNothingIsReadForThem() {
    for (String nobody : new String[] { null, "", " ", "__anonim" }) {
      assertThrows(IllegalAccessException.class, () -> service.getItems(RecycleBinScope.user(), null, nobody));
      assertThrows(IllegalAccessException.class, () -> service.countItems(RecycleBinScope.user(), null, nobody));
      assertThrows(IllegalAccessException.class, () -> service.restoreItem(ITEM_ID, RecycleBinScope.user(), nobody));
      assertThrows(IllegalAccessException.class, () -> service.deleteItem(ITEM_ID, RecycleBinScope.user(), nobody));
      assertThrows(IllegalAccessException.class, () -> service.checkCanView(ITEM_ID, nobody));
      assertThrows(IllegalAccessException.class, () -> service.checkCanViewDocument("document-id", nobody));
      assertThrows(IllegalAccessException.class, () -> service.undoDelete("document-id", nobody));
      assertThrows(IllegalAccessException.class, () -> service.getDrives(0, 10, nobody));
      assertThrows(IllegalAccessException.class, () -> service.countDrives(nobody));
    }
    verifyNoInteractions(storage, driveStorage, identityManager, spaceService, userAcl);
  }

  @Test
  void aPageThatCannotBeIsRefusedOnceTheCallerIsKnownToHoldTheBin() throws Exception {
    assertThrows(IllegalArgumentException.class,
                 () -> service.getItems(RecycleBinScope.user(), new RecycleBinFilter(0, null, null, false, -1, 10), "john"));
    assertThrows(IllegalArgumentException.class,
                 () -> service.countItems(RecycleBinScope.user(), new RecycleBinFilter(0, null, null, false, 0, -1), "john"));
    assertThrows(IllegalAccessException.class,
                 () -> service.getItems(RecycleBinScope.space(SALES_SPACE), new RecycleBinFilter(0, null, null, false, -1, 10), "john"));
    assertThrows(IllegalArgumentException.class, () -> service.getDrives(-1, 10, "root"));
    assertThrows(IllegalArgumentException.class, () -> service.getDrives(0, -1, "root"));
    assertThrows(IllegalArgumentException.class, () -> service.getItems(null, null, "john"));
    verifyNoInteractions(storage, driveStorage);
  }

  @Test
  void anItemOfTheBinIsRestoredAsTheCallerAndCountedOutOfItsDrive() throws Exception {
    RecycleBinItem item = item(SALES_ID, 5);
    when(storage.getItem(ITEM_ID)).thenReturn(item);

    service.restoreItem(ITEM_ID, RecycleBinScope.space(SALES_SPACE), "mary");

    InOrder order = inOrder(storage, driveStorage);
    order.verify(storage).restoreItem(ITEM_ID, maryAcl, false);
    order.verify(driveStorage).remove(SALES_ID, 5);
  }

  @Test
  void anAdministratorRestoresAnItemOfADriveWithoutBeingAskedForTheTarget() throws Exception {
    when(storage.getItem(ITEM_ID)).thenReturn(item(JOHN_ID, 5));

    service.restoreItem(ITEM_ID, RecycleBinScope.drive(JOHN_ID), "root");

    verify(storage).restoreItem(any(), any(), org.mockito.ArgumentMatchers.eq(true));
    verify(driveStorage).remove(JOHN_ID, 5);
  }

  @Test
  void anItemOfAnotherBinOrOfNoBinIsNotActedOnThroughThisOne() throws Exception {
    when(storage.getItem(ITEM_ID)).thenReturn(item(SALES_ID, 5));
    assertThrows(IllegalAccessException.class, () -> service.restoreItem(ITEM_ID, RecycleBinScope.user(), "mary"));
    assertThrows(IllegalAccessException.class, () -> service.deleteItem(ITEM_ID, RecycleBinScope.user(), "mary"));
    assertThrows(IllegalAccessException.class, () -> service.restoreItem(ITEM_ID, RecycleBinScope.drive(JOHN_ID), "root"));

    when(storage.getItem(ITEM_ID)).thenReturn(item(null, 5));
    assertThrows(IllegalAccessException.class, () -> service.restoreItem(ITEM_ID, RecycleBinScope.user(), "mary"));
    assertThrows(IllegalAccessException.class, () -> service.deleteItem(ITEM_ID, RecycleBinScope.space(SALES_SPACE), "mary"));

    when(storage.getItem(ITEM_ID)).thenReturn(null);
    ObjectNotFoundException notFound = assertThrows(ObjectNotFoundException.class,
                                                    () -> service.restoreItem(ITEM_ID, RecycleBinScope.user(), "mary"));
    assertEquals(RecycleBinError.ITEM_NOT_FOUND, notFound.getMessage());
    assertThrows(ObjectNotFoundException.class, () -> service.deleteItem(ITEM_ID, RecycleBinScope.user(), "mary"));

    verify(storage, never()).restoreItem(any(), any(), anyBoolean());
    verify(storage, never()).removeItem(any());
    verifyNoInteractions(driveStorage);
  }

  @Test
  void aRestoreThatIsRefusedOrFailsCountsNothingOut() throws Exception {
    when(storage.getItem(ITEM_ID)).thenReturn(item(JOHN_ID, 5));
    IllegalAccessException forbidden = new IllegalAccessException(RecycleBinError.RESTORE_TARGET_FORBIDDEN);
    IllegalArgumentException missing = new IllegalArgumentException(RecycleBinError.RESTORE_LOCATION_MISSING);
    ObjectNotFoundException gone = new ObjectNotFoundException(RecycleBinError.ITEM_NOT_FOUND);
    RepositoryException failure = new RepositoryException("The repository cannot answer");
    when(storage.restoreItem(any(), any(), anyBoolean())).thenThrow(forbidden, missing, gone, failure);

    assertSame(forbidden, assertThrows(IllegalAccessException.class, () -> service.restoreItem(ITEM_ID, RecycleBinScope.user(), "john")));
    assertSame(missing, assertThrows(IllegalArgumentException.class, () -> service.restoreItem(ITEM_ID, RecycleBinScope.user(), "john")));
    assertSame(gone, assertThrows(ObjectNotFoundException.class, () -> service.restoreItem(ITEM_ID, RecycleBinScope.user(), "john")));
    assertSame(failure,
               assertThrows(IllegalStateException.class, () -> service.restoreItem(ITEM_ID, RecycleBinScope.user(), "john")).getCause());

    verifyNoInteractions(driveStorage);
  }

  @Test
  void anItemRemovedForGoodIsCountedOutWithTheItemsOfABinThatLeftWithIt() throws Exception {
    when(storage.getItem(ITEM_ID)).thenReturn(item(JOHN_ID, 5));
    RecycleBinItem theirs = item(SALES_ID, 0);
    RecycleBinItem ofNoDrive = item(null, 9);
    when(storage.removeItem(ITEM_ID)).thenReturn(List.of(theirs, ofNoDrive));

    service.deleteItem(ITEM_ID, RecycleBinScope.user(), "john");

    verify(driveStorage).remove(JOHN_ID, 5);
    verify(driveStorage).remove(SALES_ID, 0);
    verify(driveStorage, never()).remove(org.mockito.ArgumentMatchers.eq(0L), anyLong());
  }

  @Test
  void anItemAnotherActorTookMeanwhileIsNotFoundAndARepositoryThatFailsSaysSo() throws Exception {
    when(storage.getItem(ITEM_ID)).thenReturn(item(JOHN_ID, 5));
    RepositoryException failure = new RepositoryException("The repository cannot answer");
    when(storage.removeItem(ITEM_ID)).thenThrow(new PathNotFoundException("No node of the trash carries the id"), failure);

    ObjectNotFoundException notFound = assertThrows(ObjectNotFoundException.class,
                                                    () -> service.deleteItem(ITEM_ID, RecycleBinScope.user(), "john"));
    assertEquals(RecycleBinError.ITEM_NOT_FOUND, notFound.getMessage());
    assertSame(failure,
               assertThrows(IllegalStateException.class, () -> service.deleteItem(ITEM_ID, RecycleBinScope.user(), "john")).getCause());
    when(storage.getItem(ITEM_ID)).thenThrow(failure);
    assertSame(failure,
               assertThrows(IllegalStateException.class, () -> service.deleteItem(ITEM_ID, RecycleBinScope.user(), "john")).getCause());
    when(storage.getItems(any())).thenThrow(failure);
    when(storage.countItems(any())).thenThrow(failure);
    assertSame(failure, assertThrows(IllegalStateException.class, () -> service.getItems(RecycleBinScope.user(), null, "john")).getCause());
    assertSame(failure, assertThrows(IllegalStateException.class, () -> service.countItems(RecycleBinScope.user(), null, "john")).getCause());

    verifyNoInteractions(driveStorage);
  }

  @Test
  void anItemThatLeftItsBinHasLeftEvenWhenItCannotBeCountedOut() throws Exception {
    when(storage.getItem(ITEM_ID)).thenReturn(item(JOHN_ID, 5));
    when(storage.removeItem(ITEM_ID)).thenReturn(List.of(item(SALES_ID, 1)));
    doThrow(new IllegalStateException("The database refuses the statement")).when(driveStorage).remove(JOHN_ID, 5);

    service.restoreItem(ITEM_ID, RecycleBinScope.user(), "john");
    service.deleteItem(ITEM_ID, RecycleBinScope.user(), "john");

    verify(storage).restoreItem(any(), any(), anyBoolean());
    verify(storage).removeItem(ITEM_ID);
    // The one that left with it is still counted out.
    verify(driveStorage).remove(SALES_ID, 1);
  }

  @Test
  void theContentOfAnItemIsReadByWhoHoldsItsBinAndNeverAsAnAdministrator() throws Exception {
    RecycleBinItem mine = item(JOHN_ID, 5);
    RecycleBinItem ofSales = item(SALES_ID, 5);
    when(storage.getItem(ITEM_ID)).thenReturn(mine, mine, mine, ofSales, ofSales, ofSales, ofSales, item(null, 5), item(99L, 5), null);

    assertSame(mine, service.checkCanView(ITEM_ID, "john"));
    assertThrows(IllegalAccessException.class, () -> service.checkCanView(ITEM_ID, "mary"));
    assertThrows(IllegalAccessException.class, () -> service.checkCanView(ITEM_ID, "root"));
    assertSame(ofSales, service.checkCanView(ITEM_ID, "mary"));
    assertSame(ofSales, service.checkCanView(ITEM_ID, "paul"));
    assertThrows(IllegalAccessException.class, () -> service.checkCanView(ITEM_ID, "john"));
    assertThrows(IllegalAccessException.class, () -> service.checkCanView(ITEM_ID, "root"));
    // An item of no drive, and one whose owner is gone, are in no one's bin.
    assertThrows(IllegalAccessException.class, () -> service.checkCanView(ITEM_ID, "john"));
    assertThrows(IllegalAccessException.class, () -> service.checkCanView(ITEM_ID, "john"));
    assertThrows(ObjectNotFoundException.class, () -> service.checkCanView(ITEM_ID, "john"));

    when(storage.getItemOfDocument("document-id")).thenReturn(mine, mine, null);
    assertSame(mine, service.checkCanViewDocument("document-id", "john"));
    assertThrows(IllegalAccessException.class, () -> service.checkCanViewDocument("document-id", "root"));
    assertThrows(ObjectNotFoundException.class, () -> service.checkCanViewDocument("document-id", "john"));
  }

  @Test
  void aDeletedDocumentIsRestoredForWhoDeletedItOrHoldsItsBinWhereTheyCanAddContent() throws Exception {
    RecycleBinItem ofSales = item(SALES_ID, 5);
    ofSales.setDeletedBy("john");
    RecycleBinItem ofNoDrive = item(null, 4);
    ofNoDrive.setDeletedBy("john");
    when(storage.getItemOfDocument("document-id")).thenReturn(ofSales, ofSales, ofSales, ofNoDrive, ofNoDrive, null);

    // Who deleted it, a holder of its bin, then an administrator who is neither.
    service.undoDelete("document-id", "john");
    service.undoDelete("document-id", "mary");
    assertThrows(IllegalAccessException.class, () -> service.undoDelete("document-id", "root"));
    // A document of no drive comes back for who deleted it only, and counts in no drive.
    service.undoDelete("document-id", "john");
    assertThrows(IllegalAccessException.class, () -> service.undoDelete("document-id", "mary"));
    assertThrows(ObjectNotFoundException.class, () -> service.undoDelete("document-id", "john"));

    verify(storage, org.mockito.Mockito.times(3)).restoreItem(org.mockito.ArgumentMatchers.eq(ITEM_ID), any(), org.mockito.ArgumentMatchers.eq(false));
    verify(storage).restoreItem(ITEM_ID, maryAcl, false);
    verify(driveStorage, org.mockito.Mockito.times(2)).remove(SALES_ID, 5);
    verify(driveStorage, org.mockito.Mockito.times(2)).remove(anyLong(), anyLong());
  }

  @Test
  void theDrivesAreListedForTheAdministratorsOfThePlatform() throws Exception {
    List<RecycleBinDrive> drives = List.of(new RecycleBinDrive(JOHN_ID, 10, 2));
    when(driveStorage.getDrives(20, 10)).thenReturn(drives);
    when(driveStorage.countDrives()).thenReturn(21L);

    assertSame(drives, service.getDrives(20, 10, "root"));
    assertEquals(21, service.countDrives("root"));
    assertThrows(IllegalAccessException.class, () -> service.getDrives(0, 10, "mary"));
    assertThrows(IllegalAccessException.class, () -> service.countDrives("mary"));
    assertThrows(IllegalAccessException.class, () -> service.countDrives("nobody"));
  }

  @Test
  void aDeletedItemIsCountedOnTheRowOfItsDrive() {
    when(driveStorage.add(JOHN_ID, 5)).thenReturn(true);

    service.itemTrashed(JOHN_ID, 5);

    verify(driveStorage).add(JOHN_ID, 5);
    verify(driveStorage, never()).create(anyLong(), anyLong());
  }

  @Test
  void theFirstItemOfADriveCreatesItsRowAndIsCountedOnTheRowAnotherOneCreatedMeanwhile() {
    when(driveStorage.add(JOHN_ID, 5)).thenReturn(false);
    service.itemTrashed(JOHN_ID, 5);
    InOrder order = inOrder(driveStorage);
    order.verify(driveStorage).add(JOHN_ID, 5);
    order.verify(driveStorage).create(JOHN_ID, 5);
    order.verifyNoMoreInteractions();

    when(driveStorage.add(SALES_ID, 3)).thenReturn(false, true);
    doThrow(new IllegalStateException("The row exists")).when(driveStorage).create(SALES_ID, 3);
    service.itemTrashed(SALES_ID, 3);
    verify(driveStorage, org.mockito.Mockito.times(2)).add(SALES_ID, 3);

    // A creation that fails for another reason is told to who reports the deletion.
    IllegalStateException failure = new IllegalStateException("The database refuses the statement");
    when(driveStorage.add(8, 1)).thenReturn(false, false);
    doThrow(failure).when(driveStorage).create(8, 1);
    assertSame(failure, assertThrows(IllegalStateException.class, () -> service.itemTrashed(8, 1)));
  }

  private RecycleBinServiceImpl service(boolean administratorAccessEnabled) {
    return new RecycleBinServiceImpl(storage, driveStorage, identityManager, spaceService, userAcl, administratorAccessEnabled);
  }

  private static RecycleBinItem item(Long ownerId, long size) {
    RecycleBinItem item = new RecycleBinItem();
    item.setId(ITEM_ID);
    item.setOwnerId(ownerId);
    item.setSize(size);
    return item;
  }

  private static Identity identity(String provider, String remoteId, long id) {
    Identity identity = new Identity(provider, remoteId);
    identity.setId(String.valueOf(id));
    return identity;
  }

}
