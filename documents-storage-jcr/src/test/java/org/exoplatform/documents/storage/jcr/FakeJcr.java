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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.mockito.invocation.InvocationOnMock;

import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.WorkspaceImpl;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.exoplatform.services.jcr.impl.ext.action.SessionActionInterceptor;

/**
 * A workspace kept in memory for the storage tests: a tree of nodes with their
 * primary type, mixins, properties and permission entries, read and written
 * through mocked sessions and nodes whose answers come from that tree. A
 * session's save keeps the tree as the saved state and its refresh(false)
 * brings the tree back to it, so a test can tell what a failed save leaves
 * behind. It knows no node type rule, no access check and no query grammar: a
 * statement is answered by the function the test registers, and the statements
 * run are kept for the test to parse with the repository's own parser.
 */
public class FakeJcr {

  /** A node of the tree. */
  public static class Item {
    String              id;

    String              name;

    String              primaryType;

    Set<String>         mixins      = new LinkedHashSet<>();

    Map<String, Object> properties  = new LinkedHashMap<>();

    List<String[]>      permissions = new ArrayList<>();

    String              owner       = "__system";

    List<Item>          children    = new ArrayList<>();

    Item                parent;

    public String getId() {
      return id;
    }

    public Object get(String property) {
      return properties.get(property);
    }

    public boolean has(String type) {
      return type.equals(primaryType) || mixins.contains(type);
    }

    public Item mixin(String... names) {
      for (String mixin : names) {
        mixins.add(mixin);
      }
      return this;
    }

    public Item set(String property, Object value) {
      properties.put(property, value);
      return this;
    }

    public Item permission(String identity, String permission) {
      permissions.add(new String[] { identity, permission });
      return this;
    }

    public List<Item> getChildren() {
      return children;
    }

    public Set<String> getMixins() {
      return mixins;
    }

    public Map<String, Object> getProperties() {
      return properties;
    }

    public List<String[]> getPermissions() {
      return permissions;
    }
  }

  private static final Pattern                        SEGMENT          = Pattern.compile("^(.*?)(?:\\[(\\d+)\\])?$");

  private static final Pattern                        LINK_TARGET      = Pattern.compile("exo:uuid='([^']*)'");

  private final String                                workspaceName;

  private Item                                        root;

  private Item                                        savedRoot;

  private int                                         nextId           = 1;

  private int                                         saves;

  private int                                         refreshes;

  private RepositoryException                         saveFailure;

  private final List<String>                          statements       = new ArrayList<>();

  private final List<String>                          moves            = new ArrayList<>();

  private Function<String, List<Item>>                queryHandler     = this::findLinks;

  private java.util.function.Consumer<Item>           addNodeAction    = added -> {
                                                                       };

  private final Map<Session, Map<String, Node>>       nodes            = new HashMap<>();

  public FakeJcr(String workspaceName) {
    this.workspaceName = workspaceName;
    this.root = new Item();
    this.root.id = "root";
    this.root.name = "";
    this.root.primaryType = "nt:unstructured";
    this.savedRoot = copy(root, null);
  }

  /**
   * Adds a node to the saved state, creating the missing ancestors as
   * nt:unstructured nodes.
   */
  public Item add(String path, String primaryType) {
    Item parent = root;
    String[] segments = path.substring(1).split("/");
    for (int i = 0; i < segments.length; i++) {
      boolean last = i == segments.length - 1;
      Item child = last ? null : child(parent, segments[i]);
      if (child == null) {
        child = new Item();
        child.id = "id" + nextId++;
        child.name = segments[i];
        child.primaryType = last ? primaryType : "nt:unstructured";
        child.parent = parent;
        parent.children.add(child);
      }
      parent = child;
    }
    savedRoot = copy(root, null);
    return parent;
  }

  /** Adds an nt:file node whose content holds the given number of bytes. */
  public Item addFile(String path, long length) {
    Item file = add(path, "nt:file");
    Item content = add(path + "/jcr:content", "nt:resource");
    content.set("jcr:data", length);
    savedRoot = copy(root, null);
    return find(file.id);
  }

  /** Keeps the tree as it stands as the saved state, after a direct change. */
  public void commit() {
    savedRoot = copy(root, null);
  }

  /** The node at a path in the current state, null when there is none. */
  public Item at(String path) {
    if ("/".equals(path)) {
      return root;
    }
    Item item = root;
    for (String segment : path.substring(1).split("/")) {
      item = child(item, segment);
      if (item == null) {
        return null;
      }
    }
    return item;
  }

