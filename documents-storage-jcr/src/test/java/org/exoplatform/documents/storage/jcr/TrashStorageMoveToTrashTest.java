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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.AccessControlException;
import java.util.Calendar;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.documents.service.RecycleBinService;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.FakeJcr.Item;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.core.ItemImpl;
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
 * The move of a node to the trash, on a workspace kept in memory: where the
 * node lands, what is recorded on it, and that the move, the facts and the
 * shortcuts are one save. What the repository itself decides is not seen here
 * and is checked on a running server: the access a bin folder's permissions
 * give, the node type rules of the trash home, and what the statements select.
 */
class TrashStorageMoveToTrashTest {

  private static final String               WORKSPACE      = "collaboration";

  private static final String               JOHN_HOME      = "/Users/j___/jo___/joh___/john";

  private static final String               JOHN_BIN       = "/Trash" + JOHN_HOME;

  private static final String               JOHN_ID        = "7";

  private static final String               MARKETING_ID   = "9";

  private static MockedStatic<CommonsUtils> commonsUtils;

  private FakeJcr                           jcr;

  private SessionImpl                       systemSession;

  private SessionImpl                       johnSession;

  private SessionProvider                   systemProvider;

  private ListenerService                   listenerService;

  private TrashStorageImpl                  trashStorage;

  private RecycleBinService                 recycleBinService = mock(RecycleBinService.class);

  @BeforeAll
  static void mockContainerLookups() {
    commonsUtils = mockStatic(CommonsUtils.class);
  }

  @AfterAll
  static void releaseContainerLookups() {
    commonsUtils.close();
  }

