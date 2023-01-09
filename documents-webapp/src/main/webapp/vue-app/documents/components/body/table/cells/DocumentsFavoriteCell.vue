<template>
  <div 
    v-show="isFavorite && !file.folder"
    :id="`favorite-cell-file-${fileId}`">
    <div v-if="!isMobile">
      <documents-favorite-action :file="file"  :is-mobile="isMobile" />
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
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    fileId() {
      return this.file && this.file.id;
    },
    spaceId() {
      return eXo.env.portal.spaceId || 0;
    },
    isFavorite() {
      return this.file && this.file.metadatas && this.file.metadatas.favorites && this.file.metadatas.favorites.length;
    }
  },
  created() {
    this.isFavorite = this.file && this.file.metadatas && this.file.metadatas.favorites && this.file.metadatas.favorites.length;
  },
  mounted() {
    // show favorite button when hovering over the corresponding row.
    if (!this.isMobile) {
      const self = this;
      $(`#favorite-cell-file-${this.fileId}`).parent().parent().parent().hover(function () {
        if (!self.isFavorite && !self.file.folder) {
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
    hitFavoriteButton() {
      $(`#FavoriteLink_file_${this.fileId}`).click();
    },
  },
};
</script>