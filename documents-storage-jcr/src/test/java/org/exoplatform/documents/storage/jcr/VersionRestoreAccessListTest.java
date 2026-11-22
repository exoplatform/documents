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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.version.Version;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * Restoring a document's version brings back that version, but the document
 * keeps the access list it has at the time of the restore, and the version
 * the restore creates carries that list.
 */
class VersionRestoreAccessListTest {

  private static final String           SPACE_GROUP = "/spaces/restorers";

  /** May restore the space's documents, not change their access. */
  private static final Identity         EDITOR      = new Identity("editor", List.of(new MembershipEntry(SPACE_GROUP, "member")));

  /**
   * Holds the rights a restore needs, not the remove right that changing an
   * access list also needs
   */
  private static final String[]         EDIT        = new String[] { PermissionType.READ, PermissionType.ADD_NODE,
      PermissionType.SET_PROPERTY };

  /** The rights that also let the restore replace a file below a folder */
  private static final String[]         EDIT_REMOVE = new String[] { PermissionType.READ, PermissionType.ADD_NODE,
      PermissionType.SET_PROPERTY, PermissionType.REMOVE };

  private static final String[]         READ        = new String[] { PermissionType.READ };

  private static Session                systemSession;

  private static Node                   folder;

  private static JCRDocumentFileStorage storage;

  @BeforeAll
  static void setUpRepository() throws Exception {
    RepositoryService repositoryService = JcrAclTestContainer.start().getComponentInstanceOfType(RepositoryService.class);
    ManageableRepository repository = repositoryService.getDefaultRepository();
    repositoryService.setCurrentRepositoryName(repository.getConfiguration().getName());
    systemSession = repository.getSystemSession("collaboration");
    folder = systemSession.getRootNode().addNode("restoreSpace", "nt:folder");
    folder.addMixin("exo:privilegeable");
    ((ExtendedNode) folder).setPermissions(Map.of("*:" + SPACE_GROUP, EDIT));
    systemSession.save();
    IdentityRegistry identityRegistry = mock(IdentityRegistry.class);
    when(identityRegistry.getIdentity(EDITOR.getUserId())).thenReturn(EDITOR);
    IdentityManager identityManager = mock(IdentityManager.class);
    storage = new JCRDocumentFileStorage(null,
                                         repositoryService,
                                         null,
                                         identityManager,
                                         null,
                                         null,
                                         null,
                                         identityRegistry,
                                         null,
                                         null,
                                         null,
                                         null);
  }

  @AfterAll
  static void removeFolder() throws Exception {
    if (systemSession != null) {
      folder.remove();
      systemSession.save();
      systemSession.logout();
    }
  }

  /** The restore runs on the editor's request. */
  @BeforeEach
  void setEditor() {
    ConversationState.setCurrent(new ConversationState(EDITOR));
  }

  @AfterEach
  void resetEditor() {
    ConversationState.setCurrent(null);
  }

  @Test
  void aDocumentSharedAfterTheVersionKeepsItsShareWhenTheVersionIsRestored() throws Exception {
    Node document = addVersionedFile("shared-later.txt");
    Version firstVersion = document.checkin();
    document.checkout();
    document.addMixin("exo:privilegeable");
    ((ExtendedNode) document).setPermissions(Map.of("*:" + SPACE_GROUP, EDIT, "guest", new String[] { PermissionType.READ }));
    systemSession.save();

    storage.restoreVersion(firstVersion.getUUID(), EDITOR.getUserId());

    Node restored = reload(document);
    assertTrue(restored.isNodeType("exo:privilegeable"), "the document keeps a list of its own");
    assertTrue(entriesOf(restored).contains("guest read"), "the share made after the version is kept: " + entriesOf(restored));
    assertEquals(entriesOf(restored), frozenEntriesOf(restored.getBaseVersion()), "the new version carries the kept list");
  }

  @Test
  void aDocumentWhoseOwnListWasRemovedAfterTheVersionDoesNotGetItBack() throws Exception {
    Node document = addVersionedFile("unshared-later.txt");
    document.addMixin("exo:privilegeable");
    ((ExtendedNode) document).setPermissions(Map.of("*:" + SPACE_GROUP, EDIT, "guest", new String[] { PermissionType.READ }));
    systemSession.save();
    Version firstVersion = document.checkin();
    document.checkout();
    document.removeMixin("exo:privilegeable");
    systemSession.save();

    storage.restoreVersion(firstVersion.getUUID(), EDITOR.getUserId());

    Node restored = reload(document);
    assertFalse(restored.isNodeType("exo:privilegeable"), "the document still reads its folder's list");
    assertEquals(entriesOf(folder), entriesOf(restored));
    assertFalse(entriesOf(restored).contains("guest read"), "the version's share is not brought back");
    Node frozen = restored.getBaseVersion().getNode("jcr:frozenNode");
    assertFalse(frozenMixinsOf(restored.getBaseVersion()).contains("exo:privilegeable"),
                "the new version has no list of its own either");
    assertFalse(frozen.hasProperty("exo:permissions"));
  }

