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

import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.DOCUMENT_CATEGORY_IDS;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_DATE_MODIFIED;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.EXO_LAST_MODIFIED_DATE;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_CONTENT;
import static org.exoplatform.documents.storage.jcr.util.NodeTypeConstants.JCR_LAST_MODIFIED;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.jcr.*;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.ibm.icu.text.Transliterator;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.HTMLSanitizer;
import org.exoplatform.documents.constant.DocumentSortField;
import org.exoplatform.documents.legacy.search.data.SearchResult;
import org.exoplatform.documents.model.*;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.documents.storage.jcr.search.DocumentFileSearchResult;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.RepositoryServiceImpl;
import org.exoplatform.services.jcr.impl.WorkspaceContainer;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.util.Text;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.services.security.MembershipEntry;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import lombok.SneakyThrows;

public class JCRDocumentsUtil {
  private static final Log                              LOG                           =
                                                            ExoLogger.getLogger(JCRDocumentsUtil.class);

  private static final String                           DEFAULT_GROUPS_HOME_PATH      = "/Groups";                             // NOSONAR

  private static final String                           DEFAULT_USERS_HOME_PATH       = "/Users";                              // NOSONAR

  public static final String                            GROUPS_PATH_ALIAS             = "groupsPath";

  public static final String                            USERS_PATH_ALIAS              = "usersPath";

  public static final String                            DOCUMENTS_NODE                = "Documents";

  private static final String                           JCR_DATASOURCE_NAME           = "jcr";

  public static final String                           USER_PRIVATE_ROOT_NODE           = "Private";

  public static final String                           USER_PUBLIC_ROOT_NODE           = "Public";

  private static final String                          SPACE_PATH_PREFIX = "/Groups/spaces/";

  public static final String MIX_VERSIONABLE  = "mix:versionable";


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

  private static String usersPath = null;

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

