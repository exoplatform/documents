<template>
  <div>
    <common-search-input
      ref="commonInput"
      :query="query"
      @filterQuery="filterQuery"
      @cancelSearch="cancelSearch" />
  </div>
</template>
<script>
export default {
  data: () => ({
    query: null,
  }),

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
    cancelSearch(){
      this.query = null;
    },
  },
};
</script>