/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.storage.jcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.Session;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.webdav.plugin.PathCommandHandler;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavWriteCommandHandler;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.MembershipEntry;

/**
 * Cancelling the checkout of a document over WebDAV brings back its content,
 * and leaves the access lists of the document and of the nodes below it as
 * they are.
 */
class WebdavUncheckoutAccessListTest {

  private static final String           SPACE_GROUP = "/spaces/uncheckouters";

  /** May cancel the checkout, not change the access. */
  private static final Identity         EDITOR      = new Identity("editor", List.of(new MembershipEntry(SPACE_GROUP, "member")));

  private static final String[]         EDIT        = new String[] { PermissionType.READ, PermissionType.ADD_NODE,
      PermissionType.SET_PROPERTY };

  /** The rights that also let the restore replace a file below a folder */
  private static final String[]         EDIT_REMOVE = new String[] { PermissionType.READ, PermissionType.ADD_NODE,
      PermissionType.SET_PROPERTY, PermissionType.REMOVE };

  private static final String[]         READ        = new String[] { PermissionType.READ };

  private static final String           GROUP       = "*:" + SPACE_GROUP;

  private static ManageableRepository   repository;

  private static Session                systemSession;

  private static Node                   folder;

  private static WebdavWriteCommandHandler handler;

  private Session                       userSession;

