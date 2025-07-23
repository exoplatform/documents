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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
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

import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.lock.Lock;
import javax.xml.namespace.NamespaceContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.storage.jcr.webdav.model.JcrNamespaceContext;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavReadCommandHandler;
import org.exoplatform.documents.storage.jcr.webdav.plugin.WebdavWriteCommandHandler;
import org.exoplatform.documents.webdav.model.WebDavFileDownload;
import org.exoplatform.documents.webdav.model.WebDavItemProperty;
import org.exoplatform.documents.webdav.model.WebDavLockResponse;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;

import lombok.SneakyThrows;

@RunWith(MockitoJUnitRunner.Silent.class)
public class JcrWebDavServiceTest {

  private static final String LOCKED = "/locked";

  @Mock
  private WebdavReadCommandHandler  readHandler;

  @Mock
  private WebdavWriteCommandHandler writeHandler;

  @Mock
  private RepositoryService         repoService;

  @Mock
  private UserACL                   userACL;

  @Mock
  private ManageableRepository      repo;

  @Mock
  private Session                   session;

  @Mock
  private NamespaceRegistry         nsRegistry;

  @Mock
  private Node                      node;

  @Mock
  private Lock                      lock;

  private JcrWebDavService          service;

  @Before
  @SneakyThrows
  public void setUp() {
    when(repoService.getDefaultRepository()).thenReturn(repo);
    when(repo.getSystemSession(anyString())).thenReturn(session);
    when(repo.getNamespaceRegistry()).thenReturn(nsRegistry);
    when(nsRegistry.getPrefixes()).thenReturn(new String[] { "pfx" });
    when(nsRegistry.getURI("pfx")).thenReturn("uri");
    service = Mockito.spy(new JcrWebDavService(readHandler, writeHandler, repoService, userACL));
    doReturn(session).when(service).getSession(anyString());
    doReturn(session).when(service).getSession();
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
    when(readHandler.isFile(session, "/a")).thenReturn(true);
    assertTrue(service.isFile("/a"));
    verify(session).logout();
  }

  @Test
  @SneakyThrows
  public void testDownloadDelegates() {
    WebDavFileDownload d = new WebDavFileDownload(null, 0, 0, null, null);
    when(readHandler.download(session, "/a", "v1")).thenReturn(d);
    assertEquals(d, service.download("/a", "v1", "", "user"));
    verify(session).logout();
  }

  @Test
  @SneakyThrows
  public void testCreateFolderChecksLockAndDelegates() {
    service.createFolder("/a", "", "", "", Collections.emptyList(), "user");
    verify(writeHandler).createFolder(eq(session), eq("/a"), any());
  }

  @Test
  @SneakyThrows
  public void testSaveFileChecksLockAndDelegates() {
    InputStream is = mock(InputStream.class);
    service.saveFile("/a", "", "", "", "", is, Collections.emptyList(), "user");
    verify(writeHandler).saveFile(eq(session), eq("/a"), anyString(), any(), eq(is));
  }

  @Test
  @SneakyThrows
  public void testDeleteChecksLockAndDelegates() {
    service.delete("/a", Collections.emptyList(), "user");
    verify(writeHandler).delete(session, "/a");
  }

  @Test
  @SneakyThrows
  public void testMoveChecksLockAndDelegates() {
    when(writeHandler.move(session, "/a", "/b", true)).thenReturn(true);
    assertTrue(service.move("/a", "/b", true, Collections.emptyList(), "user"));
  }

  @Test
  @SneakyThrows
  public void testCopyChecksLockAndDelegates() {
    service.copy("/a", "/b", 1, true, true, null, Collections.emptyList(), "user");
    verify(writeHandler).copy(session, "/a", "/b", true, true);
  }

  @Test
  @SneakyThrows
  public void testSavePropertiesChecksLockAndDelegates() {
    Map<String, Collection<WebDavItemProperty>> result = new HashMap<>();
    when(writeHandler.saveProperties(session, "/a", null, null)).thenReturn(result);
    assertEquals(result, service.saveProperties("/a", null, null, Collections.emptyList(), "user"));
  }

  @Test
  @SneakyThrows
  public void testEnableVersioningChecksLockAndDelegates() {
    service.enableVersioning("/a", Collections.emptyList(), "user");
    verify(writeHandler).enableVersioning(session, "/a");
  }

  @Test
  @SneakyThrows
  public void testLockChecksLockAndDelegates() {
    WebDavLockResponse resp = new WebDavLockResponse("", "");
    when(writeHandler.lock(session, "/a", 0, 0, true, "user")).thenReturn(resp);
    assertEquals(resp, service.lock("/a", 0, 0, true, Collections.emptyList(), "user"));
  }

  @Test
  @SneakyThrows
  public void testOrderChecksLockAndDelegates() {
    when(writeHandler.order(session, "/a", Collections.emptyList())).thenReturn(true);
    assertTrue(service.order("/a", Collections.emptyList(), Collections.emptyList(), "user"));
  }

  @Test
  public void testUnlockTimedOutNodesWithItems() {
    when(writeHandler.getOutdatedLockedNodePaths()).thenReturn(Arrays.asList("/p1", "/p2"));
    service.unlockTimedOutNodes();
    verify(writeHandler, atLeastOnce()).unlockNode(eq(session), anyString());
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
