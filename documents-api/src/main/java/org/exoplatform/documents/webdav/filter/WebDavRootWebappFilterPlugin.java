/**
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.documents.webdav.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.apache.http.HttpHeaders;
import org.springframework.stereotype.Service;

import io.meeds.web.security.plugin.RootWebappFilterPlugin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class WebDavRootWebappFilterPlugin implements RootWebappFilterPlugin {

  private static final Predicate<String>       WINDOWS_MINIREDIR =
                                                                 Pattern.compile("^Microsoft-WebDAV-MiniRedir/[0-9.]+$")
                                                                        .asMatchPredicate();

  private static final Predicate<String>       LINUX_CADAVER     =
                                                             Pattern.compile("^cadaver/[0-9.]+ neon/[0-9.]+$").asMatchPredicate();

  private static final Predicate<String>       LINUX_DAVFS2      =
                                                            Pattern.compile("^davfs2/[0-9.]+ neon/[0-9.]+$").asMatchPredicate();

  private static final Predicate<String>       LINUX_GVFS        =
                                                          Pattern.compile("^gvfs/[0-9.]+$").asMatchPredicate();

  private static final Predicate<String>       LINUX_CURL        =
                                                          Pattern.compile("^curl/[0-9.]+.*").asMatchPredicate();

  private static final Predicate<String>       MACOS_WEBDAVFS    =
                                                              Pattern.compile("^WebDAVFS/[0-9.]+ .*Darwin/[0-9.]+.*")
                                                                     .asMatchPredicate();

  private static final Predicate<String>       MACOS_CYBERDUCK   =
                                                               Pattern.compile("^Cyberduck/[0-9.]+ \\(Mac OS X/[0-9.]+\\).*")
                                                                      .asMatchPredicate();

  private static final Predicate<String>       MACOS_TRANSMIT    =
                                                              Pattern.compile("^Transmit/[0-9.]+ \\(Mac OS X/[0-9.]+\\).*")
                                                                     .asMatchPredicate();

  private static final List<Predicate<String>> WEBDAV_PREDICATES = Arrays.asList(WINDOWS_MINIREDIR,
                                                                                 LINUX_CADAVER,
                                                                                 LINUX_DAVFS2,
                                                                                 LINUX_GVFS,
                                                                                 LINUX_CURL,
                                                                                 MACOS_WEBDAVFS,
                                                                                 MACOS_CYBERDUCK,
                                                                                 MACOS_TRANSMIT);

  @Override
  public boolean matches(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    return httpRequest.getRequestURI().equals("/")
           && isWebDav(httpRequest.getHeader(HttpHeaders.USER_AGENT));
  }

  @Override
  public void doFilter(HttpServletRequest httpRequest, HttpServletResponse httpResponse, FilterChain chain) throws IOException,
                                                                                                            ServletException {
    httpResponse.sendRedirect("/webdav/drives");
  }

  private boolean isWebDav(String userAgent) {
    if (userAgent == null) {
      return false;
    }
    return WEBDAV_PREDICATES.stream()
                            .anyMatch(p -> p.test(userAgent));
  }

}
