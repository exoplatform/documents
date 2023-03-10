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
package org.exoplatform.documents.rest;

import java.util.List;
import java.util.Map;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.rest.model.AbstractNodeEntity;
import org.exoplatform.documents.rest.model.FileNodeEntity;
import org.exoplatform.documents.rest.model.NodePermissionEntity;
import org.exoplatform.documents.rest.util.EntityBuilder;
import org.exoplatform.documents.rest.util.RestUtils;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.http.PATCH;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;

@Path("/v1/documents")
@Tag(name = "/v1/documents", description = "Manages documents associated to users and spaces") // NOSONAR
public class DocumentFileRest implements ResourceContainer {

  private static final Log          LOG = ExoLogger.getLogger(DocumentFileRest.class);

  private final DocumentFileService documentFileService;

  private final SpaceService        spaceService;

  private final MetadataService     metadataService;

  private final IdentityManager     identityManager;

  private final SettingService       settingService;

  public DocumentFileRest(DocumentFileService documentFileService,
                          SpaceService spaceService,
                          IdentityManager identityManager,
                          MetadataService metadataService,
                          SettingService settingService) {
    this.documentFileService = documentFileService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.metadataService = metadataService;
    this.settingService = settingService;
  }
  
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/canAddDocument")
  @Operation(summary = "check if the current user can add document", method = "GET", description = "This checks if the current user can add document.")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"), })
  public Response canAddDocument(@Parameter(description = "Space technical identifier")
  @QueryParam("spaceId")
  String spaceId) {
    if (StringUtils.isBlank(spaceId)) {
      return Response.status(Status.BAD_REQUEST).entity("spaceId_is_mandatory").build();
    }
    String currentUserName = RestUtils.getCurrentUser();
    boolean canAdd = documentFileService.canAddDocument(spaceId, currentUserName);
    return Response.ok(String.valueOf(canAdd)).build();
  }
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Retrieves the list of document items (folders and files) for an authenticated user switch filter", method = "GET", description = "Retrieves the list of document items (folders and files) for an authenticated user switch filter.")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getDocumentItems(@Parameter(description = "Identity technical identifier")
  @QueryParam("ownerId")
  Long ownerId,
                                   @Parameter(description = "Parent folder technical identifier")
                                   @QueryParam("parentFolderId")
                                   String parentFolderId,
                                   @Parameter(description = "Symlink technical identifier")
                                   @QueryParam("symlinkFolderId")
                                   String symlinkFolderId,
                                   @Parameter(description = "Parent folder path")
                                   @QueryParam("folderPath")
                                   String folderPath,
                                   @Parameter(description = "Listing type of folder. Can be 'TIMELINE' or 'FOLDER'.")
                                   @QueryParam("listingType")
                                     FileListingType listingType,
                                   @Parameter(description = "Search query entered by the user")
                                     @QueryParam("query")
                                     String query,
                                   @Parameter(description = "extendedSearch")
                                     @QueryParam("extendedSearch")
                                     boolean extendedSearch,
                                   @Parameter(description = "userId")
                                   @QueryParam("userId")
                                   String userId,
                                   @Parameter(description = "favorites") @Schema(defaultValue = "false")
                                   @QueryParam("favorites")
                                   boolean favorites,
                                   @Parameter(description = "File properties to expand.")
                                   @QueryParam("expand")
                                   String expand,
                                   @Parameter(description = "Document items sort field")
                                   @QueryParam("sortField")
                                   String sortField,
                                   @Parameter(description = "Sort ascending or descending")
                                   @QueryParam("ascending")
                                   boolean ascending,
                                   @Parameter(description = "Offset of results to return") @Schema(defaultValue = "10")
                                   @QueryParam("offset")
                                   int offset,
                                   @Parameter(description = "Limit of results to return") @Schema(defaultValue = "10")
                                   @QueryParam("limit")
                                   int limit,
                                   @Parameter(description = "showHiddenFiles of results to return") @Schema(defaultValue = "false")
                                   @QueryParam("showHiddenFiles")
                                   boolean showHiddenFiles) {

    if (ownerId == null && StringUtils.isBlank(parentFolderId)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_folderId_is_mandatory").build();
    }
    if (listingType == null) {
      return Response.status(Status.BAD_REQUEST).entity("listingType_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      DocumentNodeFilter filter = listingType == FileListingType.TIMELINE ? new DocumentTimelineFilter(ownerId)
                                                                          : new DocumentFolderFilter(parentFolderId,
                                                                                                     folderPath,
                                                                                                     ownerId,
                                                                                                     symlinkFolderId);
      filter.setQuery(query);
      filter.setExtendedSearch(extendedSearch);
      filter.setFavorites(favorites);
      filter.setUserId(userId);
      filter.setAscending(ascending);
      filter.setSortField(DocumentSortField.getFromAlias(sortField));
      List<AbstractNode> documents = documentFileService.getDocumentItems(listingType, filter, offset, limit, userIdentityId,showHiddenFiles);
      List<AbstractNodeEntity> documentEntities = EntityBuilder.toDocumentItemEntities(documentFileService,
                                                                                       identityManager,
                                                                                       spaceService,
                                                                                       metadataService,
                                                                                       documents,
                                                                                       expand,
                                                                                       userIdentityId);
      return Response.ok(documentEntities).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized documents of owner Id '{}'", RestUtils.getCurrentUser(), ownerId, e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of documents", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/group/count")
  @Operation(summary = "Get documents groups sizes", method = "GET", description = "Get documents groups sizes")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getDocumentGroupsCount(@Parameter(description = "Identity technical identifier")
  @QueryParam("ownerId")
  Long ownerId, @QueryParam("parentFolderId")
  String parentFolderId,
                                         @Parameter(description = "Search query entered by the user")
                                         @QueryParam("query")
                                         String query,
                                         @Parameter(description = "favorites") @Schema(defaultValue = "false")
                                         @QueryParam("favorites")
                                         boolean favorites) {

    if (ownerId == null && StringUtils.isBlank(parentFolderId)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_folderId_is_mandatory").build();
    }

    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      DocumentTimelineFilter filter = new DocumentTimelineFilter(ownerId);
      filter.setQuery(query);
      filter.setFavorites(favorites);

      DocumentGroupsSize documentGroupsSize = documentFileService.getGroupDocumentsCount(filter, userIdentityId);

      return Response.ok(documentGroupsSize).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' attempts to access not authorized documents of owner Id '{}'", RestUtils.getCurrentUser(), ownerId, e);
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving list of documents", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/breadcrumb")
  @Operation(summary = "Get breadcrumb of given", method = "GET", description = "Get breadcrumb of given")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getBreadcrumb(@Parameter(description = "Identity technical identifier", required = false)
  @QueryParam("ownerId")
  Long ownerId,
                                @Parameter(description = "Folder technical identifier")
                                @QueryParam("folderId")
                                String folderId,
                                @Parameter(description = "Folder path")
                                @QueryParam("folderPath")
                                String folderPath) {

    if (ownerId == null && StringUtils.isBlank(folderId)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_folderId_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      return Response.ok(EntityBuilder.toBreadCrumbItemEntities(documentFileService.getBreadcrumb(ownerId, folderId, folderPath, userIdentityId)))
                     .build();
    } catch (IllegalAccessException e) {
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving breadcrumb", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/fullTree")
  @Operation(summary = "Get Full Tree of given folder", method = "GET", description = "Get Full Tree of given folder")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "404", description = "Not found"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getFullTreeData(@Parameter(description = "Identity technical identifier")
                                @QueryParam("ownerId")
                                        Long ownerId,
                                @Parameter(description = "Folder technical identifier")
                                @QueryParam("folderId")
                                        String folderId) {

    if (ownerId == null && StringUtils.isBlank(folderId)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_folderId_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      return Response.ok(EntityBuilder.toFullTreeItemEntities(documentFileService.getFullTreeData(ownerId, folderId, userIdentityId)))
              .build();
    } catch (IllegalAccessException e) {
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving tree folder", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/duplicate")
  @Operation(summary = "POST DUPLICATE of given document", method = "POST", description = "POST DUPLICATE of given document")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "404", description = "Not found"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response duplicateDocument(@Parameter(description = "Identity technical identifier")
                                @QueryParam("ownerId")
                                        Long ownerId,
                                @Parameter(description = "File technical identifier")
                                @QueryParam("fileId")
                                        String fileId,
                                @Parameter(description = "File prefix Clone")
                                @QueryParam("prefixClone")
                                        String prefixClone,
                                @Parameter(description = "File properties to expand.")
                                @QueryParam("expand")
                                         String expand) {

    if (ownerId == null && StringUtils.isBlank(fileId)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_FileID_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      AbstractNode abstractNode = documentFileService.duplicateDocument(ownerId, fileId, prefixClone, userIdentityId);
      AbstractNodeEntity abstractNodeEntity = EntityBuilder.toDocumentItemEntity(documentFileService,
              identityManager,
              spaceService,
              metadataService,
              abstractNode,
              expand,
              userIdentityId);
      return Response.ok(abstractNodeEntity)
              .build();
    } catch (IllegalAccessException e) {
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving duplicate file", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @PUT
  @Path("/move")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Move documents", method = "POST", description = "This rename a giving document.")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "403", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "404", description = "Resource not found")})
  public Response moveDocument (@Parameter(description = "document id") @QueryParam("documentID") String documentID,
                                @Parameter(description = "ownerId") @QueryParam("ownerId") Long ownerId,
                                @Parameter(description = "new path") @QueryParam("destPath") String destPath,
                                @Parameter(description = "conflict action name") @QueryParam("conflictAction") String conflictAction) {

    if (ownerId == null && StringUtils.isBlank(documentID)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_documentID_is_mandatory").build();
    }
    if (StringUtils.isEmpty(destPath)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Document destination path should not be empty").build();
    }
    try {
      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      documentFileService.moveDocument(ownerId, documentID, destPath, userIdentityId, conflictAction);
      return Response.ok().build();
    } catch (ObjectAlreadyExistsException e) {
      LOG.warn("Document with same name already exist", e);
      return Response.status(HTTPStatus.CONFLICT).entity(e.getExistingObject())
                                                 .type(MediaType.APPLICATION_JSON)
                                                 .build();
    } catch (Exception ex) {
      LOG.warn("Failed to rename Document", ex);
      return Response.status(HTTPStatus.INTERNAL_ERROR).build();
    }
  }

  @POST
  @Path("/folder")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Add a new Folder", method = "POST", description = "This adds a new Folder under givin Folder.")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "403", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "404", description = "Resource not found")})
  public Response createFolder (@Parameter(description = "parent id") @QueryParam("parentid") String parentid,
                                @Parameter(description = "folder Path") @QueryParam("folderPath") String folderPath,
                                @Parameter(description = "owner id") @QueryParam("ownerId") Long ownerId,
                                @Parameter(description = "folder name") @QueryParam("name") String name) {

    if (ownerId == null && StringUtils.isBlank(parentid)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_parentid_is_mandatory").build();
    }
    if (StringUtils.isEmpty(name)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Folder Name should not be empty").build();
    }
    try {
      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      AbstractNode createdFolder = documentFileService.createFolder(ownerId, parentid, folderPath, name, userIdentityId);
      AbstractNodeEntity abstractNodeEntity = EntityBuilder.toDocumentItemEntity(documentFileService,
                                                                                 identityManager,
                                                                                 spaceService,
                                                                                 metadataService,
                                                                                 createdFolder,
                                                                                 null,
                                                                                 userIdentityId);
      return Response.ok(abstractNodeEntity).build();
    } catch (ObjectAlreadyExistsException e) {
      LOG.warn("Folder with same name already exists", e);
      return Response.status(HTTPStatus.CONFLICT).build();
    } catch (Exception ex) {
      LOG.warn("Failed to create Folder", ex);
      return Response.status(HTTPStatus.INTERNAL_ERROR).build();
    }
  }

  @GET
  @Path("/newname")
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
    @Operation(
            summary = "propose a new name for Folder is there is already a folder with the provided name",
            method = "GET",
            description = "propose a new name for Folder is there is already a folder with the provided name")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "403", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "404", description = "Resource not found")})
    public Response getNewName (@Parameter(description = "parent id") @QueryParam("parentid") String parentid,
                                @Parameter(description = "folder Path") @QueryParam("folderPath") String folderPath,
                                @Parameter(description = "ownerId") @QueryParam("ownerId") Long ownerId,
                                @Parameter(description = "folder name") @QueryParam("name") String name) {

    if (ownerId == null && StringUtils.isBlank(parentid)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_parentid_is_mandatory").build();
    }
    if (StringUtils.isEmpty(name)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Folder Name should not be empty").build();
    }
    if (NumberUtils.isNumber(name)) {
      LOG.warn("Folder Name should not be number");
      return Response.status(Response.Status.BAD_REQUEST).entity("Folder Name should not be number").build();
    }
    try {
        return Response.ok(documentFileService.getNewName(ownerId, parentid, folderPath, name)).build();
      } catch (Exception ex) {
        LOG.warn("Failed to propose new Folder name", ex);
        return Response.status(HTTPStatus.INTERNAL_ERROR).build();
      }
    }

  @PUT
  @Path("/rename")
  @RolesAllowed("users")
  @Operation(summary = "Rename documents", method = "POST", description = "This rename a giving document.")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "403", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "404", description = "Resource not found")})
  public Response renameDocument (@Parameter(description = "document id") @QueryParam("documentID") String documentID,
                                @Parameter(description = "ownerId") @QueryParam("ownerId") Long ownerId,
                                @Parameter(description = "new name") @QueryParam("newName") String newName) {

    if (ownerId == null && StringUtils.isBlank(documentID)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_documentID_is_mandatory").build();
    }
    if (StringUtils.isEmpty(newName)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Document Name should not be empty").build();
    }
    try {
      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      documentFileService.renameDocument(ownerId, documentID, newName, userIdentityId);
      return Response.ok().build();
    } catch (ObjectAlreadyExistsException ex) {
      LOG.warn("Document with same name already exists", ex);
      return Response.status(HTTPStatus.CONFLICT).build();
    } catch (Exception ex) {
      LOG.warn("Failed to rename Document", ex);
      return Response.status(HTTPStatus.INTERNAL_ERROR).build();
    }
  }

  @DELETE
  @Path("{documentId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Delete document", method = "DELETE", description = "This deletes document")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Document deleted"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "User not authorized to delete the document"),
          @ApiResponse(responseCode = "500", description = "Internal server error") })
  public Response deleteDocument(@Parameter(description = "Document id", required = true)
                                 @PathParam("documentId") String documentId,
                                 @Parameter(description = "folder path", required = true)
                                 @QueryParam("document path") String documentPath,
                                 @Parameter(description = "Is favorite document", required = false)
                                 @QueryParam("favorite") boolean favorite,
                                 @Parameter(description = "Time to effectively delete document", required = false)
                                 @QueryParam("delay") long delay) {
    if (StringUtils.isBlank(documentId)) {
      return Response.status(Status.BAD_REQUEST).entity("document_id_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      documentFileService.deleteDocument(documentPath,documentId,  favorite, delay, userIdentityId);
      return Response.ok().build();
    } catch (IllegalAccessException e) {
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.error("Error when deleting the news target with name " + userIdentityId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @Path("{documentId}/undoDelete")
  @POST
  @RolesAllowed("users")
  @Operation(summary = "Undo deleting document if not yet effectively deleted", method = "POST", description = "Undo deleting document if not yet effectively deleted")
  @ApiResponses(value = {@ApiResponse(responseCode = "400", description = "Invalid query input"),
                         @ApiResponse(responseCode = "403", description = "Forbidden operation"), })
  public Response undoDeleteDocument(@Parameter(description = "Document identifier", required = true)
                                     @PathParam("documentId") String documentId) {
    if (StringUtils.isBlank(documentId)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("document_id_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    documentFileService.undoDeleteDocument(documentId, userIdentityId);
    return Response.noContent().build();
  }
  @Path("permissions")
  @POST
  @RolesAllowed("users")
  @Operation(summary = "Undo deleting document if not yet effectively deleted", method = "POST", description = "Undo deleting document if not yet effectively deleted")
  @ApiResponses(value = {@ApiResponse(responseCode = "400", description = "Invalid query input"),
                         @ApiResponse(responseCode = "403", description = "Forbidden operation"), })
  public Response updatePermissions( @Parameter(description = "Permission object", required = true)
                                             FileNodeEntity nodeEntity) {

    if (nodeEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("node_object_is_mandatory").build();
    }
    NodePermissionEntity nodePermissionEntity = nodeEntity.getAcl();

    if (nodePermissionEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("permissions_object_is_mandatory").build();
    }
    SettingValue<?> sharedDocumentSettingValue = settingService.get(Context.GLOBAL.id("sharedDocumentStatus"),
                                                                    Scope.APPLICATION.id("sharedDocumentStatus"),
                                                                    "exo:sharedDocumentStatus");
    boolean isSharedDocumentSuspended = sharedDocumentSettingValue != null && !sharedDocumentSettingValue.getValue().toString().isEmpty() ? Boolean.valueOf(sharedDocumentSettingValue.getValue().toString()) : false;
    if (isSharedDocumentSuspended){
      //return forbidden response status if the share documents forbidden by the administrators
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);

    try {
      documentFileService.updatePermissions(nodeEntity.getId(),EntityBuilder.toNodePermission(nodeEntity, documentFileService, spaceService, identityManager), userIdentityId);
    } catch (IllegalAccessException e) {
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    }
    return Response.noContent().build();
  }

  @PUT
  @Path("/description")
  @RolesAllowed("users")
  @Operation(summary = "update or create a document's description", method = "PUT", description = "This creates or updates a given document's description.")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "403", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "404", description = "Resource not found") })
  public Response updateDocumentDescription(@Parameter(description = "owner id", required = true)
  @QueryParam("ownerId")
  long ownerId,
                                            @Parameter(description = "document id", required = true)
                                            @QueryParam("documentId")
                                            String documentId,
                                            @Parameter(description = "document id", required = true)
                                            @QueryParam("description")
                                            String description) {
    try {
      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      documentFileService.updateDocumentDescription(ownerId, documentId, description, userIdentityId);
      return Response.noContent().build();
    } catch (Exception ex) {
      LOG.warn("Failed to update document description", ex);
      return Response.status(HTTPStatus.INTERNAL_ERROR).build();
    }

  }

  @POST
  @Path("/shortcut")
  @RolesAllowed("users")
  @Operation(summary = "document shortcut", method = "POST", description = "Creates a document shortcut.")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "403", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "404", description = "Resource not found")})
  public Response createShortcut (@Parameter(description = "document id") @QueryParam("documentID") String documentID,
                                  @Parameter(description = "new path") @QueryParam("destPath") String destPath,
                                  @Parameter(description = "conflict action name") @QueryParam("conflictAction") String conflictAction) {

    if (StringUtils.isEmpty(documentID)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Document's id should not be empty").build();
    }
    if (StringUtils.isEmpty(destPath)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Document destination path should not be empty").build();
    }
    try {
      documentFileService.createShortcut(documentID, destPath, RestUtils.getCurrentUser(), conflictAction);
      return Response.ok().build();
    } catch (ObjectAlreadyExistsException e) {
      LOG.warn("Document with same name already exists", e);
      return Response.status(HTTPStatus.CONFLICT).build();
    } catch (Exception ex) {
      LOG.warn("Failed to create document shortcut", ex);
      return Response.status(HTTPStatus.INTERNAL_ERROR).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/versions")
  @Operation(summary = "Get versions list of a a given document", method = "GET", description = "Get versions list of a a given document")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "404", description = "Not found"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getFileVersions(@Parameter(description = "Identity technical identifier", required = true)
                                  @QueryParam("fileId") String fileId) {

    if (StringUtils.isBlank(fileId)) {
      return Response.status(Status.BAD_REQUEST).entity("file id is mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (userIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      return Response.ok(EntityBuilder.toVersionEntities(documentFileService.getFileVersions(fileId, RestUtils.getCurrentUser())))
              .build();
    } catch (IllegalArgumentException e) {
      return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving versions list", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @PATCH
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/versions")
  @Operation(
          summary = "update version summary of a give document version",
          method = "GET",
          description = "update version summary of a give document version")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "404", description = "Not found"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response updateVersionSummary(@Parameter(description = "version summary to update", required = true) Map<String,String> summary,
                                       @Parameter(description = "original file node identifier", required = true)
                                       @QueryParam("originFileId") String originFileId,
                                       @Parameter(description = "version file node identifier", required = true)
                                       @QueryParam("versionId") String versionId) {

    if (StringUtils.isBlank(versionId) || StringUtils.isBlank(originFileId)) {
      return Response.status(Status.BAD_REQUEST).entity("version fil id and original file id are mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (userIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      return Response.ok(EntityBuilder.toVersionEntity(documentFileService.updateVersionSummary(originFileId,
                                                                                                versionId,
                                                                                                summary.get("value"),
                                                                                                RestUtils.getCurrentUser())))
                     .build();
    } catch (IllegalArgumentException e) {
      return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error while updating version summary", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/versions")
  @Operation(
          summary = "Restore a document to a specific version",
          method = "GET",
          description = "Restore a document to a specific version")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "404", description = "Not found"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response restoreVersion(@Parameter(description = "version file node identifier", required = true)
                                 @QueryParam("versionId") String versionId) {

    if (StringUtils.isBlank(versionId)) {
      return Response.status(Status.BAD_REQUEST).entity("version fil id is mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (userIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      return Response.ok(EntityBuilder.toVersionEntity(documentFileService.restoreVersion(versionId, RestUtils.getCurrentUser())))
                     .build();
    } catch (IllegalArgumentException e) {
      return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error while restoring version", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

}

