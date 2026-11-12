<template>
  <div
    v-show="display"
    :id="`favorite-cell-file-${fileId}`"
    :class="isFavorite ? '' : 'hover-reveal'"
    class="align-center">
    <documents-favorite-button
      :file="file"
      @added="favoriteAdded"
      @removed="favoriteRemoved" />
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
    isFavorite: false,
  }),
  computed: {
    fileId() {
      return this.file?.id;
    },
    display() {
      return !this.isMobile && !this.file?.folder;
    },
  },
  created() {
    this.init();
  },
  methods: {
    init() {
      this.isFavorite = !!this.file?.metadatas?.favorites?.length;
    },
    favoriteAdded() {
      this.isFavorite = true;
    },
    favoriteRemoved() {
      this.isFavorite = false;
    },
  },
};
</script>