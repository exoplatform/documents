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
package org.exoplatform.documents.storage.jcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.InvalidItemStateException;
import javax.jcr.PathNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.documents.model.RecycleBinDrive;
import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.constant.RecycleBinSortField;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.FakeJcr.Item;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.NamespaceRegistryImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.query.DefaultQueryNodeFactory;

import io.meeds.portal.thumbnail.model.FileContent;
import org.exoplatform.services.jcr.impl.core.query.sql.JCRSQLQueryBuilder;

/**
 * The reading of a Recycle Bin, on a workspace kept in memory: which
 * statement is run, with which page, and what an item carries. The
 * repository does not run the statements here: the test answers them itself,
 * reading in each one the drive, the term, the id and the sort it expects to
 * find there, and each statement is given to the repository's own parser.
 * What the repository selects and how it orders missing values are checked
 * on a running server.
 */
class JCRRecycleBinStorageTest {

  private static final String  WORKSPACE = "collaboration";

  private static final String  JOHN_BIN  = "/Trash/Users/j___/jo___/joh___/john";

  private static final String  ID_1      = "11111111-1111-1111-1111-111111111111";

  private static final String  ID_2      = "22222222-2222-2222-2222-222222222222";

  private static final String  ID_3      = "33333333-3333-3333-3333-333333333333";

  private static final String  ID_4      = "44444444-4444-4444-4444-444444444444";

  private FakeJcr              jcr;

  private SessionProvider      systemProvider;

  private JCRRecycleBinStorage storage;

  @BeforeEach
  void setUp() throws Exception {
    jcr = new FakeJcr(WORKSPACE);
    jcr.add("/Trash", "nt:unstructured");
    jcr.commit();
    jcr.setQueryHandler(this::answer);
    SessionImpl systemSession = jcr.session("__system");

    RepositoryService repositoryService = mock(RepositoryService.class);
    when(repositoryService.getCurrentRepository()).thenReturn(mock(ManageableRepository.class));
    systemProvider = mock(SessionProvider.class);
    lenient().when(systemProvider.getSession(anyString(), any())).thenReturn(systemSession);
    TrashStorage trashStorage = mock(TrashStorage.class);
    lenient().when(trashStorage.getTrashHomeNode()).thenAnswer(invocation -> jcr.node(systemSession, jcr.at("/Trash")));

    storage = new JCRRecycleBinStorage(repositoryService, trashStorage, null, null, null) {
      @Override
      SessionProvider createSystemProvider() {
        return systemProvider;
      }
    };
  }

  @Test
  void theBinOfADriveListsItsItemsWithWhatWasRecordedAtTheirDeletion() throws Exception {
    Calendar deleted = Calendar.getInstance();
    Item file = item(JOHN_BIN + "/report.docx", ID_1, "7", "report.docx", 10, deleted);
    file.set("exo:title", "Report.DOCX")
        .set("exo:deletedBy", "mary")
        .set("exo:restorePath", "/Users/j___/jo___/joh___/john/Private/Documents/report.docx")
        .set("exo:restoreWorkspace", WORKSPACE)
        .set("exo:restoreParentId", "folder-id");
    jcr.at(JOHN_BIN + "/report.docx/jcr:content").set("jcr:mimeType", "application/msword");
    jcr.commit();

    List<RecycleBinItem> items = storage.getItems(filter(7));

    assertEquals(1, items.size());
    RecycleBinItem item = items.get(0);
    assertEquals(ID_1, item.getId());
    assertEquals(file.getId(), item.getDocumentId());
    assertEquals(7L, item.getOwnerId());
    assertEquals("Report.DOCX", item.getName());
    assertFalse(item.isFolder());
    assertFalse(item.isShortcut());
    assertEquals("application/msword", item.getMimeType());
    assertEquals(10, item.getSize());
    assertEquals(deleted.getTimeInMillis(), item.getDeletedDate());
    assertEquals("mary", item.getDeletedBy());
    assertEquals("/Users/j___/jo___/joh___/john/Private/Documents/report.docx", item.getRestorePath());
    assertEquals(WORKSPACE, item.getRestoreWorkspace());
    assertEquals("folder-id", item.getRestoreParentId());
  }

  @Test
  void theFoldersAnItemWasDeletedFromAreNamedAsTheDocumentsShowThem() throws Exception {
    String drive = "/Users/j___/jo___/joh___/john/Private";
    jcr.add(drive + "/projets-2026", "nt:folder").set("exo:title", "Projets 2026");
    // A folder deleted since is named as the trash keeps it.
    Item archive = jcr.add(JOHN_BIN + "/archive-q1", "nt:folder");
    archive.mixin("exo:recycleBinItem", "exo:restoreLocation")
           .set("exo:trashId", ID_2)
           .set("exo:recycleBinOwner", "7")
           .set("exo:title", "Archive Q1")
           .set("exo:restorePath", drive + "/projets-2026/archive-q1")
           .set("exo:restoreWorkspace", WORKSPACE);
    item(JOHN_BIN + "/report.docx", ID_1, "7", "report.docx", 1, Calendar.getInstance())
        .set("exo:restorePath", drive + "/projets-2026/archive-q1/l'equipe/report.docx")
        .set("exo:restoreWorkspace", WORKSPACE);
    // In another workspace, the names are not read.
    item(JOHN_BIN + "/elsewhere.txt", ID_3, "7", "elsewhere.txt", 1, Calendar.getInstance())
        .set("exo:restorePath", drive + "/projets-2026/elsewhere.txt")
        .set("exo:restoreWorkspace", "dms-system");
    jcr.commit();

    List<RecycleBinItem> items = storage.getItems(filter(7));

    // A folder found nowhere keeps its name in the path.
    assertEquals(List.of("Users", "j___", "jo___", "joh___", "john", "Private", "Projets 2026", "Archive Q1", "l'equipe"),
                 itemOf(items, ID_1).getRestoreFolderTitles());
    assertEquals(List.of("Users", "j___", "jo___", "joh___", "john", "Private", "Projets 2026"),
                 itemOf(items, ID_2).getRestoreFolderTitles());
    assertNull(itemOf(items, ID_3).getRestoreFolderTitles());
    List<String> lookups = jcr.getStatements().stream().filter(statement -> statement.contains(" AND exo:restorePath = '")).toList();
    assertEquals(List.of("SELECT * FROM exo:recycleBinItem WHERE jcr:path LIKE '/Trash/%' AND exo:restorePath = '" + drive
        + "/projets-2026/archive-q1'",
                         "SELECT * FROM exo:recycleBinItem WHERE jcr:path LIKE '/Trash/%' AND exo:restorePath = '" + drive
                             + "/projets-2026/archive-q1/l''equipe'"),
                 lookups);
    LocationFactory locationFactory = new LocationFactory(new NamespaceRegistryImpl());
    for (String statement : lookups) {
      assertNotNull(JCRSQLQueryBuilder.createQuery(statement, locationFactory, new DefaultQueryNodeFactory()), statement);
    }
  }

