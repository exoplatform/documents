<template>
  <document-action-item
    icon="fas fa-trash"
    icon-extra-class="error-color"
    label-extra-class="error-color"
    :label="$t('documents.label.delete')"
    :is-mobile="isMobile"
    show-divider-above
    @click="deleteAction" />
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
      if (!this.isMultiSelection) {
        this.$root.$emit('confirm-document-deletion', this.file);
        this.$root.$emit('close-file-action-menu');
        const deleteDelay = 6;
        this.$documentFileService.deleteDocument(this.file.path, this.file.id, this.file.favorite, deleteDelay);
      } else {
        this.$root.$emit('documents-bulk-delete');
        this.$root.$emit('close-file-action-menu');
      }

    }
  },
};
</script>