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
package org.exoplatform.documents.service;

import java.util.List;
import java.util.Objects;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.documents.constant.RecycleBinError;
import org.exoplatform.documents.model.RecycleBinDrive;
import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.model.RecycleBinScope;
import org.exoplatform.documents.storage.RecycleBinDriveStorage;
import org.exoplatform.documents.storage.RecycleBinStorage;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

@Service
public class RecycleBinServiceImpl implements RecycleBinService {

  private static final Log             LOG = ExoLogger.getLogger(RecycleBinServiceImpl.class);

  private final RecycleBinStorage      recycleBinStorage;

  private final RecycleBinDriveStorage recycleBinDriveStorage;

  private final IdentityManager        identityManager;

  private final SpaceService           spaceService;

  private final UserACL                userAcl;

  private final boolean                administratorAccessEnabled;

  public RecycleBinServiceImpl(RecycleBinStorage recycleBinStorage,
                               RecycleBinDriveStorage recycleBinDriveStorage,
                               IdentityManager identityManager,
                               SpaceService spaceService,
                               UserACL userAcl,
                               @Value("${exo.documents.recycleBin.administratorAccess.enabled:true}")
                               boolean administratorAccessEnabled) {
    this.recycleBinStorage = recycleBinStorage;
    this.recycleBinDriveStorage = recycleBinDriveStorage;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.userAcl = userAcl;
    this.administratorAccessEnabled = administratorAccessEnabled;
  }

  @Override
  public List<RecycleBinItem> getItems(RecycleBinScope scope, RecycleBinFilter filter, String username) throws ObjectNotFoundException,
                                                                                                        IllegalAccessException {
    RecycleBinFilter ownerFilter = getOwnerFilter(scope, filter, username);
    try {
      List<RecycleBinItem> items = recycleBinStorage.getItems(ownerFilter);
      if (scope.getType() == RecycleBinScope.Type.DRIVE) {
        // Without the id of a document there is nothing to open it with.
        items.forEach(item -> item.setDocumentId(null));
      }
      return items;
    } catch (RepositoryException e) {
      throw new IllegalStateException("The Recycle Bin of the drive " + ownerFilter.getOwnerId() + " cannot be read", e);
    }
  }

  @Override
  public int countItems(RecycleBinScope scope, RecycleBinFilter filter, String username) throws ObjectNotFoundException,
                                                                                         IllegalAccessException {
    RecycleBinFilter ownerFilter = getOwnerFilter(scope, filter, username);
    try {
      return recycleBinStorage.countItems(ownerFilter);
    } catch (RepositoryException e) {
      throw new IllegalStateException("The Recycle Bin of the drive " + ownerFilter.getOwnerId() + " cannot be read", e);
    }
  }

  @Override
  public void restoreItem(String itemId, RecycleBinScope scope, String username) throws ObjectNotFoundException,
                                                                                 IllegalAccessException {
    RecycleBinItem item = getItemOfScope(itemId, scope, username);
    restore(item, username, scope.getType() == RecycleBinScope.Type.DRIVE);
  }

  @Override
  public void deleteItem(String itemId, RecycleBinScope scope, String username) throws ObjectNotFoundException,
                                                                                IllegalAccessException {
    RecycleBinItem item = getItemOfScope(itemId, scope, username);
    List<RecycleBinItem> removedWithIt;
    try {
      removedWithIt = recycleBinStorage.removeItem(itemId);
    } catch (PathNotFoundException e) {
      throw new ObjectNotFoundException(RecycleBinError.ITEM_NOT_FOUND);
    } catch (RepositoryException e) {
      throw new IllegalStateException("The item " + itemId + " of the Recycle Bin cannot be removed", e);
    }
    countOut(item);
    removedWithIt.forEach(this::countOut);
  }

  @Override
  public RecycleBinItem checkCanView(String itemId, String username) throws ObjectNotFoundException, IllegalAccessException {
    checkCaller(username);
    RecycleBinItem item = getItem(itemId);
    if (!holdsBinOf(item, username)) {
      throw new IllegalAccessException("User " + username + " may not read the item " + itemId + " of the Recycle Bin");
    }
    return item;
  }

