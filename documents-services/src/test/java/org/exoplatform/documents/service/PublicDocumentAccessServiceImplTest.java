package org.exoplatform.documents.service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.exception.DocumentSharingRefusedException;
import org.exoplatform.documents.model.DownloadItem;
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.documents.model.PublicDocumentInfo;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.PublicDocumentAccessStorage;
import org.exoplatform.web.security.codec.AbstractCodec;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PublicDocumentAccessServiceImplTest {

  private PublicDocumentAccessStorage publicDocumentAccessStorage;

  private DocumentFileStorage         documentFileStorage;

  private PublicDocumentAccessService publicDocumentAccessService;

  private AbstractCodec               codec;

  private DocumentFileService         documentFileService;

  @Before
  public void setUp() throws Exception {
    publicDocumentAccessStorage = mock(PublicDocumentAccessStorage.class);
    documentFileStorage = mock(DocumentFileStorage.class);
    documentFileService = mock(DocumentFileService.class);
    codec = mock(AbstractCodec.class);
    CodecInitializer codecInitializer = mock(CodecInitializer.class);
    when(codecInitializer.getCodec()).thenReturn(codec);
    publicDocumentAccessService = new PublicDocumentAccessServiceImpl(publicDocumentAccessStorage,
                                                                      documentFileStorage,
                                                                      documentFileService,
                                                                      codecInitializer);
  }

  @Test
  public void createDocumentPublicAccess() throws IllegalAccessException {
    PublicDocumentAccess publicDocumentAccess = new PublicDocumentAccess();
    publicDocumentAccess.setId(1L);
    publicDocumentAccess.setNodeId("123");
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(publicDocumentAccess);
    when(publicDocumentAccessStorage.savePublicDocumentAccess(any(), anyLong())).thenReturn(publicDocumentAccess);
    publicDocumentAccessService.createDocumentPublicAccess(1L, "123", "12345678", new Date().getTime(), false);
    verify(publicDocumentAccessStorage).savePublicDocumentAccess(any(), anyLong());
  }

  @Test
  public void getDocumentPublicAccess() {
    PublicDocumentAccess publicDocumentAccess = new PublicDocumentAccess();
    publicDocumentAccess.setEncodedPassword("encoded");
    when(codec.decode("encoded")).thenReturn("decoded");
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(publicDocumentAccess);
    publicDocumentAccessService.getPublicDocumentAccess("123");
    verify(publicDocumentAccessStorage, times(1)).getPublicDocumentAccessByNodeId("123");
  }

  @Test
  public void isAccessExpired() {
    PublicDocumentAccess publicDocumentAccess = new PublicDocumentAccess();
    Date expiration = new Date(new Date().getTime() + (1000 * 60 * 60 * 24));
    publicDocumentAccess.setExpirationDate(expiration);
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(null, publicDocumentAccess);
    assertTrue(publicDocumentAccessService.isPublicDocumentAccessExpired("123"));
    assertFalse(publicDocumentAccessService.isPublicDocumentAccessExpired("123"));
  }

  private String generatePasswordHash(String password) throws NoSuchMethodException,
                                                       InvocationTargetException,
                                                       IllegalAccessException {
    Method generatePasswordHash = publicDocumentAccessService.getClass().getDeclaredMethod("generatePasswordHash", String.class);
    generatePasswordHash.setAccessible(true);
    return (String) generatePasswordHash.invoke(publicDocumentAccessService, password);
  }

  @Test
  public void isPublicDocumentAccessValid() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    PublicDocumentAccess publicDocumentAccess = new PublicDocumentAccess();
    publicDocumentAccess.setPasswordHashKey(generatePasswordHash("12345678"));
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(publicDocumentAccess);
    assertFalse(publicDocumentAccessService.isDocumentPublicAccessValid("123", "123456"));
    assertTrue(publicDocumentAccessService.isDocumentPublicAccessValid("123", "12345678"));
  }

  @Test
  public void hasPublicLink() {
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(null, new PublicDocumentAccess());
    assertFalse(publicDocumentAccessService.hasDocumentPublicAccess("123"));
    assertTrue(publicDocumentAccessService.hasDocumentPublicAccess("123"));
  }

  @Test
  public void getPublicDocumentInfoWithoutPublicLinkReadsNothingOfTheDocument() {
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(null);
    assertThrows(ObjectNotFoundException.class, () -> publicDocumentAccessService.getPublicDocumentInfo("123"));
    verifyNoInteractions(documentFileStorage);
  }

  @Test
  public void getPublicDocumentInfoWithLockedLinkReadsTheLinkOnceAndNoContent() throws ObjectNotFoundException {
    PublicDocumentAccess publicDocumentAccess = new PublicDocumentAccess();
    publicDocumentAccess.setNodeId("123");
    publicDocumentAccess.setPasswordHashKey("100:salt:hash");
    publicDocumentAccess.setEncodedPassword("encoded");
    publicDocumentAccess.setExpirationDate(new Date(System.currentTimeMillis() + 86400000L));
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(publicDocumentAccess);
    when(documentFileStorage.getDocumentDownloadInfo("123")).thenReturn(new DownloadItem("123",
                                                                                         "document.pdf",
                                                                                         null,
                                                                                         "application/pdf"));

    PublicDocumentInfo publicDocumentInfo = publicDocumentAccessService.getPublicDocumentInfo("123");

    assertEquals("document.pdf", publicDocumentInfo.getDocumentName());
    assertEquals("application/pdf", publicDocumentInfo.getMimeType());
    assertTrue(publicDocumentInfo.isAccessLocked());
    assertFalse(publicDocumentInfo.isAccessExpired());
    verify(publicDocumentAccessStorage, times(1)).getPublicDocumentAccessByNodeId("123");
    verify(codec, never()).decode(anyString());
    verify(documentFileStorage, never()).getDocumentDownloadItem(anyString());
  }

  @Test
  public void getPublicDocumentInfoWithExpiredLinkWithoutPassword() throws ObjectNotFoundException {
    PublicDocumentAccess publicDocumentAccess = new PublicDocumentAccess();
    publicDocumentAccess.setNodeId("123");
    publicDocumentAccess.setExpirationDate(new Date(System.currentTimeMillis() - 86400000L));
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(publicDocumentAccess);
    when(documentFileStorage.getDocumentDownloadInfo("123")).thenReturn(new DownloadItem("123", "folder", null, null));

    PublicDocumentInfo publicDocumentInfo = publicDocumentAccessService.getPublicDocumentInfo("123");

    assertEquals("folder", publicDocumentInfo.getDocumentName());
    assertNull(publicDocumentInfo.getMimeType());
    assertFalse(publicDocumentInfo.isAccessLocked());
    assertTrue(publicDocumentInfo.isAccessExpired());
  }

  @Test
  public void getPublicDocumentInfoWithLinkWithoutExpirationDate() throws ObjectNotFoundException {
    PublicDocumentAccess publicDocumentAccess = new PublicDocumentAccess();
    publicDocumentAccess.setNodeId("123");
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(publicDocumentAccess);
    when(documentFileStorage.getDocumentDownloadInfo("123")).thenReturn(new DownloadItem("123", "document.pdf", null, null));
    assertFalse(publicDocumentAccessService.getPublicDocumentInfo("123").isAccessExpired());
  }

  @Test
  public void getPublicDocumentInfoWithLinkOfAMissingDocument() {
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(new PublicDocumentAccess());
    when(documentFileStorage.getDocumentDownloadInfo("123")).thenReturn(null);
    assertThrows(ObjectNotFoundException.class, () -> publicDocumentAccessService.getPublicDocumentInfo("123"));
  }

  @Test
  public void getPublicDocumentInfoWithoutId() {
    assertThrows(IllegalArgumentException.class, () -> publicDocumentAccessService.getPublicDocumentInfo(null));
    verifyNoInteractions(publicDocumentAccessStorage, documentFileStorage);
  }

  @Test
  @SuppressWarnings("removal")
  public void revokePublicAccess() {
    publicDocumentAccessService.revokeDocumentPublicAccess("123");
    verify(publicDocumentAccessStorage, times(1)).removePublicDocumentAccess("123");
  }

  @Test
  public void createPublicAccessRefusedWhenTheSpaceRefusesTheUser() throws IllegalAccessException {
    doThrow(refusal(DocumentSharingRefusedException.CREATE_PUBLIC_LINK_ACTION)).when(documentFileService)
                                                                              .checkCanManageAccess("123",
                                                                                                    1L,
                                                                                                    DocumentSharingRefusedException.CREATE_PUBLIC_LINK_ACTION);

    DocumentSharingRefusedException refusal = assertThrows(DocumentSharingRefusedException.class,
                                                           () -> publicDocumentAccessService.createDocumentPublicAccess(1L,
                                                                                                                        "123",
                                                                                                                        null,
                                                                                                                        0L,
                                                                                                                        false));

    assertEquals(DocumentSharingRefusedException.ACCESS_MANAGEMENT_LIMITED, refusal.getMessageCode());
    verify(publicDocumentAccessStorage, never()).savePublicDocumentAccess(any(), anyLong());
  }

  @Test
  @SuppressWarnings("removal")
  public void theDeprecatedCreationThrowsARefusalUnchecked() throws IllegalAccessException {
    doThrow(refusal(DocumentSharingRefusedException.CREATE_PUBLIC_LINK_ACTION)).when(documentFileService)
                                                                              .checkCanManageAccess("123",
                                                                                                    1L,
                                                                                                    DocumentSharingRefusedException.CREATE_PUBLIC_LINK_ACTION);

    IllegalStateException refusal = assertThrows(IllegalStateException.class,
                                                 () -> publicDocumentAccessService.createPublicDocumentAccess(1L,
                                                                                                              "123",
                                                                                                              null,
                                                                                                              0L,
                                                                                                              false));

    assertTrue(refusal.getCause() instanceof DocumentSharingRefusedException);
    verify(publicDocumentAccessStorage, never()).savePublicDocumentAccess(any(), anyLong());
  }

  @Test
  public void revokePublicAccessOnBehalfOfAnAllowedUser() throws IllegalAccessException {
    publicDocumentAccessService.revokeDocumentPublicAccess("123", 1L);

    verify(documentFileService).checkCanManageAccess("123", 1L, DocumentSharingRefusedException.REVOKE_PUBLIC_LINK_ACTION);
    verify(publicDocumentAccessStorage).removePublicDocumentAccess("123");
  }

  @Test
  public void revokePublicAccessRefusedWhenTheSpaceRefusesTheUser() throws IllegalAccessException {
    doThrow(refusal(DocumentSharingRefusedException.REVOKE_PUBLIC_LINK_ACTION)).when(documentFileService)
                                                                              .checkCanManageAccess("123",
                                                                                                    1L,
                                                                                                    DocumentSharingRefusedException.REVOKE_PUBLIC_LINK_ACTION);

    assertThrows(DocumentSharingRefusedException.class, () -> publicDocumentAccessService.revokeDocumentPublicAccess("123", 1L));

    verify(publicDocumentAccessStorage, never()).removePublicDocumentAccess(anyString());
  }

  private static DocumentSharingRefusedException refusal(String action) {
    return new DocumentSharingRefusedException(DocumentSharingRefusedException.ACCESS_MANAGEMENT_LIMITED, 7L, "123", action);
  }
}
