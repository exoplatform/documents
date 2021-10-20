/*
 * Copyright (C) 2003-2021 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.documents.rest;

import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.rest.util.RestUtils;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.annotations.*;

@Path("/v1/documents")
@Api(value = "/v1/documents", description = "Manages documents associated to users and spaces") // NOSONAR
public class DocumentsRest {

  private DocumentFileService documentFileService;

  private IdentityManager     identityManager;

  public DocumentsRest(DocumentFileService documentFileService,
                       IdentityManager identityManager) {
    this.documentFileService = documentFileService;
    this.identityManager = identityManager;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(
      value = "Retrieves the list of documents for an authenticated user.",
      httpMethod = "GET",
      response = Response.class,
      produces = "application/json"
  )
  @ApiResponses(
      value = {
          @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
          @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
          @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error"),
      }
  )
  public Response getDocuments(
                               @ApiParam(
                                   value = "Identity technical identifier",
                                   required = false
                               )
                               @QueryParam(
                                 "ownerId"
                               )
                               Long ownerId,
                               @ApiParam(
                                   value = "Parent folder technical identifier",
                                   required = false
                               )
                               @QueryParam("parentFolderId")
                               String parentFolderId,
                               @ApiParam(
                                   value = "Listing type of folder. Can be 'timeline' or 'folder'.",
                                   required = false
                               )
                               @QueryParam(
                                 "listingType"
                               )
                               FileListingType listingType,
                               @ApiParam(
                                   value = "Search query entered by the user",
                                   required = false
                               )
                               @QueryParam(
                                 "query"
                               )
                               String query,
                               @ApiParam(
                                   value = "File properties to expand.",
                                   required = false
                               )
                               @QueryParam(
                                 "expand"
                               )
                               String expand,
                               @ApiParam(
                                   value = "Offset of results to return",
                                   required = false,
                                   defaultValue = "10"
                               )
                               @QueryParam("offset")
                               int offset,
                               @ApiParam(
                                   value = "Limit of results to return",
                                   required = false,
                                   defaultValue = "10"
                               )
                               @QueryParam("limit")
                               int limit) {

    if (ownerId == null && StringUtils.isBlank(parentFolderId)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_folderId_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    DocumentFileFilter filter = new DocumentFileFilter(ownerId, parentFolderId, query, expand, offset, limit);
    List<DocumentFile> files = documentFileService.getFiles(listingType, filter, userIdentityId);

//    EntityBuilder.toDocumentFileEntities(files);
    return null;
  }

}
