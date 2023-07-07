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
package org.exoplatform.documents.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
public class PublicDocumentAccess {

  private Long   id;

  private String nodeId;

  /**
   * Password hash used to validate the access
   */
  private String passwordHashKey;

  /**
   * Password encoded stored in database used to display
   * the password to the user after decoding it
   */
  private String encodedPassword;

  /**
   * Password decoded used to display the password
   * to the user in the front
   */
  private String decodedPassword;

  private Date   expirationDate;

  public PublicDocumentAccess(Long id, String nodeId, String passwordHashKey, String encodedPassword, Date expirationDate) {
    this.id = id;
    this.nodeId = nodeId;
    this.passwordHashKey = passwordHashKey;
    this.encodedPassword = encodedPassword;
    this.expirationDate = expirationDate;
  }
}
