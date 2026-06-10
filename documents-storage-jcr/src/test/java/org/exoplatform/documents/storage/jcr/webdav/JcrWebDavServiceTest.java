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
package org.exoplatform.documents.storage.jcr.webdav;

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.DISPLAYNAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.lock.Lock;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.storage.jcr.webdav.model.JcrNamespaceContext;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavReadCommandHandler;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavWriteCommandHandler;
import org.exoplatform.documents.webdav.model.WebDavFileDownload;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.model.WebDavLockResponse;
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
public class JcrWebDavServiceTest {

  private static final String       REPOSITORY_NAME          = JcrWebDavService.REPOSITORY_NAME;

  private static final List<String> LOCK_TOKENS              = Collections.singletonList("lockToken");

  private static final String       USERNAME                 = "username";

  private static final String       BASE_URI                 = "baseUri";

  private static final Set<QName>   REQUESTED_PROPERTY_NAMES = Collections.singleton(DISPLAYNAME);

  private static final String       PROP_REQUEST_TYPE        = "include";

  private static final String       QUERY_LANGUAGE           = "queryLanguage";

  private static final String       QUERY                    = "query";

  private static final String       FILE_VERSION             = "v1";

  private static final String       WEBDAV_PATH              = "/path/to/file";                       // NOSONAR

  private static final String       LOCKED                   = "/locked";

  private static final String       WS_NAME                  = "test";

  @Mock
  private WebdavReadCommandHandler  readCommandHandler;

  @Mock
  private WebdavWriteCommandHandler writeCommandHandler;

  @Mock
  private RepositoryServiceImpl     repositoryService;

  @Mock
  private UserACL                   userACL;

  @Mock
  private Session                   session;

  @Mock
  private NamespaceRegistry         nsRegistry;

  @Mock
  private Node                      node;

  @Mock
  private Lock                      lock;

  @InjectMocks
  private JcrWebDavService          service;

  @Mock
  private ManageableRepository      repository;

  @Mock
  private RepositoryContainer       repositoryContainer;

  @Mock
  private WorkspaceContainer        workspaceContainer;

  @Mock
  private ContainerEntry            containerEntry;

  @Mock
  private RepositoryEntry           repositoryEntry;

  @Mock
  private WorkspaceEntry            workspaceEntry;

  @Mock
  private Workspace                 workspace;

