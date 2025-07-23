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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.Session;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.documents.webdav.model.WebDavFileDownload;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

import lombok.SneakyThrows;

@RunWith(MockitoJUnitRunner.Silent.class)
public class WebdavReadCommandHandlerTest {

  private static final String      WEBDAV_PATH = "/webdav/path";

  @Mock
  private IdentityManager          identityManager;

  @Mock
  private SpaceService             spaceService;

  @Mock
  private PathCommandHandler       pathCommandHandler;

  @Mock
  private Session                  session;

  @Mock
  private Node                     node;

  @Mock
  private Node                     contentNode;

  @Mock
  private Property                 property;

  @Mock
  private Version                  version;

  @Mock
  private VersionHistory           versionHistory;

  @Mock
  private VersionIterator          versionIterator;

  @Mock
  private NodeIterator             nodeIterator;

  @Mock
  private Identity                 identity;

  @Mock
  private Profile                  profile;

  private WebdavReadCommandHandler handler;

  @Before
  @SneakyThrows
  public void setUp() {
    handler = new WebdavReadCommandHandler(identityManager, spaceService, pathCommandHandler);

    when(pathCommandHandler.transformToJcrPath(anyString())).thenReturn("/jcr/path");
    when(session.itemExists(anyString())).thenReturn(true);
    when(session.getItem("/jcr/path")).thenReturn(node);

    when(node.isNodeType(anyString())).thenReturn(false); // default
    when(node.getNode("jcr:content")).thenReturn(contentNode);
    when(contentNode.hasProperty(anyString())).thenReturn(true);
    when(contentNode.getProperty(anyString())).thenReturn(property);
    when(property.getString()).thenReturn("text/plain");
    when(property.getStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));
    when(property.getLength()).thenReturn(4L);

    Calendar cal = Calendar.getInstance();
    when(contentNode.getProperty("jcr:lastModified")).thenReturn(property);
    when(property.getDate()).thenReturn(cal);

    when(identity.getProfile()).thenReturn(profile);
    when(profile.getFullName()).thenReturn("John Doe");
    when(identity.getIdentityId()).thenReturn(1L);
    when(identityManager.getIdentity(anyLong())).thenReturn(identity);
  }

  @Test
  @SneakyThrows
  public void testIsFileTrue() {
    when(node.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
    assertTrue(handler.isFile(session, WEBDAV_PATH));
  }

  @Test
  @SneakyThrows
  public void testIsFileFalse() {
    when(node.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(false);
    assertFalse(handler.isFile(session, WEBDAV_PATH));
  }

  @Test
  @SneakyThrows
  public void testGetLastModifiedDate() {
    when(node.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
    long ts = handler.getLastModifiedDate(session, WEBDAV_PATH, null);
    assertTrue(ts > 0);
  }

  @Test
  @SneakyThrows
  public void testDownload() {
    when(node.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
    WebDavFileDownload dl = handler.download(session, WEBDAV_PATH, null);
    assertEquals("text/plain", dl.getMimeType());
    assertNotNull(dl.getInputStream());
  }

  @Test
  @SneakyThrows
  public void testGetVersionsEmptyWhenNotVersionable() {
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(false);
    List<WebDavItem> result = handler.getVersions(session, WEBDAV_PATH, Collections.emptySet(), "baseUri");
    assertTrue(result.isEmpty());
  }

  @Test
  @SneakyThrows
  public void testGetVersionsWithVersionableNode() {
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(true);
    when(node.getVersionHistory()).thenReturn(versionHistory);
    when(versionHistory.getAllVersions()).thenReturn(versionIterator);
    when(versionIterator.hasNext()).thenReturn(false); // no versions
    List<WebDavItem> result = handler.getVersions(session, WEBDAV_PATH, Collections.emptySet(), "baseUri");
    assertNotNull(result);
  }
}
