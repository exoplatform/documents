/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.storage.jcr.util;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.FileNode;

import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.value.StringValue;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.jcr.*;
import javax.jcr.nodetype.NodeType;
import javax.jcr.version.Version;

import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*" })
@PrepareForTest({ CommonsUtils.class})
public class JCRDocumentsUtilTest {
  
  @Before
  public void setUp() throws Exception {
    PowerMockito.mockStatic(CommonsUtils.class);
    JCRDeleteFileStorage jcrDeleteFileStorage = mock(JCRDeleteFileStorage.class);
    when(CommonsUtils.getService(JCRDeleteFileStorage.class)).thenReturn(jcrDeleteFileStorage);
  }

  @Test
  public void testToFileNodes() throws  RepositoryException {
    IdentityManager identityManager = mock(IdentityManager.class);
    Identity aclIdentity = mock(Identity.class);
    NodeIterator nodeIterator = mock(NodeIterator.class);
    ExtendedSession session = mock(ExtendedSession.class);
    SessionImpl sessionImpl = mock(SessionImpl.class);
    SpaceService spaceService = mock(SpaceService.class);
    JCRDeleteFileStorage jcrDeleteFileStorage = mock(JCRDeleteFileStorage.class);
    NodeImpl node = mock(NodeImpl.class);
    Property property = mock(Property.class);
    Property property1 = mock(Property.class);
    List<FileNode> fileNodes;
    FileNode fileNode = new FileNode();
    fileNode.setLinkedFileId("test");
    fileNode.setVersionnedFileId("test");
    fileNode.setMimeType(NodeTypeConstants.NT_FILE);
    fileNode.setSize(20);
    node.setProperty(NodeTypeConstants.EXO_SYMLINK_UUID,"test");
    Map<String, String> documentsToDelete = new HashMap<>(1,1);
    documentsToDelete.put("identifier","identifier");
    when(nodeIterator.nextNode()).thenReturn(node);
    when(nodeIterator.hasNext()).thenReturn(true,true,false);
    when(node.getIdentifier()).thenReturn("identifier");
    when(jcrDeleteFileStorage.getDocumentsToDelete()).thenReturn(documentsToDelete);
    when(node.getACL()).thenReturn(new AccessControlList());
    when(node.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true,false);
    when(node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(property);
    when(node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString()).thenReturn("1");
    when(property1.getString()).thenReturn("application/pdf");
    when(node.getProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(property1);
    when(JCRDocumentsUtil.getNodeByIdentifier(sessionImpl, "1")).thenReturn(node);
    when(node.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true,false);
    when(node.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED)).thenReturn(false);
    when(node.isNodeType(NodeTypeConstants.EXO_HIDDENABLE)).thenReturn(false);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(node.getPath()).thenReturn("test");
    when(node.getNodes()).thenReturn(nodeIterator);
    when(node.getSession()).thenReturn(sessionImpl);
    when(node.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(node);
    when(node.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    NodeType nodeType = mock(NodeType.class);
    when(nodeType.getName()).thenReturn("nt:file");
    when(node.getPrimaryNodeType()).thenReturn(nodeType);
    when(JCRDocumentsUtil.getMimeType(node)).thenReturn("application/pdf");
    when(((ExtendedSession) session).getNodeByIdentifier("1")).thenReturn(node);
    fileNodes = JCRDocumentsUtil.toFileNodes(identityManager, nodeIterator, aclIdentity, session, spaceService,false);
    assertEquals(1, fileNodes.size());
  }
}
