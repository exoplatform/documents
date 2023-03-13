<template>
  <div class="d-flex flex-row">
    <v-tabs
      :value="tab"
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
    },
    selectedView: {
      type: String,
      default: '',
    },
    isMobile: {
      type: Boolean,
      default: false
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
    spaceId() {
      return eXo.env.portal.spaceId;
    },
  },
  watch: {
    selectedView: {
      immediate: true,
      handler() {
        const tabsExtensionIds = Object.values(this.tabsExtensions).map(extension => extension.viewName);
        this.tab = tabsExtensionIds.includes(this.selectedView) ? tabsExtensionIds.indexOf(this.selectedView) : 0;
      },
    },
    tabsExtensions() {
      const tabsExtensionIds = Object.values(this.tabsExtensions).map(extension => extension.viewName);
      this.tab = tabsExtensionIds.includes(this.selectedView) ? tabsExtensionIds.indexOf(this.selectedView) : 0;
    },
  },
  created() {
    document.addEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshTabExtensions);
    this.refreshTabExtensions();
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
          'name': `Switch View ${viewTab} Tab`
        }
      }));
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