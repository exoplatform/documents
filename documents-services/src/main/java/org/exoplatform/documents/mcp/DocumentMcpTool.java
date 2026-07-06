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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
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
import org.exoplatform.wiki.WikiException;
import org.exoplatform.wiki.model.Page;
import org.exoplatform.wiki.service.NoteService;

import io.meeds.mcp.server.plugin.McpToolPlugin;
import io.meeds.mcp.server.tool.model.UserModel;
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

  private final DocumentFileService documentFileService;

  private final AttachmentService   attachmentService;

  private final IdentityManager     identityManager;

  private final SpaceService        spaceService;

  private final TranslationService  translationService;

  private final ProfilePropertyService profilePropertyService;

  private final UserACL             userAcl;

  private final UserPortalConfigService portalConfigService;

  public DocumentMcpTool(DocumentFileService documentFileService,
                         AttachmentService attachmentService,
                         IdentityManager identityManager,
                         SpaceService spaceService,
                         TranslationService translationService,
                         ProfilePropertyService profilePropertyService,
                         UserACL userAcl,
                         UserPortalConfigService portalConfigService) {
    this.documentFileService = documentFileService;
    this.attachmentService = attachmentService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.translationService = translationService;
    this.profilePropertyService = profilePropertyService;
    this.userAcl = userAcl;
    this.portalConfigService = portalConfigService;
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
    AbstractNode folder = getNode(folderId);
    List<BreadCrumbItem> breadcrumb = documentFileService.getBreadcrumb(getOwnerId(folderId),
                                                                        folderId,
                                                                        folder.getPath(),
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
    AbstractNode parent = getNode(parentFolderId);
    try {
      AbstractNode folder = documentFileService.createFolder(getOwnerId(parentFolderId),
                                                             parentFolderId,
                                                             parent.getPath(),
                                                             name,
                                                             getCurrentUserIdentityId());
      return toDocumentModel(folder);
    } catch (ObjectAlreadyExistsException e) {
      throw new IllegalStateException("A folder named '%s' already exists under this parent folder. Tell the user to pick a different name."
          .formatted(name));
    }
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

  private long getCurrentUserIdentityId() {
    return identityManager.getOrCreateUserIdentity(getCurrentUserName())
                          .getIdentityId();
  }

}
