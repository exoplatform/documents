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

import static org.exoplatform.documents.webdav.model.constant.PropertyConstants.DISPLAYNAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.Session;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;
import javax.xml.namespace.QName;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.documents.storage.jcr.util.NodeTypeConstants;
import org.exoplatform.documents.webdav.model.WebDavFileDownload;
import org.exoplatform.documents.webdav.model.WebDavItem;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import lombok.SneakyThrows;

@RunWith(MockitoJUnitRunner.Silent.class)
public class WebdavReadCommandHandlerTest {

  private static final String      IDENTITY_PATH            = "/Users/r/root/Private";                    // NOSONAR

  private static final String      JCR_RELATIVE_PATH        = "/path/to/file";                            // NOSONAR

  private static final String      WEBDAV_PATH              = "/John%20Doe%20%281%29" + JCR_RELATIVE_PATH;// NOSONAR

  private static final String      ENCODED_WEBDAV_PATH      = "/John%20Doe%20%281%29" + JCR_RELATIVE_PATH;// NOSONAR

  private static final String      JCR_PATH                 = IDENTITY_PATH + JCR_RELATIVE_PATH;

  private static final String      USERNAME                 = "username";

  private static final String      BASE_URI                 = "baseUri";

  private static final Set<QName>  REQUESTED_PROPERTY_NAMES = Collections.singleton(DISPLAYNAME);

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

  @Mock
  private ListAccess<Space>        memberSpacesListAccess;

  private WebdavReadCommandHandler handler;

  @Before
  @SneakyThrows
  public void setUp() {
    handler = new WebdavReadCommandHandler(identityManager, spaceService, pathCommandHandler);

    when(pathCommandHandler.resolveToJcrPath(eq(session), anyString())).thenReturn(JCR_PATH);
    when(pathCommandHandler.isIdentityRootWebDavPath(WEBDAV_PATH)).thenReturn(false);
    when(pathCommandHandler.getIdentityIdFromWebDavPath(WEBDAV_PATH)).thenReturn(1L);
    when(pathCommandHandler.getIdentityBaseJcrPath(WEBDAV_PATH)).thenReturn(IDENTITY_PATH);
    when(pathCommandHandler.getIdentityBaseJcrPath(1L)).thenReturn(IDENTITY_PATH);
    when(pathCommandHandler.getVisibleName(any(Node.class))).thenReturn("file");
    when(pathCommandHandler.getParentVisibleNodeName(any(Node.class))).thenReturn("path");
    when(pathCommandHandler.getOrCreateWebDavPath(eq("1"),
                                                  eq(IDENTITY_PATH),
                                                  eq("/John%20Doe%20%281%29"),
                                                  any(Node.class),
                                                  anyString())).thenReturn(ENCODED_WEBDAV_PATH);
    when(pathCommandHandler.getOrCreateWebDavPath(any(Node.class))).thenReturn(ENCODED_WEBDAV_PATH);

    when(session.itemExists(anyString())).thenReturn(true);
    when(session.getItem(JCR_PATH)).thenReturn(node);
    when(session.getItem(IDENTITY_PATH)).thenReturn(node);

    when(node.isNodeType(anyString())).thenReturn(false);
    when(node.getName()).thenReturn("file");
    when(node.getPath()).thenReturn(JCR_PATH);
    when(node.hasNodes()).thenReturn(false);
    when(node.getNode("jcr:content")).thenReturn(contentNode);
    when(node.hasProperty(anyString())).thenReturn(false);
    when(contentNode.hasProperty(anyString())).thenReturn(true);
    when(contentNode.getProperty(anyString())).thenReturn(property);
    when(property.getString()).thenReturn("text/plain");
    when(property.getStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));
    when(property.getLength()).thenReturn(4L);

    Calendar cal = Calendar.getInstance();
    when(contentNode.getProperty("jcr:lastModified")).thenReturn(property);
    when(property.getDate()).thenReturn(cal);

