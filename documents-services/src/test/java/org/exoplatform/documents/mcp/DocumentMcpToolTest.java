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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.mcp.model.DocumentFileModel;
import org.exoplatform.documents.mcp.model.DocumentFolderModel;
import org.exoplatform.documents.mcp.model.DocumentModel;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.FileNode;
import org.exoplatform.documents.model.FolderNode;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.attachments.service.AttachmentService;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.social.translation.service.TranslationService;

public class DocumentMcpToolTest {

  private static final String            USERNAME     = "testuser1";

  private static final long              USER_IDENTITY_ID = 100L;

  private static final long              SPACE_ID     = 3L;

  private static final String            DOCUMENT_ID  = "be0688cd7f00010134384ab7e1b15a48";

  private static final String            FOLDER_ID    = "folder-42";

  private DocumentFileService            documentFileService;

  private AttachmentService              attachmentService;

  private IdentityManager                identityManager;

  private SpaceService                   spaceService;

  private Identity                       currentIdentity;

  private DocumentMcpTool                documentMcpTool;

  @Before
  public void setUp() {
    documentFileService = Mockito.mock(DocumentFileService.class);
    attachmentService = Mockito.mock(AttachmentService.class);
    identityManager = Mockito.mock(IdentityManager.class);
    spaceService = Mockito.mock(SpaceService.class);
    TranslationService translationService = Mockito.mock(TranslationService.class);
    ProfilePropertyService profilePropertyService = Mockito.mock(ProfilePropertyService.class);
    UserACL userAcl = Mockito.mock(UserACL.class);
    UserPortalConfigService portalConfigService = Mockito.mock(UserPortalConfigService.class);
    currentIdentity = new Identity(USERNAME);

    org.exoplatform.social.core.identity.model.Identity socialIdentity =
                                                                       new org.exoplatform.social.core.identity.model.Identity(String.valueOf(USER_IDENTITY_ID));
    lenient().when(identityManager.getOrCreateUserIdentity(USERNAME)).thenReturn(socialIdentity);

    documentMcpTool = new DocumentMcpTool(documentFileService,
                                          attachmentService,
                                          identityManager,
                                          spaceService,
                                          translationService,
                                          profilePropertyService,
                                          userAcl,
                                          portalConfigService) {
      @Override
      public Identity getCurrentUserAclIdentity() {
        return currentIdentity;
      }

      @Override
      public Locale getCurrentUserLocale() {
        return Locale.ENGLISH;
      }
    };
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

    DocumentFolderModel model = documentMcpTool.getRootFolderBySpace(SPACE_ID);

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
    when(documentFileService.getFileContentAsText(DOCUMENT_ID)).thenReturn("Hello world");

    assertEquals("Hello world", documentMcpTool.getDocumentContentById(DOCUMENT_ID));
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
  public void getDocumentsByFolderId() throws Exception {
    List<AbstractNode> items = List.of(fileNode(DOCUMENT_ID, "application/pdf"), folderNode(FOLDER_ID));
    Mockito.<List<? extends AbstractNode>>when(documentFileService.getDocumentItems(any(), any(), anyInt(), anyInt(), anyLong(), anyBoolean()))
           .thenReturn(items);

    List<DocumentModel> models = documentMcpTool.getDocumentsByFolderId(FOLDER_ID, null, null, null, null);

    assertNotNull(models);
    assertEquals(2, models.size());
  }

  @Test
  public void searchDocuments() throws Exception {
    when(documentFileService.search(any(org.exoplatform.documents.model.DocumentTimelineFilter.class),
                                    eq(currentIdentity),
                                    anyInt(),
                                    anyInt())).thenReturn(List.of(fileNode(DOCUMENT_ID, "application/pdf")));

    List<DocumentFileModel> models = documentMcpTool.searchDocuments("report", null, null, null, null, null);

    assertNotNull(models);
    assertEquals(1, models.size());
    assertEquals(DOCUMENT_ID, models.get(0).getId());
  }

  @Test
  public void searchDocumentsWithUnknownSpaceFails() {
    when(spaceService.getSpaceById(String.valueOf(SPACE_ID))).thenReturn(null);

    assertThrows(ObjectNotFoundException.class,
                 () -> documentMcpTool.searchDocuments("report", SPACE_ID, null, null, null, null));
  }

  @Test
  public void searchDocumentsInSpaceNotViewableFails() {
    Space space = new Space();
    space.setId(String.valueOf(SPACE_ID));
    space.setPrettyName("engineering");
    when(spaceService.getSpaceById(String.valueOf(SPACE_ID))).thenReturn(space);
    when(spaceService.canViewSpace(eq(space), eq(USERNAME))).thenReturn(false);

    assertThrows(IllegalAccessException.class,
                 () -> documentMcpTool.searchDocuments("report", SPACE_ID, null, null, null, null));
  }

  @Test
  public void updateDocumentDescription() throws Exception {
    when(documentFileService.getRootFolderOwnerId(DOCUMENT_ID)).thenReturn(55L);

    documentMcpTool.updateDocumentDescription(DOCUMENT_ID, "<p>new</p>");

    verify(documentFileService).updateDocumentDescription(eq(55L), eq(DOCUMENT_ID), eq("<p>new</p>"), eq(USER_IDENTITY_ID));
  }

  @Test
  public void attachDocumentToContent() throws Exception {
    documentMcpTool.attachDocumentToContent(DOCUMENT_ID, "activity", 77L);

    verify(attachmentService).linkAttachmentToEntity(eq(USER_IDENTITY_ID), eq(77L), eq("activity"), eq(DOCUMENT_ID));
  }

}
