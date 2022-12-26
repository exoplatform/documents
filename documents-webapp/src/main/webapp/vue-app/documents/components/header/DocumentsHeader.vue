<template>
  <div>
    <div class="d-flex flex-row">
      <documents-header-left
        v-if="canAdd"
        :selected-view="selectedView" />
      <v-spacer v-show="!canShowMobileFilter" />
      <documents-header-center v-if="!canShowMobileFilter" :selected-view="selectedView" />
      <v-spacer v-show="!canShowMobileFilter" />
      <documents-header-right :query="query" />
    </div>
    <documents-breadcrumb v-show="showBreadcrumb" v-if="selectedView === 'folder'" class="py-4 px-1" />
  </div>
</template>

<script>
export default {
  props: {
    canAdd: {
      type: Boolean,
      default: false
    },
    filesSize: {
      type: Number,
      default: 0
    },
    selectedView: {
      type: String,
      default: '',
    },
    query: {
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
    showBreadcrumb(){
      return !this.query;
    }
  },
  created() {
    this.$root.$on('show-mobile-filter', data => {
      this.showMobileFilter= data;
    });
  },
};
</script>