  /**
   * @return the JCR path holding every user's home node, e.g. {@code /Users}.
   */
  public static String getUsersPath() {
    if (usersPath != null) {
      return usersPath;
    }
    NodeHierarchyCreator nodeHierarchyCreator = CommonsUtils.getService(NodeHierarchyCreator.class);
    usersPath = nodeHierarchyCreator == null ? null : nodeHierarchyCreator.getJcrPath(USERS_PATH_ALIAS);
    if (StringUtils.isBlank(usersPath)) {
      usersPath = DEFAULT_USERS_HOME_PATH;
    }
    return usersPath;
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
            && (!node.isNodeType(NodeTypeConstants.EXO_HIDDENABLE) || includeHiddenFiles)) {
          FolderNode folderNode = toFolderNode(identityManager, aclIdentity, node, sourceID, spaceService);
          fileNodes.add(folderNode);
        }
        if ((sourceNode.isNodeType(NodeTypeConstants.NT_FILE))
            && (!node.isNodeType(NodeTypeConstants.EXO_HIDDENABLE) || includeHiddenFiles)) {
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
        if (filter.getSortField().equals(DocumentSortField.MODIFIED_DATE)) {
          Date modifiedDateO1 = new Date(o1.getModifiedDate());
          Date modifiedDateO2 = new Date(o2.getModifiedDate());
          if (filter.isAscending()) {
            return modifiedDateO1.compareTo(modifiedDateO2);
          } else {
            return modifiedDateO1.compareTo(modifiedDateO1);
          }
        } else if (filter.getSortField().equals(DocumentSortField.CREATED_DATE)) {
          Date createdDateO1 = new Date(o1.getCreatedDate());
          Date createdDateO2 = new Date(o2.getCreatedDate());
          if (filter.isAscending()) {
            return createdDateO1.compareTo(createdDateO2);
          } else {
            return createdDateO2.compareTo(createdDateO1);
          }
        } else {
          if (filter.isAscending()) {
            return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
          } else {
            return o2.getName().toLowerCase().compareTo(o1.getName().toLowerCase());
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
      if (node == null) {
        return null;
      } else {
        toFileNode(identityManager, aclIdentity, node, fileNode, spaceService);
        return fileNode;
      }
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
      if (node.isNodeType(NodeTypeConstants.NT_RESOURCE)) {
        retrieveFileContentProperties(node, fileNode);
      } else if (node.isNodeType(NodeTypeConstants.NT_FILE)) {
        Node content = node.getNode(NodeTypeConstants.JCR_CONTENT);
        retrieveFileContentProperties(content, fileNode);
      } else if (node.isNodeType(NodeTypeConstants.NT_FROZEN_NODE)) {
        if (node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
          Node content = node.getNode(NodeTypeConstants.JCR_CONTENT);
          retrieveFileContentProperties(content, fileNode);
        }
      } else if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        if (StringUtils.isEmpty(fileNode.getSourceID())) {
          String sourceID = node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
          fileNode.setSourceID(sourceID);
        }
        Node source = getNodeByIdentifier(node.getSession(), fileNode.getSourceID());
        if (source != null) {
          if (source.hasNode(NodeTypeConstants.JCR_CONTENT)) {
            Node content = source.getNode(NodeTypeConstants.JCR_CONTENT);
            retrieveFileContentProperties(content, fileNode);
          }
        }
      }
      retrieveViewsProperty(node, fileNode);
      retrieveCategoryIdsProperty(node, fileNode);
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

  private static void retrieveCategoryIdsProperty(Node node, FileNode fileNode) throws RepositoryException {
    List<Long> idsList = new ArrayList<>();
    if (node != null && node.hasProperty(NodeTypeConstants.DOCUMENT_CATEGORY_IDS)) {
      Value[] categoryIds = node.getProperty(DOCUMENT_CATEGORY_IDS).getValues();
      idsList = Arrays.stream(categoryIds).map(v -> {
        try {
          return v.getLong();
        } catch (RepositoryException e) {
          return null;
        }
      }).filter(Objects::nonNull).collect(Collectors.toList());
    }
    fileNode.setCategoryIds(idsList);
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
      return getNodeByIdentifier(session, targetNodeId);
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
    if (node.isNodeType(NodeTypeConstants.NT_FROZEN_NODE)) {
      documentNode.setVersion(true);
    }

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
    if (node.isNodeType(NodeTypeConstants.EXO_HIDDENABLE)) {
      documentNode.setHidden(true);
    } else {
      documentNode.setHidden(false);
    }
    if (versionNode.isNodeType(NodeTypeConstants.MIX_VERSIONABLE) && versionNode.getBaseVersion() != null) {
      documentNode.setVersionable(true);
      Version version = versionNode.getBaseVersion();
      if (StringUtils.isNumeric(version.getName())) {
        documentNode.setVersionNumber(version.getName());
      }
    }

    documentNode.setName(getTitle(node));
    if (node.hasProperty(NodeTypeConstants.EXO_DATE_CREATED)) {
      long createdDate = node.getProperty(NodeTypeConstants.EXO_DATE_CREATED)
                             .getDate()
                             .getTimeInMillis();
      documentNode.setCreatedDate(createdDate);
    }
    if (node.hasProperty(NodeTypeConstants.EXO_OWNER)) {
      String owner = node.getProperty(NodeTypeConstants.EXO_OWNER).getString();
      documentNode.setCreatorUserName(owner);
      documentNode.setCreatorId(getUserIdentityId(identityManager, owner));
    }
    long lastModifiedDate = getLastModifiedDate(Objects.requireNonNullElse(versionNode, node));
    documentNode.setModifiedDate(lastModifiedDate == 0l ? documentNode.getCreatedDate() : lastModifiedDate);
    String modifierUsername = getLastModifier(Objects.requireNonNullElse(versionNode, node));
    documentNode.setModifierId(modifierUsername == null ? documentNode.getCreatorId() : getUserIdentityId(identityManager, modifierUsername));
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
    if (aclIdentity != null) {
      computeDocumentAcl(node, documentNode, aclIdentity,identityManager, spaceService);
    }
  }

  public static void retrieveFileContentProperties(Node content, FileNode fileNode) throws RepositoryException {
    if (content.hasProperty(NodeTypeConstants.DC_DESCRIPTION)
        && content.getProperty(NodeTypeConstants.DC_DESCRIPTION).getValues().length > 0) {
      String description = HTMLSanitizer.sanitize(content.getProperty(NodeTypeConstants.DC_DESCRIPTION).getValues()[0].getString());
      fileNode.setDescription(description);
    }
    if (content.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)) {
      fileNode.setMimeType(content.getProperty(NodeTypeConstants.JCR_MIME_TYPE).getString());
    } else if (content.hasProperty("jcr:frozenPrimaryType") && content.getProperty("jcr:frozenPrimaryType").getString().equals(NodeTypeConstants.NT_RESOURCE)) {
      if (content.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)) {
        fileNode.setMimeType(content.getProperty(NodeTypeConstants.JCR_MIME_TYPE).getString());
      }
    }
    if (content.hasProperty(NodeTypeConstants.JCR_DATA)) {
      long fileSize = content.getProperty(NodeTypeConstants.JCR_DATA).getLength();
      fileNode.setSize(fileSize);

      boolean computeVersionSize = Boolean.parseBoolean(System.getProperty("documents.content.compute.version.size","true"));

      if (computeVersionSize) {
        long versionsFileSize = computeVersionsSize(content.getParent());
        fileNode.setSizeWithVersions(fileSize+versionsFileSize);
      } else {
        fileNode.setSizeWithVersions(fileSize);
      }

    }
  }

  public static long getLastModifiedDate(Node node) throws RepositoryException { // NOSONAR
    Calendar modifiedCalendar = Stream.of("jcr:content/jcr:lastModified",
                                          "jcr:content/exo:dateModified",
                                          "jcr:content/exo:lastModifiedDate",
                                          "jcr:lastModified",
                                          "exo:dateModified",
                                          "exo:lastModifiedDate")
                                      .filter(p -> hasProperty(node, p))
                                      .map(p -> getPropertyDate(node, p))
                                      .findFirst()
                                      .orElse(null);
    return modifiedCalendar == null ? 0l : modifiedCalendar.getTimeInMillis();
  }

  public static String getLastModifierOrCreator(Node node) throws RepositoryException {
    String lastModifier = getLastModifier(node);
    if (lastModifier != null) {
      return lastModifier;
    } else if (node.hasProperty(NodeTypeConstants.EXO_OWNER)) {
      return node.getProperty(NodeTypeConstants.EXO_OWNER).getString();
    } else {
      return null;
    }
  }

  public static String getLastModifier(Node node) throws RepositoryException {
    if (node.hasProperty(NodeTypeConstants.EXO_LAST_MODIFIER)) {
      return node.getProperty(NodeTypeConstants.EXO_LAST_MODIFIER).getString();
    } else {
      return null;
    }
  }

