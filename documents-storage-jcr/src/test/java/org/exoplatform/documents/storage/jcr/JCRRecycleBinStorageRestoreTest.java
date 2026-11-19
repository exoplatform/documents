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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.InvalidItemStateException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.constant.RecycleBinError;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.FakeJcr.Item;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.NamespaceRegistryImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.query.DefaultQueryNodeFactory;
import org.exoplatform.services.jcr.impl.core.query.sql.JCRSQLQueryBuilder;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * The restore of an item of a Recycle Bin, on a workspace kept in memory:
 * where the item goes, under which name, with which permissions, what comes
 * back with it and what a failure leaves. The repository checks no right
 * here: a test says which folder refuses which user, and reads which check
 * was asked, as whom. What the repository itself decides is checked on a
 * running server.
 */
class JCRRecycleBinStorageRestoreTest {

  private static final String               WORKSPACE      = "collaboration";

  private static final String               JOHN_HOME      = "/Users/j___/jo___/joh___/john";

  private static final String               JOHN_ROOT      = JOHN_HOME + "/Private";

  private static final String               JOHN_FOLDER    = JOHN_ROOT + "/Reports";

  private static final String               JOHN_BIN       = "/Trash/Users/j___/jo___/joh___/john";

  private static final String               SALES_ROOT     = "/Groups/spaces/sales/Documents";

  private static final String               SALES_BIN      = "/Trash/Groups/spaces/sales";

  private static final String               ADMINISTRATORS = "*:/platform/administrators";

  private static final String               ID_1           = "11111111-1111-1111-1111-111111111111";

  private static final String               ID_2           = "22222222-2222-2222-2222-222222222222";

  private static final String               ID_3           = "33333333-3333-3333-3333-333333333333";

  private static final Identity             MARY           = new Identity("mary");

  private static MockedStatic<CommonsUtils> commonsUtils;

  private FakeJcr                           jcr;

  private FakeJcr                           archive;

  private SessionProvider                   systemProvider;

  private IdentityManager                   identityManager;

  private SpaceService                      spaceService;

  private Item                              johnFolder;

  private JCRRecycleBinStorage              storage;

  @BeforeAll
  static void mockContainerLookups() {
    commonsUtils = mockStatic(CommonsUtils.class);
  }

  @AfterAll
  static void releaseContainerLookups() {
    commonsUtils.close();
  }

  /**
   * The marks and the permissions of a restored node are given back before it
   * moves, never after in the same save: the cache of the repository would
   * then keep what the node holds at its paths in the trash.
   */
  @AfterEach
  void noMarkOrPermissionIsChangedAfterAMoveInTheSameSave() {
    assertEquals(List.of(), jcr.getMarksChangedAfterAMove());
  }

  @BeforeEach
  void setUp() throws Exception {
    jcr = new FakeJcr(WORKSPACE);
    jcr.add("/Trash", "nt:unstructured");
    johnFolder = jcr.add(JOHN_FOLDER, "nt:folder");
    jcr.add(SALES_ROOT, "nt:unstructured");
    jcr.setQueryHandler(this::answer);
    SessionImpl systemSession = jcr.session("__system");
    archive = new FakeJcr("archive");
    archive.add("/Library", "nt:folder");
    archive.linkWorkspace(jcr);
    SessionImpl archiveSession = archive.session("__system");

    RepositoryService repositoryService = mock(RepositoryService.class);
    when(repositoryService.getCurrentRepository()).thenReturn(mock(ManageableRepository.class));
    systemProvider = mock(SessionProvider.class);
    lenient().when(systemProvider.getSession(eq(WORKSPACE), any())).thenReturn(systemSession);
    lenient().when(systemProvider.getSession(eq("archive"), any())).thenReturn(archiveSession);
    TrashStorage trashStorage = mock(TrashStorage.class);
    lenient().when(trashStorage.getTrashHomeNode()).thenAnswer(invocation -> jcr.node(systemSession, jcr.at("/Trash")));

    NodeHierarchyCreator nodeHierarchyCreator = mock(NodeHierarchyCreator.class);
    lenient().when(nodeHierarchyCreator.getJcrPath("groupsPath")).thenReturn("/Groups");
    lenient().when(nodeHierarchyCreator.getUserNode(any(), eq("john"))).thenAnswer(invocation -> jcr.node(systemSession, jcr.at(JOHN_HOME)));

    identityManager = mock(IdentityManager.class);
    org.exoplatform.social.core.identity.model.Identity john = identity("organization", "john", "7");
    org.exoplatform.social.core.identity.model.Identity sales = identity("space", "sales", "20");
    lenient().when(identityManager.getIdentity("7")).thenReturn(john);
    lenient().when(identityManager.getIdentity("20")).thenReturn(sales);
    lenient().when(identityManager.getOrCreateUserIdentity("john")).thenReturn(john);
    lenient().when(identityManager.getOrCreateSpaceIdentity("sales")).thenReturn(sales);

    spaceService = mock(SpaceService.class);
    lenient().when(spaceService.getSpaceByGroupId("/spaces/sales")).thenReturn(space("sales"));
    lenient().when(spaceService.getSpaceByPrettyName("sales")).thenReturn(space("sales"));

    storage = new JCRRecycleBinStorage(repositoryService, trashStorage, nodeHierarchyCreator, identityManager, spaceService) {
      @Override
      SessionProvider createSystemProvider() {
        return systemProvider;
      }

      @Override
      Session openSession(Identity user, String workspace) {
        return (WORKSPACE.equals(workspace) ? jcr : archive).session(user.getUserId());
      }
    };
  }

