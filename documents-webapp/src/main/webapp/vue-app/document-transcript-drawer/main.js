/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */

import './initComponents.js';
import * as attachmentService from '../../js/attachmentService.js';
import * as documentsUtils from '../../js/DocumentsUtils.js';
import * as transcriptionService from '../../js/TranscriptionService.js';

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

if (!Vue.prototype.$transcriptionService) {
  window.Object.defineProperty(Vue.prototype, '$transcriptionService', {
    value: transcriptionService,
  });
}

Vue.use(Vuetify);
const vuetify = new Vuetify(eXo.env.portal.vuetifyPreset);

const appId = 'documentTranscriptDrawer';

const lang = eXo?.env?.portal?.language || 'en';

const url = `/documents-portlet/i18n/locale.portlet.Documents?lang=${lang}`;

/**
 * Opens the transcript drawer of a file, mounting its application on first use.
 *
 * @param {Object} detail the file's id as documentId, and optionally the
 *          moment to show, in seconds, as seconds
 * @returns {void}
 */
export function init(detail) {
  if (!document.querySelector(`#${appId}`)) {
    const parent = document.createElement('div');
    parent.id = appId;
    document.querySelector('#vuetify-apps').appendChild(parent);
    exoi18n.loadLanguageAsync(lang, url).then(i18n => {
      Vue.createApp({
        mounted() {
          document.dispatchEvent(new CustomEvent('documents-transcript-drawer-open', {detail}));
        },
        template: `<document-transcript-drawer id="${appId}"/>`,
        vuetify,
        i18n,
      }, `#${appId}`, 'Documents Transcript drawer application');
    });
  } else {
    document.dispatchEvent(new CustomEvent('documents-transcript-drawer-open', {detail}));
  }
}
