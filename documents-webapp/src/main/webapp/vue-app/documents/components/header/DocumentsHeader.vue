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
          selected: primaryFilter,
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
        @filter-expand="filterDispalyed = !filterDispalyed"
        ref="applicationToolbar">
        <template #left>
          <documents-header-left
            v-if="canAdd"
            :selected-view="selectedView" 
            :is-mobile="isMobile"
            :selected-documents="selectedDocuments" />
        </template>
        <template #right>
          <div class="d-flex ms-auto">
            <v-tooltip
              v-if="$root.canEdit && $root.hover && !filterDispalyed"
              max-width="300"
              bottom>
              <template #activator="{ on, attrs }">
                <v-btn
                  id="documentSettingsButton"
                  small
                  icon
                  v-bind="attrs"
                  v-on="on"
                  @click="$root.$emit('open-document-settings')">
                  <v-icon size="20">fa-cog</v-icon>
                </v-btn>
              </template>
              <span class="caption">
                {{ $t('documents.settings.button.tooltip') }}
              </span>
            </v-tooltip>
            <v-tooltip v-if="offlineModeEnabled" bottom>
              <template #activator="{ on, attrs }">
                <div
                  v-bind="attrs"
                  v-on="on">
                  <v-btn
                    id="offlineDocumentsButton"
                    class="ms-4"
                    small
                    icon
                    @click="$root.$emit('open-document-offline-files')">
                    <v-icon size="20">fa-power-off</v-icon>
                  </v-btn>
                </div>
              </template>
              <span>{{ $t('documents.offline.accessDocumentsTooltip') }}</span>
            </v-tooltip>
          </div>
        </template>
      </application-toolbar>
    </div>
    <documents-setting-drawer :view-list="viewList" />
    <documents-offline-drawer />
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
    selectedDocuments: {
      type: Array,
      default: () => []
    }
  },
  data: () => ({
    tabsExtensionApp: 'Documents',
    tabsExtensionType: 'views',
    tabsExtensions: {},
    mobileOnlyTabsExtensions: [],
    desktopOnlyTabsExtensions: [],
    tabsList: [],
    tab: 'timeline',
    selectAllChecked: false,
    showFilter: false,
    offlineModeEnabled: false,
    filterDispalyed: false,
    centerBotton: {
      selected: 'timeline',
      hide: false,
      buttons: []
    },
    viewList: []
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
        if (this.$refs.applicationToolbar) {
          this.$refs.applicationToolbar?.selectToggle(this.tab);
        }
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
      if (this.afterDate && this.beforeDate) {
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
    }
  },
  created() {
    this.$root.$on('resetSearch', this.cancelSearch);
    this.$root.$on('filer-query', this.filterQuery);
    this.$root.$on('show-mobile-filter', this.handleShowFilter);
    document.addEventListener(`extension-${this.tabsExtensionApp}-${this.tabsExtensionType}-updated`, this.refreshTabExtensions);
    this.init();
    this.refreshTabExtensions();
  },
  beforeDestroy() {
    this.$root.$off('resetSearch', this.cancelSearch);
    this.$root.$off('filer-query', this.filterQuery);
    this.$root.$off('show-mobile-filter', this.handleShowFilter);
    document.removeEventListener(`extension-${this.tabsExtensionApp}-${this.tabsExtensionType}-updated`, this.refreshTabExtensions);
  },
  methods: {
    async init() {
      this.offlineModeEnabled = await this.$documentOfflineService.isDatabaseExists();
    },
    handleShowFilter(data) {
      this.showFilter = data;
    },
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
    refreshTabExtensions(event) {
      let extensions = extensionRegistry.loadExtensions(this.tabsExtensionApp, this.tabsExtensionType);
      this.viewList = [];
      let changed =false;
      if (event?.detail?.forceUpdate) {
        this.tabsExtensions= {};
      }
      extensions.forEach(extension => {
        this.viewList.push({'id': extension.id, 'name': extension.labelKey, 'enabled': !(this.$root.settings?.enabledViewList?.length !== 0) || this.$root.settings.enabledViewList.includes(extension.id)});
      });
      if (!this.$root.settings.enabledViewList || this.$root.settings.enabledViewList.length > 0)
      {
        extensions = extensions.filter(item => this.$root.settings.enabledViewList.includes(item.id));
      }
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
        if (this.tabsList.length < 2){
          this.tabsList=[];
        }
        this.centerBotton.buttons=this.tabsList.map(e => ({...e, text: this.$t(`${e.labelKey}`)}));
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
    openSettingsDrawer(){
      this.$root.$emit('open-advanced-filter-drawer');    
    },  
    changeDocumentsFilter(primaryFilter){
      this.$root.$emit('documents-filter', primaryFilter);
      this.$root.$emit('set-mobile-filter', primaryFilter);
    },  
    displayRightFilter(){
      this.filterDispalyed = true;
    },
  }
};
</script>
