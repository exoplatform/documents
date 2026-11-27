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

import java.security.AccessControlException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.Query;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.RecycleBinError;
import org.exoplatform.documents.model.RecycleBinDrive;
import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.constant.RecycleBinSortField;
import org.exoplatform.documents.storage.RecycleBinStorage;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.jcr.impl.RepositoryServiceImpl;
import org.exoplatform.services.jcr.impl.WorkspaceContainer;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import io.meeds.portal.thumbnail.model.FileContent;

/**
 * Reads, restores and removes the items of the Recycle Bins in the trash of
 * the repository. Each call opens a system session of its own and closes it,
 * and a restore or a removal that fails discards what it had pending. An item is a node
 * carrying exo:recycleBinItem under the trash home: its drive is the one
 * recorded on it when it was deleted, never deduced from its path.
 */
public class JCRRecycleBinStorage implements RecycleBinStorage {

  private static final String     GROUP_ADMINISTRATORS = "*:/platform/administrators";

  /** A date as a statement reads it, in UTC. */
  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")
                                                                             .withZone(ZoneOffset.UTC);

  private static final Pattern    ITEM_ID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

  private final RepositoryService repositoryService;

  private final TrashStorage      trashStorage;

  private final NodeHierarchyCreator nodeHierarchyCreator;

  private final IdentityManager   identityManager;

  private final SpaceService      spaceService;

  private String                  trashHome;

  private String                  trashWorkspace;

