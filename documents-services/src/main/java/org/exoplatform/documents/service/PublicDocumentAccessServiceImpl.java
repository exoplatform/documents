/*
 * Copyright (C) 2023 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.impl.crypto.DefaultJwtSignatureValidator;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.documents.storage.PublicDocumentAccessStorage;
import org.exoplatform.web.security.codec.AbstractCodec;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.exoplatform.web.security.security.TokenServiceInitializationException;

import javax.crypto.spec.SecretKeySpec;
import java.util.Date;

public class PublicDocumentAccessServiceImpl implements PublicDocumentAccessService {

  private final AbstractCodec  codec;

  private PublicDocumentAccessStorage publicDocumentAccessStorage;

  private static final String  PUBLIC_ACCESS_SECRET_KEY = "exo.documents.public.access.secret";

  private static final String  TOKEN_TYPE               = "Document Public Access";

  public PublicDocumentAccessServiceImpl(PublicDocumentAccessStorage publicDocumentAccessStorage, CodecInitializer codecInitializer)
      throws TokenServiceInitializationException {
    this.publicDocumentAccessStorage = publicDocumentAccessStorage;
    this.codec = codecInitializer.getCodec();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String createPublicDocumentAccess(long docOwnerId, String nodeId, boolean isFolder, String password, Long expirationDate) {
    String token = generateToken(docOwnerId, nodeId, isFolder, password, expirationDate);
    PublicDocumentAccess publicDocumentAccess = publicDocumentAccessStorage.getPublicDocumentAccessByNodeId(nodeId);
    Date expiration = expirationDate != 0L ? new Date(expirationDate) : null;
    long id = publicDocumentAccess != null ? publicDocumentAccess.getId() : 0L;
    boolean hasPassword = password != null;
    publicDocumentAccess = publicDocumentAccessStorage.savePublicDocumentAccess(new PublicDocumentAccess(id, nodeId, token, expiration, hasPassword), docOwnerId);
    return publicDocumentAccess.getNodeId();
  }
  
  private String generateToken(long docOwnerId, String nodeId, boolean isFolder, String password, Long expirationDate) {
    JwtBuilder jwtBuilder = Jwts.builder();
    jwtBuilder.setHeaderParam("type", "JWT")
              .setSubject(TOKEN_TYPE)
              .setIssuer(String.valueOf(docOwnerId))
              .setAudience("*")
              .claim("nodeId", nodeId)
              .claim("isFolder", isFolder)
              .setIssuedAt(new Date());
    if (expirationDate != 0L) {
      jwtBuilder.setExpiration(new Date(expirationDate));
    }
    jwtBuilder.signWith(Keys.hmacShaKeyFor(getSecretKey(password).getBytes()), SignatureAlgorithm.HS256);
    return jwtBuilder.compact();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PublicDocumentAccess getPublicDocumentAccess(String documentId) {
    return publicDocumentAccessStorage.getPublicDocumentAccessByNodeId(documentId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isPublicDocumentAccessExpired(String documentId) {
    PublicDocumentAccess publicDocumentAccess = publicDocumentAccessStorage.getPublicDocumentAccessByNodeId(documentId);
    if (publicDocumentAccess == null) {
      return true;
    }
    Date expiration = publicDocumentAccess.getExpirationDate();
    return expiration != null && expiration.before(new Date());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isDocumentPublicAccessValid(String documentId, String password) {
    PublicDocumentAccess publicDocumentAccess = publicDocumentAccessStorage.getPublicDocumentAccessByNodeId(documentId);
    if (publicDocumentAccess == null) {
      return false;
    }
    String[] chunks = publicDocumentAccess.getToken().split("\\.");
    String tokenWithoutSignature = chunks[0] + "." + chunks[1];
    String signature = chunks[2];
    SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
    SecretKeySpec secretKeySpec = new SecretKeySpec(getSecretKey(password).getBytes(), signatureAlgorithm.getJcaName());
    DefaultJwtSignatureValidator jwtSignatureValidator = new DefaultJwtSignatureValidator(signatureAlgorithm,
                                                                                          secretKeySpec,
                                                                                          Decoders.BASE64URL);
    return jwtSignatureValidator.isValid(tokenWithoutSignature, signature);
  }

  private String getSecretKey(String password) {
    String secretKey = PropertyManager.getProperty(PUBLIC_ACCESS_SECRET_KEY);
    if (secretKey == null) {
      throw new IllegalArgumentException("secret key is missing");
    }
    if (password != null) {
      secretKey += codec.encode(password);
    }
    return secretKey;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasDocumentPublicAccess(String documentId) {
    return publicDocumentAccessStorage.getPublicDocumentAccessByNodeId(documentId) != null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void revokeDocumentPublicAccess(String documentId) {
    publicDocumentAccessStorage.removePublicDocumentAccess(documentId);
  }
}
