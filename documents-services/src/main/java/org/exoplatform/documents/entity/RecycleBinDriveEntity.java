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
package org.exoplatform.documents.entity;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * What the Recycle Bin of a drive holds. The row is keyed by the identity id
 * of the drive owner, which the application gives: there is no generated id.
 * Its totals are only ever changed by statements that add to or subtract from
 * the stored value, never by saving an entity that was read, so that two
 * deletions at the same time both count.
 */
@Entity(name = "RecycleBinDrive")
@Table(name = "DOCUMENTS_RECYCLE_BIN_DRIVE")
@Data
public class RecycleBinDriveEntity implements Serializable {

  private static final long serialVersionUID = 3180571943322604618L;

  @Id
  @Column(name = "OWNER_ID", nullable = false)
  private Long              ownerId;

  @Column(name = "ITEMS_SIZE", nullable = false)
  private long              itemsSize;

  @Column(name = "ITEMS_COUNT", nullable = false)
  private long              itemsCount;

  @Column(name = "UPDATED_DATE")
  private Date              updatedDate;

}
