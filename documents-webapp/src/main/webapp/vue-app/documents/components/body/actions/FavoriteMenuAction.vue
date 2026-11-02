<template>
  <div
    class="clickable ma-auto py-10px px-2"
    @click.prevent="changeFavorite">
    <documents-favorite-button
      ref="favoriteButton"
      :file="file"
      @added="added"
      @removed="removed" />
    <span class="pa-1">{{ favoriteLabel }}</span>
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
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    favoriteOverride: null,
    displayActionMenu: false,
  }),
  computed: {
    fileFavorite() {
      return !!this.file?.metadatas?.favorites?.length;
    },
    isFavorite() {
      return this.favoriteOverride === null ? this.fileFavorite : this.favoriteOverride;
    },
    favoriteLabel() {
      return this.isFavorite ? this.$t('documents.label.remove.favorite') : this.$t('documents.label.add.favorite');
    },
  },
  watch: {
    fileFavorite() {
      this.favoriteOverride = null;
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
  },
  methods: {
    removed() {
      this.favoriteOverride = false;
      this.$emit('removed');
      if (this.isMobile) {
        this.$root.$emit('close-file-action-menu');
      }
    },
    added() {
      this.favoriteOverride = true;
      this.$emit('added');
      if (this.isMobile) {
        this.$root.$emit('close-file-action-menu');
      }
    },
    changeFavorite() {
      this.$refs.favoriteButton.changeFavorite();
    },
  },
};
</script>