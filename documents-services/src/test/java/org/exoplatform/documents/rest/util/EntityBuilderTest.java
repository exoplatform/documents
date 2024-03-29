package org.exoplatform.documents.rest.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.exoplatform.documents.model.PermissionRole;
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
        assertEquals(PermissionRole.MANAGERS_REDACTORS.name(), specificCollabotratorsNodePermission.getPermissions().get(0).getRole());
        assertEquals(identity.getRemoteId(), specificCollabotratorsNodePermission.getPermissions().get(0).getIdentity().getRemoteId());
        assertEquals("edit", specificCollabotratorsNodePermission.getPermissions().get(0).getPermission());

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
}