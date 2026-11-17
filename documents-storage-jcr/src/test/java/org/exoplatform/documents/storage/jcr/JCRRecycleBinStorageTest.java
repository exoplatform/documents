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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.model.RecycleBinSortField;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.FakeJcr.Item;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.NamespaceRegistryImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.query.DefaultQueryNodeFactory;
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

    storage = new JCRRecycleBinStorage(repositoryService, trashStorage) {
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
    assertTrue(statement.startsWith("SELECT * FROM exo:recycleBinItem WHERE jcr:path LIKE '/Trash/%' AND "), statement);
    String owner = group(statement, "exo:recycleBinOwner = '([^']*)'");
    String id = group(statement, "exo:trashId = '([^']*)'");
    String pattern = group(statement, "exo:recycleBinName LIKE '%(.*)%' ESCAPE '\\\\'");
    String term = pattern == null ? null : pattern.replace("''", "'").replaceAll("\\\\(.)", "$1");
    List<Item> found = new ArrayList<>();
    collect(jcr.at("/Trash"), found);
    found.removeIf(item -> owner != null && !owner.equals(item.get("exo:recycleBinOwner")));
    found.removeIf(item -> id != null && !id.equals(item.get("exo:trashId")));
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

  private static String group(String statement, String regex) {
    Matcher matcher = Pattern.compile(regex).matcher(statement);
    return matcher.find() ? matcher.group(1) : null;
  }

}