  @Test
  void theNewVersionHoldsTheDocumentAsTheRestoreLeavesIt() throws Exception {
    Node document = addVersionedFile("locked-later.txt");
    Version firstVersion = document.checkin();
    document.checkout();
    document.addMixin("mix:lockable");
    systemSession.save();

    storage.restoreVersion(firstVersion.getUUID(), EDITOR.getUserId());

    Node restored = reload(document);
    Set<String> mixins = Arrays.stream(restored.getMixinNodeTypes()).map(type -> type.getName()).collect(Collectors.toSet());
    assertEquals(mixins, frozenMixinsOf(restored.getBaseVersion()));
  }

  @Test
  void aShareRevokedAfterTheVersionIsNotGivenBackByTheRestore() throws Exception {
    Node document = addVersionedFile("revoked-later.txt");
    document.addMixin("exo:privilegeable");
    ((ExtendedNode) document).setPermissions(Map.of("*:" + SPACE_GROUP, EDIT, "guest", new String[] { PermissionType.READ }));
    systemSession.save();
    Version firstVersion = document.checkin();
    document.checkout();
    ((ExtendedNode) document).setPermissions(Map.of("*:" + SPACE_GROUP, EDIT));
    systemSession.save();

    storage.restoreVersion(firstVersion.getUUID(), EDITOR.getUserId());

    Node restored = reload(document);
    assertFalse(entriesOf(restored).contains("guest read"), "the revoked share stays revoked: " + entriesOf(restored));
    assertEquals(entriesOf(restored), frozenEntriesOf(restored.getBaseVersion()), "the new version carries the kept list");
  }

  @Test
  void aUserGivenTheRightsAfterTheVersionRestoresItAndKeepsThem() throws Exception {
    Node document = addVersionedFile("editor-added-later.txt");
    document.addMixin("exo:privilegeable");
    ((ExtendedNode) document).setPermissions(Map.of("guest", new String[] { PermissionType.READ }));
    systemSession.save();
    Version firstVersion = document.checkin();
    document.checkout();
    ((ExtendedNode) document).setPermissions(Map.of("editor", EDIT, "guest", new String[] { PermissionType.READ }));
    systemSession.save();

    storage.restoreVersion(firstVersion.getUUID(), EDITOR.getUserId());

    Node restored = reload(document);
    assertTrue(entriesOf(restored).containsAll(Set.of("editor add_node", "editor set_property", "guest read")),
               "the rights given after the version are kept: " + entriesOf(restored));
  }

  /**
   * A document has a list of its own or reads its folder's: the restore keeps
   * either, and never meets an emptied list
   */
  @Test
  void theRepositoryKeepsNoEmptiedListOfItsOwn() throws Exception {
    Node document = addVersionedFile("never-emptied.txt");
    document.addMixin("exo:privilegeable");
    ((ExtendedNode) document).setPermissions(Map.of("*:" + SPACE_GROUP, EDIT, "guest", new String[] { PermissionType.READ }));
    systemSession.save();
    try {
      ((ExtendedNode) document).removePermission("*:" + SPACE_GROUP);
      ((ExtendedNode) document).removePermission("guest");

      assertTrue(assertThrows(RepositoryException.class, document::save).getMessage().contains("wrong formed ACL"));
      assertTrue(assertThrows(RepositoryException.class, systemSession::save).getMessage().contains("wrong formed ACL"));
    } finally {
      systemSession.refresh(false);
    }
    assertEquals(Set.of("*:" + SPACE_GROUP + " read", "*:" + SPACE_GROUP + " add_node", "*:" + SPACE_GROUP + " set_property",
                        "guest read"),
                 entriesOf(reload(document)));
  }

  @Test
  void aShareRevokedBelowAFolderAfterTheVersionIsNotGivenBackByTheRestore() throws Exception {
    Node versionedFolder = addVersionedFolder("revoked-below");
    Node child = addFileWithList(versionedFolder, "child.txt", Map.of("*:" + SPACE_GROUP, EDIT_REMOVE, "member", READ));
    Node deeper = addFileWithList(versionedFolder.addNode("sub", "nt:folder"),
                                  "deeper.txt",
                                  Map.of("*:" + SPACE_GROUP, EDIT_REMOVE, "member", READ));
    Version firstVersion = versionedFolder.checkin();
    versionedFolder.checkout();
    ((ExtendedNode) child).setPermissions(Map.of("*:" + SPACE_GROUP, EDIT_REMOVE, "guest", READ));
    ((ExtendedNode) deeper).setPermissions(Map.of("*:" + SPACE_GROUP, EDIT_REMOVE, "guest", READ));
    systemSession.save();

    storage.restoreVersion(firstVersion.getUUID(), EDITOR.getUserId());

    Node restored = reload(versionedFolder);
    Set<String> childEntries = entriesOf(restored.getNode("child.txt"));
    Set<String> deeperEntries = entriesOf(restored.getNode("sub/deeper.txt"));
    assertFalse(childEntries.contains("member read"), "the revoked share stays revoked: " + childEntries);
    assertTrue(childEntries.contains("guest read"));
    assertFalse(deeperEntries.contains("member read"), "the revoked share stays revoked below: " + deeperEntries);
    assertTrue(deeperEntries.contains("guest read"));
  }

