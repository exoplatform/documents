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
package org.exoplatform.documents.storage.jcr.util;

import java.util.*;

import javax.jcr.*;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.api.search.data.SearchResult;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.jcr.search.DocumentFileSearchResult;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.*;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.*;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class JCRDocumentsUtil {
  private static final Log                              LOG                           =
                                                            ExoLogger.getLogger(JCRDocumentsUtil.class);

  private static final String                           DEFAULT_GROUPS_HOME_PATH      = "/Groups";                             // NOSONAR

  public static final String                            GROUPS_PATH_ALIAS             = "groupsPath";

  public static final String                            DOCUMENTS_NODE                = "Documents";

  private static final String                           JCR_DATASOURCE_NAME           = "jcr";

  protected static final Map<DocumentSortField, String> SORT_FIELDS_ES_CORRESPONDING  = new EnumMap<>(DocumentSortField.class);

  protected static final Map<DocumentSortField, String> SORT_FIELDS_JCR_CORRESPONDING = new EnumMap<>(DocumentSortField.class);
  static {
    SORT_FIELDS_JCR_CORRESPONDING.put(DocumentSortField.NAME, NodeTypeConstants.EXO_TITLE);
    SORT_FIELDS_JCR_CORRESPONDING.put(DocumentSortField.CREATED_DATE, NodeTypeConstants.EXO_DATE_CREATED);
    SORT_FIELDS_JCR_CORRESPONDING.put(DocumentSortField.MODIFIED_DATE, NodeTypeConstants.EXO_DATE_MODIFIED);

    SORT_FIELDS_ES_CORRESPONDING.put(DocumentSortField.NAME, "title");
    SORT_FIELDS_ES_CORRESPONDING.put(DocumentSortField.CREATED_DATE, "lastUpdatedDate");
    SORT_FIELDS_ES_CORRESPONDING.put(DocumentSortField.MODIFIED_DATE, "lastUpdatedDate");
  }

  private static String groupsPath = null;

  private JCRDocumentsUtil() {
    // Utils class, no constructor will be needed
  }

  public static Node getGroupNode(NodeHierarchyCreator nodeHierarchyCreator,
                                  Session session,
                                  String groupId) throws RepositoryException {
    String groupsHomePath = getGroupsPath(nodeHierarchyCreator);
    String groupPath = groupsHomePath + groupId + "/" + DOCUMENTS_NODE; // NOSONAR
    if (session.itemExists(groupPath)) {
      return (Node) session.getItem(groupPath);
    }
    return null;
  }

  public static String getGroupsPath(NodeHierarchyCreator nodeHierarchyCreator) {
    if (groupsPath != null) {
      return groupsPath;
    }
    groupsPath = nodeHierarchyCreator.getJcrPath(GROUPS_PATH_ALIAS);
    if (StringUtils.isBlank(groupsPath)) {
      groupsPath = DEFAULT_GROUPS_HOME_PATH;
    }
    return groupsPath;
  }

  public static List<FileNode> toFileNodes(IdentityManager identityManager,
                                           NodeIterator nodeIterator,
                                           Identity aclIdentity,
                                           int offset,
                                           int limit) {
    List<FileNode> fileNodes = new ArrayList<>();
    int index = 0;
    int size = 0;
    while (nodeIterator.hasNext()) {
      if (index < offset) {
        index++;
        continue;
      }
      Node node = nodeIterator.nextNode();
      FileNode fileNode = toFileNode(identityManager, aclIdentity, node);
      fileNodes.add(fileNode);
      size++;
      if (size >= limit) {
        return fileNodes;
      }
    }
    return fileNodes;
  }

  public static FileNode toFileNode(IdentityManager identityManager,
                                    Session session,
                                    Identity aclIdentity,
                                    SearchResult searchResult) {
    DocumentFileSearchResult fileSearchResult = (DocumentFileSearchResult) searchResult;
    try {
      FileNode fileNode = new FileNode();
      Node node = getNode(session, fileNode, fileSearchResult);
      toFileNode(identityManager, aclIdentity, node, fileNode);
      return fileNode;
    } catch (Exception e) {
      LOG.warn("Error computing File Node for search result with id {}", fileSearchResult.getId(), e);
      return null;
    }
  }

  public static FileNode toFileNode(IdentityManager identityManager,
                                    Identity aclIdentity,
                                    Node node) {
    if (node == null) {
      return null;
    }
    FileNode fileNode = new FileNode();
    toFileNode(identityManager, aclIdentity, node, fileNode);
    return fileNode;
  }

  public static void toFileNode(IdentityManager identityManager,
                                Identity aclIdentity,
                                Node node,
                                FileNode fileNode) {
    try {
      fileNode.setDatasource(JCR_DATASOURCE_NAME);
      retrieveFileProperties(identityManager, node, aclIdentity, fileNode);
      if (node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
        Node content = node.getNode(NodeTypeConstants.JCR_CONTENT);
        retrieveFileContentProperties(content, fileNode);
      }
    } catch (Exception e) {
      try {
        LOG.warn("Error computing File Node for search result with path {}", node.getPath(), e);
      } catch (Exception e1) {
        LOG.warn("Error computing File Node for search result with path {}", node, e);
      }
    }
  }

  public static Node getNode(Session session,
                             FileNode fileNode,
                             DocumentFileSearchResult fileSearchResult) throws RepositoryException {
    Node node = getNodeByIdentifier(session, fileSearchResult.getId());
    if (node == null) {
      node = getNodeByPath(session, fileSearchResult.getNodePath());
    }
    if (node == null) {
      return null;
    }
    if (node.isNodeType(NodeTypeConstants.NT_FROZEN_NODE)) {
      String originalNodeId = node.getProperty(NodeTypeConstants.JCR_FROZEN_UUID).getString();
      fileNode.setVersionnedFileId(originalNodeId);
    }
    if (node.hasProperty(NodeTypeConstants.EXO_SYMLINK_UUID)) {
      String targetNodeId = node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
      fileNode.setLinkedFileId(targetNodeId);
      return getNodeById(session, targetNodeId);
    }
    return node;
  }

  public static void retrieveFileProperties(IdentityManager identityManager,
                                            Node node,
                                            Identity aclIdentity,
                                            AbstractNode documentNode) throws RepositoryException {
    documentNode.setId(((NodeImpl) node).getIdentifier());
    documentNode.setParentFolderId(((NodeImpl) node.getParent()).getIdentifier());
    if (node.hasProperty(NodeTypeConstants.EXO_TITLE)) {
      documentNode.setName(node.getProperty(NodeTypeConstants.EXO_TITLE).getString());
    } else {
      documentNode.setName(node.getName());
    }
    if (node.hasProperty(NodeTypeConstants.EXO_DATE_CREATED)) {
      long createdDate = node.getProperty(NodeTypeConstants.EXO_DATE_CREATED)
                             .getDate()
                             .getTimeInMillis();
      documentNode.setCreatedDate(createdDate);
    }
    if (node.hasProperty(NodeTypeConstants.EXO_OWNER)) {
      String owner = node.getProperty(NodeTypeConstants.EXO_OWNER).getString();
      documentNode.setCreatorId(getUserIdentityId(identityManager, owner));
    }
    if (node.hasProperty(NodeTypeConstants.EXO_DATE_MODIFIED)) {
      long modifiedDate = node.getProperty(NodeTypeConstants.EXO_DATE_MODIFIED)
                              .getDate()
                              .getTimeInMillis();
      documentNode.setModifiedDate(modifiedDate);
      String modifier = node.getProperty(NodeTypeConstants.EXO_LAST_MODIFIER).getString();
      documentNode.setModifierId(getUserIdentityId(identityManager, modifier));
    } else {
      documentNode.setModifiedDate(documentNode.getCreatedDate());
      documentNode.setModifierId(documentNode.getCreatorId());
    }
    if (node.hasProperty(NodeTypeConstants.DC_DESCRIPTION)) {
      documentNode.setDescription(node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getString());
    }
    if (node.isNodeType(NodeTypeConstants.DC_DESCRIPTION)) {
      documentNode.setDescription(node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getString());
    }
    computeDocumentAcl(node, documentNode, aclIdentity);
  }

  public static void retrieveFileContentProperties(Node content, FileNode fileNode) throws RepositoryException {
    if (content.hasProperty(NodeTypeConstants.DC_DESCRIPTION)) {
      fileNode.setDescription(content.getProperty(NodeTypeConstants.DC_DESCRIPTION).getString());
    }
    if (content.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)) {
      fileNode.setMimeType(content.getProperty(NodeTypeConstants.JCR_MIME_TYPE).getString());
    }
    if (content.hasProperty(NodeTypeConstants.JCR_DATA)) {
      fileNode.setSize(content.getProperty(NodeTypeConstants.JCR_DATA).getLength());
    }
  }

  public static void computeDocumentAcl(Node node, AbstractNode documentNode, Identity aclIdentity) throws RepositoryException {
    boolean canEdit = false;
    boolean canDelete = false;
    String userId = aclIdentity.getUserId();
    ExtendedNode extendedNode = (ExtendedNode) node;
    List<AccessControlEntry> permsList = extendedNode.getACL().getPermissionEntries();
    for (AccessControlEntry accessControlEntry : permsList) {
      String nodeAclIdentity = accessControlEntry.getIdentity();
      MembershipEntry membershipEntry = accessControlEntry.getMembershipEntry();
      if (StringUtils.equals(nodeAclIdentity, userId)
          || StringUtils.equals(IdentityConstants.ANY, userId)
          || (membershipEntry != null && aclIdentity.isMemberOf(membershipEntry))) {
        canEdit = canEdit || accessControlEntry.getPermission().contains(PermissionType.SET_PROPERTY);
        canDelete = canDelete || accessControlEntry.getPermission().contains(PermissionType.REMOVE);
      }
    }
    documentNode.setAcl(new NodePermission(true, canEdit, canDelete));
  }

  public static Node getNodeByIdentifier(Session session, String nodeId) {
    try {
      return ((ExtendedSession) session).getNodeByIdentifier(nodeId);
    } catch (PathNotFoundException e) {
      LOG.info("Node with identifier {} is not found. Ignore search result.", nodeId);
    } catch (RepositoryException e) {
      LOG.debug("Error retrieving node with identifier {}. Will attempt to retrieve it by path", nodeId, e);
    }
    return null;
  }

  public static Node getNodeById(Session session, String nodeId) {
    try {
      return session.getNodeByUUID(nodeId);
    } catch (PathNotFoundException e) {
      LOG.info("Node with UUID {} is not found. Ignore search result.", nodeId);
    } catch (RepositoryException e) {
      LOG.debug("Error retrieving node with UUID {}. Will attempt to retrieve it by path", nodeId, e);
    }
    return null;
  }

  public static Node getNodeByPath(Session session, String nodePath) {
    try {
      if (StringUtils.isNotBlank(nodePath) && session.itemExists(nodePath)) {
        return (Node) session.getItem(nodePath);
      }
    } catch (PathNotFoundException e) {
      LOG.info("Node with path {} is not found. Ignore search result.", nodePath);
    } catch (RepositoryException e) {
      LOG.debug("Error retrieving node with path {}", nodePath, e);
    }
    return null;
  }

  public static Node getIdentityRootNode(SpaceService spaceService,
                                         NodeHierarchyCreator nodeHierarchyCreator,
                                         String username,
                                         org.exoplatform.social.core.identity.model.Identity ownerIdentity,
                                         SessionProvider sessionProvider) throws Exception {
    Node identityRootNode = null;
    if (ownerIdentity.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(ownerIdentity.getRemoteId());
      Session session = sessionProvider.getSession(sessionProvider.getCurrentWorkspace(), sessionProvider.getCurrentRepository());
      identityRootNode = getGroupNode(nodeHierarchyCreator, session, space.getGroupId());
    } else if (ownerIdentity.isUser()) {
      identityRootNode = nodeHierarchyCreator.getUserNode(sessionProvider, username);
    }
    return identityRootNode;
  }

  public static String getSortDirection(DocumentTimelineFilter filter) {
    return filter.isAscending() ? "ASC" : "DESC";
  }

  public static String getSortField(DocumentTimelineFilter filter, boolean isJcr) {
    DocumentSortField sortField = filter.getSortField();
    if (isJcr) {
      return SORT_FIELDS_JCR_CORRESPONDING.get(sortField);
    } else {
      return SORT_FIELDS_ES_CORRESPONDING.get(sortField);
    }
  }

  public static SessionProvider getUserSessionProvider(RepositoryService repositoryService, Identity aclIdentity) {
    SessionProvider sessionProvider = new SessionProvider(new ConversationState(aclIdentity));
    try {
      ManageableRepository repository = repositoryService.getCurrentRepository();
      String workspace = repository.getConfiguration().getDefaultWorkspaceName();

      sessionProvider.setCurrentRepository(repository);
      sessionProvider.setCurrentWorkspace(workspace);
      return sessionProvider;
    } catch (RepositoryException e) {
      throw new IllegalStateException("Can't build a SessionProvider", e);
    }
  }

  public static long getUserIdentityId(IdentityManager identityManager, String username) {
    if (StringUtils.equals(IdentityConstants.ANONIM, username)
        || StringUtils.equals(IdentityConstants.SYSTEM, username)
        || StringUtils.equals(IdentityConstants.ANY, username)) {
      return 0;
    }
    org.exoplatform.social.core.identity.model.Identity identity = identityManager.getOrCreateUserIdentity(username);
    if (identity != null) {
      return Long.parseLong(identity.getId());
    }
    return 0;
  }

}
