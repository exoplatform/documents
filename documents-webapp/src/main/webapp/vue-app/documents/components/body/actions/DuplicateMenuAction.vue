<template>
  <document-action-item
    icon="fas fa-clone"
    :label="$t('documents.label.duplicate')"
    @click="duplicate" />
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
  data: () => ({
    workspace: 'collaboration',
  }),
  methods: {
    duplicate(){
      this.$root.$emit('duplicate-document',this.file);
      if ( this.isMobile ) {
        this.$root.$emit('close-file-action-menu');
      }
    }
  },
};
</script>