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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
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
import org.exoplatform.services.jcr.impl.core.WorkspaceImpl;
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

  private static final String       WEBDAV_SOURCE_PATH = "/John%20Doe%20%281%29/Source.txt";// NOSONAR

  private static final String       SOURCE_JCR_PATH    = "/jcr/folder/source";        // NOSONAR

  private static final String       TARGET_JCR_PATH    = "/jcr/existing";             // NOSONAR

  private static final String       NEW_TARGET_JCR_PATH = PARENT_JCR_PATH + "/" + TECHNICAL_NAME;

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

  @Mock
  private NodeImpl                  sourceNode;

  @Mock
  private NodeImpl                  existingNode;

  @Mock
  private WorkspaceImpl             workspace;

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
    when(pathCommandHandler.getIdentityBaseJcrPath(anyString())).thenReturn(PARENT_JCR_PATH);

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

  @Test
  @SneakyThrows
  public void testDeleteChecksThenTrashesThenDeletesMappingInOrder() {
    givenExistingTarget();

    handler.delete(session, WEBDAV_FILE_PATH);

    InOrder inOrder = inOrder(pathCommandHandler, existingNode, trashStorage, session);
    inOrder.verify(pathCommandHandler).isIdentityRootWebDavPath(WEBDAV_FILE_PATH);
    inOrder.verify(pathCommandHandler).resolveToJcrPath(session, WEBDAV_FILE_PATH);
    inOrder.verify(existingNode).checkPermission(PermissionType.REMOVE);
    inOrder.verify(trashStorage).moveToTrash(eq(existingNode), any(SessionProvider.class));
    inOrder.verify(pathCommandHandler).deleteMapping(TARGET_JCR_PATH);
    inOrder.verify(session).save();
  }

  @Test
  @SneakyThrows
  public void testMoveToFreeNameCreatesTheTarget() {
    givenMoveSource();
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_FILE_PATH)).thenThrow(new WebDavException(404, "missing"));

    assertFalse(handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true));

    verify(trashStorage, never()).moveToTrash(any(), any());
    verify(session).move(SOURCE_JCR_PATH, NEW_TARGET_JCR_PATH);
    verify(pathCommandHandler).saveMapping(session, WEBDAV_FILE_PATH, VISIBLE_NAME, node);
  }

  @Test
  @SneakyThrows
  public void testMoveOntoExistingNameReplacesTheTargetWhenUserMayRemoveIt() {
    givenMoveSource();
    givenExistingTarget();

    assertTrue(handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true));

    InOrder inOrder = inOrder(trashStorage, pathCommandHandler, session);
    inOrder.verify(trashStorage).moveToTrash(eq(existingNode), any(SessionProvider.class));
    inOrder.verify(pathCommandHandler).deleteMapping(TARGET_JCR_PATH);
    inOrder.verify(pathCommandHandler).allocateTechnicalName(session, PARENT_JCR_PATH, VISIBLE_NAME);
    inOrder.verify(session).move(SOURCE_JCR_PATH, NEW_TARGET_JCR_PATH);
    inOrder.verify(pathCommandHandler).saveMapping(session, WEBDAV_FILE_PATH, VISIBLE_NAME, node);
  }

  @Test
  @SneakyThrows
  public void testMoveOntoExistingNameIsRefusedWhenUserMayNotRemoveTheTarget() {
    givenMoveSource();
    givenExistingTarget();
    doThrow(new AccessControlException("denied")).when(existingNode).checkPermission(PermissionType.REMOVE);

    WebDavException exception = assertStatus(403, () -> handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true));

    assertFalse(exception.getMessage().contains(TARGET_JCR_PATH));
    assertTrue(exception.getMessage().contains(WEBDAV_FILE_PATH));
    assertTargetUntouched();
  }

  @Test
  @SneakyThrows
  public void testMoveOntoExistingNameIsRefusedWithoutOverwrite() {
    givenMoveSource();
    givenExistingTarget();

    assertStatus(409, () -> handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, false));

    assertTargetUntouched();
  }

  @Test
  @SneakyThrows
  public void testMoveOntoNameOfADocumentTheUserCannotSeeIsRefused() {
    givenMoveSource();
    givenExistingTarget();
    when(session.itemExists(TARGET_JCR_PATH)).thenReturn(false);

    assertStatus(403, () -> handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true));

    assertTargetUntouched();
  }

  @Test
  @SneakyThrows
  public void testMoveOntoItselfIsRefused() {
    givenMoveSource();
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_FILE_PATH)).thenReturn(SOURCE_JCR_PATH);

    assertStatus(403, () -> handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true));

    assertTargetUntouched();
  }

  @Test
  @SneakyThrows
  public void testMoveOntoAnAncestorOfTheSourceIsRefused() {
    givenMoveSource();
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_FILE_PATH)).thenReturn(PARENT_JCR_PATH + "/folder");
    givenNodeAt(PARENT_JCR_PATH + "/folder", PARENT_JCR_PATH + "/folder");

    assertStatus(403, () -> handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true));

    assertTargetUntouched();
  }

  @Test
  @SneakyThrows
  public void testMoveOntoADescendantOfTheSourceIsRefused() {
    givenMoveSource();
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_FILE_PATH)).thenReturn(SOURCE_JCR_PATH + "/child");
    givenNodeAt(SOURCE_JCR_PATH + "/child", SOURCE_JCR_PATH + "/child");

    assertStatus(403, () -> handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true));

    assertTargetUntouched();
  }

  @Test
  @SneakyThrows
  public void testMoveOntoTheRepositoryRootIsRefused() {
    givenMoveSource();
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_FILE_PATH)).thenReturn("/");
    givenNodeAt("/", "/");

    assertStatus(403, () -> handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true));

    assertTargetUntouched();
  }

  @Test
  @SneakyThrows
  public void testMoveOntoTheRootOfTheTargetDriveIsRefused() {
    givenMoveSource();
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_SOURCE_PATH)).thenReturn("/other/source");
    when(session.getItem("/other/source")).thenReturn(sourceNode);
    when(session.itemExists("/other/source")).thenReturn(true);
    when(sourceNode.getPath()).thenReturn("/other/source");
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_FILE_PATH)).thenReturn(PARENT_JCR_PATH + "/folder/..");
    givenNodeAt(PARENT_JCR_PATH + "/folder/..", PARENT_JCR_PATH);

    assertStatus(403, () -> handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true));

    assertTargetUntouched();
  }

  @Test
  @SneakyThrows
  public void testMoveOntoANodeOutsideTheTargetDriveIsRefused() {
    givenMoveSource();
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_FILE_PATH)).thenReturn(PARENT_JCR_PATH + "/../home/file");
    givenNodeAt(PARENT_JCR_PATH + "/../home/file", "/home/file");

    assertStatus(403, () -> handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true));

    assertTargetUntouched();
  }

  @Test
  @SneakyThrows
  public void testMoveOntoAnotherSpellingOfTheSourcePathIsRefused() {
    givenMoveSource();
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_FILE_PATH)).thenReturn(PARENT_JCR_PATH + "/other/../folder/source");
    givenNodeAt(PARENT_JCR_PATH + "/other/../folder/source", SOURCE_JCR_PATH);

    assertStatus(403, () -> handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true));

    assertTargetUntouched();
  }

  @Test
  @SneakyThrows
  public void testMoveKeepsATargetResolutionFailureOtherThanNotFound() {
    givenMoveSource();
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_FILE_PATH)).thenThrow(new WebDavException(400, "unsupported"));

    assertStatus(400, () -> handler.move(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true));

    assertTargetUntouched();
  }

  @Test
  @SneakyThrows
  public void testCopyToFreeNameCreatesTheTarget() {
    givenCopySource();
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_FILE_PATH)).thenThrow(new WebDavException(404, "missing"));

    handler.copy(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true, false);

    verify(trashStorage, never()).moveToTrash(any(), any());
    verify(workspace).copy(SOURCE_JCR_PATH, NEW_TARGET_JCR_PATH);
    verify(pathCommandHandler).saveMapping(session, WEBDAV_FILE_PATH, VISIBLE_NAME, node);
  }

  @Test
  @SneakyThrows
  public void testCopyOntoExistingNameReplacesTheTargetWhenOverwriteIsAllowed() {
    givenCopySource();
    givenExistingTarget();

    handler.copy(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true, false);

    assertTargetReplacedThenCopied();
  }

  /**
   * Pins the {@code removeDestination} flag of the
   * {@code DocumentWebDavService#copy} contract on its own. The WebDAV WAR does
   * not send this combination: {@code Overwrite: T} sets both flags.
   */
  @Test
  @SneakyThrows
  public void testCopyOntoExistingNameReplacesTheTargetWhenDestinationRemovalIsAsked() {
    givenCopySource();
    givenExistingTarget();

    handler.copy(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, false, true);

    assertTargetReplacedThenCopied();
  }

  @Test
  @SneakyThrows
  public void testCopyOntoExistingNameIsRefusedWhenUserMayNotRemoveTheTarget() {
    givenCopySource();
    givenExistingTarget();
    doThrow(new AccessControlException("denied")).when(existingNode).checkPermission(PermissionType.REMOVE);

    assertStatus(403, () -> handler.copy(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true, true));

    assertTargetUntouched();
    verify(workspace, never()).copy(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  public void testCopyOntoExistingNameIsRefusedWithoutOverwrite() {
    givenCopySource();
    givenExistingTarget();

    assertStatus(409, () -> handler.copy(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, false, false));

    assertTargetUntouched();
    verify(workspace, never()).copy(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  public void testCopyOntoNameOfADocumentTheUserCannotSeeIsRefused() {
    givenCopySource();
    givenExistingTarget();
    when(session.itemExists(TARGET_JCR_PATH)).thenReturn(false);

    assertStatus(403, () -> handler.copy(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true, true));

    assertTargetUntouched();
    verify(workspace, never()).copy(anyString(), anyString());
  }

  @Test
  @SneakyThrows
  public void testCopyOntoItselfIsRefused() {
    givenCopySource();
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_FILE_PATH)).thenReturn(SOURCE_JCR_PATH);

    assertStatus(403, () -> handler.copy(session, WEBDAV_SOURCE_PATH, WEBDAV_FILE_PATH, true, true));

    assertTargetUntouched();
    verify(workspace, never()).copy(anyString(), anyString());
  }

  @SneakyThrows
  private void givenMoveSource() {
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_SOURCE_PATH)).thenReturn(SOURCE_JCR_PATH);
    when(session.getItem(SOURCE_JCR_PATH)).thenReturn(sourceNode);
    when(session.itemExists(SOURCE_JCR_PATH)).thenReturn(true);
    when(session.itemExists(NEW_TARGET_JCR_PATH)).thenReturn(false);
    when(sourceNode.getPath()).thenReturn(SOURCE_JCR_PATH);
    when(sourceNode.isLocked()).thenReturn(false);
  }

  @SneakyThrows
  private void givenNodeAt(String resolvedPath, String nodePath) {
    NodeImpl resolvedNode = org.mockito.Mockito.mock(NodeImpl.class);
    when(resolvedNode.getPath()).thenReturn(nodePath);
    when(session.getItem(resolvedPath)).thenReturn(resolvedNode);
  }

  @SneakyThrows
  private void givenCopySource() {
    givenMoveSource();
    when(session.getWorkspace()).thenReturn(workspace);
  }

  @SneakyThrows
  private void givenExistingTarget() {
    when(pathCommandHandler.resolveToJcrPath(session, WEBDAV_FILE_PATH)).thenReturn(TARGET_JCR_PATH);
    when(session.itemExists(TARGET_JCR_PATH)).thenReturn(true);
    when(session.getItem(TARGET_JCR_PATH)).thenReturn(existingNode);
    when(session.getUserState()).thenReturn(conversationState);
    when(existingNode.getPath()).thenReturn(TARGET_JCR_PATH);
    when(existingNode.getSession()).thenReturn(session);
    when(existingNode.isLocked()).thenReturn(false);
  }

  @SneakyThrows
  private void assertTargetReplacedThenCopied() {
    InOrder inOrder = inOrder(trashStorage, pathCommandHandler, workspace);
    inOrder.verify(trashStorage).moveToTrash(eq(existingNode), any(SessionProvider.class));
    inOrder.verify(pathCommandHandler).deleteMapping(TARGET_JCR_PATH);
    inOrder.verify(workspace).copy(SOURCE_JCR_PATH, NEW_TARGET_JCR_PATH);
    inOrder.verify(pathCommandHandler).saveMapping(session, WEBDAV_FILE_PATH, VISIBLE_NAME, node);
  }

  @SneakyThrows
  private void assertTargetUntouched() {
    verify(trashStorage, never()).moveToTrash(any(), any());
    verify(pathCommandHandler, never()).deleteMapping(anyString());
    verify(session, never()).move(anyString(), anyString());
    verify(pathCommandHandler, never()).saveMapping(any(), anyString(), anyString(), any());
  }

  private WebDavException assertStatus(int status, org.junit.function.ThrowingRunnable runnable) {
    WebDavException exception = assertThrows(WebDavException.class, runnable);
    assertEquals(status, exception.getHttpError());
    return exception;
  }
}
