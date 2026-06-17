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
package org.exoplatform.documents.utils;

import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import java.util.*;

public final class DocumentUtils {

  private DocumentUtils() {
  }

  public static boolean shouldRewriteSpacePermissions(Node node,
                                                      String groupId,
                                                      boolean targetRedactionalMode) throws RepositoryException {
    if (!node.isNodeType("exo:privilegeable")) {
      return false;
    }
    Map<String, Set<String>> currentSpacePermissions = getSpacePermissions(node, groupId);
    if (targetRedactionalMode) {
      return currentSpacePermissions.equals(buildStandardOpenSpacePermissions(groupId))
          || currentSpacePermissions.equals(buildOldStandardRedactionalSpacePermissions(groupId));
    }
    return currentSpacePermissions.equals(buildStandardRedactionalSpacePermissions(groupId));
  }

  public static Map<String, String[]> buildPermissionsForTargetMode(Node node,
                                                                    String groupId,
                                                                    boolean targetRedactionalMode) throws RepositoryException {
    Map<String, Set<String>> permissions = getNonSpacePermissions(node, groupId);
    Map<String, Set<String>> targetSpacePermissions = targetRedactionalMode ? buildStandardRedactionalSpacePermissions(groupId)
                                                                            : buildStandardOpenSpacePermissions(groupId);
    permissions.putAll(targetSpacePermissions);
    return toPermissionArrayMap(permissions);
  }

  public static Map<String, Set<String>> getNonSpacePermissions(Node node, String groupId) throws RepositoryException {
    Map<String, Set<String>> permissions = new LinkedHashMap<>();
    for (AccessControlEntry entry : ((ExtendedNode) node).getACL().getPermissionEntries()) {
      if (!isSpacePermission(entry.getIdentity(), groupId)) {
        addPermission(permissions, entry.getIdentity(), entry.getPermission());
      }
    }
    return permissions;
  }

  public static Map<String, Set<String>> getSpacePermissions(Node node, String groupId) throws RepositoryException {
    Map<String, Set<String>> permissions = new LinkedHashMap<>();
    for (AccessControlEntry entry : ((ExtendedNode) node).getACL().getPermissionEntries()) {
      if (isSpacePermission(entry.getIdentity(), groupId)) {
        addPermission(permissions, entry.getIdentity(), entry.getPermission());
      }
    }
    return permissions;
  }

  public static boolean isSpacePermission(String identity, String groupId) {
    return identity.endsWith(":" + groupId);
  }

  public static boolean hasIdentityPermission(Node node, String identity) throws RepositoryException {
    for (AccessControlEntry entry : ((ExtendedNode) node).getACL().getPermissionEntries()) {
      if (entry.getIdentity().equals(identity)) {
        return true;
      }
    }
    return false;
  }

  public static Map<String, Set<String>> buildStandardOpenSpacePermissions(String groupId) {
    Map<String, Set<String>> permissions = new LinkedHashMap<>();
    permissions.put("*:" + groupId, toPermissionSet(PermissionType.ALL));
    return permissions;
  }

  public static Map<String, Set<String>> buildStandardRedactionalSpacePermissions(String groupId) {
    Map<String, Set<String>> permissions = new LinkedHashMap<>();
    permissions.put("*:" + groupId, toPermissionSet(PermissionType.READ));
    permissions.put("manager:" + groupId, toPermissionSet(PermissionType.ALL));
    permissions.put("redactor:" + groupId, toPermissionSet(PermissionType.ALL));
    permissions.put("publisher:" + groupId, toPermissionSet(PermissionType.ALL));
    return permissions;
  }

  public static Map<String, Set<String>> buildOldStandardRedactionalSpacePermissions(String groupId) {
    Map<String, Set<String>> permissions = new LinkedHashMap<>();
    permissions.put("*:" + groupId, toPermissionSet(PermissionType.READ));
    permissions.put("manager:" + groupId, toPermissionSet(PermissionType.ALL));
    permissions.put("redactor:" + groupId, toPermissionSet(PermissionType.ALL));
    return permissions;
  }

  public static void addPermission(Map<String, Set<String>> permissions, String identity, String permission) {
    permissions.computeIfAbsent(identity, key -> new LinkedHashSet<>()).add(permission);
  }

  public static Set<String> toPermissionSet(String permission) {
    Set<String> permissions = new LinkedHashSet<>();
    permissions.add(permission);
    return permissions;
  }

  public static Set<String> toPermissionSet(String[] permissions) {
    return new LinkedHashSet<>(Arrays.asList(permissions));
  }

  public static Map<String, String[]> toPermissionArrayMap(Map<String, Set<String>> permissions) {
    Map<String, String[]> result = new LinkedHashMap<>();
    for (Map.Entry<String, Set<String>> entry : permissions.entrySet()) {
      List<String> permissionList = new ArrayList<>(entry.getValue());
      result.put(entry.getKey(), permissionList.toArray(new String[0]));
    }
    return result;
  }
}
