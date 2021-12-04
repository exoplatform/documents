<template>
  <div>
    <v-icon
      size="25"
      class="inputDocumentsFilter text-sub-title pa-1 my-auto "
      :class="isMobile && !showMobileFilter ? '' : 'd-none'"
      @click="mobileFilter">
      fas fa-filter
    </v-icon>
    <v-text-field
      v-model="query"
      :placeholder="$t('documents.label.filterDocuments')"
      :class="isMobile && showMobileFilter || !isMobile ? '' : 'd-none'"
      :append-icon="appendIcon"
      prepend-inner-icon="fa-filter"
      class="inputDocumentsFilter pa-1 my-auto width-full"
      @click:append="query = null" />
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
  methods: {
    mobileFilter(){
      this.showMobileFilter = !this.showMobileFilter;
      this.$root.$emit('show-mobile-filter', this.showMobileFilter);
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