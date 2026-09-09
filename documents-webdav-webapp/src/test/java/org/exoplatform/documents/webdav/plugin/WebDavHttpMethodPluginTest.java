/**
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
package org.exoplatform.documents.webdav.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
public class WebDavHttpMethodPluginTest {

  @Mock
  private HttpServletRequest request;

  private final TestPlugin   plugin = new TestPlugin();

  /**
   * EXO-89613 — the single-drive marker is a path segment, not a substring. A
   * Space pretty name is lower-cased, so a drive-list request for a drive whose
   * name starts with 'd' used to be read as a single-drive request: the first
   * character of the drive name was eaten and the leading '/' lost, and every
   * href emitted for that drive pointed somewhere else than the client asked.
   */
  @Test
  public void testGetResourcePathOfDriveListWhoseDriveNameStartsWithTheSingleDriveMarker() {
    when(request.getRequestURI()).thenReturn("/webdav/drives/dev_team%20%2831%29");

    assertEquals("/dev_team (31)", plugin.resourcePath(request));
  }

  @Test
  public void testGetResourcePathOfDriveList() {
    when(request.getRequestURI()).thenReturn("/webdav/drives/one27_two27_three_1%20%2828%29");

    assertEquals("/one27_two27_three_1 (28)", plugin.resourcePath(request));
  }

  @Test
  public void testGetResourcePathOfSingleDrive() {
    when(request.getRequestURI()).thenReturn("/webdav/drives/d/dev_team%20%2831%29");

    assertEquals("/dev_team (31)", plugin.resourcePath(request));
  }

  @Test
  public void testGetResourcePathOfSingleDriveChild() {
    when(request.getRequestURI()).thenReturn("/webdav/drives/d/dev_team%20%2831%29/folder%20one");

    assertEquals("/dev_team (31)/folder one", plugin.resourcePath(request));
  }

  /**
   * The bare single-drive path, the only case the {@code equals} half of the
   * mount-mode test answers: read as a drive-list path it would resolve to the
   * resource {@code /d}, whose segment carries no {@code (id)} and so 404s.
   * <p>
   * This pins the helper in isolation, not an end-to-end behaviour:
   * {@code WebDavRest#handle} currently 302s this URI to {@code /webdav/drives/}
   * before any plugin runs, so the branch is unreachable through the WAR. It is
   * pinned so that relaxing that gate — it lives in another class — does not
   * silently turn the bare path into a 404.
   */
  @Test
  public void testGetResourcePathOfSingleDriveRoot() {
    when(request.getRequestURI()).thenReturn("/webdav/drives/d");

    assertEquals("/", plugin.resourcePath(request));
  }

  @Test
  public void testGetResourcePathOfDriveListRoot() {
    when(request.getRequestURI()).thenReturn("/webdav/drives/");

    assertEquals("/", plugin.resourcePath(request));
  }

  /**
   * A literal '+' in a name is not a space: a WebDAV path segment is RFC 3986,
   * not application/x-www-form-urlencoded.
   */
  @Test
  public void testGetResourcePathKeepsLiteralPlus() {
    when(request.getRequestURI()).thenReturn("/webdav/drives/d/c%2B%2B%20team%20%2812%29");

    assertEquals("/c++ team (12)", plugin.resourcePath(request));
  }

  private static class TestPlugin extends WebDavHttpMethodPlugin {

    public TestPlugin() {
      super("PROPFIND");
    }

    @Override
    public void handle(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
      throw new UnsupportedOperationException();
    }

    public String resourcePath(HttpServletRequest httpRequest) {
      return getResourcePath(httpRequest);
    }

  }

}
