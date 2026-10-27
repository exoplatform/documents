/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.mcp.model.BreadcrumbItemModel;
import org.exoplatform.documents.mcp.model.DocumentFileModel;
import org.exoplatform.documents.mcp.model.DocumentFolderModel;
import org.exoplatform.documents.mcp.model.DocumentModel;
import org.exoplatform.documents.mcp.model.DocumentPublicLinkModel;
import org.exoplatform.documents.mcp.model.DocumentTreeItemModel;
import org.exoplatform.documents.mcp.model.DocumentVersionModel;
import org.exoplatform.documents.mcp.model.DocumentsSizeModel;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.BreadCrumbItem;
import org.exoplatform.documents.model.DocumentTextContent;
import org.exoplatform.documents.model.DocumentsSize;
import org.exoplatform.documents.model.FileNode;
import org.exoplatform.documents.model.TranscriptionState;
import org.exoplatform.documents.constant.TranscriptionStatus;
import org.exoplatform.documents.model.FileVersion;
import org.exoplatform.documents.model.FolderNode;
import org.exoplatform.documents.model.FullTreeItem;
import org.exoplatform.documents.model.NodePermission;
import org.exoplatform.documents.model.PermissionEntry;
import org.exoplatform.documents.model.PermissionRole;
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.documents.service.PublicDocumentAccessService;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.attachments.service.AttachmentService;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.favorite.FavoriteService;
import org.exoplatform.social.metadata.favorite.model.Favorite;
import org.exoplatform.upload.UploadService;

import io.meeds.mcp.server.constant.Origin;
import io.meeds.mcp.server.model.ActingIdentity;
import io.meeds.mcp.server.tool.util.UploadToolUtils;
import io.meeds.mcp.server.util.McpToolUtils;
import io.meeds.social.translation.service.TranslationService;
import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.doAnswer;
import java.util.HashMap;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.exoplatform.documents.constant.DocumentUploadLimit;
import org.exoplatform.documents.constant.UploadConflictAction;
import org.exoplatform.documents.mcp.model.DocumentUploadModel;

public class DocumentMcpToolTest {

  private static final String            USERNAME     = "testuser1";

  private static final long              USER_IDENTITY_ID = 100L;

  private static final long              SPACE_ID     = 3L;

  private static final String            DOCUMENT_ID  = "be0688cd7f00010134384ab7e1b15a48";

  private static final String            FOLDER_ID    = "folder-42";

  private DocumentFileService            documentFileService;

  private AttachmentService              attachmentService;

  private org.exoplatform.social.attachment.AttachmentService socialAttachmentService;

  private org.exoplatform.commons.file.services.FileService    fileService;

  private IdentityManager                identityManager;

  private SpaceService                   spaceService;

  private PublicDocumentAccessService    publicDocumentAccessService;

  private FavoriteService                favoriteService;

  private Identity                       currentIdentity;

  private DocumentMcpTool                documentMcpTool;

  private Long                           scopeSpaceId;

  private boolean                        scopeUnreadable;

  @Before
  public void setUp() throws Exception {
    documentFileService = Mockito.mock(DocumentFileService.class);
    attachmentService = Mockito.mock(AttachmentService.class);
    socialAttachmentService = Mockito.mock(org.exoplatform.social.attachment.AttachmentService.class);
    fileService = Mockito.mock(org.exoplatform.commons.file.services.FileService.class);
    identityManager = Mockito.mock(IdentityManager.class);
    spaceService = Mockito.mock(SpaceService.class);
    TranslationService translationService = Mockito.mock(TranslationService.class);
    ProfilePropertyService profilePropertyService = Mockito.mock(ProfilePropertyService.class);
    UserACL userAcl = Mockito.mock(UserACL.class);
    UserPortalConfigService portalConfigService = Mockito.mock(UserPortalConfigService.class);
    uploadService = Mockito.mock(UploadService.class);
    publicDocumentAccessService = Mockito.mock(PublicDocumentAccessService.class);
    favoriteService = Mockito.mock(FavoriteService.class);
    currentIdentity = new Identity(USERNAME);

    org.exoplatform.social.core.identity.model.Identity socialIdentity =
                                                                       new org.exoplatform.social.core.identity.model.Identity(String.valueOf(USER_IDENTITY_ID));
    lenient().when(identityManager.getOrCreateUserIdentity(USERNAME)).thenReturn(socialIdentity);
    // A folder a test writes into is writable unless the test says otherwise
    lenient().when(documentFileService.hasEditPermissionOnDocument(eq(FOLDER_ID), anyLong())).thenReturn(true);

    documentMcpTool = new DocumentMcpTool(documentFileService,
                                          attachmentService,
                                          socialAttachmentService,
                                          fileService,
                                          identityManager,
                                          spaceService,
                                          translationService,
                                          profilePropertyService,
                                          userAcl,
                                          portalConfigService,
                                          uploadService,
                                          publicDocumentAccessService,
                                          favoriteService) {
      @Override
      public Identity getCurrentUserAclIdentity() {
        return currentIdentity;
      }

      @Override
      public Locale getCurrentUserLocale() {
        return Locale.ENGLISH;
      }

      @Override
      protected Long getScopeSpaceId() {
        if (scopeUnreadable) {
          throw new IllegalStateException("No acting identity");
        }
        return scopeSpaceId;
      }
    };
  }

