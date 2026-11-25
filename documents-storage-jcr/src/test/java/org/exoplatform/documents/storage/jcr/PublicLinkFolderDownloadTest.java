/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
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
package org.exoplatform.documents.storage.jcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.StandaloneContainer;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.core.nodetype.ExtendedNodeTypeManager;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.services.security.MembershipEntry;

/**
 * The public-link folder download against a real JCR repository, whose engine
 * applies the nodes' permissions: the archive holds what the given user may
 * read, and nothing else (EXO-91530). A mocked session cannot show it, since
 * the filtering is the engine's.
 */
class PublicLinkFolderDownloadTest {

  private static final String           SYMLINK_NODE_TYPE = "<nodeTypes xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\" "
      + "xmlns:mix=\"http://www.jcp.org/jcr/mix/1.0\" xmlns:jcr=\"http://www.jcp.org/jcr/1.0\">"
      + "<nodeType name=\"exo:symlink\" isMixin=\"false\" hasOrderableChildNodes=\"false\" primaryItemName=\"\">"
      + "<supertypes><supertype>nt:hierarchyNode</supertype></supertypes><propertyDefinitions>"
      + "<propertyDefinition name=\"exo:workspace\" requiredType=\"String\" autoCreated=\"false\" mandatory=\"false\" "
      + "onParentVersion=\"COPY\" protected=\"false\" multiple=\"false\"><valueConstraints/></propertyDefinition>"
      + "<propertyDefinition name=\"exo:uuid\" requiredType=\"String\" autoCreated=\"false\" mandatory=\"true\" "
      + "onParentVersion=\"COPY\" protected=\"false\" multiple=\"false\"><valueConstraints/></propertyDefinition>"
      + "</propertyDefinitions></nodeType></nodeTypes>";

  /** A member of space1, neither a host of it nor a member of space2. */
  private static final Identity         MEMBER            = new Identity("mary",
                                                                         List.of(new MembershipEntry("/spaces/space1",
                                                                                                     "member")));

  /** A host of space1, not a member of space2. */
  private static final Identity         HOST              = new Identity("root1",
                                                                         List.of(new MembershipEntry("/spaces/space1",
                                                                                                     "manager")));

  /** A member of space2 only. */
  private static final Identity         SPACE2_MEMBER     = new Identity("paul",
                                                                         List.of(new MembershipEntry("/spaces/space2",
                                                                                                     "member")));

  /** The trash home the tests move documents to, created and removed by them. */
  private static final String           TRASH_HOME        = "/Trash";

  private static RepositoryService      repositoryService;

  private static Session                systemSession;

  /**
   * Stands in for {@code TrashStorageImpl}, whose {@code isInTrash} is a prefix
   * test on the trash home: a node is in the trash when its path is under
   * {@link #TRASH_HOME}.
   */
  private static TrashStorage           trashStorage;

  private static JCRDocumentFileStorage storage;

  private static String                 sharedFolderId;

  private static String                 restrictedFolderId;

  private static String                 sharedWithMemberId;

