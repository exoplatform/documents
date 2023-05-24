<template>
  <div>
    <div v-if="!showFilter" class="d-flex flex-row">
      <documents-header-left
        v-if="canAdd"
        :selected-view="selectedView" 
        :is-mobile="isMobile"
        :selected-documents="selectedDocuments" />
      <v-spacer />
      <documents-header-center
        :selected-view="selectedView"
        :is-mobile="isMobile" />
      <v-spacer />
      <documents-header-right
        :query="query"
        :primary-filter="primaryFilter"
        :file-type="fileType"
        :after-date="afterDate"
        :befor-date="beforDate"
        :min-size="minSize"
        :max-size="maxSize"
        :is-mobile="isMobile" />
    </div>
    <div>
      <documents-filter-conatiner
        :query="query"
        :primary-filter="primaryFilter"
        :file-type="fileType"
        :after-date="afterDate"
        :befor-date="beforDate"
        :min-size="minSize"
        :max-size="maxSize"
        :is-mobile="isMobile" />
    </div>
    <documents-breadcrumb
      v-if="selectedView === 'folder'"
      v-show="showBreadcrumb"
      :is-mobile="isMobile"
      class="pt-4 px-1" />
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
    fileType: {
      type: Array,
      default: () => []
    },
    afterDate: {
      type: Number,
      default: null,
    },
    beforDate: {
      type: Number,
      default: null,
    },
    minSize: {
      type: Number,
      default: null,
    },
    maxSize: {
      type: Number,
      default: null,
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
    showFilter: false,
  }),
  computed: {
    canShowMobileFilter() {
      return this.isMobile && this.showFilter;
    },
    showBreadcrumb(){
      return !this.query;
    }
  },
  created() {
    this.$root.$on('show-mobile-filter', data => {
      this.showFilter= data;
    });
  },
};
</script>