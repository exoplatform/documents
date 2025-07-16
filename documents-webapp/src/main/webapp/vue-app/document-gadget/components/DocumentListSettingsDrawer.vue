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
      <div class="pa-5">
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
    viewOptions: 'list',
    customHeader: false,
    displaySeeMore: true,
    maxDocumentsToList: 4,
    objectType: 'documentGadget',
    fieldName: 'headerTitle',
    translations: [],
    userLocale: eXo.env.portal.language,
    translationsInitialized: false,
    currentTranslations: []
  }),
  computed: {
    settings() {
      return this.$root.settings;
    },
    saveSettingsUrl() {
      return this.settings?.saveSettingsUrl;
    },
    applicationId() {
      return this.settings?.applicationId;
    },
    displayedValue() {
      return this.translations?.[this.userLocale] || this.$t('documents.documentGadget.title');
    },
  },
  created() {
    this.$root.$on('document-gadget-settings', this.open);
  },
  beforeDestroy() {
    this.$root.$off('document-gadget-settings', this.open);
  },
  methods: {
    open() {
      this.reset();
      this.$refs.drawer.open();
    },
    reset() {
      this.maxDocumentsToList = Number(this.settings?.maxDocumentsToList);
      this.viewOptions = this.settings?.viewOptions || 'list';
      this.customHeader = this.settings?.customHeader || false;
      this.displaySeeMore = this.settings?.displaySeeMore || false;
      this.loading = false;
    },
    close() {
      this.$refs.drawer.close();
    },
    save() {
      const settings = {
        viewOptions: this.viewOptions,
        maxDocumentsToList: this.maxDocumentsToList,
        customHeader: this.customHeader,
        displaySeeMore: this.displaySeeMore,
      };
      const refreshList = Number(this.maxDocumentsToList) !== this.settings.maxDocumentsToList;
      this.$documentGadgetService.saveSettings(this.saveSettingsUrl, settings).then(() => {
        this.saveHeaderTranslations();
        this.$emit('settings-updated', settings, this.displayedValue, refreshList);
        this.$root.$emit('alert-message', this.$t('myApplications.settings.save.success.message'), 'success');
        this.close();
      }).catch(() => {
        this.$root.$emit('alert-message', this.$t('myApplications.settings.save.error.message'), 'error');
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
  },
};
</script>
