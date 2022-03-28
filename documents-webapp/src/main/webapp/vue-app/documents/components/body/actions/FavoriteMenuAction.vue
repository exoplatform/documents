<template>
  <div
    class="clickable mx-2"
    @click.prevent="hitFavoriteButton">
    <favorite-button
      :id="fileId"
      :space-id="spaceId"
      :favorite="isFavorite"
      type="file"
      class="favoriteDoc"
      @removed="removed"
      @remove-error="removeError"
      @added="added"
      @add-error="addError" />
    <span class="pt-1">{{ favoriteLabel }}</span>
    <v-divider class="mt-1 dividerStyle" />
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
  },
  data: () => ({
    isFavorite: false,
    displayActionMenu: false,
  }),
  computed: {
    fileId() {
      return this.file && this.file.id;
    },
    spaceId() {
      return eXo.env.portal.spaceId;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    favoriteLabel() {
      return this.isFavorite ? this.$t('documents.label.remove.favorite') : this.$t('documents.label.add.favorite');
    },
  },
  created() {
    $(document).on('mousedown', () => {
      if (this.displayActionMenu) {
        window.setTimeout(() => {
          this.displayActionMenu = false;
        }, 200);
      }
    });
    this.isFavorite = this.file && this.file.metadatas && this.file.metadatas.favorites && this.file.metadatas.favorites.length;
  },
  methods: {
    removed() {
      this.isFavorite = !this.isFavorite;
      this.displayAlert(this.$t('Favorite.tooltip.SuccessfullyDeletedFavorite', {0: this.$t('file.label')}));
      this.$emit('removed');
      if ( this.isMobile ) {
        this.$root.$emit('close-file-action-menu');
      }
    },
    removeError() {
      this.displayAlert(this.$t('Favorite.tooltip.ErrorDeletingFavorite', {0: this.$t('file.label')}), 'error');
    },
    added() {
      this.isFavorite = !this.isFavorite;
      this.displayAlert(this.$t('Favorite.tooltip.SuccessfullyAddedAsFavorite', {0: this.$t('file.label')}));
      this.$emit('added');
      if ( this.isMobile ) {
        this.$root.$emit('close-file-action-menu');
      }
    },
    addError() {
      this.displayAlert(this.$t('Favorite.tooltip.ErrorAddingAsFavorite', {0: this.$t('file.label')}), 'error');
    },
    hitFavoriteButton() {
      $(`#FavoriteLink_file_${this.fileId}`).click();
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