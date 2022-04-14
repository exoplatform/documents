<template>
  <div class="d-flex flex-row">
    <documents-header-left />
    <v-spacer v-show="!canShowMobileFilter" />
    <documents-header-center v-show="!canShowMobileFilter" />
    <v-spacer v-show="!canShowMobileFilter" />
    <documents-header-right />
  </div>
</template>

<script>
export default {
  props: {
    filesSize: {
      type: Number,
      default: 0
    }
  },
  data: () => ({
    showMobileFilter: false,
  }),
  computed: {
    canShowMobileFilter() {
      return this.isMobile && this.showMobileFilter;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  created() {
    this.$root.$on('show-mobile-filter', data => {
      this.showMobileFilter= data;
    });
  },
};
</script>