  /**
   * In a call limited to a space, a move, a copy or a shortcut goes only into
   * a folder of that space: a folder of another space, a folder in no space's
   * drive, and any folder when the call's space can't be read are refused
   * before the storage is touched. A call that isn't limited doesn't look the
   * destination's space up.
   * Mutants: the check removed from one of the three tools, the comparison
   * inverted, a destination in no space accepted, an unreadable space taken
   * as unlimited, or an unlimited call checked.
   *
   * @throws Exception never
   */
  @Test
  public void aCallLimitedToASpaceMovesCopiesAndLinksIntoThatSpaceOnly() throws Exception {
    String spaceFolder = "ae0688cd7f00010134384ab7e1b15a49";
    String otherSpaceFolder = "00000000000000000000000000000003";
    String driveLessFolder = "00000000000000000000000000000004";
    Space space = new Space();
    space.setId("12");
    Space otherSpace = new Space();
    otherSpace.setId("13");
    when(documentFileService.getDocumentSpaceGroupId(spaceFolder)).thenReturn("/spaces/legal");
    when(documentFileService.getDocumentSpaceGroupId(otherSpaceFolder)).thenReturn("/spaces/hr");
    when(documentFileService.getDocumentSpaceGroupId(driveLessFolder)).thenReturn(null);
    when(spaceService.getSpaceByGroupId("/spaces/legal")).thenReturn(space);
    when(spaceService.getSpaceByGroupId("/spaces/hr")).thenReturn(otherSpace);
    lenient().when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    lenient().when(documentFileService.getDocumentById(spaceFolder, USERNAME)).thenReturn(folderNode(spaceFolder));
    lenient().when(documentFileService.getDocumentById(otherSpaceFolder, USERNAME)).thenReturn(folderNode(otherSpaceFolder));
    lenient().when(documentFileService.getDocumentById(driveLessFolder, USERNAME)).thenReturn(folderNode(driveLessFolder));
    lenient().when(documentFileService.getRootFolderOwnerId(DOCUMENT_ID)).thenReturn(OWNER_ID);

    scopeSpaceId = 12L;
    for (String outside : List.of(otherSpaceFolder, driveLessFolder)) {
      assertThrows(IllegalAccessException.class, () -> documentMcpTool.moveDocument(DOCUMENT_ID, outside, null));
      assertThrows(IllegalAccessException.class, () -> documentMcpTool.copyDocument(DOCUMENT_ID, outside));
      assertThrows(IllegalAccessException.class, () -> documentMcpTool.createDocumentShortcut(DOCUMENT_ID, outside, null));
    }
    scopeUnreadable = true;
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.moveDocument(DOCUMENT_ID, spaceFolder, null));
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.copyDocument(DOCUMENT_ID, spaceFolder));
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.createDocumentShortcut(DOCUMENT_ID, spaceFolder, null));
    verify(documentFileService, never()).moveDocument(anyLong(), any(), any(), anyLong(), any());
    verify(documentFileService, never()).copyDocument(any(), any(), anyLong());
    verify(documentFileService, never()).createShortcut(any(), any(), any(), any());

    scopeUnreadable = false;
    documentMcpTool.moveDocument(DOCUMENT_ID, spaceFolder, null);
    documentMcpTool.createDocumentShortcut(DOCUMENT_ID, spaceFolder, null);
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(Collections.emptyList())
        .thenReturn(List.of(fileNodeNamed("copy-1", "report.pdf")));
    assertEquals("copy-1", documentMcpTool.copyDocument(DOCUMENT_ID, spaceFolder).getId());
    verify(documentFileService).moveDocument(eq(OWNER_ID), eq(DOCUMENT_ID), any(), eq(USER_IDENTITY_ID), eq("keepBoth"));
    verify(documentFileService).createShortcut(eq(DOCUMENT_ID), any(), eq(USERNAME), eq("keepBoth"));
    verify(documentFileService).copyDocument(DOCUMENT_ID, spaceFolder, USER_IDENTITY_ID);

    scopeSpaceId = null;
    Mockito.clearInvocations(documentFileService);
    documentMcpTool.moveDocument(DOCUMENT_ID, spaceFolder, null);
    verify(documentFileService, never()).getDocumentSpaceGroupId(any());
  }

  /**
   * In a call limited to a space, a node the platform created for the space
   * (creator {@code __system}) is neither moved, copied, linked nor hidden, and
   * a file is neither hidden nor shown: only a folder is. A folder created by
   * a user is hidden. A call that isn't limited keeps the storage's own rules,
   * with no lookup of the node; one whose space can't be read is refused.
   * Mutants: the creator check removed or applied to one tool only, the
   * folder-only rule removed or applied to a move, an unlimited call checked,
   * or an unreadable space taken as unlimited.
   *
   * @throws Exception never
   */
  @Test
  public void aCallLimitedToASpaceLeavesThePlatformsNodesAndHidesFoldersOnly() throws Exception {
    String spaceFolder = "ae0688cd7f00010134384ab7e1b15a49";
    String systemFolderId = "ce0688cd7f00010134384ab7e1b15a50";
    String userFolderId = "de0688cd7f00010134384ab7e1b15a51";
    Space space = new Space();
    space.setId("12");
    FolderNode systemFolder = folderNode(systemFolderId);
    systemFolder.setCreatorUserName("__system");
    FolderNode userFolder = folderNode(userFolderId);
    userFolder.setCreatorUserName(USERNAME);
    FileNode userFile = fileNode(DOCUMENT_ID, "application/pdf");
    userFile.setCreatorUserName(USERNAME);
    lenient().when(documentFileService.getDocumentSpaceGroupId(spaceFolder)).thenReturn("/spaces/legal");
    lenient().when(spaceService.getSpaceByGroupId("/spaces/legal")).thenReturn(space);
    lenient().when(documentFileService.getDocumentById(spaceFolder, USERNAME)).thenReturn(folderNode(spaceFolder));
    when(documentFileService.getDocumentById(systemFolderId, USERNAME)).thenReturn(systemFolder);
    when(documentFileService.getDocumentById(userFolderId, USERNAME)).thenReturn(userFolder);
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(userFile);
    lenient().when(documentFileService.getRootFolderOwnerId(any())).thenReturn(OWNER_ID);

    scopeSpaceId = 12L;
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.moveDocument(systemFolderId, spaceFolder, null));
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.copyDocument(systemFolderId, spaceFolder));
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.createDocumentShortcut(systemFolderId, spaceFolder, null));
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.setDocumentVisibility(systemFolderId, true));
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.setDocumentVisibility(DOCUMENT_ID, true));
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.setDocumentVisibility(DOCUMENT_ID, false));
    verify(documentFileService, never()).moveDocument(anyLong(), any(), any(), anyLong(), any());
    verify(documentFileService, never()).copyDocument(any(), any(), anyLong());
    verify(documentFileService, never()).createShortcut(any(), any(), any(), any());
    verify(documentFileService, never()).setDocumentVisibility(anyLong(), any(), anyBoolean(), anyLong());

    documentMcpTool.setDocumentVisibility(userFolderId, true);
    documentMcpTool.moveDocument(DOCUMENT_ID, spaceFolder, null);
    verify(documentFileService).setDocumentVisibility(OWNER_ID, userFolderId, true, USER_IDENTITY_ID);
    verify(documentFileService).moveDocument(eq(OWNER_ID), eq(DOCUMENT_ID), any(), eq(USER_IDENTITY_ID), eq("keepBoth"));

    scopeSpaceId = null;
    scopeUnreadable = true;
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.setDocumentVisibility(userFolderId, false));

    scopeUnreadable = false;
    Mockito.clearInvocations(documentFileService);
    documentMcpTool.setDocumentVisibility(DOCUMENT_ID, true);
    documentMcpTool.moveDocument(systemFolderId, spaceFolder, null);
    verify(documentFileService).setDocumentVisibility(OWNER_ID, DOCUMENT_ID, true, USER_IDENTITY_ID);
    verify(documentFileService, never()).getDocumentById(eq(DOCUMENT_ID), any());
    verify(documentFileService, never()).getDocumentById(eq(systemFolderId), any());
  }

  /**
   * In a call limited to a space, a node the platform created for the space
   * (creator {@code __system}) is not duplicated, and a node a user created
   * is; a call that isn't limited duplicates either.
   * Mutants: the creator check removed from the duplicate, or run on an
   * unlimited call.
   *
   * @throws Exception never
   */
  @Test
  public void aCallLimitedToASpaceDuplicatesNoNodeOfThePlatform() throws Exception {
    FileNode systemFile = fileNode(DOCUMENT_ID, "application/pdf");
    systemFile.setCreatorUserName("__system");
    systemFile.setParentFolderId(FOLDER_ID);
    FileNode userFile = fileNode("fe0688cd7f00010134384ab7e1b15a52", "application/pdf");
    userFile.setCreatorUserName(USERNAME);
    userFile.setParentFolderId(FOLDER_ID);
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(systemFile);
    when(documentFileService.getDocumentById(userFile.getId(), USERNAME)).thenReturn(userFile);
    lenient().when(documentFileService.getRootFolderOwnerId(any())).thenReturn(OWNER_ID);
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(List.of())
        .thenReturn(List.of(fileNodeNamed("dup-1", "report.pdf")))
        .thenReturn(List.of())
        .thenReturn(List.of(fileNodeNamed("dup-2", "report.pdf")));

    scopeSpaceId = 12L;
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.duplicateDocument(DOCUMENT_ID, null));
    verify(documentFileService, never()).duplicateDocument(anyLong(), any(), any(), anyLong());
    assertEquals("dup-1", documentMcpTool.duplicateDocument(userFile.getId(), null).getId());

    scopeSpaceId = null;
    assertEquals("dup-2", documentMcpTool.duplicateDocument(DOCUMENT_ID, null).getId());
    verify(documentFileService).duplicateDocument(OWNER_ID, DOCUMENT_ID, null, USER_IDENTITY_ID);
  }

  /**
   * In a call limited to a space, a node the platform created for the space
   * (creator {@code __system}) is not renamed, and a folder or a file a user
   * created is; a call that isn't limited renames either.
   * Mutants: the creator check removed from the rename, the rename checked as
   * a folder-only change, or every folder refused whatever its creator.
   *
   * @throws Exception never
   */
  @Test
  public void aCallLimitedToASpaceRenamesNoNodeOfThePlatform() throws Exception {
    FolderNode systemFolder = folderNode(FOLDER_ID);
    systemFolder.setCreatorUserName("__system");
    FileNode userFile = fileNode(DOCUMENT_ID, "application/pdf");
    userFile.setCreatorUserName(USERNAME);
    String userFolderId = "user-folder";
    FolderNode userFolder = folderNode(userFolderId);
    userFolder.setCreatorUserName(USERNAME);
    when(documentFileService.getDocumentById(userFolderId, USERNAME)).thenReturn(userFolder);
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(systemFolder);
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(userFile);
    lenient().when(documentFileService.getRootFolderOwnerId(any())).thenReturn(OWNER_ID);

    scopeSpaceId = 12L;
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.renameDocument(FOLDER_ID, "Minutes"));
    verify(documentFileService, never()).renameDocument(anyLong(), any(), any(), anyLong());
    documentMcpTool.renameDocument(DOCUMENT_ID, "contract.pdf");
    documentMcpTool.renameDocument(userFolderId, "Agreements");
    verify(documentFileService).renameDocument(OWNER_ID, DOCUMENT_ID, "contract.pdf", USER_IDENTITY_ID);
    verify(documentFileService).renameDocument(OWNER_ID, userFolderId, "Agreements", USER_IDENTITY_ID);

    scopeSpaceId = null;
    documentMcpTool.renameDocument(FOLDER_ID, "Minutes");
    verify(documentFileService).renameDocument(OWNER_ID, FOLDER_ID, "Minutes", USER_IDENTITY_ID);
  }

  /**
   * The space a call is limited to is the one its acting identity carries,
   * none for an identity without a scope or no identity, and an identity that
   * can't be read fails the read rather than passing for an unlimited call.
   * Runs the real {@code getScopeSpaceId}, which the other tests stub.
   * Mutants: the scope not read, or the read failure swallowed.
   */
  @Test
  public void theScopeSpaceIsTheOneTheActingIdentityCarries() {
    DocumentMcpTool realTool = new DocumentMcpTool(documentFileService,
                                                   attachmentService,
                                                   socialAttachmentService,
                                                   fileService,
                                                   identityManager,
                                                   spaceService,
                                                   Mockito.mock(TranslationService.class),
                                                   Mockito.mock(ProfilePropertyService.class),
                                                   Mockito.mock(UserACL.class),
                                                   Mockito.mock(UserPortalConfigService.class),
                                                   Mockito.mock(UploadService.class),
                                                   publicDocumentAccessService,
                                                   favoriteService);
    ActingIdentity limited = ActingIdentity.agent("agent-docs", "DOCS", Origin.PROPOSAL, 7L);
    ActingIdentity unlimited = ActingIdentity.person(USERNAME, Origin.CHAT);
    try (MockedStatic<McpToolUtils> mocked = Mockito.mockStatic(McpToolUtils.class)) {
      mocked.when(McpToolUtils::getCurrentActingIdentity).thenReturn(limited);
      assertEquals(Long.valueOf(7L), realTool.getScopeSpaceId());
      mocked.when(McpToolUtils::getCurrentActingIdentity).thenReturn(unlimited);
      assertEquals(null, realTool.getScopeSpaceId());
      mocked.when(McpToolUtils::getCurrentActingIdentity).thenReturn(null);
      assertEquals(null, realTool.getScopeSpaceId());
      mocked.when(McpToolUtils::getCurrentActingIdentity).thenThrow(new IllegalStateException("refused"));
      assertThrows(IllegalStateException.class, realTool::getScopeSpaceId);
    }
  }

  private FileNode fileNode(String id, String mimeType) {
    FileNode fileNode = new FileNode();
    fileNode.setId(id);
    fileNode.setName("report.pdf");
    fileNode.setPath("/documents/report.pdf");
    fileNode.setMimeType(mimeType);
    fileNode.setSize(2048L);
    return fileNode;
  }

  private FolderNode folderNode(String id) {
    FolderNode folderNode = new FolderNode();
    folderNode.setId(id);
    folderNode.setName("Projects");
    folderNode.setPath("/documents/Projects");
    return folderNode;
  }

  @Test
  public void getRootFolderBySpace() throws Exception {
    when(documentFileService.getSpaceRootFolder(eq(SPACE_ID), eq(currentIdentity))).thenReturn(folderNode(FOLDER_ID));

    DocumentFolderModel model = documentMcpTool.getRootFolderBySpace(SPACE_ID, null);

    assertNotNull(model);
    assertEquals(FOLDER_ID, model.getId());
    assertEquals("Projects", model.getName());
    assertNotNull(model.getUrl());
  }

  @Test
  public void getRootFolderForUser() {
    when(documentFileService.getPersonalRootFolder(eq(currentIdentity))).thenReturn(folderNode(FOLDER_ID));

    DocumentFolderModel model = documentMcpTool.getRootFolderForUser();

    assertNotNull(model);
    assertEquals(FOLDER_ID, model.getId());
  }

  @Test
  public void getDocumentById() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));

    DocumentModel model = documentMcpTool.getDocumentById(DOCUMENT_ID);

    assertNotNull(model);
    assertEquals(DOCUMENT_ID, model.getId());
  }

  @Test
  public void getDocumentByIdWithBlankIdFails() {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.getDocumentById("  "));
  }

  @Test
  public void getDocumentContentById() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.getFileTextContent(DOCUMENT_ID)).thenReturn(DocumentTextContent.of("Hello world",
                                                                                              DocumentTextContent.Status.INDEXED));

    assertEquals("Hello world", documentMcpTool.getDocumentContentById(DOCUMENT_ID));
  }

  @Test
  public void getDocumentContentByIdReturnsTheTextExtractedFromTheFile() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.getFileTextContent(DOCUMENT_ID)).thenReturn(DocumentTextContent.of("Freshly saved",
                                                                                              DocumentTextContent.Status.EXTRACTED));

    assertEquals("Freshly saved", documentMcpTool.getDocumentContentById(DOCUMENT_ID));
  }

  @Test
  public void getDocumentContentByIdChecksAccessBeforeReadingAnyText() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenThrow(new IllegalAccessException("denied"));

    assertThrows(IllegalAccessException.class, () -> documentMcpTool.getDocumentContentById(DOCUMENT_ID));
    verify(documentFileService, never()).getFileTextContent(anyString());
  }

  @Test
  public void getDocumentContentByIdStatesWhyThereIsNoText() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    assertNoTextMessage(DocumentTextContent.Status.UNSUPPORTED_FORMAT, "its format is not supported for text extraction.");
    assertNoTextMessage(DocumentTextContent.Status.NO_TEXT, "no text was found in the file");
    assertNoTextMessage(DocumentTextContent.Status.UNREADABLE, "the file could not be read.");
    assertNoTextMessage(DocumentTextContent.Status.NOT_A_FILE, "is not a file with a content, so it has no text.");
    assertNoTextMessage(DocumentTextContent.Status.BUSY, "too many documents are being read at the moment");
    assertNoTextMessage(DocumentTextContent.Status.TIMED_OUT, "reading the file took too long.");
  }

  @Test
  public void getDocumentContentByIdNeverClaimsTheFileIsBeingIndexed() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    for (DocumentTextContent.Status status : DocumentTextContent.Status.values()) {
      if (status == DocumentTextContent.Status.INDEXED || status == DocumentTextContent.Status.EXTRACTED) {
        continue;
      }
      when(documentFileService.getFileTextContent(DOCUMENT_ID)).thenReturn(DocumentTextContent.none(status));
      IllegalStateException e = assertThrows(IllegalStateException.class,
                                             () -> documentMcpTool.getDocumentContentById(DOCUMENT_ID));
      assertFalse(e.getMessage().toLowerCase().contains("index"));
      assertFalse(e.getMessage().toLowerCase().contains("try again"));
    }
  }

  @Test
  public void getDocumentContentByIdWithBlankTextRaisesRatherThanReturningNothing() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.getFileTextContent(DOCUMENT_ID)).thenReturn(null);

    IllegalStateException e = assertThrows(IllegalStateException.class,
                                           () -> documentMcpTool.getDocumentContentById(DOCUMENT_ID));
    assertTrue(e.getMessage().endsWith("the file could not be read."));
  }

  /**
   * Asserts the tool raises, for a document without text for the given reason,
   * an exception whose message names that reason.
   *
   * @param status why the document has no text
   * @param expected the part of the message stating that reason
   * @throws Exception when the call fails otherwise
   */
  private void assertNoTextMessage(DocumentTextContent.Status status, String expected) throws Exception {
    when(documentFileService.getFileTextContent(DOCUMENT_ID)).thenReturn(DocumentTextContent.none(status));
    IllegalStateException e = assertThrows(IllegalStateException.class,
                                           () -> documentMcpTool.getDocumentContentById(DOCUMENT_ID));
    assertTrue("'" + e.getMessage() + "' should state: " + expected, e.getMessage().contains(expected));
    assertTrue(e.getMessage().contains(DOCUMENT_ID));
  }

  @Test
  public void getDocumentTranscriptionById() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "audio/mp3"));
    when(documentFileService.getAudioTranscription(eq(DOCUMENT_ID), anyLong())).thenReturn("the transcription");

    assertEquals("the transcription", documentMcpTool.getDocumentTranscriptionById(DOCUMENT_ID));
  }

  @Test
  public void getDocumentTranscriptionByIdWhenPendingFails() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "video/mp4"));
    when(documentFileService.getAudioTranscription(eq(DOCUMENT_ID), anyLong())).thenReturn(null);

    assertThrows(IllegalStateException.class, () -> documentMcpTool.getDocumentTranscriptionById(DOCUMENT_ID));
  }

  @Test
  public void getDocumentTranscriptionByIdTellsAQueuedOrRunningTranscription() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "video/mp4"));
    when(documentFileService.getTranscriptionState(eq(DOCUMENT_ID), anyLong())).thenReturn(TranscriptionState.builder()
                                                                                                   .status(TranscriptionStatus.QUEUED)
                                                                                                   .updated(System.currentTimeMillis())
                                                                                                   .build(),
                                                                                         TranscriptionState.builder()
                                                                                                   .status(TranscriptionStatus.PROCESSING)
                                                                                                   .progress("3/9")
                                                                                                   .updated(System.currentTimeMillis())
                                                                                                   .build());

    assertTrue(assertThrows(IllegalStateException.class,
                            () -> documentMcpTool.getDocumentTranscriptionById(DOCUMENT_ID)).getMessage().contains("queued"));
    String running = assertThrows(IllegalStateException.class, () -> documentMcpTool.getDocumentTranscriptionById(DOCUMENT_ID)).getMessage();
    assertTrue(running, running.contains("in progress (3/9 pieces transcribed)"));
  }

  @Test
  public void getDocumentTranscriptionByIdTellsARunLostLongAgoAsStopped() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "video/mp4"));
    when(documentFileService.getTranscriptionState(eq(DOCUMENT_ID), anyLong())).thenReturn(TranscriptionState.builder()
                                                                                                   .status(TranscriptionStatus.PROCESSING)
                                                                                                   .progress("3/9")
                                                                                                   .updated(System.currentTimeMillis()
                                                                                                       - TranscriptionState.STALE_MILLIS
                                                                                                       - 1000)
                                                                                                   .build());

    String stopped = assertThrows(IllegalStateException.class, () -> documentMcpTool.getDocumentTranscriptionById(DOCUMENT_ID)).getMessage();

    assertTrue(stopped, stopped.contains("stopped without finishing"));
    assertFalse("Expected a lost run never told as in progress", stopped.contains("in progress"));
  }

  @Test
  public void getDocumentTranscriptionByIdTellsAFileQueuedForLongAsStillQueued() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "video/mp4"));
    when(documentFileService.getTranscriptionState(eq(DOCUMENT_ID), anyLong())).thenReturn(TranscriptionState.builder()
                                                                                                   .status(TranscriptionStatus.QUEUED)
                                                                                                   .updated(System.currentTimeMillis()
                                                                                                       - TranscriptionState.STALE_MILLIS
                                                                                                       - 1000)
                                                                                                   .build());

    String queued = assertThrows(IllegalStateException.class, () -> documentMcpTool.getDocumentTranscriptionById(DOCUMENT_ID)).getMessage();

    assertTrue("Expected a file waiting behind other runs told queued, not stopped: " + queued, queued.contains("queued"));
  }

  @Test
  public void getDocumentTranscriptionByIdTellsAFailedAttemptAsScheduledAgain() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "video/mp4"));
    when(documentFileService.getTranscriptionState(eq(DOCUMENT_ID), anyLong())).thenReturn(TranscriptionState.builder()
                                                                                                   .status(TranscriptionStatus.QUEUED)
                                                                                                   .error("provider.timeout")
                                                                                                   .updated(System.currentTimeMillis())
                                                                                                   .build());

    String retried = assertThrows(IllegalStateException.class, () -> documentMcpTool.getDocumentTranscriptionById(DOCUMENT_ID)).getMessage();

    assertTrue(retried, retried.contains("failed (the transcription service did not answer in time), and a new attempt is scheduled"));
    assertFalse("Expected a pending retry never told as failed for good", retried.contains("Tell the user it failed"));
  }

  @Test
  public void getDocumentTranscriptionByIdTellsAFailedTranscriptionAndWhy() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "video/mp4"));
    when(documentFileService.getTranscriptionState(eq(DOCUMENT_ID), anyLong())).thenReturn(TranscriptionState.builder()
                                                                                                   .status(TranscriptionStatus.FAILED)
                                                                                                   .error("ffmpeg.unavailable")
                                                                                                   .build());

    String failed = assertThrows(IllegalStateException.class, () -> documentMcpTool.getDocumentTranscriptionById(DOCUMENT_ID)).getMessage();

    assertTrue(failed, failed.contains("failed: the server has no tool to convert this recording, which its format or its size needs"));
    assertFalse("Expected a failed transcription never told as not ready yet", failed.contains("try again later"));
  }

  @Test
  public void getDocumentTranscriptionByIdTellsADisabledTranscriptionAsSwitchedOffOrUnavailable() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "audio/ogg"));
    when(documentFileService.getTranscriptionState(eq(DOCUMENT_ID), anyLong())).thenReturn(TranscriptionState.builder()
                                                                                                   .status(TranscriptionStatus.FAILED)
                                                                                                   .error("transcription.disabled")
                                                                                                   .build());

    String failed = assertThrows(IllegalStateException.class, () -> documentMcpTool.getDocumentTranscriptionById(DOCUMENT_ID)).getMessage();

    assertTrue(failed, failed.contains("transcription is switched off, or its transcription assistant is not available"));
  }

  @Test
  public void getDocumentTranscriptionByIdTellsATranscriptWithNoSpeech() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "audio/ogg"));
    when(documentFileService.getTranscriptionState(eq(DOCUMENT_ID), anyLong())).thenReturn(TranscriptionState.builder()
                                                                                                   .status(TranscriptionStatus.DONE)
                                                                                                   .build());

    assertTrue(assertThrows(IllegalStateException.class,
                            () -> documentMcpTool.getDocumentTranscriptionById(DOCUMENT_ID)).getMessage().contains("no speech"));
  }

  @Test
  public void getDocumentsByFolderId() throws Exception {
    List<AbstractNode> items = List.of(fileNode(DOCUMENT_ID, "application/pdf"), folderNode(FOLDER_ID));
    Mockito.<List<? extends AbstractNode>>when(documentFileService.getDocumentItems(any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean()))
           .thenReturn(items);

    List<DocumentModel> models = documentMcpTool.getDocumentsByFolderId(FOLDER_ID, null, null, null, null);

    assertNotNull(models);
    assertEquals(2, models.size());
  }

  /**
   * A negative limit lists nothing and reads nothing, as list_folder_children
   * does: given to the storage, it would read every child of the folder.
   * Mutant: the clamp dropped.
   *
   * @throws Exception never
   */
  @Test
  public void getDocumentsByFolderIdWithANegativeLimitListsNothing() throws Exception {
    assertEquals(List.of(), documentMcpTool.getDocumentsByFolderId(FOLDER_ID, null, null, null, -1));

    verify(documentFileService, never()).getDocumentItems(any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean());
  }

  @Test
  public void getDocumentsByFolderIdFilesOnlyExcludesFolders() throws Exception {
    List<AbstractNode> items = List.of(fileNode(DOCUMENT_ID, "application/pdf"), folderNode(FOLDER_ID));
    Mockito.<List<? extends AbstractNode>>when(documentFileService.getDocumentItems(any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean()))
           .thenReturn(items);

    List<DocumentModel> models = documentMcpTool.getDocumentsByFolderId(FOLDER_ID, Boolean.TRUE, null, null, null);

    // files_only=true must keep only the file, dropping the folder.
    assertEquals(1, models.size());
    assertEquals(DOCUMENT_ID, models.get(0).getId());
  }

  @Test
  public void getDocumentsByFolderIdFoldersOnlyExcludesFiles() throws Exception {
    List<AbstractNode> items = List.of(fileNode(DOCUMENT_ID, "application/pdf"), folderNode(FOLDER_ID));
    Mockito.<List<? extends AbstractNode>>when(documentFileService.getDocumentItems(any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean()))
           .thenReturn(items);

    List<DocumentModel> models = documentMcpTool.getDocumentsByFolderId(FOLDER_ID, null, Boolean.TRUE, null, null);

    // folders_only=true must keep only the folder, dropping the file.
    assertEquals(1, models.size());
    assertEquals(FOLDER_ID, models.get(0).getId());
  }

  @Test
  public void searchDocuments() throws Exception {
    when(documentFileService.search(any(org.exoplatform.documents.model.DocumentTimelineFilter.class),
                                    eq(currentIdentity),
                                    anyInt(),
                                    anyInt())).thenReturn(List.of(fileNode(DOCUMENT_ID, "application/pdf")));

    List<DocumentFileModel> models = documentMcpTool.searchDocuments("report", null, null, null, null, null, null, null);

    assertNotNull(models);
    assertEquals(1, models.size());
    assertEquals(DOCUMENT_ID, models.get(0).getId());
  }

  @Test
  public void searchDocumentsWithUnknownSpaceFails() {
    when(spaceService.getSpaceById(String.valueOf(SPACE_ID))).thenReturn(null);

    assertThrows(ObjectNotFoundException.class,
                 () -> documentMcpTool.searchDocuments("report", SPACE_ID, null, null, null, null, null, null));
  }

  @Test
  public void searchDocumentsInSpaceNotViewableFails() {
    Space space = new Space();
    space.setId(String.valueOf(SPACE_ID));
    space.setPrettyName("engineering");
    when(spaceService.getSpaceById(String.valueOf(SPACE_ID))).thenReturn(space);
    when(spaceService.canViewSpace(eq(space), eq(USERNAME))).thenReturn(false);

    assertThrows(IllegalAccessException.class,
                 () -> documentMcpTool.searchDocuments("report", SPACE_ID, null, null, null, null, null, null));
  }

  @Test
  public void updateDocumentDescription() throws Exception {
    when(documentFileService.getRootFolderOwnerId(DOCUMENT_ID)).thenReturn(55L);

    documentMcpTool.updateDocumentDescription(DOCUMENT_ID, "<p>new</p>");

    verify(documentFileService).updateDocumentDescription(eq(55L), eq(DOCUMENT_ID), eq("<p>new</p>"), eq(USER_IDENTITY_ID));
  }

  @Test
  public void attachDocumentToContent() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));

    documentMcpTool.attachDocumentToContent(DOCUMENT_ID, "activity", 77L);

    verify(attachmentService).linkAttachmentToEntity(eq(USER_IDENTITY_ID), eq(77L), eq("activity"), eq(DOCUMENT_ID));
  }

  @Test
  public void attachDocumentToContentBlankIdFails() {
    // The nit: attach must apply the same id validation every sibling method does,
    // so a blank id fails cleanly before reaching the attachment service.
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.attachDocumentToContent("  ", "activity", 77L));
  }

  // ---------------------------------------------------------------------------
  // New folder-navigation and content-management tools
  // ---------------------------------------------------------------------------

  private static final long OWNER_ID = 55L;

  private FileVersion fileVersion() {
    FileVersion version = new FileVersion();
    version.setId("version-1");
    version.setVersionNumber(2);
    version.setTitle("report.pdf");
    version.setSummary("initial upload");
    version.setAuthor(USERNAME);
    version.setAuthorFullName("Test User");
    version.setCreatedDate(new Date());
    version.setCurrent(true);
    version.setSize(2048L);
    return version;
  }

  @Test
  public void listFolderChildren() throws Exception {
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong())).thenReturn(List.of(fileNode(DOCUMENT_ID,
                                                                                                                    "application/pdf"),
                                                                                                           folderNode(FOLDER_ID)));

    List<DocumentModel> models = documentMcpTool.listFolderChildren(FOLDER_ID, null, null, null);

    assertNotNull(models);
    assertEquals(2, models.size());
  }

  @Test
  public void listFolderChildrenBlankFolderFails() {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.listFolderChildren(" ", null, null, null));
  }

  @Test
  public void getFolderBreadcrumb() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.getBreadcrumb(eq(OWNER_ID), eq(FOLDER_ID), isNull(), eq(USER_IDENTITY_ID)))
        .thenReturn(List.of(new BreadCrumbItem(FOLDER_ID, "Projects", "Projects", "/documents/Projects", false, null)));

    List<BreadcrumbItemModel> models = documentMcpTool.getFolderBreadcrumb(FOLDER_ID);

    assertEquals(1, models.size());
    assertEquals(FOLDER_ID, models.get(0).id());
    assertEquals("Projects", models.get(0).name());
  }

  @Test
  public void getFolderBreadcrumbUnknownFolderFails() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenThrow(new ObjectNotFoundException("not found"));

    assertThrows(ObjectNotFoundException.class, () -> documentMcpTool.getFolderBreadcrumb(FOLDER_ID));
  }

  @Test
  public void getFolderTree() throws Exception {
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    FullTreeItem child = new FullTreeItem("child-1", "Sub", "/documents/Projects/Sub", null, false, String.valueOf(OWNER_ID));
    FullTreeItem root = new FullTreeItem(FOLDER_ID, "Projects", "/documents/Projects", List.of(child), false, String.valueOf(OWNER_ID));
    when(documentFileService.getFullTreeData(eq(OWNER_ID), eq(FOLDER_ID), any(), eq(USER_IDENTITY_ID), anyBoolean(), anyBoolean()))
        .thenReturn(List.of(root));

    List<DocumentTreeItemModel> tree = documentMcpTool.getFolderTree(FOLDER_ID, true);

    assertEquals(1, tree.size());
    assertEquals(FOLDER_ID, tree.get(0).id());
    assertEquals(1, tree.get(0).children().size());
    assertEquals("child-1", tree.get(0).children().get(0).id());
  }

  @Test
  public void listDocumentVersions() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getFileVersions(DOCUMENT_ID, USERNAME)).thenReturn(List.of(fileVersion()));

    List<DocumentVersionModel> versions = documentMcpTool.listDocumentVersions(DOCUMENT_ID);

    assertEquals(1, versions.size());
    assertEquals("version-1", versions.get(0).id());
    assertTrue(versions.get(0).current());
  }

  @Test
  public void getDocumentsSizeDefaultsToCurrentUser() throws Exception {
    when(documentFileService.getDocumentsSizeStat(USER_IDENTITY_ID, USER_IDENTITY_ID)).thenReturn(new DocumentsSize(USER_IDENTITY_ID,
                                                                                                                    4096L,
                                                                                                                    0L,
                                                                                                                    0L,
                                                                                                                    0L,
                                                                                                                    true,
                                                                                                                    0L));

    DocumentsSizeModel model = documentMcpTool.getDocumentsSize(null);

    assertNotNull(model);
    assertEquals(USER_IDENTITY_ID, model.ownerId());
    assertEquals(4096L, model.sizeInBytes());
    // Fresh, non-zero, today's stat -> no on-demand compute needed.
    verify(documentFileService, never()).addDocumentsSizeStat(anyLong(), anyLong());
  }

  @Test
  public void getDocumentsSizeComputesWhenStatMissing() throws Exception {
    // No stat written yet -> getDocumentsSizeStat returns an empty (owner 0, size 0) value.
    when(documentFileService.getDocumentsSizeStat(USER_IDENTITY_ID, USER_IDENTITY_ID))
        .thenReturn(new DocumentsSize(0L, 0L, 0L, 0L, 0L, false, 0L));
    when(documentFileService.addDocumentsSizeStat(USER_IDENTITY_ID, USER_IDENTITY_ID))
        .thenReturn(new DocumentsSize(USER_IDENTITY_ID, 8192L, 0L, 0L, 0L, true, 0L));

    DocumentsSizeModel model = documentMcpTool.getDocumentsSize(null);

    // The tool must trigger the compute path and return the real size for the correct owner,
    // never the misleading owner_id 0 / size 0.
    verify(documentFileService).addDocumentsSizeStat(USER_IDENTITY_ID, USER_IDENTITY_ID);
    assertEquals(USER_IDENTITY_ID, model.ownerId());
    assertEquals(8192L, model.sizeInBytes());
  }

  @Test
  public void createFolder() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.createFolder(eq(OWNER_ID), eq(FOLDER_ID), isNull(), eq("New"), eq(USER_IDENTITY_ID)))
        .thenReturn(folderNode("new-folder"));

    DocumentModel model = documentMcpTool.createFolder(FOLDER_ID, "New");

    assertNotNull(model);
    assertEquals("new-folder", model.getId());
  }

  @Test
  public void createFolderUnderRootFolderPassesNullFolderPath() throws Exception {
    // Reproduces the live bug: creating a folder under the user's ROOT folder
    // (e.g. "Private", whose absolute JCR path is /Users/.../root/Private). The
    // parent folder id already identifies the JCR node, so the storage-level
    // 'folderPath' must be null; passing the absolute path made the JCR storage
    // throw ObjectNotFoundException "Folder with path ... isn't found".
    FolderNode rootFolder = new FolderNode();
    rootFolder.setId(FOLDER_ID);
    rootFolder.setName("Private");
    rootFolder.setPath("/Users/t___/te___/tes___/testuser1/Private");
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(rootFolder);
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.createFolder(eq(OWNER_ID), eq(FOLDER_ID), isNull(), eq("MCPJam-Test"), eq(USER_IDENTITY_ID)))
        .thenReturn(folderNode("new-folder"));

    DocumentModel model = documentMcpTool.createFolder(FOLDER_ID, "MCPJam-Test");

    assertNotNull(model);
    assertEquals("new-folder", model.getId());
    // folderPath must be null (not the absolute root path) so the storage resolves
    // the parent purely from the folder id.
    verify(documentFileService).createFolder(eq(OWNER_ID), eq(FOLDER_ID), isNull(), eq("MCPJam-Test"), eq(USER_IDENTITY_ID));
  }

  @Test
  public void createFolderBlankNameFails() {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.createFolder(FOLDER_ID, " "));
  }

  /**
   * A path in the name creates each missing folder under the one before it,
   * starting from the given parent, and answers the last one: a folder tree
   * then needs no id of a folder created in the same round (EXO-91528).
   *
   * @throws Exception when the tool fails
   */
  @Test
  public void createFolderPathCreatesEachMissingFolderUnderThePreviousOne() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong())).thenReturn(Collections.emptyList());
    when(documentFileService.createFolder(eq(OWNER_ID), eq(FOLDER_ID), isNull(), eq("Finances"), eq(USER_IDENTITY_ID)))
        .thenReturn(folderNode("finances-id"));
    when(documentFileService.createFolder(eq(OWNER_ID), eq("finances-id"), isNull(), eq("Factures"), eq(USER_IDENTITY_ID)))
        .thenReturn(folderNode("factures-id"));
    when(documentFileService.createFolder(eq(OWNER_ID), eq("factures-id"), isNull(), eq("Entrantes"), eq(USER_IDENTITY_ID)))
        .thenReturn(folderNode("entrantes-id"));

    DocumentModel model = documentMcpTool.createFolder(FOLDER_ID, " Finances / Factures/Entrantes ");

    assertEquals("entrantes-id", model.getId());
    verify(documentFileService).createFolder(eq(OWNER_ID), eq("factures-id"), isNull(), eq("Entrantes"), eq(USER_IDENTITY_ID));
  }

  /**
   * A folder of the path that already exists, under another case, is reused
   * rather than created again, so the leaf is created into it; a file of that
   * name is not taken for the folder.
   *
   * @throws Exception when the tool fails
   */
  @Test
  public void createFolderPathReusesTheExistingFolderOfASegment() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    FolderNode existing = folderNode("finances-id");
    existing.setName("finances");
    when(documentFileService.getFolderChildNodes(argThat(filter -> filter != null && FOLDER_ID.equals(filter.getParentFolderId())),
                                                 anyInt(),
                                                 anyInt(),
                                                 anyLong())).thenReturn(List.of(fileNodeNamed("file-id", "Finances"), existing));
    when(documentFileService.createFolder(eq(OWNER_ID), eq("finances-id"), isNull(), eq("Budgets"), eq(USER_IDENTITY_ID)))
        .thenReturn(folderNode("budgets-id"));

    DocumentModel model = documentMcpTool.createFolder(FOLDER_ID, "Finances/Budgets");

    assertEquals("budgets-id", model.getId());
    verify(documentFileService, never()).createFolder(anyLong(), anyString(), any(), eq("Finances"), anyLong());
  }

  /**
   * A folder of the path created by another call between the lookup and the
   * creation is looked up again and reused, rather than failing the call.
   *
   * @throws Exception when the tool fails
   */
  @Test
  public void createFolderPathReusesASegmentFolderCreatedMeanwhile() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    FolderNode createdMeanwhile = folderNode("contrats-id");
    createdMeanwhile.setName("Contrats");
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(Collections.emptyList())
        .thenReturn(List.of(createdMeanwhile));
    when(documentFileService.createFolder(eq(OWNER_ID), eq(FOLDER_ID), isNull(), eq("Contrats"), eq(USER_IDENTITY_ID)))
        .thenThrow(new ObjectAlreadyExistsException("Contrats"));
    when(documentFileService.createFolder(eq(OWNER_ID), eq("contrats-id"), isNull(), eq("Signés"), eq(USER_IDENTITY_ID)))
        .thenReturn(folderNode("signes-id"));

    DocumentModel model = documentMcpTool.createFolder(FOLDER_ID, "Contrats/Signés");

    assertEquals("signes-id", model.getId());
  }

  /**
   * The leaf of a path that already exists keeps failing as a single name
   * does, so the user picks another name.
   *
   * @throws Exception when the tool fails
   */
  @Test
  public void createFolderPathWhoseLeafExistsFails() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong())).thenReturn(List.of(folderNode("contrats-id")));
    when(documentFileService.createFolder(eq(OWNER_ID), eq("contrats-id"), isNull(), eq("Signés"), eq(USER_IDENTITY_ID)))
        .thenThrow(new ObjectAlreadyExistsException("Signés"));

    IllegalStateException error = assertThrows(IllegalStateException.class,
                                               () -> documentMcpTool.createFolder(FOLDER_ID, "Projects/Signés"));

    assertTrue(error.getMessage().contains("'Signés' already exists"));
  }

  /**
   * A path segment naming the folder itself or its parent is refused before
   * anything is created.
   *
   * @throws Exception when the tool fails
   */
  @Test
  public void createFolderPathWithDotSegmentFails() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.createFolder(FOLDER_ID, "Finances/../Budgets"));
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.createFolder(FOLDER_ID, " / "));
    verify(documentFileService, never()).createFolder(anyLong(), anyString(), any(), anyString(), anyLong());
  }

  /**
   * A shortcut to a folder, listed as a folder carrying its target's id as
   * source id, is never reused as a segment of the path: a folder is created
   * beside it instead, since none can be created under a shortcut node.
   *
   * @throws Exception when the tool fails
   */
  @Test
  public void createFolderPathDoesNotReuseAShortcutOfTheSegmentName() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    FolderNode shortcut = folderNode("shortcut-id");
    shortcut.setName("Finances");
    shortcut.setSourceID("target-id");
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong())).thenReturn(List.of(shortcut));
    when(documentFileService.createFolder(eq(OWNER_ID), eq(FOLDER_ID), isNull(), eq("Finances"), eq(USER_IDENTITY_ID)))
        .thenReturn(folderNode("finances-id"));
    when(documentFileService.createFolder(eq(OWNER_ID), eq("finances-id"), isNull(), eq("Budgets"), eq(USER_IDENTITY_ID)))
        .thenReturn(folderNode("budgets-id"));

    DocumentModel model = documentMcpTool.createFolder(FOLDER_ID, "Finances/Budgets");

    assertEquals("budgets-id", model.getId());
    verify(documentFileService, never()).createFolder(anyLong(), eq("shortcut-id"), any(), anyString(), anyLong());
  }

  /**
   * A segment whose node name is taken by an item the lookup can't find again
   * fails with a message that names the possible causes, rather than claiming
   * the item is not a folder.
   *
   * @throws Exception when the tool fails
   */
  @Test
  public void createFolderPathWhoseSegmentNameIsTakenByAnItemNotFoundFails() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong())).thenReturn(Collections.emptyList());
    when(documentFileService.createFolder(eq(OWNER_ID), eq(FOLDER_ID), isNull(), eq("R&D"), eq(USER_IDENTITY_ID)))
        .thenThrow(new ObjectAlreadyExistsException("r_d"));

    IllegalStateException error = assertThrows(IllegalStateException.class,
                                               () -> documentMcpTool.createFolder(FOLDER_ID, "R&D/Budgets"));

    assertTrue(error.getMessage().contains("'R&D' already exists in this folder but can't be reused as a folder"));
    assertTrue(error.getMessage().contains("a name differing only by punctuation"));
    verify(documentFileService, never()).createFolder(anyLong(), anyString(), any(), eq("Budgets"), anyLong());
  }

  /**
   * Every segment of the path is checked against the storage's title rule
   * before the first folder is created, so an invalid last segment creates
   * nothing.
   *
   * @throws Exception when the tool fails
   */
  @Test
  public void createFolderPathWithAnInvalidSegmentCreatesNothing() throws Exception {
    lenient().when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    lenient().when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                  () -> documentMcpTool.createFolder(FOLDER_ID, "Finances/Factures/Q1?"));
    assertTrue(error.getMessage().contains("'Q1?' is not valid"));
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.createFolder(FOLDER_ID, "Finances/.pdf"));
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.createFolder(FOLDER_ID, "A<B"));

    verify(documentFileService, never()).createFolder(anyLong(), anyString(), any(), anyString(), anyLong());
  }

  /**
   * A parent folder id no folder has, such as one the model composed for a
   * folder it creates in the same round, is answered with what to do instead:
   * use a returned id, or pass the nested folders as a path (EXO-91528).
   *
   * @throws Exception when the tool fails
   */
  @Test
  public void createFolderUnderAnUnknownParentTellsToUseAReturnedIdOrAPath() throws Exception {
    String composedId = "26123e45c0a8004950b9ade0f1a2c3d4";
    when(documentFileService.getDocumentById(composedId, USERNAME))
        .thenThrow(new ObjectNotFoundException("Node with id " + composedId + " not found"));

    ObjectNotFoundException error = assertThrows(ObjectNotFoundException.class,
                                                 () -> documentMcpTool.createFolder(composedId, "Modèles"));

    assertTrue(error.getMessage().contains("No folder has the id " + composedId));
    assertTrue(error.getMessage().contains("never a composed one"));
    assertTrue(error.getMessage().contains("'Finances/Factures/Entrantes'"));
    assertTrue(error.getMessage().contains("get_root_folder_for_user"));
    verify(documentFileService, never()).createFolder(anyLong(), anyString(), any(), anyString(), anyLong());
  }

  private FileNode fileNodeNamed(String id, String name) {
    FileNode fileNode = new FileNode();
    fileNode.setId(id);
    fileNode.setName(name);
    fileNode.setPath("/documents/Projects/" + name);
    fileNode.setMimeType("text/plain");
    fileNode.setSize(16L);
    return fileNode;
  }



  @Test
  public void createDocumentBlankNameFails() {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.createDocument(FOLDER_ID, " ", "body"));
  }

  @Test
  public void createDocumentMissingExtensionFails() {
    // A name with no extension (and a trailing-dot name, which also has no
    // extension) must fail: the extension drives the stored content type.
    for (String name : new String[] { "notes", "my.notes.", "report " }) {
      IllegalArgumentException ex =
                                  assertThrows(IllegalArgumentException.class,
                                               () -> documentMcpTool.createDocument(FOLDER_ID, name, "some content"));
      assertTrue(ex.getMessage().contains("must include a file extension"));
    }
  }

  @Test
  public void createDocumentOfficeExtensionFails() {
    // .docx / .xlsx / .pptx / .pdf are structured/binary formats that cannot be
    // built from a text string: fail up front with an LLM-directed message that
    // names the extension and points to upload_document.
    for (String name : new String[] { "report.docx", "budget.xlsx", "deck.pptx", "doc.pdf" }) {
      IllegalArgumentException ex =
                                   assertThrows(IllegalArgumentException.class,
                                                () -> documentMcpTool.createDocument(FOLDER_ID, name, "some content"));
      String ext = name.substring(name.lastIndexOf('.') + 1);
      assertTrue(ex.getMessage().contains("'" + ext + "'"));
      assertTrue(ex.getMessage().contains("upload_document"));
    }
  }

  @Test
  public void createDocumentBlankContentFails() {
    IllegalArgumentException nullContent =
                                         assertThrows(IllegalArgumentException.class,
                                                      () -> documentMcpTool.createDocument(FOLDER_ID, "notes.md", null));
    assertTrue(nullContent.getMessage().contains("content is required"));
    IllegalArgumentException blankContent =
                                          assertThrows(IllegalArgumentException.class,
                                                       () -> documentMcpTool.createDocument(FOLDER_ID, "notes.md", "  "));
    assertTrue(blankContent.getMessage().contains("content is required"));
  }

  @Test
  public void createDocumentFromTemplateSucceeds() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    // ecms names the file after the title (with the extension), so the tool builds
    // "Q3 report.docx" and passes the normalized type "docx".
    when(documentFileService.createDocumentFromTemplate(eq(OWNER_ID),
                                                        eq(FOLDER_ID),
                                                        isNull(),
                                                        eq("Q3 report.docx"),
                                                        eq("docx"),
                                                        eq(USER_IDENTITY_ID)))
        .thenReturn(fileNodeNamed("doc-tpl", "q3 report.docx"));

    DocumentModel model = documentMcpTool.createDocumentFromTemplate(FOLDER_ID, "Q3 report", "docx");

    assertNotNull(model);
    assertEquals("doc-tpl", model.getId());
    verify(documentFileService).createDocumentFromTemplate(eq(OWNER_ID),
                                                           eq(FOLDER_ID),
                                                           isNull(),
                                                           eq("Q3 report.docx"),
                                                           eq("docx"),
                                                           eq(USER_IDENTITY_ID));
  }

  @Test
  public void createDocumentFromTemplateStripsDuplicateExtensionAndNormalizesType() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.createDocumentFromTemplate(eq(OWNER_ID),
                                                        eq(FOLDER_ID),
                                                        isNull(),
                                                        eq("budget.xlsx"),
                                                        eq("xlsx"),
                                                        eq(USER_IDENTITY_ID)))
        .thenReturn(fileNodeNamed("doc-xls", "budget.xlsx"));

    // name already carries the extension and document_type is upper-cased with a
    // leading dot: the tool must not produce "budget.xlsx.xlsx" nor pass ".XLSX".
    DocumentModel model = documentMcpTool.createDocumentFromTemplate(FOLDER_ID, "budget.xlsx", ".XLSX");

    assertNotNull(model);
    assertEquals("doc-xls", model.getId());
    verify(documentFileService).createDocumentFromTemplate(eq(OWNER_ID),
                                                           eq(FOLDER_ID),
                                                           isNull(),
                                                           eq("budget.xlsx"),
                                                           eq("xlsx"),
                                                           eq(USER_IDENTITY_ID));
  }

  @Test
  public void createDocumentFromTemplateBlankNameFails() {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.createDocumentFromTemplate(FOLDER_ID, " ", "docx"));
  }

  @Test
  public void createDocumentFromTemplateBlankTypeFails() {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.createDocumentFromTemplate(FOLDER_ID, "report", " "));
  }

  @Test
  public void createDocumentFromTemplateUnsupportedTypeFails() {
    IllegalArgumentException ex =
                                assertThrows(IllegalArgumentException.class,
                                             () -> documentMcpTool.createDocumentFromTemplate(FOLDER_ID, "report", "pdf"));
    assertTrue(ex.getMessage().contains("Unsupported document_type"));
  }

  @Test
  public void createDocumentFromTemplateBlankFolderFails() {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.createDocumentFromTemplate(" ", "report", "docx"));
  }

  @Test
  public void createDocumentFromTemplateBadFolderFails() throws Exception {
    when(documentFileService.getDocumentById("missing", USERNAME)).thenThrow(new ObjectNotFoundException("no node"));
    assertThrows(ObjectNotFoundException.class,
                 () -> documentMcpTool.createDocumentFromTemplate("missing", "report", "docx"));
  }

  @Test
  public void createDocumentFromTemplateNoEditPermissionFails() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.createDocumentFromTemplate(anyLong(), anyString(), isNull(), anyString(), anyString(), anyLong()))
        .thenThrow(new IllegalAccessException("Permission to add document is missing"));
    assertThrows(IllegalAccessException.class,
                 () -> documentMcpTool.createDocumentFromTemplate(FOLDER_ID, "report", "docx"));
  }

  @Test
  public void createDocumentFromTemplateNameClashFails() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.createDocumentFromTemplate(anyLong(), anyString(), isNull(), anyString(), anyString(), anyLong()))
        .thenThrow(new ObjectAlreadyExistsException("exists"));
    IllegalStateException ex =
                             assertThrows(IllegalStateException.class,
                                          () -> documentMcpTool.createDocumentFromTemplate(FOLDER_ID, "report", "docx"));
    assertTrue(ex.getMessage().contains("already exists"));
  }

  /**
   * A file sent whole is filed at once with the conflict action asked, its
   * bytes the decoded base64, and the staged upload is released; an unknown
   * conflict action is refused before anything is staged.
   * Mutants: the conflict action dropped or defaulted, the bytes not the
   * decoded ones, the upload not released, an unknown action accepted.
   *
   * @throws Exception never
   */
  @Test
  public void uploadDocumentFromBase64() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    fileUploadsAs(fileNodeNamed("upl-1", "hello.txt"));
    String base64 = java.util.Base64.getEncoder().encodeToString("hello".getBytes());

    DocumentModel model = documentMcpTool.uploadDocument(FOLDER_ID, "hello.txt", base64, null, null, null, "New_Version", null);

    assertEquals("upl-1", model.getId());
    ArgumentCaptor<String> uploadId = ArgumentCaptor.forClass(String.class);
    verify(documentFileService).saveUploadedDocument(eq(FOLDER_ID),
                                                     uploadId.capture(),
                                                     eq("hello.txt"),
                                                     eq(UploadConflictAction.NEW_VERSION),
                                                     eq(USER_IDENTITY_ID));
    assertArrayEquals("hello".getBytes(), filedBytes.get(uploadId.getValue()));
    verify(uploadService).removeUploadResource(uploadId.getValue());

    assertTrue(assertThrows(IllegalArgumentException.class,
                            () -> documentMcpTool.uploadDocument(FOLDER_ID, "hello.txt", base64, null, null, null, "merge", null)).getMessage()
                                                                                                                         .contains("keep_both"));
    verify(documentFileService).saveUploadedDocument(any(), any(), any(), any(), anyLong());
  }

  @Test
  public void uploadDocumentFromChatAttachment() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    fileUploadsAs(fileNodeNamed("upl-att", "screenshot.png"));
    // The client injects a platform attachment reference; the tool resolves its
    // bytes as the current user (ACL enforced) via the social AttachmentService.
    when(socialAttachmentService.getAttachmentFileIds(eq("activity"), eq("77"), eq(currentIdentity)))
        .thenReturn(List.of("999"));
    org.exoplatform.commons.file.model.FileItem fileItem = Mockito.mock(org.exoplatform.commons.file.model.FileItem.class);
    org.exoplatform.commons.file.model.FileInfo fileInfo = Mockito.mock(org.exoplatform.commons.file.model.FileInfo.class);
    when(fileItem.getAsByte()).thenReturn("PNGBYTES".getBytes());
    when(fileItem.getFileInfo()).thenReturn(fileInfo);
    when(fileInfo.getMimetype()).thenReturn("image/png");
    when(fileInfo.getName()).thenReturn("screenshot.png");
    when(fileService.getFile(999L)).thenReturn(fileItem);

    // name left blank -> reused from the resolved attachment file name.
    DocumentModel model = documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, "activity", "77", null, null);

    assertNotNull(model);
    assertEquals("upl-att", model.getId());
    verify(documentFileService).saveUploadedDocument(eq(FOLDER_ID),
                                                     anyString(),
                                                     eq("screenshot.png"),
                                                     eq(UploadConflictAction.KEEP_BOTH),
                                                     eq(USER_IDENTITY_ID));
  }

  @Test
  public void uploadDocumentUnknownAttachmentFails() throws Exception {
    when(socialAttachmentService.getAttachmentFileIds(eq("activity"), eq("77"), eq(currentIdentity)))
        .thenReturn(java.util.Collections.emptyList());

    assertThrows(ObjectNotFoundException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, "activity", "77", null, null));
  }

  @Test
  public void uploadDocumentRequiresExactlyOneSource() {
    String base64 = java.util.Base64.getEncoder().encodeToString("hello".getBytes());
    // no source at all
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.uploadDocument(FOLDER_ID, "x.txt", null, null, null, null, null, null));
    // both base64 and url
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, "x.txt", base64, "https://example.com/x.txt", null, null, null, null));
    // both an attachment and base64
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, "x.txt", base64, null, "activity", "77", null, null));
  }

  @Test
  public void uploadDocumentBase64RequiresName() {
    String base64 = java.util.Base64.getEncoder().encodeToString("hello".getBytes());
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.uploadDocument(FOLDER_ID, " ", base64, null, null, null, null, null));
  }

  @Test
  public void renameDocument() throws Exception {
    when(documentFileService.getRootFolderOwnerId(DOCUMENT_ID)).thenReturn(OWNER_ID);

    documentMcpTool.renameDocument(DOCUMENT_ID, "renamed.pdf");

    verify(documentFileService).renameDocument(eq(OWNER_ID), eq(DOCUMENT_ID), eq("renamed.pdf"), eq(USER_IDENTITY_ID));
  }

  @Test
  public void moveDocumentDefaultsConflictActionToKeepBoth() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(DOCUMENT_ID)).thenReturn(OWNER_ID);

    documentMcpTool.moveDocument(DOCUMENT_ID, FOLDER_ID, null);

    // "keepBoth" is the only rename-style conflict value the storage honours;
    // the old default "rename" was ignored (fell through to ObjectAlreadyExists).
    verify(documentFileService).moveDocument(eq(OWNER_ID),
                                             eq(DOCUMENT_ID),
                                             eq("/documents/Projects"),
                                             eq(USER_IDENTITY_ID),
                                             eq("keepBoth"));
  }

  @Test
  public void moveDocumentBlankDestinationFails() {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.moveDocument(DOCUMENT_ID, " ", null));
  }

  @Test
  public void copyDocumentReturnsResolvedCopyNotDestinationFolder() throws Exception {
    // The storage copyDocument returns the DESTINATION FOLDER node (not the copy);
    // the tool must re-resolve the newly added file in the destination folder, by
    // name (the copy keeps the source's "report.pdf" name).
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.copyDocument(DOCUMENT_ID, FOLDER_ID, USER_IDENTITY_ID)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(Collections.emptyList())
        .thenReturn(List.of(fileNodeNamed("copy-1", "report.pdf")));

    DocumentModel model = documentMcpTool.copyDocument(DOCUMENT_ID, FOLDER_ID);

    assertNotNull(model);
    // Must be the resolved copied file, NOT the destination folder id the storage returned.
    assertEquals("copy-1", model.getId());
    assertFalse(FOLDER_ID.equals(model.getId()));
  }

  @Test
  public void copyDocumentResolvesByNameIgnoringConcurrentDifferentFile() throws Exception {
    // Concurrency: while the copy of "report.pdf" is created, another user drops a
    // DIFFERENT-named file ("other.pdf") into the same destination. The old blind
    // folder-diff + reduce((a,b)->b) heuristic returns the last added id (here the
    // concurrent "other.pdf"); the name-aware resolver must return the real copy.
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.copyDocument(DOCUMENT_ID, FOLDER_ID, USER_IDENTITY_ID)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(Collections.emptyList())
        .thenReturn(List.of(fileNodeNamed("copy-1", "report.pdf"), fileNodeNamed("concurrent-1", "other.pdf")));

    DocumentModel model = documentMcpTool.copyDocument(DOCUMENT_ID, FOLDER_ID);

    assertNotNull(model);
    assertEquals("copy-1", model.getId());
  }

  @Test
  public void copyDocumentThrowsWhenMultipleCandidatesMatch() throws Exception {
    // Ambiguity guard (B): two newly-added files both match the expected copy name
    // ("report.pdf" and its " (1)" counter variant). The resolver must refuse to
    // guess and throw a clear error rather than silently return one of them.
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.copyDocument(DOCUMENT_ID, FOLDER_ID, USER_IDENTITY_ID)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(Collections.emptyList())
        .thenReturn(List.of(fileNodeNamed("copy-1", "report.pdf"), fileNodeNamed("copy-2", "report.pdf (1)")));

    IllegalStateException ex = assertThrows(IllegalStateException.class,
                                            () -> documentMcpTool.copyDocument(DOCUMENT_ID, FOLDER_ID));
    assertTrue(ex.getMessage().contains("multiple candidates"));
  }

  /**
   * A copied folder and a duplicated folder are found after the operation and
   * returned, by their kind and their name. A file of the same name added
   * meanwhile is not taken for the folder, and a
   * file is still resolved among files only.
   * Mutants: the copied node read among files only again, for the copy or the
   * duplicate, or the kind ignored.
   *
   * @throws Exception never
   */
  @Test
  public void aCopiedOrDuplicatedFolderIsFoundAfterTheOperation() throws Exception {
    FolderNode source = folderNode(DOCUMENT_ID);
    source.setParentFolderId(FOLDER_ID);
    FolderNode copied = folderNode("copy-folder");
    FolderNode duplicated = folderNode("dup-folder");
    duplicated.setName("Copy of Projects");
    FileNode sameNamedFile = fileNodeNamed("file-1", "Projects");
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(source);
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(DOCUMENT_ID)).thenReturn(OWNER_ID);
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(List.of())
        .thenReturn(List.of(sameNamedFile, copied))
        .thenReturn(List.of(source))
        .thenReturn(List.of(source, duplicated));

    assertEquals("copy-folder", documentMcpTool.copyDocument(DOCUMENT_ID, FOLDER_ID).getId());
    assertEquals("dup-folder", documentMcpTool.duplicateDocument(DOCUMENT_ID, "Copy of").getId());
  }

  @Test
  public void duplicateDocumentReturnsResolvedDuplicateNotParentFolder() throws Exception {
    // The storage duplicateDocument returns the PARENT FOLDER node (not the
    // duplicate); the tool must re-resolve the newly added file in the parent, by
    // name (the duplicate is named "<prefix> <source name>" = "Copy of report.pdf").
    FileNode source = fileNode(DOCUMENT_ID, "application/pdf");
    source.setParentFolderId(FOLDER_ID);
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(source);
    when(documentFileService.getRootFolderOwnerId(DOCUMENT_ID)).thenReturn(OWNER_ID);
    when(documentFileService.duplicateDocument(OWNER_ID, DOCUMENT_ID, "Copy of", USER_IDENTITY_ID)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(List.of(source))
        .thenReturn(List.of(source, fileNodeNamed("dup-1", "Copy of report.pdf")));

    DocumentModel model = documentMcpTool.duplicateDocument(DOCUMENT_ID, "Copy of");

    assertNotNull(model);
    // Must be the resolved duplicate (the newly added file), NOT the parent folder.
    assertEquals("dup-1", model.getId());
    assertFalse(FOLDER_ID.equals(model.getId()));
  }

  @Test
  public void duplicateDocumentResolvesByNameIgnoringConcurrentDifferentFile() throws Exception {
    // Concurrency: while the "Copy of report.pdf" duplicate is created in the
    // parent folder, another differently-named file ("other.pdf") appears there.
    // The name-aware resolver must return the duplicate, not the concurrent file
    // (which reduce((a,b)->b) would wrongly return as the last added id).
    FileNode source = fileNode(DOCUMENT_ID, "application/pdf");
    source.setParentFolderId(FOLDER_ID);
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(source);
    when(documentFileService.getRootFolderOwnerId(DOCUMENT_ID)).thenReturn(OWNER_ID);
    when(documentFileService.duplicateDocument(OWNER_ID, DOCUMENT_ID, "Copy of", USER_IDENTITY_ID)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(List.of(source))
        .thenReturn(List.of(source,
                            fileNodeNamed("dup-1", "Copy of report.pdf"),
                            fileNodeNamed("concurrent-1", "other.pdf")));

    DocumentModel model = documentMcpTool.duplicateDocument(DOCUMENT_ID, "Copy of");

    assertNotNull(model);
    assertEquals("dup-1", model.getId());
  }

  @Test
  public void deleteDocumentMovesToTrashImmediately() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));

    documentMcpTool.deleteDocument(DOCUMENT_ID);

    // favorite=true so a favorited file is also dropped from favorites on delete
    // (the storage cleanup uses object type "file").
    verify(documentFileService).deleteDocument(eq("/documents/report.pdf"),
                                               eq(DOCUMENT_ID),
                                               eq(true),
                                               eq(0L),
                                               eq(USER_IDENTITY_ID));
  }

  @Test
  public void deleteDocumentUnknownFails() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenThrow(new ObjectNotFoundException("not found"));

    assertThrows(ObjectNotFoundException.class, () -> documentMcpTool.deleteDocument(DOCUMENT_ID));
  }

  @Test
  public void undoDeleteDocumentRestoresFromTrash() throws Exception {
    // After an immediate delete the node lives in trash but keeps its id; undo must
    // actually restore it from trash (the delayed-delete cancel path is a no-op).
    FileNode trashed = fileNode(DOCUMENT_ID, "application/pdf");
    trashed.setPath("/Trash/report.pdf");
    // The trashed node must be resolved through the SYSTEM-session overload
    // (getDocumentById(id)), not the user-session one: trash lives under a system
    // session and a regular user session cannot read the trashed node.
    when(documentFileService.getDocumentById(DOCUMENT_ID)).thenReturn(trashed);

    documentMcpTool.undoDeleteDocument(DOCUMENT_ID);

    verify(documentFileService).restoreDocumentFromTrash(eq("/Trash/report.pdf"));
    // It must use the system-session overload, NOT the user-session one (which would
    // false-negative on a trashed node).
    verify(documentFileService).getDocumentById(DOCUMENT_ID);
    verify(documentFileService, never()).getDocumentById(DOCUMENT_ID, USERNAME);
    // It must NOT rely on the no-op delayed-delete cancel.
    verify(documentFileService, never()).undoDeleteDocument(anyString(), anyLong());
  }

  @Test
  public void undoDeleteDocumentNotFoundFails() throws Exception {
    // The system-session overload returns null when the node is genuinely gone.
    when(documentFileService.getDocumentById(DOCUMENT_ID)).thenReturn(null);

    // Never silently succeed: a document no longer in trash must surface a clear error.
    assertThrows(IllegalStateException.class, () -> documentMcpTool.undoDeleteDocument(DOCUMENT_ID));
  }

  @Test
  public void restoreDocumentVersion() {
    when(documentFileService.restoreVersion("version-1", USERNAME)).thenReturn(fileVersion());

    DocumentVersionModel model = documentMcpTool.restoreDocumentVersion("version-1");

    assertNotNull(model);
    assertEquals("version-1", model.id());
  }

  @Test
  public void restoreDocumentVersionBlankFails() {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.restoreDocumentVersion(" "));
  }

  @Test
  public void updateVersionSummaryRereadsFullVersion() {
    // Storage updateVersionSummary returns a near-empty FileVersion (only id + summary);
    // the tool must re-read the full version so number/author/dates/size are populated.
    FileVersion stamped = new FileVersion();
    stamped.setId("version-1");
    stamped.setSummary("new summary");
    when(documentFileService.updateVersionSummary(DOCUMENT_ID, "version-1", "new summary", USERNAME)).thenReturn(stamped);
    FileVersion full = fileVersion();
    full.setSummary("new summary");
    when(documentFileService.getFileVersions(DOCUMENT_ID, USERNAME)).thenReturn(List.of(full));

    DocumentVersionModel model = documentMcpTool.updateVersionSummary(DOCUMENT_ID, "version-1", "new summary");

    assertNotNull(model);
    assertEquals("new summary", model.summary());
    // Fields that only the full re-read carries:
    assertEquals(2, model.versionNumber());
    assertEquals("Test User", model.authorFullName());
    assertTrue(model.current());
  }

  @Test
  public void setDocumentVisibility() throws Exception {
    when(documentFileService.getRootFolderOwnerId(DOCUMENT_ID)).thenReturn(OWNER_ID);

    documentMcpTool.setDocumentVisibility(DOCUMENT_ID, Boolean.TRUE);

    verify(documentFileService).setDocumentVisibility(eq(OWNER_ID), eq(DOCUMENT_ID), eq(Boolean.TRUE), eq(USER_IDENTITY_ID));
  }

  @Test
  public void setDocumentVisibilityNullHiddenFails() {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.setDocumentVisibility(DOCUMENT_ID, null));
  }

  // ---------------------------------------------------------------------------
  // Versioning, sharing, public links and favorites
  // ---------------------------------------------------------------------------

  @Test
  public void addDocumentVersionFromContent() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);

    DocumentModel model = documentMcpTool.addDocumentVersion(DOCUMENT_ID, "updated body", null, null, null, null, null, null);

    assertNotNull(model);
    assertEquals(DOCUMENT_ID, model.getId());
    verify(documentFileService).createNewVersion(eq(DOCUMENT_ID), eq(USERNAME), any());
    // No summary provided -> the version summary is not touched.
    verify(documentFileService, Mockito.never()).updateVersionSummary(any(), any(), any(), any());
  }

  @Test
  public void addDocumentVersionWithSummaryStampsCurrentVersion() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    when(documentFileService.getFileVersions(DOCUMENT_ID, USERNAME)).thenReturn(List.of(fileVersion()));

    documentMcpTool.addDocumentVersion(DOCUMENT_ID, "updated body", null, null, null, null, "reworded intro", null);

    verify(documentFileService).createNewVersion(eq(DOCUMENT_ID), eq(USERNAME), any());
    verify(documentFileService).updateVersionSummary(eq(DOCUMENT_ID), eq("version-1"), eq("reworded intro"), eq(USERNAME));
  }

  /**
   * A new version's text content is capped at the upload size limit a new
   * file is capped at, read from the same property: one byte over is refused
   * before anything is stored, the limit itself is accepted. A base64 one is
   * refused well below, by its length
   * ({@link #aBase64FileLongerThanOneCallIsRefusedBeforeItIsDecoded}).
   * Mutants: the cap removed, not the upload limit, or off by one.
   *
   * @throws Exception never
   */
  @Test
  public void aNewVersionsInlineContentIsCapped() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    int limit = 1024 * 1024;
    String overText = "a".repeat(limit + 1);

    org.exoplatform.commons.utils.PropertyManager.setProperty(DocumentUploadLimit.PROPERTY_NAME, "1");
    try {
      assertThrows(IllegalArgumentException.class,
                   () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, overText, null, null, null, null, null, null));
      verify(documentFileService, never()).createNewVersion(any(), any(), any());

      documentMcpTool.addDocumentVersion(DOCUMENT_ID, "a".repeat(limit), null, null, null, null, null, null);
    } finally {
      org.exoplatform.commons.utils.PropertyManager.setProperty(DocumentUploadLimit.PROPERTY_NAME, "");
    }
    verify(documentFileService).createNewVersion(eq(DOCUMENT_ID), eq(USERNAME), any());
  }

  @Test
  public void addDocumentVersionNoSourceFails() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);

    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, null, null, null, null));
  }

  @Test
  public void addDocumentVersionOnFolderFails() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));

    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.addDocumentVersion(FOLDER_ID, "body", null, null, null, null, null, null));
  }

  @Test
  public void addDocumentVersionWithoutEditPermissionFails() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(false);

    assertThrows(IllegalAccessException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, "body", null, null, null, null, null, null));
  }

  @Test
  public void createPublicLink() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    Date expiration = new Date(1893456000000L);
    PublicDocumentAccess access = new PublicDocumentAccess(1L, DOCUMENT_ID, null, null, expiration);
    access.setDecodedPassword("s3cretPass");
    when(publicDocumentAccessService.createPublicDocumentAccess(eq(USER_IDENTITY_ID),
                                                               eq(DOCUMENT_ID),
                                                               eq("s3cretPass"),
                                                               anyLong(),
                                                               anyBoolean())).thenReturn(access);

    DocumentPublicLinkModel model = documentMcpTool.createPublicLink(DOCUMENT_ID, "s3cretPass", expiration.getTime());

    assertNotNull(model);
    assertEquals("/portal/download-document/" + DOCUMENT_ID, model.url());
    assertEquals("s3cretPass", model.password());
    assertNotNull(model.expirationDate());
  }

  @Test
  public void createPublicLinkRejectsInvalidPassword() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);

    // Too short for the same password policy DocumentFileRest applies (min 9 chars).
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.createPublicLink(DOCUMENT_ID, "short", null));
    verify(publicDocumentAccessService, never()).createPublicDocumentAccess(anyLong(), anyString(), anyString(), anyLong(), anyBoolean());
  }

  @Test
  public void createPublicLinkWithoutEditPermissionFails() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(false);

    assertThrows(IllegalAccessException.class, () -> documentMcpTool.createPublicLink(DOCUMENT_ID, null, null));
  }

  @Test
  public void createDocumentShortcutDefaultsConflictToKeepBoth() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));

    documentMcpTool.createDocumentShortcut(DOCUMENT_ID, FOLDER_ID, null);

    // "keepBoth" is the only value the storage handleShortcutDocConflict honours;
    // the old default "rename" fell through to ObjectAlreadyExists on a clash.
    verify(documentFileService).createShortcut(eq(DOCUMENT_ID), eq("/documents/Projects"), eq(USERNAME), eq("keepBoth"));
  }

  @Test
  public void createDocumentShortcutBlankDestinationFails() {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.createDocumentShortcut(DOCUMENT_ID, " ", null));
  }

  @Test
  public void shareDocumentWithUser() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    org.exoplatform.social.core.identity.model.Identity bob =
                                                             new org.exoplatform.social.core.identity.model.Identity("200");
    when(identityManager.getOrCreateUserIdentity("bob")).thenReturn(bob);

    documentMcpTool.shareDocument(DOCUMENT_ID, "bob", null, null, null);

    // Must route through updatePermissions (grant + share), not the bare shareDocument
    // that only copies the recipient's pre-existing permissions onto the symlink.
    verify(documentFileService).updatePermissions(eq(DOCUMENT_ID),
                                                  argThat((NodePermission np) -> np.getToShare().containsKey(200L)
                                                                                 && np.getToNotify().containsKey(200L)
                                                                                 && np.getPermissions()
                                                                                      .stream()
                                                                                      .anyMatch(p -> p.getIdentity()
                                                                                                      .getIdentityId() == 200L
                                                                                                     && "read".equals(p.getPermission()))),
                                                  eq(USER_IDENTITY_ID));
    verify(documentFileService, never()).shareDocument(anyString(), anyLong(), anyLong(), anyBoolean());
  }

  @Test
  public void shareDocumentWithSpace() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    Space space = new Space();
    space.setId(String.valueOf(SPACE_ID));
    space.setPrettyName("engineering");
    when(spaceService.getSpaceById(String.valueOf(SPACE_ID))).thenReturn(space);
    org.exoplatform.social.core.identity.model.Identity spaceIdentity =
                                                                       new org.exoplatform.social.core.identity.model.Identity("300");
    when(identityManager.getOrCreateSpaceIdentity("engineering")).thenReturn(spaceIdentity);

    documentMcpTool.shareDocument(DOCUMENT_ID, null, SPACE_ID, null, false);

    // notify=false -> the recipient is in toShare (granted access) but not in toNotify.
    verify(documentFileService).updatePermissions(eq(DOCUMENT_ID),
                                                  argThat((NodePermission np) -> np.getToShare().containsKey(300L)
                                                                                 && np.getToNotify().isEmpty()),
                                                  eq(USER_IDENTITY_ID));
  }

  @Test
  public void shareDocumentRequiresExactlyOneRecipient() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));

    // neither recipient
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.shareDocument(DOCUMENT_ID, null, null, null, null));
    // both recipients
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.shareDocument(DOCUMENT_ID, "bob", SPACE_ID, null, null));
  }

  @Test
  public void shareDocumentWithoutEditPermissionFails() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(false);

    assertThrows(IllegalAccessException.class, () -> documentMcpTool.shareDocument(DOCUMENT_ID, "bob", null, null, null));
  }

  @Test
  public void shareDocumentDoesNotDowngradeExistingEditor() throws Exception {
    // The recipient already collaborates on the document with EDIT (an "add_node"
    // raw JCR permission). Sharing must not append a (recipient,"read") entry after
    // the preserved (recipient,"edit"), which the storage's list-ordered write would
    // otherwise use to silently DOWNGRADE the recipient to read.
    org.exoplatform.social.core.identity.model.Identity bob =
                                                             new org.exoplatform.social.core.identity.model.Identity("200");
    bob.setProviderId("organization");
    bob.setRemoteId("bob");
    when(identityManager.getOrCreateUserIdentity("bob")).thenReturn(bob);

    org.exoplatform.social.core.identity.model.Identity bobInAcl =
                                                                  new org.exoplatform.social.core.identity.model.Identity("200");
    bobInAcl.setProviderId("organization");
    bobInAcl.setRemoteId("bob");
    FileNode node = fileNode(DOCUMENT_ID, "application/pdf");
    node.setAcl(new NodePermission(true,
                                   true,
                                   false,
                                   false,
                                   List.of(new PermissionEntry(bobInAcl, "add_node", PermissionRole.ALL.name())),
                                   new java.util.HashMap<>(),
                                   new java.util.HashMap<>(),
                                   null));
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(node);
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);

    documentMcpTool.shareDocument(DOCUMENT_ID, "bob", null, null, null);

    verify(documentFileService).updatePermissions(eq(DOCUMENT_ID),
                                                  argThat((NodePermission np) -> np.getPermissions()
                                                                                   .stream()
                                                                                   .anyMatch(p -> p.getIdentity()
                                                                                                   .getIdentityId() == 200L
                                                                                                  && "edit".equals(p.getPermission()))
                                                                                 && np.getPermissions()
                                                                                      .stream()
                                                                                      .noneMatch(p -> p.getIdentity()
                                                                                                       .getIdentityId() == 200L
                                                                                                      && "read".equals(p.getPermission()))),
                                                  eq(USER_IDENTITY_ID));
  }

  @Test
  public void favoriteDocumentCreatesFavorite() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));

    documentMcpTool.favoriteDocument(DOCUMENT_ID);

    // Ground truth: file favorites are stored under object type "file" (the JCR node
    // id), which is what the app's favorite button / drawer read. Not "document".
    verify(favoriteService).createFavorite(argThat(f -> "file".equals(f.getObjectType())
                                                        && DOCUMENT_ID.equals(f.getObjectId())
                                                        && f.getUserIdentityId() == USER_IDENTITY_ID));
  }

  @Test
  public void favoriteFolderIsRejected() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));

    // Folders have no favorite surface in the app; favoriting one must throw rather
    // than writing a "folder" favorite that nothing ever shows.
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.favoriteDocument(FOLDER_ID));
    verify(favoriteService, never()).createFavorite(any());
  }

  @Test
  public void unfavoriteFolderIsRejected() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));

    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.unfavoriteDocument(FOLDER_ID));
    verify(favoriteService, never()).deleteFavorite(any());
  }

  @Test
  public void favoriteDocumentAlreadyFavoriteFails() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    doThrow(new ObjectAlreadyExistsException(DOCUMENT_ID)).when(favoriteService).createFavorite(any());

    assertThrows(IllegalStateException.class, () -> documentMcpTool.favoriteDocument(DOCUMENT_ID));
  }

  @Test
  public void unfavoriteDocumentDeletesFavorite() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));

    documentMcpTool.unfavoriteDocument(DOCUMENT_ID);

    verify(favoriteService).deleteFavorite(any());
  }

  @Test
  public void unfavoriteDocumentNotFavoriteFails() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    doThrow(new ObjectNotFoundException("not a favorite")).when(favoriteService).deleteFavorite(any());

    assertThrows(IllegalStateException.class, () -> documentMcpTool.unfavoriteDocument(DOCUMENT_ID));
  }

  /**
   * The root folder of a space is found by the space's name, whatever its
   * case and surrounding whitespace, among the user's spaces, and read by the
   * id the name resolves to.
   * Mutants: the name ignored, or resolved to another space.
   *
   * @throws Exception never
   */
  @Test
  public void getRootFolderBySpaceName() throws Exception {
    memberOfLegalAndMarketing();
    when(documentFileService.getSpaceRootFolder(eq(6L), eq(currentIdentity))).thenReturn(folderNode(FOLDER_ID));

    assertEquals(FOLDER_ID, documentMcpTool.getRootFolderBySpace(null, "  legal ").getId());
    assertEquals(FOLDER_ID, documentMcpTool.getRootFolderBySpace(0L, "LEGAL").getId());
  }

  /**
   * A space given both by id and by name, or by neither, is refused before
   * any space is read.
   * Mutants: one of the two taken silently, or a missing space read as 0.
   */
  @Test
  public void getRootFolderBySpaceTakesExactlyOneOfIdAndName() throws Exception {
    memberOfLegalAndMarketing();

    IllegalArgumentException both = assertThrows(IllegalArgumentException.class,
                                                 () -> documentMcpTool.getRootFolderBySpace(6L, "Legal"));
    assertTrue(both.getMessage(), both.getMessage().contains("not both"));
    IllegalArgumentException neither = assertThrows(IllegalArgumentException.class,
                                                    () -> documentMcpTool.getRootFolderBySpace(null, " "));
    assertTrue(neither.getMessage(), neither.getMessage().contains("space_name"));
    verify(documentFileService, never()).getSpaceRootFolder(anyLong(), any());
  }

  /**
   * An unknown space id is answered with how to find the space: by its name,
   * or with get_my_spaces.
   * Mutants: the storage's bare message passed through.
   *
   * @throws Exception never
   */
  @Test
  public void getRootFolderBySpaceWithUnknownIdSaysHowToFindTheSpace() throws Exception {
    when(documentFileService.getSpaceRootFolder(eq(7L), eq(currentIdentity))).thenThrow(new ObjectNotFoundException("Space with id 7 doesn't exist"));

    ObjectNotFoundException e = assertThrows(ObjectNotFoundException.class, () -> documentMcpTool.getRootFolderBySpace(7L, null));

    assertEquals("Space with id 7 doesn't exist. Pass space_name (the space's name) or find it with get_my_spaces.", e.getMessage());
  }

  /**
   * A name of a space the user isn't a member of, or of no space, is refused
   * with the user's closest spaces, and no root folder is read.
   * Mutants: a space searched outside the user's spaces.
   */
  @Test
  public void getRootFolderBySpaceNameOutsideTheUsersSpacesIsRefused() throws Exception {
    memberOfLegalAndMarketing();

    ObjectNotFoundException e = assertThrows(ObjectNotFoundException.class,
                                             () -> documentMcpTool.getRootFolderBySpace(null, "Finance"));

    assertTrue(e.getMessage(), e.getMessage().contains("'Legal' (space_id 6)"));
    verify(documentFileService, never()).getSpaceRootFolder(anyLong(), any());
  }

  /**
   * In a call limited to a space, a name of that space is read, a name of
   * another space of the user is refused as the space scope refuses another
   * space's id, before any root folder is read, and a name is refused when
   * the call's space can't be read.
   * Mutants: the scope not compared, the comparison inverted, or an
   * unreadable scope taken as unlimited.
   *
   * @throws Exception never
   */
  @Test
  public void aCallLimitedToASpaceNamesThatSpaceOnly() throws Exception {
    memberOfLegalAndMarketing();
    when(documentFileService.getSpaceRootFolder(eq(6L), eq(currentIdentity))).thenReturn(folderNode(FOLDER_ID));
    scopeSpaceId = 6L;

    assertEquals(FOLDER_ID, documentMcpTool.getRootFolderBySpace(null, "Legal").getId());
    IllegalAccessException e = assertThrows(IllegalAccessException.class,
                                            () -> documentMcpTool.getRootFolderBySpace(null, "Marketing"));
    assertTrue(e.getMessage(), e.getMessage().contains("limited to the space with id 6"));
    assertThrows(IllegalAccessException.class,
                 () -> documentMcpTool.searchDocuments("report", null, "Marketing", null, null, null, null, null));
    verify(documentFileService, never()).getSpaceRootFolder(eq(5L), any());

    scopeSpaceId = null;
    scopeUnreadable = true;
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.getRootFolderBySpace(null, "Legal"));
  }

  /**
   * A search in a space given by its name searches that space's documents.
   * Mutants: the name ignored.
   *
   * @throws Exception never
   */
  @Test
  public void searchDocumentsBySpaceName() throws Exception {
    Space legal = memberOfLegalAndMarketing();
    when(spaceService.getSpaceById("6")).thenReturn(legal);
    when(spaceService.canViewSpace(eq(legal), eq(USERNAME))).thenReturn(true);
    when(identityManager.getOrCreateSpaceIdentity("legal")).thenReturn(new org.exoplatform.social.core.identity.model.Identity("306"));
    when(documentFileService.search(any(org.exoplatform.documents.model.DocumentTimelineFilter.class),
                                    eq(currentIdentity),
                                    anyInt(),
                                    anyInt())).thenReturn(List.of(fileNode(DOCUMENT_ID, "application/pdf")));

    assertEquals(1, documentMcpTool.searchDocuments("report", null, "legal", null, null, null, null, null).size());

    verify(documentFileService).search(argThat((org.exoplatform.documents.model.DocumentTimelineFilter filter) -> Long.valueOf(306L).equals(filter.getOwnerId())),
                                       eq(currentIdentity),
                                       anyInt(),
                                       anyInt());
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.searchDocuments("report", 6L, "legal", null, null, null, null, null));
  }

  /**
   * A document is shared with a space given by its name, and a name is one
   * more recipient: with a username or a space id, it is refused.
   * Mutants: the name ignored, or counted apart from the other recipients.
   *
   * @throws Exception never
   */
  @Test
  public void shareDocumentWithSpaceByName() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    Space legal = memberOfLegalAndMarketing();
    when(spaceService.getSpaceById("6")).thenReturn(legal);
    when(identityManager.getOrCreateSpaceIdentity("legal")).thenReturn(new org.exoplatform.social.core.identity.model.Identity("306"));

    documentMcpTool.shareDocument(DOCUMENT_ID, null, null, "Legal", false);

    verify(documentFileService).updatePermissions(eq(DOCUMENT_ID),
                                                  argThat((NodePermission np) -> np.getToShare().containsKey(306L)),
                                                  eq(USER_IDENTITY_ID));
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.shareDocument(DOCUMENT_ID, "bob", null, "Legal", null));
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.shareDocument(DOCUMENT_ID, null, 6L, "Legal", null));
  }

  /**
   * Makes the user a member of the spaces Legal (id 6) and Marketing (id 5).
   *
   * @return the Legal space
   */
  private Space memberOfLegalAndMarketing() {
    Space legal = new Space();
    legal.setId("6");
    legal.setDisplayName("Legal");
    legal.setPrettyName("legal");
    Space marketing = new Space();
    marketing.setId("5");
    marketing.setDisplayName("Marketing");
    marketing.setPrettyName("marketing");
    when(spaceService.getMemberSpaces(USERNAME)).thenReturn(SpaceNameResolverTest.listAccessOf(marketing, legal));
    return legal;
  }

  // ---------------------------------------------------------------------------
  // Reading a large document part by part, and its outline
  // ---------------------------------------------------------------------------

  /**
   * Builds a part of a document's text with an outline: "Intro" (level 1) at
   * 0, "Sub" (level 2) at 5, "Next" (level 1) at 12, over a 20-character
   * text.
   *
   * @param text the part's text
   * @param offset where it starts
   * @return the part
   */
  private static org.exoplatform.documents.model.DocumentTextPage textPage(String text, long offset) {
    return new org.exoplatform.documents.model.DocumentTextPage(text,
                                                               offset,
                                                               20,
                                                               false,
                                                               List.of(new org.exoplatform.documents.model.DocumentOutlineEntry(0, 1, "Intro", 0, null),
                                                                       new org.exoplatform.documents.model.DocumentOutlineEntry(1, 2, "Sub", 5, null),
                                                                       new org.exoplatform.documents.model.DocumentOutlineEntry(2, 1, "Next", 12, 3)),
                                                               DocumentTextContent.Status.EXTRACTED);
  }

  /**
   * read_document_text checks the user can read the document, then reads by
   * offset 40000 characters when no number is set, and gives where the next
   * part starts, none at the end of the text; a number past the largest is
   * brought back to it.
   * Mutants: the access check dropped, the default or the cap changed, the
   * next offset given at the end of the text.
   *
   * @throws Exception never
   */
  @Test
  public void readDocumentTextReadsByOffsetAndGivesTheNextOne() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getFileTextPage(DOCUMENT_ID, 0, DocumentMcpTool.DEFAULT_TEXT_PART_CHARS)).thenReturn(textPage("0123456789", 0));
    when(documentFileService.getFileTextPage(DOCUMENT_ID, 10, DocumentMcpTool.MAX_TEXT_PART_CHARS)).thenReturn(textPage("abcdefghij", 10));

    org.exoplatform.documents.mcp.model.DocumentTextPartModel first = documentMcpTool.readDocumentText(DOCUMENT_ID, null, null, null);
    org.exoplatform.documents.mcp.model.DocumentTextPartModel last = documentMcpTool.readDocumentText(DOCUMENT_ID, 10L, 999_999, null);

    assertEquals("0123456789", first.text());
    assertEquals(Long.valueOf(10), first.nextOffset());
    assertEquals(20, first.totalChars());
    assertEquals(20, last.endOffset());
    assertEquals(null, last.nextOffset());
    assertEquals(null, last.section());
    when(documentFileService.getDocumentById("ce0688cd7f00010134384ab7e1b15a49", USERNAME)).thenThrow(new IllegalAccessException("no"));
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.readDocumentText("ce0688cd7f00010134384ab7e1b15a49", null, null, null));
    verify(documentFileService, never()).getFileTextPage(eq("ce0688cd7f00010134384ab7e1b15a49"), anyLong(), anyInt());
  }

  /**
   * A section is read from its heading's offset up to the next heading of
   * the same or a higher level, a deeper one not ending it; the next offset it
   * gives, passed back unchanged, reads the rest of the section, and an offset
   * before the section starts at its heading; there is no next part past the
   * section's end.
   * Mutants: the section's start ignored, the offset counted from the
   * section's start, a deeper heading ending it, the read going past its end,
   * the next offset past it.
   *
   * @throws Exception never
   */
  @Test
  public void readDocumentTextReadsOneSectionUpToTheNextHeadingOfItsLevel() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    String text = "01234567890123456789";
    when(documentFileService.getFileTextPage(eq(DOCUMENT_ID), anyLong(), anyInt())).thenAnswer(invocation -> {
      int from = (int) Math.min((long) invocation.getArgument(1), text.length());
      int length = invocation.getArgument(2);
      return textPage(text.substring(from, Math.min(text.length(), from + length)), from);
    });

    org.exoplatform.documents.mcp.model.DocumentTextPartModel intro = documentMcpTool.readDocumentText(DOCUMENT_ID, 2L, 100, 0);
    org.exoplatform.documents.mcp.model.DocumentTextPartModel sub = documentMcpTool.readDocumentText(DOCUMENT_ID, null, 4, 1);
    org.exoplatform.documents.mcp.model.DocumentTextPartModel subEnd = documentMcpTool.readDocumentText(DOCUMENT_ID,
                                                                                                         sub.nextOffset(),
                                                                                                         100,
                                                                                                         1);
    org.exoplatform.documents.mcp.model.DocumentTextPartModel before = documentMcpTool.readDocumentText(DOCUMENT_ID, 1L, 4, 1);

    assertEquals("Intro", intro.section().title());
    assertEquals("2345678901", intro.text());
    assertEquals(12, intro.section().endOffset());
    assertEquals(null, intro.nextOffset());
    assertEquals("5678", sub.text());
    assertEquals(Long.valueOf(9), sub.nextOffset());
    assertEquals(12, sub.section().endOffset());
    assertEquals("901", subEnd.text());
    assertEquals(null, subEnd.nextOffset());
    assertEquals("5678", before.text());
  }

  /**
   * A section the outline doesn't have is refused with the number of
   * headings and the tool listing them.
   * Mutants: the bound check dropped or off by one.
   *
   * @throws Exception never
   */
  @Test
  public void readDocumentTextRefusesASectionTheOutlineDoesNotHave() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getFileTextPage(DOCUMENT_ID, 0, 0)).thenReturn(textPage("", 0));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                  () -> documentMcpTool.readDocumentText(DOCUMENT_ID, null, null, 3));
    assertTrue(error.getMessage().contains("3 headings"));
    assertTrue(error.getMessage().contains("get_document_outline"));
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.readDocumentText(DOCUMENT_ID, null, null, -1));
  }

  /**
   * get_document_outline lists the headings, each section ending at the
   * next heading of the same or a higher level or at the end of the text,
   * with its page when it has one.
   * Mutants: a section ended by a deeper heading, the last one not ending at
   * the text's end, the page dropped.
   *
   * @throws Exception never
   */
  @Test
  public void getDocumentOutlineListsTheHeadingsAndWhereTheirSectionsEnd() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getFileTextPage(DOCUMENT_ID, 0, 0)).thenReturn(textPage("", 0));

    org.exoplatform.documents.mcp.model.DocumentOutlineModel outline = documentMcpTool.getDocumentOutline(DOCUMENT_ID);

    assertEquals(20, outline.totalChars());
    assertEquals(List.of(12L, 12L, 20L), outline.headings().stream().map(h -> h.endOffset()).toList());
    assertEquals(Integer.valueOf(3), outline.headings().get(2).page());
  }

  /**
   * Two headings of one level starting at one offset, two bookmarks on one
   * PDF page, do not give the first an empty section: it ends at the next
   * heading starting after it.
   * Mutants: the section ended at a heading sharing its start.
   *
   * @throws Exception never
   */
  @Test
  public void getDocumentOutlineEndsASectionAtAHeadingStartingAfterIt() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getFileTextPage(DOCUMENT_ID, 0, 0))
        .thenReturn(new org.exoplatform.documents.model.DocumentTextPage("",
                                                                         0,
                                                                         20,
                                                                         false,
                                                                         List.of(new org.exoplatform.documents.model.DocumentOutlineEntry(0, 1, "One", 4, 2),
                                                                                 new org.exoplatform.documents.model.DocumentOutlineEntry(1, 1, "Two", 4, 2),
                                                                                 new org.exoplatform.documents.model.DocumentOutlineEntry(2, 1, "Three", 10, 3)),
                                                                         DocumentTextContent.Status.EXTRACTED));

    org.exoplatform.documents.mcp.model.DocumentOutlineModel outline = documentMcpTool.getDocumentOutline(DOCUMENT_ID);

    assertEquals(10, outline.headings().get(0).endOffset());
    assertEquals(10, outline.headings().get(1).endOffset());
    assertEquals(20, outline.headings().get(2).endOffset());
  }

  /**
   * A large document still being read, or waiting behind other large ones,
   * says to call again, one too large even for parts says so, and any other
   * reason is the one-shot tool's.
   * Mutants: the being-read answer worded as a failure or naming only this
   * document's reading, the reasons swapped.
   *
   * @throws Exception never
   */
  @Test
  public void readDocumentTextSaysWhyThereIsNoText() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getFileTextPage(eq(DOCUMENT_ID), anyLong(), anyInt()))
        .thenReturn(org.exoplatform.documents.model.DocumentTextPage.none(DocumentTextContent.Status.BUSY))
        .thenReturn(org.exoplatform.documents.model.DocumentTextPage.none(DocumentTextContent.Status.TOO_LARGE))
        .thenReturn(org.exoplatform.documents.model.DocumentTextPage.none(DocumentTextContent.Status.NO_TEXT));

    String busy = assertThrows(IllegalStateException.class,
                               () -> documentMcpTool.readDocumentText(DOCUMENT_ID, null, null, null)).getMessage();
    assertTrue(busy.contains("Call the same tool again"));
    assertTrue(busy.contains("or other large documents are"));
    assertTrue(assertThrows(IllegalStateException.class,
                            () -> documentMcpTool.getDocumentOutline(DOCUMENT_ID)).getMessage().contains("even part by part"));
    assertTrue(assertThrows(IllegalStateException.class,
                            () -> documentMcpTool.readDocumentText(DOCUMENT_ID, null, null, null)).getMessage()
                                                                                                    .contains("no text was found"));
  }

  /**
   * A file too large to be read in one go gives the first part read part by
   * part, with the text's length and the offset to read on; a text that fits
   * in that part comes whole, a cut one says so; a file too large even part
   * by part, or one still being read, says it.
   * Mutants: the refusal kept, the note dropped or with the wrong offset, a
   * part smaller than the largest, the cut not told, whether the whole text
   * fits in the part or not.
   *
   * @throws Exception never
   */
  @Test
  public void getDocumentContentByIdReadsTheFirstPartOfAFileTooLargeForOneGo() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getFileTextContent(DOCUMENT_ID)).thenReturn(DocumentTextContent.none(DocumentTextContent.Status.TOO_LARGE));
    when(documentFileService.getFileTextPage(DOCUMENT_ID, 0, DocumentMcpTool.MAX_TEXT_PART_CHARS))
        .thenReturn(new org.exoplatform.documents.model.DocumentTextPage("0123456789", 0, 500_000, false, List.of(), DocumentTextContent.Status.EXTRACTED))
        .thenReturn(new org.exoplatform.documents.model.DocumentTextPage("whole", 0, 5, false, List.of(), DocumentTextContent.Status.EXTRACTED))
        .thenReturn(new org.exoplatform.documents.model.DocumentTextPage("cut", 0, 3, true, List.of(), DocumentTextContent.Status.EXTRACTED))
        .thenReturn(new org.exoplatform.documents.model.DocumentTextPage("start", 0, 900, true, List.of(), DocumentTextContent.Status.EXTRACTED))
        .thenReturn(org.exoplatform.documents.model.DocumentTextPage.none(DocumentTextContent.Status.TOO_LARGE))
        .thenReturn(org.exoplatform.documents.model.DocumentTextPage.none(DocumentTextContent.Status.BUSY));

    String first = documentMcpTool.getDocumentContentById(DOCUMENT_ID);
    assertTrue(first.startsWith("0123456789\n\n["));
    assertTrue(first.contains("500000 characters long"));
    assertTrue(first.contains("read_document_text with offset=10"));
    assertEquals("whole", documentMcpTool.getDocumentContentById(DOCUMENT_ID));
    assertTrue(documentMcpTool.getDocumentContentById(DOCUMENT_ID).contains("it was cut at the most characters read"));
    String cutFirst = documentMcpTool.getDocumentContentById(DOCUMENT_ID);
    assertTrue(cutFirst.contains("900 characters long once cut at the most characters read"));
    assertTrue(cutFirst.contains("offset=5"));
    assertFalse(first.contains("once cut"));
    assertTrue(assertThrows(IllegalStateException.class,
                            () -> documentMcpTool.getDocumentContentById(DOCUMENT_ID)).getMessage().contains("even part by part"));
    assertTrue(assertThrows(IllegalStateException.class,
                            () -> documentMcpTool.getDocumentContentById(DOCUMENT_ID)).getMessage().contains("Call the same tool again"));
  }

  /**
   * The one-shot read says when its text was cut and where to read on.
   * Mutants: the cut not told, an offset given, the pointer dropped.
   *
   * @throws Exception never
   */
  @Test
  public void getDocumentContentByIdTellsACutTextAndPointsToThePartTools() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getFileTextContent(DOCUMENT_ID)).thenReturn(DocumentTextContent.truncated("0123456789"));

    String text = documentMcpTool.getDocumentContentById(DOCUMENT_ID);

    assertTrue(text.startsWith("0123456789"));
    assertTrue(text.contains("get_document_outline and read_document_text"));
    // this text's length is no position in the text read part by part, so none is given
    assertFalse(text.contains("offset 10"));
  }

  // ---------------------------------------------------------------------------
  // Rights on a document, and the documents changed since a date
  // ---------------------------------------------------------------------------

  /**
   * Builds a child of a folder last changed at the given time.
   *
   * @param id the child id
   * @param folder true for a folder, false for a file
   * @param modifiedDate the last change, in milliseconds
   * @return the child
   */
  private AbstractNode childChangedAt(String id, boolean folder, long modifiedDate) {
    AbstractNode node = folder ? folderNode(id) : fileNode(id, "application/pdf");
    node.setModifiedDate(modifiedDate);
    return node;
  }

  /**
   * Without a date, the page is the storage's, which pages over the listing
   * itself: the offset and the limit are passed through. A negative limit
   * asks nothing, the storage reading every child when given none.
   * Mutants: the offset or the limit not passed, the page cut again here, a
   * negative limit passed to the storage.
   *
   * @throws Exception never
   */
  @Test
  public void listFolderChildrenPassesThePageToTheStorage() throws Exception {
    when(documentFileService.getFolderChildNodes(any(), eq(2), eq(2), anyLong())).thenReturn(List.of(childChangedAt("c", false, 1L),
                                                                                                    childChangedAt("d", false, 1L)));

    List<DocumentModel> models = documentMcpTool.listFolderChildren(FOLDER_ID, 2, 2, null);

    assertEquals(List.of("c", "d"), models.stream().map(DocumentModel::getId).toList());
    assertEquals(0, documentMcpTool.listFolderChildren(FOLDER_ID, 0, -1, null).size());
    verify(documentFileService, times(1)).getFolderChildNodes(any(), anyInt(), anyInt(), anyLong());
  }

  /**
   * With updated_since, only the children changed since then are listed,
   * folders and files alike, the most recent first, the page cut after the
   * sort. A date alone, without a time, is the start of that day in UTC.
   * Mutants: the filter dropped or inverted, the sort dropped or reversed,
   * the page cut before the sort, the date read in another zone.
   *
   * @throws Exception never
   */
  @Test
  public void listFolderChildrenUpdatedSinceKeepsTheChangedOnesMostRecentFirst() throws Exception {
    long since = java.time.LocalDate.parse("2026-10-01").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
    when(documentFileService.getFolderChildNodes(any(), eq(0), eq(DocumentMcpTool.MAX_SCANNED_CHILDREN + 1), anyLong()))
        .thenReturn(List.of(childChangedAt("old-folder", true, since - 1),
                            childChangedAt("new-folder", true, since + 10),
                            childChangedAt("same-time", false, since),
                            childChangedAt("newest", false, since + 50)));

    List<DocumentModel> models = documentMcpTool.listFolderChildren(FOLDER_ID, null, 2, "2026-10-01");

    assertEquals(List.of("newest", "new-folder"), models.stream().map(DocumentModel::getId).toList());
  }

  /**
   * A folder holding more children than the scan reads is refused with
   * updated_since, rather than answered with part of its changes, and the
   * refusal names the search to use instead.
   * Mutants: the bound check dropped, or applied at the bound itself.
   *
   * @throws Exception never
   */
  @Test
  public void listFolderChildrenUpdatedSinceRefusesAFolderTooLargeToScan() throws Exception {
    List<AbstractNode> children = new java.util.ArrayList<>();
    for (int i = 0; i <= DocumentMcpTool.MAX_SCANNED_CHILDREN; i++) {
      children.add(childChangedAt("child-" + i, false, 1L));
    }
    when(documentFileService.getFolderChildNodes(any(), eq(0), eq(DocumentMcpTool.MAX_SCANNED_CHILDREN + 1), anyLong()))
        .thenReturn(children)
        .thenReturn(children.subList(0, DocumentMcpTool.MAX_SCANNED_CHILDREN));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                  () -> documentMcpTool.listFolderChildren(FOLDER_ID, null, null, "2026-10-01"));
    assertTrue(error.getMessage().contains("search_documents"));
    assertEquals(0, documentMcpTool.listFolderChildren(FOLDER_ID, null, null, "2026-10-01").size());
  }

  /**
   * A date the tool can't read is refused with the accepted forms, never
   * taken as no date; a date-time with an offset is read with it.
   * Mutants: an unreadable date ignored, the offset dropped.
   *
   * @throws Exception never
   */
  @Test
  public void updatedSinceIsReadAsAnIsoDateOrRefused() throws Exception {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                  () -> documentMcpTool.listFolderChildren(FOLDER_ID, null, null, "last week"));
    assertTrue(error.getMessage().contains("ISO-8601"));
    // a year too far for a date in milliseconds is refused the same way, not with an overflow
    for (String farAway : List.of("+999999999-12-31", "+999999999-12-31T00:00:00Z", "+999999999-12-31T00:00:00")) {
      IllegalArgumentException overflow = assertThrows(IllegalArgumentException.class,
                                                       () -> documentMcpTool.listFolderChildren(FOLDER_ID, null, null, farAway));
      assertTrue(overflow.getMessage().contains("ISO-8601"));
    }

    long since = java.time.OffsetDateTime.parse("2026-10-01T10:00:00+02:00").toInstant().toEpochMilli();
    when(documentFileService.getFolderChildNodes(any(), eq(0), eq(DocumentMcpTool.MAX_SCANNED_CHILDREN + 1), anyLong()))
        .thenReturn(List.of(childChangedAt("before", false, since - 1), childChangedAt("after", false, since)));
    assertEquals(List.of("after"),
                 documentMcpTool.listFolderChildren(FOLDER_ID, null, null, "2026-10-01T10:00:00+02:00")
                                .stream()
                                .map(DocumentModel::getId)
                                .toList());
  }

  /**
   * search_documents with updated_since and no query searches the files
   * changed since then, the most recent first; with neither, it is refused
   * before the search runs.
   * Mutants: the date not set on the filter, the sort not set or ascending,
   * the query still required, the refusal dropped.
   *
   * @throws Exception never
   */
  @Test
  public void searchDocumentsUpdatedSinceSearchesTheChangedFilesMostRecentFirst() throws Exception {
    long since = java.time.Instant.parse("2026-10-01T00:00:00Z").toEpochMilli();
    when(documentFileService.search(any(org.exoplatform.documents.model.DocumentTimelineFilter.class),
                                    eq(currentIdentity),
                                    anyInt(),
                                    anyInt())).thenReturn(List.of(fileNode(DOCUMENT_ID, "application/pdf")));

    assertEquals(1, documentMcpTool.searchDocuments(null, null, null, FOLDER_ID, null, null, null, "2026-10-01").size());

    verify(documentFileService).search(argThat((org.exoplatform.documents.model.DocumentTimelineFilter filter) -> Long.valueOf(since)
                                                                                                                       .equals(filter.getAfterDate())
        && filter.getSortField() == org.exoplatform.documents.constant.DocumentSortField.MODIFIED_DATE
        && !filter.isAscending()
        && FOLDER_ID.equals(filter.getParentFolderId())), eq(currentIdentity), anyInt(), anyInt());
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.searchDocuments(" ", null, null, FOLDER_ID, null, null, null, null));
    verify(documentFileService, Mockito.times(1)).search(any(org.exoplatform.documents.model.DocumentTimelineFilter.class),
                                                         any(),
                                                         anyInt(),
                                                         anyInt());
  }

  /**
   * The listed folders and files carry the rights the storage computed for
   * the user, the owner's being all of them as the repository grants, and a
   * folder its last change; a document whose rights weren't computed leaves
   * them unknown rather than false.
   * Mutants: the rights not copied, edit and delete swapped, unknown rights
   * reported as false, the owner's rights left to the entries, the folder's
   * date dropped.
   *
   * @throws Exception never
   */
  @Test
  public void listedDocumentsCarryTheUsersRightsAndTheFoldersLastChange() throws Exception {
    AbstractNode folder = childChangedAt(FOLDER_ID, true, 1_000L);
    folder.setAcl(new NodePermission(true, true, false, false, null, null, null, null));
    AbstractNode file = childChangedAt(DOCUMENT_ID, false, 1L);
    file.setAcl(new NodePermission(true, false, true, false, null, null, null, null));
    AbstractNode unknown = childChangedAt("unknown", false, 1L);
    AbstractNode owned = childChangedAt("owned", true, 1L);
    owned.setCreatorUserName(USERNAME);
    owned.setAcl(new NodePermission(true, false, false, false, null, null, null, null));
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong())).thenReturn(List.of(folder,
                                                                                                           file,
                                                                                                           unknown,
                                                                                                           owned));

    List<DocumentModel> models;
    // the creator's user card is not what this test checks
    try (MockedStatic<io.meeds.mcp.server.tool.util.UserToolUtils> users = Mockito.mockStatic(io.meeds.mcp.server.tool.util.UserToolUtils.class)) {
      models = documentMcpTool.listFolderChildren(FOLDER_ID, null, null, null);
    }

    assertEquals(Boolean.TRUE, models.get(0).getCanEdit());
    assertEquals(Boolean.FALSE, models.get(0).getCanDelete());
    assertNotNull(((DocumentFolderModel) models.get(0)).getUpdatedDate());
    assertEquals(Boolean.FALSE, models.get(1).getCanEdit());
    assertEquals(Boolean.TRUE, models.get(1).getCanDelete());
    assertEquals(null, models.get(2).getCanEdit());
    assertEquals(null, models.get(2).getCanDelete());
    // the repository grants every right to the owner, whatever the entries say
    assertEquals(Boolean.TRUE, models.get(3).getCanEdit());
    assertEquals(Boolean.TRUE, models.get(3).getCanDelete());
  }

  /**
   * A shortcut listed in a folder carries no rights, its own not being its
   * target's, and names its target, whose id the agent uses to add documents
   * to the shortcut's folder, asking get_document_permissions for its rights
   * there; the other children keep theirs. The same for
   * get_documents_by_folder_id, which lists the same children.
   * Mutants: the shortcut's own rights copied, the target not named, the
   * rights of a plain child dropped.
   *
   * @throws Exception never
   */
  @Test
  public void listedShortcutsCarryTheirTargetAndNoRights() throws Exception {
    AbstractNode shortcut = childChangedAt("shortcut-id", true, 1L);
    shortcut.setSourceID("target-id");
    shortcut.setCreatorUserName(USERNAME);
    shortcut.setAcl(new NodePermission(true, true, true, false, null, null, null, null));
    AbstractNode file = childChangedAt(DOCUMENT_ID, false, 1L);
    file.setAcl(new NodePermission(true, true, false, false, null, null, null, null));
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong())).thenReturn(List.of(shortcut, file));
    Mockito.<List<? extends AbstractNode>>when(documentFileService.getDocumentItems(any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean()))
           .thenReturn(List.of(shortcut, file));

    List<DocumentModel> listed;
    List<DocumentModel> byFolder;
    // the creator's user card is not what this test checks
    try (MockedStatic<io.meeds.mcp.server.tool.util.UserToolUtils> users = Mockito.mockStatic(io.meeds.mcp.server.tool.util.UserToolUtils.class)) {
      listed = documentMcpTool.listFolderChildren(FOLDER_ID, null, null, null);
      byFolder = documentMcpTool.getDocumentsByFolderId(FOLDER_ID, null, null, null, null);
    }

    for (List<DocumentModel> models : List.of(listed, byFolder)) {
      assertEquals("target-id", models.get(0).getShortcutTargetId());
      assertNull(models.get(0).getCanEdit());
      assertNull(models.get(0).getCanDelete());
      assertNull(models.get(1).getShortcutTargetId());
      assertEquals(Boolean.TRUE, models.get(1).getCanEdit());
      assertEquals(Boolean.FALSE, models.get(1).getCanDelete());
    }
  }

  /**
   * Every tool that writes in a folder through the storage's own node lookup
   * refuses a shortcut's id as that folder before writing anything, naming the
   * folder it points to and the argument to pass it as: create_folder,
   * create_document_from_template, move_document, copy_document and
   * create_document_shortcut. A shortcut's id resolves to its target for the
   * reads and the rights, while the storage would write under the shortcut
   * node itself. An upload files through a shortcut into its target:
   * {@link #aShortcutsRightIsItsTargetFolders}.
   * Mutants: the check dropped from any one of the tools, the argument not
   * named.
   *
   * @throws Exception never
   */
  @Test
  public void everyToolWritingInAFolderRefusesAFolderShortcutNamingItsTarget() throws Exception {
    when(documentFileService.getDocumentById("shortcut-id", USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.getRootFolderOwnerId(anyString())).thenReturn(OWNER_ID);

    assertRefusedAsAShortcut(() -> documentMcpTool.createFolder("shortcut-id", "New"), "parent_folder_id");
    assertRefusedAsAShortcut(() -> documentMcpTool.createFolder("shortcut-id", "A/B"), "parent_folder_id");
    assertRefusedAsAShortcut(() -> documentMcpTool.createDocumentFromTemplate("shortcut-id", "Q3 report", "docx"),
                             "parent_folder_id");
    assertRefusedAsAShortcut(() -> documentMcpTool.moveDocument(DOCUMENT_ID, "shortcut-id", null), "destination_folder_id");
    assertRefusedAsAShortcut(() -> documentMcpTool.copyDocument(DOCUMENT_ID, "shortcut-id"), "destination_folder_id");
    assertRefusedAsAShortcut(() -> documentMcpTool.createDocumentShortcut(DOCUMENT_ID, "shortcut-id", null),
                             "destination_folder_id");

    verify(documentFileService, never()).createFolder(anyLong(), any(), any(), any(), anyLong());
    verify(documentFileService, never()).getFolderChildNodes(any(), anyInt(), anyInt(), anyLong());
    verify(documentFileService, never()).createDocumentFromTemplate(anyLong(), any(), any(), any(), any(), anyLong());
    verify(documentFileService, never()).importFiles(any(), any(), any(), any(), any(), any(), anyLong());
    verify(documentFileService, never()).moveDocument(anyLong(), any(), any(), anyLong(), any());
    verify(documentFileService, never()).copyDocument(any(), any(), anyLong());
    verify(documentFileService, never()).createShortcut(any(), any(), any(), any());
  }

  /**
   * A shortcut to a file given as the folder a tool writes in is refused as no
   * folder, naming the file, never as a shortcut to a folder whose id the
   * agent would then pass.
   * Mutant: a shortcut to a file refused as a shortcut to a folder.
   *
   * @throws Exception never
   */
  @Test
  public void aFileShortcutGivenAsTheFolderIsRefusedAsNoFolder() throws Exception {
    when(documentFileService.getDocumentById("file-shortcut-id", USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                                  () -> documentMcpTool.moveDocument(DOCUMENT_ID, "file-shortcut-id", null));

    assertTrue(error.getMessage(), error.getMessage().contains("shortcut to the file " + DOCUMENT_ID + ", not a folder"));
    assertTrue(error.getMessage(), error.getMessage().contains("destination_folder_id"));
    assertFalse(error.getMessage(), error.getMessage().contains("shortcut to the folder"));
    verify(documentFileService, never()).moveDocument(anyLong(), any(), any(), anyLong(), any());
  }

  /**
   * A file's own id given as the folder a move writes in is refused as no
   * folder before the move reaches the storage. An upload into a file is
   * {@link #anUploadIntoAFileIsRefused}.
   * Mutant: a node whose id is the one given returned as the folder whatever
   * its kind.
   *
   * @throws Exception never
   */
  @Test
  public void aFileGivenAsTheFolderIsRefusedAsNoFolder() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));

    IllegalArgumentException move = assertThrows(IllegalArgumentException.class,
                                                 () -> documentMcpTool.moveDocument(DOCUMENT_ID, DOCUMENT_ID, null));

    assertTrue(move.getMessage(), move.getMessage().contains("The id " + DOCUMENT_ID + " is a file, not a folder"));
    assertTrue(move.getMessage(), move.getMessage().contains("destination_folder_id"));
    verify(documentFileService, never()).moveDocument(anyLong(), any(), any(), anyLong(), any());
  }

  /**
   * In a call limited to a space, a folder shortcut given as the destination
   * of a move, a copy or a link is refused as a shortcut, naming the folder to
   * pass, not as a destination outside the space: a shortcut node belongs to
   * no space drive.
   * Mutant: the space checked before the destination is resolved.
   *
   * @throws Exception never
   */
  @Test
  public void aCallLimitedToASpaceRefusesAFolderShortcutAsAShortcut() throws Exception {
    String shortcutId = "00000000000000000000000000000005";
    when(documentFileService.getDocumentById(shortcutId, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    lenient().when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    // reached only if the space were checked first: a shortcut node belongs to no space drive
    lenient().when(documentFileService.getDocumentSpaceGroupId(shortcutId)).thenReturn(null);
    scopeSpaceId = 12L;

    for (ThrowingRunnable call : List.<ThrowingRunnable> of(() -> documentMcpTool.moveDocument(DOCUMENT_ID, shortcutId, null),
                                                            () -> documentMcpTool.copyDocument(DOCUMENT_ID, shortcutId),
                                                            () -> documentMcpTool.createDocumentShortcut(DOCUMENT_ID,
                                                                                                         shortcutId,
                                                                                                         null))) {
      IllegalArgumentException error = assertThrows(IllegalArgumentException.class, call);
      assertTrue(error.getMessage(), error.getMessage().contains(shortcutId + " is a shortcut to the folder " + FOLDER_ID));
      assertTrue(error.getMessage(), error.getMessage().contains("destination_folder_id"));
    }
    scopeSpaceId = null;
  }

  /**
   * A file shortcut's id is still taken where a tool acts on a document, not
   * on a folder: a move of that shortcut into a folder goes on. A version
   * added through it is {@link #aFileShortcutIsVersionedAsItsTarget}.
   * Mutant: the folder check applied to the document a tool acts on.
   *
   * @throws Exception never
   */
  @Test
  public void aFileShortcutIsStillTakenAsTheDocumentAToolActsOn() throws Exception {
    when(documentFileService.getDocumentById("file-shortcut-id", USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(anyString())).thenReturn(OWNER_ID);

    documentMcpTool.moveDocument("file-shortcut-id", FOLDER_ID, null);

    verify(documentFileService).moveDocument(eq(OWNER_ID),
                                             eq("file-shortcut-id"),
                                             eq("/documents/Projects"),
                                             eq(USER_IDENTITY_ID),
                                             eq("keepBoth"));
  }

  /**
   * Asserts a tool call is refused as given a folder shortcut's id, naming the
   * folder it points to and the argument to pass it as.
   *
   * @param call the tool call
   * @param argumentName the argument the refusal names
   */
  private void assertRefusedAsAShortcut(ThrowingRunnable call, String argumentName) {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, call);
    assertTrue(error.getMessage(), error.getMessage().contains("shortcut-id is a shortcut to the folder " + FOLDER_ID));
    assertTrue(error.getMessage(), error.getMessage().contains(argumentName));
  }

  /**
   * get_document_permissions takes the edit right from the repository's
   * check for the user, not from the listing's computed flag, and the
   * delete right from the computed one.
   * Mutants: the edit right taken from the acl, the delete right dropped,
   * the folder flag dropped.
   *
   * @throws Exception never
   */
  @Test
  public void getDocumentPermissionsTellsTheEditRightTheRepositoryChecks() throws Exception {
    FolderNode folder = folderNode(FOLDER_ID);
    folder.setAcl(new NodePermission(true, true, true, false, null, null, null, null));
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folder);
    when(documentFileService.hasEditPermissionOnDocument(FOLDER_ID, USER_IDENTITY_ID)).thenReturn(false);

    org.exoplatform.documents.mcp.model.DocumentPermissionsModel permissions = documentMcpTool.getDocumentPermissions(FOLDER_ID);

    assertEquals(FOLDER_ID, permissions.getId());
    assertTrue(permissions.isFolder());
    assertTrue(permissions.isCanRead());
    assertFalse(permissions.isCanEdit());
    assertEquals(Boolean.TRUE, permissions.getCanDelete());
  }

  /**
   * get_document_permissions adds the owner's delete right as a listing
   * does, so both tell the same about a document the user owns whose entries
   * grant nothing.
   * Mutants: the owner's delete right left to the entries.
   *
   * @throws Exception never
   */
  @Test
  public void getDocumentPermissionsTellsTheOwnersDeleteRightAsAListingDoes() throws Exception {
    FileNode file = fileNode(DOCUMENT_ID, "application/pdf");
    file.setCreatorUserName(USERNAME);
    file.setAcl(new NodePermission(true, false, false, false, null, null, null, null));
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(file);
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);

    org.exoplatform.documents.mcp.model.DocumentPermissionsModel permissions = documentMcpTool.getDocumentPermissions(DOCUMENT_ID);

    assertTrue(permissions.isCanEdit());
    assertEquals(Boolean.TRUE, permissions.getCanDelete());
  }

  /**
   * A document added to a folder the user can only read is refused before
   * anything is staged or filed, with a reason naming the folder and the
   * tool that tells the rights.
   * Mutants: the check dropped, or run after the filing.
   *
   * @throws Exception never
   */
  @Test
  public void createDocumentInAReadOnlyFolderIsRefusedBeforeTheImport() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.hasEditPermissionOnDocument(FOLDER_ID, USER_IDENTITY_ID)).thenReturn(false);

    IllegalAccessException error = assertThrows(IllegalAccessException.class,
                                                () -> documentMcpTool.createDocument(FOLDER_ID, "notes.txt", "body"));

    assertTrue(error.getMessage().contains("get_document_permissions"));
    assertTrue(error.getMessage().contains("Projects"));
    verify(uploadService, never()).createUploadResource(any(org.exoplatform.upload.UploadResource.class));
    verify(documentFileService, never()).saveUploadedDocument(any(), any(), any(), any(), anyLong());
  }

  /**
   * get_document_permissions on a shortcut answers for the id asked, names
   * the target its rights are read on, checks the edit right on the target,
   * and leaves the delete right out, deleting the shortcut removing only the
   * shortcut.
   * Mutants: the target's id returned as the one asked, the edit right
   * checked on the shortcut, the target's delete right given.
   *
   * @throws Exception never
   */
  @Test
  public void getDocumentPermissionsOnAShortcutTellsTheTargetsEditRightAndNoDeleteRight() throws Exception {
    String shortcutId = "de0688cd7f00010134384ab7e1b15a40";
    FileNode target = fileNode(DOCUMENT_ID, "application/pdf");
    target.setAcl(new NodePermission(true, true, true, false, null, null, null, null));
    when(documentFileService.getDocumentById(shortcutId, USERNAME)).thenReturn(target);
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(target);
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);

    org.exoplatform.documents.mcp.model.DocumentPermissionsModel permissions = documentMcpTool.getDocumentPermissions(shortcutId);

    assertEquals(shortcutId, permissions.getId());
    assertEquals(DOCUMENT_ID, permissions.getShortcutTargetId());
    assertTrue(permissions.isCanEdit());
    assertEquals(null, permissions.getCanDelete());
    verify(documentFileService, never()).hasEditPermissionOnDocument(eq(shortcutId), anyLong());
    // get_document_by_id on the shortcut leaves its delete right out the same way
    assertEquals(null, documentMcpTool.getDocumentById(shortcutId).getCanDelete());
    assertEquals(Boolean.TRUE, documentMcpTool.getDocumentById(DOCUMENT_ID).getCanDelete());
  }

  private UploadService                  uploadService;

  /** The bytes of each upload filed, read when it is filed, by upload id. */
  private final Map<String, byte[]>      filedBytes   = new HashMap<>();

  /**
   * Stages the uploads the tool registers, and files each one as the given
   * document, keeping the bytes the staged file held when it was filed.
   *
   * @param document the document filed
   * @throws Exception never
   */
  private void fileUploadsAs(FileNode document) throws Exception {
    Map<String, String> locations = new HashMap<>();
    doAnswer(invocation -> {
      org.exoplatform.upload.UploadResource resource = invocation.getArgument(0);
      locations.put(resource.getUploadId(), resource.getStoreLocation());
      return null;
    }).when(uploadService).createUploadResource(any(org.exoplatform.upload.UploadResource.class));
    when(documentFileService.saveUploadedDocument(eq(FOLDER_ID), anyString(), anyString(), any(), eq(USER_IDENTITY_ID)))
        .thenAnswer(invocation -> {
          String uploadId = invocation.getArgument(1);
          filedBytes.put(uploadId, java.nio.file.Files.readAllBytes(java.nio.file.Path.of(locations.get(uploadId))));
          return document;
        });
  }

  /**
   * A text document is filed at once, as the Documents app files an upload,
   * under its name, keeping both files on a conflict, and the staged upload
   * is released.
   * Mutants: the text not the content, the name or the conflict action
   * changed, the upload not released.
   *
   * @throws Exception never
   */
  @Test
  public void createDocumentFilesTheTextAsAnUpload() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    fileUploadsAs(fileNodeNamed("doc-txt", "notes.txt"));

    DocumentModel model = documentMcpTool.createDocument(FOLDER_ID, "notes.txt", "plain text body");

    assertEquals("doc-txt", model.getId());
    ArgumentCaptor<String> uploadId = ArgumentCaptor.forClass(String.class);
    verify(documentFileService).saveUploadedDocument(eq(FOLDER_ID),
                                                     uploadId.capture(),
                                                     eq("notes.txt"),
                                                     eq(UploadConflictAction.KEEP_BOTH),
                                                     eq(USER_IDENTITY_ID));
    assertEquals("plain text body", new String(filedBytes.get(uploadId.getValue()), java.nio.charset.StandardCharsets.UTF_8));
    verify(uploadService).removeUploadResource(uploadId.getValue());
  }

  /**
   * A file over the upload size limit is refused before anything is staged,
   * the limit read from the property the upload forms read.
   * Mutants: the limit unchecked, or not read from the property.
   *
   * @throws Exception never
   */
  @Test
  public void aFileOverTheUploadLimitIsRefused() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    fileUploadsAs(fileNodeNamed("upl-big", "big.bin"));
    // an attached file, the source that carries a file of that size
    attachmentHolds("big.bin", new byte[1024 * 1024 + 1]);
    org.exoplatform.commons.utils.PropertyManager.setProperty(DocumentUploadLimit.PROPERTY_NAME, "1");
    try {
      assertTrue(assertThrows(IllegalArgumentException.class,
                              () -> documentMcpTool.uploadDocument(FOLDER_ID, "big.bin", null, null, "activity", "77", null, null)).getMessage()
                                                                                                                          .contains("1 MB"));
    } finally {
      org.exoplatform.commons.utils.PropertyManager.setProperty(DocumentUploadLimit.PROPERTY_NAME, "");
    }
    verify(uploadService, never()).createUploadResource(any(org.exoplatform.upload.UploadResource.class));
    verify(documentFileService, never()).saveUploadedDocument(any(), any(), any(), any(), anyLong());
  }

  /**
   * A file sent in pieces is filed by the piece that completes its size, into
   * the folder, under the name and with the conflict action its start
   * announced, the pieces joined in order; the staged upload is released, and
   * the last piece sent again returns the same document without filing it
   * again.
   * Mutants: a piece dropped or doubled, the folder, the name or the action
   * not the start's, the filing run early, twice or not at all, the upload
   * not released.
   *
   * @throws Exception never
   */
  @Test
  public void aFileSentInPiecesIsFiledByItsLastPiece() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    fileUploadsAs(fileNodeNamed("doc-xlsx", "grid.xlsx"));
    String content = java.util.Base64.getEncoder().encodeToString("0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    DocumentUploadModel upload = documentMcpTool.startDocumentUpload(FOLDER_ID, "grid.xlsx", 10L, "new_version");
    DocumentUploadModel first = documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), 0, content.substring(0, 8));

    assertNull(first.document());
    verify(documentFileService, never()).saveUploadedDocument(any(), any(), any(), any(), anyLong());

    DocumentUploadModel last = documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), 1, content.substring(8));

    assertEquals(10, last.receivedBytes());
    assertEquals(2, last.nextIndex());
    assertEquals("doc-xlsx", last.document().getId());
    ArgumentCaptor<String> uploadId = ArgumentCaptor.forClass(String.class);
    verify(documentFileService).saveUploadedDocument(eq(FOLDER_ID),
                                                     uploadId.capture(),
                                                     eq("grid.xlsx"),
                                                     eq(UploadConflictAction.NEW_VERSION),
                                                     eq(USER_IDENTITY_ID));
    assertEquals("0123456789", new String(filedBytes.get(uploadId.getValue()), java.nio.charset.StandardCharsets.UTF_8));
    verify(uploadService).removeUploadResource(uploadId.getValue());

    DocumentUploadModel again = documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), 1, content.substring(8));

    assertEquals("doc-xlsx", again.document().getId());
    verify(documentFileService).saveUploadedDocument(any(), any(), any(), any(), anyLong());
  }

  /**
   * An upload is refused at its start, before any piece is sent, into a
   * folder the user can only read, without its size, over the upload size
   * limit, with an unknown conflict action, or with a conflict action the
   * name's existing document can't take.
   * Mutants: any of those checks dropped.
   *
   * @throws Exception never
   */
  @Test
  public void anUploadIsRefusedAtItsStart() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));

    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.startDocumentUpload(FOLDER_ID, "grid.xlsx", null, null));
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.startDocumentUpload(FOLDER_ID, "grid.xlsx", DocumentUploadLimit.getMaxBytes() + 1, null));
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.startDocumentUpload(FOLDER_ID, "grid.xlsx", 10L, "rename"));
    // the handler's replacement deletes before it files: it is not offered
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.startDocumentUpload(FOLDER_ID, "grid.xlsx", 10L, "replace"));
    doThrow(new IllegalArgumentException("A folder is named 'grid.xlsx' in this folder")).when(documentFileService)
                                                                                          .checkUploadConflict(FOLDER_ID,
                                                                                                               "grid.xlsx",
                                                                                                               UploadConflictAction.NEW_VERSION,
                                                                                                               USER_IDENTITY_ID);
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.startDocumentUpload(FOLDER_ID, "grid.xlsx", 10L, "new_version"));
    assertEquals(0, documentMcpTool.getChunkedUploads().start(USERNAME, FOLDER_ID, "a.txt", 1, UploadConflictAction.KEEP_BOTH).nextIndex());
    when(documentFileService.hasEditPermissionOnDocument(FOLDER_ID, USER_IDENTITY_ID)).thenReturn(false);
    assertThrows(IllegalAccessException.class, () -> documentMcpTool.startDocumentUpload(FOLDER_ID, "grid.xlsx", 10L, null));
  }

  /**
   * A file that can't be filed ends its upload, its staged upload released,
   * and says so, whatever the refusal: a failure, a right lost before the
   * last piece, a name the conflict action can't take, or a folder gone.
   * Mutants: the upload kept after a failed filing, the upload not released,
   * the refusal swallowed.
   *
   * @throws Exception never
   */
  @Test
  public void aFileThatCantBeFiledEndsItsUpload() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.saveUploadedDocument(eq(FOLDER_ID), anyString(), anyString(), any(), eq(USER_IDENTITY_ID)))
        .thenThrow(new IllegalStateException("The file 'grid.xlsx' couldn't be filed"))
        .thenThrow(new IllegalAccessException("Permission to add a document to the folder is missing"))
        .thenThrow(new IllegalArgumentException("A folder is named 'grid.xlsx' in this folder"))
        .thenThrow(new ObjectNotFoundException("The folder folder-42 doesn't exist"));

    DocumentUploadModel upload = documentMcpTool.startDocumentUpload(FOLDER_ID, "grid.xlsx", 3L, null);
    IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                  () -> documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), 0, "AAAA"));

    assertTrue(failure.getMessage().contains("start it again"));
    verify(uploadService).removeUploadResource(anyString());
    assertTrue(assertThrows(IllegalArgumentException.class,
                            () -> documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), 0, "AAAA")).getMessage()
                                                                                                          .contains("No upload"));

    DocumentUploadModel refused = documentMcpTool.startDocumentUpload(FOLDER_ID, "grid.xlsx", 3L, null);
    assertTrue(assertThrows(IllegalAccessException.class,
                            () -> documentMcpTool.appendDocumentUploadChunk(refused.uploadId(), 0, "AAAA")).getMessage()
                                                                                                         .contains("start it again"));
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.appendDocumentUploadChunk(refused.uploadId(), 0, "AAAA"));

    DocumentUploadModel clashing = documentMcpTool.startDocumentUpload(FOLDER_ID, "grid.xlsx", 3L, null);
    assertTrue(assertThrows(IllegalArgumentException.class,
                            () -> documentMcpTool.appendDocumentUploadChunk(clashing.uploadId(), 0, "AAAA")).getMessage()
                                                                                                          .contains("start it again"));

    DocumentUploadModel folderGone = documentMcpTool.startDocumentUpload(FOLDER_ID, "grid.xlsx", 3L, null);
    String gone = assertThrows(IllegalStateException.class,
                               () -> documentMcpTool.appendDocumentUploadChunk(folderGone.uploadId(), 0, "AAAA")).getMessage();
    assertTrue(gone.contains("is gone") && gone.contains("start it again"));
  }

  /**
   * A piece over the most base64 characters of one call is refused, as is
   * a piece without its number, a piece cut inside a 4-character group, and
   * a piece that isn't base64; a data: prefix and white space are left out,
   * as upload_document does, and a padded last piece is taken.
   * Mutants: the piece's size unchecked, the group check dropped or refusing
   * a padded piece.
   *
   * @throws Exception never
   */
  @Test
  public void aPieceIsReadAsUploadDocumentReadsBase64() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    fileUploadsAs(fileNodeNamed("doc-bin", "big.bin"));
    DocumentUploadModel upload = documentMcpTool.startDocumentUpload(FOLDER_ID, "big.bin", 10L, null);

    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), 0, "A".repeat(DocumentMcpTool.MAX_CHUNK_CHARS + 4)));
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), null, "AAAA"));
    assertEquals(3, documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), 0, "data:application/octet-stream;base64,AA\nAA").receivedBytes());
    assertEquals(6, documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), 1, "data:text/plain,BBBB").receivedBytes());
    assertTrue(assertThrows(IllegalArgumentException.class,
                            () -> documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), 2, "aGVsbG")).getMessage()
                                                                                                          .contains("group of 4"));
    assertTrue(assertThrows(IllegalArgumentException.class,
                            () -> documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), 2, "a$b!")).getMessage()
                                                                                                        .contains("not valid base64"));
    assertEquals(9, documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), 2, "AAAA").receivedBytes());
    assertEquals("doc-bin", documentMcpTool.appendDocumentUploadChunk(upload.uploadId(), 3, "AA==").document().getId());
  }

  /**
   * A file's id given as the folder is refused before anything is staged or
   * sent, by the upload in pieces and by a file sent whole, with the tool that
   * adds a version named.
   * Mutants: the folder check dropped.
   *
   * @throws Exception never
   */
  @Test
  public void anUploadIntoAFileIsRefused() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    lenient().when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    String base64 = java.util.Base64.getEncoder().encodeToString("hello".getBytes());

    assertTrue(assertThrows(IllegalArgumentException.class,
                            () -> documentMcpTool.startDocumentUpload(DOCUMENT_ID, "a.pdf", 10L, null)).getMessage()
                                                                                                      .contains("add_document_version"));
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.uploadDocument(DOCUMENT_ID, "a.pdf", base64, null, null, null, null, null));
    verify(uploadService, never()).createUploadResource(any(org.exoplatform.upload.UploadResource.class));
    verify(documentFileService, never()).saveUploadedDocument(any(), any(), any(), any(), anyLong());
  }

  /**
   * A shortcut to a folder is written in as the folder it targets: the right
   * to add documents is checked on the target, not on the shortcut.
   * Mutants: the right checked on the id given.
   *
   * @throws Exception never
   */
  @Test
  public void aShortcutsRightIsItsTargetFolders() throws Exception {
    when(documentFileService.getDocumentById("shortcut-id", USERNAME)).thenReturn(folderNode("target-id"));
    when(documentFileService.hasEditPermissionOnDocument("target-id", USER_IDENTITY_ID)).thenReturn(true);

    DocumentUploadModel upload = documentMcpTool.startDocumentUpload("shortcut-id", "grid.xlsx", 10L, null);

    assertEquals("shortcut-id", upload.parentFolderId());
    verify(documentFileService, never()).hasEditPermissionOnDocument(eq("shortcut-id"), anyLong());
  }

  /**
   * A new version's content is checked by the upload validators as an
   * upload is, under the file's name and type, before it replaces the file's
   * content; a refusal stores nothing.
   * Mutants: the check dropped, or run after the version is stored.
   *
   * @throws Exception never
   */
  @Test
  public void aNewVersionsContentIsValidatedAsAnUpload() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "image/svg+xml"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    String svg = "<svg onload='alert(1)'/>";
    doThrow(new IllegalArgumentException("The file is refused: Infected file detected")).when(documentFileService)
                                                                                     .validateUploadedContent(anyString(),
                                                                                                              eq("image/svg+xml"),
                                                                                                              eq(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, svg, null, null, null, null, null, null));
    verify(documentFileService, never()).createNewVersion(any(), any(), any());
  }

  /**
   * A file shortcut is versioned as the file it targets: its right is read
   * on the target and the version is stored on the target.
   * Mutants: the right read, or the version stored, on the shortcut's id.
   *
   * @throws Exception never
   */
  @Test
  public void aFileShortcutIsVersionedAsItsTarget() throws Exception {
    when(documentFileService.getDocumentById("shortcut-id", USERNAME)).thenReturn(fileNode("target-id", "text/plain"));
    when(documentFileService.getDocumentById("target-id", USERNAME)).thenReturn(fileNode("target-id", "text/plain"));
    when(documentFileService.hasEditPermissionOnDocument("target-id", USER_IDENTITY_ID)).thenReturn(true);

    documentMcpTool.addDocumentVersion("shortcut-id", "new text", null, null, null, null, null, null);

    verify(documentFileService).createNewVersion(eq("target-id"), eq(USERNAME), any());
    verify(documentFileService, never()).hasEditPermissionOnDocument(eq("shortcut-id"), anyLong());
  }

  /**
   * A new version read from a chat attachment is capped at the upload size
   * limit as the other sources are, the attachment being read unbounded.
   * Mutants: the attachment's size unchecked.
   *
   * @throws Exception never
   */
  @Test
  public void aNewVersionFromAnAttachmentIsCapped() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    when(socialAttachmentService.getAttachmentFileIds(eq("activity"), eq("77"), eq(currentIdentity))).thenReturn(List.of("999"));
    org.exoplatform.commons.file.model.FileItem fileItem = Mockito.mock(org.exoplatform.commons.file.model.FileItem.class);
    org.exoplatform.commons.file.model.FileInfo fileInfo = Mockito.mock(org.exoplatform.commons.file.model.FileInfo.class);
    when(fileItem.getAsByte()).thenReturn(new byte[1024 * 1024 + 1]);
    when(fileItem.getFileInfo()).thenReturn(fileInfo);
    when(fileInfo.getMimetype()).thenReturn("application/pdf");
    when(fileInfo.getName()).thenReturn("big.pdf");
    when(fileService.getFile(999L)).thenReturn(fileItem);

    org.exoplatform.commons.utils.PropertyManager.setProperty(DocumentUploadLimit.PROPERTY_NAME, "1");
    try {
      assertThrows(IllegalArgumentException.class,
                   () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, "activity", "77", null, null));
    } finally {
      org.exoplatform.commons.utils.PropertyManager.setProperty(DocumentUploadLimit.PROPERTY_NAME, "");
    }
    verify(documentFileService, never()).createNewVersion(any(), any(), any());
  }

  /**
   * Makes the attachment of the activity 77 the given file, readable by the
   * current user.
   *
   * @param name the file's name
   * @param bytes the file's bytes
   * @throws Exception never
   */
  private void attachmentHolds(String name, byte[] bytes) throws Exception {
    when(socialAttachmentService.getAttachmentFileIds(eq("activity"), eq("77"), eq(currentIdentity))).thenReturn(List.of("999"));
    org.exoplatform.commons.file.model.FileItem fileItem = Mockito.mock(org.exoplatform.commons.file.model.FileItem.class);
    org.exoplatform.commons.file.model.FileInfo fileInfo = Mockito.mock(org.exoplatform.commons.file.model.FileInfo.class);
    when(fileItem.getAsByte()).thenReturn(bytes);
    when(fileItem.getFileInfo()).thenReturn(fileInfo);
    lenient().when(fileInfo.getMimetype()).thenReturn("application/octet-stream");
    lenient().when(fileInfo.getName()).thenReturn(name);
    when(fileService.getFile(999L)).thenReturn(fileItem);
  }

  /**
   * A base64 file longer than one call carries is refused before it is
   * decoded, by upload_document and by add_document_version, each naming the
   * tool that sends it in pieces; one of exactly that length is filed. The
   * refused base64 is not valid base64, so a decode would fail with another
   * message, and the decoder is watched not to be called.
   * In a call limited to a space, which is not offered those tools, the
   * refusal of a new version says so instead.
   * Mutants: the guard removed from either tool, moved after the decode, off
   * by one, a refusal that names no tool to send the file in pieces, or one
   * that names them in a call limited to a space.
   *
   * @throws Exception never
   */
  @Test
  public void aBase64FileLongerThanOneCallIsRefusedBeforeItIsDecoded() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    fileUploadsAs(fileNodeNamed("upl-max", "max.bin"));
    String tooLong = "!".repeat(DocumentMcpTool.CHUNK_CHARS + 1);
    String longest = "A".repeat(DocumentMcpTool.CHUNK_CHARS);

    try (MockedStatic<UploadToolUtils> utils = Mockito.mockStatic(UploadToolUtils.class, Mockito.CALLS_REAL_METHODS)) {
      String uploadRefusal = assertThrows(IllegalArgumentException.class,
                                          () -> documentMcpTool.uploadDocument(FOLDER_ID, "big.bin", tooLong, null, null, null, null, null))
                                                                                                                                 .getMessage();
      assertTrue(uploadRefusal, uploadRefusal.contains("at most " + DocumentMcpTool.CHUNK_CHARS));
      assertTrue(uploadRefusal, uploadRefusal.contains("start_document_upload"));
      assertTrue(uploadRefusal, uploadRefusal.contains("append_document_upload_chunk"));
      assertTrue(uploadRefusal, uploadRefusal.contains("start_upload"));

      String versionRefusal = assertThrows(IllegalArgumentException.class,
                                           () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, tooLong, null, null, null, null, null))
                                                                                                                                  .getMessage();
      assertTrue(versionRefusal, versionRefusal.contains("at most " + DocumentMcpTool.CHUNK_CHARS));
      assertTrue(versionRefusal, versionRefusal.contains("start_document_upload"));
      assertTrue(versionRefusal, versionRefusal.contains("on_conflict=new_version"));
      assertTrue(versionRefusal, versionRefusal.contains("start_upload"));

      // a call limited to a space isn't offered the tools that send a file in pieces
      scopeSpaceId = 12L;
      String scopedRefusal = assertThrows(IllegalArgumentException.class,
                                          () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, tooLong, null, null, null, null, null))
                                                                                                                                 .getMessage();
      scopeSpaceId = null;
      assertTrue(scopedRefusal, scopedRefusal.contains("at most " + DocumentMcpTool.CHUNK_CHARS));
      assertTrue(scopedRefusal, scopedRefusal.contains("limited to a space"));
      assertFalse(scopedRefusal, scopedRefusal.contains("start_document_upload"));
      assertFalse(scopedRefusal, scopedRefusal.contains("start_upload"));

      utils.verify(() -> UploadToolUtils.decodeBase64(anyString()), never());
      verify(uploadService, never()).createUploadResource(any(org.exoplatform.upload.UploadResource.class));
      verify(documentFileService, never()).createNewVersion(any(), any(), any());

      documentMcpTool.uploadDocument(FOLDER_ID, "max.bin", longest, null, null, null, null, null);
      documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, longest, null, null, null, null, null);
    }
    verify(documentFileService).saveUploadedDocument(eq(FOLDER_ID), anyString(), eq("max.bin"), any(), eq(USER_IDENTITY_ID));
    verify(documentFileService).createNewVersion(eq(DOCUMENT_ID), eq(USERNAME), any());
  }

  /**
   * A file is read from a URL up to 15 MB, by upload_document and by
   * add_document_version alike, or up to the upload size limit when it is
   * lower; the fetcher's refusal of a larger file is completed with what to
   * do instead, and its other refusals are passed as they are.
   * Mutants: either tool asking the upload size limit, the limit not 15 MB, a
   * lower upload size limit not applied, the refusal of a larger file not
   * completed, or another refusal completed.
   *
   * @throws Exception never
   */
  @Test
  public void aFileIsReadFromAUrlUpTo15MB() throws Exception {
    assertEquals(15L * 1024 * 1024, DocumentUploadLimit.URL_FETCH_MAX_BYTES);
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    fileUploadsAs(fileNodeNamed("upl-url", "report.pdf"));
    String url = "https://example.com/report.pdf";
    String bigUrl = "https://example.com/big.pdf";
    String privateUrl = "https://intranet.example/report.pdf";
    String tooLarge = "The file exceeds the maximum allowed size (15 MB).";
    String privateHost = "URL host is not allowed (it points to a private or internal address).";

    try (MockedStatic<UploadToolUtils> utils = Mockito.mockStatic(UploadToolUtils.class, Mockito.CALLS_REAL_METHODS)) {
      utils.when(() -> UploadToolUtils.fetchUrl(eq(url), anyLong(), any()))
           .thenReturn(new UploadToolUtils.FetchedContent("PDF".getBytes(), "application/pdf", "report.pdf"));
      utils.when(() -> UploadToolUtils.fetchUrl(eq(bigUrl), anyLong(), any())).thenThrow(new IllegalArgumentException(tooLarge));
      utils.when(() -> UploadToolUtils.fetchUrl(eq(privateUrl), anyLong(), any()))
           .thenThrow(new IllegalArgumentException(privateHost));

      documentMcpTool.uploadDocument(FOLDER_ID, "report.pdf", null, url, null, null, null, null);
      documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, url, null, null, null, null);
      utils.verify(() -> UploadToolUtils.fetchUrl(eq(url), eq(DocumentUploadLimit.URL_FETCH_MAX_BYTES), any()), Mockito.times(2));

      for (String refusal : List.of(assertThrows(IllegalArgumentException.class,
                                                 () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, bigUrl, null, null, null, null))
                                                                                                                                .getMessage(),
                                    assertThrows(IllegalArgumentException.class,
                                                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, bigUrl, null, null, null, null))
                                                                                                                                    .getMessage())) {
        assertTrue(refusal, refusal.startsWith(tooLarge));
        assertTrue(refusal, refusal.contains("up to 15 MB"));
        assertTrue(refusal, refusal.contains("start_upload"));
        assertTrue(refusal, refusal.contains("upload_id"));
      }
      assertEquals(privateHost,
                   assertThrows(IllegalArgumentException.class,
                                () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, privateUrl, null, null, null, null)).getMessage());

      // an upload size limit below 15 MB bounds the read, for a new version as for a new file
      org.exoplatform.commons.utils.PropertyManager.setProperty(DocumentUploadLimit.PROPERTY_NAME, "1");
      try {
        documentMcpTool.uploadDocument(FOLDER_ID, "report.pdf", null, url, null, null, null, null);
        documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, url, null, null, null, null);
        String refusal = assertThrows(IllegalArgumentException.class,
                                      () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, bigUrl, null, null, null, null))
                                                                                                                              .getMessage();
        assertTrue(refusal, refusal.contains("up to 1 MB"));
      } finally {
        org.exoplatform.commons.utils.PropertyManager.setProperty(DocumentUploadLimit.PROPERTY_NAME, "");
      }
      utils.verify(() -> UploadToolUtils.fetchUrl(eq(url), eq(1024L * 1024), any()), Mockito.times(2));
    }
  }

  /** The files the tests staged as uploads, deleted after each test. */
  private final List<java.nio.file.Path> stagedFiles  = new java.util.ArrayList<>();

  /**
   * An attached file whose stored size is over the upload size limit is
   * refused before its bytes are read, by both tools.
   * Mutants: the stored size unchecked, the file read first.
   *
   * @throws Exception never
   */
  @Test
  public void anAttachmentOverTheLimitIsRefusedBeforeItIsRead() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    when(socialAttachmentService.getAttachmentFileIds(eq("activity"), eq("77"), eq(currentIdentity))).thenReturn(List.of("999"));
    org.exoplatform.commons.file.model.FileItem fileItem = Mockito.mock(org.exoplatform.commons.file.model.FileItem.class);
    org.exoplatform.commons.file.model.FileInfo fileInfo = Mockito.mock(org.exoplatform.commons.file.model.FileInfo.class);
    when(fileItem.getFileInfo()).thenReturn(fileInfo);
    when(fileInfo.getSize()).thenReturn(1024L * 1024 + 1);
    when(fileService.getFile(999L)).thenReturn(fileItem);

    org.exoplatform.commons.utils.PropertyManager.setProperty(DocumentUploadLimit.PROPERTY_NAME, "1");
    try {
      assertTrue(assertThrows(IllegalArgumentException.class,
                              () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, "activity", "77", null, null)).getMessage()
                                                                                                                             .contains("1 MB"));
      assertThrows(IllegalArgumentException.class,
                   () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, "activity", "77", null, null));
    } finally {
      org.exoplatform.commons.utils.PropertyManager.setProperty(DocumentUploadLimit.PROPERTY_NAME, "");
    }
    verify(fileItem, never()).getAsByte();
  }

  // ---------------------------------------------------------------------------
  // A file sent through an upload link (upload_id)
  // ---------------------------------------------------------------------------

  /**
   * Stages one of the current user's uploads: its file on disk holds the
   * given bytes, and the owner-checked accessor answers it for the current
   * user only.
   *
   * @param uploadId the upload id
   * @param fileName the name it was uploaded under
   * @param bytes the file's bytes
   * @param status the upload's status
   * @return the staged file
   * @throws Exception never
   */
  private java.nio.file.Path stagedUpload(String uploadId, String fileName, byte[] bytes, int status) throws Exception {
    java.nio.file.Path file = java.nio.file.Files.createTempFile("upload", ".bin");
    stagedFiles.add(file);
    java.nio.file.Files.write(file, bytes);
    org.exoplatform.upload.UploadResource resource = new org.exoplatform.upload.UploadResource(uploadId);
    resource.setFileName(fileName);
    resource.setMimeType("application/octet-stream");
    resource.setStoreLocation(file.toString());
    resource.setStatus(status);
    when(uploadService.getUploadResource(uploadId, USERNAME)).thenReturn(resource);
    return file;
  }

  /**
   * Deletes the files the tests staged.
   *
   * @throws Exception never
   */
  @org.junit.After
  public void deleteStagedFiles() throws Exception {
    for (java.nio.file.Path file : stagedFiles) {
      java.nio.file.Files.deleteIfExists(file);
    }
  }

  /**
   * A file sent through an upload link is filed from its staged file, by its
   * upload id, under the name it was uploaded under or the name given, with
   * the conflict action given: the tool never reads it nor stages a copy, and
   * releases it through the owner-checked remove once filed. The upload is
   * read through the owner-checked accessor only, its id trimmed, and takes
   * the name it is filed under, which the filing types the file from.
   * Mutants: the id not trimmed, the upload left under its own name, the
   * upload id not handed to the filing, a copy staged, the
   * upload's name or the given name ignored, the conflict action dropped,
   * the remove dropped or not owner-checked, the unchecked accessor used.
   *
   * @throws Exception never
   */
  @Test
  public void anUploadIsFiledFromItsStagedFileWithoutACopy() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    Map<String, String> typedAs = new HashMap<>();
    when(documentFileService.saveUploadedDocument(eq(FOLDER_ID), anyString(), anyString(), any(), eq(USER_IDENTITY_ID)))
        .thenAnswer(invocation -> {
          String uploadId = invocation.getArgument(1);
          typedAs.put(uploadId, uploadService.getUploadResource(uploadId, USERNAME).getFileName());
          return fileNodeNamed("up-doc", "deck.pptx");
        });
    stagedUpload("up-1", "deck.pptx", "PPTX".getBytes(), org.exoplatform.upload.UploadResource.UPLOADED_STATUS);
    stagedUpload("up-2", "deck.pptx", "PPTX".getBytes(), org.exoplatform.upload.UploadResource.UPLOADED_STATUS);

    try (MockedStatic<UploadToolUtils> utils = Mockito.mockStatic(UploadToolUtils.class, Mockito.CALLS_REAL_METHODS)) {
      assertEquals("up-doc", documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, null, null, null, " up-1 ").getId());
      documentMcpTool.uploadDocument(FOLDER_ID, "Final deck.pdf", null, null, null, null, "new_version", "up-2");

      utils.verify(() -> UploadToolUtils.materialize(any(), any(), any(), any()), never());
      utils.verify(() -> UploadToolUtils.decodeBase64(any()), never());
    }
    verify(documentFileService).saveUploadedDocument(FOLDER_ID, "up-1", "deck.pptx", UploadConflictAction.KEEP_BOTH, USER_IDENTITY_ID);
    verify(documentFileService).saveUploadedDocument(FOLDER_ID,
                                                     "up-2",
                                                     "Final deck.pdf",
                                                     UploadConflictAction.NEW_VERSION,
                                                     USER_IDENTITY_ID);
    // the filing types the file from the upload's name, which must be the one it is filed under
    assertEquals(Map.of("up-1", "deck.pptx", "up-2", "Final deck.pdf"), typedAs);
    verify(uploadService, never()).createUploadResource(any(org.exoplatform.upload.UploadResource.class));
    verify(uploadService).removeUploadResource("up-1", USERNAME);
    verify(uploadService).removeUploadResource("up-2", USERNAME);
    verify(uploadService, never()).getUploadResource(anyString());
    verify(uploadService, never()).removeUploadResource(anyString());
  }

  /**
   * An upload id the owner-checked accessor doesn't answer for the current
   * user, unknown or another user's, is refused alike by both tools, naming
   * start_upload and get_upload, before anything is filed or versioned, and
   * nothing is removed: another user's upload stays theirs.
   * Mutants: the accessor's answer unchecked, the unchecked accessor used, a
   * refusal naming neither tool, something removed.
   *
   * @throws Exception never
   */
  @Test
  public void anUnknownOrAnotherUsersUploadIsRefused() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    org.exoplatform.upload.UploadResource theirs = new org.exoplatform.upload.UploadResource("theirs");
    theirs.setStatus(org.exoplatform.upload.UploadResource.UPLOADED_STATUS);
    lenient().when(uploadService.getUploadResource("theirs")).thenReturn(theirs);
    lenient().when(uploadService.getUploadResource("theirs", "mary")).thenReturn(theirs);

    for (String refusal : List.of(assertThrows(IllegalArgumentException.class,
                                               () -> documentMcpTool.uploadDocument(FOLDER_ID, "x.pdf", null, null, null, null, null, "theirs"))
                                                                                                                                    .getMessage(),
                                  assertThrows(IllegalArgumentException.class,
                                               () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, null, null, null, "theirs"))
                                                                                                                                         .getMessage(),
                                  assertThrows(IllegalArgumentException.class,
                                               () -> documentMcpTool.uploadDocument(FOLDER_ID, "x.pdf", null, null, null, null, null, "unknown"))
                                                                                                                                     .getMessage())) {
      assertTrue(refusal, refusal.contains("start_upload"));
      assertTrue(refusal, refusal.contains("get_upload"));
    }
    verify(documentFileService, never()).saveUploadedDocument(any(), any(), any(), any(), anyLong());
    verify(documentFileService, never()).createNewVersion(any(), any(), any());
    verify(uploadService, never()).removeUploadResource(anyString(), anyString());
    verify(uploadService, never()).removeUploadResource(anyString());
  }

  /**
   * An upload still being sent is refused and left in place for a later
   * call, removing it would delete a file still being written; one that
   * failed is refused and dropped; by both tools.
   * Mutants: the status unchecked, an upload being sent removed, a failed one
   * kept.
   *
   * @throws Exception never
   */
  @Test
  public void anUploadNotCompleteIsRefused() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    stagedUpload("sending", "x.pdf", "PDF".getBytes(), org.exoplatform.upload.UploadResource.UPLOADING_STATUS);
    stagedUpload("failed", "x.pdf", "PDF".getBytes(), org.exoplatform.upload.UploadResource.FAILED_STATUS);

    assertTrue(assertThrows(IllegalStateException.class,
                            () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, null, null, null, "sending")).getMessage()
                                                                                                                         .contains("get_upload"));
    assertThrows(IllegalStateException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, null, null, null, "sending"));
    verify(uploadService, never()).removeUploadResource(anyString(), anyString());

    assertTrue(assertThrows(IllegalStateException.class,
                            () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, null, null, null, "failed")).getMessage()
                                                                                                                        .contains("start_upload"));
    assertThrows(IllegalStateException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, null, null, null, "failed"));
    verify(uploadService, Mockito.times(2)).removeUploadResource("failed", USERNAME);
    verify(documentFileService, never()).saveUploadedDocument(any(), any(), any(), any(), anyLong());
    verify(documentFileService, never()).createNewVersion(any(), any(), any());
  }

  /**
   * An upload is capped at the upload size limit, measured on its staged
   * file, by both tools: one byte over is refused and dropped, the limit
   * itself is filed; an empty file is refused and dropped.
   * Mutants: the cap removed, off by one, not the upload limit, an empty
   * file filed, a refused upload kept.
   *
   * @throws Exception never
   */
  @Test
  public void anUploadOverTheLimitOrEmptyIsRefusedAndDropped() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    when(documentFileService.saveUploadedDocument(eq(FOLDER_ID), anyString(), anyString(), any(), eq(USER_IDENTITY_ID)))
        .thenReturn(fileNodeNamed("up-max", "max.bin"));
    int limit = 1024 * 1024;
    stagedUpload("over", "over.bin", new byte[limit + 1], org.exoplatform.upload.UploadResource.UPLOADED_STATUS);
    stagedUpload("over-v", "over.bin", new byte[limit + 1], org.exoplatform.upload.UploadResource.UPLOADED_STATUS);
    stagedUpload("max", "max.bin", new byte[limit], org.exoplatform.upload.UploadResource.UPLOADED_STATUS);
    stagedUpload("empty", "empty.bin", new byte[0], org.exoplatform.upload.UploadResource.UPLOADED_STATUS);

    org.exoplatform.commons.utils.PropertyManager.setProperty(DocumentUploadLimit.PROPERTY_NAME, "1");
    try {
      assertTrue(assertThrows(IllegalArgumentException.class,
                              () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, null, null, null, "over")).getMessage()
                                                                                                                         .contains("1 MB"));
      assertThrows(IllegalArgumentException.class,
                   () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, null, null, null, "over-v"));
      assertThrows(IllegalArgumentException.class,
                   () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, null, null, null, "empty"));
      verify(documentFileService, never()).saveUploadedDocument(any(), any(), any(), any(), anyLong());
      verify(documentFileService, never()).createNewVersion(any(), any(), any());

      documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, null, null, null, "max");
    } finally {
      org.exoplatform.commons.utils.PropertyManager.setProperty(DocumentUploadLimit.PROPERTY_NAME, "");
    }
    verify(documentFileService).saveUploadedDocument(FOLDER_ID, "max", "max.bin", UploadConflictAction.KEEP_BOTH, USER_IDENTITY_ID);
    for (String uploadId : List.of("over", "over-v", "max", "empty")) {
      verify(uploadService).removeUploadResource(uploadId, USERNAME);
    }
  }

  /**
   * An upload id is one source among the others, exclusive of each, for both
   * tools: given with another one, the call is refused before the upload is
   * read, and the upload is left in place.
   * Mutants: the upload id not counted as a source, or read before the
   * count.
   *
   * @throws Exception never
   */
  @Test
  public void anUploadIdIsOneSourceAmongTheOthers() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    String base64 = java.util.Base64.getEncoder().encodeToString("hello".getBytes());

    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, "x.txt", base64, null, null, null, null, "up-1"));
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, "x.txt", null, "https://example.com/x.txt", null, null, null, "up-1"));
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, "activity", "77", null, "up-1"));
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, "text", null, null, null, null, null, "up-1"));
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, base64, null, null, null, null, "up-1"));
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, "activity", "77", null, "up-1"));

    verify(uploadService, never()).getUploadResource(anyString(), anyString());
    verify(uploadService, never()).removeUploadResource(anyString(), anyString());
    verify(documentFileService, never()).saveUploadedDocument(any(), any(), any(), any(), anyLong());
    verify(documentFileService, never()).createNewVersion(any(), any(), any());
  }

  /**
   * An upload refused at its filing (a validator, a conflict, the storage) is
   * dropped all the same; a folder the user can't write in is refused before
   * the upload is read, which leaves it in place.
   * Mutants: the remove not in a finally, the folder checked after the read.
   *
   * @throws Exception never
   */
  @Test
  public void anUploadIsDroppedWhenItsFilingFails() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.saveUploadedDocument(eq(FOLDER_ID), anyString(), anyString(), any(), eq(USER_IDENTITY_ID)))
        .thenThrow(new IllegalArgumentException("The file 'x.svg' is refused: Infected file detected"))
        .thenThrow(new IllegalStateException("The file 'x.svg' couldn't be filed"));
    stagedUpload("refused", "x.svg", "<svg/>".getBytes(), org.exoplatform.upload.UploadResource.UPLOADED_STATUS);
    stagedUpload("broken", "x.svg", "<svg/>".getBytes(), org.exoplatform.upload.UploadResource.UPLOADED_STATUS);
    stagedUpload("kept", "x.svg", "<svg/>".getBytes(), org.exoplatform.upload.UploadResource.UPLOADED_STATUS);

    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, null, null, null, "refused"));
    assertThrows(IllegalStateException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, null, null, null, "broken"));
    verify(uploadService).removeUploadResource("refused", USERNAME);
    verify(uploadService).removeUploadResource("broken", USERNAME);

    when(documentFileService.hasEditPermissionOnDocument(eq(FOLDER_ID), anyLong())).thenReturn(false);
    assertThrows(IllegalAccessException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, null, null, null, "kept"));
    verify(uploadService, never()).getUploadResource("kept", USERNAME);
    verify(uploadService, never()).removeUploadResource("kept", USERNAME);
  }

  /**
   * A new version from an upload is checked by the upload validators on its
   * staged file, under the versioned file's name and type, then streamed
   * from that file into the version, never read into memory by the tool;
   * the upload is dropped once read, whether the version is added or
   * refused. A file the user can't edit is refused before the upload is
   * read.
   * Mutants: the staged file not validated or validated in memory, the
   * version given a byte array, another file streamed, the remove dropped,
   * the edit right checked after the read.
   *
   * @throws Exception never
   */
  @Test
  public void aNewVersionIsStreamedFromTheStagedUpload() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    when(documentFileService.getFileVersions(DOCUMENT_ID, USERNAME)).thenReturn(List.of(fileVersion()));
    java.nio.file.Path staged = stagedUpload("up-v", "new.pdf", "PDF v2".getBytes(), org.exoplatform.upload.UploadResource.UPLOADED_STATUS);
    List<String> streamed = new java.util.ArrayList<>();
    doAnswer(invocation -> {
      java.io.InputStream content = invocation.getArgument(2);
      assertFalse(content instanceof java.io.ByteArrayInputStream);
      streamed.add(new String(content.readAllBytes()));
      return null;
    }).when(documentFileService).createNewVersion(eq(DOCUMENT_ID), eq(USERNAME), any());

    documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, null, null, "from my machine", "up-v");

    assertEquals(List.of("PDF v2"), streamed);
    verify(documentFileService).validateUploadedFile("report.pdf", "application/pdf", staged);
    verify(documentFileService, never()).validateUploadedContent(any(), any(), any());
    verify(documentFileService).updateVersionSummary(DOCUMENT_ID, "version-1", "from my machine", USERNAME);
    verify(uploadService).removeUploadResource("up-v", USERNAME);

    java.nio.file.Path refused = stagedUpload("up-svg", "x.svg", "<svg/>".getBytes(), org.exoplatform.upload.UploadResource.UPLOADED_STATUS);
    doThrow(new IllegalArgumentException("The file 'report.pdf' is refused: Infected file detected")).when(documentFileService)
                                                                                                   .validateUploadedFile(any(),
                                                                                                                         any(),
                                                                                                                         eq(refused));
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, null, null, null, "up-svg"));
    verify(uploadService).removeUploadResource("up-svg", USERNAME);
    assertEquals(1, streamed.size());

    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(false);
    stagedUpload("kept", "x.pdf", "PDF".getBytes(), org.exoplatform.upload.UploadResource.UPLOADED_STATUS);
    assertThrows(IllegalAccessException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, null, null, null, "kept"));
    verify(uploadService, never()).getUploadResource("kept", USERNAME);
  }

  /**
   * In a call limited to a space, where start_upload is not offered, an
   * upload id is refused by both tools before the upload is read, and when
   * the call's space can't be read too; the upload is left in place. (The
   * server refuses the argument before the tool runs as well, its
   * declaration not accepting it: {@code DocumentToolDefinitionSpaceScopeTest}.)
   * Mutants: the scope unchecked, an unreadable scope taken as unlimited.
   *
   * @throws Exception never
   */
  @Test
  public void anUploadIdIsRefusedInACallLimitedToASpace() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "application/pdf"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    stagedUpload("up-1", "x.pdf", "PDF".getBytes(), org.exoplatform.upload.UploadResource.UPLOADED_STATUS);

    scopeSpaceId = 12L;
    assertTrue(assertThrows(IllegalAccessException.class,
                            () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, null, null, null, "up-1")).getMessage()
                                                                                                                             .contains("limited to a space"));
    assertThrows(IllegalAccessException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, null, null, null, "up-1"));
    scopeSpaceId = null;
    scopeUnreadable = true;
    assertThrows(IllegalAccessException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, null, null, null, "up-1"));
    scopeUnreadable = false;

    verify(uploadService, never()).getUploadResource(anyString(), anyString());
    verify(uploadService, never()).removeUploadResource(anyString(), anyString());
    verify(documentFileService, never()).createNewVersion(any(), any(), any());
    verify(documentFileService, never()).saveUploadedDocument(any(), any(), any(), any(), anyLong());

    documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, null, null, null, "up-1");
    verify(documentFileService).createNewVersion(eq(DOCUMENT_ID), eq(USERNAME), any());
  }

}
