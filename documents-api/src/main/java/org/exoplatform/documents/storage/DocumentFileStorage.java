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

import java.util.List;

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.*;
import org.exoplatform.services.security.Identity;
import javax.jcr.RepositoryException;

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
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
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
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
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
   * @param fileId Id of the given file
   * @param aclIdentity {@link Identity} of the user acessing files
   * @throws IllegalAccessException when the user isn't allowed to access
   *           documents of the designated parentFolderId
   * @throws ObjectNotFoundException when folderId doesn't exisits
   */
  void moveDocument(long ownerId, String fileId, String destPath, Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException;

  void createFolder(long ownerId, String folderId, String folderPath, String title, Identity aclIdentity) throws IllegalAccessException,  ObjectAlreadyExistsException,
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
                                         Identity aclIdentity) throws IllegalStateException {
    throw new IllegalStateException("updateDocumentDescription not implemented in the target classs");
  }

  /**
   * Creates a shortcut for a document
   *
   * @param documentId document id
   * @param destPath destination path
   * @throws IllegalAccessException
   */
  void createShortcut(String documentId, String destPath) throws IllegalAccessException;
}
