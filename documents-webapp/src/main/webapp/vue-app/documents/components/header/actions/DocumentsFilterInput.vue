<template>
  <div>
    <v-icon
      size="24"
      class="inputDocumentsFilter text-sub-title pa-1 my-auto mt-2"
      v-show="isMobile && !showMobileFilter"
      @click="mobileFilter()">
      {{filterIcon}}
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
  props: {
    query: {
      type: String,
      default: null,
    },
    primaryFilter: {
      type: String,
      default: 'all',
    },
    isMobile: {
      type: Boolean,
      default: false
    },
  },
  data: () => ({
    startSearchAfterInMilliseconds: 300,
    showMobileFilter: false,
    timeout: null,
  }),
  computed: {
    appendIcon() {
      return this.query && 'mdi-close primary--text' || null;
    },
    filterIcon() {
      return (this.query!=null && this.query.length > 0)  || this.primaryFilter !== 'all'  ? 'mdi-filter' : 'mdi-filter-outline';
    }
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
    this.$root.$on('resetSearch', this.cancelSearch);
    this.$root.$on('filer-query', this.filterQuery);
    this.$root.$on('mobile-filter', this.mobileFilter);
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
    waitForEndTyping() {
      this.timeout = window.setTimeout(() => {        
        this.$root.$emit('document-search', this.query);         
      }, this.startSearchAfterInMilliseconds);
    },
  },
};
</script>