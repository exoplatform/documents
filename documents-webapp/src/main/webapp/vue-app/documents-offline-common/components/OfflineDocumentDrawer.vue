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
    allow-expand
    @expand-updated="expanded = $event">
    <template #title>{{ $t('OfflineApp.pwa.documents.drawer.title') }}</template>
    <template v-if="drawer" #content>
      <div class="layout-page-body light-grey-background-color">
        <v-card
          :class="expanded && 'page-content pa-5'"
          color="transparent"
          flat>
          <application-toolbar
            v-if="expanded"
            ref="applicationToolbar"
            :right-text-filter="{
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
            v-bind="expanded ? {
              headers,
              search,
            } : {
              'hide-default-header': true,
            }"
            :items="offlineFiles"
            class="d-flex flex-wrap"
            hide-default-footer
            disable-pagination>
            <template #item="{item}">
              <documents-offline-item
                :file="item"
                :no-dates="!expanded"
                class="mb-4 me-4"
                cell-class="no-border"
                access-badge
                allow-upload
                info-icon
                @download="download"
                @preview="openPreview"
                @updated="retrieveList" />
            </template>
          </v-data-table>
        </v-card>
      </div>
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
    expanded: false,
    offlineFiles: [],
    initialized: false,
    search: '',
  }),
  computed: {
    headers() {
      return this.expanded && [{
        text: this.$t('OfflineApp.pwa.header.name'),
        align: 'start',
        sortable: true,
        value: 'name'
      }, {
        text: this.$t('OfflineApp.pwa.header.lastModified'),
        align: 'center',
        sortable: true,
        value: 'modifiedDate',
        width: '175px',
      }, {
        text: this.$t('OfflineApp.pwa.header.downloadTime'),
        align: 'center',
        sortable: true,
        value: 'downloadTime',
        width: '175px',
      }, {
        text: this.$t('OfflineApp.pwa.header.actions'),
        align: 'end',
        sortable: false,
        value: 'name',
        width: '150px',
      }] || null;
    },
  },
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
        await this.retrieveList();
      } finally {
        this.loading = false;
      }
    },
    close() {
      this.$refs.drawer.close();
    },
    async retrieveList() {
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