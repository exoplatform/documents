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
import org.exoplatform.social.core.space.spi.SpaceService;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getGroupNode;

public class RestrictContentCreationSpaceListener extends SpaceListenerPlugin {

  private static final Log             LOG = ExoLogger.getExoLogger(RestrictContentCreationSpaceListener.class);

  private final SpaceService           spaceService;

  private final RepositoryService      repositoryService;

  private final NodeHierarchyCreator   nodeHierarchyCreator;

  private final SessionProviderService sessionProviderService;

  public RestrictContentCreationSpaceListener(SpaceService spaceService,
                                              RepositoryService repositoryService,
                                              NodeHierarchyCreator nodeHierarchyCreator,
                                              SessionProviderService sessionProviderService) {
    this.spaceService = spaceService;
    this.repositoryService = repositoryService;
    this.nodeHierarchyCreator = nodeHierarchyCreator;
    this.sessionProviderService = sessionProviderService;
  }

  @Override
  public void spaceCreated(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    synchronizeSpacePermissions(space, isRedactionalSpace(space));
  }

  @Override
  public void addRedactorUser(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    if (isRedactionalSpace(space)) {
      synchronizeSpacePermissions(space, true);
    }
  }

  @Override
  public void removeRedactorUser(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    if (!isRedactionalSpace(space)) {
      synchronizeSpacePermissions(space, false);
    }
  }

