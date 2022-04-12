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

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.rest.model.AbstractNodeEntity;
import org.exoplatform.documents.rest.model.FileNodeEntity;
import org.exoplatform.documents.rest.model.IdentityEntity;
import org.exoplatform.documents.rest.model.NodePermissionEntity;
import org.exoplatform.documents.rest.util.EntityBuilder;
import org.exoplatform.documents.rest.util.RestUtils;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataService;

import io.swagger.annotations.*;

@Path("/v1/documents")
@Api(value = "/v1/documents", description = "Manages documents associated to users and spaces") // NOSONAR
public class DocumentFileRest implements ResourceContainer {

  private static final Log          LOG = ExoLogger.getLogger(DocumentFileRest.class);

  private final DocumentFileService documentFileService;

  private final SpaceService        spaceService;

  private final MetadataService     metadataService;

  private final IdentityManager     identityManager;

  public DocumentFileRest(DocumentFileService documentFileService,
                          SpaceService spaceService,
                          IdentityManager identityManager,
                          MetadataService metadataService) {
    this.documentFileService = documentFileService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.metadataService = metadataService;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Retrieves the list of document items (folders and files) for an authenticated user switch filter.", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Not found"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response getDocumentItems(@ApiParam(value = "Identity technical identifier", required = false)
  @QueryParam("ownerId")
  Long ownerId,
                                   @ApiParam(value = "Parent folder technical identifier", required = false)
                                   @QueryParam("parentFolderId")
                                   String parentFolderId,
                                   @ApiParam(value = "Parent folder path", required = false)
                                   @QueryParam("folderPath")
                                   String folderPath,
                                   @ApiParam(value = "Listing type of folder. Can be 'TIMELINE' or 'FOLDER'.", required = false)
                                   @QueryParam("listingType")
                                   FileListingType listingType,
                                   @ApiParam(value = "Search query entered by the user", required = false)
                                   @QueryParam("query")
                                   String query,
                                   @ApiParam(value = "userId", required = false)
                                   @QueryParam("userId")
                                   String userId,
                                   @ApiParam(value = "favorites", required = false, defaultValue = "false")
                                   @QueryParam("favorites")
                                   boolean favorites,
                                   @ApiParam(value = "File properties to expand.", required = false)
                                   @QueryParam("expand")
                                   String expand,
                                   @ApiParam(value = "Document items sort field", required = false)
                                   @QueryParam("sortField")
                                   String sortField,
                                   @ApiParam(value = "Sort ascending or descending", required = false)
                                   @QueryParam("ascending")
                                   boolean ascending,
                                   @ApiParam(value = "Offset of results to return", required = false, defaultValue = "10")
                                   @QueryParam("offset")
                                   int offset,
                                   @ApiParam(value = "Limit of results to return", required = false, defaultValue = "10")
                                   @QueryParam("limit")
                                   int limit) {

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
                                                                                                     ownerId);
      filter.setQuery(query);
      filter.setFavorites(favorites);
      filter.setUserId(userId);
      filter.setAscending(ascending);
      filter.setSortField(DocumentSortField.getFromAlias(sortField));

      List<AbstractNode> documents = documentFileService.getDocumentItems(listingType, filter, offset, limit, userIdentityId);
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
  @ApiOperation(value = "Get documents groups sizes.", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Not found"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response getDocumentGroupsCount(@ApiParam(value = "Identity technical identifier", required = false)
  @QueryParam("ownerId")
  Long ownerId, @QueryParam("parentFolderId")
  String parentFolderId,
                                         @ApiParam(value = "Search query entered by the user", required = false)
                                         @QueryParam("query")
                                         String query,
                                         @ApiParam(value = "favorites", required = false, defaultValue = "false")
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
  @ApiOperation(value = "Get breadcrumb of given .", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Not found"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response getBreadcrumb(@ApiParam(value = "Identity technical identifier", required = false)
  @QueryParam("ownerId")
  Long ownerId,
                                @ApiParam(value = "Folder technical identifier", required = false)
                                @QueryParam("folderId")
                                String folderId,
                                @ApiParam(value = "Folder path", required = false)
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
  @ApiOperation(value = "Get Full Tree of given .", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Not found"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response getFullTreeData(@ApiParam(value = "Identity technical identifier", required = false)
                                @QueryParam("ownerId")
                                        Long ownerId,
                                @ApiParam(value = "Folder technical identifier", required = false)
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
  @ApiOperation(value = "POST DUPLICATE of given .", httpMethod = "POST", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.NOT_FOUND, message = "Not found"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"), })
  public Response duplicateDocument(@ApiParam(value = "Identity technical identifier", required = false)
                                @QueryParam("ownerId")
                                        Long ownerId,
                                @ApiParam(value = "File technical identifier", required = false)
                                @QueryParam("fileId")
                                        String fileId,
                                @ApiParam(value = "File properties to expand.", required = false)
                                @QueryParam("expand")
                                         String expand) {

    if (ownerId == null && StringUtils.isBlank(fileId)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_FileID_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      AbstractNode abstractNode = documentFileService.duplicateDocument(ownerId, fileId, userIdentityId);
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
  @RolesAllowed("users")
  @ApiOperation(value = "Move documents", httpMethod = "POST", response = Response.class, notes = "This rename a giving document.")
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Request fulfilled"),
          @ApiResponse(code = 400, message = "Invalid query input"), @ApiResponse(code = 403, message = "Unauthorized operation"),
          @ApiResponse(code = 404, message = "Resource not found")})
  public Response moveDocument (@ApiParam(value = "documentID", required = false) @QueryParam("documentID") String documentID,
                                  @ApiParam(value = "ownerId", required = false) @QueryParam("ownerId") Long ownerId,
                                  @ApiParam(value = "new path", required = false) @QueryParam("destPath") String destPath) {

    if (ownerId == null && StringUtils.isBlank(documentID)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_documentID_is_mandatory").build();
    }
    if (StringUtils.isEmpty(destPath)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Document destination path should not be empty").build();
    }
    try {
      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      documentFileService.moveDocument(ownerId, documentID, destPath, userIdentityId);
      return Response.ok().build();
    } catch (Exception ex) {
      LOG.warn("Failed to rename Document", ex);
      return Response.status(HTTPStatus.INTERNAL_ERROR).build();
    }
  }

  @POST
  @Path("/folder")
  @RolesAllowed("users")
  @ApiOperation(value = "Add a new Folder", httpMethod = "POST", response = Response.class, notes = "This adds a new Folder under givin Folder.")
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Request fulfilled"),
          @ApiResponse(code = 400, message = "Invalid query input"), @ApiResponse(code = 403, message = "Unauthorized operation"),
          @ApiResponse(code = 404, message = "Resource not found")})
  public Response createFolder (@ApiParam(value = "parentid", required = false) @QueryParam("parentid") String parentid,
                                @ApiParam(value = "folderPath", required = false) @QueryParam("folderPath") String folderPath,
                                @ApiParam(value = "ownerId", required = false) @QueryParam("ownerId") Long ownerId,
                                @ApiParam(value = "folder name", required = false) @QueryParam("name") String name) {

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
      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
        documentFileService.createFolder(ownerId, parentid, folderPath, name, userIdentityId);
        return Response.ok().build();
      } catch (Exception ex) {
        LOG.warn("Failed to create Folder", ex);
        return Response.status(HTTPStatus.INTERNAL_ERROR).build();
      }
    }

  @GET
  @Path("/newname")
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @ApiOperation(value = "propose a new name for Folder is there is already a folder with the provided name", httpMethod = "GET", response = Response.class, notes = "propse a new name for Folder is there is already a folder with the provided name")
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Request fulfilled"),
          @ApiResponse(code = 400, message = "Invalid query input"), @ApiResponse(code = 403, message = "Unauthorized operation"),
          @ApiResponse(code = 404, message = "Resource not found")})
  public Response getNewName (@ApiParam(value = "parentid", required = false) @QueryParam("parentid") String parentid,
                                @ApiParam(value = "folderPath", required = false) @QueryParam("folderPath") String folderPath,
                                @ApiParam(value = "ownerId", required = false) @QueryParam("ownerId") Long ownerId,
                                @ApiParam(value = "folder name", required = false) @QueryParam("name") String name) {

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
  @ApiOperation(value = "Rename documents", httpMethod = "POST", response = Response.class, notes = "This rename a giving document.")
  @ApiResponses(value = {@ApiResponse(code = 200, message = "Request fulfilled"),
          @ApiResponse(code = 400, message = "Invalid query input"), @ApiResponse(code = 403, message = "Unauthorized operation"),
          @ApiResponse(code = 404, message = "Resource not found")})
  public Response renameDocument (@ApiParam(value = "documentID", required = false) @QueryParam("documentID") String documentID,
                                @ApiParam(value = "ownerId", required = false) @QueryParam("ownerId") Long ownerId,
                                @ApiParam(value = "new name", required = false) @QueryParam("newName") String newName) {

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
    } catch (Exception ex) {
      LOG.warn("Failed to rename Document", ex);
      return Response.status(HTTPStatus.INTERNAL_ERROR).build();
    }
  }

