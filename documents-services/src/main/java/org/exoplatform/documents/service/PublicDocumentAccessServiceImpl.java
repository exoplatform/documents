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

import org.apache.commons.codec.binary.Hex;
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.documents.storage.PublicDocumentAccessStorage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.security.codec.AbstractCodec;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.exoplatform.web.security.security.TokenServiceInitializationException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Date;

public class PublicDocumentAccessServiceImpl implements PublicDocumentAccessService {

  private static final Log            LOG                      = ExoLogger.getLogger(PublicDocumentAccessServiceImpl.class);

  private PublicDocumentAccessStorage publicDocumentAccessStorage;

  private final AbstractCodec codec;


  private static final String         HASHING_ALGORITHM            = "PBKDF2WithHmacSHA512";

  private static final int            HASHING_ALGORITHM_ITERATIONS = 65536;

  private static final int            HASH_KEY_LENGTH              = 256;
  
  
  public PublicDocumentAccessServiceImpl(PublicDocumentAccessStorage publicDocumentAccessStorage,
                                         CodecInitializer codecInitializer)
      throws TokenServiceInitializationException {
    this.publicDocumentAccessStorage = publicDocumentAccessStorage;
    this.codec = codecInitializer.getCodec();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PublicDocumentAccess createPublicDocumentAccess(long docOwnerId,
                                                         String nodeId,
                                                         String password,
                                                         Long expirationDate,
                                                         boolean hasPassword) {
    try {
      PublicDocumentAccess publicDocumentAccess = publicDocumentAccessStorage.getPublicDocumentAccessByNodeId(nodeId);
      Date expiration = expirationDate != 0L ? new Date(expirationDate) : null;
      long id = publicDocumentAccess != null ? publicDocumentAccess.getId() : 0L;
      String hashKey = null;
      String encodedPassword = null;
      if (password != null) {
        hashKey = generatePasswordHash(password);
        encodedPassword = codec.encode(password);
      } else if (hasPassword && publicDocumentAccess != null) {
        hashKey = publicDocumentAccess.getPasswordHashKey();
        encodedPassword = publicDocumentAccess.getEncodedPassword();
      }
      publicDocumentAccess = publicDocumentAccessStorage.savePublicDocumentAccess(
                                                                                  new PublicDocumentAccess(id,
                                                                                                           nodeId,
                                                                                                           hashKey,
                                                                                                           encodedPassword,
                                                                                                           expiration),
                                                                                  docOwnerId);
      publicDocumentAccess.setDecodedPassword(password);
      return publicDocumentAccess;
    } catch (Exception e) {
      LOG.error("Error while creating document public access", e);
    }
    return null;
  }

  private String generatePasswordHash(String password) throws NoSuchAlgorithmException, InvalidKeySpecException {
    codec.encode(password);
    SecureRandom secureRandom = new SecureRandom();
    byte[] salt = new byte[16];
    secureRandom.nextBytes(salt);
    KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, HASHING_ALGORITHM_ITERATIONS, HASH_KEY_LENGTH);
    SecretKeyFactory factory = SecretKeyFactory.getInstance(HASHING_ALGORITHM);
    byte[] hash = factory.generateSecret(keySpec).getEncoded();
    return HASHING_ALGORITHM_ITERATIONS + ":" + Hex.encodeHexString(salt) + ":" + Hex.encodeHexString(hash);
  }

  private static boolean validatePassword(String password, String hashKey) {
    try {
      String[] parts = hashKey.split(":");
      int iterations = Integer.parseInt(parts[0]);
      byte[] salt = Hex.decodeHex(parts[1].toCharArray());
      byte[] hash = Hex.decodeHex(parts[2].toCharArray());
      PBEKeySpec pbeKeySpec = new PBEKeySpec(password.toCharArray(), salt, iterations, hash.length * 8);
      SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(HASHING_ALGORITHM);
      byte[] checkHash = secretKeyFactory.generateSecret(pbeKeySpec).getEncoded();
      int diff = hash.length ^ checkHash.length;
      for (int i = 0; i < hash.length && i < checkHash.length; i++) {
        diff |= hash[i] ^ checkHash[i];
      }
      return diff == 0;
    } catch (Exception e) {
      LOG.error("Error while validating document public access password", e);
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PublicDocumentAccess getPublicDocumentAccess(String documentId) {
    PublicDocumentAccess publicDocumentAccess = publicDocumentAccessStorage.getPublicDocumentAccessByNodeId(documentId);
    if (publicDocumentAccess != null && publicDocumentAccess.getEncodedPassword() != null) {
      publicDocumentAccess.setDecodedPassword(codec.decode(publicDocumentAccess.getEncodedPassword()));
    }
    return publicDocumentAccess;
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
    if (publicDocumentAccess.getPasswordHashKey() == null) {
      return true;
    }
    return validatePassword(password, publicDocumentAccess.getPasswordHashKey());
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
