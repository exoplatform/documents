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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.version.Version;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;

/**
 * When the access lists cannot be put back after a cancelled checkout whose
 * private-access mixin has to change, the error reaches the caller and the
 * document is not checked in.
 */
class WebdavWriteCommandHandlerUncheckoutFailureTest {

  @Test
  void theErrorPropagatesAndNoVersionIsCreatedWhenTheAccessListCannotBePutBack() throws Exception {
    RepositoryService repositoryService = mock(RepositoryService.class);
    when(repositoryService.getDefaultRepository()).thenReturn(mock(ManageableRepository.class));
    PathCommandHandler pathCommandHandler = mock(PathCommandHandler.class);
    Session session = mock(Session.class);
    Node root = mock(Node.class);
    Node node = mock(Node.class);
    Workspace workspace = mock(Workspace.class);
    when(pathCommandHandler.resolveToJcrPath(session, "/doc.txt")).thenReturn("/space/doc.txt");
    when(session.itemExists("/space/doc.txt")).thenReturn(true);
    when(session.getRootNode()).thenReturn(root);
    when(root.getNode(any(String.class))).thenReturn(node);
    when(node.getBaseVersion()).thenReturn(mock(Version.class));
    when(node.getUUID()).thenReturn("123");
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getName()).thenReturn("collaboration");
    WebdavWriteCommandHandler handler = new WebdavWriteCommandHandler(null, repositoryService, null, null, pathCommandHandler);

    try (MockedStatic<JCRDocumentsUtil> util = mockStatic(JCRDocumentsUtil.class)) {
      // The document had no list of its own, the base version has one: the mixin changes
      util.when(() -> JCRDocumentsUtil.getOwnPermissions(node)).thenReturn(null);
      util.when(() -> JCRDocumentsUtil.getDescendantsOwnPermissions(node)).thenReturn(Map.of());
      util.when(() -> JCRDocumentsUtil.needsMixinChange(any(), anyString(), anyString(), nullable(Map.class), anyMap()))
          .thenReturn(true);
      util.when(() -> JCRDocumentsUtil.putBackOwnPermissions(any(), anyString(), anyString(), nullable(Map.class), anyMap()))
          .thenThrow(new RepositoryException("refused"));

      RepositoryException thrown = assertThrows(RepositoryException.class, () -> handler.uncheckout(session, "/doc.txt"));

      assertEquals("refused", thrown.getMessage());
    }
    verify(node).checkout();
    verify(node, never()).checkin();
  }
}
