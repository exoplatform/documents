<template>
  <document-action-item
    icon="fas fa-edit"
    :label="$t('document.label.edit')"
    :href="editLink"
    target="_blank" />
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
  computed: {
    fileId() {
      return this.file && this.file.sourceID || this.file.id;
    },
    editLink() {
      return this.fileId && `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${this.fileId}&backTo=${window.location.pathname}` || null;
    },
  },
};
</script>