  @BeforeAll
  static void setUpRepository() throws Exception {
    repositoryService = startContainer().getComponentInstanceOfType(RepositoryService.class);
    trashStorage = Mockito.mock(TrashStorage.class);
    Mockito.when(trashStorage.isInTrash(ArgumentMatchers.any(Node.class)))
           .thenAnswer(invocation -> ((Node) invocation.getArgument(0)).getPath().startsWith(TRASH_HOME + "/"));
    ManageableRepository repository = repositoryService.getDefaultRepository();
    repositoryService.setCurrentRepositoryName(repository.getConfiguration().getName());
    systemSession = repository.getSystemSession(repository.getConfiguration().getDefaultWorkspaceName());
    ((ExtendedNodeTypeManager) systemSession.getWorkspace().getNodeTypeManager())
        .registerNodeTypes(new ByteArrayInputStream(SYMLINK_NODE_TYPE.getBytes(StandardCharsets.UTF_8)),
                           ExtendedNodeTypeManager.IGNORE_IF_EXISTS,
                           "text/xml");

    Node root = systemSession.getRootNode();
    // space1: its members may edit the shared folder, one sub-folder of which
    // is restricted to the space hosts and john
    Node space1 = root.addNode("space1", "nt:folder");
    setPermissions(space1, Map.of("*:/spaces/space1", PermissionType.ALL));
    Node shared = space1.addNode("Shared", "nt:folder");
    addFile(shared, "public.txt", "visible to every member");
    Node restricted = shared.addNode("Restricted", "nt:folder");
    setPermissions(restricted, Map.of("manager:/spaces/space1", PermissionType.ALL, "john", PermissionType.ALL));
    addFile(restricted, "hosts-only.txt", "hosts and john only");
    // a sub-folder shared with mary by her name, not by a membership
    Node forMember = shared.addNode("ForMary", "nt:folder");
    setPermissions(forMember, Map.of("mary", PermissionType.ALL));
    addFile(forMember, "for-mary.txt", "mary only");
    Node elsewhere = space1.addNode("Elsewhere", "nt:folder");
    Node readableTarget = addFile(elsewhere, "readable-target.txt", "every space1 member");
    // space2: neither mary nor root1 is a member
    Node space2 = root.addNode("space2", "nt:folder");
    setPermissions(space2, Map.of("*:/spaces/space2", PermissionType.ALL));
    Node unreadableTarget = addFile(space2, "other-space.txt", "space2 members only");
    // a space2 folder shared with mary by her name: she reads it, not its parent
    Node sharedWithMember = space2.addNode("SharedWithMary", "nt:folder");
    setPermissions(sharedWithMember, Map.of("*:/spaces/space2", PermissionType.ALL, "mary", PermissionType.ALL));
    addFile(sharedWithMember, "for-mary.txt", "space2 members and mary");
    Node sub = sharedWithMember.addNode("Sub", "nt:folder");
    Node inner = addFile(sub, "inner.txt", "space2 members and mary");
    systemSession.save();
    addShortcut(sharedWithMember, "inner-link.txt", inner);
    addShortcut(shared, "other-space.txt", unreadableTarget);
    addShortcut(shared, "readable-target.txt", readableTarget);
    systemSession.save();
    sharedFolderId = ((ExtendedNode) shared).getIdentifier();
    restrictedFolderId = ((ExtendedNode) restricted).getIdentifier();
    sharedWithMemberId = ((ExtendedNode) sharedWithMember).getIdentifier();

    storage = new JCRDocumentFileStorage(null, repositoryService, null, null, null, null, null, null, null, null, null, trashStorage);
  }

  @AfterAll
  static void closeSession() {
    if (systemSession != null) {
      systemSession.logout();
    }
  }

  /** The download runs on the visitor's request, which carries no user. */
  @BeforeEach
  void setAnonymousVisitor() {
    ConversationState.setCurrent(new ConversationState(new Identity(IdentityConstants.ANONIM)));
  }

  @AfterEach
  void resetVisitor() {
    ConversationState.setCurrent(null);
  }

  @Test
  void aMemberLinkLeavesOutTheRestrictedSubFolderAndTheUnreadableShortcutTarget() throws Exception {
    List<String> entries = zipEntries(storage.downloadFolder(sharedFolderId, MEMBER));

    assertTrue(entries.contains("Shared/public.txt"), "readable content stays in the zip: " + entries);
    assertTrue(entries.contains("Shared/readable-target.txt"), "a readable shortcut target stays in the zip: " + entries);
    assertTrue(entries.contains("Shared/ForMary/for-mary.txt"),
               "a sub-folder shared with the member by her name stays in the zip: " + entries);
    assertFalse(entries.stream().anyMatch(entry -> entry.contains("Restricted") || entry.contains("hosts-only")),
                "a sub-folder the member cannot read is left out: " + entries);
    assertFalse(entries.stream().anyMatch(entry -> entry.contains("other-space")),
                "a shortcut target the member cannot read is left out: " + entries);
  }

