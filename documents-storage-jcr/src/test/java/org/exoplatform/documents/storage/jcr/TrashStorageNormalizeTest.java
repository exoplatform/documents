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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.jcr.RepositoryException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.documents.storage.jcr.FakeJcr.Item;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.NamespaceRegistryImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.query.DefaultQueryNodeFactory;
import org.exoplatform.services.jcr.impl.core.query.sql.JCRSQLQueryBuilder;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * The facts given to the items already in the trash, on a workspace kept in
 * memory: which nodes are taken for items, what each one is given and what is
 * left out, and that each item is a save of its own. What the statement
 * selects in the repository is not seen here: the test answers it with every
 * node of the trash carrying the restore location, as the repository would.
 */
class TrashStorageNormalizeTest {

  private static final String WORKSPACE    = "collaboration";

  private static final String JOHN_HOME    = "/Users/j___/jo___/joh___/john";

  private static final String JOHN_BIN     = "/Trash" + JOHN_HOME;

  private static final String JOHN_ID      = "7";

  private static final String MARKETING_ID = "9";

  private FakeJcr             jcr;

  private SessionImpl         systemSession;

  private SessionProvider     systemProvider;

  private TrashStorageImpl    trashStorage;

  @BeforeEach
  void setUp() throws Exception {
    jcr = new FakeJcr(WORKSPACE);
    jcr.add("/Trash", "nt:unstructured");
    jcr.add(JOHN_HOME + "/Private/Documents", "nt:folder");
    jcr.add("/Groups/spaces/marketing/Documents", "nt:unstructured");
    jcr.commit();
    jcr.setQueryHandler(statement -> {
      List<Item> found = new ArrayList<>();
      collectRestorable(jcr.at("/Trash"), found);
      return found;
    });
    systemSession = jcr.session("__system");

    ManageableRepository repository = mock(ManageableRepository.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    systemProvider = mock(SessionProvider.class);
    lenient().when(systemProvider.getSession(anyString(), any())).thenReturn(systemSession);

    NodeHierarchyCreator nodeHierarchyCreator = mock(NodeHierarchyCreator.class);
    lenient().when(nodeHierarchyCreator.getJcrPath("groupsPath")).thenReturn("/Groups");
    lenient().when(nodeHierarchyCreator.getJcrPath("usersPath")).thenReturn("/Users");
    lenient().when(nodeHierarchyCreator.getUserNode(any(), eq("john")))
             .thenAnswer(invocation -> jcr.node(systemSession, jcr.at(JOHN_HOME)));

    IdentityManager identityManager = mock(IdentityManager.class);
    Identity john = new Identity("organization", "john");
    john.setId(JOHN_ID);
    Identity marketing = new Identity("space", "marketing");
    marketing.setId(MARKETING_ID);
    lenient().when(identityManager.getOrCreateUserIdentity("john")).thenReturn(john);
    lenient().when(identityManager.getOrCreateSpaceIdentity("marketing")).thenReturn(marketing);

    SpaceService spaceService = mock(SpaceService.class);
    Space space = new Space();
    space.setPrettyName("marketing");
    space.setGroupId("/spaces/marketing");
    lenient().when(spaceService.getSpaceByGroupId("/spaces/marketing")).thenReturn(space);

    InitParams params = new InitParams();
    params.addParameter(valueParam("trashWorkspace", WORKSPACE));
    params.addParameter(valueParam("trashHomeNodePath", "/Trash"));
    trashStorage = new TrashStorageImpl(repositoryService,
                                        mock(SessionProviderService.class),
                                        mock(ListenerService.class),
                                        nodeHierarchyCreator,
                                        identityManager,
                                        spaceService,
                                        params) {
      @Override
      SessionProvider createSystemProvider() {
        return systemProvider;
      }
    };
  }

  @Test
  void anItemDeletedBeforeTheRecycleBinGetsItsDriveDateSizeAndName() throws Exception {
    Calendar lastModified = Calendar.getInstance();
    lastModified.add(Calendar.DAY_OF_YEAR, -40);
    Item file = trashedFile("/Trash/report.docx", 10, JOHN_HOME + "/Private/Documents/report.docx", "t1");
    file.set("exo:title", "Report.DOCX").set(NodeTypeConstants.EXO_LAST_MODIFIED_DATE, lastModified);
    jcr.commit();

    int normalized = trashStorage.normalizeRecycleBinItems();

    assertEquals(1, normalized);
    Item item = jcr.find(file.getId());
    assertTrue(item.has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    assertEquals(JOHN_ID, item.get(NodeTypeConstants.RECYCLE_BIN_OWNER));
    assertSame(lastModified, item.get(NodeTypeConstants.DELETED_DATE));
    assertEquals(10L, item.get(NodeTypeConstants.RECYCLE_BIN_SIZE));
    assertEquals("report.docx", item.get(NodeTypeConstants.RECYCLE_BIN_NAME));
    assertEquals("t1", item.get(NodeTypeConstants.TRASH_ID));
    assertEquals(JOHN_HOME + "/Private/Documents/report.docx", item.get(NodeTypeConstants.RESTORE_PATH));
  }

  @Test
  void whatWasNotRecordedAtTheDeletionIsLeftOutAndTheItemIsNotMoved() throws Exception {
    Item file = trashedFile("/Trash/report.docx", 10, JOHN_HOME + "/Private/Documents/report.docx", "t1");

    trashStorage.normalizeRecycleBinItems();

    Item item = jcr.find(file.getId());
    assertEquals("/Trash/report.docx", jcr.pathOf(item));
    assertTrue(jcr.getMoves().isEmpty());
    assertFalse(item.getProperties().containsKey(NodeTypeConstants.DELETED_BY));
    assertFalse(item.getProperties().containsKey(NodeTypeConstants.RESTORE_PARENT_ID));
    assertFalse(item.getProperties().containsKey(NodeTypeConstants.RECYCLE_BIN_PERMISSIONS));
    assertFalse(item.getProperties().containsKey(NodeTypeConstants.RECYCLE_BIN_OF));
    // No bin folder is created for an item that stays where it is.
    assertFalse(jcr.at("/Trash").getChildren().stream().anyMatch(child -> child.has(NodeTypeConstants.EXO_RECYCLE_BIN_FOLDER)));
  }

  @Test
  void anItemWithNoLastModificationIsDatedOfTheRunAndOneWithNoTrashIdGetsOne() throws Exception {
    Item file = trashedFile("/Trash/report.docx", 10, "/Groups/spaces/marketing/Documents/report.docx", null);
    Calendar before = Calendar.getInstance();

    trashStorage.normalizeRecycleBinItems();

    Item item = jcr.find(file.getId());
    assertFalse(((Calendar) item.get(NodeTypeConstants.DELETED_DATE)).before(before));
    assertNotNull(item.get(NodeTypeConstants.TRASH_ID));
    assertFalse(((String) item.get(NodeTypeConstants.TRASH_ID)).isBlank());
    assertEquals(MARKETING_ID, item.get(NodeTypeConstants.RECYCLE_BIN_OWNER));
    // With no title, the name is the node's.
    assertEquals("report.docx", item.get(NodeTypeConstants.RECYCLE_BIN_NAME));
  }

  @Test
  void anItemOfNoDriveGetsNoOwner() throws Exception {
    Item file = trashedFile("/Trash/page.html", 4, "/sites/intranet/page.html", "t1");

    assertEquals(1, trashStorage.normalizeRecycleBinItems());

    Item item = jcr.find(file.getId());
    assertTrue(item.has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    assertFalse(item.getProperties().containsKey(NodeTypeConstants.RECYCLE_BIN_OWNER));
    assertEquals(4L, item.get(NodeTypeConstants.RECYCLE_BIN_SIZE));
  }

  @Test
  void aDeletedFolderIsSizedByItsFilesAndWhatItHoldsIsNoItem() throws Exception {
    Item folder = jcr.add("/Trash/archive", "nt:folder");
    restorable(folder, JOHN_HOME + "/Private/Documents/archive", "t1");
    jcr.addFile("/Trash/archive/a.txt", 3);
    Item inner = jcr.addFile("/Trash/archive/b.txt", 5);
    // A document deleted, restored inside the folder, may still carry the mark.
    restorable(inner, JOHN_HOME + "/Private/Documents/b.txt", "t2");
    jcr.commit();

    assertEquals(1, trashStorage.normalizeRecycleBinItems());

    assertEquals(8L, jcr.find(folder.getId()).get(NodeTypeConstants.RECYCLE_BIN_SIZE));
    assertFalse(jcr.find(inner.getId()).has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
  }

  @Test
  void anItemAnotherMoverLeftInABinFolderGetsItsFactsAndTheBinFolderNone() throws Exception {
    Item binFolder = jcr.add(JOHN_BIN, "nt:unstructured");
    for (Item level = binFolder; !"Trash".equals(level.name); level = level.parent) {
      level.mixin(NodeTypeConstants.EXO_RECYCLE_BIN_FOLDER);
    }
    // The repository's own action may have marked a level as a deleted item.
    restorable(binFolder, JOHN_HOME, "t0");
    Item file = trashedFile(JOHN_BIN + "/report.docx", 10, JOHN_HOME + "/Private/Documents/report.docx", "t1");

    assertEquals(1, trashStorage.normalizeRecycleBinItems());

    assertTrue(jcr.find(file.getId()).has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    assertFalse(jcr.find(binFolder.getId()).has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
  }

  @Test
  void anItemThatAlreadyCarriesItsFactsIsNotWrittenAgain() throws Exception {
    Item file = trashedFile("/Trash/report.docx", 10, JOHN_HOME + "/Private/Documents/report.docx", "t1");
    file.mixin(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM).set(NodeTypeConstants.RECYCLE_BIN_SIZE, 99L);
    jcr.commit();

    assertEquals(0, trashStorage.normalizeRecycleBinItems());

    assertEquals(0, jcr.getSaves());
    assertEquals(99L, jcr.find(file.getId()).get(NodeTypeConstants.RECYCLE_BIN_SIZE));
  }

  @Test
  void eachItemIsASaveOfItsOwnAndASecondRunSavesNothing() throws Exception {
    trashedFile("/Trash/a.txt", 1, JOHN_HOME + "/Private/Documents/a.txt", "t1");
    trashedFile("/Trash/b.txt", 2, JOHN_HOME + "/Private/Documents/b.txt", "t2");

    assertEquals(2, trashStorage.normalizeRecycleBinItems());
    assertEquals(2, jcr.getSaves());

    assertEquals(0, trashStorage.normalizeRecycleBinItems());
    assertEquals(2, jcr.getSaves());
  }

  @Test
  void aFailureOnOneItemLeavesItUntouchedAndDoesNotStopTheOthers() throws Exception {
    Item first = trashedFile("/Trash/a.txt", 1, JOHN_HOME + "/Private/Documents/a.txt", "t1");
    Item second = trashedFile("/Trash/b.txt", 2, JOHN_HOME + "/Private/Documents/b.txt", "t2");
    jcr.failNextSave(new RepositoryException("The save failed"));

    assertEquals(1, trashStorage.normalizeRecycleBinItems());

    assertEquals(1, jcr.getRefreshes());
    assertFalse(jcr.find(first.getId()).has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    assertFalse(jcr.find(first.getId()).getProperties().containsKey(NodeTypeConstants.RECYCLE_BIN_SIZE));
    assertTrue(jcr.find(second.getId()).has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    assertEquals(2L, jcr.find(second.getId()).get(NodeTypeConstants.RECYCLE_BIN_SIZE));
    // The item left out is taken up by the next run.
    assertEquals(1, trashStorage.normalizeRecycleBinItems());
    assertTrue(jcr.find(first.getId()).has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
  }

  @Test
  void anItemAnotherRunHandledMeanwhileKeepsWhatThatRunWrote() throws Exception {
    Item file = trashedFile("/Trash/a.txt", 1, JOHN_HOME + "/Private/Documents/a.txt", "t1");
    jcr.failNextSave(new RepositoryException("The item was changed by another session"));
    jcr.onNextRefresh(() -> jcr.find(file.getId())
                               .mixin(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM)
                               .set(NodeTypeConstants.RECYCLE_BIN_SIZE, 77L));

    assertEquals(0, trashStorage.normalizeRecycleBinItems());

    assertEquals(77L, jcr.find(file.getId()).get(NodeTypeConstants.RECYCLE_BIN_SIZE));
    assertEquals(0, jcr.getSaves());
  }

  @Test
  void anItemRemovedMeanwhileDoesNotStopTheRun() throws Exception {
    Item first = trashedFile("/Trash/a.txt", 1, JOHN_HOME + "/Private/Documents/a.txt", "t1");
    Item second = trashedFile("/Trash/b.txt", 2, JOHN_HOME + "/Private/Documents/b.txt", "t2");
    jcr.failNextSave(new RepositoryException("The item was removed by another session"));
    jcr.onNextRefresh(() -> {
      Item removed = jcr.find(first.getId());
      removed.parent.getChildren().remove(removed);
    });

    assertEquals(1, trashStorage.normalizeRecycleBinItems());

    assertTrue(jcr.find(second.getId()).has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
  }

  @Test
  void theStatementIsKeptToTheTrashAndAcceptedByTheRepositoryParser() throws Exception {
    trashStorage.normalizeRecycleBinItems();

    assertEquals(List.of("SELECT * FROM exo:restoreLocation WHERE jcr:path LIKE '/Trash/%'"), jcr.getStatements());
    LocationFactory locationFactory = new LocationFactory(new NamespaceRegistryImpl());
    assertNotNull(JCRSQLQueryBuilder.createQuery(jcr.getStatements().get(0), locationFactory, new DefaultQueryNodeFactory()));
  }

  @Test
  void theSessionOfTheRunIsClosedEvenWhenTheTrashCannotBeRead() throws Exception {
    jcr.setQueryHandler(statement -> {
      throw new IllegalStateException("The index is not available");
    });

    assertThrows(IllegalStateException.class, () -> trashStorage.normalizeRecycleBinItems());

    verify(systemProvider).close();
  }

  private Item trashedFile(String path, long length, String restorePath, String trashId) {
    Item file = jcr.addFile(path, length);
    restorable(file, restorePath, trashId);
    jcr.commit();
    return file;
  }

  private void restorable(Item item, String restorePath, String trashId) {
    item.mixin(NodeTypeConstants.EXO_RESTORE_LOCATION)
        .set(NodeTypeConstants.RESTORE_PATH, restorePath)
        .set(NodeTypeConstants.RESTORE_WORKSPACE, WORKSPACE);
    if (trashId != null) {
      item.set(NodeTypeConstants.TRASH_ID, trashId);
    }
  }

  private void collectRestorable(Item item, List<Item> found) {
    for (Item child : item.getChildren()) {
      if (child.has(NodeTypeConstants.EXO_RESTORE_LOCATION)) {
        found.add(child);
      }
      collectRestorable(child, found);
    }
  }

  private static ValueParam valueParam(String name, String value) {
    ValueParam param = new ValueParam();
    param.setName(name);
    param.setValue(value);
    return param;
  }

}
