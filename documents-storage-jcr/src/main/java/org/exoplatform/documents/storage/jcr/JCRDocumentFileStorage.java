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
package org.exoplatform.documents.storage.jcr;

import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.*;
import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.toFileNode;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.api.search.data.SearchResult;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.jcr.search.DocumentSearchServiceConnector;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.util.Text;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

public class JCRDocumentFileStorage implements DocumentFileStorage {

  private static final String                  COLLABORATION     = "collaboration";
  public final String                          PREFIX_CLONE      = "Copy of ";
  private final SpaceService                   spaceService;
  private final RepositoryService              repositoryService;
  private final IdentityManager                identityManager;
  private final NodeHierarchyCreator           nodeHierarchyCreator;
  private final DocumentSearchServiceConnector documentSearchServiceConnector;
  private final String                         DATE_FORMAT       = "yyyy-MM-dd";
  private final String                         SPACE_PATH_PREFIX = "/Groups/spaces/";
  private final SimpleDateFormat               formatter         = new SimpleDateFormat(DATE_FORMAT);

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
      if (StringUtils.isBlank(filter.getQuery()) && BooleanUtils.isNotTrue(filter.getFavorites())) {
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
                                                                                            filter,
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
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  @Override
  public DocumentGroupsSize getGroupDocumentsCount(DocumentTimelineFilter filter,
                                                   Identity aclIdentity) throws ObjectNotFoundException {
    String username = aclIdentity.getUserId();
    Long ownerId = filter.getOwnerId();
    org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
    if (ownerIdentity == null) {
      throw new ObjectNotFoundException("Owner Identity with id : " + ownerId + " isn't found");
    }
    SessionProvider sessionProvider = getUserSessionProvider(repositoryService, aclIdentity);
    DocumentGroupsSize documentGroupsSize = new DocumentGroupsSize();
    try {
      Node identityRootNode = getIdentityRootNode(spaceService, nodeHierarchyCreator, username, ownerIdentity, sessionProvider);
      if (identityRootNode == null) {
        return documentGroupsSize;
      }
      Session session = identityRootNode.getSession();
      String rootPath = identityRootNode.getPath();
      if (StringUtils.isBlank(filter.getQuery())) {
        String statement = getTimeLineGroupeSizeQueryStatement(rootPath, null, new Date());
        Query jcrQuery = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
        QueryResult queryResult = jcrQuery.execute();
        documentGroupsSize.setThisDay(queryResult.getRows().getSize());

        Calendar thisWeek = GregorianCalendar.getInstance();
        thisWeek.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);

        Calendar thisMonth = GregorianCalendar.getInstance();
        thisMonth.set(Calendar.DAY_OF_MONTH, 0);

        Calendar thisYear = GregorianCalendar.getInstance();
        thisYear.set(Calendar.DAY_OF_YEAR, 0);

        statement = getTimeLineGroupeSizeQueryStatement(rootPath, new Date(), thisWeek.getTime());
        jcrQuery = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
        queryResult = jcrQuery.execute();
        documentGroupsSize.setThisWeek(queryResult.getRows().getSize());

        statement = getTimeLineGroupeSizeQueryStatement(rootPath, thisWeek.getTime(), thisMonth.getTime());
        jcrQuery = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
        queryResult = jcrQuery.execute();
        documentGroupsSize.setThisMonth(queryResult.getRows().getSize());

        statement = getTimeLineGroupeSizeQueryStatement(rootPath, thisMonth.getTime(), thisYear.getTime());
        jcrQuery = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
        queryResult = jcrQuery.execute();
        documentGroupsSize.setThisYear(queryResult.getRows().getSize());

        statement = getTimeLineGroupeSizeQueryStatement(rootPath, thisYear.getTime(), null);
        jcrQuery = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
        queryResult = jcrQuery.execute();
        documentGroupsSize.setBeforeThisYear(queryResult.getRows().getSize());
        return documentGroupsSize;

      } else {
        return documentGroupsSize;
      }
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving User '" + username + "' parent node", e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  @Override
  public List<AbstractNode> getFolderChildNodes(DocumentFolderFilter filter,
                                                Identity aclIdentity,
                                                int offset,
                                                int limit) throws IllegalAccessException, ObjectNotFoundException {
    String username = aclIdentity.getUserId();
    String parentFolderId = filter.getParentFolderId();
    String folderPath = filter.getFolderPath();
    SessionProvider sessionProvider = null;
    try {
      Node parent = null;
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      sessionProvider = getUserSessionProvider(repositoryService, aclIdentity);
      Session session = sessionProvider.getSession(COLLABORATION, manageableRepository);
      if (StringUtils.isBlank(parentFolderId)) {
        Long ownerId = filter.getOwnerId();
        org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
        parent = getIdentityRootNode(spaceService, nodeHierarchyCreator, username, ownerIdentity, sessionProvider);
        parentFolderId = ((NodeImpl) parent).getIdentifier();
      } else {
        parent = getNodeByIdentifier(session, parentFolderId);
      }
      if (StringUtils.isNotBlank(folderPath)) {
        try {
          parent = parent.getNode(java.net.URLDecoder.decode(folderPath, StandardCharsets.UTF_8.name()));
        } catch (RepositoryException repositoryException) {
          throw new ObjectNotFoundException("Folder with path : " + folderPath + " isn't found");
        }
      }
      if (parent != null) {
        if (StringUtils.isBlank(filter.getQuery()) && BooleanUtils.isNotTrue(filter.getFavorites())) {
          NodeIterator nodeIterator = parent.getNodes();
          return toNodes(identityManager, nodeIterator, aclIdentity, offset, limit);
        } else {
          String workspace = session.getWorkspace().getName();
          String sortField = getSortField(filter, false);
          String sortDirection = getSortDirection(filter);
          Collection<SearchResult> filesSearchList = documentSearchServiceConnector.appSearch(aclIdentity,
                                                                                              workspace,
                                                                                              parent.getPath(),
                                                                                              filter,
                                                                                              offset,
                                                                                              limit,
                                                                                              sortField,
                                                                                              sortDirection);
          return filesSearchList.stream()
                                .map(result -> toFileNode(identityManager, session, aclIdentity, result))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
        }

      } else {
        throw new ObjectNotFoundException("Folder with Id : " + parentFolderId + " isn't found");
      }
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving User '" + username + "' parent node", e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  @Override
  public List<BreadCrumbItem> getBreadcrumb(long ownerId,
                                            String folderId,
                                            String folderPath,
                                            Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    String username = aclIdentity.getUserId();
    SessionProvider sessionProvider = null;
    List<BreadCrumbItem> parents = new ArrayList<>();
    try {
      Node node = null;
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      sessionProvider = getUserSessionProvider(repositoryService, aclIdentity);
      Session session = sessionProvider.getSession(COLLABORATION, manageableRepository);
      if (StringUtils.isBlank(folderId) && ownerId > 0) {
        org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
        node = getIdentityRootNode(spaceService, nodeHierarchyCreator, username, ownerIdentity, sessionProvider);
        folderId = ((NodeImpl) node).getIdentifier();
      } else {
        node = getNodeByIdentifier(session, folderId);
      }
      if (StringUtils.isNotBlank(folderPath)) {
        try {
          node = node.getNode(java.net.URLDecoder.decode(folderPath, StandardCharsets.UTF_8.name()));
        } catch (RepositoryException repositoryException) {
          throw new ObjectNotFoundException("Folder with path : " + folderPath + " isn't found");
        }
      }
      String homePath = "";
      if (node != null) {
        String nodeName = node.hasProperty(NodeTypeConstants.EXO_NAME) ? node.getProperty(NodeTypeConstants.EXO_NAME).getString() : node.getName();
        parents.add(new BreadCrumbItem(((NodeImpl) node).getIdentifier(), nodeName, node.getPath()));
        if (node.getPath().contains(SPACE_PATH_PREFIX)) {
          String[] pathParts = node.getPath().split(SPACE_PATH_PREFIX)[1].split("/");
          homePath = SPACE_PATH_PREFIX + pathParts[0] + "/" + pathParts[1];
        }
        while (node != null && !node.getPath().equals(homePath)) {
          try {
            node = node.getParent();
            if (node != null) {
              nodeName = node.hasProperty(NodeTypeConstants.EXO_NAME) ? node.getProperty(NodeTypeConstants.EXO_NAME).getString() : node.getName();
              parents.add(new BreadCrumbItem(((NodeImpl) node).getIdentifier(), nodeName, node.getPath()));
            }
          } catch (RepositoryException repositoryException) {
            node = null;
          }
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving folder'" + folderId + "' breadcrumb", e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
    return parents;
  }

  @Override
  public void createFolder(long ownerId, String folderId, String folderPath, String title, Identity aclIdentity) throws IllegalAccessException, ObjectAlreadyExistsException,
                                                                               ObjectNotFoundException {
    String username = aclIdentity.getUserId();
    SessionProvider sessionProvider = null;
    try {
      Node node = null;
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      sessionProvider = getUserSessionProvider(repositoryService, aclIdentity);
      Session session = sessionProvider.getSession(COLLABORATION, manageableRepository);
      if (StringUtils.isBlank(folderId) && ownerId > 0) {
        org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
        node = getIdentityRootNode(spaceService, nodeHierarchyCreator, username, ownerIdentity, sessionProvider);
        folderId = ((NodeImpl) node).getIdentifier();
      } else {
        node = getNodeByIdentifier(session, folderId);
      }
      if(StringUtils.isNotBlank(folderPath)){
        try {
          node = node.getNode(java.net.URLDecoder.decode(folderPath, StandardCharsets.UTF_8.name()));
        } catch (RepositoryException repositoryException) {
          throw new ObjectNotFoundException("Folder with path : " + folderPath + " isn't found");
        }
      }
      String name = Text.escapeIllegalJcrChars(JCRDocumentsUtil.cleanString(title));
      if (node.hasNode(name)) {
        throw new ObjectAlreadyExistsException("Folder'" + title + "' already exist") ;
      }
      Node addedNode = node.addNode(name, NodeTypeConstants.NT_FOLDER);
      addedNode.setProperty(NodeTypeConstants.EXO_TITLE, title);
      node.save();
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving folder'" + folderId + "' breadcrumb", e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  @Override
  public void renameDocument(long ownerId, String documentID, String title, Identity aclIdentity) throws IllegalAccessException, ObjectAlreadyExistsException,
          ObjectNotFoundException {
    String username = aclIdentity.getUserId();
    SessionProvider sessionProvider = null;
    try {
      Node node = null;
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      sessionProvider = getUserSessionProvider(repositoryService, aclIdentity);
      Session session = sessionProvider.getSession(COLLABORATION, manageableRepository);
      if (StringUtils.isBlank(documentID) && ownerId > 0) {
        org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
        node = getIdentityRootNode(spaceService, nodeHierarchyCreator, username, ownerIdentity, sessionProvider);
        documentID = ((NodeImpl) node).getIdentifier();
      } else {
        node = getNodeByIdentifier(session, documentID);
      }
      String name = Text.escapeIllegalJcrChars(JCRDocumentsUtil.cleanString(title));
      String oldName = node.getName();
      if (oldName.indexOf('.') != -1 && node.isNodeType(NodeTypeConstants.NT_FILE)) {
        String ext = oldName.substring(oldName.lastIndexOf('.'));
        title = title.concat(ext);
      }

      if (node.hasNode(name)) {
        throw new ObjectAlreadyExistsException("Document'" + title + "' already exist") ;
      }
      if (node.canAddMixin(NodeTypeConstants.EXO_MODIFY)) {
        node.addMixin(NodeTypeConstants.EXO_MODIFY);
      }
      Calendar now = Calendar.getInstance();
      node.setProperty(NodeTypeConstants.EXO_DATE_MODIFIED, now);
      node.setProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE, now);
      node.setProperty(NodeTypeConstants.EXO_LAST_MODIFIER, username);

      // Update exo:name
      if(node.canAddMixin(NodeTypeConstants.EXO_SORTABLE)) {
        node.addMixin(NodeTypeConstants.EXO_SORTABLE);
      }
      if (!node.hasProperty(NodeTypeConstants.EXO_TITLE)) {
        node.addMixin(NodeTypeConstants.EXO_RSS_ENABLE);
      }
      node.setProperty(NodeTypeConstants.EXO_TITLE, title);
      node.setProperty(NodeTypeConstants.EXO_NAME, title);
      node.save();
    } catch (Exception e) {
      throw new IllegalStateException("Error renaming document'" + documentID, e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  @Override
  public AbstractNode duplicateDocument(long ownerId, String fileId, Identity aclIdentity) throws IllegalAccessException,
                                                                                           ObjectNotFoundException {
    String username = aclIdentity.getUserId();
    SessionProvider sessionProvider = null;
    Node newNode = null;
    try {
      Node oldNode = null;
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      sessionProvider = getUserSessionProvider(repositoryService, aclIdentity);
      Session session = sessionProvider.getSession(COLLABORATION, manageableRepository);
      if (StringUtils.isBlank(fileId) && ownerId > 0) {
        org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
        oldNode = getIdentityRootNode(spaceService, nodeHierarchyCreator, username, ownerIdentity, sessionProvider);
        fileId = ((NodeImpl) oldNode).getIdentifier();
      } else {
        oldNode = getNodeByIdentifier(session, fileId);
      }
      Node parentNode = oldNode.getParent();

      if (oldNode != null) {
          duplicateItem(oldNode, parentNode, parentNode);
        parentNode.save();
      }

      return toFileNode(identityManager, aclIdentity, parentNode);
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving duplicate file'" + fileId, e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }

  }

  private void duplicateItem(Node oldNode, Node destinationNode, Node parentNode) throws Exception{
    if (oldNode.getProperty(NodeTypeConstants.JCR_PRIMARY_TYPE).getString().equals(NodeTypeConstants.EXO_THUMBNAILS_FOLDER)){
      return;
    }
    Node newNode = null;
    String name = oldNode.getName();
    String title = oldNode.getProperty(NodeTypeConstants.EXO_TITLE).getString();
    if (((NodeImpl) destinationNode).getIdentifier().equals(((NodeImpl) parentNode).getIdentifier())){
      name = PREFIX_CLONE + name;
      title = PREFIX_CLONE + title;
      String newName = name;
      int i =0;
      while((destinationNode.hasNode(newName))){
        i++;
        newName = name + " (" + i + ")";
      }
      name = newName.toLowerCase();
      if(i>0){
        title = title + " (" + i + ")";
      }
    }
    if (oldNode.getProperty(NodeTypeConstants.JCR_PRIMARY_TYPE).getString().equals(NodeTypeConstants.NT_FOLDER)) {
      newNode = destinationNode.addNode(name, NodeTypeConstants.NT_FOLDER);
      newNode.setProperty(NodeTypeConstants.EXO_TITLE, title);
      NodeIterator nodeIterator = oldNode.getNodes();
      while (nodeIterator.hasNext()) {
        Node node = nodeIterator.nextNode();
        duplicateItem(node, newNode, parentNode);
      }
    } else {
      newNode = destinationNode.addNode(name, oldNode.getProperty(NodeTypeConstants.JCR_PRIMARY_TYPE).getString());

      if (oldNode.isNodeType(NodeTypeConstants.MIX_VERSIONABLE) && !newNode.isNodeType(NodeTypeConstants.MIX_VERSIONABLE))
        newNode.addMixin(NodeTypeConstants.MIX_VERSIONABLE);

      if (oldNode.isNodeType(NodeTypeConstants.MIX_REFERENCEABLE) && !newNode.isNodeType(NodeTypeConstants.MIX_REFERENCEABLE))
        newNode.addMixin(NodeTypeConstants.MIX_REFERENCEABLE);

      if (oldNode.isNodeType(NodeTypeConstants.MIX_COMMENTABLE) && !newNode.isNodeType(NodeTypeConstants.MIX_COMMENTABLE))
        newNode.addMixin(NodeTypeConstants.MIX_COMMENTABLE);

      if (oldNode.isNodeType(NodeTypeConstants.MIX_VOTABLE) && !newNode.isNodeType(NodeTypeConstants.MIX_VOTABLE))
        newNode.addMixin(NodeTypeConstants.MIX_VOTABLE);

      if (oldNode.isNodeType(NodeTypeConstants.MIX_I18N) && !newNode.isNodeType(NodeTypeConstants.MIX_I18N))
        newNode.addMixin(NodeTypeConstants.MIX_I18N);

      newNode.setProperty(NodeTypeConstants.EXO_TITLE, title);
      if(oldNode.hasNode(NodeTypeConstants.JCR_CONTENT)){
        Node resourceNode = newNode.addNode(NodeTypeConstants.JCR_CONTENT, NodeTypeConstants.NT_RESOURCE);

        Calendar now = Calendar.getInstance();
        resourceNode.setProperty(NodeTypeConstants.JCR_LAST_MODIFIED, now);
        resourceNode.setProperty(NodeTypeConstants.JCR_DATA,
                oldNode.getNode(NodeTypeConstants.JCR_CONTENT)
                        .getProperty(NodeTypeConstants.JCR_DATA)
                        .getStream());
        resourceNode.setProperty(NodeTypeConstants.JCR_MIME_TYPE,
                oldNode.getNode(NodeTypeConstants.JCR_CONTENT)
                        .getProperty(NodeTypeConstants.JCR_MIME_TYPE)
                        .getString());
        resourceNode.setProperty(NodeTypeConstants.EXO_DATE_MODIFIED, now);
      }
    }
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

  private String getTimeLineGroupeSizeQueryStatement(String rootPath, Date before, Date after) {
    StringBuilder sb = new StringBuilder().append("SELECT * FROM ")
                                          .append(NodeTypeConstants.NT_FILE)
                                          .append(" WHERE jcr:path LIKE '")
                                          .append(rootPath)
                                          .append("/%'");
    if (before != null) {
      sb.append(" AND (")
        .append(NodeTypeConstants.EXO_DATE_MODIFIED)
        .append(" < TIMESTAMP '")
        .append(formatter.format(before))
        .append("T00:00:00.000')");
    }
    if (after != null) {
      sb.append(" AND (")
        .append(NodeTypeConstants.EXO_DATE_MODIFIED)
        .append(" > TIMESTAMP '")
        .append(formatter.format(after))
        .append("T00:00:00.000')");
    }

    return sb.toString();
  }

}
