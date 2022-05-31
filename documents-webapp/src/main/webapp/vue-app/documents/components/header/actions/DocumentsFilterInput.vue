<template>
  <div>
    <v-icon
      size="16"
      class="inputDocumentsFilter text-sub-title pa-1 my-auto mt-2"
      v-show="isMobile && !showMobileFilter"
      @click="mobileFilter()">
      fas fa-filter
    </v-icon>
    <v-text-field
      v-model="query"
      ref="inputQuery"
      :placeholder="$t('documents.label.filterDocuments')"
      v-show="isMobile && showMobileFilter || !isMobile"
      :append-icon="appendIcon"
      prepend-inner-icon="fa-filter"
      class="inputDocumentsFilter pa-1 my-auto width-full"
      @click:append="cancelSearch" />
  </div>
</template>
<script>
export default {
  data: () => ({
    query: null,
    startSearchAfterInMilliseconds: 600,
    endTypingKeywordTimeout: 50,
    startTypingKeywordTimeout: 0,
    loading: false,
    showMobileFilter: false,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    appendIcon() {
      return this.query && 'mdi-close primary--text' || null;
    },
  },
  watch: {
    query() {
      if (!this.query) {
        this.loading = false;
        this.$root.$emit('document-search', this.query);
        return;
      }
      this.startTypingKeywordTimeout = Date.now();
      if (!this.loading) {
        this.loading = true;
        this.waitForEndTyping();
      }
    },
  },
  created() {
    this.$root.$on('resetSearch', this.resetSearch);
  },
  methods: {
    mobileFilter(){
      this.showMobileFilter = !this.showMobileFilter;
      this.$root.$emit('show-mobile-filter', this.showMobileFilter);
    },
    cancelSearch(){
      this.query = null;
      this.$refs.inputQuery.blur();
    },
    resetSearch(){
      this.cancelSearch();
      this.mobileFilter();
    },
    waitForEndTyping() {
      window.setTimeout(() => {
        if (Date.now() - this.startTypingKeywordTimeout > this.startSearchAfterInMilliseconds) {
          this.loading = false;
          this.$root.$emit('document-search', this.query);
        } else {
          this.waitForEndTyping();
        }
      }, this.endTypingKeywordTimeout);
    },
  },
};
</script>