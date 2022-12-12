<template>
  <div>
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
    <div
      class="extendFilterButton"
      v-show="showExtend"
      @click="extendFilter()">
      <v-icon
        size="24"
        class="extendIcon">
        mdi-file-search
      </v-icon>
      <span> {{ extendFilterMessage }}</span>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    query: {
      type: String,
      default: null,
    },
    extendFilterMessage: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    startSearchAfterInMilliseconds: 600,
    endTypingKeywordTimeout: 50,
    startTypingKeywordTimeout: 0,
    loading: false,
    showMobileFilter: false,
    showExtend: false,
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
        this.showExtend = false;
        this.$emit('filterQuery', this.query);
        return;
      } else {
        this.showExtend = true;
      }
      this.startTypingKeywordTimeout = Date.now();
      if (!this.loading) {
        this.loading = true;
        this.waitForEndTyping();
      }
    },
  },
  methods: {
    filterQuery(query){
      if (this.query === query){
        return;
      }
      this.query = query;
      this.$emit('filterQuery', this.query);
      if (this.query) {
        this.showExtend = true;
      }
    },
    extendFilter(){
      this.$emit('extendFilter', this.query);
      this.showExtend = false;
    },
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
          this.$emit('filterQuery', this.query);
          if (this.query) {
            this.showExtend = true;
          }
        } else {
          this.waitForEndTyping();
        }
      }, this.endTypingKeywordTimeout);
    },
  },
};
</script>