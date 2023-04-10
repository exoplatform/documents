<template>
  <v-list dense>
    <v-list-item
      v-for="(extension, i) in menuExtensions"
      :key="i"
      :disabled="isMultiSelection && extension.disabled"
      class="menu-list px-2 text-left action-menu-item">
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
  },
  data: () => ({
    menuExtensionApp: 'DocumentMenu',
    menuExtensionType: 'menuActionMenu',
    menuExtensions: [],
    editExtensions: ['edit'],
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
  computed: {
    fileCanEdit() {
      const type = this.file && this.file.mimeType || '';
      return this.$supportedDocuments && this.$supportedDocuments.filter(doc => doc.edit && doc.mimeType === type && !this.file.cloudDriveFile).length > 0;
    }
  },
  methods: {
    getParams(extension) {
      return {
        file: this.file,
        isMobile: this.isMobile,
        selectedDocuments: this.selectedDocuments,
        disabledExtension: extension.disabled,
        isMultiSelection: this.isMultiSelection
      };
    },
    refreshMenuExtensions() {
      let extensions = extensionRegistry.loadExtensions(this.menuExtensionApp, this.menuExtensionType);

      if (!this.isMultiSelection) {
        if (!this.fileCanEdit) {
          extensions = extensions.filter(extension => !this.editExtensions.includes(extension.id));
        }
        extensions = extensions.filter(extension => extension.enabled(this.file, this.isMobile));

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
