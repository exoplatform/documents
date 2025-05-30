<template>
  <div>
    <div
      class="clickable d-flex  mx-2"
      @click="editNameMode()">
      <v-icon
        size="16"
        class="pe-1">
        fas fa-pen
      </v-icon>
      <div class="ps-1 pt-1 text-body menu-text-color">{{ $t('document.label.rename') }}</div>
    </div>
    <v-divider
      v-if="!isMobile"
      class="mt-2 dividerStyle" />
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
    }
  },
  data: () => ({
    editName: false
  }),
  methods: {
    editNameMode(){
      this.$root.$emit('update-file-name', this.file);
      if ( this.isMobile ) {
        this.$root.$emit('close-file-action-menu');
      }
    }
  },
};
</script>