  @Test
  void anItemGoesBackToTheFolderItWasDeletedFromWithoutItsMarksInOneSave() throws Exception {
    Item file = binItem(JOHN_BIN + "/report.docx", ID_1, "7", JOHN_FOLDER + "/report.docx", johnFolder.getId());
    file.set("exo:deletedBy", "john").set("exo:recycleBinPermissions", new String[] { "john read" });
    jcr.commit();

    RecycleBinItem restored = storage.restoreItem(ID_1, MARY, false);

    assertSame(jcr.find(file.getId()), jcr.at(JOHN_FOLDER + "/report.docx"));
    assertEquals(Set.of(), file(JOHN_FOLDER + "/report.docx").getMixins());
    assertEquals(Set.of("exo:title"), file(JOHN_FOLDER + "/report.docx").getProperties().keySet());
    assertEquals("Report.docx", file(JOHN_FOLDER + "/report.docx").get("exo:title"));
    // Back in its folder it takes the folder's permissions again: none of its own.
    assertTrue(file(JOHN_FOLDER + "/report.docx").getPermissions().isEmpty());
    assertEquals(1, jcr.getSaves());
    assertEquals(List.of(JOHN_BIN + "/report.docx -> " + JOHN_FOLDER + "/report.docx"), jcr.getMoves());
    assertEquals(Set.of("__system"), jcr.getWriters());
    // The item as it was in the bin, for the caller to count it out.
    assertEquals(ID_1, restored.getId());
    assertEquals(7L, restored.getOwnerId());
    assertEquals(5, restored.getSize());
    // The folder was asked to the caller's own session, logged out afterwards.
    assertEquals(List.of("mary " + JOHN_FOLDER + " add_node"), jcr.getPermissionChecks());
    assertEquals(1, jcr.getLogouts());
    verify(systemProvider).close();
  }

  @Test
  void theFolderIsFoundByItsIdWhereverItWasMovedAndAFolderMadeAgainAtItsPathIsNotUsed() throws Exception {
    binItem(JOHN_BIN + "/report.docx", ID_1, "7", JOHN_FOLDER + "/report.docx", johnFolder.getId());
    jcr.add(JOHN_ROOT + "/2026", "nt:folder");
    jcr.at(JOHN_ROOT).getChildren().remove(johnFolder);
    jcr.at(JOHN_ROOT + "/2026").getChildren().add(johnFolder);
    johnFolder.parent = jcr.at(JOHN_ROOT + "/2026");
    jcr.add(JOHN_FOLDER, "nt:folder");
    jcr.commit();

    storage.restoreItem(ID_1, MARY, false);

    assertNotNull(jcr.at(JOHN_ROOT + "/2026/Reports/report.docx"));
    assertNull(jcr.at(JOHN_FOLDER + "/report.docx"));
  }

  @Test
  void aFolderThatLeftTheDriveOrIsInTheTrashOrIsGoneSendsTheItemToTheRootOfItsDrive() throws Exception {
    Item elsewhere = jcr.add(SALES_ROOT + "/Reports", "nt:folder");
    Item trashed = jcr.add(JOHN_BIN + "/Old", "nt:folder");
    binItem(JOHN_BIN + "/a.txt", ID_1, "7", JOHN_FOLDER + "/a.txt", elsewhere.getId());
    binItem(JOHN_BIN + "/b.txt", ID_2, "7", JOHN_FOLDER + "/b.txt", trashed.getId());
    binItem(JOHN_BIN + "/c.txt", ID_3, "7", JOHN_FOLDER + "/c.txt", "an-id-of-no-node");

    storage.restoreItem(ID_1, MARY, false);
    storage.restoreItem(ID_2, MARY, false);
    storage.restoreItem(ID_3, MARY, false);

    assertNotNull(jcr.at(JOHN_ROOT + "/a.txt"));
    assertNotNull(jcr.at(JOHN_ROOT + "/b.txt"));
    assertNotNull(jcr.at(JOHN_ROOT + "/c.txt"));
    assertTrue(jcr.at(SALES_ROOT + "/Reports").getChildren().isEmpty());
    assertTrue(jcr.at(JOHN_FOLDER).getChildren().isEmpty());
  }

  @Test
  void anItemThatRecordedNoFolderIdGoesBackByItsPathAndToTheRootWhenThePathIsGone() throws Exception {
    binItem(JOHN_BIN + "/a.txt", ID_1, "7", JOHN_FOLDER + "/a.txt", null);
    binItem(JOHN_BIN + "/b.txt", ID_2, "7", JOHN_ROOT + "/Gone/b.txt", null);
    // A path of no drive of the owner is not one to go back to.
    binItem(JOHN_BIN + "/c.txt", ID_3, "7", SALES_ROOT + "/c.txt", null);

    storage.restoreItem(ID_1, MARY, false);
    storage.restoreItem(ID_2, MARY, false);
    storage.restoreItem(ID_3, MARY, false);

    assertNotNull(jcr.at(JOHN_FOLDER + "/a.txt"));
    assertNotNull(jcr.at(JOHN_ROOT + "/b.txt"));
    assertNotNull(jcr.at(JOHN_ROOT + "/c.txt"));
  }

