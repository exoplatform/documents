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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.documents.service.RecycleBinService;
import org.exoplatform.documents.storage.jcr.FakeJcr.Item;
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
 * How the nodes of the trash follow a folder that moved, on a workspace kept
 * in memory. The repository does not run the statement here: the test answers
 * it with every marked node of the trash, more than a pattern would select,
 * and gives the statement to the repository's own parser.
 */
class TrashStorageRestorePathTest {

  private static final String               WORKSPACE = "collaboration";

  private static final String               JOHN_ROOT = "/Users/j___/jo___/joh___/john/Private";

  private static final String               SPACE_ROOT = "/Groups/spaces/marketing/Documents";

  private static MockedStatic<CommonsUtils> commonsUtils;

  private FakeJcr                           jcr;

  private SessionProvider                   systemProvider;

  private RecycleBinService                 recycleBinService = mock(RecycleBinService.class);

  private TrashStorageImpl                  trashStorage;

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
    jcr.add("/Users/j___/jo___/joh___/john", "nt:unstructured");
    jcr.commit();
    jcr.setQueryHandler(statement -> {
      List<Item> marked = new ArrayList<>();
      collect(jcr.at("/Trash"), marked);
      return marked;
    });
    SessionImpl systemSession = jcr.session("__system");

    RepositoryService repositoryService = mock(RepositoryService.class);
    when(repositoryService.getCurrentRepository()).thenReturn(mock(ManageableRepository.class));
    systemProvider = mock(SessionProvider.class);
    lenient().when(systemProvider.getSession(anyString(), any())).thenReturn(systemSession);

    NodeHierarchyCreator nodeHierarchyCreator = mock(NodeHierarchyCreator.class);
    lenient().when(nodeHierarchyCreator.getJcrPath("groupsPath")).thenReturn("/Groups");
    lenient().when(nodeHierarchyCreator.getJcrPath("usersPath")).thenReturn("/Users");
    lenient().when(nodeHierarchyCreator.getUserNode(any(), eq("john")))
             .thenAnswer(invocation -> jcr.node(systemSession, jcr.at("/Users/j___/jo___/joh___/john")));

    IdentityManager identityManager = mock(IdentityManager.class);
    Identity john = new Identity("organization", "john");
    john.setId("7");
    Identity marketing = new Identity("space", "marketing");
    marketing.setId("9");
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

