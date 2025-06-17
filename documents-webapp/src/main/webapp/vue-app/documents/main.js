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
 */

import './initComponents.js';
import './extensions.js';
import './services.js';
import '../documents-icons-extension/extensions.js';

// get overrided components if exists
if (extensionRegistry) {
  const components = extensionRegistry.loadComponents('Documents');
  if (components && components.length > 0) {
    components.forEach(cmp => {
      Vue.component(cmp.componentName, cmp.componentOptions);
    });
  }
  Vue.prototype.$supportedDocuments = extensionRegistry.loadExtensions('documents', 'supported-document-types');
  document.addEventListener('documents-supported-document-types-updated', () => {
    Vue.prototype.$supportedDocuments = extensionRegistry.loadExtensions('documents', 'supported-document-types');
  });
}

//getting language of the PLF
const lang = eXo && eXo.env.portal.language || 'en';

//should expose the locale ressources as REST API 
const url = `/documents-portlet/i18n/locale.portlet.Documents?lang=${lang}`;

Vue.prototype.$nextTick(() => {
  Vue.prototype.$transferRulesService.getDocumentsTransferRules().then(rules => {
    Vue.prototype.$shareDocumentSuspended = rules.sharedDocumentStatus === 'true';
    Vue.prototype.$downloadDocumentSuspended = rules.downloadDocumentStatus === 'true';
  });
});

export function init(appId, canEdit,  settings, settingsSaveUrl) {
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    // init Vue app when locale ressources are ready
    Vue.createApp({
      data: {
        DB_NAME: 'favoriteDocuments',
        DB_VERSION: '1',
        DB_OBJECT_STORE: 'handles',
        DB_KEY: 'favorite',
        localDatabase: null,
        handle: null,
        canEdit,
        settings,
        settingsSaveUrl,
        hover: false,
      },
      computed: {
        isFavoritesSynchronized() {
          return !!this.handle;
        },
      },
      created() {
        this.init();
      },
      methods: {
        async init() {
          this.handle = await this.$documentOfflineService.getDirectoryHandle();
        },
      },
      template: `<documents-main id="${appId}" />`,
      vuetify: Vue.prototype.vuetifyOptions,
      i18n
    }, `#${appId}`, 'Documents');
  });
}