  @Test
  void anItemOfASpaceGoesBackToTheDocumentsFolderOfTheSpace() throws Exception {
    binItem(SALES_BIN + "/a.txt", ID_1, "20", SALES_ROOT + "/Gone/a.txt", null);

    storage.restoreItem(ID_1, MARY, false);

    assertNotNull(jcr.at(SALES_ROOT + "/a.txt"));
  }

  @Test
  void aFolderThatCannotBeReadLeavesTheItemInTheBinAndIsNotTakenForAGoneOne() throws Exception {
    binItem(JOHN_BIN + "/a.txt", ID_1, "7", JOHN_FOLDER + "/a.txt", johnFolder.getId());
    RepositoryException failure = new RepositoryException("The repository cannot answer");
    jcr.failNextLookup(failure);

    assertSame(failure, assertThrows(RepositoryException.class, () -> storage.restoreItem(ID_1, MARY, false)));

    assertNotNull(jcr.at(JOHN_BIN + "/a.txt"));
    assertNull(jcr.at(JOHN_ROOT + "/a.txt"));
    assertEquals(0, jcr.getSaves());
    assertEquals(1, jcr.getRefreshes());
    verify(systemProvider).close();
  }

  @Test
  void anItemWhoseOwnerOrWhoseDriveNoLongerExistsHasNoLocationAndIsLookedForNowhere() throws Exception {
    binItem(JOHN_BIN + "/a.txt", ID_1, "8", JOHN_FOLDER + "/a.txt", johnFolder.getId());
    binItem(JOHN_BIN + "/b.txt", ID_2, "9", JOHN_FOLDER + "/b.txt", johnFolder.getId());
    org.exoplatform.social.core.identity.model.Identity deleted = identity("organization", "jack", "9");
    deleted.setDeleted(true);
    when(identityManager.getIdentity("9")).thenReturn(deleted);
    binItem(SALES_BIN + "/c.txt", ID_3, "20", SALES_ROOT + "/Gone/c.txt", null);

    assertLocationMissing(ID_1);
    assertLocationMissing(ID_2);
    assertTrue(jcr.getPermissionChecks().isEmpty());
    // The space is gone, or its Documents folder is.
    jcr.at("/Groups/spaces/sales").getChildren().clear();
    jcr.commit();
    assertLocationMissing(ID_3);
    when(spaceService.getSpaceByPrettyName("sales")).thenReturn(null);
    assertLocationMissing(ID_3);

    assertEquals(0, jcr.getSaves());
    assertTrue(jcr.getMoves().isEmpty());
    assertNotNull(jcr.at(JOHN_BIN + "/a.txt"));
  }

  @Test
  void aCallerTheFolderRefusesGetsTheItemAtTheRootWithThePermissionsItHadAndNoOther() throws Exception {
    Item file = binItem(JOHN_BIN + "/a.txt", ID_1, "20", SALES_ROOT + "/Reports/a.txt", null);
    file.set("exo:recycleBinPermissions", new String[] { "*:/spaces/sales read", "manager:/spaces/sales read", "manager:/spaces/sales add_node" });
    jcr.add(SALES_ROOT + "/Reports", "nt:folder");
    jcr.commit();
    jcr.refuse("mary", SALES_ROOT + "/Reports");

    storage.restoreItem(ID_1, MARY, false);

    Item restored = jcr.at(SALES_ROOT + "/a.txt");
    assertNotNull(restored);
    assertEquals(Set.of("exo:privilegeable"), restored.getMixins());
    assertEquals(List.of("*:/spaces/sales read", "manager:/spaces/sales read", "manager:/spaces/sales add_node"), permissions(restored));
    assertEquals(List.of("mary " + SALES_ROOT + "/Reports add_node", "mary " + SALES_ROOT + " add_node"), jcr.getPermissionChecks());
    assertEquals(2, jcr.getLogouts());
    assertEquals(1, jcr.getSaves());
  }

  @Test
  void anItemWithPermissionsOfItsOwnKeepsThemAtTheRoot() throws Exception {
    Item file = binItem(SALES_BIN + "/a.txt", ID_1, "20", SALES_ROOT + "/Reports/a.txt", null);
    file.mixin("exo:privilegeable").permission("mary", "read");
    jcr.add(SALES_ROOT + "/Reports", "nt:folder");
    jcr.commit();
    jcr.refuse("mary", SALES_ROOT + "/Reports");

    storage.restoreItem(ID_1, MARY, false);

    assertEquals(List.of("mary read"), permissions(jcr.at(SALES_ROOT + "/a.txt")));
    assertEquals(Set.of("exo:privilegeable"), jcr.at(SALES_ROOT + "/a.txt").getMixins());
  }

