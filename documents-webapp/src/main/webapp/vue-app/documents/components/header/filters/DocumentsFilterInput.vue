<template>
  <v-text-field
    v-model="query"
    ref="inputQuery"
    autofocus
    :placeholder="$t('documents.label.filterDocuments')"
    :append-icon="appendIcon"
    class="pa-2 my-auto width-full inputDocumentsFilter"
    @click:append="cancelSearch" />
</template>
<script>
export default {
  props: {
    query: {
      type: String,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false
    },
  },
  data: () => ({
    startSearchAfterInMilliseconds: 300,
    showFilter: false,
    timeout: null,
  }),
  computed: {
    appendIcon() {
      return this.query && 'mdi-close primary--text' || null;
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
  mounted(){
    this.$el.parentElement.classList.add('fill-available'); //Since :has css pseudo-class is not supported in firefox, we need to add the class to parent manually  
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
      this.showFilter = !this.showFilter;
      this.$root.$emit('show-mobile-filter', this.showFilter);
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