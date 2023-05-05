<template>
  <div>
    <upload-overlay />
    <v-data-table
      ref="dataTable"
      class="documents-folder-table border-box-sizing"
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
      mobile-breakpoint="960"
      :show-select="!isMobile && documentMultiSelectionActive"
      hide-default-footer
      disable-pagination>
      <template slot="group.header">
        <span></span>
      </template>
      <template #[`header.data-table-select`]="{ on , props }">
        <v-simple-checkbox
          v-model="selectAll"
          v-on="on"
          v-bind="props"
          :indeterminate="false"
          color="primary"
          :class="showSelectAll? 'visible': 'invisible'"
          class="mt-auto"
          @mouseover="showSelectAllInputOnHover"
          @mouseleave="hideSelectAllInputOnHover"
          @click="selectAllDocuments" />
      </template>
      <template #[`header.name`]>
        <span
          id="headerName">
          {{ $t('documents.label.name') }}
        </span>
      </template>
      <template
        v-if="!isMobile && documentMultiSelectionActive"
        #body="{ items }">
        <tbody>
          <tr
            v-for="item in items"
            :key="item.id"
            :class="isDocumentSelected(item)? 'v-data-table__selected': ''"
            @mouseover="showSelectionInput(item)"
            @mouseleave="hideSelectionInput(item)"
            @contextmenu="openContextMenu($event, item)">
            <td>
              <documents-selection-cell
                :file="item"
                :files="items"
                :select-all-checked="selectAll"
                :selected-documents="selectedDocuments"
                @document-selected="handleDocumentSelection"
                @document-unselected="handleDocumentSelection" />
            </td>
            <td
              v-for="header in extendedCells"
              :key="header.value + item.id">
              <documents-table-cell
                :extension="header.cellExtension"
                :file="item"
                :query="query"
                :extended-search="extendedSearch"
                :is-mobile="isMobile"
                :selected-view="selectedView"
                :selected-documents="selectedDocuments" />
            </td>
          </tr>
        </tbody>
      </template>
      <template
        v-else-if="documentMultiSelectionActive"
        #item="{item}">
        <tr
          :class="isDocumentSelected(item)? 'v-data-table__selected': ''"
          class="v-data-table__mobile-table-row pb-2 pt-2">
          <td
            class="v-data-table__mobile-row">
            <documents-table-cell
              v-for="header in extendedCells"
              :key="header.value + item.id"
              :extension="header.cellExtension"
              :file="item"
              :query="query"
              :extended-search="extendedSearch"
              :is-mobile="isMobile"
              :selected-view="selectedView"
              :select-all-checked="selectAll"
              :selected-documents="selectedDocuments"
              :class="header.value === 'name' && isXScreen && 'ms-10'" />
          </td>
        </tr>
      </template>
      <template
        v-else
        v-for="header in extendedCells"
        #[`item.${header.value}`]="{item}">
        <documents-table-cell
          :key="header.value"
          :extension="header.cellExtension"
          :file="item"
          :query="query"
          :extended-search="extendedSearch"
          :is-mobile="isMobile"
          :selected-view="selectedView"
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
    },
    selectedView: {
      type: String,
      default: null
    },
    selectedDocuments: {
      type: Array,
      default: () => []
    },
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
    selectAll: false,
    showSelectAllInput: false,
    showSelectInputTimer: null
  }),
  computed: {
    isXScreen() {
      return this.$vuetify.breakpoint.width < 600;
    },
    documentMultiSelectionActive() {
      return eXo?.env?.portal?.documentMultiSelection;
    },
    showSelectAll() {
      return this.selectedDocuments && this.selectedDocuments.length || this.showSelectAllInput;
    },
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
    this.$root.$on('select-all-documents', (value) => this.selectAll = value);
    this.$root.$on('reset-selections', () => this.selectAll = false);
    document.addEventListener(`extension-${this.headerExtensionApp}-${this.headerExtensionType}-updated`, this.refreshHeaderExtensions);
    this.refreshHeaderExtensions();
    this.setSortOptions(this.sortField, this.ascending);
    this.$root.$on('documents-filter', this.updateFilter);
  },
  mounted(){
    document.getElementById('headerName').parentElement.addEventListener('mouseover', this.showSelectAllInputOnHover);
    document.getElementById('headerName').parentElement.addEventListener('mouseleave', this.hideSelectAllInputOnHover);
    this.$documentsUtils.injectSortTooltip(this.$t('documents.sort.tooltip'),'tooltip-marker');
  },
  beforeDestroy() {
    this.$root.$off('documents-filter', this.updateFilter);
  },
  methods: {
    showSelectAllInputOnHover(){
      clearTimeout(this.showSelectInputTimer);
      this.showSelectAllInput = true;
    },
    hideSelectAllInputOnHover(){
      this.showSelectInputTimer = setTimeout(() => {
        this.showSelectAllInput = false;
      }, 200);
    },
    handleDocumentSelection() {
      this.selectAll = this.items.length === this.selectedDocuments.length;
    },
    openContextMenu(event, file) {
      this.$root.$emit('open-action-context-menu', event, file, this.selectedDocuments);
    },
    isDocumentSelected(item) {
      return this.selectedDocuments.findIndex(file => file.id === item.id) !== -1;
    },
    selectAllDocuments() {
      this.$root.$emit('select-all-documents', this.selectAll);
    },
    showSelectionInput(file) {
      this.$root.$emit('show-selection-input', file);
    },
    hideSelectionInput(file) {
      this.$root.$emit('hide-selection-input', file);
    },
    customSort: function (items, sortBy, isDesc) {
      let sorted = items;
      if (sortBy[1] === 'name') {
        const collator = new Intl.Collator(eXo.env.portal.language, {numeric: true, sensitivity: 'base'});
        sorted = items.sort((a, b) => {
          return (b.folder - a.folder) || collator.compare(a.name, b.name);
        });
        if (isDesc[1]) {
          return sorted.reverse();
        }
      } else if (sortBy[1] === 'size') {
        sorted = items.sort((a, b) => {
          if (a.folder && b.folder) {
            return 0;
          } else if (a.folder) {
            return 1;
          }
          else if (b.folder) {
            return -1;
          }
          return isDesc[1] ? b.size - a.size : a.size - b.size;
        });
      }
      return sorted;
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
