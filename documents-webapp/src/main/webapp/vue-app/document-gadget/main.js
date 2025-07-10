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
import './initComponents.js';
import './services.js';
import '../documents-icons-extension/extensions.js';

// getting language of user
const lang = eXo?.env?.portal?.language || 'en';
const url = `/documents-portlet/i18n/locale.portlet.Documents?lang=${lang}`;

const appId = 'DocumentGadget';

export function init(
  portletStorageId,
  viewOptions,
  canEdit,
  pageRef) {
  exoi18n.loadLanguageAsync(lang, url)
    .then(i18n => {
      new Vue({
        data: {
          portletStorageId,
          viewOptions,
          canEdit,
          pageRef
        },
        template: `<document-list id="${appId}" />`,
        i18n,
        vuetify: Vue.prototype.vuetifyOptions,
      }).$mount(`#${appId}`);
    });
}
