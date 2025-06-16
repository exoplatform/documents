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
    this.init();
  },
  methods: {
    init() {
      this.isFavorite = this.file && this.file.metadatas && this.file.metadatas.favorites && this.file.metadatas.favorites.length;
    },
    async handleFavoriteDocumentRemoved() {
      document.dispatchEvent(new CustomEvent('alert-message', {detail: {
        alertType: 'success',
        alertMessage: this.$t('documents.favoriteRemoved'),
      }}));
      await this.$documentOfflineService.removeFile(this.file);
    },
    async syncFavoriteDocument() {
      await this.$documentOfflineService.saveFile(this.file);
    },
    async handleSyncFavoriteDocument() {
      await this.syncFavoriteDocument();
      this.$root.isFavoritesSynchronized = true;
      this.$root.$emit('alert-message', this.$t('documents.file.synchronizationProcessInitialized'), 'success');
    },
    handleFavoriteDocumentAdded() {
      if (this.$root.isFavoritesSynchronized) {
        document.dispatchEvent(new CustomEvent('alert-message', {detail: {
          alertType: 'success',
          alertMessage: this.$t('documents.favoriteAdded'),
        }}));
        this.syncFavoriteDocument();
      } else {
        document.dispatchEvent(new CustomEvent('alert-message', {detail: {
          alertType: 'success',
          alertMessage: this.$t('documents.favoriteAddedWithSyncChoice'),
          alertLinkCallback: this.handleSyncFavoriteDocument,
          alertLinkText: this.$t('documents.file.synchronizeLocally'),
        }}));
      }
    },
  },
};
</script>