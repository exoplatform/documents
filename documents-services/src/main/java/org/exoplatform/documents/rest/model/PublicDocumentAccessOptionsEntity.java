package org.exoplatform.documents.rest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicDocumentAccessOptionsEntity {

  private String  password;

  private Long    expirationDate;

  private boolean hasPassword;
}
