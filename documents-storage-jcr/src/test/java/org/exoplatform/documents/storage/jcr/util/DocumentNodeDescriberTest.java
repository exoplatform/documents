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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.exoplatform.documents.constant.DocumentNodeType;
import org.exoplatform.documents.model.DocumentIndexInfo;
import org.exoplatform.documents.model.DocumentNodeInfo;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.NamespaceRegistryImpl;
import org.exoplatform.services.jcr.impl.core.query.DefaultQueryNodeFactory;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.exoplatform.services.jcr.impl.core.query.sql.JCRSQLQueryBuilder;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

class DocumentNodeDescriberTest {

  private static final String LIBRARY = "/Groups/spaces/marketing/Documents";

  private ExtendedSession     session;

  private SpaceService        spaceService;

  private Map<String, Node>   nodes;

  private final Set<Node>     vanishing  = new java.util.HashSet<>();

  private final Node          failingRow = mock(Node.class);

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
   * Pins that the folder access keeps only folders, a library root included,
   * with their edit right, and leaves out files, shortcuts, refused and
   * missing ids.
   */
  @Test
  void folderAccessKeepsReadableFoldersWithTheirEditRight() throws RepositoryException {
    node("editable", "nt:folder", LIBRARY + "/E", null);
    node("root", "nt:unstructured", LIBRARY, null);
    node("file", "nt:file", LIBRARY + "/f.pdf", null);
    symlink("link", LIBRARY + "/link", "editable");
    doThrow(new AccessDeniedException()).when(session).getNodeByIdentifier("refused");
    doThrow(new AccessControlException("read only")).when(session).checkPermission(LIBRARY, PermissionType.SET_PROPERTY);

    Map<String, Boolean> access = DocumentNodeDescriber.getFolderAccess(session,
                                                                        List.of("editable", "root", "file", "link", "refused", "nothing"));

    assertEquals(Map.of("editable", true, "root", false), access);
  }

  /**
   * Pins that a repository failure other than a refused or missing node is
   * thrown, never read as a folder the user cannot open.
   */
  @Test
  void folderAccessThrowsOnARepositoryFailure() throws RepositoryException {
    doThrow(new RepositoryException("down")).when(session).getNodeByIdentifier("broken");

    assertThrows(RepositoryException.class, () -> DocumentNodeDescriber.getFolderAccess(session, List.of("broken")));
  }

