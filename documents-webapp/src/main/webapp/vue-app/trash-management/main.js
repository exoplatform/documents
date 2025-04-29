/*
 * Copyright (C) 2024 eXo Platform SAS.
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
import './initComponents.js';
import * as trashManagementService from './js/TrashManagementService';
import * as documentFileService from '../../js/DocumentFileService.js';
import * as documentsWebSocket from '../documents/js/WebSocket';
import '../documents-icons-extension/extensions.js';

Vue.use(Vuetify);
const vuetify = new Vuetify(eXo.env.portal.vuetifyPreset);

if (!Vue.prototype.$trashManagementService) {
  window.Object.defineProperty(Vue.prototype, '$trashManagementService', {
    value: trashManagementService,
  });
}
if (!Vue.prototype.$documentFileService) {
  window.Object.defineProperty(Vue.prototype, '$documentFileService', {
    value: documentFileService,
  });
}
if (!Vue.prototype.$documentsWebSocket) {
  window.Object.defineProperty(Vue.prototype, '$documentsWebSocket', {
    value: documentsWebSocket,
  });
}

const appId = 'TrashManagement';

//getting language of the PLF
const lang = eXo?.env?.portal?.language || 'en';

//should expose the locale resources as REST API
const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.TrashManagement-${lang}.json`;

export function init() {
  Vue.prototype.$documentsIconsExtension = extensionRegistry.loadExtensions('documents', 'documents-icons-extension');
  document.addEventListener('documents-documents-icons-extension-updated', () => {
    Vue.prototype.$documentsIconsExtension = extensionRegistry.loadExtensions('documents', 'documents-icons-extension');
  });
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    // init Vue app when locale ressources are ready
    Vue.createApp({
      template: `<trash-management id="${appId}" />`,
      vuetify,
      i18n
    }, `#${appId}`, 'TrashManagement');
  });
}