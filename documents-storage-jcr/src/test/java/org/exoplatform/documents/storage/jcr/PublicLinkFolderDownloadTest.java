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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.jcr.Node;
import javax.jcr.Session;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.StandaloneContainer;
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

  private static Session                systemSession;

  private static JCRDocumentFileStorage storage;

  private static String                 sharedFolderId;

  private static String                 restrictedFolderId;

  @BeforeAll
  static void setUpRepository() throws Exception {
    RepositoryService repositoryService = startContainer().getComponentInstanceOfType(RepositoryService.class);
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
    systemSession.save();
    addShortcut(shared, "other-space.txt", unreadableTarget);
    addShortcut(shared, "readable-target.txt", readableTarget);
    systemSession.save();
    sharedFolderId = ((ExtendedNode) shared).getIdentifier();
    restrictedFolderId = ((ExtendedNode) restricted).getIdentifier();

    storage = new JCRDocumentFileStorage(null, repositoryService, null, null, null, null, null, null, null, null, null);
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

  @Test
  void aFolderTheCreatorCannotReadIsNotFound() {
    assertThrows(ObjectNotFoundException.class, () -> storage.downloadFolder(restrictedFolderId, MEMBER));
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
