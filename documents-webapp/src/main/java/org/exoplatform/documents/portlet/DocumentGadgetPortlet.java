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

import javax.portlet.*;

import io.meeds.social.portlet.CMSPortlet;
import org.apache.commons.lang3.StringUtils;
import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.ThreadLocalRandom;

public class DocumentGadgetPortlet extends CMSPortlet {

  private static final String OBJECT_TYPE = "documentGadget";

  @Override
  public void init(PortletConfig config) throws PortletException {
    super.init(config);
    this.contentType = OBJECT_TYPE;
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

  @Override
  protected String generateRandomId() {
    String name;
    do {
      name = String.valueOf(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
    } while (isSettingNameExists(name));
    return name;
  }
}
