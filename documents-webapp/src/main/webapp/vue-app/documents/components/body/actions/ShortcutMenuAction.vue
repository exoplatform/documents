<template>
  <div
    class="clickable mx-2">
    <v-icon
      size="13"
      class="pe-1 iconStyle">
      fas fa-share-square
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.shortcut') }}</span>
  </div>
</template>
<script>
export default {
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
};
</script>