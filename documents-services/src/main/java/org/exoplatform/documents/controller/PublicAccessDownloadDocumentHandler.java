/*
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
package org.exoplatform.documents.controller;

import org.exoplatform.portal.branding.BrandingService;
import org.exoplatform.portal.resource.SkinService;
import org.exoplatform.services.resources.LocaleConfigService;
import org.exoplatform.web.ControllerContext;
import org.exoplatform.web.application.JspBasedWebHandler;
import org.exoplatform.web.application.javascript.JavascriptConfigService;
import org.exoplatform.web.controller.QualifiedName;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class PublicAccessDownloadDocumentHandler extends JspBasedWebHandler {

  private static final QualifiedName    NODE_ID                   = QualifiedName.create("gtn", "nodeId");

  private static final String           NAME                       = "download-document";

  public PublicAccessDownloadDocumentHandler(LocaleConfigService localeConfigService,
                                             BrandingService brandingService,
                                             JavascriptConfigService javascriptConfigService,
                                             SkinService skinService) {
    super(localeConfigService, brandingService, javascriptConfigService, skinService);
  }

  @Override
  public boolean execute(ControllerContext controllerContext) throws Exception {
    HttpServletRequest request = controllerContext.getRequest();
    HttpServletResponse response = controllerContext.getResponse();

    String nodeId = controllerContext.getParameter(NODE_ID);

    String redirect = request.getRequestURI().replace("/"+nodeId, "?nodeId="+nodeId);
    response.sendRedirect(response.encodeRedirectURL(redirect));
    return true;
  }

  @Override
  public String getHandlerName() {
    return NAME;
  }

  @Override
  protected boolean getRequiresLifeCycle() {
    return true;
  }

}
