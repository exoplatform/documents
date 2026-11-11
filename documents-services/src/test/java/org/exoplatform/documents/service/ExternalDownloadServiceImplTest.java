package org.exoplatform.documents.service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.PublicDocumentAccessStorage;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

public class ExternalDownloadServiceImplTest {

  private static final String         FOLDER_ID  = "123";

  private static final long           CREATOR_ID = 5L;

  private DocumentFileStorage         documentFileStorage;

  private PublicDocumentAccessStorage publicDocumentAccessStorage;

  private DocumentFileService         documentFileService;

  private IdentityManager             identityManager;

  private ExternalDownloadService     externalDownloadService;

  @Before
  public void setUp() throws Exception {
    documentFileStorage = mock(DocumentFileStorage.class);
    publicDocumentAccessStorage = mock(PublicDocumentAccessStorage.class);
    documentFileService = mock(DocumentFileService.class);
    identityManager = mock(IdentityManager.class);
    externalDownloadService = new ExternalDownloadServiceImpl(documentFileStorage,
                                                              publicDocumentAccessStorage,
                                                              documentFileService,
                                                              identityManager);
  }

  @Test
  public void getDocumentDownloadItem() {
    Throwable exception = assertThrows(IllegalArgumentException.class,
                                       () -> this.externalDownloadService.getDocumentDownloadItem(null));
    assertEquals("document id is mandatory", exception.getMessage());
    externalDownloadService.getDocumentDownloadItem(FOLDER_ID);
    verify(documentFileStorage, times(1)).getDocumentDownloadItem(FOLDER_ID);
  }

  /**
   * The folder is walked with the rights of the link's creator, resolved when
   * the download runs (EXO-91530).
   */
  @Test
  public void downloadZippedFolderWalksWithTheCreatorRights() throws Exception {
    org.exoplatform.services.security.Identity creatorAclIdentity = mockLinkCreator(true, false);
    java.nio.file.Path zip = Files.createTempFile("test", "test.zip");
    Files.write(zip, new byte[] { 1, 2, 3 });
    when(documentFileStorage.downloadFolder(FOLDER_ID, creatorAclIdentity)).thenReturn(zip.toString());

    try (java.io.InputStream folderStream = externalDownloadService.downloadZippedFolder(FOLDER_ID)) {
      assertArrayEquals(new byte[] { 1, 2, 3 }, folderStream.readAllBytes());
      assertTrue("the zip is read from its file while it is sent", Files.exists(zip));
    }
    verify(documentFileStorage).downloadFolder(FOLDER_ID, creatorAclIdentity);
    assertFalse("the zip is deleted once its stream is closed", Files.exists(zip));
  }

  @Test
  public void downloadZippedFolderOfALinkWithoutCreatorIsNotFound() throws Exception {
    PublicDocumentAccess legacyLink = new PublicDocumentAccess(1L, FOLDER_ID, null, null, null);
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId(FOLDER_ID)).thenReturn(legacyLink);

    assertThrows(ObjectNotFoundException.class, () -> externalDownloadService.downloadZippedFolder(FOLDER_ID));
    verify(documentFileStorage, never()).downloadFolder(anyString(), nullable(org.exoplatform.services.security.Identity.class));
  }

  @Test
  public void downloadZippedFolderWithoutLinkIsNotFound() throws Exception {
    assertThrows(ObjectNotFoundException.class, () -> externalDownloadService.downloadZippedFolder(FOLDER_ID));
    verify(documentFileStorage, never()).downloadFolder(anyString(), nullable(org.exoplatform.services.security.Identity.class));
  }

  @Test
  public void downloadZippedFolderOfAnUnknownCreatorIsNotFound() throws Exception {
    mockLinkCreator(true, false);
    when(identityManager.getIdentity(String.valueOf(CREATOR_ID))).thenReturn(null);

    assertThrows(ObjectNotFoundException.class, () -> externalDownloadService.downloadZippedFolder(FOLDER_ID));
    verify(documentFileStorage, never()).downloadFolder(anyString(), nullable(org.exoplatform.services.security.Identity.class));
  }

  @Test
  public void downloadZippedFolderOfADisabledCreatorIsNotFound() throws Exception {
    mockLinkCreator(false, false);

    assertThrows(ObjectNotFoundException.class, () -> externalDownloadService.downloadZippedFolder(FOLDER_ID));
    verify(documentFileStorage, never()).downloadFolder(anyString(), nullable(org.exoplatform.services.security.Identity.class));
  }

  @Test
  public void downloadZippedFolderOfADeletedCreatorIsNotFound() throws Exception {
    mockLinkCreator(true, true);

    assertThrows(ObjectNotFoundException.class, () -> externalDownloadService.downloadZippedFolder(FOLDER_ID));
    verify(documentFileStorage, never()).downloadFolder(anyString(), nullable(org.exoplatform.services.security.Identity.class));
  }

  @Test
  public void downloadZippedFolderWhoseCreatorRightsCannotBeResolvedIsNotFound() throws Exception {
    mockLinkCreator(true, false);
    when(documentFileService.getAclUserIdentity("mary")).thenThrow(IllegalAccessException.class);

    assertThrows(ObjectNotFoundException.class, () -> externalDownloadService.downloadZippedFolder(FOLDER_ID));
    verify(documentFileStorage, never()).downloadFolder(anyString(), nullable(org.exoplatform.services.security.Identity.class));
  }

  @Test
  public void downloadZippedFolderUnreadableByTheCreatorIsNotFound() throws Exception {
    org.exoplatform.services.security.Identity creatorAclIdentity = mockLinkCreator(true, false);
    when(documentFileStorage.downloadFolder(FOLDER_ID, creatorAclIdentity)).thenThrow(ObjectNotFoundException.class);

    assertThrows(ObjectNotFoundException.class, () -> externalDownloadService.downloadZippedFolder(FOLDER_ID));
  }

  @Test
  public void downloadZippedFolderThatCannotBeBuiltIsAnIOError() throws Exception {
    mockLinkCreator(true, false);
    when(documentFileStorage.downloadFolder(any(), any())).thenReturn(null);

    assertThrows(IOException.class, () -> externalDownloadService.downloadZippedFolder(FOLDER_ID));
  }

  private org.exoplatform.services.security.Identity mockLinkCreator(boolean enabled, boolean deleted) throws Exception {
    PublicDocumentAccess link = new PublicDocumentAccess(1L, FOLDER_ID, null, null, null);
    link.setCreatorId(CREATOR_ID);
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId(FOLDER_ID)).thenReturn(link);
    Identity creator = new Identity("organization", "mary");
    creator.setId(String.valueOf(CREATOR_ID));
    creator.setEnable(enabled);
    creator.setDeleted(deleted);
    when(identityManager.getIdentity(String.valueOf(CREATOR_ID))).thenReturn(creator);
    org.exoplatform.services.security.Identity creatorAclIdentity = new org.exoplatform.services.security.Identity("mary");
    when(documentFileService.getAclUserIdentity("mary")).thenReturn(creatorAclIdentity);
    return creatorAclIdentity;
  }
}
