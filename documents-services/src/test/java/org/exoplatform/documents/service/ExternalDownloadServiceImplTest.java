package org.exoplatform.documents.service;

import org.exoplatform.documents.storage.DocumentFileStorage;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ExternalDownloadServiceImplTest {

  private DocumentFileStorage     documentFileStorage;

  private ExternalDownloadService externalDownloadService;

  @Before
  public void setUp() throws Exception {
    documentFileStorage = mock(DocumentFileStorage.class);
    externalDownloadService = new ExternalDownloadServiceImpl(documentFileStorage);
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
  public void downloadZippedFolder() throws IOException {
    String path = Files.createTempFile("test", "test.txt").toString();
    when(documentFileStorage.downloadFolder("123")).thenReturn(path);
    byte[] folderBytes = externalDownloadService.downloadZippedFolder("123");
    assertNotNull(folderBytes);
  }
}
