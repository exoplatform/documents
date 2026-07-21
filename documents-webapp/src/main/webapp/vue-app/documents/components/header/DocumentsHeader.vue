<template>
  <div>
    <div v-if="!showFilter">
      <application-toolbar
        :center-button-toggle="centerBotton"
        :right-text-filter="{
          minCharacters: 3,
          placeholder: $root.driveView ? $t('documents.label.filterDrives') : $t('documents.label.searchDocuments'),
          ariaLabel: $root.driveView ? $t('documents.label.filterDrives') : $t('documents.label.filterDocuments'),
          tooltip: $root.driveView ? $t('documents.label.filterDrives') : $t('documents.label.filterDocuments')
        }"
        :right-filter-button="{
          text: $t('documents.label.filter'),
          hide: $root.driveView,
        }"

        :right-select-box="{
          hide: isMobile || $root.driveView,
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
        @filter-text-input-end-typing="filterQuery($event)"
        @filter-button-click="openAdvacedDrawer()"
        @filter-select-change="changeDocumentsFilter($event)"
        @toggle-select="changeDocumentView($event)"
        @filter-expand="filterDispalyed = !filterDispalyed"
        class="mb-4"
        ref="applicationToolbar">
        <template #left>
          <documents-header-left
            v-if="canAdd"
            :selected-view="selectedView" 
            :is-mobile="isMobile"
            :selected-documents="selectedDocuments" />
        </template>
        <template #right>
          <div v-if="!filterDispalyed" class="d-flex ms-auto">
            <v-tooltip
              v-if="$root.canEdit && $root.hover"
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
              <span>
                {{ $t('documents.settings.button.tooltip') }}
              </span>
            </v-tooltip>
            <extension-registry-components
              :params="params"
              name="DocumentsHeader"
              type="documents-header-right"
              parent-element="div"
              element="div" />
            <documents-offline-button
              v-if="!spaceId"
              btn-class="ms-4"
              no-go-back-button
              tooltip
              small />
          </div>
          <div v-if="!$root.isMobile" class="d-flex ms-5">
            <v-menu v-model="menu" offset-y>
              <template #activator="{ on, attrs }">
                <v-btn
                  id="documentSettingsView"
                  small
                  elevation="0"
                  class="px-0"
                  v-bind="attrs"
                  v-on="on">
                  <v-icon
                    v-if="selectedViewType"
                    :class="selectedViewType.icon"
                    class="icon-default-color"
                    size="20" />
                  <v-icon class="ms-1" size="13">mdi-chevron-down</v-icon>
                </v-btn>
              </template>
              <v-list class="pa-0">
                <v-list-item
                  v-for="item in viewItemsTypes"
                  :key="item.value"
                  @click="setViewType(item)">
                  <v-list-item-icon class="me-2 my-0 align-self-center">
                    <v-icon
                      :class="[item.icon, item.value === viewType ? 'primary--text' : 'icon-default-color']"
                      size="20" />
                  </v-list-item-icon>
                  <div :class="item.value === viewType && 'primary--text'">{{ item.label }}</div>
                </v-list-item>
              </v-list>
            </v-menu>
          </div>
        </template>
      </application-toolbar>
    </div>
    <documents-setting-drawer :view-list="viewList" />
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
    spaceId: eXo.env.portal.spaceId,
    selectAllChecked: false,
    showFilter: false,
    filterDispalyed: false,
    centerBotton: {
      selected: 'timeline',
      hide: false,
      buttons: []
    },
    viewList: [],
    menu: false,
    viewType: 'listView',
  }),
  watch: {
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
    viewItemsTypes() {
      return [
        { value: 'listView', icon: 'fas fa-th-list', label: this.$t('documents.label.viewType.list') },
        { value: 'cardsView', icon: 'fas fa-th-large', label: this.$t('documents.label.viewType.cards')},
      ];
    },
    params() {
      return {
        selectedDocuments: this.selectedDocuments,
        selectedDrive: this.$root.selectedDrive,
        selectedPath: this.$root.selectedPath,
        spaceId: this.spaceId,
        tab: this.tab,
      };
    },
    selectedViewType() {
      const item = this.viewItemsTypes.find(i => i.value === this.viewType);
      return item ? item : null;
    },
    filtersCount() {
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
  },
  created() {
    this.viewType = this.$documentsUtils.getViewType(this.$root.appId);
    this.$root.$on('resetSearch', this.cancelSearch);
    this.$root.$on('filer-query', this.filterQuery);
    this.$root.$on('show-mobile-filter', this.handleShowFilter);
    document.addEventListener(`extension-${this.tabsExtensionApp}-${this.tabsExtensionType}-updated`, this.refreshTabExtensions);
    window.addEventListener('keydown', this.handleKeydown);
    this.refreshTabExtensions();
  },
  beforeDestroy() {
    this.$root.$off('resetSearch', this.cancelSearch);
    this.$root.$off('filer-query', this.filterQuery);
    this.$root.$off('show-mobile-filter', this.handleShowFilter);
    document.removeEventListener(`extension-${this.tabsExtensionApp}-${this.tabsExtensionType}-updated`, this.refreshTabExtensions);
    window.removeEventListener('keydown', this.handleKeydown);
  },
  methods: {
    setViewType(item) {
      this.viewType = item.value;
      this.$emit('documents-type-view-applied', this.viewType);
      this.$documentsUtils.setViewType(this.viewType, this.$root.appId);
      this.menu = false;
    },
    handleShowFilter(data) {
      this.showFilter = data;
    },
    filterQuery(query){
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
        this.centerBotton.hide = !eXo.env.portal.spaceId;
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
    changeDocumentsFilter(primaryFilter){
      this.$root.$emit('documents-filter', primaryFilter);
      this.$root.$emit('set-mobile-filter', primaryFilter);
    },
    handleKeydown(event) {
      if (event.ctrlKey && event.key === 'a') {
        event.preventDefault();
        this.$root.$emit('select-all-documents', true);
      }
    }
  }
};
</script>
