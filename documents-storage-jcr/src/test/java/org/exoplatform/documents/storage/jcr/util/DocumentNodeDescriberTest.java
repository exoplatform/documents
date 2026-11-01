/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.storage.jcr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.AccessControlException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.documents.constant.DocumentNodeType;
import org.exoplatform.documents.model.DocumentNodeInfo;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

class DocumentNodeDescriberTest {

  private static final String LIBRARY = "/Groups/spaces/marketing/Documents";

  private ExtendedSession     session;

  private SpaceService        spaceService;

  private Map<String, Node>   nodes;

  @BeforeEach
  void setUp() throws RepositoryException {
    session = mock(ExtendedSession.class);
    spaceService = mock(SpaceService.class);
    nodes = new HashMap<>();
    when(session.getNodeByIdentifier(anyString())).thenAnswer(invocation -> {
      Node node = nodes.get(invocation.getArgument(0, String.class));
      if (node == null) {
        throw new ItemNotFoundException();
      }
      return node;
    });
    Space space = new Space();
    space.setId("7");
    when(spaceService.getSpaceByGroupId("/spaces/marketing")).thenReturn(space);
  }

  @Test
  void describesAFileInNestedFoldersNearestFirst() throws RepositoryException {
    Node library = node("lib", "nt:folder", LIBRARY, null);
    Node contracts = node("contracts", "nt:folder", LIBRARY + "/Contracts", library);
    Node year = node("y2026", "nt:unstructured", LIBRARY + "/Contracts/2026", contracts);
    node("file", "nt:file", LIBRARY + "/Contracts/2026/a.pdf", year);

    DocumentNodeInfo info = DocumentNodeDescriber.describe(session, List.of("file"), spaceService).get("file");

    assertEquals(new DocumentNodeInfo("file", DocumentNodeType.FILE, "file", DocumentNodeType.FILE, 7, List.of("y2026", "contracts"), false),
                 info);
  }

  @Test
  void describesAFileAtTheLibraryRootAndTheRootItself() throws RepositoryException {
    Node library = node("lib", "nt:folder", LIBRARY, null);
    node("file", "nt:file", LIBRARY + "/a.pdf", library);

    Map<String, DocumentNodeInfo> infos = DocumentNodeDescriber.describe(session, List.of("file", "lib"), spaceService);

    assertEquals(7, infos.get("file").spaceId());
    assertEquals(List.of(), infos.get("file").ancestorFolderIds());
    assertEquals(DocumentNodeType.FOLDER, infos.get("lib").type());
    assertEquals(7, infos.get("lib").spaceId());
    assertEquals(List.of(), infos.get("lib").ancestorFolderIds());
    assertTrue(infos.get("lib").libraryRoot());
    assertFalse(infos.get("file").libraryRoot());
  }

  @Test
  void describesAFolderWithItsParentsOnly() throws RepositoryException {
    Node library = node("lib", "nt:folder", LIBRARY, null);
    Node contracts = node("contracts", "nt:folder", LIBRARY + "/Contracts", library);
    node("y2026", "nt:folder", LIBRARY + "/Contracts/2026", contracts);

    Map<String, DocumentNodeInfo> infos = DocumentNodeDescriber.describe(session, List.of("y2026", "contracts"), spaceService);

    assertEquals(DocumentNodeType.FOLDER, infos.get("y2026").type());
    assertEquals(List.of("contracts"), infos.get("y2026").ancestorFolderIds());
    assertEquals(List.of(), infos.get("contracts").ancestorFolderIds());
    assertFalse(infos.get("contracts").libraryRoot());
  }

  @Test
  void placesNoSpaceOnPersonalDrivesTrashUnknownSpacesOrOtherGroupFolders() throws RepositoryException {
    node("private", "nt:file", "/Users/r___/ro___/root/Private/a.pdf", null);
    node("trashed", "nt:file", "/Trash/a.pdf", null);
    node("gone", "nt:file", "/Groups/spaces/deleted/Documents/a.pdf", null);
    node("other", "nt:file", "/Groups/spaces/marketing/ApplicationData/a.pdf", null);

    Map<String, DocumentNodeInfo> infos = DocumentNodeDescriber.describe(session,
                                                                         List.of("private", "trashed", "gone", "other"),
                                                                         spaceService);

    for (String id : List.of("private", "trashed", "gone", "other")) {
      assertEquals(0, infos.get(id).spaceId(), id);
      assertEquals(List.of(), infos.get(id).ancestorFolderIds(), id);
      assertEquals(DocumentNodeType.FILE, infos.get(id).type(), id);
    }
  }

  @Test
  void typesAVersionAsOtherWhateverItFroze() throws RepositoryException {
    node("version", "nt:frozenNode", "/jcr:system/jcr:versionStorage/x/1/jcr:frozenNode", null);
    node("versionEntry", "nt:version", "/jcr:system/jcr:versionStorage/x/1", null);

    Map<String, DocumentNodeInfo> infos = DocumentNodeDescriber.describe(session, List.of("version", "versionEntry"), spaceService);

    assertEquals(DocumentNodeType.OTHER, infos.get("version").type());
    assertEquals(DocumentNodeType.OTHER, infos.get("versionEntry").type());
  }

  @Test
  void leavesOutIdsNoNodeCarries() throws RepositoryException {
    assertTrue(DocumentNodeDescriber.describe(session, List.of("nothing"), spaceService).isEmpty());
  }

