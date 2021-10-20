<template>
  <v-app
    class="documents-application border-box-sizing"
    role="main"
    flat>
    <div class="pa-3 white">
      <documents-header class="py-2" />
      <documents-body :view-extension="selectedViewExtension" />
    </div>
  </v-app>
</template>
<script>
export default {
  data: () => ({
    extensionApp: 'Documents',
    extensionType: 'views',
    viewExtensions: {},
    selectedView: null,
  }),
  computed: {
    selectedViewExtension() {
      if (this.selectedView) {
        return this.viewExtensions.find(viewExtension => viewExtension.id === this.selectedView);
      } else if (Object.keys(this.viewExtensions).length) {
        const sortedViewExtensions = Object.values(this.viewExtensions).sort((ext1, ext2) => ext1.rank - ext2.rank);
        return sortedViewExtensions[0];
      }
      return null;
    },
  },
  created() {
    document.addEventListener(`extension-${this.extensionApp}-${this.extensionType}-updated`, this.refreshViewExtensions);
    this.refreshViewExtensions();
    this.$root.$applicationLoaded();
  },
  methods: {
    refreshViewExtensions() {
      const extensions = extensionRegistry.loadExtensions(this.extensionApp, this.extensionType);
      let changed = false;
      extensions.forEach(extension => {
        if (extension.id && (!this.viewExtensions[extension.id] || this.viewExtensions[extension.id] !== extension)) {
          this.viewExtensions[extension.id] = extension;
          changed = true;
        }
      });
      // force update of attribute to re-render switch new extension id
      if (changed) {
        this.viewExtensions = Object.assign({}, this.viewExtensions);
      }
    },
  },
};
</script>