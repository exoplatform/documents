<template>
  <div
    class="align-center"
    v-show="display"
    :id="`favorite-cell-file-${fileId}`">
    <documents-favorite-action
      :file="file"
      :is-mobile="isMobile"
      @added="handleFavoriteDocumentAdded"
      @removed="handleFavoriteDocumentRemoved" />
  </div>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    extension: {
      type: Object,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    hover: {
      type: Boolean,
      default: false
    },
  },
  data: () => ({
    fileToDownload: false,
    handle: null,
  }),
  computed: {
    fileId() {
      return this.file && this.file.id;
    },
    spaceId() {
      return eXo.env.portal.spaceId || 0;
    },
    isFavorite() {
      return this.file && this.file.metadatas && this.file.metadatas.favorites && this.file.metadatas.favorites.length;
    },
    display() {
      return !this.isMobile && !this.file?.folder && (this.isFavorite || this.hover);
    },
  },
  created() {
    this.$root.$on('documents-offline-favorite-sync-clear', this.setNoDownload);
    this.$root.$on('documents-offline-settings-updated', this.downloadFile);
    this.init();
  },
  beforeDestroy() {
    this.$root.$off('documents-offline-favorite-sync-clear', this.setNoDownload);
    this.$root.$off('documents-offline-settings-updated', this.downloadFile);
  },
  methods: {
    init() {
      this.isFavorite = this.file && this.file.metadatas && this.file.metadatas.favorites && this.file.metadatas.favorites.length;
    },
    setNoDownload() {
      this.fileToDownload = true;
    },
    downloadFile() {
      if (this.fileToDownload) {
        if (this.$root.isFavoritesSynchronized) {
          this.handleFavoriteDocumentAdded();
        } else {
          window.setTimeout(() => this.handleFavoriteDocumentAdded(), 200);
        }
      }
    },
    async handleFavoriteDocumentRemoved() {
      document.dispatchEvent(new CustomEvent('alert-message', {detail: {
        alertType: 'success',
        alertMessage: this.$t('documents.favoriteRemoved'),
      }}));
      if (this.$root.isFavoritesSynchronized) {
        await this.$documentOfflineService.removeFile(this.file);
      }
    },
    async handleFavoriteDocumentAdded() {
      if (this.$root.isFavoritesSynchronized || !this.$root.pwaEnabled) {
        document.dispatchEvent(new CustomEvent('alert-message', {detail: {
          alertType: 'success',
          alertMessage: this.$t('documents.favoriteAdded'),
        }}));
        if (this.$root.isFavoritesSynchronized) {
          await this.$documentOfflineService.saveFile(this.file);
        }
      } else {
        document.dispatchEvent(new CustomEvent('alert-message', {detail: {
          alertType: 'success',
          alertMessage: this.$t('documents.favoriteAddedWithSyncChoice'),
          alertLinkCallback: this.handleSyncFavoriteDocument,
          alertLinkText: this.$t('documents.file.synchronizeLocally'),
        }}));
      }
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