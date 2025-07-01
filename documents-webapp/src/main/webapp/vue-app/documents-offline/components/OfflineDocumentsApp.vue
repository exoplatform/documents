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
  <v-card
    v-if="!offlineModeEnabled && initialized"
    class="d-flex flex-column align-center justify-center"
    min-height="50vh"
    min-width="100%"
    flat>
    <v-icon size="75" class="tertiary-color mt-auto mb-5">fa-wifi</v-icon>
    <div class="text-title mb-auto mx-5 text-truncate">{{ $t('OfflineApp.pwa.noSiteConnection') }}</div>
  </v-card>
  <v-card
    v-else-if="initialized"
    color="transparent"
    flat>
    <application-toolbar
      ref="applicationToolbar"
      :right-text-filter="hasOfflineFiles && {
        minCharacters: 3,
        placeholder: $t('OfflineApp.pwa.header.search.placeholder'),
        tooltip: $t('OfflineApp.pwa.header.search')
      }"
      compact
      @filter-text-input-end-typing="search = $event">
      <template #left>
        <div class="text-title">{{ $t('OfflineApp.pwa.offlineDocuments') }}</div>
      </template>
    </application-toolbar>
    <v-data-table
      v-if="hasOfflineFiles"
      :items="offlineFiles"
      :headers="headers"
      :search="search"
      class="d-flex flex-wrap"
      hide-default-footer
      disable-pagination>
      <template #item="{item}">
        <documents-offline-item
          :file="item"
          cell-class="no-border"
          class="mb-4 me-4"
          @download="download"
          @preview="openPreview" />
      </template>
    </v-data-table>
    <v-card
      v-else
      class="d-flex flex-column align-center justify-center"
      min-height="180"
      min-width="100%"
      flat>
      <v-icon size="75" class="tertiary-color mt-auto mb-5">fa-file-alt</v-icon>
      <div class="mb-auto mx-5 text-truncate">{{ $t('OfflineApp.pwa.offlineBookmarkDocumentsCta') }}</div>
    </v-card>
    <documents-offline-preview-dialog
      v-if="hasOfflineFiles"
      ref="preview"
      @download="download" />
  </v-card>
</template>
<script>
export default {
  data: () => ({
    offlineFiles: [],
    offlineModeEnabled: false,
    initialized: false,
    search: '',
  }),
  computed: {
    hasOfflineFiles() {
      return !!this.offlineFiles?.length;
    },
    headers() {
      return [{
        text: this.$t('OfflineApp.pwa.header.name'),
        align: 'left',
        class: 'ps-14',
        sortable: true,
        value: 'name'
      }, {
        text: this.$t('OfflineApp.pwa.header.lastModified'),
        align: 'center',
        sortable: true,
        value: 'modifiedDate',
        width: '150px',
      }, {
        text: this.$t('OfflineApp.pwa.header.downloadTime'),
        align: 'center',
        sortable: true,
        value: 'downloadTime',
        width: '150px',
      }, {
        text: this.$t('OfflineApp.pwa.header.actions'),
        align: 'center',
        sortable: false,
        value: 'name',
        width: '75px',
      }];
    },
  },
  created() {
    this.init();
  },
  methods: {
    async init() {
      try {
        this.offlineFiles = await this.$documentOfflineService.getFiles();
        this.offlineModeEnabled = await this.$documentOfflineService.isDatabaseExists();
      } finally {
        this.initialized = true;
      }
    },
    async download(file) {
      const destination = await window.showSaveFilePicker({
        suggestedName: file.name,
        id: 'FavoriteDocuments',
        startIn: 'documents',
      });
      const writable = await destination.createWritable();
      await writable.write(await this.$documentOfflineService.getFileBlob(file.id, true));
      await writable.close();
    },
    openPreview(file, extension) {
      this.$refs.preview.open(file, extension, true);
    },
  },
};
</script>