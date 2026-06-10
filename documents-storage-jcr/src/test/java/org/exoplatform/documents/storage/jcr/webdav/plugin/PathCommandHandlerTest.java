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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.jcr.Node;
import javax.jcr.Session;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.storage.jcr.webdav.WebDavPathMappingService;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import lombok.SneakyThrows;

@RunWith(MockitoJUnitRunner.class)
public class PathCommandHandlerTest {

  private static final String      IDENTITY_PATH      = "/(123)";              // NOSONAR

  private static final String      USER1              = "user1";

  private static final String      USER_BASE_JCR_PATH = "/users/user1/Private";// NOSONAR

  @Mock
  private IdentityManager          identityManager;

  @Mock
  private SpaceService             spaceService;

  @Mock
  private NodeHierarchyCreator     nodeHierarchyCreator;

  @Mock
  private SessionProviderService   sessionProviderService;

  @Mock
  private WebDavPathMappingService webDavPathMappingService;

  @Mock
  private Session                  session;

  @Mock
  private Identity                 identity;

  @Mock
  private Node                     userNode;

  @Mock
  private Node                     privateNode;

  @Mock
  private Space                    space;

  @Mock
  private SessionProvider          sessionProvider;

  @InjectMocks
  private PathCommandHandler       handler;

  @Before
  @SneakyThrows
  public void setUp() {
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    when(nodeHierarchyCreator.getUserNode(sessionProvider, USER1)).thenReturn(userNode);
    when(userNode.getNode("Private")).thenReturn(privateNode);
    when(privateNode.getPath()).thenReturn(USER_BASE_JCR_PATH);
    when(nodeHierarchyCreator.getJcrPath("groupsPath")).thenReturn("/groups");
    when(nodeHierarchyCreator.getJcrPath("usersPath")).thenReturn("/users");
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

    Long id = handler.getIdentityIdFromJcrPath("/users/user1/doc", USER1);

    assertEquals(Long.valueOf(111L), id);
  }

  @Test
  public void testGetIdentityIdFromJcrPathNoMatch() {
    assertNull(handler.getIdentityIdFromJcrPath("/random/path", USER1));
  }

  @Test
  public void testGetIdentityIdFromWebDavPathValid() {
    assertEquals(Long.valueOf(123), handler.getIdentityIdFromWebDavPath("/folder(123)"));
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
    when(webDavPathMappingService.findJcrPath(USER_BASE_JCR_PATH, "Rapport Équipe.docx"))
                                                                                         .thenReturn(USER_BASE_JCR_PATH +
                                                                                             "/technical-report");

    String path = handler.resolveToJcrPath(session, "/John%20Doe%20(123)/Rapport%20%C3%89quipe.docx");

    assertEquals(USER_BASE_JCR_PATH + "/technical-report", path);
  }

  @Test
  @SneakyThrows
  public void testResolveToJcrPathWithLegacyFallback() {
    mockUserIdentity(123L);
    when(webDavPathMappingService.findJcrPath(eq(USER_BASE_JCR_PATH), anyString())).thenReturn(null);
    when(session.itemExists(USER_BASE_JCR_PATH + "/subdir")).thenReturn(true);

    String path = handler.resolveToJcrPath(session, "/John%20Doe%20(123)/subdir");

    assertEquals(USER_BASE_JCR_PATH + "/subdir", path);
  }

  @Test(expected = WebDavException.class)
  @SneakyThrows
  public void testResolveToJcrPathNotFoundWhenNoMappingAndNoLegacyPath() {
    mockUserIdentity(123L);
    when(webDavPathMappingService.findJcrPath(eq(USER_BASE_JCR_PATH), anyString())).thenReturn(null);
    when(session.itemExists(anyString())).thenReturn(false);

    handler.resolveToJcrPath(session, "/John%20Doe%20(123)/missing");
  }

  @Test
  public void testGetLastVisibleSegment() {
    assertEquals("Rapport Équipe.docx", handler.getLastVisibleSegment("/John%20Doe%20(123)/Rapport%20%C3%89quipe.docx"));
  }

  @Test
  public void testIsIdentityRootWebDavPath() {
    assertTrue(handler.isIdentityRootWebDavPath(IDENTITY_PATH));
    assertFalse(handler.isIdentityRootWebDavPath("/(123)/subdir"));
  }

  private void mockUserIdentity(long identityId) {
    when(identityManager.getIdentity(identityId)).thenReturn(identity);
    when(identity.isUser()).thenReturn(true);
    when(identity.getRemoteId()).thenReturn(USER1);
  }
}