  /**
   * Two downloads of the same folder build two zips of their own, and leave no
   * temp folder behind.
   */
  @Test
  void twoDownloadsOfTheSameFolderBuildTheirOwnZipsAndLeaveNoTempFolder() throws Exception {
    java.util.Set<String> tempFoldersBefore = tempDownloadFolders();
    String first = storage.downloadFolder(sharedFolderId, MEMBER);
    String second = storage.downloadFolder(sharedFolderId, MEMBER);
    try {
      org.junit.jupiter.api.Assertions.assertNotEquals(first, second, "each download has a zip of its own");
      assertTrue(new File(first).isFile() && new File(second).isFile(), "both zips are there until they are sent");
    } finally {
      Files.deleteIfExists(new File(first).toPath());
      Files.deleteIfExists(new File(second).toPath());
    }
    assertEquals(tempFoldersBefore, tempDownloadFolders(), "no temp folder is left behind");
  }

  @Test
  void aHostLinkKeepsTheSubFolderTheHostCanRead() throws Exception {
    List<String> entries = zipEntries(storage.downloadFolder(sharedFolderId, HOST));

    assertTrue(entries.contains("Shared/public.txt"), "readable content stays in the zip: " + entries);
    assertTrue(entries.contains("Shared/Restricted/hosts-only.txt"), "a sub-folder the host can read stays in the zip: " + entries);
    assertFalse(entries.stream().anyMatch(entry -> entry.contains("ForMary")),
                "a sub-folder shared with another user by name is left out: " + entries);
    assertFalse(entries.stream().anyMatch(entry -> entry.contains("other-space")),
                "a shortcut target the host cannot read is left out: " + entries);
  }

  /**
   * The creator reads the folder and not its parent: the zip holds the same
   * entries, laid out the same way, as for a user who reads the parent too.
   */
  @Test
  void aFolderReadableUnderAnUnreadableParentKeepsTheZipLayout() throws Exception {
    org.exoplatform.services.jcr.core.ExtendedSession memberSession = sessionOf(MEMBER);
    try {
      assertThrows(javax.jcr.AccessDeniedException.class,
                   () -> memberSession.getNodeByIdentifier(sharedWithMemberId).getParent(),
                   "the fixture: the member cannot read the folder's parent");
    } finally {
      memberSession.logout();
    }

    List<String> memberEntries = zipEntries(storage.downloadFolder(sharedWithMemberId, MEMBER));
    List<String> space2MemberEntries = zipEntries(storage.downloadFolder(sharedWithMemberId, SPACE2_MEMBER));

    assertEquals(List.of("SharedWithMary/Sub/inner.txt", "SharedWithMary/for-mary.txt", "SharedWithMary/inner-link.txt"),
                 space2MemberEntries,
                 "the layout when the parent is readable");
    assertEquals(space2MemberEntries, memberEntries, "the same layout when the parent is not readable");
  }

  @Test
  void aFolderTheCreatorCannotReadIsNotFound() {
    assertThrows(ObjectNotFoundException.class, () -> storage.downloadFolder(restrictedFolderId, MEMBER));
  }

  /**
   * The download check reads the folder with the same rights as the download,
   * and builds nothing.
   */
  @Test
  void theFolderCheckAppliesTheCreatorRightsAndBuildsNothing() throws Exception {
    java.util.Set<String> tempFoldersBefore = tempDownloadFolders();
    storage.checkFolderReadable(sharedFolderId, MEMBER);
    storage.checkFolderReadable(sharedWithMemberId, MEMBER);
    storage.checkFolderReadable(restrictedFolderId, HOST);
    assertThrows(ObjectNotFoundException.class, () -> storage.checkFolderReadable(restrictedFolderId, MEMBER));
    assertEquals(tempFoldersBefore, tempDownloadFolders(), "the check copies nothing");
  }

