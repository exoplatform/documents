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
    v-if="hasOfflineFiles"
    flat>
    <div class="d-flex align-center mt-5 mb-2 mx-4">
      <div class="text-header">{{ $t('OfflineApp.pwa.offlineDocuments') }}</div>
      <v-spacer />
      <v-text-field
        v-model="search"
        :label="$t('OfflineApp.pwa.header.search')"
        :prepend-inner-icon="term && 'fa-filter primary--text' || 'fa-filter icon-default-color'"
        height="24"
        class="full-height pa-0 my-0 ms-4"
        single-line
        hide-details />
    </div>
    <v-data-table
      :items="offlineFiles"
      :headers="headers"
      :search="search"
      class="d-flex flex-wrap"
      hide-default-footer
      disable-pagination>
      <template #item="{item}">
        <documents-offline-item
          :file="item"
          class="mb-4 me-4"
          @download="download"
          @preview="openPreview" />
      </template>
    </v-data-table>
    <documents-offline-preview-dialog
      ref="preview"
      @download="download" />
  </v-card>
</template>
<script>
export default {
  data: () => ({
    offlineFiles: [],
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
      this.offlineFiles = await this.$documentOfflineService.getFiles();
    },
    async download(file) {
      const destination = await window.showSaveFilePicker({
        suggestedName: file.name,
        id: 'FavoriteDocuments',
        startIn: 'documents',
      });
      const writable = await destination.createWritable();
      await writable.write(await this.$documentOfflineService.getFileBlob(file.id));
      await writable.close();
    },
    openPreview(file, extension) {
      this.$refs.preview.open(file, extension);
    },
  },
};
</script>