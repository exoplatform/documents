<template>
  <div
    class="clickable"
    @click="deleteAction">
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
    documentId: {
      type: String,
      default: '1',
    }
  },
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  methods: {
    deleteAction() {
      this.$root.$emit('confirm-document-deletion', this.documentId);
      if ( this.isMobile ) {
        this.$root.$emit('close-file-action-menu');
      }
    }
  },
};
</script>