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
package org.exoplatform.documents.utils;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.junit.Test;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import java.util.*;

public class DocumentUtilsTest {

  private static final String GROUP_ID      = "/spaces/groupOne";

  private static final String GROUP_ID_REF  = "*:" + GROUP_ID;

  private static final String MANAGER_REF   = "manager:" + GROUP_ID;

  private static final String REDACTOR_REF  = "redactor:" + GROUP_ID;

  private static final String PUBLISHER_REF = "publisher:" + GROUP_ID;

  private static AccessControlList acl(List<AccessControlEntry> entries) {
    return new AccessControlList("root", entries);
  }

  @Test
  public void isSpacePermission_endsWithGroupId() {
    assertTrue(DocumentUtils.isSpacePermission("*:" + GROUP_ID, GROUP_ID));
    assertTrue(DocumentUtils.isSpacePermission("manager:" + GROUP_ID, GROUP_ID));
    assertTrue(DocumentUtils.isSpacePermission("redactor:" + GROUP_ID, GROUP_ID));
  }

  @Test
  public void isSpacePermission_doesNotEndWithGroupId() {
    assertFalse(DocumentUtils.isSpacePermission("*:/other", GROUP_ID));
    assertFalse(DocumentUtils.isSpacePermission("root", GROUP_ID));
  }

  @Test
  public void buildStandardOpenSpacePermissions() {
    Map<String, Set<String>> perms = DocumentUtils.buildStandardOpenSpacePermissions(GROUP_ID);
    assertEquals(1, perms.size());
    assertTrue(perms.containsKey(GROUP_ID_REF));
    assertEquals(new HashSet<>(Arrays.asList(PermissionType.ALL)), perms.get(GROUP_ID_REF));
  }

  @Test
  public void buildStandardRedactionalSpacePermissions() {
    Map<String, Set<String>> perms = DocumentUtils.buildStandardRedactionalSpacePermissions(GROUP_ID);
    assertEquals(4, perms.size());
    assertEquals(Collections.singleton(PermissionType.READ), perms.get(GROUP_ID_REF));
    assertEquals(new HashSet<>(Arrays.asList(PermissionType.ALL)), perms.get(MANAGER_REF));
    assertEquals(new HashSet<>(Arrays.asList(PermissionType.ALL)), perms.get(REDACTOR_REF));
    assertEquals(new HashSet<>(Arrays.asList(PermissionType.ALL)), perms.get(PUBLISHER_REF));
  }

  @Test
  public void buildOldStandardRedactionalSpacePermissions() {
    Map<String, Set<String>> perms = DocumentUtils.buildOldStandardRedactionalSpacePermissions(GROUP_ID);
    assertEquals(3, perms.size());
    assertEquals(Collections.singleton(PermissionType.READ), perms.get(GROUP_ID_REF));
    assertEquals(new HashSet<>(Arrays.asList(PermissionType.ALL)), perms.get(MANAGER_REF));
    assertEquals(new HashSet<>(Arrays.asList(PermissionType.ALL)), perms.get(REDACTOR_REF));
    assertNull(perms.get(PUBLISHER_REF));
  }

  @Test
  public void addPermission_newIdentity() {
    Map<String, Set<String>> map = new LinkedHashMap<>();
    DocumentUtils.addPermission(map, "root", "read");
    assertTrue(map.containsKey("root"));
    assertEquals(Collections.singleton("read"), map.get("root"));
  }

  @Test
  public void addPermission_existingIdentity() {
    Map<String, Set<String>> map = new LinkedHashMap<>();
    map.put("root", new LinkedHashSet<>(Collections.singletonList("read")));
    DocumentUtils.addPermission(map, "root", "add_node");
    assertEquals(2, map.get("root").size());
    assertTrue(map.get("root").contains("read"));
    assertTrue(map.get("root").contains("add_node"));
  }

  @Test
  public void toPermissionSet_singleString() {
    Set<String> set = DocumentUtils.toPermissionSet("read");
    assertEquals(Collections.singleton("read"), set);
  }

  @Test
  public void toPermissionSet_array() {
    String[] perms = { "read", "add_node" };
    Set<String> set = DocumentUtils.toPermissionSet(perms);
    assertEquals(new HashSet<>(Arrays.asList("read", "add_node")), set);
  }

  @Test
  public void toPermissionArrayMap() {
    Map<String, Set<String>> input = new LinkedHashMap<>();
    input.put("root", new LinkedHashSet<>(Arrays.asList("read", "add_node")));
    Map<String, String[]> result = DocumentUtils.toPermissionArrayMap(input);
    assertArrayEquals(new String[] { "read", "add_node" }, result.get("root"));
  }

  // --- JCR-dependent tests ---

  @Test
  public void getSpacePermissions() throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    List<AccessControlEntry> entries = new ArrayList<>();
    entries.add(new AccessControlEntry("root", "read"));
    entries.add(new AccessControlEntry(GROUP_ID_REF, "read"));
    entries.add(new AccessControlEntry(GROUP_ID_REF, "add_node"));
    when(node.getACL()).thenReturn(acl(entries));

