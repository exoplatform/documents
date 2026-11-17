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
package org.exoplatform.documents.dao;

import java.util.Date;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.exoplatform.documents.entity.RecycleBinDriveEntity;

public interface RecycleBinDriveRepository extends JpaRepository<RecycleBinDriveEntity, Long> {

  /**
   * Adds to the totals of a drive, in the database: the stored values are not
   * read first. Negative values subtract.
   *
   * @return the number of rows changed, 0 when the drive has no row
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE RecycleBinDrive d
         SET d.itemsSize = d.itemsSize + :size,
             d.itemsCount = d.itemsCount + :count,
             d.updatedDate = :updatedDate
       WHERE d.ownerId = :ownerId
      """)
  int addToTotals(@Param("ownerId") long ownerId,
                  @Param("size") long size,
                  @Param("count") long count,
                  @Param("updatedDate") Date updatedDate);

  /**
   * Creates the row of a drive. A drive that already has one is refused by
   * the primary key: the row is never replaced.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      INSERT INTO RecycleBinDrive (ownerId, itemsSize, itemsCount, updatedDate)
      VALUES (:ownerId, :size, :count, :updatedDate)
      """)
  int insert(@Param("ownerId") long ownerId,
             @Param("size") long size,
             @Param("count") long count,
             @Param("updatedDate") Date updatedDate);

  /**
   * Deletes the row of a drive whose bin holds no item any more.
   *
   * @return the number of rows deleted
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("DELETE FROM RecycleBinDrive d WHERE d.ownerId = :ownerId AND d.itemsCount <= 0")
  int deleteWhenEmpty(@Param("ownerId") long ownerId);

}
