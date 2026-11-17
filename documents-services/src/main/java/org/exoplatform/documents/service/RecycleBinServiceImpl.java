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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jcr.InvalidItemStateException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.documents.constant.RecycleBinError;
import org.exoplatform.documents.model.ActionData;
import org.exoplatform.documents.model.ActionStatus;
import org.exoplatform.documents.model.ActionType;
import org.exoplatform.documents.model.RecycleBinDrive;
import org.exoplatform.documents.model.RecycleBinFilter;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.model.RecycleBinScope;
import org.exoplatform.documents.model.RecycleBinSortField;
import org.exoplatform.documents.storage.BulkItemAction;
import org.exoplatform.documents.storage.JCRDeleteFileStorage;
import org.exoplatform.documents.storage.RecycleBinDriveStorage;
import org.exoplatform.documents.storage.RecycleBinStorage;
import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import jakarta.annotation.PreDestroy;

@Service
public class RecycleBinServiceImpl implements RecycleBinService {

  /** The event the bulk actions report on: who listens to it tells the screens of the user. */
  static final String                      BULK_ACTION_EVENT   = "bulk_actions_document_event";

  /** What a purge asked for by a user reports under, for a screen to tell it from a bulk action. */
  static final String                      PURGE_ACTION_ID     = "recycle-bin-purge";

  private static final String              PURGE_THREAD        = "documents-recycle-bin-purge";

  private static final Log                 LOG                 = ExoLogger.getLogger(RecycleBinServiceImpl.class);

  private final RecycleBinStorage          recycleBinStorage;

  private final RecycleBinDriveStorage     recycleBinDriveStorage;

  private final TrashStorage               trashStorage;

  private final JCRDeleteFileStorage       jcrDeleteFileStorage;

  private final RecycleBinRetentionSetting retentionSetting;

  private final IdentityManager            identityManager;

  private final SpaceService               spaceService;

  private final UserACL                    userAcl;

  private final ListenerService            listenerService;

  private final boolean                    administratorAccessEnabled;

  /** Runs the purge, one at a time. */
  private final Executor                   purgeExecutor;

  /** Guards the three fields below: the daily run and the purges asked for are one run. */
  private final Object                     purgeLock           = new Object();

  private boolean                          purgeRunning;

  private boolean                          dailyRunOwed;

  private final Set<String>                purgeRequesters     = new LinkedHashSet<>();

  @Autowired
  public RecycleBinServiceImpl(RecycleBinStorage recycleBinStorage, // NOSONAR the collaborators of a service
                               RecycleBinDriveStorage recycleBinDriveStorage,
                               TrashStorage trashStorage,
                               JCRDeleteFileStorage jcrDeleteFileStorage,
                               RecycleBinRetentionSetting retentionSetting,
                               IdentityManager identityManager,
                               SpaceService spaceService,
                               UserACL userAcl,
                               ListenerService listenerService,
                               @Value("${exo.documents.recycleBin.administratorAccess.enabled:true}")
                               boolean administratorAccessEnabled) {
    this(recycleBinStorage,
         recycleBinDriveStorage,
         trashStorage,
         jcrDeleteFileStorage,
         retentionSetting,
         identityManager,
         spaceService,
         userAcl,
         listenerService,
         administratorAccessEnabled,
         newPurgeExecutor());
  }

  RecycleBinServiceImpl(RecycleBinStorage recycleBinStorage, // NOSONAR the collaborators of a service
                        RecycleBinDriveStorage recycleBinDriveStorage,
                        TrashStorage trashStorage,
                        JCRDeleteFileStorage jcrDeleteFileStorage,
                        RecycleBinRetentionSetting retentionSetting,
                        IdentityManager identityManager,
                        SpaceService spaceService,
                        UserACL userAcl,
                        ListenerService listenerService,
                        boolean administratorAccessEnabled,
                        Executor purgeExecutor) {
    this.recycleBinStorage = recycleBinStorage;
    this.recycleBinDriveStorage = recycleBinDriveStorage;
    this.trashStorage = trashStorage;
    this.jcrDeleteFileStorage = jcrDeleteFileStorage;
    this.retentionSetting = retentionSetting;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.userAcl = userAcl;
    this.listenerService = listenerService;
    this.administratorAccessEnabled = administratorAccessEnabled;
    this.purgeExecutor = purgeExecutor;
  }

