package org.exoplatform.documents.rest.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.exoplatform.documents.model.FileNode;
import org.exoplatform.documents.model.PermissionEntry;

import org.exoplatform.documents.model.PermissionRole;
import org.exoplatform.documents.service.PublicDocumentAccessService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.model.NodePermission;
import org.exoplatform.documents.rest.model.AbstractNodeEntity;
import org.exoplatform.documents.rest.model.IdentityEntity;
import org.exoplatform.documents.rest.model.NodePermissionEntity;
import org.exoplatform.documents.rest.model.PermissionEntryEntity;
import org.exoplatform.documents.rest.model.Visibility;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

@RunWith(MockitoJUnitRunner.Silent.class)
public class EntityBuilderTest {

    @Mock
    private DocumentFileService documentFileService;

    @Mock
    private SpaceService spaceService;

    @Mock
    private IdentityManager identityManager;

    @Mock
    private PublicDocumentAccessService publicDocumentAccessService;

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void toNodePermission() {
        AbstractNodeEntity abstractNodeEntity = new AbstractNodeEntity(true);
        NodePermissionEntity nodePermissionEntity = new NodePermissionEntity();
        NodePermission nodePermission = EntityBuilder.toNodePermission(abstractNodeEntity,documentFileService, spaceService, identityManager);
        assertNull(nodePermission);
        abstractNodeEntity.setAcl(nodePermissionEntity);
        Identity identity = mock(Identity.class);
        when(identity.getId()).thenReturn("1");
        Space space = new Space();
        space.setPrettyName("testspace");
        when(spaceService.getSpaceByGroupId("/spaces/testspace")).thenReturn(space);
        when(identityManager.getOrCreateSpaceIdentity("testspace")).thenReturn(identity);
        abstractNodeEntity.setPath("path/spaces/testspace");
        NodePermission nodePermission1 = EntityBuilder.toNodePermission(abstractNodeEntity,documentFileService, spaceService, identityManager);
        assertNull(nodePermission1);
        abstractNodeEntity.setPath("/Groups/spaces/testspace");
        PermissionEntryEntity permissionEntryEntity = new PermissionEntryEntity();
        IdentityEntity identityEntity = new IdentityEntity();
        identityEntity.setId("1");
        identityEntity.setRemoteId("remoteId");
        identityEntity.setProviderId("space");
        permissionEntryEntity.setIdentity(identityEntity);
        permissionEntryEntity.setPermission("read");
        nodePermissionEntity.setVisibilityChoice(Visibility.ALL_MEMBERS.name());
        nodePermissionEntity.setAllMembersCanEdit(true);
        when(identityManager.getOrCreateSpaceIdentity("remoteId")).thenReturn(identity);
        nodePermissionEntity.setCollaborators(List.of(permissionEntryEntity));
        NodePermission nodePermission2 = EntityBuilder.toNodePermission(abstractNodeEntity,documentFileService, spaceService, identityManager);
        assertNotNull(nodePermission2);
        //
        nodePermissionEntity.setVisibilityChoice(Visibility.SPECIFIC_COLLABORATOR.name());
        nodePermissionEntity.setAllMembersCanEdit(false);
        NodePermission specificCollabotratorsNodePermission = EntityBuilder.toNodePermission(abstractNodeEntity,documentFileService, spaceService, identityManager);
        assertNotNull(specificCollabotratorsNodePermission);
        assertEquals(2, specificCollabotratorsNodePermission.getPermissions().size());
        // collaborator entry is processed first
        assertEquals(PermissionRole.ALL.name(), specificCollabotratorsNodePermission.getPermissions().get(0).getRole());
        // space-wide managers/redactors permission is appended after collaborators
        assertEquals(PermissionRole.MANAGERS_REDACTORS_PUBLISHERS.name(), specificCollabotratorsNodePermission.getPermissions().get(1).getRole());
        assertEquals(identity.getRemoteId(), specificCollabotratorsNodePermission.getPermissions().get(1).getIdentity().getRemoteId());
        assertEquals("edit", specificCollabotratorsNodePermission.getPermissions().get(1).getPermission());

        IdentityEntity useridentityEntity = new IdentityEntity();
        useridentityEntity.setId("1");
        useridentityEntity.setRemoteId("userRemoteId");
        useridentityEntity.setProviderId("user");
        permissionEntryEntity.setIdentity(useridentityEntity);
        permissionEntryEntity.setPermission("edit");
        Identity destinationIdentity = mock(Identity.class);
        when(destinationIdentity.getRemoteId()).thenReturn("userRemoteId");
        when(destinationIdentity.getId()).thenReturn(useridentityEntity.getId());
        when(identityManager.getOrCreateUserIdentity(destinationIdentity.getRemoteId())).thenReturn(destinationIdentity);
        when(identity.getId()).thenReturn("3");
        when(identity.isSpace()).thenReturn(true);
        when(identity.getRemoteId()).thenReturn("spaceTest");
        when(spaceService.getSpaceByPrettyName(identity.getRemoteId())).thenReturn(space);
        // Destination user isn't member of the space
        when(spaceService.isMember(space, "userRemoteId")).thenReturn(false);
        // When
        NodePermission nodePermission3 = EntityBuilder.toNodePermission(abstractNodeEntity,documentFileService, spaceService, identityManager);
        // assert to share with destination user
        assertNotNull(nodePermission3);
        assertEquals(Long.valueOf(useridentityEntity.getId()), nodePermission3.getToShare().keySet().toArray()[0]);

        // Destination user is member of the space
        when(spaceService.isMember(space, "userRemoteId")).thenReturn(true);
        // When
        NodePermission nodePermission4 = EntityBuilder.toNodePermission(abstractNodeEntity,documentFileService, spaceService, identityManager);
        // assert to notify destination user
        assertNotNull(nodePermission3);
        assertEquals(Long.valueOf(useridentityEntity.getId()), nodePermission4.getToNotify().keySet().toArray()[0]);
    }

