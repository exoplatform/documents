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
package org.exoplatform.documents.entity;

import java.io.Serializable;
import java.util.Date;

import io.meeds.common.persistence.PortableSequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import lombok.Data;

@Entity(name = "PublicDocumentAccess")
@Table(name = "DOCUMENTS_PUBLIC_ACCESS")
@Data
@NamedQuery(name = "PublicDocumentAccess.getPublicAccessByNodeId", query = "SELECT DISTINCT c FROM PublicDocumentAccess c where c.nodeId = :nodeId")
public class PublicDocumentAccessEntity implements Serializable {

  private static final long serialVersionUID = -7234365786103959973L;

  @Id
  @PortableSequence(name = "SEQ_DOCUMENT_PUBLIC_ACCESS_ID")
  @Column(name = "ID", nullable = false)
  private Long    id;

  @Column(name = "NODE_ID", nullable = false)
  private String nodeId;

  @Column(name = "PASSWORD_HASH_KEY")
  private String  passwordHashKey;

  @Column(name = "ENCODED_PASSWORD")
  private String encodedPassword;

  @Column(name = "EXPIRATION_DATE")
  private Date    expirationDate;

}
