/*
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.documents.portlet;

import java.io.IOException;

import javax.portlet.*;

import org.exoplatform.commons.api.portlet.GenericDispatchedViewPortlet;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.space.SpaceUtils;
import org.exoplatform.social.core.space.model.Space;

import io.meeds.social.space.plugin.SpaceAclPlugin;

public class DocumentGadgetPortlet extends GenericDispatchedViewPortlet {

  private UserACL         userAcl;

  @Override
  protected void doView(RenderRequest request, RenderResponse response) throws PortletException, IOException {
    boolean canModifySettings = canModifySettings();
    request.setAttribute("canEdit", canModifySettings);
    super.doView(request, response);
  }

  @Override
  public void processAction(ActionRequest request, ActionResponse response) throws IOException, PortletException {

    if (!canModifySettings()) {
      throw new PortletException("User is not allowed to save settings");
    }
    PortletPreferences preferences = request.getPreferences();
    String settings = request.getParameter("settings");
    preferences.setValue("settings", settings);
    preferences.store();
    response.setPortletMode(PortletMode.VIEW);
  }

  private Identity getCurrentIdentity() {
    return ConversationState.getCurrent() == null ? null : ConversationState.getCurrent().getIdentity();
  }

  private UserACL getUserAcl() {
    if (userAcl == null) {
      userAcl = ExoContainerContext.getService(UserACL.class);
    }
    return userAcl;
  }

  private boolean canModifySettings() {
    Space space = SpaceUtils.getSpaceByContext();
    Identity identity = getCurrentIdentity();
    if (identity == null || getUserAcl().isAnonymousUser(identity)) {
      return false;
    } else if (space == null) {
      return getUserAcl().isAdministrator(identity);
    } else {
      return getUserAcl().isAdministrator(identity) || getUserAcl().hasEditPermission(SpaceAclPlugin.OBJECT_TYPE,
              space.getId(),
              identity);
    }
  }

}
