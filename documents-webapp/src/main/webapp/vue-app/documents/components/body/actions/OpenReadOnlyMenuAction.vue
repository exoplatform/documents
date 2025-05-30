<template>
  <div
    class="clickable d-flex  mx-2"
    @click="openReadOnlyFile()">
    <v-icon
      size="16"
      class="pe-1">
      fab fa-readme
    </v-icon>
    <div class="ps-1 text-body menu-text-color">{{ $t('document.label.open.read.only') }}</div>
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