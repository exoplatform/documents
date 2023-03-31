<template>
  <extension-registry-components
    :params="params"
    name="DocumentsHeaderLeft"
    type="documents-header-left"
    class="d-flex flex-no-wrap documents-header-left-actions"
    parent-element="div"
    element="div"
    element-class="ma-lg-0 documents-header-left-action" />
</template>
<script>
export default {
  props: {
    selectedView: {
      type: String,
      default: '',
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    selectedDocuments: {
      type: Array,
      default: () => []
    }
  },
  computed: {
    params() {
      return {
        selectedView: this.selectedView,
        isMobile: this.isMobile,
        selectedDocuments: this.selectedDocuments
      };
    },
  },
};
</script>