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

import java.util.List;

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
                                      long userIdentityId) throws IllegalAccessException, ObjectNotFoundException;

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
  AbstractNode duplicateDocument(long ownerId,String fileId, long authenticatedUserId) throws IllegalAccessException, ObjectNotFoundException;

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

  void createFolder(long ownerId,String folderId, String folderPath, String name, long authenticatedUserId) throws IllegalAccessException, ObjectAlreadyExistsException, ObjectNotFoundException;

  void renameDocument(long ownerId,String documentID, String name, long authenticatedUserId) throws IllegalAccessException, ObjectAlreadyExistsException, ObjectNotFoundException;
}
