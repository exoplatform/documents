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
package org.exoplatform.documents.filter;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import org.exoplatform.web.filter.Filter;
import org.exoplatform.container.PortalContainer;

import java.io.IOException;

public class RestrictedDriveFilter implements Filter {

  private static final String RESTRICTED_DRIVE_JSP_PATH = "/WEB-INF/jsp/restrictedDrive.jsp";

  @Override
  public void doFilter(ServletRequest request,
                       ServletResponse response,
                       FilterChain chain) throws IOException, ServletException {
    
    PortalContainer container = PortalContainer.getInstance();
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    String uri = httpRequest.getRequestURI();
    if (uri.contains("restricted-drive")) {
      container.getPortalContext()
               .getRequestDispatcher(RESTRICTED_DRIVE_JSP_PATH)
               .forward(httpRequest, httpResponse);

      return;
    }
    chain.doFilter(request, response);
  }
}