  @Test
  void anotherDrivesItemsTheShortcutsTrashedWithADocumentAndWhatAFolderHoldsAreNotListed() throws Exception {
    item(JOHN_BIN + "/mine.txt", ID_1, "7", "mine.txt", 1, Calendar.getInstance());
    item("/Trash/Groups/spaces/marketing/theirs.txt", ID_2, "9", "theirs.txt", 1, Calendar.getInstance());
    // A shortcut trashed with the document it points at: marked, with no drive.
    Item cascade = jcr.add(JOHN_BIN + "/link", "exo:symlink");
    cascade.mixin("exo:recycleBinItem", "exo:restoreLocation").set("exo:trashId", ID_3).set("exo:recycleBinOf", ID_1);
    Item folder = jcr.add(JOHN_BIN + "/archive", "nt:folder");
    folder.mixin("exo:recycleBinItem", "exo:restoreLocation")
          .set("exo:trashId", ID_4)
          .set("exo:recycleBinOwner", "7")
          .set("exo:recycleBinName", "archive")
          .set("exo:recycleBinSize", 3L);
    jcr.addFile(JOHN_BIN + "/archive/inside.txt", 3);
    jcr.commit();

    List<RecycleBinItem> items = storage.getItems(filter(7));

    assertEquals(List.of(ID_1, ID_4), items.stream().map(RecycleBinItem::getId).sorted().toList());
    assertTrue(items.stream().filter(item -> ID_4.equals(item.getId())).findFirst().orElseThrow().isFolder());
    assertEquals(2, storage.countItems(filter(7)));
    assertEquals(1, storage.countItems(filter(9)));
    assertEquals(0, storage.countItems(filter(8)));
  }

  @Test
  void theStatementIsKeptToTheTrashAndToTheDrive() throws Exception {
    storage.getItems(filter(7));

    assertEquals(List.of("SELECT * FROM exo:recycleBinItem WHERE jcr:path LIKE '/Trash/%' AND exo:recycleBinOwner = '7'"
        + " ORDER BY exo:deletedDate DESC"), jcr.getStatements());
  }

  @Test
  void aTermIsLookedForInTheNamesWhateverItsCaseAndReadsNoPatternCharacter() throws Exception {
    item(JOHN_BIN + "/a", ID_1, "7", "budget_2026.xlsx", 1, Calendar.getInstance());
    item(JOHN_BIN + "/b", ID_2, "7", "budget-2026.xlsx", 1, Calendar.getInstance());
    item(JOHN_BIN + "/c", ID_3, "7", "john's 100% plan.txt", 1, Calendar.getInstance());

    assertEquals(List.of(ID_1), ids(storage.getItems(filter(7, " T_2026 "))));
    assertEquals(List.of(ID_3), ids(storage.getItems(filter(7, "N'S 100%"))));
    assertEquals(3, storage.countItems(filter(7, "  ")));

    assertTrue(jcr.getStatements().get(0).contains(" AND exo:recycleBinName LIKE '%t\\_2026%' ESCAPE '\\'"), jcr.getStatements().get(0));
    assertTrue(jcr.getStatements().get(1).contains(" LIKE '%n''s 100\\%%' ESCAPE '\\'"), jcr.getStatements().get(1));
    assertFalse(jcr.getStatements().get(2).contains("LIKE '%  "), jcr.getStatements().get(2));
  }

  @Test
  void aBackslashOfATermIsReadAsItself() throws Exception {
    storage.getItems(filter(7, "a\\b"));

    assertTrue(jcr.getStatements().get(0).contains(" LIKE '%a\\\\b%' ESCAPE '\\'"), jcr.getStatements().get(0));
  }

  @Test
  void eachSortFieldReadsThePropertyRecordedForIt() throws Exception {
    assertEquals("exo:recycleBinName ASC", orderOf(RecycleBinSortField.NAME, true));
    assertEquals("exo:restorePath DESC", orderOf(RecycleBinSortField.ORIGINAL_LOCATION, false));
    assertEquals("exo:deletedDate ASC", orderOf(RecycleBinSortField.DELETED_DATE, true));
    assertEquals("exo:deletedBy DESC", orderOf(RecycleBinSortField.DELETED_BY, false));
    assertEquals("exo:recycleBinSize ASC", orderOf(RecycleBinSortField.SIZE, true));
    assertEquals("exo:deletedDate DESC", orderOf(null, false));
  }

