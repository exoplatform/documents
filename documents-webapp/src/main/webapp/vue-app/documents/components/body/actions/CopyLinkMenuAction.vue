<template>
  <div
    class="clickable"
    @click="copyLink">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      mdi-link-variant
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.copy.link') }}</span>
    <v-divider
      class="mt-2 dividerStyle" />
  </div>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    }
  },
  methods: {
    copyLink() {
      const inputTemp = $('<input>');
      const path = `${window.location.href}?documentPreviewId=${this.file.id}`;
      $('body').append(inputTemp);
      inputTemp.val(path).select();
      document.execCommand('copy');
      inputTemp.remove();
      this.$root.$emit('show-alert', {type: 'success',message: this.$t('documents.alert.success.label.linkCopied')});
    }
  },
};
</script>