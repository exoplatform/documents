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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.documents.model.RecycleBinDrive;
import org.exoplatform.documents.model.RecycleBinItem;
import org.exoplatform.documents.rest.model.RecycleBinDriveEntity;
import org.exoplatform.documents.rest.model.RecycleBinItemEntity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * What the screens of the Recycle Bin read of an item and of a drive.
 */
class RecycleBinEntityBuilderTest {

  private IdentityManager identityManager;

  private SpaceService    spaceService;

  @BeforeEach
  void setUp() {
    identityManager = mock(IdentityManager.class);
    spaceService = mock(SpaceService.class);
  }

  @Test
  void anItemIsListedWithWhatWasRecordedAndTheCurrentNameOfWhoDeletedIt() {
    Identity mary = identity(false, "mary", "Mary Smith", null, false);
    when(identityManager.getOrCreateUserIdentity("mary")).thenReturn(mary);
    RecycleBinItem item = item();

    RecycleBinItemEntity entity = RecycleBinEntityBuilder.toItemEntity(item, true, identityManager);

    assertEquals("item-id", entity.getId());
    assertEquals("Report.docx", entity.getName());
    assertFalse(entity.isFolder());
    assertEquals("application/msword", entity.getMimeType());
    assertEquals(12, entity.getSize());
    assertEquals(Long.valueOf(1234), entity.getDeletedDate());
    assertEquals("mary", entity.getDeletedBy().getUsername());
    assertEquals("Mary Smith", entity.getDeletedBy().getFullName());
    assertEquals("/Projects/2026", entity.getOriginalLocation().getPath());
    assertTrue(entity.isCanView());
    assertEquals("document-id", entity.getDocumentId());
  }

  @Test
  void theIdOfTheDocumentIsGivenOnlyToWhoMayOpenIt() {
    // The bin of a drive an administrator acts on.
    RecycleBinItemEntity inDrive = RecycleBinEntityBuilder.toItemEntity(item(), false, identityManager);
    assertFalse(inDrive.isCanView());
    assertNull(inDrive.getDocumentId());

    RecycleBinItem folder = item();
    folder.setFolder(true);
    RecycleBinItemEntity folderEntity = RecycleBinEntityBuilder.toItemEntity(folder, true, identityManager);
    assertTrue(folderEntity.isFolder());
    assertFalse(folderEntity.isCanView());
    assertNull(folderEntity.getDocumentId());

    RecycleBinItem shortcut = item();
    shortcut.setShortcut(true);
    RecycleBinItemEntity shortcutEntity = RecycleBinEntityBuilder.toItemEntity(shortcut, true, identityManager);
    assertFalse(shortcutEntity.isCanView());
    assertNull(shortcutEntity.getDocumentId());
  }

  @Test
  void whoDeletedAnItemIsEmptyWhenItWasNotRecordedAndNamedByTheUsernameWhenNoNameIsKnown() {
    RecycleBinItem unknown = item();
    unknown.setDeletedBy(null);
    unknown.setRestorePath(null);
    RecycleBinItemEntity entity = RecycleBinEntityBuilder.toItemEntity(unknown, true, identityManager);
    assertNull(entity.getDeletedBy());
    assertNull(entity.getOriginalLocation());
    verify(identityManager, never()).getOrCreateUserIdentity(anyString());

    // The user is gone, or has no name.
    RecycleBinItem byMary = item();
    assertEquals("mary", RecycleBinEntityBuilder.toItemEntity(byMary, true, identityManager).getDeletedBy().getFullName());
    Identity nameless = identity(false, "mary", " ", null, false);
    when(identityManager.getOrCreateUserIdentity("mary")).thenReturn(nameless);
    assertEquals("mary", RecycleBinEntityBuilder.toItemEntity(byMary, true, identityManager).getDeletedBy().getFullName());
    Identity noProfile = mock(Identity.class);
    when(identityManager.getOrCreateUserIdentity("mary")).thenReturn(noProfile);
    assertEquals("mary", RecycleBinEntityBuilder.toItemEntity(byMary, true, identityManager).getDeletedBy().getFullName());
  }

  @Test
  void theOriginalLocationIsTheFolderFromTheRootOfItsDrive() {
    assertEquals("/Projects/2026", RecycleBinEntityBuilder.toOriginalLocation("/Users/j___/jo___/joh___/john/Private/Projects/2026/report.docx"));
    assertEquals("/", RecycleBinEntityBuilder.toOriginalLocation("/Users/j___/jo___/joh___/john/Private/report.docx"));
    assertEquals("/Plans", RecycleBinEntityBuilder.toOriginalLocation("/Groups/spaces/sales/Documents/Plans/q1.xlsx"));
    assertEquals("/", RecycleBinEntityBuilder.toOriginalLocation("/Groups/spaces/sales/Documents/q1.xlsx"));
    // A folder named as a drive root, deeper in a drive, is a folder.
    assertEquals("/Private/Documents", RecycleBinEntityBuilder.toOriginalLocation("/Users/j___/jo___/joh___/john/Private/Private/Documents/a.txt"));
    // In no drive: the folder as it is.
    assertEquals("/Users/j___/jo___/joh___/john/Public", RecycleBinEntityBuilder.toOriginalLocation("/Users/j___/jo___/joh___/john/Public/a.txt"));
    assertEquals("/Groups/platform/users/Documents", RecycleBinEntityBuilder.toOriginalLocation("/Groups/platform/users/Documents/a.txt"));
    assertEquals("/sites", RecycleBinEntityBuilder.toOriginalLocation("/sites/a.txt"));
    assertEquals("/", RecycleBinEntityBuilder.toOriginalLocation("/a.txt"));
    assertNull(RecycleBinEntityBuilder.toOriginalLocation(null));
    assertNull(RecycleBinEntityBuilder.toOriginalLocation(" "));
  }