  @Test
  void theItemsAreReadSortedAndByPageWhileTheCountIgnoresBoth() throws Exception {
    item(JOHN_BIN + "/a", ID_1, "7", "a", 30, Calendar.getInstance());
    item(JOHN_BIN + "/b", ID_2, "7", "b", 10, Calendar.getInstance());
    item(JOHN_BIN + "/c", ID_3, "7", "c", 20, Calendar.getInstance());
    RecycleBinFilter filter = new RecycleBinFilter(7, null, RecycleBinSortField.SIZE, true, 1, 1);

    assertEquals(List.of(ID_3), ids(storage.getItems(filter)));
    assertEquals(3, storage.countItems(filter));

    assertFalse(jcr.getStatements().get(1).contains("ORDER BY"), jcr.getStatements().get(1));
    filter.setOffset(0);
    filter.setLimit(0);
    assertEquals(List.of(ID_2, ID_3, ID_1), ids(storage.getItems(filter)));
  }

  @Test
  void anItemIsReadByItsIdInTheBin() throws Exception {
    item(JOHN_BIN + "/mine.txt", ID_1, "7", "mine.txt", 1, Calendar.getInstance());
    item("/Trash/orphan.txt", ID_2, null, "orphan.txt", 1, Calendar.getInstance());

    assertEquals("mine.txt", storage.getItem(ID_1).getName());
    assertNull(storage.getItem(ID_2).getOwnerId());
    assertNull(storage.getItem(ID_3));

    assertEquals("SELECT * FROM exo:recycleBinItem WHERE jcr:path LIKE '/Trash/%' AND exo:trashId = '" + ID_1 + "'",
                 jcr.getStatements().get(0));
  }

  @Test
  void whatIsNotAnIdOfAnItemReadsNothingAndRunsNoStatement() throws Exception {
    item(JOHN_BIN + "/mine.txt", ID_1, "7", "mine.txt", 1, Calendar.getInstance());

    assertNull(storage.getItem(null));
    assertNull(storage.getItem(""));
    assertNull(storage.getItem(ID_1 + "' OR exo:recycleBinOwner = '9"));
    assertNull(storage.getItem(ID_1.substring(1)));
    assertNull(storage.getItem(" " + ID_1));

    assertTrue(jcr.getStatements().isEmpty());
  }

  @Test
  void aShortcutTrashedWithADocumentIsNotReadAsAnItem() throws Exception {
    Item cascade = jcr.add(JOHN_BIN + "/link", "exo:symlink");
    cascade.mixin("exo:recycleBinItem", "exo:restoreLocation").set("exo:trashId", ID_3).set("exo:recycleBinOf", ID_1);
    Item own = jcr.add(JOHN_BIN + "/own-link", "exo:symlink");
    own.mixin("exo:recycleBinItem", "exo:restoreLocation").set("exo:trashId", ID_4).set("exo:recycleBinOwner", "7");
    jcr.commit();

    assertNull(storage.getItem(ID_3));
    // A shortcut a user deleted is an item of its own, and no folder.
    RecycleBinItem shortcut = storage.getItem(ID_4);
    assertNotNull(shortcut);
    assertTrue(shortcut.isShortcut());
    assertFalse(shortcut.isFolder());
  }

  @Test
  void aDeletedDocumentIsReadAsTheItemItIsByItsOwnId() throws Exception {
    Item file = item(JOHN_BIN + "/mine.txt", ID_1, "7", "mine.txt", 1, Calendar.getInstance());
    Item folder = jcr.add(JOHN_BIN + "/archive", "nt:folder");
    folder.mixin("exo:recycleBinItem", "exo:restoreLocation").set("exo:trashId", ID_2).set("exo:recycleBinOwner", "7");
    Item inside = jcr.addFile(JOHN_BIN + "/archive/inside.txt", 3);
    Item cascade = shortcut(JOHN_BIN + "/link", file.getId(), ID_3, ID_1);
    Item alive = jcr.addFile("/Users/j___/jo___/joh___/john/Private/alive.txt", 1);
    // Back in a drive with the marks of an earlier stay in a bin.
    alive.mixin("exo:recycleBinItem", "exo:restoreLocation").set("exo:trashId", ID_4).set("exo:recycleBinOwner", "7");
    Item unmarked = jcr.addFile("/Trash/unmarked.txt", 1);
    jcr.commit();

    assertEquals(ID_1, storage.getItemOfDocument(file.getId()).getId());
    assertEquals(7L, storage.getItemOfDocument(file.getId()).getOwnerId());
    assertTrue(storage.getItemOfDocument(folder.getId()).isFolder());
    // What a deleted folder holds, a shortcut trashed with a document, a
    // document of a drive and a node of the trash that is no item are none.
    assertNull(storage.getItemOfDocument(inside.getId()));
    assertNull(storage.getItemOfDocument(cascade.getId()));
    assertNull(storage.getItemOfDocument(alive.getId()));
    assertNull(storage.getItemOfDocument(unmarked.getId()));
    assertNull(storage.getItemOfDocument("an-id-of-no-node"));
    verify(systemProvider, times(8)).close();
    assertNull(storage.getItemOfDocument(null));
    assertNull(storage.getItemOfDocument(" "));
    verify(systemProvider, times(8)).close();
    assertTrue(jcr.getStatements().isEmpty());
  }