  @PreDestroy
  void stopPurge() {
    if (purgeExecutor instanceof PurgeExecutor executor) {
      executor.close();
    }
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
  public void restoreItems(String actionId, List<String> itemIds, RecycleBinScope scope, String username) throws ObjectNotFoundException,
                                                                                                          IllegalAccessException {
    getOwnerId(scope, username);
    startBulkAction(actionId, ActionType.RESTORE, checkItemIds(itemIds), username, itemId -> {
      try {
        restoreItem(itemId, scope, username);
        return true;
      } catch (ObjectNotFoundException e) {
        return false;
      }
    });
  }

  @Override
  public void deleteItems(String actionId, List<String> itemIds, RecycleBinScope scope, String username) throws ObjectNotFoundException,
                                                                                                         IllegalAccessException {
    getOwnerId(scope, username);
    startBulkAction(actionId, ActionType.PERMANENTLY_DELETE, checkItemIds(itemIds), username, removal(scope, username));
  }

  @Override
  public void emptyBin(String actionId, RecycleBinScope scope, String username) throws ObjectNotFoundException, IllegalAccessException {
    long ownerId = getOwnerId(scope, username);
    List<String> itemIds;
    try {
      itemIds = recycleBinStorage.getItems(new RecycleBinFilter(ownerId, null, RecycleBinSortField.DELETED_DATE, false, 0, 0))
                                 .stream()
                                 .map(RecycleBinItem::getId)
                                 .toList();
    } catch (RepositoryException e) {
      throw new IllegalStateException("The Recycle Bin of the drive " + ownerId + " cannot be read", e);
    }
    startBulkAction(actionId, ActionType.PERMANENTLY_DELETE, itemIds, username, removal(scope, username));
  }

  /** The removal of an item at its turn: one that already left, alone or with another, is left out. */
  private BulkItemAction removal(RecycleBinScope scope, String username) {
    return itemId -> {
      try {
        deleteItem(itemId, scope, username);
        return true;
      } catch (ObjectNotFoundException e) {
        return false;
      }
    };
  }

  private void startBulkAction(String actionId, ActionType actionType, List<String> itemIds, String username, BulkItemAction itemAction) {
    if (StringUtils.isBlank(actionId)) {
      throw new IllegalArgumentException("The id of the bulk action is mandatory");
    }
    ActionData actionData = new ActionData();
    actionData.setActionId(actionId);
    actionData.setActionType(actionType.name());
    actionData.setIdentity(userAcl.getUserIdentity(username));
    actionData.setUserName(username);
    actionData.setNumberOfItems(itemIds.size());
    jcrDeleteFileStorage.executeBulkAction(actionData, itemIds, itemAction);
  }

  private static List<String> checkItemIds(List<String> itemIds) {
    if (itemIds == null || itemIds.isEmpty() || itemIds.stream().anyMatch(StringUtils::isBlank)) {
      throw new IllegalArgumentException("The ids of the items are mandatory");
    }
    return itemIds;
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

  @Override
  public long getRetentionDays() {
    return retentionSetting.get();
  }

  @Override
  public void setRetentionDays(long retentionDays, String username) throws IllegalAccessException {
    checkPlatformAdministrator(username);
    long previousDays = retentionSetting.get();
    retentionSetting.set(retentionDays);
    // Asked once the period is stored: the purge reads it when it runs.
    if (retentionDays < previousDays) {
      purgeExpired(username);
    }
  }

  @Override
  public void purgeExpired() {
    startPurge(null);
  }

  @Override
  public void purgeExpired(String requester) {
    if (StringUtils.isBlank(requester)) {
      throw new IllegalArgumentException("The user a purge is run for is mandatory");
    }
    startPurge(requester);
  }

  /** Records what is asked for, and starts the run when none is going on: the one going on serves it. */
  private void startPurge(String requester) {
    synchronized (purgeLock) {
      if (requester == null) {
        dailyRunOwed = true;
      } else {
        purgeRequesters.add(requester);
      }
      if (purgeRunning) {
        return;
      }
      purgeRunning = true;
    }
    try {
      purgeExecutor.execute(this::runPurgeRounds);
    } catch (RuntimeException e) {
      synchronized (purgeLock) {
        purgeRunning = false;
      }
      throw e;
    }
  }

  /** One round for what was asked before it started, then another while more was asked meanwhile. */
  private void runPurgeRounds() {
    boolean ended = false;
    try {
      while (true) {
        boolean dailyRun;
        List<String> requesters;
        synchronized (purgeLock) {
          if (!dailyRunOwed && purgeRequesters.isEmpty()) {
            purgeRunning = false;
            ended = true;
            return;
          }
          dailyRun = dailyRunOwed;
          dailyRunOwed = false;
          requesters = new ArrayList<>(purgeRequesters);
          purgeRequesters.clear();
        }
        runPurgeRound(dailyRun, requesters);
      }
    } finally {
      if (!ended) {
        // The run broke off: the next call starts another.
        synchronized (purgeLock) {
          purgeRunning = false;
        }
      }
    }
  }

  private void runPurgeRound(boolean dailyRun, List<String> requesters) {
    int removed = 0;
    boolean failed = false;
    try {
      if (dailyRun) {
        int normalized = trashStorage.normalizeRecycleBinItems();
        if (normalized > 0) {
          LOG.info("{} item(s) of the trash were given their Recycle Bin facts", normalized);
        }
        recycleBinDriveStorage.replaceAll(recycleBinStorage.getDriveTotals());
      }
      removed = removeExpiredItems();
    } catch (RepositoryException | RuntimeException e) {
      LOG.error("The run that removes the expired items of the Recycle Bins ended on a failure", e);
      failed = true;
    }
    for (String requester : requesters) {
      tellRequester(requester, removed, failed);
    }
  }

  /** @return the number of items that left a bin */
  private int removeExpiredItems() throws RepositoryException {
    int removed = 0;
    for (String itemId : recycleBinStorage.getIdsDeletedBefore(getExpiryInstant())) {
      removed += removeWhenExpired(itemId);
    }
    return removed;
  }

  /**
   * Reads an item again at its turn and removes it when it is still due.
   *
   * @return the number of items that left a bin with it, itself included
   */
  private int removeWhenExpired(String itemId) {
    try {
      RecycleBinItem item = recycleBinStorage.getRemovableItem(itemId);
      // Restored, deleted again, or given more time by a longer period since it was selected.
      if (item == null || item.getDeletedDate() == null || !Instant.ofEpochMilli(item.getDeletedDate()).isBefore(getExpiryInstant())) {
        return 0;
      }
      List<RecycleBinItem> removedWithIt = recycleBinStorage.removeItem(itemId);
      countOut(item);
      removedWithIt.forEach(this::countOut);
      return (item.getOwnerId() == null ? 0 : 1) + removedWithIt.size();
    } catch (PathNotFoundException | InvalidItemStateException e) {
      LOG.debug("The expired item {} of the trash was removed or restored meanwhile", itemId, e);
      return 0;
    } catch (RepositoryException | RuntimeException e) {
      LOG.warn("The expired item {} of the trash could not be removed: it is left to the next run", itemId, e);
      return 0;
    }
  }

  /** What was deleted before this instant has stayed longer than the period in force now. */
  private Instant getExpiryInstant() {
    return Instant.now().minus(Duration.ofDays(retentionSetting.get()));
  }

  private void tellRequester(String requester, int removed, boolean failed) {
    try {
      org.exoplatform.services.security.Identity aclIdentity = userAcl.getUserIdentity(requester);
      ActionData actionData = new ActionData();
      actionData.setActionId(PURGE_ACTION_ID);
      actionData.setActionType(ActionType.PERMANENTLY_DELETE.name());
      actionData.setStatus(failed ? ActionStatus.FAILED.name() : ActionStatus.DONE_SUCCESSFULLY.name());
      actionData.setNumberOfItems(removed);
      actionData.setUserName(requester);
      actionData.setIdentity(aclIdentity);
      listenerService.broadcast(BULK_ACTION_EVENT, aclIdentity, actionData);
    } catch (Exception e) {
      LOG.warn("User {} could not be told that the purge of the Recycle Bins is over", requester, e);
    }
  }

  private static Executor newPurgeExecutor() {
    ExecutorService executorService = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, PURGE_THREAD);
      thread.setDaemon(true);
      return thread;
    });
    return new PurgeExecutor(executorService);
  }

  /** Runs each purge on its thread, inside the portal container and a request of its own. */
  private static final class PurgeExecutor implements Executor, AutoCloseable {

    private final ExecutorService executorService;

    private PurgeExecutor(ExecutorService executorService) {
      this.executorService = executorService;
    }

    @Override
    public void execute(Runnable purge) {
      PortalContainer container = PortalContainer.getInstance();
      executorService.execute(() -> {
        ExoContainerContext.setCurrentContainer(container);
        RequestLifeCycle.begin(container);
        try {
          purge.run();
        } finally {
          RequestLifeCycle.end();
        }
      });
    }

    @Override
    public void close() {
      executorService.shutdownNow();
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
