/*
 * Copyright (C) 2026 eXo Platform SAS.
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
 *
*/
package org.exoplatform.documents.plugin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.documents.service.SpaceDocumentsExposureService;
import org.exoplatform.social.core.space.model.Space;

import io.meeds.social.space.security.plugin.SpaceSecuritySettingsPlugin;

/**
 * Removes the public links of a space's documents before its "Keep documents
 * inside the space" setting changes. Social collects it from every addon's
 * beans: it carries {@code @Service} under a name of its own and is not final.
 */
@Service(SpaceDocumentsSecuritySettingsPlugin.BEAN_NAME)
public class SpaceDocumentsSecuritySettingsPlugin implements SpaceSecuritySettingsPlugin {

  public static final String            BEAN_NAME = "documentsSpaceSecuritySettingsPlugin";

  @Autowired
  private SpaceDocumentsExposureService spaceDocumentsExposureService;

  @Override
  public void beforeDocumentsKeptInsideChange(Space space, boolean newValue, String username) {
    spaceDocumentsExposureService.removePublicLinks(space, newValue, username);
  }

}