  @Before
  @SneakyThrows
  public void setUp() {
    when(repositoryService.getDefaultRepository()).thenReturn(repository);
    when(repositoryService.getRepositoryContainer(REPOSITORY_NAME)).thenReturn(repositoryContainer);
    when(repositoryContainer.getWorkspaceContainer(WS_NAME)).thenReturn(workspaceContainer);
    when(repository.getSystemSession(anyString())).thenReturn(session);
    when(repository.getNamespaceRegistry()).thenReturn(nsRegistry);
    when(nsRegistry.getPrefixes()).thenReturn(new String[] { "pfx" });
    when(nsRegistry.getURI("pfx")).thenReturn("uri");
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn(WS_NAME);
    when(repository.getSystemSession(WS_NAME)).thenReturn(session);
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspaceContainer.getComponentInstanceOfType(WorkspaceEntry.class, false)).thenReturn(workspaceEntry);
    when(workspaceEntry.getContainer()).thenReturn(containerEntry);
    service = Mockito.spy(service);
    doReturn(session).when(service).newSession(anyString(), any(), any());
  }

  @Test
  public void testGetDaslValue() {
    assertEquals(JcrWebDavService.DAS_VALUE, service.getDaslValue());
  }

  @Test
  @SneakyThrows
  public void testGetLastModifiedDate() {
    long lastModifiedDate = service.getLastModifiedDate(WEBDAV_PATH, FILE_VERSION);
    assertEquals(0l, lastModifiedDate);
    verify(readCommandHandler).getLastModifiedDate(any(Session.class), eq(WEBDAV_PATH), eq(FILE_VERSION));
  }

  @Test
  @SneakyThrows
  public void testGet() {
    when(readCommandHandler.get(any(), any(), any(), anyBoolean(), anyInt(), any(), any())).thenReturn(mock(WebDavItem.class));
    WebDavItem webDavItem = service.get(WEBDAV_PATH,
                                        PROP_REQUEST_TYPE,
                                        REQUESTED_PROPERTY_NAMES,
                                        true,
                                        3,
                                        BASE_URI,
                                        USERNAME);
    assertNotNull(webDavItem);
    verify(readCommandHandler).get(any(Session.class),
                                   eq(WEBDAV_PATH),
                                   eq(REQUESTED_PROPERTY_NAMES),
                                   eq(true),
                                   eq(3),
                                   eq(BASE_URI),
                                   eq(USERNAME));
  }

  @Test
  @SneakyThrows
  public void testSearch() {
    when(readCommandHandler.search(any(),
                                   any(),
                                   any(),
                                   any(),
                                   any())).thenReturn(Collections.emptyList());
    List<WebDavItem> webDavItems = service.search(WEBDAV_PATH,
                                                  QUERY_LANGUAGE,
                                                  QUERY,
                                                  BASE_URI,
                                                  USERNAME);
    assertNotNull(webDavItems);
    verify(readCommandHandler).search(any(Session.class),
                                      eq(QUERY_LANGUAGE),
                                      eq(QUERY),
                                      eq(BASE_URI),
                                      eq(USERNAME));
  }

  @Test
  @SneakyThrows
  public void testGetVersions() {
    when(readCommandHandler.getVersions(any(),
                                        any(),
                                        any(),
                                        any())).thenReturn(Collections.emptyList());
    List<WebDavItem> webDavItems = service.getVersions(WEBDAV_PATH,
                                                       REQUESTED_PROPERTY_NAMES,
                                                       BASE_URI,
                                                       USERNAME);
    assertNotNull(webDavItems);
    verify(readCommandHandler).getVersions(any(Session.class),
                                           eq(WEBDAV_PATH),
                                           eq(REQUESTED_PROPERTY_NAMES),
                                           eq(BASE_URI));
  }

  @Test
  @SneakyThrows
  public void testCheckin() {
    service.checkin(WEBDAV_PATH,
                    LOCK_TOKENS,
                    USERNAME);
    verify(writeCommandHandler).checkin(any(Session.class),
                                        eq(WEBDAV_PATH));
  }

  @Test
  @SneakyThrows
  public void testCheckout() {
    service.checkout(WEBDAV_PATH,
                     LOCK_TOKENS,
                     USERNAME);
    verify(writeCommandHandler).checkout(any(Session.class),
                                         eq(WEBDAV_PATH));
  }

  @Test
  @SneakyThrows
  public void testUncheckout() {
    service.uncheckout(WEBDAV_PATH,
                       LOCK_TOKENS,
                       USERNAME);
    verify(writeCommandHandler).uncheckout(any(Session.class),
                                           eq(WEBDAV_PATH));
  }

  @Test
  @SneakyThrows
  public void testUnlock() {
    service.unlock(WEBDAV_PATH,
                   LOCK_TOKENS,
                   USERNAME);
    verify(writeCommandHandler).unlock(any(Session.class),
                                       eq(WEBDAV_PATH),
                                       eq(LOCK_TOKENS));
  }

  @Test
  public void testGetNamespaceContextBuildsAndCaches() {
    NamespaceContext ctx1 = service.getNamespaceContext();
    NamespaceContext ctx2 = service.getNamespaceContext();
    assertTrue(ctx1 instanceof JcrNamespaceContext);
    assertSame(ctx1, ctx2); // cached
  }

  @Test
  public void testIsFileDelegatesAndClosesSession() {
    when(readCommandHandler.isFile(session, "/a")).thenReturn(true);
    assertTrue(service.isFile("/a"));
    verify(session).logout();
  }

  @Test
  @SneakyThrows
  public void testDownloadDelegates() {
    WebDavFileDownload d = new WebDavFileDownload(null, 0, 0, null, null);
    when(readCommandHandler.download(session, "/a", FILE_VERSION)).thenReturn(d);
    assertEquals(d, service.download("/a", FILE_VERSION, "", "user"));
    verify(session).logout();
  }

  @Test
  @SneakyThrows
  public void testCreateFolderChecksLockAndDelegates() {
    service.createFolder("/a", "", "", "", Collections.emptyList(), "user");
    verify(writeCommandHandler).createFolder(eq(session), eq("/a"), any());
  }

  @Test
  @SneakyThrows
  public void testSaveFileChecksLockAndDelegates() {
    InputStream is = mock(InputStream.class);
    service.saveFile("/a", "", "", "", "", is, Collections.emptyList(), "user");
    verify(writeCommandHandler).saveFile(eq(session), eq("/a"), anyString(), any(), eq(is));
  }

  @Test
  @SneakyThrows
  public void testDeleteChecksLockAndDelegates() {
    service.delete("/a", Collections.emptyList(), "user");
    verify(writeCommandHandler).delete(session, "/a");
  }

  @Test
  @SneakyThrows
  public void testMoveChecksLockAndDelegates() {
    when(writeCommandHandler.move(session, "/a", "/b", true)).thenReturn(true);
    assertTrue(service.move("/a", "/b", true, Collections.emptyList(), "user"));
  }

  @Test
  @SneakyThrows
  public void testCopyChecksLockAndDelegates() {
    service.copy("/a", "/b", 1, true, true, null, Collections.emptyList(), "user");
    verify(writeCommandHandler).copy(session, "/a", "/b", true, true);
  }

  @Test
  @SneakyThrows
  public void testSavePropertiesChecksLockAndDelegates() {
    Map<String, Collection<WebDavItemProperty>> result = new HashMap<>();
    when(writeCommandHandler.saveProperties(session, "/a", null, null)).thenReturn(result);
    assertEquals(result, service.saveProperties("/a", null, null, Collections.emptyList(), "user"));
  }

  @Test
  @SneakyThrows
  public void testEnableVersioningChecksLockAndDelegates() {
    service.enableVersioning("/a", Collections.emptyList(), "user");
    verify(writeCommandHandler).enableVersioning(session, "/a");
  }

  @Test
  @SneakyThrows
  public void testLockChecksLockAndDelegates() {
    WebDavLockResponse resp = new WebDavLockResponse("", "");
    when(writeCommandHandler.lock(session, "/a", 0, 0, true, "user")).thenReturn(resp);
    assertEquals(resp, service.lock("/a", 0, 0, true, Collections.emptyList(), "user"));
  }

  @Test
  @SneakyThrows
  public void testOrderChecksLockAndDelegates() {
    when(writeCommandHandler.order(session, "/a", Collections.emptyList())).thenReturn(true);
    assertTrue(service.order("/a", Collections.emptyList(), Collections.emptyList(), "user"));
  }

  @Test
  public void testUnlockTimedOutNodesWithItems() {
    when(writeCommandHandler.getOutdatedLockedNodePaths()).thenReturn(Arrays.asList("/p1", "/p2"));
    service.unlockTimedOutNodes();
    verify(writeCommandHandler, atLeastOnce()).unlockNode(eq(session), anyString());
  }

  @Test
  @SneakyThrows
  public void testCheckLockThrowsWhenLockedByOther() {
    when(session.itemExists(LOCKED)).thenReturn(true);
    when(session.getItem(LOCKED)).thenReturn(node);
    when(node.isLocked()).thenReturn(true);
    when(node.getLock()).thenReturn(lock);
    when(lock.getLockToken()).thenReturn("t1");
    when(lock.getLockOwner()).thenReturn("owner");
    List<String> tokens = Collections.singletonList("other");
    try {
      service.getClass() // NOSONAR
             .getDeclaredMethod("checkLock", Session.class, String.class, List.class)
             .setAccessible(true);
      service.getClass()
             .getDeclaredMethod("checkLock", Session.class, String.class, List.class)
             .invoke(service, session, LOCKED, tokens);
      fail("Expected WebDavException");
    } catch (Exception e) {
      // expected
    }
  }
}
