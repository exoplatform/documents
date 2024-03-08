<template>
  <div
    class="clickable pt-1 mx-2"
    @click="openLocation()">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fa-external-link-alt
    </v-icon>
    <span class="ps-1">{{ $t('document.label.go.location') }}</span>
    <v-divider
      v-if="!file.cloudDriveFolder"
      class="mt-1 dividerStyle" />
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