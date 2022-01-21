<template>
  <div
    class="clickable"
    @click="editFile">
    <v-icon
      size="18"
      class="primary--text pe-1">
      mdi-square-edit-outline
    </v-icon>
    <span>{{ $t('document.label.edit') }}</span>
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
  computed: {
    fileId() {
      return this.file && this.file.id;
    },
  },
  methods: {
    editFile() {
      if (this.fileId) {
        window.open(`${eXo.env.portal.context}/${eXo.env.portal.portalName}/oeditor?docId=${this.fileId}&source=peview`, '_blank');
      }
    },
  },
};
</script>