  public JCRRecycleBinStorage(RepositoryService repositoryService,
                              TrashStorage trashStorage,
                              NodeHierarchyCreator nodeHierarchyCreator,
                              IdentityManager identityManager,
                              SpaceService spaceService) {
    this.repositoryService = repositoryService;
    this.trashStorage = trashStorage;
    this.nodeHierarchyCreator = nodeHierarchyCreator;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
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
      // The items of a page share most of their folders: each is read once.
      Map<String, String> folderTitles = new HashMap<>();
      Session session = getSession(provider);
      for (NodeIterator nodes = query.execute().getNodes(); nodes.hasNext();) {
        RecycleBinItem item = toItem(nodes.nextNode());
        item.setRestoreFolderTitles(getRestoreFolderTitles(session, item, folderTitles));
        items.add(item);
      }
      return items;
    } finally {
      provider.close();
    }
  }

  /**
   * The names the documents show of the folders an item was deleted from,
   * null when they are in another workspace than the trash.
   */
  private List<String> getRestoreFolderTitles(Session session,
                                              RecycleBinItem item,
                                              Map<String, String> folderTitles) throws RepositoryException {
    String restorePath = item.getRestorePath();
    if (StringUtils.isBlank(restorePath)
        || item.getRestoreWorkspace() != null && !item.getRestoreWorkspace().equals(trashWorkspace)) {
      return null; // NOSONAR an absent list tells the names were not read
    }
    String[] folders = StringUtils.split(getParentPath(restorePath), '/');
    List<String> titles = new ArrayList<>(folders.length);
    StringBuilder folderPath = new StringBuilder();
    for (String folder : folders) {
      folderPath.append('/').append(folder);
      String path = folderPath.toString();
      String title = folderTitles.get(path);
      if (title == null) {
        title = StringUtils.defaultIfBlank(readFolderTitle(session, path), folder);
        folderTitles.put(path, title);
      }
      titles.add(title);
    }
    return titles;
  }

  /**
   * The title of the folder at a path, or of the folder deleted from that path
   * the trash keeps, null when neither is found.
   */
  private String readFolderTitle(Session session, String path) throws RepositoryException {
    if (session.itemExists(path) && session.getItem(path) instanceof Node folder) {
      return JCRDocumentsUtil.getTitle(folder);
    }
    String statement = selectItems() + " AND " + NodeTypeConstants.RESTORE_PATH + " = '" + path.replace("'", "''") + "'";
    QueryImpl query = (QueryImpl) session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
    query.setLimit(1);
    NodeIterator nodes = query.execute().getNodes();
    return nodes.hasNext() ? JCRDocumentsUtil.getTitle(nodes.nextNode()) : null;
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
  public RecycleBinItem getItemOfDocument(String documentId) throws RepositoryException {
    if (StringUtils.isBlank(documentId)) {
      return null;
    }
    SessionProvider provider = createSystemProvider();
    try {
      Node node;
      try {
        node = ((ExtendedSession) getSession(provider)).getNodeByIdentifier(documentId);
      } catch (ItemNotFoundException e) {
        return null;
      }
      return toItemOrNull(node);
    } finally {
      provider.close();
    }
  }

  @Override
  public RecycleBinItem getItemAt(String path) throws RepositoryException {
    if (StringUtils.isBlank(path)) {
      return null;
    }
    SessionProvider provider = createSystemProvider();
    try {
      try {
        return getSession(provider).getItem(path) instanceof Node node ? toItemOrNull(node) : null;
      } catch (PathNotFoundException e) {
        return null;
      }
    } finally {
      provider.close();
    }
  }

  /**
   * The item a node is, null when it is none: a node of a drive, whatever
   * marks it kept, a node a deleted folder holds, which carries no mark of
   * its own, or a shortcut trashed with the document it points at.
   */
  private RecycleBinItem toItemOrNull(Node node) throws RepositoryException {
    boolean item = node.getPath().startsWith(trashHome + "/") && node.isNodeType(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM)
        && node.hasProperty(NodeTypeConstants.TRASH_ID) && !node.hasProperty(NodeTypeConstants.RECYCLE_BIN_OF);
    return item ? toItem(node) : null;
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
        if (!itemPath.startsWith(trashHome + "/") || !item.isNodeType(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM)) {
          // The index answered with a node that left the trash since.
          throw new PathNotFoundException("The node of the id " + itemId + " is no longer in the trash");
        }
        // What leaves with the item, by path: a shortcut may be found twice.
        Map<String, Node> shortcuts = new TreeMap<>(Comparator.reverseOrder());
        if (!item.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
          collect(session, linksTo(item), itemPath, shortcuts);
        }
        collect(session, selectItems() + " AND " + NodeTypeConstants.RECYCLE_BIN_OF + " = '" + itemId + "'", itemPath, shortcuts);
        if (JCRDocumentsUtil.isFolder(item)) {
          collectShortcutsInside(session, item, itemPath, shortcuts);
        }
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

  @Override
  public FileContent getItemContent(String itemId) throws RepositoryException {
    if (itemId == null || !ITEM_ID_PATTERN.matcher(itemId).matches()) {
      return null;
    }
    SessionProvider provider = createSystemProvider();
    try {
      Node node = findNode(getSession(provider), itemId);
      if (node == null || node.hasProperty(NodeTypeConstants.RECYCLE_BIN_OF) || !node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
        return null;
      }
      Node content = node.getNode(NodeTypeConstants.JCR_CONTENT);
      if (!content.hasProperty(NodeTypeConstants.JCR_DATA)) {
        return null;
      }
      return new FileContent(((ExtendedNode) node).getIdentifier(),
                             JCRDocumentsUtil.getTitle(node),
                             getString(content, NodeTypeConstants.JCR_MIME_TYPE),
                             content.getProperty(NodeTypeConstants.JCR_DATA).getStream(),
                             content.hasProperty(NodeTypeConstants.JCR_LAST_MODIFIED) ? content.getProperty(NodeTypeConstants.JCR_LAST_MODIFIED)
                                                                                               .getDate()
                                                                                               .getTime()
                                                                                      : null);
    } finally {
      provider.close();
    }
  }

  @Override
  public List<String> getIdsDeletedBefore(Instant instant) throws RepositoryException {
    SessionProvider provider = createSystemProvider();
    try {
      String statement = selectItems() + " AND " + NodeTypeConstants.DELETED_DATE + " < TIMESTAMP '" + TIMESTAMP_FORMAT.format(instant)
          + "'";
      List<String> ids = new ArrayList<>();
      for (NodeIterator nodes = createQuery(provider, statement).execute().getNodes(); nodes.hasNext();) {
        String id = getString(nodes.nextNode(), NodeTypeConstants.TRASH_ID);
        if (id != null) {
          ids.add(id);
        }
      }
      return ids;
    } finally {
      provider.close();
    }
  }

  @Override
  public RecycleBinItem getRemovableItem(String itemId) throws RepositoryException {
    if (itemId == null || !ITEM_ID_PATTERN.matcher(itemId).matches()) {
      return null;
    }
    SessionProvider provider = createSystemProvider();
    try {
      Session session = getSession(provider);
      Node node = findNode(session, itemId);
      if (node == null) {
        return null;
      }
      // A shortcut trashed with a document leaves with it while it is there.
      String documentItemId = getString(node, NodeTypeConstants.RECYCLE_BIN_OF);
      boolean heldByItsDocument = documentItemId != null && ITEM_ID_PATTERN.matcher(documentItemId).matches()
          && findNode(session, documentItemId) != null;
      return heldByItsDocument ? null : toItem(node);
    } finally {
      provider.close();
    }
  }

  @Override
  public List<RecycleBinDrive> getDriveTotals() throws RepositoryException {
    SessionProvider provider = createSystemProvider();
    try {
      Map<Long, RecycleBinDrive> totals = new TreeMap<>();
      for (NodeIterator nodes = createQuery(provider, selectItems()).execute().getNodes(); nodes.hasNext();) {
        Node node = nodes.nextNode();
        String ownerId = getString(node, NodeTypeConstants.RECYCLE_BIN_OWNER);
        // A shortcut trashed with a document records no owner, as an item of no drive.
        if (!StringUtils.isNumeric(ownerId)) {
          continue;
        }
        RecycleBinDrive drive = totals.computeIfAbsent(Long.valueOf(ownerId), id -> new RecycleBinDrive(id, 0, 0));
        drive.setItemsCount(drive.getItemsCount() + 1);
        if (node.hasProperty(NodeTypeConstants.RECYCLE_BIN_SIZE)) {
          drive.setItemsSize(drive.getItemsSize() + node.getProperty(NodeTypeConstants.RECYCLE_BIN_SIZE).getLong());
        }
      }
      return new ArrayList<>(totals.values());
    } finally {
      provider.close();
    }
  }

  @Override
  public RecycleBinItem restoreItem(String itemId, Identity caller, boolean administrator) throws RepositoryException,
                                                                                           ObjectNotFoundException,
                                                                                           IllegalAccessException {
    if (itemId == null || !ITEM_ID_PATTERN.matcher(itemId).matches()) {
      throw new ObjectNotFoundException(RecycleBinError.ITEM_NOT_FOUND);
    }
    if (!administrator && caller == null) {
      throw new IllegalAccessException(RecycleBinError.RESTORE_TARGET_FORBIDDEN);
    }
    SessionProvider provider = createSystemProvider();
    Identity checkedCaller = administrator ? null : caller;
    try {
      Session session = getSession(provider);
      Session restoreSession = null;
      try {
        Node node = findNode(session, itemId);
        // A shortcut trashed with the document it points at is no item.
        if (node == null || node.hasProperty(NodeTypeConstants.RECYCLE_BIN_OF)) {
          throw new ObjectNotFoundException(RecycleBinError.ITEM_NOT_FOUND);
        }
        RecycleBinItem item = toItem(node);
        if (item.getRestorePath() == null) {
          throw new IllegalArgumentException(RecycleBinError.RESTORE_LOCATION_MISSING);
        }
        String shortcutRefusal = item.isShortcut() ? shortcutRefusal(provider, session, node) : null;
        if (shortcutRefusal != null) {
          throw new IllegalArgumentException(shortcutRefusal);
        }
        if (item.getRestoreWorkspace() != null && !item.getRestoreWorkspace().equals(trashWorkspace)) {
          restoreSession = provider.getSession(item.getRestoreWorkspace(), repositoryService.getCurrentRepository());
          restoreToItsWorkspace(session, restoreSession, checkedCaller, node, item);
        } else {
          restoreInOneSave(session, provider, checkedCaller, node, item);
        }
        return item;
      } catch (RepositoryException | RuntimeException | ObjectNotFoundException | IllegalAccessException e) {
        session.refresh(false);
        if (restoreSession != null) {
          restoreSession.refresh(false);
        }
        throw e;
      }
    } finally {
      provider.close();
    }
  }

  SessionProvider createSystemProvider() {
    return SessionProvider.createSystemProvider();
  }

  /**
   * A session acting as the user given, whoever the thread acts as: a restore
   * run on a thread of its own still asks the caller's rights. The one who
   * opens it logs it out.
   */
  Session openSession(Identity user, String workspace) throws RepositoryException {
    ManageableRepository repository = repositoryService.getCurrentRepository();
    WorkspaceContainer workspaceContainer = ((RepositoryServiceImpl) repositoryService).getRepositoryContainer(repository.getConfiguration()
                                                                                                                         .getName())
                                                                                      .getWorkspaceContainer(workspace);
    return new SessionImpl(workspace, new ConversationState(user), workspaceContainer);
  }

  /**
   * Moves an item of the trash workspace back, with the shortcuts trashed
   * with it, and gives it its name, its permissions and its marks back, as
   * pending changes of the session saved once at the end.
   */
  private void restoreInOneSave(Session session,
                                SessionProvider provider,
                                Identity caller,
                                Node node,
                                RecycleBinItem item) throws RepositoryException, IllegalAccessException {
    String itemPath = node.getPath();
    // The owner is resolved before any target: without it no drive is known.
    org.exoplatform.social.core.identity.model.Identity owner = null;
    if (item.getOwnerId() != null) {
      owner = identityManager.getIdentity(String.valueOf(item.getOwnerId()));
      if (owner == null || owner.isDeleted()) {
        throw new IllegalArgumentException(RecycleBinError.RESTORE_LOCATION_MISSING);
      }
    }
    String name = getRestoreName(item.getRestorePath());
    Node target = findOriginalFolder(session, provider, item.getRestoreParentId(), item.getRestorePath(), item.getOwnerId());
    boolean toRoot = false;
    if (owner == null) {
      // An item of no drive has no root to go to.
      if (target == null) {
        throw new IllegalArgumentException(RecycleBinError.RESTORE_LOCATION_MISSING);
      }
      if (!acceptsContent(caller, target)) {
        throw new IllegalAccessException(RecycleBinError.RESTORE_TARGET_FORBIDDEN);
      }
    } else if (target == null || !acceptsContent(caller, target)) {
      // At the root the item takes the permissions it had: one that has none
      // to take would get the root's, wider than its own, so it stays.
      if (target != null && !node.isNodeType(NodeTypeConstants.EXO_PRIVILEGEABLE)
          && !node.hasProperty(NodeTypeConstants.RECYCLE_BIN_PERMISSIONS)) {
        throw new IllegalAccessException(RecycleBinError.RESTORE_TARGET_FORBIDDEN);
      }
      target = getDriveRoot(session, provider, owner);
      if (target == null) {
        throw new IllegalArgumentException(RecycleBinError.RESTORE_LOCATION_MISSING);
      }
      if (!acceptsContent(caller, target)) {
        throw new IllegalAccessException(RecycleBinError.RESTORE_TARGET_FORBIDDEN);
      }
      toRoot = true;
    }

    // Both statements read what is saved: they run before anything is pending.
    Map<String, Node> shortcuts = new TreeMap<>(Comparator.reverseOrder());
    collect(session, selectItems() + " AND " + NodeTypeConstants.RECYCLE_BIN_OF + " = '" + item.getId() + "'", itemPath, shortcuts);
    boolean personalDrive = owner != null && !owner.isSpace();
    List<String> withOwnPermissions = personalDrive ? findDescendantsWithOwnPermissions(session, itemPath) : List.of();
    Map<String, String[]> snapshot = toRoot ? readPermissionsSnapshot(node) : Map.of();

    // The permissions are given back while the item is still in the trash:
    // the move keeps the ones a node holds of its own, and a change of them
    // after the move in the same save would leave what the item holds at its
    // paths in the trash in the cache of the repository.
    if (!snapshot.isEmpty()) {
      if (node.canAddMixin(NodeTypeConstants.EXO_PRIVILEGEABLE)) {
        node.addMixin(NodeTypeConstants.EXO_PRIVILEGEABLE);
      }
      ((ExtendedNode) node).setPermissions(snapshot);
    }
    if (personalDrive) {
      removeAdministratorsEntries(node, owner.getRemoteId());
      for (String relativePath : withOwnPermissions) {
        removeAdministratorsEntries((Node) session.getItem(itemPath + relativePath), owner.getRemoteId());
      }
    }
    moveBack(session, itemPath, target.getPath(), name);
    // The paths are walked in descending order, so that moving a shortcut
    // never renumbers a same-name sibling still to move.
    for (Node shortcut : shortcuts.values()) {
      restoreShortcut(session, provider, shortcut);
    }
    session.save();
  }

  /**
   * The trash workspace is not the item's: the item is copied back to the
   * folder it was deleted from, then removed from the trash, as two
   * operations. A failure between them leaves the item in both.
   */
  private void restoreToItsWorkspace(Session session,
                                     Session restoreSession,
                                     Identity caller,
                                     Node node,
                                     RecycleBinItem item) throws RepositoryException, IllegalAccessException {
    Node target;
    try {
      target = (Node) restoreSession.getItem(getParentPath(item.getRestorePath()));
    } catch (PathNotFoundException e) {
      throw new IllegalArgumentException(RecycleBinError.RESTORE_LOCATION_MISSING);
    }
    if (!acceptsContent(caller, target)) {
      throw new IllegalAccessException(RecycleBinError.RESTORE_TARGET_FORBIDDEN);
    }
    String name = getRestoreName(item.getRestorePath());
    String targetPath = target.getPath();
    int count = getFreeNameIndex(restoreSession, targetPath, name);
    String destinationPath = getChildPath(targetPath, count == 0 ? name : JCRDocumentsUtil.increaseNameIndex(name, count));
    restoreSession.getWorkspace().clone(trashWorkspace, node.getPath(), destinationPath, true);
    Node restored = (Node) restoreSession.getItem(destinationPath);
    giveBackNameAndMarks(restored, count);
    restoreSession.save();
    node.remove();
    session.save();
  }

  /**
   * Removes the marks of a node of the trash, then moves it under a folder,
   * at the first name free there. Removed after the move in the same save,
   * the marks would leave what a node whose permissions change with the move
   * holds at its paths in the trash in the cache of the repository.
   */
  private void moveBack(Session session, String path, String targetPath, String name) throws RepositoryException {
    int count = getFreeNameIndex(session, targetPath, name);
    giveBackNameAndMarks((Node) session.getItem(path), count);
    String destinationPath = getChildPath(targetPath, count == 0 ? name : JCRDocumentsUtil.increaseNameIndex(name, count));
    session.move(path, destinationPath);
  }

  /**
   * The name of a node and its title are two names: a restored node renamed
   * for a taken name gets the same index in its title, as a moved one does.
   */
  private void giveBackNameAndMarks(Node restored, int count) throws RepositoryException {
    if (restored.isNodeType(NodeTypeConstants.MIX_VERSIONABLE) && !restored.isCheckedOut()) {
      restored.checkout();
    }
    if (count > 0 && restored.hasProperty(NodeTypeConstants.EXO_TITLE)) {
      String title = restored.getProperty(NodeTypeConstants.EXO_TITLE).getString();
      restored.setProperty(NodeTypeConstants.EXO_TITLE, JCRDocumentsUtil.getNewIndexedName(title, "(" + count + ")"));
    }
    if (restored.isNodeType(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM)) {
      restored.removeMixin(NodeTypeConstants.EXO_RECYCLE_BIN_ITEM);
    }
    if (restored.isNodeType(NodeTypeConstants.EXO_RESTORE_LOCATION)) {
      restored.removeMixin(NodeTypeConstants.EXO_RESTORE_LOCATION);
    }
  }

  /** The index that makes a name free under a folder, 0 when the name itself is. */
  private int getFreeNameIndex(Session session, String targetPath, String name) throws RepositoryException {
    int count = 0;
    String candidate = name;
    while (session.itemExists(getChildPath(targetPath, candidate))) {
      candidate = JCRDocumentsUtil.increaseNameIndex(name, ++count);
    }
    return count;
  }

  /**
   * A shortcut trashed with the item goes back to its own folder, with no
   * check of the caller: it left with the item. One whose folder is gone or
   * whose name is taken stays in the trash.
   */
  private void restoreShortcut(Session session, SessionProvider provider, Node shortcut) throws RepositoryException {
    String restorePath = getString(shortcut, NodeTypeConstants.RESTORE_PATH);
    String restoreWorkspace = getString(shortcut, NodeTypeConstants.RESTORE_WORKSPACE);
    if (restorePath == null || restoreWorkspace != null && !restoreWorkspace.equals(trashWorkspace)) {
      return;
    }
    Node folder = findOriginalFolder(session, provider, getString(shortcut, NodeTypeConstants.RESTORE_PARENT_ID), restorePath, null);
    String name = getRestoreName(restorePath);
    if (folder == null || session.itemExists(getChildPath(folder.getPath(), name))) {
      return;
    }
    moveBack(session, shortcut.getPath(), folder.getPath(), name);
  }

  /**
   * The folder a node was deleted from: the one recorded by its id, wherever
   * it was moved since, or for a node that recorded none the parent of the
   * path it was deleted from. Null when that folder is gone, is itself in the
   * trash, or left the drive given. Only a folder the repository says is not
   * there is gone: any other failure of the read is thrown.
   */
  private Node findOriginalFolder(Session session,
                                  SessionProvider provider,
                                  String parentId,
                                  String restorePath,
                                  Long ownerId) throws RepositoryException {
    Node folder;
    try {
      if (parentId != null) {
        folder = session.getNodeByUUID(parentId);
      } else if (session.getItem(getParentPath(restorePath)) instanceof Node parent) {
        folder = parent;
      } else {
        return null;
      }
    } catch (ItemNotFoundException | PathNotFoundException e) {
      return null;
    }
    String folderPath = folder.getPath();
    if (folderPath.equals(trashHome) || folderPath.startsWith(trashHome + "/")) {
      return null;
    }
    if (ownerId != null) {
      String folderOwner = JCRDocumentsUtil.resolveRecycleBinOwner(getChildPath(folderPath, getRestoreName(restorePath)),
                                                                  nodeHierarchyCreator,
                                                                  identityManager,
                                                                  spaceService,
                                                                  provider);
      if (!String.valueOf(ownerId).equals(folderOwner)) {
        return null;
      }
    }
    return folder;
  }

  /** The root folder of a drive: the Private folder of a user, the Documents folder of a space. */
  private Node getDriveRoot(Session session,
                            SessionProvider provider,
                            org.exoplatform.social.core.identity.model.Identity owner) throws RepositoryException {
    String rootPath;
    if (owner.isSpace()) {
      Space space = spaceService.getSpaceByPrettyName(owner.getRemoteId());
      if (space == null) {
        return null;
      }
      rootPath = JCRDocumentsUtil.getGroupsPath(nodeHierarchyCreator) + space.getGroupId() + "/" + JCRDocumentsUtil.DOCUMENTS_NODE;
    } else {
      rootPath = JCRDocumentsUtil.getUserHomePath(nodeHierarchyCreator, provider, owner.getRemoteId()) + "/"
          + JCRDocumentsUtil.USER_PRIVATE_ROOT_NODE;
    }
    try {
      return session.getItem(rootPath) instanceof Node root ? root : null;
    } catch (PathNotFoundException e) {
      return null;
    }
  }

  /** Whether a folder accepts content from the caller, asked in the caller's own session. */
  private boolean acceptsContent(Identity caller, Node folder) throws RepositoryException {
    if (caller == null) {
      return true;
    }
    Session callerSession = openSession(caller, folder.getSession().getWorkspace().getName());
    try {
      callerSession.checkPermission(folder.getPath(), PermissionType.ADD_NODE);
      return true;
    } catch (AccessControlException e) {
      return false;
    } finally {
      callerSession.logout();
    }
  }

  /** The permission entries recorded at the deletion of a node that had none of its own. */
  private Map<String, String[]> readPermissionsSnapshot(Node node) throws RepositoryException {
    if (!node.hasProperty(NodeTypeConstants.RECYCLE_BIN_PERMISSIONS)) {
      return Map.of();
    }
    Map<String, List<String>> entries = new LinkedHashMap<>();
    for (Value value : node.getProperty(NodeTypeConstants.RECYCLE_BIN_PERMISSIONS).getValues()) {
      String entry = value.getString();
      int separator = entry.lastIndexOf(' ');
      if (separator > 0) {
        entries.computeIfAbsent(entry.substring(0, separator), key -> new ArrayList<>()).add(entry.substring(separator + 1));
      }
    }
    return toPermissions(entries);
  }

  /** The paths, relative to an item, of what it holds that carries permissions of its own. */
  private List<String> findDescendantsWithOwnPermissions(Session session, String itemPath) throws RepositoryException {
    // The path is spliced as it is but for a quote: a path comparison takes
    // no escape, so what it finds is kept only when the item holds it.
    String statement = "SELECT * FROM " + NodeTypeConstants.EXO_PRIVILEGEABLE + " WHERE jcr:path LIKE '" + itemPath.replace("'", "''")
        + "/%'";
    List<String> relativePaths = new ArrayList<>();
    Query query = session.getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
    for (NodeIterator nodes = query.execute().getNodes(); nodes.hasNext();) {
      String path = nodes.nextNode().getPath();
      if (path.startsWith(itemPath + "/")) {
        relativePaths.add(path.substring(itemPath.length()));
      }
    }
    return relativePaths;
  }

  /**
   * In a personal drive the administrators of the platform hold no entry: a
   * node restored there loses the one its own permissions carry. A shortcut
   * is left as it is. A node left with no entry gets its owner's.
   */
  private void removeAdministratorsEntries(Node node, String driveOwner) throws RepositoryException {
    if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK) || !node.isNodeType(NodeTypeConstants.EXO_PRIVILEGEABLE)) {
      return;
    }
    Map<String, List<String>> kept = new LinkedHashMap<>();
    boolean removed = false;
    for (AccessControlEntry entry : ((ExtendedNode) node).getACL().getPermissionEntries()) {
      if (GROUP_ADMINISTRATORS.equals(entry.getIdentity())) {
        removed = true;
      } else {
        kept.computeIfAbsent(entry.getIdentity(), key -> new ArrayList<>()).add(entry.getPermission());
      }
    }
    if (!removed) {
      return;
    }
    Map<String, String[]> permissions = toPermissions(kept);
    if (permissions.isEmpty()) {
      String nodeOwner = getString(node, NodeTypeConstants.EXO_OWNER);
      permissions.put(nodeOwner == null ? driveOwner : nodeOwner, PermissionType.ALL);
    }
    ((ExtendedNode) node).setPermissions(permissions);
  }

  private static Map<String, String[]> toPermissions(Map<String, List<String>> entries) {
    Map<String, String[]> permissions = new LinkedHashMap<>();
    entries.forEach((identity, types) -> permissions.put(identity, types.toArray(new String[0])));
    return permissions;
  }

  /** The name a node had where it was deleted from: the last segment of its path, without its index. */
  private static String getRestoreName(String restorePath) {
    String name = restorePath.substring(restorePath.lastIndexOf('/') + 1);
    return name.endsWith("]") && name.lastIndexOf('[') > 0 ? name.substring(0, name.lastIndexOf('[')) : name;
  }

  private static String getParentPath(String path) {
    int slash = path.lastIndexOf('/');
    return slash <= 0 ? "/" : path.substring(0, slash);
  }

  private static String getChildPath(String parentPath, String name) {
    return "/".equals(parentPath) ? "/" + name : parentPath + "/" + name;
  }

  private Query createQuery(SessionProvider provider, String statement) throws RepositoryException {
    return getSession(provider).getWorkspace().getQueryManager().createQuery(statement, Query.SQL);
  }

  private Session getSession(SessionProvider provider) throws RepositoryException {
    readTrashLocation();
    return provider.getSession(trashWorkspace, repositoryService.getCurrentRepository());
  }

  /**
   * Why a shortcut cannot be restored, read in a system session whoever
   * restores: the code of a node it points at that is in a Recycle Bin, or of
   * one that is gone, since restored it would open nothing; null while the
   * node it points at is outside the trash.
   */
  private String shortcutRefusal(SessionProvider provider, Session session, Node shortcut) throws RepositoryException {
    String targetId = getString(shortcut, NodeTypeConstants.EXO_SYMLINK_UUID);
    if (StringUtils.isBlank(targetId)) {
      return RecycleBinError.RESTORE_SHORTCUT_TARGET_GONE;
    }
    String targetWorkspace = getString(shortcut, NodeTypeConstants.EXO_WORKSPACE);
    boolean trashWorkspaceTarget = targetWorkspace == null || targetWorkspace.equals(trashWorkspace);
    Session targetSession = trashWorkspaceTarget ? session
                                                 : provider.getSession(targetWorkspace, repositoryService.getCurrentRepository());
    String targetPath;
    try {
      targetPath = ((ExtendedSession) targetSession).getNodeByIdentifier(targetId).getPath();
    } catch (ItemNotFoundException e) {
      return RecycleBinError.RESTORE_SHORTCUT_TARGET_GONE;
    }
    return trashWorkspaceTarget && targetPath.startsWith(trashHome + "/") ? RecycleBinError.RESTORE_SHORTCUT_TARGET_IN_BIN : null;
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
   * Adds the shortcuts to the nodes a folder holds, looked up node by node as
   * the trash of the folder walked them: the shortcuts users deleted to one of
   * them are items of their own bins, which the trash of the folder left where
   * they were, and which would point at nothing once the folder is gone.
   */
  private void collectShortcutsInside(Session session, Node folder, String folderPath, Map<String, Node> found) throws RepositoryException {
    Deque<Node> nodes = new ArrayDeque<>();
    for (NodeIterator children = folder.getNodes(); children.hasNext();) {
      nodes.add(children.nextNode());
    }
    while (!nodes.isEmpty()) {
      Node node = nodes.poll();
      if (node.isNodeType(NodeTypeConstants.EXO_SYMLINK)) {
        continue;
      }
      collect(session, linksTo(node), folderPath, found);
      if (JCRDocumentsUtil.isFolder(node)) {
        for (NodeIterator children = node.getNodes(); children.hasNext();) {
          nodes.add(children.nextNode());
        }
      }
    }
  }

  /** The statement that finds the shortcuts to a node of the trash. */
  private String linksTo(Node node) throws RepositoryException {
    return "SELECT * FROM " + NodeTypeConstants.EXO_SYMLINK + " WHERE " + NodeTypeConstants.EXO_SYMLINK_UUID + "='"
        + ((ExtendedNode) node).getIdentifier() + "' AND " + NodeTypeConstants.EXO_WORKSPACE + "='" + trashWorkspace.replace("'", "''")
        + "'";
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

  /**
   * Whether a node of the type named is listed as a folder, as the Documents
   * app lists the target of a shortcut: a folder, or a type extending one. A
   * type the repository no longer registers, under a namespace it still knows,
   * is no folder.
   */
  private static boolean isFolderType(Session session, String typeName) throws RepositoryException {
    try {
      NodeType type = session.getWorkspace().getNodeTypeManager().getNodeType(typeName);
      return type.isNodeType(NodeTypeConstants.NT_FOLDER) || type.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED);
    } catch (NoSuchNodeTypeException e) {
      return false;
    }
  }

  /**
   * Gives a shortcut the type of what it points to, for its icon: as the
   * shortcut recorded it when it was created; read on the node it points to
   * when it recorded neither a folder's type nor a mime type, as a duplicated
   * or pasted shortcut, which records its own type, or one created out of the
   * Documents app; kept as recorded when that node is gone or in another
   * workspace.
   */
  private static void readTargetType(Node shortcut, RecycleBinItem item) throws RepositoryException {
    Session session = shortcut.getSession();
    String targetType = getString(shortcut, NodeTypeConstants.EXO_PRIMARY_TYPE);
    boolean targetFolder = targetType != null && isFolderType(session, targetType);
    String mimeType = getString(shortcut, NodeTypeConstants.EXO_FILE_TYPE);
    String targetWorkspace = getString(shortcut, NodeTypeConstants.EXO_WORKSPACE);
    if (!targetFolder && StringUtils.isBlank(mimeType)
        && (targetWorkspace == null || targetWorkspace.equals(session.getWorkspace().getName()))) {
      try {
        Node target = ((ExtendedSession) session).getNodeByIdentifier(getString(shortcut, NodeTypeConstants.EXO_SYMLINK_UUID));
        targetFolder = target.isNodeType(NodeTypeConstants.NT_FOLDER) || target.isNodeType(NodeTypeConstants.NT_UNSTRUCTURED);
        if (!targetFolder && target.hasNode(NodeTypeConstants.JCR_CONTENT)) {
          mimeType = getString(target.getNode(NodeTypeConstants.JCR_CONTENT), NodeTypeConstants.JCR_MIME_TYPE);
        }
      } catch (ItemNotFoundException e) {
        // Gone: the recorded values stay.
      }
    }
    item.setTargetFolder(targetFolder);
    item.setMimeType(StringUtils.isBlank(mimeType) ? null : mimeType);
  }

  private static RecycleBinItem toItem(Node node) throws RepositoryException {
    RecycleBinItem item = new RecycleBinItem();
    item.setId(getString(node, NodeTypeConstants.TRASH_ID));
    item.setDocumentId(((ExtendedNode) node).getIdentifier());
    item.setPath(node.getPath());
    String ownerId = getString(node, NodeTypeConstants.RECYCLE_BIN_OWNER);
    item.setOwnerId(StringUtils.isNumeric(ownerId) ? Long.valueOf(ownerId) : null);
    item.setName(JCRDocumentsUtil.getTitle(node));
    item.setShortcut(node.isNodeType(NodeTypeConstants.EXO_SYMLINK));
    item.setFolder(!item.isShortcut() && JCRDocumentsUtil.isFolder(node));
    if (item.isShortcut()) {
      readTargetType(node, item);
    } else if (node.hasNode(NodeTypeConstants.JCR_CONTENT)) {
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
