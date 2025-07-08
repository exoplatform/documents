<template>
  <div>
    <v-divider
      v-if="!isMobile"
      class="mt-1 dividerStyle" />
    <div
      class="clickable py-10px px-4"
      @click="deleteAction()">
      <v-icon
        size="16"
        class="pe-1 error-color">
        fas fa-trash
      </v-icon>
      <span class="ps-1 ml-n2px text-body error-color">{{ $t('documents.label.delete') }}</span>
    </div>
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