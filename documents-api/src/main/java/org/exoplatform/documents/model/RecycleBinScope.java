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
package org.exoplatform.documents.model;

import org.exoplatform.documents.constant.RecycleBinScopeType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Which Recycle Bin a caller acts on: their own, the one of a space they
 * manage or publish in, or, for an administrator of the platform, the one of
 * any drive.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class RecycleBinScope {

  private final RecycleBinScopeType type;

  /** Id of the space, or identity id of the drive's owner; 0 for the caller's own bin. */
  private final long id;

  private RecycleBinScope(RecycleBinScopeType type, long id) {
    this.type = type;
    this.id = id;
  }

  public static RecycleBinScope user() {
    return new RecycleBinScope(RecycleBinScopeType.USER, 0);
  }

  public static RecycleBinScope space(long spaceId) {
    return new RecycleBinScope(RecycleBinScopeType.SPACE, spaceId);
  }

  public static RecycleBinScope drive(long ownerIdentityId) {
    return new RecycleBinScope(RecycleBinScopeType.DRIVE, ownerIdentityId);
  }

}
