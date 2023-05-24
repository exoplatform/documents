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
package org.exoplatform.documents.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.*;
import org.exoplatform.services.security.Identity;

public interface DocumentFileStorage {

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
   * @param aclIdentity {@link Identity} of the user acessing files
   * @return {@link List} of {@link AbstractNode}
   * @throws ObjectNotFoundException when parentFolderId doesn't exisits
   */
  List<FileNode> getFilesTimeline(DocumentTimelineFilter filter,
                                  Identity aclIdentity,
                                  int offset,
                                  int limit) throws ObjectNotFoundException;


  /**
   * Retrieves the number of existing files by group.
   *
   * @param filter {@link DocumentFolderFilter} that contains filtering criteria
   * @param aclIdentity {@link Identity} of the user acessing files
   * @return {@link DocumentGroupsSize}
   * @throws ObjectNotFoundException when parentFolderId doesn't exisits
   */

  DocumentGroupsSize getGroupDocumentsCount(DocumentTimelineFilter filter, Identity aclIdentity) throws ObjectNotFoundException;

  /**
   * Retrieves a list of accessible files, for a selected user, by applying the
   * designated filter. The parentForlderId of filter object will be used to
   * select the list of Nodes to retrieve.
   * 
   * @param filter {@link DocumentFolderFilter} that contains filtering criteria
   * @param offset Offset of the result list
   * @param limit Limit of the result list
   * @param aclIdentity {@link Identity} of the user acessing files
   * @return {@link List} of {@link AbstractNode}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when parentFolderId doesn't exisits
   */
  List<AbstractNode> getFolderChildNodes(DocumentFolderFilter filter,
                                         Identity aclIdentity,
                                         int offset,
                                         int limit) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Retrieves breadcrumb of the given node.
   *
   * @param folderId Id of the given folder
   * @param aclIdentity {@link Identity} of the user acessing files
   * @return {@link List} of {@link AbstractNode}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   */
  List<BreadCrumbItem> getBreadcrumb(long ownerId, String folderId, String folderPath, Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Retrieves breadcrumb of the given node.
   *
   * @param folderId Id of the given folder
   * @param aclIdentity {@link Identity} of the user acessing files
   * @return {@link List} of {@link AbstractNode}
   */
  List<FullTreeItem> getFullTreeData(long ownerId, String folderId, Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Duplicate the given node.
   *
   * @param fileId Id of the given file
   * @param aclIdentity {@link Identity} of the user acessing files
   * @return  {@link AbstractNode}
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   */
  AbstractNode duplicateDocument(long ownerId, String fileId, String prefixClone, Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Move the given node.
   *
   * @param session current jcr session
   * @param fileId Id of the given file
   * @param aclIdentity {@link Identity} of the user acessing files
   * @param conflictAction conflict action
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   * @throws ObjectAlreadyExistsException when file with same name already exists
   *           in the target path
   */
  void moveDocument(Session session,
                    long ownerId,
                    String fileId,
                    String destPath,
                    Identity aclIdentity,
                    String conflictAction) throws Exception;

  /**
   * Move the given node.
   *
   * @param fileId Id of the given file
   * @param aclIdentity {@link Identity} of the user acessing files
   * @param conflictAction conflict action
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   * @throws ObjectAlreadyExistsException when file with same name already exists
   *           in the target path
   */
  void moveDocument(long ownerId, String fileId, String destPath, Identity aclIdentity, String conflictAction) throws IllegalAccessException, ObjectNotFoundException, ObjectAlreadyExistsException;

  AbstractNode createFolder(long ownerId, String folderId, String folderPath, String title, Identity aclIdentity) throws IllegalAccessException,  ObjectAlreadyExistsException,
                                                                               ObjectNotFoundException;

  String getNewName(long ownerId,
                    String folderId,
                    String folderPath,
                    String title) throws IllegalAccessException,
                                                 ObjectAlreadyExistsException,
                                                 ObjectNotFoundException;

  void renameDocument(long ownerId, String documentID, String title, Identity aclIdentity) throws IllegalAccessException,  ObjectAlreadyExistsException,
                                                                               ObjectNotFoundException;
  void updatePermissions(String documentID, NodePermission nodePermissionEntity, Identity aclIdentity);

  void downloadDocuments(int actionId, List<AbstractNode> documents, Identity identity, long authenticatedUserId);

  /**
   * Shares a document with given user or space
   *
   * @param documentId document id
   * @param destId target user or space identity id
   * @throws IllegalAccessException
   */
  void shareDocument(String documentId, long destId) throws IllegalAccessException;

  void notifyMember(String documentId, long destId) throws IllegalAccessException;

  boolean canAccess(String documentID, Identity aclIdentity) throws RepositoryException;
  
  default void updateDocumentDescription(long ownerId,
                                         String documentID,
                                         String description,
                                         Identity aclIdentity) throws IllegalStateException, RepositoryException {
    throw new IllegalStateException("updateDocumentDescription not implemented in the target classs");
  }

  /**
   * Creates a shortcut for a document
   *
   * @param documentId     document id
   * @param destPath       destination path
   * @param aclIdentity    user identity id
   * @param conflictAction conflict action
   * @throws IllegalAccessException
   * @throws ObjectAlreadyExistsException
   */
  void createShortcut(String documentId, String destPath, String aclIdentity, String conflictAction) throws IllegalAccessException, ObjectAlreadyExistsException;

  /**
   * Retrieves versions of specific file
   *
   * @param fileNodeId target file node id
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
   * @param userIdentityId current user acl identity id
   * @param identityId     current user identity id
   */
  void moveDocuments(int actionId, long ownerId, List<AbstractNode> documents, String destPath, Identity userIdentityId, long identityId);

  /**
   * Checks if user has edit permission on document
   *
   * @param nodeId document node id
   * @param aclUserIdentity user identity id
   * @return true if has edit permission or false
   */
  boolean hasEditPermissions(String nodeId, Identity aclUserIdentity);

  /**
   * Gets a download item of a given document
   *
   * @param documentId document id
   * @return {@link DownloadItem}
   */
  DownloadItem getDocumentDownloadItem(String documentId);

  /**
   * Download a zipped folder
   *
   * @param folderId  folder node id
   * @return downloaded zip path
   */
  String downloadFolder(String folderId);
}
