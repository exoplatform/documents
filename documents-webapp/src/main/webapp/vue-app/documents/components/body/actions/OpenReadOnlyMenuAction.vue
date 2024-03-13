<template>
  <div
    class="clickable mx-2 theme--light"
    @click="openReadOnlyFile()">
    <v-icon
      size="13"
      dark
      class="pe-1 iconStyle grey--text text--darken-1">
      fab fa-readme
    </v-icon>
    <span class="ps-1">{{ $t('document.label.open.read.only') }}</span>
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
        window.open(`${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${this.fileId}&mode=view`, '_blank');
      }
    },
  },
};
</script>