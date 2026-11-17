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

  public enum Type {
    /** The bin of the caller's personal drive. */
    USER,
    /** The bin of a space, named by the id of the space. */
    SPACE,
    /** The bin of any drive, named by the identity id of its owner. */
    DRIVE
  }

  private final Type type;

  /** Id of the space, or identity id of the drive's owner; 0 for the caller's own bin. */
  private final long id;

  private RecycleBinScope(Type type, long id) {
    this.type = type;
    this.id = id;
  }

  public static RecycleBinScope user() {
    return new RecycleBinScope(Type.USER, 0);
  }

  public static RecycleBinScope space(long spaceId) {
    return new RecycleBinScope(Type.SPACE, spaceId);
  }

  public static RecycleBinScope drive(long ownerIdentityId) {
    return new RecycleBinScope(Type.DRIVE, ownerIdentityId);
  }

}
