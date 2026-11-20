/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.documents.storage.jcr;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.Session;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.exoplatform.documents.storage.TrashStorage;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.MembershipEntry;

/**
 * The right to restore a trashed document is the engine's: a mocked session
 * cannot show which members may add to the folder it was deleted from.
 */
class TrashRestoreRightTest {

  /** A host of the space, who may add to its documents folder. */
  private static final Identity         WRITER = new Identity("restorehost",
                                                              List.of(new MembershipEntry("/spaces/restorespace",
                                                                                          "manager")));

  /** A member of the space, who may only read its documents folder. */
  private static final Identity         READER = new Identity("restoremember",
                                                              List.of(new MembershipEntry("/spaces/restorespace",
                                                                                          "member")));

  private static Session                systemSession;

  private static TrashStorage           trashStorage;

  private static SessionProvider        systemSessionProvider;

  private static JCRDeleteFileStorageImpl deleteFileStorage;

  private static String                 trashedId;

  private static String                 trashedPath;

  @BeforeAll
  static void setUpRepository() throws Exception {
    RepositoryService repositoryService = PublicLinkFolderDownloadTest.startContainer()
                                                                      .getComponentInstanceOfType(RepositoryService.class);
    ManageableRepository repository = repositoryService.getDefaultRepository();
    repositoryService.setCurrentRepositoryName(repository.getConfiguration().getName());
    String workspace = repository.getConfiguration().getDefaultWorkspaceName();
    systemSession = repository.getSystemSession(workspace);

    Node root = systemSession.getRootNode();
    Node space = root.addNode("restorespace", "nt:folder");
    Node documents = space.addNode("Documents", "nt:folder");
    PublicLinkFolderDownloadTest.setPermissions(documents,
                                                Map.of("*:/spaces/restorespace",
                                                       new String[] { PermissionType.READ },
                                                       "manager:/spaces/restorespace",
                                                       PermissionType.ALL));
    Node trashHome = root.addNode("RestoreTrash", "nt:unstructured");
    Node trashed = trashHome.addNode("report.pdf", "nt:unstructured");
    trashed.setProperty("exo:restorePath", "/restorespace/Documents/report.pdf");
    trashed.setProperty("exo:restoreWorkspace", workspace);
    systemSession.save();
    trashedId = ((ExtendedNode) trashed).getIdentifier();
    trashedPath = trashed.getPath();

    trashStorage = mock(TrashStorage.class);
    when(trashStorage.getTrashHomeNode()).thenReturn(trashHome);
    when(trashStorage.isInTrash(any())).thenReturn(true);
    SessionProviderService sessionProviderService = mock(SessionProviderService.class);
    systemSessionProvider = mock(SessionProvider.class);
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(systemSessionProvider);
    deleteFileStorage = new JCRDeleteFileStorageImpl(repositoryService,
                                                     null,
                                                     trashStorage,
                                                     null,
                                                     null,
                                                     sessionProviderService,
                                                     null,
                                                     null);
  }

  @AfterAll
  static void closeSession() {
    if (systemSession != null) {
      systemSession.logout();
    }
  }

  @Test
  void aMemberWhoMayOnlyReadTheFolderCannotRestoreAndAWriterCan() throws Exception {
    assertThrows(IllegalAccessException.class, () -> deleteFileStorage.restoreFromTrash(trashedId, READER));
    verify(trashStorage, never()).restoreFromTrash(anyString(), any());

    deleteFileStorage.restoreFromTrash(trashedId, WRITER);
    verify(trashStorage).restoreFromTrash(trashedPath, systemSessionProvider);
  }
}