  @Test
  void anItemIsReadAtItsPathInTheTrashAndNowhereElse() throws Exception {
    Item file = item(JOHN_BIN + "/mine.txt", ID_1, "7", "mine.txt", 1, Calendar.getInstance());
    Item folder = jcr.add(JOHN_BIN + "/archive", "nt:folder");
    folder.mixin("exo:recycleBinItem", "exo:restoreLocation").set("exo:trashId", ID_2).set("exo:recycleBinOwner", "7");
    jcr.addFile(JOHN_BIN + "/archive/inside.txt", 3);
    shortcut(JOHN_BIN + "/link", file.getId(), ID_3, ID_1);
    Item alive = jcr.addFile("/Users/j___/jo___/joh___/john/Private/alive.txt", 1);
    alive.mixin("exo:recycleBinItem", "exo:restoreLocation").set("exo:trashId", ID_4).set("exo:recycleBinOwner", "7");
    jcr.commit();

    assertEquals(ID_1, storage.getItemAt(JOHN_BIN + "/mine.txt").getId());
    assertEquals(JOHN_BIN + "/mine.txt", storage.getItemAt(JOHN_BIN + "/mine.txt").getPath());
    assertEquals(JOHN_BIN + "/mine.txt", storage.getItem(ID_1).getPath());
    assertNull(storage.getItemAt(JOHN_BIN + "/archive/inside.txt"));
    assertNull(storage.getItemAt(JOHN_BIN + "/link"));
    assertNull(storage.getItemAt(JOHN_BIN + "/gone.txt"));
    assertNull(storage.getItemAt("/Users/j___/jo___/joh___/john/Private/alive.txt"));
    assertNull(storage.getItemAt("/Trash"));
    assertNull(storage.getItemAt("/Trashed/mine.txt"));
    assertNull(storage.getItemAt(null));
    assertNull(storage.getItemAt(" "));
  }

  @Test
  void everyStatementIsAcceptedByTheRepositoryParser() throws Exception {
    storage.getItems(new RecycleBinFilter(7, "N'S 100%_\\", RecycleBinSortField.SIZE, true, 0, 10));
    storage.countItems(filter(7, "a"));
    storage.getItem(ID_1);
    for (RecycleBinSortField sortField : RecycleBinSortField.values()) {
      storage.getItems(new RecycleBinFilter(7, null, sortField, false, 0, 10));
    }

    LocationFactory locationFactory = new LocationFactory(new NamespaceRegistryImpl());
    assertEquals(3 + RecycleBinSortField.values().length, jcr.getStatements().size());
    for (String statement : jcr.getStatements()) {
      assertNotNull(JCRSQLQueryBuilder.createQuery(statement, locationFactory, new DefaultQueryNodeFactory()), statement);
    }
  }

  @Test
  void theSessionOfEachCallIsClosedEvenWhenTheBinCannotBeRead() throws Exception {
    storage.getItems(filter(7));
    storage.countItems(filter(7));
    storage.getItem(ID_1);
    verify(systemProvider, times(3)).close();

    jcr.setQueryHandler(statement -> {
      throw new IllegalStateException("The index is not available");
    });
    assertThrows(IllegalStateException.class, () -> storage.getItems(filter(7)));
    assertThrows(IllegalStateException.class, () -> storage.countItems(filter(7)));
    assertThrows(IllegalStateException.class, () -> storage.getItem(ID_1));
    verify(systemProvider, times(6)).close();
  }

  @Test
  void anItemIsRemovedForGoodInOneSaveAndTheOthersStay() throws Exception {
    Item file = item(JOHN_BIN + "/mine.txt", ID_1, "7", "mine.txt", 1, Calendar.getInstance());
    Item other = item(JOHN_BIN + "/other.txt", ID_2, "7", "other.txt", 1, Calendar.getInstance());
    Item folder = jcr.add(JOHN_BIN + "/archive", "nt:folder");
    folder.mixin("exo:recycleBinItem", "exo:restoreLocation").set("exo:trashId", ID_3).set("exo:recycleBinOwner", "7");
    Item inside = jcr.addFile(JOHN_BIN + "/archive/inside.txt", 3);

    storage.removeItem(ID_1);
    assertNull(jcr.find(file.getId()));
    assertEquals(1, jcr.getSaves());

    storage.removeItem(ID_3);
    assertNull(jcr.find(folder.getId()));
    assertNull(jcr.find(inside.getId()));
    assertEquals(2, jcr.getSaves());
    assertNotNull(jcr.find(other.getId()));
    assertNotNull(jcr.at(JOHN_BIN));
  }

  @Test
  void theShortcutsToAnItemAndTheOnesTrashedWithItLeaveInTheSameSave() throws Exception {
    Item file = item(JOHN_BIN + "/mine.txt", ID_1, "7", "mine.txt", 1, Calendar.getInstance());
    Item other = item(JOHN_BIN + "/other.txt", ID_2, "7", "other.txt", 1, Calendar.getInstance());
    // Trashed with the item, and pointing at it: found by both lookups.
    Item cascade = shortcut(JOHN_BIN + "/mine-link", file.getId(), ID_3, ID_1);
    // Trashed with the item, pointing at a document a deleted folder held.
    Item cascadeOfInner = shortcut(JOHN_BIN + "/inner-link", "another-id", ID_4, ID_1);
    // Left in a drive by a deletion made before the Recycle Bin.
    Item left = shortcut("/Groups/spaces/marketing/Documents/mine-link", file.getId(), null, null);
    Item cascadeOfOther = shortcut(JOHN_BIN + "/other-link", other.getId(), "55555555-5555-5555-5555-555555555555", ID_2);

    storage.removeItem(ID_1);

    assertNull(jcr.find(file.getId()));
    assertNull(jcr.find(cascade.getId()));
    assertNull(jcr.find(cascadeOfInner.getId()));
    assertNull(jcr.find(left.getId()));
    assertNotNull(jcr.find(other.getId()));
    assertNotNull(jcr.find(cascadeOfOther.getId()));
    assertEquals(1, jcr.getSaves());
  }

