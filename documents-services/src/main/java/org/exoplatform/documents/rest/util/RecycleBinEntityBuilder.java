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
package org.exoplatform.documents.rest.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.documents.model.RecycleBinDrive;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.rest.model.RecycleBinDriveEntity;
import org.exoplatform.documents.rest.model.RecycleBinItemEntity;
import org.exoplatform.documents.rest.model.RecycleBinLocationEntity;
import org.exoplatform.documents.rest.model.RecycleBinUserEntity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Builds what the screens of the Recycle Bin read from what the service
 * returns.
 */
public final class RecycleBinEntityBuilder {

  /**
   * The folders a user owns under their home: the home lies under one level
   * per leading letter of the name, three at most, each ending with "___".
   */
  private static final Pattern USER_DRIVE  = Pattern.compile("^/Users/(?:[^/]+___/){0,3}[^/]+/(Private|Public)(/.*)?$");

  /** The folders of the drive of a space. */
  private static final Pattern SPACE_DRIVE = Pattern.compile("^/Groups/spaces/[^/]+/Documents(/.*)?$");

  private static final String  PUBLIC_FOLDER = "Public";

  private RecycleBinEntityBuilder() {
  }

  /**
   * @param item an item of a bin
   * @param viewable whether the bin is one its items are opened from: the
   *          caller's own or a space's, never the bin of a drive an
   *          administrator acts on
   * @param identityManager reads the name of who deleted the item
   * @return the item as a screen lists it
   */
  public static RecycleBinItemEntity toItemEntity(RecycleBinItem item, boolean viewable, IdentityManager identityManager) {
    RecycleBinItemEntity entity = new RecycleBinItemEntity();
    entity.setId(item.getId());
    entity.setName(item.getName());
    entity.setFolder(item.isFolder());
    entity.setMimeType(item.getMimeType());
    entity.setSize(item.getSize());
    entity.setDeletedDate(item.getDeletedDate());
    entity.setDeletedBy(toUser(item.getDeletedBy(), identityManager));
    String location = toOriginalLocation(item.getRestorePath(), item.getRestoreFolderTitles());
    entity.setOriginalLocation(location == null ? null : new RecycleBinLocationEntity(location));
    // A folder and a shortcut have no content of their own to open.
    entity.setCanView(viewable && !item.isFolder() && !item.isShortcut());
    entity.setDocumentId(entity.isCanView() ? item.getDocumentId() : null);
    return entity;
  }

  /**
   * @param restorePath the path an item was deleted from
   * @return the folder it was in, from the root of its drive, "/" for the
   *         root itself, the public folder of a user and what it holds
   *         under "/Public"; the whole path of the folder when it is in no
   *         drive; null when no path was recorded
   */
  public static String toOriginalLocation(String restorePath) {
    return toOriginalLocation(restorePath, null);
  }

  /**
   * @param restorePath the path an item was deleted from
   * @param folderTitles the names the documents show of the folders of that
   *          path, one for each of them from the root of the repository, or
   *          null when not known
   * @return the folder it was in, as {@link #toOriginalLocation(String)}
   *         gives it, each folder of its drive named as the documents show it
   *         when the names are known
   */
  public static String toOriginalLocation(String restorePath, List<String> folderTitles) {
    if (StringUtils.isBlank(restorePath)) {
      return null;
    }
    int nameIndex = restorePath.lastIndexOf('/');
    String folder = nameIndex <= 0 ? "/" : restorePath.substring(0, nameIndex);
    Matcher userDrive = USER_DRIVE.matcher(folder);
    if (userDrive.matches()) {
      String inDrive = toTitles(folder, StringUtils.defaultString(userDrive.group(2)), folderTitles);
      return PUBLIC_FOLDER.equals(userDrive.group(1)) ? "/" + PUBLIC_FOLDER + inDrive : StringUtils.defaultIfEmpty(inDrive, "/");
    }
    Matcher spaceDrive = SPACE_DRIVE.matcher(folder);
    if (spaceDrive.matches()) {
      return StringUtils.defaultIfEmpty(toTitles(folder, StringUtils.defaultString(spaceDrive.group(1)), folderTitles), "/");
    }
    return folder;
  }

  /**
   * The folders of a drive a folder lies under, named by their titles: the
   * last ones of the folder's path, whose titles are the last ones given.
   */
  private static String toTitles(String folder, String inDrive, List<String> folderTitles) {
    String[] names = StringUtils.split(folder, '/');
    if (inDrive.isEmpty() || folderTitles == null || folderTitles.size() != names.length) {
      return inDrive;
    }
    StringBuilder titled = new StringBuilder();
    for (int i = names.length - StringUtils.split(inDrive, '/').length; i < names.length; i++) {
      titled.append('/').append(StringUtils.defaultIfBlank(folderTitles.get(i), names[i]));
    }
    return titled.toString();
  }

  /**
   * @param drive the totals of the bin of a drive
   * @return the drive as the Bin Overview lists it, with what is known of
   *         its owner
   */
  public static RecycleBinDriveEntity toDriveEntity(RecycleBinDrive drive, IdentityManager identityManager, SpaceService spaceService) {
    RecycleBinDriveEntity entity = new RecycleBinDriveEntity();
    entity.setOwnerIdentityId(drive.getOwnerId());
    entity.setSize(drive.getItemsSize());
    entity.setItemsCount(drive.getItemsCount());
    Identity owner = identityManager.getIdentity(String.valueOf(drive.getOwnerId()));
    if (owner == null) {
      entity.setDeleted(true);
      return entity;
    }
    entity.setDeleted(owner.isDeleted());
    if (owner.isSpace()) {
      entity.setType("SPACE");
      Space space = spaceService.getSpaceByPrettyName(owner.getRemoteId());
      if (space == null) {
        entity.setDeleted(true);
        entity.setName(getFullName(owner, owner.getRemoteId()));
      } else {
        entity.setName(space.getDisplayName());
        entity.setAvatarUrl(space.getAvatarUrl());
      }
    } else {
      entity.setType("USER");
      entity.setName(getFullName(owner, owner.getRemoteId()));
      entity.setAvatarUrl(owner.getProfile() == null ? null : owner.getProfile().getAvatarUrl());
    }
    return entity;
  }

  private static RecycleBinUserEntity toUser(String username, IdentityManager identityManager) {
    if (StringUtils.isBlank(username)) {
      return null;
    }
    return new RecycleBinUserEntity(username, getFullName(identityManager.getOrCreateUserIdentity(username), username));
  }

  private static String getFullName(Identity identity, String fallback) {
    String fullName = identity == null || identity.getProfile() == null ? null : identity.getProfile().getFullName();
    return StringUtils.isBlank(fullName) ? fallback : fullName;
  }

}
