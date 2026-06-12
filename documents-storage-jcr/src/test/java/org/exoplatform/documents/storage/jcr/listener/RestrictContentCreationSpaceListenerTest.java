package org.exoplatform.documents.storage.jcr.listener;

import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
class RestrictContentCreationSpaceListenerTest {

  RestrictContentCreationSpaceListener restrictContentCreationSpaceListener;

  String                               userId      = "userOne";

  RepositoryService                    repositoryService;

  NodeHierarchyCreator                 nodeHierarchyCreator;

  ManageableRepository                 repository;

  SessionProviderService               sessionProviderService;

  SessionProvider                      systemSessionProvider;

  ExtendedNode                         groupNode   = Mockito.mock(ExtendedNode.class);

  AccessControlList                    acl         = mock(AccessControlList.class);

  String                               memberShip1 = "member:/spaces/groupOne";

  String                               memberShip2 = "*:/platform/users";

  String                               memberShip3 = "member:/organization/board";

  String                               groupId     = "/spaces/groupOne";

  @BeforeEach
  void setUp() throws RepositoryException {
    repositoryService = mock(RepositoryService.class);
    nodeHierarchyCreator = mock(NodeHierarchyCreator.class);
    repository = mock(ManageableRepository.class);
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
    this.restrictContentCreationSpaceListener = new RestrictContentCreationSpaceListener(repositoryService,
                                                                                         nodeHierarchyCreator,
                                                                                         sessionProviderService);

    List<AccessControlEntry> permissionEntries = new ArrayList<>();
    permissionEntries.add(new AccessControlEntry("root", "read"));
    permissionEntries.add(new AccessControlEntry("root", "add_node"));
    permissionEntries.add(new AccessControlEntry("root", "set_property"));
    permissionEntries.add(new AccessControlEntry("root", "remove"));
    permissionEntries.add(new AccessControlEntry(memberShip1, "read"));
    permissionEntries.add(new AccessControlEntry(memberShip1, "add_node"));
    permissionEntries.add(new AccessControlEntry(memberShip1, "set_property"));
    permissionEntries.add(new AccessControlEntry(memberShip1, "remove"));
    permissionEntries.add(new AccessControlEntry(memberShip2, "read"));
    permissionEntries.add(new AccessControlEntry(memberShip3, "read"));
    permissionEntries.add(new AccessControlEntry(memberShip3, "add_node"));
    permissionEntries.add(new AccessControlEntry(memberShip3, "set_property"));
    permissionEntries.add(new AccessControlEntry(memberShip3, "remove"));
    when(acl.getPermissionEntries()).thenReturn(permissionEntries);
  }

  @Test
  void addRedactorUser() throws RepositoryException {
    Space space = new Space();
    space.setGroupId(groupId);
    space.setRedactors(new String[] { "root" });
    when(groupNode.getACL()).thenReturn(acl);

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.ADD_REDACTOR_USER);
    restrictContentCreationSpaceListener.addRedactorUser(event);
    ArgumentCaptor<Map<String, String[]>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(groupNode).setPermissions(argumentCaptor.capture());
    Map<String, String[]> capturedArgument = argumentCaptor.getValue();
    assertNotNull(capturedArgument);
    assertArrayEquals(new String[]{"read"}, capturedArgument.get("*:/spaces/groupOne"));
  }

  @Test
  void removeRedactorUser() throws RepositoryException {
    Space space = new Space();
    space.setGroupId(groupId);

    when(groupNode.getACL()).thenReturn(acl);

    SpaceLifeCycleEvent event = new SpaceLifeCycleEvent(space, userId, SpaceLifeCycleEvent.Type.REMOVE_REDACTOR_USER);
    restrictContentCreationSpaceListener.removeRedactorUser(event);
    ArgumentCaptor<Map<String, String[]>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(groupNode).setPermissions(argumentCaptor.capture());
    Map<String, String[]> capturedArgument = argumentCaptor.getValue();
    assertNotNull(capturedArgument);
    assertArrayEquals(PermissionType.ALL, capturedArgument.get("*:/spaces/groupOne"));
  }
}
