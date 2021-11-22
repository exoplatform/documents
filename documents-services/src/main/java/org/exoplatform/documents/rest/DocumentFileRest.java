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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.rest.model.AbstractNodeEntity;
import org.exoplatform.documents.rest.util.EntityBuilder;
import org.exoplatform.documents.rest.util.RestUtils;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.swagger.annotations.*;

@Path("/v1/documents")
@Api(value = "/v1/documents", description = "Manages documents associated to users and spaces") // NOSONAR
public class DocumentFileRest implements ResourceContainer {

  private static final Log          LOG = ExoLogger.getLogger(DocumentFileRest.class);

  private final DocumentFileService documentFileService;

  private final SpaceService        spaceService;

  private final IdentityManager     identityManager;

  public DocumentFileRest(DocumentFileService documentFileService, SpaceService spaceService, IdentityManager identityManager) {
    this.documentFileService = documentFileService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
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
  public Response getDocumentItems(@ApiParam(value = "Identity technical identifier", required = false) @QueryParam("ownerId") Long ownerId,
                                   @ApiParam(value = "Parent folder technical identifier", required = false) @QueryParam("parentFolderId") String parentFolderId,
                                   @ApiParam(value = "Listing type of folder. Can be 'TIMELINE' or 'FOLDER'.", required = false) @QueryParam("listingType") FileListingType listingType,
                                   @ApiParam(value = "Search query entered by the user", required = false) @QueryParam("query") String query,
                                   @ApiParam(value = "File properties to expand.", required = false) @QueryParam("expand") String expand,
                                   @ApiParam(value = "Document items sort field", required = false) @QueryParam("sortField") String sortField,
                                   @ApiParam(value = "Sort ascending or descending", required = false) @QueryParam("ascending") boolean ascending,
                                   @ApiParam(value = "Offset of results to return", required = false, defaultValue = "10") @QueryParam("offset") int offset,
                                   @ApiParam(value = "Limit of results to return", required = false, defaultValue = "10") @QueryParam("limit") int limit) {

    if (ownerId == null && StringUtils.isBlank(parentFolderId)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_folderId_is_mandatory").build();
    }
    if (listingType == null) {
      return Response.status(Status.BAD_REQUEST).entity("listingType_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      DocumentNodeFilter filter = listingType == FileListingType.TIMELINE ? new DocumentTimelineFilter(ownerId)
                                                                          : new DocumentFolderFilter(parentFolderId);
      filter.setQuery(query);
      filter.setAscending(ascending);
      filter.setSortField(DocumentSortField.getFromAlias(sortField));

      List<AbstractNode> documents = documentFileService.getDocumentItems(listingType, filter, offset, limit, userIdentityId);
      List<AbstractNodeEntity> documentEntities = EntityBuilder.toDocumentItemEntities(documentFileService,
                                                                                       identityManager,
                                                                                       spaceService,
                                                                                       documents,
                                                                                       expand);
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
  public Response getDocumentGroupsCount(@ApiParam(value = "Identity technical identifier", required = false) @QueryParam("ownerId") Long ownerId,
                                         @QueryParam("parentFolderId") String parentFolderId,
                                         @ApiParam(value = "Search query entered by the user", required = false) @QueryParam("query") String query) {

    if (ownerId == null && StringUtils.isBlank(parentFolderId)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_folderId_is_mandatory").build();
    }

    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      DocumentTimelineFilter filter = new DocumentTimelineFilter(ownerId);
      filter.setQuery(query);

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

}
