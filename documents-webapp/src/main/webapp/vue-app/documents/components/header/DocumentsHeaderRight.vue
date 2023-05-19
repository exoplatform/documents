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
    fileType: {
      type: Array,
      default: () => []
    },
    afterDate: {
      type: Number,
      default: null,
    },
    beforDate: {
      type: Number,
      default: null,
    },
    minSize: {
      type: Number,
      default: null,
    },
    maxSize: {
      type: Number,
      default: null,
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
        fileType: this.fileType,
        afterDate: this.afterDate,
        beforDate: this.beforDate,
        minSize: this.minSize,
        maxSize: this.maxSize,
        isMobile: this.isMobile,
      };
    }
  },
 
};
</script>
