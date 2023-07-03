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
      :group-by="groupBy"
      :group-desc="groupDesc"
      :disable-sort="isMobile"
      :loading-text="loadingLabel"
      :class="loadingClass"
      :custom-sort="customSort"
      mobile-breakpoint="960"
      :show-select="!isMobile"
      hide-default-footer
      disable-pagination
      disable-filtering
      class="documents-table border-box-sizing">
      <template #[`header.data-table-select`]="{ on , props }">
        <v-tooltip
          v-on="on"
          v-bind="props"
          :disabled="selectAll"
          open-on-hover
          bottom>
          <template #activator="{ on, attrs }">
            <v-simple-checkbox
              v-model="selectAll"
              v-on="on"
              v-bind="attrs"
              :indeterminate="false"
              color="primary"
              :class="showSelectAll? 'visible': 'invisible'"
              class="mt-auto"
              @mouseover="showSelectAllInputOnHover"
              @mouseleave="hideSelectAllInputOnHover"
              @click="selectAllDocuments" />
          </template>
          {{ $t('documents.multiSelection.selectAll.element.tooltip.message') }}
        </v-tooltip>
      </template>
      <template #[`header.name`]>
        <span
          id="headerName">
          {{ $t('documents.label.name') }}
        </span>
      </template>
      <template
        v-if="!isMobile"
        #item="{item}">
        <tr
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
      </template>
      <template
        v-else
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
        v-if="grouping"
        #[`group.header`]="{group, items, isOpen, toggle}">
        <documents-timeline-group-header
          :group="group"
          :headers="headers"
          :files="items"
          :open="isOpen"
          :toggle-function="toggle"
          :query="querySearch"
          :is-mobile="isMobile"
          :primary-filter="primaryFilterFavorite"
          :file-type="fileType"
          :after-date="afterDate"
          :befor-date="beforDate"
          :min-size="minSize" />
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
    headerExtensionApp: 'Documents',
    headerExtensionType: 'timelineViewHeader',
    headerExtensions: {},
    mobileUnfriendlyExtensions: ['visibility','lastUpdated', 'size', 'lastActivity', 'favorite'],
    dayFirstDay: 0,
    weekFirstDay: 0,
    monthFirstDay: 0,
    yearFirstDay: 0,
    selectAll: false,
    showSelectAllInput: false
  }),
  computed: {
    isXScreen() {
      return this.$vuetify.breakpoint.width < 600;
    },
    showSelectAll() {
      return this.selectedDocuments && this.selectedDocuments.length  || this.showSelectAllInput;
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
    grouping() {
      return !(this.query?.length > 0  || this.primaryFilter !== 'all' || this.fileType?.length>0 || this.afterDate || this.beforDate || this.minSize || this.maxSize) && (!this.sortField || this.sortField === 'lastUpdated') ;
    },
    querySearch() {
      return this.query && this.query.length;
    },
    primaryFilterFavorite() {
      return this.primaryFilter === 'favorites';
    },
    groupBy() {
      return this.grouping && 'groupValue' || [];
    },
    groupDesc() {
      return this.sortField === 'favorite' ? false : this.ascending;
    },
    items() {
      if (this.grouping) {
        return this.files && this.files.slice().map(file => {
          const fileGrouping = JSON.parse(JSON.stringify(file));
          if (this.isToday(fileGrouping.modifiedDate)) {
            fileGrouping.groupValue = '1:thisDay';
          }
          else if (this.weekFirstDay <= fileGrouping.modifiedDate) {
            fileGrouping.groupValue = '2:thisWeek';
          } else if (this.monthFirstDay <= fileGrouping.modifiedDate) {
            fileGrouping.groupValue = '3:thisMonth';
          } else if (this.yearFirstDay <= fileGrouping.modifiedDate) {
            fileGrouping.groupValue = '4:thisYear';
          } else {
            fileGrouping.groupValue = '5:beforeThisYear';
          }
          return fileGrouping;
        }) || [];
      } else {
        return this.files && this.files.slice() || [];
      }
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
    this.dayFirstDay = this.getDayStart();
    this.weekFirstDay = this.getWeekStart();
    this.monthFirstDay = this.getMonthStart();
    this.yearFirstDay = this.getYearStart();

    document.addEventListener(`extension-${this.headerExtensionApp}-${this.headerExtensionType}-updated`, this.refreshHeaderExtensions);
    this.refreshHeaderExtensions();
    this.setSortOptions(this.sortField, this.ascending);
    this.$root.$on('documents-filter', this.updateFilter);
  },
  beforeDestroy() {
    this.$root.$off('documents-filter', this.updateFilter);
  },
  mounted(){
    document.getElementById('headerName')?.closest('tr').addEventListener('mouseover', this.showSelectAllInputOnHover);
    document.getElementById('headerName')?.closest('tr').addEventListener('mouseleave', this.hideSelectAllInputOnHover);
    this.$documentsUtils.injectSortTooltip(this.$t('documents.sort.tooltip'),'tooltip-marker');
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
      if (sortBy[0] === 'name') {
        const collator = new Intl.Collator(eXo.env.portal.language, {numeric: true, sensitivity: 'base'});
        sorted = items.sort((a, b) => {
          return (b.folder - a.folder) || collator.compare(a.name, b.name);
        });
        if (isDesc[0]) {
          return sorted.reverse();
        }
      } else if (sortBy[0] === 'size') {
        sorted = items.sort((a, b) => {
          return isDesc[0] ? a.size - b.size : b.size - a.size;
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
    isToday (someDate){
      const today = new Date();
      const day = new Date(someDate);
      return day.getDate() === today.getDate() &&
          day.getMonth() === today.getMonth() &&
          day.getFullYear() === today.getFullYear();
    },
    getDayStart() {
      const now = new Date();
      return now.getTime();
    },
    getWeekStart() { // Return Monday
      const now = new Date();
      const day = now.getDay();
      const startOfWeekDate = now.getDate() - day + (day && 1 || -6);
      const date = new Date(1900 + now.getYear(), now.getMonth(), startOfWeekDate);
      return date.getTime();
    },
    getMonthStart() {
      const now = new Date();
      return new Date(1900 + now.getYear(), now.getMonth(), 1).getTime();
    },
    getYearStart() {
      const now = new Date();
      return new Date(1900 + now.getYear(), 0, 1).getTime();
    },
  },
};
</script>
