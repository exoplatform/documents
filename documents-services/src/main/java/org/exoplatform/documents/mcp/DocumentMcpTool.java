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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
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
import org.exoplatform.documents.mcp.model.DocumentTreeItemModel;
import org.exoplatform.documents.mcp.model.DocumentVersionModel;
import org.exoplatform.documents.mcp.model.DocumentsSizeModel;
import org.exoplatform.documents.model.AbstractNode;
import org.exoplatform.documents.model.BreadCrumbItem;
import org.exoplatform.documents.model.DocumentFolderFilter;
import org.exoplatform.documents.model.DocumentTimelineFilter;
import org.exoplatform.documents.model.DocumentsSize;
import org.exoplatform.documents.model.FileNode;
import org.exoplatform.documents.model.FileVersion;
import org.exoplatform.documents.model.FolderNode;
import org.exoplatform.documents.model.FullTreeItem;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.attachments.service.AttachmentService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.upload.UploadService;
import org.exoplatform.wiki.WikiException;
import org.exoplatform.wiki.model.Page;
import org.exoplatform.wiki.service.NoteService;

import io.meeds.mcp.server.plugin.McpToolPlugin;
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
   * Structured / binary office and PDF formats that cannot be produced from a
   * plain text string: they are zipped packages (OOXML/ODF) or a binary layout
   * (PDF), so writing text content as the file body would yield a corrupt file.
   * {@link #createDocument} rejects these up front and points to
   * {@code upload_document} (real file bytes) instead.
   */
  private static final Set<String> UNSUPPORTED_BINARY_EXTENSIONS =
                                                                 Set.of("docx", "xlsx", "pptx", "ppsx", "potx", "dotx", "xltx",
                                                                        "xls", "doc", "ppt", "odt", "ods", "odp", "pdf");

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
                         UploadService uploadService) {
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
  }

  public DocumentFolderModel getRootFolderBySpace(long spaceId) throws ObjectNotFoundException, IllegalAccessException {
    FolderNode spaceRootFolder = documentFileService.getSpaceRootFolder(spaceId, getCurrentUserAclIdentity());
    return toDocumentFolderModel(spaceRootFolder);
  }

  public void attachDocumentToContent(String documentId,
                                      String contentType,
                                      long contentId) throws IllegalAccessException {
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
                                     || (foldersOnly == null || !foldersOnly || d instanceof FolderNode))
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

  public String getDocumentContentById(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    checkCanAccessDocument(documentId);
    return documentFileService.getFileContentAsText(documentId);
  }

  public String getDocumentTranscriptionById(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode document = checkCanAccessDocument(documentId);
    String audioTranscription = documentFileService.getAudioTranscription(documentId, getCurrentUserIdentityId());
    if (StringUtils.isNotBlank(audioTranscription)) {
      return audioTranscription;
    } else if (document instanceof FileNode fileNode
               && StringUtils.startsWithAny(fileNode.getMimeType(), "video/", "audio/")) {
      throw new IllegalStateException("No transcription is available yet for this audio/video file. "
          + "Transcriptions are generated automatically once the media is processed; tell the user to try again later.");
    } else {
      throw new IllegalStateException("The document with id %s isn't an audio nor a video file, so it has no transcription."
          .formatted(documentId));
    }
  }

  public List<DocumentFileModel> searchDocuments(String query,
                                                 Long spaceId,
                                                 String parentFolderId,
                                                 Integer offset,
                                                 Integer limit,
                                                 Boolean isFavorites) throws ObjectNotFoundException, IllegalAccessException {
    DocumentTimelineFilter filter = new DocumentTimelineFilter();
    filter.setQuery(query);
    if (isFavorites != null && isFavorites.booleanValue()) {
      filter.setFavorites(true);
    }
    if (StringUtils.isNotBlank(parentFolderId)) {
      filter.setParentFolderId(parentFolderId);
    } else if (spaceId != null && spaceId > 0) {
      Space space = spaceService.getSpaceById(String.valueOf(spaceId));
      if (space == null) {
        throw new ObjectNotFoundException("Space with id %s not found. Use 'search_my_spaces' tool to check accessible spaces");
      } else if (!spaceService.canViewSpace(space, getCurrentUserName())) {
        throw new IllegalAccessException("Space with id %s not accessbile for current user. Use 'search_my_spaces' tool to check accessible spaces");
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
    DocumentsSize size = documentFileService.getDocumentsSizeStat(resolvedOwnerId, getCurrentUserIdentityId());
    return new DocumentsSizeModel(size.getOwnerId(), size.getToSize());
  }

  // ---------------------------------------------------------------------------
  // Content management (writes, require approval)
  // ---------------------------------------------------------------------------

  public DocumentModel createFolder(String parentFolderId,
                                    String name) throws IllegalAccessException, ObjectNotFoundException {
    checkFolderIdParameter(parentFolderId);
    if (StringUtils.isBlank(name)) {
      throw new IllegalArgumentException("The 'name' parameter is mandatory to create a folder. Ask the user for a folder name.");
    }
    // Validate the parent folder exists and is readable by the user, so a bad id
    // fails with a clean ObjectNotFoundException / IllegalAccessException before
    // reaching the JCR storage.
    getNode(parentFolderId);
    try {
      // parentFolderId already identifies the parent JCR node uniquely; the
      // storage 'folderPath' argument is a RELATIVE sub-path resolved *from* that
      // node (node.getNode(folderPath)), so it must stay empty. Passing the
      // absolute node path made root-folder creation fail with
      // "Folder with path : /Users/.../Private isn't found".
      AbstractNode folder = documentFileService.createFolder(getOwnerId(parentFolderId),
                                                             parentFolderId,
                                                             null,
                                                             name,
                                                             getCurrentUserIdentityId());
      return toDocumentModel(folder);
    } catch (ObjectAlreadyExistsException e) {
      throw new IllegalStateException("A folder named '%s' already exists under this parent folder. Tell the user to pick a different name."
          .formatted(name));
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
          + " '%s' file — office and PDF formats are structured packages, not text. Creating an empty office document from a"
          + " template isn't supported yet. For text use .txt/.md/.html, or use upload_document with the real file bytes"
          + " (base64, url, or a file attached in the conversation).").formatted(extension));
    }
    if (StringUtils.isBlank(content)) {
      throw new IllegalArgumentException("content is required to create a document (the text to write into the file).");
    }
    return importContent(parentFolderId, name, content.getBytes(StandardCharsets.UTF_8));
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
                                                                            null,
                                                                            null,
                                                                            attachmentObjectType,
                                                                            attachmentObjectId,
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

  public void renameDocument(String documentId, String newName) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    if (StringUtils.isBlank(newName)) {
      throw new IllegalArgumentException("The 'newName' parameter is mandatory to rename a document.");
    }
    try {
      documentFileService.renameDocument(getOwnerId(documentId), documentId, newName, getCurrentUserIdentityId());
    } catch (ObjectAlreadyExistsException e) {
      throw new IllegalStateException("A document named '%s' already exists in the same folder. Tell the user to pick a different name."
          .formatted(newName));
    }
  }

  public void moveDocument(String documentId,
                           String destinationFolderId,
                           String conflictAction) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    if (StringUtils.isBlank(destinationFolderId)) {
      throw new IllegalArgumentException("The 'destinationFolderId' parameter is mandatory. Call get_document_by_id or get_documents_by_folder_id to find the target folder id.");
    }
    AbstractNode destination = getNode(destinationFolderId);
    try {
      documentFileService.moveDocument(getOwnerId(documentId),
                                       documentId,
                                       destination.getPath(),
                                       getCurrentUserIdentityId(),
                                       StringUtils.isBlank(conflictAction) ? "rename" : conflictAction);
    } catch (ObjectAlreadyExistsException e) {
      throw new IllegalStateException("A document with the same name already exists in the destination folder. Retry with conflict_action set to 'rename'.");
    }
  }

  public DocumentModel copyDocument(String documentId,
                                    String destinationFolderId) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    if (StringUtils.isBlank(destinationFolderId)) {
      throw new IllegalArgumentException("The 'destinationFolderId' parameter is mandatory. Call get_document_by_id or get_documents_by_folder_id to find the target folder id.");
    }
    AbstractNode copy = documentFileService.copyDocument(documentId, destinationFolderId, getCurrentUserIdentityId());
    return toDocumentModel(copy);
  }

  public DocumentModel duplicateDocument(String documentId,
                                         String prefix) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    AbstractNode duplicate = documentFileService.duplicateDocument(getOwnerId(documentId),
                                                                   documentId,
                                                                   prefix,
                                                                   getCurrentUserIdentityId());
    return toDocumentModel(duplicate);
  }

  public void deleteDocument(String documentId) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode document = checkCanAccessDocument(documentId);
    // delay = 0: move to trash immediately. favorite = false: the flag only
    // triggers removal from the favorites list, which we don't manage here.
    documentFileService.deleteDocument(document.getPath(), documentId, false, 0, getCurrentUserIdentityId());
  }

  public void undoDeleteDocument(String documentId) {
    checkDocumentIdParameter(documentId);
    documentFileService.undoDeleteDocument(documentId, getCurrentUserIdentityId());
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
    FileVersion version = documentFileService.updateVersionSummary(documentId, versionId, summary, getCurrentUserName());
    return toVersionModel(version);
  }

  @SneakyThrows
  public void setDocumentVisibility(String documentId, Boolean hidden) throws IllegalAccessException {
    checkDocumentIdParameter(documentId);
    if (hidden == null) {
      throw new IllegalArgumentException("The 'hidden' parameter is mandatory (true to hide the document, false to make it visible).");
    }
    documentFileService.setDocumentVisibility(getOwnerId(documentId), documentId, hidden, getCurrentUserIdentityId());
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
    String uploadId = null;
    try {
      uploadId = UploadToolUtils.materialize(uploadService,
                                             zipSingleEntry(entryName, bytes),
                                             entryName + ".zip",
                                             "application/zip");
      // folderPath stays null: parentFolderId already identifies the JCR node
      // (same rule as createFolder). conflict "rename" keeps existing documents
      // untouched on a name clash.
      documentFileService.importFiles(ownerId,
                                      parentFolderId,
                                      null,
                                      uploadId,
                                      "rename",
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
    return findImportedDocument(parentFolderId, entryName);
  }

  /**
   * The import runs on a background thread, so the created file is polled for in
   * the destination folder until it appears (or a short timeout elapses).
   */
  private DocumentModel findImportedDocument(String parentFolderId,
                                             String fileName) throws ObjectNotFoundException, IllegalAccessException {
    long userIdentityId = getCurrentUserIdentityId();
    DocumentFolderFilter filter = new DocumentFolderFilter(parentFolderId, null, null, null);
    for (int attempt = 0; attempt < IMPORT_POLL_MAX_ATTEMPTS; attempt++) {
      List<AbstractNode> children = documentFileService.getFolderChildNodes(filter, 0, IMPORT_POLL_PAGE_SIZE, userIdentityId);
      DocumentFileModel match = children.stream()
                                        .filter(FileNode.class::isInstance)
                                        .map(FileNode.class::cast)
                                        .filter(file -> StringUtils.equalsIgnoreCase(file.getName(), fileName))
                                        .reduce((first, second) -> second)
                                        .map(this::toDocumentFileModel)
                                        .orElse(null);
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
