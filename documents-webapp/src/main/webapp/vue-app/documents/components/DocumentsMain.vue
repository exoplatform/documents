<template>
  <v-app
    class="documents-application singlePageApplication border-box-sizing"
    :class="isMobile ? 'mobile' : ''"
    role="main"
    flat>
    <div class="pa-3 white">
      <div v-if="searchResult">
        <documents-header
          class="py-2" />
        <documents-no-result-body />
      </div>
      <div v-else-if="!filesLoad">
        <documents-header-left
          class="py-2" />
        <documents-no-body
          :is-mobile="isMobile" />
      </div>
      <div v-else>
        <documents-header
          class="py-2" />
        <documents-body
          :view-extension="selectedViewExtension"
          :files="files"
          :groups-sizes="groupsSizes"
          :page-size="pageSize"
          :offset="offset"
          :limit="limit"
          :has-more="hasMore"
          :sort-field="sortField"
          :ascending="ascending"
          :loading="loading" />
      </div>
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
    groupsSizes: {
      'thisDay': 0,
      'thisWeek': 0,
      'thisMonth': 0,
      'thisYear': 0,
      'beforeThisYear': 0,
    },
    selectedView: null,
    previewMode: false,
  }),
  computed: {
    filesLoad(){
      return this.files && this.files.length;
    },
    selectedViewExtension() {
      if (this.selectedView) {
        return this.viewExtensions.find(viewExtension => viewExtension.id === this.selectedView);
      } else if (Object.keys(this.viewExtensions).length) {
        const sortedViewExtensions = Object.values(this.viewExtensions).sort((ext1, ext2) => ext1.rank - ext2.rank);
        return sortedViewExtensions[0];
      }
      return null;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    searchResult(){
      return this.query && this.query.length && !this.files.length;
    }
  },
  created() {
    document.addEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshViewExtensions);
    this.refreshViewExtensions();

    this.$root.$on('documents-refresh-files', this.refreshFilesEvent);
    this.getDocumentGroupSizes();
    this.refreshFiles().then(() => {
      this.watchDocumentPreview();
    }).finally(() => this.$root.$applicationLoaded());

    this.$root.$on('document-load-more', this.loadMore);
    this.$root.$on('document-search', this.search);
    this.$root.$on('documents-sort', this.sort);
    
    const currentUrlSearchParams = window.location.search;
    const queryParams = new URLSearchParams(currentUrlSearchParams);
    if (queryParams.has('documentPreviewId')) {
      this.loading = true;
      this.previewMode = true;
      const documentPreviewId = queryParams.get('documentPreviewId');
      this.$attachmentService.getAttachmentById(documentPreviewId)
        .then(attachment => {
          documentPreview.init({
            doc: {
              id: documentPreviewId,
              repository: 'repository',
              workspace: 'collaboration',
              path: attachment.path,
              title: attachment.title,
              icon: attachment.icon,
              size: attachment.size,
              openUrl: attachment.openUrl,
              downloadUrl: attachment.downloadUrl,
            },
            author: attachment.updater,
            showComments: false,
          });
        })
        .catch(e => console.error(e))
        .finally(() => this.loading = false);
    }
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
    refreshFilesEvent() {
      this.getDocumentGroupSizes();
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
    getDocumentGroupSizes() {
      if (!this.selectedViewExtension) {
        return Promise.resolve(null);
      }
      const filter = {
        ownerId: eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId,
      };

      if (this.query) {
        filter.query = this.query;
      }
      return this.$documentFileService
        .getDocumentGroupSizes(filter)
        .then(sizes => {
          this.groupsSizes =  sizes || {};
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
    watchDocumentPreview() {
      const self = this;
      const bodyElement = document.body;

      const config = {childList: true, subtree: true};

      const callback = function () {
        const documentPreviewContainer = document.getElementById('documentPreviewContainer');
        if (!documentPreviewContainer && self.previewMode) {
          // Quit preview mode
          self.previewMode = false;
          window.history.pushState('', '', eXo.env.server.portalBaseURL);
        } else if (documentPreviewContainer && !self.previewMode) {
          // Enter preview mode
          self.previewMode = true;
        }
      };

      // Create an observer instance linked to the callback function
      const observer = new MutationObserver(callback);

      // Start observing the target node for configured mutations
      observer.observe(bodyElement, config);
    },
  },
};
</script>