/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 * This program is free software: you can redistribute it and/or modify
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.exoplatform.documents.constant.DocumentNodeType;
import org.exoplatform.documents.model.DocumentNodeInfo;
import org.exoplatform.documents.storage.jcr.model.ChainEnd;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Reads, in a session the caller opened and closes, what nodes are, where
 * they are and what a user may do with files, for several nodes at once.
 * Every lookup goes to {@link ExtendedSession#getNodeByIdentifier(String)}
 * directly: only {@link ItemNotFoundException} means that no node carries an
 * id, and any other repository failure reaches the caller.
 */
public final class DocumentNodeDescriber {

  /** The path under which every space's group folder lies. */
  public static final String SPACES_PATH_PREFIX = "/Groups/spaces/";

  /** The name of a space group's library folder. */
  public static final String LIBRARY_FOLDER_NAME = "Documents";

  private DocumentNodeDescriber() {
    // static methods only
  }

  /**
   * Tells which of some nodes are files the session's user may read, and which
   * of them he may edit.
   *
   * @param session a session of the user
   * @param ids node identifiers
   * @return for each id naming a file the user may read, whether he may edit
   *         it; ids naming no node, a node he may not read or another kind of
   *         node are absent
   * @throws RepositoryException when the repository fails otherwise reading a
   *           node; a failure of the edit check reads as read only
   */
  public static Map<String, Boolean> getAccess(ExtendedSession session,
                                               Collection<String> ids) throws RepositoryException {
    Map<String, Boolean> access = new LinkedHashMap<>();
    for (String id : distinct(ids)) {
      Node node;
      try {
        node = session.getNodeByIdentifier(id);
      } catch (ItemNotFoundException | AccessDeniedException e) {
        continue;
      }
      if (node != null && typeOf(node) == DocumentNodeType.FILE) {
        access.put(id, JCRDocumentsUtil.hasEditPermission(session, node));
      }
    }
    return access;
  }

  /**
   * Describes some nodes, whoever may see them.
   *
   * @param session a session that may read every node, a system session
   * @param ids node identifiers
   * @param spaceService the service resolving a library's space
   * @return each id's description, plus, for a shortcut whose chain ends on a
   *         node that is not a shortcut, that node's description under its id;
   *         an id no node carries is absent
   * @throws RepositoryException when the repository fails otherwise than by
   *           finding no node
   */
  public static Map<String, DocumentNodeInfo> describe(ExtendedSession session,
                                                       Collection<String> ids,
                                                       SpaceService spaceService) throws RepositoryException {
    Map<String, DocumentNodeInfo> descriptions = new LinkedHashMap<>();
    Map<String, Long> spaceIdsByGroup = new HashMap<>();
    for (String id : distinct(ids)) {
      if (descriptions.containsKey(id)) {
        continue;
      }
      Node node = find(session, id);
      if (node == null) {
        continue;
      }
      DocumentNodeType type = typeOf(node);
      if (type != DocumentNodeType.SYMLINK) {
        descriptions.put(id, describe(id, node, type, id, type, spaceIdsByGroup, spaceService));
        continue;
      }
      ChainEnd end = followChain(session, id, node);
      descriptions.put(id, describe(id, node, type, end.id(), end.type(), spaceIdsByGroup, spaceService));
      if (end.node() != null && end.type() != DocumentNodeType.SYMLINK && !descriptions.containsKey(end.id())) {
        descriptions.put(end.id(),
                         describe(end.id(), end.node(), end.type(), end.id(), end.type(), spaceIdsByGroup, spaceService));
      }
    }
    return descriptions;
  }

  /**
   * @param node a node
   * @return what the node is, read from its own primary type: a version's
   *         frozen node is {@link DocumentNodeType#OTHER}, whatever it froze
   * @throws RepositoryException when the type cannot be read
   */
  public static DocumentNodeType typeOf(Node node) throws RepositoryException {
    if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
      return DocumentNodeType.SYMLINK;
    }
    String primaryType = node.getPrimaryNodeType().getName();
    if (NodeTypeConstants.NT_FILE.equals(primaryType)) {
      return DocumentNodeType.FILE;
    } else if (NodeTypeConstants.NT_FOLDER.equals(primaryType) || NodeTypeConstants.NT_UNSTRUCTURED.equals(primaryType)) {
      return DocumentNodeType.FOLDER;
    } else {
      return DocumentNodeType.OTHER;
    }
  }

  /**
   * Follows a chain of shortcuts to its end, in the same session.
   *
   * @param session the session
   * @param id the first shortcut's id
   * @param symlink the first shortcut
   * @return the end of the chain: a node that is no shortcut; the last
   *         shortcut of a chain that loops; no node when the chain is broken
   * @throws RepositoryException when the repository fails otherwise than by
   *           finding no node
   */
  private static ChainEnd followChain(ExtendedSession session, String id, Node symlink) throws RepositoryException {
    Set<String> seen = new LinkedHashSet<>();
    seen.add(id);
    String currentId = id;
    Node current = symlink;
    while (true) {
      String nextId = current.hasProperty(NodeTypeConstants.EXO_SYMLINK_UUID) ? current.getProperty(NodeTypeConstants.EXO_SYMLINK_UUID)
                                                                                      .getString()
                                                                              : null;
      if (nextId == null || nextId.isEmpty()) {
        return new ChainEnd(null, null, null);
      }
      if (!seen.add(nextId)) {
        return new ChainEnd(currentId, current, DocumentNodeType.SYMLINK);
      }
      Node next = find(session, nextId);
      if (next == null) {
        return new ChainEnd(null, null, null);
      }
      DocumentNodeType nextType = typeOf(next);
      if (nextType != DocumentNodeType.SYMLINK) {
        return new ChainEnd(nextId, next, nextType);
      }
      currentId = nextId;
      current = next;
    }
  }

  /**
   * Builds a node's description.
   *
   * @param id the node's id
   * @param node the node
   * @param type the node's type
   * @param targetId see {@link DocumentNodeInfo#targetId()}
   * @param targetType see {@link DocumentNodeInfo#targetType()}
   * @param spaceIdsByGroup the space ids already resolved in this call, by
   *          group name
   * @param spaceService the service resolving a library's space
   * @return the description
   * @throws RepositoryException when the node's path or parents cannot be read
   */
  private static DocumentNodeInfo describe(String id,
                                           Node node,
                                           DocumentNodeType type,
                                           String targetId,
                                           DocumentNodeType targetType,
                                           Map<String, Long> spaceIdsByGroup,
                                           SpaceService spaceService) throws RepositoryException {
    String path = node.getPath();
    if (path == null || !path.startsWith(SPACES_PATH_PREFIX)) {
      return new DocumentNodeInfo(id, type, targetId, targetType, 0, List.of(), false);
    }
    String[] segments = path.substring(SPACES_PATH_PREFIX.length()).split("/");
    if (segments.length < 2 || segments[0].isEmpty() || !LIBRARY_FOLDER_NAME.equals(segments[1])) {
      return new DocumentNodeInfo(id, type, targetId, targetType, 0, List.of(), false);
    }
    long spaceId = spaceIdsByGroup.computeIfAbsent(segments[0], groupName -> spaceId(spaceService, groupName));
    if (spaceId == 0) {
      return new DocumentNodeInfo(id, type, targetId, targetType, 0, List.of(), false);
    }
    // segments: group, Documents, then one per level below the library root,
    // the node itself last; its parents below the root are the levels above it
    int parentsBelowRoot = segments.length - 3;
    List<String> ancestorFolderIds = new ArrayList<>(Math.max(parentsBelowRoot, 0));
    Node parent = node;
    for (int i = 0; i < parentsBelowRoot; i++) {
      parent = parent.getParent();
      if (typeOf(parent) == DocumentNodeType.FOLDER) {
        ancestorFolderIds.add(((ExtendedNode) parent).getIdentifier());
      }
    }
    return new DocumentNodeInfo(id, type, targetId, targetType, spaceId, ancestorFolderIds, segments.length == 2);
  }

  /**
   * @param spaceService the space service
   * @param groupName a space group's name, the last segment of its group id
   * @return the space's id, 0 when no space has that group
   */
  private static long spaceId(SpaceService spaceService, String groupName) {
    Space space = spaceService.getSpaceByGroupId("/spaces/" + groupName);
    return space == null || space.getId() == null ? 0 : Long.parseLong(space.getId());
  }

  /**
   * @param session the session
   * @param id a node identifier
   * @return the node, or null when no node carries the id
   * @throws RepositoryException when the repository fails otherwise
   */
  private static Node find(ExtendedSession session, String id) throws RepositoryException {
    try {
      return session.getNodeByIdentifier(id);
    } catch (ItemNotFoundException e) {
      return null;
    }
  }

  /**
   * @param ids identifiers, possibly repeated or null
   * @return the distinct non-null ones, in order
   */
  private static Set<String> distinct(Collection<String> ids) {
    Set<String> distinct = new LinkedHashSet<>();
    if (ids != null) {
      ids.stream().filter(Objects::nonNull).forEach(distinct::add);
    }
    return distinct;
  }

}
