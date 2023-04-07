<template>
  <div
    class="clickable mx-2"
    @click="deleteAction()">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fas fa-trash
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.delete') }}</span>
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
    },
    disabledExtension: {
      type: Boolean,
      default: false
    },
    isMultiSelection: {
      type: Boolean,
      default: false
    },
  },
  methods: {
    deleteAction() {
      this.$root.$emit('confirm-document-deletion', this.file);
      this.$root.$emit('close-file-action-menu');
      const deleteDelay = 6;
      this.$documentFileService.deleteDocument(this.file.path, this.file.id, this.file.favorite, deleteDelay);
    }
  },
};
</script>