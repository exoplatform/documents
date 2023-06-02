/*
 * Copyright (C) 2023 eXo Platform SAS
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

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.filter.Filter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class DocumentPreviewFilter implements Filter {

  private static final Log LOG = ExoLogger.getLogger(DocumentPreviewFilter.class);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    ListenerService listenerService = CommonsUtils.getService(ListenerService.class);
    HttpServletRequest httpServletRequest = (HttpServletRequest) request;
    String documentPreviewId = httpServletRequest.getParameter("documentPreviewId");
    String documentEditId = httpServletRequest.getParameter("docId");
    String sourcePreview = httpServletRequest.getParameter("source");

    if (documentPreviewId != null || (documentEditId != null && sourcePreview == null)) {
      String viewer = httpServletRequest.getRemoteUser();
      try {
        String documentId = documentPreviewId != null ? documentPreviewId : documentEditId;
        listenerService.broadcast("update-document-views-detail", viewer, documentId);
      } catch (Exception e) {
        LOG.error("Error while broadcasting event", e);
      }
    }
    chain.doFilter(request, response);
  }
}
