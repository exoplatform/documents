<template>
  <div
    class="clickable d-flex  mx-2"
    @click="openLocation()">
    <v-icon
      size="16"
      class="pe-1">
      fa-external-link-alt
    </v-icon>
    <div class="ps-1 pt-1 text-body menu-text-color">{{ $t('document.label.go.location') }}</div>
  </div>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    }
  },
  methods: {
    openLocation() {
      this.$root.$emit('open-folder-by-id', this.file.parentFolderId);
    },
  },
};
</script>./OpenLocationMenuAction.vue