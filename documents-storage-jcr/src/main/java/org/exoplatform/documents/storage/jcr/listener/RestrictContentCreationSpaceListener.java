package org.exoplatform.documents.storage.jcr.listener;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getGroupNode;

public class RestrictContentCreationSpaceListener extends SpaceListenerPlugin {
  private static final Log                 LOG               = ExoLogger.getExoLogger(RestrictContentCreationSpaceListener.class);

  private RepositoryService                repositoryService;

  private NodeHierarchyCreator             nodeHierarchyCreator;

  private SessionProviderService           sessionProviderService;

  private final Map<String, AtomicBoolean> pendingOperations = new ConcurrentHashMap<>();

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
    AtomicBoolean cancelFlag = pendingOperations.get(space.getId());
    if (cancelFlag != null) {
      LOG.info("Cancelling in-progress permission restriction for space {}", space.getPrettyName());
      cancelFlag.set(true);
    }
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
          LOG.info("Space {} is already restricted, skipping permission update", space.getPrettyName());
          return;
        }
        if (!readOnlyForMembers && spaceIsOpen) {
          LOG.info("Space {} is already open, skipping permission update", space.getPrettyName());
          return;
        }

        AtomicBoolean cancelFlag = null;
        if (readOnlyForMembers) {
          cancelFlag = new AtomicBoolean(false);
          pendingOperations.put(space.getId(), cancelFlag);
        }

        try {
          Map<String, String[]> permissions = buildPermissions(spaceRootNode, space, readOnlyForMembers);
          ((ExtendedNode) spaceRootNode).setPermissions(permissions);
          applyPermissionsRecursively(spaceRootNode, space, readOnlyForMembers, cancelFlag);
          if (cancelFlag != null && cancelFlag.get()) {
            LOG.info("Permission restriction for space {} was cancelled, discarding changes",
                     space.getPrettyName());
            session.refresh(false);
          } else {
            session.save();
          }
        } finally {
          if (cancelFlag != null) {
            pendingOperations.remove(space.getId());
          }
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
                                           AtomicBoolean cancelFlag) {
    try {
      NodeIterator children = parentNode.getNodes();
      while (children.hasNext()) {
        if (cancelFlag != null && cancelFlag.get()) {
          LOG.info("Permission restriction cancelled for space {}, stopping recursion",
                   space.getPrettyName());
          return;
        }
        Node child = children.nextNode();
        try {
          if (child.isNodeType("exo:privilegeable")) {
            Map<String, String[]> permissions = buildPermissions(child, space, readOnlyForMembers);
            ((ExtendedNode) child).setPermissions(permissions);
          }
        } catch (Exception e) {
          LOG.warn("Error applying permissions to a child node in space {}, continuing",
                   space.getPrettyName(), e);
        }
        applyPermissionsRecursively(child, space, readOnlyForMembers, cancelFlag);
      }
    } catch (RepositoryException e) {
      LOG.error("Error iterating children for space {}", space.getPrettyName(), e);
    }
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