  /**
   * The single-file copy the bulk download uses still strips its parent's
   * titles path, now handed to the walk as a string.
   */
  @Test
  void aFileIsCopiedAtItsPathBelowItsParent() throws Exception {
    File tempFolder = Files.createTempDirectory("public-link-file").toFile();
    try {
      Node file = systemSession.getRootNode().getNode("space1/Shared/public.txt");
      org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.createFile(file, "", "", tempFolder.getPath(), file.getParent());
      assertTrue(new File(tempFolder, "public.txt").isFile(), "the file is written at its name under the temp folder");
    } finally {
      org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.cleanFiles(tempFolder);
    }
  }

  /**
   * The archive written into a stream holds the same entries, with the same
   * content, as the zip built into a file, for every rights layout; its copy
   * stays until the archive is closed, and is deleted then.
   */
  @Test
  void theArchiveHoldsTheZipEntriesAndClosingItDeletesItsCopy() throws Exception {
    Object[][] cases = { { sharedFolderId, MEMBER }, { sharedFolderId, HOST }, { sharedWithMemberId, MEMBER } };
    for (Object[] link : cases) {
      String folderId = (String) link[0];
      Identity creator = (Identity) link[1];
      java.util.Set<String> tempFoldersBefore = tempDownloadFolders();
      Map<String, String> archived;
      try (org.exoplatform.documents.model.FolderArchive archive = storage.getFolderArchive(folderId, creator)) {
        assertEquals(tempFoldersBefore.size() + 1, tempDownloadFolders().size(), "the copy is there until the archive is closed");
        java.io.ByteArrayOutputStream zip = new java.io.ByteArrayOutputStream();
        archive.writeZip(zip);
        archived = zipContent(zip.toByteArray());
      }
      assertEquals(tempFoldersBefore, tempDownloadFolders(), "closing the archive deletes its copy");

      String zipPath = storage.downloadFolder(folderId, creator);
      Map<String, String> built;
      try {
        built = zipContent(Files.readAllBytes(new File(zipPath).toPath()));
      } finally {
        Files.deleteIfExists(new File(zipPath).toPath());
      }
      assertEquals(built, archived, "the archive holds the zip's entries for " + creator.getUserId());
    }
  }

  /** A client that leaves mid-download: the copy is deleted all the same. */
  @Test
  void anArchiveWhoseWritingFailsIsDeletedOnClose() throws Exception {
    java.util.Set<String> tempFoldersBefore = tempDownloadFolders();
    java.io.OutputStream clientGone = new java.io.OutputStream() {
      @Override
      public void write(int b) throws IOException {
        throw new IOException("client gone");
      }

      @Override
      public void write(byte[] bytes, int offset, int length) throws IOException {
        throw new IOException("client gone");
      }
    };
    try (org.exoplatform.documents.model.FolderArchive archive = storage.getFolderArchive(sharedFolderId, MEMBER)) {
      assertThrows(IOException.class, () -> archive.writeZip(clientGone));
    }
    assertEquals(tempFoldersBefore, tempDownloadFolders(), "no copy is left behind");
  }

  @Test
  void aFolderTheCreatorCannotReadHasNoArchiveAndCopiesNothing() throws Exception {
    java.util.Set<String> tempFoldersBefore = tempDownloadFolders();
    assertThrows(ObjectNotFoundException.class, () -> storage.getFolderArchive(restrictedFolderId, MEMBER));
    assertEquals(tempFoldersBefore, tempDownloadFolders(), "nothing is copied");
  }