  @Test
  void aDriveIsListedWithTheNameAndTheAvatarOfItsOwner() {
    Identity john = identity(false, "john", "John Doe", "/avatar/john", false);
    Identity sales = identity(true, "sales", "sales", "/avatar/identity", false);
    when(identityManager.getIdentity("7")).thenReturn(john);
    when(identityManager.getIdentity("20")).thenReturn(sales);
    Space space = new Space();
    space.setDisplayName("Sales Team");
    space.setAvatarUrl("/avatar/sales");
    when(spaceService.getSpaceByPrettyName("sales")).thenReturn(space);

    RecycleBinDriveEntity user = RecycleBinEntityBuilder.toDriveEntity(new RecycleBinDrive(7, 150, 3), identityManager, spaceService);
    assertEquals(7, user.getOwnerIdentityId());
    assertEquals("USER", user.getType());
    assertEquals("John Doe", user.getName());
    assertEquals("/avatar/john", user.getAvatarUrl());
    assertEquals(150, user.getSize());
    assertEquals(3, user.getItemsCount());
    assertFalse(user.isDeleted());

    RecycleBinDriveEntity ofSpace = RecycleBinEntityBuilder.toDriveEntity(new RecycleBinDrive(20, 70, 1), identityManager, spaceService);
    assertEquals(20, ofSpace.getOwnerIdentityId());
    assertEquals("SPACE", ofSpace.getType());
    assertEquals("Sales Team", ofSpace.getName());
    assertEquals("/avatar/sales", ofSpace.getAvatarUrl());
    assertFalse(ofSpace.isDeleted());
  }

  @Test
  void aDriveWhoseOwnerWasDeletedKeepsItsLastKnownNameAndSaysSo() {
    Identity deletedUser = identity(false, "paul", "Paul Gone", null, true);
    Identity goneSpace = identity(true, "old", "Old Team", null, false);
    Identity bare = mock(Identity.class);
    when(bare.getRemoteId()).thenReturn("bare");
    when(identityManager.getIdentity("8")).thenReturn(deletedUser);
    when(identityManager.getIdentity("21")).thenReturn(goneSpace);
    when(identityManager.getIdentity("9")).thenReturn(bare);

    RecycleBinDriveEntity user = RecycleBinEntityBuilder.toDriveEntity(new RecycleBinDrive(8, 10, 1), identityManager, spaceService);
    assertEquals("USER", user.getType());
    assertEquals("Paul Gone", user.getName());
    assertTrue(user.isDeleted());

    // The identity of the space is still there, the space is not.
    RecycleBinDriveEntity ofSpace = RecycleBinEntityBuilder.toDriveEntity(new RecycleBinDrive(21, 10, 1), identityManager, spaceService);
    assertEquals("SPACE", ofSpace.getType());
    assertEquals("Old Team", ofSpace.getName());
    assertNull(ofSpace.getAvatarUrl());
    assertTrue(ofSpace.isDeleted());

    // A user with no profile is named by its username.
    RecycleBinDriveEntity noProfile = RecycleBinEntityBuilder.toDriveEntity(new RecycleBinDrive(9, 10, 1), identityManager, spaceService);
    assertEquals("bare", noProfile.getName());
    assertNull(noProfile.getAvatarUrl());

    // No identity at all: the sizes only.
    RecycleBinDriveEntity unknown = RecycleBinEntityBuilder.toDriveEntity(new RecycleBinDrive(99, 10, 2), identityManager, spaceService);
    assertEquals(99, unknown.getOwnerIdentityId());
    assertEquals(10, unknown.getSize());
    assertEquals(2, unknown.getItemsCount());
    assertNull(unknown.getType());
    assertNull(unknown.getName());
    assertTrue(unknown.isDeleted());
  }

  private static RecycleBinItem item() {
    RecycleBinItem item = new RecycleBinItem();
    item.setId("item-id");
    item.setDocumentId("document-id");
    item.setName("Report.docx");
    item.setMimeType("application/msword");
    item.setSize(12);
    item.setDeletedDate(1234L);
    item.setDeletedBy("mary");
    item.setRestorePath("/Users/j___/jo___/joh___/john/Private/Projects/2026/report.docx");
    return item;
  }

  private static Identity identity(boolean space, String remoteId, String fullName, String avatarUrl, boolean deleted) {
    Identity identity = mock(Identity.class);
    Profile profile = mock(Profile.class);
    when(identity.isSpace()).thenReturn(space);
    when(identity.getRemoteId()).thenReturn(remoteId);
    when(identity.isDeleted()).thenReturn(deleted);
    when(identity.getProfile()).thenReturn(profile);
    when(profile.getFullName()).thenReturn(fullName);
    when(profile.getAvatarUrl()).thenReturn(avatarUrl);
    return identity;
  }

}