  @Test
  void anItemWithNoPermissionToTakeStaysInTheBinWhenItsFolderRefusesTheCaller() throws Exception {
    binItem(SALES_BIN + "/a.txt", ID_1, "20", SALES_ROOT + "/Reports/a.txt", null);
    jcr.add(SALES_ROOT + "/Reports", "nt:folder");
    jcr.commit();
    jcr.refuse("mary", SALES_ROOT + "/Reports");

    assertTargetForbidden(ID_1, MARY);

    assertNotNull(jcr.at(SALES_BIN + "/a.txt"));
    assertEquals(List.of("mary " + SALES_ROOT + "/Reports add_node"), jcr.getPermissionChecks());
    // The same item whose folder is gone goes to the root: nothing refused it.
    jcr.at(SALES_ROOT).getChildren().clear();
    jcr.commit();
    storage.restoreItem(ID_1, MARY, false);
    assertNotNull(jcr.at(SALES_ROOT + "/a.txt"));
    assertTrue(jcr.at(SALES_ROOT + "/a.txt").getPermissions().isEmpty());
  }

  @Test
  void aCallerTheRootRefusesTooRestoresNothing() throws Exception {
    Item file = binItem(SALES_BIN + "/a.txt", ID_1, "20", SALES_ROOT + "/Gone/a.txt", null);
    file.set("exo:recycleBinPermissions", new String[] { "mary read" });
    jcr.commit();
    jcr.refuse("mary", SALES_ROOT);

    assertTargetForbidden(ID_1, MARY);
    assertTargetForbidden(ID_1, null);

    assertNotNull(jcr.at(SALES_BIN + "/a.txt"));
    assertEquals(0, jcr.getSaves());
    assertEquals(Set.of("exo:restoreLocation", "exo:recycleBinItem"), jcr.at(SALES_BIN + "/a.txt").getMixins());
  }

  @Test
  void anAdministratorOfThePlatformIsAskedNothingAndTheItemGoesToItsFolder() throws Exception {
    binItem(SALES_BIN + "/a.txt", ID_1, "20", SALES_ROOT + "/Reports/a.txt", null);
    binItem(SALES_BIN + "/b.txt", ID_2, "20", SALES_ROOT + "/Gone/b.txt", null);
    jcr.add(SALES_ROOT + "/Reports", "nt:folder");
    jcr.commit();
    jcr.refuse("root", SALES_ROOT + "/Reports");
    jcr.refuse("root", SALES_ROOT);

    storage.restoreItem(ID_1, new Identity("root"), true);
    storage.restoreItem(ID_2, null, true);

    assertNotNull(jcr.at(SALES_ROOT + "/Reports/a.txt"));
    assertNotNull(jcr.at(SALES_ROOT + "/b.txt"));
    assertTrue(jcr.getPermissionChecks().isEmpty());
    assertEquals(0, jcr.getLogouts());
  }

  @Test
  void aTakenNameIsKeptAndTheRestoredItemIsRenamedInItsNameAndItsTitleAsAMovedOne() throws Exception {
    binItem(JOHN_BIN + "/report.docx", ID_1, "7", JOHN_FOLDER + "/report.docx", johnFolder.getId());
    binItem(JOHN_BIN + "/report-2.docx", ID_2, "7", JOHN_FOLDER + "/report.docx[2]", johnFolder.getId());
    Item folder = jcr.add(JOHN_BIN + "/Plans", "nt:folder");
    folder.mixin("exo:restoreLocation", "exo:recycleBinItem")
          .set("exo:trashId", ID_3)
          .set("exo:recycleBinOwner", "7")
          .set("exo:restorePath", JOHN_FOLDER + "/Plans")
          .set("exo:restoreWorkspace", WORKSPACE)
          .set("exo:title", "Plans");
    Item taken = jcr.addFile(JOHN_FOLDER + "/report.docx", 1);
    jcr.add(JOHN_FOLDER + "/Plans", "nt:folder");
    jcr.commit();

    storage.restoreItem(ID_1, MARY, false);
    storage.restoreItem(ID_2, MARY, false);
    storage.restoreItem(ID_3, MARY, false);

    assertSame(jcr.find(taken.getId()), jcr.at(JOHN_FOLDER + "/report.docx"));
    assertEquals("Report(1).docx", jcr.at(JOHN_FOLDER + "/report(1).docx").get("exo:title"));
    assertEquals("Report(2).docx", jcr.at(JOHN_FOLDER + "/report(2).docx").get("exo:title"));
    assertEquals("Plans(1)", jcr.at(JOHN_FOLDER + "/Plans(1)").get("exo:title"));
    assertEquals(3, jcr.getMoves().size());
  }

  @Test
  void aFolderWhoseNameHoldsADotIsRenamedForATakenNameAsAMovedOneIs() throws Exception {
    Item folder = jcr.add(JOHN_BIN + "/v1.2", "nt:folder");
    folder.mixin("exo:restoreLocation", "exo:recycleBinItem")
          .set("exo:trashId", ID_1)
          .set("exo:recycleBinOwner", "7")
          .set("exo:restorePath", JOHN_FOLDER + "/v1.2")
          .set("exo:restoreWorkspace", WORKSPACE)
          .set("exo:title", "v1.2");
    Item taken = jcr.add(JOHN_FOLDER + "/v1.2", "nt:folder");
    jcr.commit();

    storage.restoreItem(ID_1, MARY, false);

    assertSame(jcr.find(taken.getId()), jcr.at(JOHN_FOLDER + "/v1.2"));
    String name = org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.increaseNameIndex("v1.2", 1);
    assertSame(jcr.find(folder.getId()), jcr.at(JOHN_FOLDER + "/" + name));
    assertEquals("v1(1).2", name);
    assertEquals("v1(1).2", jcr.at(JOHN_FOLDER + "/" + name).get("exo:title"));
  }