  @Test
  void theItemsOfABinThatLeaveWithAnotherAreToldToTheCaller() throws Exception {
    Item file = item(JOHN_BIN + "/mine.txt", ID_1, "7", "mine.txt", 1, Calendar.getInstance());
    // A shortcut to the document that another user deleted: an item of that user's bin.
    Item theirs = shortcut("/Trash/Groups/spaces/marketing/mine-link", file.getId(), ID_2, null);
    theirs.set("exo:recycleBinOwner", "9").set("exo:recycleBinSize", 0L);
    // Trashed with the document: in no bin.
    shortcut(JOHN_BIN + "/mine-link", file.getId(), ID_3, ID_1);
    // Back in a drive with the marks of an earlier stay in a bin: in no bin either.
    Item restored = shortcut("/Groups/spaces/marketing/Documents/mine-link", file.getId(), ID_4, null);
    restored.set("exo:recycleBinOwner", "9");
    jcr.commit();

    List<RecycleBinItem> removedWithIt = storage.removeItem(ID_1);

    assertEquals(List.of(ID_2), ids(removedWithIt));
    assertEquals(9L, removedWithIt.get(0).getOwnerId());
    assertTrue(removedWithIt.get(0).isShortcut());
    assertNull(jcr.find(theirs.getId()));
    assertNull(jcr.find(restored.getId()));
    assertEquals(1, jcr.getSaves());
  }

  @Test
  void theShortcutsUsersDeletedToWhatAFolderHeldLeaveWithTheFolder() throws Exception {
    Item folder = jcr.add(JOHN_BIN + "/archive", "nt:folder");
    folder.mixin("exo:recycleBinItem", "exo:restoreLocation").set("exo:trashId", ID_1).set("exo:recycleBinOwner", "7");
    Item inside = jcr.addFile(JOHN_BIN + "/archive/Deep/inside.txt", 3);
    Item outside = jcr.addFile("/Groups/spaces/marketing/Documents/outside.txt", 3);
    // A shortcut the folder holds leaves with it, as the document it points to.
    jcr.add(JOHN_BIN + "/archive/Deep/a-link", "exo:symlink").set("exo:uuid", inside.getId());
    // Deleted by their user before the folder was: items of that user's bin.
    Item toInside = shortcut("/Trash/Groups/spaces/marketing/inside-link", inside.getId(), ID_2, null);
    toInside.set("exo:recycleBinOwner", "9").set("exo:recycleBinSize", 0L);
    Item toOutside = shortcut("/Trash/Groups/spaces/marketing/outside-link", outside.getId(), ID_3, null);
    toOutside.set("exo:recycleBinOwner", "9").set("exo:recycleBinSize", 0L);
    Item toNothing = shortcut("/Trash/Groups/spaces/marketing/gone-link", "an-id-of-no-node", ID_4, null);
    toNothing.set("exo:recycleBinOwner", "9").set("exo:recycleBinSize", 0L);
    item(JOHN_BIN + "/mine.txt", "55555555-5555-5555-5555-555555555555", "7", "mine.txt", 1, Calendar.getInstance());
    jcr.commit();

    int before = jcr.getStatements().size();
    List<RecycleBinItem> removedWithIt = storage.removeItem(ID_1);

    assertEquals(List.of(ID_2), ids(removedWithIt));
    assertNull(jcr.find(toInside.getId()));
    assertNotNull(jcr.find(toOutside.getId()));
    assertNotNull(jcr.find(toNothing.getId()));
    assertEquals(1, jcr.getSaves());
    // The shortcuts are looked up for the folder and each node it holds, not inside a document nor for a shortcut.
    assertEquals(3, linkLookups(before));

    // A document holds no node a shortcut may point at: its removal looks for its own shortcuts only.
    before = jcr.getStatements().size();
    storage.removeItem("55555555-5555-5555-5555-555555555555");
    assertEquals(1, linkLookups(before));
  }

  private long linkLookups(int from) {
    return jcr.getStatements().subList(from, jcr.getStatements().size()).stream().filter(s -> s.startsWith("SELECT * FROM exo:symlink ")).count();
  }

  @Test
  void aShortcutIsRemovedWithoutLookingForShortcutsToIt() throws Exception {
    Item own = shortcut(JOHN_BIN + "/own-link", "a-document", ID_4, null);
    own.set("exo:recycleBinOwner", "7");
    Item cascade = shortcut(JOHN_BIN + "/link", "a-document", ID_3, ID_1);
    jcr.commit();

    assertTrue(storage.removeItem(ID_4).isEmpty());
    // A shortcut trashed with a document can be removed by its id too.
    storage.removeItem(ID_3);

    assertNull(jcr.find(own.getId()));
    assertNull(jcr.find(cascade.getId()));
    assertFalse(jcr.getStatements().stream().anyMatch(statement -> statement.contains("exo:uuid=")), jcr.getStatements().toString());
  }

  @Test
  void aNodeTheIndexStillAnswersWithAfterItLeftTheTrashIsNotRemoved() throws Exception {
    Item restored = jcr.addFile("/Library/report.docx", 5);
    restored.mixin("exo:recycleBinItem").set("exo:trashId", ID_1);
    Item unmarked = jcr.addFile(JOHN_BIN + "/plain.docx", 5);
    unmarked.set("exo:trashId", ID_2);
    jcr.commit();
    int saves = jcr.getSaves();

    jcr.setQueryHandler(statement -> List.of(restored));
    assertThrows(PathNotFoundException.class, () -> storage.removeItem(ID_1));
    jcr.setQueryHandler(statement -> List.of(unmarked));
    assertThrows(PathNotFoundException.class, () -> storage.removeItem(ID_2));

    assertEquals(saves, jcr.getSaves());
    assertNotNull(jcr.at("/Library/report.docx"));
    assertNotNull(jcr.at(JOHN_BIN + "/plain.docx"));
  }

