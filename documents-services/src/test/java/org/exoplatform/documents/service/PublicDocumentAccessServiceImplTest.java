package org.exoplatform.documents.service;

import org.exoplatform.documents.model.PublicDocumentAccess;
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

  private PublicDocumentAccessService publicDocumentAccessService;

  private AbstractCodec               codec;

  @Before
  public void setUp() throws Exception {
    publicDocumentAccessStorage = mock(PublicDocumentAccessStorage.class);
    codec = mock(AbstractCodec.class);
    CodecInitializer codecInitializer = mock(CodecInitializer.class);
    when(codecInitializer.getCodec()).thenReturn(codec);
    publicDocumentAccessService = new PublicDocumentAccessServiceImpl(publicDocumentAccessStorage, codecInitializer);
  }

  @Test
  public void createDocumentPublicAccess() {
    PublicDocumentAccess publicDocumentAccess = new PublicDocumentAccess();
    publicDocumentAccess.setId(1L);
    publicDocumentAccess.setNodeId("123");
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(publicDocumentAccess);
    when(publicDocumentAccessStorage.savePublicDocumentAccess(any(), anyLong())).thenReturn(publicDocumentAccess);
    publicDocumentAccessService.createPublicDocumentAccess(1L, "123", "12345678", new Date().getTime(), false);
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
  public void revokePublicAccess() {
    publicDocumentAccessService.revokeDocumentPublicAccess("123");
    verify(publicDocumentAccessStorage, times(1)).removePublicDocumentAccess("123");
  }
}
