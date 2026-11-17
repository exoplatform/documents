/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.storage.jcr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.model.RecycleBinSortField;
import org.exoplatform.documents.storage.RecycleBinStorage;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;

/**
 * Reads and removes the items of the Recycle Bins in the trash of the
 * repository. Each call opens a system session of its own and closes it, and
 * a removal that fails discards what it had pending. An item is a node
 * carrying exo:recycleBinItem under the trash home: its drive is the one
 * recorded on it when it was deleted, never deduced from its path.
 */
public class JCRRecycleBinStorage implements RecycleBinStorage {

  private static final Pattern    ITEM_ID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

  private final RepositoryService repositoryService;

  private final TrashStorage      trashStorage;

  private String                  trashHome;

  private String                  trashWorkspace;

  public JCRRecycleBinStorage(RepositoryService repositoryService, TrashStorage trashStorage) {
    this.repositoryService = repositoryService;
    this.trashStorage = trashStorage;
  }

  @Override
  public List<RecycleBinItem> getItems(RecycleBinFilter filter) throws RepositoryException {
    SessionProvider provider = createSystemProvider();
    try {
      QueryImpl query = (QueryImpl) createQuery(provider, getItemsStatement(filter, true));
      if (filter.getOffset() > 0) {
        query.setOffset(filter.getOffset());
      }
      if (filter.getLimit() > 0) {
        query.setLimit(filter.getLimit());
      }
      List<RecycleBinItem> items = new ArrayList<>();
      for (NodeIterator nodes = query.execute().getNodes(); nodes.hasNext();) {
        items.add(toItem(nodes.nextNode()));
      }
      return items;
    } finally {
      provider.close();
    }
  }

  @Override
  public int countItems(RecycleBinFilter filter) throws RepositoryException {
    SessionProvider provider = createSystemProvider();
    try {
      return (int) createQuery(provider, getItemsStatement(filter, false)).execute().getNodes().getSize();
    } finally {
      provider.close();
    }
  }

  @Override
  public RecycleBinItem getItem(String itemId) throws RepositoryException {
    // The id of an item is a UUID: anything else names no item and is not
    // written into a statement.
    if (itemId == null || !ITEM_ID_PATTERN.matcher(itemId).matches()) {
      return null;
    }
    SessionProvider provider = createSystemProvider();
    try {
      Node node = findNode(getSession(provider), itemId);
      // A shortcut trashed with the document it points at is no item.
      return node == null || node.hasProperty(NodeTypeConstants.RECYCLE_BIN_OF) ? null : toItem(node);
    } finally {
      provider.close();
    }
  }

  @Override
  public List<RecycleBinItem> removeItem(String itemId) throws RepositoryException {
    if (itemId == null || !ITEM_ID_PATTERN.matcher(itemId).matches()) {
      throw new PathNotFoundException("No node of the trash carries the id given");
    }
    SessionProvider provider = createSystemProvider();
    try {
      Session session = getSession(provider);
      try {
        Node item = findNode(session, itemId);
        if (item == null) {
          throw new PathNotFoundException("No node of the trash carries the id " + itemId);
        }
        String itemPath = item.getPath();
        // What leaves with the item, by path: a shortcut may be found twice.
        Map<String, Node> shortcuts = new TreeMap<>(Comparator.reverseOrder());
        if (!item.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
          collect(session,
                  "SELECT * FROM " + NodeTypeConstants.EXO_SYMLINK + " WHERE " + NodeTypeConstants.EXO_SYMLINK_UUID + "='"
                      + ((ExtendedNode) item).getIdentifier() + "' AND " + NodeTypeConstants.EXO_WORKSPACE + "='"
                      + trashWorkspace.replace("'", "''") + "'",
                  itemPath,
                  shortcuts);
        }
        collect(session, selectItems() + " AND " + NodeTypeConstants.RECYCLE_BIN_OF + " = '" + itemId + "'", itemPath, shortcuts);
        // A shortcut that leaves with the item may be an item of a bin itself:
        // it is read before it is removed, for the caller to count it out. A
        // shortcut outside the trash is in no bin, whatever marks it kept.
        List<RecycleBinItem> removedWithIt = new ArrayList<>();
        for (Map.Entry<String, Node> entry : shortcuts.entrySet()) {
          Node shortcut = entry.getValue();
          if (entry.getKey().startsWith(trashHome + "/") && shortcut.isNodeType(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM)
              && shortcut.hasProperty(NodeTypeConstants.RECYCLE_BIN_OWNER)) {
            removedWithIt.add(toItem(shortcut));
          }
          shortcut.remove();
        }
        item.remove();
        session.save();
        return removedWithIt;
      } catch (RepositoryException | RuntimeException e) {
        session.refresh(false);
        throw e;
      }
    } finally {
      provider.close();
    }
  }

  SessionProvider createSystemProvider() {
    return SessionProvider.createSystemProvider();
  }

  private Query createQuery(SessionProvider provider, String statement) throws RepositoryException {
    return getSession(provider).getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
  }

  private Session getSession(SessionProvider provider) throws RepositoryException {
    readTrashLocation();
    return provider.getSession(trashWorkspace, repositoryService.getCurrentRepository());
  }

