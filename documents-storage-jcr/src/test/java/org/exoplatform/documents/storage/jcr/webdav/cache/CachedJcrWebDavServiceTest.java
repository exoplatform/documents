/**
 * Copyright (C) 2025 eXo Platform SAS
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
package org.exoplatform.documents.storage.jcr.webdav.cache;

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.CHECKEDIN;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.GETLASTMODIFIED;
import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.HREF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.jcr.NamespaceRegistry;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.observation.ObservationManager;
import javax.xml.namespace.QName;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.storage.jcr.webdav.JcrWebDavService;
import org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.dao.WebDavItemDao;
import org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.entity.WebDavItemEntity;
import org.exoplatform.documents.storage.jcr.webdav.cache.elasticsearch.entity.WebDavItemPropertyEntity;
import org.exoplatform.documents.storage.jcr.webdav.cache.listener.WebDavCacheUpdaterAction;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavReadCommandHandler;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavWriteCommandHandler;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.jcr.config.ContainerEntry;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.config.WorkspaceEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.impl.RepositoryContainer;
import org.exoplatform.services.jcr.impl.RepositoryServiceImpl;
import org.exoplatform.services.jcr.impl.WorkspaceContainer;

import lombok.SneakyThrows;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CachedJcrWebDavServiceTest {

  private static final String       FILE_PATH       = "/test";                         // NOSONAR

  /**
   * A Space drive segment as emitted by the WebDAV handler: the Space pretty
   * name, then the identity id between percent-encoded parentheses.
   */
  private static final String       DRIVE_PATH      = "/one27_two27_three_1%20%2828%29";

  private static final String       DRIVES_BASE_URI = "https://exo.test/webdav/drives";

  private static final String       DRIVE_BASE_URI  = "https://exo.test/webdav/drives/d";

  private static final String       USERNAME        = "user";

  private static final String       WS_NAME         = "test";

  private static final String       REPOSITORY_NAME = JcrWebDavService.REPOSITORY_NAME;

  @Mock
  private WebdavReadCommandHandler  readCommandHandler;

  @Mock
  private WebdavWriteCommandHandler writeCommandHandler;

  @Mock
  private RepositoryServiceImpl     repositoryService;

  @Mock
  private UserACL                   userAcl;

  @Mock
  private WebDavItemDao             webDavItemRepository;

  @Mock
  private ManageableRepository      repository;

  @Mock
  private RepositoryEntry           repositoryEntry;

  @Mock
  private WorkspaceEntry            workspaceEntry;

  @Mock
  private Session                   session;

  @Mock
  private Workspace                 workspace;

  @Mock
  private NamespaceRegistry         nsRegistry;

  @Mock
  private ObservationManager        observationManager;

  @Mock
  private RepositoryContainer       repositoryContainer;

  @Mock
  private WorkspaceContainer        workspaceContainer;

  @Mock
  private ContainerEntry            containerEntry;

  @InjectMocks
  private CachedJcrWebDavService    service;

  @Before
  @SneakyThrows
  public void setup() {
    when(repositoryService.getDefaultRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn(WS_NAME);
    when(repository.getSystemSession(WS_NAME)).thenReturn(session);
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getObservationManager()).thenReturn(observationManager);
    when(repositoryService.getRepositoryContainer(REPOSITORY_NAME)).thenReturn(repositoryContainer);
    when(repositoryContainer.getWorkspaceContainer(WS_NAME)).thenReturn(workspaceContainer);
    when(repository.getSystemSession(anyString())).thenReturn(session);
    when(repository.getNamespaceRegistry()).thenReturn(nsRegistry);
    when(nsRegistry.getPrefixes()).thenReturn(new String[] { "pfx" });
    when(nsRegistry.getURI("pfx")).thenReturn("uri");
    when(workspaceContainer.getComponentInstanceOfType(WorkspaceEntry.class, false)).thenReturn(workspaceEntry);
    when(workspaceEntry.getContainer()).thenReturn(containerEntry);
    service = Mockito.spy(service);
    doReturn(session).when(service).newSession(anyString(), any(), any());
    when(webDavItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, WebDavItemEntity.class));
  }

  @Test
  @SneakyThrows
  public void testInit() {
    service.init();
    verify(webDavItemRepository).deleteAll();
    for (String path : WebDavCacheUpdaterAction.SUPPORTED_PATHS) {
      verify(observationManager).addEventListener(any(WebDavCacheUpdaterAction.class),
                                                  eq(WebDavCacheUpdaterAction.SUPPORTED_EVENT_TYPES),
                                                  eq(path),
                                                  eq(true),
                                                  eq(null),
                                                  eq(WebDavCacheUpdaterAction.SUPPORTED_NODE_TYPES.toArray(String[]::new)),
                                                  eq(false));
    }
  }

  @Test
  @SneakyThrows
  public void testIsFileWhenPathIsBlankShouldReturnFalse() {
    assertFalse(service.isFile(null));
    assertFalse(service.isFile(""));
    assertFalse(service.isFile("/"));
  }

  @Test
  @SneakyThrows
  public void testIsFileWhenEntityExistsShouldReturnEntityValue() {
    WebDavItemEntity entity = mock(WebDavItemEntity.class);
    when(entity.isFile()).thenReturn(true);
    when(webDavItemRepository.findById(FILE_PATH)).thenReturn(Optional.of(entity));

    boolean result = service.isFile(FILE_PATH);

    assertTrue(result);
    verify(webDavItemRepository).findById(FILE_PATH);
  }

  @Test
  @SneakyThrows
  public void testGetLastModifiedDateWhenEntityHasProperty() {
    WebDavItemPropertyEntity property = new WebDavItemPropertyEntity();
    property.setName(GETLASTMODIFIED.getNamespaceURI() + ":" + GETLASTMODIFIED.getLocalPart());
    property.setValue("Thu, 01 Jan 2025 00:00:00 GMT");

    WebDavItemEntity entity = new WebDavItemEntity();
    entity.setProperties(Collections.singletonList(property));

    when(webDavItemRepository.findById(FILE_PATH)).thenReturn(Optional.of(entity));

    long result = service.getLastModifiedDate(FILE_PATH, "1");

    assertTrue(result > 0);
  }

  @Test
  @SneakyThrows
  public void testGetLastModifiedDateWhenPathIsRootShouldReturnZero() {
    long result = service.getLastModifiedDate("/", "1");
    assertEquals(0L, result);
  }

  @Test
  @SneakyThrows
  public void testClearCacheWhenEntityExistsAndDropTrueShouldDelete() {
    WebDavItemEntity entity = new WebDavItemEntity();
    entity.setModified(false);

    when(webDavItemRepository.findAllByJcrPath(FILE_PATH)).thenReturn(List.of(entity));

    service.clearCache(FILE_PATH, true);

    verify(webDavItemRepository).deleteAll(List.of(entity));
  }

  @Test
  @SneakyThrows
  public void testClearCacheWhenEntityExistsAndDropFalseShouldMarkModified() {
    WebDavItemEntity entity = new WebDavItemEntity();
    entity.setModified(false);
    when(webDavItemRepository.findAllByJcrPath(FILE_PATH)).thenReturn(List.of(entity));

    service.clearCache(FILE_PATH, false);

    assertTrue(entity.isModified());
    verify(webDavItemRepository).save(entity);
  }

  @Test
  @SneakyThrows
  public void testGetWhenCacheIsEmptyShouldCallSuper() {
    when(webDavItemRepository.findById(FILE_PATH)).thenReturn(Optional.empty());

    WebDavItem expectedItem = new WebDavItem();
    expectedItem.setWebDavPath(FILE_PATH);
    when(readCommandHandler.get(any(),
                                any(),
                                any(),
                                anyBoolean(),
                                anyInt(),
                                any(),
                                any())).thenReturn(expectedItem);

    WebDavItem result = service.get(FILE_PATH, "all", Collections.<QName> emptySet(), false, 5, "", "user");

    assertNotNull(result);
    assertEquals(FILE_PATH, result.getWebDavPath());
  }

  /**
   * EXO-89613 — a cache row is keyed by the drive-relative WebDAV path alone,
   * so the same row answers the drive-list mount and the single-drive mount,
   * whose base URIs differ. The href it returns must follow the base URI of the
   * request being answered, never the one that happened to populate the row: a
   * client that receives an href it did not ask for discards the whole
   * multistatus response and the mount fails.
   */
  @Test
  @SneakyThrows
  public void testGetFromCacheShouldBuildIdentifierFromRequestBaseUriInBothMountModes() {
    WebDavItemEntity entity = cachedDriveEntry();
    when(webDavItemRepository.findById(DRIVE_PATH)).thenReturn(Optional.of(entity));

    WebDavItem fromDrivesList = service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVES_BASE_URI, USERNAME);
    WebDavItem fromSingleDrive = service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, USERNAME);

    assertEquals(new URI(DRIVES_BASE_URI + DRIVE_PATH), fromDrivesList.getIdentifier());
    assertEquals(new URI(DRIVE_BASE_URI + DRIVE_PATH), fromSingleDrive.getIdentifier());
    // both answers came from the one cached row, not from a JCR read
    verify(readCommandHandler, never()).get(any(), any(), any(), anyBoolean(), anyInt(), any(), any());
  }

  /**
   * DAV:checked-in carries the same absolute href as the response's own
   * D:href — it is rebuilt from the same base URI.
   */
  @Test
  @SneakyThrows
  public void testGetFromCacheShouldRebuildCheckedInHrefFromRequestBaseUri() {
    WebDavItemEntity entity = cachedDriveEntry();
    when(webDavItemRepository.findById(DRIVE_PATH)).thenReturn(Optional.of(entity));

    WebDavItem result = service.get(DRIVE_PATH, "allprop", null, false, 0, DRIVE_BASE_URI, USERNAME);

    WebDavItemProperty checkedIn = result.getProperty(CHECKEDIN);
    assertNotNull(checkedIn);
    assertEquals(DRIVE_BASE_URI + DRIVE_PATH, checkedIn.getChild(HREF).getValue());
  }

  /**
   * The children a Depth:1 PROPFIND returns are served from their own cache
   * rows and get the same treatment.
   */
  @Test
  @SneakyThrows
  public void testGetChildrenFromCacheShouldBuildIdentifierFromRequestBaseUri() {
    String childPath = DRIVE_PATH + "/folder%20one";
    WebDavItemEntity entity = cachedDriveEntry();
    entity.setDeep(true);
    WebDavItemEntity childEntity = new WebDavItemEntity();
    childEntity.setWebDavPath(childPath);
    childEntity.setUsernames(Set.of(USERNAME));

    when(webDavItemRepository.findById(DRIVE_PATH)).thenReturn(Optional.of(entity));
    when(webDavItemRepository.findByParentWebDavPath(DRIVE_PATH)).thenReturn(List.of(childEntity));

    WebDavItem result = service.get(DRIVE_PATH, "allprop", null, false, 1, DRIVE_BASE_URI, USERNAME);

    assertEquals(1, result.getChildren().size());
    assertEquals(new URI(DRIVE_BASE_URI + childPath), result.getChildren().get(0).getIdentifier());
    verify(readCommandHandler, never()).get(any(), any(), any(), anyBoolean(), anyInt(), any(), any());
  }

  private WebDavItemEntity cachedDriveEntry() {
    WebDavItemProperty checkedIn = new WebDavItemProperty(CHECKEDIN);
    checkedIn.addChild(new WebDavItemProperty(HREF)).setValue(DRIVES_BASE_URI + DRIVE_PATH);

    WebDavItemEntity entity = new WebDavItemEntity();
    entity.setWebDavPath(DRIVE_PATH);
    entity.setJcrPath("/Groups/spaces/one27_two27_three_1/Documents");
    entity.setUsernames(Set.of(USERNAME));
    entity.setProperties(List.of(new WebDavItemPropertyEntity(checkedIn)));
    return entity;
  }

}
