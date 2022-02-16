<template>
  <div>
    <documents-breadcrumb class="pt-2 pe-1 pl-1" />
    <v-data-table
      ref="dataTable"
      :headers="headers"
      :items="items"
      :items-per-page="pageSize"
      :loading="loading"
      :options.sync="options"
      :locale="lang"
      :groupable="grouping"
      :group-by="groupBy"
      :group-desc="groupDesc"
      :disable-sort="isMobile"
      :loading-text="loadingLabel"
      hide-default-footer
      disable-pagination
      disable-filtering
      :class="loading && !items.length ? 'loadingClass' : ''"
      class="documents-folder-table border-box-sizing">
      <template
        v-for="header in extendedCells"
        v-slot:[`item.${header.value}`]="{item}">
        <documents-table-cell
          :key="header.value"
          :extension="header.cellExtension"
          :file="item" />
      </template>
      <template v-if="hasMore" slot="footer">
        <v-flex class="d-flex py-2 border-box-sizing mb-1">
          <v-btn
            :loading="loading"
            :disabled="loading"
            class="white mx-auto no-border primary--text no-box-shadow"
            @click="$root.$emit('document-load-more')">
            {{ $t('documents.loadMore') }}
          </v-btn>
        </v-flex>
      </template>
    </v-data-table>
  </div>
</template>

<script>
export default {
  props: {
    files: {
      type: Array,
      default: null,
    },
    pageSize: {
      type: Number,
      default: 20
    },
    offset: {
      type: Number,
      default: 20
    },
    limit: {
      type: Number,
      default: 20
    },
    sortField: {
      type: String,
      default: null
    },
    query: {
      type: String,
      default: null
    },
    loading: {
      type: Boolean,
      default: false
    },
    hasMore: {
      type: Boolean,
      default: false,
    },
    ascending: {
      type: Boolean,
      default: false,
    },
    primaryFilter: {
      type: String,
      default: null,
    }
  },
  data: () => ({
    lang: eXo.env.portal.language,
    options: {},
    headerExtensionApp: 'Documents',
    headerExtensionType: 'timelineViewHeader',
    headerExtensions: {},
    mobileUnfriendlyExtensions: ['lastUpdated', 'size', 'lastActivity'],
  }),
  computed: {
    extendedCells() {
      return this.headers && this.headers.filter(header => header.cellExtension && header.cellExtension.componentOptions);
    },
    querySearch() {
      return this.query && this.query.length;
    },
    primaryFilterFavorite() {
      return this.primaryFilter === 'favorites';
    },
    items() {
      return this.files && this.files.slice() || [];
    },
    headers() {
      const sortedHeaderExtensions = Object.values(this.headerExtensions).sort((ext1, ext2) => ext1.rank - ext2.rank);
      const headers = [];
      sortedHeaderExtensions.forEach(headerExtension => {
        headers.push({
          text: headerExtension.labelKey && this.$t(headerExtension.labelKey) || '',
          align: headerExtension.align || 'center',
          sortable: headerExtension.sortable || false,
          value: headerExtension.id,
          class: headerExtension.cssClass || '',
          width: headerExtension.width || 'auto',
          cellExtension: headerExtension,
        });
      });
      return headers;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs';
    },
    loadingLabel() {
      return `${this.$t('documents.label.loading')}...`;
    }
  },
  watch: {
    options() {
      const sortField = this.options.sortBy.length && this.options.sortBy[0] || this.sortField;
      const ascending = this.options.sortDesc.length ? !this.options.sortDesc[0] : true;
      if (!this.options.sortBy.length) {
        this.setSortOptions(sortField, ascending);
      }
      if (sortField !== this.sortField || this.ascending !== ascending) {
        this.$root.$emit('documents-sort', sortField, ascending);
      }
    },

  },
  created() {
    document.addEventListener(`extension-${this.headerExtensionApp}-${this.headerExtensionType}-updated`, this.refreshHeaderExtensions);
    this.refreshHeaderExtensions();
    this.setSortOptions(this.sortField, this.ascending);
    this.$root.$on('documents-filter', filter => {
      this.primaryFilter = filter;
    });
  },
  methods: {
    setSortOptions(sortField, ascending) {
      this.options.sortBy = [sortField];
      this.options.sortDesc = [!ascending];
    },
    refreshHeaderExtensions() {
      const extensions = extensionRegistry.loadExtensions(this.headerExtensionApp, this.headerExtensionType);
      let changed = false;
      extensions.forEach(extension => {
        if (extension.id && (!this.headerExtensions[extension.id] || this.headerExtensions[extension.id] !== extension)) {
          if (!this.isMobile || this.isMobile && !this.mobileUnfriendlyExtensions.includes(extension.id)) {
            this.headerExtensions[extension.id] = extension;
            changed = true;
          }
        }
      });
      // force update of attribute to re-render switch new extension id
      if (changed) {
        this.headerExtensions = Object.assign({}, this.headerExtensions);
      }
    },
  },
};
</script>