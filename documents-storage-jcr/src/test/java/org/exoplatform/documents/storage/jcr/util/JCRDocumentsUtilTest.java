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

import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getNodeByIdentifier;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;

import javax.jcr.*;
import javax.jcr.nodetype.NodeType;
import javax.jcr.version.Version;

import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.FileNode;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.value.StringValue;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

@RunWith(MockitoJUnitRunner.Silent.class)
public class JCRDocumentsUtilTest {

  private static final MockedStatic<CommonsUtils>        COMMONS_UTILS_UTIL    = mockStatic(CommonsUtils.class);

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    COMMONS_UTILS_UTIL.close();
  }

  @Before
  public void setUp() throws Exception {
    JCRDeleteFileStorage jcrDeleteFileStorage = mock(JCRDeleteFileStorage.class);
    COMMONS_UTILS_UTIL.when(() -> CommonsUtils.getService(JCRDeleteFileStorage.class)).thenReturn(jcrDeleteFileStorage);
  }

  @Test
  public void testRetrieveFileProperties() throws IOException, RepositoryException {
    IdentityManager identityManager = mock(IdentityManager.class);
    NodeImpl node = mock(NodeImpl.class);
    Identity aclIdentity = mock(Identity.class);
    AbstractNode documentNode = mock(AbstractNode.class);
    SpaceService spaceService = mock(SpaceService.class);

    // Build node properties
    NodeImpl parentNode = mock(NodeImpl.class);
    when(((NodeImpl) parentNode).getIdentifier()).thenReturn("identifierOfParentNode");
    when(node.getParent()).thenReturn(parentNode);
    when(node.getName()).thenReturn("NodeName.pdf");
    Property property = mock(Property.class);
    when(((ExtendedNode) node).getACL()).thenReturn(new AccessControlList());
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(true);
    Version baseVersion = mock(Version.class);
    when(baseVersion.getName()).thenReturn("1");
    when(node.getBaseVersion()).thenReturn(baseVersion);
    when(node.hasProperty(NodeTypeConstants.EXO_DATE_CREATED)).thenReturn(true);
    when(node.hasProperty(NodeTypeConstants.EXO_DATE_MODIFIED)).thenReturn(true);
    Property createdDateProperty = mock(Property.class);
    when(createdDateProperty.getDate()).thenReturn(Calendar.getInstance());
    Property modifiedDateProperty = mock(Property.class);
    when(modifiedDateProperty.getDate()).thenReturn(Calendar.getInstance());
    when(node.getProperty(NodeTypeConstants.EXO_DATE_CREATED)).thenReturn(createdDateProperty);
    when(node.getProperty(NodeTypeConstants.EXO_DATE_MODIFIED)).thenReturn(modifiedDateProperty);
    Property lasdtModifierProperty = mock(Property.class);
    when(lasdtModifierProperty.getString()).thenReturn("root");
    when(node.getProperty(NodeTypeConstants.EXO_LAST_MODIFIER)).thenReturn(lasdtModifierProperty);

    when(aclIdentity.getUserId()).thenReturn("root");

    // When
    when(node.getProperty(NodeTypeConstants.DC_DESCRIPTION)).thenReturn(property);
    when(node.hasProperty(NodeTypeConstants.DC_DESCRIPTION)).thenReturn(true);
    when(node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getString()).thenThrow(new ValueFormatException());
    // Then
    try {
      JCRDocumentsUtil.retrieveFileProperties(identityManager, node, aclIdentity, documentNode, spaceService);
    } catch (Exception e) {
      // Exception should be catched
      fail();
    }

    // When
    when(node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getValues()).thenReturn(new Value[0]);
    // Then
    JCRDocumentsUtil.retrieveFileProperties(identityManager, node, aclIdentity, documentNode, spaceService);
    verify(documentNode, times(0)).setDescription(anyString());

    // When
    when(node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getValues()).thenReturn(new Value[1]);
    Value[] descriptionValues = new Value[]{new StringValue("File description !")};
    when(node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getValues()).thenReturn(descriptionValues);


    JCRDocumentsUtil.retrieveFileProperties(identityManager, node, aclIdentity, documentNode, spaceService);
    // Then
    verify(documentNode, times(1)).setDescription(anyString());

  }

  @Test
  public void testToFileNodes() throws  RepositoryException {
    IdentityManager identityManager = mock(IdentityManager.class);
    Identity aclIdentity = mock(Identity.class);
    SpaceService spaceService = mock(SpaceService.class);

    // Initiating files

    // This file will be converted and returned
    NodeImpl file = mock(NodeImpl.class);
    NodeImpl fileContent = mock(NodeImpl.class);
    when(file.getName()).thenReturn("document-test.pdf");
    when(file.getIdentifier()).thenReturn("fileIdentifier");
    when(file.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(file.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(fileContent);
    when(fileContent.hasProperty(NodeTypeConstants.DC_DESCRIPTION)).thenReturn(false);
    when(fileContent.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(true);
    Property mimeTypeProperty = mock(Property.class);
    when(mimeTypeProperty.getString()).thenReturn("application/pdf");
    when(fileContent.getProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(mimeTypeProperty);
    when(file.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(false);
    when(file.getACL()).thenReturn(new AccessControlList());

    // This file inside a folder's symlink will be converted and returned
    NodeImpl fileInFolderSymlink = mock(NodeImpl.class);
    NodeImpl fileContentSymlink = mock(NodeImpl.class);
    when(fileInFolderSymlink.getName()).thenReturn("second-document-test.pdf");
    when(fileInFolderSymlink.getIdentifier()).thenReturn("fileIdentifierInsideSymlink");
    when(fileInFolderSymlink.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(fileInFolderSymlink.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(fileContent);
    when(fileContentSymlink.hasProperty(NodeTypeConstants.DC_DESCRIPTION)).thenReturn(false);
    when(fileContentSymlink.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(true);
    when(fileContentSymlink.getProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(mimeTypeProperty);
    when(fileInFolderSymlink.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(false);
    when(fileInFolderSymlink.getACL()).thenReturn(new AccessControlList());

    // Folder where we search files
    NodeImpl folderNode = mock(NodeImpl.class);
    when(folderNode.getIdentifier()).thenReturn("folderIdentifier");
    when(folderNode.getPath()).thenReturn("/path/folderNode");
    when(folderNode.isNodeType("nt:folder")).thenReturn(true);

    // folder which symlink is inside FolderNode
    NodeImpl anotherFolderNode = mock(NodeImpl.class);
    when(anotherFolderNode.getIdentifier()).thenReturn("anotherFolderIdentifier");
    when(anotherFolderNode.getPath()).thenReturn("/path/antherFolder/anotherFolderNode");
    when(anotherFolderNode.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    NodeType anotherFolderPrimaryNT = mock(NodeType.class);
    when(anotherFolderPrimaryNT.getName()).thenReturn("nt:folder");
    when(anotherFolderNode.getPrimaryNodeType()).thenReturn(anotherFolderPrimaryNT);

    // Files iterator of anotherFolderNode
    NodeIterator anotherFolderNodeIterator = mock(NodeIterator.class);
    when(anotherFolderNodeIterator.hasNext()).thenReturn(true,false);
    when(anotherFolderNodeIterator.nextNode()).thenReturn(fileInFolderSymlink);
    when(anotherFolderNode.getNodes()).thenReturn(anotherFolderNodeIterator);

    NodeImpl anotherFolderLink = mock(NodeImpl.class);
    when(anotherFolderLink.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true);
    Property anotherFolderSymlinkUUID = mock(Property.class);
    when(anotherFolderSymlinkUUID.getString()).thenReturn("anotherFolderIdentifier");
    when(anotherFolderLink.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(anotherFolderSymlinkUUID);
    when(anotherFolderLink.getPath()).thenReturn("/path/folderNode/anotherFolderLink.lnk");
    when(anotherFolderLink.getACL()).thenReturn(new AccessControlList());


    // this folder will be ignored since it is inside its source folder
    NodeImpl folderLink = mock(NodeImpl.class);
    folderLink.setProperty(NodeTypeConstants.EXO_SYMLINK_UUID,"test");
    when(folderLink.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true);
    Property symlinkUUID = mock(Property.class);
    when(symlinkUUID.getString()).thenReturn("folderIdentifier");
    when(folderLink.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(symlinkUUID);
    when(folderLink.getPath()).thenReturn("/path/folderNode/folderLink.lnk");
    when(folderLink.getACL()).thenReturn(new AccessControlList());

    NodeImpl fileToDelete = mock(NodeImpl.class);
    when(fileToDelete.getIdentifier()).thenReturn("fileToDeleteIdentifier");

    // Files iterator of folderNode
    NodeIterator folderNodeIterator = mock(NodeIterator.class);
    when(folderNodeIterator.hasNext()).thenReturn(true,true, true, false);
    when(folderNodeIterator.nextNode()).thenReturn(file, folderLink, anotherFolderLink);

    ExtendedSession session = mock(ExtendedSession.class);
    when(session.getNodeByIdentifier("folderIdentifier")).thenReturn(folderNode);
    when(session.getNodeByIdentifier("anotherFolderIdentifier")).thenReturn(anotherFolderNode);

    List <FileNode> fileNodes = JCRDocumentsUtil.toFileNodes(identityManager, folderNodeIterator, aclIdentity, session, spaceService,false);
    assertEquals(2, fileNodes.size());
    assertEquals("document-test.pdf", fileNodes.get(0).getName());
    assertEquals("second-document-test.pdf", fileNodes.get(1).getName());
  }
  @Test
  public void testToNodes() throws RepositoryException {
    IdentityManager identityManager = mock(IdentityManager.class);
    SpaceService spaceService = mock(SpaceService.class);
    Identity identity = mock(Identity.class);
    NodeIterator nodeIterator = mock(NodeIterator.class);
    SessionImpl session= mock(SessionImpl.class);
    NodeImpl file1 = mock(NodeImpl.class);
    NodeImpl file2 = mock(NodeImpl.class);
    ExtendedSession extendedSession = mock(ExtendedSession.class);

    //when
    when(nodeIterator.hasNext()).thenReturn(true, false);
    when(nodeIterator.nextNode()).thenReturn(file1);
    when(file1.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true);
    Property symlinkUUID1 = mock(Property.class);
    when(symlinkUUID1.getString()).thenReturn("file1Identifier");
    when(file1.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(symlinkUUID1);
    when(extendedSession.getNodeByIdentifier("file1Identifier")).thenReturn(null);
    //then
    List <AbstractNode> listNodes1 = JCRDocumentsUtil.toNodes(identityManager, extendedSession, nodeIterator, identity, spaceService,false);
    assertEquals(0, listNodes1.size());

    //when
    when(file2.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true);
    Property symlinkUUID2 = mock(Property.class);
    when(symlinkUUID2.getString()).thenReturn("file2Identifier");
    when(file2.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(symlinkUUID2);
    when(extendedSession.getNodeByIdentifier("file2Identifier")).thenReturn(file2);
    when(file2.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
    when(file2.getSession()).thenReturn(session);
    NodeType filePrimaryNT = mock(NodeType.class);
    when(file2.getPrimaryNodeType()).thenReturn(filePrimaryNT);
    when(file2.getPrimaryNodeType().getName()).thenReturn("");
    when(nodeIterator.hasNext()).thenReturn(true, true, false);
    when(nodeIterator.nextNode()).thenReturn(file1, file2);
    //then
    List <AbstractNode> listNodes2 = JCRDocumentsUtil.toNodes(identityManager, extendedSession, nodeIterator, identity, spaceService,false);
    assertEquals(1, listNodes2.size());
  }

  @Test
  public void retrieveFileContentProperties() throws RepositoryException, IOException {
    Node contentNode = mock(Node.class);
    FileNode fileNode = mock(FileNode.class);

    lenient().when(contentNode.hasProperty(NodeTypeConstants.DC_DESCRIPTION)).thenReturn(true);
    lenient().when(contentNode.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(true);
    lenient().when(contentNode.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(true);

    Property descriptionProperty = mock(Property.class);
    Property mimeTypeProperty = mock(Property.class);
    Property dataProperty = mock(Property.class);

    lenient().when(contentNode.getProperty(NodeTypeConstants.DC_DESCRIPTION)).thenReturn(descriptionProperty);
    lenient().when(contentNode.getProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(mimeTypeProperty);
    lenient().when(contentNode.getProperty(NodeTypeConstants.JCR_DATA)).thenReturn(dataProperty);

    lenient().when(descriptionProperty.getValues()).thenReturn(new Value[0]);
    lenient().when(mimeTypeProperty.getString()).thenReturn("application/pdf");
    lenient().when(dataProperty.getLength()).thenReturn(1024L);
    
    JCRDocumentsUtil.retrieveFileContentProperties(contentNode, fileNode);

    verify(fileNode, times(0)).setDescription(anyString());
    verify(fileNode, times(1)).setMimeType(anyString());
    verify(fileNode, times(1)).setSize(anyLong());

    lenient().when(descriptionProperty.getValues()).thenReturn(new Value[] { new StringValue("test description") });
    JCRDocumentsUtil.retrieveFileContentProperties(contentNode, fileNode);

    verify(fileNode, times(1)).setDescription(anyString());
  }
  }