  public static long computeVersionsSize(Node node) {
    long versionSize=0;
    try {
      if (node.isNodeType(MIX_VERSIONABLE)) {
        VersionHistory versionHistory = node.getVersionHistory();
        VersionIterator iterator = versionHistory.getAllVersions();
        while (iterator.hasNext()) {
          Version version = iterator.nextVersion();
          try {
            if (version.hasNode("jcr:frozenNode")) {
              Node frozen = version.getNode("jcr:frozenNode");
              if (frozen.hasNode("jcr:content")) {
                long currentVersionSize = frozen.getNode("jcr:content").getProperty("jcr:data").getLength();
                versionSize += currentVersionSize;
              }
            }
          } catch (Exception e) {
            LOG.error("Unable to read version {}", version.getPath(), e);
          }
        }
      }
    }catch (Exception e) {
      versionSize=0;
    }
    return versionSize;
  }

  /**
   * As the JCR does (AccessManager#hasPermission), the ACL owner holds every
   * right, but a shortcut's owner is whoever created it: its rights are not the
   * target's.
   *
   * @param node the node whose rights are computed
   * @param acl the node's access control list
   * @param userId the user whose rights are computed
   * @return true when the user is the ACL owner of a node that is not a shortcut
   * @throws RepositoryException when the node type cannot be read
   */
  public static boolean isAclOwner(Node node, AccessControlList acl, String userId) throws RepositoryException {
    return userId != null && StringUtils.equals(userId, acl.getOwner())
        && !node.isNodeType(NodeTypeConstants.EXO_SYMLINK);
  }