      @Override
      RecycleBinService getRecycleBinService() {
        return recycleBinService;
      }
    };
  }

  @Test
  void theNodesDeletedFromUnderTheFolderFollowItAndTheOthersDoNot() throws Exception {
    Item direct = trashed("/Trash/a", JOHN_ROOT + "/foo/a.txt");
    Item deep = trashed("/Trash/b", JOHN_ROOT + "/foo/sub/b.txt");
    Item sibling = trashed("/Trash/c", JOHN_ROOT + "/foobar/c.txt");
    Item elsewhere = trashed("/Trash/d", SPACE_ROOT + "/foo/d.txt");
    Item ofAnotherWorkspace = trashed("/Trash/e", JOHN_ROOT + "/foo/e.txt");
    ofAnotherWorkspace.set("exo:restoreWorkspace", "dms-system");
    jcr.commit();

    trashStorage.updateRestorePath(JOHN_ROOT + "/foo", JOHN_ROOT + "/foo2");

    assertEquals(JOHN_ROOT + "/foo2/a.txt", path(direct));
    assertEquals(JOHN_ROOT + "/foo2/sub/b.txt", path(deep));
    assertEquals(JOHN_ROOT + "/foobar/c.txt", path(sibling));
    assertEquals(SPACE_ROOT + "/foo/d.txt", path(elsewhere));
    assertEquals(JOHN_ROOT + "/foo/e.txt", path(ofAnotherWorkspace));
    // Each node in a save of its own, by the system and no one else.
    assertEquals(2, jcr.getSaves());
    assertEquals(Set.of("__system"), jcr.getWriters());
    verify(systemProvider).close();
    verifyNoInteractions(recycleBinService);
  }

  @Test
  void theNameOfTheFolderIsReadLiterallyWhateverItsCharacters() throws Exception {
    Item item = trashed("/Trash/a", JOHN_ROOT + "/a(1)+{x}$/a.txt");
    Item wildcard = trashed("/Trash/b", JOHN_ROOT + "/aX1Y+{x}$/b.txt");

    trashStorage.updateRestorePath(JOHN_ROOT + "/a(1)+{x}$", JOHN_ROOT + "/$1 \\ new");

    assertEquals(JOHN_ROOT + "/$1 \\ new/a.txt", path(item));
    assertEquals(JOHN_ROOT + "/aX1Y+{x}$/b.txt", path(wildcard));
  }

  @Test
  void aPathGivenWithoutItsLeadingSlashIsFollowedToo() throws Exception {
    Item item = trashed("/Trash/a", "/Library/foo/a.txt");

    trashStorage.updateRestorePath("Library/foo", "/Library/foo2");

    assertEquals("/Library/foo2/a.txt", path(item));
  }

  @Test
  void anItemStaysInTheBinOfTheDriveItWasDeletedFromWhereverItsFolderGoes() throws Exception {
    Item toSpace = binItem("/Trash/a", JOHN_ROOT + "/foo/a.txt", "7", 5);
    Item toNoDrive = binItem("/Trash/b", JOHN_ROOT + "/bar/b.txt", "7", 6);
    Item fromNoDrive = binItem("/Trash/c", "/Library/baz/c.txt", null, 4);
    Item cascade = binItem("/Trash/d", JOHN_ROOT + "/foo/d", null, 0);
    cascade.set("exo:recycleBinOf", "11111111-1111-1111-1111-111111111111");
    Item notTakenIn = trashed("/Trash/e", JOHN_ROOT + "/foo/e.txt");
    jcr.commit();

    trashStorage.updateRestorePath(JOHN_ROOT + "/foo", SPACE_ROOT + "/foo");
    trashStorage.updateRestorePath(JOHN_ROOT + "/bar", "/Library/bar");
    trashStorage.updateRestorePath("/Library/baz", SPACE_ROOT + "/baz");

    // An item of a drive keeps its path when its folder leaves the drive.
    assertEquals(JOHN_ROOT + "/foo/a.txt", path(toSpace));
    assertEquals(JOHN_ROOT + "/bar/b.txt", path(toNoDrive));
    // What is in no bin follows its folder wherever it goes.
    assertEquals(SPACE_ROOT + "/baz/c.txt", path(fromNoDrive));
    assertEquals(SPACE_ROOT + "/foo/d", path(cascade));
    assertEquals(SPACE_ROOT + "/foo/e.txt", path(notTakenIn));
    // The bin of each one is the one it had.
    assertEquals("7", jcr.find(toSpace.getId()).get("exo:recycleBinOwner"));
    assertEquals("7", jcr.find(toNoDrive.getId()).get("exo:recycleBinOwner"));
    assertFalse(jcr.find(fromNoDrive.getId()).getProperties().containsKey("exo:recycleBinOwner"));
    assertFalse(jcr.find(cascade.getId()).getProperties().containsKey("exo:recycleBinOwner"));
    assertFalse(jcr.find(notTakenIn.getId()).getProperties().containsKey("exo:recycleBinOwner"));
    // No bin is counted again: none changed.
    verifyNoInteractions(recycleBinService);
  }

  @Test
  void anItemFollowsItsFolderInsideItsDrive() throws Exception {
    Item item = binItem("/Trash/a", JOHN_ROOT + "/foo/a.txt", "7", 5);

    trashStorage.updateRestorePath(JOHN_ROOT + "/foo", JOHN_ROOT + "/Projects/foo2");

    assertEquals(JOHN_ROOT + "/Projects/foo2/a.txt", path(item));
    assertEquals("7", jcr.find(item.getId()).get("exo:recycleBinOwner"));
    verifyNoInteractions(recycleBinService);
  }

  @Test
  void aNodeThatCannotBeSavedIsLeftAsItWasAndTheOthersStillFollow() throws Exception {
    Item failing = binItem("/Trash/a", JOHN_ROOT + "/foo/a.txt", "7", 5);
    Item following = binItem("/Trash/b", JOHN_ROOT + "/foo/b.txt", "7", 3);
    jcr.failNextSave(new RepositoryException("The save failed"));

    trashStorage.updateRestorePath(JOHN_ROOT + "/foo", JOHN_ROOT + "/foo2");

    assertEquals(JOHN_ROOT + "/foo/a.txt", path(failing));
    assertEquals("7", jcr.find(failing.getId()).get("exo:recycleBinOwner"));
    assertEquals(JOHN_ROOT + "/foo2/b.txt", path(following));
    assertEquals("7", jcr.find(following.getId()).get("exo:recycleBinOwner"));
    assertEquals(1, jcr.getRefreshes());
    verify(systemProvider).close();
  }

  @Test
  void theStatementIsKeptToTheTrashReadsThePathLiterallyAndIsAcceptedByTheRepositoryParser() throws Exception {
    trashStorage.updateRestorePath("/Library/john's 100%_done\\x", "/Library/new");

    assertEquals(List.of("SELECT * FROM exo:restoreLocation WHERE jcr:path LIKE '/Trash/%' AND exo:restorePath LIKE"
        + " '/Library/john''s 100\\%\\_done\\\\x/%' ESCAPE '\\'"), jcr.getStatements());
    assertNotNull(JCRSQLQueryBuilder.createQuery(jcr.getStatements().get(0),
                                                 new LocationFactory(new NamespaceRegistryImpl()),
                                                 new DefaultQueryNodeFactory()));
    assertEquals(0, jcr.getSaves());
  }

  private String path(Item item) {
    return String.valueOf(jcr.find(item.getId()).get("exo:restorePath"));
  }

  private Item trashed(String path, String restorePath) {
    Item item = jcr.addFile(path, 1);
    item.mixin("exo:restoreLocation").set("exo:restorePath", restorePath).set("exo:restoreWorkspace", WORKSPACE);
    jcr.commit();
    return item;
  }

  private Item binItem(String path, String restorePath, String ownerId, long size) {
    Item item = trashed(path, restorePath);
    item.mixin("exo:recycleBinItem").set("exo:recycleBinSize", size);
    if (ownerId != null) {
      item.set("exo:recycleBinOwner", ownerId);
    }
    jcr.commit();
    return item;
  }

  private void collect(Item item, List<Item> found) {
    for (Item child : item.getChildren()) {
      if (child.has("exo:restoreLocation")) {
        found.add(child);
      }
      collect(child, found);
    }
  }

  private static ValueParam valueParam(String name, String value) {
    ValueParam valueParam = new ValueParam();
    valueParam.setName(name);
    valueParam.setValue(value);
    return valueParam;
  }

}
