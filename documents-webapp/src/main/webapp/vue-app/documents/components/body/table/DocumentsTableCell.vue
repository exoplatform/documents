<template>
  <extension-registry-component
    :component="extension"
    :params="params"
    :element="div" />
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
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
    extension: {
      type: Object,
      default: null,
    },
  },
  computed: {
    params() {
      return {
        file: this.file,
        query: this.query,
        extendedSearch: this.extendedSearch,
        extension: this.extension,
      };
    },
  },
};
</script>