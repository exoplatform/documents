/*
 * This file is part of the Meeds project (https://meeds.io/).
 * 
 * Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import './initComponents.js';
import '../documents-favorite-action/initComponents.js';
import * as attachmentService from '../../js/attachmentService.js';
import * as documentFileService from '../../js/DocumentFileService.js';
import * as documentsUtils from '../../js/DocumentsUtils.js';
import '../documents-icons-extension/extensions.js';

if (!Vue.prototype.$documentFileService) {
  window.Object.defineProperty(Vue.prototype, '$documentFileService', {
    value: documentFileService,
  });
}

if (!Vue.prototype.$attachmentService) {
  window.Object.defineProperty(Vue.prototype, '$attachmentService', {
    value: attachmentService,
  });
}

if (!Vue.prototype.$documentsUtils) {
  window.Object.defineProperty(Vue.prototype, '$documentsUtils', {
    value: documentsUtils,
  });
}

Vue.use(Vuetify);
const vuetify = new Vuetify(eXo.env.portal.vuetifyPreset);

const appId = 'documentInfoDrawer';

//getting language of the PLF
const lang = eXo?.env?.portal?.language || 'en';

//should expose the locale ressources as REST API
const url = `/documents-portlet/i18n/locale.portlet.Documents?lang=${lang}`;

export function init(event) {
  if (!document.querySelector(`#${appId}`)) {
    const parent = document.createElement('div');
    parent.id = appId;
    document.querySelector('#vuetify-apps').appendChild(parent);
    exoi18n.loadLanguageAsync(lang, url).then(i18n => {
      Vue.createApp({
        data: {
          pwaEnabled: false,
          isFavoritesSynchronized: false,
        },
        created() {
          this.$root.$on('documents-offline-settings-updated', this.init);
          this.init();
        },
        mounted() {
          document.dispatchEvent(new CustomEvent('open-info-drawer', event));
        },
        beforeDestroy() {
          this.$root.$off('documents-offline-settings-updated', this.init);
        },
        methods: {
          async init() {
            const registration = await navigator?.serviceWorker?.getRegistration?.();
            this.pwaEnabled = !!registration;
            this.isFavoritesSynchronized = this.pwaEnabled && (await this.$documentOfflineService.isOfflineDocumentsEnabled());
            this.$utils.includeExtensions('DocumentsExtension');
          },
        },
        template: `<document-info-drawer id="${appId}"/>`,
        vuetify,
        i18n,
      }, `#${appId}`, 'Documents Info drawer application');

    });
  } else {
    document.dispatchEvent(new CustomEvent('open-info-drawer', event));
  }
}