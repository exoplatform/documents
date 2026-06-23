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

import static org.exoplatform.documents.constant.DocumentSortField.getFromAlias;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.jcr.AccessDeniedException;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONObject;

import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.DocumentFavoriteFilter;
import org.exoplatform.documents.model.DocumentFolderFilter;
import org.exoplatform.documents.model.DocumentGroupsSize;
import org.exoplatform.documents.model.DocumentNodeFilter;
import org.exoplatform.documents.model.DocumentTimelineFilter;
import org.exoplatform.documents.model.DownloadItem;
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.documents.model.TrashElementNode;
import org.exoplatform.documents.model.TrashElementNodeFilter;
import org.exoplatform.documents.rest.model.AbstractNodeEntity;
import org.exoplatform.documents.rest.model.DocumentsUserSettings;
import org.exoplatform.documents.rest.model.FileNodeEntity;
import org.exoplatform.documents.rest.model.NodePermissionEntity;
import org.exoplatform.documents.rest.model.PublicDocumentAccessOptionsEntity;
import org.exoplatform.documents.rest.model.TrashElementEntity;

import org.exoplatform.documents.rest.util.EntityBuilder;
import org.exoplatform.documents.rest.util.RestUtils;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.documents.service.ExternalDownloadService;
import org.exoplatform.documents.service.PublicDocumentAccessService;
import org.exoplatform.documents.webdav.service.DocumentWebDavService;
import org.exoplatform.portal.rest.CollectionEntity;
import org.exoplatform.portal.rest.UserFieldValidator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.http.PATCH;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.common.Utils;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataService;

import io.meeds.portal.thumbnail.model.FileContent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Path("/v1/documents")
@Tag(name = "/v1/documents", description = "Manages documents associated to users and spaces") // NOSONAR
public class DocumentFileRest implements ResourceContainer {

  public static final UserFieldValidator    PASSWORD_VALIDATOR     = new UserFieldValidator("password", false, false, 9, 255);

  private static final Log                  LOG                    = ExoLogger.getLogger(DocumentFileRest.class);

  private static final CacheControl         CACHE_CONTROL          = new CacheControl();

  private static final int                  CACHE_IN_SECONDS       = 7 * 86400;

  private static final int                  CACHE_IN_MILLI_SECONDS = CACHE_IN_SECONDS * 1000;

  private final DocumentFileService         documentFileService;

  private final SpaceService                spaceService;

  private final MetadataService             metadataService;

  private final IdentityManager             identityManager;

  private final SettingService              settingService;

  private final PublicDocumentAccessService publicDocumentAccessService;

  private final ExternalDownloadService     externalDownloadService;

  private final DocumentWebDavService       documentWebDavService;

