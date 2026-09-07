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

export function init(settings) {
  exoi18n.loadLanguageAsync(lang, url)
    .then(i18n => {
      new Vue({
        data: {
          settings
        },
        computed: {
          isMobile() {
            return this.$vuetify.breakpoint.smAndDown;
          }
        },
        methods: {
          safeDecodeURIComponent(name) {
            if (!name) {
              return '';
            }
            try {
              return decodeURIComponent(name.replace(/%25/g, '%').replace(/%([^2][^5])/g, '%25$1'));
            } catch (e) {
              console.warn('Filename decode failed:', name, e);
              return name;
            }
          },
          getFileIcon(file) {
            return this.$documentsIconsExtension?.[0]?.get?.(file?.mimeType) || 'fas fa-file';
          },
          isFileEditable(file) {
            return this.$supportedDocuments?.some(doc => doc.edit && doc.mimeType === file.mimeType) ?? false;
          },
          isFileReadable(file) {
            return this.$supportedDocuments?.some(doc => doc.mimeType === file.mimeType) ?? false;
          },
          getImageUrl(file) {
            file.readable = this.isFileReadable(file);
            return this.$documentsUtils.getThumbnailUrl(file,'250x250',file.lastModified);
          },
          getDownloadUrl(file) {
            return this.$documentsUtils.getDownloadUrl(file.id, file.lastModified);
          },
          openInEditMode(file) {
            const fileId = file.sourceID ? file.sourceID : file.id;
            this.$documentsUtils.openLink(`${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${fileId}&backTo=${window.location.pathname}`, '_blank');
          },
          openInReadOnlyMode(file) {
            const fileId = file.sourceID ? file.sourceID : file.id;
            this.$documentsUtils.openLink(`${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${fileId}&mode=view&backTo=${window.location.pathname}`, '_blank');
          },
        },
        template: `<document-list id="${settings?.id}" />`,
        i18n,
        vuetify: Vue.prototype.vuetifyOptions,
      }).$mount(`#${settings?.id}`);
    });
}
