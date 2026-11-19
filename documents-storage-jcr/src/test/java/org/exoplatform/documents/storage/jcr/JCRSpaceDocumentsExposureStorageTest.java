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
 *
*/
package org.exoplatform.documents.storage.jcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.exoplatform.documents.model.ExposedDocument;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.services.cms.documents.security.DocumentSpaceGuard;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.NamespaceRegistryImpl;
import org.exoplatform.services.jcr.impl.core.query.DefaultQueryNodeFactory;
import org.exoplatform.services.jcr.impl.core.query.sql.JCRSQLQueryBuilder;
import org.exoplatform.social.core.space.model.Space;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JCRSpaceDocumentsExposureStorageTest {

  private static final String              DRIVE_ROOT = "/Groups/spaces/team/Documents";

  private static final String              TRASH_HOME = "/Trash";

  @Mock
  private RepositoryService                repositoryService;

  @Mock
  private NodeHierarchyCreator             nodeHierarchyCreator;

  @Mock
  private TrashStorage                     trashStorage;

  @Mock
  private DocumentSpaceGuard               guard;

  @Mock
  private ExtendedSession                  session;

  @Mock
  private QueryManager                     queryManager;

  private Space                            space;

  private Space                            otherSpace;

  private Map<String, List<Node>>          queryResults;

  private JCRSpaceDocumentsExposureStorage storage;

  @BeforeEach
  void setUp() throws Exception {
    space = new Space();
    space.setId("1");
    space.setGroupId("/spaces/team");
    space.setMembers(new String[] { "mary", "root" });
    space.setManagers(new String[] { "root" });
    otherSpace = new Space();
    otherSpace.setId("2");
    otherSpace.setGroupId("/spaces/other");

    when(nodeHierarchyCreator.getJcrPath(anyString())).thenReturn("/Groups");
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    queryResults = new LinkedHashMap<>();
    when(queryManager.createQuery(anyString(), anyString())).thenAnswer(invocation -> {
      List<Node> nodes = queryResults.getOrDefault(invocation.getArgument(0), List.of());
      Query query = mock(Query.class);
      QueryResult result = mock(QueryResult.class);
      when(query.execute()).thenReturn(result);
      when(result.getNodes()).thenAnswer(nodesInvocation -> iterator(nodes));
      return query;
    });

    storage = new JCRSpaceDocumentsExposureStorage(repositoryService, nodeHierarchyCreator, trashStorage) {
      @Override
      protected DocumentSpaceGuard getDocumentSpaceGuard() {
        return guard;
      }

      @Override
      protected Session getSystemSession(SessionProvider sessionProvider) {
        return session;
      }
    };
  }

  @Test
  @DisplayName("The statement parses under the repository's own JCR-SQL grammar for each root; index usage on a real repository is not covered here")
  void statementsParseUnderTheRepositoryGrammar() throws Exception {
    for (String root : List.of(DRIVE_ROOT, TRASH_HOME, JCRSpaceDocumentsExposureStorage.QUARANTINE_ROOT)) {
      assertNotNull(JCRSQLQueryBuilder.createQuery(String.format(JCRSpaceDocumentsExposureStorage.ACCESS_LISTS_QUERY, root),
                                                   new LocationFactory(new NamespaceRegistryImpl()),
                                                   new DefaultQueryNodeFactory()));
    }
  }

  @Test
  @DisplayName("Two jcr:path clauses joined by OR are refused by the JCR-SQL grammar: one query per root")
  void twoPathClausesJoinedByOrDoNotParse() {
    String joined = "SELECT * FROM nt:base WHERE (jcr:path LIKE '/Trash/%' OR jcr:path LIKE '/Quarantine/%')"
        + " AND jcr:mixinTypes = 'exo:privilegeable'";
    assertThrows(javax.jcr.query.InvalidQueryException.class,
                 () -> JCRSQLQueryBuilder.createQuery(joined,
                                                      new LocationFactory(new NamespaceRegistryImpl()),
                                                      new DefaultQueryNodeFactory()));
  }

  @Test
  void sharedOutsideListsTheNodesOfTheSpaceWithAnOutsideEntry() throws Exception {
    driveRoot("*:/spaces/team", "*:/platform/administrators");
    ExtendedNode sharedWithAUser = node("shared", DRIVE_ROOT + "/shared.pdf", "nt:file", "owner");
    accessList(sharedWithAUser, "manager:/spaces/team", "owner", "paul");
    ExtendedNode keptInside = node("inside", DRIVE_ROOT + "/inside.pdf", "nt:file", "owner");
    accessList(keptInside, "manager:/spaces/team", "owner", "mary", "root", "*:/platform/administrators");
    ExtendedNode shortcut = node("shortcut", DRIVE_ROOT + "/shortcut", "exo:symlink", "owner");
    when(shortcut.isNodeType("exo:symlink")).thenReturn(true);
    accessList(shortcut, "any");
    ExtendedNode publicFolder = node("folder", DRIVE_ROOT + "/folder", "nt:folder", "owner");
    accessList(publicFolder, "*:/spaces/team", "any");
    ExtendedNode inheriting = node("inheriting", DRIVE_ROOT + "/folder/child.pdf", "nt:file", "owner");
    queryResults.put(String.format(JCRSpaceDocumentsExposureStorage.ACCESS_LISTS_QUERY, DRIVE_ROOT),
                     List.of(sharedWithAUser, keptInside, shortcut, publicFolder, inheriting));

    trashHome();
    ExtendedNode trashedOfTheSpace = node("trashed", TRASH_HOME + "/trashed.pdf", "nt:file", "owner");
    accessList(trashedOfTheSpace, "owner", "paul");
    when(guard.getDocumentSpace(trashedOfTheSpace)).thenReturn(space);
    ExtendedNode trashedOfAnotherSpace = node("otherTrashed", TRASH_HOME + "/other.pdf", "nt:file", "owner");
    accessList(trashedOfAnotherSpace, "any");
    when(guard.getDocumentSpace(trashedOfAnotherSpace)).thenReturn(otherSpace);
    ExtendedNode quarantined = node("quarantined", "/Quarantine/infected.pdf", "nt:file", "owner");
    accessList(quarantined, "*:/spaces/other");
    when(guard.getDocumentSpace(quarantined)).thenReturn(space);
    queryResults.put(String.format(JCRSpaceDocumentsExposureStorage.ACCESS_LISTS_QUERY, TRASH_HOME),
                     List.of(trashedOfTheSpace, trashedOfAnotherSpace));
    queryResults.put(String.format(JCRSpaceDocumentsExposureStorage.ACCESS_LISTS_QUERY, JCRSpaceDocumentsExposureStorage.QUARANTINE_ROOT),
                     List.of(quarantined));

    List<ExposedDocument> documents = storage.getSharedOutsideDocuments(space, Set.of("mary", "root")::contains);

    assertEquals(List.of("shared", "folder", "trashed", "quarantined"), documents.stream().map(ExposedDocument::id).toList());
    assertTrue(documents.get(1).folder());
    assertNull(documents.get(1).mimeType());
    assertFalse(documents.get(0).folder());
    assertEquals("owner", documents.get(0).owner());
  }

  @Test
  void sharedOutsideClassifiesTheDriveRootIdentitiesAsOutsideWhenTheDriveRootLacksThem() throws Exception {
    driveRoot("*:/spaces/team");
    ExtendedNode administratorsOnly = node("admins", DRIVE_ROOT + "/admins.pdf", "nt:file", "owner");
    accessList(administratorsOnly, "*:/platform/administrators");
    queryResults.put(String.format(JCRSpaceDocumentsExposureStorage.ACCESS_LISTS_QUERY, DRIVE_ROOT),
                     List.of(administratorsOnly));
    trashHome();

    assertEquals(List.of("admins"), storage.getSharedOutsideDocuments(space, Set.of("mary", "root")::contains).stream().map(ExposedDocument::id).toList());
  }

  @Test
  void sharedOutsideClassifiesUsersWithTheMembershipItIsGivenNotTheSpaceFields() throws Exception {
    driveRoot("*:/spaces/team");
    ExtendedNode sharedWithMary = node("mary", DRIVE_ROOT + "/mary.pdf", "nt:file", "owner");
    accessList(sharedWithMary, "mary");
    ExtendedNode sharedWithPaul = node("paul", DRIVE_ROOT + "/paul.pdf", "nt:file", "owner");
    accessList(sharedWithPaul, "paul");
    queryResults.put(String.format(JCRSpaceDocumentsExposureStorage.ACCESS_LISTS_QUERY, DRIVE_ROOT),
                     List.of(sharedWithMary, sharedWithPaul));
    trashHome();

    // the space's own fields list mary, the membership given lists paul
    List<ExposedDocument> documents = storage.getSharedOutsideDocuments(space, Set.of("paul")::contains);

    assertEquals(List.of("mary"), documents.stream().map(ExposedDocument::id).toList());
  }

  @Test
  void sharedOutsideFailsWhenTheDocumentsCannotBeRead() throws Exception {
    driveRoot("*:/spaces/team");
    when(queryManager.createQuery(anyString(), anyString())).thenThrow(new RepositoryException("down"));

    assertThrows(IllegalStateException.class, () -> storage.getSharedOutsideDocuments(space, Set.of("mary", "root")::contains));
  }

  @Test
  void spaceDocumentsKeepsTheExistingDocumentsOfTheSpaceInOrder() throws Exception {
    ExtendedNode first = node("b", DRIVE_ROOT + "/b.pdf", "nt:file", "owner");
    when(guard.getDocumentSpace(first)).thenReturn(space);
    ExtendedNode second = node("a", TRASH_HOME + "/a.pdf", "nt:file", "owner");
    when(guard.getDocumentSpace(second)).thenReturn(space);
    ExtendedNode otherSpaceDocument = node("other", "/Groups/spaces/other/Documents/c.pdf", "nt:file", "owner");
    when(guard.getDocumentSpace(otherSpaceDocument)).thenReturn(otherSpace);
    ExtendedNode personalDocument = node("personal", "/Users/u___/user/Private/d.pdf", "nt:file", "user");
    when(guard.getDocumentSpace(personalDocument)).thenReturn(null);
    when(session.getNodeByIdentifier("gone")).thenThrow(new ItemNotFoundException("gone"));

    List<ExposedDocument> documents = storage.getSpaceDocuments(space, List.of("b", "gone", "other", "a", "personal"));

    assertEquals(List.of("b", "a"), documents.stream().map(ExposedDocument::id).toList());
  }

  @Test
  void aShortcutOrAVersionWhoseSourceIsGoneIsPlacedByItsOwnPath() throws Exception {
    ExtendedNode deadShortcutHere = node("deadHere", DRIVE_ROOT + "/folder/shortcut", "exo:symlink", "owner");
    when(guard.getDocumentSpace(deadShortcutHere)).thenThrow(new ItemNotFoundException("target gone"));
    ExtendedNode deadShortcutElsewhere = node("deadElsewhere", "/Groups/spaces/other/Documents/shortcut", "exo:symlink", "owner");
    when(guard.getDocumentSpace(deadShortcutElsewhere)).thenThrow(new ItemNotFoundException("target gone"));
    ExtendedNode siblingDrive = node("sibling", "/Groups/spaces/team/DocumentsArchive/shortcut", "exo:symlink", "owner");
    when(guard.getDocumentSpace(siblingDrive)).thenThrow(new ItemNotFoundException("target gone"));
    ExtendedNode orphanVersion = node("version", "/jcr:system/jcr:versionStorage/a/1/jcr:frozenNode", "nt:frozenNode", "owner");
    when(guard.getDocumentSpace(orphanVersion)).thenThrow(new ItemNotFoundException("document gone"));
    Property frozenPrimaryType = mock(Property.class);
    when(frozenPrimaryType.getString()).thenReturn("nt:file");
    when(orphanVersion.getProperty("jcr:frozenPrimaryType")).thenReturn(frozenPrimaryType);
    ExtendedNode live = node("live", DRIVE_ROOT + "/live.pdf", "nt:file", "owner");
    when(guard.getDocumentSpace(live)).thenReturn(space);

    List<ExposedDocument> documents = storage.getSpaceDocuments(space,
                                                                List.of("deadHere", "deadElsewhere", "sibling", "version", "live"));

    assertEquals(List.of("deadHere", "live"), documents.stream().map(ExposedDocument::id).toList());
    assertNotNull(storage.prepareLinkRemoval(space, "deadHere", false));
    assertNull(storage.prepareLinkRemoval(space, "deadElsewhere", false));
  }

  @Test
  void aReadErrorOtherThanAMissingSourceStillFails() throws Exception {
    ExtendedNode unreadable = node("unreadable", DRIVE_ROOT + "/unreadable.pdf", "nt:file", "owner");
    when(guard.getDocumentSpace(unreadable)).thenThrow(new RepositoryException("repository down"));

    assertThrows(IllegalStateException.class, () -> storage.getSpaceDocuments(space, List.of("unreadable")));
    assertThrows(IllegalStateException.class, () -> storage.prepareLinkRemoval(space, "unreadable", true));
  }

  @Test
  void spaceDocumentsFailsWhenADocumentCannotBeRead() throws Exception {
    when(session.getNodeByIdentifier("broken")).thenThrow(new RepositoryException("broken"));

    assertThrows(IllegalStateException.class, () -> storage.getSpaceDocuments(space, List.of("broken")));
  }

  @Test
  void linkRemovalTakesEveryoneOffOnATurnOn() throws Exception {
    ExtendedNode document = node("doc", DRIVE_ROOT + "/doc.pdf", "nt:file", "owner");
    when(guard.getDocumentSpace(document)).thenReturn(space);
    accessList(document, "owner", "any", "*:/spaces/team");

    ExposedDocument prepared = storage.prepareLinkRemoval(space, "doc", true);

    assertEquals("owner", prepared.owner());
    InOrder order = inOrder(document);
    order.verify(document).removePermission("any");
    order.verify(document).save();
    verify(document, never()).removePermission("owner");
    verify(document, never()).checkout();
    // 55210 §4.8: through the permission API only, never the raw property,
    // whatever setProperty overload, nor a cleared list
    verify(document, never()).clearACL();
    assertTrue(mockingDetails(document).getInvocations()
                                       .stream()
                                       .noneMatch(invocation -> "setProperty".equals(invocation.getMethod().getName())
                                           && "exo:permissions".equals(invocation.getArguments()[0])));
  }

  @Test
  void linkRemovalChecksOutACheckedInDocumentBeforeTakingEveryoneOff() throws Exception {
    ExtendedNode document = node("doc", DRIVE_ROOT + "/doc.pdf", "nt:file", "owner");
    when(guard.getDocumentSpace(document)).thenReturn(space);
    when(document.isCheckedOut()).thenReturn(false);
    accessList(document, "owner", "any");

    storage.prepareLinkRemoval(space, "doc", true);

    InOrder order = inOrder(document);
    order.verify(document).checkout();
    order.verify(document).removePermission("any");
    order.verify(document).save();
  }

  @Test
  void linkRemovalLeavesEveryoneOnATurnOff() throws Exception {
    ExtendedNode document = node("doc", DRIVE_ROOT + "/doc.pdf", "nt:file", "owner");
    when(guard.getDocumentSpace(document)).thenReturn(space);
    accessList(document, "owner", "any");

    assertNotNull(storage.prepareLinkRemoval(space, "doc", false));

    verify(document, never()).removePermission(anyString());
    verify(document, never()).save();
  }

  @Test
  void linkRemovalWritesNothingOnADocumentWithoutEveryone() throws Exception {
    ExtendedNode inheriting = node("inheriting", DRIVE_ROOT + "/inheriting.pdf", "nt:file", "owner");
    when(guard.getDocumentSpace(inheriting)).thenReturn(space);
    ExtendedNode restricted = node("restricted", DRIVE_ROOT + "/restricted.pdf", "nt:file", "owner");
    when(guard.getDocumentSpace(restricted)).thenReturn(space);
    accessList(restricted, "owner", "paul");

    assertNotNull(storage.prepareLinkRemoval(space, "inheriting", true));
    assertNotNull(storage.prepareLinkRemoval(space, "restricted", true));

    verify(inheriting, never()).removePermission(anyString());
    verify(inheriting, never()).save();
    verify(restricted, never()).removePermission(anyString());
    verify(restricted, never()).save();
  }

  @Test
  void linkRemovalChangesNothingOnADocumentMovedOutOfTheSpace() throws Exception {
    ExtendedNode document = node("doc", "/Groups/spaces/other/Documents/doc.pdf", "nt:file", "owner");
    when(guard.getDocumentSpace(document)).thenReturn(otherSpace);
    accessList(document, "owner", "any");

    assertNull(storage.prepareLinkRemoval(space, "doc", true));

    verify(document, never()).removePermission(anyString());
    verify(document, never()).save();
  }

  @Test
  void linkRemovalChangesNothingOnADocumentGone() throws Exception {
    when(session.getNodeByIdentifier("gone")).thenThrow(new ItemNotFoundException("gone"));

    assertNull(storage.prepareLinkRemoval(space, "gone", true));
  }

  @Test
  void linkRemovalFailsWhenTheDocumentCannotBeRead() throws Exception {
    ExtendedNode document = node("doc", DRIVE_ROOT + "/doc.pdf", "nt:file", "owner");
    accessList(document, "owner", "any");
    when(guard.getDocumentSpace(document)).thenThrow(new RepositoryException("unreadable"));

    assertThrows(IllegalStateException.class, () -> storage.prepareLinkRemoval(space, "doc", true));

    verify(document, never()).removePermission(anyString());
    verify(document, never()).save();
  }

  private void driveRoot(String... identities) throws Exception {
    ExtendedNode driveRoot = mock(ExtendedNode.class);
    when(session.itemExists(DRIVE_ROOT)).thenReturn(true);
    when(session.getItem(DRIVE_ROOT)).thenReturn(driveRoot);
    accessList(driveRoot, identities);
  }

  private void trashHome() throws Exception {
    Node trashHome = mock(Node.class);
    when(trashHome.getPath()).thenReturn(TRASH_HOME);
    when(trashStorage.getTrashHomeNode()).thenReturn(trashHome);
  }

  private void accessList(Node node, String... identities) throws Exception {
    Map<String, List<String>> accessList = new LinkedHashMap<>();
    for (String identity : identities) {
      accessList.computeIfAbsent(identity, key -> new ArrayList<>()).add("read");
    }
    when(guard.getAccessList(node)).thenReturn(accessList);
  }

  private ExtendedNode node(String id, String path, String primaryType, String owner) throws Exception {
    ExtendedNode node = mock(ExtendedNode.class);
    when(node.getIdentifier()).thenReturn(id);
    when(node.getPath()).thenReturn(path);
    when(node.getName()).thenReturn(path.substring(path.lastIndexOf('/') + 1));
    NodeType nodeType = mock(NodeType.class);
    when(nodeType.getName()).thenReturn(primaryType);
    when(node.getPrimaryNodeType()).thenReturn(nodeType);
    when(node.isCheckedOut()).thenReturn(true);
    if (owner != null) {
      Property ownerProperty = mock(Property.class);
      when(ownerProperty.getString()).thenReturn(owner);
      when(node.hasProperty("exo:owner")).thenReturn(true);
      when(node.getProperty("exo:owner")).thenReturn(ownerProperty);
    }
    when(session.getNodeByIdentifier(id)).thenReturn(node);
    return node;
  }

  private static NodeIterator iterator(List<Node> nodes) {
    Iterator<Node> iterator = nodes.iterator();
    NodeIterator nodeIterator = mock(NodeIterator.class);
    when(nodeIterator.hasNext()).thenAnswer(invocation -> iterator.hasNext());
    when(nodeIterator.nextNode()).thenAnswer(invocation -> iterator.next());
    return nodeIterator;
  }

}
