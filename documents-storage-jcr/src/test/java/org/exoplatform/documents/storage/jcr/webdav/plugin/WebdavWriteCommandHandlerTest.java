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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.AccessControlException;
import java.util.Collections;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.lock.Lock;
import javax.jcr.version.Version;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.webdav.model.WebDavException;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.security.ConversationState;

import lombok.SneakyThrows;

@RunWith(MockitoJUnitRunner.Silent.class)
public class WebdavWriteCommandHandlerTest {

  private static final MockedStatic<JCRDocumentsUtil> JCR_DOCUMENTS_UTIL = mockStatic(JCRDocumentsUtil.class);

  private static final String       JCR_CONTENT        = "jcr:content";

  private static final String       JCR_PATH           = "/jcr/path";                 // NOSONAR

  private static final String       PARENT_JCR_PATH    = "/jcr";                      // NOSONAR

  private static final String       WEBDAV_FILE_PATH   = "/John%20Doe%20%281%29/New%20File.txt";// NOSONAR

  private static final String       WEBDAV_PARENT_PATH = "/John%20Doe%20%281%29";     // NOSONAR

  private static final String       VISIBLE_NAME       = "New File.txt";

  private static final String       TECHNICAL_NAME     = "New_File.txt";

  @Mock
  private RepositoryService         repositoryService;

  @Mock
  private SessionProviderService    sessionProviderService;

  @Mock
  private TrashStorage              trashStorage;

  @Mock
  private SettingService            settingService;

  @Mock
  private PathCommandHandler        pathCommandHandler;

  @Mock
  private SessionImpl               session;

  @Mock
  private NodeImpl                  node;

  @Mock
  private NodeImpl                  parentNode;

  @Mock
  private Node                      contentNode;

  @Mock
  private Property                  property;

  @Mock
  private ConversationState         conversationState;

  @Mock
  private Lock                      lock;

  @Mock
  private Version                   version;

