<template>
  <div
    class="clickable pt-1 mx-2"
    @click="editNameMode()">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      mdi-form-textbox
    </v-icon>
    <span class="ps-1">{{ $t('document.label.rename') }}</span>
    <v-divider
      v-if="!isMobile"
      class="mt-1 dividerStyle" />
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