  /** A document's content is copied into the stream, which is left open. */
  @Test
  void aDocumentContentIsWrittenIntoTheStream() throws Exception {
    String fileId = ((ExtendedNode) systemSession.getRootNode().getNode("space1/Shared/public.txt")).getIdentifier();
    java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
    java.io.ByteArrayOutputStream content = new java.io.ByteArrayOutputStream() {
      @Override
      public void close() {
        closed.set(true);
      }
    };
    long length = storage.getDocumentContentLength(fileId);
    storage.writeDocumentContent(fileId, length, content);
    assertEquals("visible to every member", content.toString(StandardCharsets.UTF_8));
    assertFalse(closed.get(), "the caller owns the stream");

    // A content replaced since its size was announced is not written
    java.io.ByteArrayOutputStream replaced = new java.io.ByteArrayOutputStream();
    assertThrows(IOException.class, () -> storage.writeDocumentContent(fileId, length + 1, replaced));
    assertEquals(0, replaced.size(), "nothing is written");

    assertEquals("visible to every member".getBytes(StandardCharsets.UTF_8).length,
                 storage.getDocumentContentLength(fileId),
                 "the size is read without the content");
    assertThrows(ObjectNotFoundException.class, () -> storage.getDocumentContentLength(sharedFolderId), "a folder has no content");
    assertThrows(ObjectNotFoundException.class,
                 () -> storage.writeDocumentContent(sharedFolderId, 0, new java.io.ByteArrayOutputStream()),
                 "a folder has no content");
    assertThrows(ObjectNotFoundException.class,
                 () -> storage.writeDocumentContent(UUID.randomUUID().toString(), 0, new java.io.ByteArrayOutputStream()),
                 "no document has this id");
  }

  /**
   * A file that cannot be read while it is zipped: the zip sent so far has no
   * directory, so that it reads as broken, never as complete with the file
   * missing. The file is a link to nothing, which no user can open.
   */
  @Test
  void aZipCutShortByAnUnreadableFileHasNoDirectory(@TempDir java.nio.file.Path tempDir) throws Exception {
    File folder = Files.createDirectory(tempDir.resolve("zip-cut-short")).toFile();
    Files.writeString(new File(folder, "a.txt").toPath(), "a");
    Files.createSymbolicLink(new File(folder, "b.txt").toPath(), new File(folder, "missing").toPath());
    java.io.ByteArrayOutputStream zip = new java.io.ByteArrayOutputStream();
    assertThrows(IOException.class, () -> org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.zipFiles(folder, zip));
    File written = Files.write(tempDir.resolve("cut-short.zip"), zip.toByteArray()).toFile();
    assertThrows(java.util.zip.ZipException.class, () -> new ZipFile(written).close(), "no zip directory");
  }

