<template>
  <extension-registry-component
    :component="extension"
    :params="params"
    :element="div" />
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    query: {
      type: String,
      default: null,
    },
    extendedSearch: {
      type: Boolean,
      default: false,
    },
    extension: {
      type: Object,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    selectedView: {
      type: String,
      default: null
    },
    isSearchResult: {
      type: Boolean,
      default: false
    },
    selectedDocuments: {
      type: Array,
      default: () => []
    },
    selectAllChecked: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    params() {
      return {
        file: this.file,
        query: this.query,
        extendedSearch: this.extendedSearch,
        extension: this.extension,
        isMobile: this.isMobile,
        selectedView: this.selectedView,
        isSearchResult: this.isSearchResult,
        selectedDocuments: this.selectedDocuments,
        selectAllChecked: this.selectAllChecked
      };
    },
  },
};
</script>