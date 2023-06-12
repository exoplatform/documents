package org.exoplatform.documents.rest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicDocumentAccessEntity {

    private String nodeId;

    private Date expirationDate;

    private boolean hasPassword;
}
