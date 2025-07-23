/**
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.documents.webdav.security;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;
import org.springframework.stereotype.Component;

import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.Identity;

import lombok.Getter;
import lombok.SneakyThrows;

@Component
public class WebDavDigestAuthenticator {

  @Getter
  private static WebDavDigestAuthenticator instance;

  private Authenticator                    authenticator;

  public WebDavDigestAuthenticator(Authenticator authenticator) {
    this.authenticator = authenticator;
    WebDavDigestAuthenticator.instance = this; // NOSONAR singleton
  }

  @SneakyThrows
  public String validateUser(String userName, // NOSONAR
                             String clientDigest,
                             String nonce,
                             String nc,
                             String cnonce,
                             String qop,
                             String realm,
                             String digestA2,
                             String algorithm) {
    // In digest auth, digests are always lower case
    String digestA1 = getDigest(userName, realm, algorithm);
    digestA1 = digestA1.toLowerCase(Locale.ENGLISH);
    String serverDigestValue;
    if (qop == null) {
      serverDigestValue = digestA1 + ":" + nonce + ":" + digestA2;
    } else {
      serverDigestValue = digestA1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + digestA2;
    }
    byte[] valueBytes = serverDigestValue.getBytes(StandardCharsets.UTF_8);
    String serverDigest = HexUtils.toHexString(ConcurrentMessageDigest.digest(algorithm, valueBytes));
    if (serverDigest.equals(clientDigest)) {
      return userName;
    }
    return null;
  }

  @SneakyThrows
  public Identity createIdentity(String userName) {
    return authenticator.createIdentity(userName);
  }

  private String getDigest(String userName, String realmName, String algorithm) {
    String clearTextPassword = getPassword(userName);
    if (StringUtils.isBlank(clearTextPassword)) {
      throw new IllegalStateException(String.format("No generated password for user '%s'", userName));
    }
    String digestValue = userName + ":" + realmName + ":" + clearTextPassword;
    byte[] valueBytes = digestValue.getBytes(StandardCharsets.UTF_8);
    return HexUtils.toHexString(ConcurrentMessageDigest.digest(algorithm, valueBytes));
  }

  @SneakyThrows
  private String getPassword(String userName) {
    return "password"; // TODO replace with real implementation to get clear
                       // text password
  }

}
