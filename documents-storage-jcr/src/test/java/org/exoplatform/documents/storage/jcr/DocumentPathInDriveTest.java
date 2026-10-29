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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Iterator;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.NamespaceRegistryImpl;
import org.exoplatform.services.jcr.impl.core.query.DefaultQueryNodeFactory;
import org.exoplatform.services.jcr.impl.core.query.sql.JCRSQLQueryBuilder;

class DocumentPathInDriveTest {

  private static final String SPACE_B_DRIVE_PATH = "/Groups/spaces/space_b/Documents";

  private static final String USER_DRIVE_PATH    = "/Users/u___/us___/use___/user/Private";

  private Session             session;

  private QueryManager        queryManager;

  private ExtendedNode        spaceADocuments;

  private ExtendedNode        folderA;

  private ExtendedNode        docA;

  @BeforeEach
  void setUp() throws Exception {
    session = mock(Session.class);
    Workspace workspace = mock(Workspace.class);
    queryManager = mock(QueryManager.class);
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getQueryManager()).thenReturn(queryManager);

    ExtendedNode root = node("rootId", "/", 0, null);
    ExtendedNode groups = node("groupsId", "/Groups", 1, root);
    ExtendedNode spaces = node("spacesId", "/Groups/spaces", 2, groups);
    ExtendedNode spaceA = node("spaceAId", "/Groups/spaces/space_a", 3, spaces);
    spaceADocuments = node("spaceADocumentsId", "/Groups/spaces/space_a/Documents", 4, spaceA);
    folderA = node("folderAId", "/Groups/spaces/space_a/Documents/folderA", 5, spaceADocuments);
    docA = node("docAId", "/Groups/spaces/space_a/Documents/folderA/docA", 6, folderA);
  }

  @Test
  void documentInTheDriveKeepsItsOwnPathWithoutQuery() throws Exception {
    ExtendedNode spaceBDoc = node("docBId", SPACE_B_DRIVE_PATH + "/docB", 5, null);

    assertEquals(SPACE_B_DRIVE_PATH + "/docB", JCRDocumentFileStorage.getDocumentPathInDrive(session, spaceBDoc, SPACE_B_DRIVE_PATH, true));
    verify(queryManager, never()).createQuery(anyString(), anyString());
  }

  @Test
  void documentOfAnotherDriveIsReachedThroughTheShortcutOfItsParentFolder() throws Exception {
    mockSymlinks(symlink("folderAId", SPACE_B_DRIVE_PATH + "/folderA"));

    assertEquals(SPACE_B_DRIVE_PATH + "/folderA/docA",
                 JCRDocumentFileStorage.getDocumentPathInDrive(session, docA, SPACE_B_DRIVE_PATH, true));
  }

  @Test
  void documentShortcutItselfIsUsedWhenTheDocumentIsTheTarget() throws Exception {
    mockSymlinks(symlink("docAId", SPACE_B_DRIVE_PATH + "/docA"));

    assertEquals(SPACE_B_DRIVE_PATH + "/docA", JCRDocumentFileStorage.getDocumentPathInDrive(session, docA, SPACE_B_DRIVE_PATH, true));
  }

  @Test
  void shortcutOfTheNearestAncestorWins() throws Exception {
    mockSymlinks(symlink("spaceADocumentsId", SPACE_B_DRIVE_PATH + "/spaceADocs"),
                 symlink("folderAId", SPACE_B_DRIVE_PATH + "/sub/folderA"));

    assertEquals(SPACE_B_DRIVE_PATH + "/sub/folderA/docA",
                 JCRDocumentFileStorage.getDocumentPathInDrive(session, docA, SPACE_B_DRIVE_PATH, true));
  }

  @Test
  void shortestShortcutPathWinsForTheSameTarget() throws Exception {
    mockSymlinks(symlink("folderAId", SPACE_B_DRIVE_PATH + "/deeper/folderA"), symlink("folderAId", SPACE_B_DRIVE_PATH + "/folderA"));

    assertEquals(SPACE_B_DRIVE_PATH + "/folderA/docA",
                 JCRDocumentFileStorage.getDocumentPathInDrive(session, docA, SPACE_B_DRIVE_PATH, true));
  }

  @Test
  void noShortcutOfTheDriveLeadsToTheDocument() throws Exception {
    mockSymlinks();

    assertNull(JCRDocumentFileStorage.getDocumentPathInDrive(session, docA, SPACE_B_DRIVE_PATH, true));
  }

  @Test
  void drivePathPrefixOfAnotherDriveIsNotTheDrive() throws Exception {
    ExtendedNode otherDriveDoc = node("otherId", SPACE_B_DRIVE_PATH + "2/docC", 5, null);
    mockSymlinks();

    assertNull(JCRDocumentFileStorage.getDocumentPathInDrive(session, otherDriveDoc, SPACE_B_DRIVE_PATH, true));
  }

  @Test
  void parentFolderShortcutIsUsedWhenOnlyTheParentMayBeTargeted() throws Exception {
    mockSymlinks(symlink("folderAId", USER_DRIVE_PATH + "/folderA"));

    assertEquals(USER_DRIVE_PATH + "/folderA/docA", JCRDocumentFileStorage.getDocumentPathInDrive(session, docA, USER_DRIVE_PATH, false));
    String statement = capturedStatement();
    assertTrue(statement.contains("exo:uuid = 'docAId'"));
    assertTrue(statement.contains("exo:uuid = 'folderAId'"));
    assertFalse(statement.contains("exo:uuid = 'spaceADocumentsId'"));
  }

  @Test
  void grandParentShortcutIsIgnoredWhenOnlyTheParentMayBeTargeted() throws Exception {
    mockSymlinks(symlink("spaceADocumentsId", USER_DRIVE_PATH + "/spaceADocs"));

    assertNull(JCRDocumentFileStorage.getDocumentPathInDrive(session, docA, USER_DRIVE_PATH, false));
  }

  @Test
  void ancestorsAboveAnUnreadableParentAreNotQueried() throws Exception {
    when(spaceADocuments.getParent()).thenThrow(new AccessDeniedException());
    mockSymlinks();

    JCRDocumentFileStorage.getDocumentPathInDrive(session, docA, SPACE_B_DRIVE_PATH, true);

    String statement = capturedStatement();
    assertTrue(statement.contains("exo:uuid = 'docAId'"));
    assertTrue(statement.contains("exo:uuid = 'folderAId'"));
    assertTrue(statement.contains("exo:uuid = 'spaceADocumentsId'"));
    assertFalse(statement.contains("spaceAId'"));
  }

  /**
   * Parses the statement under the repository's own JCR-SQL grammar without
   * executing it: how the engine evaluates the path LIKE and which index it
   * uses are not covered by this suite.
   */
  @Test
  void statementIsRestrictedToTheDriveAndParsesUnderJcrSqlGrammar() throws Exception {
    mockSymlinks();

    JCRDocumentFileStorage.getDocumentPathInDrive(session, docA, SPACE_B_DRIVE_PATH, true);

    String statement = capturedStatement();
    assertTrue(statement.contains("jcr:path LIKE '" + SPACE_B_DRIVE_PATH + "/%'"));
    assertNotNull(JCRSQLQueryBuilder.createQuery(statement,
                                                 new LocationFactory(new NamespaceRegistryImpl()),
                                                 new DefaultQueryNodeFactory()));
  }

  private String capturedStatement() throws Exception {
    ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
    verify(queryManager).createQuery(statement.capture(), eq(Query.SQL));
    return statement.getValue();
  }

  private void mockSymlinks(Node... symlinks) throws Exception {
    Query query = mock(Query.class);
    QueryResult queryResult = mock(QueryResult.class);
    when(queryManager.createQuery(anyString(), eq(Query.SQL))).thenReturn(query);
    when(query.execute()).thenReturn(queryResult);
    Iterator<Node> iterator = Arrays.asList(symlinks).iterator();
    NodeIterator nodeIterator = mock(NodeIterator.class);
    when(nodeIterator.hasNext()).thenAnswer(invocation -> iterator.hasNext());
    when(nodeIterator.nextNode()).thenAnswer(invocation -> iterator.next());
    when(queryResult.getNodes()).thenReturn(nodeIterator);
  }

  private static Node symlink(String targetId, String path) throws Exception {
    Node symlink = mock(Node.class);
    Property uuidProperty = mock(Property.class);
    when(uuidProperty.getString()).thenReturn(targetId);
    when(symlink.getProperty("exo:uuid")).thenReturn(uuidProperty);
    when(symlink.getPath()).thenReturn(path);
    return symlink;
  }

  private static ExtendedNode node(String id, String path, int depth, Node parent) throws Exception {
    ExtendedNode node = mock(ExtendedNode.class);
    when(node.getIdentifier()).thenReturn(id);
    when(node.getPath()).thenReturn(path);
    when(node.getDepth()).thenReturn(depth);
    when(node.getParent()).thenReturn(parent);
    return node;
  }
}
