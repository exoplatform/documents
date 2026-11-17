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
package org.exoplatform.documents.storage;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import org.exoplatform.documents.dao.RecycleBinDriveRepository;
import org.exoplatform.documents.entity.RecycleBinDriveEntity;
import org.exoplatform.documents.model.RecycleBinDrive;

import io.meeds.common.ContainerTransactional;

/**
 * Keeps what the Recycle Bin of each drive holds: its number of items and
 * their total size. The totals are changed in the database, by adding to or
 * subtracting from the stored values, so that concurrent changes all count.
 * Each write is a transaction of its own when its caller holds none: a caller
 * that chains several of them is not atomic and says how it handles a failure
 * in between. Called inside a transaction, a write joins it, and a write that
 * fails marks that whole transaction for rollback: a caller that means to
 * retry after a failure, as after {@link #create(long, long)}, holds none.
 */
@Component
public class RecycleBinDriveStorage {

  /** The order the drives are listed in: largest bin first, then by owner. */
  static final Sort                       LARGEST_FIRST = Sort.by(Sort.Order.desc("itemsSize"), Sort.Order.asc("ownerId"));

  private final RecycleBinDriveRepository recycleBinDriveRepository;

  public RecycleBinDriveStorage(RecycleBinDriveRepository recycleBinDriveRepository) {
    this.recycleBinDriveRepository = recycleBinDriveRepository;
  }

  /**
   * Counts one more item in the bin of a drive that already has a row.
   *
   * @param ownerId identity id of the drive owner
   * @param size size of the item, in bytes
   * @return false when the drive has no row yet: nothing was counted, and the
   *         caller creates the row with {@link #create(long, long)}
   */
  @ContainerTransactional(atomic = true)
  public boolean add(long ownerId, long size) {
    return doAdd(ownerId, size);
  }

  /**
   * Creates the row of a drive with its first item.
   *
   * @param ownerId identity id of the drive owner
   * @param size size of the item, in bytes
   * @throws RuntimeException when the drive got a row meanwhile: nothing was
   *           counted, and the caller counts the item with
   *           {@link #add(long, long)}
   */
  @ContainerTransactional(atomic = true)
  public void create(long ownerId, long size) {
    doCreate(ownerId, size);
  }

  /**
   * Counts one item less in the bin of a drive, and deletes the row of a
   * drive left with none.
   *
   * @param ownerId identity id of the drive owner
   * @param size size of the item, in bytes
   */
  @ContainerTransactional(atomic = true)
  public void remove(long ownerId, long size) {
    doRemove(ownerId, size);
  }

  /**
   * Moves the count of one item from the bin of a drive to another's, in one
   * transaction.
   *
   * @param fromOwnerId identity id of the owner of the drive the item leaves
   * @param toOwnerId identity id of the owner of the drive the item joins
   * @param size size of the item, in bytes
   */
  @ContainerTransactional(atomic = true)
  public void move(long fromOwnerId, long toOwnerId, long size) {
    doMove(fromOwnerId, toOwnerId, size);
  }

  /**
   * Replaces the totals of every drive by the ones counted from the trash, in
   * one transaction: each drive counted gets its totals, and the row of a
   * drive that was not counted is deleted. An item counted in or out between
   * the count and this write is overwritten, until the next replacement.
   *
   * @param totals the totals of the drives whose bin holds items
   */
  @ContainerTransactional(atomic = true)
  public void replaceAll(List<RecycleBinDrive> totals) {
    doReplaceAll(totals);
  }

  /**
   * Lists the drives whose bin holds items, largest first.
   *
   * @param offset index of the first drive to return
   * @param limit maximum number of drives to return
   * @return the drives of that page
   */
  public List<RecycleBinDrive> getDrives(long offset, int limit) {
    return recycleBinDriveRepository.findAllBy(new OffsetPageable(offset, limit, LARGEST_FIRST))
                                    .stream()
                                    .map(RecycleBinDriveStorage::toModel)
                                    .toList();
  }

  /**
   * @return the number of drives whose bin holds items
   */
  public long countDrives() {
    return recycleBinDriveRepository.count();
  }

  boolean doAdd(long ownerId, long size) {
    return recycleBinDriveRepository.addToTotals(ownerId, size, 1, new Date()) > 0;
  }

  void doCreate(long ownerId, long size) {
    recycleBinDriveRepository.insert(ownerId, size, 1, new Date());
  }

  void doRemove(long ownerId, long size) {
    if (recycleBinDriveRepository.addToTotals(ownerId, -size, -1, new Date()) > 0) {
      recycleBinDriveRepository.deleteWhenEmpty(ownerId);
    }
  }

  void doReplaceAll(List<RecycleBinDrive> totals) {
    Date now = new Date();
    Set<Long> counted = new HashSet<>();
    for (RecycleBinDrive total : totals) {
      if (total.getItemsCount() <= 0) {
        continue;
      }
      counted.add(total.getOwnerId());
      if (recycleBinDriveRepository.setTotals(total.getOwnerId(), total.getItemsSize(), total.getItemsCount(), now) == 0) {
        recycleBinDriveRepository.insert(total.getOwnerId(), total.getItemsSize(), total.getItemsCount(), now);
      }
    }
    for (Long ownerId : recycleBinDriveRepository.findOwnerIds()) {
      if (!counted.contains(ownerId)) {
        recycleBinDriveRepository.deleteByOwner(ownerId);
      }
    }
  }

  void doMove(long fromOwnerId, long toOwnerId, long size) {
    doRemove(fromOwnerId, size);
    if (!doAdd(toOwnerId, size)) {
      doCreate(toOwnerId, size);
    }
  }

  private static RecycleBinDrive toModel(RecycleBinDriveEntity entity) {
    // A total may have drifted below zero between two recomputations.
    return new RecycleBinDrive(entity.getOwnerId(), Math.max(0, entity.getItemsSize()), Math.max(0, entity.getItemsCount()));
  }

}