  @Test
  void anItemRestoredToAPersonalDriveLosesTheEntryOfThePlatformAdministratorsAndSoDoesWhatItHolds() throws Exception {
    Item folder = jcr.add(JOHN_BIN + "/Plans", "nt:folder");
    folder.mixin("exo:restoreLocation", "exo:recycleBinItem", "exo:privilegeable")
          .set("exo:trashId", ID_1)
          .set("exo:recycleBinOwner", "7")
          .set("exo:restorePath", JOHN_FOLDER + "/Plans")
          .set("exo:restoreWorkspace", WORKSPACE)
          .permission("john", "read")
          .permission(ADMINISTRATORS, "read")
          .permission("john", "add_node")
          .permission(ADMINISTRATORS, "remove");
    // Left with no entry once the administrators' is removed: its owner's is written.
    Item owned = jcr.addFile(JOHN_BIN + "/Plans/owned.txt", 1);
    owned.mixin("exo:privilegeable").set("exo:owner", "mary").permission(ADMINISTRATORS, "read");
    Item unowned = jcr.addFile(JOHN_BIN + "/Plans/Deep/unowned.txt", 1);
    unowned.mixin("exo:privilegeable").permission(ADMINISTRATORS, "read");
    Item untouched = jcr.addFile(JOHN_BIN + "/Plans/untouched.txt", 1);
    untouched.mixin("exo:privilegeable").permission("mary", "read");
    Item link = jcr.add(JOHN_BIN + "/Plans/link", "exo:symlink");
    link.mixin("exo:privilegeable").permission(ADMINISTRATORS, "read");
    // What the item does not hold is left as it is, whatever the statement finds.
    Item outside = jcr.addFile(JOHN_BIN + "/Plans2/outside.txt", 1);
    outside.mixin("exo:privilegeable").permission(ADMINISTRATORS, "read");
    jcr.commit();

    storage.restoreItem(ID_1, MARY, false);

    assertEquals(List.of("john read", "john add_node"), permissions(jcr.at(JOHN_FOLDER + "/Plans")));
    assertEquals(permissionsOf("mary"), permissions(jcr.at(JOHN_FOLDER + "/Plans/owned.txt")));
    assertEquals(permissionsOf("john"), permissions(jcr.at(JOHN_FOLDER + "/Plans/Deep/unowned.txt")));
    assertEquals(List.of("mary read"), permissions(jcr.at(JOHN_FOLDER + "/Plans/untouched.txt")));
    assertEquals(List.of(ADMINISTRATORS + " read"), permissions(jcr.at(JOHN_FOLDER + "/Plans/link")));
    assertEquals(List.of(ADMINISTRATORS + " read"), permissions(jcr.at(JOHN_BIN + "/Plans2/outside.txt")));
    assertEquals(1, jcr.getSaves());
  }

  @Test
  void theEntryOfThePlatformAdministratorsIsRemovedFromTheRecordedPermissionsTooAndKeptInASpace() throws Exception {
    Item mine = binItem(JOHN_BIN + "/a.txt", ID_1, "7", JOHN_ROOT + "/Gone/a.txt", null);
    mine.set("exo:recycleBinPermissions", new String[] { ADMINISTRATORS + " read", "john read" });
    Item shortcut = jcr.add(JOHN_BIN + "/link", "exo:symlink");
    shortcut.mixin("exo:restoreLocation", "exo:recycleBinItem", "exo:privilegeable")
            .set("exo:trashId", ID_2)
            .set("exo:recycleBinOwner", "7")
            .set("exo:restorePath", JOHN_FOLDER + "/link")
            .set("exo:restoreWorkspace", WORKSPACE)
            .permission(ADMINISTRATORS, "read");
    Item theirs = binItem(SALES_BIN + "/c.txt", ID_3, "20", SALES_ROOT + "/c.txt", null);
    theirs.mixin("exo:privilegeable").permission(ADMINISTRATORS, "read");
    jcr.commit();

    storage.restoreItem(ID_1, MARY, false);
    storage.restoreItem(ID_2, MARY, false);
    storage.restoreItem(ID_3, MARY, false);

    assertEquals(List.of("john read"), permissions(jcr.at(JOHN_ROOT + "/a.txt")));
    // A shortcut is left as it is, and a space keeps the entry.
    assertEquals(List.of(ADMINISTRATORS + " read"), permissions(jcr.at(JOHN_FOLDER + "/link")));
    assertEquals(List.of(ADMINISTRATORS + " read"), permissions(jcr.at(SALES_ROOT + "/c.txt")));
  }