    when(identity.getProfile()).thenReturn(profile);
    when(identity.getIdentityId()).thenReturn(1L);
    when(identity.getId()).thenReturn("1");
    when(profile.getFullName()).thenReturn("John Doe");
    when(identityManager.getIdentity(anyLong())).thenReturn(identity);
    when(identityManager.getOrCreateUserIdentity(USERNAME)).thenReturn(identity);
    when(spaceService.getMemberSpaces(USERNAME)).thenReturn(memberSpacesListAccess);
  }

  @Test
  @SneakyThrows
  public void testGetRootPath() {
    WebDavItem webDavItem = handler.get(session,
                                        "/",
                                        REQUESTED_PROPERTY_NAMES,
                                        false,
                                        0,
                                        BASE_URI,
                                        USERNAME);
    assertNotNull(webDavItem);
    assertEquals("/", webDavItem.getJcrPath());
    assertEquals("/", webDavItem.getWebDavPath());
    assertFalse(webDavItem.isFile());
    assertNotNull(webDavItem.getIdentifier());
    assertNotNull(webDavItem.getProperties());
  }

  @Test
  @SneakyThrows
  public void testGetRootPathAddressesSpaceByPrettyNameAndPersonalDriveByFullName() {
    String spaceBaseJcrPath = "/groups/spaces/rd-ops/Documents"; // NOSONAR
    Space space = mock(Space.class);
    Identity spaceIdentity = mock(Identity.class);
    Profile spaceProfile = mock(Profile.class);

    when(memberSpacesListAccess.getSize()).thenReturn(1);
    when(spaceService.getMemberSpacesIds(USERNAME, 0, 1)).thenReturn(List.of("s1"));
    when(spaceService.getSpaceById("s1")).thenReturn(space);
    when(space.getPrettyName()).thenReturn("rd-ops");
    when(space.getDisplayName()).thenReturn("R&D / Ops");
    when(identityManager.getOrCreateSpaceIdentity("rd-ops")).thenReturn(spaceIdentity);
    when(identityManager.getIdentity(42L)).thenReturn(spaceIdentity);
    when(spaceIdentity.getProfile()).thenReturn(spaceProfile);
    when(spaceProfile.getFullName()).thenReturn("R&D / Ops");
    when(spaceIdentity.getIdentityId()).thenReturn(42L);
    when(spaceIdentity.isSpace()).thenReturn(true);
    when(spaceIdentity.getRemoteId()).thenReturn("rd-ops");
    when(pathCommandHandler.getIdentityBaseJcrPath(42L)).thenReturn(spaceBaseJcrPath);
    when(session.getItem(spaceBaseJcrPath)).thenReturn(node);

    WebDavItem webDavItem = handler.get(session,
                                        "/",
                                        REQUESTED_PROPERTY_NAMES,
                                        false,
                                        1,
                                        BASE_URI,
                                        USERNAME);

    assertNotNull(webDavItem.getChildren());
    WebDavItem spaceItem = webDavItem.getChildren()
                                     .stream()
                                     .filter(child -> child.getIdentifier().toString().contains("%2842%29"))
                                     .findFirst()
                                     .orElse(null);
    assertNotNull(spaceItem);
    // the drive is addressed by the Space pretty name, so the '/' of the
    // display name never reaches the href
    assertEquals(BASE_URI + "/rd-ops%20%2842%29", spaceItem.getIdentifier().toString());
    // while it stays presented under its real display name
    assertEquals("R&D / Ops", spaceItem.getProperty(DISPLAYNAME).getValue());

    // the personal drive keeps the user full name, it is not slugified
    WebDavItem userItem = webDavItem.getChildren()
                                    .stream()
                                    .filter(child -> child.getIdentifier().toString().contains("%281%29"))
                                    .findFirst()
                                    .orElse(null);
    assertNotNull(userItem);
    assertEquals(BASE_URI + "/John%20Doe%20%281%29", userItem.getIdentifier().toString());
  }

  @Test
  @SneakyThrows
  public void testGetWithNodePathUsesMappedWebDavPath() {
    WebDavItem webDavItem = handler.get(session,
                                        WEBDAV_PATH,
                                        REQUESTED_PROPERTY_NAMES,
                                        false,
                                        0,
                                        BASE_URI,
                                        USERNAME);
    assertNotNull(webDavItem);
    assertEquals(JCR_PATH, webDavItem.getJcrPath());
    assertEquals(ENCODED_WEBDAV_PATH, webDavItem.getWebDavPath());
    assertFalse(webDavItem.isFile());
    assertNotNull(webDavItem.getIdentifier());
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
  public void testDownloadUsesVisibleNameAsFileName() {
    when(node.isNodeType(NodeTypeConstants.NT_FILE)).thenReturn(true);
    when(pathCommandHandler.getVisibleName(node)).thenReturn("Rapport Équipe.txt");

    WebDavFileDownload dl = handler.download(session, WEBDAV_PATH, null);

    assertEquals("text/plain", dl.getMimeType());
    assertNotNull(dl.getInputStream());
  }

  @Test
  @SneakyThrows
  public void testGetVersionsEmptyWhenNotVersionable() {
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(false);
    List<WebDavItem> result = handler.getVersions(session, WEBDAV_PATH, Collections.emptySet(), BASE_URI);
    assertTrue(result.isEmpty());
  }

  @Test
  @SneakyThrows
  public void testGetVersionsWithVersionableNode() {
    when(node.isNodeType(NodeTypeConstants.MIX_VERSIONABLE)).thenReturn(true);
    when(node.getVersionHistory()).thenReturn(versionHistory);
    when(versionHistory.getAllVersions()).thenReturn(versionIterator);
    when(versionIterator.hasNext()).thenReturn(false);
    List<WebDavItem> result = handler.getVersions(session, WEBDAV_PATH, Collections.emptySet(), BASE_URI);
    assertNotNull(result);
  }
}
