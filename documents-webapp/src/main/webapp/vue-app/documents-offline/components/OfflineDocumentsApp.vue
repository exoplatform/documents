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
    class="px-4"
    flat>
    <div class="text-header my-5">{{ $t('OfflineApp.pwa.offlineDocuments') }}</div>
    <div v-if="hasOfflineFiles" class="d-flex flex-wrap">
      <documents-offline-item
        v-for="file in offlineFiles"
        :key="file.id"
        :file="file"
        :local-folder-path="localFolderPath"
        :office-link="isLink"
        class="mb-4 me-4"
        @download="download"
        @preview="openPreview" />
    </div>
    <documents-offline-preview-dialog
      ref="preview"
      @download="download" />
  </v-card>
</template>
<script>
export default {
  data: () => ({
    linkType: null,
    localFolderPath: null,
    offlineFiles: [],
  }),
  computed: {
    hasOfflineFiles() {
      return !!this.offlineFiles?.length;
    },
    isLink() {
      return this.linkType === 'LINK';
    },
  },
  created() {
    this.init();
  },
  methods: {
    async init() {
      this.offlineFiles = await this.$documentOfflineService.getFiles();
      this.linkType = await this.$documentOfflineService.getLinkType();
      if (this.isLink) {
        this.localFolderPath = await this.$documentOfflineService.getLocalFolderPath();
      }
    },
    async download(file) {
      const fileHandle = await window.showSaveFilePicker({
        suggestedName: `${file.id}-${file.name}`,
        id: 'FavoriteDocuments',
      });
      const writable = await fileHandle.createWritable();
      await writable.write(await file.handle.getFile());
      await writable.close();
    },
    openPreview(file, extension) {
      this.$refs.preview.open(file, extension);
    },
  },
};
</script>