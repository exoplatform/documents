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
package org.exoplatform.documents.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.jcr.RepositoryException;

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.*;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.social.core.identity.model.Identity;

import io.meeds.portal.thumbnail.model.FileContent;

public interface DocumentFileService {

  /**
   * Retrieves a list of accessible folders and/or files, for a selected user,
   * by applying the designated filter.
   * 
   * @param listingType {@link FileListingType}
   * @param filter {@link DocumentNodeFilter} that contains filtering criteria
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param userIdentityId {@link Identity} technical identifier of the user
   *          acessing files
   * @return {@link List} of {@link AbstractNode}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId or ownerId
   * @throws ObjectNotFoundException when parentFolderId or ownerId doesn't
   *           exisits
   */
  List<? extends AbstractNode> getDocumentItems(FileListingType listingType,
                                                DocumentNodeFilter filter,
                                                int offset,
                                                int limit,
                                                long userIdentityId,
                                                boolean showHiddenFiles) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * @param keyword Search term
   * @param identity User ACL {@link org.exoplatform.services.security.Identity}
   *          which can be retrieved by {@link UserACL#getUserIdentity(String)}
   * @param offset search offset
   * @param limit search limit
   * @return {@link List} of found {@link FileNode} available for user
   */
  List<FileNode> search(String keyword, org.exoplatform.services.security.Identity identity, int offset, int limit);

  /**
   * Retrieves a file by its identifier.
   *
   *  @param documentId Id of the given document
   *  @param aclIdentity user identity id
   *  @return {@link AbstractNode}
   * @throws ObjectNotFoundException when document doesn't exisits
   * @throws IllegalAccessException when document isn't accessible for user
   */
  AbstractNode getDocumentById(String documentId, String aclIdentity) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Retrieves a file by its identifier.
   *
   *  @param documentId Id of the given document
   */
  AbstractNode getDocumentById(String documentId);

  /**
   * Retrieves a file content by its identifier.
   *
   * @param documentId  Id of the given document
   * @param aclIdentity user identity id
   * @return {@link InputStream}
   */
  public FileContent getDocumentContent(String documentId, String aclIdentity) throws ObjectNotFoundException;

  /**
   * Retrieves a list of accessible files, for a selected user, by applying the
   * designated filter. The returned results will be of type {@link FileNode}
   * only. The ownerId of filter object will be used to select the list of
   * accessible Nodes to retrieve switch a timeline.
   * 
   * @param filter {@link DocumentTimelineFilter} that contains filtering
   *          criteria
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param userIdentityId {@link Identity} technical identifier of the user
   *          acessing files
   * @return {@link List} of {@link AbstractNode}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated ownerId
   * @throws ObjectNotFoundException when ownerId doesn't exisits
   */
  List<FileNode> getFilesTimeline(DocumentTimelineFilter filter,
                                  int offset,
                                  int limit,
                                  long userIdentityId) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * @param filter {@link DocumentFavoriteFilter} that contains filtering criteria
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param userIdentityId {@link Identity} technical identifier of the user
   *          acessing files
   * @return {@link List} of {@link AbstractNode} identifiers
   * @throws IllegalAccessException when the user isn't found
   */
  List<String> getFavoriteFileIds(DocumentNodeFilter filter, int offset, int limit, long userIdentityId) throws IllegalAccessException;

  /**
   * Retrieves a list of accessible files, for a selected user, by applying the
   * designated filter. The returned results will be of type {@link FileNode}
   * only. The ownerId of filter object will be used to select the list of
   * accessible Nodes to retrieve switch a timeline.

   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param userIdentity {@link Identity} technical identifier of the user
   *          acessing files
   * @param ownerId {@link Identity} technical identifier of the where search files
   * @return {@link List} of {@link FileNode}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated ownerId
   * @throws ObjectNotFoundException when ownerId doesn't exisits
   */
  List<AbstractNode> getBiggestDocuments(long ownerId,
                                     Identity userIdentity,
                                     int offset,
                                     int limit) throws IllegalAccessException, ObjectNotFoundException;
  /**
   * Retrieves the number of existing files by group.
   *
   * @param filter {@link DocumentTimelineFilter} that contains filtering
   *          criteria
   * @param userIdentityId {@link Identity} technical identifier of the user
   *          acessing files
   * @return {@link List} of {@link AbstractNode}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated ownerId
   * @throws ObjectNotFoundException when ownerId doesn't exisits
   */
  DocumentGroupsSize getGroupDocumentsCount(DocumentTimelineFilter filter,
                                            long userIdentityId) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Retrieves a list of accessible files, for a selected user, by applying the
   * designated filter. The parentForlderId of filter object will be used to
   * select the list of Nodes to retrieve.
   * 
   * @param filter {@link DocumentFolderFilter} that contains filtering criteria
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param userIdentityId {@link Identity} technical identifier of the user
   *          acessing files
   * @return {@link List} of {@link AbstractNode}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when parentFolderId doesn't exisits
   */
  List<AbstractNode> getFolderChildNodes(DocumentFolderFilter filter,
                                         int offset,
                                         int limit,
                                         long userIdentityId) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Retrieves breadcrumb of the given node.
   *
   * @param folderId Id of the given folder
   * @param authenticatedUserId of the user acessing files
   * @return {@link List} of {@link BreadCrumbItem}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   */
  List<BreadCrumbItem> getBreadcrumb(long ownerId,String folderId, String folderPath, long authenticatedUserId) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Retrieves tree of the given node.
   *
   * @param folderId Id of the given folder
   * @param authenticatedUserId of the user acessing files
   * @param destinationFolderPath destination folder to add to the tree
   * @param withChildren get all children
   * @return {@link List} of {@link FullTreeItem}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   */
  List<FullTreeItem> getFullTreeData(long ownerId, String folderId, String destinationFolderPath, long authenticatedUserId, boolean withChildren, boolean showHidden) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Duplicate the given node.
   *
   * @param fileId Id of the given file
   * @param authenticatedUserId of the user acessing files
   * @return {@link AbstractNode}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   */
  AbstractNode duplicateDocument(long ownerId,String fileId,String prefixClone, long authenticatedUserId) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Copy the given node.
   *
   * @param nodeId              Id of the given file
   * @param destintionNodeId    Id of the destination folder
   * @param authenticatedUserId of the user acessing files
   * @return {@link AbstractNode}
   * @throws IllegalAccessException  when the user isn't allowed to access
   *                                 documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   */
  AbstractNode copyDocument(String nodeId, String destintionNodeId, long authenticatedUserId) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Move the given node.
   *
   * @param fileId Id of the given file
   * @param authenticatedUserId of the user accessing files
   * @param conflictAction conflict action
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exist
   * @throws ObjectAlreadyExistsException when file with same name already exists
   *           in the target path
   */
  void moveDocument(long ownerId, String fileId, String destPath, long authenticatedUserId, String conflictAction) throws IllegalAccessException, ObjectNotFoundException, ObjectAlreadyExistsException;


  /**
   * Delete a document.
   *
   * @param documentPath the path of document
   * @param documentId the Id of document
   * @param favorite Is favorite document
   * @param delay the delay to apply the delete atcion
   * @param authenticatedUserId of the user acessing files
   * @throws IllegalAccessException when the user isn't allowed to delete
   *           document
   */
  void deleteDocument(String documentPath, String documentId, boolean favorite, long delay, long authenticatedUserId) throws IllegalAccessException;

  /**
   * Undo delete a document (Cancel the delete action).
   *
   * @param documentId the Id of document
   * @param authenticatedUserId of the user acessing files
   */

  void undoDeleteDocument(String documentId, long authenticatedUserId);

  /**
   * Delete a list of documents.
   *
   * @param documents the list document
   * @param authenticatedUserId of the user acessing files
   */
  void deleteDocuments(int actionId, List<AbstractNode> documents, long authenticatedUserId) throws IllegalAccessException;

  /**
   * Download a list of documents.
   *
   * @param documents the list document
   * @param authenticatedUserId of the user accessing files
   */
  void downloadDocuments(int actionId, List<AbstractNode> documents, long authenticatedUserId) throws IllegalAccessException;

  void updatePermissions(String documentId,
                         NodePermission nodePermissionEntity,
                         long authenticatedUserId) throws IllegalAccessException;

  /**
   * Shares a document with given user or space
   *
   * @param documentId document id
   * @param destId target user or space identity id
   * @param authenticatedUserId current user Identity id
   * @throws IllegalAccessException
   */
  void shareDocument(String documentId, long destId, long authenticatedUserId, boolean broadcast) throws IllegalAccessException;

  void notifyMember(String documentId, long destId) throws IllegalAccessException;

  void unShareDocument(String documentId, long destId) throws IllegalStateException, RepositoryException;

  AbstractNode createFolder(long ownerId,String folderId, String folderPath, String name, long authenticatedUserId) throws IllegalAccessException, ObjectAlreadyExistsException, ObjectNotFoundException;

  String getNewName(long ownerId, String folderId, String folderPath, String name) throws IllegalAccessException, ObjectAlreadyExistsException, ObjectNotFoundException;

  void renameDocument(long ownerId, String documentID, String name, long authenticatedUserId) throws IllegalAccessException, ObjectAlreadyExistsException, ObjectNotFoundException;

  boolean canAccess(String documentID, org.exoplatform.services.security.Identity aclIdentity);

  org.exoplatform.services.security.Identity getAclUserIdentity(String userName) throws IllegalAccessException;
  
  default void updateDocumentDescription(long ownerId,
                                         String documentID,
                                         String description,
                                         long aclIdentity) throws IllegalStateException, IllegalAccessException, RepositoryException {
    throw new IllegalStateException("updateDocumentDescription method not implemented in the target class");
  }

  /**
   * Creates a shortcut for a document
   *
   * @param documentId     document id
   * @param destPath       destination path
   * @param aclIdentity    user identity id
   * @param conflictAction conflictAction
   * @throws IllegalAccessException
   * @throws ObjectAlreadyExistsException
   */
  void createShortcut(String documentId, String destPath, String aclIdentity, String conflictAction) throws IllegalAccessException, ObjectAlreadyExistsException;

  /**
   * Retrieves versions of specific file
   *
   * @param fileNodeId  target file node id
   * @param aclIdentity user identity id
   * @return {@link List} of {@link FileVersion}
   */
  List<FileVersion> getFileVersions(String fileNodeId, String aclIdentity);

  /**
   * update or add a version summary
   *
   * @param originFileId original file id
   * @param versionId version id
   * @param summary new summary to be saved
   * @param aclIdentity current user identity
   * @return {@link FileVersion}
   */
  FileVersion updateVersionSummary(String originFileId, String versionId, String summary, String aclIdentity);

  /**
   * restore document version
   *
   * @param versionId   version id
   * @param aclIdentity current user identity
   * @return {@link FileVersion}
   */
  FileVersion restoreVersion(String versionId, String aclIdentity);

  /**
   * verify if current user can add document
   *
   * @param spaceId space id
   * @param currentUserName current user name
   */
  boolean canAddDocument(String spaceId, String currentUserName);

  /**
   * Get the zip for download by action ID
   *
   * @param actionId action id
   * @param userName current user name
   */
  byte[] getDownloadZipBytes(int actionId, String userName) throws IOException;

  /**
   * Cancel any bulk action by action ID
   *
   * @param actionId action id
   * @param userName current user name
   */
  void cancelBulkAction(String actionId, String userName) throws IOException;

  /**
   * Creates a new version from an input stream
   *
   * @param nodeId target node id
   * @param aclIdentity current user identity id
   * @param newContent the new content to be set in the new version
   * @return {@link FileVersion}
   */
  FileVersion createNewVersion(String nodeId, String aclIdentity, InputStream newContent);

  /**
   * Move list of documents in bulk
   *
   * @param actionId       action id
   * @param ownerId        owner id
   * @param documents      list of documents to move
   * @param destPath       destination path
   * @param userIdentityId current user identity id
   */
  void moveDocuments(int actionId, long ownerId, List<AbstractNode>documents, String destPath, long userIdentityId) throws IllegalAccessException;


  /**
   * Check if the current user can import documents from zip
   *
   * @param identity Current user Identity
   */
  boolean canImport(org.exoplatform.services.security.Identity identity);

  /**
   * Get the total size of a space or user drive
   *
   * @param ownerId Id of the owner Identity
   * @return the document size object
   */
  DocumentsSize getDocumentsSizeStat(long ownerId, long userIdentityId) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Calculate total size of a space or user drive and store the result in new
   * analytics entry
   *
   * @param ownerId Id of the owner Identity
   * @param userIdentityId current user identity id
   * @return the document size object
   */
  DocumentsSize addDocumentsSizeStat(long ownerId, long userIdentityId) throws IllegalAccessException, ObjectNotFoundException;


  /**
   * Checks if user has edit permission on document
   *
   * @param nodeId document node id
   * @param userIdentityId user identity id
   * @return true if has edit permission or false
   * @throws IllegalAccessException
   */
  boolean hasEditPermissionOnDocument(String nodeId, long userIdentityId) throws IllegalAccessException;

  /**
   * @param nodeId {@link AbstractNode} technical identifier
   * @param aclIdentity User {@link org.exoplatform.services.security.Identity}
   * @return true if has edit permission, else false
   */
  boolean hasEditPermissionOnDocument(String nodeId,
                                      org.exoplatform.services.security.Identity aclIdentity);

  /**
   * Import list of documents from an uploaded zip
   *
   * @param ownerId owner id
   * @param folderId folder id
   * @param folderPath folder path
   * @param uploadId upload id
   * @param conflict conflict rule
   * @param identity current user identity
   * @param authenticatedUserId current user identity id
   */
  void importFiles(String ownerId,
                   String folderId,
                   String folderPath,
                   String uploadId,
                   String conflict,
                   org.exoplatform.services.security.Identity identity,
                   long authenticatedUserId) throws Exception;

  /**
   * Retrieves a list of trash elements that match the specified filter.
   *
   * @param trashElementNodeFilter the filter used to find trash elements
   * @return a list of nodes representing the trash elements
   * @throws RepositoryException if an error occurs while accessing the repository
   */
  List<TrashElementNode> getDeletedDocuments(TrashElementNodeFilter trashElementNodeFilter) throws RepositoryException;

  /**
   * Counts the total number of deleted documents in the trash.
   *
   * @return the total count of deleted documents
   */
  int countDeletedDocuments();

  /**
   * Restores a document from the trash based on the specified trash node path.
   *
   * @param trashNodePath the path of the trash node to restore
   * @throws RepositoryException if an error occurs during the restoration process
   */
  void restoreDocumentFromTrash(String trashNodePath) throws RepositoryException;

  /**
   * Delete a document from the trash based on the specified trash node path.
   *
   * @param documentPath the path of the trash node to delete
   * @throws ObjectNotFoundException if a document not exist in the trash
   * @throws RepositoryException if an error occurs during the delete process
   */
  void deleteDocumentPermanently(String documentPath) throws ObjectNotFoundException, RepositoryException;

  /**
   * Delete a list of documents from the trash.
   *
   * @param actionId the delete permanently action identifier
   * @param trashElementNodes the list of the trash nodes to delete
   * @param userIdentityId the user identity identifier
   */
  void deleteDocumentsPermanently(int actionId, List<AbstractNode> trashElementNodes, long userIdentityId) throws IllegalAccessException;

  /**
   * Restore a list of documents from the trash.
   *
   * @param actionId the restore action identifier
   * @param trashElementNodes the list of the trash nodes to restore
   * @param userIdentityId the user identity identifier
   */
  void restoreDocuments(int actionId, List<AbstractNode> trashElementNodes, long userIdentityId) throws IllegalAccessException;


  /**
   * Get image thumbnail content.
   *
   * @param fileType the file type
   * @param documentId the document Id
   * @param userName the user name
   * @param width the width of the thumbnail
   * @param height the height of the thumbnail
   */
  FileContent getImageThumbnailContent(String fileType, String documentId, String userName, int width, int height) throws Exception;

  /**
   * Create  thumbnail for given image.
   *
   * @param fileType the file type
   * @param fileContent the FileContent object
   * @param userName the user name
   * @param width the width of the thumbnail
   * @param height the height of the thumbnail
   */
  FileItem createThumbnail(String fileType, FileContent fileContent, String userName, int width, int height) throws Exception;

  /**
   * Set the document visibility.
   *
   * @param ownerId the owner Id
   * @param documentID theDocument Id
   * @param hidden the hidden value
   * @param authenticatedUserId userId
   */
  void setDocumentVisibility(long ownerId, String documentID, Boolean hidden, long authenticatedUserId)  throws Exception;

  /**
   * @param documentId Document identifier
   * @return {@link List} of linked category identifiers
   */
  default List<Long> getDocumentCategoryIds(String documentId) {
    throw new UnsupportedOperationException();
  }

  /**
   * @param spaceIdentityId Space {@link org.exoplatform.social.core.identity.model.Identity} Identifier
   * @return {@link List} of Category Ids used in Documents
   */
  default List<Long> getDocumentCategoryIds(long spaceIdentityId, String userName) {
    throw new UnsupportedOperationException();
  }
}
