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

export function init() {
  downloadFavorites();
  checkDocumentsAccessdOffline();
  window.setInterval(downloadFavorites, 3 * 60 * 1000);
}

function downloadFavorites() {
  window.require(['SHARED/DocumentsPWAOfflineApp'], () => {
    downloadFavoritesFromServer();
  });
}

function checkDocumentsAccessdOffline() {
  window.require(['SHARED/eXoVueI18n', 'SHARED/DocumentsPWAOfflineApp'], exoi18n => {
    displayDocumentsAccessdOfflineNotification(exoi18n);
  });
}

async function displayDocumentsAccessdOfflineNotification(exoi18n) {
  if (await Vue.prototype.$documentOfflineService.isOfflineDocumentsEnabled()
      && await Vue.prototype.$documentOfflineService.isDocumentsAccessedOffline()) {
    const lang = eXo?.env?.portal?.language || 'en';
    const urls = [
      `/documents-portlet/i18n/locale.portlet.Documents?lang=${lang}`,
      `/pwa/i18n/locale.portlet.OfflineApplication?lang=${lang}`
    ];
    await exoi18n.loadLanguageAsync(lang, urls);
    document.dispatchEvent(new CustomEvent('alert-message', {detail: {
      alertType: 'info',
      alertMessage: window.vueI18nMessages['OfflineApp.pwa.documents.accessed.tip'],
      alertLinkText: window.vueI18nMessages['OfflineApp.pwa.review'],
      alertLinkCallback: displayOfflineDocumentsDrawer,
    }}));
    await Vue.prototype.$documentOfflineService.setDocumentsAccessedOffline(false);
  }
}

async function downloadFavoritesFromServer() {
  if (await Vue.prototype.$documentOfflineService.isOfflineDocumentsEnabled()) {
    if (!(await Vue.prototype.$documentOfflineService.isDatabaseExists())) {
      await Vue.prototype.$documentOfflineService.createDatabase();
    }
    await Vue.prototype.$documentOfflineService.downloadFavorites();
  }
}

function displayOfflineDocumentsDrawer() {
  window.require(['SHARED/eXoVueI18n'], exoi18n => initOfflineDocumentsDrawer(exoi18n));
}

async function initOfflineDocumentsDrawer(exoi18n) {
  const appId = 'OfflineDocumentsDrawer';
  if (!document.querySelector(`#${appId}`)) {
    const parent = document.createElement('div');
    parent.id = appId;
    document.querySelector('#vuetify-apps').appendChild(parent);
    await initOfflineDocumentsDrawerApp(appId, exoi18n);
  }
  document.dispatchEvent(new CustomEvent('open-document-offline-files'));
  document.dispatchEvent(new CustomEvent('close-alert-message'));
}

function initOfflineDocumentsDrawerApp(appId, exoi18n) {
  const lang = eXo?.env?.portal?.language || 'en';
  const url = `/pwa/i18n/locale.portlet.OfflineApplication?lang=${lang}`;
  return new Promise(resolve => exoi18n.loadLanguageAsync(lang, url)
    .then(i18n => Vue.createApp({
      template: `
        <documents-offline-drawer
          id="${appId}"
          ref="drawer" />
      `,
      created() {
        document.addEventListener('open-document-offline-files', this.openDrawer);
      },
      mounted() {
        resolve();
      },
      beforeDestroy() {
        document.removeEventListener('open-document-offline-files', this.openDrawer);
      },
      methods: {
        openDrawer() {
          this.$refs.drawer.open();
        },
      },
      vuetify: Vue.prototype.vuetifyOptions,
      i18n,
    }, `#${appId}`, 'Kudos List Quick Action')));
}
