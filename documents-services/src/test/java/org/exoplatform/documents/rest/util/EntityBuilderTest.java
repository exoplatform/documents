package org.exoplatform.documents.rest.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

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
    }
}