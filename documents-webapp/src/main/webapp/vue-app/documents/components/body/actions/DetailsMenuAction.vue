<template>
  <document-action-item
    icon="fa-info-circle"
    :label="$t('documents.drawer.details.title')"
    @click="displayDetails" />
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
  data: () => ({
    editName: false
  }),
  methods: {
    displayDetails(){
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: this.file.id}));
      if (this.isMobile) {
        this.$root.$emit('close-file-action-menu');
      }
    }
  },
};
</script>