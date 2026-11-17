/*
 * Copyright (C) 2026 eXo Platform SAS.
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

import './initComponents.js';

import * as spaceSecuritySettingsService from './js/SpaceSecuritySettingsService.js';

if (!Vue.prototype.$spaceSecuritySettingsService) {
  window.Object.defineProperty(Vue.prototype, '$spaceSecuritySettingsService', {
    value: spaceSecuritySettingsService,
  });
}

const lang = eXo?.env?.portal?.language || 'en';
exoi18n.loadLanguageAsync(lang, `/documents-portlet/i18n/locale.portlet.Documents?lang=${lang}`);

extensionRegistry.registerComponent('SpaceSettings', 'space-settings-components', {
  id: 'documents-space-settings',
  vueComponent: Vue.options.components['documents-space-settings'],
  rank: 0,
});