  /**
   * Pins what a file's index description carries: its title, creator and
   * access owner, dates, the description held on its content, its library and
   * folders, and as readers its owner then every entry holding the read
   * permission, each once. Mutants: the read filter removed (the
   * {@code remove}-only membership comes in), the owner left out.
   */
  @Test
  void describesAFileForAnIndex() throws RepositoryException {
    Node library = node("lib", "nt:folder", LIBRARY, null);
    Node contracts = node("contracts", "nt:folder", LIBRARY + "/Contracts", library);
    ExtendedNode file = (ExtendedNode) node("file", "nt:file", LIBRARY + "/Contracts/a.pdf", contracts);
    stringProperty(file, NodeTypeConstants.EXO_TITLE, "Master agreement");
    stringProperty(file, NodeTypeConstants.EXO_OWNER, "mary");
    Calendar created = Calendar.getInstance();
    created.setTimeInMillis(1_000L);
    Property createdProperty = mock(Property.class);
    when(createdProperty.getDate()).thenReturn(created);
    when(file.hasProperty(NodeTypeConstants.EXO_DATE_CREATED)).thenReturn(true);
    when(file.getProperty(NodeTypeConstants.EXO_DATE_CREATED)).thenReturn(createdProperty);
    Node content = mock(Node.class);
    when(file.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(file.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(content);
    multiValuedProperty(content, NodeTypeConstants.DC_DESCRIPTION, "Signed in March", "older");
    when(file.getACL()).thenReturn(new AccessControlList("root",
                                                         List.of(new AccessControlEntry("mary", PermissionType.READ),
                                                                 new AccessControlEntry("mary", PermissionType.SET_PROPERTY),
                                                                 new AccessControlEntry("*:/spaces/marketing",
                                                                                        PermissionType.READ),
                                                                 new AccessControlEntry("manager:/spaces/marketing",
                                                                                        PermissionType.REMOVE),
                                                                 new AccessControlEntry("any", PermissionType.READ),
                                                                 new AccessControlEntry("root", PermissionType.READ))));

    DocumentIndexInfo info = DocumentNodeDescriber.describeForIndex(session, List.of("file"), spaceService).get("file");

    assertEquals(new DocumentIndexInfo("file",
                                       "Master agreement",
                                       "mary",
                                       "root",
                                       1_000L,
                                       1_000L,
                                       "Signed in March",
                                       null,
                                       7,
                                       List.of("contracts"),
                                       List.of("root", "mary", "*:/spaces/marketing", "any")),
                 info);
  }

  /**
   * Pins that only files are described for an index: a folder, a shortcut (even
   * to a described file), a version and a missing id are absent, and a file
   * asked twice is described once.
   */
  @Test
  void describesOnlyFilesForAnIndex() throws RepositoryException {
    Node library = node("lib", "nt:folder", LIBRARY, null);
    node("file", "nt:file", LIBRARY + "/a.pdf", library);
    symlink("link", "/Users/u/Private/link", "file");
    node("version", "nt:frozenNode", "/jcr:system/v", null);

    Map<String, DocumentIndexInfo> infos = DocumentNodeDescriber.describeForIndex(session,
                                                                                  List.of("lib", "file", "link", "version", "nothing", "file"),
                                                                                  spaceService);

    assertEquals(Set.of("file"), infos.keySet());
    assertEquals(List.of(), infos.get("file").readIdentities());
  }

  /**
   * Pins that a repository failure reaches the caller instead of leaving the
   * file out: a file left out of an index answer would read as deleted.
   */
  @Test
  void describeForIndexThrowsOnARepositoryFailure() throws RepositoryException {
    doThrow(new RepositoryException("down")).when(session).getNodeByIdentifier("broken");

    assertThrows(RepositoryException.class,
                 () -> DocumentNodeDescriber.describeForIndex(session, List.of("broken"), spaceService));
  }

  /**
   * Pins the page statements: every page orders by {@code jcr:uuid}, the first
   * reads every file carrying one, the next ones those after the cursor, and
   * a quote in the folder path or the cursor is escaped.
   */
  @Test
  void buildsTheFileListingStatements() {
    assertEquals("SELECT jcr:path, jcr:uuid FROM nt:file WHERE jcr:path LIKE '/Groups/spaces/o''brien/Documents/%' AND jcr:uuid IS NOT NULL ORDER BY jcr:uuid ASC",
                 DocumentNodeDescriber.listFileIdsStatement("/Groups/spaces/o'brien/Documents", null));
    assertEquals("SELECT jcr:path, jcr:uuid FROM nt:file WHERE jcr:path LIKE '/Groups/spaces/marketing/Documents/%' AND jcr:uuid > 'ab''c' ORDER BY jcr:uuid ASC",
                 DocumentNodeDescriber.listFileIdsStatement(LIBRARY, "ab'c"));
  }

  /**
   * Pins that both page statements parse under the repository's own JCR SQL
   * grammar. Parsing is not running: whether the engine orders and compares
   * {@code jcr:uuid} as the keyset needs is checked on a running repository.
   */
  @Test
  void fileListingStatementsParseUnderJcrSqlGrammar() throws Exception {
    for (String statement : List.of(DocumentNodeDescriber.listFileIdsStatement(LIBRARY + "/it's", null),
                                    DocumentNodeDescriber.listFileIdsStatement(LIBRARY, "a1b2"))) {
      assertNotNull(JCRSQLQueryBuilder.createQuery(statement,
                                                   new LocationFactory(new NamespaceRegistryImpl()),
                                                   new DefaultQueryNodeFactory()),
                    statement);
    }
  }

  /**
   * Pins that a page keeps only the files under the folder's own path, though
   * the index still placed a file there that has since moved out, and reads on
   * from the last row read until the page is full. Mutants: the path check
   * removed (the moved file comes in), the cursor taken from the last kept
   * file instead of the last row read.
   */
  @Test
  void listsTheFilesUnderTheFolderOnlyAndFillsThePage() throws RepositoryException {
    String folder = LIBRARY + "/Contracts";
    node("folder", "nt:folder", folder, null);
    List<String> statements = new ArrayList<>();
    List<Long> limits = new ArrayList<>();
    queryAnswers(statements,
                 limits,
                 rows(file("a", folder + "/a.pdf"), file("b", folder + "/sub/b.pdf"), file("x", LIBRARY + "/Archive/x.pdf")),
                 rows(file("c", folder + "/c.pdf")));

    List<String> fileIds = DocumentNodeDescriber.listFileIds(session, "folder", null, 3);

    assertEquals(List.of("a", "b", "c"), fileIds);
    assertEquals(List.of(DocumentNodeDescriber.listFileIdsStatement(folder, null),
                         DocumentNodeDescriber.listFileIdsStatement(folder, "x")),
                 statements);
    assertEquals(List.of(3L, 1L), limits);
  }

  /**
   * Pins that a page stops when the query returns fewer rows than asked, and
   * that it starts after the identifier it was given.
   */
  @Test
  void listsTheLastPageAfterTheCursor() throws RepositoryException {
    node("folder", "nt:folder", LIBRARY, null);
    List<String> statements = new ArrayList<>();
    queryAnswers(statements, new ArrayList<>(), rows(file("z", LIBRARY + "/z.pdf")));

    assertEquals(List.of("z"), DocumentNodeDescriber.listFileIds(session, "folder", "y", 5));
    assertEquals(List.of(DocumentNodeDescriber.listFileIdsStatement(LIBRARY, "y")), statements);
  }

  /**
   * Pins that a search index hit whose node no longer exists does not end the
   * walk: it counts in a full page, so the page is not short, and the next
   * query asks again for it plus the file still missing. Mutants: the end
   * decided from the nodes loaded (the walk stops after « a »), the unreadable
   * hit left out of the next limit (the second page reads only that hit, and
   * « b » is never read).
   */
  @Test
  void stepsOverAnUnreadableHitWithoutEndingTheWalk() throws RepositoryException {
    node("folder", "nt:folder", LIBRARY, null);
    List<String> statements = new ArrayList<>();
    List<Long> limits = new ArrayList<>();
    queryAnswers(statements,
                 limits,
                 rows(file("a", LIBRARY + "/a.pdf"), null),
                 rows(null, file("b", LIBRARY + "/b.pdf"), file("c", LIBRARY + "/c.pdf")));

    assertEquals(List.of("a", "b"), DocumentNodeDescriber.listFileIds(session, "folder", null, 2));
    assertEquals(List.of(DocumentNodeDescriber.listFileIdsStatement(LIBRARY, null),
                         DocumentNodeDescriber.listFileIdsStatement(LIBRARY, "a")),
                 statements);
    assertEquals(List.of(2L, 2L), limits);
  }

  /**
   * Pins that a page of unreadable hits only, which cannot move the cursor, is
   * read again with a wider limit instead of ending the walk or repeating the
   * same query.
   */
  @Test
  void widensAPageOfUnreadableHitsOnly() throws RepositoryException {
    node("folder", "nt:folder", LIBRARY, null);
    List<String> statements = new ArrayList<>();
    List<Long> limits = new ArrayList<>();
    queryAnswers(statements, limits, rows((Node) null), rows(null, file("a", LIBRARY + "/a.pdf")));

    assertEquals(List.of("a"), DocumentNodeDescriber.listFileIds(session, "folder", null, 1));
    assertEquals(List.of(1L, 2L), limits);
    assertEquals(DocumentNodeDescriber.listFileIdsStatement(LIBRARY, null), statements.get(1));
  }

  /**
   * Pins that a walk facing more unreadable hits after its cursor than it
   * steps over reports a repository failure instead of an empty page, which
   * the caller would read as the end of the folder. The page doubles while
   * it holds no readable hit, so the bound is reached in a few queries, and
   * no page is asked once the bound is passed.
   */
  @Test
  @Timeout(30)
  void reportsAnIndexWithTooManyUnreadableHits() throws RepositoryException {
    node("folder", "nt:folder", LIBRARY, null);
    List<Long> limits = new ArrayList<>();
    Workspace workspace = mock(Workspace.class);
    QueryManager queryManager = mock(QueryManager.class);
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    when(queryManager.createQuery(anyString(), eq(Query.SQL))).thenAnswer(invocation -> {
      long[] limit = new long[1];
      QueryImpl query = mock(QueryImpl.class);
      doAnswer(set -> {
        limit[0] = set.getArgument(0, Long.class);
        return limits.add(limit[0]);
      }).when(query).setLimit(org.mockito.ArgumentMatchers.anyLong());
      QueryResult result = mock(QueryResult.class);
      when(query.execute()).thenReturn(result);
      when(result.getRows()).thenAnswer(execute -> unreadableRows(limit[0]));
      return query;
    });

    assertThrows(RepositoryException.class, () -> DocumentNodeDescriber.listFileIds(session, "folder", null, 1));
    List<Long> doubling = new ArrayList<>();
    for (long page = 1; page <= DocumentNodeDescriber.MAX_UNREADABLE_HITS; page *= 2) {
      doubling.add(page);
    }
    doubling.add(2 * doubling.get(doubling.size() - 1));
    assertEquals(doubling, limits);
  }

  /**
   * Pins that a walk through runs of unreadable hits mixed with files returns
   * exactly {@code limit} files, in the index order, the pages doubling while
   * they hold no readable hit. Mutants: linear widening; no stop at the limit.
   */
  @Test
  void returnsTheLimitFromUnreadableAndReadableHitsInOrder() throws RepositoryException {
    node("folder", "nt:folder", LIBRARY, null);
    List<String> statements = new ArrayList<>();
    List<Long> limits = new ArrayList<>();
    queryAnswers(statements,
                 limits,
                 rows(null, null),
                 rows(null, null, null, null),
                 rows(null, null, null, null, file("a", LIBRARY + "/a.pdf"), null, file("b", LIBRARY + "/b.pdf"),
                      file("c", LIBRARY + "/c.pdf")));

    assertEquals(List.of("a", "b"), DocumentNodeDescriber.listFileIds(session, "folder", null, 2));
    assertEquals(List.of(2L, 4L, 8L), limits);
  }

  /**
   * Pins that a file in the trash is absent from an index description, both a
   * trashed file and a file under a trashed folder, so the index removes it as
   * a deleted file. Mutant: the trash check removed.
   */
  @Test
  void leavesTrashedFilesOutOfAnIndexDescription() throws RepositoryException {
    Node trashedFolder = node("folder", "nt:folder", "/Trash/Contracts", null);
    when(trashedFolder.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION)).thenReturn(true);
    when(trashedFolder.getDepth()).thenReturn(2);
    Node trash = node("trash", "nt:folder", "/Trash", null);
    when(trashedFolder.getParent()).thenReturn(trash);
    when(trash.getDepth()).thenReturn(1);
    Node root = node("root", "nt:unstructured", "/", null);
    when(trash.getParent()).thenReturn(root);
    Node underTrashedFolder = node("inside", "nt:file", "/Trash/Contracts/a.pdf", trashedFolder);
    when(underTrashedFolder.getDepth()).thenReturn(3);
    Node trashedFile = node("trashed", "nt:file", "/Trash/b.pdf", trash);
    when(trashedFile.getDepth()).thenReturn(2);
    when(trashedFile.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION)).thenReturn(true);
    Node library = node("lib", "nt:folder", LIBRARY, root);
    when(library.getDepth()).thenReturn(4);
    Node live = node("live", "nt:file", LIBRARY + "/c.pdf", library);
    when(live.getDepth()).thenReturn(5);

    Map<String, DocumentIndexInfo> infos = DocumentNodeDescriber.describeForIndex(session,
                                                                                  List.of("inside", "trashed", "live"),
                                                                                  spaceService);

    assertEquals(Set.of("live"), infos.keySet());
  }

