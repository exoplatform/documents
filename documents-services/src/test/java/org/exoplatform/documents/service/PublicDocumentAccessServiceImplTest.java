package org.exoplatform.documents.service;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.documents.storage.PublicDocumentAccessStorage;
import org.exoplatform.web.security.codec.AbstractCodec;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Base64;
import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PublicDocumentAccessServiceImplTest {

  private PublicDocumentAccessStorage publicDocumentAccessStorage;

  private PublicDocumentAccessService publicDocumentAccessService;

  private AbstractCodec        codec;

  @Before
  public void setUp() throws Exception {
    publicDocumentAccessStorage = mock(PublicDocumentAccessStorage.class);
    codec = mock(AbstractCodec.class);
    CodecInitializer codecInitializer = mock(CodecInitializer.class);
    when(codecInitializer.getCodec()).thenReturn(codec);
    System.setProperty("exo.documents.public.access.secret", "itQyLyNScBjZX8Ss3pVyI2pp7JDoAFUgYEMO/oV94V0=");
    publicDocumentAccessService = new PublicDocumentAccessServiceImpl(publicDocumentAccessStorage, codecInitializer);
  }

  @Test
  public void createDocumentToken() {
    when(codec.encode(anyString())).thenReturn(Base64.getEncoder().encodeToString("12345678".getBytes()));
    PublicDocumentAccess publicDocumentAccess = new PublicDocumentAccess();
    publicDocumentAccess.setId(1L);
    publicDocumentAccess.setNodeId("123");
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(publicDocumentAccess);
    when(publicDocumentAccessStorage.savePublicDocumentAccess(any(), anyLong())).thenReturn(publicDocumentAccess);
    publicDocumentAccessService.createPublicDocumentAccess(1L, "123", false, "12345678", new Date().getTime());
    verify(publicDocumentAccessStorage).savePublicDocumentAccess(any(), anyLong());
  }

  @Test
  public void getDocumentToken() {
    publicDocumentAccessService.getPublicDocumentAccess("123");
    verify(publicDocumentAccessStorage, times(1)).getPublicDocumentAccessByNodeId("123");
  }

  @Test
  public void isTokenExpired() {
    PublicDocumentAccess publicDocumentAccess = new PublicDocumentAccess();
    Date expiration = new Date(new Date().getTime() + (1000 * 60 * 60 * 24));
    publicDocumentAccess.setExpirationDate(expiration);
    when(publicDocumentAccessStorage.getPublicDocumentAccessByNodeId("123")).thenReturn(null, publicDocumentAccess);
    assertTrue(publicDocumentAccessService.isPublicDocumentAccessExpired("123"));
    assertFalse(publicDocumentAccessService.isPublicDocumentAccessExpired("123"));
  }

  private String generateToken(String docId, String password, boolean isFolder) {
    String secretKey = PropertyManager.getProperty("exo.documents.public.access.secret");
    when(codec.encode(password)).thenReturn(Base64.getEncoder().encodeToString(password.getBytes()));
    Date expirationDate = new Date(new Date().getTime() + (1000 * 60 * 60 * 24));
    JwtBuilder jwtBuilder = Jwts.builder();
    jwtBuilder.setHeaderParam("type", "JWT")
              .setSubject("Document Public Access")
              .setIssuer(String.valueOf(1L))
              .setAudience("*")
              .claim("nodeId", docId)
              .claim("isFolder", isFolder)
              .setIssuedAt(new Date())
              .setExpiration(expirationDate)
              .signWith(Keys.hmacShaKeyFor((secretKey + codec.encode(password)).getBytes()), SignatureAlgorithm.HS256);
    return jwtBuilder.compact();
  }

  @Test
  public void isTokenSignatureValid() {
    when(codec.encode("123456")).thenReturn(Base64.getEncoder().encodeToString("123456".getBytes()));
    PublicDocumentAccess publicDocumentAccess = new PublicDocumentAccess();
    publicDocumentAccess.setToken(generateToken("123", "12345678", false));
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
