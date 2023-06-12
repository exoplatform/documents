package org.exoplatform.documents.utils;

import org.exoplatform.documents.entity.PublicDocumentAccessEntity;
import org.exoplatform.documents.model.PublicDocumentAccess;

public class EntityMapper {

  private EntityMapper() { // NOSONAR
  }

  public static PublicDocumentAccess toDocumentPublicAccess(PublicDocumentAccessEntity publicDocumentAccessEntity) {
    if (publicDocumentAccessEntity == null) {
      return null;
    }
    return new PublicDocumentAccess(publicDocumentAccessEntity.getId(),
                                    publicDocumentAccessEntity.getNodeId(),
                                    publicDocumentAccessEntity.getPasswordHashKey(),
                                    publicDocumentAccessEntity.getExpirationDate());
  }

  public static PublicDocumentAccessEntity toPublicDocumentAccessEntity(PublicDocumentAccess publicDocumentAccess) {
    PublicDocumentAccessEntity publicDocumentAccessEntity = new PublicDocumentAccessEntity();
    publicDocumentAccessEntity.setId(publicDocumentAccess.getId());
    publicDocumentAccessEntity.setNodeId(publicDocumentAccess.getNodeId());
    publicDocumentAccessEntity.setPasswordHashKey(publicDocumentAccess.getPasswordHashKey());
    publicDocumentAccessEntity.setExpirationDate(publicDocumentAccess.getExpirationDate());
    return publicDocumentAccessEntity;
  }
}
