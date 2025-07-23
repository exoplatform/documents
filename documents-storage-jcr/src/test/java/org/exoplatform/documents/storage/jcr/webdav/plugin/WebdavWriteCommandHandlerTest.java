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
package org.exoplatform.documents.storage.jcr.webdav.plugin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.lock.Lock;
import javax.jcr.version.Version;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;

import lombok.SneakyThrows;

@RunWith(MockitoJUnitRunner.Silent.class)
public class WebdavWriteCommandHandlerTest {

  private static final String       JCR_PATH = "/jcr/path";

  @Mock
  private RepositoryService         repositoryService;

  @Mock
  private SessionProviderService    sessionProviderService;

  @Mock
  private PathCommandHandler        pathCommandHandler;

  @Mock
  private Session                   session;

  @Mock
  private Node                      node;

  @Mock
  private Node                      destNode;

  @Mock
  private Lock                      lock;

  @Mock
  private Version                   version;

  @InjectMocks
  private WebdavWriteCommandHandler handler;

  @Before
  @SneakyThrows
  public void setUp() {
    // Common stubs
    when(pathCommandHandler.transformToJcrPath(anyString())).thenReturn(JCR_PATH);
    when(session.itemExists(anyString())).thenReturn(true);
    when(session.getItem(JCR_PATH)).thenReturn(node);
    when(node.getSession()).thenReturn(session);
    when(node.getPath()).thenReturn(JCR_PATH);
  }

  @Test
  @SneakyThrows
  public void testSaveFile() {
    when(node.addNode("file", "nt:file")).thenReturn(destNode);
    when(destNode.addNode("jcr:content", "nt:resource")).thenReturn(destNode);
    when(destNode.setProperty(eq("jcr:data"), any(InputStream.class))).thenReturn(null);
    handler.saveFile(session, JCR_PATH, "text/plain", Collections.emptyList(), new ByteArrayInputStream("data".getBytes()));
    verify(session, atLeastOnce()).save();
  }

  @Test
  @SneakyThrows
  public void testDelete() {
    handler.delete(session, JCR_PATH);
    verify(node).remove();
    verify(session).save();
  }

  @Test
  @SneakyThrows
  public void testEnableVersioning() {
    when(node.isNodeType("mix:versionable")).thenReturn(false);
    handler.enableVersioning(session, JCR_PATH);
    verify(node).addMixin("mix:versionable");
    verify(session).save();
  }
}
