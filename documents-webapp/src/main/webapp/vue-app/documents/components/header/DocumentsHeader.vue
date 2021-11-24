<template>
  <div class="d-flex flex-row">
    <documents-header-left />
    <v-spacer v-show="!showMobileFilter" />
    <documents-header-right v-if="!isMobile" />
  </div>
</template>

<script>
export default {
  props: {
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    showMobileFilter: false,
  }),
  computed: {
    showMobileFilter() {
      return this.isMobile && this.showMobileFilter;
    },
  },
  created() {
    this.$root.$on('show-mobile-filter', data => {
      this.showMobileFilter= data;
    });
  }
};
</script>