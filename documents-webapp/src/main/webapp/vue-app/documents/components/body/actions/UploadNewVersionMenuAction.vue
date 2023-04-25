<template>
  <div
    class="clickable pt-1 mx-2"
    @click="uploadVersion">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fas fa-upload
    </v-icon>
    {{ $t('documents.label.upload.newVersion') }}
    <v-divider
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
  methods: {
    uploadVersion() {
      this.$root.$emit('upload-new-version-action-invoke', this.file);
      if (this.isMobile) {
        this.$root.$emit('close-file-action-menu');
      }
    },
  }
};
</script>