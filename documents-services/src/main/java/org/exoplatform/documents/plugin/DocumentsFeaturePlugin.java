/*
 * Copyright (C) 2022 eXo Platform SAS
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
package org.exoplatform.documents.plugin;

import org.exoplatform.commons.api.settings.FeaturePlugin;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;

public class DocumentsFeaturePlugin extends FeaturePlugin {

  public static final Scope    APP_SCOPE = Scope.APPLICATION.id("changesReminder");

  private final SettingService settingService;

  public DocumentsFeaturePlugin(SettingService settingService) {
    this.settingService = settingService;
  }

  @Override
  public boolean isFeatureActiveForUser(String featureName, String username) {
    SettingValue<?> userSettingValue = settingService.get(Context.USER.id(username), APP_SCOPE, featureName);
    return userSettingValue == null || userSettingValue.getValue().equals(false);
  }

}
