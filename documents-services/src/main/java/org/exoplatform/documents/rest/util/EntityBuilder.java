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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.BreadCrumbItem;
import org.exoplatform.documents.model.FileNode;
import org.exoplatform.documents.model.FolderNode;
import org.exoplatform.documents.rest.model.*;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataObject;
import org.exoplatform.social.rest.entity.MetadataItemEntity;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class EntityBuilder {
  private static final Log    LOG                       = ExoLogger.getExoLogger(EntityBuilder.class);

  private static final String FILE_METADATA_OBJECT_TYPE = "file";

  private EntityBuilder() {
  }

  public static List<AbstractNodeEntity> toDocumentItemEntities(DocumentFileService documentFileService,
                                                                IdentityManager identityManager,
                                                                SpaceService spaceService,
                                                                MetadataService metadataService,
                                                                List<AbstractNode> documents,
                                                                String expand,
                                                                long authenticatedUserId) {
    return documents.stream()
                    .map(document -> toDocumentItemEntity(documentFileService,
                                                          identityManager,
                                                          spaceService,
                                                          metadataService,
                                                          document,
                                                          expand,
                                                          authenticatedUserId))
                    .collect(Collectors.toList());
  }

  public static AbstractNodeEntity toDocumentItemEntity(DocumentFileService documentFileService,
                                                        IdentityManager identityManager,
                                                        SpaceService spaceService,
                                                        MetadataService metadataService,
                                                        AbstractNode document,
                                                        String expand,
                                                        long authenticatedUserId) {
    List<String> expandProperties =
                                  StringUtils.isBlank(expand) ? Collections.emptyList()
                                                              : Arrays.asList(StringUtils.split(expand.replaceAll(" ", ""), ","));

    if (document instanceof FileNode) {
      return toFileEntity(documentFileService,
                          identityManager,
                          spaceService,
                          metadataService,
                          (FileNode) document,
                          expandProperties,
                          authenticatedUserId);
    } else if (document instanceof FolderNode) {
      return toFolderEntity(documentFileService,
                            identityManager,
                            spaceService,
                            metadataService,
                            (FolderNode) document,
                            expandProperties,
                            authenticatedUserId);
    }
    return null;
  }

  public static FileNodeEntity toFileEntity(DocumentFileService documentFileService,
                                            IdentityManager identityManager,
                                            SpaceService spaceService,
                                            MetadataService metadataService,
                                            FileNode file,
                                            List<String> expandProperties,
                                            long authenticatedUserId) {
    FileNodeEntity fileEntity = new FileNodeEntity();
    toNode(documentFileService,
           identityManager,
           spaceService,
           metadataService,
           file,
           fileEntity,
           expandProperties,
           authenticatedUserId);
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
                                                MetadataService metadataService,
                                                FolderNode folder,
                                                List<String> expandProperties,
                                                long authenticatedUser) {
    FolderNodeEntity folderEntity = new FolderNodeEntity();
    toNode(documentFileService,
           identityManager,
           spaceService,
           metadataService,
           folder,
           folderEntity,
           expandProperties,
           authenticatedUser);
    return folderEntity;
  }

  public static List<BreadCrumbItemEntity> toBreadCrumbItemEntities(List<BreadCrumbItem> folders) {
    List<BreadCrumbItemEntity>  brList = new ArrayList<BreadCrumbItemEntity>();
    brList = folders.stream().map(document -> new BreadCrumbItemEntity(document.getId(), document.getName())).collect(Collectors.toList());
    Collections.reverse(brList);
    return brList;
  }

  private static void toNode(DocumentFileService documentFileService,
                             IdentityManager identityManager,
                             SpaceService spaceService,
                             MetadataService metadataService,
                             AbstractNode node,
                             AbstractNodeEntity nodeEntity,
                             List<String> expandProperties,
                             long authenticatedUserId) {
    try {
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
        List<MetadataItem> metadataItems = metadataService.getMetadataItemsByObject(new MetadataObject(FILE_METADATA_OBJECT_TYPE,
                                                                                                       node.getId()));
        Map<String, List<MetadataItem>> metadatas = new HashMap<>();
        metadataItems.forEach(metadataItem -> {
          String type = metadataItem.getMetadata().getType().getName();
          if (metadatas.get(type) == null) {
            metadatas.put(type, new ArrayList<>());
          }
          metadatas.get(type).add(metadataItem);
        });
        nodeEntity.setFavorite(metadatas.containsKey("favorites"));
        nodeEntity.setMetadatas(retrieveMetadataItems(metadatas, authenticatedUserId));
      }

      if (expandProperties.contains("breadcrumb") && (node instanceof FolderNode)) {
        ((FolderNodeEntity)nodeEntity).setBreadcrumb(getBreadCrumbs(documentFileService, node, authenticatedUserId));
      }
    } catch (Exception e) {
      LOG.error("==== exception occured when converting node with ID = {} and name = {}", node.getId(), node.getName(), e);
    }
  }

  public static Map<String, List<MetadataItemEntity>> retrieveMetadataItems(Map<String, List<MetadataItem>> metadatas,
                                                                            long authentiatedUserId) {
    if (MapUtils.isEmpty(metadatas)) {
      return null;// NOSONAR
    }
    Map<String, List<MetadataItemEntity>> fileMetadatasToPublish = new HashMap<>();
    Set<Map.Entry<String, List<MetadataItem>>> metadataEntries = metadatas.entrySet();
    for (Map.Entry<String, List<MetadataItem>> metadataEntry : metadataEntries) {
      String metadataType = metadataEntry.getKey();
      List<MetadataItem> metadataItems = metadataEntry.getValue();
      if (MapUtils.isNotEmpty(metadatas)) {
        List<MetadataItemEntity> activityMetadataEntities =
                                                          metadataItems.stream()
                                                                       .filter(metadataItem -> metadataItem.getMetadata()
                                                                                                           .getAudienceId() == 0
                                                                           || metadataItem.getMetadata()
                                                                                          .getAudienceId() == authentiatedUserId)
                                                                       .map(metadataItem -> new MetadataItemEntity(metadataItem.getId(),
                                                                                                                   metadataItem.getMetadata()
                                                                                                                               .getName(),
                                                                                                                   metadataItem.getObjectType(),
                                                                                                                   metadataItem.getObjectId(),
                                                                                                                   metadataItem.getParentObjectId(),
                                                                                                                   metadataItem.getCreatorId(),
                                                                                                                   metadataItem.getMetadata()
                                                                                                                               .getAudienceId(),
                                                                                                                   metadataItem.getProperties()))
                                                                       .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(activityMetadataEntities)) {
          fileMetadatasToPublish.put(metadataType, activityMetadataEntities);
        }
      }
    }
    return fileMetadatasToPublish;
  }

  public static List<BreadCrumbItemEntity> getBreadCrumbs (DocumentFileService documentFileService,
                                                           AbstractNode node,
                                                           long authenticatedUserId){
    try {
      return toBreadCrumbItemEntities(documentFileService.getBreadcrumb(0, node.getId(),authenticatedUserId));
    } catch (IllegalAccessException e) {
      LOG.error("Cannot get folder breadcrumb, Current user is not allowed to access the folder");
    } catch (ObjectNotFoundException e) {
      LOG.error("Cannot get folder breadcrumb, node folder not found");
    }
    return new ArrayList<BreadCrumbItemEntity>();
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
