<template>
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
    hide-default-footer
    disable-pagination
    disable-filtering
    class="documents-table border-box-sizing">
    <template
      v-for="header in extendedCells"
      #[`item.${header.value}`]="{item}">
      <documents-table-cell
        :key="header.value"
        :extension="header.cellExtension"
        :file="item"
        :class="header.value === 'name' && 'ms-8'" />
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
        :primary-filter="primaryFilterFavorite" />
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
  },
  data: () => ({
    lang: eXo.env.portal.language,
    options: {},
    headerExtensionApp: 'Documents',
    headerExtensionType: 'timelineViewHeader',
    headerExtensions: {},
    mobileUnfriendlyExtensions: ['lastUpdated', 'size', 'lastActivity', 'favorite'],
    dayFirstDay: 0,
    weekFirstDay: 0,
    monthFirstDay: 0,
    yearFirstDay: 0,
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
    grouping() {
      return !this.sortField || this.sortField === 'lastUpdated' || this.sortField === 'favorite';
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
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
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
    this.$documentsUtils.injectSortTooltip(this.$t('documents.sort.tooltip'));
  },
  methods: {
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