  private static Map<String, String> zipContent(byte[] zip) throws IOException {
    Map<String, String> content = new java.util.TreeMap<>();
    try (java.util.zip.ZipInputStream entries = new java.util.zip.ZipInputStream(new ByteArrayInputStream(zip))) {
      for (ZipEntry entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry()) {
        content.put(entry.getName(), new String(entries.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return content;
  }

  private static org.exoplatform.services.jcr.core.ExtendedSession sessionOf(Identity identity) throws Exception {
    return (org.exoplatform.services.jcr.core.ExtendedSession) org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.openUserSession(
        StandaloneContainer.getInstance().getComponentInstanceOfType(RepositoryService.class), identity);
  }

  /**
   * The temp folders of downloads, in the folder the JVM creates temp files in,
   * which a probe file locates: that folder is read once by the JVM, and the
   * system property may have changed since.
   */
  private static java.util.Set<String> tempDownloadFolders() throws IOException {
    java.nio.file.Path probe = Files.createTempFile("probe", null);
    File tempDirectory = probe.getParent().toFile();
    Files.delete(probe);
    String[] names = tempDirectory.list((dir, name) -> name.startsWith(JCRDocumentFileStorage.TEMP_DOWNLOAD_FOLDER_PREFIX));
    return names == null ? java.util.Set.of() : java.util.Set.of(names);
  }

  private static List<String> zipEntries(String zipPath) throws IOException {
    assertNotNull(zipPath, "the zip is built");
    List<String> names = new ArrayList<>();
    try (ZipFile zip = new ZipFile(zipPath)) {
      zip.stream().map(ZipEntry::getName).sorted().forEach(names::add);
    } finally {
      Files.deleteIfExists(new File(zipPath).toPath());
    }
    return names;
  }

  private static StandaloneContainer startContainer() throws Exception {
    StandaloneContainer.addConfigurationURL(PublicLinkFolderDownloadTest.class.getResource("/jcr-acl/configuration.xml")
                                                                              .toString());
    if (System.getProperty("java.security.auth.login.config") == null) {
      File login = File.createTempFile("login", ".conf");
      login.deleteOnExit();
      Files.writeString(login.toPath(), "exo-domain { org.exoplatform.services.security.jaas.DefaultLoginModule required; };");
      System.setProperty("java.security.auth.login.config", login.getAbsolutePath());
    }
    // Only the kernel's and the JCR's own configurations: the test classpath
    // carries other modules' ones, which need services this test does not start
    ClassLoader parent = Thread.currentThread().getContextClassLoader();
    ClassLoader filtering = new ClassLoader(parent) {
      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
        if ("conf/configuration.xml".equals(name) || "conf/portal/configuration.xml".equals(name)) {
          List<URL> kept = new ArrayList<>();
          for (URL url : Collections.list(super.getResources(name))) {
            if (url.toString().contains("exo.kernel.component.common") || url.toString().contains("exo.jcr.component.core")) {
              kept.add(url);
            }
          }
          return Collections.enumeration(kept);
        }
        return super.getResources(name);
      }
    };
    Thread.currentThread().setContextClassLoader(filtering);
    try {
      return StandaloneContainer.getInstance();
    } finally {
      Thread.currentThread().setContextClassLoader(parent);
    }
  }

  private static void setPermissions(Node node, Map<String, String[]> permissions) throws Exception {
    node.addMixin("exo:privilegeable");
    ((ExtendedNode) node).setPermissions(permissions);
  }

  /** A document moved to the trash is reported so, and no longer once it is restored. */
  @Test
  void aDocumentIsInTheTrashOnlyWhileItLivesUnderTheTrashHome() throws Exception {
    Node root = systemSession.getRootNode();
    boolean trashHomeCreated = !root.hasNode("Trash");
    Node trashHome = trashHomeCreated ? root.addNode("Trash", "nt:folder") : root.getNode("Trash");
    Node document = addFile(root, "trash-check.txt", "content");
    systemSession.save();
    String documentId = ((ExtendedNode) document).getIdentifier();
    try {
      assertFalse(storage.isDocumentInTrash(documentId));

      systemSession.move("/trash-check.txt", trashHome.getPath() + "/trash-check.txt");
      systemSession.save();
      assertTrue(storage.isDocumentInTrash(documentId));

      systemSession.move(TRASH_HOME + "/trash-check.txt", "/trash-check.txt");
      systemSession.save();
      assertFalse(storage.isDocumentInTrash(documentId));
    } finally {
      removeIfExists("/trash-check.txt", TRASH_HOME + "/trash-check.txt");
      if (trashHomeCreated) {
        removeIfExists(TRASH_HOME);
      }
    }
  }

  /** No document has the id: nothing is trashed, which is not a failure to tell. */
  @Test
  void anIdNoDocumentHasIsNotInTheTrash() {
    assertFalse(storage.isDocumentInTrash(UUID.randomUUID().toString()));
  }

  /** A failure to tell closes the access: the document is reported as trashed. */
  @Test
  void aFailureToTellWhetherTheDocumentIsInTheTrashReportsItAsTrashed() throws Exception {
    TrashStorage failingTrashStorage = Mockito.mock(TrashStorage.class);
    Mockito.when(failingTrashStorage.isInTrash(ArgumentMatchers.any(Node.class))).thenThrow(new RepositoryException("down"));
    JCRDocumentFileStorage failingStorage =
                                          new JCRDocumentFileStorage(null, repositoryService, null, null, null, null, null, null, null, null, null, failingTrashStorage);

    assertTrue(failingStorage.isDocumentInTrash(sharedFolderId));
  }

  /**
   * A public folder archive leaves out a shortcut while its target is in the
   * trash, and holds it again once the target is back. The trash is simulated
   * by a raw move under the trash home: the real delete also sends a direct
   * shortcut to the trash, so the skip guards the shortcuts that stay in place
   * (a target in a trashed folder its deleter could not read, a failed
   * cascade, a shortcut restored while its target is still in the trash).
   */
  @Test
  void aShortcutIsLeftOutOfThePublicArchiveWhileItsTargetIsInTheTrash() throws Exception {
    Node root = systemSession.getRootNode();
    boolean trashHomeCreated = !root.hasNode("Trash");
    Node trashHome = trashHomeCreated ? root.addNode("Trash", "nt:folder") : root.getNode("Trash");
    // readable by the members, so that only the trash can leave the target out
    setPermissions(trashHome, Map.of("*:/spaces/space1", PermissionType.ALL));
    Node space1 = root.getNode("space1");
    Node folder = space1.addNode("WithShortcuts", "nt:folder");
    Node target = addFile(space1.getNode("Elsewhere"), "to-trash.txt", "trashed then restored");
    systemSession.save();
    addShortcut(folder, "to-trash-link.txt", target);
    systemSession.save();
    String folderId = ((ExtendedNode) folder).getIdentifier();
    try {
      assertTrue(zipEntries(storage.downloadFolder(folderId, MEMBER)).contains("WithShortcuts/to-trash-link.txt"),
                 "the fixture: the shortcut's target is archived while it lives");

      systemSession.move("/space1/Elsewhere/to-trash.txt", TRASH_HOME + "/to-trash.txt");
      systemSession.save();
      List<String> trashedEntries = zipEntries(storage.downloadFolder(folderId, MEMBER));
      assertFalse(trashedEntries.stream().anyMatch(entry -> entry.contains("to-trash")),
                  "a shortcut to a trashed document is left out: " + trashedEntries);

      systemSession.move(TRASH_HOME + "/to-trash.txt", "/space1/Elsewhere/to-trash.txt");
      systemSession.save();
      assertTrue(zipEntries(storage.downloadFolder(folderId, MEMBER)).contains("WithShortcuts/to-trash-link.txt"),
                 "the restored target is archived again");
    } finally {
      removeIfExists("/space1/WithShortcuts", "/space1/Elsewhere/to-trash.txt", TRASH_HOME + "/to-trash.txt");
      if (trashHomeCreated) {
        removeIfExists(TRASH_HOME);
      }
    }
  }

  private static void removeIfExists(String... paths) throws Exception {
    for (String path : paths) {
      if (systemSession.itemExists(path)) {
        systemSession.getItem(path).remove();
      }
    }
    systemSession.save();
  }

  private static Node addFile(Node parent, String name, String content) throws Exception {
    Node file = parent.addNode(name, "nt:file");
    Node resource = file.addNode("jcr:content", "nt:resource");
    resource.setProperty("jcr:mimeType", "text/plain");
    resource.setProperty("jcr:lastModified", Calendar.getInstance());
    resource.setProperty("jcr:data", new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    return file;
  }

  private static void addShortcut(Node parent, String name, Node target) throws Exception {
    Node shortcut = parent.addNode(name, "exo:symlink");
    shortcut.setProperty("exo:workspace", systemSession.getWorkspace().getName());
    shortcut.setProperty("exo:uuid", ((ExtendedNode) target).getIdentifier());
  }
}
