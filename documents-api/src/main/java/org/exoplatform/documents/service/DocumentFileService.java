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
import org.exoplatform.documents.constant.FileListingType;
import org.exoplatform.documents.model.*;
import org.exoplatform.social.core.identity.model.Identity;

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
  List<AbstractNode> getDocumentItems(FileListingType listingType,
                                      DocumentNodeFilter filter,
                                      int offset,
                                      int limit,
                                      long userIdentityId,
                                      boolean showHiddenFiles) throws IllegalAccessException, ObjectNotFoundException;

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
   * Retrieves breadcrumb of the given node.
   *
   * @param folderId Id of the given folder
   * @param authenticatedUserId of the user acessing files
   * @return {@link List} of {@link FullTreeItem}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   */
  List<FullTreeItem> getFullTreeData(long ownerId,String folderId, long authenticatedUserId) throws IllegalAccessException, ObjectNotFoundException;

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
   * @throws IllegalAccessException
   */
  void shareDocument(String documentId, long destId) throws IllegalAccessException;

  void notifyMember(String documentId, long destId) throws IllegalAccessException;

  AbstractNode createFolder(long ownerId,String folderId, String folderPath, String name, long authenticatedUserId) throws IllegalAccessException, ObjectAlreadyExistsException, ObjectNotFoundException;

  String getNewName(long ownerId, String folderId, String folderPath, String name) throws IllegalAccessException, ObjectAlreadyExistsException, ObjectNotFoundException;

  void renameDocument(long ownerId, String documentID, String name, long authenticatedUserId) throws IllegalAccessException, ObjectAlreadyExistsException, ObjectNotFoundException;

  boolean canAccess(String documentID, org.exoplatform.services.security.Identity aclIdentity) throws RepositoryException;

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
  void cancelBulkAction(int actionId, String userName) throws IOException;

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
   * Get Stored default View for the current user
   *
   * @param ownerId Id of the owner Identity
   * @param userIdentityId user identity id
   * @return the stored view
   */
  String getDefaultView(Long ownerId, String userIdentityId);

  /**
   * Set default View for the current user
   *
   * @param ownerId Id of the owner Identity
   * @param userIdentityId user identity id
   * @param view the view to store
   */
  void setDefaultView(Long ownerId, String userIdentityId, String view);
  
  /**
   * Checks if user has edit permission on document
   *
   * @param nodeId document node id
   * @param userIdentityId user identity id
   * @return true if has edit permission or false
   * @throws IllegalAccessException
   */
  boolean hasEditPermissionOnDocument(String nodeId, long userIdentityId) throws IllegalAccessException;
}