  @Override
  public RecycleBinItem checkCanViewDocument(String documentId, String username) throws ObjectNotFoundException,
                                                                                 IllegalAccessException {
    checkCaller(username);
    RecycleBinItem item = getItemOfDocument(documentId);
    if (!holdsBinOf(item, username)) {
      throw new IllegalAccessException("User " + username + " may not read the deleted document " + documentId);
    }
    return item;
  }

  @Override
  public void undoDelete(String documentId, String username) throws ObjectNotFoundException, IllegalAccessException {
    checkCaller(username);
    RecycleBinItem item = getItemOfDocument(documentId);
    if (!username.equals(item.getDeletedBy()) && !holdsBinOf(item, username)) {
      throw new IllegalAccessException("User " + username + " may not restore the deleted document " + documentId);
    }
    restore(item, username, false);
  }

  @Override
  public List<RecycleBinDrive> getDrives(long offset, int limit, String username) throws IllegalAccessException {
    checkPlatformAdministrator(username);
    if (offset < 0 || limit < 0) {
      throw new IllegalArgumentException("The offset and the limit of a page cannot be negative");
    }
    return recycleBinDriveStorage.getDrives(offset, limit);
  }

  @Override
  public long countDrives(String username) throws IllegalAccessException {
    checkPlatformAdministrator(username);
    return recycleBinDriveStorage.countDrives();
  }

  @Override
  public boolean isAdministratorAccessEnabled() {
    return administratorAccessEnabled;
  }

  @Override
  public void itemTrashed(long ownerId, long size) {
    // Three writes, each a transaction of its own: the first item of a drive
    // finds no row and creates it, and when another first item created it
    // meanwhile the creation fails and the item is counted on that row.
    if (recycleBinDriveStorage.add(ownerId, size)) {
      return;
    }
    try {
      recycleBinDriveStorage.create(ownerId, size);
    } catch (RuntimeException e) {
      if (!recycleBinDriveStorage.add(ownerId, size)) {
        throw e;
      }
    }
  }

  private void restore(RecycleBinItem item, String username, boolean administrator) throws ObjectNotFoundException,
                                                                                    IllegalAccessException {
    try {
      recycleBinStorage.restoreItem(item.getId(), userAcl.getUserIdentity(username), administrator);
    } catch (RepositoryException e) {
      throw new IllegalStateException("The item " + item.getId() + " of the Recycle Bin cannot be restored", e);
    }
    countOut(item);
  }

  /**
   * An item that left a bin is counted out of its drive. The item has left
   * whatever happens here: a count that fails is reported and corrected by
   * the daily run.
   */
  private void countOut(RecycleBinItem item) {
    if (item.getOwnerId() == null) {
      return;
    }
    try {
      recycleBinDriveStorage.remove(item.getOwnerId(), item.getSize());
    } catch (RuntimeException e) {
      LOG.warn("The item {} left the Recycle Bin of the drive {} and could not be counted out of it",
               item.getId(),
               item.getOwnerId(),
               e);
    }
  }

  private RecycleBinFilter getOwnerFilter(RecycleBinScope scope, RecycleBinFilter filter, String username) throws ObjectNotFoundException,
                                                                                                           IllegalAccessException {
    long ownerId = getOwnerId(scope, username);
    RecycleBinFilter asked = filter == null ? new RecycleBinFilter() : filter;
    if (asked.getOffset() < 0 || asked.getLimit() < 0) {
      throw new IllegalArgumentException("The offset and the limit of a page cannot be negative");
    }
    return new RecycleBinFilter(ownerId, asked.getQuery(), asked.getSortField(), asked.isAscending(), asked.getOffset(), asked.getLimit());
  }

  /** The item of an id, when the caller holds the bin named and the item is one of it. */
  private RecycleBinItem getItemOfScope(String itemId, RecycleBinScope scope, String username) throws ObjectNotFoundException,
                                                                                              IllegalAccessException {
    long ownerId = getOwnerId(scope, username);
    RecycleBinItem item = getItem(itemId);
    if (!Objects.equals(item.getOwnerId(), ownerId)) {
      throw new IllegalAccessException("The item " + itemId + " is not one of the Recycle Bin of the drive " + ownerId);
    }
    return item;
  }

