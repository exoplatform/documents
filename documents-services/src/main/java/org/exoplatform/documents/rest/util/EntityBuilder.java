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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.rest.model.*;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.documents.service.PublicDocumentAccessService;
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

public class EntityBuilder {
  private static final Log    LOG                       = ExoLogger.getExoLogger(EntityBuilder.class);

  private static final String FILE_METADATA_OBJECT_TYPE = "file";

  private static final String        SPACE_PATH_PREFIX = "/Groups/spaces/";

  public static final String         USER_PRIVATE_ROOT_NODE           = "/Private";

  public static final String         USER_PUBLIC_ROOT_NODE           = "/Public";

  public static final String         GROUP_PROVIDER_ID               = "group";

  private EntityBuilder() {
  }

  public static List<AbstractNodeEntity> toDocumentItemEntities(DocumentFileService documentFileService,
                                                                IdentityManager identityManager,
                                                                SpaceService spaceService,
                                                                MetadataService metadataService,
                                                                PublicDocumentAccessService publicDocumentAccessService,
                                                                List<AbstractNode> documents,
                                                                String expand,
                                                                long authenticatedUserId) {
    return documents.stream()
                    .map(document -> toDocumentItemEntity(documentFileService,
                                                          identityManager,
                                                          spaceService,
                                                          metadataService,
                            publicDocumentAccessService,
                                                          document,
                                                          expand,
                                                          authenticatedUserId))
                    .collect(Collectors.toList());
  }

  public static List<AbstractNode> toAbstractNodes(List<AbstractNodeEntity> documents) {
    return documents.stream().map(EntityBuilder::toAbstractNode).toList();
  }

  public static AbstractNode toAbstractNode(AbstractNodeEntity document) {

    if (document.isFolder()) {
      AbstractNode node = new FolderNode();
      node.setId(document.getId());
      node.setPath(document.getPath());
      node.setName(document.getName());
      return node;
    } else {
      AbstractNode node = new FileNode();
      node.setId(document.getId());
      node.setPath(document.getPath());
      node.setName(document.getName());
      return node;
    }
  }

