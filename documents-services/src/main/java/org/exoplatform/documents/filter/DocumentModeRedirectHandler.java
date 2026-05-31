/*
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
package org.exoplatform.documents.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.web.filter.Filter;

import io.meeds.common.ContainerTransactional;

public class DocumentModeRedirectHandler implements Filter {

  @SneakyThrows
  @Override
  @ContainerTransactional
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    DocumentFileService documentFileService = ExoContainerContext.getService(DocumentFileService.class);
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    HttpServletResponse httpServletResponse = (HttpServletResponse) response;
    HttpServletRequest httpServletRequest = (HttpServletRequest) request;
    String documentPreviewId = httpServletRequest.getParameter("documentPreviewId");
    String documentEditId = httpServletRequest.getParameter("docId");
    String viewer = httpServletRequest.getRemoteUser();
    if (viewer == null) {
      chain.doFilter(request, response);
    }

    String documentId = documentPreviewId != null ? documentPreviewId : documentEditId;

    documentFileService.getDocumentById(documentId);
    Identity authenticatedUserIdentity = identityManager.getOrCreateUserIdentity(viewer);
    boolean canEdit = documentFileService.hasEditPermissionOnDocument(documentId,
                                                                      Long.parseLong(authenticatedUserIdentity.getId()));
    if (!canEdit && httpServletRequest.getParameter("mode") == null) {
      StringBuilder redirectUrl = new StringBuilder(httpServletRequest.getRequestURL());
      String query = httpServletRequest.getQueryString();
      if (query != null && !query.isEmpty()) {
        redirectUrl.append("?").append(query).append("&mode=view");
      } else {
        redirectUrl.append("?mode=view");
      }
      httpServletResponse.sendRedirect(redirectUrl.toString());
      return;
    }
    chain.doFilter(request, response);
  }
}
