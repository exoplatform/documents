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
package org.exoplatform.documents.storage.jcr.listener;

import lombok.SneakyThrows;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestrictContentCreationSpaceListenerTest {

  RestrictContentCreationSpaceListener restrictContentCreationSpaceListener;

  String                               userId            = "userOne";

  SpaceService                         spaceService;

  RepositoryService                    repositoryService;

  NodeHierarchyCreator                 nodeHierarchyCreator;

  ManageableRepository                 repository;

  RepositoryEntry                      repositoryEntry;

  SessionProviderService               sessionProviderService;

  SessionProvider                      systemSessionProvider;

  ExtendedNode                         groupNode         = Mockito.mock(ExtendedNode.class);

  AccessControlList                    acl               = mock(AccessControlList.class);

  List<AccessControlEntry>             permissionEntries = new ArrayList<>();

  String                               memberShip2       = "*:/platform/users";

  String                               memberShip3       = "member:/organization/board";

  String                               groupId           = "/spaces/groupOne";

  String                               groupIdRef        = "*:" + groupId;

  String                               managerRef        = "manager:" + groupId;

  String                               redactorRef       = "redactor:" + groupId;

  String                               publisherRef      = "publisher:" + groupId;

  long                                 spaceId           = 1L;

  @BeforeEach
  void setUp() throws RepositoryException {
    spaceService = mock(SpaceService.class);
    repositoryService = mock(RepositoryService.class);
    nodeHierarchyCreator = mock(NodeHierarchyCreator.class);
    repository = mock(ManageableRepository.class);
    repositoryEntry = mock(RepositoryEntry.class);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("defaultWorkspace");
    systemSessionProvider = mock(SessionProvider.class);
    sessionProviderService = mock(SessionProviderService.class);

    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(systemSessionProvider);
    Session session = mock(Session.class);

    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(session.itemExists(anyString())).thenReturn(true);
    when(session.getItem(anyString())).thenReturn(groupNode);
    when(systemSessionProvider.getSession(anyString(), any())).thenReturn(session);
    NodeIterator nodeIterator = mock(NodeIterator.class);
    when(nodeIterator.hasNext()).thenReturn(false);
    when(groupNode.getNodes()).thenReturn(nodeIterator);
    when(groupNode.isNodeType("exo:privilegeable")).thenReturn(true);
    when(groupNode.getACL()).thenReturn(acl);
    this.restrictContentCreationSpaceListener = new RestrictContentCreationSpaceListener(spaceService,
                                                                                         repositoryService,
                                                                                         nodeHierarchyCreator,
                                                                                         sessionProviderService);

    permissionEntries.clear();
    permissionEntries.add(new AccessControlEntry("root", "read"));
    permissionEntries.add(new AccessControlEntry("root", "add_node"));
    permissionEntries.add(new AccessControlEntry("root", "set_property"));
    permissionEntries.add(new AccessControlEntry("root", "remove"));
    permissionEntries.add(new AccessControlEntry(memberShip2, "read"));
    permissionEntries.add(new AccessControlEntry(memberShip3, "read"));
    permissionEntries.add(new AccessControlEntry(memberShip3, "add_node"));
    permissionEntries.add(new AccessControlEntry(memberShip3, "set_property"));
    permissionEntries.add(new AccessControlEntry(memberShip3, "remove"));
    when(acl.getPermissionEntries()).thenReturn(permissionEntries);
  }

  private void addStandardOpenPermissionEntries() {
    for (String permission : PermissionType.ALL) {
      permissionEntries.add(new AccessControlEntry(groupIdRef, permission));
    }
    when(acl.getPermissionEntries()).thenReturn(permissionEntries);
  }

  private void addStandardRedactionalPermissionEntries() {
    permissionEntries.add(new AccessControlEntry(groupIdRef, PermissionType.READ));
    for (String permission : PermissionType.ALL) {
      permissionEntries.add(new AccessControlEntry(managerRef, permission));
      permissionEntries.add(new AccessControlEntry(redactorRef, permission));
      permissionEntries.add(new AccessControlEntry(publisherRef, permission));
    }
    when(acl.getPermissionEntries()).thenReturn(permissionEntries);
  }

  private Space createSpaceWithRedactors(boolean hasRedactors) {
    Space space = new Space();
    space.setId(spaceId);
    space.setGroupId(groupId);
    space.setRedactors(hasRedactors ? new String[] { "root" } : new String[0]);
    when(spaceService.hasRedactor(space)).thenReturn(hasRedactors);
    when(spaceService.getSpaceById(spaceId)).thenReturn(space);
    return space;
  }

  @Test
  void addRedactorUser_standardOpen_to_redactional() throws RepositoryException {
    Space space = createSpaceWithRedactors(true);
    addStandardOpenPermissionEntries();
    doNothing().when(groupNode).save();

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.ADD_REDACTOR_USER);
    restrictContentCreationSpaceListener.addRedactorUser(event);

    ArgumentCaptor<Map<String, String[]>> captor = ArgumentCaptor.forClass(Map.class);
    verify(groupNode).setPermissions(captor.capture());
    Map<String, String[]> perms = captor.getValue();
    assertNotNull(perms);
    assertArrayEquals(new String[] { PermissionType.READ }, perms.get("*:/spaces/groupOne"));
    assertArrayEquals(PermissionType.ALL, perms.get("manager:/spaces/groupOne"));
    assertArrayEquals(PermissionType.ALL, perms.get("redactor:/spaces/groupOne"));
    assertArrayEquals(PermissionType.ALL, perms.get("publisher:/spaces/groupOne"));
    verify(groupNode).save();
  }

  @Test
  void removeRedactorUser_standardRedactional_to_open() throws RepositoryException {
    Space space = createSpaceWithRedactors(false);
    addStandardRedactionalPermissionEntries();
    doNothing().when(groupNode).save();

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.REMOVE_REDACTOR_USER);
    restrictContentCreationSpaceListener.removeRedactorUser(event);

    ArgumentCaptor<Map<String, String[]>> captor = ArgumentCaptor.forClass(Map.class);
    verify(groupNode).setPermissions(captor.capture());
    Map<String, String[]> perms = captor.getValue();
    assertNotNull(perms);
    assertArrayEquals(PermissionType.ALL, perms.get("*:/spaces/groupOne"));
    verify(groupNode).save();
  }

  @SneakyThrows
  @Test
  void addRedactorUser_alreadyRedactional_skipped() {
    Space space = createSpaceWithRedactors(true);
    addStandardRedactionalPermissionEntries();

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.ADD_REDACTOR_USER);
    restrictContentCreationSpaceListener.addRedactorUser(event);

    verify(groupNode, never()).setPermissions(any());
  }

  @SneakyThrows
  @Test
  void removeRedactorUser_alreadyOpen_skipped() {
    Space space = createSpaceWithRedactors(false);
    addStandardOpenPermissionEntries();

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.REMOVE_REDACTOR_USER);
    restrictContentCreationSpaceListener.removeRedactorUser(event);

    verify(groupNode, never()).setPermissions(any());
  }

  @SneakyThrows
  @Test
  void addRedactorUser_nonPrivilegeable_skipped() {
    Space space = createSpaceWithRedactors(true);
    addStandardOpenPermissionEntries();
    when(groupNode.isNodeType("exo:privilegeable")).thenReturn(false);

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.ADD_REDACTOR_USER);
    restrictContentCreationSpaceListener.addRedactorUser(event);

    verify(groupNode, never()).setPermissions(any());
  }

  @SneakyThrows
  @Test
  void removeRedactorUser_nonPrivilegeable_skipped() {
    Space space = createSpaceWithRedactors(false);
    addStandardRedactionalPermissionEntries();
    when(groupNode.isNodeType("exo:privilegeable")).thenReturn(false);

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.REMOVE_REDACTOR_USER);
    restrictContentCreationSpaceListener.removeRedactorUser(event);

    verify(groupNode, never()).setPermissions(any());
  }

  @SneakyThrows
  @Test
  void customSpacePermissions_skipped() {
    Space space = createSpaceWithRedactors(true);
    // Add custom space permission (non-standard shape)
    permissionEntries.add(new AccessControlEntry(groupIdRef, PermissionType.ALL[0]));
    permissionEntries.add(new AccessControlEntry(groupIdRef, PermissionType.ALL[1]));
    permissionEntries.add(new AccessControlEntry(managerRef, PermissionType.ALL[0]));
    when(acl.getPermissionEntries()).thenReturn(permissionEntries);

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.ADD_REDACTOR_USER);
    restrictContentCreationSpaceListener.addRedactorUser(event);

    verify(groupNode, never()).setPermissions(any());
  }

  @SneakyThrows
  @Test
  void cancelled_whenSpaceStateChangedMidOperation() {
    Space space = createSpaceWithRedactors(true);
    addStandardOpenPermissionEntries();
    // Return a different space object with opposite state
    Space changedSpace = new Space();
    changedSpace.setRedactors(new String[0]);
    when(spaceService.getSpaceById(spaceId)).thenReturn(changedSpace);

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.ADD_REDACTOR_USER);
    restrictContentCreationSpaceListener.addRedactorUser(event);

    verify(groupNode, never()).setPermissions(any());
  }

  @Test
  void children_rewrittenWhenStandardPermissions() throws RepositoryException {
    Space space = createSpaceWithRedactors(true);
    addStandardOpenPermissionEntries();

    // Set up children
    ExtendedNode child1 = mock(ExtendedNode.class);
    when(child1.isNodeType("exo:privilegeable")).thenReturn(true);
    AccessControlList childAcl1 = mock(AccessControlList.class);
    List<AccessControlEntry> childEntries1 = new ArrayList<>();
    for (String permission : PermissionType.ALL) {
      childEntries1.add(new AccessControlEntry(groupIdRef, permission));
    }
    when(childAcl1.getPermissionEntries()).thenReturn(childEntries1);
    when(child1.getACL()).thenReturn(childAcl1);
    doNothing().when(child1).save();

    NodeIterator childIterator = mock(NodeIterator.class);
    when(childIterator.hasNext()).thenReturn(true, false);
    when(childIterator.nextNode()).thenReturn(child1);
    when(groupNode.getNodes()).thenReturn(childIterator);

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.ADD_REDACTOR_USER);
    restrictContentCreationSpaceListener.addRedactorUser(event);

    verify(child1).setPermissions(any());
    verify(child1).save();
  }

  @Test
  void children_skippedWhenNonStandardSpacePermissions() throws RepositoryException {
    Space space = createSpaceWithRedactors(true);
    addStandardOpenPermissionEntries();

    ExtendedNode child1 = mock(ExtendedNode.class);
    when(child1.isNodeType("exo:privilegeable")).thenReturn(true);
    AccessControlList childAcl1 = mock(AccessControlList.class);
    List<AccessControlEntry> childEntries1 = new ArrayList<>();
    // Non-standard: only some permissions for *:groupId, not all PermissionType.ALL
    childEntries1.add(new AccessControlEntry(groupIdRef, PermissionType.ALL[0]));
    childEntries1.add(new AccessControlEntry(groupIdRef, PermissionType.ALL[1]));
    when(childAcl1.getPermissionEntries()).thenReturn(childEntries1);
    when(child1.getACL()).thenReturn(childAcl1);

    NodeIterator childIterator = mock(NodeIterator.class);
    when(childIterator.hasNext()).thenReturn(true, false);
    when(childIterator.nextNode()).thenReturn(child1);
    when(groupNode.getNodes()).thenReturn(childIterator);

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.ADD_REDACTOR_USER);
    restrictContentCreationSpaceListener.addRedactorUser(event);

    verify(child1, never()).setPermissions(any());
  }

  @Test
  void childError_continuesToSiblings() throws RepositoryException {
    Space space = createSpaceWithRedactors(true);
    addStandardOpenPermissionEntries();

    ExtendedNode child1 = mock(ExtendedNode.class);
    when(child1.isNodeType("exo:privilegeable")).thenReturn(true);
    AccessControlList childAcl1 = mock(AccessControlList.class);
    List<AccessControlEntry> childEntries1 = new ArrayList<>();
    for (String permission : PermissionType.ALL) {
      childEntries1.add(new AccessControlEntry(groupIdRef, permission));
    }
    when(childAcl1.getPermissionEntries()).thenReturn(childEntries1);
    when(child1.getACL()).thenReturn(childAcl1);
    doThrow(new RepositoryException("child1 failed")).when(child1).save();

    ExtendedNode child2 = mock(ExtendedNode.class);
    when(child2.isNodeType("exo:privilegeable")).thenReturn(true);
    AccessControlList childAcl2 = mock(AccessControlList.class);
    List<AccessControlEntry> childEntries2 = new ArrayList<>();
    for (String permission : PermissionType.ALL) {
      childEntries2.add(new AccessControlEntry(groupIdRef, permission));
    }
    when(childAcl2.getPermissionEntries()).thenReturn(childEntries2);
    when(child2.getACL()).thenReturn(childAcl2);
    doNothing().when(child2).save();

    NodeIterator childIterator = mock(NodeIterator.class);
    when(childIterator.hasNext()).thenReturn(true, true, false);
    when(childIterator.nextNode()).thenReturn(child1, child2);
    when(groupNode.getNodes()).thenReturn(childIterator);

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.ADD_REDACTOR_USER);
    restrictContentCreationSpaceListener.addRedactorUser(event);

    // child1 failed (save threw) but we still process child2
    verify(child1).setPermissions(any());
    verify(child2).setPermissions(any());
    verify(child2).save();
  }
}
