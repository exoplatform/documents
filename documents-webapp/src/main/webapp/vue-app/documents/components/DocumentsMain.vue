<template>
  <v-app
    class="documents-application border-box-sizing"
    role="main"
    flat>
    <div class="pa-3 white">
      <documents-header
        class="py-2" />
      <documents-body
        :view-extension="selectedViewExtension"
        :files="files"
        :page-size="pageSize"
        :offset="offset"
        :limit="limit"
        :has-more="hasMore"
        :sort-field="sortField"
        :ascending="ascending"
        :loading="loading" />
    </div>
  </v-app>
</template>
<script>
export default {
  data: () => ({
    extensionApp: 'Documents',
    extensionType: 'views',
    query: null,
    sortField: 'lastUpdated',
    ascending: false,
    parentFolderId: null,
    pageSize: 50,
    files: [],
    offset: 0,
    limit: 0,
    loading: false,
    hasMore: false,
    viewExtensions: {},
    selectedView: null,
  }),
  computed: {
    selectedViewExtension() {
      if (this.selectedView) {
        return this.viewExtensions.find(viewExtension => viewExtension.id === this.selectedView);
      } else if (Object.keys(this.viewExtensions).length) {
        const sortedViewExtensions = Object.values(this.viewExtensions).sort((ext1, ext2) => ext1.rank - ext2.rank);
        return sortedViewExtensions[0];
      }
      return null;
    },
  },
  created() {
    document.addEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshViewExtensions);
    this.refreshViewExtensions();

    this.$root.$on('documents-refresh-files', this.refreshFiles);
    this.refreshFiles().finally(() => this.$root.$applicationLoaded());

    this.$root.$on('document-load-more', this.loadMore);
    this.$root.$on('document-search', this.search);
    this.$root.$on('documents-sort', this.sort);
  },
  destroyed() {
    document.removeEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshViewExtensions);
  },
  methods: {
    sort(sortField, ascending) {
      this.sortField = sortField;
      this.ascending = ascending;

      this.refreshFiles();
    },
    search(query) {
      this.query = query;

      this.refreshFiles();
    },
    loadMore() {
      this.limit += this.pageSize;

      this.refreshFiles();
    },
    refreshFiles() {
      if (!this.selectedViewExtension) {
        return Promise.resolve(null);
      }
      const filter = {
        ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
        listingType: this.selectedViewExtension.listingType,
      };
      if (this.parentFolderId) {
        filter.parentFolderId = this.parentFolderId;
      }
      if (this.query) {
        filter.query = this.query;
      }
      if (this.sortField) {
        filter.sortField = this.sortField;
      }
      if (this.ascending) {
        filter.ascending = true;
      }
      const expand = this.selectedViewExtension.filePropertiesExpand || 'modifier,creator,owner';
      this.limit = this.limit || this.pageSize;
      this.loading = true;
      return this.$documentFileService
        .getDocumentItems(filter, this.offset, this.limit + 1, expand)
        .then(files => {
          this.files = files && files.slice(this.offset, this.limit) || [];
          this.hasMore = files && files.length > this.limit;
        })
        .finally(() => this.loading = false);
    },
    refreshViewExtensions() {
      const extensions = extensionRegistry.loadExtensions(this.extensionApp, this.extensionType);
      let changed = false;
      extensions.forEach(extension => {
        if (extension.id && (!this.viewExtensions[extension.id] || this.viewExtensions[extension.id] !== extension)) {
          this.viewExtensions[extension.id] = extension;
          changed = true;
        }
      });
      // force update of attribute to re-render switch new extension id
      if (changed) {
        this.viewExtensions = Object.assign({}, this.viewExtensions);
      }
    },
  },
};
</script>