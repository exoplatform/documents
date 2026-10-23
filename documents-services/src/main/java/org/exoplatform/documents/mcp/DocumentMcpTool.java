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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.mcp.model.BreadcrumbItemModel;
import org.exoplatform.documents.mcp.model.DocumentFileModel;
import org.exoplatform.documents.mcp.model.DocumentFolderModel;
import org.exoplatform.documents.mcp.model.DocumentModel;
import org.exoplatform.documents.mcp.model.DocumentPublicLinkModel;
import org.exoplatform.documents.mcp.model.DocumentTreeItemModel;
import org.exoplatform.documents.mcp.model.DocumentVersionModel;
import org.exoplatform.documents.mcp.model.DocumentsSizeModel;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.BreadCrumbItem;
import org.exoplatform.documents.model.DocumentFolderFilter;
import org.exoplatform.documents.model.DocumentTextContent;
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

  /**
   * Same password policy DocumentFileRest applies to public-link passwords
   * (min length 9), so a link created here is openable with the given password.
   */
  private static final Log        LOG = ExoLogger.getLogger(DocumentMcpTool.class);

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

  public List<DocumentModel> getDocumentsByFolderId(String folderId,
                                                    Boolean filesOnly,
                                                    Boolean foldersOnly,
                                                    Integer offset,
                                                    Integer limit) throws ObjectNotFoundException,
                                                                   IllegalAccessException {
    List<? extends AbstractNode> documentItems = documentFileService.getDocumentItems(FileListingType.FOLDER,
                                                                                      new DocumentFolderFilter(folderId,
                                                                                                               null,
                                                                                                               null,
                                                                                                               null),
                                                                                      getInteger(offset, DEFAULT_OFFSET),
                                                                                      getInteger(limit, DEFAULT_LIMIT),
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

  public DocumentModel getDocumentById(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    AbstractNode document = documentFileService.getDocumentById(documentId, getCurrentUserName());
    return toDocumentModel(document);
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
      return content.text();
    }
    throw new IllegalStateException(getNoTextMessage(documentId,
                                                     content == null ? DocumentTextContent.Status.UNREADABLE
                                                                     : content.status()));
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
    case TOO_LARGE -> prefix + "the file is too large for its text to be read.";
    case NO_TEXT -> prefix + "no text was found in the file (a scanned document or an image, for instance).";
    case NOT_A_FILE -> "The document with id %s is not a file with a content, so it has no text.".formatted(documentId);
    case BUSY -> prefix + "too many documents are being read at the moment; asking again in a little while may succeed.";
    case TIMED_OUT -> prefix + "reading the file took too long.";
    default -> prefix + "the file could not be read.";
    };
  }

  /**
   * The transcript of an audio or video file, read with the user's rights. When
   * there is none, the answer says why, from the file's transcription state
   * (EXO-92097): queued, in progress with its progress, stopped when its run was
   * lost (running, unchanged for {@link TranscriptionState#STALE_MILLIS}), failed
   * with its reason, transcribed with no speech heard; a file never
   * sent to transcription answers as before. Asked with timestamps, a transcript
   * that has timed segments is given one segment per line, each starting with
   * the moment it was said, {@code [mm:ss · Ns]} ({@code [h:mm:ss · Ns]} past an
   * hour), where N is that moment in whole seconds (EXO-92112); without
   * segments, the plain transcript is given, as without timestamps.
   *
   * @param documentId the file's UUID
   * @param timestamps true to have each segment start with its moment
   * @return the transcript
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist
   * @throws IllegalStateException when the file has no transcript, saying why
   */
  public String getDocumentTranscriptionById(String documentId,
                                             Boolean timestamps) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode document = checkCanAccessDocument(documentId);
    long identityId = getCurrentUserIdentityId();
    String audioTranscription = documentFileService.getAudioTranscription(documentId, identityId);
    if (StringUtils.isNotBlank(audioTranscription)) {
      String timedTranscription = Boolean.TRUE.equals(timestamps) ? toTimedTranscription(documentId, identityId) : null;
      return timedTranscription == null ? audioTranscription : timedTranscription;
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
   * Writes a transcript's timed segments one per line, each starting with the
   * moment it was said.
   *
   * @param documentId the file's UUID
   * @param identityId the identity id of the user reading it
   * @return the timed transcript, or null when the file has no segment, or none
   *         that can be read
   * @throws IllegalAccessException when the user may not read the file
   * @throws ObjectNotFoundException when the file does not exist
   */
  private String toTimedTranscription(String documentId, long identityId) throws IllegalAccessException,
                                                                             ObjectNotFoundException {
    String segments = documentFileService.getTranscriptionSegments(documentId, identityId);
    if (StringUtils.isBlank(segments)) {
      return null;
    }
    StringBuilder timedTranscription = new StringBuilder();
    try {
      JSONArray segmentsArray = new JSONArray(segments);
      for (int i = 0; i < segmentsArray.length(); i++) {
        JSONObject segment = segmentsArray.optJSONObject(i);
        String text = segment == null ? null : StringUtils.trimToNull(segment.optString("text", null));
        double start = segment == null ? Double.NaN : segment.optDouble("start", Double.NaN);
        if (text != null && Double.isFinite(start) && start >= 0) {
          if (!timedTranscription.isEmpty()) {
            timedTranscription.append('\n');
          }
          timedTranscription.append(toMoment((long) start)).append(' ').append(text);
        }
      }
    } catch (JSONException e) {
      LOG.debug("The timed segments of media file {} cannot be read, its plain transcript is given", documentId, e);
      return null;
    }
    return timedTranscription.isEmpty() ? null : timedTranscription.toString();
  }

  /**
   * @param seconds a moment, in whole seconds from the beginning of the media
   * @return the moment as a marker: {@code [mm:ss · Ns]}, or
   *         {@code [h:mm:ss · Ns]} past an hour
   */
  static String toMoment(long seconds) {
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
    case "transcription.disabled" -> "transcription is switched off";
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
   * @return the matching documents
   * @throws ObjectNotFoundException when the space doesn't exist, or no space
   *           of the user carries the name
   * @throws IllegalAccessException when the user can't view the space
   * @throws IllegalArgumentException when both a space id and a name are
   *           given, or several of the user's spaces carry the name
   */
  public List<DocumentFileModel> searchDocuments(String query,
                                                 Long spaceId,
                                                 String spaceName,
                                                 String parentFolderId,
                                                 Integer offset,
                                                 Integer limit,
                                                 Boolean isFavorites) throws ObjectNotFoundException, IllegalAccessException {
    DocumentTimelineFilter filter = new DocumentTimelineFilter();
    filter.setQuery(query);
    if (isFavorites != null && isFavorites.booleanValue()) {
      filter.setFavorites(true);
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

  public List<DocumentModel> listFolderChildren(String folderId,
                                                Integer offset,
                                                Integer limit) throws ObjectNotFoundException, IllegalAccessException {
    checkFolderIdParameter(folderId);
    DocumentFolderFilter filter = new DocumentFolderFilter(folderId, null, null, null);
    List<AbstractNode> children = documentFileService.getFolderChildNodes(filter,
                                                                          getInteger(offset, DEFAULT_OFFSET),
                                                                          getInteger(limit, DEFAULT_LIMIT),
                                                                          getCurrentUserIdentityId());
    return children.stream().map(this::toDocumentModel).toList();
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
      getNode(parentFolderId);
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
    return importContent(parentFolderId, name, content.getBytes(StandardCharsets.UTF_8));
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
    getNode(parentFolderId);
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
   * of three sources: a file/image the user attached in the chat conversation
   * (resolved server-side as the current user via
   * <code>attachment_object_type</code> / <code>attachment_object_id</code>), a
   * base64 payload, or an http(s) URL (fetched server-side behind an SSRF
   * guard).
   *
   * @param parentFolderId the destination folder id (get it from
   *          get_root_folder_for_user / get_root_folder_by_space /
   *          get_documents_by_folder_id)
   * @param name the document file name including its extension (e.g.
   *          'report.pdf'); when uploading from a URL or a chat attachment it may
   *          be left blank to reuse the source file name
   * @param base64 the file bytes encoded in base64 (mutually exclusive with url /
   *          the chat attachment)
   * @param url an http(s) URL to download the file from (mutually exclusive with
   *          base64 / the chat attachment)
   * @param attachmentObjectType the object type of the file the user attached in
   *          chat; set automatically by the client — do not fill it from the
   *          model
   * @param attachmentObjectId the object id of the file the user attached in
   *          chat; set automatically by the client — do not fill it from the
   *          model
   * @return the created document
   */
  public DocumentModel uploadDocument(String parentFolderId,
                                      String name,
                                      String base64,
                                      String url,
                                      String attachmentObjectType,
                                      String attachmentObjectId) throws IllegalAccessException, ObjectNotFoundException {
    checkFolderIdParameter(parentFolderId);
    boolean hasAttachment = StringUtils.isNotBlank(attachmentObjectId);
    boolean hasBase64 = StringUtils.isNotBlank(base64);
    boolean hasUrl = StringUtils.isNotBlank(url);
    int sources = (hasAttachment ? 1 : 0) + (hasBase64 ? 1 : 0) + (hasUrl ? 1 : 0);
    if (sources == 0) {
      throw new IllegalArgumentException("""
          Provide a file to upload from exactly one source:
          a file/image the user attached in the conversation, a 'base64' payload, or an http(s) 'url'.
          """);
    }
    if (sources > 1) {
      throw new IllegalArgumentException("Provide the file to upload from only one source: the chat attachment, 'base64' or 'url' — not several.");
    }
    byte[] bytes;
    String fileName = name;
    try {
      if (hasAttachment) {
        // Reuse UploadToolUtils.resolveImage's attachment branch: it resolves the
        // referenced file to its raw bytes as the current user (ACL enforced) and,
        // unlike its base64/url branches, does NOT constrain to image mime types —
        // so any attached file works. Pass null url/base64 to stay on that branch.
        UploadToolUtils.FetchedContent fetched = UploadToolUtils.resolveImage(socialAttachmentService,
                                                                            fileService,
                                                                            getCurrentUserAclIdentity(),
                                                                            new UploadToolUtils.ImageSource(null,
                                                                                                            null,
                                                                                                            attachmentObjectType,
                                                                                                            attachmentObjectId),
                                                                            UploadToolUtils.DEFAULT_MAX_BYTES);
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
        UploadToolUtils.FetchedContent fetched = UploadToolUtils.fetchUrl(url, UploadToolUtils.DEFAULT_MAX_BYTES, name);
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
    return importContent(parentFolderId, fileName, bytes);
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
    checkDestinationInScopeSpace(destinationFolderId);
    checkScopedChange(documentId, false);
    AbstractNode destination = getNode(destinationFolderId);
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
   * payload, an http(s) <code>url</code>, or a file the user attached in the
   * conversation. The file's name and type are unchanged; only its content and
   * version history are updated.
   *
   * @param documentId the id of the existing file to add a version to (get it
   *          from get_document_by_id / get_documents_by_folder_id /
   *          search_documents)
   * @param content plain text / markdown / HTML body to write as the new version
   *          (use for text files)
   * @param base64 the new file bytes encoded in base64
   * @param url an http(s) URL to download the new content from (fetched
   *          server-side behind an SSRF guard)
   * @param attachmentObjectType the object type of the file the user attached in
   *          chat; set automatically by the client — do not fill it from the model
   * @param attachmentObjectId the object id of the file the user attached in
   *          chat; set automatically by the client — do not fill it from the model
   * @param versionSummary an optional change note stored on the new version
   * @return the updated document (with its refreshed size / modification info)
   */
  public DocumentModel addDocumentVersion(String documentId,
                                          String content,
                                          String base64,
                                          String url,
                                          String attachmentObjectType,
                                          String attachmentObjectId,
                                          String versionSummary) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode node = checkCanAccessDocument(documentId);
    if (!(node instanceof FileNode)) {
      throw new IllegalArgumentException("Only a file can have versions; the id %s points to a folder. Provide a file document_id."
          .formatted(documentId));
    }
    if (!documentFileService.hasEditPermissionOnDocument(documentId, getCurrentUserIdentityId())) {
      throw new IllegalAccessException("You are not allowed to edit the document %s, so you cannot add a new version to it."
          .formatted(documentId));
    }
    byte[] bytes = resolveContentBytes(content, base64, url, attachmentObjectType, attachmentObjectId);
    documentFileService.createNewVersion(documentId, getCurrentUserName(), new ByteArrayInputStream(bytes));
    if (StringUtils.isNotBlank(versionSummary)) {
      applyVersionSummary(documentId, versionSummary);
    }
    return getDocumentById(documentId);
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
    checkDestinationInScopeSpace(destinationFolderId);
    checkScopedChange(documentId, false);
    // Validate the source and the destination folder exist and are readable by
    // the user before touching the JCR storage.
    checkCanAccessDocument(documentId);
    AbstractNode destination = getNode(destinationFolderId);
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
   * Resolves the raw bytes for a new file version from exactly one of the
   * supported sources (chat text, base64, url or a chat attachment), reusing
   * {@code upload_document}'s attachment/url resolution so the same ACL and SSRF
   * guards apply. Every source is capped at
   * {@code UploadToolUtils.DEFAULT_MAX_BYTES}.
   *
   * @param content chat text
   * @param base64 base64-encoded bytes
   * @param url an http(s) url
   * @param attachmentObjectType the chat attachment's object type
   * @param attachmentObjectId the chat attachment's object id
   * @return the bytes
   * @throws IllegalArgumentException when not exactly one source is given, or
   *           the content is over the limit
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
    boolean hasUrl = StringUtils.isNotBlank(url);
    boolean hasAttachment = StringUtils.isNotBlank(attachmentObjectId);
    int sources = (hasContent ? 1 : 0) + (hasBase64 ? 1 : 0) + (hasUrl ? 1 : 0) + (hasAttachment ? 1 : 0);
    if (sources == 0) {
      throw new IllegalArgumentException("""
          Provide the new version content from exactly one source:
          a text 'content' string, a 'base64' payload, an http(s) 'url', or a file the user attached in the conversation.
          """);
    }
    if (sources > 1) {
      throw new IllegalArgumentException("Provide the new version content from only one source: 'content', 'base64', 'url' or the chat attachment — not several.");
    }
    try {
      if (hasContent || hasBase64) {
        byte[] bytes = hasContent ? content.getBytes(StandardCharsets.UTF_8) : UploadToolUtils.decodeBase64(base64);
        if (bytes.length > UploadToolUtils.DEFAULT_MAX_BYTES) {
          throw new IllegalArgumentException(MSG_CONTENT_TOO_LARGE.formatted(bytes.length, UploadToolUtils.DEFAULT_MAX_BYTES));
        }
        return bytes;
      } else if (hasAttachment) {
        return UploadToolUtils.resolveImage(socialAttachmentService,
                                            fileService,
                                            getCurrentUserAclIdentity(),
                                            new UploadToolUtils.ImageSource(null,
                                                                            null,
                                                                            attachmentObjectType,
                                                                            attachmentObjectId),
                                            UploadToolUtils.DEFAULT_MAX_BYTES)
                              .bytes();
      } else {
        return UploadToolUtils.fetchUrl(url, UploadToolUtils.DEFAULT_MAX_BYTES, null).bytes();
      }
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new IllegalStateException("Could not read the new version content from the given source: " + e.getMessage());
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
    return documentFileService.getFolderChildNodes(filter, 0, IMPORT_POLL_PAGE_SIZE, getCurrentUserIdentityId())
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

  private DocumentFileModel toDocumentFileModel(FileNode fileNode) {
    String documentId = fileNode.getId();
    return new DocumentFileModel(documentId,
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

  private DocumentFolderModel toDocumentFolderModel(AbstractNode folder) {
    return new DocumentFolderModel(folder.getId(),
                                   folder.getName(),
                                   folder.getPath(),
                                   getUrl(folder),
                                   folder.getParentFolderId(),
                                   formatDate(folder.getCreatedDate()),
                                   toUserModel(folder.getCreatorUserName()));
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

  private static final int  IMPORT_POLL_MAX_ATTEMPTS = 20;

  private static final long IMPORT_POLL_INTERVAL_MS  = 500L;

  private static final int  IMPORT_POLL_PAGE_SIZE    = 200;

  /**
   * Stages the given bytes as a single file and imports it into the folder,
   * then resolves and returns the created document. {@code importFiles} expects
   * a zip and unzips it, so the file is wrapped in a one-entry zip whose entry
   * name becomes the created document title.
   */
  private DocumentModel importContent(String parentFolderId,
                                      String fileName,
                                      byte[] bytes) throws IllegalAccessException, ObjectNotFoundException {
    if (bytes == null || bytes.length == 0) {
      throw new IllegalArgumentException("The document content is empty; there is nothing to create.");
    }
    // Validate the parent folder exists and is readable before staging anything,
    // so a bad id fails with a clean ObjectNotFoundException / IllegalAccessException.
    getNode(parentFolderId);
    String entryName = sanitizeFileName(fileName);
    String ownerId = String.valueOf(getOwnerId(parentFolderId));
    long userIdentityId = getCurrentUserIdentityId();
    // Snapshot the destination file ids before the import so the created node can
    // be identified even when a same-name file already exists and the import
    // creates a renamed ("duplicate") copy with a different, suffixed name.
    Set<String> beforeFileIds = currentChildFileIds(parentFolderId);
    String uploadId = null;
    try {
      uploadId = UploadToolUtils.materialize(uploadService,
                                             zipSingleEntry(entryName, bytes),
                                             entryName + ".zip",
                                             "application/zip");
      // folderPath stays null: parentFolderId already identifies the JCR node
      // (same rule as createFolder). conflict "duplicate": on a name clash the
      // import creates a renamed (keep-both) copy. NOT "rename" — the async import
      // thread only understands "updateAll"/"duplicate", so "rename" would fall
      // through to ignoredFiles and silently create nothing.
      documentFileService.importFiles(ownerId,
                                      parentFolderId,
                                      null,
                                      uploadId,
                                      "duplicate",
                                      getCurrentUserAclIdentity(),
                                      userIdentityId);
      // On success the asynchronous import owns the upload resource and releases
      // it once the file is created, so it must not be released here.
      uploadId = null;
    } catch (IllegalAccessException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Could not create the document '%s': %s".formatted(entryName, e.getMessage()));
    } finally {
      if (uploadId != null) {
        UploadToolUtils.release(uploadService, uploadId);
      }
    }
    return findImportedDocument(parentFolderId, entryName, beforeFileIds);
  }

  /**
   * The import runs on a background thread, so the created file is polled for in
   * the destination folder until it appears (or a short timeout elapses). The
   * newly created node is identified as the file whose id was not present before
   * the import, so it resolves the just-created (possibly suffixed) file rather
   * than a pre-existing same-name document.
   */
  private DocumentModel findImportedDocument(String parentFolderId,
                                             String fileName,
                                             Set<String> beforeFileIds) throws ObjectNotFoundException,
                                                                        IllegalAccessException {
    for (int attempt = 0; attempt < IMPORT_POLL_MAX_ATTEMPTS; attempt++) {
      DocumentModel match = resolveAddedChildFile(parentFolderId, beforeFileIds, fileName);
      if (match != null) {
        return match;
      }
      sleepQuietly(IMPORT_POLL_INTERVAL_MS);
    }
    throw new IllegalStateException("""
        The document '%s' was uploaded and is being created in the background.
        Call list_folder_children on folder %s in a few seconds to retrieve it.
        """.formatted(fileName, parentFolderId));
  }

  /**
   * Lists the file ids currently under a folder, used to snapshot a destination
   * before an import adds a file, so the newly created node can be identified
   * afterwards.
   *
   * @param folderId the folder
   * @return the ids of its child files
   * @throws ObjectNotFoundException when the folder doesn't exist
   * @throws IllegalAccessException when the user can't read it
   */
  private Set<String> currentChildFileIds(String folderId) throws ObjectNotFoundException, IllegalAccessException {
    return currentChildIds(folderId, FileNode.class);
  }

  /**
   * Lists the ids of the children of one kind currently under a folder, used
   * to snapshot a destination before an operation that adds a node of that
   * kind (import, copy, duplicate) so the newly created node can be identified
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
    return documentFileService.getFolderChildNodes(filter, 0, IMPORT_POLL_PAGE_SIZE, getCurrentUserIdentityId())
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
   * Resolves the file that was just added to a folder by an operation (import /
   * copy / duplicate) as the child file whose id is not in the pre-operation
   * snapshot AND whose name matches the expected created name. Narrowing by name
   * (rather than blindly collapsing the id-diff with {@code reduce((a,b)->b)})
   * keeps the result correct when the folder gains another, differently-named file
   * concurrently (a parallel upload, a copy/import by another user, a retry).
   * <p>
   * Returns {@code null} when no added file matches yet (so a poller can retry),
   * and throws an {@link IllegalStateException} when MORE THAN ONE added file
   * matches the expected name — the operation cannot then be resolved
   * deterministically, and guessing one would risk returning the wrong document.
   *
   * @param folderId the folder to resolve the created file in
   * @param beforeFileIds the folder's file ids snapshotted before the operation
   * @param expectedName the name the created file is expected to carry (the source
   *          name for copy/duplicate, the uploaded file name for import); may be
   *          blank, in which case any single added file is accepted
   * @return the resolved created document, or {@code null} if none matches yet
   */
  private DocumentModel resolveAddedChildFile(String folderId,
                                              Set<String> beforeFileIds,
                                              String expectedName) throws ObjectNotFoundException,
                                                                   IllegalAccessException {
    return resolveAddedChild(folderId, beforeFileIds, expectedName, FileNode.class);
  }

  /**
   * Resolves the node of one kind just added to a folder, as
   * {@link #resolveAddedChildFile} does for a file: the child of that kind
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
    List<AbstractNode> children = documentFileService.getFolderChildNodes(filter, 0, IMPORT_POLL_PAGE_SIZE,
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

  private static byte[] zipSingleEntry(String entryName, byte[] content) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      zip.putNextEntry(new ZipEntry(entryName));
      zip.write(content);
      zip.closeEntry();
    } catch (IOException e) {
      throw new IllegalStateException("Could not package the document content: " + e.getMessage());
    }
    return output.toByteArray();
  }

  private static String sanitizeFileName(String fileName) {
    String cleaned = StringUtils.trimToEmpty(fileName).replaceAll("[/\\\\]", "_");
    return cleaned.isEmpty() ? "document" : cleaned;
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private long getCurrentUserIdentityId() {
    return identityManager.getOrCreateUserIdentity(getCurrentUserName())
                          .getIdentityId();
  }

}
