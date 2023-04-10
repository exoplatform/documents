<template>
  <div
    class="clickable pt-1 mx-2"
    @click="duplicate()">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fas fa-clone
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.duplicate') }}</span>
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