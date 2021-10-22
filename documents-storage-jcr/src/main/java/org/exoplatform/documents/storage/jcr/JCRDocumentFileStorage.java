/*
 * Copyright (C) 2003-2021 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.documents.storage.jcr;

import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.*;

import java.util.*;
import java.util.stream.Collectors;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.api.search.data.SearchResult;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.jcr.search.DocumentSearchServiceConnector;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

public class JCRDocumentFileStorage implements DocumentFileStorage {

  private SpaceService                   spaceService;

  private RepositoryService              repositoryService;

  private IdentityManager                identityManager;

  private NodeHierarchyCreator           nodeHierarchyCreator;

  private DocumentSearchServiceConnector documentSearchServiceConnector;

  public JCRDocumentFileStorage(NodeHierarchyCreator nodeHierarchyCreator,
                                RepositoryService repositoryService,
                                DocumentSearchServiceConnector documentSearchServiceConnector,
                                IdentityManager identityManager,
                                SpaceService spaceService) {
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.repositoryService = repositoryService;
    this.nodeHierarchyCreator = nodeHierarchyCreator;
    this.documentSearchServiceConnector = documentSearchServiceConnector;
  }

  @Override
  public List<FileNode> getFilesTimeline(DocumentTimelineFilter filter,
                                         Identity aclIdentity,
                                         int offset,
                                         int limit) throws ObjectNotFoundException {
    String username = aclIdentity.getUserId();
    Long ownerId = filter.getOwnerId();
    org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
    if (ownerIdentity == null) {
      throw new ObjectNotFoundException("Owner Identity with id : " + ownerId + " isn't found");
    }
    SessionProvider sessionProvider = getUserSessionProvider(repositoryService, aclIdentity);
    try {
      Node identityRootNode = getIdentityRootNode(spaceService, nodeHierarchyCreator, username, ownerIdentity, sessionProvider);
      if (identityRootNode == null) {
        return Collections.emptyList();
      }

      Session session = identityRootNode.getSession();
      String rootPath = identityRootNode.getPath();
      if (StringUtils.isBlank(filter.getQuery())) {
        String sortField = getSortField(filter, true);
        String sortDirection = getSortDirection(filter);

        String statement = getTimeLineQueryStatement(rootPath, sortField, sortDirection);
        Query jcrQuery = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
        QueryResult queryResult = jcrQuery.execute();
        NodeIterator nodeIterator = queryResult.getNodes();
        return toFileNodes(identityManager, nodeIterator, aclIdentity, offset, limit);
      } else {
        String workspace = session.getWorkspace().getName();
        String sortField = getSortField(filter, false);
        String sortDirection = getSortDirection(filter);
        Collection<SearchResult> filesSearchList = documentSearchServiceConnector.appSearch(aclIdentity,
                                                                                            workspace,
                                                                                            rootPath,
                                                                                            filter.getQuery(),
                                                                                            offset,
                                                                                            limit,
                                                                                            sortField,
                                                                                            sortDirection);
        return filesSearchList.stream()
                              .map(result -> toFileNode(identityManager, session, aclIdentity, result))
                              .filter(Objects::nonNull)
                              .collect(Collectors.toList());
      }
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving User '" + username + "' parent node", e);
    } finally {
      sessionProvider.close();
    }
  }

  @Override
  public List<AbstractNode> getFolderChildNodes(DocumentFolderFilter filter,
                                                Identity aclIdentity,
                                                int offset,
                                                int limit) throws IllegalAccessException, ObjectNotFoundException {
    // TODO
    return null;
  }

  private String getTimeLineQueryStatement(String rootPath, String sortField, String sortDirection) {
    return new StringBuilder().append("SELECT * FROM ")
                              .append(NodeTypeConstants.NT_FILE)
                              .append(" WHERE jcr:path LIKE '")
                              .append(rootPath)
                              .append("/%' ORDER BY ")
                              .append(sortField)
                              .append(" ")
                              .append(sortDirection)
                              .toString();
  }

}
