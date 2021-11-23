/*
 * Copyright (C) 2021 eXo Platform SAS
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
package org.exoplatform.documents.rest.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.documents.model.*;
import org.exoplatform.documents.rest.model.*;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class EntityBuilder {

  private EntityBuilder() {
  }

  public static List<AbstractNodeEntity> toDocumentItemEntities(DocumentFileService documentFileService,
                                                                IdentityManager identityManager,
                                                                SpaceService spaceService,
                                                                List<AbstractNode> documents,
                                                                String expand) {
    return documents.stream()
                    .map(document -> toDocumentItemEntity(documentFileService, identityManager, spaceService, document, expand))
                    .collect(Collectors.toList());
  }

  public static AbstractNodeEntity toDocumentItemEntity(DocumentFileService documentFileService,
                                                        IdentityManager identityManager,
                                                        SpaceService spaceService,
                                                        AbstractNode document,
                                                        String expand) {
    List<String> expandProperties = StringUtils.isBlank(expand) ? Collections.emptyList()
                                                                : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""),
                                                                                                  ","));

    if (document instanceof FileNode) {
      return toFileEntity(documentFileService, identityManager, spaceService, (FileNode) document, expandProperties);
    } else if (document instanceof FolderNode) {
      return toFolderEntity(documentFileService, identityManager, spaceService, (FolderNode) document, expandProperties);
    }
    return null;
  }

  public static FileNodeEntity toFileEntity(DocumentFileService documentFileService,
                                            IdentityManager identityManager,
                                            SpaceService spaceService,
                                            FileNode file,
                                            List<String> expandProperties) {
    FileNodeEntity fileEntity = new FileNodeEntity();
    toNode(documentFileService, identityManager, spaceService, file, fileEntity, expandProperties);
    fileEntity.setLinkedFileId(file.getLinkedFileId());
    fileEntity.setVersionnedFileId(file.getVersionnedFileId());
    fileEntity.setMimeType(file.getMimeType());
    fileEntity.setSize(file.getSize());
    if (expandProperties.contains("versions")) {
      // TODO (documentFileService.getFileVersions) think of using limit of file
      // versions to retrieve
    }
    return fileEntity;
  }

  public static FolderNodeEntity toFolderEntity(DocumentFileService documentFileService,
                                                IdentityManager identityManager,
                                                SpaceService spaceService,
                                                FolderNode folder,
                                                List<String> expandProperties) {
    FolderNodeEntity folderEntity = new FolderNodeEntity();
    toNode(documentFileService, identityManager, spaceService, folder, folderEntity, expandProperties);
    return folderEntity;
  }

  private static void toNode(DocumentFileService documentFileService,
                             IdentityManager identityManager,
                             SpaceService spaceService,
                             AbstractNode node,
                             AbstractNodeEntity nodeEntity,
                             List<String> expandProperties) {
    nodeEntity.setId(node.getId());
    nodeEntity.setName(node.getName() != null ? URLDecoder.decode(node.getName(), StandardCharsets.UTF_8) : null);
    nodeEntity.setDatasource(node.getDatasource());
    nodeEntity.setDescription(node.getDescription());
    nodeEntity.setAcl(node.getAcl());
    nodeEntity.setCreatedDate(node.getCreatedDate());
    nodeEntity.setModifiedDate(node.getModifiedDate());
    nodeEntity.setParentFolderId(node.getParentFolderId());
    if (expandProperties.contains("creator")) {
      nodeEntity.setCreatorIdentity(toIdentityEntity(identityManager, spaceService, node.getCreatorId()));
    }
    if (expandProperties.contains("modifier")) {
      nodeEntity.setModifierIdentity(toIdentityEntity(identityManager, spaceService, node.getModifierId()));
    }
    if (expandProperties.contains("owner")) {
      nodeEntity.setOwnerIdentity(toIdentityEntity(identityManager, spaceService, node.getOwnerId()));
    }
    if (expandProperties.contains("auditTrails")) {
      // TODO (documentFileService.getNodeAuditTrails) think of using limit of
      // file auditTrails to retrieve. In listing, we need only latest activity,
      // so limit = 1
    }
    if (expandProperties.contains("metadatas")) {
      // TODO (documentFileService.getNodeMetadatas) retrieving all Metadata of
      // a file, visible for current user only. The current user, by example
      // must not see the metadata of other users, such as favorites metadata
    }
  }

  public static IdentityEntity toIdentityEntity(IdentityManager identityManager, SpaceService spaceService, long identityId) {
    Identity identity = identityManager.getIdentity(String.valueOf(identityId));
    if (identity == null) {
      return null;
    }
    IdentityEntity identityEntity = new IdentityEntity();
    identityEntity.setId(identity.getId());
    identityEntity.setProviderId(identity.getProviderId());
    identityEntity.setRemoteId(identity.getRemoteId());
    if (identity.isUser()) {
      identityEntity.setName(identity.getProfile().getFullName());
      identityEntity.setAvatar(identity.getProfile().getAvatarUrl());
    } else if (identity.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(identity.getRemoteId());
      if (space != null) {
        identityEntity.setName(space.getDisplayName());
        identityEntity.setAvatar(space.getAvatarUrl());
      }
    }
    return identityEntity;
  }

}