  private void synchronizeSpacePermissions(Space space, boolean targetRedactionalMode) {
    if (space == null) {
      return;
    }
    SessionProvider sessionProvider = null;
    try {
      ManageableRepository repository = repositoryService.getCurrentRepository();
      sessionProvider = sessionProviderService.getSystemSessionProvider(null);

      Session session = sessionProvider.getSession(repository.getConfiguration().getDefaultWorkspaceName(), repository);
      Node spaceRootNode = getGroupNode(nodeHierarchyCreator, session, space.getGroupId());
      if (spaceRootNode != null) {
        synchronizeNodePermissions(space, targetRedactionalMode, spaceRootNode);
        synchronizeChildrenPermissions(space, targetRedactionalMode, spaceRootNode);
      }
    } catch (OperationCancelledException e) {
      LOG.info("Permission update cancelled for space width id '{}'", space.getId());
    } catch (Exception e) {
      LOG.error("Error updating permissions of space width id '{}'", space.getId(), e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }

  private void synchronizeChildrenPermissions(Space space,
                                              boolean targetRedactionalMode,
                                              Node parentNode) throws RepositoryException,
          OperationCancelledException {
    NodeIterator children = parentNode.getNodes();
    while (children.hasNext()) {
      Node child = children.nextNode();
      try {
        synchronizeNodePermissions(space, targetRedactionalMode, child);
        synchronizeChildrenPermissions(space, targetRedactionalMode, child);
      } catch (OperationCancelledException e) {
        throw e;
      } catch (Exception e) {
        LOG.warn("Error applying permissions to a child node in space width id '{}', continuing", space.getId(), e);
      }
    }
  }

  private void synchronizeNodePermissions(Space space,
                                          boolean targetRedactionalMode,
                                          Node node) throws RepositoryException,
          OperationCancelledException {
    assertRedactionalModeStillExpected(space, targetRedactionalMode);
    if (shouldRewriteSpacePermissions(node, space.getGroupId(), targetRedactionalMode)) {
      Map<String, String[]> permissions = buildPermissionsForTargetMode(node, space.getGroupId(), targetRedactionalMode);
      ((ExtendedNode) node).setPermissions(permissions);
      node.save();
    }
  }

  private void assertRedactionalModeStillExpected(Space space,
                                                  boolean expectedRedactionalMode) throws OperationCancelledException {
    Space currentSpace = spaceService.getSpaceById(space.getSpaceId());
    if (isRedactionalSpace(currentSpace) != expectedRedactionalMode) {
      throw new OperationCancelledException();
    }
  }

  /**
   * Rewrite only nodes whose space ACL still has the exact previous default
   * shape. targetRedactionalMode = true: previous expected ACL is the standard
   * non-redactional ACL. targetRedactionalMode = false: previous expected ACL
   * is the standard redactional ACL. Any additional, missing, or different
   * space-related permission is considered a customization, so the node is
   * skipped.
   *
   * @param node JCR Node
   * @param groupId Space Group Id
   * @param targetRedactionalMode true if space has become redactional else
   *          false
   * @return whether should rewrite permissions on node or not
   * @throws RepositoryException when a JCR exception is thrown
   */
  private boolean shouldRewriteSpacePermissions(Node node,
                                                String groupId,
                                                boolean targetRedactionalMode) throws RepositoryException {
    if (!node.isNodeType("exo:privilegeable")) {
      return false;
    }
    Map<String, Set<String>> currentSpacePermissions = getSpacePermissions(node, groupId);
    Map<String, Set<String>> expectedPreviousPermissions = targetRedactionalMode ? buildStandardOpenSpacePermissions(groupId) :
            buildStandardRedactionalSpacePermissions(groupId);

    return currentSpacePermissions.equals(expectedPreviousPermissions);
  }

  private Map<String, String[]> buildPermissionsForTargetMode(Node node,
                                                              String groupId,
                                                              boolean targetRedactionalMode) throws RepositoryException {
    Map<String, Set<String>> permissions = getNonSpacePermissions(node, groupId);
    Map<String, Set<String>> targetSpacePermissions = targetRedactionalMode ? buildStandardRedactionalSpacePermissions(groupId) :
            buildStandardOpenSpacePermissions(groupId);

    permissions.putAll(targetSpacePermissions);
    return toPermissionArrayMap(permissions);
  }

  private Map<String, Set<String>> getNonSpacePermissions(Node node, String groupId) throws RepositoryException {
    Map<String, Set<String>> permissions = new LinkedHashMap<>();
    for (AccessControlEntry entry : ((ExtendedNode) node).getACL().getPermissionEntries()) {
      if (!isSpacePermission(entry.getIdentity(), groupId)) {
        addPermission(permissions, entry.getIdentity(), entry.getPermission());
      }
    }
    return permissions;
  }

  private Map<String, Set<String>> getSpacePermissions(Node node, String groupId) throws RepositoryException {
    Map<String, Set<String>> permissions = new LinkedHashMap<>();
    for (AccessControlEntry entry : ((ExtendedNode) node).getACL().getPermissionEntries()) {
      if (isSpacePermission(entry.getIdentity(), groupId)) {
        addPermission(permissions, entry.getIdentity(), entry.getPermission());
      }
    }
    return permissions;
  }

  private boolean isSpacePermission(String identity, String groupId) {
    return identity.endsWith(":" + groupId);
  }

  private Map<String, Set<String>> buildStandardOpenSpacePermissions(String groupId) {
    Map<String, Set<String>> permissions = new LinkedHashMap<>();
    permissions.put("*:" + groupId, toPermissionSet(PermissionType.ALL));
    return permissions;
  }

  private Map<String, Set<String>> buildStandardRedactionalSpacePermissions(String groupId) {
    Map<String, Set<String>> permissions = new LinkedHashMap<>();
    permissions.put("*:" + groupId, toPermissionSet(PermissionType.READ));
    permissions.put("manager:" + groupId, toPermissionSet(PermissionType.ALL));
    permissions.put("redactor:" + groupId, toPermissionSet(PermissionType.ALL));
    permissions.put("publisher:" + groupId, toPermissionSet(PermissionType.ALL));
    return permissions;
  }

  private void addPermission(Map<String, Set<String>> permissions, String identity, String permission) {
    permissions.computeIfAbsent(identity, key -> new LinkedHashSet<>()).add(permission);
  }

  private Set<String> toPermissionSet(String permission) {
    Set<String> permissions = new LinkedHashSet<>();
    permissions.add(permission);
    return permissions;
  }

  private Set<String> toPermissionSet(String[] permissions) {
    return new LinkedHashSet<>(Arrays.asList(permissions));
  }

  private Map<String, String[]> toPermissionArrayMap(Map<String, Set<String>> permissions) {
    Map<String, String[]> result = new LinkedHashMap<>();

    for (Map.Entry<String, Set<String>> entry : permissions.entrySet()) {
      List<String> permissionList = new ArrayList<>(entry.getValue());
      result.put(entry.getKey(), permissionList.toArray(new String[0]));
    }

    return result;
  }

  private boolean isRedactionalSpace(Space space) {
    return space != null && spaceService.hasRedactor(space);
  }
}
