<template>
  <div class="documents-body">
    <extension-registry-component
      v-if="viewExtension"
      :component="viewExtension"
      :params="params"
      :element="div" />
    <div
      class="extendFilterButton"
      v-show="showExtend && extendedSearchEnabled"
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
  },
  data: () => ({
    showExtend: false,
  }),
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
      };
    },
    extendedSearchEnabled() {
      return eXo.env.portal.extendedSearchEnabled && !this.isMobile;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  created() {
    this.$root.$on('enable-extend-filter', this.enableExtendFilter);
    this.$root.$on('disable-extend-filter', this.disableExtendFilter);
  },
  methods: {
    enableExtendFilter(){
      this.showExtend = true;
    },
    disableExtendFilter(){
      this.showExtend = false;
    },
    extendFilter(){
      this.$root.$emit('document-extended-search');
      this.showExtend = false;
    },
  }
};
</script>