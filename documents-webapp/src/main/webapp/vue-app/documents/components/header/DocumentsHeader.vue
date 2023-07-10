<template>
  <div>
    <div v-if="!showFilter">
      <application-toolbar
        :center-button-toggle="centerBotton"
        :right-text-filter="{
          minCharacters: 3,
          placeholder: $t('documents.label.filterDocuments'),
          tooltip: $t('documents.label.filterDocuments')
        }"
        :right-filter-button="{
          text: $t('documents.label.filter'),
        }"

        :right-select-box="{
          hide: isMobile,
          selected:'all', 
          items: [{
            value: 'all',
            text: $t('documents.filter.all'),
          } ,{
            value: 'favorites',
            text: $t('documents.filter.favorites'),
          }],
        }"
        :filters-count="filtersCount"
        @filter-text-input-end-typing="query = $event"
        @filter-button-click="openAdvacedDrawer()"
        @filter-select-change="changeDocumentsFilter($event)"
        @toggle-select="changeDocumentView($event)"
        ref="applicationToolbar">
        <template #left>
          <documents-header-left
            v-if="canAdd"
            :selected-view="selectedView" 
            :is-mobile="isMobile"
            :selected-documents="selectedDocuments" />
        </template>
      </application-toolbar>
    </div>
    <documents-breadcrumb
      v-if="selectedView === 'folder'"
      v-show="showBreadcrumb"
      :is-mobile="isMobile"
      class="pt-4 px-1" />
  </div>
</template>

<script>
export default {
  props: {
    canAdd: {
      type: Boolean,
      default: false
    },
    filesSize: {
      type: Number,
      default: 0
    },
    selectedView: {
      type: String,
      default: '',
    },
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
    selectedDocuments: {
      type: Array,
      default: () => []
    }
  },
  data: () => ({
    tabsExtensionApp: 'DocumentTabs',
    tabsExtensionType: 'documentsHeaderTab',
    tabsExtensions: {},
    mobileOnlyTabsExtensions: [],
    desktopOnlyTabsExtensions: [],
    tabsList: [],
    tab: 'timeline',
    selectAllChecked: false,
    showFilter: false,
    centerBotton: {
      selected: 'timeline',
      hide: false,
      buttons: []
    }
  }),
  watch: {
    query() {  
      this.$root.$emit('document-search', this.query);
      return;
    },
    selectedView: {
      immediate: true,
      handler() {
        const tabsExtensionIds = Object.values(this.tabsExtensions).map(extension => extension.viewName);
        this.tab = tabsExtensionIds.includes(this.selectedView) ? tabsExtensionIds[tabsExtensionIds.indexOf(this.selectedView)] : 'timeline';
        this.$refs.applicationToolbar.selectToggle(this.tab);
      },
    },
    tabsExtensions() {
      const tabsExtensionIds = Object.values(this.tabsExtensions).map(extension => extension.viewName);
      this.tab = tabsExtensionIds.includes(this.selectedView) ? tabsExtensionIds[tabsExtensionIds.indexOf(this.selectedView)] : 'timeline';
      this.$refs.applicationToolbar.selectToggle(this.tab);
    },
  },
  computed: {
    filtersCount(){
      let fNum = 0;
      if (this.primaryFilter.toLowerCase()!=='all') {
        fNum++;
      }
      if (this.extended && this.query) {
        fNum++;
      }
      if (this.fileType?.length>0) {
        fNum++;
      }
      if (this.afterDate && this.beforDate) {
        fNum++;
      }
      if (this.minSize) {
        fNum++;
      }
      if (this.maxSize) {
        fNum++;
      }
      return fNum;
    },
    canShowMobileFilter() {
      return this.isMobile && this.showFilter;
    },
    showBreadcrumb(){
      return !this.query;
    }
  },
  created() {
    this.$root.$on('resetSearch', this.cancelSearch);
    this.$root.$on('filer-query', this.filterQuery);
    this.$root.$on('show-mobile-filter', data => {
      this.showFilter= data;
    });
    document.addEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshTabExtensions);
    this.refreshTabExtensions();
  },
  methods: {
    filterQuery(query){
      if (this.query === query){
        return;
      }
      this.query = query;
      this.$root.$emit('document-search', this.query);     
    },
    cancelSearch(){
      this.query = null;
      this.$refs.applicationToolbar.setTerm(null);
    },
    refreshTabExtensions() {
      const extensions = extensionRegistry.loadExtensions(this.tabsExtensionApp, this.tabsExtensionType);
      let changed = false;
      extensions.forEach(extension => {
        if (extension.id && (!this.tabsExtensions[extension.id] || this.tabsExtensions[extension.id] !== extension)) {
          if ( (!this.isMobile && !this.mobileOnlyTabsExtensions.includes(extension.id))
              || (this.isMobile && !this.desktopOnlyTabsExtensions.includes(extension.id))) {
            this.tabsExtensions[extension.id] = extension;
            changed = true;
          }
        }
      });
      // force update of attribute to re-render switch new extension id
      if (changed) {
        this.tabsExtensions = Object.assign({}, this.tabsExtensions);
        this.tabsList=Object.values(this.tabsExtensions);
        this.centerBotton.buttons=this.tabsList.map(e => ({...e, text: this.$t(`${e.labelKey}`).toUpperCase()}));
      }
    },
    changeDocumentView(view) {
      this.$root.$emit('document-change-view', view);
      const viewTab = view ==='folder'? 'FOLDER' : 'RECENT';
      document.dispatchEvent(new CustomEvent('document-change', {
        detail: {
          'type': 'document',
          'spaceId': this.spaceId,
          'name': `Switch View ${viewTab} Tab`
        }
      }));
    },
    openAdvacedDrawer(){
      if (this.isMobile){
        this.$root.$emit('open-mobile-filter-menu',true);
      } else {
        this.$root.$emit('open-advanced-filter-drawer');
      }
      
    },  
    changeDocumentsFilter(primaryFilter){
      this.$root.$emit('documents-filter', primaryFilter);
      this.$root.$emit('set-mobile-filter', primaryFilter);
    },
  }
};
</script>