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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.FileNode;
import org.exoplatform.documents.model.FileVersion;
import org.exoplatform.documents.model.TrashElementNode;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.value.StringValue;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.GroupHandler;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
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
    when(node.getPath()).thenReturn("/Users/root/Private/NodeName.pdf");
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
    when(file.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
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
    when(fileInFolderSymlink.getName()).thenReturn("second-document-test.pdf");
    when(fileInFolderSymlink.getIdentifier()).thenReturn("fileIdentifierInsideSymlink");
    when(fileInFolderSymlink.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
    when(fileInFolderSymlink.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(fileContent);
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
    List <AbstractNode> listNodes1 = JCRDocumentsUtil.toNodes(identityManager, extendedSession, nodeIterator, identity, spaceService,false, null);
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
    AccessControlList accessControlList = mock(AccessControlList.class);
    when(accessControlList.getPermissionEntries()).thenReturn(new ArrayList<>());
    when(((ExtendedNode)file1).getACL()).thenReturn(accessControlList);
    when(((ExtendedNode)file2).getACL()).thenReturn(accessControlList);

    when(nodeIterator.nextNode()).thenReturn(file1, file2);
    //then
    List <AbstractNode> listNodes2 = JCRDocumentsUtil.toNodes(identityManager, extendedSession, nodeIterator, identity, spaceService,false, null);
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

  @Test
  public void isValidDocumentTitle() {
    assertFalse(JCRDocumentsUtil.isValidDocumentTitle("   "));
    assertFalse(JCRDocumentsUtil.isValidDocumentTitle("   .docx"));
    assertTrue(JCRDocumentsUtil.isValidDocumentTitle("test.docx"));
  }

  @Test
  public void hasEditPermission() throws RepositoryException {
    Session session = mock(Session.class);
    Node node = mock(Node.class);
    when(node.getPath()).thenReturn("path");
    doNothing().when(session).checkPermission("path", PermissionType.SET_PROPERTY);
    assertTrue(JCRDocumentsUtil.hasEditPermission(session, node));
    doThrow(new RuntimeException()).when(session).checkPermission("path", PermissionType.SET_PROPERTY);
    assertFalse(JCRDocumentsUtil.hasEditPermission(session, node));
  }

  @Test
  public void testGroupToIdentity() throws Exception {
    OrganizationService organizationService = mock(OrganizationService.class);
    Group group = mock(Group.class);
    when(group.getGroupName()).thenReturn("users");
    when(group.getLabel()).thenReturn("Users");
    when(group.getId()).thenReturn("/platform/users");
    COMMONS_UTILS_UTIL.when(()-> CommonsUtils.getService(OrganizationService.class)).thenReturn(organizationService);
    GroupHandler groupHandler = mock(GroupHandler.class);
    when(organizationService.getGroupHandler()).thenReturn(groupHandler);
    when(groupHandler.findGroupById("/platform/users")).thenReturn(group);
    org.exoplatform.social.core.identity.model.Identity identity = JCRDocumentsUtil.groupToIdentity(group.getId());
    assertNotNull(identity);
    assertEquals("group:users", identity.getId());
    assertEquals(group.getId(), identity.getRemoteId());
    assertEquals("group", identity.getProviderId());
  }

  @Test
  public void testCleanName(){
    //legal document name
    String fileName = "fileName.text";
    String folderName = "folderName";
    assertEquals(fileName, JCRDocumentsUtil.cleanName(fileName, NodeTypeConstants.NT_FILE));
    assertEquals(folderName, JCRDocumentsUtil.cleanName(folderName, NodeTypeConstants.NT_FOLDER));

    // illegal document name
    String illeglFileName = "illegal&file#Name.text";
    String illegalFolderName = "illegal&folder#Name";
    assertEquals("illegal_file_Name.text", JCRDocumentsUtil.cleanName(illeglFileName, NodeTypeConstants.NT_FILE));
    assertEquals("illegal_folder_Name", JCRDocumentsUtil.cleanName(illegalFolderName, NodeTypeConstants.NT_FOLDER));

    // folder name with '.' character
    String folderNameWithPointChar = "folderNameWithPoint.char";
    assertEquals("folderNameWithPoint_char", JCRDocumentsUtil.cleanName(folderNameWithPointChar, NodeTypeConstants.NT_FOLDER));

    //folder name with '.' character followed by a special character
    String folderNameWithPointfollowedBySpChar = "folder&Name.followedBy#character";
    assertEquals("folder_Name_followedBy_character", JCRDocumentsUtil.cleanName(folderNameWithPointfollowedBySpChar, NodeTypeConstants.NT_FOLDER));
  }

  @Test
  public void testToFileVersionWithSystemAuthor() throws  RepositoryException {
    Version version = mock(Version.class);
    when(version.getName()).thenReturn("1");
    when(version.getCreated()).thenReturn(Calendar.getInstance());

    Version baseVersion = mock(Version.class);
    when(baseVersion.getName()).thenReturn("baseVersionName");

    Node frozenNode = mock(Node.class);
    when(version.getNode(NodeTypeConstants.JCR_FROZEN_NODE)).thenReturn(frozenNode);

    Property property= mock(Property.class);
    when(property.getString()).thenReturn("__system");
    when(frozenNode.hasProperty(NodeTypeConstants.EXO_LAST_MODIFIER)).thenReturn(true);
    when(frozenNode.getProperty(NodeTypeConstants.EXO_LAST_MODIFIER)).thenReturn(property);


    Node node = mock(Node.class);
    when(node.hasProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(false);
    when(node.getName()).thenReturn("nodeName");
    when(node.getBaseVersion()).thenReturn(baseVersion);

    VersionHistory versionHistory = mock(VersionHistory.class);
    String[] versionLabels = {"versionLabel"};
    when(versionHistory.getVersionLabels(version)).thenReturn(versionLabels);
    when(node.getVersionHistory()).thenReturn(versionHistory);

    IdentityManager identityManager = mock(IdentityManager.class);
    when(identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "__system")).thenReturn(null);

    FileVersion fileVersion = JCRDocumentsUtil.toFileVersion(version,node,identityManager);


    assertEquals("__system",fileVersion.getAuthor());
    assertEquals("__system",fileVersion.getAuthorFullName());

  }

  @Test
  public void testCleanNameWithAccents(){
    String fileName = "filName";
    String fileNameWithAccent = "fileNameWithAccént";
    assertEquals("filName", JCRDocumentsUtil.cleanNameWithAccents(fileName, NodeTypeConstants.NT_FILE));
    assertEquals("fileNameWithAccent", JCRDocumentsUtil.cleanNameWithAccents(fileNameWithAccent, NodeTypeConstants.NT_FILE));
    //folder name with '.' character followed by a accented character
    String folderNameWithPointFollowedByAccent = "folderName.followedByAccént";
    assertEquals("folderName.followedByAccent", JCRDocumentsUtil.cleanNameWithAccents(folderNameWithPointFollowedByAccent, NodeTypeConstants.NT_FOLDER));
  }

  @Test
  public void testRetrieveTrashElementProperties() throws RepositoryException {
    NodeImpl mockNode = mock(NodeImpl.class);
    Node contentNode = mock(Node.class);
    Property mockProperty = mock(Property.class);
    NodeType nodeType = mock(NodeType.class);

    when(mockNode.hasProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(true);
    when(mockNode.getProperty(NodeTypeConstants.EXO_TITLE)).thenReturn(mockProperty);

    when(mockNode.hasProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE)).thenReturn(true);
    when(mockNode.getProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE)).thenReturn(mockProperty);
    Calendar calendar = Calendar.getInstance();
    calendar.set(2023, Calendar.JANUARY, 1);
    when(mockProperty.getDate()).thenReturn(calendar);

    when(mockNode.hasProperty(NodeTypeConstants.RESTORE_PATH)).thenReturn(true);
    when(mockNode.getProperty(NodeTypeConstants.RESTORE_PATH)).thenReturn(mockProperty);

    when(mockNode.hasNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(true);
    when(mockNode.getNode(NodeTypeConstants.JCR_CONTENT)).thenReturn(contentNode);
    when(contentNode.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(true);
    when(contentNode.getProperty(NodeTypeConstants.JCR_MIME_TYPE)).thenReturn(mockProperty);

    when(contentNode.hasProperty(NodeTypeConstants.JCR_DATA)).thenReturn(true);
    when(contentNode.getProperty(NodeTypeConstants.JCR_DATA)).thenReturn(mockProperty);
    when(mockProperty.getLength()).thenReturn(1234L);
    when(mockProperty.getString()).thenReturn("Test Title").thenReturn("/restore/path").thenReturn("text/plain");
    when(mockNode.getName()).thenReturn("Test Node");
    when(mockNode.isNodeType(NodeTypeConstants.NT_FOLDER)).thenReturn(true);
    when(mockNode.getPrimaryNodeType()).thenReturn(nodeType);
    when(nodeType.getName()).thenReturn(NodeTypeConstants.NT_FOLDER);

    // Act
    TrashElementNode result = new TrashElementNode();
    JCRDocumentsUtil.retrieveTrashElementProperties(mockNode, result);

    // Assert
    assertEquals("Test Title", result.getName());
    assertEquals(calendar.getTimeInMillis(), result.getModifiedDate());
    assertEquals("/restore/path", result.getRestorePath());
    assertTrue(result.isFolder()); // Assuming isFolder returns true
    assertEquals("text/plain", result.getMimeType());
    assertEquals(1234L, result.getSize());
  }

  @Test
  public void testIsInUserPrivateSpace() throws RepositoryException {
    Node node = mock(NodeImpl.class);

    // The user's own personal drive.
    when(node.getPath()).thenReturn("/Users/r___/ro__/root/Private/file.pdf");
    assertTrue(JCRDocumentsUtil.isInUserPrivateSpace(node, "root"));

    // The Private root itself.
    when(node.getPath()).thenReturn("/Users/r___/ro__/root/Private");
    assertTrue(JCRDocumentsUtil.isInUserPrivateSpace(node, "root"));

    // Somebody else's personal drive.
    assertFalse(JCRDocumentsUtil.isInUserPrivateSpace(node, "bob"));

    // The user's Public drive is not their Private one.
    when(node.getPath()).thenReturn("/Users/r___/ro__/root/Public/file.pdf");
    assertFalse(JCRDocumentsUtil.isInUserPrivateSpace(node, "root"));

    assertFalse(JCRDocumentsUtil.isInUserPrivateSpace(null, "root"));
    when(node.getPath()).thenReturn("/Users/r___/ro__/root/Private/file.pdf");
    assertFalse(JCRDocumentsUtil.isInUserPrivateSpace(node, ""));
  }

  @Test
  public void testIsInUserPrivateSpaceIsNotAPathSubstringMatch() throws RepositoryException {
    Node node = mock(NodeImpl.class);

    // A folder named after another user, holding a "Private" folder, inside root's drive:
    // the drive belongs to root, not to bob.
    when(node.getPath()).thenReturn("/Users/r___/ro__/root/Private/bob/Private/file.pdf");
    assertTrue(JCRDocumentsUtil.isInUserPrivateSpace(node, "root"));
    assertFalse(JCRDocumentsUtil.isInUserPrivateSpace(node, "bob"));

    // The same folder shape in a space drive is not a personal drive at all.
    when(node.getPath()).thenReturn("/Groups/spaces/myspace/Documents/bob/Private/file.pdf");
    assertFalse(JCRDocumentsUtil.isInUserPrivateSpace(node, "bob"));
  }

  /**
   * The copy counter belongs before the extension. A name ending in the counter -
   * "report.docx (1)" - is no longer recognised as a .docx and does not open,
   * which is what EXO-89931 was reported for.
   */
  @Test
  public void getNewIndexedNameKeepsTheExtensionLast() {
    assertEquals("report (1).docx", JCRDocumentsUtil.getNewIndexedName("report.docx", " (1)"));
    // The LAST dot is the extension separator, so a dotted base name is preserved.
    assertEquals("my.report (2).docx", JCRDocumentsUtil.getNewIndexedName("my.report.docx", " (2)"));
    // No extension: the counter is simply appended, with no stray dot introduced.
    assertEquals("report (1)", JCRDocumentsUtil.getNewIndexedName("report", " (1)"));
    // A hidden file's leading dot is not an extension separator: splitting there
    // would open the name with the counter, which is why duplicateItem guards on
    // "dot > 0" before calling this rather than the helper doing it itself.
    assertEquals(" (1).gitignore", JCRDocumentsUtil.getNewIndexedName(".gitignore", " (1)"));
  }


  /**
   * The walk completes, with each file of the folder copied once.
   */
  @Test(timeout = 30000)
  public void createTempFilesAndFoldersEndsEachBranch() throws Exception {
    ExtendedSession session = mock(ExtendedSession.class);
    ExtendedNode documents = folderNode("documentsId", "Documents", null);
    ExtendedNode folderA = folderNode("aId", "A", documents);
    ExtendedNode folderB = folderNode("bId", "B", folderA);
    ExtendedNode fileA = fileNode("fId", "f.txt", folderA);
    ExtendedNode fileB = fileNode("gId", "g.txt", folderB);
    ExtendedNode shortcutToA = shortcutNode("linkId", "toA", folderB, folderA, session);
    childrenOf(folderA, fileA, folderB);
    childrenOf(folderB, fileB, shortcutToA);

    Path tempFolder = Files.createTempDirectory("walk-test");
    try {
      JCRDocumentsUtil.createTempFilesAndFolders(folderA, "", "", tempFolder.toString(), documents);
      assertEquals(Set.of("A/f.txt", "A/B/g.txt"), filesUnder(tempFolder));
    } finally {
      JCRDocumentsUtil.cleanFiles(tempFolder.toFile());
    }
  }

  /**
   * The walked folder is not copied a second time inside its own copy.
   */
  @Test(timeout = 30000)
  public void createTempFilesAndFoldersDoesNotWalkTheDownloadedFolderTwice() throws Exception {
    ExtendedSession session = mock(ExtendedSession.class);
    ExtendedNode documents = folderNode("documentsId", "Documents", null);
    ExtendedNode folderA = folderNode("aId", "A", documents);
    ExtendedNode folderB = folderNode("bId", "B", folderA);
    ExtendedNode fileA = fileNode("fId", "a.txt", folderA);
    ExtendedNode fileB = fileNode("gId", "b.txt", folderB);
    ExtendedNode shortcutToA = shortcutNode("linkId", "toA", folderB, folderA, session);
    childrenOf(folderA, fileA, folderB);
    childrenOf(folderB, fileB, shortcutToA);

    Path tempFolder = Files.createTempDirectory("walk-test");
    try {
      JCRDocumentsUtil.createTempFilesAndFolders(folderB, "", "", tempFolder.toString(), folderA);
      assertEquals(Set.of("B/b.txt", "B/toA/a.txt"), filesUnder(tempFolder));
    } finally {
      JCRDocumentsUtil.cleanFiles(tempFolder.toFile());
    }
  }

  /**
   * A shortcut's target folder is copied under the shortcut, as before, also
   * when the same folder is copied elsewhere in the same download.
   */
  @Test(timeout = 30000)
  public void createTempFilesAndFoldersKeepsAShortcutToASiblingFolder() throws Exception {
    ExtendedSession session = mock(ExtendedSession.class);
    ExtendedNode documents = folderNode("documentsId", "Documents", null);
    ExtendedNode folderA = folderNode("aId", "A", documents);
    ExtendedNode folderC = folderNode("cId", "C", folderA);
    ExtendedNode folderB = folderNode("bId", "B", folderA);
    ExtendedNode fileC = fileNode("fId", "c.txt", folderC);
    ExtendedNode shortcutToC = shortcutNode("linkId", "toC", folderB, folderC, session);
    childrenOf(folderA, folderC, folderB);
    childrenOf(folderC, fileC);
    childrenOf(folderB, shortcutToC);

    Path tempFolder = Files.createTempDirectory("walk-test");
    try {
      JCRDocumentsUtil.createTempFilesAndFolders(folderA, "", "", tempFolder.toString(), documents);
      assertEquals(Set.of("A/C/c.txt", "A/B/toC/c.txt"), filesUnder(tempFolder));
    } finally {
      JCRDocumentsUtil.cleanFiles(tempFolder.toFile());
    }
  }

  private static ExtendedNode folderNode(String id, String name, ExtendedNode parent) throws RepositoryException {
    ExtendedNode node = namedNode(id, name, parent, NodeTypeConstants.NT_FOLDER);
    childrenOf(node);
    return node;
  }

  private static ExtendedNode fileNode(String id, String name, ExtendedNode parent) throws RepositoryException {
    ExtendedNode node = namedNode(id, name, parent, NodeTypeConstants.NT_FILE);
    Node content = mock(Node.class);
    Property data = mock(Property.class);
    when(node.getNode("jcr:content")).thenReturn(content);
    when(content.getProperty("jcr:data")).thenReturn(data);
    when(data.getStream()).thenAnswer(invocation -> new ByteArrayInputStream(name.getBytes(StandardCharsets.UTF_8)));
    return node;
  }

  private static ExtendedNode shortcutNode(String id,
                                           String name,
                                           ExtendedNode parent,
                                           ExtendedNode target,
                                           ExtendedSession session) throws RepositoryException {
    ExtendedNode node = namedNode(id, name, parent, NodeTypeConstants.EXO_SYMLINK);
    when(node.isNodeType(NodeTypeConstants.EXO_SYMLINK)).thenReturn(true);
    Property targetId = mock(Property.class);
    String targetIdentifier = target.getIdentifier();
    when(targetId.getString()).thenReturn(targetIdentifier);
    when(node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)).thenReturn(targetId);
    when(node.getSession()).thenReturn(session);
    when(session.getNodeByIdentifier(targetIdentifier)).thenReturn(target);
    return node;
  }

  private static ExtendedNode namedNode(String id, String name, ExtendedNode parent, String primaryType) throws RepositoryException {
    ExtendedNode node = mock(ExtendedNode.class);
    when(node.getIdentifier()).thenReturn(id);
    when(node.getName()).thenReturn(name);
    NodeType nodeType = mock(NodeType.class);
    when(nodeType.getName()).thenReturn(primaryType);
    when(node.getPrimaryNodeType()).thenReturn(nodeType);
    if (parent == null) {
      when(node.getParent()).thenThrow(new ItemNotFoundException("root"));
    } else {
      when(node.getParent()).thenReturn(parent);
    }
    return node;
  }

  private static void childrenOf(ExtendedNode folder, Node... children) throws RepositoryException {
    when(folder.getNodes()).thenAnswer(invocation -> new ChildNodeIterator(Arrays.asList(children)));
  }

  private static Set<String> filesUnder(Path folder) throws IOException {
    try (Stream<Path> paths = Files.walk(folder)) {
      return paths.filter(Files::isRegularFile)
                  .map(path -> folder.relativize(path).toString().replace(File.separatorChar, '/'))
                  .collect(Collectors.toSet());
    }
  }


  private static class ChildNodeIterator implements NodeIterator {

    private final List<Node>     children;

    private final Iterator<Node> iterator;

    private int                  position;

    ChildNodeIterator(List<Node> children) {
      this.children = children;
      this.iterator = children.iterator();
    }

    @Override
    public Node nextNode() {
      position++;
      return iterator.next();
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public Object next() {
      return nextNode();
    }

    @Override
    public void skip(long skipNum) {
      for (long i = 0; i < skipNum; i++) {
        nextNode();
      }
    }

    @Override
    public long getSize() {
      return children.size();
    }

    @Override
    public long getPosition() {
      return position;
    }
  }
}
