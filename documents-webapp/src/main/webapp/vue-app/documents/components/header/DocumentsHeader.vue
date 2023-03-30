<template>
  <div>
    <div class="d-flex flex-row">
      <documents-header-left
        v-if="canAdd"
        :selected-view="selectedView" 
        :is-mobile="isMobile"
        :selected-documents="selectedDocuments" />
      <v-spacer />
      <documents-header-center
        v-if="!canShowMobileFilter"
        :selected-view="selectedView"
        :is-mobile="isMobile" />
      <v-spacer />
      <documents-header-right
        :query="query"
        :primary-filter="primaryFilter"
        :is-mobile="isMobile" />
    </div>
    <documents-breadcrumb
      v-if="selectedView === 'folder'"
      v-show="showBreadcrumb"
      :is-mobile="isMobile"
      class="py-4 px-1" />
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
    primaryFilter: {
      type: String,
      default: 'all',
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    selectedDocuments: {
      type: Array,
      default: () => []
    }
  },
  data: () => ({
    showMobileFilter: false,
  }),
  computed: {
    canShowMobileFilter() {
      return this.isMobile && this.showMobileFilter;
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