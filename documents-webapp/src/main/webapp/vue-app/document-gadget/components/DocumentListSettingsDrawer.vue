<!--
 Copyright (C) 2025 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <exo-drawer
    ref="drawer"
    v-model="drawer"
    :right="!$vuetify.rtl"
    :loading="loading"
    eager
    @closed="reset">
    <template #title>
      {{ $t('documents.documentGadget.settings.title') }}
    </template>
    <template v-if="drawer" #content>
      <div class="pa-4">
        <div class="mb-2 text-header">{{ $t('documents.documentGadget.settings.displayManagement') }}</div>
        <div class="font-weight-bold mb-2">{{ $t('documents.documentGadget.settings.viewOptions') }}</div>
        <v-radio-group
          v-model="viewOptions"
          class="mt-0"
          mandatory>
          <v-radio value="list">
            <template #label>
              <span class="ms-1">{{ $t('documents.documentGadget.settings.viewOptions.list') }}</span>
            </template>
          </v-radio>
          <v-radio value="cards">
            <template #label>
              <span class="ms-1">{{ $t('documents.documentGadget.settings.viewOptions.cards') }}</span>
            </template>
          </v-radio>
        </v-radio-group>
        <div class="font-weight-bold my-2">{{ $t('documents.documentGadget.settings.additionalOptions') }}</div>
        <div class="d-flex my-2 align-center justify-space-between">
          <label class="v-label text-color align-start">
            {{ $t('documents.documentGadget.settings.additionalOptions.updateHeader') }}
          </label>
          <div class="align-end">
            <v-switch
              v-model="customHeader"
              color="primary"
              class="pa-0 my-auto"
              hide-details />
          </div>
        </div>
        <translation-text-field
          v-if="customHeader"
          :object-id="applicationId"
          :object-type="objectType"
          :field-name="fieldName"
          :field-value="displayedValue"
          :drawer-title="$t('documents.documentGadget.header.translation.title')"
          class="mt-2"
          no-expand-icon
          back-icon
          required
          @input="translationUpdated" />
        <div class="d-flex my-2 align-center justify-space-between">
          <label class="v-label text-color align-start">
            {{ $t('documents.documentGadget.settings.additionalOptions.displaySeeMore') }}
          </label>
          <div class="align-end">
            <v-switch
              v-model="displaySeeMore"
              color="primary"
              class="pa-0 my-auto"
              hide-details />
          </div>
        </div>
        <div class="d-flex align-center">
          <label class="v-label text-color">
            {{ $t('documents.documentGadget.settings.additionalOptions.numberItems') }}
          </label>
          <div class="ms-auto">
            <number-input
              v-model="maxDocumentsToList"
              :min="0"
              :max="100"
              :step="1"
              editable />
          </div>
        </div>
        <div class="mb-2 text-header">{{ $t('documents.documentGadget.settings.documentListing') }}</div>
        <div class="font-weight-bold mb-2">{{ $t('documents.documentGadget.settings.documentSource') }}</div>
        <v-radio-group
          v-model="documentSource"
          class="mt-0"
          mandatory>
          <v-radio value="currentDrive">
            <template #label>
              <span class="ms-1">{{ $t('documents.documentGadget.settings.documentSource.currentDrive') }}</span>
            </template>
          </v-radio>
          <v-radio value="oneDrive">
            <template #label>
              <span class="ms-1">{{ $t('documents.documentGadget.settings.documentSource.oneSelectedDrive') }}</span>
            </template>
          </v-radio>
          <exo-identity-suggester
            v-if="oneDriveSelected"
            ref="driveSuggester"
            v-model="spaceIdentity"
            :labels="driveSuggesterLabels"
            :include-users="false"
            :width="220"
            name="driveSuggester"
            class="user-suggester mt-n2"
            include-spaces
            only-redactor />
        </v-radio-group>
        <div class="font-weight-bold mb-2">{{ $t('documents.documentGadget.settings.documentType') }}</div>
        <v-radio-group
          v-model="documentType"
          class="mt-0"
          mandatory>
          <v-radio value="recentDocument">
            <template #label>
              <span class="ms-1">{{ $t('documents.documentGadget.settings.documentType.recentDocument') }}</span>
            </template>
          </v-radio>
          <v-radio :disabled="oneDriveSelected" value="sharedWithMe">
            <template #label>
              <span class="ms-1">{{ $t('documents.documentGadget.settings.documentType.sharedWithMe') }}</span>
            </template>
          </v-radio>
          <v-radio :disabled="oneDriveSelected" value="favorites">
            <template #label>
              <span class="ms-1">{{ $t('documents.documentGadget.settings.documentType.favorites') }}</span>
            </template>
          </v-radio>
        </v-radio-group>
        <div class="font-weight-bold mb-2">{{ $t('documents.settings.filterList') }}</div>
        <div class="d-flex align-center text-start">
          <div>{{ $t('documents.settings.filterListPerCategory') }}</div>
          <v-spacer />
          <v-switch
            v-model="filterPerCategories"
            class="ma-0 width-fit-content" />
        </div>
        <div v-if="filterPerCategories" class="mt-4">
          <category-suggester
            v-model="categoryId"
            class="mt-n2 mb-4 mx-0 pa-0"
            label=""
            access-permission />
          <v-list class="pa-0" dense>
            <v-list-item
              v-for="(c, index) in selectedCategories"
              :key="c.id"
              class="pa-0"
              dense>
              <v-list-item-icon class="me-2 my-auto">
                <v-icon size="24">{{ c.icon }}</v-icon>
              </v-list-item-icon>
              <v-list-item-content class="me-2 pa-0 text-truncate">
                <v-list-item-title class="text-truncate">
                  {{ c.name }}
                </v-list-item-title>
              </v-list-item-content>
              <v-list-item-action class="mx-0 my-auto">
                <v-btn
                  :title="$t('documents.settings.deleteCategory')"
                  icon
                  @click="removeItem(index, settings.categoryIds)">
                  <v-icon size="18" color="error">fa-trash</v-icon>
                </v-btn>
              </v-list-item-action>
            </v-list-item>
          </v-list>
        </div>
        <div class="d-flex align-center text-start">
          <div>{{ $t('documents.settings.filterExcludeCategory') }}</div>
          <v-spacer />
          <v-switch
            v-model="filterPerExcludeCategories"
            class="ma-0 width-fit-content" />
        </div>
        <div v-if="filterPerExcludeCategories" class="mt-4">
          <category-suggester
            v-model="excludeCategoryId"
            class="mt-n2 mb-4 mx-0 pa-0"
            label=""
            access-permission />
          <v-list class="pa-0" dense>
            <v-list-item
              v-for="(c, index) in selectedExcludeCategories"
              :key="c.id"
              class="pa-0"
              dense>
              <v-list-item-icon class="me-2 my-auto">
                <v-icon size="24">{{ c.icon }}</v-icon>
              </v-list-item-icon>
              <v-list-item-content class="me-2 pa-0 text-truncate">
                <v-list-item-title class="text-truncate">
                  {{ c.name }}
                </v-list-item-title>
              </v-list-item-content>
              <v-list-item-action class="mx-0 my-auto">
                <v-btn
                  :title="$t('documents.settings.deleteCategory')"
                  icon
                  @click="removeItem(index, settings.excludeCategoryIds)">
                  <v-icon size="18" color="error">fa-trash</v-icon>
                </v-btn>
              </v-list-item-action>
            </v-list-item>
          </v-list>
        </div>
      </div>
    </template>
    <template #footer>
      <div class="d-flex align-center">
        <v-btn
          :disabled="loading"
          :title="$t('links.label.cancel')"
          class="btn ms-auto me-2"
          @click="close()">
          {{ $t('documents.button.cancel') }}
        </v-btn>
        <v-btn
          :loading="loading"
          color="primary"
          elevation="0"
          @click="save()">
          {{ $t('documents.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    drawer: false,
    loading: false,
    settings: {},
    viewOptions: 'list',
    customHeader: false,
    displaySeeMore: true,
    maxDocumentsToList: 4,
    objectType: 'documentGadget',
    fieldName: 'headerTitle',
    translations: [],
    userLocale: eXo.env.portal.language,
    translationsInitialized: false,
    currentTranslations: [],
    documentType: 'recentDocument',
    documentSource: 'currentDrive',
    space: null,
    spaceIdentity: null,
    categoryId: null,
    excludeCategoryId: null,
    selectedCategories: [],
    selectedExcludeCategories: [],
    filterPerCategories: false,
    filterPerExcludeCategories: false,
  }),
  computed: {
    saveSettingsUrl() {
      return this.settings?.saveSettingsUrl;
    },
    applicationId() {
      return this.settings?.applicationId;
    },
    displayedValue() {
      return this.translations?.[this.userLocale];
    },
    categoryIds() {
      return this.settings?.categoryIds;
    },
    excludeCategoryIds() {
      return this.settings?.excludeCategoryIds;
    },
    refreshList() {
      return  Number(this.maxDocumentsToList) !== this.settings.maxDocumentsToList
          || (this.documentType !== this.settings.documentType)
          || (this.selectedCategories !== this.settings.categoryIds)
          || (this.selectedExcludeCategories !== this.settings.excludeCategoryIds)
          || (this.spaceIdentityId !== this.settings.spaceIdentityId);
    },
    oneDriveSelected() {
      return this.documentSource === 'oneDrive';
    },
    driveSuggesterLabels() {
      return {
        placeholder: this.$t('documents.documentGadget.settings.drive.placeholder'),
        noDataLabel: this.$t('documents.documentGadget.settings.drive.noDataLabel'),
      };
    },
    spaceId() {
      return this.spaceIdentity?.spaceId;
    },
    spaceIdentityId() {
      return this.space?.identityId || '';
    }
  },
  created() {
    this.$root.$on('document-gadget-settings', this.open);
  },
  beforeDestroy() {
    this.$root.$off('document-gadget-settings', this.open);
  },
  watch: {
    oneDriveSelected() {
      if (this.oneDriveSelected) {
        this.documentType = 'recentDocument';
      } else {
        this.spaceIdentity = null;
      }
    },
    async categoryId() {
      if (this.categoryId) {
        if (!Array.isArray(this.settings.categoryIds)) {
          this.$root.settings.categoryIds = [];
        }
        if (this.settings.categoryIds.indexOf(this.categoryId) < 0) {
          this.$root.settings.categoryIds.push(this.categoryId);
        }
        await this.$nextTick();
        this.categoryId = null;
      }
    },
    async excludeCategoryId() {
      if (this.excludeCategoryId) {
        if (!Array.isArray(this.settings.excludeCategoryIds)) {
          this.$root.settings.excludeCategoryIds = [];
        }
        if (this.settings.excludeCategoryIds.indexOf(this.excludeCategoryId) < 0) {
          this.$root.settings.excludeCategoryIds.push(this.excludeCategoryId);
        }
        await this.$nextTick();
        this.excludeCategoryId = null;
      }
    },
    async categoryIds() {
      if (!this.categoryIds?.length) {
        this.selectedCategories = [];
      } else {
        this.selectedCategories = await Promise.all(this.categoryIds.map(id => this.$categoryService.getCategory(id)));
      }
    },
    filterPerCategories() {
      if (this.drawer && !this.filterPerCategories) {
        this.$root.settings.categoryIds = [];
      }
    },
    async excludeCategoryIds() {
      if (!this.excludeCategoryIds?.length) {
        this.selectedExcludeCategories = [];
      } else {
        this.selectedExcludeCategories = await Promise.all(this.excludeCategoryIds.map(id => this.$categoryService.getCategory(id)));
      }
    },
    filterPerExcludeCategories() {
      if (this.drawer && !this.filterPerExcludeCategories) {
        this.$root.settings.excludeCategoryIds = [];
      }
    },
  },
  methods: {
    open() {
      this.reset();
      this.$refs.drawer.open();
    },
    async reset() {
      this.settings = Object.assign({}, this.$root.settings);
      if (this.settings?.spaceIdentityId) {
        this.documentSource = 'oneDrive';
        await this.$identityService.getIdentityById(this.settings?.spaceIdentityId)
          .then(data => {
            const spaceEntity = data?.dataEntity?.space;
            this.spaceIdentity ={
              id: `space:${spaceEntity?.prettyName}`,
              profile: {
                avatarUrl: spaceEntity.avatarUrl,
                fullName: spaceEntity.displayName,
              },
              providerId: 'space',
              remoteId: data?.dataEntity.remoteId,
              spaceId: spaceEntity.id,
              displayName: spaceEntity.displayName,
            };
          });
      }
      this.maxDocumentsToList = Number(this.settings?.maxDocumentsToList);
      this.viewOptions = this.settings?.viewOptions || 'list';
      this.documentType = this.settings?.documentType || 'recentDocument';
      this.customHeader = this.settings?.customHeader || false;
      this.displaySeeMore = this.settings?.displaySeeMore || false;
      this.filterPerCategories = !!this.categoryIds?.length;
      this.filterPerExcludeCategories = !!this.excludeCategoryIds?.length;
      this.loading = false;
    },
    close() {
      this.$refs.drawer.close();
    },
    async save() {
      if (this.spaceId) {
        await this.$spaceService.getSpaceById(this.spaceId, 'identity')
          .then((space) => {
            this.space = space;
          });
      }
      const settings = {
        viewOptions: this.viewOptions,
        documentType: this.documentType,
        maxDocumentsToList: this.maxDocumentsToList,
        customHeader: this.customHeader,
        displaySeeMore: this.displaySeeMore,
        categoryIds: JSON.stringify(this.categoryIds),
        excludeCategoryIds: JSON.stringify(this.excludeCategoryIds),
        spaceIdentityId: this.spaceIdentityId
      };
      this.$documentGadgetService.saveSettings(this.saveSettingsUrl, settings).then(() => {
        this.saveHeaderTranslations();
        this.$emit('settings-updated', settings, this.displayedValue, this.refreshList);
        this.$root.$emit('alert-message', this.$t('documents.documentGadget.settings.save.success.message'), 'success');
        this.close();
      }).catch(() => {
        this.$root.$emit('alert-message', this.$t('documents.documentGadget.settings.error.success.message'), 'error');
      });
    },
    async saveHeaderTranslations() {
      if (this.customHeader) {
        await this.$translationService.saveTranslations(this.objectType, this.applicationId, this.fieldName, this.translations);
        this.currentTranslations = structuredClone(this.translations);
      }
    },
    translationUpdated(translations) {
      this.translations = translations;
      if (!this.translationsInitialized) {
        this.currentTranslations = structuredClone(this.translations);
        this.translationsInitialized = true;
      }
    },
    removeItem(index, array) {
      array.splice(index, 1);
      this.settings = JSON.parse(JSON.stringify(this.settings));
    },
  },
};
</script>