  /** The node of an id in the current state, null when there is none. */
  public Item find(String id) {
    return find(root, id);
  }

  public String pathOf(Item item) {
    if (item.parent == null) {
      return "";
    }
    int index = 1;
    int sameName = 0;
    for (Item sibling : item.parent.children) {
      if (sibling.name.equals(item.name)) {
        sameName++;
        if (sibling == item) {
          index = sameName;
        }
      }
    }
    String parentPath = pathOf(item.parent);
    return parentPath + "/" + item.name + (index > 1 ? "[" + index + "]" : "");
  }

  public int getSaves() {
    return saves;
  }

  public int getRefreshes() {
    return refreshes;
  }

  public List<String> getStatements() {
    return statements;
  }

  public List<String> getMoves() {
    return moves;
  }

  public void failNextSave(RepositoryException failure) {
    this.saveFailure = failure;
  }

  public void setQueryHandler(Function<String, List<Item>> queryHandler) {
    this.queryHandler = queryHandler;
  }

  /** What the repository's own actions do to a node a session adds. */
  public void setAddNodeAction(java.util.function.Consumer<Item> addNodeAction) {
    this.addNodeAction = addNodeAction;
  }

  /** A mocked session on this workspace, acting as the given user. */
  public SessionImpl session(String userId) {
    SessionImpl session = mock(SessionImpl.class, invocation -> answerSession(invocation, userId));
    nodes.put(session, new HashMap<>());
    return session;
  }

  /** The mocked node of an item, read through a session. */
  public Node node(Session session, Item item) {
    return node(session, item.id);
  }

  private Node node(Session session, String id) {
    return nodes.computeIfAbsent(session, key -> new HashMap<>())
                .computeIfAbsent(id, key -> mock(NodeImpl.class, invocation -> answerNode(invocation, session, id)));
  }

