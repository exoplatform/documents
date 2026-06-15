package org.exoplatform.documents.storage.jcr.listener;

import org.exoplatform.documents.webdav.model.OperationCancelledException;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.SpaceListenerPlugin;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getGroupNode;

public class RestrictContentCreationSpaceListener extends SpaceListenerPlugin {

  private static final Log          LOG               = ExoLogger.getExoLogger(RestrictContentCreationSpaceListener.class);

  private RepositoryService         repositoryService;

  private NodeHierarchyCreator      nodeHierarchyCreator;

  private SessionProviderService    sessionProviderService;

  private final Map<String, String> pendingOperations = new ConcurrentHashMap<>();

  public RestrictContentCreationSpaceListener(RepositoryService repositoryService,
                                              NodeHierarchyCreator nodeHierarchyCreator,
                                              SessionProviderService sessionProviderService) {
    this.repositoryService = repositoryService;
    this.nodeHierarchyCreator = nodeHierarchyCreator;
    this.sessionProviderService = sessionProviderService;
  }

  @Override
  public void spaceCreated(SpaceLifeCycleEvent event) {
    applyRestrictPermissions(event);
  }

  @Override
  public void addRedactorUser(SpaceLifeCycleEvent event) {
    applyRestrictPermissions(event);
  }

  @Override
  public void removeRedactorUser(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    if (space.getRedactors() == null || space.getRedactors().length == 0) {
      changePermissionsForSpaceMembers(space, false);
    }
  }

  private void applyRestrictPermissions(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    if (space.getRedactors() != null && space.getRedactors().length == 1) {
      changePermissionsForSpaceMembers(space, true);
    }
  }

  private void changePermissionsForSpaceMembers(Space space, boolean readOnlyForMembers) {
    SessionProvider sessionProvider = null;
    try {
      ManageableRepository repository = repositoryService.getCurrentRepository();
      sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      Session session = sessionProvider.getSession("collaboration", repository);
      Node spaceRootNode = getGroupNode(nodeHierarchyCreator, session, space.getGroupId());
      if (spaceRootNode != null) {
        boolean spaceIsOpen = isSpaceOpenToAll(spaceRootNode, space.getGroupId());
        if (readOnlyForMembers && !spaceIsOpen) {
          return;
        }
        if (!readOnlyForMembers && spaceIsOpen) {
          return;
        }

        String operationId = UUID.randomUUID().toString();
        pendingOperations.put(space.getId(), operationId);

        try {
          if (isStandardModel(spaceRootNode, space.getGroupId())) {
            Map<String, String[]> permissions = buildPermissions(spaceRootNode, space, readOnlyForMembers);
            ((ExtendedNode) spaceRootNode).setPermissions(permissions);
          }
          applyPermissionsRecursively(spaceRootNode, space, readOnlyForMembers, operationId);
          session.save();
        } catch (OperationCancelledException e) {
          LOG.info("Permission restriction for space {} was cancelled, discarding changes", space.getPrettyName());
          session.refresh(false);
        } finally {
          pendingOperations.remove(space.getId(), operationId);
        }
      }
    } catch (Exception e) {
      LOG.error("Error updating permissions of the root Node of the space {}", space.getPrettyName(), e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  private void applyPermissionsRecursively(Node parentNode,
                                           Space space,
                                           boolean readOnlyForMembers,
                                           String operationId) throws OperationCancelledException, RepositoryException {
    NodeIterator children = parentNode.getNodes();
    while (children.hasNext()) {
      String currentOpId = pendingOperations.get(space.getId());
      if (operationId != null && !operationId.equals(currentOpId)) {
        throw new OperationCancelledException();
      }
      Node child = children.nextNode();
      try {
        if (isStandardModel(child, space.getGroupId()) && !hasTargetPermissions(child, space.getGroupId(), readOnlyForMembers)) {
          Map<String, String[]> permissions = buildPermissions(child, space, readOnlyForMembers);
          ((ExtendedNode) child).setPermissions(permissions);
        }
      } catch (Exception e) {
        LOG.warn("Error applying permissions to a child node in space {}, continuing", space.getPrettyName(), e);
      }
      applyPermissionsRecursively(child, space, readOnlyForMembers, operationId);
    }
  }

  private boolean isStandardModel(Node node, String groupId) throws RepositoryException {
    if (!node.isNodeType("exo:privilegeable")) {
      return true;
    }
    return hasTargetPermissions(node, groupId, true) || hasTargetPermissions(node, groupId, false);
  }

  private boolean hasTargetPermissions(Node node, String groupId, boolean readOnlyForMembers) throws RepositoryException {
    if (readOnlyForMembers) {
      return !isSpaceOpenToAll(node, groupId) && hasPermission(node, "manager:" + groupId)
          && hasPermission(node, "redactor:" + groupId) && hasPermission(node, "publisher:" + groupId);
    } else {
      return isSpaceOpenToAll(node, groupId);
    }
  }

  private boolean hasPermission(Node node, String identity) throws RepositoryException {
    List<String> currentPermissions = new ArrayList<>();
    for (AccessControlEntry entry : ((ExtendedNode) node).getACL().getPermissionEntries()) {
      if (entry.getIdentity().equals(identity)) {
        currentPermissions.add(entry.getPermission());
      }
    }
    List<String> expectedList = Arrays.asList(PermissionType.ALL);
    return currentPermissions.size() == expectedList.size() && new HashSet<>(currentPermissions).containsAll(expectedList);
  }

  private boolean isSpaceOpenToAll(Node node, String groupId) throws RepositoryException {
    for (AccessControlEntry entry : ((ExtendedNode) node).getACL().getPermissionEntries()) {
      if (entry.getIdentity().equals("*:" + groupId)) {
        return !PermissionType.READ.equals(entry.getPermission());
      }
    }
    return false;
  }

  private Map<String, String[]> buildPermissions(Node node, Space space, boolean readOnlyForMembers) throws RepositoryException {
    Map<String, String[]> permissions = new HashMap<>();
    for (AccessControlEntry accessEntry : ((ExtendedNode) node).getACL().getPermissionEntries()) {
      if (!accessEntry.getIdentity().endsWith(":" + space.getGroupId())) {
        if (permissions.get(accessEntry.getIdentity()) == null) {
          permissions.put(accessEntry.getIdentity(), new String[] { accessEntry.getPermission() });
        } else {
          List<String> existingPermissions = new ArrayList<>(List.of(permissions.get(accessEntry.getIdentity())));
          existingPermissions.add(accessEntry.getPermission());
          permissions.put(accessEntry.getIdentity(), existingPermissions.toArray(new String[0]));
        }
      }
    }
    if (readOnlyForMembers) {
      permissions.put("*:" + space.getGroupId(), new String[] { PermissionType.READ });
      permissions.put("manager:" + space.getGroupId(), PermissionType.ALL);
      permissions.put("redactor:" + space.getGroupId(), PermissionType.ALL);
      permissions.put("publisher:" + space.getGroupId(), PermissionType.ALL);
    } else {
      permissions.put("*:" + space.getGroupId(), PermissionType.ALL);
    }
    return permissions;
  }
}
