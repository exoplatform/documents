<template>
  <div v-if="showFilter" class="d-flex flex-row">
         <v-icon
          size="20"
          class="mx-auto ma-lg-0"
          @click="hideFilter">
          fas fa-arrow-left
        </v-icon>
        <v-spacer />
    <extension-registry-components
    name="DocumentsFilters"
    type="documents-filters"
    :params="params"
    class="d-flex flex-no-wrap documents-header-filter-container"
    parent-element="div"
    element="div"
    element-class="mx-auto ma-lg-0  documents-header-filter-container" />
  </div>
</template>
<script>

export default {
  props: {
    query: {
      type: String,
      default: '',
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
    showFilter: false,
  }),
  created() {
    this.$root.$on('show-mobile-filter', data => {
      this.showFilter= data;
    });
  },
  computed: {
    params() {
      return {
        query: this.query,
        primaryFilter: this.primaryFilter,
        isMobile: this.isMobile,
      };
    }
  },
  methods: {
    hideFilter(){
      this.showFilter = !this.showFilter;
      this.$root.$emit('show-mobile-filter', this.showFilter);
    },
  },
};
</script>