  public static void computeDocumentAcl(Node node, AbstractNode documentNode, Identity aclIdentity, IdentityManager identityManager, SpaceService spaceService) throws RepositoryException {
    boolean canEdit = false;
    boolean canDelete = false;
    boolean isPublic = false;
    List<PermissionEntry> permissions = new ArrayList<>();
    String userId = aclIdentity.getUserId();
    ExtendedNode extendedNode = (ExtendedNode) node;
    AccessControlList acl = extendedNode.getACL();
    if (isAclOwner(node, acl, userId)) {
      canEdit = true;
      canDelete = true;
    }
    List<AccessControlEntry> permsList = acl.getPermissionEntries();
    for (AccessControlEntry accessControlEntry : permsList) {
      String nodeAclIdentity = accessControlEntry.getIdentity();
      MembershipEntry membershipEntry = accessControlEntry.getMembershipEntry();
      isPublic = isPublic || StringUtils.equals(IdentityConstants.ANY, nodeAclIdentity);
      if (StringUtils.equals(nodeAclIdentity, userId)
          || StringUtils.equals(IdentityConstants.ANY, nodeAclIdentity)
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
    // Owning the containing space outranks a node's own ACL for delete purposes — see
    // isInUserPrivateSpace.
    canDelete = canDelete || isInUserPrivateSpace(node, userId);
    documentNode.setAcl(new NodePermission(true, canEdit, canDelete, isPublic, permissions,null, null,null));
  }

  private static String getPermissionRole (String membershipType){
    if(membershipType.equals("manager") || membershipType.equals("redactor") || membershipType.equals("publisher") ){
      return PermissionRole.MANAGERS_REDACTORS_PUBLISHERS.name();
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
    return getNodeByIdentifier(session, nodeId);
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

  /**
   * Whether the node sits inside {@code username}'s own Personal Documents ("Private")
   * space.
   * <p>
   * Whoever owns the containing space must always be able to manage what is inside it,
   * even a node whose own explicit ACL does not grant them {@code REMOVE}/
   * {@code SET_PROPERTY} — an ACE that never named the space's owner (e.g. a node
   * created there on somebody else's behalf) must not outrank ownership of the space
   * itself.
   *
   * @param node the node to check
   * @param username the user whose private space to check against
   * @return true when the node's path sits under that user's own Private root
   * @throws RepositoryException if the node's path cannot be read
   */
  public static boolean isInUserPrivateSpace(Node node, String username) throws RepositoryException {
    if (node == null || StringUtils.isBlank(username)) {
      return false;
    }
    return StringUtils.equals(username, getPrivateDriveOwner(node.getPath()));
  }

  /**
   * The user whose personal drive holds the given path.
   * <p>
   * Resolved by anchoring on the users home ({@link #getUsersPath()}) and on the
   * <em>first</em> {@code Private} segment of the path, so that the owner is the user
   * whose home node the drive belongs to — never a folder further down that happens to
   * be named after a user, wherever it sits.
   *
   * @param path the JCR path to resolve
   * @return the owning username, or null when the path is not inside a personal drive
   */
  private static String getPrivateDriveOwner(String path) {
    String usersHomePath = getUsersPath();
    if (StringUtils.isBlank(path) || !path.startsWith(usersHomePath + "/")) {
      return null;
    }
    String privateRootSuffix = "/" + USER_PRIVATE_ROOT_NODE;
    int index = path.indexOf(privateRootSuffix + "/");
    if (index < 0 && path.endsWith(privateRootSuffix)) {
      index = path.length() - privateRootSuffix.length();
    }
    if (index <= usersHomePath.length()) {
      return null;
    }
    String userHomePath = path.substring(0, index);
    return userHomePath.substring(userHomePath.lastIndexOf('/') + 1);
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
      checkNotDeleted(ownerIdentity);
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
      checkNotDeleted(ownerIdentity);
      SessionProvider systemSession = SessionProvider.createSystemProvider();
      Node identityNode = nodeHierarchyCreator.getUserNode(systemSession, ownerIdentity.getRemoteId());
      String privatePathNode = identityNode.getPath()+"/"+USER_PRIVATE_ROOT_NODE;
        if (session.itemExists(privatePathNode)) {
          identityRootNode = (Node) session.getItem(privatePathNode);
        }
     }
    return identityRootNode;
  }

  private static void checkNotDeleted(org.exoplatform.social.core.identity.model.Identity ownerIdentity) throws ObjectNotFoundException {
    if (ownerIdentity.isDeleted()) {
      throw new ObjectNotFoundException("Identity with id %s not found".formatted(ownerIdentity.getId()));
    }
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

  /**
   * Opens a session of the default workspace that carries the given identity,
   * whatever the identity of the current thread. A session of
   * {@link #getUserSessionProvider(RepositoryService, Identity)} carries the
   * current thread's identity instead, the JCR login reading
   * {@link ConversationState#getCurrent()}: it is the given user's only on that
   * user's own request. The caller logs the session out.
   *
   * @param repositoryService the repository service
   * @param aclIdentity the identity whose rights the session applies
   * @return the session
   * @throws RepositoryException when the session cannot be opened
   */
  public static Session openUserSession(RepositoryService repositoryService, Identity aclIdentity) throws RepositoryException {
    if (!(repositoryService instanceof RepositoryServiceImpl repositoryServiceImpl)) {
      throw new RepositoryException("A session of a given identity needs the repository's own service");
    }
    ManageableRepository repository = repositoryService.getCurrentRepository();
    String workspaceName = repository.getConfiguration().getDefaultWorkspaceName();
    WorkspaceContainer workspaceContainer = repositoryServiceImpl.getRepositoryContainer(repository.getConfiguration().getName())
                                                                 .getWorkspaceContainer(workspaceName);
    return new SessionImpl(workspaceName, new ConversationState(aclIdentity), workspaceContainer);
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
    if (org.apache.commons.lang3.StringUtils.isEmpty(oldName)) return oldName;
    String extension = "" ;
    if(nodeType.equals(NodeTypeConstants.NT_FILE) && oldName.lastIndexOf(".") > -1){
      extension = oldName.substring(oldName.lastIndexOf("."));
      oldName = oldName.substring(0,oldName.lastIndexOf(".")) ;
    }
    oldName = oldName.trim();
    String specialChar = "&#*.'\"\t\r\n$\\><:;[]/|’";
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
  public static String cleanNameWithAccents(String fileName, String nodeType) {
    Transliterator accentsconverter = Transliterator.getInstance("Latin; NFD; [:Nonspacing Mark:] Remove; NFC;");
    if (NodeTypeConstants.NT_FILE.equals(nodeType) && fileName.indexOf('.') > 0) {
      String ext = fileName.substring(fileName.lastIndexOf('.'));
      fileName = accentsconverter.transliterate(fileName.substring(0, fileName.lastIndexOf('.'))).concat(ext);
    } else {
      fileName = accentsconverter.transliterate(fileName);
    }
    return Text.escapeIllegalJcrChars(fileName);
  }

  public static boolean isValidDocumentTitle(String name) {
    if (StringUtils.isBlank(FilenameUtils.getBaseName(name))) {
      return false;
    }
    String strippedName = StringUtils.strip(name);
    if (".".equals(strippedName) || "..".equals(strippedName)) {
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
    versionFileNode.setTitle(getTitle(node));
    String userName = getLastModifierOrCreator(frozen);
    org.exoplatform.social.core.identity.model.Identity identity = userName != null ?
                                                                                    identityManager.getOrCreateUserIdentity(userName) :
                                                                                    null;
    Profile profile = identity != null ? identity.getProfile() : null;
    String[] summary = node.getVersionHistory().getVersionLabels(version);
    if (summary.length > 0) {
      versionFileNode.setSummary(summary[0]);
    }
    versionFileNode.setId(version.getUUID());
    versionFileNode.setFrozenId(frozen.getUUID());
    versionFileNode.setOriginId(node.getUUID());
    versionFileNode.setAuthor(userName);
    versionFileNode.setAuthorFullName(profile != null ? profile.getFullName() : userName);
    versionFileNode.setCreatedDate(version.getCreated().getTime());
    versionFileNode.setVersionNumber(Integer.parseInt(version.getName()));
    if (version.getName().equals(currentVersionName)) {
      versionFileNode.setCurrent(true);
    }
    return versionFileNode;
  }

  /**
   * Reads a document's own access list, to put it back after a change that
   * resets it, such as a version restore
   *
   * @param node the document
   * @return its own permissions by identity, or null when it has no list of
   *         its own and reads its folder's
   * @throws RepositoryException when the document can't be read
   */
  public static Map<String, String[]> getOwnPermissions(Node node) throws RepositoryException {
    if (!node.isNodeType(NodeTypeConstants.EXO_PRIVILEGEABLE)) {
      return null; // NOSONAR null means no list of its own: the repository holds no empty one
    }
    return toPermissions((ExtendedNode) node);
  }

  /**
   * Reads the own access list of every node below a document that a restore
   * of the document brings back with it, to put them back as well. The walk
   * goes down through the non-versionable nodes only, since a versionable
   * node keeps its own history and is not touched by the restore.
   *
   * @param node the document
   * @return the own permissions by identity of those nodes, by node id
   * @throws RepositoryException when a node can't be read
   */
  public static Map<String, Map<String, String[]>> getDescendantsOwnPermissions(Node node) throws RepositoryException {
    Map<String, Map<String, String[]>> descendants = new LinkedHashMap<>();
    collectDescendantsOwnPermissions(node, descendants);
    return descendants;
  }

  private static void collectDescendantsOwnPermissions(Node node,
                                                       Map<String, Map<String, String[]>> descendants) throws RepositoryException {
    NodeIterator children = node.getNodes();
    while (children.hasNext()) {
      Node child = children.nextNode();
      if (child.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)) {
        continue;
      }
      if (child.isNodeType(NodeTypeConstants.EXO_PRIVILEGEABLE)) {
        descendants.put(((ExtendedNode) child).getIdentifier(), toPermissions((ExtendedNode) child));
      }
      collectDescendantsOwnPermissions(child, descendants);
    }
  }

  private static Map<String, String[]> toPermissions(ExtendedNode node) throws RepositoryException {
    Map<String, List<String>> permissions = new LinkedHashMap<>();
    for (AccessControlEntry entry : node.getACL().getPermissionEntries()) {
      permissions.computeIfAbsent(entry.getIdentity(), identity -> new ArrayList<>()).add(entry.getPermission());
    }
    Map<String, String[]> permissionsByIdentity = new LinkedHashMap<>();
    permissions.forEach((identity, types) -> permissionsByIdentity.put(identity, types.toArray(new String[0])));
    return permissionsByIdentity;
  }

  /**
   * Gives a document back the access list read by
   * {@link #getOwnPermissions(Node)}, through a system session, so that a
   * user who may edit the document without changing its access can restore it.
   * The document must be checked out: its mixins may change.
   *
   * @param repository the repository holding the document
   * @param workspace the document's workspace
   * @param nodeUuid the document's UUID
   * @param ownPermissions its own permissions, or null when it had no list of
   *          its own
   * @param descendantsPermissions the own permissions of the nodes below it, by
   *          node id, as read by {@link #getDescendantsOwnPermissions(Node)}
   * @throws RepositoryException when the list can't be put back
   */
  public static void putBackOwnPermissions(ManageableRepository repository,
                                           String workspace,
                                           String nodeUuid,
                                           Map<String, String[]> ownPermissions,
                                           Map<String, Map<String, String[]>> descendantsPermissions) throws RepositoryException {
    Session systemSession = repository.getSystemSession(workspace);
    try {
      putBackDescendantsPermissions(systemSession, descendantsPermissions);
      ExtendedNode node = (ExtendedNode) systemSession.getNodeByUUID(nodeUuid);
      if (ownPermissions != null) {
        if (!node.isNodeType(NodeTypeConstants.EXO_PRIVILEGEABLE)) {
          node.addMixin(NodeTypeConstants.EXO_PRIVILEGEABLE);
        }
        node.setPermissions(ownPermissions);
      } else if (node.isNodeType(NodeTypeConstants.EXO_PRIVILEGEABLE)) {
        // Removing the mixin keeps the list in effect: its folder's is set first
        node.setPermissions(toPermissions((ExtendedNode) node.getParent()));
        systemSession.save();
        node.removeMixin(NodeTypeConstants.EXO_PRIVILEGEABLE);
      }
      systemSession.save();
    } finally {
      systemSession.logout();
    }
  }

  private static void putBackDescendantsPermissions(Session systemSession,
                                                    Map<String, Map<String, String[]>> descendantsPermissions) throws RepositoryException {
    for (Map.Entry<String, Map<String, String[]>> descendant : descendantsPermissions.entrySet()) {
      ExtendedNode child;
      try {
        child = (ExtendedNode) ((ExtendedSession) systemSession).getNodeByIdentifier(descendant.getKey());
      } catch (ItemNotFoundException e) {
        continue; // the restore did not bring this node back
      }
      if (!child.isNodeType(NodeTypeConstants.EXO_PRIVILEGEABLE)) {
        child.addMixin(NodeTypeConstants.EXO_PRIVILEGEABLE);
      }
      child.setPermissions(descendant.getValue());
    }
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
    String primaryType = node.getPrimaryNodeType().getName();
    if (NodeTypeConstants.NT_FROZEN_NODE.equals(primaryType)) {
      primaryType = node.getProperty("jcr:frozenPrimaryType").getString();
    }
    return NodeTypeConstants.NT_FOLDER.equals(primaryType) || NodeTypeConstants.NT_UNSTRUCTURED.equals(primaryType);
  }

  public static boolean isFile(Node node) throws RepositoryException {
    String primaryType = node.getPrimaryNodeType().getName();
    if (NodeTypeConstants.NT_FROZEN_NODE.equals(primaryType)) {
      primaryType = node.getProperty("jcr:frozenPrimaryType").getString();
    }
    return NodeTypeConstants.NT_FILE.equals(primaryType) || NodeTypeConstants.NT_RESOURCE.equals(primaryType);
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
                            getTitle(node),
                            byteArrayOutputStream,
                            mimeType);
  }

  /**
   * Builds a {@link DownloadItem} holding the node's identifier, title and mime
   * type, with no content: the {@code jcr:data} property is neither read nor
   * tested, whatever the document's size.
   *
   * @param node the document node
   * @return the {@link DownloadItem}, its content null, its mime type null for
   *         a node without {@code jcr:content}
   * @throws RepositoryException when a property cannot be read
   */
  public static DownloadItem toDownloadInfo(Node node) throws RepositoryException {
    String mimeType = null;
    if (node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
      mimeType = Utils.getStringProperty(node.getNode(NodeTypeConstants.JCR_CONTENT), NodeTypeConstants.JCR_MIME_TYPE);
    }
    return new DownloadItem(((NodeImpl) node).getIdentifier(), getTitle(node), null, mimeType);
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
                                 Node parentNode) throws RepositoryException, IOException {
    if (node == null) {
      return;
    }
    createFile(node, symlinkPath, sourcePath, tempFolderPath, getPathWithTitles(parentNode));
  }

  private static void createFile(Node node,
                                 String symlinkPath,
                                 String sourcePath,
                                 String tempFolderPath,
                                 String parentPath) throws RepositoryException, IOException {
    if (node == null) {
      return;
    }
    if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
      String sourceID = node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
      Node sourceNode = getNodeByIdentifier(node.getSession(), sourceID);
      String shortcutPath = getDownloadPath(node, symlinkPath, sourcePath);
      if (sourceNode != null && toTempFolderPath(tempFolderPath, getRelativePath(node, symlinkPath, sourcePath, parentPath)) == null) {
        LOG.warn("Shortcut {} left out of the download: its path is not a path in the download folder", node.getPath());
      } else if (sourceNode != null) {
        createFile(sourceNode, shortcutPath, getPathWithTitles(sourceNode), tempFolderPath, parentPath);
      }
      return;
    }
    Path filePath = toTempFolderPath(tempFolderPath, getRelativePath(node, symlinkPath, sourcePath, parentPath));
    if (filePath == null) {
      LOG.warn("Document {} left out of the download: its path is not a path in the download folder", node.getPath());
      return;
    }
    Node jrcNode = node.getNode("jcr:content");
    try (InputStream inputStream = jrcNode.getProperty("jcr:data").getStream();
         OutputStream outputStream = new FileOutputStream(filePath.toFile())) {
      byte[] buffer = new byte[1024];
      int length;
      while ((length = inputStream.read(buffer)) > 0) {
        outputStream.write(buffer, 0, length);
      }
    }
  }

  public static String getPathWithTitles(Node node){
    boolean hasParent = true;
    List<String> parents = new ArrayList<>();
    while (hasParent) {
      try {
        parents.add(getTitle(node));
        node = node.getParent();
      } catch (RepositoryException e) {
        hasParent = false;
      }
    }
    Collections.reverse(parents);
    return String.join("/", parents);
  }

  public static void createTempFilesAndFolders(Node node,
                                               String symlinkPath,
                                               String sourcePath,
                                               String tempFolderPath,
                                               Node parentNode) throws Exception {
    if (node == null) {
      return;
    }
    createTempFilesAndFolders(node, symlinkPath, sourcePath, tempFolderPath, getPathWithTitles(parentNode));
  }

  /**
   * Copies the tree under a node, following shortcuts to their targets, each
   * one at its titles path ({@link #getPathWithTitles(Node)}) less
   * {@code parentPath}, under {@code tempFolderPath}.
   *
   * @param parentPath the titles path the walk strips from each node's: the
   *          parent's, or "" when the walking session cannot read the parent,
   *          the node's own titles path then starting at the node, with no
   *          leading separator
   */
  public static void createTempFilesAndFolders(Node node,
                                               String symlinkPath,
                                               String sourcePath,
                                               String tempFolderPath,
                                               String parentPath) throws Exception {
    createTempFilesAndFolders(node, symlinkPath, sourcePath, tempFolderPath, parentPath, null);
  }

  /**
   * Same as {@link #createTempFilesAndFolders(Node, String, String, String, String)},
   * leaving out the shortcuts whose target satisfies {@code skipTarget}.
   *
   * @param skipTarget tests the target of a shortcut met in the walk, a target
   *          it accepts is not copied; null skips none
   */
  public static void createTempFilesAndFolders(Node node,
                                               String symlinkPath,
                                               String sourcePath,
                                               String tempFolderPath,
                                               String parentPath,
                                               Predicate<Node> skipTarget) throws Exception {
    createTempFilesAndFolders(node,
                              symlinkPath,
                              sourcePath,
                              tempFolderPath,
                              parentPath,
                              new HashSet<>(),
                              new HashSet<>(),
                              skipTarget);
  }

  /**
   * Copies the tree under a node, following shortcuts to their targets.
   * {@code walkPath} holds the identifiers of the nodes on the current branch of
   * the walk, so a node is walked at most once on any one branch.
   * {@code nestedTargets} holds the identifiers of the own targets of the
   * shortcuts met inside another shortcut's target: each one is copied once for
   * each downloaded folder, at the place of the first such shortcut that has a
   * place in the download. A shortcut whose target {@code skipTarget} accepts
   * is left out.
   */
  private static void createTempFilesAndFolders(Node node,
                                                String symlinkPath,
                                                String sourcePath,
                                                String tempFolderPath,
                                                String parentPath,
                                                Set<String> walkPath,
                                                Set<String> nestedTargets,
                                                Predicate<Node> skipTarget) throws Exception {
    if (node == null) {
      return;
    }
    String nodeId = ((ExtendedNode) node).getIdentifier();
    if (!walkPath.add(nodeId)) {
      return;
    }
    try {
      if (JCRDocumentsUtil.isFolder(node)) {
        Path folderPath = toTempFolderPath(tempFolderPath, getRelativePath(node, symlinkPath, sourcePath, parentPath));
        if (folderPath == null) {
          LOG.warn("Folder {} left out of the download: its path is not a path in the download folder", node.getPath());
          return;
        }
        Files.createDirectories(folderPath);
        NodeIterator nodeIterator = node.getNodes();
        while (nodeIterator.hasNext()) {
          Node child = nodeIterator.nextNode();
          createTempFilesAndFolders(child, symlinkPath, sourcePath, tempFolderPath, parentPath, walkPath, nestedTargets, skipTarget);
        }
      } else {
        if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
          String sourceID = node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
          Node sourceNode = JCRDocumentsUtil.getNodeByIdentifier(node.getSession(), sourceID);
          String shortcutPath = getDownloadPath(node, symlinkPath, sourcePath);
          if (sourceNode == null || (skipTarget != null && skipTarget.test(sourceNode))) {
            return;
          } else if (toTempFolderPath(tempFolderPath, getRelativePath(node, symlinkPath, sourcePath, parentPath)) == null) {
            LOG.warn("Shortcut {} left out of the download: its path is not a path in the download folder", node.getPath());
          } else if (isInShortcutTarget(node, symlinkPath, sourcePath) && !nestedTargets.add(sourceID)) {
            LOG.warn("Shortcut {} left out of the download: its target is already in the download", node.getPath());
          } else {
            createTempFilesAndFolders(sourceNode,
                                      shortcutPath,
                                      getPathWithTitles(sourceNode),
                                      tempFolderPath,
                                      parentPath,
                                      walkPath,
                                      nestedTargets,
                                      skipTarget);
          }
        } else {
          createFile(node, symlinkPath, sourcePath, tempFolderPath, parentPath);
        }
      }
    } finally {
      walkPath.remove(nodeId);
    }
  }

  /**
   * @return true when the node lies inside the target folder of the shortcut
   *         that led to it, false when it is in the downloaded tree itself or
   *         is that shortcut's target
   */
  private static boolean isInShortcutTarget(Node node, String symlinkPath, String sourcePath) throws RepositoryException {
    return StringUtils.isNotEmpty(symlinkPath) && !StringUtils.equals(getPathWithTitles(node), sourcePath);
  }

  /**
   * @return the node's titles path in the download, less the downloaded
   *         folder's parent; null when that parent's path does not lead it
   */
  private static String getRelativePath(Node node, String symlinkPath, String sourcePath, String parentPath) {
    String nodePath = getDownloadPath(node, symlinkPath, sourcePath);
    return nodePath == null ? null : stripLeadingPath(nodePath, parentPath);
  }

  /**
   * @return the node's titles path, placed under the shortcut that led to it,
   *         if any; null when the shortcut's target path does not lead the
   *         node's
   */
  private static String getDownloadPath(Node node, String symlinkPath, String sourcePath) {
    String nodePath = getPathWithTitles(node);
    if (StringUtils.isNotEmpty(symlinkPath) || StringUtils.isNotEmpty(sourcePath)) {
      String targetPath = stripLeadingPath(nodePath, sourcePath);
      nodePath = targetPath == null ? null : symlinkPath + targetPath;
    }
    return nodePath;
  }

  private static String stripLeadingPath(String path, String leadingPath) {
    if (StringUtils.isEmpty(leadingPath)) {
      return path;
    } else if (path.equals(leadingPath)) {
      return "";
    } else {
      return path.startsWith(leadingPath + "/") ? path.substring(leadingPath.length()) : null;
    }
  }

  /**
   * @param relativePath the copied item's titles path in the download
   * @return the copied item's path, or null when one of its titles is blank,
   *         "." or "..", or when it is not below the download folder: each item
   *         keeps its own place in the download
   */
  private static Path toTempFolderPath(String tempFolderPath, String relativePath) {
    if (relativePath == null) {
      return null;
    }
    String[] titles = relativePath.split("/", -1);
    for (int i = 0; i < titles.length; i++) {
      String strippedTitle = titles[i].strip();
      // the path starts with a separator, but for a folder read without its parent
      boolean leadingSeparator = i == 0 && titles[i].isEmpty();
      if ((strippedTitle.isEmpty() && !leadingSeparator) || ".".equals(strippedTitle) || "..".equals(strippedTitle)) {
        return null;
      }
    }
    Path tempFolder = Paths.get(tempFolderPath).toAbsolutePath().normalize();
    Path itemPath = Paths.get(tempFolderPath + relativePath).toAbsolutePath().normalize();
    return itemPath.startsWith(tempFolder) && !itemPath.equals(tempFolder) ? itemPath : null;
  }

  public static void cleanFiles(File file) {
    File[] files = file.listFiles();
    if (files != null) {
      for (File f : files) {
        cleanFiles(f);
      }
    }
    try {
      Files.delete(file.toPath());
    } catch (IOException e) {
      LOG.warn("Cannot delete {}", file.toPath(), e);
    }
  }

  public static void zipFiles(String zipFilePath, String tempFolderPath) throws Exception {
    try (FileOutputStream fos = new FileOutputStream(zipFilePath)) {
      try (ZipOutputStream zos = new ZipOutputStream(fos)) {
        File folder = new File(tempFolderPath);
        zipFolder(folder, "", zos);
      }
    }
  }

  /**
   * Zips a folder's tree into a stream, entry by entry: nothing is held in
   * memory but the current buffer, and the zip switches to its 64-bit format
   * by itself above 4 GiB or 65535 entries.
   *
   * @param folder the folder whose content is zipped, the folder itself left
   *          out of the entries' paths
   * @param outputStream where the zip is written; it is finished, not closed
   * @throws IOException when a file cannot be read, a folder cannot be listed
   *           or the stream written
   */
  public static void zipFiles(File folder, OutputStream outputStream) throws IOException {
    ZipTarget target = new ZipTarget(outputStream);
    ZipOutputStream zipOutputStream = new ZipOutputStream(target);
    try {
      zipFolder(folder, "", zipOutputStream);
    } catch (IOException | RuntimeException e) {
      // The zip's directory is dropped, so that a zip cut short reads as
      // broken, never as complete with files missing
      target.discard();
      throw e;
    } finally {
      // Frees the deflater; finishes the zip unless it was discarded
      zipOutputStream.close();
    }
  }

  /**
   * The caller's stream, which closing the zip leaves open, and which drops
   * what the zip writes once discarded.
   */
  private static final class ZipTarget extends FilterOutputStream {

    private boolean discarded;

    private ZipTarget(OutputStream outputStream) {
      super(outputStream);
    }

    private void discard() {
      discarded = true;
    }

    @Override
    public void write(int b) throws IOException {
      if (!discarded) {
        out.write(b);
      }
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
      if (!discarded) {
        out.write(bytes, offset, length);
      }
    }

    @Override
    public void flush() throws IOException {
      if (!discarded) {
        out.flush();
      }
    }

    @Override
    public void close() throws IOException {
      flush();
    }
  }

  private static String getFolderName(File file, String folderName) {
    return StringUtils.isNotEmpty(folderName) ? folderName + "/" + file.getName() : file.getName();
  }
  
  private static void zipFolder(File folder, String folderName, ZipOutputStream zipOutputStream) throws IOException {
    File[] files = folder.listFiles();
    if (files == null) {
      // A folder that cannot be listed is a read failure: the zip fails rather
      // than finishing without that folder's content
      throw new IOException("Cannot list the folder " + folder);
    }
    for (File file : files) {
      if (file.isDirectory()) {
        zipFolder(file, getFolderName(file, folderName), zipOutputStream);
      } else {
        try (FileInputStream fis = new FileInputStream(file)) {
          zipOutputStream.putNextEntry(new ZipEntry(getFolderName(file, folderName)));
          fis.transferTo(zipOutputStream);
          zipOutputStream.closeEntry();
        }
      }
    }
  }

  public static String getFolderLink(Node node, Space space) throws RepositoryException {
    StringBuilder stringBuilder = new StringBuilder();
    String portalOwner = CommonsUtils.getCurrentPortalOwner();
    String domain = CommonsUtils.getCurrentDomain();
    stringBuilder.append(domain).append("/").append(LinkProvider.getPortalName(null)).append("/");
    if (space != null) {
      stringBuilder.append("s/").append(space.getId()).append("/documents");
    } else {
      stringBuilder.append(portalOwner).append("/documents");
    }
    stringBuilder.append("?folderId=");
    stringBuilder.append(((NodeImpl) node).getIdentifier());
    return stringBuilder.toString();
  }

  public static void retrieveTrashElementProperties(Node node, TrashElementNode trashElementNode) throws RepositoryException {
    trashElementNode.setId(((NodeImpl) node).getIdentifier());
    trashElementNode.setName(getTitle(node));
    trashElementNode.setPath(node.getPath());
    if (node.hasProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE)) {
      Node nodeToModify = node;
      if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        RepositoryService repositoryService = CommonsUtils.getService(RepositoryService.class);
        SessionProvider sessionProvider = SessionProvider.createSystemProvider();
        ManageableRepository repository = repositoryService.getCurrentRepository();
        Session systemSession = sessionProvider.getSession(repository.getConfiguration().getDefaultWorkspaceName(), repository);
        String sourceNodeId = node.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID).getString();
        Node sourceNode = getNodeByIdentifier(systemSession, sourceNodeId);
        if (sourceNode != null && sourceNode.getProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE)
                                            .getDate()
                                            .compareTo(node.getProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE).getDate()) > 0) {
          nodeToModify = sourceNode;
        }
      }
      long modifiedDate = nodeToModify.getProperty(NodeTypeConstants.EXO_LAST_MODIFIED_DATE).getDate().getTimeInMillis();
      trashElementNode.setModifiedDate(modifiedDate);
    }
    if (node.hasProperty(NodeTypeConstants.RESTORE_PATH)) {
      trashElementNode.setRestorePath(node.getProperty(NodeTypeConstants.RESTORE_PATH).getString());
    }
    trashElementNode.setFolder(isFolder(node));
    if (node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
      Node content = node.getNode(NodeTypeConstants.JCR_CONTENT);
      if (content.hasProperty(NodeTypeConstants.JCR_MIME_TYPE)) {
        trashElementNode.setMimeType(content.getProperty(NodeTypeConstants.JCR_MIME_TYPE).getString());
      }
      if (content.hasProperty(NodeTypeConstants.JCR_DATA)) {
        long fileSize = content.getProperty(NodeTypeConstants.JCR_DATA).getLength();
        trashElementNode.setSize(fileSize);
      }
    }
  }

  public static String getTitle(Node node) throws RepositoryException {
    String title = null;
    if (node.hasProperty(NodeTypeConstants.EXO_TITLE)) {
      title = node.getProperty(NodeTypeConstants.EXO_TITLE).getString();
    }
    if (StringUtils.isBlank(title)) {
      return node.getName();
    } else {
      return title;
    }
  }

  @SneakyThrows
  private static boolean hasProperty(Node node, String propertyName) {
    return node.hasProperty(propertyName);
  }

  @SneakyThrows
  private static Calendar getPropertyDate(Node node, String propertyName) {
    return node.getProperty(propertyName).getDate();
  }

}