  public static AbstractNodeEntity toDocumentItemEntity(DocumentFileService documentFileService,
                                                        IdentityManager identityManager,
                                                        SpaceService spaceService,
                                                        MetadataService metadataService,
                                                        PublicDocumentAccessService publicDocumentAccessService,
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
              publicDocumentAccessService,
                          (FileNode) document,
                          expandProperties,
                          authenticatedUserId);
    } else if (document instanceof FolderNode) {
      return toFolderEntity(documentFileService,
                            identityManager,
                            spaceService,
                            metadataService,
              publicDocumentAccessService,
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
                                            PublicDocumentAccessService publicDocumentAccessService,
                                            FileNode file,
                                            List<String> expandProperties,
                                            long authenticatedUserId) {
    FileNodeEntity fileEntity = new FileNodeEntity();
    toNode(documentFileService,
           identityManager,
           spaceService,
           metadataService,
            publicDocumentAccessService,
           file,
           fileEntity,
           expandProperties,
           authenticatedUserId);
    fileEntity.setLinkedFileId(file.getLinkedFileId());
    fileEntity.setVersionnedFileId(file.getVersionnedFileId());
    fileEntity.setMimeType(file.getMimeType());
    fileEntity.setSize(file.getSize());
    fileEntity.setViews(file.getViews());
    if (expandProperties.contains("versions")) {
      fileEntity.setVersions(toVersionEntities(documentFileService.getFileVersions(file.getId(), RestUtils.getCurrentUser())));
    }
    return fileEntity;
  }

  public static FileVersionsEntity toVersionEntities(List<FileVersion> versions) {
    List<FileVersionEntity> versionEntities = versions.stream().map(EntityBuilder::toVersionEntity).collect(Collectors.toList());
    return new FileVersionsEntity(versionEntities, versionEntities.size(), 0L, 0L);
  }
  
  public static FileVersionEntity toVersionEntity(FileVersion fileVersion) {
    FileVersionEntity fileVersionEntity = new FileVersionEntity();
    fileVersionEntity.setId(fileVersion.getId());
    fileVersionEntity.setFrozenId(fileVersion.getFrozenId());
    fileVersionEntity.setOriginId(fileVersion.getOriginId());
    fileVersionEntity.setVersionNumber(fileVersion.getVersionNumber());
    fileVersionEntity.setTitle(fileVersion.getTitle());
    fileVersionEntity.setCreatedDate(fileVersion.getCreatedDate());
    fileVersionEntity.setSize(fileVersion.getSize());
    fileVersionEntity.setAuthor(fileVersion.getAuthor());
    fileVersionEntity.setAuthorFullName(fileVersion.getAuthorFullName());
    fileVersionEntity.setSummary(fileVersion.getSummary());
    fileVersionEntity.setCurrent(fileVersion.isCurrent());
    return  fileVersionEntity;
  }
  public static FolderNodeEntity toFolderEntity(DocumentFileService documentFileService,
                                                IdentityManager identityManager,
                                                SpaceService spaceService,
                                                MetadataService metadataService,
                                                PublicDocumentAccessService publicDocumentAccessService,
                                                FolderNode folder,
                                                List<String> expandProperties,
                                                long authenticatedUser) {
    FolderNodeEntity folderEntity = new FolderNodeEntity();
    toNode(documentFileService,
           identityManager,
           spaceService,
           metadataService,
            publicDocumentAccessService,
           folder,
           folderEntity,
           expandProperties,
           authenticatedUser);
    return folderEntity;
  }

  public static List<BreadCrumbItemEntity> toBreadCrumbItemEntities(List<BreadCrumbItem> folders) {
    List<BreadCrumbItemEntity>  brList = new ArrayList<BreadCrumbItemEntity>();
    brList = folders.stream()
                    .map(document -> new BreadCrumbItemEntity(document.getId(),
                                                              document.getName(),
                                                              document.getPath(),
                                                              document.isSymlink(),
                                                              document.getAccessList()))
                    .collect(Collectors.toList());
    Collections.reverse(brList);
    return brList;
  }

  public static List<FullTreeItemEntity> toFullTreeItemEntities(List<FullTreeItem> folders) {
    List<FullTreeItemEntity>  brList = new ArrayList<>();
    brList = folders.stream().map(document -> new FullTreeItemEntity(document.getId(), document.getName(), document.getPath(),document.getChildren())).collect(Collectors.toList());
    Collections.reverse(brList);
    return brList;
  }

  private static void toNode(DocumentFileService documentFileService,
                             IdentityManager identityManager,
                             SpaceService spaceService,
                             MetadataService metadataService,
                             PublicDocumentAccessService publicDocumentAccessService,
                             AbstractNode node,
                             AbstractNodeEntity nodeEntity,
                             List<String> expandProperties,
                             long authenticatedUserId) {
    try {
      nodeEntity.setId(node.getId());
      nodeEntity.setPath(node.getPath());
      nodeEntity.setName(encodeName(node));
      nodeEntity.setDatasource(node.getDatasource());
      nodeEntity.setDescription(node.getDescription());
      nodeEntity.setAcl(toNodePermissionEntity(node,identityManager, spaceService, publicDocumentAccessService));
      nodeEntity.setCreatedDate(node.getCreatedDate());
      nodeEntity.setModifiedDate(node.getModifiedDate());
      nodeEntity.setParentFolderId(node.getParentFolderId());
      nodeEntity.setSourceID(node.getSourceID());
      nodeEntity.setCloudDriveFolder(node.isCloudDriveFolder());
      nodeEntity.setCloudDriveFile(node.isCloudDriveFile());
      nodeEntity.setVersionNumber(node.getVersionNumber());
      nodeEntity.setVersionable(node.isVersionable());
      if ((node instanceof FolderNode)) {
        ((FolderNodeEntity)nodeEntity).setPath(((FolderNode)node).getPath());
      }

      if (expandProperties.contains("creator") && node.getCreatorId() > 0) {
        nodeEntity.setCreatorIdentity(toIdentityEntity(identityManager, spaceService, node.getCreatorId()));
      }
      if (expandProperties.contains("modifier") && node.getModifierId() > 0) {
        nodeEntity.setModifierIdentity(toIdentityEntity(identityManager, spaceService, node.getModifierId()));
      }
      if (expandProperties.contains("owner") && node.getOwnerId() > 0) {
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

  /**
   * Decode node name if it is already encoded
   * @param node its name
   * @return decoded node name
   */
  private static String encodeName(AbstractNode node) {
    String nodeName = node.getName();
    if(StringUtils.isNotBlank(nodeName)) {
      try {
        nodeName = URLDecoder.decode(node.getName(), StandardCharsets.UTF_8);
      } catch (IllegalArgumentException iae) {
        // nothing to do
      }
    }
    return nodeName;
  }

  private static NodePermissionEntity toNodePermissionEntity(AbstractNode node,
                                                             IdentityManager identityManager,
                                                             SpaceService spaceService,
                                                             PublicDocumentAccessService publicDocumentAccessService) {
    String path = node.getPath();
    NodePermission nodePermission = node.getAcl();
    if(nodePermission == null) return null;
    boolean allCanRead = false;
    boolean allCanEdit = false;
    Identity identity = getOwnerIdentityFromNodePath(path, identityManager, spaceService);
    Map<String, PermissionEntryEntity> map = new HashMap<>();
    List<PermissionEntry> permissions = nodePermission.getPermissions();
    for(PermissionEntry permissionEntry : permissions){
      if(permissionEntry.getIdentity().getId().equals(String.valueOf(node.getCreatorId()))){
        continue;
      }
      if(identity != null && permissionEntry.getIdentity().getId().equals(identity.getId())){
        if (permissionEntry.getPermission().equals("read") && permissionEntry.getRole().equals(PermissionRole.ALL.name())){
          allCanRead = true;
        }
        if (permissionEntry.getRole().equals(PermissionRole.ALL.name()) && isEditPermission(permissionEntry.getPermission())){
          allCanEdit = true;
        }
      } else {
        PermissionEntryEntity permissionEntry_ = map.get(permissionEntry.getIdentity().getRemoteId());
        if(permissionEntry_ == null){
          map.put(permissionEntry.getIdentity().getRemoteId(),toPermissionEntryEntity(permissionEntry, spaceService));
        } else{
          if(isEditPermission(permissionEntry.getPermission()) && !isEditPermission(permissionEntry_.getPermission()) ){
            map.put(permissionEntry.getIdentity().getRemoteId(),toPermissionEntryEntity(permissionEntry, spaceService));
          }
        }

      }
    }
    String visibilityChoice = allCanRead ? Visibility.ALL_MEMBERS.name() : Visibility.SPECIFIC_COLLABORATOR.name();
    if (publicDocumentAccessService.hasDocumentPublicAccess(node.getId())) {
      visibilityChoice = Visibility.COLLABORATORS_AND_PUBLIC_ACCESS.name();
    }
    return new NodePermissionEntity(nodePermission.isCanAccess(),nodePermission.isCanEdit(),nodePermission.isCanDelete(), allCanEdit, visibilityChoice, new ArrayList<>(map.values()));
  }
  private static PermissionEntryEntity toPermissionEntryEntity(PermissionEntry permissionEntry, SpaceService spaceService){
    if(permissionEntry == null) return null;
    String permission = "read";
    if(isEditPermission(permissionEntry.getPermission())){
      permission = "edit";
    }
    return new PermissionEntryEntity(toIdentityEntity(permissionEntry.getIdentity(),spaceService), permission);
  }
  public static NodePermission toNodePermission(AbstractNodeEntity node, DocumentFileService documentFileService, SpaceService spaceService, IdentityManager identityManager){
    if(node.getAcl() == null) return null;
    NodePermissionEntity nodePermissionEntity = node.getAcl();
    Identity identity = getOwnerIdentityFromNodePath(node.getPath(), identityManager, spaceService);
    if(identity!=null){
      List<PermissionEntryEntity> collaborators = nodePermissionEntity.getCollaborators();
      List<PermissionEntry> permissions = new ArrayList<>();
      Map<Long,String> toShare = new HashMap<>();
      Map<Long,String> toNotify = new HashMap<>();
      String invitedGroupId = null;

      for(PermissionEntryEntity permissionEntryEntity : collaborators){
        Identity ownerId = getOwnerIdentityFromNodePath(node.getPath(), identityManager, spaceService);
        if(ownerId != null && !ownerId.getId().equals(permissionEntryEntity.getIdentity().getId())) {
          if (permissionEntryEntity.getIdentity().getProviderId().equals("space")) {
            toShare.put(Long.valueOf(identityManager.getOrCreateSpaceIdentity(permissionEntryEntity.getIdentity().getRemoteId()).getId()), permissionEntryEntity.getPermission());
            permissions.add(toPermissionEntry(permissionEntryEntity, identityManager));
          } else if(permissionEntryEntity.getIdentity().getProviderId().equals("group")){
              permissions.add(toPermissionEntry(permissionEntryEntity, identityManager));
          } else {
            try {
              //check if the owner is a space and the destination is a member of this space
              if (ownerId.isSpace() && spaceService.isMember(spaceService.getSpaceByPrettyName(ownerId.getRemoteId()), permissionEntryEntity.getIdentity().getRemoteId())) {
                toNotify.put(Long.valueOf(identityManager.getOrCreateUserIdentity(permissionEntryEntity.getIdentity().getRemoteId()).getId()),permissionEntryEntity.getPermission());
              } else {
                toShare.put(Long.valueOf(identityManager.getOrCreateUserIdentity(permissionEntryEntity.getIdentity().getRemoteId()).getId()),permissionEntryEntity.getPermission());
              }
              permissions.add(toPermissionEntry(permissionEntryEntity, identityManager));
            } catch (Exception exception) {
              LOG.error(exception.getMessage(), exception);
            }
          }
        }
      }
      if(nodePermissionEntity.getVisibilityChoice().equals(Visibility.ALL_MEMBERS.name())){
        permissions.add(new PermissionEntry(identity,"read", PermissionRole.ALL.name()));
        if(nodePermissionEntity.isAllMembersCanEdit()){
          permissions.add(new PermissionEntry(identity,"edit",PermissionRole.ALL.name()));
        } else {
          permissions.add(new PermissionEntry(identity,"edit",PermissionRole.MANAGERS_REDACTORS.name()));
        }
      }
      return new NodePermission(nodePermissionEntity.isCanAccess(),nodePermissionEntity.isCanEdit(),nodePermissionEntity.isCanDelete(),permissions,toShare, toNotify);
    }
    return null;
  }

  public static Identity getOwnerIdentityFromNodePath(String path, IdentityManager identityManager, SpaceService spaceService){
    Identity identity = null;
    if (path.contains(SPACE_PATH_PREFIX)) {
      String[] pathParts = path.split(SPACE_PATH_PREFIX)[1].split("/");
      String groupId = "/spaces/" + pathParts[0];
      Space space = spaceService.getSpaceByGroupId(groupId);
      if(space != null){
        identity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
      }
    } else if(path.contains(USER_PRIVATE_ROOT_NODE)) {
      String[] pathParts = path.split(USER_PRIVATE_ROOT_NODE)[0].split("/");
      String userName = pathParts[pathParts.length-1];
      identity = identityManager.getOrCreateUserIdentity(userName);
    } else if(path.contains(USER_PUBLIC_ROOT_NODE)) {
      String[] pathParts = path.split(USER_PUBLIC_ROOT_NODE)[0].split("/");
      String userName = pathParts[pathParts.length-1];
      identity = identityManager.getOrCreateUserIdentity(userName);
    }
    return identity;
  }
  private static PermissionEntry toPermissionEntry(PermissionEntryEntity permissionEntryEntity, IdentityManager identityManager){
    if(permissionEntryEntity == null) return null;
    Identity identity = null;
    if(permissionEntryEntity.getIdentity().getProviderId().equals("space")){
      identity = identityManager.getOrCreateSpaceIdentity(permissionEntryEntity.getIdentity().getRemoteId());
    } else if (permissionEntryEntity.getIdentity().getProviderId().equals("group")) {
      identity = new Identity(permissionEntryEntity.getIdentity().getProviderId(), permissionEntryEntity.getIdentity().getRemoteId());
    } else {
      identity = identityManager.getOrCreateUserIdentity(permissionEntryEntity.getIdentity().getRemoteId());
    }
    if(identity == null) return null;
    return new PermissionEntry(identity, permissionEntryEntity.getPermission(),PermissionRole.ALL.name());
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
      return toBreadCrumbItemEntities(documentFileService.getBreadcrumb(0, node.getId(),"",authenticatedUserId));
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
      identityEntity.setFullname(identity.getProfile().getFullName());
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

  public static IdentityEntity toIdentityEntity(Identity identity, SpaceService spaceService) {
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
    } else if (identity.getProviderId().equals(GROUP_PROVIDER_ID))
      identityEntity.setName(identity.getProfile().getFullName());
    return identityEntity;
  }
  private static boolean isEditPermission(String permission){
    return  permission.contains("add_node") || permission.contains("set_property") || permission.contains("remove") ? true : false;
  }

  public static PublicDocumentAccessEntity toPublicDocumentAccessEntity(PublicDocumentAccess publicDocumentAccess) {
    PublicDocumentAccessEntity publicDocumentAccessEntity = new PublicDocumentAccessEntity();
    publicDocumentAccessEntity.setNodeId(publicDocumentAccess.getNodeId());
    publicDocumentAccessEntity.setExpirationDate(publicDocumentAccess.getExpirationDate());
    publicDocumentAccessEntity.setHasPassword(publicDocumentAccess.getPasswordHashKey() != null);
    return publicDocumentAccessEntity;
  }
}
