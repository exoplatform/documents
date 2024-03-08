<template>
  <v-list dense>
    <v-list-item
      v-for="(extension, i) in menuExtensions"
      :key="i"
      :disabled="isMultiSelection && extension.disabled"
      class="text-caption px-2 text-left action-menu-item">
      <extension-registry-component
        :component="extension"
        :params="getParams(extension)"
        class="width-full"
        :element="div" />
    </v-list-item>
  </v-list>
</template>
<script>

export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    selectedDocuments: {
      type: Array,
      default: () => []
    },
    isMultiSelection: {
      type: Boolean,
      default: false
    },
    currentView: {
      type: String,
      default: ''
    },
  },
  data: () => ({
    menuExtensionApp: 'DocumentMenu',
    menuExtensionType: 'menuActionMenu',
    menuExtensions: [],
    sharedDocumentSuspended: true,
    downloadDocumentSuspended: true,
    supportedDocuments: null
  }),
  created() {
    this.$root.$on('selection-documents-list-updated', this.refreshMenuExtensions);
    document.addEventListener(`extension-${this.menuExtensionApp}-${this.menuExtensionType}-updated`, this.refreshMenuExtensions);
    this.refreshMenuExtensions();
  },
  beforeDestroy() {
    this.$root.$off('selection-documents-list-updated', this.refreshMenuExtensions);
  },
  methods: {
    getParams(extension) {
      return {
        file: this.file,
        isMobile: this.isMobile,
        selectedDocuments: this.selectedDocuments,
        disabledExtension: extension.disabled,
        isMultiSelection: this.isMultiSelection,
        currentView: this.currentView
      };
    },
    refreshMenuExtensions() {
      let extensions = extensionRegistry.loadExtensions(this.menuExtensionApp, this.menuExtensionType);

      if (!this.isMultiSelection) {
        extensions = extensions.filter(extension => extension.enabled(this.file, this.isMobile, this.currentView));

      } else {
        extensions = extensions.filter(extension => extension.enabledForMultiSelection());
        extensions.forEach(extension => {
          extension.disabled = this.selectedDocuments.some(file => !extension.enabled(file));
        });
      }
      this.menuExtensions = extensions;
    },
  }
};
</script>
