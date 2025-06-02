<template>
  <div
    class="clickable my-10px mx-2"
    @click="openLocation()">
    <v-icon
      size="16"
      class="pe-1">
      fa-external-link-alt
    </v-icon>
    <span class="ps-1 text-body menu-text-color">{{ $t('document.label.go.location') }}</span>
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