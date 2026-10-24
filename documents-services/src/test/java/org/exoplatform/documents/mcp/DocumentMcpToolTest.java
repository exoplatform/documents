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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
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
  public void setUp() {
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
    UploadService uploadService = Mockito.mock(UploadService.class);
    publicDocumentAccessService = Mockito.mock(PublicDocumentAccessService.class);
    favoriteService = Mockito.mock(FavoriteService.class);
    currentIdentity = new Identity(USERNAME);

    org.exoplatform.social.core.identity.model.Identity socialIdentity =
                                                                       new org.exoplatform.social.core.identity.model.Identity(String.valueOf(USER_IDENTITY_ID));
    lenient().when(identityManager.getOrCreateUserIdentity(USERNAME)).thenReturn(socialIdentity);

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
    assertNoTextMessage(DocumentTextContent.Status.TOO_LARGE, "the file is too large for its text to be read.");
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

    List<DocumentFileModel> models = documentMcpTool.searchDocuments("report", null, null, null, null, null, null);

    assertNotNull(models);
    assertEquals(1, models.size());
    assertEquals(DOCUMENT_ID, models.get(0).getId());
  }

  @Test
  public void searchDocumentsWithUnknownSpaceFails() {
    when(spaceService.getSpaceById(String.valueOf(SPACE_ID))).thenReturn(null);

    assertThrows(ObjectNotFoundException.class,
                 () -> documentMcpTool.searchDocuments("report", SPACE_ID, null, null, null, null, null));
  }

  @Test
  public void searchDocumentsInSpaceNotViewableFails() {
    Space space = new Space();
    space.setId(String.valueOf(SPACE_ID));
    space.setPrettyName("engineering");
    when(spaceService.getSpaceById(String.valueOf(SPACE_ID))).thenReturn(space);
    when(spaceService.canViewSpace(eq(space), eq(USERNAME))).thenReturn(false);

    assertThrows(IllegalAccessException.class,
                 () -> documentMcpTool.searchDocuments("report", SPACE_ID, null, null, null, null, null));
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

    List<DocumentModel> models = documentMcpTool.listFolderChildren(FOLDER_ID, null, null);

    assertNotNull(models);
    assertEquals(2, models.size());
  }

  @Test
  public void listFolderChildrenBlankFolderFails() {
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.listFolderChildren(" ", null, null));
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
  public void createDocumentTextExtensionSucceeds() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    // First call = pre-import snapshot (empty), second call = after import (the new file).
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(Collections.emptyList())
        .thenReturn(List.of(fileNodeNamed("doc-txt", "notes.txt")));

    // The name is used as-is (it already carries a .txt extension).
    DocumentModel model = documentMcpTool.createDocument(FOLDER_ID, "notes.txt", "plain text body");

    assertNotNull(model);
    assertEquals("doc-txt", model.getId());
    // folderPath must be null (parent resolved from the folder id) and conflict
    // "duplicate" (NOT "rename": the async import thread only understands
    // "updateAll"/"duplicate", so "rename" silently creates nothing on a clash).
    verify(documentFileService).importFiles(eq(String.valueOf(OWNER_ID)),
                                            eq(FOLDER_ID),
                                            isNull(),
                                            any(),
                                            eq("duplicate"),
                                            eq(currentIdentity),
                                            eq(USER_IDENTITY_ID));
  }

  @Test
  public void createDocumentResolvesByNameIgnoringConcurrentDifferentFile() throws Exception {
    // Concurrency on the import/create path: while "notes.txt" is being created,
    // another user drops a DIFFERENT-named file ("other.txt") into the same folder.
    // The name-aware resolver must return the created "notes.txt", not the last
    // added id ("other.txt") that the old reduce((a,b)->b) heuristic would pick.
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(Collections.emptyList())
        .thenReturn(List.of(fileNodeNamed("doc-txt", "notes.txt"), fileNodeNamed("concurrent-1", "other.txt")));

    DocumentModel model = documentMcpTool.createDocument(FOLDER_ID, "notes.txt", "plain text body");

    assertNotNull(model);
    assertEquals("doc-txt", model.getId());
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

  @Test
  public void uploadDocumentFromBase64() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(Collections.emptyList())
        .thenReturn(List.of(fileNodeNamed("upl-1", "hello.txt")));
    String base64 = java.util.Base64.getEncoder().encodeToString("hello".getBytes());

    DocumentModel model = documentMcpTool.uploadDocument(FOLDER_ID, "hello.txt", base64, null, null, null);

    assertNotNull(model);
    assertEquals("upl-1", model.getId());
    verify(documentFileService).importFiles(eq(String.valueOf(OWNER_ID)),
                                            eq(FOLDER_ID),
                                            isNull(),
                                            any(),
                                            eq("duplicate"),
                                            eq(currentIdentity),
                                            eq(USER_IDENTITY_ID));
  }

  @Test
  public void uploadDocumentFromChatAttachment() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));
    when(documentFileService.getRootFolderOwnerId(FOLDER_ID)).thenReturn(OWNER_ID);
    when(documentFileService.getFolderChildNodes(any(), anyInt(), anyInt(), anyLong()))
        .thenReturn(Collections.emptyList())
        .thenReturn(List.of(fileNodeNamed("upl-att", "screenshot.png")));
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
    DocumentModel model = documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, "activity", "77");

    assertNotNull(model);
    assertEquals("upl-att", model.getId());
    verify(documentFileService).importFiles(eq(String.valueOf(OWNER_ID)),
                                            eq(FOLDER_ID),
                                            isNull(),
                                            any(),
                                            eq("duplicate"),
                                            eq(currentIdentity),
                                            eq(USER_IDENTITY_ID));
  }

  @Test
  public void uploadDocumentUnknownAttachmentFails() throws Exception {
    when(socialAttachmentService.getAttachmentFileIds(eq("activity"), eq("77"), eq(currentIdentity)))
        .thenReturn(java.util.Collections.emptyList());

    assertThrows(ObjectNotFoundException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, null, null, null, "activity", "77"));
  }

  @Test
  public void uploadDocumentRequiresExactlyOneSource() {
    String base64 = java.util.Base64.getEncoder().encodeToString("hello".getBytes());
    // no source at all
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.uploadDocument(FOLDER_ID, "x.txt", null, null, null, null));
    // both base64 and url
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, "x.txt", base64, "https://example.com/x.txt", null, null));
    // both an attachment and base64
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.uploadDocument(FOLDER_ID, "x.txt", base64, null, "activity", "77"));
  }

  @Test
  public void uploadDocumentBase64RequiresName() {
    String base64 = java.util.Base64.getEncoder().encodeToString("hello".getBytes());
    assertThrows(IllegalArgumentException.class, () -> documentMcpTool.uploadDocument(FOLDER_ID, " ", base64, null, null, null));
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

    DocumentModel model = documentMcpTool.addDocumentVersion(DOCUMENT_ID, "updated body", null, null, null, null, null);

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

    documentMcpTool.addDocumentVersion(DOCUMENT_ID, "updated body", null, null, null, null, "reworded intro");

    verify(documentFileService).createNewVersion(eq(DOCUMENT_ID), eq(USERNAME), any());
    verify(documentFileService).updateVersionSummary(eq(DOCUMENT_ID), eq("version-1"), eq("reworded intro"), eq(USERNAME));
  }

  /**
   * A new version's inline content, text or base64, is capped at the size the
   * url and the chat attachment are capped at: one byte over is refused before
   * anything is stored, the limit itself is accepted.
   * Mutants: the cap removed, applied to the text or to the base64 only, or
   * off by one.
   *
   * @throws Exception never
   */
  @Test
  public void aNewVersionsInlineContentIsCapped() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);
    int limit = (int) UploadToolUtils.DEFAULT_MAX_BYTES;
    String overText = "a".repeat(limit + 1);
    String overBase64 = java.util.Base64.getEncoder().encodeToString(new byte[limit + 1]);

    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, overText, null, null, null, null, null));
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, overBase64, null, null, null, null));
    verify(documentFileService, never()).createNewVersion(any(), any(), any());

    documentMcpTool.addDocumentVersion(DOCUMENT_ID, "a".repeat(limit), null, null, null, null, null);
    verify(documentFileService).createNewVersion(eq(DOCUMENT_ID), eq(USERNAME), any());
  }

  @Test
  public void addDocumentVersionNoSourceFails() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(true);

    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, null, null, null, null, null, null));
  }

  @Test
  public void addDocumentVersionOnFolderFails() throws Exception {
    when(documentFileService.getDocumentById(FOLDER_ID, USERNAME)).thenReturn(folderNode(FOLDER_ID));

    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.addDocumentVersion(FOLDER_ID, "body", null, null, null, null, null));
  }

  @Test
  public void addDocumentVersionWithoutEditPermissionFails() throws Exception {
    when(documentFileService.getDocumentById(DOCUMENT_ID, USERNAME)).thenReturn(fileNode(DOCUMENT_ID, "text/plain"));
    when(documentFileService.hasEditPermissionOnDocument(DOCUMENT_ID, USER_IDENTITY_ID)).thenReturn(false);

    assertThrows(IllegalAccessException.class,
                 () -> documentMcpTool.addDocumentVersion(DOCUMENT_ID, "body", null, null, null, null, null));
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
                 () -> documentMcpTool.searchDocuments("report", null, "Marketing", null, null, null, null));
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

    assertEquals(1, documentMcpTool.searchDocuments("report", null, "legal", null, null, null, null).size());

    verify(documentFileService).search(argThat((org.exoplatform.documents.model.DocumentTimelineFilter filter) -> Long.valueOf(306L).equals(filter.getOwnerId())),
                                       eq(currentIdentity),
                                       anyInt(),
                                       anyInt());
    assertThrows(IllegalArgumentException.class,
                 () -> documentMcpTool.searchDocuments("report", 6L, "legal", null, null, null, null));
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

}