  /**
   * Pins that a repository failure is never read as "no node": the purge of
   * Forms values relies on an absent id meaning the node is gone.
   */
  @Test
  void throwsOnARepositoryFailure() throws RepositoryException {
    doThrow(new RepositoryException("down")).when(session).getNodeByIdentifier("broken");

    assertThrows(RepositoryException.class, () -> DocumentNodeDescriber.describe(session, List.of("broken"), spaceService));
  }

  /**
   * Pins that a shortcut resolves to the file it leads to, through a chain, and
   * that the target's own description comes with it.
   */
  @Test
  void resolvesAChainOfShortcutsToItsTarget() throws RepositoryException {
    Node library = node("lib", "nt:folder", LIBRARY, null);
    Node contracts = node("contracts", "nt:folder", LIBRARY + "/Contracts", library);
    node("file", "nt:file", LIBRARY + "/Contracts/a.pdf", contracts);
    symlink("link2", "/Users/u/Private/link2", "file");
    symlink("link1", "/Users/u/Private/link1", "link2");

    Map<String, DocumentNodeInfo> infos = DocumentNodeDescriber.describe(session, List.of("link1"), spaceService);

    assertEquals(new DocumentNodeInfo("link1", DocumentNodeType.SYMLINK, "file", DocumentNodeType.FILE, 0, List.of(), false),
                 infos.get("link1"));
    assertEquals(new DocumentNodeInfo("file", DocumentNodeType.FILE, "file", DocumentNodeType.FILE, 7, List.of("contracts"), false),
                 infos.get("file"));
    assertEquals(Set.of("link1", "file"), infos.keySet());
  }

  @Test
  void stopsALoopOfShortcutsAtItsLastShortcut() throws RepositoryException {
    symlink("a", "/Users/u/Private/a", "b");
    symlink("b", "/Users/u/Private/b", "a");

    Map<String, DocumentNodeInfo> infos = DocumentNodeDescriber.describe(session, List.of("a"), spaceService);

    assertEquals("b", infos.get("a").targetId());
    assertEquals(DocumentNodeType.SYMLINK, infos.get("a").targetType());
    assertFalse(infos.containsKey("b"));
  }

  @Test
  void givesNoTargetToABrokenShortcut() throws RepositoryException {
    symlink("a", "/Users/u/Private/a", "missing");

    DocumentNodeInfo info = DocumentNodeDescriber.describe(session, List.of("a"), spaceService).get("a");

    assertNull(info.targetId());
    assertNull(info.targetType());
  }

  @Test
  void resolvesEachLibrarySpaceOncePerCall() throws RepositoryException {
    Node library = node("lib", "nt:folder", LIBRARY, null);
    node("f1", "nt:file", LIBRARY + "/1.pdf", library);
    node("f2", "nt:file", LIBRARY + "/2.pdf", library);

    DocumentNodeDescriber.describe(session, List.of("f1", "f2", "f1"), spaceService);

    verify(spaceService, times(1)).getSpaceByGroupId("/spaces/marketing");
  }

  @Test
  void accessKeepsReadableFilesWithTheirEditRight() throws RepositoryException {
    node("editable", "nt:file", LIBRARY + "/e.pdf", null);
    node("readonly", "nt:file", LIBRARY + "/r.pdf", null);
    node("folder", "nt:folder", LIBRARY + "/F", null);
    node("version", "nt:frozenNode", "/jcr:system/v", null);
    doThrow(new AccessDeniedException()).when(session).getNodeByIdentifier("refused");
    doThrow(new AccessControlException("read only")).when(session).checkPermission(LIBRARY + "/r.pdf", PermissionType.SET_PROPERTY);

    Map<String, Boolean> access = DocumentNodeDescriber.getAccess(session,
                                                                  List.of("editable", "readonly", "folder", "version", "refused", "nothing"));

    assertEquals(Map.of("editable", true, "readonly", false), access);
  }

  @Test
  void accessThrowsOnARepositoryFailure() throws RepositoryException {
    doThrow(new RepositoryException("down")).when(session).getNodeByIdentifier("broken");

    assertThrows(RepositoryException.class, () -> DocumentNodeDescriber.getAccess(session, List.of("broken")));
  }

  /**
   * Registers a node the session finds by its id.
   *
   * @param id its identifier
   * @param primaryType its primary type
   * @param path its path
   * @param parent its parent, or null
   * @return the node
   */
  private Node node(String id, String primaryType, String path, Node parent) throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    NodeType nodeType = mock(NodeType.class);
    when(nodeType.getName()).thenReturn(primaryType);
    when(node.getPrimaryNodeType()).thenReturn(nodeType);
    when(node.getPath()).thenReturn(path);
    when(node.getIdentifier()).thenReturn(id);
    when(node.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(false);
    if (parent != null) {
      when(node.getParent()).thenReturn(parent);
    }
    nodes.put(id, node);
    return node;
  }

  /**
   * Registers a shortcut the session finds by its id.
   *
   * @param id its identifier
   * @param path its path
   * @param targetId the identifier it points to
   */
  private void symlink(String id, String path, String targetId) throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    NodeType nodeType = mock(NodeType.class);
    when(nodeType.getName()).thenReturn("exo:symlink");
    when(node.getPrimaryNodeType()).thenReturn(nodeType);
    when(node.getPath()).thenReturn(path);
    when(node.getIdentifier()).thenReturn(id);
    when(node.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true);
    Property property = mock(Property.class);
    when(property.getString()).thenReturn(targetId);
    when(node.hasProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(true);
    when(node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(property);
    nodes.put(id, node);
  }
}
