<!--

  Copyright (C) 2003 - 2025 eXo Platform SAS.

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License
  as published by the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU Affero General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program. If not, see <gnu.org/licenses>.

-->
<template>
  <exo-drawer
    id="DocumentSettingsDrawer"
    ref="drawer"
    v-model="drawer"
    :loading="loading"
    :right="!$vuetify.rtl"
    go-back-button
    allow-expand>
    <template #title>{{ $t('OfflineApp.pwa.documents.drawer.title') }}</template>
    <template v-if="drawer" #content>
      <v-data-table
        :items="offlineFiles"
        class="d-flex flex-wrap"
        hide-default-header
        hide-default-footer
        disable-pagination>
        <template #item="{item}">
          <documents-offline-item
            :file="item"
            class="mb-4 me-4"
            cell-class="no-border"
            no-dates
            access-badge
            @download="download"
            @preview="openPreview" />
        </template>
      </v-data-table>
      <documents-offline-preview-dialog
        ref="preview"
        @download="download" />
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    drawer: false,
    loading: false,
  }),
  created() {
    this.$root.$on('open-document-offline-files', this.open);
  },
  beforeDestroy() {
    this.$root.$off('open-document-offline-files', this.open);
  },
  methods: {
    async open() {
      this.loading = true;
      try {
        this.$refs.drawer.open();
        this.offlineFiles = await this.$documentOfflineService.getFiles();
      } finally {
        this.loading = false;
      }
    },
    close() {
      this.$refs.drawer.close();
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