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

import '../documents-offline-common/main.js';

import './initComponents.js';
import '../documents-favorite-action/initComponents.js';

import './services.js';

import './extensions.js';
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
const urls = [
  `/documents-portlet/i18n/locale.portlet.Documents?lang=${lang}`,
  `/pwa/i18n/locale.portlet.OfflineApplication?lang=${lang}`,
  `/social/i18n/locale.portlet.social.UserSettings?lang=${lang}`
];

Vue.prototype.$nextTick(() => {
  Vue.prototype.$transferRulesService.getDocumentsTransferRules().then(rules => {
    Vue.prototype.$shareDocumentSuspended = rules.sharedDocumentStatus === 'true';
    Vue.prototype.$downloadDocumentSuspended = rules.downloadDocumentStatus === 'true';
  });
});

export async function init(appId, canEdit,  settings, settingsSaveUrl) {

  const i18n = await exoi18n.loadLanguageAsync(lang, urls);

  // init Vue app when locale ressources are ready
  let settingsSubcategoryIds;
  if (settings?.categoryIds?.length) {
    settingsSubcategoryIds = await getSubcategoryIds(settings.categoryIds, 1);
  }
  await Vue.createApp({
    data: {
      canEdit,
      settings,
      settingsSaveUrl,
      hover: false,
      selectedCategoryId: null,
      selectedCategoryIds: null,
      settingsSubcategoryIds,
      isFavoritesSynchronized: false,
      pwaEnabled: false,
      ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
    },
    computed: {
      categoryIds() {
        return this.settingsSubcategoryIds || this.settings.categoryIds;
      },
      allowFilteringPerCategory() {
        return this.settings.allowFilteringPerCategory;
      },
      categoryDepth() {
        return this.settings.categoryDepth || 4;
      },
    },
    watch: {
      async selectedCategoryId() {
        if (this.selectedCategoryId) {
          this.selectedCategoryIds = await getSubcategoryIds([this.selectedCategoryId], -1);
        } else {
          this.selectedCategoryIds = await getSubcategoryIds(this.settings.categoryIds || [], -1);
        }
      },
    },
    created() {
      this.$root.$on('documents-offline-settings-updated', this.init);
      this.$root.$on('documents-settings-updated', this.handleSettingsUpdate);
      this.init();
    },
    beforeDestroy() {
      this.$root.$off('documents-offline-settings-updated', this.init);
      this.$root.$off('documents-settings-updated', this.handleSettingsUpdate);
    },
    methods: {
      async init() {
        const registration = await navigator?.serviceWorker?.getRegistration?.();
        this.pwaEnabled = !!registration;
        this.isFavoritesSynchronized = this.pwaEnabled && (await this.$documentOfflineService.isOfflineDocumentsEnabled());
      },
      async handleSettingsUpdate() {
        this.settings = JSON.parse(JSON.stringify(this.settings)); // Force update
        this.settingsSubcategoryIds = await getSubcategoryIds(this.settings.categoryIds, 1);
        this.selectedCategoryIds = await getSubcategoryIds(this.settings.categoryIds || [], -1);
      },
    },
    template: `<documents-main id="${appId}" />`,
    vuetify: Vue.prototype.vuetifyOptions,
    i18n
  }, `#${appId}`, 'Documents');

}

async function getSubcategoryIds(categoryIds, depth) {
  if (!categoryIds?.length) {
    return [];
  }
  const subcategoyIds = await Promise.all(categoryIds.map(id => Vue.prototype.$categoryService.getSubcategoryIds(id, {
    offset: 0,
    limit: -1,
    depth,
  })));
  const subcategoyIdsFlat = subcategoyIds.flatMap(s => s);
  subcategoyIdsFlat.push(...categoryIds);
  return [...new Set(subcategoyIdsFlat)];
}
