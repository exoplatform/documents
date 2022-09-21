<template>
  <div
    class="clickable ma-auto pt-1 mx-2"
    @click="showVersionHistory">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fa fa-history
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.showVersionHistory') }}</span>
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
    }
  },
  methods: {
    showVersionHistory() {
      this.$root.$emit('show-version-history', this.file?.id);
    }
  }
};
</script>