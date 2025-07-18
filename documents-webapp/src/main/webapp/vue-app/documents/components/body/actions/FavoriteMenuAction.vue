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
    isFavorite: false,
    displayActionMenu: false,
  }),
  computed: {
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
    this.isFavorite = !!this.file?.metadatas?.favorites?.length;
  },
  methods: {
    removed() {
      this.isFavorite = false;
      this.$emit('removed');
      if (this.isMobile) {
        this.$root.$emit('close-file-action-menu');
      }
    },
    added() {
      this.isFavorite = true;
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