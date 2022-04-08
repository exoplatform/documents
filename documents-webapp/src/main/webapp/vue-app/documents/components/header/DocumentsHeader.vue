<template>
  <div class="d-flex flex-row">
    <documents-header-left />
    <v-spacer v-show="!showMobileFilter" />
    <documents-header-center v-show="!showMobileFilter" />
    <v-spacer v-show="!showMobileFilter" />
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
    showMobileFilter() {
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