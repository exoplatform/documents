<template>
  <document-action-item
    icon="fa-external-link-alt"
    :label="$t('document.label.go.location')"
    @click="openLocation" />
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
</script>