    @Test
    public void toNodePermissionEntityShowsCreatorWhenExplicitlyAddedAsReadCollaborator() throws Exception {
        Space space = new Space();
        space.setPrettyName("testspace");
        when(spaceService.getSpaceByGroupId("/spaces/testspace")).thenReturn(space);

        Identity spaceIdentity = mock(Identity.class);
        when(spaceIdentity.getId()).thenReturn("999");
        when(spaceIdentity.isSpace()).thenReturn(true);
        when(identityManager.getOrCreateSpaceIdentity("testspace")).thenReturn(spaceIdentity);

        Identity creatorIdentity = mock(Identity.class);
        when(creatorIdentity.getId()).thenReturn("42");
        when(creatorIdentity.getRemoteId()).thenReturn("wilhelmine");
        when(creatorIdentity.isUser()).thenReturn(true);
        Profile profile = mock(Profile.class);
        when(profile.getFullName()).thenReturn("Wilhelmine Abden");
        when(creatorIdentity.getProfile()).thenReturn(profile);

        FileNode node = new FileNode();
        node.setId("fileId");
        node.setPath("/Groups/spaces/testspace");
        node.setCreatorId(42);
        when(publicDocumentAccessService.hasDocumentPublicAccess("fileId")).thenReturn(false);

        // Creator explicitly added as a read-only collaborator: the default owner grant is
        // always full access, so a read-only entry can only come from an explicit choice
        // and must show up in the collaborators list.
        PermissionEntry readEntry = new PermissionEntry(creatorIdentity, "read", PermissionRole.ALL.name());
        node.setAcl(new NodePermission(true, false, false, false, List.of(readEntry), null, null, null));
        NodePermissionEntity readResult = invokeToNodePermissionEntity(node);
        assertEquals(1, readResult.getCollaborators().size());
        assertEquals("wilhelmine", readResult.getCollaborators().get(0).getIdentity().getRemoteId());

        // Creator's implicit full-access owner grant must stay hidden from the collaborators list.
        PermissionEntry editEntry = new PermissionEntry(creatorIdentity, "add_node,set_property,remove,read", PermissionRole.ALL.name());
        node.setAcl(new NodePermission(true, true, false, false, List.of(editEntry), null, null, null));
        NodePermissionEntity editResult = invokeToNodePermissionEntity(node);
        assertEquals(0, editResult.getCollaborators().size());
    }

  @Test
  public void encodeNamePreservesPlusSign() throws Exception {
    FileNode node = new FileNode();
    node.setName("ABD + DEF.docx");
    assertEquals("ABD + DEF.docx", invokeEncodeName(node));
  }

  private NodePermissionEntity invokeToNodePermissionEntity(FileNode node) throws Exception {
    Method method = EntityBuilder.class.getDeclaredMethod("toNodePermissionEntity",
                                                          org.exoplatform.documents.model.AbstractNode.class,
                                                          IdentityManager.class,
                                                          SpaceService.class,
                                                          PublicDocumentAccessService.class);
    method.setAccessible(true);
    return (NodePermissionEntity) method.invoke(null, node, identityManager, spaceService, publicDocumentAccessService);
  }

  private String invokeEncodeName(FileNode node) throws Exception {
    Method method = EntityBuilder.class.getDeclaredMethod("encodeName",
                                                          org.exoplatform.documents.model.AbstractNode.class);
    method.setAccessible(true);
    return (String) method.invoke(null, node);
  }
}