  @Test
  void aListOfItsOwnGainedBelowAFolderAfterTheVersionIsKeptByTheRestore() throws Exception {
    Node versionedFolder = addVersionedFolder("gained-below");
    Node child = versionedFolder.addNode("child.txt", "nt:file");
    Node resource = child.addNode("jcr:content", "nt:resource");
    resource.setProperty("jcr:mimeType", "text/plain");
    resource.setProperty("jcr:lastModified", Calendar.getInstance());
    resource.setProperty("jcr:data", new ByteArrayInputStream("c".getBytes(StandardCharsets.UTF_8)));
    systemSession.save();
    Version firstVersion = versionedFolder.checkin();
    versionedFolder.checkout();
    child.addMixin("exo:privilegeable");
    ((ExtendedNode) child).setPermissions(Map.of("*:" + SPACE_GROUP, EDIT_REMOVE, "guest", READ));
    systemSession.save();

    storage.restoreVersion(firstVersion.getUUID(), EDITOR.getUserId());

    assertTrue(entriesOf(reload(versionedFolder).getNode("child.txt")).contains("guest read"));
  }

  @Test
  void aListOfItsOwnRemovedBelowAFolderAfterTheVersionStaysRemovedWhenTheVersionIsRestored() throws Exception {
    Node versionedFolder = addVersionedFolder("removed-below");
    versionedFolder.addMixin("exo:privilegeable");
    ((ExtendedNode) versionedFolder).setPermissions(Map.of("*:" + SPACE_GROUP, EDIT_REMOVE));
    systemSession.save();
    Node child = addFileWithList(versionedFolder, "child.txt", Map.of("*:" + SPACE_GROUP, EDIT_REMOVE, "member", READ));
    Version firstVersion = versionedFolder.checkin();
    versionedFolder.checkout();
    child.removeMixin("exo:privilegeable");
    systemSession.save();

    storage.restoreVersion(firstVersion.getUUID(), EDITOR.getUserId());

    Node restored = reload(versionedFolder);
    Node restoredChild = restored.getNode("child.txt");
    assertFalse(restoredChild.isNodeType("exo:privilegeable"), "the list of its own is not brought back");
    assertEquals(entriesOf(restored), entriesOf(restoredChild));
  }

  @Test
  void aFileBelowAFolderKeepsItsListWhenAVersionOfTheFolderIsRestored() throws Exception {
    Node versionedFolder = addVersionedFolder("untouched-below");
    Node child = addFileWithList(versionedFolder, "child.txt", Map.of("*:" + SPACE_GROUP, EDIT_REMOVE, "member", READ));
    Version firstVersion = versionedFolder.checkin();
    versionedFolder.checkout();
    Set<String> before = entriesOf(child);

    storage.restoreVersion(firstVersion.getUUID(), EDITOR.getUserId());

    assertEquals(before, entriesOf(reload(versionedFolder).getNode("child.txt")));
  }

  private static Node addVersionedFolder(String name) throws Exception {
    Node versionedFolder = folder.addNode(name, "nt:folder");
    versionedFolder.addMixin("mix:versionable");
    systemSession.save();
    return versionedFolder;
  }

  private static Node addFileWithList(Node parent, String name, Map<String, String[]> permissions) throws Exception {
    Node file = parent.addNode(name, "nt:file");
    Node resource = file.addNode("jcr:content", "nt:resource");
    resource.setProperty("jcr:mimeType", "text/plain");
    resource.setProperty("jcr:lastModified", Calendar.getInstance());
    resource.setProperty("jcr:data", new ByteArrayInputStream(name.getBytes(StandardCharsets.UTF_8)));
    file.addMixin("exo:privilegeable");
    ((ExtendedNode) file).setPermissions(permissions);
    systemSession.save();
    return file;
  }

  private static Node addVersionedFile(String name) throws Exception {
    Node file = folder.addNode(name, "nt:file");
    Node resource = file.addNode("jcr:content", "nt:resource");
    resource.setProperty("jcr:mimeType", "text/plain");
    resource.setProperty("jcr:lastModified", Calendar.getInstance());
    resource.setProperty("jcr:data", new ByteArrayInputStream(name.getBytes(StandardCharsets.UTF_8)));
    file.addMixin("mix:versionable");
    systemSession.save();
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

  private static Set<String> frozenMixinsOf(Version version) throws Exception {
    Node frozen = version.getNode("jcr:frozenNode");
    if (!frozen.hasProperty("jcr:frozenMixinTypes")) {
      return Set.of();
    }
    return Arrays.stream(frozen.getProperty("jcr:frozenMixinTypes").getValues()).map(value -> {
      try {
        return value.getString();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }).collect(Collectors.toSet());
  }

  private static Set<String> frozenEntriesOf(Version version) throws Exception {
    Value[] values = version.getNode("jcr:frozenNode").getProperty("exo:permissions").getValues();
    return Arrays.stream(values).map(value -> {
      try {
        return value.getString();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }).collect(Collectors.toSet());
  }
}
