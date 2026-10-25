/**
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.mcp;

import static io.meeds.mcp.server.tool.util.McpToolPluginUtils.getInteger;
import static io.meeds.mcp.server.util.McpToolUtils.formatDate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jcr.RepositoryException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.constant.DocumentUploadLimit;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.constant.UploadConflictAction;
import org.exoplatform.documents.mcp.model.BreadcrumbItemModel;
import org.exoplatform.documents.mcp.model.DocumentFileModel;
import org.exoplatform.documents.mcp.model.ChunkedUpload;
import org.exoplatform.documents.mcp.model.DocumentFolderModel;
import org.exoplatform.documents.mcp.model.DocumentModel;
import org.exoplatform.documents.mcp.model.DocumentPermissionsModel;
import org.exoplatform.documents.mcp.model.DocumentOutlineModel;
import org.exoplatform.documents.mcp.model.DocumentHeadingModel;
import org.exoplatform.documents.mcp.model.DocumentPublicLinkModel;
import org.exoplatform.documents.mcp.model.DocumentTextPartModel;
import org.exoplatform.documents.mcp.model.DocumentTreeItemModel;
import org.exoplatform.documents.mcp.model.DocumentUploadModel;
import org.exoplatform.documents.mcp.model.DocumentVersionModel;
import org.exoplatform.documents.mcp.model.DocumentsSizeModel;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.BreadCrumbItem;
import org.exoplatform.documents.model.DocumentFolderFilter;
import org.exoplatform.documents.model.DocumentOutlineEntry;
import org.exoplatform.documents.model.DocumentTextContent;
import org.exoplatform.documents.model.DocumentTextPage;
import org.exoplatform.documents.model.DocumentTimelineFilter;
import org.exoplatform.documents.model.DocumentsSize;
import org.exoplatform.documents.constant.TranscriptionStatus;
import org.exoplatform.documents.model.FileNode;
import org.exoplatform.documents.model.TranscriptionState;
import org.exoplatform.documents.model.FileVersion;
import org.exoplatform.documents.model.FolderNode;
import org.exoplatform.documents.model.FullTreeItem;
import org.exoplatform.documents.model.NodePermission;
import org.exoplatform.documents.model.PermissionEntry;
import org.exoplatform.documents.model.PermissionRole;
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.documents.service.PublicDocumentAccessService;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.portal.rest.UserFieldValidator;
import org.exoplatform.services.attachments.service.AttachmentService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.favorite.FavoriteService;
import org.exoplatform.social.metadata.favorite.model.Favorite;
import org.exoplatform.upload.UploadResource;
import org.exoplatform.upload.UploadService;
import org.exoplatform.wiki.WikiException;
import org.exoplatform.wiki.model.Page;
import org.exoplatform.wiki.service.NoteService;

import io.meeds.mcp.server.model.ActingIdentity;
import io.meeds.mcp.server.plugin.McpToolPlugin;
import io.meeds.mcp.server.util.McpToolUtils;
import io.meeds.mcp.server.tool.model.UserModel;
import io.meeds.mcp.server.tool.util.UploadToolUtils;
import io.meeds.mcp.server.tool.util.UserToolUtils;
import io.meeds.social.translation.service.TranslationService;

import lombok.SneakyThrows;

/**
 * MCP tools exposing the Documents (DMS) add-on to the AI agent (EVA). Every
 * method acts as the current user, so document ACLs are enforced by
 * {@link DocumentFileService}. These tools only READ or update document
 * metadata / content; they carry no coupling to the AI service layer (the
 * AI-powered document experiences remain in the enterprise edition).
 */
@Service
@Profile("mcp-server")
public class DocumentMcpTool implements McpToolPlugin {

  public static final String       FOLDER_URL_FORMAT = "/portal/dw/documents?folderId=%s";

  public static final String       FILE_URL_FORMAT   = "/portal/dw/documents?documentPreviewId=%s";

  /**
   * Path of the anonymous public-download portal page (global page
   * <code>download-document</code>); the document node id is appended as an
   * extra path segment, mirroring how the Documents front-end builds the
   * shareable link (<code>{origin}/{containerName}/download-document/{nodeId}</code>).
   */
  public static final String       PUBLIC_LINK_URL_FORMAT = "/portal/download-document/%s";

  /** The characters {@code read_document_text} returns when the caller sets no number. */
  static final int                 DEFAULT_TEXT_PART_CHARS = 40_000;

  /** The most characters {@code read_document_text} returns in one call. */
  static final int                 MAX_TEXT_PART_CHARS     = 200_000;

  /**
   * The most children {@code list_folder_children} reads to keep those changed
   * since a date.
   */
  static final int                 MAX_SCANNED_CHILDREN   = 1000;

  /** The most base64 characters of one piece of a file sent in pieces. */
  static final int                 MAX_CHUNK_CHARS        = 1024 * 1024;

  /** The base64 characters of one piece an assistant is told to send. */
  static final int                 CHUNK_CHARS            = 20000;

  /** How long an upload in pieces is kept without a piece. */
  static final Duration            UPLOAD_TIME_TO_LIVE    = Duration.ofMinutes(30);

  /** The most uploads in pieces a user keeps in progress. */
  static final int                 MAX_OPEN_UPLOADS       = 3;

  /** The directory, under the temporary directory, of the pieces of the uploads in progress. */
  static final String              UPLOAD_DIRECTORY       = "documents-mcp-uploads";

  private static final Log        LOG = ExoLogger.getLogger(DocumentMcpTool.class);

  /**
   * Same password policy DocumentFileRest applies to public-link passwords
   * (min length 9), so a link created here is openable with the given password.
   */
  private static final UserFieldValidator PASSWORD_VALIDATOR = new UserFieldValidator("password", false, false, 9, 255);

  /**
   * Favorite object type for files. Ground truth: the Documents app reads/writes
   * file favorites as {@code metadataService} metadata "favorites" on object type
   * {@code "file"} + the JCR node id (see {@code EntityBuilder}, the
   * {@code DocumentsFavoriteButton.vue} button and the favorites drawer
   * registration). Writing any other type produces a favorite that no surface in
   * the app ever shows.
   */
  private static final String      FAVORITE_TYPE_FILE     = "file";

  private static final String      MSG_DESTINATION_OUTSIDE_SCOPE_SPACE =
                                                                       "The destination folder %s isn't in the space this call is limited to. Pick a folder of that space (get_root_folder_by_space, get_documents_by_folder_id).";

  private static final String      MSG_SCOPE_UNREADABLE   =
                                                          "The space this call is limited to can't be read, so it can't rename, move, copy, link, duplicate or hide a document";

  private static final String      MSG_SYSTEM_NODE        =
                                                          "The document %s was created by the platform for the space; a digital worker can't rename, move, copy, link, duplicate or hide it.";

  private static final String      MSG_HIDE_FOLDER_ONLY   =
                                                          "Only a folder can be hidden or shown; %s is a file.";

  /**
   * The note ending a text cut at the most characters read on the fly. It
   * gives no offset: read_document_text lays out a Word document, a
   * presentation, a PDF or a spreadsheet its own way, so this text's length is no position
   * in that one.
   */
  private static final String      MSG_TEXT_CONTINUES      =
                                                           "\n\n[The text continues past this point. To read the rest, call get_document_outline and read_document_text with the sections that follow, or read_document_text by offset from the start: its text is laid out its own way, so this text's length is not an offset in it.]";

  /** The answer while a large document's text is being extracted, or others' fill the queue. */
  private static final String      MSG_TEXT_BEING_READ     =
                                                           "The document with id %s is being read, or other large documents are, which takes a while. Call the same tool again in a minute.";

  /** The note ending the first part of a document too large to be read in one go: its length, where to read on. */
  private static final String      MSG_FIRST_PART_CONTINUES =
                                                            "\n\n[This is the first part of a large document, whose text is %d characters long%s. To read the rest, call read_document_text with offset=%d, or get_document_outline to find a chapter.]";

  /** Said of a text read part by part that was cut at the most characters read from one document. */
  private static final String      MSG_TEXT_CUT_AT_MOST     = " once cut at the most characters read from one document, so its end is missing";

  /** The note ending a text read part by part that was cut at the most characters read from one document. */
  private static final String      MSG_PAGED_TEXT_CUT       =
                                                            "\n\n[The document's text stops here: it was cut at the most characters read from one document.]";

  /** The refusal of a document too large even to be read part by part. */
  private static final String      MSG_TOO_LARGE_FOR_PARTS =
                                                           "The document with id %s is over the largest size whose text is read, even part by part.";

  /** The refusal of a section the outline doesn't have. */
  private static final String      MSG_NO_SUCH_SECTION     =
                                                           "There is no section %d in the document %s: its outline has %d headings, numbered from 0. Call get_document_outline to list them.";

  private static final String      MSG_CONTENT_TOO_LARGE  =
                                                          "The new version content is %d bytes, over the %d bytes limit.";

  /**
   * The error the model reads when {@code create_folder} names a parent folder
   * id no folder has, typically one it composed for a folder it creates in the
   * same round.
   */
  private static final String      PARENT_FOLDER_NOT_FOUND_MESSAGE =
                                                                   "No folder has the id %s. Use only a document_id a tool returned (create_folder, get_root_folder_for_user, get_root_folder_by_space, get_documents_by_folder_id), never a composed one. To create nested folders, call create_folder once with their path in 'name', such as 'Finances/Factures/Entrantes', under a folder that exists.";

  /**
   * The characters the storage refuses in a document title, as
   * {@code JCRDocumentsUtil#isValidDocumentTitle} of
   * {@code documents-storage-jcr} checks them.
   */
  private static final java.util.regex.Pattern INVALID_TITLE_CHARACTERS = java.util.regex.Pattern.compile("[<\\\\>:\"/|?*]");

  /** The refusal of an unknown space id, saying how to find the space. */
  private static final String      MSG_SPACE_NOT_FOUND    =
                                                          "Space with id %s doesn't exist. Pass space_name (the space's name) or find it with get_my_spaces.";

  /** The refusal of a space given both by id and by name. */
  private static final String      MSG_SPACE_ID_OR_NAME   = "Pass either space_id or space_name, not both.";

  /** The refusal of a call that gives no space where one is required. */
  private static final String      MSG_SPACE_REQUIRED     =
                                                          "Pass the space: its space_name (the space's name) or its space_id; get_my_spaces lists your spaces.";

  /**
   * The refusal of a space name naming another space than the one the call is
   * limited to, worded as the space scope refuses another space's id.
   */
  private static final String      MSG_NAME_OUTSIDE_SCOPE =
                                                          "Not executed: while this run is limited to the space with id %s, '%s' can only reach that space, and the space_name '%s' names another one. Call it again without space_name, or answer from what you already have.";

  /** The refusal of a space name when the call's space can't be read. */
  private static final String      MSG_NAME_SCOPE_UNREADABLE =
                                                             "The space this call is limited to can't be read, so a space can't be named by its space_name. Call it again with its space_id.";

  /** The refusal of a date-limited listing of a folder holding too many children. */
  private static final String      MSG_TOO_MANY_CHILDREN  =
                                                          "The folder holds more than %d documents, too many to keep the changed ones here. Call search_documents with parent_folder_id and updated_since instead (it searches the whole folder tree, files only).";

  /** The refusal of an unreadable {@code updated_since}. */
  private static final String      MSG_UPDATED_SINCE_UNREADABLE =
                                                                "The date '%s' can't be read. Pass updated_since as an ISO-8601 date or date-time, such as 2026-10-01 or 2026-10-01T08:00:00Z.";

  /** The refusal of a shortcut's id given as the folder a tool writes in. */
  private static final String      MSG_FOLDER_IS_SHORTCUT  =
                                                           "The folder id %s is a shortcut to the folder %s: pass that folder's id as %s to write there (get_document_permissions tells whether you can write in it).";

  /** The refusal of a shortcut to a file given as the folder a tool writes in. */
  private static final String      MSG_FILE_SHORTCUT_IS_NO_FOLDER =
                                                                  "The id %s is a shortcut to the file %s, not a folder: pass a folder's id as %s.";

  /** The refusal of a file's id given as the folder a tool writes in. */
  private static final String      MSG_FILE_IS_NO_FOLDER   =
                                                           "The id %s is a file, not a folder: pass a folder's id as %s.";

  /** The argument of the tools that add a document or a folder to a folder. */
  private static final String      PARENT_FOLDER_ID_ARG      = "parent_folder_id";

  /** The argument of the tools that move, copy or link a document to a folder. */
  private static final String      DESTINATION_FOLDER_ID_ARG = "destination_folder_id";

  /**
   * The children of a folder read in one page when a tool looks a child
   * folder up by name, or lists a folder's children before and after a copy
   * or a duplicate to find the one added: only that first page is read, so a
   * folder holding more children is searched in its first ones only.
   */
  private static final int         FOLDER_PAGE_SIZE       = 200;

  /**
   * The refusal of a base64 file too long for one upload_document call: a
   * larger one is sent through an upload link, or in pieces without a shell.
   */
  private static final String      MSG_UPLOAD_BASE64_TOO_LONG =
                                                              "'base64' is at most %d characters in one upload_document call, and this one is %d. For a file on your machine, open an upload link with start_upload, run its curl command in your shell, then call upload_document with its upload_id. With no shell, send a small file in pieces: start_document_upload, then append_document_upload_chunk for each piece of at most %d characters.";

  /**
   * The refusal of a base64 new version too long for one add_document_version
   * call: a larger one is sent through an upload link, or, without a shell,
   * in pieces into the file's folder, under the file's name, as a new version
   * of the file of that name.
   */
  private static final String      MSG_VERSION_BASE64_TOO_LONG =
                                                               "'base64' is at most %d characters in one add_document_version call, and this one is %d. For a file on your machine, open an upload link with start_upload, run its curl command in your shell, then call add_document_version with its upload_id. With no shell, send a small new version in pieces: start_document_upload into the file's folder (its parent_folder_id), with the file's exact name and on_conflict=new_version, then append_document_upload_chunk for each piece of at most %d characters. That adds a version to the file of that name in that folder (a new file if the folder holds none of that name), without a version_summary.";

  /**
   * The refusal of a base64 new version too long for one add_document_version
   * call in a call limited to a space, where the tools that send a file in
   * pieces are not offered.
   */
  private static final String      MSG_VERSION_BASE64_TOO_LONG_IN_SPACE =
                                                                        "'base64' is at most %d characters in one add_document_version call, and this one is %d. While this run is limited to a space, a larger new version can't be sent from here: sending a file in pieces isn't available in it. Ask the user to add the new version from the Documents app.";

  /**
   * Appended to the fetcher's refusal of a URL over
   * {@link DocumentUploadLimit#URL_FETCH_MAX_BYTES}, or the upload size
   * limit when it is lower: what to do with a larger file.
   */
  private static final String      MSG_URL_TOO_LARGE_HINT =
                                                          " A file is read from a URL up to %d MB. For a larger file on your machine, open an upload link with start_upload, run its curl command in your shell, then pass its upload_id; otherwise ask the user to attach it in the conversation.";

  /**
   * How mcp-server's shared fetcher words its refusal of a body over the
   * limit asked ({@code UploadToolUtils#fetchInternal}, failure
   * {@code TOO_LARGE}): the one refusal this tool completes with what to do
   * instead.
   */
  private static final String      FETCH_TOO_LARGE_MARKER = "exceeds the maximum allowed size";

  /**
   * The refusal of an upload id that names no upload of the user's on this
   * node, worded alike whether the id is unknown or another user's.
   */
  private static final String      MSG_UPLOAD_ID_UNKNOWN  =
                                                          "No upload '%s' of yours is waiting on this server: it expired, a tool already consumed it, or its curl command reached another server node. Check it with get_upload, or open a new one with start_upload and run its curl command again.";

  /** The refusal of an upload still being sent, left for a later call. */
  private static final String      MSG_UPLOAD_ID_UPLOADING =
                                                           "The upload '%s' is still being sent: wait for its curl command to answer {\"status\":\"uploaded\"} (get_upload tells where it stands), then call this tool again with it.";

  /** The refusal of an upload that failed, or whose file is gone. */
  private static final String      MSG_UPLOAD_ID_FAILED   =
                                                          "The upload '%s' did not complete, and is dropped: open a new one with start_upload and run its curl command again.";

  /**
   * The refusal of an upload id in a call limited to a space, where
   * start_upload is not offered.
   */
  private static final String      MSG_UPLOAD_ID_IN_SPACE =
                                                          "While this run is limited to a space, a file sent through an upload link can't be used: start_upload isn't available in it. Ask the user to add the file from the Documents app.";

  /** The refusal of an attached file over the upload size limit. */
  private static final String      MSG_ATTACHMENT_TOO_LARGE =
                                                            "The attached file is %d bytes, over the %d MB a file uploaded can be.";

  /** The type an upload is staged under: the filing resolves the file's own from its name. */
  private static final String      DEFAULT_UPLOAD_MIME_TYPE = "application/octet-stream";

  /** The refusal of an upload into a document that is not a folder. */
  private static final String      MSG_UPLOAD_NOT_A_FOLDER =
                                                           "'%s' (%s) is a file, not a folder: a file is uploaded into a folder. To add a new version of this file, use add_document_version.";

  /** Said of an upload in pieces whose file couldn't be filed. */
  private static final String      MSG_UPLOAD_ENDED       = " The upload is ended: start it again with start_document_upload.";

  /** The refusal of an upload into a folder the user can't write in. */
  private static final String      MSG_UPLOAD_FOLDER_NOT_WRITABLE =
                                                                  "You can't add documents to the folder '%s' (%s): you can read it but not write in it. Pick a folder whose can_edit is true (get_document_permissions tells it), or ask a space manager for the right.";

  /** The creator of the nodes the platform creates for a space. */
  private static final String      SYSTEM_USER            = "__system";

  /**
   * Structured / binary office and PDF formats that cannot be produced from a
   * plain text string: they are zipped packages (OOXML/ODF) or a binary layout
   * (PDF), so writing text content as the file body would yield a corrupt file.
   * {@link #createDocument} rejects these up front and points to
   * {@code upload_document} (real file bytes) instead.
   */
  private static final Set<String> UNSUPPORTED_BINARY_EXTENSIONS =
                                                                 Set.of("docx", "xlsx", "pptx", "ppsx", "potx", "dotx", "xltx",
                                                                        "xls", "doc", "ppt", "odt", "ods", "odp", "pdf");

