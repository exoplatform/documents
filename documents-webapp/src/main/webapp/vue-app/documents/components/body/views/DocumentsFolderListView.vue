<!--
 Copyright (C) 2025 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <div class="row no-gutters">
    <component
      :is="isMobile ? 'folder-treeview-drawer' : 'folder-tree-view'"
      :tree-view-expended="treeViewExpended"
      :is-mobile="isMobile"
      :folder-path="folderPath" />
    <v-card
      flat
      :class="!isMobile && treeViewExpended ? 'col-9':'col-12'">
      <documents-breadcrumb
        class="width-min-content"
        :is-mobile="isMobile"
        :tree-view-expended="treeViewExpended" />
      <upload-overlay />
      <documents-no-body-folder
        v-if="!loading && items.length === 0"
        :query="query"
        :is-mobile="isMobile" />
      <v-hover v-else v-model="hoverTable">
        <div>
          <v-data-table
            ref="dataTable"
            id="folderView"
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
            :show-select="!isMobile"
            hide-default-footer
            disable-pagination>
            <template slot="group.header">
              <span></span>
            </template>
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
                    :class="!$root.driveView && (showSelectAll || hoverTable) ? 'visible': 'invisible'"
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
              #body="{ items }">
              <tbody>
                <v-hover
                  v-for="item in items"
                  v-slot="{ hover }"
                  :key="item.id">
                  <tr
                    :class="isDocumentSelected(item)? 'v-data-table__selected': ''"
                    :data-fileId="item.id"
                    :data-isFolder="item.folder? 'true': 'false'"
                    :data-canEdit="canEditFile(item)? 'true': 'false'"
                    :draggable="!editName"
                    @mouseover="showSelectionInput(item)"
                    @mouseleave="hideSelectionInput(item)"
                    @contextmenu="openContextMenu($event, item)">
                    <td>
                      <documents-selection-cell
                        v-if="!$root.driveView"
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
                        :hover="hover"
                        :selected-view="selectedView"
                        :is-search-result="isSearchResult"
                        :selected-documents="selectedDocuments" />
                    </td>
                  </tr>
                </v-hover>
              </tbody>
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
                    :is-search-result="isSearchResult"
                    :select-all-checked="selectAll"
                    :selected-documents="selectedDocuments"
                    :class="header.value === 'name' && isXScreen && 'ms-0'" />
                </td>
              </tr>
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
      </v-hover>
    </v-card>
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
    beforeDate: {
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
    folderPath: {
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
    showSelectInputTimer: null,
    treeViewExpended: true,
    hoverTable: false,
    editName: false,
  }),
  computed: {
    isXScreen() {
      return this.$vuetify.breakpoint.width < 600;
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
        if (!this.$root.driveView  || (this.$root.driveView && headerExtension.id === 'name')) {
          headers.push({
            text: headerExtension.labelKey && this.$t(headerExtension.labelKey) || '',
            align: headerExtension.align || 'center',
            sortable: (!this.$root.driveView && headerExtension.sortable) || false,
            value: headerExtension.id,
            class: headerExtension.cssClass || '',
            width: headerExtension.width || 'auto',
            cellExtension: headerExtension,
          });
        }
      });
      return headers;
    },
    loadingLabel() {
      return `${this.$t('documents.label.loading')}...`;
    },
    isSearchResult(){
      return ((this.query && this.query.length > 0) || this.minSize || this.maxSize || this.afterDate || this.beforeDate || this.fileType?.length>0 || this.primaryFilter!=='all') ;
    },
    showBreadcrumb(){
      return !this.query;
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
    this.treeViewExpended =  localStorage.getItem('expendedTreeView')!=null ? localStorage.getItem('expendedTreeView') === 'true' : (this.$root.settings?.expendedTreeView !== null ? this.$root.settings.expendedTreeView : true);
    this.$root.$on('select-all-documents', (value) => this.selectAll = value);
    this.$root.$on('reset-selections', () => this.selectAll = false);
    document.addEventListener(`extension-${this.headerExtensionApp}-${this.headerExtensionType}-updated`, this.refreshHeaderExtensions);
    this.refreshHeaderExtensions();
    this.setSortOptions(this.sortField, this.ascending);
    this.$root.$on('documents-filter', this.updateFilter);
    this.$root.$on('tree-view-expend', this.extendTreeView );
    this.$root.$on('loading-documents', this.setLoading);
    this.$root.$on('documents-file-name-edit', this.setEditName);
  },
  mounted(){
    this.$documentsUtils.injectSortTooltip(this.$t('documents.sort.tooltip'),'tooltip-marker');
    DocumentsDraggable.invoke('folderView', 'breadcrumb-list-items');
  },
  beforeDestroy() {
    this.$root.$off('documents-filter', this.updateFilter);
    this.$root.$off('tree-view-expend', this.extendTreeView);
    this.$root.$off('openTreeFolderDrawer', this.folderTreeDrawer);
    this.$root.$off('loading-documents', this.setLoading);
    this.$root.$off('documents-file-name-edit', this.setEditName);
  },
  methods: {
    setEditName(value) {
      this.editName = value;
    },
    extendTreeView(value) {
      this.treeViewExpended = value;
      localStorage.setItem('expendedTreeView', value);
    },
    canEditFile(file) {
      return file?.acl?.canEdit;
    },
    setLoading(value) {
      this.loading = value;
    },
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
      this.$root.$emit('open-action-context-menu', event, file, this.selectedDocuments,'folder',this.isSearchResult);
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
      if (this.$root.driveView) {
        return items;
      }
      let sorted = items;
      if (sortBy[1] === 'name') {
        const folders = items.filter((item) => item.folder);
        const files = items.filter((item) => !item.folder);
        const collator = new Intl.Collator(eXo.env.portal.language, {numeric: true, sensitivity: 'base'});
        folders.sort((a, b) => collator.compare(a.name, b.name));
        files.sort((a, b) => collator.compare(a.name, b.name));
        sorted = [...folders, ...files];
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