  @BeforeEach
  void setUp() throws Exception {
    jcr = new FakeJcr(WORKSPACE);
    jcr.add("/Trash", "nt:unstructured");
    jcr.add(JOHN_HOME + "/Private", "nt:unstructured").mixin("exo:privilegeable").permission("john", "read").permission("john", "add_node");
    jcr.add(JOHN_HOME + "/Private/Documents", "nt:folder").mixin("mix:referenceable");
    jcr.add("/Groups/spaces/marketing/Documents", "nt:unstructured");
    jcr.commit();
    systemSession = jcr.session("__system");
    johnSession = jcr.session("john");

    ManageableRepository repository = mock(ManageableRepository.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    systemProvider = mock(SessionProvider.class);
    lenient().when(systemProvider.getSession(anyString(), any())).thenReturn(systemSession);
    listenerService = mock(ListenerService.class);

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
    lenient().when(identityManager.getIdentity(JOHN_ID)).thenReturn(john);
    lenient().when(identityManager.getOrCreateSpaceIdentity("marketing")).thenReturn(marketing);
    lenient().when(identityManager.getIdentity(MARKETING_ID)).thenReturn(marketing);

    SpaceService spaceService = mock(SpaceService.class);
    Space space = new Space();
    space.setPrettyName("marketing");
    space.setGroupId("/spaces/marketing");
    lenient().when(spaceService.getSpaceByGroupId("/spaces/marketing")).thenReturn(space);
    lenient().when(spaceService.getSpaceByPrettyName("marketing")).thenReturn(space);

    InitParams params = new InitParams();
    params.addParameter(valueParam("trashWorkspace", WORKSPACE));
    params.addParameter(valueParam("trashHomeNodePath", "/Trash"));
    trashStorage = new TrashStorageImpl(repositoryService,
                                        mock(SessionProviderService.class),
                                        listenerService,
                                        nodeHierarchyCreator,
                                        identityManager,
                                        spaceService,
                                        params) {
      @Override
      SessionProvider createSystemProvider() {
        return systemProvider;
      }

      @Override
      RecycleBinService getRecycleBinService() {
        return recycleBinService;
      }
    };
  }

  @Test
  void aDeletionFromADriveIsReportedWithItsDriveAndItsSizeOnceItIsSaved() throws Exception {
    jcr.add(JOHN_HOME + "/Private/Documents/archive", "nt:folder");
    jcr.addFile(JOHN_HOME + "/Private/Documents/archive/a.txt", 5);
    jcr.addFile(JOHN_HOME + "/Private/Documents/archive/b.txt", 7);
    Item file = jcr.addFile("/Groups/spaces/marketing/Documents/plan.txt", 3);
    int[] savesAtReport = new int[1];
    org.mockito.Mockito.doAnswer(invocation -> {
      savesAtReport[0] = jcr.getSaves();
      return null;
    }).when(recycleBinService).itemTrashed(7L, 12L);

    trashStorage.moveToTrash(jcr.node(johnSession, jcr.at(JOHN_HOME + "/Private/Documents/archive")), mock(SessionProvider.class), "john");
    int savesOfTheFirst = jcr.getSaves();
    trashStorage.moveToTrash(jcr.node(johnSession, file), mock(SessionProvider.class), "john");

    verify(recycleBinService).itemTrashed(7L, 12L);
    verify(recycleBinService).itemTrashed(9L, 3L);
    assertEquals(savesOfTheFirst, savesAtReport[0]);
  }

  @Test
  void aDeletionFromNoDriveAndOneThatIsNotSavedAreNotReported() throws Exception {
    Item form = jcr.addFile("/Application Data/form.txt", 4);
    trashStorage.moveToTrash(jcr.node(johnSession, form), mock(SessionProvider.class), "john");
    Item first = jcr.addFile(JOHN_HOME + "/Private/Documents/first.txt", 1);
    trashStorage.moveToTrash(jcr.node(johnSession, first), mock(SessionProvider.class), "john");
    org.mockito.Mockito.clearInvocations(recycleBinService);
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);
    Node node = jcr.node(johnSession, file);
    jcr.failNextSave(new RepositoryException("save refused"));

    assertThrows(RepositoryException.class, () -> trashStorage.moveToTrash(node, mock(SessionProvider.class), "john"));

    verify(recycleBinService, never()).itemTrashed(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void aDeletionStandsWhenItsReportFailsOrFindsNoService() throws Exception {
    Item first = jcr.addFile(JOHN_HOME + "/Private/Documents/first.txt", 1);
    Item second = jcr.addFile(JOHN_HOME + "/Private/Documents/second.txt", 1);
    org.mockito.Mockito.doThrow(new IllegalStateException("The database refuses the statement")).when(recycleBinService).itemTrashed(7L, 1L);

    trashStorage.moveToTrash(jcr.node(johnSession, first), mock(SessionProvider.class), "john");
    recycleBinService = null;
    trashStorage.moveToTrash(jcr.node(johnSession, second), mock(SessionProvider.class), "john");

    assertNotNull(jcr.at(JOHN_BIN + "/first.txt"));
    assertNotNull(jcr.at(JOHN_BIN + "/second.txt"));
    assertEquals(0, jcr.getRefreshes());
    verify(listenerService, org.mockito.Mockito.times(2)).broadcast(eq(TrashStorage.FILE_REMOVE_ACTIVITY), any(), any());
  }

  @Test
  void aDocumentOfAPersonalDriveGoesToItsBinFolderWithItsFacts() throws Exception {
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);
    file.set("exo:title", "Report.DOCX");
    jcr.commit();
    String documentsId = jcr.at(JOHN_HOME + "/Private/Documents").getId();
    Calendar before = Calendar.getInstance();

    String trashId = trashStorage.moveToTrash(jcr.node(johnSession, file), mock(SessionProvider.class), "john");

    Item trashed = jcr.at(JOHN_BIN + "/report.docx");
    assertNotNull(trashed);
    assertEquals(file.getId(), trashed.getId());
    assertNull(jcr.at(JOHN_HOME + "/Private/Documents/report.docx"));
    assertTrue(trashed.has(NodeTypeConstants.EXO_RESTORE_LOCATION));
    assertTrue(trashed.has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    assertEquals(JOHN_HOME + "/Private/Documents/report.docx", trashed.get(NodeTypeConstants.RESTORE_PATH));
    assertEquals(WORKSPACE, trashed.get(NodeTypeConstants.RESTORE_WORKSPACE));
    assertEquals(trashId, trashed.get(NodeTypeConstants.TRASH_ID));
    assertEquals(JOHN_ID, trashed.get(NodeTypeConstants.RECYCLE_BIN_OWNER));
    assertEquals(documentsId, trashed.get(NodeTypeConstants.RESTORE_PARENT_ID));
    assertEquals("john", trashed.get(NodeTypeConstants.DELETED_BY));
    assertEquals(10L, trashed.get(NodeTypeConstants.RECYCLE_BIN_SIZE));
    assertEquals("report.docx", trashed.get(NodeTypeConstants.RECYCLE_BIN_NAME));
    assertFalse(((Calendar) trashed.get(NodeTypeConstants.DELETED_DATE)).before(before));
    assertFalse(trashed.getProperties().containsKey(NodeTypeConstants.RECYCLE_BIN_OF));
    // The document carried no permissions of its own: the ones it inherited are kept.
    assertArrayEquals(new String[] { "john read", "john add_node" },
                      (String[]) trashed.get(NodeTypeConstants.RECYCLE_BIN_PERMISSIONS));
  }

  @Test
  void theMoveAndTheFactsAreOneSaveOfASessionMove() throws Exception {
    // A first delete creates the bin folder, each level in a save of its own.
    Item first = jcr.addFile(JOHN_HOME + "/Private/Documents/first.txt", 1);
    trashStorage.moveToTrash(jcr.node(johnSession, first), mock(SessionProvider.class), "john");
    int savesBefore = jcr.getSaves();
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);

    trashStorage.moveToTrash(jcr.node(johnSession, file), mock(SessionProvider.class), "john");

    assertEquals(savesBefore + 1, jcr.getSaves());
    assertEquals(JOHN_HOME + "/Private/Documents/report.docx -> " + JOHN_BIN + "/report.docx", jcr.getMoves().get(1));
    verify(systemSession.getWorkspace(), never()).move(anyString(), anyString());
    // Nothing is written through the session of the user who deletes.
    assertEquals(java.util.Set.of("__system"), jcr.getWriters());
    verify(systemProvider, org.mockito.Mockito.times(2)).close();
  }

  @Test
  void aFailedSaveLeavesTheDocumentWhereItWas() throws Exception {
    Item first = jcr.addFile(JOHN_HOME + "/Private/Documents/first.txt", 1);
    trashStorage.moveToTrash(jcr.node(johnSession, first), mock(SessionProvider.class), "john");
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);
    Node node = jcr.node(johnSession, file);
    RepositoryException failure = new RepositoryException("save refused");
    jcr.failNextSave(failure);

    assertSame(failure, assertThrows(RepositoryException.class, () -> trashStorage.moveToTrash(node, mock(SessionProvider.class), "john")));

    Item untouched = jcr.at(JOHN_HOME + "/Private/Documents/report.docx");
    assertNotNull(untouched);
    assertTrue(untouched.getMixins().isEmpty());
    assertTrue(untouched.getProperties().isEmpty());
    assertNull(jcr.at(JOHN_BIN + "/report.docx"));
    assertEquals(1, jcr.getRefreshes());
    verify(listenerService, never()).broadcast(eq(TrashStorage.FILE_REMOVE_ACTIVITY), any(), argThat(isAt(JOHN_BIN + "/report.docx")));
  }

