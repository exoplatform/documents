<template>
  <div
    class="clickable mx-2"
    @click="editFile()">
    <v-icon
      size="13"
      dark
      class="pe-1 iconStyle">
      fas fa-edit
    </v-icon>
    <span class="ps-1">{{ $t('document.label.edit') }}</span>
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
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
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