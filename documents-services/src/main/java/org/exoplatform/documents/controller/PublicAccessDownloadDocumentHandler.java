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
package org.exoplatform.documents.controller;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.documents.model.PublicDocumentAccess;
import org.exoplatform.documents.model.DownloadItem;
import org.exoplatform.documents.service.PublicDocumentAccessService;
import org.exoplatform.documents.service.ExternalDownloadService;
import org.exoplatform.portal.branding.BrandingService;
import org.exoplatform.portal.resource.SkinService;
import org.exoplatform.services.resources.LocaleConfigService;
import org.exoplatform.web.ControllerContext;
import org.exoplatform.web.application.JspBasedWebHandler;
import org.exoplatform.web.application.javascript.JavascriptConfigService;
import org.exoplatform.web.controller.QualifiedName;
import org.json.JSONObject;
import org.exoplatform.container.xml.InitParams;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PublicAccessDownloadDocumentHandler extends JspBasedWebHandler {

  private ServletContext                servletContext;

  private static final QualifiedName    TOKEN_ID                   = QualifiedName.create("gtn", "nodeId");

  private static final String           DOWNLOAD_DOCUMENT_JSP_PATH = "public.download.jsp.path";

  private static final String           NAME                       = "download-document";

  private final PublicDocumentAccessService publicDocumentAccessService;

  private final ExternalDownloadService externalDownloadService;

  private String                        publicDownloadJspPath;

  public PublicAccessDownloadDocumentHandler(PortalContainer container,
                                             PublicDocumentAccessService publicDocumentAccessService,
                                             ExternalDownloadService externalDownloadService,
                                             LocaleConfigService localeConfigService,
                                             BrandingService brandingService,
                                             JavascriptConfigService javascriptConfigService,
                                             SkinService skinService,
                                             InitParams initParams) {
    super(localeConfigService, brandingService, javascriptConfigService, skinService);
    this.publicDocumentAccessService = publicDocumentAccessService;
    this.externalDownloadService = externalDownloadService;
    this.servletContext = container.getPortalContext();
    if (initParams != null && initParams.containsKey(DOWNLOAD_DOCUMENT_JSP_PATH)) {
      this.publicDownloadJspPath = initParams.getValueParam(DOWNLOAD_DOCUMENT_JSP_PATH).getValue();
    }
  }

  @Override
  public boolean execute(ControllerContext controllerContext) throws Exception {
    HttpServletRequest request = controllerContext.getRequest();
    HttpServletResponse response = controllerContext.getResponse();

    String nodeId = controllerContext.getParameter(TOKEN_ID);
    DownloadItem downloadItem = externalDownloadService.getDocumentDownloadItem(nodeId);
    PublicDocumentAccess publicDocumentAccess = publicDocumentAccessService.getPublicDocumentAccess(nodeId);
    boolean hasPublicLink = publicDocumentAccess != null;
    boolean isTokenLocked = hasPublicLink && publicDocumentAccess.isHasPassword();
    boolean isTokenExpired = publicDocumentAccessService.isPublicDocumentAccessExpired(nodeId);
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("nodeId", nodeId);
    parameters.put("hasPublicLink", hasPublicLink);
    parameters.put("isTokenLocked", isTokenLocked);
    parameters.put("isTokenExpired", isTokenExpired);
    if (downloadItem != null) {
      parameters.put("documentName", downloadItem.getItemName());
      parameters.put("documentType", downloadItem.getMimeType());
    }
    return dispatch(controllerContext, request, response, parameters);
  }

  @Override
  public String getHandlerName() {
    return NAME;
  }

  @Override
  protected boolean getRequiresLifeCycle() {
    return true;
  }

  protected void extendApplicationParameters(JSONObject applicationParameters, Map<String, Object> additionalParameters) {
    additionalParameters.forEach(applicationParameters::put);
  }

  private boolean dispatch(ControllerContext controllerContext,
                           HttpServletRequest request,
                           HttpServletResponse response,
                           Map<String, Object> parameters) throws Exception {

    super.prepareDispatch(controllerContext,
                          "PORTLET/documents-portlet/DownloadDocumentsPublicAccess",
                          Collections.emptyList(),
                          Collections.singletonList("portal/login"),
                          params -> extendApplicationParameters(params, parameters));
    servletContext.getRequestDispatcher(publicDownloadJspPath).include(request, response);
    return true;
  }
}
