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
package org.exoplatform.documents.storage.jcr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.storage.jcr.FakeJcr;
import org.exoplatform.documents.storage.jcr.FakeJcr.Item;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Which drive a path belongs to for the Recycle Bin, and where that drive's
 * bin folder is, on a workspace kept in memory.
 */
class RecycleBinLocationTest {

  private static final String               JOHN_HOME   = "/Users/j___/jo___/joh___/john";

  private static final String               JOHNNY_HOME = "/Users/j___/jo___/joh___/johnny";

  private static MockedStatic<CommonsUtils> commonsUtils;

  private FakeJcr                           jcr;

  private SessionImpl                       systemSession;

  private SessionProvider                   systemProvider;

  private NodeHierarchyCreator              nodeHierarchyCreator;

  private IdentityManager                   identityManager;

  private SpaceService                      spaceService;

  private Identity                          john;

  private Identity                          sales;

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
    jcr = new FakeJcr("collaboration");
    jcr.add("/Trash", "exo:trashFolder");
    jcr.add(JOHN_HOME + "/Private", "nt:unstructured");
    jcr.add(JOHNNY_HOME + "/Private", "nt:unstructured");
    systemSession = jcr.session("__system");
    systemProvider = mock(SessionProvider.class);

    nodeHierarchyCreator = mock(NodeHierarchyCreator.class);
    lenient().when(nodeHierarchyCreator.getJcrPath("groupsPath")).thenReturn("/Groups");
    lenient().when(nodeHierarchyCreator.getUserNode(any(), eq("john"))).thenAnswer(invocation -> jcr.node(systemSession, jcr.at(JOHN_HOME)));
    lenient().when(nodeHierarchyCreator.getUserNode(any(), eq("johnny")))
             .thenAnswer(invocation -> jcr.node(systemSession, jcr.at(JOHNNY_HOME)));

    identityManager = mock(IdentityManager.class);
    john = identity("organization", "john", "7");
    Identity johnny = identity("organization", "johnny", "8");
    sales = identity("space", "sales", "20");
    Identity sales2 = identity("space", "sales2", "21");
    lenient().when(identityManager.getOrCreateUserIdentity("john")).thenReturn(john);
    lenient().when(identityManager.getOrCreateUserIdentity("johnny")).thenReturn(johnny);
    lenient().when(identityManager.getOrCreateSpaceIdentity("sales")).thenReturn(sales);
    lenient().when(identityManager.getOrCreateSpaceIdentity("sales2")).thenReturn(sales2);