  @Test
  void theShortcutsTrashedWithTheItemComeBackToTheirOwnFoldersInTheSameSaveOrStayInTheTrash() throws Exception {
    Item file = binItem(JOHN_BIN + "/report.docx", ID_1, "7", JOHN_FOLDER + "/report.docx", johnFolder.getId());
    Item salesFolder = jcr.add(SALES_ROOT + "/Shared", "nt:folder");
    // Its folder was moved since: it is found by its id, its former path being gone.
    Item back = cascade(SALES_BIN + "/report.docx", "44444444-4444-4444-4444-444444444444", SALES_ROOT + "/Former/report.docx", salesFolder.getId());
    Item trashedFolder = jcr.add(SALES_BIN + "/Old", "nt:folder");
    Item folderTrashed = cascade(SALES_BIN + "/in-old", "99999999-9999-9999-9999-999999999999", SALES_ROOT + "/Old/in-old", trashedFolder.getId());
    Item byPath = cascade(SALES_BIN + "/by-path", "55555555-5555-5555-5555-555555555555", SALES_ROOT + "/by-path", null);
    Item folderGone = cascade(SALES_BIN + "/gone", "66666666-6666-6666-6666-666666666666", SALES_ROOT + "/Gone/gone", "an-id-of-no-node");
    Item nameTaken = cascade(SALES_BIN + "/taken", "77777777-7777-7777-7777-777777777777", SALES_ROOT + "/Shared/taken", salesFolder.getId());
    Item ofAnother = cascade(SALES_BIN + "/other", "88888888-8888-8888-8888-888888888888", SALES_ROOT + "/Shared/other", salesFolder.getId());
    ofAnother.set("exo:recycleBinOf", ID_2);
    // Deleted from another workspace: its path names nothing of this one.
    Item ofAnotherWorkspace = cascade(SALES_BIN + "/elsewhere", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", SALES_ROOT + "/Shared/elsewhere", null);
    ofAnotherWorkspace.set("exo:restoreWorkspace", "archive");
    jcr.add(SALES_ROOT + "/Shared/taken", "exo:symlink");
    jcr.commit();
    // The folders of the shortcuts are not asked to the caller: they left with the item.
    jcr.refuse("mary", SALES_ROOT + "/Shared");
    jcr.refuse("mary", SALES_ROOT);

    storage.restoreItem(ID_1, MARY, false);

    assertSame(jcr.find(file.getId()), jcr.at(JOHN_FOLDER + "/report.docx"));
    assertSame(jcr.find(back.getId()), jcr.at(SALES_ROOT + "/Shared/report.docx"));
    assertEquals(Set.of(), jcr.find(back.getId()).getMixins());
    assertEquals(Set.of("exo:uuid"), jcr.find(back.getId()).getProperties().keySet());
    assertSame(jcr.find(byPath.getId()), jcr.at(SALES_ROOT + "/by-path"));
    assertSame(jcr.find(folderGone.getId()), jcr.at(SALES_BIN + "/gone"));
    assertSame(jcr.find(folderTrashed.getId()), jcr.at(SALES_BIN + "/in-old"));
    assertSame(jcr.find(nameTaken.getId()), jcr.at(SALES_BIN + "/taken"));
    assertEquals(ID_1, jcr.find(nameTaken.getId()).get("exo:recycleBinOf"));
    assertSame(jcr.find(ofAnother.getId()), jcr.at(SALES_BIN + "/other"));
    assertSame(jcr.find(ofAnotherWorkspace.getId()), jcr.at(SALES_BIN + "/elsewhere"));
    assertEquals(1, jcr.getSaves());
    assertEquals(List.of("mary " + JOHN_FOLDER + " add_node"), jcr.getPermissionChecks());
  }

  @Test
  void anItemOfNoDriveOnlyGoesBackToTheFolderItWasDeletedFrom() throws Exception {
    Item folder = jcr.add("/Library/Shelf", "nt:folder");
    binItem("/Trash/a.txt", ID_1, null, "/Library/Shelf/a.txt", folder.getId());
    binItem("/Trash/b.txt", ID_2, null, "/Library/Gone/b.txt", null);
    binItem("/Trash/c.txt", ID_3, null, "/Library/Shelf/c.txt", folder.getId());
    jcr.refuse("jack", "/Library/Shelf");

    storage.restoreItem(ID_1, MARY, false);
    assertLocationMissing(ID_2);
    assertTargetForbidden(ID_3, new Identity("jack"));
    // A folder that is itself in the trash is no place to go back to.
    Item trashedFolder = jcr.add("/Trash/Shelf2", "nt:folder");
    binItem("/Trash/d.txt", ID_2.replace('2', '4'), null, "/Library/Shelf2/d.txt", trashedFolder.getId());
    assertLocationMissing(ID_2.replace('2', '4'));
    assertNotNull(jcr.at("/Trash/d.txt"));
    assertTrue(jcr.at("/Trash/Shelf2").getChildren().isEmpty());

    assertNotNull(jcr.at("/Library/Shelf/a.txt"));
    assertNotNull(jcr.at("/Trash/b.txt"));
    assertNotNull(jcr.at("/Trash/c.txt"));
    assertEquals(1, jcr.getSaves());
  }

  @Test
  void whatIsNoItemOfABinIsNotFoundAndNothingMoves() throws Exception {
    binItem(JOHN_BIN + "/a.txt", ID_1, "7", JOHN_FOLDER + "/a.txt", johnFolder.getId());
    cascade(JOHN_BIN + "/link", ID_2, JOHN_FOLDER + "/link", johnFolder.getId());

    assertNotFound(ID_3);
    assertNotFound(ID_2);
    assertEquals(2, jcr.getStatements().size());
    assertNotFound(null);
    assertNotFound(ID_1 + "' OR exo:recycleBinOwner = '7");
    assertEquals(2, jcr.getStatements().size());

    assertTrue(jcr.getMoves().isEmpty());
    assertEquals(0, jcr.getSaves());
  }

  @Test
  void aRestoreThatCannotBeSavedLeavesTheItemAndItsShortcutsInTheTrashAndTellsWhy() throws Exception {
    Item file = binItem(JOHN_BIN + "/a.txt", ID_1, "7", JOHN_FOLDER + "/a.txt", johnFolder.getId());
    Item link = cascade(JOHN_BIN + "/link", ID_2, JOHN_FOLDER + "/link", johnFolder.getId());
    InvalidItemStateException failure = new InvalidItemStateException("The item was changed by another session");
    jcr.failNextSave(failure);

    assertSame(failure, assertThrows(InvalidItemStateException.class, () -> storage.restoreItem(ID_1, MARY, false)));

    assertSame(jcr.find(file.getId()), jcr.at(JOHN_BIN + "/a.txt"));
    assertSame(jcr.find(link.getId()), jcr.at(JOHN_BIN + "/link"));
    assertEquals(Set.of("exo:restoreLocation", "exo:recycleBinItem"), jcr.find(file.getId()).getMixins());
    assertEquals(1, jcr.getRefreshes());
    verify(systemProvider).close();
  }

  @Test
  void aVersionedItemLeftCheckedInIsCheckedOutBeforeItsMarksAreRemoved() throws Exception {
    Item file = binItem(JOHN_BIN + "/a.txt", ID_1, "7", JOHN_FOLDER + "/a.txt", johnFolder.getId());
    file.mixin("mix:versionable").set("jcr:isCheckedOut", Boolean.FALSE);
    jcr.commit();

    storage.restoreItem(ID_1, MARY, false);

    assertEquals(Set.of("mix:versionable"), jcr.at(JOHN_FOLDER + "/a.txt").getMixins());
    assertEquals(Boolean.TRUE, jcr.at(JOHN_FOLDER + "/a.txt").get("jcr:isCheckedOut"));
  }

  @Test
  void anItemDeletedFromAnotherWorkspaceIsCopiedBackThereThenRemovedFromTheTrash() throws Exception {
    Item file = binItem("/Trash/a.txt", ID_1, null, "/Library/a.txt", null);
    file.set("exo:restoreWorkspace", "archive");
    Item taken = binItem("/Trash/b.txt", ID_2, null, "/Library/b.txt", null);
    taken.set("exo:restoreWorkspace", "archive");
    Item gone = binItem("/Trash/c.txt", ID_3, null, "/Gone/c.txt", null);
    gone.set("exo:restoreWorkspace", "archive");
    jcr.commit();
    archive.addFile("/Library/b.txt", 1);

    storage.restoreItem(ID_1, MARY, false);
    storage.restoreItem(ID_2, MARY, false);
    assertLocationMissing(ID_3);

    assertNull(jcr.at("/Trash/a.txt"));
    assertEquals(file.getId(), archive.at("/Library/a.txt").getId());
    assertEquals(Set.of(), archive.at("/Library/a.txt").getMixins());
    assertEquals("B(1).txt", archive.at("/Library/b(1).txt").get("exo:title"));
    assertNotNull(jcr.at("/Trash/c.txt"));
    assertEquals(List.of("mary /Library add_node", "mary /Library add_node"), archive.getPermissionChecks());
    assertTrue(jcr.getPermissionChecks().isEmpty());

    binItem("/Trash/d.txt", ID_1, null, "/Library/d.txt", null).set("exo:restoreWorkspace", "archive");
    jcr.commit();
    archive.refuse("mary", "/Library");
    assertTargetForbidden(ID_1, MARY);
    assertNotNull(jcr.at("/Trash/d.txt"));
    assertNull(archive.at("/Library/d.txt"));
  }

  @Test
  void theStatementsOfARestoreAreAcceptedByTheRepositoryParser() throws Exception {
    binItem(JOHN_BIN + "/john's plan.txt", ID_1, "7", JOHN_FOLDER + "/john's plan.txt", johnFolder.getId());

    storage.restoreItem(ID_1, MARY, false);

    assertEquals(List.of("SELECT * FROM exo:recycleBinItem WHERE jcr:path LIKE '/Trash/%' AND exo:trashId = '" + ID_1 + "'",
                         "SELECT * FROM exo:recycleBinItem WHERE jcr:path LIKE '/Trash/%' AND exo:recycleBinOf = '" + ID_1 + "'",
                         "SELECT * FROM exo:privilegeable WHERE jcr:path LIKE '" + JOHN_BIN + "/john''s plan.txt/%'"),
                 jcr.getStatements());
    LocationFactory locationFactory = new LocationFactory(new NamespaceRegistryImpl());
    for (String statement : jcr.getStatements()) {
      assertNotNull(JCRSQLQueryBuilder.createQuery(statement, locationFactory, new DefaultQueryNodeFactory()), statement);
    }
  }

  private void assertLocationMissing(String itemId) {
    IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> storage.restoreItem(itemId, MARY, false));
    assertEquals(RecycleBinError.RESTORE_LOCATION_MISSING, refusal.getMessage());
  }

