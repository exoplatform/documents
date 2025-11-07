<template>
  <document-action-item
    icon="fab fa-readme"
    :label="$t('document.label.open.read.only')"
    @click="openReadOnlyFile" />
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
  },
  methods: {
    openReadOnlyFile() {
      if (this.fileId) {
        window.open(`${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${this.fileId}&mode=view&backTo=${window.location.pathname}`, '_blank');
      }
    },
  },
};
</script>