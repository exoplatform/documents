<template>
  <div
    class="clickable d-flex  mx-2"
    @click="duplicate()">
    <v-icon
      size="16"
      class="pe-1">
      fas fa-clone
    </v-icon>
    <div class="ps-1 pt-1 text-body menu-text-color">{{ $t('documents.label.duplicate') }}</div>
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