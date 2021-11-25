<template>
  <div class="documents-body">
    <extension-registry-component
      v-if="viewExtension"
      :component="viewExtension"
      :params="params"
      :element="div" />
  </div>
</template>

<script>
export default {
  props: {
    viewExtension: {
      type: Object,
      default: null,
    },
    files: {
      type: Array,
      default: null,
    },
    groupsSizes: {
      type: Object,
      default: null,
    },
    loading: {
      type: Boolean,
      default: false,
    },
    offset: {
      type: Number,
      default: () => 0,
    },
    limit: {
      type: Number,
      default: () => 50,
    },
    pageSize: {
      type: Number,
      default: () => 50,
    },
    hasMore: {
      type: Boolean,
      default: false,
    },
    ascending: {
      type: Boolean,
      default: false,
    },
    sortField: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    emptyDocs: '/documents-portlet/images/docs.png',
    spaceDisplayName: eXo.env.portal.spaceDisplayName,
  }),
  computed: {
    welcomeTitle() {
      return this.$t && this.$t('documents.label.no-content', {
        '0': `<strong>${this.spaceDisplayName}</strong>`,
      });
    },
    params() {
      return {
        files: this.files,
        groupsSizes: this.groupsSizes,
        hasMore: this.hasMore,
        ascending: this.ascending,
        pageSize: this.pageSize,
        sortField: this.sortField,
        loading: this.loading,
        offset: this.offset,
        limit: this.limit,
      };
    },
  },
  methods: {
    openDrawer() {
      document.dispatchEvent(new CustomEvent('open-attachments-app-drawer'));
    },
  }
};
</script>