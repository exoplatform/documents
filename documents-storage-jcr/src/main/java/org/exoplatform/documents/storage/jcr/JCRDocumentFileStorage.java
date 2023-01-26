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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.jcr.version.Version;
import javax.jcr.version.VersionIterator;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.api.search.data.SearchResult;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.DocumentFileStorage;
import org.exoplatform.documents.storage.jcr.search.DocumentSearchServiceConnector;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.documents.storage.jcr.util.Utils;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.ext.utils.VersionHistoryUtils;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.exoplatform.services.jcr.util.Text;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.tag.TagService;
import org.exoplatform.social.metadata.tag.model.TagName;
import org.exoplatform.social.metadata.tag.model.TagObject;

public class JCRDocumentFileStorage implements DocumentFileStorage {

  private static final String                  COLLABORATION              = "collaboration";

  private final SpaceService                   spaceService;

  private final RepositoryService              repositoryService;

  private final IdentityManager                identityManager;

  private final NodeHierarchyCreator           nodeHierarchyCreator;

  private final DocumentSearchServiceConnector documentSearchServiceConnector;

  private final ListenerService                listenerService;

  private final IdentityRegistry               identityRegistry;

  private final ActivityManager                activityManager;

  private final String                         DATE_FORMAT                = "yyyy-MM-dd";

  private final String                         SPACE_PATH_PREFIX          = "/Groups/spaces/";

  private final SimpleDateFormat               formatter                  = new SimpleDateFormat(DATE_FORMAT);

  private static final String                  GROUP_ADMINISTRATORS       = "*:/platform/administrators";

  private static final String                  SPACE_PROVIDER_ID          = "space";

  private static final String                  SHARED_FOLDER_NAME         = "Shared";

  private static final String                  EOO_COMMENT_ID             = "eoo:commentId";

  private static final String                  ADD_TAG_DOCUMENT             = "add_tag_document";

  private static final String                  KEEP_BOTH                    = "keepBoth";

  private static final String                 CREATE_NEW_VERSION            = "createNewVersion";
  private static Map<Long, List<SymlinkNavigation>> symlinksNavHistory   = new HashMap<>();

  public JCRDocumentFileStorage(NodeHierarchyCreator nodeHierarchyCreator,
                                RepositoryService repositoryService,
                                DocumentSearchServiceConnector documentSearchServiceConnector,
                                IdentityManager identityManager,
                                SpaceService spaceService,
                                ListenerService listenerService,
                                IdentityRegistry identityRegistry,
                                ActivityManager activityManager) {
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.repositoryService = repositoryService;
    this.nodeHierarchyCreator = nodeHierarchyCreator;
    this.documentSearchServiceConnector = documentSearchServiceConnector;
    this.listenerService = listenerService;
    this.identityRegistry = identityRegistry;
    this.activityManager = activityManager;
  }