  /**
   * Pins that no query runs for an id no node carries, or naming a file.
   */
  @Test
  void listsNothingUnderAMissingIdOrAFile() throws RepositoryException {
    node("file", "nt:file", LIBRARY + "/a.pdf", null);

    assertEquals(List.of(), DocumentNodeDescriber.listFileIds(session, "nothing", null, 10));
    assertEquals(List.of(), DocumentNodeDescriber.listFileIds(session, "file", null, 10));
    verify(session, never()).getWorkspace();
  }

  /**
   * @param name what the read asked for
   * @return the failure the engine's row raises for an item that no longer
   *         exists: a plain repository failure caused by the item's absence
   */
  private static RepositoryException missingItem(String name) {
    return new RepositoryException(name, new ItemNotFoundException(name));
  }

  /**
   * Pins that a hit whose node is deleted between the reads of its path and
   * of its identifier is stepped over like any unreadable hit, the walk going
   * on to the next file.
   */
  @Test
  void stepsOverAFileDeletedBetweenTheReadsOfItsRow() throws RepositoryException {
    node("folder", "nt:folder", LIBRARY, null);
    Node gone = file("gone", LIBRARY + "/gone.pdf");
    vanishing.add(gone);
    List<Long> limits = new ArrayList<>();
    Node a = file("a", LIBRARY + "/a.pdf");
    queryAnswers(new ArrayList<>(), limits, rows(gone, a), rows(gone, a));

    assertEquals(List.of("a"), DocumentNodeDescriber.listFileIds(session, "folder", null, 1));
    assertEquals(List.of(1L, 2L), limits);
  }

