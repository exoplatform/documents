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
import org.exoplatform.documents.mcp.model.DocumentPublicLinkModel;
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
                                       StringUtils.isBlank(conflictAction) ? "keepBoth" : conflictAction);
    } catch (ObjectAlreadyExistsException e) {
      throw new IllegalStateException("A document with the same name already exists in the destination folder. Retry with conflict_action set to 'keepBoth' (move as a renamed copy) or 'createNewVersion' (replace the existing file's content, keeping its history).");
    }
  }

  public DocumentModel copyDocument(String documentId,
                                    String destinationFolderId) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    if (StringUtils.isBlank(destinationFolderId)) {
      throw new IllegalArgumentException("The 'destinationFolderId' parameter is mandatory. Call get_document_by_id or get_documents_by_folder_id to find the target folder id.");
    }
    // The storage copyDocument returns the DESTINATION folder node, not the copy,
    // so snapshot the destination's file ids first and resolve the newly added
    // file afterwards to return the actual copied document.
    Set<String> beforeFileIds = currentChildFileIds(destinationFolderId);
    AbstractNode copyReturn = documentFileService.copyDocument(documentId, destinationFolderId, getCurrentUserIdentityId());
    DocumentModel copied = resolveAddedChildFile(destinationFolderId, beforeFileIds);
    return copied != null ? copied : toDocumentModel(copyReturn);
  }

  public DocumentModel duplicateDocument(String documentId,
                                         String prefix) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    // The storage duplicateDocument returns the PARENT folder node, not the
    // duplicate, so snapshot the parent's file ids first and resolve the newly
    // added (suffixed/prefixed) file afterwards to return the actual duplicate.
    AbstractNode source = getNode(documentId);
    String parentFolderId = source.getParentFolderId();
    Set<String> beforeFileIds = StringUtils.isBlank(parentFolderId) ? Set.of() : currentChildFileIds(parentFolderId);
    AbstractNode duplicateReturn = documentFileService.duplicateDocument(getOwnerId(documentId),
                                                                         documentId,
                                                                         prefix,
                                                                         getCurrentUserIdentityId());
    DocumentModel duplicate = StringUtils.isBlank(parentFolderId) ? null
                                                                  : resolveAddedChildFile(parentFolderId, beforeFileIds);
    return duplicate != null ? duplicate : toDocumentModel(duplicateReturn);
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

  @SneakyThrows
  public void setDocumentVisibility(String documentId, Boolean hidden) throws IllegalAccessException {
    checkDocumentIdParameter(documentId);
    if (hidden == null) {
      throw new IllegalArgumentException("The 'hidden' parameter is mandatory (true to hide the document, false to make it visible).");
    }
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
   */
  public void createDocumentShortcut(String documentId,
                                     String destinationFolderId,
                                     String conflictAction) throws IllegalAccessException, ObjectNotFoundException {
    checkDocumentIdParameter(documentId);
    if (StringUtils.isBlank(destinationFolderId)) {
      throw new IllegalArgumentException("The 'destinationFolderId' parameter is mandatory. Call get_documents_by_folder_id or get_root_folder_for_user to find the target folder id.");
    }
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
   * one of <code>username</code> or <code>spaceId</code>. Requires edit permission
   * on the document.
   *
   * @param documentId the id of the file to share (get it from the read tools)
   * @param username the username to share the document with (mutually exclusive
   *          with space_id)
   * @param spaceId the id of the space to share the document with (mutually
   *          exclusive with username)
   * @param notify whether to notify the recipient; defaults to true
   */
  public void shareDocument(String documentId,
                            String username,
                            Long spaceId,
                            Boolean notify) throws IllegalAccessException, ObjectNotFoundException {
    AbstractNode node = checkCanAccessDocument(documentId);
    boolean hasUser = StringUtils.isNotBlank(username);
    boolean hasSpace = spaceId != null && spaceId > 0;
    if (hasUser == hasSpace) {
      throw new IllegalArgumentException("Provide exactly one recipient: either 'username' or 'space_id', not both and not none.");
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
      Space space = spaceService.getSpaceById(String.valueOf(spaceId));
      if (space == null) {
        throw new ObjectNotFoundException("Space with id %s not found. Use get_my_spaces to check accessible spaces."
            .formatted(spaceId));
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
   * guards apply.
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
      if (hasContent) {
        return content.getBytes(StandardCharsets.UTF_8);
      } else if (hasBase64) {
        return UploadToolUtils.decodeBase64(base64);
      } else if (hasAttachment) {
        return UploadToolUtils.resolveImage(socialAttachmentService,
                                            fileService,
                                            getCurrentUserAclIdentity(),
                                            null,
                                            null,
                                            attachmentObjectType,
                                            attachmentObjectId,
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
      DocumentModel match = resolveAddedChildFile(parentFolderId, beforeFileIds);
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
   * before an operation that adds a file (import / copy / duplicate) so the newly
   * created node can be identified afterwards.
   */
  private Set<String> currentChildFileIds(String folderId) throws ObjectNotFoundException, IllegalAccessException {
    DocumentFolderFilter filter = new DocumentFolderFilter(folderId, null, null, null);
    return documentFileService.getFolderChildNodes(filter, 0, IMPORT_POLL_PAGE_SIZE, getCurrentUserIdentityId())
                              .stream()
                              .filter(FileNode.class::isInstance)
                              .map(AbstractNode::getId)
                              .collect(java.util.stream.Collectors.toSet());
  }

  /**
   * Resolves the file that was just added to a folder as the child file whose id
   * is not in the pre-operation snapshot; returns {@code null} if none is found
   * yet.
   */
  private DocumentModel resolveAddedChildFile(String folderId,
                                              Set<String> beforeFileIds) throws ObjectNotFoundException,
                                                                         IllegalAccessException {
    DocumentFolderFilter filter = new DocumentFolderFilter(folderId, null, null, null);
    List<AbstractNode> children = documentFileService.getFolderChildNodes(filter, 0, IMPORT_POLL_PAGE_SIZE,
                                                                          getCurrentUserIdentityId());
    return children.stream()
                   .filter(FileNode.class::isInstance)
                   .map(FileNode.class::cast)
                   .filter(file -> !beforeFileIds.contains(file.getId()))
                   .reduce((first, second) -> second)
                   .map(this::toDocumentFileModel)
                   .orElse(null);
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
