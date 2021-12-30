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

}
