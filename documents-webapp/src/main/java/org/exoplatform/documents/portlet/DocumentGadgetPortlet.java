/**
 * Copyright (C) 2025 eXo Platform SAS.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.portlet;

import java.io.IOException;
import java.util.Enumeration;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletConfig;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.localization.LocaleContextInfoUtils;

import io.meeds.social.portlet.CMSPortlet;
import io.meeds.social.translation.service.TranslationService;

public class DocumentGadgetPortlet extends CMSPortlet {

  private static final String HEADER_FIELD_NAME = "headerTitle";

  private static final String OBJECT_TYPE       = "documentGadget";

  private TranslationService  translationService;

  @Override
  public void init(PortletConfig config) throws PortletException {
    super.init(config);
    this.contentType = OBJECT_TYPE;
  }

  @Override
  public void doView(RenderRequest request, RenderResponse response) throws PortletException, IOException {
    super.doView(request, response);
    String headerTitle = getTranslationService().getTranslationLabelOrDefault(OBJECT_TYPE,
                                                                              (String) request.getAttribute("settingName"),
                                                                              HEADER_FIELD_NAME,
                                                                              LocaleContextInfoUtils.getUserLocale(request.getRemoteUser()));
    if (headerTitle != null) {
      request.setAttribute(HEADER_FIELD_NAME, headerTitle);
    }
  }

  @Override
  public void processAction(ActionRequest request, ActionResponse response) throws IOException, PortletException {
    PortletPreferences preferences = request.getPreferences();
    Enumeration<String> parameterNames = request.getParameterNames();
    while (parameterNames.hasMoreElements()) {
      String name = parameterNames.nextElement();
      if (StringUtils.equals(name, "action") || StringUtils.contains(name, "portal:")) {
        continue;
      }
      String value = request.getParameter(name);
      preferences.setValue(name, value);
    }
    preferences.store();
  }

  public TranslationService getTranslationService() {
    if (translationService == null) {
      translationService = ExoContainerContext.getService(TranslationService.class);
    }
    return translationService;
  }
}
