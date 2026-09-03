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
import '../documents-user-setting-common/main.js';

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
  // Needed by the shared "Add a document" drawer, whose labels live in the
  // attachments bundle.
  `/documents-portlet/i18n/locale.portlet.attachments?lang=${lang}`,
  `/pwa/i18n/locale.portlet.OfflineApplication?lang=${lang}`,
  `/social/i18n/locale.portlet.social.UserSettings?lang=${lang}`
];

Vue.prototype.$nextTick(() => {
  if (!window.transferRulesFetched) {
    window.transferRulesFetched = true;
    Vue.prototype.$transferRulesService.getDocumentsTransferRules().then(rules => {
      Vue.prototype.$shareDocumentSuspended = rules.sharedDocumentStatus === 'true';
      Vue.prototype.$downloadDocumentSuspended = rules.downloadDocumentStatus === 'true';
    });
  }
});

export async function init(appId, canEdit,  settings, settingsSaveUrl) {

  const i18n = await exoi18n.loadLanguageAsync(lang, urls);

  // init Vue app when locale ressources are ready
  let settingsSubcategoryIds;
  let settingsExcludeSubcategoryIds;
  if (settings?.categoryIds?.length) {
    settingsSubcategoryIds = await getSubcategoryIds(settings.categoryIds, 1);
  }
  if (settings?.excludeCategoryIds?.length) {
    settingsExcludeSubcategoryIds = await getSubcategoryIds(settings.excludeCategoryIds, 1);
  }

  await Vue.createApp({
    data: {
      appId,
      canEdit,
      settings,
      settingsSaveUrl,
      hover: false,
      selectedCategoryId: null,
      selectedCategoryIds: null,
      settingsSubcategoryIds,
      settingsExcludeSubcategoryIds,
      isFavoritesSynchronized: false,
      pwaEnabled: false,
      ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
      driveView: false,
      spaceId: null,
      selectedDrive: null,
      selectedPath: null,
    },
    computed: {
      categoryIds() {
        return this.settingsSubcategoryIds || this.settings.categoryIds;
      },
      excludeCategoryIds() {
        return this.settingsExcludeSubcategoryIds || this.settings.excludeCategoryIds;
      },
      allowFilteringPerCategory() {
        return this.settings.allowFilteringPerCategory;
      },
      categoryDepth() {
        return this.settings.categoryDepth || 4;
      },
      isMobile() {
        return this.$vuetify.breakpoint.smAndDown;
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
        await this.$utils.includeExtensions('DocumentsExtension');
      },
      async handleSettingsUpdate() {
        this.settings = JSON.parse(JSON.stringify(this.settings)); // Force update
        this.settingsSubcategoryIds = await getSubcategoryIds(this.settings.categoryIds, 1);
        this.settingsExcludeSubcategoryIds = await getSubcategoryIds(this.settings.excludeCategoryIds, 1);
        this.selectedCategoryIds = await getSubcategoryIds(this.settings.categoryIds || [], -1);
      },
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
    template: `<documents-main id="${appId}" />`,
    vuetify: Vue.prototype.vuetifyOptions,
    i18n
  }, `#${appId}`, 'Documents');
  Vue.prototype.$utils.includeExtensions('LeftHeaderExtension');
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
