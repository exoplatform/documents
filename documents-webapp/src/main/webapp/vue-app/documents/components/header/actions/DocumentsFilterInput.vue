<template>
  <v-text-field
    v-model="query"
    :placeholder="$t('documents.label.filterDocuments')"
    :append-icon="appendIcon"
    prepend-inner-icon="fa-filter"
    class="inputDocumentsFilter pa-0 my-auto"
    @click:append="query = null" />
</template>
<script>
export default {
  data: () => ({
    query: null,
    startSearchAfterInMilliseconds: 600,
    endTypingKeywordTimeout: 50,
    startTypingKeywordTimeout: 0,
    loading: false,
  }),
  computed: {
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