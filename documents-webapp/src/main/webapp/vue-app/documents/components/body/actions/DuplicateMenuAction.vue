<template>
  <div
    class="clickable py-10px px-4"
    @click="duplicate()">
    <v-icon
      size="16"
      class="pe-1">
      fas fa-clone
    </v-icon>
    <span class="ps-1 text-body menu-text-color">{{ $t('documents.label.duplicate') }}</span>
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