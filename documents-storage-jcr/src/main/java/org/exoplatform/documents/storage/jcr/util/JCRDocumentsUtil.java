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
package org.exoplatform.documents.storage.jcr.util;

import java.util.*;

import javax.jcr.*;

import com.ibm.icu.text.Transliterator;
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

  public static final String                           USER_PRIVATE_ROOT_NODE           = "Private";

  public static final String                           USER_PUBLIC_ROOT_NODE           = "Public";

  protected static final Map<DocumentSortField, String> SORT_FIELDS_ES_CORRESPONDING  = new EnumMap<>(DocumentSortField.class);

  protected static final Map<DocumentSortField, String> SORT_FIELDS_JCR_CORRESPONDING = new EnumMap<>(DocumentSortField.class);
  static {
    SORT_FIELDS_JCR_CORRESPONDING.put(DocumentSortField.NAME, NodeTypeConstants.EXO_TITLE);
    SORT_FIELDS_JCR_CORRESPONDING.put(DocumentSortField.CREATED_DATE, NodeTypeConstants.EXO_DATE_CREATED);
    SORT_FIELDS_JCR_CORRESPONDING.put(DocumentSortField.MODIFIED_DATE, NodeTypeConstants.EXO_DATE_MODIFIED);

    SORT_FIELDS_ES_CORRESPONDING.put(DocumentSortField.NAME, "title");
    SORT_FIELDS_ES_CORRESPONDING.put(DocumentSortField.CREATED_DATE, "createdDate");
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
                                           Session session,
                                           SpaceService spaceService,
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
      String sourceID = "";
      String sourceMimeType = "";
      Node node = nodeIterator.nextNode();

      try {
        if(node.isNodeType(NodeTypeConstants.EXO_SYMLINK)){
          sourceID = node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
          Node sourceNode = getNodeByIdentifier(session, sourceID);
          if(sourceNode==null || sourceNode.isNodeType(NodeTypeConstants.NT_FOLDER) || sourceNode.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED)){
            break;
          }
          sourceMimeType = getMimeType(sourceNode);
        }
      } catch (RepositoryException repositoryException) {
        LOG.warn("Cannot check if the current node is a symlink");
      }
      FileNode fileNode = toFileNode(identityManager, aclIdentity, node,sourceID, spaceService);
      if(StringUtils.isNotBlank(sourceMimeType)){
        fileNode.setMimeType(sourceMimeType);
      }
      fileNodes.add(fileNode);
      size++;
      if (size >= limit) {
        return fileNodes;
      }
    }
    return fileNodes;
  }

  public static List<AbstractNode> toNodes(IdentityManager identityManager,
                                           Session session,
                                           NodeIterator nodeIterator,
                                           Identity aclIdentity,
                                           SpaceService spaceService) {
    List<AbstractNode> fileNodes = new ArrayList<>();
    while (nodeIterator.hasNext()) {
      Node node = nodeIterator.nextNode();
      String sourceID = "";
      Node sourceNode = null;
      try {
        if(node.isNodeType(NodeTypeConstants.EXO_SYMLINK)){
          sourceID = node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
          sourceNode = getNodeByIdentifier(session, sourceID);
          if(sourceNode==null){
            break;
          }
          if(sourceNode.isNodeType(NodeTypeConstants.NT_FOLDER) || sourceNode.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED)){
            FolderNode folderNode = toFolderNode(identityManager, aclIdentity, node, sourceID,spaceService);
            fileNodes.add(folderNode);
          }
          if(sourceNode.isNodeType(NodeTypeConstants.NT_FILE)) {
            FileNode fileNode = toFileNode(identityManager, aclIdentity, node, sourceID, spaceService);
            fileNode.setMimeType(getMimeType(sourceNode));
            fileNodes.add(fileNode);
          }
        } else{
          if(node.isNodeType(NodeTypeConstants.NT_FOLDER) || node.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED)){
            FolderNode folderNode = toFolderNode(identityManager, aclIdentity, node, sourceID,spaceService);
            fileNodes.add(folderNode);
          }
          if(node.isNodeType(NodeTypeConstants.NT_FILE)) {
            FileNode fileNode = toFileNode(identityManager, aclIdentity, node, sourceID, spaceService);
            fileNodes.add(fileNode);
          }
        }
      } catch (RepositoryException e) {
        LOG.warn("Error getting Folder Node for search result with path {}", node, e);
      }
    }

    return fileNodes;
  }

  public static FolderNode toFolderNode(IdentityManager identityManager,
                                 Identity aclIdentity,
                                 Node node,
                                 String sourceID,
                                 SpaceService spaceService) {
    try {
      if (node == null) {
        return null;
      }
      FolderNode folderNode = new FolderNode();
      if(StringUtils.isNotBlank(sourceID)){
        folderNode.setSourceID(sourceID);
      }
      folderNode.setDatasource(JCR_DATASOURCE_NAME);
      folderNode.setPath(node.getPath());
      retrieveFileProperties(identityManager, node, aclIdentity, folderNode, spaceService);

      return folderNode;
    } catch (Exception e) {
      try {
        LOG.warn("Error computing Folder Node for search result with path {}", node.getPath(), e);
      } catch (Exception e1) {
        LOG.warn("Error computing Folder Node for search result with path {}", node, e);
      }
      return null;
    }
  }

  public static FileNode toFileNode(IdentityManager identityManager,
                                    Session session,
                                    Identity aclIdentity,
                                    SearchResult searchResult,
                                    SpaceService spaceService) {
    DocumentFileSearchResult fileSearchResult = (DocumentFileSearchResult) searchResult;
    try {
      FileNode fileNode = new FileNode();
      Node node = getNode(session, fileNode, fileSearchResult);
      toFileNode(identityManager, aclIdentity, node, fileNode, spaceService);
      return fileNode;
    } catch (Exception e) {
      LOG.warn("Error computing File Node for search result with id {}", fileSearchResult.getId(), e);
      return null;
    }
  }

  public static FileNode toFileNode(IdentityManager identityManager,
                                    Identity aclIdentity,
                                    Node node,
                                    String sourceID,
                                    SpaceService spaceService) {
    if (node == null) {
      return null;
    }
    FileNode fileNode = new FileNode();
    if(StringUtils.isNotBlank(sourceID)){
      fileNode.setSourceID(sourceID);
    }
    toFileNode(identityManager, aclIdentity, node, fileNode , spaceService);
    return fileNode;
  }

  public static void toFileNode(IdentityManager identityManager,
                                Identity aclIdentity,
                                Node node,
                                FileNode fileNode,
                                SpaceService spaceService) {
    try {
      fileNode.setDatasource(JCR_DATASOURCE_NAME);
      retrieveFileProperties(identityManager, node, aclIdentity, fileNode, spaceService);
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
                                            AbstractNode documentNode,
                                            SpaceService spaceService) throws RepositoryException {
    documentNode.setId(((NodeImpl) node).getIdentifier());
    documentNode.setPath(((NodeImpl) node).getPath());

    try {
      Node parent = node.getParent();
      if(parent != null){
        documentNode.setParentFolderId(((NodeImpl) parent).getIdentifier());
      }
    } catch (RepositoryException repositoryException) {
      //Do noting, it means that the current user don't have access to the parent node
    }

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
    computeDocumentAcl(node, documentNode, aclIdentity,identityManager, spaceService);
  }

  public static void retrieveFileContentProperties(Node content, FileNode fileNode) throws RepositoryException {
    if (content.hasProperty(NodeTypeConstants.DC_DESCRIPTION)) {
      fileNode.setDescription(content.getProperty(NodeTypeConstants.DC_DESCRIPTION).getValues()[0].getString());
    }
    if (content.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)) {
      fileNode.setMimeType(content.getProperty(NodeTypeConstants.JCR_MIME_TYPE).getString());
    }
    if (content.hasProperty(NodeTypeConstants.JCR_DATA)) {
      fileNode.setSize(content.getProperty(NodeTypeConstants.JCR_DATA).getLength());
    }
  }

  public static void computeDocumentAcl(Node node, AbstractNode documentNode, Identity aclIdentity, IdentityManager identityManager, SpaceService spaceService) throws RepositoryException {
    boolean canEdit = false;
    boolean canDelete = false;
    List<PermissionEntry> permissions = new ArrayList<>();
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
      if(membershipEntry!=null && StringUtils.isNotEmpty(membershipEntry.getGroup())){
        Space space = spaceService.getSpaceByGroupId(membershipEntry.getGroup());
        if(space!=null){
          org.exoplatform.social.core.identity.model.Identity identity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
          if(identity!=null){
            permissions.add(new PermissionEntry(identity, accessControlEntry.getPermission(),getPermissionRole(accessControlEntry.getMembershipEntry().getMembershipType())));
          }
        }
      } else{
        org.exoplatform.social.core.identity.model.Identity identity = identityManager.getOrCreateUserIdentity(nodeAclIdentity);
        if(identity!=null){
          permissions.add(new PermissionEntry(identity, accessControlEntry.getPermission(),PermissionRole.ALL.name()));
        }
      }

    }
    documentNode.setAcl(new NodePermission(true, canEdit, canDelete, permissions,null));
  }

  private static String getPermissionRole (String membershipType){
    if(membershipType.equals("manager") || membershipType.equals("redactor") ){
      return PermissionRole.MANAGERS_REDACTORS.name();
    }
    return PermissionRole.ALL.name();
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
      SessionProvider systemSession = SessionProvider.createSystemProvider();
      Node identityNode = nodeHierarchyCreator.getUserNode(systemSession, ownerIdentity.getRemoteId());
      Session session = sessionProvider.getSession(sessionProvider.getCurrentWorkspace(), sessionProvider.getCurrentRepository());
      if(username.equals(ownerIdentity.getRemoteId())){
        String privatePathNode = identityNode.getPath()+"/"+USER_PRIVATE_ROOT_NODE;
        if (session.itemExists(privatePathNode)) {
          identityRootNode = (Node) session.getItem(privatePathNode);
        }
      }else{
        String publicPathNode = identityNode.getPath()+"/"+USER_PUBLIC_ROOT_NODE;
        identityRootNode = (Node) session.getItem(publicPathNode);
      }

    }
    return identityRootNode;
  }
  public static Node getIdentityRootNode(SpaceService spaceService,
                                         NodeHierarchyCreator nodeHierarchyCreator,
                                         org.exoplatform.social.core.identity.model.Identity ownerIdentity,
                                         Session session) throws Exception {
    Node identityRootNode = null;
    if (ownerIdentity.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(ownerIdentity.getRemoteId());
      identityRootNode = getGroupNode(nodeHierarchyCreator, session, space.getGroupId());
    } else if (ownerIdentity.isUser()) {
      SessionProvider systemSession = SessionProvider.createSystemProvider();
      Node identityNode = nodeHierarchyCreator.getUserNode(systemSession, ownerIdentity.getRemoteId());
      String privatePathNode = identityNode.getPath()+"/"+USER_PRIVATE_ROOT_NODE;
        if (session.itemExists(privatePathNode)) {
          identityRootNode = (Node) session.getItem(privatePathNode);
        }
     }
    return identityRootNode;
  }

  public static String getSortDirection(DocumentNodeFilter filter) {
    return filter.isAscending() ? "ASC" : "DESC";
  }

  public static String getSortField(DocumentNodeFilter filter, boolean isJcr) {
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

  /**
   * Clean string.
   *
   * @param str the str
   *
   * @return the string
   */
  public static String cleanString(String str) {
    Transliterator accentsconverter = Transliterator.getInstance("Latin; NFD; [:Nonspacing Mark:] Remove; NFC;");
    str = accentsconverter.transliterate(str);
    //the character ? seems to not be changed to d by the transliterate function
    StringBuilder cleanedStr = new StringBuilder(str.trim());
    // delete special character
    int strLength = cleanedStr.length();
    int i = 0;
    while (i < strLength) {
      char c = cleanedStr.charAt(i);
      if (c == '/' || c == ':' || c == '[' || c == ']' || c == '*' || c == '\'' || c == '"' || c == '|' || c == '??' || c == '??' || c == '.') {
        cleanedStr.deleteCharAt(i);
        cleanedStr.insert(i, '_');
      } else if (!(Character.isLetterOrDigit(c) || Character.isWhitespace(c) || c == '-' || c == '_')) {
        cleanedStr.deleteCharAt(i);
        strLength = cleanedStr.length();
        continue;
      }
      i++;
    }
    while (org.apache.commons.lang.StringUtils.isNotEmpty(cleanedStr.toString()) && !Character.isLetterOrDigit(cleanedStr.charAt(0))) {
      cleanedStr.deleteCharAt(0);
    }
    String clean = cleanedStr.toString();
    if (clean.endsWith("-")) {
      clean = clean.substring(0, clean.length()-1);
    }

    return clean;
  }

  public static String getMimeType(Node node) {
    try {
      if (node.getPrimaryNodeType().getName().equals(NodeTypeConstants.NT_FILE) && node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
          return node.getNode(NodeTypeConstants.JCR_CONTENT)
                  .getProperty(NodeTypeConstants.JCR_MIME_TYPE)
                  .getString();
      }
    } catch (RepositoryException e) {
      LOG.error(e.getMessage(), e);
    }
    return "";
  }


}
