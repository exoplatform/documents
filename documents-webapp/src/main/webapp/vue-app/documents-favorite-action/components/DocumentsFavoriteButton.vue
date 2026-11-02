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
  <favorite-button
    ref="favoriteButton"
    :id="fileId"
    :space-id="spaceId"
    :favorite="isFavorite"
    :small="small"
    :top="top"
    :right="right"
    type="file"
    type-label="Documents"
    class="favoriteDoc"
    @added="handleFavoriteDocumentAdded"
    @removed="handleFavoriteDocumentRemoved"
    @add-error="addError"
    @remove-error="removeError" />
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    standalone: {
      type: Boolean,
      default: false,
    },
    small: {
      type: Boolean,
      default: true,
    },
    top: {
      type: Number,
      default: () => 0,
    },
    right: {
      type: Number,
      default: () => 0,
    },
  },
  data: () => ({
    fileToDownload: false,
    handle: null,
    favoritesSynchronized: false,
    pwaEnabled: false,
  }),
  computed: {
    fileId() {
      return this.file && this.file.id;
    },
    spaceId() {
      return eXo.env.portal.spaceId || 0;
    },
    isFavorite() {
      return !!this.file?.metadatas?.favorites?.length;
    },
    isFavoritesSynchronized() {
      return this.$root.isFavoritesSynchronized || this.favoritesSynchronized;
    },
    isPwaEnabled() {
      return this.$root.pwaEnabled || this.pwaEnabled;
    },
  },
  created() {
    this.$root.$on('documents-offline-favorite-sync-clear', this.setNoDownload);
    this.$root.$on('documents-offline-settings-updated', this.downloadFile);
    if (this.standalone) {
      this.init();
    }
  },
  beforeDestroy() {
    this.$root.$off('documents-offline-favorite-sync-clear', this.setNoDownload);
    this.$root.$off('documents-offline-settings-updated', this.downloadFile);
  },
  methods: {
    async init() {
      const registration = await navigator?.serviceWorker?.getRegistration?.();
      this.pwaEnabled = !!registration;
      this.favoritesSynchronized = this.pwaEnabled && (await this.$documentOfflineService.isOfflineDocumentsEnabled());
    },
    // Begin: API to use by parent component
    changeFavorite() {
      this.$refs.favoriteButton.changeFavorite();
    },
    // End: API to use by parent component
    setNoDownload() {
      this.fileToDownload = true;
    },
    downloadFile() {
      if (this.standalone) {
        this.init();
      }
      if (this.fileToDownload) {
        if (this.isFavoritesSynchronized) {
          this.handleFavoriteDocumentAdded();
        } else {
          window.setTimeout(() => this.handleFavoriteDocumentAdded(), 200);
        }
      }
    },
    async handleFavoriteDocumentRemoved() {
      this.$emit('removed');
      document.dispatchEvent(new CustomEvent('alert-message', {detail: {
        alertType: 'success',
        alertMessage: this.$t('documents.favoriteRemoved'),
      }}));
      if (this.isFavoritesSynchronized) {
        await this.$documentOfflineService.removeFile(this.file);
      }
    },
    async handleFavoriteDocumentAdded() {
      this.$emit('added');
      document.dispatchEvent(new CustomEvent('alert-message', {detail: {
        alertType: 'success',
        alertMessage: this.$t('documents.favoriteAdded'),
      }}));
      if (this.isFavoritesSynchronized && this.isPwaEnabled) {
        await this.$documentOfflineService.saveFile(this.file.id);
      }
    },
    removeError() {
      this.$root.$emit('alert-message', this.$t('Favorite.tooltip.ErrorDeletingFavorite', {0: this.$t('file.label')}), 'error');
      this.$emit('remove-error');
    },
    addError() {
      this.$root.$emit('alert-message', this.$t('Favorite.tooltip.ErrorAddingAsFavorite', {0: this.$t('file.label')}), 'error');
      this.$emit('add-error');
    },
    handleSyncFavoriteDocument() {
      this.$root.$emit('documents-offline-favorite-sync-clear');
      this.$root.$emit('documents-offline-settings-open');
      this.$root.$emit('close-alert-message');
      this.fileToDownload = true;
    },
  },
};
</script>