  /** The owner of the drive a scope names, when the caller holds its bin. */
  private long getOwnerId(RecycleBinScope scope, String username) throws ObjectNotFoundException, IllegalAccessException {
    checkCaller(username);
    if (scope == null) {
      throw new IllegalArgumentException("A Recycle Bin is mandatory");
    }
    Identity owner;
    switch (scope.getType()) {
    case SPACE:
      Space space = spaceService.getSpaceById(scope.getId());
      if (space == null) {
        throw new ObjectNotFoundException("Space with id " + scope.getId() + " is not found");
      }
      if (!holdsBinOf(space, username)) {
        throw new IllegalAccessException("User " + username + " neither manages nor publishes in the space " + scope.getId());
      }
      owner = identityManager.getOrCreateSpaceIdentity(space.getPrettyName());
      break;
    case DRIVE:
      // Asked before the drive is looked for: who may not act on a drive
      // learns nothing about which drives exist.
      if (!administratorAccessEnabled || !isPlatformAdministrator(username)) {
        throw new IllegalAccessException("User " + username + " may not act on the Recycle Bin of a drive");
      }
      owner = identityManager.getIdentity(String.valueOf(scope.getId()));
      break;
    default:
      owner = identityManager.getOrCreateUserIdentity(username);
      break;
    }
    if (owner == null || !StringUtils.isNumeric(owner.getId())) {
      throw new ObjectNotFoundException("The owner of the drive is not found");
    }
    return Long.parseLong(owner.getId());
  }

  private RecycleBinItem getItem(String itemId) throws ObjectNotFoundException {
    RecycleBinItem item;
    try {
      item = recycleBinStorage.getItem(itemId);
    } catch (RepositoryException e) {
      throw new IllegalStateException("The item " + itemId + " of the Recycle Bin cannot be read", e);
    }
    if (item == null) {
      throw new ObjectNotFoundException(RecycleBinError.ITEM_NOT_FOUND);
    }
    return item;
  }

  private RecycleBinItem getItemOfDocument(String documentId) throws ObjectNotFoundException {
    RecycleBinItem item;
    try {
      item = recycleBinStorage.getItemOfDocument(documentId);
    } catch (RepositoryException e) {
      throw new IllegalStateException("The deleted document " + documentId + " cannot be read", e);
    }
    if (item == null) {
      throw new ObjectNotFoundException(RecycleBinError.ITEM_NOT_FOUND);
    }
    return item;
  }

  /**
   * Whether a user holds the bin an item is in: their own, or the one of a
   * space they manage or publish in. An item of no drive is in no bin.
   */
  private boolean holdsBinOf(RecycleBinItem item, String username) {
    if (item.getOwnerId() == null) {
      return false;
    }
    Identity owner = identityManager.getIdentity(String.valueOf(item.getOwnerId()));
    if (owner == null) {
      return false;
    }
    if (owner.isSpace()) {
      return holdsBinOf(spaceService.getSpaceByPrettyName(owner.getRemoteId()), username);
    }
    return owner.isUser() && username.equals(owner.getRemoteId());
  }

  /** The managers and the publishers of a space, and no one else, whatever they manage on the platform. */
  private boolean holdsBinOf(Space space, String username) {
    return space != null && (spaceService.isManager(space, username) || spaceService.isPublisher(space, username));
  }

  private void checkPlatformAdministrator(String username) throws IllegalAccessException {
    checkCaller(username);
    if (!isPlatformAdministrator(username)) {
      throw new IllegalAccessException("User " + username + " is no administrator of the platform");
    }
  }

  private boolean isPlatformAdministrator(String username) {
    org.exoplatform.services.security.Identity aclIdentity = userAcl.getUserIdentity(username);
    return aclIdentity != null && aclIdentity.isMemberOf(userAcl.getAdminGroups());
  }

  private static void checkCaller(String username) throws IllegalAccessException {
    if (StringUtils.isBlank(username) || IdentityConstants.ANONIM.equals(username)) {
      throw new IllegalAccessException("A Recycle Bin is opened by a user of the platform only");
    }
  }

}
