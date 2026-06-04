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
import javax.jcr.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.exoplatform.documents.storage.jcr.util.JCRDocumentsUtil.getGroupNode;

public class RestrictContentCreationSpaceListener extends SpaceListenerPlugin {
  private static final Log       LOG = ExoLogger.getExoLogger(RestrictContentCreationSpaceListener.class);

  private RepositoryService      repositoryService;

  private NodeHierarchyCreator   nodeHierarchyCreator;

  private SessionProviderService sessionProviderService;

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
      Map<String, String[]> permissions = new HashMap<>();
      if (spaceRootNode != null) {
        for (AccessControlEntry accessEntry : ((ExtendedNode) spaceRootNode).getACL().getPermissionEntries()) {
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
        } else {
          permissions.put("*:" + space.getGroupId(), PermissionType.ALL);
        }
        ((ExtendedNode) spaceRootNode).setPermissions(permissions);
        session.save();
      }
    } catch (Exception e) {
      LOG.error("Error updating permissions of the root Node of the space {}", space.getPrettyName(), e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }
}
