<template>
  <div
    class="documents-body">
    <extension-registry-component
      v-if="viewExtension"
      :component="viewExtension"
      :params="params"
      :element="div" />
    <div
      class="extendFilterButton"
      v-show="showExtend && !this.isMobile"
      @click="extendFilter()">
      <v-icon
        size="24"
        class="extendIcon">
        mdi-file-search
      </v-icon>
      <span> {{ $t('documents.message.extendedSearch') }}</span>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    viewExtension: {
      type: Object,
      default: null,
    },
    files: {
      type: Array,
      default: null,
    },
    loading: {
      type: Boolean,
      default: false,
    },
    initialized: {
      type: Boolean,
      default: false,
    },
    offset: {
      type: Number,
      default: () => 0,
    },
    limit: {
      type: Number,
      default: () => 50,
    },
    pageSize: {
      type: Number,
      default: () => 50,
    },
    hasMore: {
      type: Boolean,
      default: false,
    },
    ascending: {
      type: Boolean,
      default: false,
    },
    sortField: {
      type: String,
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
    primaryFilter: {
      type: String,
      default: null,
    },
    showExtendFilter: {
      type: Boolean,
      default: false
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    selectedView: {
      type: String,
      default: null
    },
    selectedDocuments: {
      type: Array,
      default: () => []
    }
  },
  computed: {
    params() {
      return {
        files: this.files,
        hasMore: this.hasMore,
        ascending: this.ascending,
        pageSize: this.pageSize,
        sortField: this.sortField,
        initialized: this.initialized,
        loading: this.loading,
        offset: this.offset,
        limit: this.limit,
        query: this.query,
        extendedSearch: this.extendedSearch,
        isMobile: this.isMobile,
        selectedView: this.selectedView,
        selectedDocuments: this.selectedDocuments
      };
    },
    showExtend(){
      return this.showExtendFilter;
    }
  },
  methods: {
    extendFilter(){
      this.$root.$emit('document-extended-search');
    },
  }
};
</script>