  private Object answerSession(InvocationOnMock invocation, String userId) throws RepositoryException {
    Session session = (Session) invocation.getMock();
    switch (invocation.getMethod().getName()) {
    case "getUserID":
      return userId;
    case "getItem": {
      Item item = at(invocation.getArgument(0));
      if (item == null) {
        throw new PathNotFoundException((String) invocation.getArgument(0));
      }
      return node(session, item);
    }
    case "itemExists":
      return at(invocation.getArgument(0)) != null;
    case "getRootNode":
      return node(session, root);
    case "getNodeByUUID":
    case "getNodeByIdentifier": {
      Item item = find(invocation.getArgument(0));
      if (item == null) {
        throw new ItemNotFoundException((String) invocation.getArgument(0));
      }
      return node(session, item);
    }
    case "move":
      move(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    case "save":
      save();
      return null;
    case "refresh":
      refreshes++;
      if (!Boolean.TRUE.equals(invocation.getArgument(0))) {
        root = copy(savedRoot, null);
      }
      return null;
    case "getWorkspace":
      return workspace(session);
    case "getActionHandler":
      return actionHandlers.computeIfAbsent(session, key -> mock(SessionActionInterceptor.class));
    case "hashCode":
      return System.identityHashCode(session);
    case "equals":
      return session == invocation.getArgument(0);
    case "toString":
      return "FakeJcr session of " + userId;
    default:
      return null;
    }
  }

  private final Map<Session, SessionActionInterceptor> actionHandlers = new HashMap<>();

  private final Map<Session, Workspace>                workspaces     = new HashMap<>();

  private Workspace workspace(Session session) {
    return workspaces.computeIfAbsent(session, key -> {
      try {
        WorkspaceImpl workspace = mock(WorkspaceImpl.class);
        QueryManager queryManager = mock(QueryManager.class);
        lenient().when(workspace.getName()).thenReturn(workspaceName);
        lenient().when(workspace.getQueryManager()).thenReturn(queryManager);
        lenient().when(queryManager.createQuery(anyString(), anyString())).thenAnswer(invocation -> query(session, invocation.getArgument(0)));
        return workspace;
      } catch (RepositoryException e) {
        throw new IllegalStateException(e);
      }
    });
  }

  private Query query(Session session, String statement) throws RepositoryException {
    statements.add(statement);
    QueryImpl query = mock(QueryImpl.class);
    QueryResult result = mock(QueryResult.class);
    long[] page = new long[] { 0, -1 };
    lenient().doAnswer(invocation -> {
      page[0] = invocation.getArgument(0);
      return null;
    }).when(query).setOffset(org.mockito.ArgumentMatchers.anyLong());
    lenient().doAnswer(invocation -> {
      page[1] = invocation.getArgument(0);
      return null;
    }).when(query).setLimit(org.mockito.ArgumentMatchers.anyLong());
    lenient().when(query.execute()).thenReturn(result);
    lenient().when(result.getNodes()).thenAnswer(invocation -> {
      List<Item> items = queryHandler.apply(statement);
      int from = (int) Math.min(page[0], items.size());
      int to = page[1] < 0 ? items.size() : (int) Math.min(from + page[1], items.size());
      return iterator(session, items.subList(from, to));
    });
    return query;
  }

  private List<Item> findLinks(String statement) {
    List<Item> found = new ArrayList<>();
    Matcher matcher = LINK_TARGET.matcher(statement);
    if (matcher.find()) {
      collectLinks(root, matcher.group(1), found);
    }
    return found;
  }

  private void collectLinks(Item item, String targetId, List<Item> found) {
    if (item.has("exo:symlink") && targetId.equals(item.get("exo:uuid"))) {
      found.add(item);
    }
    for (Item child : item.children) {
      collectLinks(child, targetId, found);
    }
  }

  private void move(String sourcePath, String destinationPath) throws RepositoryException {
    moves.add(sourcePath + " -> " + destinationPath);
    Item source = at(sourcePath);
    if (source == null) {
      throw new PathNotFoundException(sourcePath);
    }
    int slash = destinationPath.lastIndexOf('/');
    Item destinationParent = slash == 0 ? root : at(destinationPath.substring(0, slash));
    if (destinationParent == null) {
      throw new PathNotFoundException(destinationPath);
    }
    source.parent.children.remove(source);
    source.parent = destinationParent;
    source.name = destinationPath.substring(slash + 1);
    destinationParent.children.add(source);
  }

  private void save() throws RepositoryException {
    if (saveFailure != null) {
      RepositoryException failure = saveFailure;
      saveFailure = null;
      throw failure;
    }
    saves++;
    savedRoot = copy(root, null);
  }

  private Object answerNode(InvocationOnMock invocation, Session session, String id) throws RepositoryException {
    String method = invocation.getMethod().getName();
    if ("hashCode".equals(method)) {
      return System.identityHashCode(invocation.getMock());
    }
    if ("equals".equals(method)) {
      return invocation.getMock() == invocation.getArgument(0);
    }
    if ("toString".equals(method)) {
      return "FakeJcr node " + id;
    }
    if ("getSession".equals(method)) {
      return session;
    }
    Item item = find(id);
    if (item == null) {
      throw new InvalidItemStateException("Node " + id + " no longer exists");
    }
    switch (method) {
    case "getPath":
      return item.parent == null ? "/" : pathOf(item);
    case "getName":
      return item.name;
    case "getIndex": {
      String path = pathOf(item);
      Matcher matcher = SEGMENT.matcher(path.substring(path.lastIndexOf('/') + 1));
      return matcher.matches() && matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
    }
    case "getUUID":
    case "getIdentifier":
      return item.id;
    case "isRoot":
      return item.parent == null;
    case "isNodeType":
      return item.has(invocation.getArgument(0));
    case "getPrimaryNodeType": {
      NodeType nodeType = mock(NodeType.class);
      lenient().when(nodeType.getName()).thenReturn(item.primaryType);
      return nodeType;
    }
    case "canAddMixin":
      return !item.mixins.contains(invocation.getArgument(0));
    case "addMixin":
      item.mixins.add(invocation.getArgument(0));
      return null;
    case "removeMixin":
      removeMixin(item, invocation.getArgument(0));
      return null;
    case "hasProperty":
      return item.properties.containsKey(invocation.getArgument(0));
    case "getProperty":
      return property(item.id, invocation.getArgument(0));
    case "setProperty":
      item.properties.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    case "getParent":
      if (item.parent == null) {
        throw new ItemNotFoundException("The root has no parent");
      }
      return node(session, item.parent);
    case "hasNode":
      return child(item, invocation.getArgument(0)) != null;
    case "getNode": {
      Item child = child(item, invocation.getArgument(0));
      if (child == null) {
        throw new PathNotFoundException((String) invocation.getArgument(0));
      }
      return node(session, child);
    }
    case "getNodes": {
      List<Item> children = new ArrayList<>();
      for (Item child : item.children) {
        if (invocation.getArguments().length == 0 || child.name.equals(invocation.getArgument(0))) {
          children.add(child);
        }
      }
      return iterator(session, children);
    }
    case "addNode": {
      Item child = new Item();
      child.id = "id" + nextId++;
      child.name = invocation.getArgument(0);
      child.primaryType = invocation.getArguments().length > 1 ? invocation.getArgument(1) : "nt:unstructured";
      child.parent = item;
      item.children.add(child);
      addNodeAction.accept(child);
      return node(session, child);
    }
    case "remove":
      item.parent.children.remove(item);
      return null;
    case "getACL": {
      List<AccessControlEntry> entries = new ArrayList<>();
      for (Item holder = item; holder != null; holder = holder.parent) {
        if (holder == item && !holder.permissions.isEmpty() || holder.has("exo:privilegeable")) {
          for (String[] permission : holder.permissions) {
            entries.add(new AccessControlEntry(permission[0], permission[1]));
          }
          break;
        }
      }
      return new AccessControlList(item.owner, entries);
    }
    case "setPermissions": {
      Map<String, String[]> permissions = invocation.getArgument(0);
      item.permissions.clear();
      permissions.forEach((identity, types) -> {
        for (String type : types) {
          item.permissions.add(new String[] { identity, type });
        }
      });
      return null;
    }
    default:
      return null;
    }
  }

  private void removeMixin(Item item, String mixin) {
    item.mixins.remove(mixin);
    if ("exo:restoreLocation".equals(mixin)) {
      item.properties.keySet().removeIf(name -> name.equals("exo:restorePath") || name.equals("exo:restoreWorkspace") || name.equals("exo:trashId"));
    } else if ("exo:recycleBinItem".equals(mixin)) {
      item.properties.keySet().removeIf(name -> name.startsWith("exo:recycleBin") || name.equals("exo:deletedBy")
          || name.equals("exo:deletedDate") || name.equals("exo:restoreParentId"));
    }
  }

  private Property property(String id, String name) throws RepositoryException {
    Item item = find(id);
    if (!item.properties.containsKey(name)) {
      throw new PathNotFoundException(name);
    }
    Object value = item.properties.get(name);
    Property property = mock(Property.class);
    lenient().when(property.getString()).thenReturn(String.valueOf(value));
    if (value instanceof Long length) {
      lenient().when(property.getLong()).thenReturn(length);
      lenient().when(property.getLength()).thenReturn(length);
    }
    if (value instanceof Calendar date) {
      lenient().when(property.getDate()).thenReturn(date);
    }
    lenient().doAnswer(invocation -> {
      find(id).properties.remove(name);
      return null;
    }).when(property).remove();
    return property;
  }

  private NodeIterator iterator(Session session, List<Item> items) {
    List<Item> listed = new ArrayList<>(items);
    Iterator<Item> iterator = listed.iterator();
    return new NodeIterator() {
      private long position;

      @Override
      public Node nextNode() {
        position++;
        return node(session, iterator.next());
      }

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public Object next() {
        return nextNode();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void skip(long skipNum) {
        for (long i = 0; i < skipNum; i++) {
          nextNode();
        }
      }

      @Override
      public long getSize() {
        return listed.size();
      }

      @Override
      public long getPosition() {
        return position;
      }
    };
  }

  private Item child(Item parent, String segment) {
    Matcher matcher = SEGMENT.matcher(segment);
    if (!matcher.matches()) {
      return null;
    }
    String name = matcher.group(1);
    int index = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
    int sameName = 0;
    for (Item child : parent.children) {
      if (child.name.equals(name) && ++sameName == index) {
        return child;
      }
    }
    return null;
  }

  private Item find(Item item, String id) {
    if (item.id.equals(id)) {
      return item;
    }
    for (Item child : item.children) {
      Item found = find(child, id);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private Item copy(Item item, Item parent) {
    Item copy = new Item();
    copy.id = item.id;
    copy.name = item.name;
    copy.primaryType = item.primaryType;
    copy.owner = item.owner;
    copy.mixins.addAll(item.mixins);
    copy.properties.putAll(item.properties);
    for (String[] permission : item.permissions) {
      copy.permissions.add(permission.clone());
    }
    copy.parent = parent;
    for (Item child : item.children) {
      copy.children.add(copy(child, copy));
    }
    return copy;
  }

}
