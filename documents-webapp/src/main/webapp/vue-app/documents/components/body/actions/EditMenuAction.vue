<template>
  <div
    class="clickable my-10px mx-2"
    @click="editFile()">
    <v-icon
      size="16"
      class="pe-1">
      fas fa-edit
    </v-icon>
    <span class="ps-1 text-body menu-text-color">{{ $t('document.label.edit') }}</span>
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
    editFile() {
      if (this.fileId) {
        window.open(`${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/oeditor?docId=${this.fileId}`, '_blank');
      }
    },
  },
};
</script>