  /**
   * Pins that a repository failure other than a missing item reaches the
   * caller instead of being stepped over as a stale hit. Mutant: every
   * failure read as a missing item.
   */
  @Test
  void reportsARepositoryFailureReadingARow() throws RepositoryException {
    node("folder", "nt:folder", LIBRARY, null);
    queryAnswers(new ArrayList<>(), new ArrayList<>(), rows(failingRow, file("a", LIBRARY + "/a.pdf")));

    assertThrows(RepositoryException.class, () -> DocumentNodeDescriber.listFileIds(session, "folder", null, 1));
  }

  /**
   * Answers the session's queries in turn, recording each statement and the
   * limit each query was given.
   *
   * @param statements the list receiving each statement run
   * @param limits the list receiving each query's limit
   * @param pages the rows each query returns, in order: a node per row, null
   *          for a hit whose node no longer exists
   */
  @SafeVarargs
  private void queryAnswers(List<String> statements, List<Long> limits, List<Node>... pages) throws RepositoryException {
    Workspace workspace = mock(Workspace.class);
    QueryManager queryManager = mock(QueryManager.class);
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    Iterator<List<Node>> answers = List.of(pages).iterator();
    when(queryManager.createQuery(anyString(), eq(Query.SQL))).thenAnswer(invocation -> {
      statements.add(invocation.getArgument(0, String.class));
      List<Node> rows = answers.hasNext() ? answers.next() : List.of();
      QueryImpl query = mock(QueryImpl.class);
      long[] limit = { Long.MAX_VALUE };
      doAnswer(set -> {
        limit[0] = set.getArgument(0, Long.class);
        return limits.add(limit[0]);
      }).when(query).setLimit(org.mockito.ArgumentMatchers.anyLong());
      QueryResult result = mock(QueryResult.class);
      when(query.execute()).thenReturn(result);
      when(result.getRows()).thenAnswer(execute -> rowIterator(rows.subList(0, (int) Math.min(rows.size(), limit[0]))));
      return query;
    });
  }

