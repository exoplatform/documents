<template>
  <extension-registry-components
    name="DocumentsHeaderRight"
    type="documents-header-right"
    :params="params"
    class="d-flex flex-no-wrap flex-row-reverse documents-header-right-actions"
    parent-element="div"
    element="div"
    element-class="mx-auto ma-lg-0 documents-header-right-action" />
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
  computed: {
    params() {
      return {
        query: this.query,
        primaryFilter: this.primaryFilter,
        isMobile: this.isMobile,
      };
    }
  },
 
};
</script>
