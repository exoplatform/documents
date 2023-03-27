<template>
  <v-list dense>
    <v-list-item
      v-for="(extension, i) in menuExtensions"
      :key="i"
      class="menu-list px-2 text-left action-menu-item">
      <extension-registry-component
        :component="extension"
        :params="params"
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
    }
  },
  data: () => ({
    menuExtensionApp: 'DocumentMenu',
    menuExtensionType: 'menuActionMenu',
    menuExtensions: {},
    editExtensions: ['edit'],
    sharedDocumentSuspended: true,
    downloadDocumentSuspended: true,
    supportedDocuments: null
  }),
  created() {
    document.addEventListener(`extension-${this.menuExtensionApp}-${this.menuExtensionType}-updated`, this.refreshMenuExtensions);
    document.addEventListener('documents-supported-document-types-updated', this.refreshSupportedDocumentExtensions);
    this.refreshSupportedDocumentExtensions();
    this.refreshMenuExtensions();
  },
  computed: {
    params() {
      return {
        file: this.file,
        isMobile: this.isMobile
      };
    },
    fileCanEdit() {
      const type = this.file && this.file.mimeType || '';
      return this.supportedDocuments && this.supportedDocuments.filter(doc => doc.edit && doc.mimeType === type && !this.file.cloudDriveFile).length > 0;
    }
  },
  methods: {
    refreshSupportedDocumentExtensions() {
      this.supportedDocuments = extensionRegistry.loadExtensions('documents', 'supported-document-types');
    },
    refreshMenuExtensions() {
      let extensions = extensionRegistry.loadExtensions(this.menuExtensionApp, this.menuExtensionType);

      if (!this.fileCanEdit) {
        extensions = extensions.filter(extension => !this.editExtensions.includes(extension.id));
      }
      extensions = extensions.filter(extension => extension.enabled(this.file));

      this.menuExtensions = extensions;
    },
  }
};
</script>
