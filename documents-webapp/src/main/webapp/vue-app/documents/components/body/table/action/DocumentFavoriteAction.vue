<template>
  <favorite-button
    :id="fileId"
    :space-id="spaceId"
    :favorite="isFavorite"
    type="file"
    type-label="Documents"
    @removed="removed"
    @remove-error="removeError"
    @added="added"
    @add-error="addError" />
</template>

<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    isFavorite: false,
  }),
  computed: {
    fileId() {
      return this.file && this.file.id;
    },
    spaceId() {
      return eXo.env.portal.spaceId;
    },
  },
  created() {
    this.isFavorite = this.file && this.file.metadatas && this.file.metadatas.favorites && this.file.metadatas.favorites.length;
  },
  methods: {
    removed() {
      this.isFavorite = !this.isFavorite;
      this.$root.$emit('documents-refresh-files');
      this.displayAlert(this.$t('Favorite.tooltip.SuccessfullyDeletedFavorite', {0: this.$t('file.label')}));
      this.$emit('removed');
    },
    removeError() {
      this.displayAlert(this.$t('Favorite.tooltip.ErrorDeletingFavorite', {0: this.$t('file.label')}), 'error');
    },
    added() {
      this.isFavorite = !this.isFavorite;
      this.displayAlert(this.$t('Favorite.tooltip.SuccessfullyAddedAsFavorite', {0: this.$t('file.label')}));
      this.$emit('added');
      this.$root.$emit('documents-refresh-files');
    },
    addError() {
      this.displayAlert(this.$t('Favorite.tooltip.ErrorAddingAsFavorite', {0: this.$t('file.label')}), 'error');
    },
    displayAlert(message, type) {
      document.dispatchEvent(new CustomEvent('attachments-notification-alert', {
        detail: {
          messageObject: {
            message: message,
            type: type || 'success',
          }
        }
      }));
    },
  },
};
</script>