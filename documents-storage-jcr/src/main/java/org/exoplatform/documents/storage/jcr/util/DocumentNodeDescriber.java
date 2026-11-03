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
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;

import org.exoplatform.documents.constant.DocumentNodeType;
import org.exoplatform.documents.model.DocumentIndexInfo;
import org.exoplatform.documents.model.DocumentNodeInfo;
import org.exoplatform.documents.storage.jcr.model.ChainEnd;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
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

  /**
   * The statement reading a page of the files under a folder: the folder's
   * path, the {@code jcr:uuid} condition, then the ordering property.
   */
  public static final String LIST_FILE_IDS_QUERY = "SELECT jcr:path, jcr:uuid FROM nt:file WHERE jcr:path LIKE '%s/%%' AND %s ORDER BY %s ASC";

  /**
   * The bound on the unreadable search index hits after a file listing's
   * cursor: once a full page leaves more than this many unreadable hits after
   * the cursor and no readable one, the listing reports the index as broken
   * rather than end the walk. Pages double while they hold no readable hit, so
   * a last page may step over up to about twice as many before the folder ends.
   */
  public static final int    MAX_UNREADABLE_HITS           = 10_000;

  /** The largest list a file listing allocates before it reads any row. */
  static final int           INITIAL_PAGE_CAPACITY         = 1_000;

  /** The {@code jcr:uuid} condition of a first page. */
  static final String        LIST_FILE_IDS_FIRST_CONDITION = "%s IS NOT NULL";

  /** The {@code jcr:uuid} condition of a page following an identifier. */
  static final String        LIST_FILE_IDS_NEXT_CONDITION  = "%s > '%s'";

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
    return getAccess(session, ids, DocumentNodeType.FILE);
  }

  /**
   * Tells which of some nodes are folders the session's user may open, and
   * which of them he may edit. A folder is typed as {@link #typeOf(Node)} types
   * it, so a library root is one, and a shortcut is not: a caller resolves a
   * shortcut to its target first.
   *
   * @param session a session of the user
   * @param ids node identifiers
   * @return for each id naming a folder the user may open, whether he may edit
   *         it; ids naming no node, a node he may not read or another kind of
   *         node are absent
   * @throws RepositoryException when the repository fails otherwise reading a
   *           node; a failure of the edit check reads as read only
   */
  public static Map<String, Boolean> getFolderAccess(ExtendedSession session,
                                                     Collection<String> ids) throws RepositoryException {
    return getAccess(session, ids, DocumentNodeType.FOLDER);
  }

  /**
   * Tells which of some nodes are of one type and readable by the session's
   * user, and which of them he may edit.
   *
   * @param session a session of the user
   * @param ids node identifiers
   * @param type the only type of node kept
   * @return for each id naming a readable node of that type, whether the user
   *         may edit it
   * @throws RepositoryException when the repository fails otherwise reading a
   *           node
   */
  private static Map<String, Boolean> getAccess(ExtendedSession session,
                                                Collection<String> ids,
                                                DocumentNodeType type) throws RepositoryException {
    Map<String, Boolean> access = new LinkedHashMap<>();
    for (String id : distinct(ids)) {
      Node node;
      try {
        node = session.getNodeByIdentifier(id);
      } catch (ItemNotFoundException | AccessDeniedException e) {
        continue;
      }
      if (node != null && typeOf(node) == type) {
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
   * Describes some files for a search index built outside Documents, whoever
   * may see them.
   *
   * @param session a session that may read every node, a system session
   * @param ids node identifiers
   * @param spaceService the service resolving a library's space
   * @return each id naming a file and its description, in the order of the
   *         ids, every {@code url} null; an id no node carries, naming a
   *         folder, a shortcut or another kind of node, or naming a file in
   *         the trash (itself trashed, or under a trashed folder) is absent
   * @throws RepositoryException when the repository fails otherwise than by
   *           finding no node
   */
  public static Map<String, DocumentIndexInfo> describeForIndex(ExtendedSession session,
                                                                Collection<String> ids,
                                                                SpaceService spaceService) throws RepositoryException {
    Map<String, DocumentIndexInfo> descriptions = new LinkedHashMap<>();
    Map<String, Long> spaceIdsByGroup = new HashMap<>();
    for (String id : distinct(ids)) {
      Node node = find(session, id);
      if (node == null || typeOf(node) != DocumentNodeType.FILE || inTrash(node)) {
        continue;
      }
      DocumentNodeInfo place = describe(id,
                                        node,
                                        DocumentNodeType.FILE,
                                        id,
                                        DocumentNodeType.FILE,
                                        spaceIdsByGroup,
                                        spaceService);
      AccessControlList acl = ((ExtendedNode) node).getACL();
      long createdDate = node.hasProperty(NodeTypeConstants.EXO_DATE_CREATED) ? node.getProperty(NodeTypeConstants.EXO_DATE_CREATED)
                                                                                    .getDate()
                                                                                    .getTimeInMillis()
                                                                              : 0;
      long modifiedDate = JCRDocumentsUtil.getLastModifiedDate(node);
      descriptions.put(id,
                       new DocumentIndexInfo(id,
                                             JCRDocumentsUtil.getTitle(node),
                                             stringProperty(node, NodeTypeConstants.EXO_OWNER),
                                             acl == null ? null : acl.getOwner(),
                                             createdDate,
                                             modifiedDate == 0 ? createdDate : modifiedDate,
                                             description(node),
                                             null,
                                             place.spaceId(),
                                             place.ancestorFolderIds(),
                                             readIdentities(acl)));
    }
    return descriptions;
  }

  /**
   * Lists a page of the files under a folder, its sub-folders included,
   * whoever may see them, ordered by {@code jcr:uuid}. Documents makes every
   * file it stores referenceable, so its identifier is its {@code jcr:uuid}; a
   * file that is not referenceable carries no {@code jcr:uuid} and is not
   * listed.
   * <p>
   * The end of the folder is decided from the rows the query returns, never
   * from the nodes loaded: the search index can hold a hit whose node no
   * longer exists (a file deleted since it was indexed), which the node
   * iterator would skip silently and so shorten the page. Each row's path is
   * read from the repository's item data, without loading the node; a hit
   * with no item is counted and skipped. It cannot move the cursor, since its
   * {@code jcr:uuid} is not readable, so the next query asks for as many more
   * rows as there are unreadable hits after the cursor, and for twice the last
   * page when that page held no readable hit, so that a run of unreadable hits
   * reaches the bound in a few queries; the page still ends at {@code limit}
   * files. A row whose node now lies outside the
   * folder (a file moved since it was indexed) moves the cursor but is not
   * listed.
   *
   * @param session a session that may read every node, a system session
   * @param folderId the folder's identifier
   * @param afterId the identifier the page starts after, null for the first
   *          page
   * @param limit the most identifiers returned
   * @return up to {@code limit} file identifiers following {@code afterId},
   *         fewer only when the query has no row left; empty when no folder
   *         carries {@code folderId}
   * @throws RepositoryException when the repository fails otherwise than by
   *           finding no node, or when a full page leaves more than
   *           {@link #MAX_UNREADABLE_HITS} unreadable hits after the cursor
   *           and no readable one
   */
  public static List<String> listFileIds(ExtendedSession session,
                                         String folderId,
                                         String afterId,
                                         int limit) throws RepositoryException {
    Node folder = find(session, folderId);
    if (folder == null || typeOf(folder) != DocumentNodeType.FOLDER) {
      return List.of();
    }
    String folderPath = folder.getPath();
    String descendantPrefix = folderPath + "/";
    List<String> fileIds = new ArrayList<>(Math.min(limit, INITIAL_PAGE_CAPACITY));
    String cursor = afterId;
    int unreadableAfterCursor = 0;
    int asked = 0;
    while (fileIds.size() < limit) {
      if (unreadableAfterCursor > MAX_UNREADABLE_HITS) {
        throw new RepositoryException("More than " + MAX_UNREADABLE_HITS
            + " search index hits without a node follow a file under a folder");
      }
      // a page that held no readable hit doubles the next one, so the bound is reached in a few queries
      asked = Math.max(limit - fileIds.size() + unreadableAfterCursor,
                       unreadableAfterCursor == asked ? 2 * asked : 0);
      QueryImpl query = (QueryImpl) session.getWorkspace()
                                           .getQueryManager()
                                           .createQuery(listFileIdsStatement(folderPath, cursor), Query.SQL);
      query.setLimit(asked);
      RowIterator rows = query.execute().getRows();
      int read = 0;
      // unreadable hits after the last readable one, the cursor's: the next query reads them again
      int unreadableSinceLastFile = 0;
      while (rows.hasNext()) {
        Row row = rows.nextRow();
        read++;
        String path = rowPath(row);
        String uuid = path == null ? null : rowUuid(row);
        if (uuid == null) {
          unreadableSinceLastFile++;
          continue;
        }
        cursor = uuid;
        unreadableSinceLastFile = 0;
        if (path.startsWith(descendantPrefix)) {
          fileIds.add(cursor);
          if (fileIds.size() == limit) {
            break;
          }
        }
      }
      unreadableAfterCursor = unreadableSinceLastFile;
      if (read < asked) {
        break;
      }
    }
    return fileIds;
  }

  /**
   * Tells whether a node lies in the trash. Moving an item to the trash marks
   * that item only with {@code exo:restoreLocation}, so a file under a trashed
   * folder is recognised by an ancestor's mark; the trash's own path is
   * configuration and is not relied on.
   *
   * @param node a node
   * @return true when the node or one of its ancestors carries the trash mark
   * @throws RepositoryException when a node's type or parent cannot be read
   */
  private static boolean inTrash(Node node) throws RepositoryException {
    Node current = node;
    while (current != null) {
      if (current.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION)) {
        return true;
      }
      current = current.getDepth() == 0 ? null : current.getParent();
    }
    return false;
  }

  /**
   * Reads a row's path from the repository's item data, without loading its
   * node.
   *
   * @param row a row of a query result
   * @return the path of the row's node, null when no node carries its hit any
   *         more
   * @throws RepositoryException when the repository fails otherwise
   */
  private static String rowPath(Row row) throws RepositoryException {
    try {
      Value value = row.getValue(NodeTypeConstants.JCR_PATH);
      return value == null ? null : value.getString();
    } catch (RepositoryException e) {
      if (isMissingItem(e)) {
        return null;
      }
      throw e;
    }
  }

  /**
   * Reads a row's {@code jcr:uuid}. Unlike its path, the engine reads it from
   * the row's node, which it loads, and answers a node deleted since the row's
   * path was read by dereferencing the null node it gets back
   * ({@code RowIteratorImpl.RowImpl#getValue}, the {@code getNodeImpl()} branch
   * at jcr {@code RowIteratorImpl.java:417}); the {@code Row} interface offers
   * no check that does not throw, so that one read is guarded.
   *
   * @param row a row of a query result whose path was read
   * @return the identifier, null when the node has none or no longer exists
   * @throws RepositoryException when the repository fails otherwise
   */
  private static String rowUuid(Row row) throws RepositoryException {
    Value value;
    try {
      value = row.getValue(NodeTypeConstants.JCR_UUID);
    } catch (RepositoryException e) {
      if (isMissingItem(e)) {
        return null;
      }
      throw e;
    } catch (NullPointerException e) { // NOSONAR the engine's answer for a node deleted since the path read, see above
      return null;
    }
    return value == null ? null : value.getString();
  }

  /**
   * Tells whether a row's read failed because its item no longer exists. The
   * engine's row wraps the {@link ItemNotFoundException} it raises for a
   * missing item in a plain {@link RepositoryException}, so the cause is read
   * too.
   *
   * @param e the failure of a row's read
   * @return true when the failure or its cause is an
   *         {@link ItemNotFoundException}
   */
  private static boolean isMissingItem(RepositoryException e) {
    return e instanceof ItemNotFoundException || e.getCause() instanceof ItemNotFoundException;
  }


  /**
   * Builds the statement reading a page of the files under a folder.
   *
   * @param folderPath the folder's path
   * @param afterId the identifier the page starts after, null for the first
   *          page
   * @return a JCR SQL statement, its string literals escaped
   */
  public static String listFileIdsStatement(String folderPath, String afterId) {
    String uuidCondition = afterId == null ? String.format(LIST_FILE_IDS_FIRST_CONDITION, NodeTypeConstants.JCR_UUID)
                                           : String.format(LIST_FILE_IDS_NEXT_CONDITION,
                                                           NodeTypeConstants.JCR_UUID,
                                                           escapeLiteral(afterId));
    return String.format(LIST_FILE_IDS_QUERY, escapeLiteral(folderPath), uuidCondition, NodeTypeConstants.JCR_UUID);
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
   * @param acl a node's access control list, possibly null
   * @return its owner, then every identity holding the read permission, each
   *         once, in the list's order
   */
  private static List<String> readIdentities(AccessControlList acl) {
    if (acl == null) {
      return List.of();
    }
    Set<String> identities = new LinkedHashSet<>();
    if (acl.getOwner() != null) {
      identities.add(acl.getOwner());
    }
    for (AccessControlEntry entry : acl.getPermissionEntries()) {
      if (PermissionType.READ.equals(entry.getPermission()) && entry.getIdentity() != null) {
        identities.add(entry.getIdentity());
      }
    }
    return new ArrayList<>(identities);
  }

  /**
   * @param node a file
   * @return its description, read on the file then on its content, its first
   *         value when it holds several; null when it has none
   * @throws RepositoryException when a property cannot be read
   */
  private static String description(Node node) throws RepositoryException {
    String description = firstValue(node, NodeTypeConstants.DC_DESCRIPTION);
    if (description == null && node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
      description = firstValue(node.getNode(NodeTypeConstants.JCR_CONTENT), NodeTypeConstants.DC_DESCRIPTION);
    }
    return description;
  }

  /**
   * @param node a node
   * @param name a property name
   * @return the property's value, its first one when it holds several; null
   *         when the node has no such property or it holds no value
   * @throws RepositoryException when the property cannot be read
   */
  private static String firstValue(Node node, String name) throws RepositoryException {
    if (!node.hasProperty(name)) {
      return null;
    }
    Property property = node.getProperty(name);
    if (!property.getDefinition().isMultiple()) {
      return property.getString();
    }
    Value[] values = property.getValues();
    return values.length == 0 ? null : values[0].getString();
  }

  /**
   * @param node a node
   * @param name a single-valued string property name
   * @return the property's value, null when the node has no such property
   * @throws RepositoryException when the property cannot be read
   */
  private static String stringProperty(Node node, String name) throws RepositoryException {
    return node.hasProperty(name) ? node.getProperty(name).getString() : null;
  }

  /**
   * @param value a value to place in a JCR SQL string literal
   * @return the value with each single quote doubled
   */
  private static String escapeLiteral(String value) {
    return value.replace("'", "''");
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