  @BeforeAll
  static void setUpRepository() throws Exception {
    RepositoryService repositoryService = JcrAclTestContainer.start().getComponentInstanceOfType(RepositoryService.class);
    repository = repositoryService.getDefaultRepository();
    repositoryService.setCurrentRepositoryName(repository.getConfiguration().getName());
    systemSession = repository.getSystemSession("collaboration");
    folder = systemSession.getRootNode().addNode("uncheckoutSpace", "nt:folder");
    folder.addMixin("exo:privilegeable");
    ((ExtendedNode) folder).setPermissions(Map.of(GROUP, EDIT));
    systemSession.save();
    PathCommandHandler pathCommandHandler = mock(PathCommandHandler.class);
    when(pathCommandHandler.resolveToJcrPath(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
    handler = new WebdavWriteCommandHandler(null, repositoryService, null, null, pathCommandHandler);
  }

  @AfterAll
  static void removeFolder() throws Exception {
    if (systemSession != null) {
      folder.remove();
      systemSession.save();
      systemSession.logout();
    }
  }

  @BeforeEach
  void setEditor() throws Exception {
    ConversationState.setCurrent(new ConversationState(EDITOR));
    RepositoryService repositoryService = (RepositoryService) org.exoplatform.container.ExoContainerContext.getCurrentContainer()
                                                                                                            .getComponentInstanceOfType(RepositoryService.class);
    userSession = JCRDocumentsUtil.getUserSessionProvider(repositoryService, EDITOR).getSession("collaboration", repository);
  }

  @AfterEach
  void resetEditor() {
    ConversationState.setCurrent(null);
    if (userSession != null) {
      userSession.logout();
    }
  }

  @Test
  void aShareRevokedAfterTheCheckoutStaysRevokedWhenTheCheckoutIsCancelled() throws Exception {
    Node document = addVersionedFile("revoked.txt");
    document.addMixin("exo:privilegeable");
    ((ExtendedNode) document).setPermissions(Map.of(GROUP, EDIT, "guest", READ));
    systemSession.save();
    document.checkin();
    document.checkout();
    ((ExtendedNode) document).setPermissions(Map.of(GROUP, EDIT));
    systemSession.save();

    handler.uncheckout(userSession, "/uncheckoutSpace/revoked.txt");

    Node cancelled = reload(document);
    assertFalse(entriesOf(cancelled).contains("guest read"), "the revoked share stays revoked: " + entriesOf(cancelled));
    assertFalse(cancelled.isCheckedOut());
  }

  @Test
  void aShareAddedAfterTheCheckoutIsKeptWhenTheCheckoutIsCancelled() throws Exception {
    Node document = addVersionedFile("added.txt");
    document.addMixin("exo:privilegeable");
    ((ExtendedNode) document).setPermissions(Map.of(GROUP, EDIT));
    systemSession.save();
    document.checkin();
    document.checkout();
    ((ExtendedNode) document).setPermissions(Map.of(GROUP, EDIT, "guest", READ));
    systemSession.save();

    handler.uncheckout(userSession, "/uncheckoutSpace/added.txt");

    assertTrue(entriesOf(reload(document)).contains("guest read"));
  }

  @Test
  void aListOfItsOwnRemovedAfterTheCheckoutStaysRemovedWhenTheCheckoutIsCancelled() throws Exception {
    Node document = addVersionedFile("own-removed.txt");
    document.addMixin("exo:privilegeable");
    ((ExtendedNode) document).setPermissions(Map.of(GROUP, EDIT, "guest", READ));
    systemSession.save();
    document.checkin();
    document.checkout();
    document.removeMixin("exo:privilegeable");
    systemSession.save();

    handler.uncheckout(userSession, "/uncheckoutSpace/own-removed.txt");

    Node cancelled = reload(document);
    assertFalse(cancelled.isNodeType("exo:privilegeable"), "the document reads its folder's list");
    assertEquals(entriesOf(folder), entriesOf(cancelled));
    assertFalse(cancelled.isCheckedOut());
  }

  @Test
  void aListOfItsOwnAddedAfterTheCheckoutIsKeptWhenTheCheckoutIsCancelled() throws Exception {
    Node document = addVersionedFile("own-added.txt");
    document.checkin();
    document.checkout();
    document.addMixin("exo:privilegeable");
    ((ExtendedNode) document).setPermissions(Map.of(GROUP, EDIT, "guest", READ));
    systemSession.save();

    handler.uncheckout(userSession, "/uncheckoutSpace/own-added.txt");

    Node cancelled = reload(document);
    assertTrue(cancelled.isNodeType("exo:privilegeable"));
    assertTrue(entriesOf(cancelled).contains("guest read"));
    assertFalse(cancelled.isCheckedOut());
  }

  @Test
  void aShareRevokedBelowAFolderStaysRevokedWhenTheCheckoutIsCancelled() throws Exception {
    Node versionedFolder = addVersionedFolder("revoked-below");
    Node child = addFileWithList(versionedFolder, "child.txt", Map.of(GROUP, EDIT_REMOVE, "member", READ));
    Node deeper = addFileWithList(versionedFolder.addNode("sub", "nt:folder"),
                                  "deeper.txt",
                                  Map.of(GROUP, EDIT_REMOVE, "member", READ));
    versionedFolder.checkin();
    versionedFolder.checkout();
    ((ExtendedNode) child).setPermissions(Map.of(GROUP, EDIT_REMOVE, "guest", READ));
    ((ExtendedNode) deeper).setPermissions(Map.of(GROUP, EDIT_REMOVE, "guest", READ));
    systemSession.save();

    handler.uncheckout(userSession, "/uncheckoutSpace/revoked-below");

    Node cancelled = reload(versionedFolder);
    Set<String> childEntries = entriesOf(cancelled.getNode("child.txt"));
    Set<String> deeperEntries = entriesOf(cancelled.getNode("sub/deeper.txt"));
    assertFalse(childEntries.contains("member read"), "the revoked share stays revoked: " + childEntries);
    assertTrue(childEntries.contains("guest read"));
    assertFalse(deeperEntries.contains("member read"), "the revoked share stays revoked below: " + deeperEntries);
    assertTrue(deeperEntries.contains("guest read"));
  }

  @Test
  void aListOfItsOwnGainedBelowAFolderAfterTheCheckoutIsKeptWhenTheCheckoutIsCancelled() throws Exception {
    Node versionedFolder = addVersionedFolder("gained-below");
    Node child = addFile(versionedFolder, "child.txt");
    systemSession.save();
    versionedFolder.checkin();
    versionedFolder.checkout();
    child.addMixin("exo:privilegeable");
    ((ExtendedNode) child).setPermissions(Map.of(GROUP, EDIT_REMOVE, "guest", READ));
    systemSession.save();

    handler.uncheckout(userSession, "/uncheckoutSpace/gained-below");

    Node cancelled = reload(versionedFolder);
    assertTrue(entriesOf(cancelled.getNode("child.txt")).contains("guest read"));
    assertFalse(cancelled.isCheckedOut());
  }

  @Test
  void aListOfItsOwnRemovedBelowAFolderAfterTheCheckoutStaysRemovedWhenTheCheckoutIsCancelled() throws Exception {
    Node versionedFolder = addVersionedFolder("removed-below");
    versionedFolder.addMixin("exo:privilegeable");
    ((ExtendedNode) versionedFolder).setPermissions(Map.of(GROUP, EDIT_REMOVE));
    systemSession.save();
    Node child = addFileWithList(versionedFolder, "child.txt", Map.of(GROUP, EDIT_REMOVE, "member", READ));
    versionedFolder.checkin();
    versionedFolder.checkout();
    child.removeMixin("exo:privilegeable");
    systemSession.save();

    handler.uncheckout(userSession, "/uncheckoutSpace/removed-below");

    Node cancelled = reload(versionedFolder);
    Node cancelledChild = cancelled.getNode("child.txt");
    assertFalse(cancelledChild.isNodeType("exo:privilegeable"), "the list of its own is not brought back");
    assertEquals(entriesOf(cancelled), entriesOf(cancelledChild));
    assertFalse(cancelled.isCheckedOut());
  }

  @Test
  void aFileBelowAFolderKeepsItsListWhenTheCheckoutOfTheFolderIsCancelled() throws Exception {
    Node versionedFolder = addVersionedFolder("untouched-below");
    Node child = addFileWithList(versionedFolder, "child.txt", Map.of(GROUP, EDIT_REMOVE, "member", READ));
    versionedFolder.checkin();
    versionedFolder.checkout();
    Set<String> before = entriesOf(child);

    handler.uncheckout(userSession, "/uncheckoutSpace/untouched-below");

    assertEquals(before, entriesOf(reload(versionedFolder).getNode("child.txt")));
  }

  private static Node addVersionedFolder(String name) throws Exception {
    Node versionedFolder = folder.addNode(name, "nt:folder");
    versionedFolder.addMixin("mix:versionable");
    systemSession.save();
    return versionedFolder;
  }

  private static Node addFileWithList(Node parent, String name, Map<String, String[]> permissions) throws Exception {
    Node file = addFile(parent, name);
    file.addMixin("exo:privilegeable");
    ((ExtendedNode) file).setPermissions(permissions);
    systemSession.save();
    return file;
  }

  private static Node addVersionedFile(String name) throws Exception {
    Node file = addFile(folder, name);
    file.addMixin("mix:versionable");
    systemSession.save();
    return file;
  }

  private static Node addFile(Node parent, String name) throws Exception {
    Node file = parent.addNode(name, "nt:file");
    Node resource = file.addNode("jcr:content", "nt:resource");
    resource.setProperty("jcr:mimeType", "text/plain");
    resource.setProperty("jcr:lastModified", Calendar.getInstance());
    resource.setProperty("jcr:data", new ByteArrayInputStream(name.getBytes(StandardCharsets.UTF_8)));
    return file;
  }

  private static Node reload(Node node) throws Exception {
    systemSession.refresh(false);
    return systemSession.getNodeByUUID(node.getUUID());
  }

  private static Set<String> entriesOf(Node node) throws Exception {
    return ((ExtendedNode) node).getACL()
                                .getPermissionEntries()
                                .stream()
                                .map(AccessControlEntry::getAsString)
                                .collect(Collectors.toSet());
  }
}
