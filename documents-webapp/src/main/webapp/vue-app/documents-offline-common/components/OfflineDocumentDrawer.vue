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
    :go-back-button="goBackButton"
    allow-expand
    @expand-updated="expanded = $event">
    <template #title>{{ $t('OfflineApp.pwa.documents.drawer.title') }}</template>
    <template v-if="drawer" #content>
      <v-card
        :color="expanded ? 'light-grey-background-color' : 'transparent'"
        min-height="calc(var(--100vh, 100vh) - 61px)"
        class="d-flex"
        flat>
        <v-card
          :class="expanded && 'page-content pa-5'"
          class="d-flex flex-column"
          min-height="100%"
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
            class="flex-grow-0 flex-shrink-0"
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
            :class="expanded ? 'px-4' : 'px-3 py-2'"
            class="d-flex flex-wrap flex-grow-1 flex-shrink-0"
            hide-default-footer
            disable-pagination>
            <template #item="{item}">
              <documents-offline-item
                :file="item"
                :no-dates="!expanded"
                :hide-download="!expanded"
                :actions-cell-width="actionsCellWidth"
                cell-class="no-border"
                class="mb-4 me-4"
                access-badge
                allow-upload
                info-icon
                @download="download"
                @preview="openPreview"
                @updated="retrieveList" />
            </template>
          </v-data-table>
        </v-card>
      </v-card>
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
    goBackButton: false,
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
    hasOfflineAccessTime() {
      return !!this.offlineFiles?.find(f => f.offlineAccessTime);
    },
    actionsCellWidth() {
      let width = 90;
      if (this.hasOfflineAccessTime) {
        width += 32;
      }
      return width;
    },
  },
  created() {
    this.$root.$on('open-document-offline-files', this.open);
  },
  beforeDestroy() {
    this.$root.$off('open-document-offline-files', this.open);
  },
  methods: {
    async open(goBackButton) {
      this.goBackButton = goBackButton;
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