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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.jcr.*;
import javax.jcr.version.Version;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.legacy.search.data.SearchResult;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.documents.storage.jcr.search.DocumentFileSearchResult;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.*;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.security.*;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
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

  private static final String                          SPACE_PATH_PREFIX = "/Groups/spaces/";

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
                                           boolean includeHiddenFiles) throws RepositoryException {
    List<FileNode> fileNodes = new ArrayList<>();
    JCRDeleteFileStorage jCRDeleteFileStorage =  CommonsUtils.getService(JCRDeleteFileStorage.class);
    Map<String, String> documetsToDelete = jCRDeleteFileStorage.getDocumentsToDelete();
    while (nodeIterator.hasNext()) {
      String sourceID = "";
      String sourceMimeType = "";
      Node node = nodeIterator.nextNode();
      // Check if the node is in the queue of documents to be deleted
      if(documetsToDelete.containsKey(((NodeImpl) node).getIdentifier())) continue;
      try {
        Node sourceNode = null;
        if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
          sourceID = node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
          sourceNode = getNodeByIdentifier(session, sourceID);
          if (sourceNode == null) {
            continue;
          }
          if (sourceNode.isNodeType(NodeTypeConstants.NT_FOLDER) || sourceNode.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED)) {
            //if the link is inside its source folder, we ignore its content
            if(node.getPath().contains(sourceNode.getPath())) {
              continue;
            }
            List<FileNode> files = toFileNodes(identityManager, sourceNode.getNodes(), aclIdentity, session, spaceService, includeHiddenFiles);
            if (!files.isEmpty()) {
              fileNodes.addAll(files);
            }
          } else {
            if (sourceNode.isNodeType(NodeTypeConstants.EXO_HIDDENABLE) && !includeHiddenFiles) {
              continue;
            }
            sourceMimeType = getMimeType(sourceNode);
            FileNode fileNode = toFileNode(identityManager, aclIdentity, node, sourceID, spaceService);
            if (StringUtils.isNotBlank(sourceMimeType)) {
              fileNode.setMimeType(sourceMimeType);
            }
            if (StringUtils.isNotBlank(fileNode.getMimeType())) {
              fileNodes.add(fileNode);
            }
          }
        } else {
          if (node.isNodeType(NodeTypeConstants.EXO_HIDDENABLE) && !includeHiddenFiles) {
            continue;
          }
          FileNode fileNode = toFileNode(identityManager, aclIdentity, node, sourceID, spaceService);
          if (StringUtils.isNotBlank(fileNode.getMimeType())) {
            fileNodes.add(fileNode);
          }
        }
      } catch (RepositoryException repositoryException) {
        LOG.warn("Cannot check if the current node is a symlink");
      }
    }
    return fileNodes;
  }

  public static List<AbstractNode> toNodes(IdentityManager identityManager,
                                           Session session,
                                           NodeIterator nodeIterator,
                                           Identity aclIdentity,
                                           SpaceService spaceService,
                                           boolean includeHiddenFiles,
                                           DocumentFolderFilter filter) {
    List<AbstractNode> fileNodes = new ArrayList<>();
    while (nodeIterator.hasNext()) {
      Node node = nodeIterator.nextNode();
      String sourceID = "";
      Node sourceNode = node;
      try {
        if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
          sourceID = node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
          sourceNode = getNodeByIdentifier(session, sourceID);
          if (sourceNode == null) {
            continue;
          }
        }
        if ((sourceNode.isNodeType(NodeTypeConstants.NT_FOLDER) || sourceNode.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED))
            && (!(node.isNodeType(NodeTypeConstants.EXO_HIDDENABLE) || includeHiddenFiles))) {
          FolderNode folderNode = toFolderNode(identityManager, aclIdentity, node, sourceID, spaceService);
          fileNodes.add(folderNode);
        }
        if ((sourceNode.isNodeType(NodeTypeConstants.NT_FILE))
            && (!(node.isNodeType(NodeTypeConstants.EXO_HIDDENABLE) || includeHiddenFiles))) {
          FileNode fileNode = toFileNode(identityManager, aclIdentity, node, sourceID, spaceService);
          fileNode.setMimeType(getMimeType(sourceNode));
          fileNodes.add(fileNode);
        }
      } catch (RepositoryException e) {
        LOG.warn("Error getting Folder Node for search result with path {}", node, e);
      }
    }

    fileNodes.sort((o1, o2) -> {
      if ((o1.isFolder() && o2.isFolder()) || (!o1.isFolder() && !o2.isFolder())) {
        if(filter.getSortField().equals(DocumentSortField.MODIFIED_DATE)) {
          if(filter.isAscending()) {
            return (int) (o1.getModifiedDate() - o2.getModifiedDate());
          } else {
            return (int) (o2.getModifiedDate() - o1.getModifiedDate());
          }
        } else if(filter.getSortField().equals(DocumentSortField.CREATED_DATE)) {
          if (filter.isAscending()) {
            return (int) (o1.getCreatedDate() - o2.getCreatedDate());
          } else {
            return (int) (o2.getCreatedDate() - o1.getCreatedDate());
          }
        } else {
          if(filter.isAscending()) {
            return o1.getName().compareTo(o2.getName());
          } else {
            return o2.getName().compareTo(o1.getName());
          }
        }
      } else if (o1.isFolder()) {
        return -1;
      } else {
        return 1;
      }
    });
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
      folderNode.setCloudDriveFolder(node.hasProperty("ecd:connected"));

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
      fileNode.setCloudDriveFile(node.hasProperty("ecd:driveUUID"));
      retrieveFileProperties(identityManager, node, aclIdentity, fileNode, spaceService);
      if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        retrieveSymlinkSize(node, fileNode);
      }
      retrieveViewsProperty(node, fileNode);
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
  
  private static void retrieveViewsProperty(Node node, FileNode fileNode) throws RepositoryException {
    long views = 0L;
    if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
      node = getNodeByIdentifier(node.getSession(), fileNode.getSourceID());
    }
    if (node != null && node.hasProperty(NodeTypeConstants.DOCUMENT_VIEWS_PROPERTY)) {
      views = node.getProperty(NodeTypeConstants.DOCUMENT_VIEWS_PROPERTY).getLong();
    }
    fileNode.setViews(views);
  }
  
  private static void retrieveSymlinkSize(Node node, FileNode fileNode) throws RepositoryException {
    Node source = getNodeByIdentifier(node.getSession(), fileNode.getSourceID());
    if (source != null && source.getNode(NodeTypeConstants.JCR_CONTENT) != null) {
      Node content = source.getNode(NodeTypeConstants.JCR_CONTENT);
      if (content.hasProperty(NodeTypeConstants.JCR_DATA)) {
        fileNode.setSize(content.getProperty(NodeTypeConstants.JCR_DATA).getLength());
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
    documentNode.setPath(node.getPath());

    try {
      Node parent = node.getParent();
      if(parent != null){
        documentNode.setParentFolderId(((NodeImpl) parent).getIdentifier());
      }
    } catch (RepositoryException repositoryException) {
      //Do noting, it means that the current user don't have access to the parent node
    }

    Node versionNode = node;
    if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
      Node sourceNode = getNodeByIdentifier(node.getSession(), documentNode.getSourceID());
      if (sourceNode != null) {
        versionNode = sourceNode;
      }
    }
    if (versionNode.isNodeType(NodeTypeConstants.MIX_VERSIONABLE) && versionNode.getBaseVersion() != null) {
      documentNode.setVersionable(true);
      Version version = versionNode.getBaseVersion();
      if (StringUtils.isNumeric(version.getName())) {
        documentNode.setVersionNumber(version.getName());
      }
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
      Node nodeToModify = node;
      if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        RepositoryService repositoryService = CommonsUtils.getService(RepositoryService.class);
        SessionProvider sessionProvider = SessionProvider.createSystemProvider();
        ManageableRepository repository = repositoryService.getCurrentRepository();
        Session systemSession = sessionProvider.getSession(repository.getConfiguration().getDefaultWorkspaceName(), repository);
        String sourceNodeId = node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
        Node sourceNode = getNodeByIdentifier(systemSession, sourceNodeId);
        if (sourceNode != null && sourceNode.getProperty(NodeTypeConstants.EXO_DATE_MODIFIED).getDate().compareTo(node.getProperty(NodeTypeConstants.EXO_DATE_MODIFIED).getDate()) > 0) {
          nodeToModify = sourceNode;
        }
      }
      long modifiedDate = nodeToModify.getProperty(NodeTypeConstants.EXO_DATE_MODIFIED)
              .getDate()
              .getTimeInMillis();
      documentNode.setModifiedDate(modifiedDate);
      String modifier = nodeToModify.getProperty(NodeTypeConstants.EXO_LAST_MODIFIER).getString();
      documentNode.setModifierId(getUserIdentityId(identityManager, modifier));
    } else {
      documentNode.setModifiedDate(documentNode.getCreatedDate());
      documentNode.setModifierId(documentNode.getCreatorId());
    }
    if (node.hasProperty(NodeTypeConstants.DC_DESCRIPTION)) {
      try {
        documentNode.setDescription(node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getString());
      } catch (ValueFormatException e) {
        Value[] descriptionValues = node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getValues();
        if(descriptionValues != null && descriptionValues.length > 0) {
          documentNode.setDescription(descriptionValues[0].getString());
        }
      }
    }
    if (node.isNodeType(NodeTypeConstants.DC_DESCRIPTION)) {
      documentNode.setDescription(node.getProperty(NodeTypeConstants.DC_DESCRIPTION).getString());
    }
    computeDocumentAcl(node, documentNode, aclIdentity,identityManager, spaceService);
  }

  public static void retrieveFileContentProperties(Node content, FileNode fileNode) throws RepositoryException {
    if (content.hasProperty(NodeTypeConstants.DC_DESCRIPTION)
        && content.getProperty(NodeTypeConstants.DC_DESCRIPTION).getValues().length > 0) {
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
        } else if (groupToIdentity(membershipEntry.getGroup()) != null) {
          UserACL userACL = CommonsUtils.getService(UserACL.class);
          if (membershipEntry.getGroup().equals(userACL.getAdminGroups()) && !aclIdentity.isMemberOf(membershipEntry)) {
            continue;
          }
          permissions.add(new PermissionEntry(groupToIdentity(membershipEntry.getGroup()), accessControlEntry.getPermission(),PermissionRole.ALL.name()));
        }
      } else{
        org.exoplatform.social.core.identity.model.Identity identity = identityManager.getOrCreateUserIdentity(nodeAclIdentity);
        if(identity!=null){
          permissions.add(new PermissionEntry(identity, accessControlEntry.getPermission(),PermissionRole.ALL.name()));
        }
      }

    }
    documentNode.setAcl(new NodePermission(true, canEdit, canDelete, permissions,null, null));
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
   * @param oldName the str
   *
   * @return the string
   */
  public static String cleanName(String oldName) {
    return cleanName(oldName, NodeTypeConstants.NT_FILE);
  }
  public static String cleanName(String oldName, String nodeType) {
    if (org.apache.commons.lang.StringUtils.isEmpty(oldName)) return oldName;
    String extension = "" ;
    if(nodeType.equals(NodeTypeConstants.NT_FILE) && oldName.lastIndexOf(".") > -1){
      extension = oldName.substring(oldName.lastIndexOf("."));
      oldName = oldName.substring(0,oldName.lastIndexOf(".")) ;
    }
    oldName = oldName.trim();
    String specialChar = "&#*@.'\"\t\r\n$\\><:;[]/|’";
    StringBuilder ret = new StringBuilder();
    for (int i = 0; i < oldName.length(); i++) {
      char currentChar = oldName.charAt(i);
      if (specialChar.indexOf(currentChar) > -1) {
        ret.append('_');
      } else {
        ret.append(currentChar);
      }
    }
    ret.append(extension);
    return ret.toString();
  }

  public static boolean isValidDocumentTitle(String name) {
    if (StringUtils.isBlank(FilenameUtils.getBaseName(name))) {
      return false;
    }
    Pattern regex = Pattern.compile("[<\\\\>:\"/|?*]");
    Matcher matcher = regex.matcher(name);
    return !matcher.find();
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

  public static org.exoplatform.social.core.identity.model.Identity getOwnerIdentityFromNodePath(String path, IdentityManager identityManager, SpaceService spaceService){
    org.exoplatform.social.core.identity.model.Identity identity = null;
    if (path.contains(SPACE_PATH_PREFIX)) {
      String[] pathParts = path.split(SPACE_PATH_PREFIX)[1].split("/");
      String groupId = "/spaces/" + pathParts[0];
      Space space = spaceService.getSpaceByGroupId(groupId);
      if(space != null){
        identity = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
      }
    } else if(path.contains(USER_PRIVATE_ROOT_NODE)) {
      String[] pathParts = path.split(USER_PRIVATE_ROOT_NODE)[0].split("/");
      String userName = pathParts[pathParts.length-1];
      identity = identityManager.getOrCreateUserIdentity(userName);
    } else if(path.contains(USER_PUBLIC_ROOT_NODE)) {
      String[] pathParts = path.split(USER_PUBLIC_ROOT_NODE)[0].split("/");
      String userName = pathParts[pathParts.length-1];
      identity = identityManager.getOrCreateUserIdentity(userName);
    }
    return identity;
  }

  public static FileVersion toFileVersion(Version version, Node node, IdentityManager identityManager) throws RepositoryException {
    FileVersion versionFileNode = new FileVersion();
    String currentVersionName = node.getBaseVersion().getName();
    Node frozen = version.getNode(NodeTypeConstants.JCR_FROZEN_NODE);
    if (node.hasProperty(NodeTypeConstants.EXO_TITLE)) {
      versionFileNode.setTitle(Utils.getStringProperty(node, NodeTypeConstants.EXO_TITLE));
    } else {
      versionFileNode.setTitle(node.getName());
    }
    String userName = frozen.getProperty(NodeTypeConstants.EXO_LAST_MODIFIER).getValue().getString();
    Profile profile = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, userName).getProfile();
    String[] summary = node.getVersionHistory().getVersionLabels(version);
    if (summary.length > 0) {
      versionFileNode.setSummary(summary[0]);
    }
    versionFileNode.setId(version.getUUID());
    versionFileNode.setFrozenId(frozen.getUUID());
    versionFileNode.setOriginId(node.getUUID());
    versionFileNode.setAuthor(userName);
    versionFileNode.setAuthorFullName(profile.getFullName());
    versionFileNode.setCreatedDate(version.getCreated().getTime());
    versionFileNode.setVersionNumber(Integer.parseInt(version.getName()));
    if (version.getName().equals(currentVersionName)) {
      versionFileNode.setCurrent(true);
    }
    return versionFileNode;
  }

  public static String increaseNameIndex(String origin, int count) {
    int index = origin.indexOf('.');
    if (index == -1) {
      return origin + "(" + count + ")";
    }
    return origin.substring(0, index) + "(" + count + ")" + origin.substring(index);
  }

  public static String getNewIndexedName(String exoTitle, String newNameSuffix) {
    int pointIndex = exoTitle.lastIndexOf(".");
    String extension = pointIndex != -1 ? exoTitle.substring(pointIndex) : "";
    exoTitle = pointIndex != -1 ? exoTitle.substring(0, pointIndex).concat(newNameSuffix).concat(extension)
                                : exoTitle.concat(newNameSuffix);
    return exoTitle;
  }

  public static boolean isFolder(Node node) throws RepositoryException {
    return node.isNodeType(NodeTypeConstants.NT_FOLDER) || node.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED);
  }

  public static boolean hasEditPermission(Session session, Node node) {
    try {
      session.checkPermission(node.getPath(), PermissionType.SET_PROPERTY);
    } catch (Exception e) {
      return false;
    }
    return true;
  }
  /*
  * Build a group to identity model to display it on the manage access drawer collaborators .
  * Like the built model by the identity suggester for group suggester .
  */
  public static org.exoplatform.social.core.identity.model.Identity groupToIdentity(String groupId){

    OrganizationService organizationService = CommonsUtils.getService(OrganizationService.class);
    try {
      Group group = organizationService.getGroupHandler().findGroupById(groupId);
      org.exoplatform.social.core.identity.model.Identity identity = new org.exoplatform.social.core.identity.model.Identity();
      Profile profile = new Profile();
      profile.setProperty("fullName", group.getLabel());
      identity.setId("group:"+group.getGroupName());
      identity.setRemoteId(groupId);
      identity.setProviderId("group");
      identity.setProfile(profile);
      return identity;
    } catch (Exception e){
      return null ;
    }
  }
  
  public static DownloadItem toDownloadItem(Node node) throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = null;
    String mimeType = null;
    if (node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
      Node content = node.getNode(NodeTypeConstants.JCR_CONTENT);
      if (content != null && content.hasProperty(NodeTypeConstants.JCR_DATA)) {
        InputStream inputStream = content.getProperty(NodeTypeConstants.JCR_DATA).getStream();
        byteArrayOutputStream = write(inputStream);
        mimeType = Utils.getStringProperty(content, NodeTypeConstants.JCR_MIME_TYPE);
      }
    }
    return new DownloadItem(((NodeImpl) node).getIdentifier(),
                            Utils.getStringProperty(node, NodeTypeConstants.EXO_TITLE),
                            byteArrayOutputStream,
                            mimeType);
  }

  private static ByteArrayOutputStream write(InputStream is) throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    int length;
    byte[] buffer = new byte[1024];
    while ((length = is.read(buffer, 0, buffer.length)) != -1) {
      outputStream.write(buffer, 0, length);
    }
    return outputStream;
  }

  public static void createFile(Node node,
                                 String symlinkPath,
                                 String sourcePath,
                                 String tempFolderPath,
                                 String parentPath) throws RepositoryException, IOException {
    if (node == null) {
      return;
    }
    Node jrcNode = node.getNode("jcr:content");
    InputStream inputStream = jrcNode.getProperty("jcr:data").getStream();
    String path = "";
    String nodePath = node.getPath();
    if (StringUtils.isNotEmpty(symlinkPath) || StringUtils.isNotEmpty(sourcePath)) {
      nodePath = symlinkPath + nodePath.replace(sourcePath, "");
    }
    path = tempFolderPath + nodePath.replace(parentPath, "");
    File file = new File(path);
    try (OutputStream outputStream = new FileOutputStream(file)) {
      byte[] buffer = new byte[1024];
      int length;
      while ((length = inputStream.read(buffer)) > 0) {
        outputStream.write(buffer, 0, length);
      }
      inputStream.close();
    }
  }

  public static void createTempFilesAndFolders(Node node,
                                               String symlinkPath,
                                               String sourcePath,
                                               String tempFolderPath,
                                               String parentPath) throws Exception {
    if (node == null) {
      return;
    }
    if (JCRDocumentsUtil.isFolder(node)) {
      String nodePath = node.getPath();
      if (StringUtils.isNotEmpty(symlinkPath) || StringUtils.isNotEmpty(sourcePath)) {
        nodePath = symlinkPath + nodePath.replace(sourcePath, "");
      }
      String path = tempFolderPath + nodePath.replace(parentPath, "");
      Files.createDirectories(Paths.get(path));
      NodeIterator nodeIterator = node.getNodes();
      while (nodeIterator.hasNext()) {
        Node child = nodeIterator.nextNode();
        createTempFilesAndFolders(child, symlinkPath, sourcePath, tempFolderPath, parentPath);
      }
    } else {
      if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        String sourceID = node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
        Node sourceNode = JCRDocumentsUtil.getNodeByIdentifier(node.getSession(), sourceID);
        if (sourceNode != null) {
          createTempFilesAndFolders(sourceNode, node.getPath(), sourceNode.getPath(), tempFolderPath, parentPath);
        }
      } else {
        createFile(node, symlinkPath, sourcePath, tempFolderPath, parentPath);
      }
    }
  }

  public static void cleanFiles(File file) throws IOException {
    File[] files = file.listFiles();
    if (files != null) {
      for (File f : files) {
        cleanFiles(f);
      }
    }
    Files.delete(file.toPath());
  }

  public static void zipFiles(String zipFilePath, String tempFolderPath) throws Exception {
    try (FileOutputStream fos = new FileOutputStream(zipFilePath)) {
      try (ZipOutputStream zos = new ZipOutputStream(fos)) {
        File folder = new File(tempFolderPath);
        zipFolder(folder, "", zos);
      }
    }
  }

  private static String getFolderName(File file, String folderName) {
    return StringUtils.isNotEmpty(folderName) ? folderName + "/" + file.getName() : file.getName();
  }
  
  private static void zipFolder(File folder, String folderName, ZipOutputStream zipOutputStream) throws Exception {
    File[] files = folder.listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        zipFolder(file, getFolderName(file, folderName), zipOutputStream);
      } else {
        byte[] buffer = new byte[1024];
        try (FileInputStream fis = new FileInputStream(file)) {
          zipOutputStream.putNextEntry(new ZipEntry(getFolderName(file, folderName)));
          int length;
          while ((length = fis.read(buffer)) > 0) {
            zipOutputStream.write(buffer, 0, length);
          }
          zipOutputStream.closeEntry();
        }
      }
    }
  }
}