  @Test
  void theListenersAreToldOnceTheMoveIsSaved() throws Exception {
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);
    int[] savesAtBroadcast = new int[1];
    org.mockito.Mockito.doAnswer(invocation -> {
      savesAtBroadcast[0] = jcr.getSaves();
      return null;
    }).when(listenerService).broadcast(eq(TrashStorage.FILE_REMOVE_ACTIVITY), isNull(), any());

    trashStorage.moveToTrash(jcr.node(johnSession, file), mock(SessionProvider.class), "john");

    verify(listenerService).broadcast(eq(TrashStorage.FILE_REMOVE_ACTIVITY), isNull(), argThat(isAt(JOHN_BIN + "/report.docx")));
    assertEquals(jcr.getSaves(), savesAtBroadcast[0]);
  }

  @Test
  void aFailingListenerDoesNotUndoTheMove() throws Exception {
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);
    doThrow(new IllegalStateException("listener failed")).when(listenerService).broadcast(anyString(), any(), any());

    String trashId = trashStorage.moveToTrash(jcr.node(johnSession, file), mock(SessionProvider.class), "john");

    assertNotNull(trashId);
    assertNotNull(jcr.at(JOHN_BIN + "/report.docx"));
  }

  @Test
  void theBinFolderIsMarkedAndItsFirstLevelIsReadableByTheSystemOnly() throws Exception {
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);

    trashStorage.moveToTrash(jcr.node(johnSession, file), mock(SessionProvider.class), "john");

    Item firstLevel = jcr.at("/Trash/Users");
    assertTrue(firstLevel.has(NodeTypeConstants.EXO_RECYCLE_BIN_FOLDER));
    assertTrue(firstLevel.has(NodeTypeConstants.EXO_PRIVILEGEABLE));
    assertEquals(PermissionType.ALL.length, firstLevel.getPermissions().size());
    for (String[] permission : firstLevel.getPermissions()) {
      assertEquals("__system", permission[0]);
    }
    for (String level : List.of("/Trash/Users/j___", "/Trash/Users/j___/jo___", "/Trash/Users/j___/jo___/joh___", JOHN_BIN)) {
      Item below = jcr.at(level);
      assertTrue(below.has(NodeTypeConstants.EXO_RECYCLE_BIN_FOLDER), level);
      assertFalse(below.has(NodeTypeConstants.EXO_PRIVILEGEABLE), level);
      assertFalse(below.has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM), level);
      assertEquals("nt:unstructured", below.primaryType, level);
    }
  }

  @Test
  void aDocumentOfASpaceDriveGoesToTheSpaceBinFolder() throws Exception {
    Item file = jcr.addFile("/Groups/spaces/marketing/Documents/plan.txt", 4);

    trashStorage.moveToTrash(jcr.node(johnSession, file), mock(SessionProvider.class), "john");

    Item trashed = jcr.at("/Trash/Groups/spaces/marketing/plan.txt");
    assertNotNull(trashed);
    assertEquals(MARKETING_ID, trashed.get(NodeTypeConstants.RECYCLE_BIN_OWNER));
    assertTrue(jcr.at("/Trash/Groups").has(NodeTypeConstants.EXO_PRIVILEGEABLE));
    assertFalse(trashed.getProperties().containsKey(NodeTypeConstants.RESTORE_PARENT_ID));
  }

  @Test
  void aDocumentOfNoDriveStaysUnderTheTrashHomeAndBelongsToNoBin() throws Exception {
    Item file = jcr.addFile("/sites/intranet/page.html", 3);

    trashStorage.moveToTrash(jcr.node(johnSession, file), mock(SessionProvider.class), "john");

    Item trashed = jcr.at("/Trash/page.html");
    assertNotNull(trashed);
    assertTrue(trashed.has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    assertFalse(trashed.getProperties().containsKey(NodeTypeConstants.RECYCLE_BIN_OWNER));
    assertEquals("john", trashed.get(NodeTypeConstants.DELETED_BY));
    assertNotNull(trashed.get(NodeTypeConstants.DELETED_DATE));
    assertEquals(1, jcr.getSaves());
  }

  @Test
  void aFolderIsSizedByTheFilesItHolds() throws Exception {
    Item folder = jcr.add(JOHN_HOME + "/Private/Documents/archive", "nt:folder");
    jcr.addFile(JOHN_HOME + "/Private/Documents/archive/a.txt", 5);
    jcr.addFile(JOHN_HOME + "/Private/Documents/archive/sub/b.txt", 7);
    jcr.add(JOHN_HOME + "/Private/Documents/archive/link", "exo:symlink").set("exo:uuid", "elsewhere");
    jcr.commit();

    trashStorage.moveToTrash(jcr.node(johnSession, jcr.find(folder.getId())), mock(SessionProvider.class), "john");

    assertEquals(12L, jcr.at(JOHN_BIN + "/archive").get(NodeTypeConstants.RECYCLE_BIN_SIZE));
  }

  @Test
  void aDocumentWithPermissionsOfItsOwnKeepsNoSnapshot() throws Exception {
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/shared.txt", 2);
    file.mixin("exo:privilegeable").permission("mary", "read");
    jcr.commit();

    trashStorage.moveToTrash(jcr.node(johnSession, file), mock(SessionProvider.class), "john");

    Item trashed = jcr.at(JOHN_BIN + "/shared.txt");
    assertFalse(trashed.getProperties().containsKey(NodeTypeConstants.RECYCLE_BIN_PERMISSIONS));
    assertEquals(1, trashed.getPermissions().size());
  }

  @Test
  void theShortcutsToADeletedDocumentAreTrashedWithItInTheSameSave() throws Exception {
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);
    Item shortcut = jcr.add("/Groups/spaces/marketing/Documents/report.docx", "exo:symlink")
                       .set("exo:uuid", file.getId())
                       .set("exo:workspace", WORKSPACE);
    Item alreadyTrashed = jcr.add("/Trash/old-shortcut", "exo:symlink").set("exo:uuid", file.getId()).set("exo:workspace", WORKSPACE);
    jcr.commit();
    Item first = jcr.addFile(JOHN_HOME + "/Private/Documents/first.txt", 1);
    trashStorage.moveToTrash(jcr.node(johnSession, first), mock(SessionProvider.class), "john");
    int savesBefore = jcr.getSaves();

    String trashId = trashStorage.moveToTrash(jcr.node(johnSession, jcr.find(file.getId())), mock(SessionProvider.class), "john");

    assertEquals(savesBefore + 1, jcr.getSaves());
    Item trashedShortcut = jcr.find(shortcut.getId());
    assertEquals(JOHN_BIN + "/report.docx[2]", jcr.pathOf(trashedShortcut));
    assertEquals(trashId, trashedShortcut.get(NodeTypeConstants.RECYCLE_BIN_OF));
    assertTrue(trashedShortcut.has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    assertEquals("/Groups/spaces/marketing/Documents/report.docx", trashedShortcut.get(NodeTypeConstants.RESTORE_PATH));
    assertNotEquals(trashId, trashedShortcut.get(NodeTypeConstants.TRASH_ID));
    assertEquals("john", trashedShortcut.get(NodeTypeConstants.DELETED_BY));
    // A shortcut trashed with its document is in no bin and has no size.
    assertFalse(trashedShortcut.getProperties().containsKey(NodeTypeConstants.RECYCLE_BIN_OWNER));
    assertFalse(trashedShortcut.getProperties().containsKey(NodeTypeConstants.RECYCLE_BIN_SIZE));
    assertEquals("/Trash/old-shortcut", jcr.pathOf(jcr.find(alreadyTrashed.getId())));
    assertFalse(jcr.find(alreadyTrashed.getId()).has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    verify(listenerService).broadcast(eq(TrashStorage.FILE_REMOVE_ACTIVITY), isNull(), argThat(isAt(JOHN_BIN + "/report.docx[2]")));
  }

  @Test
  void aFailedSaveLeavesTheShortcutsInPlaceToo() throws Exception {
    Item first = jcr.addFile(JOHN_HOME + "/Private/Documents/first.txt", 1);
    trashStorage.moveToTrash(jcr.node(johnSession, first), mock(SessionProvider.class), "john");
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);
    Item shortcut = jcr.add("/Groups/spaces/marketing/Documents/report.docx", "exo:symlink")
                       .set("exo:uuid", file.getId())
                       .set("exo:workspace", WORKSPACE);
    jcr.commit();
    Node node = jcr.node(johnSession, jcr.find(file.getId()));
    jcr.failNextSave(new RepositoryException("save refused"));

    assertThrows(RepositoryException.class, () -> trashStorage.moveToTrash(node, mock(SessionProvider.class), "john"));

    assertEquals("/Groups/spaces/marketing/Documents/report.docx", jcr.pathOf(jcr.find(shortcut.getId())));
    assertEquals(JOHN_HOME + "/Private/Documents/report.docx", jcr.pathOf(jcr.find(file.getId())));
  }

  @Test
  void theShortcutsToTheDocumentsOfADeletedFolderFollowItButNotTheOnesItHolds() throws Exception {
    Item folder = jcr.add(JOHN_HOME + "/Private/Documents/archive", "nt:folder");
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/archive/a.txt", 5);
    Item outside = jcr.add("/Groups/spaces/marketing/Documents/a.txt", "exo:symlink").set("exo:uuid", file.getId()).set("exo:workspace", WORKSPACE);
    Item inside = jcr.add(JOHN_HOME + "/Private/Documents/archive/a-link", "exo:symlink").set("exo:uuid", file.getId()).set("exo:workspace", WORKSPACE);
    jcr.commit();

    String trashId = trashStorage.moveToTrash(jcr.node(johnSession, jcr.find(folder.getId())), mock(SessionProvider.class), "john");

    assertEquals(JOHN_BIN + "/a.txt", jcr.pathOf(jcr.find(outside.getId())));
    assertEquals(trashId, jcr.find(outside.getId()).get(NodeTypeConstants.RECYCLE_BIN_OF));
    assertEquals(JOHN_BIN + "/archive/a-link", jcr.pathOf(jcr.find(inside.getId())));
    assertFalse(jcr.find(inside.getId()).has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    // A document deleted with its folder is not an item of its own.
    assertFalse(jcr.find(file.getId()).has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
  }

  @Test
  void aDeletedShortcutIsAnItemOfItsOwnAndLooksForNoShortcut() throws Exception {
    Item shortcut = jcr.add(JOHN_HOME + "/Private/Documents/a-link", "exo:symlink").set("exo:uuid", "target").set("exo:workspace", WORKSPACE);
    jcr.commit();

    trashStorage.moveToTrash(jcr.node(johnSession, shortcut), mock(SessionProvider.class), "john");

    Item trashed = jcr.at(JOHN_BIN + "/a-link");
    assertEquals(JOHN_ID, trashed.get(NodeTypeConstants.RECYCLE_BIN_OWNER));
    assertEquals(0L, trashed.get(NodeTypeConstants.RECYCLE_BIN_SIZE));
    assertTrue(jcr.getStatements().isEmpty());
  }

  @Test
  void marksLeftByAnEarlierStayInTheTrashAreWrittenAgain() throws Exception {
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);
    file.mixin(NodeTypeConstants.EXO_RESTORE_LOCATION, NodeTypeConstants.EXO_RECYCLE_BIN_ITEM)
        .set(NodeTypeConstants.RESTORE_PATH, "/somewhere/else")
        .set(NodeTypeConstants.TRASH_ID, "old-trash-id")
        .set(NodeTypeConstants.RECYCLE_BIN_OWNER, "99")
        .set(NodeTypeConstants.RECYCLE_BIN_OF, "another-item")
        .set(NodeTypeConstants.DELETED_BY, "mary");
    jcr.commit();

    String trashId = trashStorage.moveToTrash(jcr.node(johnSession, file), mock(SessionProvider.class), null);

    Item trashed = jcr.at(JOHN_BIN + "/report.docx");
    assertNotNull(trashId);
    assertEquals(trashId, trashed.get(NodeTypeConstants.TRASH_ID));
    assertEquals(JOHN_HOME + "/Private/Documents/report.docx", trashed.get(NodeTypeConstants.RESTORE_PATH));
    assertEquals(JOHN_ID, trashed.get(NodeTypeConstants.RECYCLE_BIN_OWNER));
    assertFalse(trashed.getProperties().containsKey(NodeTypeConstants.RECYCLE_BIN_OF));
    // No deleter was given: the user of the node's session is recorded.
    assertEquals("john", trashed.get(NodeTypeConstants.DELETED_BY));
  }

  @Test
  void marksCarriedByADescendantAreRemoved() throws Exception {
    Item folder = jcr.add(JOHN_HOME + "/Private/Documents/archive", "nt:folder");
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/archive/a.txt", 5);
    file.mixin(NodeTypeConstants.EXO_RESTORE_LOCATION, NodeTypeConstants.EXO_RECYCLE_BIN_ITEM)
        .set(NodeTypeConstants.RESTORE_PATH, "/somewhere/else")
        .set(NodeTypeConstants.RECYCLE_BIN_OWNER, "99");
    jcr.commit();

    trashStorage.moveToTrash(jcr.node(johnSession, jcr.find(folder.getId())), mock(SessionProvider.class), "john");

    Item descendant = jcr.at(JOHN_BIN + "/archive/a.txt");
    assertFalse(descendant.has(NodeTypeConstants.EXO_RESTORE_LOCATION));
    assertFalse(descendant.has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    assertFalse(descendant.getProperties().containsKey(NodeTypeConstants.RESTORE_PATH));
    assertFalse(descendant.getProperties().containsKey(NodeTypeConstants.RECYCLE_BIN_OWNER));
  }

  @Test
  void aSystemSessionRecordsNoDeleter() throws Exception {
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);

    trashStorage.moveToTrash(jcr.node(systemSession, file), mock(SessionProvider.class), null);

    assertFalse(jcr.at(JOHN_BIN + "/report.docx").getProperties().containsKey(NodeTypeConstants.DELETED_BY));
  }

  @Test
  void twoDocumentsOfTheSameNameCoexistInOneBinFolder() throws Exception {
    Item first = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);
    String firstTrashId = trashStorage.moveToTrash(jcr.node(johnSession, first), mock(SessionProvider.class), "john");
    Item second = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 20);

    String secondTrashId = trashStorage.moveToTrash(jcr.node(johnSession, second), mock(SessionProvider.class), "john");

    assertEquals(firstTrashId, jcr.at(JOHN_BIN + "/report.docx").get(NodeTypeConstants.TRASH_ID));
    assertEquals(10L, jcr.at(JOHN_BIN + "/report.docx").get(NodeTypeConstants.RECYCLE_BIN_SIZE));
    assertEquals(secondTrashId, jcr.at(JOHN_BIN + "/report.docx[2]").get(NodeTypeConstants.TRASH_ID));
    assertEquals(20L, jcr.at(JOHN_BIN + "/report.docx[2]").get(NodeTypeConstants.RECYCLE_BIN_SIZE));
  }

  @Test
  void aNodeAlreadyInTheTrashIsLeftAsItIs() throws Exception {
    Item trashed = jcr.addFile("/Trash/report.docx", 10);
    trashed.mixin(NodeTypeConstants.EXO_RESTORE_LOCATION).set(NodeTypeConstants.TRASH_ID, "its-trash-id");
    jcr.commit();

    assertNull(trashStorage.moveToTrash(jcr.node(johnSession, trashed), mock(SessionProvider.class), "john"));

    assertTrue(jcr.getMoves().isEmpty());
    assertEquals("its-trash-id", jcr.at("/Trash/report.docx").get(NodeTypeConstants.TRASH_ID));
  }

  @Test
  void theRemovalActionsRunOnlyOnceTheUserIsAllowedToRemove() throws Exception {
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);
    Node node = jcr.node(johnSession, file);

    trashStorage.moveToTrash(node, mock(SessionProvider.class), "john");

    InOrder order = inOrder(johnSession, johnSession.getActionHandler());
    order.verify(johnSession).checkPermission(JOHN_HOME + "/Private/Documents/report.docx", PermissionType.REMOVE);
    order.verify(johnSession.getActionHandler()).preRemoveItem((ItemImpl) node);
  }

  @Test
  void aUserNotAllowedToRemoveMovesNothingAndRunsNoRemovalAction() throws Exception {
    Item file = jcr.addFile("/Groups/spaces/marketing/Documents/plan.txt", 4);
    Node node = jcr.node(johnSession, file);
    doThrow(new AccessControlException("Permission denied")).when(johnSession)
                                                            .checkPermission("/Groups/spaces/marketing/Documents/plan.txt", PermissionType.REMOVE);

    assertThrows(AccessControlException.class, () -> trashStorage.moveToTrash(node, mock(SessionProvider.class), "john"));

    verify(johnSession.getActionHandler(), never()).preRemoveItem(any());
    assertTrue(jcr.getMoves().isEmpty());
    assertNotNull(jcr.at("/Groups/spaces/marketing/Documents/plan.txt"));
  }

  @Test
  void aUserClearsTheirOwnPrivateSpaceDespiteAMissingRemoveRight() throws Exception {
    // The node was created in john's Private space on his behalf, with permissions that
    // do not let him remove it.
    Item file = jcr.addFile(JOHN_HOME + "/Private/Documents/report.docx", 10);
    Node node = jcr.node(johnSession, file);
    doThrow(new AccessControlException("Permission denied")).when(johnSession)
                                                            .checkPermission(JOHN_HOME + "/Private/Documents/report.docx", PermissionType.REMOVE);

    String trashId = trashStorage.moveToTrash(node, mock(SessionProvider.class), "john");

    assertNotNull(trashId);
    assertNotNull(jcr.at(JOHN_BIN + "/report.docx"));
    verify(johnSession.getActionHandler()).preRemoveItem((ItemImpl) node);
  }

  @Test
  void theOverloadsWithoutADeleterRecordTheUserOfTheSession() throws Exception {
    Item first = jcr.addFile(JOHN_HOME + "/Private/Documents/first.txt", 1);
    Item second = jcr.addFile(JOHN_HOME + "/Private/Documents/second.txt", 1);

    trashStorage.moveToTrash(jcr.node(johnSession, first), mock(SessionProvider.class));
    trashStorage.moveToTrash(jcr.node(johnSession, second), mock(SessionProvider.class), 0);

    assertEquals("john", jcr.at(JOHN_BIN + "/first.txt").get(NodeTypeConstants.DELETED_BY));
    assertEquals("john", jcr.at(JOHN_BIN + "/second.txt").get(NodeTypeConstants.DELETED_BY));
  }

  @Test
  void theStatementsOfADeleteAreAcceptedByTheRepositoryParser() throws Exception {
    Item folder = jcr.add(JOHN_HOME + "/Private/Documents/archive", "nt:folder");
    jcr.addFile(JOHN_HOME + "/Private/Documents/archive/a.txt", 5);
    jcr.commit();

    trashStorage.moveToTrash(jcr.node(johnSession, jcr.find(folder.getId())), mock(SessionProvider.class), "john");

    // One lookup of shortcuts for the folder and one for the file it holds.
    assertEquals(2, jcr.getStatements().size());
    LocationFactory locationFactory = new LocationFactory(new NamespaceRegistryImpl());
    for (String statement : jcr.getStatements()) {
      assertNotNull(JCRSQLQueryBuilder.createQuery(statement, locationFactory, new DefaultQueryNodeFactory()), statement);
    }
  }

  @Test
  void aNodeOfAnotherWorkspaceIsCopiedToTheTrashWithItsFactsAndTheListenersToldOnceItIsSaved() throws Exception {
    FakeJcr other = new FakeJcr("dms-system");
    Item file = other.addFile("/templates/form.txt", 4);
    jcr.linkWorkspace(other);
    SessionImpl otherSystemSession = other.session("__system");
    when(systemProvider.getSession(eq("dms-system"), any())).thenReturn(otherSystemSession);
    int[] savesAtBroadcast = new int[] { -1 };
    org.mockito.Mockito.doAnswer(invocation -> {
      savesAtBroadcast[0] = jcr.getSaves();
      return null;
    }).when(listenerService).broadcast(eq(TrashStorage.FILE_REMOVE_ACTIVITY), isNull(), any());

    String trashId = trashStorage.moveToTrash(other.node(other.session("john"), file), mock(SessionProvider.class), "john");

    Item trashed = jcr.at("/Trash/form.txt");
    assertNotNull(trashed);
    assertEquals(file.getId(), trashed.getId());
    assertNull(other.at("/templates/form.txt"));
    assertEquals(trashId, trashed.get(NodeTypeConstants.TRASH_ID));
    assertEquals("/templates/form.txt", trashed.get(NodeTypeConstants.RESTORE_PATH));
    assertEquals("dms-system", trashed.get(NodeTypeConstants.RESTORE_WORKSPACE));
    assertTrue(trashed.has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    assertEquals("john", trashed.get(NodeTypeConstants.DELETED_BY));
    assertEquals(4L, trashed.get(NodeTypeConstants.RECYCLE_BIN_SIZE));
    assertFalse(trashed.getProperties().containsKey(NodeTypeConstants.RECYCLE_BIN_OWNER));
    verify(listenerService).broadcast(eq(TrashStorage.FILE_REMOVE_ACTIVITY), isNull(), argThat(isAt("/Trash/form.txt")));
    assertEquals(1, jcr.getSaves());
    assertEquals(1, savesAtBroadcast[0]);
  }

  @Test
  void aNodeOfAnotherWorkspaceDeletedFromTheSamePathAsADriveIsReportedToo() throws Exception {
    FakeJcr other = new FakeJcr("dms-system");
    Item file = other.addFile(JOHN_HOME + "/Private/Documents/form.txt", 4);
    jcr.linkWorkspace(other);
    SessionImpl otherSystemSession = other.session("__system");
    when(systemProvider.getSession(eq("dms-system"), any())).thenReturn(otherSystemSession);

    trashStorage.moveToTrash(other.node(other.session("john"), file), mock(SessionProvider.class), "john");

    assertNotNull(jcr.at(JOHN_BIN + "/form.txt"));
    verify(recycleBinService).itemTrashed(7L, 4L);
  }

  @Test
  void aNodeOfAnotherWorkspaceWhoseFactsCannotBeSavedStaysCopiedAndTellsNoListener() throws Exception {
    FakeJcr other = new FakeJcr("dms-system");
    Item file = other.addFile("/templates/form.txt", 4);
    jcr.linkWorkspace(other);
    SessionImpl otherSystemSession = other.session("__system");
    when(systemProvider.getSession(eq("dms-system"), any())).thenReturn(otherSystemSession);
    jcr.failNextSave(new RepositoryException("The save failed"));

    assertThrows(RepositoryException.class,
                 () -> trashStorage.moveToTrash(other.node(other.session("john"), file), mock(SessionProvider.class), "john"));

    // The copy is persisted by the repository before the save: it stays in
    // the trash, without the facts the save was to write.
    Item copied = jcr.at("/Trash/form.txt");
    assertNotNull(copied);
    assertFalse(copied.has(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM));
    verify(listenerService, never()).broadcast(anyString(), any(), any());
  }

  private org.mockito.ArgumentMatcher<Object> isAt(String path) {
    return data -> {
      try {
        return data instanceof Node node && path.equals(node.getPath());
      } catch (RepositoryException e) {
        return false;
      }
    };
  }

  private static ValueParam valueParam(String name, String value) {
    ValueParam valueParam = new ValueParam();
    valueParam.setName(name);
    valueParam.setValue(value);
    return valueParam;
  }

}