  private void assertTargetForbidden(String itemId, Identity caller) {
    IllegalAccessException refusal = assertThrows(IllegalAccessException.class, () -> storage.restoreItem(itemId, caller, false));
    assertEquals(RecycleBinError.RESTORE_TARGET_FORBIDDEN, refusal.getMessage());
  }

  private void assertNotFound(String itemId) {
    ObjectNotFoundException refusal = assertThrows(ObjectNotFoundException.class, () -> storage.restoreItem(itemId, MARY, false));
    assertEquals(RecycleBinError.ITEM_NOT_FOUND, refusal.getMessage());
  }

  private Item file(String path) {
    Item file = jcr.at(path);
    assertNotNull(file, path);
    return file;
  }

  /** A file of a bin, titled as its name with a capital, deleted from the path given. */
  private Item binItem(String path, String trashId, String ownerId, String restorePath, String parentId) {
    Item file = jcr.addFile(path, 5);
    String name = restorePath.substring(restorePath.lastIndexOf('/') + 1).replaceAll("\\[\\d+\\]$", "");
    file.mixin("exo:restoreLocation", "exo:recycleBinItem")
        .set("exo:trashId", trashId)
        .set("exo:restorePath", restorePath)
        .set("exo:restoreWorkspace", WORKSPACE)
        .set("exo:recycleBinName", name)
        .set("exo:recycleBinSize", 5L)
        .set("exo:title", name.substring(0, 1).toUpperCase() + name.substring(1));
    if (ownerId != null) {
      file.set("exo:recycleBinOwner", ownerId);
    }
    if (parentId != null) {
      file.set("exo:restoreParentId", parentId);
    }
    jcr.commit();
    return file;
  }