  @Override
  public List<FileNode> getFilesTimeline(DocumentTimelineFilter filter,
                                         Identity aclIdentity,
                                         int offset,
                                         int limit) throws ObjectNotFoundException {
    List<FileNode> files = null;
    String username = aclIdentity.getUserId();
    Long ownerId = filter.getOwnerId();
    boolean showHiddenFiles = filter.isIncludeHiddenFiles();
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
      if (ownerIdentity.isUser()) {
        rootPath = rootPath.split("/" + USER_PRIVATE_ROOT_NODE)[0];
      }
      if (StringUtils.isBlank(filter.getQuery()) && BooleanUtils.isNotTrue(filter.getFavorites())) {
        String sortField = getSortField(filter, true);
        String sortDirection = getSortDirection(filter);
        String statement = getTimeLineQueryStatement(rootPath, sortField, sortDirection);
        Query jcrQuery = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
        ((QueryImpl)jcrQuery).setOffset(offset);
        ((QueryImpl)jcrQuery).setLimit(limit);
        QueryResult queryResult = jcrQuery.execute();
        NodeIterator nodeIterator = queryResult.getNodes();
        files = toFileNodes(identityManager, nodeIterator, aclIdentity, session, spaceService,showHiddenFiles);
        return files;
      } else {
        String workspace = session.getWorkspace().getName();
        String sortField = getSortField(filter, false);
        String sortDirection = getSortDirection(filter);

        Collection<SearchResult> filesSearchList =
                                                 documentSearchServiceConnector.search(aclIdentity,
                                                                                            workspace,
                                                                                            rootPath,
                                                                                            filter,
                                                                                            offset,
                                                                                            limit,
                                                                                            sortField,
                                                                                            sortDirection);
        return filesSearchList.stream()
                              .map(result -> toFileNode(identityManager, session, aclIdentity, result, spaceService))
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
    boolean includeHiddenFiles = filter.isIncludeHiddenFiles();
    SessionProvider sessionProvider = null;
    try {
      Node parent = null;
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      sessionProvider = getUserSessionProvider(repositoryService, aclIdentity);
      Session session = sessionProvider.getSession(COLLABORATION, manageableRepository);
      if (StringUtils.isBlank(parentFolderId)) {
        Long ownerId = filter.getOwnerId();
        String userId = filter.getUserId();
        org.exoplatform.social.core.identity.model.Identity ownerIdentity = null;
        if(StringUtils.isNotEmpty(filter.getUserId())){
          ownerIdentity = identityManager.getOrCreateUserIdentity(userId);
        } else{
          ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
        }
        parent = getIdentityRootNode(spaceService, nodeHierarchyCreator, username, ownerIdentity, sessionProvider);
        parentFolderId = ((NodeImpl) parent).getIdentifier();
      } else {
        parent = getNodeByIdentifier(session, parentFolderId);
        if (parent.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
          String sourceNodeId = parent.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
          parent = getNodeByIdentifier(session, sourceNodeId);
        }
        if (filter.getSymlinkId() != null && !filter.getSymlinkId().isEmpty()) {
          List<SymlinkNavigation> history = symlinksNavHistory.get(filter.getOwnerId());
          SymlinkNavigation newEntry = new SymlinkNavigation(filter.getSymlinkId(), parentFolderId);
          if (history == null) {
            history = new ArrayList<>();
            history.add(newEntry);
          } else {
            if (!history.contains(newEntry)) {
              history.add(newEntry);
            }
          }
          symlinksNavHistory.put(filter.getOwnerId(), history);
        }
      }
      if (StringUtils.isNotBlank(folderPath)) {
        parent = getNodeByPath(parent, folderPath, sessionProvider);
        if (parent != null && parent.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
          String sourceNodeId = parent.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
          parent = getNodeByIdentifier(session, sourceNodeId);
        }
      }
      if (parent != null) {
        if (StringUtils.isBlank(filter.getQuery()) && BooleanUtils.isNotTrue(filter.getFavorites())) {
          String sortField = getSortField(filter, true);
          String sortDirection = getSortDirection(filter);
          String statement = getFolderDocumentsQuery(parent.getPath(), sortField, sortDirection, includeHiddenFiles);
          Query jcrQuery = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
          ((QueryImpl)jcrQuery).setOffset(offset);
          ((QueryImpl)jcrQuery).setLimit(limit);
          QueryResult queryResult = jcrQuery.execute();
          NodeIterator nodeIterator = queryResult.getNodes();
          return toNodes(identityManager, session, nodeIterator, aclIdentity, spaceService, includeHiddenFiles);
        } else {
          String workspace = session.getWorkspace().getName();
          String sortField = getSortField(filter, false);
          String sortDirection = getSortDirection(filter);
          Collection<SearchResult> filesSearchList =
                                                   documentSearchServiceConnector.search(aclIdentity,
                                                                                              workspace,
                                                                                              parent.getPath(),
                                                                                              filter,
                                                                                              offset,
                                                                                              limit,
                                                                                              sortField,
                                                                                              sortDirection);
          return filesSearchList.stream()
                                .map(result -> toFileNode(identityManager, session, aclIdentity, result, spaceService))
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
      org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
      if (StringUtils.isBlank(folderId) && ownerId > 0) {
        node = getIdentityRootNode(spaceService, nodeHierarchyCreator, username, ownerIdentity, sessionProvider);
        folderId = ((NodeImpl) node).getIdentifier();
      } else {
        node = getNodeByIdentifier(session, folderId);
        node = checkSymlinkHistory(node, session, ownerId);
      }
      if (StringUtils.isNotBlank(folderPath)) {
        node = getNodeByPath(node, folderPath, sessionProvider);
      }
      String homePath = "";
      if (node != null) {
        String nodeName= node.hasProperty(NodeTypeConstants.EXO_TITLE) ? node.getProperty(NodeTypeConstants.EXO_TITLE).getString() : node.getName();
        parents.add(new BreadCrumbItem(((NodeImpl) node).getIdentifier(),
                                       nodeName,
                                       node.getPath(),
                                       node.isNodeType(NodeTypeConstants.EXO_SYMLINK)));
        if (node.getPath().contains(SPACE_PATH_PREFIX)) {
          String[] pathParts = node.getPath().split(SPACE_PATH_PREFIX)[1].split("/");
          homePath = SPACE_PATH_PREFIX + pathParts[0] + "/" + pathParts[1];
        }
        String userPrivatePathPrefix = username+"/"+USER_PRIVATE_ROOT_NODE;
        String userPublicPathPrefix = username+"/"+USER_PUBLIC_ROOT_NODE;
        if (node.getPath().contains(userPrivatePathPrefix)) {
          homePath = node.getPath().substring(0,node.getPath().lastIndexOf(userPrivatePathPrefix)+userPrivatePathPrefix.length());
        }
        if (node.getPath().contains(userPublicPathPrefix)) {
          homePath = node.getPath().substring(0,node.getPath().lastIndexOf(userPublicPathPrefix)+userPublicPathPrefix.length());
        }
        while (node != null && (!node.getPath().equals(homePath) || node.getName().equals(USER_PUBLIC_ROOT_NODE))) {
          try {
            if(node.getName().equals(USER_PUBLIC_ROOT_NODE)){
              node = getIdentityRootNode(spaceService, nodeHierarchyCreator, username, ownerIdentity, sessionProvider);
              if (node != null) {
                nodeName= node.hasProperty(NodeTypeConstants.EXO_TITLE) ? node.getProperty(NodeTypeConstants.EXO_TITLE).getString() : node.getName();
                parents.add(new BreadCrumbItem(((NodeImpl) node).getIdentifier(),
                                               nodeName,
                                               node.getPath(),
                                               node.isNodeType(NodeTypeConstants.EXO_SYMLINK)));
              }
              break;
            } else{
              node = node.getParent();
              node = checkSymlinkHistory(node, session, ownerId);
              if (node != null) {
                nodeName= node.hasProperty(NodeTypeConstants.EXO_TITLE) ? node.getProperty(NodeTypeConstants.EXO_TITLE).getString() : node.getName();
                parents.add(new BreadCrumbItem(((NodeImpl) node).getIdentifier(),
                                               nodeName,
                                               node.getPath(),
                                               node.isNodeType(NodeTypeConstants.EXO_SYMLINK)));
              }
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

  Node checkSymlinkHistory(Node node, Session session, Long ownerId) throws RepositoryException {
    List<SymlinkNavigation> history = symlinksNavHistory.get(ownerId);
    if (node != null && history != null && !history.isEmpty()) {
      String id = ((NodeImpl) node).getIdentifier();
      SymlinkNavigation symlink = history.stream().filter(item -> id.equals(item.getSourceId())).findAny().orElse(null);
      if (symlink != null) {
        node = getNodeByIdentifier(session, symlink.getSymlinkId());
      }
    }
    return node;
  }
  @Override
  public List<FullTreeItem> getFullTreeData(long ownerId,
                                            String folderId,
                                            Identity aclIdentity) throws IllegalAccessException, ObjectNotFoundException {
    String username = aclIdentity.getUserId();
    SessionProvider sessionProvider = null;
    List<FullTreeItem> parents = new ArrayList<>();
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
      if (node != null) {
        String nodeName= node.hasProperty(NodeTypeConstants.EXO_TITLE) ? node.getProperty(NodeTypeConstants.EXO_TITLE).getString() : node.getName();
        List<FullTreeItem> children = new ArrayList<>();
        children = getAllFolderInNode(node,session);

        parents.add(new FullTreeItem(((NodeImpl) node).getIdentifier(), nodeName, node.getPath(),children));
      }
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving tree folder'" + folderId, e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
    return parents;
  }

  private List<FullTreeItem> getAllFolderInNode(Node node, Session session) throws RepositoryException {
    List<FullTreeItem> folderListNodes = new ArrayList<>();
    NodeIterator nodeIter = node.getNodes();
    while (nodeIter.hasNext()) {
      Node childNode = nodeIter.nextNode();
      if (!childNode.isNodeType(NodeTypeConstants.EXO_HIDDENABLE)
          && (childNode.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED) || childNode.isNodeType(NodeTypeConstants.NT_FOLDER) || childNode.isNodeType(NodeTypeConstants.EXO_SYMLINK))) {
        if(childNode.isNodeType(NodeTypeConstants.EXO_SYMLINK)){
          childNode=getNodeByIdentifier(session, childNode.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString());
          if (childNode != null && !childNode.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED)
              && !childNode.isNodeType(NodeTypeConstants.NT_FOLDER)) {
            continue;
          }
        }
        if(childNode != null){
          String nodeName = childNode.hasProperty(NodeTypeConstants.EXO_TITLE) ? childNode.getProperty(NodeTypeConstants.EXO_TITLE)
                  .getString()
                  : childNode.getName();
          List<FullTreeItem> folderChildListNodes = getAllFolderInNode(childNode,session);
          folderListNodes.add(new FullTreeItem(((NodeImpl) childNode).getIdentifier(),
                  nodeName,
                  childNode.getPath(),
                  folderChildListNodes));
        }

      }
    }
    return folderListNodes.stream().sorted( new Comparator<FullTreeItem>() {
      public int compare(FullTreeItem o1, FullTreeItem o2) {
        //sorted the tree item when the name contains numbers
        if(NumberUtils.isNumber(o1.getName())&&NumberUtils.isNumber(o2.getName())){
          return Integer.parseInt(o1.getName()) - Integer.parseInt(o2.getName());
        }
        // sorted the tree item when the name contains only characters
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    }).collect(Collectors.toList());
  }
  @Override
  public AbstractNode createFolder(long ownerId,
                           String folderId,
                           String folderPath,
                           String title,
                           Identity aclIdentity) throws ObjectAlreadyExistsException {
    if (!JCRDocumentsUtil.isValidDocumentTitle(title)) {
      throw new IllegalArgumentException("folder title is not valid");
    }
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
      if (StringUtils.isNotBlank(folderPath)) {
        try {
          node = node.getNode(java.net.URLDecoder.decode(folderPath, StandardCharsets.UTF_8.name()));
        } catch (RepositoryException repositoryException) {
          throw new ObjectNotFoundException("Folder with path : " + folderPath + " isn't found");
        }
      }
      String name = Text.escapeIllegalJcrChars(cleanName(title.toLowerCase()));
      if (node.hasNode(name)) {
        throw new ObjectAlreadyExistsException("Folder'" + name + "' already exist");
      }
      Node addedNode = node.addNode(name, NodeTypeConstants.NT_FOLDER);
      addedNode.setProperty(NodeTypeConstants.EXO_TITLE, title);
      if (addedNode.canAddMixin("mix:referenceable")) {
        addedNode.addMixin("mix:referenceable");
      }
      node.save();
      return toFolderNode(identityManager, aclIdentity, addedNode, "", spaceService);
    } catch (ObjectAlreadyExistsException e) {
      throw new ObjectAlreadyExistsException(e);
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving folder'" + folderId + "' breadcrumb", e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }
  @Override
  public String getNewName(long ownerId,
                           String folderId,
                           String folderPath,
                           String title) throws IllegalAccessException,
                                                 ObjectAlreadyExistsException,
                                                 ObjectNotFoundException {
    SessionProvider systemSessionProvider = null;
    try {
      Node node = null;
      systemSessionProvider = SessionProvider.createSystemProvider();
      ManageableRepository repository = repositoryService.getCurrentRepository();
      Session systemSession = systemSessionProvider.getSession(repository.getConfiguration().getDefaultWorkspaceName(), repository);
      if (StringUtils.isBlank(folderId) && ownerId > 0) {
        org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
        node = getIdentityRootNode(spaceService, nodeHierarchyCreator, ownerIdentity, systemSession);
        folderId = ((NodeImpl) node).getIdentifier();
      } else {
        node = getNodeByIdentifier(systemSession, folderId);
      }
      if(StringUtils.isNotBlank(folderPath)){
        try {
          node = node.getNode(java.net.URLDecoder.decode(folderPath, StandardCharsets.UTF_8.name()));
        } catch (RepositoryException repositoryException) {
          throw new ObjectNotFoundException("Folder with path : " + folderPath + " isn't found");
        }
      }
      String name = Text.escapeIllegalJcrChars(cleanName(title.toLowerCase()));
      int i =0;
      String newName = name;
      String newTitle = title;
      while((node.hasNode(newName))){
        i++;
        newTitle = title + " (" + i + ")";
        newName = Text.escapeIllegalJcrChars(cleanName(newTitle.toLowerCase()));
      }
      return newTitle;
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving folder'" + folderId + "' breadcrumb", e);
    } finally {
      if (systemSessionProvider != null) {
        systemSessionProvider.close();
      }
    }
  }

  @Override
  public void renameDocument(long ownerId, String documentID, String title, Identity aclIdentity) throws ObjectAlreadyExistsException {
    if (!JCRDocumentsUtil.isValidDocumentTitle(title)) {
      throw new IllegalArgumentException("document title is not valid");
    }
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
      String name = Text.escapeIllegalJcrChars(cleanName(title));
      //clean node name
      name = URLDecoder.decode(name, "UTF-8");
      if (name.indexOf('.') == -1) {
        String oldName = node.getName().indexOf('.') == -1 && node.isNodeType(NodeTypeConstants.NT_FILE) && node.hasProperty(NodeTypeConstants.EXO_TITLE) ? node.getProperty(NodeTypeConstants.EXO_TITLE).getString() : node.getName();
        if (oldName.indexOf('.') != -1 && node.isNodeType(NodeTypeConstants.NT_FILE)) {
          String ext = oldName.substring(oldName.lastIndexOf('.'));
          title = title.concat(ext);
          name = name.concat(ext);
        }
      }
      
      checkNodeExistence(session, node, title);
      
      if (node.canAddMixin(NodeTypeConstants.EXO_MODIFY)) {
        node.addMixin(NodeTypeConstants.EXO_MODIFY);
      }
      Calendar now = Calendar.getInstance();
      node.setProperty(NodeTypeConstants.EXO_DATE_MODIFIED, now);
      node.setProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE, now);
      node.setProperty(NodeTypeConstants.EXO_LAST_MODIFIER, username);

      // Update exo:name
      if (node.canAddMixin(NodeTypeConstants.EXO_SORTABLE)) {
        node.addMixin(NodeTypeConstants.EXO_SORTABLE);
      }
      if (!node.hasProperty(NodeTypeConstants.EXO_TITLE)) {
        node.addMixin(NodeTypeConstants.EXO_RSS_ENABLE);
      }
      node.save();

      Node parent = node.getParent();
      String srcPath = node.getPath();
      String destPath = (parent.getPath().equals("/") ? org.apache.commons.lang.StringUtils.EMPTY : parent.getPath()).concat("/").concat(name);
      node.getSession().getWorkspace().move(srcPath, destPath);
      node.setProperty(NodeTypeConstants.EXO_TITLE, title);
      node.setProperty(NodeTypeConstants.EXO_NAME, name);
      node.save();
    } catch (ObjectAlreadyExistsException e) {
      throw new ObjectAlreadyExistsException(e);
    } catch (Exception e) {
      throw new IllegalStateException("Error renaming document'" + documentID, e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  private void checkNodeExistence(Session session, Node node, String title) throws RepositoryException,
                                                                            ObjectAlreadyExistsException {
    Node current = node;
    if (current != null && current.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
      current = getNodeByIdentifier(session, current.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString());
    }
    if (current != null && node.getParent() != null && node.getParent().hasNode(title)) {
      Node existNode = node.getParent().getNode(title);
      Node existCurrent = existNode;
      if (existCurrent.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        existCurrent = getNodeByIdentifier(session, existCurrent.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString());
      }
      if (existCurrent != null) {
        String primaryType = existCurrent.getPrimaryNodeType().getName();
        if (node != existNode && current.getPrimaryNodeType().getName().equals(primaryType)) {
          throw new ObjectAlreadyExistsException("Document with same name already exist");
        }
      }
    }
  }
  
  @Override
  public AbstractNode duplicateDocument(long ownerId, String fileId, String prefixClone, Identity aclIdentity) throws IllegalAccessException,
                                                                                           ObjectNotFoundException {
    String username = aclIdentity.getUserId();
    SessionProvider sessionProvider = null;

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
      Node newNode = null;

      if (oldNode != null) {
        newNode = duplicateItem(oldNode, parentNode, parentNode, prefixClone);
        parentNode.save();
      }
      VersionHistoryUtils.createVersion(newNode);
      return toFileNode(identityManager, aclIdentity, parentNode, "", spaceService);
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving duplicate file'" + fileId, e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }

  }

  @Override
  public void moveDocument(long ownerId,
                           String fileId,
                           String destPath,
                           Identity aclIdentity,
                           String conflictAction) throws ObjectAlreadyExistsException {
    String username = aclIdentity.getUserId();
    SessionProvider sessionProvider = null;
    Session session;
    Node node;
    try {
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      sessionProvider = getUserSessionProvider(repositoryService, aclIdentity);
      session = sessionProvider.getSession(COLLABORATION, manageableRepository);
      if (StringUtils.isBlank(fileId) && ownerId > 0) {
        org.exoplatform.social.core.identity.model.Identity ownerIdentity = identityManager.getIdentity(String.valueOf(ownerId));
        node = getIdentityRootNode(spaceService, nodeHierarchyCreator, username, ownerIdentity, sessionProvider);
        fileId = ((NodeImpl) node).getIdentifier();
      } else {
        node = getNodeByIdentifier(session, fileId);
      }

      if (node.canAddMixin(NodeTypeConstants.EXO_MODIFY)) {
        node.addMixin(NodeTypeConstants.EXO_MODIFY);
      }
      Calendar now = Calendar.getInstance();
      node.setProperty(NodeTypeConstants.EXO_DATE_MODIFIED, now);
      node.setProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE, now);
      node.setProperty(NodeTypeConstants.EXO_LAST_MODIFIER, username);

      node.save();

      String srcPath = node.getPath();
      if (session.itemExists(destPath + "/" + node.getName())) {
        handleMoveDocConflict(session, node, srcPath, destPath, conflictAction);
      } else {
        node.getSession().getWorkspace().move(srcPath, destPath + "/" + node.getName());
        node.save();
      }
    } catch (ObjectAlreadyExistsException e) {
      throw new ObjectAlreadyExistsException(e);
    } catch (Exception e) {
      throw new IllegalStateException("Error moving document's id " + fileId, e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }

  }

  private void handleMoveDocConflict(Session session,
                                     Node node,
                                     String srcPath,
                                     String destPath,
                                     String conflictAction) throws RepositoryException, ObjectAlreadyExistsException {
    int count = 0;
    String originName = node.getName();
    String name = originName;
    if (Objects.equals(conflictAction, KEEP_BOTH)) {
      while (session.itemExists(destPath + "/" + name)) {
        name = increaseNameIndex(originName, ++count);
      }
      destPath = destPath + "/" + name;
      node.getSession().getWorkspace().move(srcPath, destPath);
      Node destNode = (Node) session.getItem(destPath);
      if (destNode.hasProperty(NodeTypeConstants.EXO_TITLE)) {
        destNode.setProperty(NodeTypeConstants.EXO_TITLE, name);
      }
      destNode.getSession().save();
    } else if (Objects.equals(conflictAction, CREATE_NEW_VERSION)) {
      Node destNode = (Node) session.getItem(destPath + "/" + name);
      Node scrNode = (Node) session.getItem(srcPath);
      if (destNode.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)) {
        Node destContentNode = destNode.getNode(NodeTypeConstants.JCR_CONTENT);
        Node scrContentNode = scrNode.getNode(NodeTypeConstants.JCR_CONTENT);
        destContentNode.setProperty(NodeTypeConstants.JCR_DATA,
                                    scrContentNode.getProperty(NodeTypeConstants.JCR_DATA).getStream());
        destContentNode.setProperty(NodeTypeConstants.JCR_LAST_MODIFIED, Calendar.getInstance());
        if (destNode.isNodeType(NodeTypeConstants.EXO_MODIFY)) {
          destNode.setProperty(NodeTypeConstants.EXO_DATE_MODIFIED, Calendar.getInstance());
          destNode.setProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE, Calendar.getInstance());
        }
        destNode.save();
        scrNode.remove();
        if (!destNode.isCheckedOut()) {
          destNode.checkout();
        }
        destNode.checkin();
        destNode.checkout();
        destNode.getSession().save();
      }
    } else {
      Node destNode = (Node) session.getItem(destPath + "/" + name);
      Map<String, Boolean> map = new HashMap<>();
      map.put("versionable", destNode.isNodeType(NodeTypeConstants.MIX_VERSIONABLE));
      throw new ObjectAlreadyExistsException(map);
    }
  }

  private Node duplicateItem(Node oldNode, Node destinationNode, Node parentNode, String prefixClone) throws Exception{
    if (oldNode.isNodeType(NodeTypeConstants.EXO_THUMBNAILS_FOLDER)){
      return null;
    }
    Node newNode = null;
    String name = oldNode.getName();
    String title = oldNode.getProperty(NodeTypeConstants.EXO_TITLE).getString();
    if (((NodeImpl) destinationNode).getIdentifier().equals(((NodeImpl) parentNode).getIdentifier())){
      name = prefixClone.concat(" ").concat(name);
      title = prefixClone.concat(" ").concat(title);
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
    name = URLDecoder.decode(name,"UTF-8");
    if (oldNode.isNodeType(NodeTypeConstants.NT_FOLDER)) {
      newNode = destinationNode.addNode(name, NodeTypeConstants.NT_FOLDER);
      newNode.setProperty(NodeTypeConstants.EXO_TITLE, title);
      NodeIterator nodeIterator = oldNode.getNodes();
      while (nodeIterator.hasNext()) {
        Node node = nodeIterator.nextNode();
        duplicateItem(node, newNode, parentNode, prefixClone);
      }
    } else {
      newNode = destinationNode.addNode(name, oldNode.getPrimaryNodeType().getName());
      addProperties(oldNode,newNode,title);
    }
    return newNode;
  }
  private void addProperties(Node oldNode, Node newNode, String title) throws RepositoryException {
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

    if(oldNode.isNodeType(NodeTypeConstants.EXO_SYMLINK)){
      newNode.setProperty(NodeTypeConstants.EXO_WORKSPACE, oldNode.getSession().getWorkspace().getName());
      newNode.setProperty(NodeTypeConstants.EXO_PRIMARY_TYPE, oldNode.getPrimaryNodeType().getName());
      newNode.setProperty(NodeTypeConstants.EXO_SYMLINK_UUID, oldNode.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString());
    }
  }

  private String getTimeLineQueryStatement(String rootPath, String sortField, String sortDirection) {
    return new StringBuilder().append("SELECT * FROM ")
                              .append("nt:base")
                              .append(" WHERE jcr:path LIKE '")
                              .append(rootPath)
                              .append("/%' ")
                              .append(" AND ( jcr:primaryType='exo:symlink' OR jcr:primaryType='nt:file') AND NOT jcr:mixinTypes LIKE 'exo:hiddenable' ")
                              .append(" ORDER BY ")
                              .append(sortField)
                              .append(" ")
                              .append(sortDirection)
                              .toString();
  }

  private String getFolderDocumentsQuery(String folderPath, String sortField, String sortDirection, boolean includeHiddenFiles) {
    String hiddenableQuery = includeHiddenFiles ? " " : " AND NOT jcr:mixinTypes LIKE 'exo:hiddenable' ";
    return new StringBuilder().append("SELECT * FROM nt:base")
            .append(" WHERE jcr:path LIKE '")
            .append(folderPath)
            .append("/%'")
            .append(" AND NOT jcr:path LIKE '")
            .append(folderPath)
            .append("/%/%' ")
            .append(hiddenableQuery)
            .append(" ORDER BY ")
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

  private Node getNodeByPath(Node node, String folderPath, SessionProvider sessionProvider) throws ObjectNotFoundException {
    try {
      if ((node.getName().equals(USER_PRIVATE_ROOT_NODE))) {
        if (folderPath.startsWith(USER_PRIVATE_ROOT_NODE)) {
          folderPath = folderPath.split(USER_PRIVATE_ROOT_NODE + "/")[1];
          return (node.getNode(java.net.URLDecoder.decode(folderPath, StandardCharsets.UTF_8.name())));
        }
        if (folderPath.startsWith(USER_PUBLIC_ROOT_NODE)) {
          SessionProvider systemSessionProvides = SessionProvider.createSystemProvider();
          Session systemSession = systemSessionProvides.getSession(sessionProvider.getCurrentWorkspace(),
                                                                   sessionProvider.getCurrentRepository());
          Node parent = getNodeByIdentifier(systemSession, ((NodeImpl) node).getIdentifier()).getParent();
          node = parent.getNode(java.net.URLDecoder.decode(folderPath, StandardCharsets.UTF_8.name()));
          Session session = sessionProvider.getSession(sessionProvider.getCurrentWorkspace(),
                                                       sessionProvider.getCurrentRepository());
          if (session.itemExists(node.getPath())) {
            return (Node) session.getItem(node.getPath());
          }
          return null;
        }
      }
      if ((node.getName().equals(USER_PUBLIC_ROOT_NODE))) {
        if (folderPath.startsWith(USER_PRIVATE_ROOT_NODE + "/" + USER_PUBLIC_ROOT_NODE)) {
          folderPath = folderPath.split(USER_PRIVATE_ROOT_NODE + "/" + USER_PUBLIC_ROOT_NODE + "/")[1];
          return (node.getNode(java.net.URLDecoder.decode(folderPath, StandardCharsets.UTF_8.name())));
        }
        if (folderPath.startsWith(USER_PUBLIC_ROOT_NODE)) {
          folderPath = folderPath.split(USER_PUBLIC_ROOT_NODE + "/")[1];
          return (node.getNode(java.net.URLDecoder.decode(folderPath, StandardCharsets.UTF_8.name())));
        }
      }
      return (node.getNode(java.net.URLDecoder.decode(folderPath, StandardCharsets.UTF_8.name())));
    } catch (RepositoryException repositoryException) {
      throw new ObjectNotFoundException("Folder with path : " + folderPath + " isn't found");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Error retrieving folder'" + folderPath, e);
    }
  }

  public void updatePermissions(String documentID, NodePermission nodePermissionEntity, Identity aclIdentity){
    SessionProvider sessionProvider = null;
    try {
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      sessionProvider = getUserSessionProvider(repositoryService, aclIdentity);
      Session session = sessionProvider.getSession(COLLABORATION, manageableRepository);
      Node node = getNodeByIdentifier(session, documentID);
      Map<String, String[]> permissions = new HashMap<>();
      List<PermissionEntry> permissionsList = nodePermissionEntity.getPermissions();
      if (node.hasProperty(NodeTypeConstants.EXO_OWNER)) {
        String owner = node.getProperty(NodeTypeConstants.EXO_OWNER).getString();
        permissions.put(owner, PermissionType.ALL);
      }
      permissions.put(GROUP_ADMINISTRATORS, PermissionType.ALL);
      for(PermissionEntry permission : permissionsList){
        if(permission.getIdentity().getProviderId().equals(SPACE_PROVIDER_ID)) {
          Space space = spaceService.getSpaceByPrettyName(permission.getIdentity().getRemoteId());
          String groupId = space.getGroupId();
          if (permission.getPermission().equals("edit")) {
            if(permission.getRole().equals(PermissionRole.ALL.name())){
              permissions.put("*:" + groupId, PermissionType.ALL);
            }
            if(permission.getRole().equals(PermissionRole.MANAGERS_REDACTORS.name())){
              permissions.put("manager:"+ groupId, PermissionType.ALL);
              permissions.put("redactor:" + groupId, PermissionType.ALL);
            }
          }
          if (permission.getPermission().equals("read")) {
            if(permission.getRole().equals(PermissionRole.ALL.name())){
              permissions.put("*:" + groupId, new String[] { PermissionType.READ });
            }
            if(permission.getRole().equals(PermissionRole.MANAGERS_REDACTORS.name())){
              permissions.put("manager:" + groupId, new String[] { PermissionType.READ });
              permissions.put("redactor:" + groupId, new String[] { PermissionType.READ });
            }
          }
        } else {
          if (permission.getPermission().equals("edit")) {
            permissions.put(permission.getIdentity().getRemoteId(), PermissionType.ALL);
          }
          if (permission.getPermission().equals("read")) {
            permissions.put(permission.getIdentity().getRemoteId(), new String[]{PermissionType.READ});
          }
        }
        }
      if (node.canAddMixin(NodeTypeConstants.EXO_PRIVILEGEABLE)) {
        node.addMixin(NodeTypeConstants.EXO_PRIVILEGEABLE);
      }
      ((ExtendedNode) node).setPermissions(permissions);
      session.save();
    } catch (Exception e) {
      throw new IllegalStateException("Error updating permissi" +
              "ons of document'" + documentID, e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  public void shareDocument(String documentId, long destId) {
    Node rootNode = null;
    Node shared = null;
    SessionProvider sessionProvider = null;
    try {
      sessionProvider = SessionProvider.createSystemProvider();
      ManageableRepository repository = repositoryService.getCurrentRepository();
      Session systemSession = sessionProvider.getSession(repository.getConfiguration().getDefaultWorkspaceName(), repository);
      Node currentNode = getNodeByIdentifier(systemSession, documentId);
      //add symlink to destination user
      org.exoplatform.social.core.identity.model.Identity destIdentity = identityManager.getIdentity(String.valueOf(destId));
      rootNode = getIdentityRootNode(spaceService, nodeHierarchyCreator, destIdentity, systemSession);
      if(!destIdentity.getProviderId().equals(SPACE_PROVIDER_ID)){
        rootNode = rootNode.getNode("Documents");
      }
      if(!rootNode.hasNode(SHARED_FOLDER_NAME)){
        shared = rootNode.addNode(SHARED_FOLDER_NAME);
      }else{
        shared = rootNode.getNode(SHARED_FOLDER_NAME);
      }
      if(currentNode.isNodeType(NodeTypeConstants.EXO_SYMLINK)){
        String sourceNodeId = currentNode.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
        currentNode = getNodeByIdentifier(systemSession, sourceNodeId);
      }
      Node linkNode = null;
      if (shared.hasNode(currentNode.getName())) {
        linkNode = shared.getNode(currentNode.getName());
      } else {
        linkNode = shared.addNode(currentNode.getName(), NodeTypeConstants.EXO_SYMLINK);
      }
      linkNode.setProperty(NodeTypeConstants.EXO_WORKSPACE, repository.getConfiguration().getDefaultWorkspaceName());
      linkNode.setProperty(NodeTypeConstants.EXO_PRIMARY_TYPE, currentNode.getPrimaryNodeType().getName());
      linkNode.setProperty(NodeTypeConstants.EXO_SYMLINK_UUID, ((ExtendedNode) currentNode).getIdentifier());
      if(linkNode.canAddMixin(NodeTypeConstants.EXO_SORTABLE)) {
        linkNode.addMixin("exo:sortable");
      }
      if (currentNode.hasProperty(NodeTypeConstants.EXO_TITLE)) {
        linkNode.setProperty(NodeTypeConstants.EXO_TITLE,currentNode.getProperty(NodeTypeConstants.EXO_TITLE).getString());
      }
      linkNode.setProperty(NodeTypeConstants.EXO_NAME, currentNode.getName());
      String nodeMimeType = getMimeType(currentNode);
      linkNode.addMixin(NodeTypeConstants.MIX_FILE_TYPE);
      linkNode.setProperty(NodeTypeConstants.EXO_FILE_TYPE, nodeMimeType);
      rootNode.save();
      Map<String, String[]> permissions = new HashMap<>();
      if (destIdentity.getProviderId().equals(SPACE_PROVIDER_ID)) {
        Space space = spaceService.getSpaceByPrettyName(destIdentity.getRemoteId());
        String groupId = space.getGroupId();
        permissions.put("*:" + groupId, PermissionType.ALL);
      } else {
        permissions.put(destIdentity.getRemoteId(), PermissionType.ALL);
      }
      if (linkNode.canAddMixin(NodeTypeConstants.EXO_PRIVILEGEABLE)) {
        linkNode.addMixin(NodeTypeConstants.EXO_PRIVILEGEABLE);
      }
      ((ExtendedNode) linkNode).setPermissions(permissions);
      systemSession.save();
      Utils.broadcast(listenerService, "share_document_event", destIdentity, linkNode);
    } catch (Exception e) {
      throw new IllegalStateException("Error updating sharing of document'" + documentId + " to identity " + destId, e);
    }finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  public void notifyMember(String documentId, long destId) {
    SessionProvider sessionProvider = null;
    try {
      sessionProvider = SessionProvider.createSystemProvider();
      ManageableRepository repository = repositoryService.getCurrentRepository();
      Session systemSession = sessionProvider.getSession(repository.getConfiguration().getDefaultWorkspaceName(), repository);
      org.exoplatform.social.core.identity.model.Identity destIdentity = identityManager.getIdentity(String.valueOf(destId));
      Node currentNode = getNodeByIdentifier(systemSession, documentId);
      Utils.broadcast(listenerService, "share_document_event", destIdentity, currentNode);
    } catch (Exception e) {
      throw new IllegalStateException("Error updating sharing of document'" + documentId + " to identity " + destId, e);
    }finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  public boolean canAccess(String documentID, Identity aclIdentity) throws RepositoryException {
    SessionProvider sessionProvider = null;
    boolean canAccess = false;
    try {
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      sessionProvider = getUserSessionProvider(repositoryService, aclIdentity);
      Session session = sessionProvider.getSession(COLLABORATION, manageableRepository);
      Node node = getNodeByIdentifier(session, documentID);
      if(node == null) return false;

      String userId = aclIdentity.getUserId();
      ExtendedNode extendedNode = (ExtendedNode) node;
      List<AccessControlEntry> permsList = extendedNode.getACL().getPermissionEntries();
      for (AccessControlEntry accessControlEntry : permsList) {
        String nodeAclIdentity = accessControlEntry.getIdentity();
        MembershipEntry membershipEntry = accessControlEntry.getMembershipEntry();
        if (StringUtils.equals(nodeAclIdentity, userId) || StringUtils.equals(IdentityConstants.ANY, userId) || (membershipEntry != null && aclIdentity.isMemberOf(membershipEntry) && !StringUtils.equals(membershipEntry.toString(), GROUP_ADMINISTRATORS))) {
          canAccess = true;
        }
      }
      return canAccess;
    } catch (Exception e) {
      throw new IllegalStateException("Error checking access rights for on document'" + documentID + " fro user " + aclIdentity.getUserId(), e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  @Override
  public void updateDocumentDescription(long ownerId, String documentId, String description, Identity aclIdentity) throws RepositoryException {
    String username = aclIdentity.getUserId();
    SessionProvider sessionProvider = null;
    try {
      Node node = null;
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      sessionProvider = getUserSessionProvider(repositoryService, aclIdentity);
      Session session = sessionProvider.getSession(COLLABORATION, manageableRepository);
      node = getNodeByIdentifier(session, documentId);
      if (node.canAddMixin(NodeTypeConstants.EXO_MODIFY)) {
        node.addMixin(NodeTypeConstants.EXO_MODIFY);
      }
      Calendar now = Calendar.getInstance();
      node.setProperty(NodeTypeConstants.EXO_DATE_MODIFIED, now);
      node.setProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE, now);
      node.setProperty(NodeTypeConstants.EXO_LAST_MODIFIER, username);
      if (node.canAddMixin(NodeTypeConstants.DC_ELEMENT_SET) && !node.hasProperty(NodeTypeConstants.DC_DESCRIPTION)) {
        node.addMixin(NodeTypeConstants.DC_ELEMENT_SET);
      }
      try {
        node.setProperty(NodeTypeConstants.DC_DESCRIPTION, description);
      } catch (ValueFormatException e) {
        node.setProperty(NodeTypeConstants.DC_DESCRIPTION, new String[] { description });
      }
      if (node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
        Node content = node.getNode(NodeTypeConstants.JCR_CONTENT);
        try {
          content.setProperty(NodeTypeConstants.DC_DESCRIPTION, description);
        } catch (ValueFormatException e) {
          content.setProperty(NodeTypeConstants.DC_DESCRIPTION, new String[] { description });
        }
      }
      node.getSession().save();
      // Create tags if the description contains
      TagService tagService = CommonsUtils.getService(TagService.class);
      Set<TagName> tagNames = tagService.detectTagNames(description);
      if(!tagNames.isEmpty()){
        org.exoplatform.social.core.identity.model.Identity audienceIdentity = getOwnerIdentityFromNodePath(node.getPath(), identityManager, spaceService);
        long spaceId = 0;
        if(audienceIdentity.getProviderId().equals(SPACE_PROVIDER_ID)){
          Space space = spaceService.getSpaceByPrettyName(audienceIdentity.getRemoteId());
          spaceId = Long.parseLong(space.getId());
        }
        tagService.saveTags(new TagObject("file", ((ExtendedNode) node).getIdentifier(), null, spaceId),
                tagNames,
                Long.parseLong(audienceIdentity.getId()),
                Long.parseLong(identityManager.getOrCreateUserIdentity(username).getId()));
        listenerService.broadcast(ADD_TAG_DOCUMENT, new TagObject("Document", ((ExtendedNode) node).getIdentifier(), null, spaceId), tagNames);
      }

    } catch (Exception e) {
      throw new IllegalStateException("Error renaming document'" + documentId, e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }
  
  @Override
  public void createShortcut(String documentId, String destPath, String aclIdentity, String conflictAction) throws IllegalAccessException, ObjectAlreadyExistsException {
    Node rootNode;
    SessionProvider sessionProvider = null;
    Identity identity = identityRegistry.getIdentity(aclIdentity);
    try {
      sessionProvider = getUserSessionProvider(repositoryService, identity);
      ManageableRepository repository = repositoryService.getCurrentRepository();
      Session session = sessionProvider.getSession(COLLABORATION, repository);
      Node currentNode = getNodeByIdentifier(session, documentId);
      //add symlink to destination document
      rootNode = (Node) session.getItem(destPath);
      if (currentNode != null && currentNode.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        String sourceNodeId = currentNode.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
        currentNode = getNodeByIdentifier(session, sourceNodeId);
      }
      Node linkNode;
      if (currentNode != null && rootNode.hasNode(currentNode.getName())) {
        linkNode = handleShortcutDocConflict(rootNode, currentNode, conflictAction);
      } else {
        linkNode = rootNode.addNode(currentNode.getName(), NodeTypeConstants.EXO_SYMLINK);
      }
      linkNode.setProperty(NodeTypeConstants.EXO_WORKSPACE, COLLABORATION);
      linkNode.setProperty(NodeTypeConstants.EXO_PRIMARY_TYPE, currentNode.getPrimaryNodeType().getName());
      linkNode.setProperty(NodeTypeConstants.EXO_SYMLINK_UUID, ((ExtendedNode) currentNode).getIdentifier());
      if (linkNode.canAddMixin(NodeTypeConstants.EXO_SORTABLE)) {
        linkNode.addMixin("exo:sortable");
      }
      if (currentNode.hasProperty(NodeTypeConstants.EXO_TITLE) && StringUtils.isBlank(conflictAction)) {
        linkNode.setProperty(NodeTypeConstants.EXO_TITLE, currentNode.getProperty(NodeTypeConstants.EXO_TITLE).getString());
      }
      linkNode.setProperty(NodeTypeConstants.EXO_NAME, currentNode.getName());
      String nodeMimeType = getMimeType(currentNode);
      linkNode.addMixin(NodeTypeConstants.MIX_FILE_TYPE);
      linkNode.setProperty(NodeTypeConstants.EXO_FILE_TYPE, nodeMimeType);
      rootNode.save();

      if (linkNode.canAddMixin(NodeTypeConstants.EXO_PRIVILEGEABLE)) {
        linkNode.addMixin(NodeTypeConstants.EXO_PRIVILEGEABLE);
      }
      Map<String, String[]> perMap = new HashMap<>();
      List<String> permsList;
      List<String> idList = new ArrayList<>();
      for (AccessControlEntry accessEntry : ((ExtendedNode) currentNode).getACL().getPermissionEntries()) {
        if (!idList.contains(accessEntry.getIdentity())) {
          idList.add(accessEntry.getIdentity());
          permsList = ((ExtendedNode) currentNode).getACL().getPermissions(accessEntry.getIdentity());
          perMap.put(accessEntry.getIdentity(), permsList.toArray(new String[0]));
        }
      }
      ((ExtendedNode) linkNode).setPermissions(perMap);

      session.save();
    } catch (ObjectAlreadyExistsException e) {
      throw new ObjectAlreadyExistsException(e);
    } catch (Exception e) {
      throw new IllegalStateException("Error while creating a shortcut for document's id " + documentId + " to destination path" + destPath, e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  private Node handleShortcutDocConflict(Node rootNode, Node currentNode, String conflictAction) throws ObjectAlreadyExistsException,
                                                                                         RepositoryException {
    Node linkNode = null;
    boolean created = false;
    int count = 0;
    String originName = currentNode.getName();
    String name = originName;
    boolean doIndexName = rootNode.hasNode(originName);
    if (Objects.equals(conflictAction, KEEP_BOTH)) {
      do {
        try {
          linkNode = rootNode.addNode(name, NodeTypeConstants.EXO_SYMLINK);
          created = true;
        } catch (ItemExistsException e) {
          name = increaseNameIndex(originName, ++count);
          doIndexName = false;
        }
      } while (!created);

      if (doIndexName) {
        String path = linkNode.getPath();
        String index = path.substring(StringUtils.lastIndexOf(path, name) + name.length());
        if (StringUtils.isNotBlank(index)) {
          int indexSuffix = Integer.parseInt(index.substring(1, index.lastIndexOf("]")));
          String suffix = "(" + (indexSuffix - 1) + ")";
          name = getNewIndexedName(originName, suffix);
        }
      }
      if (linkNode.hasProperty(NodeTypeConstants.EXO_TITLE)) {
        linkNode.setProperty(NodeTypeConstants.EXO_TITLE, name);
      }
    } else {
      throw new ObjectAlreadyExistsException("Document with same name already exists");
    }
    return linkNode;
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  public List<FileVersion> getFileVersions(String fileNodeId, String aclIdentity) {
    List<FileVersion> fileVersions = new ArrayList<>();
    Identity identity = identityRegistry.getIdentity(String.valueOf(aclIdentity));
    try {
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      Session session = getUserSessionProvider(repositoryService, identity).getSession(COLLABORATION, manageableRepository);
      Node node = session.getNodeByUUID(fileNodeId);
      Version rootVersion = node.getVersionHistory().getRootVersion();
      VersionIterator versionIterator = node.getVersionHistory().getAllVersions();
      while (versionIterator.hasNext()) {
        Version version = versionIterator.nextVersion();
        if (version.getUUID().equals(rootVersion.getUUID())) {
          continue;
        }
        fileVersions.add(JCRDocumentsUtil.toFileVersion(version, node, identityManager));
      }
    } catch (RepositoryException e) {
      throw new IllegalStateException("Error while getting file versions", e);
    }
    fileVersions.sort(Collections.reverseOrder());
    return fileVersions;
  }

  private static String addVersionLabel(Node node, String label, Version version) throws RepositoryException {
    String[] olLabels = node.getVersionHistory().getVersionLabels(version);
    for (String oldLabel : olLabels) {
      if (label.equals(oldLabel)) {
        continue;
      }
      node.getVersionHistory().removeVersionLabel(oldLabel);
    }
    node.getVersionHistory().addVersionLabel(version.getName(), label, false);
    return label;
  }

  @Override
  public FileVersion updateVersionSummary(String originFileId, String versionId, String summary, String aclIdentity) {
    Identity identity = identityRegistry.getIdentity(String.valueOf(aclIdentity));
    FileVersion versionFileNode = new FileVersion();
    try {
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      Session session = getUserSessionProvider(repositoryService, identity).getSession(COLLABORATION, manageableRepository);
      Node node = session.getNodeByUUID(originFileId);
      Version version = (Version) session.getNodeByUUID(versionId);
      versionFileNode.setId(versionId);
      Node frozen = version.getNode(NodeTypeConstants.JCR_FROZEN_NODE);
      if (frozen.hasProperty(EOO_COMMENT_ID)) {
        String commentId = frozen.getProperty(EOO_COMMENT_ID).getString();
        if (StringUtils.isNotBlank(commentId)) {
          ExoSocialActivity activity = activityManager.getActivity(commentId);
          if (activity != null) {
            activity.setTitle(summary);
            activityManager.updateActivity(activity);
          }
        } 
      } 
      addVersionLabel(node, summary, version);
      versionFileNode.setSummary(summary);
      session.save();
    } catch (Exception e) {
      throw new IllegalStateException("Error while adding or updating version summary", e);
    }
    return versionFileNode;
  }

  /**
   * {@inheritDoc}
   *
   * @return
   */
  @Override
  public FileVersion restoreVersion(String versionId, String aclIdentity) {
    Identity identity = identityRegistry.getIdentity(String.valueOf(aclIdentity));
    FileVersion versionFileNode = new FileVersion();
    try {
      ManageableRepository manageableRepository = repositoryService.getCurrentRepository();
      Session session = getUserSessionProvider(repositoryService, identity).getSession(COLLABORATION, manageableRepository);
      Version version = (Version) session.getNodeByUUID(versionId);
      Node frozen = version.getNode(NodeTypeConstants.JCR_FROZEN_NODE);
      Node node;
      if (frozen != null) {
        String frozenUuid = Utils.getStringProperty(frozen, NodeTypeConstants.JCR_FROZEN_UUID);
        node = session.getNodeByUUID(frozenUuid);
        if (node != null) {
          node.restore(version, true);
          if (!node.isCheckedOut()) {
            node.checkout();
          }
          Version newVersion = node.checkin();
          versionFileNode = JCRDocumentsUtil.toFileVersion(newVersion, node, identityManager);
          node.checkout();
          if (node.isNodeType(NodeTypeConstants.EXO_MODIFY)) {
            node.setProperty(NodeTypeConstants.EXO_DATE_MODIFIED, Calendar.getInstance());
            node.setProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE, Calendar.getInstance());
          }
        }
      }
      session.save();
    } catch (RepositoryException e) {
      throw new IllegalStateException("Error while restoring version", e);
    }
    return versionFileNode;
  }
}
