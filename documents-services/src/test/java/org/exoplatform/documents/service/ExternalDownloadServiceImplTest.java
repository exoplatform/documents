package org.exoplatform.documents.service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.DownloadItem;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ExternalDownloadServiceImplTest {

  private DocumentFileStorage         documentFileStorage;

  private PublicDocumentAccessService publicDocumentAccessService;

  private ExternalDownloadService     externalDownloadService;

  @Before
  public void setUp() throws Exception {
    documentFileStorage = mock(DocumentFileStorage.class);
    publicDocumentAccessService = mock(PublicDocumentAccessService.class);
    externalDownloadService = new ExternalDownloadServiceImpl(documentFileStorage, publicDocumentAccessService);
  }

  @Test
  public void getDocumentDownloadItem() {
    Throwable exception = assertThrows(IllegalArgumentException.class,
                                       () -> this.externalDownloadService.getDocumentDownloadItem(null));
    assertEquals("document id is mandatory", exception.getMessage());
    externalDownloadService.getDocumentDownloadItem("123");
    verify(documentFileStorage, times(1)).getDocumentDownloadItem("123");
  }

  @Test
  public void getPublicDocumentInfoWithoutPublicLinkReadsNothingOfTheDocument() {
    when(publicDocumentAccessService.hasDocumentPublicAccess("123")).thenReturn(false);
    assertThrows(ObjectNotFoundException.class, () -> externalDownloadService.getPublicDocumentInfo("123"));
    verifyNoInteractions(documentFileStorage);
  }

  @Test
  public void getPublicDocumentInfoWithPublicLinkReadsNoContent() throws ObjectNotFoundException {
    DownloadItem documentInfo = new DownloadItem("123", "document.pdf", null, "application/pdf");
    when(publicDocumentAccessService.hasDocumentPublicAccess("123")).thenReturn(true);
    when(documentFileStorage.getDocumentDownloadInfo("123")).thenReturn(documentInfo);
    assertSame(documentInfo, externalDownloadService.getPublicDocumentInfo("123"));
    verify(documentFileStorage, never()).getDocumentDownloadItem(anyString());
  }

  @Test
  public void getPublicDocumentInfoWithPublicLinkOfAMissingDocument() {
    when(publicDocumentAccessService.hasDocumentPublicAccess("123")).thenReturn(true);
    when(documentFileStorage.getDocumentDownloadInfo("123")).thenReturn(null);
    assertThrows(ObjectNotFoundException.class, () -> externalDownloadService.getPublicDocumentInfo("123"));
  }

  @Test
  public void getPublicDocumentInfoWithoutId() {
    assertThrows(IllegalArgumentException.class, () -> externalDownloadService.getPublicDocumentInfo(null));
    verifyNoInteractions(documentFileStorage, publicDocumentAccessService);
  }

  @Test
  public void downloadZippedFolder() throws IOException {
    String path = Files.createTempFile("test", "test.txt").toString();
    when(documentFileStorage.downloadFolder("123")).thenReturn(path);
    byte[] folderBytes = externalDownloadService.downloadZippedFolder("123");
    assertNotNull(folderBytes);
  }
}
