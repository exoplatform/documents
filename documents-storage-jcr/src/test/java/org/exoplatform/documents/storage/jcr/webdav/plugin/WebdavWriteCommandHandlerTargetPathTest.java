/**
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
package org.exoplatform.documents.storage.jcr.webdav.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import org.exoplatform.documents.storage.TrashStorage;
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
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Runs the real {@link PathCommandHandler} under {@link WebdavWriteCommandHandler}
 * against an in-memory name mapping and a session stub that applies the
 * repository's '.' and '..' rule (skipped, moving up), so that the target a move
 * checks and the mapping key it then writes are computed by the production code.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class WebdavWriteCommandHandlerTargetPathTest {

  private static final String                         DRIVE_ROOT_JCR_PATH = "/users/user1/Private";

  private static final String                         DRIVE               = "/John Doe (123)";

  private static final String                         EXISTING_JCR_PATH   = DRIVE_ROOT_JCR_PATH + "/B";

  private static final String                         SOURCE_JCR_PATH     = DRIVE_ROOT_JCR_PATH + "/S";

  @Mock
  private IdentityManager                             identityManager;

  @Mock
  private SpaceService                                spaceService;

  @Mock
  private NodeHierarchyCreator                        nodeHierarchyCreator;

  @Mock
  private SessionProviderService                      sessionProviderService;

  @Mock
  private WebDavPathMappingStorage                    mappingStorage;

  @Mock
  private SessionImpl                                 session;

  @Mock
  private Identity                                    identity;

  @Mock
  private Profile                                     profile;

  @Mock
  private Node                                        userNode;

  @Mock
  private SessionProvider                             sessionProvider;

  @Mock
  private TrashStorage                                trashStorage;

  private final Map<String, NodeImpl>                 nodes               = new HashMap<>();

  private final Map<String, WebDavPathMappingEntity>  mappings            = new HashMap<>();

  private WebDavPathMappingEntity                     existingMapping;

  private WebdavWriteCommandHandler                   handler;

  @Before
  public void setUp() throws Exception {
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    when(nodeHierarchyCreator.getUserNode(sessionProvider, "user1")).thenReturn(userNode);
    when(nodeHierarchyCreator.getJcrPath("groupsPath")).thenReturn("/groups");
    when(nodeHierarchyCreator.getJcrPath("usersPath")).thenReturn("/users");
    when(identityManager.getIdentity(123L)).thenReturn(identity);
    when(identityManager.getOrCreateUserIdentity("user1")).thenReturn(identity);
    when(identity.isUser()).thenReturn(true);
    when(identity.getRemoteId()).thenReturn("user1");
    when(identity.getIdentityId()).thenReturn(123L);
    when(identity.getId()).thenReturn("123");
    when(identity.getProfile()).thenReturn(profile);
    when(profile.getFullName()).thenReturn("John Doe");
    when(session.getUserID()).thenReturn("user1");

    NodeImpl driveRoot = node(DRIVE_ROOT_JCR_PATH, "drive-root");
    when(userNode.getNode("Private")).thenReturn(driveRoot);
    when(node(EXISTING_JCR_PATH, "existing").getParent()).thenReturn(driveRoot);
    when(node(SOURCE_JCR_PATH, "source").getParent()).thenReturn(driveRoot);
    NodeImpl folder = node(DRIVE_ROOT_JCR_PATH + "/F", "folder");
    when(folder.getParent()).thenReturn(driveRoot);
    when(node(DRIVE_ROOT_JCR_PATH + "/F/G", "child").getParent()).thenReturn(folder);
    existingMapping = mapping("B", EXISTING_JCR_PATH, "existing");
    mapping("F", DRIVE_ROOT_JCR_PATH + "/F", "folder");
    mapping(DRIVE_ROOT_JCR_PATH + "/F", "G", DRIVE_ROOT_JCR_PATH + "/F/G", "child", DRIVE + "/F/G");
    mapping("S", SOURCE_JCR_PATH, "source");

    when(session.itemExists(anyString())).thenAnswer(invocation -> nodes.containsKey(normalize(invocation.getArgument(0))));
    when(session.getItem(anyString())).thenAnswer(invocation -> {
      NodeImpl item = nodes.get(normalize(invocation.getArgument(0)));
      if (item == null) {
        throw new PathNotFoundException((String) invocation.getArgument(0));
      }
      return item;
    });

    when(mappingStorage.findByParentJcrPathAndNormalizedVisibleName(anyString(), anyString()))
                                                                                                .thenAnswer(invocation -> Optional.ofNullable(mappings.get(key(invocation.getArgument(0),
                                                                                                                                                              invocation.getArgument(1)))));
    when(mappingStorage.findJcrPath(anyString(), anyString())).thenAnswer(invocation -> {
      WebDavPathMappingEntity entity = mappings.get(key(invocation.getArgument(0), invocation.getArgument(1)));
      return entity == null ? null : entity.getJcrPath();
    });
    when(mappingStorage.findByJcrPath(anyString())).thenAnswer(invocation -> mappings.values()
                                                                                      .stream()
                                                                                      .filter(e -> Objects.equals(e.getJcrPath(),
                                                                                                                  invocation.getArgument(0)))
                                                                                      .findFirst());
    when(mappingStorage.findByNodeIdentifier(anyString())).thenAnswer(invocation -> mappings.values()
                                                                                            .stream()
                                                                                            .filter(e -> Objects.equals(e.getNodeIdentifier(),
                                                                                                                        invocation.getArgument(0)))
                                                                                            .findFirst());

    PathCommandHandler pathCommandHandler = new PathCommandHandler();
    ReflectionTestUtils.setField(pathCommandHandler, "identityManager", identityManager);
    ReflectionTestUtils.setField(pathCommandHandler, "spaceService", spaceService);
    ReflectionTestUtils.setField(pathCommandHandler, "nodeHierarchyCreator", nodeHierarchyCreator);
    ReflectionTestUtils.setField(pathCommandHandler, "sessionProviderService", sessionProviderService);
    ReflectionTestUtils.setField(pathCommandHandler, "webDavPathMappingStorage", mappingStorage);
    handler = new WebdavWriteCommandHandler(sessionProviderService, null, trashStorage, null, pathCommandHandler);
  }

  @Test
  public void testMoveOntoAnExistingNameWithoutOverwriteIsRefused() {
    assertStatus(409, () -> handler.move(session, DRIVE + "/S", DRIVE + "/B", false));

    assertExistingDocumentKeepsItsName();
  }

  @Test
  public void testMoveOntoAnExistingNameUnderAParentSpelledWithADotIsRefused() {
    assertStatus(403, () -> handler.move(session, DRIVE + "/S", DRIVE + "/./B", false));

    assertExistingDocumentKeepsItsName();
  }

  @Test
  public void testCopyOntoAnExistingNameUnderAParentSpelledWithADotDotIsRefused() {
    assertStatus(403, () -> handler.copy(session, DRIVE + "/S", DRIVE + "/S/../B", false, false));

    assertExistingDocumentKeepsItsName();
  }

  @Test
  public void testMoveOntoATargetNameThatIsADotSegmentIsRefused() {
    assertStatus(403, () -> handler.move(session, DRIVE + "/S", DRIVE + "/F/G/..", true));
    assertStatus(403, () -> handler.move(session, DRIVE + "/S", DRIVE + "/F/.", true));

    assertExistingDocumentKeepsItsName();
  }

  @Test
  public void testMoveOntoATargetNameHoldingASlashIsRefused() {
    assertStatus(403, () -> handler.move(session, DRIVE + "/S", DRIVE + "/F%2FG%2F..", true));

    assertExistingDocumentKeepsItsName();
  }

  @Test
  public void testMoveOntoAPathWithAnEmptyLastSegmentIsRefused() {
    assertStatus(403, () -> handler.move(session, DRIVE + "/S", DRIVE + "/F//", true));

    assertExistingDocumentKeepsItsName();
  }

  @Test
  public void testMoveOfAFolderOntoAnExistingNameWithATrailingSlashChecksThatName() {
    assertStatus(409, () -> handler.move(session, DRIVE + "/S", DRIVE + "/F/", false));

    assertExistingDocumentKeepsItsName();
  }

  private void assertExistingDocumentKeepsItsName() {
    assertEquals(EXISTING_JCR_PATH, existingMapping.getJcrPath());
    try {
      verify(session, never()).move(anyString(), anyString());
      verify(mappingStorage, never()).save(any());
      verify(trashStorage, never()).moveToTrash(any(), any());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private void assertStatus(int status, org.junit.function.ThrowingRunnable runnable) {
    WebDavException exception = assertThrows(WebDavException.class, runnable);
    assertEquals(status, exception.getHttpError());
  }

  private NodeImpl node(String path, String identifier) throws Exception {
    NodeImpl item = mock(NodeImpl.class);
    when(item.getPath()).thenReturn(path);
    when(item.getName()).thenReturn(path.substring(path.lastIndexOf('/') + 1));
    when(item.getIdentifier()).thenReturn(identifier);
    when(item.getSession()).thenReturn(session);
    nodes.put(path, item);
    return item;
  }

  private WebDavPathMappingEntity mapping(String visibleName, String jcrPath, String identifier) {
    return mapping(DRIVE_ROOT_JCR_PATH, visibleName, jcrPath, identifier, DRIVE + "/" + visibleName);
  }

  private WebDavPathMappingEntity mapping(String parentJcrPath,
                                          String visibleName,
                                          String jcrPath,
                                          String identifier,
                                          String webDavPath) {
    WebDavPathMappingEntity entity = new WebDavPathMappingEntity();
    entity.setId(WebDavPathMappingEntity.buildId(parentJcrPath, visibleName));
    entity.setParentJcrPath(parentJcrPath);
    entity.setVisibleName(visibleName);
    entity.setNormalizedVisibleName(visibleName);
    entity.setJcrPath(jcrPath);
    entity.setNodeIdentifier(identifier);
    entity.setWebDavPath(webDavPath);
    mappings.put(key(parentJcrPath, visibleName), entity);
    return entity;
  }

  private static String key(String parentJcrPath, String visibleName) {
    return parentJcrPath + "::" + visibleName;
  }

  private static String normalize(String path) {
    Deque<String> segments = new ArrayDeque<>();
    for (String segment : path.split("/")) {
      if (segment.isEmpty() || segment.equals(".")) {
        continue;
      }
      if (segment.equals("..")) {
        segments.pollLast();
      } else {
        segments.addLast(segment);
      }
    }
    return "/" + String.join("/", segments);
  }
}
