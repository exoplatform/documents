<template>
  <exo-drawer 
    ref="documentActionsMobileMenu"
    class="mobileDrawer"
    :bottom="true">
    <template slot="content">
      <v-list dense>
        <v-list-item
          v-for="(extension, i) in menuExtensions"
          :key="i"
          :disabled="extension.disabled"
          :class="isMobile && 'mobile-menu-item'"
          class="px-2 text-left">
          <extension-registry-component
            :component="extension"
            :params="getParams(extension)"
            class="width-full"
            :element="div" />
        </v-list-item>
      </v-list>
      <exo-drawer />
    </template>
  </exo-drawer>
</template>
<script>

export default {
  props: {
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    file: null,
    menuExtensionApp: 'DocumentMenu',
    menuExtensionType: 'menuActionMenu',
    menuExtensions: [],
    selectedDocuments: [],
    isMultiSelection: false
  }),
  created() {
    this.$root.$on('open-file-action-menu', this.open);
    this.$root.$on('open-file-action-menu-for-multi-selection', this.openForMultiSelection);
    this.$root.$on('close-file-action-menu', this.close);
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
    open(file) {
      this.file = file;
      this.refreshMenuExtensions();
      this.$refs.documentActionsMobileMenu.open();
    },
    openForMultiSelection(selectedDocuments) {
      this.selectedDocuments = selectedDocuments;
      this.isMultiSelection = true;
      this.refreshMenuExtensions();
      this.$refs.documentActionsMobileMenu.open();
    },
    close() {
      this.$refs.documentActionsMobileMenu.close();
    },
    refreshMenuExtensions() {
      let extensions = extensionRegistry.loadExtensions(this.menuExtensionApp, this.menuExtensionType);

      if (!this.isMultiSelection) {
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