  /**
   * Office document types that {@link #createDocumentFromTemplate} can create as a
   * new empty file from the platform's blank template (via the ecms
   * {@code DocumentService}). Availability of a given type at runtime still
   * depends on the installed document editor add-on (onlyoffice).
   */
  private static final Set<String> SUPPORTED_TEMPLATE_TYPES =
                                                            Set.of("docx", "xlsx", "pptx");

  private final DocumentFileService documentFileService;

  private final AttachmentService   attachmentService;

  /**
   * Social attachment service (distinct from the ecms {@link AttachmentService}
   * above), used to resolve a chat-attached file referenced by
   * <code>attachment_object_type</code> / <code>attachment_object_id</code> back
   * to its stored bytes, as the current user, so its ACL is enforced.
   */
  private final org.exoplatform.social.attachment.AttachmentService socialAttachmentService;

  private final FileService         fileService;

  private final IdentityManager     identityManager;

  private final SpaceService        spaceService;

  private final TranslationService  translationService;

  private final ProfilePropertyService profilePropertyService;

  private final UserACL             userAcl;

  private final UserPortalConfigService portalConfigService;

  private final UploadService       uploadService;

  private final PublicDocumentAccessService publicDocumentAccessService;

  private final FavoriteService     favoriteService;

  /** Finds a space by its name among the calling user's spaces. */
  private final SpaceNameResolver   spaceNameResolver;

  /** The uploads in pieces in progress on this node. */
  private final ChunkedUploadStore  chunkedUploads;

  public DocumentMcpTool(DocumentFileService documentFileService,
                         AttachmentService attachmentService,
                         org.exoplatform.social.attachment.AttachmentService socialAttachmentService,
                         FileService fileService,
                         IdentityManager identityManager,
                         SpaceService spaceService,
                         TranslationService translationService,
                         ProfilePropertyService profilePropertyService,
                         UserACL userAcl,
                         UserPortalConfigService portalConfigService,
                         UploadService uploadService,
                         PublicDocumentAccessService publicDocumentAccessService,
                         FavoriteService favoriteService) {
    this.documentFileService = documentFileService;
    this.attachmentService = attachmentService;
    this.socialAttachmentService = socialAttachmentService;
    this.fileService = fileService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.translationService = translationService;
    this.profilePropertyService = profilePropertyService;
    this.userAcl = userAcl;
    this.portalConfigService = portalConfigService;
    this.uploadService = uploadService;
    this.publicDocumentAccessService = publicDocumentAccessService;
    this.favoriteService = favoriteService;
    this.spaceNameResolver = new SpaceNameResolver(spaceService);
    this.chunkedUploads = new ChunkedUploadStore(DocumentUploadLimit.getMaxBytes(),
                                                 MAX_OPEN_UPLOADS,
                                                 UPLOAD_TIME_TO_LIVE,
                                                 Clock.systemUTC(),
                                                 Path.of(System.getProperty("java.io.tmpdir"), UPLOAD_DIRECTORY));
  }

  /**
   * @return the space the current call is limited to, null when it isn't
   * @throws IllegalStateException when the call's identity can't be read
   */
  protected Long getScopeSpaceId() {
    ActingIdentity identity = McpToolUtils.getCurrentActingIdentity();
    return identity == null ? null : identity.spaceScope();
  }

  /**
   * Checks the destination folder of a call limited to a space is in that
   * space, by the same mapping the {@value DocumentSpaceScopeResolver#NAME}
   * resolver gives the moved, copied or linked node: the space scope checks
   * that node only, and the destination is a second node, so a move, a copy
   * or a shortcut could otherwise take the node out of the space. A call that
   * isn't limited is not checked; one whose space can't be read is refused.
   *
   * @param destinationFolderId the destination folder
   * @throws IllegalAccessException when the call is limited to a space the
   *           destination folder isn't in, or its space can't be read
   */
  private void checkDestinationInScopeSpace(String destinationFolderId) throws IllegalAccessException {
    Long spaceId;
    try {
      spaceId = getScopeSpaceId();
    } catch (IllegalStateException e) {
      throw new IllegalAccessException(MSG_SCOPE_UNREADABLE);
    }
    if (spaceId == null) {
      return;
    }
    Long destinationSpaceId = new DocumentSpaceScopeResolver(documentFileService, spaceService).spaceOf(destinationFolderId);
    if (!spaceId.equals(destinationSpaceId)) {
      throw new IllegalAccessException(MSG_DESTINATION_OUTSIDE_SCOPE_SPACE.formatted(destinationFolderId));
    }
  }

  /**
   * Checks a call limited to a space may rename, move, copy, link, duplicate
   * or hide a node, as the Documents application lets a space member who
   * isn't an administrator do: not a node the platform created for the space (creator
   * {@value #SYSTEM_USER}), and, for a visibility change, a folder only. A call
   * that isn't limited is not checked; one whose space can't be read is
   * refused.
   *
   * @param documentId the node
   * @param folderOnly true when only a folder may be changed
   * @throws IllegalAccessException when the call is limited to a space and the
   *           node was created by the platform, or isn't a folder when one is
   *           required, or the call's space can't be read
   * @throws ObjectNotFoundException when the node doesn't exist
   */
  private void checkScopedChange(String documentId, boolean folderOnly) throws IllegalAccessException,
                                                                         ObjectNotFoundException {
    Long spaceId;
    try {
      spaceId = getScopeSpaceId();
    } catch (IllegalStateException e) {
      throw new IllegalAccessException(MSG_SCOPE_UNREADABLE);
    }
    if (spaceId == null) {
      return;
    }
    AbstractNode node = getNode(documentId);
    if (SYSTEM_USER.equals(node.getCreatorUserName())) {
      throw new IllegalAccessException(MSG_SYSTEM_NODE.formatted(documentId));
    } else if (folderOnly && !(node instanceof FolderNode)) {
      throw new IllegalAccessException(MSG_HIDE_FOLDER_ONLY.formatted(documentId));
    }
  }

  /**
   * Reads the root folder of a space's documents, the space given by its id
   * or by its name, exactly one of them. A name is looked up among the spaces
   * the user is a member of, and nothing is guessed.
   *
   * @param spaceId the space's id, null when the space is given by its name
   * @param spaceName the space's name, blank when the space is given by its
   *          id
   * @return the space's root folder
   * @throws ObjectNotFoundException when the space doesn't exist, or no space
   *           of the user carries the name: the message says how to find it
   * @throws IllegalAccessException when the user can't view the space, or a
   *           call limited to a space names another one
   * @throws IllegalArgumentException when both or neither are given, or
   *           several of the user's spaces carry the name
   */
  public DocumentFolderModel getRootFolderBySpace(Long spaceId, String spaceName) throws ObjectNotFoundException,
                                                                                  IllegalAccessException {
    Long resolvedSpaceId = resolveSpaceId(spaceId, spaceName, "get_root_folder_by_space");
    if (resolvedSpaceId == null) {
      throw new IllegalArgumentException(MSG_SPACE_REQUIRED);
    }
    FolderNode spaceRootFolder;
    try {
      spaceRootFolder = documentFileService.getSpaceRootFolder(resolvedSpaceId, getCurrentUserAclIdentity());
    } catch (ObjectNotFoundException e) {
      throw new ObjectNotFoundException(MSG_SPACE_NOT_FOUND.formatted(resolvedSpaceId));
    }
    return toDocumentFolderModel(spaceRootFolder);
  }

  /**
   * Tells which space a call names, by its id or by its name, at most one of
   * them. A name is looked up among the spaces the user is a member of; in a
   * call limited to a space, a name of another space is refused as the space
   * scope refuses another space's id, and one whose space can't be read is
   * refused too.
   *
   * @param spaceId the space's id, null or not positive when not given
   * @param spaceName the space's name, blank when not given
   * @param toolName the tool the call runs, named in a refusal
   * @return the space's id, null when neither is given
   * @throws ObjectNotFoundException when no space of the user carries the name
   * @throws IllegalAccessException when a call limited to a space names
   *           another one, or its space can't be read
   * @throws IllegalArgumentException when both are given, or several of the
   *           user's spaces carry the name
   */
  private Long resolveSpaceId(Long spaceId, String spaceName, String toolName) throws ObjectNotFoundException,
                                                                               IllegalAccessException {
    boolean hasId = spaceId != null && spaceId > 0;
    boolean hasName = StringUtils.isNotBlank(spaceName);
    if (hasId && hasName) {
      throw new IllegalArgumentException(MSG_SPACE_ID_OR_NAME);
    } else if (hasId) {
      return spaceId;
    } else if (!hasName) {
      return null;
    }
    Long scopeSpaceId;
    try {
      scopeSpaceId = getScopeSpaceId();
    } catch (IllegalStateException e) {
      throw new IllegalAccessException(MSG_NAME_SCOPE_UNREADABLE);
    }
    Space space = spaceNameResolver.resolve(spaceName, getCurrentUserName());
    Long resolvedSpaceId = Long.valueOf(space.getId());
    if (scopeSpaceId != null && !scopeSpaceId.equals(resolvedSpaceId)) {
      throw new IllegalAccessException(MSG_NAME_OUTSIDE_SCOPE.formatted(scopeSpaceId, toolName, spaceName.trim()));
    }
    return resolvedSpaceId;
  }

  /**
   * Links an existing document to another content entity (an activity, a note, …)
   * as an attachment, acting as the current user. Validates the document id and the
   * user's read access on it first — matching every sibling tool method — so a
   * blank or inaccessible id fails cleanly instead of reaching the attachment
   * service with an unchecked reference.
   *
   * @param documentId the id of the document to attach
   * @param contentType the target content type the document is attached to
   * @param contentId the target content id the document is attached to
   */
  public void attachDocumentToContent(String documentId,
                                      String contentType,
                                      long contentId) throws IllegalAccessException, ObjectNotFoundException {
    checkCanAccessDocument(documentId);
    // FIXME Why using this content type name ??!!
    contentId = fixNoteContentId(contentType, contentId);
    // FIXME Why using this content type name ??!!
    contentType = fixNoteContentType(contentType);
    attachmentService.linkAttachmentToEntity(getCurrentUserIdentityId(), contentId, contentType, documentId);
  }

  public void updateDocumentDescription(String documentId, String htmlDescription) throws IllegalAccessException, Exception { // NOSONAR
    long ownerId = documentFileService.getRootFolderOwnerId(documentId);
    documentFileService.updateDocumentDescription(ownerId, documentId, htmlDescription, getCurrentUserIdentityId());
  }

  /**
   * Lists a page of a folder's children, folders first. A negative limit
   * lists nothing, as list_folder_children does: the storage would read every
   * child.
   *
   * @param folderId the folder
   * @param filesOnly whether only the files are kept
   * @param foldersOnly whether only the folders are kept
   * @param offset the first child listed, 0 by default
   * @param limit the most children listed, the default page size when null
   *          or 0
   * @return the folder's children on that page
   * @throws ObjectNotFoundException when the folder doesn't exist
   * @throws IllegalAccessException when the user can't read the folder
   */
  public List<DocumentModel> getDocumentsByFolderId(String folderId,
                                                    Boolean filesOnly,
                                                    Boolean foldersOnly,
                                                    Integer offset,
                                                    Integer limit) throws ObjectNotFoundException,
                                                                   IllegalAccessException {
    int size = Math.max(getInteger(limit, DEFAULT_LIMIT), 0);
    if (size == 0) {
      // a negative limit; the storage reads every child when given none
      return List.of();
    }
    List<? extends AbstractNode> documentItems = documentFileService.getDocumentItems(FileListingType.FOLDER,
                                                                                      new DocumentFolderFilter(folderId,
                                                                                                               null,
                                                                                                               null,
                                                                                                               null),
                                                                                      getInteger(offset, DEFAULT_OFFSET),
                                                                                      size,
                                                                                      getCurrentUserIdentityId(),
                                                                                      true);
    return documentItems.stream()
                        .filter(d -> (filesOnly == null || !filesOnly || d instanceof FileNode)
                                     && (foldersOnly == null || !foldersOnly || d instanceof FolderNode))
                        .map(this::toDocumentModel)
                        .toList();
  }

  public DocumentFolderModel getRootFolderForUser() {
    FolderNode personalRootFolder = documentFileService.getPersonalRootFolder(getCurrentUserAclIdentity());
    return toDocumentFolderModel(personalRootFolder);
  }

  /**
   * Returns a document's metadata, with the user's rights on it. A shortcut
   * resolves to its target, whose metadata and edit right are returned; its
   * delete right is left out, deleting the shortcut removing only the
   * shortcut.
   *
   * @param documentId the document id
   * @return the document
   * @throws IllegalAccessException when the user can't read it
   * @throws ObjectNotFoundException when it doesn't exist
   */
  public DocumentModel getDocumentById(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    AbstractNode document = documentFileService.getDocumentById(documentId, getCurrentUserName());
    DocumentModel model = toDocumentModel(document);
    if (!StringUtils.equals(documentId, document.getId())) {
      // a shortcut resolves to its target: deleting the shortcut removes only the shortcut
      model.setCanDelete(null);
    }
    return model;
  }

  /**
   * Returns the text of a document the current user can access: from the search
   * index when it holds it, else extracted from the file itself. When neither
   * gives any text, the actual reason is raised rather than an empty answer, so
   * the agent can tell the user why instead of guessing from the file name.
   * <p>
   * The reason never claims the file is still being indexed: the search index
   * service exposes no way to know whether an indexing operation is pending for
   * a file, and the file itself was just read without success. Only a busy
   * extractor, a fact known for certain, is worded as worth another attempt.
   * <p>
   * A file too large to be read in one go is read as {@code read_document_text}
   * reads it, up to the largest file a user can attach: its first part is
   * returned, with the length of its text and the offset to read on.
   *
   * @param documentId the document identifier
   * @return the text of the document
   * @throws IllegalAccessException when the user cannot access the document
   * @throws ObjectNotFoundException when the document does not exist
   * @throws IllegalStateException when the document has no readable text, with the reason
   */
  public String getDocumentContentById(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    checkCanAccessDocument(documentId);
    DocumentTextContent content = documentFileService.getFileTextContent(documentId);
    if (content != null && content.hasText()) {
      return content.truncated() ? content.text() + MSG_TEXT_CONTINUES : content.text();
    }
    if (content != null && content.status() == DocumentTextContent.Status.TOO_LARGE) {
      return readFirstPart(documentId);
    }
    throw new IllegalStateException(getNoTextMessage(documentId,
                                                     content == null ? DocumentTextContent.Status.UNREADABLE
                                                                     : content.status()));
  }

  /**
   * Reads the first part of a document too large to be read in one go, as
   * {@code read_document_text} reads it, ending with how to read on.
   *
   * @param documentId the document id
   * @return the first part, with its note
   * @throws IllegalStateException when the document has no readable text, with
   *           the reason
   */
  private String readFirstPart(String documentId) {
    DocumentTextPage page = getTextPage(documentId, 0, MAX_TEXT_PART_CHARS);
    long partEnd = page.offset() + page.text().length();
    if (partEnd < page.totalChars()) {
      return page.text() + MSG_FIRST_PART_CONTINUES.formatted(page.totalChars(), page.truncated() ? MSG_TEXT_CUT_AT_MOST : "", partEnd);
    }
    return page.truncated() ? page.text() + MSG_PAGED_TEXT_CUT : page.text();
  }

  /**
   * Why a document has no text, worded for the agent to relay to the user.
   *
   * @param documentId the document identifier
   * @param status why no text could be obtained
   * @return the message of the exception raised to the agent
   */
  static String getNoTextMessage(String documentId, DocumentTextContent.Status status) {
    String prefix = "The text of the document with id %s could not be extracted: ".formatted(documentId);
    return switch (status) {
    case UNSUPPORTED_FORMAT -> prefix + "its format is not supported for text extraction.";
    case NO_TEXT -> prefix + "no text was found in the file (a scanned document or an image, for instance).";
    case NOT_A_FILE -> "The document with id %s is not a file with a content, so it has no text.".formatted(documentId);
    case BUSY -> prefix + "too many documents are being read at the moment; asking again in a little while may succeed.";
    case TIMED_OUT -> prefix + "reading the file took too long.";
    default -> prefix + "the file could not be read.";
    };
  }

