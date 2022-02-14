<template>
  <div class="d-flex flex-row">
    <documents-header-left />
    <v-spacer :class="showMobileFilter ? 'd-none' : ''" />
    <div>
      <v-tabs
        v-model="tab"
        class="documentViewTabs">
        <v-tab
          v-for="(extension, i) in tabsList"
          :key="i"
          :class="tabClass(i)"
          @change="changeDocumentView(extension.viewName)">
          <v-icon class="tabIcon">{{ extension.icon }}</v-icon>
          {{ !isMobile ? $t(extension.labelKey) : '' }}
        </v-tab>
      </v-tabs>
    </div>
    <v-spacer :class="showMobileFilter ? 'd-none' : ''" />
    <documents-header-right />
  </div>
</template>

<script>
export default {
  data: () => ({
    showMobileFilter: false,
    tabsExtensionApp: 'DocumentTabs',
    tabsExtensionType: 'documentsHeaderTab',
    tabsExtensions: {},
    mobileOnlyTabsExtensions: [],
    desktopOnlyTabsExtensions: [],
    tabsList: [],
    tab: 0,
  }),
  computed: {
    showMobileFilter() {
      return this.isMobile && this.showMobileFilter;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  created() {
    document.addEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshTabExtensions);
    this.refreshTabExtensions();
    this.$root.$on('show-mobile-filter', data => {
      this.showMobileFilter= data;
    });
    const currentUrlSearchParams = window.location.search;
    const queryParams = new URLSearchParams(currentUrlSearchParams);
    if (queryParams.has('folderId')) {
      this.tab = 1;
    } else {
      const pathParts  = eXo.env.server.portalBaseURL.toLowerCase().split(eXo.env.portal.selectedNodeUri.toLowerCase());
      if (pathParts.length>1 && pathParts[1]){
        this.tab = 1;
      }
    }

  },
  methods: {

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
      }
      
    },

    changeDocumentView(view) {
      this.$root.$emit('document-change-view', view);
    },

    tabClass(i) {
      //const i = this.tabsExtensions.indexOf(viewId);
      if (i===0){
        return 'firstTab';
      } 
      if (i===this.tabsList.length-1){
        return 'lastTab';
      }  
      return 'middleTab';
    },
    
  }
};
</script>