  @Test
  void anIdThatNamesNoNodeOfTheTrashRemovesNothing() throws Exception {
    item(JOHN_BIN + "/mine.txt", ID_1, "7", "mine.txt", 1, Calendar.getInstance());

    assertThrows(PathNotFoundException.class, () -> storage.removeItem(ID_2));
    assertThrows(PathNotFoundException.class, () -> storage.removeItem(null));
    assertThrows(PathNotFoundException.class, () -> storage.removeItem(ID_1 + "' OR exo:recycleBinOwner = '7"));

    assertEquals(0, jcr.getSaves());
    assertEquals(1, jcr.getStatements().size());
    assertNotNull(jcr.at(JOHN_BIN + "/mine.txt"));
  }

  @Test
  void aRemovalThatCannotBeSavedLeavesTheItemAndItsShortcutsAndTellsWhy() throws Exception {
    Item file = item(JOHN_BIN + "/mine.txt", ID_1, "7", "mine.txt", 1, Calendar.getInstance());
    Item cascade = shortcut(JOHN_BIN + "/mine-link", file.getId(), ID_3, ID_1);
    InvalidItemStateException failure = new InvalidItemStateException("The item was changed by another session");
    jcr.failNextSave(failure);

    assertSame(failure, assertThrows(InvalidItemStateException.class, () -> storage.removeItem(ID_1)));

    assertEquals(1, jcr.getRefreshes());
    assertNotNull(jcr.find(file.getId()));
    assertNotNull(jcr.find(cascade.getId()));
    verify(systemProvider).close();
  }

  @Test
  void theStatementsOfARemovalAreAcceptedByTheRepositoryParser() throws Exception {
    item(JOHN_BIN + "/mine.txt", ID_1, "7", "mine.txt", 1, Calendar.getInstance());
    Item folder = jcr.add(JOHN_BIN + "/archive", "nt:folder");
    folder.mixin("exo:recycleBinItem", "exo:restoreLocation").set("exo:trashId", ID_2).set("exo:recycleBinOwner", "7");
    jcr.addFile(JOHN_BIN + "/archive/inside.txt", 3);
    jcr.commit();

    storage.removeItem(ID_1);
    assertEquals(3, jcr.getStatements().size());
    // A folder may hold what a shortcut a user deleted points at.
    storage.removeItem(ID_2);
    assertEquals(7, jcr.getStatements().size());

    LocationFactory locationFactory = new LocationFactory(new NamespaceRegistryImpl());
    for (String statement : jcr.getStatements()) {
      assertNotNull(JCRSQLQueryBuilder.createQuery(statement, locationFactory, new DefaultQueryNodeFactory()), statement);
    }
  }