    Map<String, Set<String>> result = DocumentUtils.getSpacePermissions(node, GROUP_ID);
    assertEquals(1, result.size());
    assertTrue(result.containsKey(GROUP_ID_REF));
    assertEquals(2, result.get(GROUP_ID_REF).size());
  }

  @Test
  public void getNonSpacePermissions() throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    List<AccessControlEntry> entries = new ArrayList<>();
    entries.add(new AccessControlEntry("root", "read"));
    entries.add(new AccessControlEntry(GROUP_ID_REF, "read"));
    when(node.getACL()).thenReturn(acl(entries));

    Map<String, Set<String>> result = DocumentUtils.getNonSpacePermissions(node, GROUP_ID);
    assertEquals(1, result.size());
    assertTrue(result.containsKey("root"));
  }

  @Test
  public void shouldRewriteSpacePermissions_nonPrivilegeable_returnsFalse() throws RepositoryException {
    Node node = mock(Node.class);
    when(node.isNodeType("exo:privilegeable")).thenReturn(false);
    assertFalse(DocumentUtils.shouldRewriteSpacePermissions(node, GROUP_ID, true));
  }

  @Test
  public void shouldRewriteSpacePermissions_openToRedactional_standardMatch_returnsTrue() throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    when(node.isNodeType("exo:privilegeable")).thenReturn(true);
    List<AccessControlEntry> entries = new ArrayList<>();
    for (String perm : PermissionType.ALL) {
      entries.add(new AccessControlEntry(GROUP_ID_REF, perm));
    }
    when(node.getACL()).thenReturn(acl(entries));
    assertTrue(DocumentUtils.shouldRewriteSpacePermissions(node, GROUP_ID, true));
  }

  @Test
  public void shouldRewriteSpacePermissions_redactionalToOpen_standardMatch_returnsTrue() throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    when(node.isNodeType("exo:privilegeable")).thenReturn(true);
    List<AccessControlEntry> entries = new ArrayList<>();
    entries.add(new AccessControlEntry(GROUP_ID_REF, PermissionType.READ));
    for (String perm : PermissionType.ALL) {
      entries.add(new AccessControlEntry(MANAGER_REF, perm));
      entries.add(new AccessControlEntry(REDACTOR_REF, perm));
      entries.add(new AccessControlEntry(PUBLISHER_REF, perm));
    }
    when(node.getACL()).thenReturn(acl(entries));
    assertTrue(DocumentUtils.shouldRewriteSpacePermissions(node, GROUP_ID, false));
  }

  @Test
  public void shouldRewriteSpacePermissions_customSpacePermissions_returnsFalse() throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    when(node.isNodeType("exo:privilegeable")).thenReturn(true);
    List<AccessControlEntry> entries = new ArrayList<>();
    entries.add(new AccessControlEntry(GROUP_ID_REF, PermissionType.ALL[0]));
    entries.add(new AccessControlEntry(GROUP_ID_REF, PermissionType.ALL[1]));
    when(node.getACL()).thenReturn(acl(entries));
    assertFalse(DocumentUtils.shouldRewriteSpacePermissions(node, GROUP_ID, true));
  }

  @Test
  public void shouldRewriteSpacePermissions_oldRedactionalToRedactional_standardMatch_returnsTrue() throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    when(node.isNodeType("exo:privilegeable")).thenReturn(true);
    List<AccessControlEntry> entries = new ArrayList<>();
    entries.add(new AccessControlEntry(GROUP_ID_REF, PermissionType.READ));
    for (String perm : PermissionType.ALL) {
      entries.add(new AccessControlEntry(MANAGER_REF, perm));
      entries.add(new AccessControlEntry(REDACTOR_REF, perm));
    }
    when(node.getACL()).thenReturn(acl(entries));
    assertTrue(DocumentUtils.shouldRewriteSpacePermissions(node, GROUP_ID, true));
  }

  @Test
  public void buildPermissionsForTargetMode_openToRedactional_preservesNonSpace() throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    when(node.isNodeType("exo:privilegeable")).thenReturn(true);
    List<AccessControlEntry> entries = new ArrayList<>();
    entries.add(new AccessControlEntry("root", "read"));
    entries.add(new AccessControlEntry("root", "add_node"));
    for (String perm : PermissionType.ALL) {
      entries.add(new AccessControlEntry(GROUP_ID_REF, perm));
    }
    when(node.getACL()).thenReturn(acl(entries));

    Map<String, String[]> result = DocumentUtils.buildPermissionsForTargetMode(node, GROUP_ID, true);
    assertTrue(result.containsKey("root"));
    assertArrayEquals(new String[] { PermissionType.READ }, result.get(GROUP_ID_REF));
    assertArrayEquals(PermissionType.ALL, result.get(MANAGER_REF));
    assertArrayEquals(PermissionType.ALL, result.get(REDACTOR_REF));
    assertArrayEquals(PermissionType.ALL, result.get(PUBLISHER_REF));
  }

  @Test
  public void hasIdentityPermission_exists_returnsTrue() throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    List<AccessControlEntry> entries = new ArrayList<>();
    entries.add(new AccessControlEntry(MANAGER_REF, "read"));
    when(node.getACL()).thenReturn(acl(entries));
    assertTrue(DocumentUtils.hasIdentityPermission(node, MANAGER_REF));
  }

  @Test
  public void hasIdentityPermission_notExists_returnsFalse() throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    List<AccessControlEntry> entries = new ArrayList<>();
    entries.add(new AccessControlEntry(MANAGER_REF, "read"));
    when(node.getACL()).thenReturn(acl(entries));
    assertFalse(DocumentUtils.hasIdentityPermission(node, REDACTOR_REF));
  }
}
