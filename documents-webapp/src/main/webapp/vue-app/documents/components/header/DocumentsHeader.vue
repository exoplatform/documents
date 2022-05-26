<template>
  <div>
    <div class="d-flex flex-row">
      <documents-header-left :selected-view="selectedView" />
      <v-spacer v-show="!canShowMobileFilter" />
      <documents-header-center v-show="!canShowMobileFilter" :selected-view="selectedView" />
      <v-spacer v-show="!canShowMobileFilter" />
      <documents-header-right />
    </div>
    <documents-breadcrumb v-if="selectedView === 'folder'" class="py-4 px-1" />
  </div>
</template>

<script>
export default {
  props: {
    filesSize: {
      type: Number,
      default: 0
    },
    selectedView: {
      type: String,
      default: '',
    },
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