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
    startSearchAfterInMilliseconds: 300,
    showMobileFilter: false,
    timeout: null,
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
        if (this.timeout) {
          window.clearTimeout(this.timeout);
        } 
        this.$root.$emit('document-search', this.query);
        return;
      }
      this.$root.$emit('set-loading',true);this.$root.$emit('set-loading',true);
      if (this.timeout) {
        window.clearTimeout(this.timeout);
      } 
      this.waitForEndTyping(); 
    },
  },
  created() {
    this.$root.$on('resetSearch', this.resetSearch);
    this.$root.$on('filer-query', this.filterQuery);
  },
  methods: {
    filterQuery(query){
      if (this.query === query){
        return;
      }
      this.query = query;
      this.$root.$emit('document-search', this.query);     
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
      this.timeout = window.setTimeout(() => {        
        this.$root.$emit('document-search', this.query);         
      }, this.startSearchAfterInMilliseconds);
    },
  },
};
</script>