  @InjectMocks
  private WebdavWriteCommandHandler handler;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    JCR_DOCUMENTS_UTIL.close();
  }

  @Before
  @SneakyThrows
  public void setUp() {
    when(pathCommandHandler.resolveToJcrPath(eq(session), eq(JCR_PATH))).thenReturn(JCR_PATH);
    when(pathCommandHandler.resolveToJcrPath(eq(session), eq(WEBDAV_PARENT_PATH))).thenReturn(PARENT_JCR_PATH);
    when(pathCommandHandler.getLastVisibleSegment(anyString())).thenReturn(VISIBLE_NAME);
    when(pathCommandHandler.allocateTechnicalName(eq(session), eq(PARENT_JCR_PATH), anyString())).thenReturn(TECHNICAL_NAME);
    when(pathCommandHandler.getVisibleName(any(Node.class))).thenReturn(TECHNICAL_NAME);

    when(session.itemExists(anyString())).thenReturn(true);
    when(session.getItem(JCR_PATH)).thenReturn(node);
    when(session.getItem(PARENT_JCR_PATH)).thenReturn(parentNode);
    when(session.getItem(PARENT_JCR_PATH + "/" + TECHNICAL_NAME)).thenReturn(node);

    when(parentNode.addNode(TECHNICAL_NAME, "nt:file")).thenReturn(node);
    when(parentNode.addNode(TECHNICAL_NAME, "nt:folder")).thenReturn(node);

    when(node.getSession()).thenReturn(session);
    when(node.getPath()).thenReturn(PARENT_JCR_PATH + "/" + TECHNICAL_NAME);
    when(node.getName()).thenReturn(TECHNICAL_NAME);
    when(node.getParent()).thenReturn(parentNode);
    when(node.hasNode(JCR_CONTENT)).thenReturn(false);
    when(node.addNode(JCR_CONTENT, "nt:resource")).thenReturn(contentNode);
    when(node.getNode(JCR_CONTENT)).thenReturn(contentNode);
    when(node.canAddMixin(anyString())).thenReturn(false);
    when(node.isLocked()).thenReturn(false);
    when(node.isNodeType(anyString())).thenReturn(false);
    when(node.setProperty(anyString(), anyString())).thenReturn(property);

    when(contentNode.setProperty(anyString(), anyString())).thenReturn(property);
    when(contentNode.setProperty(anyString(), any(InputStream.class))).thenReturn(property);
    when(contentNode.setProperty(eq("jcr:lastModified"), any(java.util.Calendar.class))).thenReturn(property);
  }

  @Test
  @SneakyThrows
  public void testSaveFileCreatesMappingForNewVisiblePath() {
    when(pathCommandHandler.resolveToJcrPath(eq(session), eq(WEBDAV_FILE_PATH)))
                                                                                .thenThrow(new WebDavException(404, "missing"));
    when(session.itemExists(PARENT_JCR_PATH + "/" + TECHNICAL_NAME)).thenReturn(false);

    handler.saveFile(session,
                     WEBDAV_FILE_PATH,
                     "text/plain",
                     Collections.emptyList(),
                     new ByteArrayInputStream("data".getBytes()));

    verify(parentNode).addNode(TECHNICAL_NAME, "nt:file");
    verify(node).setProperty("exo:title", VISIBLE_NAME);
    verify(pathCommandHandler).saveMapping(session, WEBDAV_FILE_PATH, VISIBLE_NAME, node);
    verify(session, atLeastOnce()).save();
  }

  @Test
  @SneakyThrows
  public void testCreateFolderCreatesMappingForVisiblePath() {
    handler.createFolder(session, WEBDAV_FILE_PATH, Collections.emptyList());

    verify(parentNode).addNode(TECHNICAL_NAME, "nt:folder");
    verify(node).setProperty("exo:title", VISIBLE_NAME);
    verify(pathCommandHandler).saveMapping(session, WEBDAV_FILE_PATH, VISIBLE_NAME, node);
    verify(session).save();
  }

  @Test
  @SneakyThrows
  public void testDeleteDeletesPathMapping() {
    when(session.getUserState()).thenReturn(conversationState);
    when(node.getPath()).thenReturn(JCR_PATH);
    doNothing().when(node).checkPermission(PermissionType.REMOVE);

    handler.delete(session, JCR_PATH);

    verify(trashStorage).moveToTrash(eq(node), any(SessionProvider.class));
    verify(pathCommandHandler).deleteMapping(JCR_PATH);
    verify(session).save();
  }

  @Test
  @SneakyThrows
  public void testDeleteAllowedInsideOwnPrivateSpaceDespiteMissingAcl() {
    when(session.getUserState()).thenReturn(conversationState);
    when(node.getPath()).thenReturn(JCR_PATH);
    when(session.getUserID()).thenReturn("testuser");
    // The node's own ACL does not grant REMOVE, but it lives inside the acting user's
    // own Private space, which must be enough to move it to trash.
    doThrow(new AccessControlException("denied")).when(node).checkPermission(PermissionType.REMOVE);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isInUserPrivateSpace(node, "testuser")).thenReturn(true);

    handler.delete(session, JCR_PATH);

    verify(trashStorage).moveToTrash(eq(node), any(SessionProvider.class));
    verify(pathCommandHandler).deleteMapping(JCR_PATH);
  }

  @Test
  @SneakyThrows
  public void testDeleteDeniedOutsideOwnPrivateSpace() {
    when(node.getPath()).thenReturn(JCR_PATH);
    when(session.getUserID()).thenReturn("testuser");
    doThrow(new AccessControlException("denied")).when(node).checkPermission(PermissionType.REMOVE);
    JCR_DOCUMENTS_UTIL.when(() -> JCRDocumentsUtil.isInUserPrivateSpace(node, "testuser")).thenReturn(false);

    assertThrows(WebDavException.class, () -> handler.delete(session, JCR_PATH));

    verify(trashStorage, never()).moveToTrash(eq(node), any(SessionProvider.class));
  }

  @Test
  @SneakyThrows
  public void testEnableVersioningUsesResolvedJcrPath() {
    when(node.getPath()).thenReturn(JCR_PATH);
    when(node.isNodeType("mix:versionable")).thenReturn(false);

    handler.enableVersioning(session, JCR_PATH);

    verify(pathCommandHandler).resolveToJcrPath(session, JCR_PATH);
    verify(node).addMixin("mix:versionable");
    verify(session).save();
  }
}
