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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

import javax.jcr.InvalidItemStateException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.RecycleBinError;
import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.RecycleBinDrive;
import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.model.RecycleBinScope;
import org.exoplatform.documents.model.RecycleBinSortField;
import org.exoplatform.documents.storage.BulkItemAction;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.documents.storage.RecycleBinDriveStorage;
import org.exoplatform.documents.storage.RecycleBinStorage;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.listener.ListenerService;
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

  private TrashStorage                                      trashStorage;

  private JCRDeleteFileStorage                              jcrDeleteFileStorage;

  private org.exoplatform.services.security.Identity        johnAcl;

  private RecycleBinRetentionSetting                        retentionSetting;

  private ListenerService                                   listenerService;

  private org.exoplatform.services.security.Identity        rootAcl;

  /** The runs the service handed to its executor, which the test runs when it chooses. */
  private final List<Runnable>                              handedRuns   = new ArrayList<>();

  private RecycleBinServiceImpl                             service;

  @BeforeEach
  void setUp() {
    storage = mock(RecycleBinStorage.class);
    driveStorage = mock(RecycleBinDriveStorage.class);
    identityManager = mock(IdentityManager.class);
    spaceService = mock(SpaceService.class);
    userAcl = mock(UserACL.class);
    trashStorage = mock(TrashStorage.class);
    jcrDeleteFileStorage = mock(JCRDeleteFileStorage.class);
    retentionSetting = mock(RecycleBinRetentionSetting.class);
    listenerService = mock(ListenerService.class);
    lenient().when(retentionSetting.get()).thenReturn(30L);

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
    johnAcl = new org.exoplatform.services.security.Identity("john");
    lenient().when(userAcl.getUserIdentity("john")).thenReturn(johnAcl);
    lenient().when(userAcl.getUserIdentity("paul")).thenReturn(new org.exoplatform.services.security.Identity("paul"));
    rootAcl = new org.exoplatform.services.security.Identity("root", Set.of(new MembershipEntry("/platform/administrators", "member")));
    lenient().when(userAcl.getUserIdentity("root")).thenReturn(rootAcl);

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

  @Test
  void thePeriodIsReadByAnyoneAndSetByAnAdministratorOfThePlatformOnly() throws Exception {
    when(retentionSetting.get()).thenReturn(45L);

    assertEquals(45, service.getRetentionDays());
    assertThrows(IllegalAccessException.class, () -> service.setRetentionDays(10, "john"));
    // Managing a space, or every space, makes no administrator of the platform.
    assertThrows(IllegalAccessException.class, () -> service.setRetentionDays(10, "mary"));
    assertThrows(IllegalAccessException.class, () -> service.setRetentionDays(10, " "));
    assertThrows(IllegalAccessException.class, () -> service.setRetentionDays(10, "__anonim"));

    verify(retentionSetting, never()).set(anyLong());
    assertTrue(handedRuns.isEmpty());
    // The setting is the platform's, whatever the administrators may do on the drives.
    service(false).setRetentionDays(60, "root");
    verify(retentionSetting).set(60);
  }

  @Test
  void aPeriodTheBoundsRefuseIsToldAndStartsNoPurge() {
    IllegalArgumentException refusal = new IllegalArgumentException(RecycleBinError.RETENTION_INVALID);
    doThrow(refusal).when(retentionSetting).set(0);

    assertSame(refusal, assertThrows(IllegalArgumentException.class, () -> service.setRetentionDays(0, "root")));

    assertTrue(handedRuns.isEmpty());
  }

  @Test
  void onlyAShorterPeriodStartsAPurgeForTheAdministratorOnceItIsStored() throws Exception {
    service.setRetentionDays(30, "root");
    service.setRetentionDays(31, "root");
    assertTrue(handedRuns.isEmpty());

    doAnswer(invocation -> {
      // Stored before the purge is asked for: it reads the period when it runs.
      assertTrue(handedRuns.isEmpty());
      return null;
    }).when(retentionSetting).set(29);
    service.setRetentionDays(29, "root");

    verify(retentionSetting).set(29);
    assertEquals(1, handedRuns.size());
    when(storage.getIdsDeletedBefore(any())).thenReturn(List.of());
    runHanded();
    assertEquals(0, toldTo(rootAcl, 1).getNumberOfItems());
    verify(trashStorage, never()).normalizeRecycleBinItems();
  }

  @Test
  void theDailyRunGivesTheirFactsToTheItemsCountsTheBinsAgainThenRemovesWhatIsDue() throws Exception {
    List<RecycleBinDrive> totals = List.of(new RecycleBinDrive(JOHN_ID, 15, 2));
    when(storage.getDriveTotals()).thenReturn(totals);
    when(storage.getIdsDeletedBefore(any())).thenReturn(List.of("old", "recent"));
    when(storage.getRemovableItem("old")).thenReturn(deleted("old", JOHN_ID, 5, 31));
    when(storage.getRemovableItem("recent")).thenReturn(deleted("recent", JOHN_ID, 9, 29));
    when(storage.removeItem("old")).thenReturn(List.of());

    service.purgeExpired();

    // The call returns at once: the run is the executor's.
    verifyNoInteractions(trashStorage, storage, driveStorage);
    Instant before = Instant.now();
    runHanded();
    Instant after = Instant.now();

    InOrder order = inOrder(trashStorage, storage, driveStorage);
    order.verify(trashStorage).normalizeRecycleBinItems();
    order.verify(storage).getDriveTotals();
    order.verify(driveStorage).replaceAll(totals);
    ArgumentCaptor<Instant> expiry = ArgumentCaptor.forClass(Instant.class);
    order.verify(storage).getIdsDeletedBefore(expiry.capture());
    order.verify(storage).removeItem("old");
    order.verify(driveStorage).remove(JOHN_ID, 5);
    // Thirty days back from now, in days of the calendar of no one.
    assertFalse(expiry.getValue().isBefore(before.minus(Duration.ofDays(30))));
    assertFalse(expiry.getValue().isAfter(after.minus(Duration.ofDays(30))));
    verify(storage, never()).removeItem("recent");
    verify(driveStorage, never()).remove(JOHN_ID, 9);
    // No one asked for it: no one is told.
    verifyNoInteractions(listenerService);
  }

  @Test
  void anItemIsReadAgainAtItsTurnWithThePeriodAsItIsThen() throws Exception {
    when(storage.getIdsDeletedBefore(any())).thenReturn(List.of("kept", "gone", "undated", "due"));
    // Ten days when the items are selected, sixty from the turn of the first on, ten again for the last.
    when(retentionSetting.get()).thenReturn(10L, 60L, 10L);
    when(storage.getRemovableItem("kept")).thenReturn(deleted("kept", JOHN_ID, 5, 20));
    // Restored meanwhile, or a shortcut its document holds.
    when(storage.getRemovableItem("gone")).thenReturn(null);
    RecycleBinItem undated = deleted("undated", JOHN_ID, 5, 20);
    undated.setDeletedDate(null);
    when(storage.getRemovableItem("undated")).thenReturn(undated);
    when(storage.getRemovableItem("due")).thenReturn(deleted("due", JOHN_ID, 5, 20));
    when(storage.removeItem("due")).thenReturn(List.of());

    service.purgeExpired("root");
    runHanded();

    verify(storage, never()).removeItem("kept");
    verify(storage, never()).removeItem("gone");
    verify(storage, never()).removeItem("undated");
    verify(storage).removeItem("due");
    verify(driveStorage, times(1)).remove(anyLong(), anyLong());
    assertEquals(1, toldTo(rootAcl, 1).getNumberOfItems());
  }

  @Test
  void aPurgeAskedForLeavesTheCountsAsTheyAreAndTellsItsRequesterWhatLeftTheBins() throws Exception {
    when(storage.getIdsDeletedBefore(any())).thenReturn(List.of("a", "orphan", "removed", "changed", "broken", "lost", "last"));
    for (String id : List.of("a", "removed", "changed", "broken", "last")) {
      when(storage.getRemovableItem(id)).thenReturn(deleted(id, JOHN_ID, 5, 40));
    }
    // A shortcut whose document left the trash, and an item of no drive: in no bin.
    when(storage.getRemovableItem("orphan")).thenReturn(deleted("orphan", null, 0, 40));
    when(storage.getRemovableItem("lost")).thenThrow(new RepositoryException("The node cannot be read"));
    // An item of the bin of the space leaves with the first one.
    when(storage.removeItem("a")).thenReturn(List.of(deleted("link", SALES_ID, 7, 2)));
    when(storage.removeItem("orphan")).thenReturn(List.of());
    when(storage.removeItem("removed")).thenThrow(new PathNotFoundException("Removed meanwhile"));
    when(storage.removeItem("changed")).thenThrow(new InvalidItemStateException("Changed meanwhile"));
    when(storage.removeItem("broken")).thenThrow(new IllegalStateException("The workspace is not available"));
    when(storage.removeItem("last")).thenReturn(List.of());

    service.purgeExpired("root");
    runHanded();

    // Neither the facts nor the counts: they are the daily run's.
    verifyNoInteractions(trashStorage);
    verify(storage, never()).getDriveTotals();
    verify(driveStorage, never()).replaceAll(any());
    // What failed is left, and the run goes on to the last.
    verify(storage).removeItem("orphan");
    verify(storage).removeItem("last");
    verify(driveStorage, times(2)).remove(JOHN_ID, 5);
    verify(driveStorage).remove(SALES_ID, 7);
    verify(driveStorage, times(3)).remove(anyLong(), anyLong());

    ActionData told = toldTo(rootAcl, 1);
    assertEquals("recycle-bin-purge", told.getActionId());
    assertEquals("PERMANENTLY_DELETE", told.getActionType());
    assertEquals("DONE_SUCCESSFULLY", told.getStatus());
    // The first and what left with it, and the last.
    assertEquals(3, told.getNumberOfItems());
    assertEquals("root", told.getUserName());
    assertSame(rootAcl, told.getIdentity());
  }

  @Test
  void aRunGoingOnServesWhatIsAskedMeanwhileInAFurtherRound() throws Exception {
    when(storage.getIdsDeletedBefore(any())).thenAnswer(invocation -> {
      // Asked while the first round is going on.
      service.purgeExpired("mary");
      service.purgeExpired();
      return List.of();
    }).thenReturn(List.of());

    service.purgeExpired("root");
    service.purgeExpired("root");
    assertEquals(1, handedRuns.size());
    runHanded();

    // One run was handed over, and it went round twice.
    verify(storage, times(2)).getIdsDeletedBefore(any());
    verify(trashStorage, times(1)).normalizeRecycleBinItems();
    toldTo(rootAcl, 1);
    toldTo(maryAcl, 1);

    // Over: the next call starts a run of its own.
    service.purgeExpired();
    assertEquals(1, handedRuns.size());
    runHanded();
    verify(trashStorage, times(2)).normalizeRecycleBinItems();
    verify(listenerService, times(2)).broadcast(anyString(), any(), any());
  }

  @Test
  void theDailyRunAndThePurgesAskedForBeforeARoundStartsAreServedByThatRound() throws Exception {
    when(storage.getIdsDeletedBefore(any())).thenReturn(List.of());

    service.purgeExpired("root");
    service.purgeExpired("mary");
    service.purgeExpired();
    service.purgeExpired("mary");
    assertEquals(1, handedRuns.size());
    runHanded();

    verify(storage, times(1)).getIdsDeletedBefore(any());
    verify(trashStorage, times(1)).normalizeRecycleBinItems();
    toldTo(rootAcl, 1);
    toldTo(maryAcl, 1);
  }

  @Test
  void aRoundThatFailsTellsItsRequestersAndTheNextCallRunsAgain() throws Exception {
    when(storage.getIdsDeletedBefore(any())).thenThrow(new RepositoryException("The index is not available")).thenReturn(List.of());

    service.purgeExpired("root");
    runHanded();

    ActionData told = toldTo(rootAcl, 1);
    assertEquals("FAILED", told.getStatus());
    assertEquals(0, told.getNumberOfItems());

    service.purgeExpired("mary");
    assertEquals(1, handedRuns.size());
    runHanded();
    assertEquals("DONE_SUCCESSFULLY", toldTo(maryAcl, 1).getStatus());
  }

  @Test
  void aDailyRunThatCannotCountTheBinsRemovesNothingInThatRound() throws Exception {
    doThrow(new IllegalStateException("The database is not available")).when(driveStorage).replaceAll(any());
    when(trashStorage.normalizeRecycleBinItems()).thenReturn(0).thenThrow(new RepositoryException("The trash cannot be read"));

    service.purgeExpired();
    service.purgeExpired("root");
    runHanded();
    service.purgeExpired();
    runHanded();

    verify(storage, times(1)).getDriveTotals();
    verify(storage, never()).getIdsDeletedBefore(any());
    assertEquals("FAILED", toldTo(rootAcl, 1).getStatus());
  }

  @Test
  void aRunThatBreaksOffOrIsNotTakenLetsTheNextOneStart() throws Exception {
    when(storage.getIdsDeletedBefore(any())).thenThrow(new StackOverflowError("The run breaks off")).thenReturn(List.of());

    service.purgeExpired("root");
    Runnable run = handedRuns.remove(0);
    assertThrows(StackOverflowError.class, run::run);
    service.purgeExpired("root");
    assertEquals(1, handedRuns.size());
    runHanded();
    toldTo(rootAcl, 1);

    List<Runnable> taken = new ArrayList<>();
    boolean[] refusing = { true };
    RecycleBinServiceImpl stopping = new RecycleBinServiceImpl(storage,
                                                               driveStorage,
                                                               trashStorage,
                                                               jcrDeleteFileStorage,
                                                               retentionSetting,
                                                               identityManager,
                                                               spaceService,
                                                               userAcl,
                                                               listenerService,
                                                               true,
                                                               run1 -> {
                                                                 if (refusing[0]) {
                                                                   throw new RejectedExecutionException("The executor is stopping");
                                                                 }
                                                                 taken.add(run1);
                                                               });
    assertThrows(RejectedExecutionException.class, stopping::purgeExpired);
    refusing[0] = false;
    stopping.purgeExpired();
    assertEquals(1, taken.size());
  }

  @Test
  void aRequesterWhoCannotBeToldDoesNotKeepTheOthersFromIt() throws Exception {
    when(storage.getIdsDeletedBefore(any())).thenReturn(List.of());
    doThrow(new IllegalStateException("The channel is closed")).when(listenerService)
                                                               .broadcast(anyString(), org.mockito.ArgumentMatchers.same(rootAcl), any());

    service.purgeExpired("root");
    service.purgeExpired("mary");
    runHanded();

    toldTo(maryAcl, 1);
    // And the run is over as any other.
    service.purgeExpired();
    assertEquals(1, handedRuns.size());
  }

  @Test
  void aPurgeIsAskedForSomeone() {
    assertThrows(IllegalArgumentException.class, () -> service.purgeExpired(null));
    assertThrows(IllegalArgumentException.class, () -> service.purgeExpired(" "));

    assertTrue(handedRuns.isEmpty());
  }

  /** The bulk action the service started: what it is, its items, and what it does to each. */
  private record Started(ActionData data, List<String> itemIds, BulkItemAction action) {
  }

  @SuppressWarnings("unchecked")
  private Started started() {
    ArgumentCaptor<ActionData> data = ArgumentCaptor.forClass(ActionData.class);
    ArgumentCaptor<List<String>> itemIds = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<BulkItemAction> action = ArgumentCaptor.forClass(BulkItemAction.class);
    verify(jcrDeleteFileStorage).executeBulkAction(data.capture(), itemIds.capture(), action.capture());
    return new Started(data.getValue(), itemIds.getValue(), action.getValue());
  }

  @Test
  void severalItemsAreRestoredAsABulkActionOfTheCallerEachAtItsTurn() throws Exception {
    service.restoreItems("12", List.of(ITEM_ID, "gone", "foreign"), RecycleBinScope.user(), "john");

    Started started = started();
    assertEquals("12", started.data().getActionId());
    assertEquals("RESTORE", started.data().getActionType());
    assertSame(johnAcl, started.data().getIdentity());
    assertEquals("john", started.data().getUserName());
    assertEquals(3, started.data().getNumberOfItems());
    assertEquals(List.of(ITEM_ID, "gone", "foreign"), started.itemIds());
    // Nothing is read nor restored before the turn of an item.
    verifyNoInteractions(storage, driveStorage);

    when(storage.getItem(ITEM_ID)).thenReturn(item(JOHN_ID, 5));
    assertTrue(started.action().apply(ITEM_ID));
    verify(storage).restoreItem(ITEM_ID, johnAcl, false);
    verify(driveStorage).remove(JOHN_ID, 5);
    // No longer in the bin: left out, and no failure.
    assertFalse(started.action().apply("gone"));
    // An item of another bin, and a restore the bin refuses: failures of the item.
    when(storage.getItem("foreign")).thenReturn(item(SALES_ID, 1));
    assertThrows(IllegalAccessException.class, () -> started.action().apply("foreign"));
    doThrow(new IllegalAccessException(RecycleBinError.RESTORE_TARGET_FORBIDDEN)).when(storage).restoreItem(ITEM_ID, johnAcl, false);
    assertEquals(RecycleBinError.RESTORE_TARGET_FORBIDDEN,
                 assertThrows(IllegalAccessException.class, () -> started.action().apply(ITEM_ID)).getMessage());
    verify(driveStorage, times(1)).remove(anyLong(), anyLong());
  }

  @Test
  void severalItemsAreRemovedForGoodAsABulkActionAndWhatAlreadyLeftIsLeftOut() throws Exception {
    service.deleteItems("13", List.of(ITEM_ID, "gone"), RecycleBinScope.user(), "john");

    Started started = started();
    assertEquals("13", started.data().getActionId());
    assertEquals("PERMANENTLY_DELETE", started.data().getActionType());
    assertSame(johnAcl, started.data().getIdentity());
    assertEquals(2, started.data().getNumberOfItems());
    verifyNoInteractions(storage, driveStorage);

    when(storage.getItem(ITEM_ID)).thenReturn(item(JOHN_ID, 5));
    when(storage.removeItem(ITEM_ID)).thenReturn(List.of()).thenThrow(new PathNotFoundException("Removed with another item"));
    assertTrue(started.action().apply(ITEM_ID));
    verify(driveStorage).remove(JOHN_ID, 5);
    // Removed meanwhile, as a shortcut is with the document it points at.
    assertFalse(started.action().apply(ITEM_ID));
    assertFalse(started.action().apply("gone"));
    verify(driveStorage, times(1)).remove(anyLong(), anyLong());
    verify(storage, never()).restoreItem(any(), any(), anyBoolean());
  }

  @Test
  void aBulkActionIsStartedOnlyByWhoHoldsTheBinForItemsNamedAndUnderAnId() throws Exception {
    List<String> ids = List.of(ITEM_ID);
    RecycleBinScope salesBin = RecycleBinScope.space(SALES_SPACE);
    assertThrows(IllegalAccessException.class, () -> service.restoreItems("1", ids, salesBin, "john"));
    assertThrows(IllegalAccessException.class, () -> service.deleteItems("1", ids, salesBin, "john"));
    assertThrows(IllegalAccessException.class, () -> service.emptyBin("1", salesBin, "john"));
    assertThrows(IllegalAccessException.class, () -> service.emptyBin("1", RecycleBinScope.drive(JOHN_ID), "mary"));
    assertThrows(ObjectNotFoundException.class, () -> service.deleteItems("1", ids, RecycleBinScope.space(99), "john"));
    verifyNoInteractions(storage);

    for (List<String> none : new ArrayList<>(java.util.Arrays.asList(null, List.<String> of(), List.of(ITEM_ID, " ")))) {
      assertThrows(IllegalArgumentException.class, () -> service.restoreItems("1", none, RecycleBinScope.user(), "john"));
      assertThrows(IllegalArgumentException.class, () -> service.deleteItems("1", none, RecycleBinScope.user(), "john"));
    }
    for (String noId : new String[] { null, " " }) {
      assertThrows(IllegalArgumentException.class, () -> service.restoreItems(noId, ids, RecycleBinScope.user(), "john"));
      assertThrows(IllegalArgumentException.class, () -> service.deleteItems(noId, ids, RecycleBinScope.user(), "john"));
      assertThrows(IllegalArgumentException.class, () -> service.emptyBin(noId, RecycleBinScope.user(), "john"));
    }

    verifyNoInteractions(jcrDeleteFileStorage);
  }

  @Test
  void whoHoldsTheBinIsAskedAgainAtTheTurnOfEachItem() throws Exception {
    when(storage.getItem(ITEM_ID)).thenReturn(item(SALES_ID, 1));
    service.deleteItems("1", List.of(ITEM_ID), RecycleBinScope.space(SALES_SPACE), "mary");
    Started started = started();

    // No longer a manager of the space when the item is reached.
    when(spaceService.isManager(sales, "mary")).thenReturn(false);

    assertThrows(IllegalAccessException.class, () -> started.action().apply(ITEM_ID));
    verify(storage, never()).removeItem(any());
  }

  @Test
  void emptyingABinRemovesForGoodTheItemsItHoldsWhenAsked() throws Exception {
    RecycleBinItem first = item(SALES_ID, 5);
    first.setId("a");
    RecycleBinItem second = item(SALES_ID, 7);
    second.setId("b");
    when(storage.getItems(any())).thenReturn(List.of(first, second));

    service.emptyBin("14", RecycleBinScope.space(SALES_SPACE), "mary");

    ArgumentCaptor<RecycleBinFilter> filter = ArgumentCaptor.forClass(RecycleBinFilter.class);
    verify(storage).getItems(filter.capture());
    // Every item of the bin of the space: no term, no page.
    assertEquals(SALES_ID, filter.getValue().getOwnerId());
    assertNull(filter.getValue().getQuery());
    assertEquals(0, filter.getValue().getOffset());
    assertEquals(0, filter.getValue().getLimit());
    Started started = started();
    assertEquals("14", started.data().getActionId());
    assertEquals("PERMANENTLY_DELETE", started.data().getActionType());
    assertSame(maryAcl, started.data().getIdentity());
    assertEquals("mary", started.data().getUserName());
    assertEquals(2, started.data().getNumberOfItems());
    assertEquals(List.of("a", "b"), started.itemIds());
    verify(storage, never()).removeItem(any());

    when(storage.getItem("a")).thenReturn(first);
    when(storage.removeItem("a")).thenReturn(List.of(second));
    assertTrue(started.action().apply("a"));
    verify(driveStorage).remove(SALES_ID, 5);
    verify(driveStorage).remove(SALES_ID, 7);
    // The second left with the first.
    assertFalse(started.action().apply("b"));
  }

  @Test
  void aBinThatCannotBeReadIsNotEmptiedAndAnEmptyOneStillReportsItsEnd() throws Exception {
    when(storage.getItems(any())).thenThrow(new RepositoryException("The index is not available")).thenReturn(List.of());

    assertThrows(IllegalStateException.class, () -> service.emptyBin("15", RecycleBinScope.user(), "john"));
    verifyNoInteractions(jcrDeleteFileStorage);

    service.emptyBin("15", RecycleBinScope.user(), "john");
    Started started = started();
    assertEquals(0, started.data().getNumberOfItems());
    assertTrue(started.itemIds().isEmpty());
  }

  @Test
  void anAdministratorActsInBulkOnTheBinOfADriveOnlyWhileThePlatformLetsThem() throws Exception {
    List<String> ids = List.of(ITEM_ID);
    RecycleBinScope johnDrive = RecycleBinScope.drive(JOHN_ID);
    assertThrows(IllegalAccessException.class, () -> service(false).restoreItems("1", ids, johnDrive, "root"));
    verifyNoInteractions(jcrDeleteFileStorage);

    service.restoreItems("16", ids, johnDrive, "root");
    Started started = started();
    assertSame(rootAcl, started.data().getIdentity());

    when(storage.getItem(ITEM_ID)).thenReturn(item(JOHN_ID, 5));
    assertTrue(started.action().apply(ITEM_ID));
    // Restored as an administrator does: to the folder or to the root of the drive of its owner.
    verify(storage).restoreItem(ITEM_ID, rootAcl, true);
  }

  private RecycleBinServiceImpl service(boolean administratorAccessEnabled) {
    return new RecycleBinServiceImpl(storage,
                                     driveStorage,
                                     trashStorage,
                                     jcrDeleteFileStorage,
                                     retentionSetting,
                                     identityManager,
                                     spaceService,
                                     userAcl,
                                     listenerService,
                                     administratorAccessEnabled,
                                     handedRuns::add);
  }

  /** Runs what the service handed to its executor, in the order it did. */
  private void runHanded() {
    while (!handedRuns.isEmpty()) {
      handedRuns.remove(0).run();
    }
  }

  private static RecycleBinItem deleted(String id, Long ownerId, long size, int daysAgo) {
    RecycleBinItem item = new RecycleBinItem();
    item.setId(id);
    item.setOwnerId(ownerId);
    item.setSize(size);
    item.setDeletedDate(Instant.now().minus(Duration.ofDays(daysAgo)).toEpochMilli());
    return item;
  }

  private ActionData toldTo(org.exoplatform.services.security.Identity user, int times) throws Exception {
    ArgumentCaptor<ActionData> told = ArgumentCaptor.forClass(ActionData.class);
    verify(listenerService, times(times)).broadcast(org.mockito.ArgumentMatchers.eq("bulk_actions_document_event"),
                                                    org.mockito.ArgumentMatchers.same(user),
                                                    told.capture());
    return told.getValue();
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