    spaceService = mock(SpaceService.class);
    lenient().when(spaceService.getSpaceByGroupId("/spaces/sales")).thenReturn(space("sales"));
    lenient().when(spaceService.getSpaceByGroupId("/spaces/sales2")).thenReturn(space("sales2"));
    lenient().when(spaceService.getSpaceByPrettyName("sales")).thenReturn(space("sales"));
  }

  @Test
  void aPathOfAPersonalDriveBelongsToItsUser() throws Exception {
    assertEquals("7", owner(JOHN_HOME + "/Private/Documents/report.docx"));
    assertEquals("7", owner(JOHN_HOME + "/Private/report.docx"));
    assertEquals("7", owner(JOHN_HOME + "/Public/report.docx"));
  }

  @Test
  void johnnyDocumentsAreNotJohns() throws Exception {
    assertEquals("8", owner(JOHNNY_HOME + "/Private/report.docx"));
    assertNotEquals(owner(JOHN_HOME + "/Private/report.docx"), owner(JOHNNY_HOME + "/Private/report.docx"));
  }

  @Test
  void aNodeOfAUserHomeOutsideTheDriveBelongsToNoBin() throws Exception {
    assertNull(owner(JOHN_HOME + "/ApplicationData/settings"));
    assertNull(owner(JOHN_HOME + "/Private"));
    assertNull(owner(JOHN_HOME));
  }

  @Test
  void aFolderNamedPrivateElsewhereInTheUsersTreeIsNoDrive() throws Exception {
    // The candidate user is "other", whose home is not where the path lies.
    lenient().when(identityManager.getOrCreateUserIdentity("other")).thenReturn(identity("organization", "other", "30"));
    jcr.add("/Users/o___/ot___/oth___/other", "nt:unstructured");
    lenient().when(nodeHierarchyCreator.getUserNode(any(), eq("other")))
             .thenAnswer(invocation -> jcr.node(systemSession, jcr.at("/Users/o___/ot___/oth___/other")));

    assertNull(owner("/Users/shared/other/Private/report.docx"));
  }

  @Test
  void aUserWhoDoesNotExistOwnsNothingAndTheirHomeIsNotRead() throws Exception {
    assertNull(owner("/Users/g___/gh___/gho___/ghost/Private/report.docx"));

    verify(nodeHierarchyCreator, never()).getUserNode(any(), anyString());
  }

  @Test
  void aDeletedUserOwnsNothingAndTheirHomeIsNotRead() throws Exception {
    john.setDeleted(true);

    assertNull(owner(JOHN_HOME + "/Private/report.docx"));

    verify(nodeHierarchyCreator, never()).getUserNode(any(), anyString());
  }

  @Test
  void aPathOfASpaceDocumentsFolderBelongsToTheSpace() throws Exception {
    assertEquals("20", owner("/Groups/spaces/sales/Documents/plan.txt"));
    assertEquals("20", owner("/Groups/spaces/sales/Documents/folder/plan.txt"));
  }

  @Test
  void theSpaceSales2IsNotTheSpaceSales() throws Exception {
    assertEquals("21", owner("/Groups/spaces/sales2/Documents/plan.txt"));
  }

  @Test
  void aNodeOfASpaceGroupOutsideItsDocumentsBelongsToNoBin() throws Exception {
    assertNull(owner("/Groups/spaces/sales/Documents"));
    assertNull(owner("/Groups/spaces/sales/Documents2/plan.txt"));
    assertNull(owner("/Groups/spaces/sales/ApplicationData/plan.txt"));
    assertNull(owner("/Groups/spaces/sales"));
    assertNull(owner("/Groups/platform/users/Documents/plan.txt"));
    assertNull(owner("/Groups/spaces/unknown/Documents/plan.txt"));
    assertNull(owner("/sites/intranet/page.html"));
    assertNull(owner(null));
  }

  @Test
  void theBinFolderOfAUserIsAtTheirHomePathUnderTheTrashHome() throws Exception {
    Node binFolder = binFolder(john, true);

    assertEquals("/Trash" + JOHN_HOME, binFolder.getPath());
    for (String level : new String[] { "/Trash/Users", "/Trash/Users/j___", "/Trash/Users/j___/jo___", "/Trash/Users/j___/jo___/joh___",
        "/Trash" + JOHN_HOME }) {
      assertTrue(jcr.at(level).has(NodeTypeConstants.EXO_RECYCLE_BIN_FOLDER), level);
      // Each level has the node type of the trash home.
      assertTrue(jcr.at(level).has("exo:trashFolder"), level);
    }
    assertEquals(5, jcr.getSaves());
  }

  @Test
  void theBinFolderOfASpaceIsAtItsGroupPathUnderTheTrashHome() throws Exception {
    assertEquals("/Trash/Groups/spaces/sales", binFolder(sales, true).getPath());
  }

  @Test
  void onlyTheFirstLevelCarriesPermissionsAndTheyNameTheSystemAlone() throws Exception {
    binFolder(john, true);

    Item firstLevel = jcr.at("/Trash/Users");
    assertTrue(firstLevel.has(NodeTypeConstants.EXO_PRIVILEGEABLE));
    assertEquals(PermissionType.ALL.length, firstLevel.getPermissions().size());
    for (String[] permission : firstLevel.getPermissions()) {
      assertEquals("__system", permission[0]);
    }
    assertFalse(jcr.at("/Trash/Users/j___").has(NodeTypeConstants.EXO_PRIVILEGEABLE));
    assertTrue(jcr.at("/Trash/Users/j___").getPermissions().isEmpty());
  }

  @Test
  void anExistingBinFolderIsReusedAndNothingIsSaved() throws Exception {
    Node created = binFolder(john, true);
    int saves = jcr.getSaves();

    Node found = binFolder(john, true);

    assertEquals(created.getPath(), found.getPath());
    assertEquals(saves, jcr.getSaves());
    assertEquals(1, jcr.at("/Trash").getChildren().size());
  }

  @Test
  void aBinFolderThatDoesNotExistIsNotCreatedWhenNotAsked() throws Exception {
    assertNull(binFolder(john, false));

    assertTrue(jcr.at("/Trash").getChildren().isEmpty());
    assertEquals(0, jcr.getSaves());
  }

  @Test
  void aDeletedFolderNamedLikeALevelIsNotTakenForIt() throws Exception {
    // A folder named Users was deleted before any bin folder existed.
    Item deletedFolder = jcr.add("/Trash/Users", "nt:folder").mixin(NodeTypeConstants.EXO_RESTORE_LOCATION);
    jcr.commit();

    Node binFolder = binFolder(john, true);

    assertEquals("/Trash/Users[2]" + JOHN_HOME.substring("/Users".length()), binFolder.getPath());
    Item binRoot = jcr.at("/Trash/Users[2]");
    assertTrue(binRoot.has(NodeTypeConstants.EXO_RECYCLE_BIN_FOLDER));
    assertTrue(binRoot.has(NodeTypeConstants.EXO_PRIVILEGEABLE));
    Item untouched = jcr.find(deletedFolder.getId());
    assertEquals("/Trash/Users", jcr.pathOf(untouched));
    assertFalse(untouched.has(NodeTypeConstants.EXO_RECYCLE_BIN_FOLDER));
    assertFalse(untouched.has(NodeTypeConstants.EXO_PRIVILEGEABLE));
    assertTrue(untouched.getChildren().isEmpty());
  }

  @Test
  void aLevelMarkedAsADeletedItemByTheRepositoryLosesThatMark() throws Exception {
    // The repository's own action marks every node added right under the trash home.
    jcr.setAddNodeAction(added -> {
      if ("/Trash/Users".equals(jcr.pathOf(added))) {
        added.mixin(NodeTypeConstants.EXO_RESTORE_LOCATION).set(NodeTypeConstants.RESTORE_PATH, "/Users");
      }
    });

    binFolder(john, true);

    Item firstLevel = jcr.at("/Trash/Users");
    assertFalse(firstLevel.has(NodeTypeConstants.EXO_RESTORE_LOCATION));
    assertFalse(firstLevel.getProperties().containsKey(NodeTypeConstants.RESTORE_PATH));
    assertTrue(firstLevel.has(NodeTypeConstants.EXO_RECYCLE_BIN_FOLDER));
    // Five levels, plus the save that removes the mark.
    assertEquals(6, jcr.getSaves());
  }

  @Test
  void aFailedCreationDiscardsThePendingLevel() throws Exception {
    RepositoryException failure = new RepositoryException("save refused");
    jcr.failNextSave(failure);

    assertEquals(failure, assertThrows(RepositoryException.class, () -> binFolder(john, true)));

    assertTrue(jcr.at("/Trash").getChildren().isEmpty());
    assertEquals(1, jcr.getRefreshes());
  }

  @Test
  void aSpaceThatNoLongerExistsHasNoBinFolder() throws Exception {
    assertNull(binFolder(identity("space", "gone", "40"), true));
  }

  private String owner(String path) throws RepositoryException {
    return JCRDocumentsUtil.resolveRecycleBinOwner(path, nodeHierarchyCreator, identityManager, spaceService, systemProvider);
  }

  private Node binFolder(Identity owner, boolean create) throws RepositoryException {
    return JCRDocumentsUtil.getRecycleBinFolder(owner, systemSession, "/Trash", nodeHierarchyCreator, spaceService, systemProvider, create);
  }

  private static Identity identity(String provider, String remoteId, String id) {
    Identity identity = new Identity(provider, remoteId);
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
