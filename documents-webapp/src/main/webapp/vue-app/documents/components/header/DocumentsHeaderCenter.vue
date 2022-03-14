<template>
  <div class="d-flex flex-row">
    <v-tabs
      v-model="tab"
      class="documentViewTabs">
      <v-tab
        v-for="(extension, i) in tabsList"
        :key="i"
        :class="isMobile ? tabClass(i) + ' px-0' : tabClass(i)"
        @change="changeDocumentView(extension.viewName)">
        <v-icon :class="isMobile ? 'tabIcon mobile':'tabIcon'">{{ extension.icon }}</v-icon>
        {{ !isMobile ? $t(extension.labelKey) : '' }}
      </v-tab>
    </v-tabs>
  </div>
</template>

<script>
export default {
  props: {
    filesSize: {
      type: Number,
      default: 0
    }
  },
  data: () => ({
    tabsExtensionApp: 'DocumentTabs',
    tabsExtensionType: 'documentsHeaderTab',
    tabsExtensions: {},
    mobileOnlyTabsExtensions: [],
    desktopOnlyTabsExtensions: [],
    tabsList: [],
    tab: 0,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    spaceId() {
      return eXo.env.portal.spaceId;
    },
  },
  created() {
    document.addEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshTabExtensions);
    this.refreshTabExtensions();
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
    if (queryParams.has('view')) {
      const view = queryParams.get('view');
      if (view.toLowerCase() === 'folder'){
        this.tab = 1;
      } else {
        this.tab = 0;
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
      const viewTab = view ==='folder'? 'FOLDER' : 'RECENT';
      document.dispatchEvent(new CustomEvent('document-change', {
        detail: {
          'type': 'document',
          'spaceId': this.spaceId,
          'name': `Switch View ${viewTab}`
        }
      }));
      if (view ==='folder'){
        const url= new URL(window.location.href);
        url.searchParams.set('view',view);
        window.history.pushState('documents', 'Documents', url.toString());
      } else {
        const pathParts = eXo.env.server.portalBaseURL.toLowerCase().split(eXo.env.portal.selectedNodeUri.toLowerCase());
        window.history.pushState('documents', 'Documents', `${pathParts[0]}${eXo.env.portal.selectedNodeUri}?view=timeline`);
      }
    },

    tabClass(i) {
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