  /** The node of the trash carrying an id, an item or a shortcut trashed with one. */
  private Node findNode(Session session, String itemId) throws RepositoryException {
    String statement = selectItems() + " AND " + NodeTypeConstants.TRASH_ID + " = '" + itemId + "'";
    QueryImpl query = (QueryImpl) session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
    query.setLimit(1);
    NodeIterator nodes = query.execute().getNodes();
    return nodes.hasNext() ? nodes.nextNode() : null;
  }

  /** Adds the nodes a statement finds, but the ones the item holds: they leave with it. */
  private void collect(Session session, String statement, String itemPath, Map<String, Node> found) throws RepositoryException {
    Query query = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
    for (NodeIterator nodes = query.execute().getNodes(); nodes.hasNext();) {
      Node node = nodes.nextNode();
      String path = node.getPath();
      if (!path.equals(itemPath) && !path.startsWith(itemPath + "/")) {
        found.put(path, node);
      }
    }
  }

  /**
   * The trash home and its workspace are the ones the trash storage settled
   * on at startup, read from it once rather than configured twice.
   */
  private synchronized void readTrashLocation() throws RepositoryException {
    if (trashHome == null) {
      Node trashHomeNode = trashStorage.getTrashHomeNode();
      if (trashHomeNode == null) {
        throw new RepositoryException("The trash home cannot be read");
      }
      trashWorkspace = trashHomeNode.getSession().getWorkspace().getName();
      trashHome = trashHomeNode.getPath();
    }
  }

  private String selectItems() throws RepositoryException {
    readTrashLocation();
    // The path is spliced as it is: a path comparison takes no escape.
    return "SELECT * FROM " + NodeTypeConstants.EXO_RECYCLE_BIN_ITEM + " WHERE jcr:path LIKE '" + trashHome + "/%'";
  }

  private String getItemsStatement(RecycleBinFilter filter, boolean sorted) throws RepositoryException {
    StringBuilder statement = new StringBuilder(selectItems());
    // The drive is always part of the statement: a shortcut trashed with the
    // document it points at records none and is in no bin.
    statement.append(" AND ").append(NodeTypeConstants.RECYCLE_BIN_OWNER).append(" = '").append(filter.getOwnerId()).append("'");
    if (StringUtils.isNotBlank(filter.getQuery())) {
      statement.append(" AND ")
               .append(NodeTypeConstants.RECYCLE_BIN_NAME)
               .append(" LIKE '%")
               .append(escapeTerm(filter.getQuery().trim()))
               .append("%' ESCAPE '\\'");
    }
    if (sorted) {
      statement.append(" ORDER BY ").append(getSortProperty(filter.getSortField())).append(filter.isAscending() ? " ASC" : " DESC");
    }
    return statement.toString();
  }

  /**
   * The term as the name of an item contains it: names are recorded in lower
   * case, and the characters a pattern or a literal reads are escaped.
   */
  private static String escapeTerm(String term) {
    return term.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
  }

  private static String getSortProperty(RecycleBinSortField sortField) {
    if (sortField == null) {
      return NodeTypeConstants.DELETED_DATE;
    }
    return switch (sortField) {
    case NAME -> NodeTypeConstants.RECYCLE_BIN_NAME;
    case ORIGINAL_LOCATION -> NodeTypeConstants.RESTORE_PATH;
    case DELETED_BY -> NodeTypeConstants.DELETED_BY;
    case SIZE -> NodeTypeConstants.RECYCLE_BIN_SIZE;
    default -> NodeTypeConstants.DELETED_DATE;
    };
  }

  private static RecycleBinItem toItem(Node node) throws RepositoryException {
    RecycleBinItem item = new RecycleBinItem();
    item.setId(getString(node, NodeTypeConstants.TRASH_ID));
    item.setDocumentId(((ExtendedNode) node).getIdentifier());
    String ownerId = getString(node, NodeTypeConstants.RECYCLE_BIN_OWNER);
    item.setOwnerId(StringUtils.isNumeric(ownerId) ? Long.valueOf(ownerId) : null);
    item.setName(JCRDocumentsUtil.getTitle(node));
    item.setShortcut(node.isNodeType(NodeTypeConstants.EXO_SYMLINK));
    item.setFolder(!item.isShortcut() && JCRDocumentsUtil.isFolder(node));
    if (node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
      item.setMimeType(getString(node.getNode(NodeTypeConstants.JCR_CONTENT), NodeTypeConstants.JCR_MIME_TYPE));
    }
    if (node.hasProperty(NodeTypeConstants.RECYCLE_BIN_SIZE)) {
      item.setSize(node.getProperty(NodeTypeConstants.RECYCLE_BIN_SIZE).getLong());
    }
    if (node.hasProperty(NodeTypeConstants.DELETED_DATE)) {
      item.setDeletedDate(node.getProperty(NodeTypeConstants.DELETED_DATE).getDate().getTimeInMillis());
    }
    item.setDeletedBy(getString(node, NodeTypeConstants.DELETED_BY));
    item.setRestorePath(getString(node, NodeTypeConstants.RESTORE_PATH));
    item.setRestoreWorkspace(getString(node, NodeTypeConstants.RESTORE_WORKSPACE));
    item.setRestoreParentId(getString(node, NodeTypeConstants.RESTORE_PARENT_ID));
    return item;
  }

  private static String getString(Node node, String property) throws RepositoryException {
    return node.hasProperty(property) ? node.getProperty(property).getString() : null;
  }

}