  /**
   * Builds a plain iterator over hits whose node no longer exists, without a
   * mock: a mock records every call, and a walk reads tens of thousands of
   * such rows before it reports the index.
   *
   * @param count the number of rows
   * @return an iterator over that many rows refusing their values as the
   *         engine does for a hit with no node
   */
  private static RowIterator unreadableRows(long count) {
    Row unreadable = new Row() {
      @Override
      public Value[] getValues() throws RepositoryException {
        throw missingItem("values");
      }

      @Override
      public Value getValue(String propertyName) throws RepositoryException {
        throw missingItem(propertyName);
      }
    };
    return new RowIterator() {
      private long position;

      @Override
      public Row nextRow() {
        if (position >= count) {
          throw new java.util.NoSuchElementException();
        }
        position++;
        return unreadable;
      }

      @Override
      public void skip(long skipNum) {
        position += skipNum;
      }

      @Override
      public long getSize() {
        return count;
      }

      @Override
      public long getPosition() {
        return position;
      }

      @Override
      public boolean hasNext() {
        return position < count;
      }

      @Override
      public Object next() {
        return nextRow();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }


  /**
   * @param nodes a node per row, null for a hit whose node no longer exists
   * @return the rows, in order
   */
  private static List<Node> rows(Node... nodes) {
    return java.util.Arrays.asList(nodes);
  }

  /**
   * @param nodes a node per row, null for a hit whose node no longer exists
   * @return an iterator over rows answering each node's path and identifier
   *         as {@code jcr:path} and {@code jcr:uuid}; for a hit with no node,
   *         refusing both as the engine does; for a vanishing node, answering
   *         its path and failing its identifier as the engine does for a node
   *         deleted between the two reads; for {@link #failingRow}, failing
   *         as a repository that cannot read its index
   */
  private RowIterator rowIterator(List<Node> nodes) throws RepositoryException {
    List<Row> rows = new ArrayList<>();
    for (Node node : nodes) {
      Row row = mock(Row.class);
      if (node == null) {
        when(row.getValue(anyString())).thenThrow(missingItem("row"));
      } else if (node == failingRow) {
        when(row.getValue(anyString())).thenThrow(new RepositoryException("index unavailable"));
      } else if (vanishing.contains(node)) {
        String pathString = node.getPath();
        Value path = mock(Value.class);
        when(path.getString()).thenReturn(pathString);
        when(row.getValue("jcr:path")).thenReturn(path);
        when(row.getValue("jcr:uuid")).thenThrow(new NullPointerException());
      } else {
        String pathString = node.getPath();
        String uuidString = ((ExtendedNode) node).getIdentifier();
        Value path = mock(Value.class);
        when(path.getString()).thenReturn(pathString);
        Value uuid = mock(Value.class);
        when(uuid.getString()).thenReturn(uuidString);
        when(row.getValue("jcr:path")).thenReturn(path);
        when(row.getValue("jcr:uuid")).thenReturn(uuid);
      }
      rows.add(row);
    }
    Iterator<Row> iterator = rows.iterator();
    RowIterator rowIterator = mock(RowIterator.class);
    when(rowIterator.hasNext()).thenAnswer(i -> iterator.hasNext());
    when(rowIterator.nextRow()).thenAnswer(i -> iterator.next());
    return rowIterator;
  }

  /**
   * @param id a file's identifier
   * @param path its path
   * @return a file row of a query, not registered in the session
   */
  private Node file(String id, String path) throws RepositoryException {
    ExtendedNode file = mock(ExtendedNode.class);
    when(file.getIdentifier()).thenReturn(id);
    when(file.getPath()).thenReturn(path);
    return file;
  }

  /**
   * Gives a node a single-valued string property.
   *
   * @param node the node
   * @param name the property's name
   * @param value its value
   */
  private void stringProperty(Node node, String name, String value) throws RepositoryException {
    Property property = mock(Property.class);
    when(property.getString()).thenReturn(value);
    when(node.hasProperty(name)).thenReturn(true);
    when(node.getProperty(name)).thenReturn(property);
  }

  /**
   * Gives a node a multi-valued string property.
   *
   * @param node the node
   * @param name the property's name
   * @param values its values
   */
  private void multiValuedProperty(Node node, String name, String... values) throws RepositoryException {
    Property property = mock(Property.class);
    PropertyDefinition definition = mock(PropertyDefinition.class);
    when(definition.isMultiple()).thenReturn(true);
    when(property.getDefinition()).thenReturn(definition);
    Value[] jcrValues = new Value[values.length];
    for (int i = 0; i < values.length; i++) {
      jcrValues[i] = mock(Value.class);
      when(jcrValues[i].getString()).thenReturn(values[i]);
    }
    when(property.getValues()).thenReturn(jcrValues);
    when(node.hasProperty(name)).thenReturn(true);
    when(node.getProperty(name)).thenReturn(property);
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
