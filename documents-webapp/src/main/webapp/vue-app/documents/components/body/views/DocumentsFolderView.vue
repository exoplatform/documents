<template>
  <div>
    <upload-overlay />
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
      :loading-text="loadingLabel"
      :class="loadingClass"
      :custom-sort="customSort"
      hide-default-footer
      disable-pagination
      disable-filtering
      class="documents-folder-table border-box-sizing">
      <template slot="group.header"><span></span></template>
      <template
        v-for="header in extendedCells"
        #[`item.${header.value}`]="{item}">
        <documents-table-cell
          :key="header.value"
          :extension="header.cellExtension"
          :file="item"
          :query="query"
          :extended-search="extendedSearch"
          :is-mobile="isMobile" 
          :class="header.value === 'name' && 'ms-8'" />
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
    extendedSearch: {
      type: Boolean,
      default: false,
    },
    initialized: {
      type: Boolean,
      default: false
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
    },
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    lang: eXo.env.portal.language,
    options: {},
    grouping: true,
    groupBy: ['folder'],
    groupDesc: [true],
    headerExtensionApp: 'Documents',
    headerExtensionType: 'timelineViewHeader',
    headerExtensions: {},
    mobileUnfriendlyExtensions: ['visibility','lastUpdated', 'size', 'lastActivity', 'favorite'],
  }),
  computed: {
    loadingClass() {
      if (this.loading && !this.items.length) {
        return this.isMobile ? 'loadingClassMobile' : 'loadingClass';
      }
      return '';
    },
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
    sortedHeaderExtensions() {
      return Object.values(this.headerExtensions || {}).filter(extension => {
        return !this.isMobile || (this.isMobile && !this.mobileUnfriendlyExtensions.includes(extension.id));
      }).sort((ext1, ext2) => ext1.rank - ext2.rank);
    },
    headers() {
      const headers = [];
      this.sortedHeaderExtensions.forEach(headerExtension => {
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
    loadingLabel() {
      return `${this.$t('documents.label.loading')}...`;
    }
  },
  watch: {
    options() {
      if (!this.initialized) {
        return;
      }
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
    this.$root.$on('documents-filter', this.updateFilter);
  },
  mounted(){
    this.$documentsUtils.injectSortTooltip(this.$t('documents.sort.tooltip'),'tooltip-marker');
  },
  beforeDestroy() {
    this.$root.$off('documents-filter', this.updateFilter);
  },
  methods: {
    customSort: function (items, sortBy, isDesc) {
      if (sortBy[1] === 'name') {
        const collator = new Intl.Collator(eXo.env.portal.language, {numeric: true, sensitivity: 'base'});
        const sorted = items.sort((a, b) => {
          return (b.folder - a.folder) || collator.compare(a.name, b.name);
        });
        if (isDesc[1]) {
          return sorted.reverse();
        }
        return sorted;
      }
      return items;
    },
    updateFilter(filter) {
      this.primaryFilter = filter;
    },
    setSortOptions(sortField, ascending) {
      this.options.sortBy = [sortField];
      this.options.sortDesc = [!ascending];
    },
    refreshHeaderExtensions() {
      const extensions = extensionRegistry.loadExtensions(this.headerExtensionApp, this.headerExtensionType);
      extensions.forEach(extension => this.$set(this.headerExtensions, extension.id, extension));
    },
  },
};
</script>