  public DocumentFileRest(DocumentFileService documentFileService, // NOSONAR
                          SpaceService spaceService,
                          IdentityManager identityManager,
                          MetadataService metadataService,
                          SettingService settingService,
                          PublicDocumentAccessService publicDocumentAccessService,
                          ExternalDownloadService externalDownloadService,
                          DocumentWebDavService documentWebDavService) {
    this.documentFileService = documentFileService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.metadataService = metadataService;
    this.settingService = settingService;
    this.publicDocumentAccessService = publicDocumentAccessService;
    this.externalDownloadService = externalDownloadService;
    this.documentWebDavService = documentWebDavService;
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @RolesAllowed("users")
  @Path("/webdav-path")
  @Operation(summary = "Get WebDAV path from JCR path", method = "GET", description = "Resolves a JCR path into the visible WebDAV path used by desktop clients.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "404", description = "Resource not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getWebDavPath(@Parameter(description = "JCR path of the document or folder", required = true)
                                @QueryParam("jcrPath")
                                String jcrPath) {
    if (StringUtils.isBlank(jcrPath)) {
      return Response.status(Status.BAD_REQUEST).entity("jcrPath_is_mandatory").build();
    }
    try {
      return Response.ok(documentWebDavService.getWebDavPath(jcrPath, RestUtils.getCurrentUser())).build();
    } catch (org.exoplatform.documents.webdav.model.WebDavException e) {
      int httpError = e.getHttpError();
      if (httpError == HttpServletResponse.SC_FORBIDDEN) {
        httpError = HttpServletResponse.SC_NOT_FOUND;
      }
      return Response.status(httpError).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving WebDAV path for JCR path '{}'", jcrPath, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/settings/{ownerId}")
  @Operation(summary = "Get User documents settings", method = "GET")
  @ApiResponses(value = { 
       @ApiResponse(responseCode = "200", description = "Request fulfilled"),
       @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getSettings(@Parameter(description = "Identity technical identifier, required = true")
  @PathParam("ownerId")
  Long ownerId) {
    Identity currentUserIdentity = RestUtils.getCurrentUserIdentity(identityManager);
    try {
      DocumentsUserSettings documentsUserSettings = new DocumentsUserSettings();
      documentsUserSettings.setCanImport(documentFileService.canImport(ConversationState.getCurrent().getIdentity()));
      return Response.ok(documentsUserSettings).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving documents settings for user with id '{}'", currentUserIdentity, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
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
                                   boolean showHiddenFiles,
                                   @Parameter(description = "fileType")
                                   @QueryParam("fileType")
                                   String fileType,
                                   @Parameter(description = "Category id used to search associated document", required = false)
                                   @QueryParam("categoryId")
                                   List<Long> categoryIds,
                                   @Parameter(description = "Category IDs to exclude from the result", required = false)
                                   @QueryParam("excludedCategoryId")
                                   List<Long> excludedCategoryIds,
                                   @Parameter(description = "afterDate")
                                   @QueryParam("afterDate")
                                   Long afterDate,
                                   @Parameter(description = "beforeDate")
                                   @QueryParam("beforeDate")
                                   Long beforeDate,
                                   @Parameter(description = "minSize")
                                   @QueryParam("minSize")
                                   Long minSize,
                                   @Parameter(description = "maxSize")
                                   @QueryParam("maxSize")
                                   Long maxSize) {

    if (ownerId == null && StringUtils.isBlank(parentFolderId)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_folderId_is_mandatory").build();
    }
    if (listingType == null) {
      return Response.status(Status.BAD_REQUEST).entity("listingType_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      DocumentNodeFilter filter = listingType == FileListingType.TIMELINE ? new DocumentTimelineFilter(ownerId, parentFolderId)
                                                                          : new DocumentFolderFilter(parentFolderId,
                                                                                                     folderPath,
                                                                                                     ownerId,
                                                                                                     symlinkFolderId);
      filter.setQuery(query);
      filter.setExtendedSearch(extendedSearch);
      filter.setFavorites(favorites);
      filter.setUserId(userId);
      filter.setFileTypes(fileType);
      filter.setAfterDate(afterDate);
      filter.setBeforeDate(beforeDate);
      filter.setMaxSize(maxSize);
      filter.setMinSize(minSize);
      filter.setAscending(ascending);
      filter.setSortField(getFromAlias(sortField));
      filter.setIncludeHiddenFiles(showHiddenFiles);
      filter.setCategoryIds(categoryIds);
      filter.setExcludedCategoryIds(excludedCategoryIds);
      List<? extends AbstractNode> documents = documentFileService.getDocumentItems(listingType, filter, offset, limit, userIdentityId, showHiddenFiles);
      List<AbstractNodeEntity> documentEntities = EntityBuilder.toDocumentItemEntities(documentFileService,
                                                                                       identityManager,
                                                                                       spaceService,
                                                                                       metadataService,
                                                                                       publicDocumentAccessService,
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
  @Path("favoriteIds")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(method = "GET", description = "Retrieves the list of favorite documents for an authenticated")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Request fulfilled"),
    @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
  })
  public Response getFavoriteDocumentIds(
                                         @Parameter(description = "Offset of results to return")
                                         @Schema(defaultValue = "0")
                                         @QueryParam("offset")
                                         int offset,
                                         @Parameter(description = "Limit of results to return")
                                         @Schema(defaultValue = "10")
                                         @QueryParam("limit")
                                         int limit,
                                         @Parameter(description = "afterDate")
                                         @QueryParam("afterDate")
                                         Long afterDate) {
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      DocumentNodeFilter filter = new DocumentFavoriteFilter();
      filter.setAscending(false);
      filter.setSortField(DocumentSortField.MODIFIED_DATE);
      if (afterDate != null && afterDate.longValue() > 0) {
        filter.setAfterDate(afterDate);
      }
      List<String> ids = documentFileService.getFavoriteFileIds(filter,
                                                                offset,
                                                                limit,
                                                                userIdentityId);
      return Response.ok(ids).build();
    } catch (IllegalAccessException e) {
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
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
      DocumentTimelineFilter filter = new DocumentTimelineFilter();
      filter.setOwnerId(ownerId);
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
                                String folderId,
                                  @Parameter(description = "destination folder path")
                                    @QueryParam("destinationFolderPath")
                                    String destinationFolderPath,
                                  @Parameter(description = "include children")
                                    @QueryParam("withChildren")
                                    boolean withChildren,
                                  @Parameter(description = "show hidden folder")
                                    @QueryParam("showHidden")
                                    boolean showHidden) {

    if (ownerId == null && StringUtils.isBlank(folderId)) {
      return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_folderId_is_mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
        ownerId = ownerId == null ? 0 :ownerId;
        return Response.ok(EntityBuilder.toFullTreeItemEntities(documentFileService.getFullTreeData(ownerId, folderId,destinationFolderPath, userIdentityId, withChildren,showHidden),ownerId))
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
                                    @QueryParam("destinationId")
                                    String destinationId,
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
      AbstractNode abstractNode = null;
    try {
        if (StringUtils.isBlank(destinationId)) {
            abstractNode = documentFileService.duplicateDocument(ownerId, fileId, prefixClone, userIdentityId);
        } else {
            abstractNode = documentFileService.copyDocument(fileId, destinationId, userIdentityId);
        }
      AbstractNodeEntity abstractNodeEntity = EntityBuilder.toDocumentItemEntity(documentFileService,
              identityManager,
              spaceService,
              metadataService,
              publicDocumentAccessService,
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
  @Operation(summary = "Move documents", method = "POST", description = "This move a giving document.")
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
      LOG.warn("Failed to move Document", ex);
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
              publicDocumentAccessService,
                                                                                 createdFolder,
                                                                                 null,
                                                                                 userIdentityId);
      return Response.ok(abstractNodeEntity).build();
    } catch (IllegalAccessException e) {
      LOG.warn(e.getMessage());
      return Response.status(HTTPStatus.UNAUTHORIZED).build();
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

    @PATCH
    @Path("/{property}")
    @RolesAllowed("users")
    @Operation(summary = "Update a document property", method = "PATCH", description = "This updates a document prperty.")
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
        @ApiResponse(responseCode = "400", description = "Invalid query input"),
        @ApiResponse(responseCode = "403", description = "Unauthorized operation"),
        @ApiResponse(responseCode = "404", description = "Resource not found") })
    public Response updateDocument(@Parameter(description = "Property to update", required = true)
                                   @PathParam("property")
                                   String property,
                                   @Parameter(description = "document id", required = true)
                                   @QueryParam("documentId")
                                   String documentId,
                                   @Parameter(description = "ownerId")
                                   @QueryParam("ownerId")
                                   Long ownerId,
                                   @Parameter(description = "new name")
                                   @QueryParam("newName")
                                   String newName,
                                   @Parameter(description = "new description")
                                   @QueryParam("description")
                                   String description,
                                   @Parameter(description = "new document hidden value")
                                   @QueryParam("hidden")
                                   Boolean hidden) {
      if (ownerId == null && StringUtils.isBlank(documentId)) {
        return Response.status(Status.BAD_REQUEST).entity("either_ownerId_or_documentID_is_mandatory").build();
      }
      if (StringUtils.equals(property, "name")) {
        if (StringUtils.isEmpty(newName)) {
          return Response.status(Response.Status.BAD_REQUEST).entity("Document Name should not be empty").build();
        }
        try {
          long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
          documentFileService.renameDocument(ownerId, documentId, newName, userIdentityId);
          return Response.ok().build();
        } catch (ObjectAlreadyExistsException ex) {
          LOG.warn("Document with same name already exists", ex);
          return Response.status(HTTPStatus.CONFLICT).build();
        } catch (Exception ex) {
          LOG.warn("Failed to rename Document", ex);
          return Response.status(HTTPStatus.INTERNAL_ERROR).build();
        }
      }
      if (StringUtils.equals(property, "description")) {
        if (StringUtils.isEmpty(description)) {
          return Response.status(Response.Status.BAD_REQUEST).entity("Document description should not be empty").build();
        }
        try {
          long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
          documentFileService.updateDocumentDescription(ownerId, documentId, description, userIdentityId);
          return Response.ok().build();
        } catch (AccessDeniedException e) {
          return Response.status(HTTPStatus.UNAUTHORIZED).build();
        } catch (Exception ex) {
          LOG.warn("Failed to update document description", ex);
          return Response.status(HTTPStatus.INTERNAL_ERROR).build();
        }
      }
      if (StringUtils.equals(property, "visibility")) {
        if (hidden == null) {
          return Response.status(Response.Status.BAD_REQUEST).entity("Document hidden value should not be empty").build();
        }
        try {
          long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
          documentFileService.setDocumentVisibility(ownerId, documentId, hidden, userIdentityId);
          return Response.ok().build();
        } catch (AccessDeniedException e) {
          return Response.status(HTTPStatus.UNAUTHORIZED).build();
        } catch (Exception ex) {
          LOG.warn("Failed to update document visibility", ex);
          return Response.status(HTTPStatus.INTERNAL_ERROR).build();
        }
      }
      return Response.status(Status.NOT_MODIFIED).build();
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

  @DELETE
  @Path("bulk/{actionId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "Delete list of documents", method = "DELETE", description = "This deletes a list of documents")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Document deleted"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "User not authorized to delete the document"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public Response bulkDeleteDocuments(@Parameter(description = "action ID", required = true)
  @PathParam("actionId")
  int actionId, @RequestBody(description = "documents List", required = true)
  List<AbstractNodeEntity> documents) {
    if (documents.isEmpty()) {
      return Response.status(Status.BAD_REQUEST).entity("documents list is mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      documentFileService.deleteDocuments(actionId, EntityBuilder.toAbstractNodes(documents), userIdentityId);
      return Response.ok().build();
    } catch (IllegalAccessException e) {
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.error("Error while deleting documents", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @POST
  @Path("bulk/download/{actionId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "download list of documents", method = "POST", description = "This download a list of documents")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Documents downloaded"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "User not authorized to download the documents"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public Response downloadDocuments(@Parameter(description = "action ID", required = true)
  @PathParam("actionId")
  int actionId, @RequestBody(description = "documents List", required = true)
  List<AbstractNodeEntity> documents) {
    if (documents.isEmpty()) {
      return Response.status(Status.BAD_REQUEST).entity("documents list is mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      documentFileService.downloadDocuments(actionId, EntityBuilder.toAbstractNodes(documents), userIdentityId);
      return Response.ok().build();
    } catch (IllegalAccessException e) {
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.error("Error while downloading documents", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @PUT
  @Path("bulk/move/{actionId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Operation(summary = "move list of documents to specific path", method = "POST", description = "This moves a list of documents")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Documents moved"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "User not authorized"),
          @ApiResponse(responseCode = "500", description = "Internal server error") })
  public Response moveDocuments(@Parameter(description = "action ID", required = true)
                                @PathParam("actionId") int actionId,
                                @Parameter(description = "ownerId") @QueryParam("ownerId") Long ownerId,
                                @RequestBody(description = "documents List", required = true) List<AbstractNodeEntity> documents,
                                @Parameter(description = "new path") @QueryParam("destPath") String destPath) {
    if (documents.isEmpty()) {
      return Response.status(Status.BAD_REQUEST).entity("documents list is mandatory").build();
    }
    if (ownerId == null) {
      return Response.status(Status.BAD_REQUEST).entity("owner id is mandatory").build();
    }
    if (StringUtils.isBlank(destPath)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Documents destination path is mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (userIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      documentFileService.moveDocuments(actionId, ownerId, EntityBuilder.toAbstractNodes(documents), destPath, userIdentityId);
      return Response.ok().build();
    } catch (Exception e) {
      LOG.error("Error while moving documents", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @GET
  @Path("bulk/download/{actionId}")
  @RolesAllowed("users")
  @Operation(summary = "Download zipped list of files", method = "GET", description = "This download a zipped list of files.")
  @ApiResponses(value = { 
          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "410", description = "Resource no more available") })
  public Response getDownloadZip(@Parameter(description = "List items", required = true)
  @PathParam("actionId")
  int actionId) {

    try {
      Identity currentUserIdentity = RestUtils.getCurrentUserIdentity(identityManager);
      byte[] filesBytes = documentFileService.getDownloadZipBytes(actionId,
              currentUserIdentity.getRemoteId());
      if (filesBytes.length == 0) {
        return Response.status(Status.GONE).build();
      }
      return Response.ok(filesBytes)
                     .type("application/zip")
                     .header("Content-Disposition", "attachment; filename=\"documents_Download" + new Date().getTime() + ".zip\"")
                     .build();
    } catch (Exception ex) {
      LOG.warn("Failed to get download file ", ex);
      return Response.status(HTTPStatus.INTERNAL_ERROR).build();
    }
  }

  @GET
  @Path("bulk/cancel/{actionId}")
  @RolesAllowed("users")
  @Operation(summary = "cancel bulk action", method = "GET", description = "Cancel bulk action.")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "403", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "404", description = "Resource not found") })
  public Response cancelBulkAction(@Parameter(description = "List items", required = true)
  @PathParam("actionId")
  String actionId) {
    Identity currentUserIdentity = RestUtils.getCurrentUserIdentity(identityManager);
    try {
      documentFileService.cancelBulkAction(actionId, currentUserIdentity.getRemoteId());
      return Response.ok().build();
    } catch (Exception ex) {
      LOG.warn("Failed to cancel bulk action", ex);
      return Response.status(HTTPStatus.INTERNAL_ERROR).build();
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

  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/createNewVersion")
  @Operation(
          summary = "Creates a new version of a document from an input stream",
          method = "PUT",
          description = "Creates a new version of a document from an input stream")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response createNewVersion(@RequestBody(description = "New content", required = true) InputStream newContent,
                                   @Parameter(description = "target file node identifier", required = true) @QueryParam("nodeId") String nodeId) {
    if (StringUtils.isBlank(nodeId)) {
      return Response.status(Status.BAD_REQUEST).entity("version fil id is mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (userIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      return Response.ok(EntityBuilder.toVersionEntity(documentFileService.createNewVersion(nodeId,
                                                                                            RestUtils.getCurrentUser(),
                                                                                            newContent))).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error while creating a new version", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/size/{ownerId}")
  @Operation(summary = "Get documents size", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getSize(@Parameter(description = "Identity technical identifier, required = true")
                          @PathParam("ownerId")
                          Long ownerId) {
    if (ownerId == null || ownerId < 1) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    Identity currentUserIdentity = RestUtils.getCurrentUserIdentity(identityManager);
    try {
      return Response.ok(documentFileService.getDocumentsSizeStat(ownerId, Long.parseLong(currentUserIdentity.getId()))).build();
    } catch (Exception e) {
      LOG.error("Error retrieving documents size", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/size/{ownerId}")
  @Operation(summary = "Calculate documents size", method = "POST")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response addSize(@Parameter(description = "Identity technical identifier, required = true")
                          @PathParam("ownerId")
                          Long ownerId) {
    if (ownerId == null || ownerId < 1) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    Identity currentUserIdentity = RestUtils.getCurrentUserIdentity(identityManager);
    try {
      return Response.ok(documentFileService.addDocumentsSizeStat(ownerId, Long.parseLong(currentUserIdentity.getId()))).build();
    } catch (Exception e) {
      LOG.error("Error while calculating documents size", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/biggest/{ownerId}")
  @Operation(summary = "Get biggest documents", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getBiggestDocuments(@Parameter(description = "Identity technical identifier, required = true")
                          @PathParam("ownerId")
                          Long ownerId,
                          @Parameter(description = "Offset of results to return") @Schema(defaultValue = "0")
                          @QueryParam("offset")
                          int offset,
                          @Parameter(description = "Limit of results to return") @Schema(defaultValue = "10")
                          @QueryParam("limit")
                          int limit) {
    if (ownerId == null || ownerId < 1) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    Identity currentUserIdentity = RestUtils.getCurrentUserIdentity(identityManager);
    try {
      List<AbstractNode> results = documentFileService.getBiggestDocuments(ownerId, currentUserIdentity, offset,limit);
      List<AbstractNodeEntity> documentEntities = EntityBuilder.toDocumentItemEntities(documentFileService,
                                                                                       identityManager,
                                                                                       spaceService,
                                                                                       metadataService,
                                                                                       publicDocumentAccessService,
                                                                                       results,
                                                                                       "creator,owner",
                                                                                       Long.parseLong(currentUserIdentity.getId()));

      return Response.ok(documentEntities).build();
    } catch (Exception e) {
      LOG.error("Error retrieving documents size", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/publicAccessLink")
  @Operation(summary = "Generate a new public link for a specific document",
             method = "POST",
             description = "Generate a new public link for a specific document")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response createPublicAccessLink(@javax.ws.rs.core.Context HttpServletRequest request,
                                         @Parameter(description = "target file node identifier", required = true)
                                         @QueryParam("nodeId") String nodeId,
                                         @RequestBody(description = "public access document options object", required = true)
                                         PublicDocumentAccessOptionsEntity publicDocumentAccessOptionsEntity) {

    Locale locale = request == null ? Locale.ENGLISH : request.getLocale();
    if (StringUtils.isBlank(nodeId)) {
      return Response.status(Status.BAD_REQUEST).entity("node id is mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (userIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    String password = publicDocumentAccessOptionsEntity != null ? publicDocumentAccessOptionsEntity.getPassword() : null;
    Long expirationDate = publicDocumentAccessOptionsEntity != null ? publicDocumentAccessOptionsEntity.getExpirationDate() : 0L;
    boolean hasPassword = publicDocumentAccessOptionsEntity != null && publicDocumentAccessOptionsEntity.isHasPassword();
    if (password != null) {
      String errorMessage = PASSWORD_VALIDATOR.validate(locale, publicDocumentAccessOptionsEntity.getPassword());
      if (StringUtils.isNotBlank(errorMessage)) {
          return Response.status(Response.Status.BAD_REQUEST).entity(errorMessage).build();
      }
    }
    try {
      boolean hasEditPermission = documentFileService.hasEditPermissionOnDocument(nodeId, userIdentityId);
      if (!hasEditPermission) {
          return Response.status(Response.Status.UNAUTHORIZED).build();
      }
      return Response.ok(EntityBuilder.toPublicDocumentAccessEntity(publicDocumentAccessService.createPublicDocumentAccess(userIdentityId,
                      nodeId,
                      password,
                      expirationDate,
                      hasPassword)))
              .build();

    } catch (Exception e) {
      LOG.error("Error while creating a document public access for document: {}", nodeId, e);
      return Response.serverError().build();
    }
  }

  @DELETE
  @RolesAllowed("users")
  @Path("/publicAccessLink")
  @Operation(summary = "Revoke public access link for a document",
      method = "DELETE",
      description = "Revoke public access link for a document")
  @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response revokePublicAccessLink(@Parameter(description = "target file node identifier", required = true)
                                         @QueryParam("nodeId") String nodeId) {
    if (StringUtils.isBlank(nodeId)) {
      return Response.status(Status.BAD_REQUEST).entity("node id is mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (userIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      boolean hasEditPermission = documentFileService.hasEditPermissionOnDocument(nodeId, userIdentityId);
      if (!hasEditPermission) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }
      publicDocumentAccessService.revokeDocumentPublicAccess(nodeId);
      return Response.noContent().build();
    } catch (Exception e) {
      LOG.error("Error while revoking public access link for document: {}", nodeId, e);
      return Response.serverError().build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/publicAccessLink")
  @Operation(summary = "Get public access details of a specific document",
          method = "GET",
          description = "Get public access details of a specific document")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getPublicAccessLink(@Parameter(description = "target file node identifier", required = true)
                                      @QueryParam("nodeId") String nodeId) {

    if (StringUtils.isBlank(nodeId)) {
      return Response.status(Status.BAD_REQUEST).entity("node id is mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    if (userIdentityId == 0) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      PublicDocumentAccess publicDocumentAccess = publicDocumentAccessService.getPublicDocumentAccess(nodeId);
      if (publicDocumentAccess == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      return Response.ok(EntityBuilder.toPublicDocumentAccessEntity(publicDocumentAccess)).build();

    } catch (Exception e) {
      LOG.error("Error while getting public access link for document: {}", nodeId, e);
      return Response.serverError().build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/publicAccessDocument")
  @Operation(summary = "Get public access information",
      method = "GET",
      description = "Get public access document")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response publicAccessDocument(@Parameter(description = "public link node id", required = true)
                                   @QueryParam("nodeId") String nodeId) {

    PublicDocumentAccess publicDocumentAccess = publicDocumentAccessService.getPublicDocumentAccess(nodeId);
    DownloadItem downloadItem = externalDownloadService.getDocumentDownloadItem(nodeId);

    boolean hasPublicLink = publicDocumentAccess != null;
    boolean isAccessLocked = hasPublicLink && publicDocumentAccess.getPasswordHashKey() != null;
    boolean isAccessExpired = publicDocumentAccessService.isPublicDocumentAccessExpired(nodeId);

    JSONObject response = new JSONObject();
    response.put("nodeId", nodeId);
    response.put("hasPublicLink", hasPublicLink);
    response.put("isAccessLocked", isAccessLocked);
    response.put("isAccessExpired", isAccessExpired);
    if (downloadItem != null) {
      response.put("documentName", downloadItem.getItemName());
      response.put("documentType", downloadItem.getMimeType());
    }

    return Response.ok(response.toString()).build();


  }

  @GET
  @Produces
  @Path("/download")
  @Operation(summary = "Download a given document",
          method = "GET",
          description = "Download a given document")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response downloadDocument(@Parameter(description = "public link node id", required = true)
                                   @QueryParam("nodeId") String nodeId,
                                   @Parameter(description = "public access password")
                                   @QueryParam("password") String password) {

    if (StringUtils.isBlank(nodeId)) {
      return Response.status(Status.BAD_REQUEST).entity("nodeId id is mandatory").build();
    }
    try {
      if (publicDocumentAccessService.isPublicDocumentAccessExpired(nodeId)) {
        return Response.status(Status.UNAUTHORIZED).entity("access expired").build();
      }
      if (!publicDocumentAccessService.isDocumentPublicAccessValid(nodeId, password)) {
        return Response.status(Status.UNAUTHORIZED).entity("access is not valid").build();
      }
      DownloadItem downloadItem = externalDownloadService.getDocumentDownloadItem(nodeId);
      if (downloadItem == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      if(downloadItem.getMimeType() != null) {
        return Response.ok(downloadItem.getItemContent().toByteArray())
                .type(downloadItem.getMimeType())
                .header("Content-Disposition",
                        "attachment;filename=\""
                                + URLEncoder.encode(downloadItem.getItemName(), StandardCharsets.UTF_8)
                                .replace("+", "%20") + "\"").build();
      } else {
        byte[] filesBytes = externalDownloadService.downloadZippedFolder(downloadItem.getItemId());
        return Response.ok(filesBytes)
                       .type("application/zip")
                       .header("Content-Disposition",
                               "attachment; filename=\""
                                   + URLEncoder.encode(downloadItem.getItemName(), StandardCharsets.UTF_8)
                                       .replace("+", "%20") + ".zip\"").build();
      }
    } catch (Exception e) {
      LOG.error("Error while downloading document", e);
      return Response.serverError().build();
    }
  }

  @POST
  @Path("/importzip/{uploadId}")
  @RolesAllowed("users")
  @Operation(summary = "Import document from a zip", method = "POST", description = "Import document from a zip")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response importDocuments(@Parameter(description = "upload id", required = true)
  @PathParam("uploadId")
  String uploadId,
                                  @Parameter(description = "the owner id")
                                  @QueryParam("ownerId")
                                  String ownerId,
                                  @Parameter(description = "the folder id, where the documents will be imported")
                                  @QueryParam("folderId")
                                  String folderId,
                                  @Parameter(description = "the folder path, where the documents will be imported")
                                  @QueryParam("folderPath")
                                  String folderPath,
                                  @Parameter(description = "the rule to apply in case of conflict")
                                  @QueryParam("conflict")
                                  String conflict) {

    try {

      org.exoplatform.services.security.Identity identity = ConversationState.getCurrent().getIdentity();
      if (StringUtils.isEmpty(ownerId)) {
        return Response.status(Response.Status.BAD_REQUEST).build();
      }
      if (uploadId == null) {
        return Response.status(Response.Status.BAD_REQUEST).build();
      }
      long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
      documentFileService.importFiles(ownerId, folderId, folderPath, uploadId, conflict, identity, userIdentityId);
      return Response.status(Response.Status.OK).build();
    } catch (IllegalAccessException e) {
      LOG.error("User does not have import permissions", e);
      return Response.status(Response.Status.UNAUTHORIZED).build();
    } catch (Exception ex) {
      LOG.warn("Failed to import documents ", ex);
      return Response.status(HTTPStatus.INTERNAL_ERROR).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("administrators")
  @Path("/deleted")
  @Operation(summary = "Fetch trash documents", method = "GET", description = "Fetch trash document")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getDeletedDocuments(@Parameter(description = "Sort filed")
                                      @Schema(defaultValue = "modifiedDate")
                                      @QueryParam("sortField")
                                      String sortField,
                                      @Parameter(description = "Sort ascending or descending")
                                      @Schema(defaultValue = "desc")
                                      @QueryParam("sortDirection")
                                      String sortDirection,
                                      @Parameter(description = "Offset of results to return")
                                      @Schema(defaultValue = "0")
                                      @QueryParam("offset")
                                      int offset,
                                      @Parameter(description = "Limit of results to return")
                                      @Schema(defaultValue = "20")
                                      @QueryParam("limit")
                                      int limit) {
    if (limit < 0 || offset < 0) {
      LOG.error("Invalid offset or limit parameters");
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    TrashElementNodeFilter filter = new TrashElementNodeFilter();
    filter.setAscending(sortDirection.equalsIgnoreCase("asc"));
    filter.setOffset(offset);
    filter.setLimit(limit);
    filter.setSortField(getFromAlias(sortField));
    try {
      List<TrashElementNode> trashElementNodes = documentFileService.getDeletedDocuments(filter);
      int size = documentFileService.countDeletedDocuments();
      List<TrashElementEntity> trashElementEntities = trashElementNodes.stream()
                                                                       .map(EntityBuilder::toTrashElement)
                                                                       .collect(Collectors.toList());
      CollectionEntity<TrashElementEntity> collectionEntity = new CollectionEntity<>(trashElementEntities,
                                                                                     filter.getOffset(),
                                                                                     filter.getLimit(),
                                                                                     size);
      return Response.ok(collectionEntity).build();
    } catch (Exception ex) {
      LOG.error("Error while fetching trash documents", ex);
      return Response.serverError().build();
    }
  }

  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("administrators")
  @Path("/trash/restore")
  @Operation(summary = "Restores a document from the trash", method = "PUT", description = "Restores a document from the trash based on the specified trash node path")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response restoreDocumentFromTrash(@Parameter(description = "node path", required = true)
                                           @QueryParam("documentPath")
                                           String documentPath) {
    if (StringUtils.isBlank(documentPath)) {
      return Response.status(Status.BAD_REQUEST).entity("document_path_is_mandatory").build();
    }
    try {
      documentFileService.restoreDocumentFromTrash(documentPath);
      return Response.ok().build();
    } catch (Exception e) {
      LOG.error("Error when restoring the document with path " + documentPath, e);
      return Response.serverError().entity(e.getMessage()).build();
    }

  }

  @DELETE
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("administrators")
  @Path("/trash/delete")
  @Operation(summary = "Delete a document from the trash", method = "DELETE", description = "Delete a document from the trash based on the specified trash node path")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Document not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response deleteDocumentPermanently(@Parameter(description = "node path", required = true)
                                            @QueryParam("documentPath")
                                            String documentPath) {
    if (StringUtils.isBlank(documentPath)) {
      return Response.status(Status.BAD_REQUEST).entity("document_path_is_mandatory").build();
    }
    try {
      documentFileService.deleteDocumentPermanently(documentPath);
      return Response.ok().build();
    } catch (ObjectNotFoundException exception) {
      return Response.status(HTTPStatus.NOT_FOUND).entity(exception.getMessage()).build();
    } catch (Exception e) {
      LOG.error("Error when restoring the document with path " + documentPath, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @DELETE
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("administrators")
  @Path("/trash/bulk/delete/{actionId}")
  @Operation(summary = "Delete list of documents permanently", method = "DELETE", description = "This deletes a list of documents permanently")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Document deleted"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "400", description = "Invalid request body input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public Response deleteDocumentsPermanently(@Parameter(description = "action ID", required = true)
                                            @PathParam("actionId")
                                            int actionId,
                                            @RequestBody(description = "documents List", required = true)
                                            List<TrashElementEntity> documents) {
    if (documents.isEmpty()) {
      return Response.status(Status.BAD_REQUEST).entity("documents list is mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      documentFileService.deleteDocumentsPermanently(actionId, EntityBuilder.toNodesFromTrashEntities(documents), userIdentityId);
      return Response.ok().build();
    } catch (IllegalAccessException e) {
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.error("Error while permanently deleting documents", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("administrators")
  @Path("/trash/bulk/restore/{actionId}")
  @Operation(summary = "Restore list of documents", method = "PUT", description = "This restore a list of documents")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Document deleted"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "400", description = "Invalid request body input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public Response restoreDocuments(@Parameter(description = "action ID", required = true)
                                   @PathParam("actionId")
                                   int actionId,
                                   @RequestBody(description = "documents List", required = true)
                                   List<TrashElementEntity> documents) {
    if (documents.isEmpty()) {
      return Response.status(Status.BAD_REQUEST).entity("documents list is mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      documentFileService.restoreDocuments(actionId, EntityBuilder.toNodesFromTrashEntities(documents), userIdentityId);
      return Response.ok().build();
    } catch (IllegalAccessException e) {
      return Response.status(Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.error("Error while permanently deleting documents", e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{documentId}")
  @Operation(summary = "Get all details of a given document", method = "GET", description = "Get versions list of a a given document")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getDocument(@Parameter(description = "Document identifier", required = true)
  @PathParam("documentId")
  String documentId,

                              @Parameter(description = "File properties to expand.")
                              @QueryParam("expand")
                              String expand) {

    if (StringUtils.isBlank(documentId)) {
      return Response.status(Status.BAD_REQUEST).entity("document id is mandatory").build();
    }
    long userIdentityId = RestUtils.getCurrentUserIdentityId(identityManager);
    try {
      AbstractNodeEntity abstractNodeEntity =
                                            EntityBuilder.toDocumentItemEntity(documentFileService,
                                                                               identityManager,
                                                                               spaceService,
                                                                               metadataService,
                                                                               publicDocumentAccessService,
                                                                               documentFileService.getDocumentById(documentId,
                                                                                                                   RestUtils.getCurrentUser()),
                                                                               expand,
                                                                               userIdentityId);
      return Response.ok(abstractNodeEntity).build();

    } catch (IllegalArgumentException e) {
      return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
    } catch (IllegalAccessException e) {
      return Response.status(Status.FORBIDDEN).entity(e.getMessage()).build();
    } catch (ObjectNotFoundException e) {
      return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.warn("Error retrieving a the file with id = {}", documentId, e);
      return Response.serverError().entity(e.getMessage()).build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{fileType}/{documentId}")
  @Operation(summary = "Get content of a given document", method = "GET", description = "Get content a a given document")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getDocumentContent(@javax.ws.rs.core.Context
  UriInfo uriInfo, @javax.ws.rs.core.Context
  Request request,
                                     @Parameter(description = "Document identifier", required = true)
                                     @PathParam("documentId")
                                     String documentId,
                                     @Parameter(description = "Document type to be able to get the content")
                                     @PathParam("fileType")
                                     String fileType,
                                     @Parameter(description = "The value of lastModified parameter will determine whether the query should be cached by browser or not. If not set, no 'expires HTTP Header will be sent'")
                                     @QueryParam("lastModified")
                                     String lastModified,
                                     @Parameter(description = "Resized image size. Use 250x250 as default size.")
                                     @DefaultValue("0x0")
                                     @QueryParam("size")
                                     String size) {

    if (StringUtils.isBlank(documentId)) {
      return Response.status(Status.BAD_REQUEST).entity("document id is mandatory").build();
    }
    if (StringUtils.isBlank(fileType)) {
      return Response.status(Status.BAD_REQUEST).entity("file Type  is mandatory").build();
    }
    try {
      FileContent file = null;
      if (fileType.equals("content")) {
        file = documentFileService.getDocumentContent(documentId, ConversationState.getCurrent().getIdentity().getUserId());
      } else {
        int[] dimension = Utils.parseDimension(size);
        file = documentFileService.getImageThumbnailContent(fileType,
                                                            documentId,
                                                            ConversationState.getCurrent().getIdentity().getUserId(),
                                                            dimension[0],
                                                            dimension[1]);
      }
      if (file == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      long lastUpdated = file.getUpdatedDate().getTime();
      EntityTag eTag = new EntityTag(Long.hashCode(lastUpdated) + "-" + size);
      Response.ResponseBuilder builder = request.evaluatePreconditions(eTag);
      if (builder == null) {
        InputStream inputStream = file.getContent();
        String mimeType = file.getMimeType();
        if (StringUtils.isBlank(mimeType)) {
          mimeType = MediaType.APPLICATION_OCTET_STREAM;
        }
        builder = Response.ok(inputStream).type(mimeType);
        builder.cacheControl(CACHE_CONTROL);
        builder.lastModified(new Date(lastUpdated));
        builder.tag(eTag);
      }
      String fileName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");
      builder.header("Content-Disposition", "filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName);
      if (StringUtils.isNotBlank(lastModified)) {
        builder.expires(new Date(System.currentTimeMillis() + CACHE_IN_MILLI_SECONDS));
      }
      return builder.build();
    } catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.error("Error getting document content with Id {}", documentId, e);
      }
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
    }
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/{fileType}/{documentId}")
  @Operation(summary = "Create a thumbnail with provided content", method = "POST", description = "Create a thumbnail with provided content")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "401", description = "Unauthorized operation"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response createVideoThumbnail(@javax.ws.rs.core.Context
  HttpServletRequest request,
                                       @Parameter(description = "Document identifier", required = true)
                                       @PathParam("documentId")
                                       String documentId,
                                       @Parameter(description = "Document type for created thumb")
                                       @PathParam("fileType")
                                       String fileType,
                                       @Parameter(description = "Resized image size. Use 250x250 as default size.")
                                       @DefaultValue("250x250")
                                       @QueryParam("size")
                                       String size,
                                       @RequestBody(description = "image content", required = true)
                                       String data) {

    if (StringUtils.isBlank(documentId)) {
      return Response.status(Status.BAD_REQUEST).entity("document id is mandatory").build();
    }
    if (StringUtils.isBlank(fileType)) {
      return Response.status(Status.BAD_REQUEST).entity("fileType id is mandatory").build();
    }
    if (StringUtils.isBlank(data)) {
      return Response.status(Status.BAD_REQUEST).entity("data is mandatory").build();
    }
    try {
      int[] dimension = Utils.parseDimension(size);
      byte[] imageBytes = Base64.getDecoder().decode(data.replaceAll("^\"|\"$", ""));
      FileContent file = new FileContent(documentId, documentId, "image/jpeg", new ByteArrayInputStream(imageBytes));

      FileItem image = documentFileService.createThumbnail(fileType,
                                                           file,
                                                           ConversationState.getCurrent().getIdentity().getUserId(),
                                                           dimension[0],
                                                           dimension[1]);
      if (image == null) {
        return Response.status(Status.NOT_MODIFIED).build();
      }
      return Response.status(Status.OK).build();
    } catch (Exception e) {
      LOG.error("Error creating video thumbnail with Id {}", documentId, e);
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
    }
  }
}