  /**
   * Reads a part of a document's text: from an offset, or the section of one
   * heading of {@code get_document_outline}, at most a given number of
   * characters, and says where the next part starts. A document too large to
   * be read in one go is read this way, part by part.
   *
   * @param documentId the document id
   * @param offset the first character, from 0, in the whole text, as
   *          next_offset gives it; within a section, brought into it: the
   *          section's start when before it
   * @param maxChars the most characters returned
   * @param section the index of the heading whose section is read, null to
   *          read by offset
   * @return the part read
   * @throws IllegalAccessException when the user cannot access the document
   * @throws ObjectNotFoundException when the document does not exist
   * @throws IllegalStateException when the document has no readable text, with
   *           the reason
   * @throws IllegalArgumentException when the section isn't one of the outline
   */
  public DocumentTextPartModel readDocumentText(String documentId,
                                                Long offset,
                                                Integer maxChars,
                                                Integer section) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    AbstractNode document = checkCanAccessDocument(documentId);
    int size = Math.clamp(maxChars == null ? DEFAULT_TEXT_PART_CHARS : maxChars, 1, MAX_TEXT_PART_CHARS);
    long start = offset == null ? 0 : Math.max(0, offset);
    DocumentHeadingModel heading = null;
    long end = Long.MAX_VALUE;
    if (section != null) {
      List<DocumentHeadingModel> headings = toHeadings(getTextPage(documentId, 0, 0));
      if (section < 0 || section >= headings.size()) {
        throw new IllegalArgumentException(MSG_NO_SUCH_SECTION.formatted(section, documentId, headings.size()));
      }
      heading = headings.get(section);
      end = heading.endOffset();
      start = Math.clamp(start, heading.offset(), Math.max(heading.offset(), end));
    }
    DocumentTextPage page = getTextPage(documentId, start, (int) Math.max(0, Math.min(size, end - start)));
    end = Math.min(end, page.totalChars());
    long partEnd = page.offset() + page.text().length();
    return new DocumentTextPartModel(documentId,
                                     document.getName(),
                                     heading,
                                     page.offset(),
                                     partEnd,
                                     page.totalChars(),
                                     partEnd < end ? partEnd : null,
                                     page.truncated(),
                                     page.text());
  }

  /**
   * Lists a document's headings: a Word document's headings, a PDF's bookmarks,
   * a presentation's slide titles, a spreadsheet's sheets, each with where its
   * section starts and ends in the text {@code read_document_text} reads.
   *
   * @param documentId the document id
   * @return the outline, empty when the document has no heading
   * @throws IllegalAccessException when the user cannot access the document
   * @throws ObjectNotFoundException when the document does not exist
   * @throws IllegalStateException when the document has no readable text, with
   *           the reason
   */
  public DocumentOutlineModel getDocumentOutline(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    AbstractNode document = checkCanAccessDocument(documentId);
    DocumentTextPage page = getTextPage(documentId, 0, 0);
    return new DocumentOutlineModel(documentId, document.getName(), page.totalChars(), page.truncated(), toHeadings(page));
  }

  /**
   * Reads a part of a document's text, raising why when it has none.
   *
   * @param documentId the document id
   * @param offset the first character
   * @param maxChars the most characters
   * @return the part, with the text's length and outline
   * @throws IllegalStateException when the document has no readable text
   */
  private DocumentTextPage getTextPage(String documentId, long offset, int maxChars) {
    DocumentTextPage page = documentFileService.getFileTextPage(documentId, offset, maxChars);
    if (page == null || !page.hasText()) {
      DocumentTextContent.Status status = page == null ? DocumentTextContent.Status.UNREADABLE : page.status();
      throw new IllegalStateException(switch (status) {
      case BUSY -> MSG_TEXT_BEING_READ.formatted(documentId);
      case TOO_LARGE -> MSG_TOO_LARGE_FOR_PARTS.formatted(documentId);
      default -> getNoTextMessage(documentId, status);
      });
    }
    return page;
  }

  /**
   * Turns a document's outline into headings, each section ending at the next
   * heading of the same or a higher level that starts after it, or at the end
   * of the text: a heading sharing its start with the next one, two bookmarks
   * on one PDF page, covers that page too.
   *
   * @param page a part of the text, carrying the outline
   * @return the headings
   */
  private static List<DocumentHeadingModel> toHeadings(DocumentTextPage page) {
    List<DocumentOutlineEntry> outline = page.outline();
    List<DocumentHeadingModel> headings = new ArrayList<>(outline.size());
    for (int i = 0; i < outline.size(); i++) {
      DocumentOutlineEntry entry = outline.get(i);
      long end = page.totalChars();
      for (int j = i + 1; j < outline.size(); j++) {
        DocumentOutlineEntry next = outline.get(j);
        if (next.level() <= entry.level() && next.offset() > entry.offset()) {
          end = next.offset();
          break;
        }
      }
      headings.add(new DocumentHeadingModel(i, entry.level(), entry.title(), entry.offset(), end, entry.page()));
    }
    return headings;
  }

  /**
   * The transcript of an audio or video file, read with the user's rights. When
   * there is none, the answer says why, from the file's transcription state
   * (EXO-92097): queued, in progress with its progress, stopped when its run was
   * lost (running, unchanged for {@link TranscriptionState#STALE_MILLIS}), failed
   * with its reason, transcribed with no speech heard; a file never
   * sent to transcription answers as before. A transcript grouped into
   * chapters (EXO-92122) is given after the list of its chapters, each with the
   * moment it starts, its title and its summary, so that the assistant can say
   * where in the recording a subject was discussed.
   *
   * @param documentId the file's UUID
   * @return the transcript
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist
   * @throws IllegalStateException when the file has no transcript, saying why
   */
  public String getDocumentTranscriptionById(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode document = checkCanAccessDocument(documentId);
    long identityId = getCurrentUserIdentityId();
    String audioTranscription = documentFileService.getAudioTranscription(documentId, identityId);
    if (StringUtils.isNotBlank(audioTranscription)) {
      String chapters = toChaptersOutline(documentId, identityId);
      return chapters == null ? audioTranscription : chapters + "\n\nTranscript:\n" + audioTranscription;
    } else if (!(document instanceof FileNode fileNode)
               || !StringUtils.startsWithAny(fileNode.getMimeType(), "video/", "audio/")) {
      throw new IllegalStateException("The document with id %s isn't an audio nor a video file, so it has no transcription."
          .formatted(documentId));
    }
    TranscriptionState state = documentFileService.getTranscriptionState(documentId, identityId);
    TranscriptionStatus status = state == null ? null : state.status();
    if (status == null) {
      throw new IllegalStateException("No transcription is available yet for this audio/video file. "
          + "Transcriptions are generated automatically once the media is processed; tell the user to try again later.");
    } else if (status == TranscriptionStatus.PROCESSING && !state.isInProgress(System.currentTimeMillis())) {
      // A running transcription writes its progress after each piece: unchanged for long, its run was lost.
      // A queued one may wait that long behind other runs, and is not.
      throw new IllegalStateException("The transcription of this audio/video file stopped without finishing: "
          + "tell the user it did not complete.");
    }
    throw new IllegalStateException(switch (status) {
    case QUEUED -> StringUtils.isBlank(state.error()) ? "The transcription of this audio/video file is queued and has not started yet: "
        + "tell the user it is on its way and to ask again later."
                                                      : "The last attempt to transcribe this audio/video file failed (%s), and a new attempt is scheduled: tell the user it will be tried again and to ask later."
                                                          .formatted(toTranscriptionFailure(state.error()));
    case PROCESSING -> "The transcription of this audio/video file is in progress%s: tell the user it is being produced and to ask again in a few minutes."
        .formatted(StringUtils.isBlank(state.progress()) ? "" : " (" + state.progress() + " pieces transcribed)");
    case FAILED -> "The transcription of this audio/video file failed: %s. Tell the user it failed, and why."
        .formatted(toTranscriptionFailure(state.error()));
    case DONE -> "This audio/video file was transcribed, but no speech was heard in it.";
    });
  }

  /**
   * Writes the chapters a transcript is grouped into, one per line, each with
   * the moment it starts, its title and its summary.
   *
   * @param documentId the file's UUID
   * @param identityId the identity id of the user reading it
   * @return the outline, starting with {@code Chapters:}, or null when the
   *         transcript has no chapter, or none that can be read
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist
   */
  private String toChaptersOutline(String documentId, long identityId) throws IllegalAccessException, ObjectNotFoundException {
    String chapters = documentFileService.getTranscriptionChapters(documentId, identityId);
    if (StringUtils.isBlank(chapters)) {
      return null;
    }
    StringBuilder outline = new StringBuilder("Chapters:");
    try {
      JSONArray chaptersArray = new JSONArray(chapters);
      for (int i = 0; i < chaptersArray.length(); i++) {
        JSONObject chapter = chaptersArray.optJSONObject(i);
        String title = chapter == null ? null : StringUtils.trimToNull(chapter.optString("title", null));
        double start = chapter == null ? Double.NaN : chapter.optDouble("start", Double.NaN);
        if (title != null && Double.isFinite(start) && start >= 0) {
          String summary = StringUtils.trimToNull(chapter.optString("summary", null));
          outline.append("\n- ").append(toChapterMoment((long) start)).append(' ').append(title);
          if (summary != null) {
            outline.append(": ").append(summary);
          }
        }
      }
    } catch (JSONException e) {
      LOG.debug("The chapters of media file {} cannot be read, its transcript is given alone", documentId, e);
      return null;
    }
    return outline.length() == "Chapters:".length() ? null : outline.toString();
  }

  /**
   * @param seconds a moment, in whole seconds from the beginning of the media
   * @return the moment as a marker: {@code [mm:ss · Ns]}, or
   *         {@code [h:mm:ss · Ns]} past an hour, N being the moment in seconds
   *         so that the assistant links to it without converting
   */
  static String toChapterMoment(long seconds) {
    long hours = seconds / 3600;
    long minutes = (seconds % 3600) / 60;
    long remainder = seconds % 60;
    String time = hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, remainder) : "%02d:%02d".formatted(minutes, remainder);
    return "[%s · %ds]".formatted(time, seconds);
  }

  /**
   * Says in words why a transcription failed, from its failure code.
   *
   * @param error the failure's message code
   * @return the reason, for the assistant to tell the user
   */
  private String toTranscriptionFailure(String error) {
    return switch (StringUtils.defaultString(error)) {
    case "ffmpeg.unavailable" -> "the server has no tool to convert this recording, which its format or its size needs";
    case "media.unreadable" -> "the file could not be read";
    case "transcription.disabled" -> "transcription is switched off, or its transcription assistant is not available";
    case "attempts.exhausted" -> "every attempt failed";
    case "provider.timeout" -> "the transcription service did not answer in time";
    case "provider.rejected" -> "the transcription service refused it";
    default -> "no reason was recorded";
    };
  }

  /**
   * Searches the documents the user can read, optionally in one space, given
   * by its id or by its name (at most one of them), or under one folder.
   *
   * @param query the full-text query
   * @param spaceId the space to search in, null for none
   * @param spaceName the name of the space to search in, blank for none
   * @param parentFolderId the folder to search under, which wins over the
   *          space
   * @param offset the first result
   * @param limit the most results
   * @param isFavorites true to search the user's favorites only
   * @param updatedSince an ISO-8601 date or date-time: only the files changed
   *          since then, the most recent first; blank for any date
   * @return the matching documents
   * @throws ObjectNotFoundException when the space doesn't exist, or no space
   *           of the user carries the name
   * @throws IllegalAccessException when the user can't view the space
   * @throws IllegalArgumentException when both a space id and a name are
   *           given, several of the user's spaces carry the name, the date
   *           can't be read, or neither a query nor a date is given
   */
  public List<DocumentFileModel> searchDocuments(String query,
                                                 Long spaceId,
                                                 String spaceName,
                                                 String parentFolderId,
                                                 Integer offset,
                                                 Integer limit,
                                                 Boolean isFavorites,
                                                 String updatedSince) throws ObjectNotFoundException, IllegalAccessException {
    Long updatedSinceTime = parseUpdatedSince(updatedSince);
    if (StringUtils.isBlank(query) && updatedSinceTime == null) {
      throw new IllegalArgumentException("Pass a 'query', or 'updated_since' to list the files changed since a date.");
    }
    DocumentTimelineFilter filter = new DocumentTimelineFilter();
    filter.setQuery(query);
    if (isFavorites != null && isFavorites.booleanValue()) {
      filter.setFavorites(true);
    }
    if (updatedSinceTime != null) {
      filter.setAfterDate(updatedSinceTime);
      filter.setSortField(DocumentSortField.MODIFIED_DATE);
      filter.setAscending(false);
    }
    Long searchedSpaceId = resolveSpaceId(spaceId, spaceName, "search_documents");
    if (StringUtils.isNotBlank(parentFolderId)) {
      filter.setParentFolderId(parentFolderId);
    } else if (searchedSpaceId != null) {
      Space space = spaceService.getSpaceById(String.valueOf(searchedSpaceId));
      if (space == null) {
        throw new ObjectNotFoundException(MSG_SPACE_NOT_FOUND.formatted(searchedSpaceId));
      } else if (!spaceService.canViewSpace(space, getCurrentUserName())) {
        throw new IllegalAccessException("Space with id %s isn't accessible to you. Use get_my_spaces to find your spaces.".formatted(searchedSpaceId));
      }
      Identity spaceIdentity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
      filter.setOwnerId(spaceIdentity.getIdentityId());
    }
    List<FileNode> files = documentFileService.search(filter,
                                                      getCurrentUserAclIdentity(),
                                                      getInteger(offset, DEFAULT_OFFSET),
                                                      getInteger(limit, DEFAULT_LIMIT));
    return files.stream().map(this::toDocumentFileModel).toList();
  }

  // ---------------------------------------------------------------------------
  // Folder navigation and metadata (reads)
  // ---------------------------------------------------------------------------

  /**
   * Lists the direct children of a folder, folders first, or with a date only
   * the children changed since then, the most recent first.
   * <p>
   * Without a date, the storage pages over the listing itself. A date is
   * checked against the same last-change date the listing shows, over the
   * folder's children read in one go, up to {@link #MAX_SCANNED_CHILDREN}: the
   * storage fills a page up to its limit whenever the folder holds more
   * children, however many rows it leaves out (a shortcut whose target is
   * gone), so a listing asked for one child more than the bound returns it
   * exactly when the folder holds more, and such a folder is refused rather
   * than answered with part of its changes.
   *
   * @param folderId the folder id
   * @param offset the first child
   * @param limit the most children
   * @param updatedSince an ISO-8601 date or date-time, blank for any date
   * @return the children
   * @throws ObjectNotFoundException when the folder doesn't exist
   * @throws IllegalAccessException when the user can't read the folder
   * @throws IllegalArgumentException when the folder id is blank, the date
   *           can't be read, or the folder holds too many children to filter
   */
  public List<DocumentModel> listFolderChildren(String folderId,
                                                Integer offset,
                                                Integer limit,
                                                String updatedSince) throws ObjectNotFoundException, IllegalAccessException {
    checkFolderIdParameter(folderId);
    Long updatedSinceTime = parseUpdatedSince(updatedSince);
    int first = Math.max(getInteger(offset, DEFAULT_OFFSET), 0);
    int size = Math.max(getInteger(limit, DEFAULT_LIMIT), 0);
    DocumentFolderFilter filter = new DocumentFolderFilter(folderId, null, null, null);
    if (size == 0) {
      // a negative limit; the storage reads every child when given none
      return List.of();
    } else if (updatedSinceTime == null) {
      return documentFileService.getFolderChildNodes(filter, first, size, getCurrentUserIdentityId())
                                .stream()
                                .map(this::toDocumentModel)
                                .toList();
    }
    List<AbstractNode> children = documentFileService.getFolderChildNodes(filter,
                                                                          0,
                                                                          MAX_SCANNED_CHILDREN + 1,
                                                                          getCurrentUserIdentityId());
    if (children.size() > MAX_SCANNED_CHILDREN) {
      throw new IllegalArgumentException(MSG_TOO_MANY_CHILDREN.formatted(MAX_SCANNED_CHILDREN));
    }
    return children.stream()
                   .filter(child -> child.getModifiedDate() >= updatedSinceTime)
                   .sorted(Comparator.comparingLong(AbstractNode::getModifiedDate).reversed())
                   .skip(first)
                   .limit(size)
                   .map(this::toDocumentModel)
                   .toList();
  }

  /**
   * Tells what the current user may do on a folder or a file: the edit right
   * as the repository checks it for the user; the delete right as the
   * storage computes it (the document's permission entries, and any document
   * of the user's own drive), plus the owner's, whom the repository grants
   * every right although that computation leaves the owner out. For a
   * shortcut, the rights are its target's, named in the answer, and the delete
   * right is left out: deleting the shortcut removes only the shortcut.
   *
   * @param documentId the folder or file id
   * @return the user's rights on it
   * @throws ObjectNotFoundException when the document doesn't exist
   * @throws IllegalAccessException when the user can't read the document
   */
  public DocumentPermissionsModel getDocumentPermissions(String documentId) throws ObjectNotFoundException,
                                                                            IllegalAccessException {
    checkDocumentIdParameter(documentId);
    AbstractNode document = documentFileService.getDocumentById(documentId, getCurrentUserName());
    if (document == null) {
      throw new ObjectNotFoundException("No document has the id %s.".formatted(documentId));
    }
    // a shortcut's id resolves to its target: the rights are the target's, but
    // deleting the shortcut removes only the shortcut, so its delete right isn't the target's
    boolean shortcut = !StringUtils.equals(documentId, document.getId());
    boolean canEdit = documentFileService.hasEditPermissionOnDocument(document.getId(), getCurrentUserIdentityId());
    NodePermission acl = document.getAcl();
    return new DocumentPermissionsModel(documentId,
                                        document.getName(),
                                        document.isFolder(),
                                        shortcut ? document.getId() : null,
                                        true,
                                        canEdit,
                                        shortcut ? null : isOwner(document) || (acl != null && acl.isCanDelete()));
  }

  public List<BreadcrumbItemModel> getFolderBreadcrumb(String folderId) throws ObjectNotFoundException,
                                                                        IllegalAccessException {
    checkFolderIdParameter(folderId);
    // Validate the folder exists and is readable by the user, so a bad id fails
    // with a clean ObjectNotFoundException / IllegalAccessException.
    getNode(folderId);
    // folderId already identifies the JCR node uniquely; the storage 'folderPath'
    // argument is a RELATIVE sub-path resolved *from* that node (getNodeByPath),
    // so it must stay empty. Passing the absolute node path makes the storage try
    // to navigate further down and fail with "Folder with path ... isn't found".
    List<BreadCrumbItem> breadcrumb = documentFileService.getBreadcrumb(getOwnerId(folderId),
                                                                        folderId,
                                                                        null,
                                                                        getCurrentUserIdentityId());
    return breadcrumb.stream()
                     .map(item -> new BreadcrumbItemModel(item.getId(), item.getName(), item.getPath()))
                     .toList();
  }

  public List<DocumentTreeItemModel> getFolderTree(String folderId,
                                                   Boolean withChildren) throws ObjectNotFoundException,
                                                                         IllegalAccessException {
    checkFolderIdParameter(folderId);
    List<FullTreeItem> tree = documentFileService.getFullTreeData(getOwnerId(folderId),
                                                                  folderId,
                                                                  null,
                                                                  getCurrentUserIdentityId(),
                                                                  withChildren == null || withChildren.booleanValue(),
                                                                  false);
    return tree.stream().map(this::toTreeItemModel).toList();
  }

  public List<DocumentVersionModel> listDocumentVersions(String documentId) throws IllegalAccessException,
                                                                            ObjectNotFoundException {
    checkCanAccessDocument(documentId);
    List<FileVersion> versions = documentFileService.getFileVersions(documentId, getCurrentUserName());
    return versions.stream().map(this::toVersionModel).toList();
  }

  public DocumentsSizeModel getDocumentsSize(Long ownerId) throws ObjectNotFoundException, IllegalAccessException {
    long resolvedOwnerId = ownerId == null || ownerId <= 0 ? getCurrentUserIdentityId() : ownerId;
    long userIdentityId = getCurrentUserIdentityId();
    DocumentsSize size = documentFileService.getDocumentsSizeStat(resolvedOwnerId, userIdentityId);
    // getDocumentsSizeStat only reads a previously computed analytics "documentsSize"
    // stat (the size gadget POSTs addDocumentsSizeStat on demand). When no stat has
    // been written yet, or it is stale (not from today), the read yields owner_id 0 /
    // size 0, which is misleading. Trigger the on-demand computation so the tool
    // returns the real current size for the correct owner.
    if (size == null || size.getOwnerId() <= 0 || size.getToSize() <= 0 || !size.isTodaySize()) {
      size = documentFileService.addDocumentsSizeStat(resolvedOwnerId, userIdentityId);
    }
    return new DocumentsSizeModel(resolvedOwnerId, size == null ? 0L : size.getToSize());
  }

  // ---------------------------------------------------------------------------
  // Content management (writes, require approval)
  // ---------------------------------------------------------------------------

  /**
   * Creates a folder under a parent folder, or a chain of nested folders when
   * the name is a path: each segment of {@code Finances/Factures/Entrantes}
   * but the last reuses the child folder of that name when there is one and
   * is created otherwise, and the last one is created. A whole tree then takes
   * one call per leaf, every call naming the same parent: none of them needs
   * the id of a folder created by another call of the same round, an id a
   * model calling them together does not have yet and makes up instead.
   *
   * @param parentFolderId the folder the name, or the path's first segment,
   *          is created under
   * @param name the folder name, or a path of folder names separated by
   *          {@code /}
   * @return the last folder of the path, the one created
   * @throws IllegalAccessException when the user cannot read the parent folder
   *           or add a folder to it
   * @throws ObjectNotFoundException when no folder has the parent folder id
   */
  public DocumentModel createFolder(String parentFolderId,
                                    String name) throws IllegalAccessException, ObjectNotFoundException {
    checkFolderIdParameter(parentFolderId);
    List<String> segments = toFolderPathSegments(name);
    // Validate the parent folder exists and is readable by the user, so a bad id
    // fails with a clean ObjectNotFoundException / IllegalAccessException before
    // reaching the JCR storage.
    try {
      getDestinationFolder(parentFolderId, PARENT_FOLDER_ID_ARG);
    } catch (ObjectNotFoundException e) {
      throw new ObjectNotFoundException(PARENT_FOLDER_NOT_FOUND_MESSAGE.formatted(parentFolderId));
    }
    long ownerId = getOwnerId(parentFolderId);
    String folderId = parentFolderId;
    for (String segment : segments.subList(0, segments.size() - 1)) {
      folderId = getOrCreateChildFolder(ownerId, folderId, segment);
    }
    String folderName = segments.get(segments.size() - 1);
    try {
      return toDocumentModel(createChildFolder(ownerId, folderId, folderName));
    } catch (ObjectAlreadyExistsException e) {
      throw new IllegalStateException("A folder named '%s' already exists under this parent folder. Tell the user to pick a different name."
          .formatted(folderName));
    }
  }

  /**
   * Creates a text-based document (markdown, HTML, plain text, CSV, JSON…) from
   * a chat-authored <code>content</code> string. This is the chat-native way to
   * turn generated text into a real file in the Documents app. The
   * <code>name</code> must include a file extension: the extension drives the
   * stored content type via the platform MimeTypeResolver, so use a registered
   * text extension (.txt, .html, .csv, .json, .xml…) so the file has the correct
   * type and is readable via get_document_content_by_id.
   *
   * @param parentFolderId the folder where the document is created (get it from
   *          get_root_folder_for_user / get_root_folder_by_space /
   *          get_documents_by_folder_id)
   * @param name the document file name, including its extension (e.g.
   *          'notes.md', 'report.txt', 'page.html'). The extension determines the
   *          file's content type (via the platform MimeTypeResolver); the name is
   *          used as-is.
   * @param content the text / markdown / HTML body
   * @return the created document
   */
  public DocumentModel createDocument(String parentFolderId,
                                      String name,
                                      String content) throws IllegalAccessException, ObjectNotFoundException {
    checkFolderIdParameter(parentFolderId);
    if (StringUtils.isBlank(name)) {
      throw new IllegalArgumentException("The 'name' parameter is mandatory to create a document (e.g. 'meeting-notes.md'). Ask the user for a document name.");
    }
    String extension = StringUtils.substringAfterLast(StringUtils.lowerCase(StringUtils.trimToEmpty(name)), ".");
    if (StringUtils.isBlank(extension)) {
      throw new IllegalArgumentException("The document name must include a file extension (e.g. 'notes.md', 'report.txt', 'page.html') — the extension determines the file's content type.");
    }
    if (UNSUPPORTED_BINARY_EXTENSIONS.contains(extension)) {
      throw new IllegalArgumentException(("create_document writes text content as the file body, so it cannot produce a valid"
          + " '%s' file — office and PDF formats are structured packages, not text. To create a NEW EMPTY office document"
          + " (Word/Excel/PowerPoint/ODF) use create_document_from_template. For text use .txt/.md/.html, or use"
          + " upload_document with the real file bytes (base64, url, or a file attached in the conversation).").formatted(extension));
    }
    if (StringUtils.isBlank(content)) {
      throw new IllegalArgumentException("content is required to create a document (the text to write into the file).");
    }
    return fileContent(parentFolderId, name, content.getBytes(StandardCharsets.UTF_8), UploadConflictAction.KEEP_BOTH);
  }

  /**
   * Creates a NEW EMPTY office document (Word/Excel/PowerPoint, or ODF) from the
   * platform's blank template, by delegating to the ecms {@code DocumentService}.
   * Use this when the user wants a blank Word/Excel/PowerPoint document to fill in
   * (e.g. via the online editor): create_document cannot produce office files
   * (they are structured packages, not text) and upload_document needs the real
   * file bytes. The template is created synchronously; the file extension is added
   * automatically from <code>documentType</code>.
   *
   * @param parentFolderId the folder where the document is created (get it from
   *          get_root_folder_for_user / get_root_folder_by_space /
   *          get_documents_by_folder_id)
   * @param name the document name, WITHOUT extension (e.g. 'Q3 report'); the
   *          extension is appended automatically from documentType
   * @param documentType the office document type: one of docx, xlsx, pptx.
   *          Availability depends on the installed editor add-on (onlyoffice).
   * @return the created document
   */
  public DocumentModel createDocumentFromTemplate(String parentFolderId,
                                                  String name,
                                                  String documentType) throws IllegalAccessException, ObjectNotFoundException {
    checkFolderIdParameter(parentFolderId);
    if (StringUtils.isBlank(name)) {
      throw new IllegalArgumentException("The 'name' parameter is mandatory to create a document (e.g. 'Q3 report'). Ask the user for a document name.");
    }
    if (StringUtils.isBlank(documentType)) {
      throw new IllegalArgumentException("The 'document_type' parameter is mandatory: the office document type to create. Supported types: "
          + SUPPORTED_TEMPLATE_TYPES + ".");
    }
    String type = StringUtils.lowerCase(StringUtils.trimToEmpty(documentType));
    if (type.startsWith(".")) {
      type = type.substring(1);
    }
    if (!SUPPORTED_TEMPLATE_TYPES.contains(type)) {
      throw new IllegalArgumentException("Unsupported document_type '%s'. Supported office document types are: %s.".formatted(documentType,
                                                                                                                              SUPPORTED_TEMPLATE_TYPES));
    }
    // Validate the parent folder exists and is readable by the user, so a bad id
    // fails with a clean ObjectNotFoundException / IllegalAccessException before
    // reaching the JCR storage.
    getDestinationFolder(parentFolderId, PARENT_FOLDER_ID_ARG);
    // ecms names the file after the title and does NOT append the extension, so
    // build a title that ends with '.<type>' (stripping a duplicate extension the
    // user may have typed to avoid 'report.docx.docx').
    String baseName = StringUtils.trimToEmpty(name);
    String suffix = "." + type;
    if (StringUtils.endsWithIgnoreCase(baseName, suffix)) {
      baseName = baseName.substring(0, baseName.length() - suffix.length());
    }
    String title = baseName + suffix;
    try {
      // parentFolderId already identifies the parent JCR node uniquely; the storage
      // 'folderPath' is a RELATIVE sub-path resolved from that node, so keep it null
      // (same rule as createFolder).
      AbstractNode document = documentFileService.createDocumentFromTemplate(getOwnerId(parentFolderId),
                                                                            parentFolderId,
                                                                            null,
                                                                            title,
                                                                            type,
                                                                            getCurrentUserIdentityId());
      return toDocumentModel(document);
    } catch (ObjectAlreadyExistsException e) {
      throw new IllegalStateException("A document named '%s' already exists under this parent folder. Tell the user to pick a different name."
          .formatted(title));
    }
  }

  /**
   * Uploads a real / binary document (PDF, image, office file…) from exactly one
   * of four sources: a file/image the user attached in the chat conversation
   * (resolved server-side as the current user via
   * <code>attachment_object_type</code> / <code>attachment_object_id</code>), a
   * file the user sent through an upload link (<code>upload_id</code>, filed
   * from its staged file, never read into memory), a base64 payload, or an
   * http(s) URL (fetched server-side behind an SSRF guard).
   *
   * @param parentFolderId the destination folder id (get it from
   *          get_root_folder_for_user / get_root_folder_by_space /
   *          get_documents_by_folder_id)
   * @param name the document file name including its extension (e.g.
   *          'report.pdf'); when uploading from a URL or a chat attachment it may
   *          be left blank to reuse the source file name
   * @param base64 the file bytes encoded in base64, at most
   *          {@code CHUNK_CHARS} (20000) characters, a larger file being sent in
   *          pieces (mutually exclusive with url / the chat attachment)
   * @param url an http(s) URL to download the file from, read up to
   *          {@link DocumentUploadLimit#URL_FETCH_MAX_BYTES}, or the upload size
   *          limit when it is lower (mutually exclusive with base64 / the chat
   *          attachment)
   * @param attachmentObjectType the object type of the file the user attached in
   *          chat; set automatically by the client — do not fill it from the
   *          model
   * @param attachmentObjectId the object id of the file the user attached in
   *          chat; set automatically by the client — do not fill it from the
   *          model
   * @param onConflict what to do when the folder holds a file of that name:
   *          keep_both (the default) or new_version
   * @param uploadId the id start_upload returned, once its curl command sent
   *          the file: one of the user's own uploads, consumed by this call
   *          once read, whether it succeeds or fails (mutually exclusive with
   *          the other sources)
   * @return the created document
   * @throws IllegalArgumentException when a parameter is missing or wrong (the
   *           folder id, the source, the name, the conflict action), the file
   *           is empty or over the upload size limit, the upload id names no
   *           upload of the user's, or a new version is asked of a folder, a
   *           shortcut or a file that keeps no versions
   * @throws IllegalStateException when the file can't be read from its source
   *           or filed, or the upload is still being sent or failed
   * @throws ObjectNotFoundException when the folder or the attachment doesn't
   *           exist
   * @throws IllegalAccessException when the user can't read the attachment,
   *           or read the folder or add documents to it
   */
  public DocumentModel uploadDocument(String parentFolderId,
                                      String name,
                                      String base64,
                                      String url,
                                      String attachmentObjectType,
                                      String attachmentObjectId,
                                      String onConflict,
                                      String uploadId) throws IllegalAccessException, ObjectNotFoundException {
    checkFolderIdParameter(parentFolderId);
    UploadConflictAction conflictAction = toConflictAction(onConflict);
    boolean hasAttachment = StringUtils.isNotBlank(attachmentObjectId);
    boolean hasBase64 = StringUtils.isNotBlank(base64);
    boolean hasUrl = StringUtils.isNotBlank(url);
    boolean hasUploadId = StringUtils.isNotBlank(uploadId);
    int sources = (hasAttachment ? 1 : 0) + (hasBase64 ? 1 : 0) + (hasUrl ? 1 : 0) + (hasUploadId ? 1 : 0);
    if (sources == 0) {
      throw new IllegalArgumentException("""
          Provide a file to upload from exactly one source:
          a file/image the user attached in the conversation, an 'upload_id' from start_upload, a 'base64' payload, or an http(s) 'url'.
          """);
    }
    if (sources > 1) {
      throw new IllegalArgumentException("Provide the file to upload from only one source: the chat attachment, 'upload_id', 'base64' or 'url' — not several.");
    }
    if (hasUploadId) {
      return fileStagedUpload(parentFolderId, uploadId.trim(), name, conflictAction);
    }
    if (hasBase64) {
      checkBase64Length(base64, MSG_UPLOAD_BASE64_TOO_LONG);
    }
    byte[] bytes;
    String fileName = name;
    try {
      if (hasAttachment) {
        UploadToolUtils.FetchedContent fetched = readChatAttachment(attachmentObjectType,
                                                                    attachmentObjectId,
                                                                    DocumentUploadLimit.getMaxBytes());
        bytes = fetched.bytes();
        if (StringUtils.isBlank(fileName)) {
          fileName = fetched.fileName();
        }
      } else if (hasBase64) {
        if (StringUtils.isBlank(name)) {
          throw new IllegalArgumentException("The 'name' parameter is mandatory when uploading from base64; it becomes the document file name (e.g. 'report.pdf').");
        }
        bytes = UploadToolUtils.decodeBase64(base64);
      } else {
        UploadToolUtils.FetchedContent fetched = fetchUrl(url, name);
        bytes = fetched.bytes();
        if (StringUtils.isBlank(fileName)) {
          fileName = fetched.fileName();
        }
      }
    } catch (IllegalArgumentException | IllegalStateException e) {
      // Already an LLM-directed message (bad source, HTTP error, SSRF block,
      // size cap, invalid base64…): the MCP wrapper passes these through verbatim.
      throw e;
    } catch (RuntimeException e) {
      // Any other unexpected failure of the resolve/fetch path: rethrow as an
      // LLM-directed type so it isn't swallowed by the generic wrapper message.
      throw new IllegalStateException("Could not read the file to upload from the given source: " + e.getMessage());
    }
    return fileContent(parentFolderId, fileName, bytes, conflictAction);
  }

  /**
   * Starts sending a file in pieces, for a file too large to be sent whole in
   * one tool call: checks the folder, the user's right to add a document to
   * it, the file's size and that its name can take the conflict action,
   * before any piece is sent. The piece that completes the size files the
   * document.
   *
   * @param parentFolderId the folder the file is filed into
   * @param name the file name, with its extension
   * @param size the file's size, in bytes
   * @param onConflict what to do when the folder holds a file of that name:
   *          keep_both (the default) or new_version
   * @return the upload, with how to send its pieces
   * @throws IllegalArgumentException when a parameter is missing or wrong (the
   *           folder id, the name, the size, the conflict action), the size is
   *           over the upload size limit, or a new version is asked of a folder, a shortcut or a file that
   *           keeps no versions
   * @throws IllegalStateException when the user has too many uploads in
   *           progress, the folder can't be read to check the name, or the
   *           upload can't be stored on this server
   * @throws ObjectNotFoundException when the folder doesn't exist
   * @throws IllegalAccessException when the user can't read the folder or add
   *           documents to it
   */
  public DocumentUploadModel startDocumentUpload(String parentFolderId,
                                                 String name,
                                                 Long size,
                                                 String onConflict) throws IllegalAccessException, ObjectNotFoundException {
    checkFolderIdParameter(parentFolderId);
    if (StringUtils.isBlank(name)) {
      throw new IllegalArgumentException("The 'name' parameter is mandatory: it becomes the document file name (e.g. 'grid.xlsx').");
    }
    if (size == null) {
      throw new IllegalArgumentException("The 'size' parameter is mandatory: the file's size in bytes, before base64 encoding.");
    }
    UploadConflictAction conflictAction = toConflictAction(onConflict);
    checkUploadFolder(parentFolderId);
    String fileName = sanitizeFileName(name);
    // the name is checked again when the file is filed: the folder may change in between
    documentFileService.checkUploadConflict(parentFolderId, fileName, conflictAction, getCurrentUserIdentityId());
    return toUploadModel(chunkedUploads.start(getCurrentUserName(), parentFolderId, fileName, size, conflictAction));
  }

  /**
   * Adds the next piece of a file sent in pieces; the piece that completes
   * the file files it, and returns the document. A piece sent again after a
   * lost answer is acknowledged without being added twice, and the last one
   * returns the document it was filed as.
   *
   * @param uploadId the upload
   * @param index the piece's number, from 0
   * @param base64 the piece, base64-encoded
   * @return the upload, with the next piece expected, or with its document
   *         once filed
   * @throws IllegalStateException when the last piece is sent again while
   *           its file is being filed, which leaves the upload open, to be sent
   *           again; or when the piece can't be stored, or the file can't be
   *           filed, its folder gone included, which ends the upload
   * @throws IllegalArgumentException when the piece is refused, which leaves
   *           the upload open, or when the file is refused at its filing, a
   *           new version asked of a folder, a shortcut or a file that keeps no
   *           versions, which
   *           ends the upload
   * @throws IllegalAccessException when the user can no longer add documents
   *           to the folder, which ends the upload
   */
  public DocumentUploadModel appendDocumentUploadChunk(String uploadId,
                                                       Integer index,
                                                       String base64) throws IllegalAccessException {
    if (index == null || StringUtils.isBlank(base64)) {
      throw new IllegalArgumentException("Pass the piece's 'index' (from 0) and its 'base64' content.");
    }
    if (base64.length() > MAX_CHUNK_CHARS) {
      throw new IllegalArgumentException("A piece is at most %d base64 characters; send this one in smaller pieces.".formatted(MAX_CHUNK_CHARS));
    }
    ChunkedUpload upload = chunkedUploads.append(getCurrentUserName(), uploadId, index, decodePiece(index, base64));
    if (upload.filing() && upload.document() == null) {
      DocumentModel document = fileChunkedUpload(upload);
      upload = upload.filedAs(document, upload.touched());
    }
    return toUploadModel(upload);
  }

  /**
   * Decodes a piece as upload_document decodes base64, and refuses a piece
   * cut inside a 4-character group: decoded alone, it would give wrong bytes.
   * Such a piece decodes, unpadded, to a number of bytes that is not a
   * multiple of 3, which only a padded last piece does.
   *
   * @param index the piece's number
   * @param base64 the piece
   * @return its bytes
   */
  private static byte[] decodePiece(int index, String base64) {
    byte[] bytes;
    try {
      bytes = UploadToolUtils.decodeBase64(base64);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("The piece %d is not valid base64.".formatted(index));
    }
    if (bytes.length % 3 != 0 && !base64.strip().endsWith("=")) {
      throw new IllegalArgumentException(("The piece %d was cut inside a group of 4 base64 characters: cut the file's padded "
          + "base64 into pieces whose lengths are multiples of 4, and send this one again.").formatted(index));
    }
    return bytes;
  }

  /**
   * Files an upload in pieces whose last piece came: its temporary file is
   * handed to the platform's upload service, which the filing consumes. The
   * upload keeps the document to answer its last piece sent again, or ends
   * when the file can't be filed.
   *
   * @param upload the upload
   * @return the document it was filed as
   * @throws IllegalStateException when the file can't be filed, its folder
   *           gone included
   * @throws IllegalArgumentException when the file is refused at its
   *           filing, a new version asked of a folder, a shortcut or a file that keeps no
   *           versions
   * @throws IllegalAccessException when the user can no longer add documents
   *           to the folder
   */
  private DocumentModel fileChunkedUpload(ChunkedUpload upload) throws IllegalAccessException {
    DocumentModel document = null;
    String uploadResourceId = UUID.randomUUID().toString();
    try {
      UploadResource resource = new UploadResource(uploadResourceId);
      resource.setFileName(upload.name());
      resource.setMimeType(DEFAULT_UPLOAD_MIME_TYPE);
      resource.setStoreLocation(upload.file().toString());
      resource.setEstimatedSize(upload.size());
      resource.setStatus(UploadResource.UPLOADED_STATUS);
      uploadService.createUploadResource(resource);
      document = fileUpload(upload.folderId(), uploadResourceId, upload.name(), upload.conflictAction());
      return document;
    } catch (IllegalStateException e) {
      throw new IllegalStateException(e.getMessage() + MSG_UPLOAD_ENDED);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(e.getMessage() + MSG_UPLOAD_ENDED);
    } catch (IllegalAccessException e) {
      throw new IllegalAccessException(e.getMessage() + MSG_UPLOAD_ENDED);
    } finally {
      UploadToolUtils.release(uploadService, uploadResourceId);
      if (document == null) {
        chunkedUploads.failed(upload.id());
      } else {
        chunkedUploads.filed(upload.id(), document);
      }
    }
  }

  /**
   * Maps an upload to the model the tools return.
   *
   * @param upload the upload
   * @return the model
   */
  private DocumentUploadModel toUploadModel(ChunkedUpload upload) {
    return new DocumentUploadModel(upload.id(),
                                   upload.folderId(),
                                   upload.name(),
                                   upload.size(),
                                   upload.received(),
                                   upload.nextIndex(),
                                   DocumentUploadLimit.getMaxBytes(),
                                   CHUNK_CHARS,
                                   UPLOAD_TIME_TO_LIVE.toMinutes(),
                                   upload.document());
  }

  /**
   * The uploads in pieces in progress on this node, for tests.
   *
   * @return the store
   */
  ChunkedUploadStore getChunkedUploads() {
    return chunkedUploads;
  }

  /**
   * Renames a file or a folder.
   *
   * @param documentId the file or folder
   * @param newName its new name
   * @throws IllegalAccessException when the user can't rename the node, or, in
   *           a call limited to a space, the node was created by the platform
   *           for the space or the call's space can't be read
   * @throws ObjectNotFoundException when the node doesn't exist
   */
  public void renameDocument(String documentId, String newName) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    if (StringUtils.isBlank(newName)) {
      throw new IllegalArgumentException("The 'newName' parameter is mandatory to rename a document.");
    }
    checkScopedChange(documentId, false);
    try {
      documentFileService.renameDocument(getOwnerId(documentId), documentId, newName, getCurrentUserIdentityId());
    } catch (ObjectAlreadyExistsException e) {
      throw new IllegalStateException("A document named '%s' already exists in the same folder. Tell the user to pick a different name."
          .formatted(newName));
    }
  }

  /**
   * Moves a file or a folder into another folder; in a call limited to a
   * space, the destination folder must be in that space.
   *
   * @param documentId the file or folder to move
   * @param destinationFolderId the destination folder
   * @param conflictAction what to do when the destination holds a node of the
   *          same name, {@code keepBoth} by default
   * @throws IllegalAccessException when the user can't move the node there,
   *           or, in a call limited to a space, the destination folder isn't in
   *           that space, the node was created by the platform for the space,
   *           or the call's space can't be read
   * @throws ObjectNotFoundException when a node doesn't exist
   */
  public void moveDocument(String documentId,
                           String destinationFolderId,
                           String conflictAction) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    if (StringUtils.isBlank(destinationFolderId)) {
      throw new IllegalArgumentException("The 'destinationFolderId' parameter is mandatory. Call get_document_by_id or get_documents_by_folder_id to find the target folder id.");
    }
    AbstractNode destination = getDestinationFolder(destinationFolderId, DESTINATION_FOLDER_ID_ARG);
    checkDestinationInScopeSpace(destinationFolderId);
    checkScopedChange(documentId, false);
    try {
      documentFileService.moveDocument(getOwnerId(documentId),
                                       documentId,
                                       destination.getPath(),
                                       getCurrentUserIdentityId(),
                                       StringUtils.isBlank(conflictAction) ? "keepBoth" : conflictAction);
    } catch (ObjectAlreadyExistsException e) {
      throw new IllegalStateException("A document with the same name already exists in the destination folder. Retry with conflict_action set to 'keepBoth' (move as a renamed copy) or 'createNewVersion' (replace the existing file's content, keeping its history).");
    }
  }

  /**
   * Copies a file into another folder; in a call limited to a space, the
   * destination folder must be in that space.
   *
   * @param documentId the file to copy
   * @param destinationFolderId the destination folder
   * @return the copy
   * @throws IllegalAccessException when the user can't copy the node there,
   *           or, in a call limited to a space, the destination folder isn't in
   *           that space, the node was created by the platform for the space,
   *           or the call's space can't be read
   * @throws ObjectNotFoundException when a node doesn't exist
   */
  public DocumentModel copyDocument(String documentId,
                                    String destinationFolderId) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    if (StringUtils.isBlank(destinationFolderId)) {
      throw new IllegalArgumentException("The 'destinationFolderId' parameter is mandatory. Call get_document_by_id or get_documents_by_folder_id to find the target folder id.");
    }
    getDestinationFolder(destinationFolderId, DESTINATION_FOLDER_ID_ARG);
    checkDestinationInScopeSpace(destinationFolderId);
    checkScopedChange(documentId, false);
    // The storage copyDocument returns the DESTINATION folder node, not the copy,
    // so snapshot the destination's children of the source's kind first and
    // resolve the newly added one by NAME afterwards to return the actual copied
    // document or folder. The copy keeps the source's name, or gets a " (n)"
    // counter suffix when the destination already holds that name, so it is
    // matched name-aware: this stays correct even if another node lands in the
    // destination concurrently (a blind folder-diff would otherwise return the
    // wrong document).
    AbstractNode source = getNode(documentId);
    String expectedName = source.getName();
    Class<? extends AbstractNode> kind = kindOf(source);
    Set<String> beforeIds = currentChildIds(destinationFolderId, kind);
    documentFileService.copyDocument(documentId, destinationFolderId, getCurrentUserIdentityId());
    return requireResolvedChild(destinationFolderId, beforeIds, expectedName, kind, "copied");
  }

  /**
   * Duplicates a file or a folder in its own folder, with an optional prefix
   * and a " (n)" counter, and returns the duplicate.
   *
   * @param documentId the file or folder to duplicate
   * @param prefix an optional prefix of the duplicate's name
   * @return the duplicate, a file or a folder like the source
   * @throws IllegalAccessException when the user can't read or duplicate the
   *           node, or, in a call limited to a space, the node was created by
   *           the platform for the space or the call's space can't be read
   * @throws ObjectNotFoundException when the node doesn't exist
   */
  public DocumentModel duplicateDocument(String documentId,
                                         String prefix) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    checkScopedChange(documentId, false);
    // The storage duplicateDocument returns the PARENT folder node, not the
    // duplicate, so snapshot the parent's children of the source's kind first and
    // resolve the newly added node by NAME afterwards to return the actual
    // duplicate. The duplicate
    // is named "[<prefix> ]<source name>" with a " (n)" counter appended on a name
    // clash (there is always at least one, since the source itself is present), so
    // it is matched name-aware to stay correct under a concurrent add.
    AbstractNode source = getNode(documentId);
    String parentFolderId = source.getParentFolderId();
    String expectedName = StringUtils.isBlank(prefix) ? source.getName()
                                                      : StringUtils.trimToEmpty(prefix) + " " + source.getName();
    if (StringUtils.isBlank(parentFolderId)) {
      // No resolvable parent (e.g. a root node): the folder-diff resolution is not
      // possible, so fall back to the storage return.
      AbstractNode duplicateReturn = documentFileService.duplicateDocument(getOwnerId(documentId),
                                                                           documentId,
                                                                           prefix,
                                                                           getCurrentUserIdentityId());
      return toDocumentModel(duplicateReturn);
    }
    Class<? extends AbstractNode> kind = kindOf(source);
    Set<String> beforeIds = currentChildIds(parentFolderId, kind);
    documentFileService.duplicateDocument(getOwnerId(documentId), documentId, prefix, getCurrentUserIdentityId());
    return requireResolvedChild(parentFolderId, beforeIds, expectedName, kind, "duplicated");
  }

  public void deleteDocument(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode document = checkCanAccessDocument(documentId);
    // delay = 0: move to trash immediately. favorite = true: also drop the file
    // from the user's favorites (storage cleanup uses object type "file"), so a
    // trashed favorite doesn't linger in the favorites view.
    documentFileService.deleteDocument(document.getPath(), documentId, true, 0, getCurrentUserIdentityId());
  }

  /**
   * Restores a document that was deleted with {@code delete_document}: since that
   * tool trashes immediately (delay 0), the delayed-delete cancel path is a no-op,
   * so the node is resolved from trash (it keeps its id/uuid after the move) and
   * restored to its original location.
   */
  public void undoDeleteDocument(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    // Resolve the trashed node through the SYSTEM-session overload: all trash
    // access in storage uses a system session, and a regular user session cannot
    // read a node once it has been moved to trash. Using the user-session overload
    // here would return null even though the node still exists, yielding a
    // misleading "permanently deleted" error.
    AbstractNode trashedNode = documentFileService.getDocumentById(documentId);
    if (trashedNode == null) {
      throw new IllegalStateException(("Document %s can no longer be found (it may have been permanently deleted from the trash), "
          + "so it cannot be restored.").formatted(documentId));
    }
    try {
      documentFileService.restoreDocumentFromTrash(trashedNode.getPath());
    } catch (RepositoryException e) {
      throw new IllegalStateException(("Could not restore document %s from trash: %s. It may not be in the trash "
          + "(only documents deleted via delete_document can be restored this way).").formatted(documentId, e.getMessage()));
    }
  }

  public DocumentVersionModel restoreDocumentVersion(String versionId) {
    if (StringUtils.isBlank(versionId)) {
      throw new IllegalArgumentException("The 'versionId' parameter is mandatory. Call list_document_versions first to get a version_id.");
    }
    FileVersion version = documentFileService.restoreVersion(versionId, getCurrentUserName());
    return toVersionModel(version);
  }

  public DocumentVersionModel updateVersionSummary(String documentId, String versionId, String summary) {
    checkDocumentIdParameter(documentId);
    if (StringUtils.isBlank(versionId)) {
      throw new IllegalArgumentException("The 'versionId' parameter is mandatory. Call list_document_versions first to get a version_id.");
    }
    FileVersion stamped = documentFileService.updateVersionSummary(documentId, versionId, summary, getCurrentUserName());
    // The storage updateVersionSummary returns a near-empty FileVersion (only id +
    // summary), so re-read the full version to return number/author/dates/size/etc.
    FileVersion full = documentFileService.getFileVersions(documentId, getCurrentUserName())
                                          .stream()
                                          .filter(v -> StringUtils.equals(v.getId(), versionId))
                                          .findFirst()
                                          .orElse(stamped);
    return toVersionModel(full);
  }

  /**
   * Hides a file or a folder from the listings, or shows it again; hiding a
   * folder may also hide some of its direct children. In a call limited to a
   * space, only a folder the platform didn't create for the space.
   *
   * @param documentId the file or folder
   * @param hidden true to hide it, false to show it
   * @throws IllegalAccessException in a call limited to a space, when the node
   *           is a file or was created by the platform, or the call's space
   *           can't be read
   * @throws IllegalStateException when the storage refuses the change, the
   *           user's refused access included, which it reports wrapped
   */
  @SneakyThrows
  public void setDocumentVisibility(String documentId, Boolean hidden) throws IllegalAccessException {
    checkDocumentIdParameter(documentId);
    if (hidden == null) {
      throw new IllegalArgumentException("The 'hidden' parameter is mandatory (true to hide the document, false to make it visible).");
    }
    checkScopedChange(documentId, true);
    documentFileService.setDocumentVisibility(getOwnerId(documentId), documentId, hidden, getCurrentUserIdentityId());
  }

  // ---------------------------------------------------------------------------
  // Versioning, sharing, public links and favorites (writes, require approval)
  // ---------------------------------------------------------------------------

  /**
   * Uploads updated content onto an EXISTING file, creating a NEW version of it
   * (the previous content stays retrievable via list_document_versions /
   * restore_document_version). The new bytes come from exactly one source: a
   * chat-authored text <code>content</code> string, a <code>base64</code>
   * payload, an http(s) <code>url</code>, a file the user attached in the
   * conversation, or a file the user sent through an upload link
   * (<code>upload_id</code>, streamed from its staged file, never read into
   * memory). The file's name and type are unchanged; only its content and
   * version history are updated.
   *
   * @param documentId the id of the existing file to add a version to (get it
   *          from get_document_by_id / get_documents_by_folder_id /
   *          search_documents)
   * @param content plain text / markdown / HTML body to write as the new version
   *          (use for text files)
   * @param base64 the new file bytes encoded in base64, at most
   *          {@code CHUNK_CHARS} (20000) characters
   * @param url an http(s) URL to download the new content from (fetched
   *          server-side behind an SSRF guard, up to
   *          {@link DocumentUploadLimit#URL_FETCH_MAX_BYTES}, or the upload
   *          size limit when it is lower)
   * @param attachmentObjectType the object type of the file the user attached in
   *          chat; set automatically by the client — do not fill it from the model
   * @param attachmentObjectId the object id of the file the user attached in
   *          chat; set automatically by the client — do not fill it from the model
   * @param versionSummary an optional change note stored on the new version
   * @param uploadId the id start_upload returned, once its curl command sent
   *          the file: one of the user's own uploads, consumed by this call
   *          once read, whether it succeeds or fails; refused in a call
   *          limited to a space
   * @return the updated document (with its refreshed size / modification info)
   * @throws IllegalArgumentException when not exactly one source is given,
   *           the document is a folder, the content is over the upload size
   *           limit or refused by a validator, or the upload id names no
   *           upload of the user's
   * @throws IllegalStateException when the content can't be read from its
   *           source, or the upload is still being sent or failed
   * @throws IllegalAccessException when the user can't edit the document, or
   *           an upload id is given in a call limited to a space
   * @throws ObjectNotFoundException when the document doesn't exist
   */
  public DocumentModel addDocumentVersion(String documentId,
                                          String content,
                                          String base64,
                                          String url,
                                          String attachmentObjectType,
                                          String attachmentObjectId,
                                          String versionSummary,
                                          String uploadId) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode node = checkCanAccessDocument(documentId);
    if (!(node instanceof FileNode)) {
      throw new IllegalArgumentException("Only a file can have versions; the id %s points to a folder. Provide a file document_id."
          .formatted(documentId));
    }
    // a shortcut's node is the file it targets, which is the one versioned
    String fileId = node.getId();
    if (!documentFileService.hasEditPermissionOnDocument(fileId, getCurrentUserIdentityId())) {
      throw new IllegalAccessException("You are not allowed to edit the document %s, so you cannot add a new version to it."
          .formatted(documentId));
    }
    checkOneVersionSource(content, base64, url, attachmentObjectId, uploadId);
    if (StringUtils.isNotBlank(uploadId)) {
      addVersionFromStagedUpload((FileNode) node, fileId, uploadId.trim());
    } else {
      byte[] bytes = resolveContentBytes(content, base64, url, attachmentObjectType, attachmentObjectId);
      // checked as a new file uploaded from a browser is, before it replaces the file's content
      documentFileService.validateUploadedContent(node.getName(), ((FileNode) node).getMimeType(), bytes);
      documentFileService.createNewVersion(fileId, getCurrentUserName(), new ByteArrayInputStream(bytes));
    }
    if (StringUtils.isNotBlank(versionSummary)) {
      applyVersionSummary(fileId, versionSummary);
    }
    return getDocumentById(fileId);
  }

  /**
   * Creates (or refreshes) an anonymous public share link for a document so it
   * can be downloaded without signing in, optionally protected by a password
   * and/or an expiration date. Requires edit permission on the document.
   *
   * @param documentId the id of the file to share publicly (get it from the read
   *          tools)
   * @param password an optional password required to open the link
   * @param expirationDate an optional expiration date, as epoch milliseconds;
   *          after it the link stops working. Leave blank for a link that never
   *          expires.
   * @return the shareable link, together with its password and expiration
   */
  public DocumentPublicLinkModel createPublicLink(String documentId,
                                                  String password,
                                                  Long expirationDate) throws IllegalAccessException,
                                                                       ObjectNotFoundException {
    checkCanAccessDocument(documentId);
    long userIdentityId = getCurrentUserIdentityId();
    if (!documentFileService.hasEditPermissionOnDocument(documentId, userIdentityId)) {
      throw new IllegalAccessException("You are not allowed to edit the document %s, so you cannot create a public link for it."
          .formatted(documentId));
    }
    if (StringUtils.isNotBlank(password)) {
      // Apply the same password policy as DocumentFileRest (min 9 chars, etc.), so
      // a link the LLM creates can actually be opened with the given password.
      String error = PASSWORD_VALIDATOR.validate(getCurrentUserLocale(), password);
      if (StringUtils.isNotBlank(error)) {
        throw new IllegalArgumentException("The public link password is invalid: " + error);
      }
    }
    long expiration = expirationDate == null ? 0L : expirationDate.longValue();
    PublicDocumentAccess access = publicDocumentAccessService.createPublicDocumentAccess(userIdentityId,
                                                                                        documentId,
                                                                                        StringUtils.isBlank(password) ? null
                                                                                                                      : password,
                                                                                        expiration,
                                                                                        false);
    if (access == null) {
      throw new IllegalStateException("The public link could not be created for document %s. Verify the document id and retry."
          .formatted(documentId));
    }
    Date expirationValue = access.getExpirationDate();
    return new DocumentPublicLinkModel(documentId,
                                       PUBLIC_LINK_URL_FORMAT.formatted(documentId),
                                       access.getDecodedPassword(),
                                       expirationValue == null ? null : formatDate(expirationValue));
  }

  /**
   * Creates a shortcut (symlink) to a document inside another folder, so the same
   * file appears in a second location without being copied.
   *
   * @param documentId the id of the file or folder to create a shortcut to
   * @param destinationFolderId the id of the folder where the shortcut is placed
   *          (get it from get_root_folder_for_user / get_root_folder_by_space /
   *          get_documents_by_folder_id)
   * @param conflictAction behaviour when a node with the same name already exists
   *          in the destination; only 'keepBoth' is honoured, defaults to
   *          'keepBoth'
   * @throws IllegalAccessException when the user can't read the nodes, or, in
   *           a call limited to a space, the destination folder isn't in that
   *           space, the node was created by the platform for the space, or the
   *           call's space can't be read
   * @throws ObjectNotFoundException when a node doesn't exist
   */
  public void createDocumentShortcut(String documentId,
                                     String destinationFolderId,
                                     String conflictAction) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    if (StringUtils.isBlank(destinationFolderId)) {
      throw new IllegalArgumentException("The 'destinationFolderId' parameter is mandatory. Call get_documents_by_folder_id or get_root_folder_for_user to find the target folder id.");
    }
    AbstractNode destination = getDestinationFolder(destinationFolderId, DESTINATION_FOLDER_ID_ARG);
    checkDestinationInScopeSpace(destinationFolderId);
    checkScopedChange(documentId, false);
    // Validate the source exists and is readable by the user before touching
    // the JCR storage.
    checkCanAccessDocument(documentId);
    try {
      documentFileService.createShortcut(documentId,
                                         destination.getPath(),
                                         getCurrentUserName(),
                                         StringUtils.isBlank(conflictAction) ? "keepBoth" : conflictAction);
    } catch (ObjectAlreadyExistsException e) {
      throw new IllegalStateException("A node with the same name already exists in the destination folder. Retry with conflict_action set to 'keepBoth' to create the shortcut under a renamed entry.");
    }
  }

  /**
   * Shares a document with another user or with a space: the recipient is GRANTED
   * read access to the file and gets it in their "Shared" folder. Provide exactly
   * one of <code>username</code>, <code>spaceId</code> or <code>spaceName</code>.
   * Requires edit permission on the document.
   *
   * @param documentId the id of the file to share (get it from the read tools)
   * @param username the username to share the document with (mutually exclusive
   *          with spaceId and spaceName)
   * @param spaceId the id of the space to share the document with (mutually
   *          exclusive with username and spaceName)
   * @param spaceName the name of the space to share the document with, one of
   *          the spaces the user is a member of (mutually exclusive with
   *          username and spaceId)
   * @param notify whether to notify the recipient; defaults to true
   * @throws IllegalAccessException when the user can't edit the document
   * @throws ObjectNotFoundException when the document, the user or the space
   *           doesn't exist, or no space of the user carries the name
   * @throws IllegalArgumentException when not exactly one recipient is given,
   *           or several of the user's spaces carry the name
   */
  public void shareDocument(String documentId,
                            String username,
                            Long spaceId,
                            String spaceName,
                            Boolean notify) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode node = checkCanAccessDocument(documentId);
    boolean hasUser = StringUtils.isNotBlank(username);
    boolean hasSpace = spaceId != null && spaceId > 0 || StringUtils.isNotBlank(spaceName);
    if (hasUser == hasSpace) {
      throw new IllegalArgumentException("Provide exactly one recipient: either 'username', 'space_id' or 'space_name', not several and not none.");
    }
    if (!documentFileService.hasEditPermissionOnDocument(documentId, getCurrentUserIdentityId())) {
      throw new IllegalAccessException("You are not allowed to edit the document %s, so you cannot share it.".formatted(documentId));
    }
    Identity destIdentity;
    if (hasUser) {
      destIdentity = identityManager.getOrCreateUserIdentity(username);
      if (destIdentity == null) {
        throw new ObjectNotFoundException("No user found with username '%s'. Use search_users to find the exact username."
            .formatted(username));
      }
    } else {
      Long recipientSpaceId = resolveSpaceId(spaceId, spaceName, "share_document");
      Space space = spaceService.getSpaceById(String.valueOf(recipientSpaceId));
      if (space == null) {
        throw new ObjectNotFoundException(MSG_SPACE_NOT_FOUND.formatted(recipientSpaceId));
      }
      destIdentity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
    }
    long destId = destIdentity.getIdentityId();
    // Route through updatePermissions like the REST does: shareDocument alone only
    // copies the recipient's EXISTING permissions onto the symlink, so a recipient
    // without prior access still can't open the file. updatePermissions GRANTS the
    // recipient read access on the node (permissions + toShare) and then shares
    // (creates the symlink). The current collaborators are preserved so they are
    // not unshared by the storage's replace-then-unshare logic.
    List<PermissionEntry> permissions = preserveExistingCollaborators(node.getAcl());
    // Only grant the recipient "read" if it is not already a collaborator with an
    // equal-or-greater permission. updatePermissions writes the map in list order,
    // so appending (recipient,"read") after preserveExistingCollaborators already
    // emitted (recipient,"edit") would silently DOWNGRADE the recipient to read
    // (this also happens when a space document is shared back to its own space).
    if (!alreadyGrantsAccess(permissions, destIdentity)) {
      permissions.add(new PermissionEntry(destIdentity, "read", PermissionRole.ALL.name()));
    }
    Map<Long, String> toShare = new HashMap<>();
    toShare.put(destId, "read");
    Map<Long, String> toNotify = new HashMap<>();
    if (notify == null || notify.booleanValue()) {
      toNotify.put(destId, "read");
    }
    NodePermission nodePermission = node.getAcl() == null ? new NodePermission(true, false, false, false, permissions, toShare, toNotify, null)
                                                          : new NodePermission(node.getAcl().isCanAccess(),
                                                                               node.getAcl().isCanEdit(),
                                                                               node.getAcl().isCanDelete(),
                                                                               node.getAcl().isPublic(),
                                                                               permissions,
                                                                               toShare,
                                                                               toNotify,
                                                                               null);
    documentFileService.updatePermissions(documentId, nodePermission, getCurrentUserIdentityId());
  }

  /**
   * Rebuilds the document's current collaborator list (as read/edit
   * {@link PermissionEntry}) from its resolved ACL, so a share can be added
   * through {@code updatePermissions} without the storage's replace-then-unshare
   * logic dropping the users/spaces that already had access. Mirrors the
   * read/edit derivation the REST layer uses ({@code EntityBuilder}).
   */
  private List<PermissionEntry> preserveExistingCollaborators(NodePermission acl) {
    List<PermissionEntry> result = new java.util.ArrayList<>();
    if (acl == null || acl.getPermissions() == null) {
      return result;
    }
    // Collapse the per-permission ACL entries (each identity appears once per raw
    // JCR permission) into one entry per identity+role, marking it "edit" when any
    // of its raw permissions is a write permission, else "read".
    Map<String, Identity> identityByKey = new java.util.LinkedHashMap<>();
    Map<String, Boolean> editByKey = new HashMap<>();
    for (PermissionEntry entry : acl.getPermissions()) {
      Identity identity = entry.getIdentity();
      if (identity == null) {
        continue;
      }
      String key = identity.getProviderId() + "|" + identity.getRemoteId() + "|" + entry.getRole();
      identityByKey.putIfAbsent(key, identity);
      editByKey.merge(key, isEditAclPermission(entry.getPermission()), Boolean::logicalOr);
    }
    identityByKey.forEach((key, identity) -> {
      String role = key.substring(key.lastIndexOf('|') + 1);
      result.add(new PermissionEntry(identity, Boolean.TRUE.equals(editByKey.get(key)) ? "edit" : "read", role));
    });
    return result;
  }

  private static boolean isEditAclPermission(String permission) {
    return permission != null
           && (permission.contains("add_node") || permission.contains("set_property") || permission.contains("remove"));
  }

  /**
   * Whether the reconstructed collaborator list already contains the given
   * identity with any grant (read or edit). When true, the share must NOT append
   * a (recipient,"read") entry, as that would overwrite an existing higher grant
   * (edit) when the storage writes the permissions map in list order.
   */
  private static boolean alreadyGrantsAccess(List<PermissionEntry> permissions, Identity identity) {
    if (permissions == null || identity == null) {
      return false;
    }
    return permissions.stream().anyMatch(entry -> {
      Identity existing = entry.getIdentity();
      return existing != null && StringUtils.equals(existing.getProviderId(), identity.getProviderId())
             && StringUtils.equals(existing.getRemoteId(), identity.getRemoteId());
    });
  }

  /**
   * Adds a file to the current user's favorites. Only files can be favorited;
   * folders have no favorites view in the Documents app.
   *
   * @param documentId the id of the file to mark as favorite
   */
  public void favoriteDocument(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    Favorite favorite = toFavorite(documentId);
    try {
      favoriteService.createFavorite(favorite);
    } catch (ObjectAlreadyExistsException e) {
      throw new IllegalStateException("The document %s is already in the user's favorites.".formatted(documentId));
    }
  }

  /**
   * Removes a file from the current user's favorites. Only files can be
   * favorited; folders have no favorites view in the Documents app.
   *
   * @param documentId the id of the file to remove from favorites
   */
  public void unfavoriteDocument(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    Favorite favorite = toFavorite(documentId);
    try {
      favoriteService.deleteFavorite(favorite);
    } catch (ObjectNotFoundException e) {
      throw new IllegalStateException("The document %s is not in the user's favorites, so it cannot be removed from them."
          .formatted(documentId));
    }
  }

  private Favorite toFavorite(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode node = checkCanAccessDocument(documentId);
    if (!(node instanceof FileNode)) {
      // Folders have no favorite surface in the Documents app (the favorite
      // button and drawer only exist for files, object type "file"). Writing a
      // "folder" favorite would silently never appear anywhere, so reject it.
      throw new IllegalArgumentException("Only files can be favorited; folders have no favorites view.");
    }
    return new Favorite(FAVORITE_TYPE_FILE, documentId, null, getCurrentUserIdentityId());
  }

  /**
   * Checks a new file version names exactly one source: chat text, base64, a
   * url, a chat attachment or an upload id.
   *
   * @param content chat text
   * @param base64 base64-encoded bytes
   * @param url an http(s) url
   * @param attachmentObjectId the chat attachment's object id
   * @param uploadId the id of one of the user's uploads
   * @throws IllegalArgumentException when no source, or several, are given
   */
  private static void checkOneVersionSource(String content,
                                            String base64,
                                            String url,
                                            String attachmentObjectId,
                                            String uploadId) {
    int sources = (StringUtils.isNotBlank(content) ? 1 : 0) + (StringUtils.isNotBlank(base64) ? 1 : 0)
        + (StringUtils.isNotBlank(url) ? 1 : 0) + (StringUtils.isNotBlank(attachmentObjectId) ? 1 : 0)
        + (StringUtils.isNotBlank(uploadId) ? 1 : 0);
    if (sources == 0) {
      throw new IllegalArgumentException("""
          Provide the new version content from exactly one source:
          a text 'content' string, an 'upload_id' from start_upload, a 'base64' payload, an http(s) 'url', or a file the user attached in the conversation.
          """);
    }
    if (sources > 1) {
      throw new IllegalArgumentException("Provide the new version content from only one source: 'content', 'upload_id', 'base64', 'url' or the chat attachment — not several.");
    }
  }

  /**
   * Resolves the raw bytes for a new file version from the one source given
   * (chat text, base64, url or a chat attachment), {@link #checkOneVersionSource}
   * having checked there is exactly one, reusing
   * {@code upload_document}'s attachment/url resolution so the same ACL and SSRF
   * guards apply. Every source is capped at the upload size limit,
   * {@link DocumentUploadLimit#getMaxBytes()}, as a new file is; base64 at
   * {@link #CHUNK_CHARS} characters, checked before it is decoded; a url read up to
   * {@link DocumentUploadLimit#URL_FETCH_MAX_BYTES} at most. A base64 one
   * too long names the tools that send it in pieces, which a call limited to
   * a space does not offer.
   *
   * @param content chat text
   * @param base64 base64-encoded bytes, at most {@link #CHUNK_CHARS}
   *          characters
   * @param url an http(s) url
   * @param attachmentObjectType the chat attachment's object type
   * @param attachmentObjectId the chat attachment's object id
   * @return the bytes
   * @throws IllegalArgumentException when the content is over the limit
   * @throws IllegalAccessException when the attachment can't be read
   * @throws ObjectNotFoundException when the attachment doesn't exist
   */
  private byte[] resolveContentBytes(String content,
                                     String base64,
                                     String url,
                                     String attachmentObjectType,
                                     String attachmentObjectId) throws IllegalAccessException, ObjectNotFoundException {
    boolean hasContent = StringUtils.isNotBlank(content);
    boolean hasBase64 = StringUtils.isNotBlank(base64);
    boolean hasAttachment = StringUtils.isNotBlank(attachmentObjectId);
    if (hasBase64) {
      checkBase64Length(base64, isLimitedToASpace() ? MSG_VERSION_BASE64_TOO_LONG_IN_SPACE : MSG_VERSION_BASE64_TOO_LONG);
    }
    long maxBytes = DocumentUploadLimit.getMaxBytes();
    try {
      if (hasContent) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
          throw new IllegalArgumentException(MSG_CONTENT_TOO_LARGE.formatted(bytes.length, maxBytes));
        }
        return bytes;
      } else if (hasBase64) {
        // within the limit: checkBase64Length bounds it far below the smallest one, 1 MB
        return UploadToolUtils.decodeBase64(base64);
      } else if (hasAttachment) {
        return readChatAttachment(attachmentObjectType, attachmentObjectId, maxBytes).bytes();
      } else {
        return fetchUrl(url, null).bytes();
      }
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new IllegalStateException("Could not read the new version content from the given source: " + e.getMessage());
    }
  }

  /**
   * Reads the first file attached to a platform object, as the current user
   * (the attachment's ACL is enforced), whatever its type: any attached file
   * can be filed or versioned, not only an image. Its size is checked on the
   * stored size before the file is read, and on the bytes read.
   *
   * @param attachmentObjectType the type of the object holding the attachment
   * @param attachmentObjectId the id of the object holding the attachment
   * @param maxBytes the most bytes accepted
   * @return the file's bytes, its stored type and its name
   * @throws IllegalArgumentException when the object type is missing, or the
   *           file is over the limit
   * @throws IllegalStateException when the file can't be read
   * @throws IllegalAccessException when the user can't read the object
   * @throws ObjectNotFoundException when the object has no file, or its file
   *           is empty
   */
  private UploadToolUtils.FetchedContent readChatAttachment(String attachmentObjectType,
                                                            String attachmentObjectId,
                                                            long maxBytes) throws IllegalAccessException,
                                                                           ObjectNotFoundException {
    if (StringUtils.isBlank(attachmentObjectType)) {
      throw new IllegalArgumentException("attachment_object_type is required together with attachment_object_id.");
    }
    List<String> fileIds = socialAttachmentService.getAttachmentFileIds(attachmentObjectType,
                                                                        attachmentObjectId,
                                                                        getCurrentUserAclIdentity());
    if (fileIds == null || fileIds.isEmpty()) {
      throw new ObjectNotFoundException("No file attachment found for %s/%s.".formatted(attachmentObjectType, attachmentObjectId));
    }
    org.exoplatform.commons.file.model.FileItem file;
    try {
      file = fileService.getFile(Long.parseLong(fileIds.get(0)));
    } catch (Exception e) {
      throw new IllegalStateException("Could not read the referenced attachment file: " + e.getMessage());
    }
    org.exoplatform.commons.file.model.FileInfo fileInfo = file == null ? null : file.getFileInfo();
    if (fileInfo != null && fileInfo.getSize() > maxBytes) {
      throw new IllegalArgumentException(MSG_ATTACHMENT_TOO_LARGE.formatted(fileInfo.getSize(), maxBytes / (1024 * 1024)));
    }
    byte[] bytes;
    try {
      bytes = file == null ? null : file.getAsByte();
    } catch (Exception e) {
      throw new IllegalStateException("Could not read the referenced attachment file: " + e.getMessage());
    }
    if (bytes == null || bytes.length == 0) {
      throw new ObjectNotFoundException("The referenced attachment file is empty or could not be read.");
    }
    if (bytes.length > maxBytes) {
      throw new IllegalArgumentException(MSG_ATTACHMENT_TOO_LARGE.formatted((long) bytes.length, maxBytes / (1024 * 1024)));
    }
    return new UploadToolUtils.FetchedContent(bytes,
                                              fileInfo == null ? null : fileInfo.getMimetype(),
                                              fileInfo == null ? null : fileInfo.getName());
  }

  /**
   * Refuses a base64 file longer than one tool call carries, before it is
   * decoded: a larger file is sent in pieces, which the refusal names.
   *
   * @param base64 the base64 file, not blank
   * @param message the refusal, formatted with the most characters, the
   *          file's characters and the most characters of a piece
   * @throws IllegalArgumentException when the file is longer than
   *           {@link #CHUNK_CHARS} characters
   */
  private static void checkBase64Length(String base64, String message) {
    if (base64.length() > CHUNK_CHARS) {
      throw new IllegalArgumentException(message.formatted(CHUNK_CHARS, base64.length(), CHUNK_CHARS));
    }
  }

  /**
   * Tells whether this call is limited to a space; a call whose space can't
   * be read is taken as limited, as the space checks take it.
   *
   * @return true when the call is limited to a space, or its space can't be
   *         read
   */
  private boolean isLimitedToASpace() {
    try {
      return getScopeSpaceId() != null;
    } catch (RuntimeException e) {
      return true;
    }
  }

  /**
   * Downloads a file through mcp-server's guarded fetcher, reading at most
   * {@link DocumentUploadLimit#URL_FETCH_MAX_BYTES}, or the upload size
   * limit when it is lower, and completes the refusal of a larger file with
   * what to do instead.
   *
   * @param url the http(s) URL
   * @param defaultFileName the file name when the URL's path has none
   * @return the downloaded bytes, type and file name
   * @throws IllegalArgumentException when the fetcher refuses the URL or its
   *           answer, the file over the limit included
   */
  private static UploadToolUtils.FetchedContent fetchUrl(String url, String defaultFileName) {
    long maxBytes = Math.min(DocumentUploadLimit.URL_FETCH_MAX_BYTES, DocumentUploadLimit.getMaxBytes());
    try {
      return UploadToolUtils.fetchUrl(url, maxBytes, defaultFileName);
    } catch (IllegalArgumentException e) {
      if (StringUtils.contains(e.getMessage(), FETCH_TOO_LARGE_MARKER)) {
        throw new IllegalArgumentException(e.getMessage() + MSG_URL_TOO_LARGE_HINT.formatted(maxBytes / (1024 * 1024)), e);
      }
      throw e;
    }
  }

  /**
   * Stamps the summary onto the version that {@code createNewVersion} just made:
   * that storage call returns an empty {@link FileVersion}, so the current
   * version is looked up and its summary updated.
   */
  private void applyVersionSummary(String documentId, String versionSummary) {
    List<FileVersion> versions = documentFileService.getFileVersions(documentId, getCurrentUserName());
    versions.stream()
            .filter(FileVersion::isCurrent)
            .findFirst()
            .ifPresent(current -> documentFileService.updateVersionSummary(documentId,
                                                                           current.getId(),
                                                                           versionSummary,
                                                                           getCurrentUserName()));
  }

  private UserModel toUserModel(long identityId) {
    Identity identity = identityManager.getIdentity(identityId);
    if (identity == null || !identity.isUser()) {
      return null;
    } else {
      return toUserModel(identity.getRemoteId());
    }
  }

  private UserModel toUserModel(String username) {
    if (StringUtils.isBlank(username)) {
      return null;
    }
    return UserToolUtils.toUserModel(identityManager,
                                     profilePropertyService,
                                     userAcl,
                                     translationService,
                                     portalConfigService,
                                     username,
                                     getCurrentUserName(),
                                     getCurrentUserLocale(),
                                     true);
  }

  @SneakyThrows
  private String getUrl(AbstractNode abstractNode) {
    if (abstractNode instanceof FileNode) {
      return FILE_URL_FORMAT.formatted(abstractNode.getId());
    } else {
      return FOLDER_URL_FORMAT.formatted(abstractNode.getId());
    }
  }

  private AbstractNode checkCanAccessDocument(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    return documentFileService.getDocumentById(documentId, getCurrentUserName());
  }

  private AbstractNode getNode(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    return documentFileService.getDocumentById(documentId, getCurrentUserName());
  }

  /**
   * Resolves the folder a tool writes in, refusing a shortcut's id with a
   * message naming the folder it points to. A shortcut's id resolves to its
   * target for the reads and the rights, while the storage writes under the
   * node of the id it is given, the shortcut node itself, which takes no
   * child: the right would be checked on one node and the write attempted on
   * another. A file, or a shortcut to one, is refused as no folder: the
   * storage would otherwise try to write under a file node.
   *
   * @param folderId the folder id the tool was given
   * @param argumentName the tool argument that carries it, named in the
   *          refusal
   * @return the folder
   * @throws IllegalAccessException when the user can't read the folder
   * @throws ObjectNotFoundException when the folder doesn't exist
   * @throws IllegalArgumentException when the id is a file's or a shortcut's
   */
  private AbstractNode getDestinationFolder(String folderId,
                                            String argumentName) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode folder = getNode(folderId);
    if (StringUtils.equals(folderId, folder.getId())) {
      if (!(folder instanceof FolderNode)) {
        throw new IllegalArgumentException(MSG_FILE_IS_NO_FOLDER.formatted(folderId, argumentName));
      }
      return folder;
    } else if (folder instanceof FolderNode) {
      throw new IllegalArgumentException(MSG_FOLDER_IS_SHORTCUT.formatted(folderId, folder.getId(), argumentName));
    } else {
      throw new IllegalArgumentException(MSG_FILE_SHORTCUT_IS_NO_FOLDER.formatted(folderId, folder.getId(), argumentName));
    }
  }

  private long getOwnerId(String documentId) {
    return documentFileService.getRootFolderOwnerId(documentId);
  }

  private DocumentTreeItemModel toTreeItemModel(FullTreeItem item) {
    List<DocumentTreeItemModel> children = item.getChildren() == null ? null
                                                                      : item.getChildren()
                                                                            .stream()
                                                                            .map(this::toTreeItemModel)
                                                                            .toList();
    return new DocumentTreeItemModel(item.getId(), item.getName(), item.getPath(), children);
  }

  private DocumentVersionModel toVersionModel(FileVersion version) {
    return new DocumentVersionModel(version.getId(),
                                    version.getVersionNumber(),
                                    version.getTitle(),
                                    version.getSummary(),
                                    version.getAuthor(),
                                    version.getAuthorFullName(),
                                    formatDate(version.getCreatedDate()),
                                    version.isCurrent(),
                                    version.getSize());
  }

  /**
   * Splits the name {@code create_folder} receives into the folder names it
   * creates, one per {@code /}-separated segment, each trimmed, empty ones
   * dropped, and checks every one of them before any folder is created, so an
   * invalid segment never leaves the path half created.
   *
   * @param name a folder name, or a path of folder names
   * @return the folder names, in path order, at least one
   * @throws IllegalArgumentException when the name holds no folder name, a
   *           segment that names the folder itself or its parent, or a segment
   *           the storage refuses as a title
   */
  private static List<String> toFolderPathSegments(String name) {
    List<String> segments = name == null ? List.of()
                                          : java.util.Arrays.stream(name.split("/"))
                                                            .map(String::trim)
                                                            .filter(StringUtils::isNotEmpty)
                                                            .toList();
    if (segments.isEmpty()) {
      throw new IllegalArgumentException("The 'name' parameter is mandatory to create a folder. Ask the user for a folder name.");
    }
    if (segments.stream().anyMatch(segment -> ".".equals(segment) || "..".equals(segment))) {
      throw new IllegalArgumentException("The folder name '%s' holds a '.' or '..' segment: give folder names only, separated by '/'."
          .formatted(name));
    }
    segments.stream().filter(segment -> !isValidFolderTitle(segment)).findFirst().ifPresent(segment -> {
      throw new IllegalArgumentException("The folder name '%s' is not valid: a folder name can't hold any of < \\ > : \" / | ? * and can't be only an extension. Ask the user for another name."
          .formatted(segment));
    });
    return segments;
  }

  /**
   * Tells whether the storage accepts a title for a folder it creates. It
   * mirrors {@code JCRDocumentsUtil#isValidDocumentTitle} of
   * {@code documents-storage-jcr}, which {@code JCRDocumentFileStorage#createFolder}
   * applies and which this module cannot call: a title holding none of
   * {@code < \ > : " / | ? *}, whose base name (the part before the last dot)
   * is not blank.
   *
   * @param title the folder title
   * @return true when the storage accepts it
   */
  private static boolean isValidFolderTitle(String title) {
    return StringUtils.isNotBlank(FilenameUtils.getBaseName(title)) && !INVALID_TITLE_CHARACTERS.matcher(title).find();
  }

  /**
   * Gives the id of the child folder of a folder named as one segment of a
   * {@code create_folder} path, creating it when the folder has none. When the
   * creation is refused because the node name is taken, the folder is looked
   * up again, which finds one created by a concurrent call meanwhile; when it
   * still finds none, the taken name belongs to an item that can't be reused:
   * a file, a hidden or shortcut folder, one past the first page of children,
   * or one whose title differs only by what {@code JCRDocumentsUtil#cleanName}
   * replaces in a node name.
   *
   * @param ownerId the owner of the folders' root
   * @param folderId the folder holding the child
   * @param folderName the child folder's name
   * @return the id of the child folder
   * @throws IllegalAccessException when the user cannot add a folder there
   * @throws ObjectNotFoundException when the folder cannot be read
   */
  private String getOrCreateChildFolder(long ownerId,
                                        String folderId,
                                        String folderName) throws IllegalAccessException, ObjectNotFoundException {
    String childFolderId = findChildFolderId(folderId, folderName);
    if (childFolderId != null) {
      return childFolderId;
    }
    try {
      return createChildFolder(ownerId, folderId, folderName).getId();
    } catch (ObjectAlreadyExistsException e) {
      childFolderId = findChildFolderId(folderId, folderName);
      if (childFolderId == null) {
        throw new IllegalStateException("An item named '%s' already exists in this folder but can't be reused as a folder (a file, a hidden or shortcut folder, or a name differing only by punctuation): pick another folder name."
            .formatted(folderName));
      }
      return childFolderId;
    }
  }

  /**
   * Looks for the child folder of a folder whose displayed name is the given
   * one, ignoring case, as the storage names a folder's node after its
   * lower-cased title passed through {@code JCRDocumentsUtil#cleanName}. A
   * shortcut to a folder is listed as a {@link FolderNode} carrying its
   * target's id as source id; it is never reused, since a folder can't be
   * created under a shortcut node.
   *
   * @param folderId the folder holding the child
   * @param folderName the child folder's name
   * @return the child folder's id, null when the folder has none of that name
   * @throws IllegalAccessException when the user cannot read the folder
   * @throws ObjectNotFoundException when the folder cannot be read
   */
  private String findChildFolderId(String folderId, String folderName) throws IllegalAccessException,
                                                                       ObjectNotFoundException {
    DocumentFolderFilter filter = new DocumentFolderFilter(folderId, null, null, null);
    return documentFileService.getFolderChildNodes(filter, 0, FOLDER_PAGE_SIZE, getCurrentUserIdentityId())
                              .stream()
                              .filter(FolderNode.class::isInstance)
                              .filter(child -> StringUtils.isBlank(child.getSourceID()))
                              .filter(child -> folderName.equalsIgnoreCase(StringUtils.trim(child.getName())))
                              .map(AbstractNode::getId)
                              .findFirst()
                              .orElse(null);
  }

  /**
   * Creates one folder under a folder identified by its id.
   *
   * @param ownerId the owner of the folders' root
   * @param folderId the folder the new one is created under
   * @param folderName the new folder's name
   * @return the created folder
   * @throws IllegalAccessException when the user cannot add a folder there
   * @throws ObjectNotFoundException when the folder cannot be read
   * @throws ObjectAlreadyExistsException when the folder already holds an item
   *           of that name
   */
  private AbstractNode createChildFolder(long ownerId,
                                         String folderId,
                                         String folderName) throws IllegalAccessException,
                                                            ObjectNotFoundException,
                                                            ObjectAlreadyExistsException {
    // folderId already identifies the parent JCR node uniquely; the storage
    // 'folderPath' argument is a RELATIVE sub-path resolved *from* that node
    // (node.getNode(folderPath)), so it must stay empty. Passing the absolute
    // node path made root-folder creation fail with
    // "Folder with path : /Users/.../Private isn't found".
    return documentFileService.createFolder(ownerId, folderId, null, folderName, getCurrentUserIdentityId());
  }

  private void checkFolderIdParameter(String folderId) {
    if (StringUtils.isBlank(folderId)) {
      throw new IllegalArgumentException("""
          The 'folderId' parameter is mandatory.
          Call get_root_folder_for_user, get_root_folder_by_space or get_documents_by_folder_id first to obtain a folder id.
          """);
    }
  }

  private void checkDocumentIdParameter(String documentId) {
    if (StringUtils.isBlank(documentId)) {
      throw new IllegalArgumentException("""
          The 'documentId' parameter is mandatory.
          Ensure to retrieve the id from the expression '/document:ID'.
          Example with documentId = be0688cd7f00010134384ab7e1b15a48, The expression would be /document:be0688cd7f00010134384ab7e1b15a48
          """);
    }
  }

  private DocumentModel toDocumentModel(AbstractNode document) {
    if (document instanceof FileNode fileNode) {
      return toDocumentFileModel(fileNode);
    } else {
      return toDocumentFolderModel(document);
    }
  }

  /**
   * Maps a file to the model the tools return, with the user's rights when the
   * listing computed them.
   *
   * @param fileNode the file
   * @return the model
   */
  private DocumentFileModel toDocumentFileModel(FileNode fileNode) {
    String documentId = fileNode.getId();
    DocumentFileModel model = new DocumentFileModel(documentId,
                                 fileNode.getName(),
                                 fileNode.getPath(),
                                 getUrl(fileNode),
                                 fileNode.getParentFolderId(),
                                 formatDate(fileNode.getCreatedDate()),
                                 toUserModel(fileNode.getCreatorUserName()),
                                 fileNode.getSize(),
                                 fileNode.getMimeType(),
                                 getAudioTranscription(documentId),
                                 fileNode.getDescription(),
                                 formatDate(fileNode.getModifiedDate()),
                                 toUserModel(fileNode.getModifierId()));
    setPermissions(model, fileNode);
    return model;
  }

  /**
   * Copies the user's edit and delete rights the storage computed on a
   * document into its model, leaving them unknown when it computed none. The
   * storage computes them from the document's permission entries, and a
   * delete right on any document of the user's own drive, while the
   * repository grants every right to the document's owner: the owner's rights
   * are added here, as {@link #getDocumentPermissions} adds them. A shortcut
   * listed in a folder is the shortcut node itself, whose rights aren't its
   * target's: its rights are left unknown and its target named, for
   * {@link #getDocumentPermissions} to tell them. A shortcut asked by its id
   * resolves to its target, whose rights the storage returned.
   *
   * @param model the model to complete
   * @param document the document it maps
   */
  private void setPermissions(DocumentModel model, AbstractNode document) {
    NodePermission acl = document.getAcl();
    if (StringUtils.isNotBlank(document.getSourceID())) {
      model.setShortcutTargetId(document.getSourceID());
    } else if (acl != null) {
      boolean owner = isOwner(document);
      model.setCanEdit(owner || acl.isCanEdit());
      model.setCanDelete(owner || acl.isCanDelete());
    }
  }

  /**
   * Tells whether the current user owns a document, the repository granting
   * its owner every right whatever its permission entries say.
   *
   * @param document the document
   * @return true when the user is its owner
   */
  private boolean isOwner(AbstractNode document) {
    return StringUtils.equals(document.getCreatorUserName(), getCurrentUserName());
  }

  private String getAudioTranscription(String documentId) {
    String audioTranscription;
    try {
      audioTranscription = documentFileService.getAudioTranscription(documentId, getCurrentUserIdentityId());
    } catch (IllegalAccessException e) {
      audioTranscription = null;
    }
    return audioTranscription;
  }

  /**
   * Maps a folder to the model the tools return, with its last change and the
   * user's rights when the listing computed them.
   *
   * @param folder the folder
   * @return the model
   */
  private DocumentFolderModel toDocumentFolderModel(AbstractNode folder) {
    DocumentFolderModel model = new DocumentFolderModel(folder.getId(),
                                                        folder.getName(),
                                                        folder.getPath(),
                                                        getUrl(folder),
                                                        folder.getParentFolderId(),
                                                        formatDate(folder.getCreatedDate()),
                                                        toUserModel(folder.getCreatorUserName()));
    if (folder.getModifiedDate() > 0) {
      model.setUpdatedDate(formatDate(folder.getModifiedDate()));
    }
    setPermissions(model, folder);
    return model;
  }

  /**
   * Reads the date a listing is limited to, as the model passes it: an
   * ISO-8601 date-time with an offset, or a date-time or a date taken in UTC.
   *
   * @param updatedSince the date, blank for none
   * @return the date in milliseconds, null for none
   * @throws IllegalArgumentException when the date can't be read
   */
  private static Long parseUpdatedSince(String updatedSince) {
    if (StringUtils.isBlank(updatedSince)) {
      return null;
    }
    String value = updatedSince.trim();
    try {
      Instant instant;
      try {
        instant = OffsetDateTime.parse(value).toInstant();
      } catch (DateTimeParseException e) {
        instant = parseWithoutOffset(value);
      }
      return instant.toEpochMilli();
    } catch (DateTimeException | ArithmeticException e) {
      // unreadable, or a year too far for a date in milliseconds
      throw new IllegalArgumentException(MSG_UPDATED_SINCE_UNREADABLE.formatted(updatedSince));
    }
  }

  /**
   * Reads a date-time or a date without an offset, in UTC.
   *
   * @param value the date-time or the date
   * @return the instant
   * @throws DateTimeParseException when it is neither
   */
  private static Instant parseWithoutOffset(String value) {
    try {
      return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException e) {
      return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
  }

  // FIXME should be removed once Notes refactored and cleaned
  private long fixNoteContentId(String contentType, long contentId) throws WikiException, IllegalAccessException {
    if (StringUtils.contains(contentType, "note")) {
      Page note = ExoContainerContext.getService(NoteService.class)
                                     .getNoteByIdAndLang(contentId,
                                                         getCurrentUserAclIdentity(),
                                                         null,
                                                         getCurrentUserLocale().toLanguageTag());
      if (note != null && note.getLatestVersionId() != null) {
        contentId = Long.parseLong(note.getLatestVersionId());
      }
    }
    return contentId;
  }

  // FIXME should be removed once Notes refactored and cleaned
  private String fixNoteContentType(String contentType) {
    if (StringUtils.contains(contentType, "note")) {
      return "WIKI_PAGE_VERSIONS";
    } else {
      return contentType;
    }
  }

  // ---------------------------------------------------------------------------
  // Content creation / upload helpers
  // ---------------------------------------------------------------------------

  /**
   * Files the given bytes into a folder the way the Documents app files an
   * upload: staged in the platform's upload service, then filed at once as
   * the user.
   *
   * @param parentFolderId the folder
   * @param fileName the file name, with its extension
   * @param bytes the file's bytes
   * @param conflictAction what to do when the folder holds a file of that name
   * @return the filed document
   * @throws IllegalArgumentException when the file is empty or over the upload
   *           size limit, or a new version is asked of a folder, a shortcut or a
   *           file that keeps no versions
   * @throws IllegalStateException when the file can't be filed
   * @throws ObjectNotFoundException when the folder doesn't exist
   * @throws IllegalAccessException when the user can't add documents to it
   */
  private DocumentModel fileContent(String parentFolderId,
                                    String fileName,
                                    byte[] bytes,
                                    UploadConflictAction conflictAction) throws IllegalAccessException, ObjectNotFoundException {
    if (bytes == null || bytes.length == 0) {
      throw new IllegalArgumentException("The document content is empty; there is nothing to create.");
    }
    long maxBytes = DocumentUploadLimit.getMaxBytes();
    if (bytes.length > maxBytes) {
      throw new IllegalArgumentException("The file is %d bytes, over the %d MB a file uploaded can be.".formatted(bytes.length,
                                                                                                                maxBytes / (1024 * 1024)));
    }
    String name = sanitizeFileName(fileName);
    checkUploadFolder(parentFolderId);
    String uploadId = UploadToolUtils.materialize(uploadService, bytes, name, DEFAULT_UPLOAD_MIME_TYPE);
    try {
      return fileUpload(parentFolderId, uploadId, name, conflictAction);
    } finally {
      UploadToolUtils.release(uploadService, uploadId);
    }
  }

  /**
   * Files one of the user's uploads, sent through an upload link, into a
   * folder: its staged file is handed to the filing as it is, never read into
   * memory, under the name given or, when none is, the name it was uploaded
   * under; the upload takes the name it is filed under, which the filing
   * types the file from. The folder is checked first, which leaves the upload in place
   * when it is refused; once read, the upload is consumed, whether the filing
   * succeeds or fails.
   *
   * @param parentFolderId the folder
   * @param uploadId the upload
   * @param name the file name, blank for the upload's own
   * @param conflictAction what to do when the folder holds a file of that name
   * @return the filed document
   * @throws IllegalArgumentException when the upload id names no upload of
   *           the user's, the file is empty, over the upload size limit or
   *           refused by a validator, it has no name, or a new version is
   *           asked of a folder, a shortcut or a file that keeps no versions
   * @throws IllegalStateException when the upload is still being sent or
   *           failed, or the file can't be read or filed
   * @throws ObjectNotFoundException when the folder doesn't exist
   * @throws IllegalAccessException when the user can't add documents to it,
   *           or the call is limited to a space
   */
  private DocumentModel fileStagedUpload(String parentFolderId,
                                         String uploadId,
                                         String name,
                                         UploadConflictAction conflictAction) throws IllegalAccessException,
                                                                              ObjectNotFoundException {
    checkUploadFolder(parentFolderId);
    UploadResource upload = readStagedUpload(uploadId);
    try {
      checkStagedFile(upload, uploadId);
      String fileName = StringUtils.isBlank(name) ? upload.getFileName() : name;
      if (StringUtils.isBlank(fileName)) {
        throw new IllegalArgumentException("The upload '%s' carries no file name: pass 'name', the document file name with its extension (e.g. 'report.pdf')."
            .formatted(uploadId));
      }
      fileName = sanitizeFileName(fileName);
      // the filing types the file from the upload's own name: the upload is the user's, and consumed by this call
      upload.setFileName(fileName);
      // the filing reads the staged file, runs the upload validators on it and removes the upload once filed
      return fileUpload(parentFolderId, uploadId, fileName, conflictAction);
    } finally {
      uploadService.removeUploadResource(uploadId, getCurrentUserName());
    }
  }

  /**
   * Adds a new version to a file from one of the user's uploads, sent through
   * an upload link: its staged file is checked by the upload validators under
   * the file's name and type, as the other sources are, then streamed into
   * the version, never read into memory. The file is checked by the caller
   * before, which leaves the upload in place when it is refused; once read,
   * the upload is consumed, whether the version is added or not.
   *
   * @param file the file versioned
   * @param fileId the id of the file versioned, a shortcut's target
   * @param uploadId the upload
   * @throws IllegalArgumentException when the upload id names no upload of
   *           the user's, or the file is empty, over the upload size limit or
   *           refused by a validator
   * @throws IllegalStateException when the upload is still being sent or
   *           failed, or its file can't be read
   * @throws IllegalAccessException when the call is limited to a space
   */
  private void addVersionFromStagedUpload(FileNode file, String fileId, String uploadId) throws IllegalAccessException {
    UploadResource upload = readStagedUpload(uploadId);
    try {
      Path stagedFile = checkStagedFile(upload, uploadId);
      documentFileService.validateUploadedFile(file.getName(), file.getMimeType(), stagedFile);
      try (InputStream content = Files.newInputStream(stagedFile)) {
        documentFileService.createNewVersion(fileId, getCurrentUserName(), content);
      } catch (IOException e) {
        throw new IllegalStateException("The file of the upload '%s' can't be read: open a new one with start_upload.".formatted(uploadId),
                                        e);
      }
    } finally {
      uploadService.removeUploadResource(uploadId, getCurrentUserName());
    }
  }

  /**
   * Reads one of the user's uploads through the owner-checked accessor: an
   * id that is unknown on this node and one owned by another user are
   * refused alike, so the refusal tells nothing of another user's uploads.
   * An upload still being sent is refused and left in place, for its curl
   * command to complete and a later call to consume: removing it would delete
   * a file still being written. An upload id is refused in a call limited to
   * a space, where start_upload is not offered.
   *
   * @param uploadId the upload
   * @return the upload, sent or failed
   * @throws IllegalArgumentException when the id names no upload of the
   *           user's on this node
   * @throws IllegalStateException when the upload is still being sent
   * @throws IllegalAccessException when the call is limited to a space
   */
  private UploadResource readStagedUpload(String uploadId) throws IllegalAccessException {
    if (isLimitedToASpace()) {
      throw new IllegalAccessException(MSG_UPLOAD_ID_IN_SPACE);
    }
    UploadResource upload = uploadService.getUploadResource(uploadId, getCurrentUserName());
    if (upload == null) {
      throw new IllegalArgumentException(MSG_UPLOAD_ID_UNKNOWN.formatted(uploadId));
    }
    if (upload.getStatus() == UploadResource.UPLOADING_STATUS) {
      throw new IllegalStateException(MSG_UPLOAD_ID_UPLOADING.formatted(uploadId));
    }
    return upload;
  }

  /**
   * Checks an upload read for this call is complete, and that its file is
   * on disk, not empty and within the upload size limit, measured on the file
   * itself.
   *
   * @param upload the upload
   * @param uploadId its id
   * @return its file
   * @throws IllegalArgumentException when the file is empty or over the upload
   *           size limit
   * @throws IllegalStateException when the upload failed, or its file is
   *           missing or can't be read
   */
  private static Path checkStagedFile(UploadResource upload, String uploadId) {
    if (upload.getStatus() != UploadResource.UPLOADED_STATUS || StringUtils.isBlank(upload.getStoreLocation())) {
      throw new IllegalStateException(MSG_UPLOAD_ID_FAILED.formatted(uploadId));
    }
    Path stagedFile = Path.of(upload.getStoreLocation());
    long size;
    try {
      size = Files.size(stagedFile);
    } catch (IOException e) {
      throw new IllegalStateException(MSG_UPLOAD_ID_FAILED.formatted(uploadId), e);
    }
    if (size == 0) {
      throw new IllegalArgumentException("The upload '%s' holds an empty file; there is nothing to file.".formatted(uploadId));
    }
    long maxBytes = DocumentUploadLimit.getMaxBytes();
    if (size > maxBytes) {
      throw new IllegalArgumentException("The upload '%s' is %d bytes, over the %d MB a document can be, and is dropped: only a file within that size can be filed, through a new start_upload.".formatted(uploadId,
                                                                                                                      size,
                                                                                                                      maxBytes
                                                                                                                          / (1024 * 1024)));
    }
    return stagedFile;
  }

  /**
   * Files an upload staged in the platform's upload service into a folder.
   *
   * @param folderId the folder
   * @param uploadId the staged upload
   * @param name the file name
   * @param conflictAction what to do when the folder holds a file of that name
   * @return the filed document
   * @throws IllegalAccessException when the user can't add documents to the
   *           folder
   */
  private DocumentModel fileUpload(String folderId,
                                   String uploadId,
                                   String name,
                                   UploadConflictAction conflictAction) throws IllegalAccessException {
    try {
      return toDocumentModel(documentFileService.saveUploadedDocument(folderId,
                                                                      uploadId,
                                                                      name,
                                                                      conflictAction,
                                                                      getCurrentUserIdentityId()));
    } catch (ObjectNotFoundException e) {
      throw new IllegalStateException("The file '%s' couldn't be filed: the folder %s, or the file staged for it, is gone.".formatted(name,
                                                                                                                                   folderId));
    }
  }

  /**
   * Checks a folder exists, is readable, is a folder, and takes the user's
   * documents.
   *
   * @param folderId the folder
   * @throws IllegalArgumentException when it is a file
   * @throws ObjectNotFoundException when it doesn't exist
   * @throws IllegalAccessException when the user can't read it or add
   *           documents to it
   */
  private void checkUploadFolder(String folderId) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode folder = getNode(folderId);
    if (!(folder instanceof FolderNode)) {
      throw new IllegalArgumentException(MSG_UPLOAD_NOT_A_FOLDER.formatted(folder.getName(), folderId));
    }
    // a shortcut's node is the folder it targets, which is the one written in
    if (!documentFileService.hasEditPermissionOnDocument(folder.getId(), getCurrentUserIdentityId())) {
      throw new IllegalAccessException(MSG_UPLOAD_FOLDER_NOT_WRITABLE.formatted(folder.getName(), folderId));
    }
  }

  /**
   * Reads what an upload does when its folder holds a file of its name.
   *
   * @param onConflict keep_both or new_version, in any case; blank
   *          for keep_both
   * @return the action
   */
  static UploadConflictAction toConflictAction(String onConflict) {
    if (StringUtils.isBlank(onConflict)) {
      return UploadConflictAction.KEEP_BOTH;
    }
    try {
      return UploadConflictAction.valueOf(onConflict.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("'on_conflict' is keep_both or new_version, not '%s'.".formatted(onConflict));
    }
  }

  /**
   * Lists the ids of the children of one kind currently under a folder, used
   * to snapshot a destination before an operation that adds a node of that
   * kind (copy, duplicate) so the newly created node can be identified
   * afterwards.
   *
   * @param folderId the folder
   * @param kind {@link FileNode} or {@link FolderNode}, the kind of the added
   *          node
   * @return the ids of its children of that kind
   * @throws ObjectNotFoundException when the folder doesn't exist
   * @throws IllegalAccessException when the user can't read it
   */
  private Set<String> currentChildIds(String folderId,
                                      Class<? extends AbstractNode> kind) throws ObjectNotFoundException,
                                                                          IllegalAccessException {
    DocumentFolderFilter filter = new DocumentFolderFilter(folderId, null, null, null);
    return documentFileService.getFolderChildNodes(filter, 0, FOLDER_PAGE_SIZE, getCurrentUserIdentityId())
                              .stream()
                              .filter(kind::isInstance)
                              .map(AbstractNode::getId)
                              .collect(java.util.stream.Collectors.toSet());
  }

  /**
   * @param node a file or a folder
   * @return {@link FolderNode} for a folder, {@link FileNode} otherwise: the
   *         kind of node a copy or a duplicate of it adds
   */
  private static Class<? extends AbstractNode> kindOf(AbstractNode node) {
    return node instanceof FolderNode ? FolderNode.class : FileNode.class;
  }

  /**
   * Resolves the node of one kind just added to a folder: the child of that kind
   * whose id is not in the pre-operation snapshot and whose name matches the
   * expected created name.
   *
   * @param folderId the folder to resolve the created node in
   * @param beforeIds the folder's ids of that kind snapshotted before the
   *          operation
   * @param expectedName the name the created node is expected to carry
   * @param kind {@link FileNode} or {@link FolderNode}
   * @return the resolved created node, or {@code null} if none matches yet
   * @throws ObjectNotFoundException when the folder doesn't exist
   * @throws IllegalAccessException when the user can't read it
   */
  private DocumentModel resolveAddedChild(String folderId,
                                          Set<String> beforeIds,
                                          String expectedName,
                                          Class<? extends AbstractNode> kind) throws ObjectNotFoundException,
                                                                              IllegalAccessException {
    DocumentFolderFilter filter = new DocumentFolderFilter(folderId, null, null, null);
    List<AbstractNode> children = documentFileService.getFolderChildNodes(filter, 0, FOLDER_PAGE_SIZE,
                                                                          getCurrentUserIdentityId());
    List<AbstractNode> candidates = children.stream()
                                            .filter(kind::isInstance)
                                            .filter(node -> !beforeIds.contains(node.getId()))
                                            .filter(node -> nameMatchesExpected(node.getName(), expectedName))
                                            .toList();
    if (candidates.isEmpty()) {
      return null;
    }
    if (candidates.size() > 1) {
      throw new IllegalStateException(("Could not unambiguously resolve the created document in folder %s "
          + "(multiple candidates matched '%s'); please retry.").formatted(folderId, expectedName));
    }
    return toDocumentModel(candidates.get(0));
  }

  /**
   * Resolves the single file or folder added to a folder by a synchronous
   * operation (copy / duplicate) and fails clearly when it cannot be found,
   * rather than returning the storage's folder node or silently guessing. The
   * ambiguity guard (more than one match) is enforced by
   * {@link #resolveAddedChild}; a {@code null} here means no added node matched
   * the expected name, which is surfaced as an actionable error instead of a
   * wrong document.
   *
   * @param folderId the folder the operation added a node to
   * @param beforeIds the folder's ids of that kind snapshotted before the
   *          operation
   * @param expectedName the expected name of the created node
   * @param kind {@link FileNode} or {@link FolderNode}, the kind of the
   *          created node
   * @param operation a short label for the operation ("copied" / "duplicated"),
   *          used only in the error message
   * @return the resolved created document (never {@code null})
   */
  private DocumentModel requireResolvedChild(String folderId,
                                             Set<String> beforeIds,
                                             String expectedName,
                                             Class<? extends AbstractNode> kind,
                                             String operation) throws ObjectNotFoundException, IllegalAccessException {
    DocumentModel resolved = resolveAddedChild(folderId, beforeIds, expectedName, kind);
    if (resolved == null) {
      throw new IllegalStateException(("Could not locate the %s document '%s' in folder %s after the operation. "
          + "Call list_folder_children on that folder to find it.").formatted(operation, expectedName, folderId));
    }
    return resolved;
  }

  /**
   * Regex of the trailing copy counter the platform appends on a name clash, e.g.
   * the " (1)" in "report.pdf (1)" (see the JCR storage's {@code duplicateItem} /
   * {@code getNewName}); matched at the very end of the name.
   */
  private static final java.util.regex.Pattern COUNTER_SUFFIX = java.util.regex.Pattern.compile("\\s*\\(\\d+\\)$");

  /**
   * Whether a folder child's actual name matches the name a created file is
   * expected to carry. Matches robustly against the platform's naming rules:
   * an exact (case-insensitive) match; the expected name plus a trailing " (n)"
   * copy counter ("report.pdf (1)"); or the same-extension "base (n).ext" variant
   * ("report (1).pdf"). A blank expected name accepts any candidate (fallback).
   */
  private static boolean nameMatchesExpected(String candidate, String expected) {
    String actual = StringUtils.trimToEmpty(candidate);
    String wanted = StringUtils.trimToEmpty(expected);
    if (StringUtils.isBlank(wanted)) {
      return true;
    }
    if (actual.equalsIgnoreCase(wanted)) {
      return true;
    }
    // Counter appended at the very end of the whole name: "report.pdf (1)".
    if (stripCounter(actual).equalsIgnoreCase(wanted)) {
      return true;
    }
    // Counter inserted before the extension: "report (1).pdf" vs "report.pdf".
    String wantedExtension = extension(wanted);
    if (StringUtils.isNotBlank(wantedExtension) && wantedExtension.equalsIgnoreCase(extension(actual))) {
      return stripCounter(baseName(actual)).equalsIgnoreCase(baseName(wanted));
    }
    return false;
  }

  /**
   * Strips a trailing " (n)" copy counter from a name, returning the name
   * unchanged when it carries none.
   */
  private static String stripCounter(String name) {
    return COUNTER_SUFFIX.matcher(StringUtils.trimToEmpty(name)).replaceAll("");
  }

  /**
   * Returns the base name of a file name (everything before the last dot), or the
   * whole name when it has no extension.
   */
  private static String baseName(String name) {
    String trimmed = StringUtils.trimToEmpty(name);
    int dot = trimmed.lastIndexOf('.');
    return dot > 0 ? trimmed.substring(0, dot) : trimmed;
  }

  /**
   * Returns the lower-cased extension of a file name (everything after the last
   * dot), or an empty string when it has none.
   */
  private static String extension(String name) {
    String trimmed = StringUtils.trimToEmpty(name);
    int dot = trimmed.lastIndexOf('.');
    return dot > 0 ? StringUtils.lowerCase(trimmed.substring(dot + 1)) : "";
  }

  private static String sanitizeFileName(String fileName) {
    String cleaned = StringUtils.trimToEmpty(fileName).replaceAll("[/\\\\]", "_");
    return cleaned.isEmpty() ? "document" : cleaned;
  }

  private long getCurrentUserIdentityId() {
    return identityManager.getOrCreateUserIdentity(getCurrentUserName())
                          .getIdentityId();
  }

}