  /** A shortcut trashed with the item of the first id. */
  private Item cascade(String path, String trashId, String restorePath, String parentId) {
    Item link = jcr.add(path, "exo:symlink");
    link.mixin("exo:restoreLocation", "exo:recycleBinItem")
        .set("exo:uuid", "a-document")
        .set("exo:trashId", trashId)
        .set("exo:recycleBinOf", ID_1)
        .set("exo:restorePath", restorePath)
        .set("exo:restoreWorkspace", WORKSPACE);
    if (parentId != null) {
      link.set("exo:restoreParentId", parentId);
    }
    jcr.commit();
    return link;
  }

  private static List<String> permissions(Item item) {
    assertNotNull(item);
    return item.getPermissions().stream().map(entry -> entry[0] + " " + entry[1]).toList();
  }

  private static List<String> permissionsOf(String identity) {
    List<String> permissions = new ArrayList<>();
    for (String type : org.exoplatform.services.jcr.access.PermissionType.ALL) {
      permissions.add(identity + " " + type);
    }
    return permissions;
  }

  /**
   * Answers a statement of the restore as the test expects the repository
   * to. The one on permissions answers every node carrying some, in the
   * trash or not: more than the item holds, as a path comparison may.
   */
  private List<Item> answer(String statement) {
    List<Item> found = new ArrayList<>();
    if (statement.startsWith("SELECT * FROM exo:privilegeable WHERE jcr:path LIKE '")) {
      collect(jcr.at("/"), "exo:privilegeable", found);
      return found;
    }
    assertTrue(statement.startsWith("SELECT * FROM exo:recycleBinItem WHERE jcr:path LIKE '/Trash/%' AND "), statement);
    collect(jcr.at("/Trash"), "exo:recycleBinItem", found);
    String id = group(statement, "exo:trashId = '([^']*)'");
    String cascadeOf = group(statement, "exo:recycleBinOf = '([^']*)'");
    found.removeIf(item -> id != null && !id.equals(item.get("exo:trashId")));
    found.removeIf(item -> cascadeOf != null && !cascadeOf.equals(item.get("exo:recycleBinOf")));
    return found;
  }

  private void collect(Item item, String type, List<Item> found) {
    for (Item child : item.getChildren()) {
      if (child.has(type)) {
        found.add(child);
      }
      collect(child, type, found);
    }
  }

  private static String group(String statement, String regex) {
    Matcher matcher = Pattern.compile(regex).matcher(statement);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static org.exoplatform.social.core.identity.model.Identity identity(String provider, String remoteId, String id) {
    org.exoplatform.social.core.identity.model.Identity identity = new org.exoplatform.social.core.identity.model.Identity(provider, remoteId);
    identity.setId(id);
    return identity;
  }

  private static Space space(String prettyName) {
    Space space = new Space();
    space.setPrettyName(prettyName);
    space.setGroupId("/spaces/" + prettyName);
    return space;
  }

}