  @Test
  void theContentOfADeletedDocumentIsReadWithItsNameAndItsType() throws Exception {
    Calendar modified = daysAgo(3);
    Item file = item(JOHN_BIN + "/report.docx", ID_1, "7", "report.docx", 5, daysAgo(1));
    file.set("exo:title", "Report.docx");
    jcr.at(JOHN_BIN + "/report.docx/jcr:content")
       .set("jcr:data", "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8))
       .set("jcr:mimeType", "application/msword")
       .set("jcr:lastModified", modified);
    Item bare = item(JOHN_BIN + "/bare.bin", ID_2, "7", "bare.bin", 2, daysAgo(1));
    jcr.at(JOHN_BIN + "/bare.bin/jcr:content").set("jcr:data", new byte[] { 1, 2 });
    jcr.commit();

    FileContent content = storage.getItemContent(ID_1);

    assertEquals(file.getId(), content.getId());
    assertEquals("Report.docx", content.getName());
    assertEquals("application/msword", content.getMimeType());
    assertEquals("hello", new String(content.getContent().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    assertEquals(modified.getTime(), content.getUpdatedDate());
    // Neither a type nor a date was recorded.
    FileContent bareContent = storage.getItemContent(ID_2);
    assertEquals(bare.getId(), bareContent.getId());
    assertNull(bareContent.getMimeType());
    assertNull(bareContent.getUpdatedDate());
    assertEquals(2, bareContent.getContent().readAllBytes().length);
    verify(systemProvider, times(2)).close();
  }

  @Test
  void whatHasNoContentOfItsOwnReadsNone() throws Exception {
    // A folder, a file whose content holds no data, and a shortcut trashed with a document.
    jcr.add(JOHN_BIN + "/folder", "nt:folder")
       .mixin("exo:restoreLocation", "exo:recycleBinItem")
       .set("exo:trashId", ID_1)
       .set("exo:recycleBinOwner", "7");
    item(JOHN_BIN + "/empty.docx", ID_2, "7", "empty.docx", 0, daysAgo(1));
    jcr.at(JOHN_BIN + "/empty.docx/jcr:content").getProperties().remove("jcr:data");
    Item cascade = jcr.addFile(JOHN_BIN + "/held.docx", 3);
    cascade.mixin("exo:restoreLocation", "exo:recycleBinItem").set("exo:trashId", ID_3).set("exo:recycleBinOf", ID_2);
    jcr.at(JOHN_BIN + "/held.docx/jcr:content").set("jcr:data", new byte[] { 1 });
    jcr.commit();

    assertNull(storage.getItemContent(ID_1));
    assertNull(storage.getItemContent(ID_2));
    assertNull(storage.getItemContent(ID_3));
    assertNull(storage.getItemContent(ID_4));
    verify(systemProvider, times(4)).close();

    jcr.getStatements().clear();
    assertNull(storage.getItemContent(null));
    assertNull(storage.getItemContent("' OR exo:trashId LIKE '%"));
    assertTrue(jcr.getStatements().isEmpty());
  }

  @Test
  void whatWasDeletedBeforeAnInstantIsListedByItsIdWhateverItsDrive() throws Exception {
    item(JOHN_BIN + "/old.docx", ID_1, "7", "old.docx", 10, daysAgo(10));
    item(JOHN_BIN + "/recent.docx", ID_2, "7", "recent.docx", 10, daysAgo(1));
    item("/Trash/lost.docx", ID_3, null, "lost.docx", 10, daysAgo(10));
    shortcut(JOHN_BIN + "/link", "target-id", ID_4, ID_1).set("exo:deletedDate", daysAgo(10));
    // A node that lost its id cannot be named to a removal.
    item(JOHN_BIN + "/unnamed.docx", ID_2, "7", "unnamed.docx", 10, daysAgo(10)).getProperties().remove("exo:trashId");
    jcr.commit();

    List<String> ids = storage.getIdsDeletedBefore(daysAgo(5).toInstant());

    assertEquals(List.of(ID_1, ID_3, ID_4), ids.stream().sorted().toList());

    jcr.getStatements().clear();
    storage.getIdsDeletedBefore(Instant.parse("2026-03-01T02:00:00.123Z"));
    // The instant is written in UTC, to the millisecond.
    assertEquals(List.of("SELECT * FROM exo:recycleBinItem WHERE jcr:path LIKE '/Trash/%' AND exo:deletedDate < TIMESTAMP '2026-03-01T02:00:00.123+00:00'"),
                 jcr.getStatements());
  }

  @Test
  void aShortcutTrashedWithADocumentIsRemovableOnlyOnceItsDocumentHasLeftTheTrash() throws Exception {
    item(JOHN_BIN + "/report.docx", ID_1, "7", "report.docx", 10, daysAgo(10));
    shortcut(JOHN_BIN + "/link", "target-id", ID_3, ID_1).set("exo:deletedDate", daysAgo(10));
    shortcut(JOHN_BIN + "/orphan", "gone-id", ID_4, ID_2).set("exo:deletedDate", daysAgo(10));
    jcr.commit();

    RecycleBinItem item = storage.getRemovableItem(ID_1);
    assertEquals(ID_1, item.getId());
    assertEquals(Long.valueOf(7), item.getOwnerId());
    assertEquals(10, item.getSize());
    assertNotNull(item.getDeletedDate());
    // Held by its document, which is still in the trash.
    assertNull(storage.getRemovableItem(ID_3));
    // Its document was restored or removed: nothing else will take it away.
    RecycleBinItem orphan = storage.getRemovableItem(ID_4);
    assertEquals(ID_4, orphan.getId());
    assertNull(orphan.getOwnerId());
    assertNotNull(orphan.getDeletedDate());
    assertNull(storage.getRemovableItem(ID_2));

    jcr.getStatements().clear();
    assertNull(storage.getRemovableItem(null));
    assertNull(storage.getRemovableItem("' OR exo:trashId LIKE '%"));
    assertTrue(jcr.getStatements().isEmpty());
  }

  @Test
  void aShortcutWhoseDocumentIsNamedByNoIdIsRemovableWithoutThatNameBeingRead() throws Exception {
    shortcut(JOHN_BIN + "/odd", "target-id", ID_4, "' OR exo:trashId LIKE '%").set("exo:deletedDate", daysAgo(10));
    jcr.commit();

    assertEquals(ID_4, storage.getRemovableItem(ID_4).getId());
    assertEquals(1, jcr.getStatements().size());
  }

  @Test
  void theItemsOfEachDriveAreCountedFromTheTrashItself() throws Exception {
    item(JOHN_BIN + "/a.docx", ID_1, "7", "a.docx", 10, daysAgo(1));
    item(JOHN_BIN + "/b.docx", ID_2, "7", "b.docx", 5, daysAgo(1));
    item("/Trash/Groups/spaces/sales/c.docx", ID_3, "20", "c.docx", 7, daysAgo(1)).getProperties().remove("exo:recycleBinSize");
    // In no drive: an item that records no owner, one whose owner is no identity id, and a shortcut trashed with a document.
    item("/Trash/lost.docx", ID_4, null, "lost.docx", 100, daysAgo(1));
    item("/Trash/odd.docx", ID_4, "john", "odd.docx", 100, daysAgo(1));
    shortcut(JOHN_BIN + "/link", "target-id", ID_4, ID_1);
    jcr.commit();
    jcr.getStatements().clear();

    List<RecycleBinDrive> totals = storage.getDriveTotals();

    assertEquals(List.of(new RecycleBinDrive(7, 15, 2), new RecycleBinDrive(20, 0, 1)), totals);
    assertEquals(List.of("SELECT * FROM exo:recycleBinItem WHERE jcr:path LIKE '/Trash/%'"), jcr.getStatements());
  }

  @Test
  void theReadsOfARetentionRunCloseTheirSessionAndAreAcceptedByTheRepositoryParser() throws Exception {
    shortcut(JOHN_BIN + "/link", "target-id", ID_3, ID_1);
    storage.getIdsDeletedBefore(Instant.now());
    storage.getRemovableItem(ID_3);
    storage.getDriveTotals();
    verify(systemProvider, times(3)).close();

    LocationFactory locationFactory = new LocationFactory(new NamespaceRegistryImpl());
    assertEquals(4, jcr.getStatements().size());
    for (String statement : jcr.getStatements()) {
      assertNotNull(JCRSQLQueryBuilder.createQuery(statement, locationFactory, new DefaultQueryNodeFactory()), statement);
    }

    jcr.setQueryHandler(statement -> {
      throw new IllegalStateException("The index is not available");
    });
    assertThrows(IllegalStateException.class, () -> storage.getIdsDeletedBefore(Instant.now()));
    assertThrows(IllegalStateException.class, () -> storage.getRemovableItem(ID_3));
    assertThrows(IllegalStateException.class, () -> storage.getDriveTotals());
    verify(systemProvider, times(6)).close();
  }

  private static Calendar daysAgo(int days) {
    Calendar date = Calendar.getInstance();
    date.add(Calendar.DAY_OF_MONTH, -days);
    return date;
  }

  private String orderOf(RecycleBinSortField sortField, boolean ascending) throws Exception {
    jcr.getStatements().clear();
    storage.getItems(new RecycleBinFilter(7, null, sortField, ascending, 0, 10));
    String statement = jcr.getStatements().get(0);
    return statement.substring(statement.indexOf(" ORDER BY ") + " ORDER BY ".length());
  }

  private static RecycleBinFilter filter(long ownerId) {
    return filter(ownerId, null);
  }

  private static RecycleBinFilter filter(long ownerId, String term) {
    return new RecycleBinFilter(ownerId, term, RecycleBinSortField.DELETED_DATE, false, 0, 0);
  }

  private static List<String> ids(List<RecycleBinItem> items) {
    return items.stream().map(RecycleBinItem::getId).toList();
  }

  private static RecycleBinItem itemOf(List<RecycleBinItem> items, String id) {
    return items.stream().filter(item -> id.equals(item.getId())).findFirst().orElseThrow();
  }

  private Item item(String path, String trashId, String ownerId, String lowerCasedName, long size, Calendar deletedDate) {
    Item file = jcr.addFile(path, size);
    file.mixin("exo:restoreLocation", "exo:recycleBinItem")
        .set("exo:trashId", trashId)
        .set("exo:recycleBinName", lowerCasedName)
        .set("exo:recycleBinSize", size)
        .set("exo:deletedDate", deletedDate);
    if (ownerId != null) {
      file.set("exo:recycleBinOwner", ownerId);
    }
    jcr.commit();
    return file;
  }

  /**
   * Answers a statement of the storage as the test expects the repository
   * to: the marked nodes under the trash home, of the drive, the id or the
   * term the statement names, in the order it asks for.
   */
  private List<Item> answer(String statement) {
    String target = group(statement, "^SELECT \\* FROM exo:symlink WHERE exo:uuid='([^']*)' AND exo:workspace='collaboration'$");
    if (target != null) {
      List<Item> links = new ArrayList<>();
      collectLinks(jcr.at("/"), target, links);
      return links;
    }
    String cascadeOf = group(statement, "exo:recycleBinOf = '([^']*)'");
    if (cascadeOf != null) {
      List<Item> cascades = new ArrayList<>();
      collect(jcr.at("/Trash"), cascades);
      cascades.removeIf(item -> !cascadeOf.equals(item.get("exo:recycleBinOf")));
      return cascades;
    }
    assertTrue(statement.equals("SELECT * FROM exo:recycleBinItem WHERE jcr:path LIKE '/Trash/%'")
        || statement.startsWith("SELECT * FROM exo:recycleBinItem WHERE jcr:path LIKE '/Trash/%' AND "), statement);
    String before = group(statement, "exo:deletedDate < TIMESTAMP '([^']*)'$");
    String owner = group(statement, "exo:recycleBinOwner = '([^']*)'");
    String id = group(statement, "exo:trashId = '([^']*)'");
    String quotedRestorePath = group(statement, "exo:restorePath = '((?:[^']|'')*)'");
    String restorePath = quotedRestorePath == null ? null : quotedRestorePath.replace("''", "'");
    String pattern = group(statement, "exo:recycleBinName LIKE '%(.*)%' ESCAPE '\\\\'");
    String term = pattern == null ? null : pattern.replace("''", "'").replaceAll("\\\\(.)", "$1");
    List<Item> found = new ArrayList<>();
    collect(jcr.at("/Trash"), found);
    found.removeIf(item -> owner != null && !owner.equals(item.get("exo:recycleBinOwner")));
    found.removeIf(item -> id != null && !id.equals(item.get("exo:trashId")));
    found.removeIf(item -> restorePath != null && !restorePath.equals(item.get("exo:restorePath")));
    found.removeIf(item -> before != null && !(item.get("exo:deletedDate") instanceof Calendar deleted
        && deleted.toInstant().isBefore(OffsetDateTime.parse(before).toInstant())));
    found.removeIf(item -> term != null && !String.valueOf(item.get("exo:recycleBinName")).contains(term));
    Matcher order = Pattern.compile(" ORDER BY (\\S+) (ASC|DESC)$").matcher(statement);
    if (order.find()) {
      Comparator<Item> comparator = Comparator.comparing(item -> comparable(item.get(order.group(1))),
                                                         Comparator.nullsFirst(Comparator.naturalOrder()));
      found.sort("ASC".equals(order.group(2)) ? comparator : comparator.reversed());
    }
    return found;
  }

  @SuppressWarnings({ "rawtypes" })
  private static Comparable comparable(Object value) {
    return value instanceof Comparable comparable ? comparable : null;
  }

  private void collect(Item item, List<Item> found) {
    for (Item child : item.getChildren()) {
      if (child.has("exo:recycleBinItem")) {
        found.add(child);
      }
      collect(child, found);
    }
  }

  private void collectLinks(Item item, String targetId, List<Item> found) {
    for (Item child : item.getChildren()) {
      if (child.has("exo:symlink") && targetId.equals(child.get("exo:uuid"))) {
        found.add(child);
      }
      collectLinks(child, targetId, found);
    }
  }

  private Item shortcut(String path, String targetId, String trashId, String cascadeOf) {
    Item link = jcr.add(path, "exo:symlink");
    link.set("exo:uuid", targetId);
    if (trashId != null) {
      link.mixin("exo:restoreLocation", "exo:recycleBinItem").set("exo:trashId", trashId);
    }
    if (cascadeOf != null) {
      link.set("exo:recycleBinOf", cascadeOf);
    }
    jcr.commit();
    return link;
  }

  private static String group(String statement, String regex) {
    Matcher matcher = Pattern.compile(regex).matcher(statement);
    return matcher.find() ? matcher.group(1) : null;
  }

}
