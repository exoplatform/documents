/**
 * Copyright (C) 2025 eXo Platform SAS
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
package org.exoplatform.documents.storage.jcr.webdav.plugin;

import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_NAME;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_TITLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import javax.jcr.Node;
import javax.jcr.Property;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.storage.jcr.webdav.entity.WebDavPathMappingEntity;
import org.exoplatform.documents.storage.jcr.webdav.storage.WebDavPathMappingStorage;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import lombok.SneakyThrows;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PathCommandHandlerTest {

  private static final String      FILE_DUPLICATED_NAME   = "Rapport (1).docx";

  private static final String      SPACE_NAME             = "marketing";

  private static final String      FILE_TITLE             = "Rapport Équipe.docx";

  private static final String      WEB_DAV_PATH           = "/John%20Doe%20%28123%29";               // NOSONAR

  private static final String      USER_PRIVATE_NODE_NAME = "Private";

  private static final String      IDENTITY_PATH          = "/(123)";                                // NOSONAR

  private static final String      USER1                  = "user1";

  private static final String      USER_BASE_JCR_PATH     = "/users/user1/Private";                  // NOSONAR

  private static final String      IDENTITY_ID            = "123";

  private static final String      IDENTITY_ROOT_WEBDAV   = WEB_DAV_PATH;

  private static final String      PARENT_JCR_PATH        = USER_BASE_JCR_PATH + "/Documents";

  private static final String      PARENT_WEBDAV_PATH     = IDENTITY_ROOT_WEBDAV + "/Documents";

  private static final String      JCR_PATH               = PARENT_JCR_PATH + "/rapport_equipe.docx";

  private static final String      NODE_TITLE_WITH_ACCENT = FILE_TITLE;

  private static final String      NODE_TITLE_OTHER       = "Rapport.docx";

  private static final String      NODE_TITLE             = "Rapport Equipe.docx";

  private static final String      NODE_NAME              = "rapport_equipe.docx";

  private static final String      NODE_ID                = "node-123";

  @Mock
  private IdentityManager          identityManager;

  @Mock
  private SpaceService             spaceService;

  @Mock
  private NodeHierarchyCreator     nodeHierarchyCreator;

  @Mock
  private SessionProviderService   sessionProviderService;

  @Mock
  private WebDavPathMappingStorage webDavPathMappingStorage;

  @Mock
  private SessionImpl              session;

  @Mock
  private Identity                 identity;

  @Mock
  private Profile                  profile;

  @Mock
  private Node                     userNode;

  @Mock
  private Node                     privateNode;

  @Mock
  private Space                    space;

  @Mock
  private SessionProvider          sessionProvider;

  @Mock
  private NodeImpl                 node;

  @Mock
  private NodeImpl                 parentNode;

  @Mock
  private NodeImpl                 identityRootNode;

  @Mock
  private Property                 property;

  @InjectMocks
  private PathCommandHandler       handler;

  @Before
  @SneakyThrows
  public void setUp() {
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    when(nodeHierarchyCreator.getUserNode(sessionProvider, USER1)).thenReturn(userNode);
    when(userNode.getNode(USER_PRIVATE_NODE_NAME)).thenReturn(privateNode);
    when(privateNode.getPath()).thenReturn(USER_BASE_JCR_PATH);
    when(nodeHierarchyCreator.getJcrPath("groupsPath")).thenReturn("/groups");
    when(nodeHierarchyCreator.getJcrPath("usersPath")).thenReturn("/users");

    when(identity.getProfile()).thenReturn(profile);
    when(profile.getFullName()).thenReturn("John Doe");

    when(node.getPath()).thenReturn(JCR_PATH);
    when(node.getName()).thenReturn(NODE_NAME);
    when(node.getIdentifier()).thenReturn(NODE_ID);
    when(node.getParent()).thenReturn(parentNode);

    when(parentNode.getPath()).thenReturn(PARENT_JCR_PATH);
    when(parentNode.getName()).thenReturn("Documents");
    when(parentNode.getIdentifier()).thenReturn("parent-node-1");
    when(parentNode.getParent()).thenReturn(identityRootNode);

    when(identityRootNode.getPath()).thenReturn(USER_BASE_JCR_PATH);
    when(identityRootNode.getName()).thenReturn(USER_PRIVATE_NODE_NAME);
    when(identityRootNode.getIdentifier()).thenReturn("identity-root-node-1");

    when(webDavPathMappingStorage.save(any(WebDavPathMappingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  public void testGetIdentityBaseJcrPathByWebDavPathUser() {
    mockUserIdentity(123L);

    String result = handler.getIdentityBaseJcrPath(IDENTITY_PATH);

    assertEquals(USER_BASE_JCR_PATH, result);
  }

  @Test(expected = WebDavException.class)
  public void testGetIdentityBaseJcrPathByWebDavPathNoIdentity() {
    when(identityManager.getIdentity(123L)).thenReturn(null);
    handler.getIdentityBaseJcrPath(IDENTITY_PATH);
  }

  @Test
  public void testGetIdentityBaseJcrPathByWebDavPathSpace() {
    when(identityManager.getIdentity(456L)).thenReturn(identity);
    when(identity.isUser()).thenReturn(false);
    when(identity.isSpace()).thenReturn(true);
    when(identity.getRemoteId()).thenReturn("spacePretty");
    when(spaceService.getSpaceByPrettyName("spacePretty")).thenReturn(space);
    when(space.getGroupId()).thenReturn("/spaces/spacePretty");

    String result = handler.getIdentityBaseJcrPath("/(456)");

    assertEquals("/groups/spaces/spacePretty/Documents", result);
  }

  @Test(expected = WebDavException.class)
  public void testGetIdentityBaseJcrPathSpaceNotFound() {
    when(identityManager.getIdentity(456L)).thenReturn(identity);
    when(identity.isSpace()).thenReturn(true);
    when(identity.getRemoteId()).thenReturn("missing");
    when(spaceService.getSpaceByPrettyName("missing")).thenReturn(null);

    handler.getIdentityBaseJcrPath("/(456)");
  }

  @Test
  public void testGetIdentityIdFromJcrPathSpace() {
    when(spaceService.getSpaceByGroupId("/spaces/space1")).thenReturn(space);
    when(space.getPrettyName()).thenReturn("space1");
    Identity spaceIdentity = mock(Identity.class);
    when(identityManager.getOrCreateSpaceIdentity("space1")).thenReturn(spaceIdentity);
    when(spaceIdentity.getIdentityId()).thenReturn(999L);

    Long id = handler.getIdentityIdFromJcrPath("/groups/spaces/space1/doc", "userX");

    assertEquals(Long.valueOf(999L), id);
  }

  @Test
  public void testGetIdentityIdFromJcrPathUser() {
    Identity userIdentity = mock(Identity.class);
    when(identityManager.getOrCreateUserIdentity(USER1)).thenReturn(userIdentity);
    when(userIdentity.getIdentityId()).thenReturn(111L);

    Long id = handler.getIdentityIdFromJcrPath("/users/user1/Private/doc", USER1);

    assertEquals(Long.valueOf(111L), id);
  }

  @Test
  public void testGetIdentityIdFromJcrPathNoMatch() {
    assertNull(handler.getIdentityIdFromJcrPath("/random/path", USER1));
  }

  @Test
  public void testGetIdentityIdFromWebDavPathValid() {
    assertEquals(Long.valueOf(123), handler.getIdentityIdFromWebDavPath(WEB_DAV_PATH));
  }

  @Test
  public void testGetIdentityIdFromWebDavPathInvalid() {
    assertNull(handler.getIdentityIdFromWebDavPath("/"));
  }

  @Test
  @SneakyThrows
  public void testResolveToJcrPathNoIdentityId() {
    assertEquals("/", handler.resolveToJcrPath(session, "/"));
  }

  @Test
  @SneakyThrows
  public void testResolveToJcrPathWithMappedSegment() {
    mockUserIdentity(123L);
    when(webDavPathMappingStorage.findJcrPath(USER_BASE_JCR_PATH, FILE_TITLE)).thenReturn(USER_BASE_JCR_PATH +
        "/technical-report");

    String path = handler.resolveToJcrPath(session, "/John%20Doe%20%28123%29/Rapport%20%C3%89quipe.docx");

    assertEquals(USER_BASE_JCR_PATH + "/technical-report", path);
  }

  @Test
  @SneakyThrows
  public void testResolveToJcrPathWithLegacyFallback() {
    mockUserIdentity(123L);
    Identity userIdentity = mock(Identity.class);
    when(identityManager.getOrCreateUserIdentity(USER1)).thenReturn(userIdentity);
    when(userIdentity.getIdentityId()).thenReturn(123L);
    when(session.getUserID()).thenReturn(USER1);

    NodeImpl legacyNode = mock(NodeImpl.class);
    when(legacyNode.getPath()).thenReturn(USER_BASE_JCR_PATH + "/subdir"); // NOSONAR
    when(legacyNode.getName()).thenReturn("subdir");
    when(legacyNode.getIdentifier()).thenReturn("legacy-node-id");
    when(legacyNode.getSession()).thenReturn(session);
    when(legacyNode.getParent()).thenReturn(identityRootNode);

    when(webDavPathMappingStorage.findJcrPath(eq(USER_BASE_JCR_PATH), anyString())).thenReturn(null);
    when(webDavPathMappingStorage.findByNodeIdentifier(anyString())).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByJcrPath(anyString())).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByParentJcrPathAndNormalizedVisibleName(anyString(),
                                                                              anyString())).thenReturn(Optional.empty());
    when(session.itemExists(USER_BASE_JCR_PATH + "/subdir")).thenReturn(true);
    when(session.getItem(USER_BASE_JCR_PATH + "/subdir")).thenReturn(legacyNode);

    String path = handler.resolveToJcrPath(session, "/John Doe (123)/subdir");

    assertEquals(USER_BASE_JCR_PATH + "/subdir", path);
  }

  @Test(expected = WebDavException.class)
  @SneakyThrows
  public void testResolveToJcrPathNotFoundWhenNoMappingAndNoLegacyPath() {
    mockUserIdentity(123L);
    when(webDavPathMappingStorage.findJcrPath(eq(USER_BASE_JCR_PATH), anyString())).thenReturn(null);
    when(session.itemExists(anyString())).thenReturn(false);

    handler.resolveToJcrPath(session, "/John%20Doe%20%28123%29/missing");
  }

  @Test
  public void testGetLastVisibleSegment() {
    assertEquals(FILE_TITLE, handler.getLastVisibleSegment("/John%20Doe%20%28123%29/Rapport%20%C3%89quipe.docx"));
  }

  @Test
  public void testIsIdentityRootWebDavPath() {
    assertTrue(handler.isIdentityRootWebDavPath(IDENTITY_PATH));
    assertFalse(handler.isIdentityRootWebDavPath("/(123)/subdir"));
  }

  @Test
  @SneakyThrows
  public void testAllocateTechnicalNameReturnsFirstAvailableEncodedName() {
    when(session.itemExists(anyString())).thenReturn(false);

    String result = handler.allocateTechnicalName(session, PARENT_JCR_PATH, NODE_TITLE);

    assertEquals(NODE_TITLE, result);
    verify(session).itemExists(PARENT_JCR_PATH + "/Rapport Equipe.docx");
  }

  @Test
  @SneakyThrows
  public void testAllocateTechnicalNameAddsSuffixWhenTechnicalNameAlreadyExists() {
    when(session.itemExists(PARENT_JCR_PATH + "/Rapport.docx")).thenReturn(true);
    when(session.itemExists(PARENT_JCR_PATH + "/Rapport (1).docx")).thenReturn(false);

    String result = handler.allocateTechnicalName(session, PARENT_JCR_PATH, NODE_TITLE_OTHER);

    assertEquals(FILE_DUPLICATED_NAME, result);
  }

  @Test
  @SneakyThrows
  public void testSaveMappingCreatesPersistentEntityAndUpdatesTitlePropertiesWhenAvailable() {
    when(node.hasProperty(EXO_TITLE)).thenReturn(true);
    when(node.hasProperty(EXO_NAME)).thenReturn(true);
    when(webDavPathMappingStorage.findByJcrPath(JCR_PATH)).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByParentJcrPathAndNormalizedVisibleName(PARENT_JCR_PATH, NODE_TITLE_WITH_ACCENT))
                                                                                                                       .thenReturn(Optional.empty());

    WebDavPathMappingEntity result = handler.saveMapping(session,
                                                         PARENT_WEBDAV_PATH + "/Rapport%20%C3%89quipe.docx",
                                                         NODE_TITLE_WITH_ACCENT,
                                                         node);

    assertNotNull(result);
    assertEquals(IDENTITY_ID, result.getIdentityId());
    assertEquals(PARENT_JCR_PATH, result.getParentJcrPath());
    assertEquals(NODE_TITLE_WITH_ACCENT, result.getVisibleName());
    assertEquals(NODE_TITLE_WITH_ACCENT, result.getNormalizedVisibleName());
    assertEquals(PARENT_WEBDAV_PATH + "/Rapport%20%C3%89quipe.docx", result.getWebDavPath());
    assertEquals(PARENT_WEBDAV_PATH, result.getParentWebDavPath());
    assertEquals(JCR_PATH, result.getJcrPath());
    assertEquals(NODE_ID, result.getNodeIdentifier());
    assertEquals(NODE_NAME, result.getTechnicalName());
    assertFalse(result.isFallbackName());
    assertFalse(result.isCollisionResolved());
    assertNotNull(result.getId());
    assertNotNull(result.getCreatedDate());
    assertNotNull(result.getUpdatedDate());

    verify(node).setProperty(EXO_TITLE, NODE_TITLE_WITH_ACCENT);
    verify(node).setProperty(EXO_NAME, NODE_NAME);
    verify(webDavPathMappingStorage).save(any(WebDavPathMappingEntity.class));
  }

  @Test
  @SneakyThrows
  public void testGetVisibleNameUsesMappingBeforeNodeTitle() {
    WebDavPathMappingEntity existing = new WebDavPathMappingEntity();
    existing.setVisibleName("Mapped.docx");
    when(webDavPathMappingStorage.findByJcrPath(JCR_PATH)).thenReturn(Optional.of(existing));

    String result = handler.getVisibleName(node);

    assertEquals("Mapped.docx", result);
  }

  @Test
  @SneakyThrows
  public void testGetVisibleNameFallsBackToTitle() {
    when(webDavPathMappingStorage.findByJcrPath(JCR_PATH)).thenReturn(Optional.empty());
    when(node.hasProperty(EXO_TITLE)).thenReturn(true);
    when(node.getProperty(EXO_TITLE)).thenReturn(property);
    when(property.getString()).thenReturn(NODE_TITLE_WITH_ACCENT);

    String result = handler.getVisibleName(node);

    assertEquals(NODE_TITLE_WITH_ACCENT, result);
  }

  @Test
  @SneakyThrows
  public void testGetOrCreateWebDavPathReturnsIdentityRootForIdentityBaseNode() {
    String result = handler.getOrCreateWebDavPath(IDENTITY_ID,
                                                  USER_BASE_JCR_PATH,
                                                  IDENTITY_ROOT_WEBDAV,
                                                  identityRootNode,
                                                  USER_PRIVATE_NODE_NAME);

    assertEquals(IDENTITY_ROOT_WEBDAV, result);
    verify(webDavPathMappingStorage, never()).save(any(WebDavPathMappingEntity.class));
  }

  @Test
  @SneakyThrows
  public void testGetOrCreateWebDavPathCreatesParentAndChildMappingsWithVisibleNames() {
    when(webDavPathMappingStorage.findByNodeIdentifier(anyString())).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByJcrPath(anyString())).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByParentJcrPathAndNormalizedVisibleName(anyString(),
                                                                              anyString())).thenReturn(Optional.empty());

    when(parentNode.hasProperty(EXO_TITLE)).thenReturn(true);
    when(parentNode.getProperty(EXO_TITLE)).thenReturn(property);
    when(property.getString()).thenReturn("Documents Partagés");

    String result = handler.getOrCreateWebDavPath(IDENTITY_ID,
                                                  USER_BASE_JCR_PATH,
                                                  IDENTITY_ROOT_WEBDAV,
                                                  node,
                                                  NODE_TITLE_WITH_ACCENT);

    assertEquals(IDENTITY_ROOT_WEBDAV + "/Documents%20Partag%C3%A9s/Rapport%20%C3%89quipe.docx", result);

    ArgumentCaptor<WebDavPathMappingEntity> captor = ArgumentCaptor.forClass(WebDavPathMappingEntity.class);
    verify(webDavPathMappingStorage, org.mockito.Mockito.times(2)).save(captor.capture());

    WebDavPathMappingEntity parentMapping = captor.getAllValues().get(0);
    WebDavPathMappingEntity childMapping = captor.getAllValues().get(1);

    assertEquals("Documents Partagés", parentMapping.getVisibleName());
    assertEquals(IDENTITY_ROOT_WEBDAV + "/Documents%20Partag%C3%A9s", parentMapping.getWebDavPath());

    assertEquals(NODE_TITLE_WITH_ACCENT, childMapping.getVisibleName());
    assertEquals(result, childMapping.getWebDavPath());
    assertEquals(PARENT_JCR_PATH, childMapping.getParentJcrPath());
    assertEquals(JCR_PATH, childMapping.getJcrPath());
  }

  @Test
  @SneakyThrows
  public void testGetOrCreateWebDavPathResolvesVisibleNameCollision() {
    WebDavPathMappingEntity collision = new WebDavPathMappingEntity();
    collision.setJcrPath(PARENT_JCR_PATH + "/other.docx");

    when(webDavPathMappingStorage.findByNodeIdentifier(anyString())).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByJcrPath(anyString())).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByParentJcrPathAndNormalizedVisibleName(eq(USER_BASE_JCR_PATH), anyString()))
                                                                                                                   .thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByParentJcrPathAndNormalizedVisibleName(PARENT_JCR_PATH, NODE_TITLE_OTHER))
                                                                                                                 .thenReturn(Optional.of(collision));
    when(webDavPathMappingStorage.findByParentJcrPathAndNormalizedVisibleName(PARENT_JCR_PATH, FILE_DUPLICATED_NAME))
                                                                                                                     .thenReturn(Optional.empty());

    String result = handler.getOrCreateWebDavPath(IDENTITY_ID,
                                                  USER_BASE_JCR_PATH,
                                                  IDENTITY_ROOT_WEBDAV,
                                                  node,
                                                  NODE_TITLE_OTHER);

    assertEquals(PARENT_WEBDAV_PATH + "/Rapport%20%281%29.docx", result);

    ArgumentCaptor<WebDavPathMappingEntity> captor = ArgumentCaptor.forClass(WebDavPathMappingEntity.class);
    verify(webDavPathMappingStorage, org.mockito.Mockito.times(2)).save(captor.capture());

    WebDavPathMappingEntity childMapping = captor.getAllValues().get(1);
    assertEquals(FILE_DUPLICATED_NAME, childMapping.getVisibleName());
    assertTrue(childMapping.isCollisionResolved());
  }

  @Test
  @SneakyThrows
  public void testRefreshMappingOrDeleteRefreshesExistingMappingAfterCrossIdentityMove() {
    String newIdentityBaseJcrPath = "/groups/spaces/marketing/Documents"; // NOSONAR
    String newParentJcrPath = newIdentityBaseJcrPath + "/Folder";
    String newJcrPath = newParentJcrPath + "/rapport_equipe.docx";
    String newIdentityRootWebDavPath = "/marketing%20%2842%29"; // NOSONAR

    NodeImpl movedNode = mock(NodeImpl.class);
    NodeImpl movedParent = mock(NodeImpl.class);
    NodeImpl movedRoot = mock(NodeImpl.class);
    Profile spaceProfile = mock(Profile.class);
    Identity spaceIdentity = mock(Identity.class);
    Space marketingSpace = mock(Space.class);

    WebDavPathMappingEntity existing = new WebDavPathMappingEntity();
    existing.setId("old-id");
    existing.setIdentityId(IDENTITY_ID);
    existing.setJcrPath(JCR_PATH);
    existing.setNodeIdentifier(NODE_ID);
    existing.setVisibleName(NODE_TITLE_OTHER);

    when(movedNode.getPath()).thenReturn(newJcrPath);
    when(movedNode.getName()).thenReturn(NODE_NAME);
    when(movedNode.getIdentifier()).thenReturn(NODE_ID);
    when(movedNode.getParent()).thenReturn(movedParent);
    when(movedNode.hasProperty(EXO_TITLE)).thenReturn(true);
    when(movedNode.getProperty(EXO_TITLE)).thenReturn(property);
    when(property.getString()).thenReturn(NODE_TITLE_WITH_ACCENT);

    when(movedParent.getPath()).thenReturn(newParentJcrPath);
    when(movedParent.getName()).thenReturn("Folder");
    when(movedParent.getIdentifier()).thenReturn("new-parent-id");
    when(movedParent.getParent()).thenReturn(movedRoot);

    when(movedRoot.getPath()).thenReturn(newIdentityBaseJcrPath);
    when(movedRoot.getName()).thenReturn("Documents");
    when(movedRoot.getIdentifier()).thenReturn("new-root-id");

    when(session.itemExists(newJcrPath)).thenReturn(true);
    when(session.getItem(newJcrPath)).thenReturn(movedNode);

    when(webDavPathMappingStorage.findByJcrPath(newJcrPath)).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByNodeIdentifier(NODE_ID)).thenReturn(Optional.of(existing));
    when(webDavPathMappingStorage.findByNodeIdentifier("new-parent-id")).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByJcrPath(newParentJcrPath)).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByNodeIdentifier("new-root-id")).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByJcrPath(newIdentityBaseJcrPath)).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByParentJcrPathAndNormalizedVisibleName(anyString(),
                                                                              anyString())).thenReturn(Optional.empty());

    when(spaceService.getSpaceByGroupId("/spaces/marketing")).thenReturn(marketingSpace);
    when(marketingSpace.getPrettyName()).thenReturn(SPACE_NAME);
    when(marketingSpace.getGroupId()).thenReturn("/spaces/marketing");
    when(marketingSpace.getDisplayName()).thenReturn("Marketing Space");
    when(identityManager.getOrCreateSpaceIdentity(SPACE_NAME)).thenReturn(spaceIdentity);
    when(identityManager.getIdentity(42L)).thenReturn(spaceIdentity);
    when(spaceIdentity.getIdentityId()).thenReturn(42L);
    when(spaceIdentity.isSpace()).thenReturn(true);
    when(spaceIdentity.getRemoteId()).thenReturn(SPACE_NAME);
    when(spaceIdentity.getProfile()).thenReturn(spaceProfile);
    when(spaceProfile.getFullName()).thenReturn("Marketing Space");
    when(spaceService.getSpaceByPrettyName(SPACE_NAME)).thenReturn(marketingSpace);

    handler.refreshMappingOrDelete(session, newJcrPath);

    ArgumentCaptor<WebDavPathMappingEntity> captor = ArgumentCaptor.forClass(WebDavPathMappingEntity.class);
    verify(webDavPathMappingStorage, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

    WebDavPathMappingEntity refreshed = captor.getAllValues()
                                              .stream()
                                              .filter(e -> newJcrPath.equals(e.getJcrPath()))
                                              .findFirst()
                                              .orElse(null);

    assertNotNull(refreshed);
    assertEquals("42", refreshed.getIdentityId()); // NOSONAR
    assertEquals(newParentJcrPath, refreshed.getParentJcrPath());
    assertEquals(NODE_TITLE_WITH_ACCENT, refreshed.getVisibleName());
    assertEquals(newIdentityRootWebDavPath + "/Folder/Rapport%20%C3%89quipe.docx", refreshed.getWebDavPath());
  }

  @Test
  public void testToWebDavSegmentReplacesCharactersUnusableInAPathSegment() {
    assertEquals("R&D _ Ops", PathCommandHandler.toWebDavSegment("R&D / Ops"));
    assertEquals("a_b", PathCommandHandler.toWebDavSegment("a\\b"));
    assertEquals("50_ Club", PathCommandHandler.toWebDavSegment("50% Club"));
    assertEquals("a_b", PathCommandHandler.toWebDavSegment("a;b"));
    assertEquals("a_b", PathCommandHandler.toWebDavSegment("a\nb"));
    assertEquals("Marketing Équipe", PathCommandHandler.toWebDavSegment("Marketing Équipe"));
    assertEquals("", PathCommandHandler.toWebDavSegment(null));
  }

  @Test
  @SneakyThrows
  public void testGetOrCreateWebDavPathAddressesSpaceDriveByPrettyNameWhenDisplayNameHasSlash() {
    String spaceBaseJcrPath = "/groups/spaces/marketing/Documents"; // NOSONAR
    String spaceFileJcrPath = spaceBaseJcrPath + "/rapport.docx";

    NodeImpl spaceFileNode = mock(NodeImpl.class);
    NodeImpl spaceRootNode = mock(NodeImpl.class);
    SessionImpl spaceSession = mock(SessionImpl.class);
    Identity spaceIdentity = mock(Identity.class);
    Space marketingSpace = mock(Space.class);

    when(spaceFileNode.getPath()).thenReturn(spaceFileJcrPath);
    when(spaceFileNode.getName()).thenReturn("rapport.docx");
    when(spaceFileNode.getIdentifier()).thenReturn("space-file-id");
    when(spaceFileNode.getParent()).thenReturn(spaceRootNode);
    when(spaceFileNode.getSession()).thenReturn(spaceSession);
    when(spaceSession.getUserID()).thenReturn(USER1);
    when(spaceRootNode.getPath()).thenReturn(spaceBaseJcrPath);

    when(spaceService.getSpaceByGroupId("/spaces/marketing")).thenReturn(marketingSpace);
    when(spaceService.getSpaceByPrettyName(SPACE_NAME)).thenReturn(marketingSpace);
    when(marketingSpace.getPrettyName()).thenReturn(SPACE_NAME);
    when(marketingSpace.getGroupId()).thenReturn("/spaces/marketing");
    when(marketingSpace.getDisplayName()).thenReturn("R&D / Ops");
    when(identityManager.getOrCreateSpaceIdentity(SPACE_NAME)).thenReturn(spaceIdentity);
    when(identityManager.getIdentity(42L)).thenReturn(spaceIdentity);
    when(spaceIdentity.getIdentityId()).thenReturn(42L);
    when(spaceIdentity.isSpace()).thenReturn(true);
    when(spaceIdentity.getRemoteId()).thenReturn(SPACE_NAME);

    when(webDavPathMappingStorage.findByNodeIdentifier(anyString())).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByJcrPath(anyString())).thenReturn(Optional.empty());
    when(webDavPathMappingStorage.findByParentJcrPathAndNormalizedVisibleName(anyString(),
                                                                              anyString())).thenReturn(Optional.empty());

    String result = handler.getOrCreateWebDavPath(spaceFileNode);

    // the drive is addressed by the Space pretty name — the name its JCR drive
    // is created under — so the '/' of the display name never reaches the path:
    // %2F is rejected before any handler runs, and once decoded it would split
    // the drive into two segments
    assertEquals("/marketing%20%2842%29/rapport.docx", result);
    assertFalse(result.contains("%2F"));

    // the identity id is still readable back from the path the client sends
    assertEquals(Long.valueOf(42L), handler.getIdentityIdFromWebDavPath(handler.decodeUrlString(result)));
  }

  @Test
  public void testIsTitlePropertyPath() {
    assertTrue(handler.isTitlePropertyPath(JCR_PATH + "/exo:title"));
    assertTrue(handler.isTitlePropertyPath(JCR_PATH + "/jcr:content/dc:title"));
    assertFalse(handler.isTitlePropertyPath(JCR_PATH + "/jcr:content/jcr:data"));
  }

  @Test
  public void testGetNodePathFromPropertyPath() {
    assertEquals(JCR_PATH, handler.getNodePathFromPropertyPath(JCR_PATH + "/exo:title"));
    assertEquals(JCR_PATH, handler.getNodePathFromPropertyPath(JCR_PATH + "/jcr:content/dc:title"));
  }

  private void mockUserIdentity(long identityId) {
    when(identityManager.getIdentity(identityId)).thenReturn(identity);
    when(identity.isUser()).thenReturn(true);
    when(identity.getRemoteId()).thenReturn(USER1);
  }
}
