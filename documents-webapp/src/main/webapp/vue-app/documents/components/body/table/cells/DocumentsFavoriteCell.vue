<template>
  <div 
    v-show="isFavorite || isMobile"
    :id="`favorite-cell-file-${fileId}`" 
    :class="isMobile ? 'position-relative' : ''">
    <div v-if="!isMobile">
      <favorite-button
        :id="fileId"
        :space-id="spaceId"
        :favorite="isFavorite"
        type="file"
        @removed="removed"
        @remove-error="removeError"
        @added="added"
        @add-error="addError" />
    </div>
    <div v-else>
      <v-icon
        class="pa-0 text-sub-title d-block"
        @click="displayActionMenu = !displayActionMenu">
        mdi-dots-vertical
      </v-icon>
      <v-menu
        v-model="displayActionMenu"
        :attach="`#favorite-cell-file-${fileId}`"
        transition="slide-x-reverse-transition"
        content-class="fileActionMenu"
        offset-y
        offset-x>
        <v-list class="pa-0" dense>
          <v-list-item 
            class="px-2" 
            @click.prevent="hitFavoriteButton">
            <v-list-item-icon class="mr-0 mb-3">
              <favorite-button
                :id="fileId"
                :space-id="spaceId"
                :favorite="isFavorite"
                type="file"
                @removed="removed"
                @remove-error="removeError"
                @added="added"
                @add-error="addError" />
            </v-list-item-icon>
            <v-list-item-title class="subtitle-2">
              <span>{{ favoriteLabel }}</span>
            </v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </div>
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
  mounted() {
    // show favorite button when hovering over the corresponding row.
    if (!this.isMobile) {
      const self = this;
      $(`#favorite-cell-file-${this.fileId}`).parent().parent().parent().hover(function () {
        if (!self.isFavorite) {
          $(`#favorite-cell-file-${self.fileId}`).show();
        }
      }, function () {
        if (!self.isFavorite) {
          $(`#favorite-cell-file-${self.fileId}`).hide();
        }
      });
    }
  },
  methods: {
    removed() {
      this.isFavorite = !this.isFavorite;
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
    },
    addError() {
      this.displayAlert(this.$t('Favorite.tooltip.ErrorAddingAsFavorite', {0: this.$t('file.label')}), 'error');
    },
    hitFavoriteButton() {
      $(`#FavoriteLink_file_${this.fileId}`).click();
    },
    displayAlert(message, type) {
      this.$root.$emit('activity-notification-alert', {
        activityId: this.activityId,
        message,
        type: type || 'success',
      });
    },
  },
};
</script>