  @DELETE
  @Path("{documentId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Delete document", httpMethod = "DELETE", response = Response.class, notes = "This deletes document", consumes = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Document deleted"),
          @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "User not authorized to delete the document"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error") })
  public Response deleteDocument(@ApiParam(value = "Document id", required = true)
                                 @PathParam("documentId") String documentId,
                                 @ApiParam(value = "folder path", required = true)
                                 @QueryParam("documentPath") String documentPath,
                                 @ApiParam(value = "Is favorite document", required = false)
                                 @QueryParam("favorite") boolean favorite,
                                 @ApiParam(value = "Time to effectively delete document", required = false)
                                 @QueryParam("delay") long delay) {
    if (StringUtils.isBlank(documentId)) {
      return Response.status(Status.BAD_REQUEST).entity("document_id_is_mandatory").build();
    }
    if (StringUtils.isBlank(documentPath)) {
      return Response.status(Status.BAD_REQUEST).entity("document_path_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    String originPath = documentPath.replace(":", "/");
    try {
      documentFileService.deleteDocument(originPath,documentId,  favorite, delay, userIdentityId);
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
  @ApiOperation(value = "Undo deleting document if not yet effectively deleted.", httpMethod = "POST", response = Response.class)
  @ApiResponses(value = {@ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
                         @ApiResponse(code = HTTPStatus.FORBIDDEN, message = "Forbidden operation"), })
  public Response undoDeleteDocument(@ApiParam(value = "Document identifier", required = true)
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
  @ApiOperation(value = "Undo deleting document if not yet effectively deleted.", httpMethod = "POST", response = Response.class)
  @ApiResponses(value = {@ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
                         @ApiResponse(code = HTTPStatus.FORBIDDEN, message = "Forbidden operation"), })
  public Response updatePermissions( @ApiParam(value = "Permission object", required = true)
                                             FileNodeEntity nodeEntity) {

    if (nodeEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("node_object_is_mandatory").build();
    }
    NodePermissionEntity nodePermissionEntity = nodeEntity.getAcl();

    if (nodePermissionEntity == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("permissions_object_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);

    try {
      documentFileService.updatePermissions(nodeEntity.getId(),EntityBuilder.toNodePermission(nodeEntity, documentFileService, spaceService, identityManager), userIdentityId);
    } catch (IllegalAccessException e) {
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    }
    return Response.noContent().build();
  }
}

