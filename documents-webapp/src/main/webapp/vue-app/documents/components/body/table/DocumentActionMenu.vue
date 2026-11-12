<template>
  <v-list
    :role="!isMobile && standalone ? 'menu' : null"
    class="py-0 transparent"
    data-documents-action-menu
    dense
    @click.native.capture="handleItemActivation"
    @keydown.native="handleKeydown">
    <v-list-item
      v-for="(extension, i) in menuExtensions"
      :key="i"
      :disabled="isMultiSelection && extension.disabled"
      :role="isMobile ? null : 'none'"
      class="text-caption px-0 text-left action-menu-item">
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
    // shown outside a v-menu (the right-click menu): the list itself is the menu
    standalone: {
      type: Boolean,
      default: false,
    },
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
    isSearchResult: {
      type: Boolean,
      default: false
    },
    parent: {
      type: String,
      default: ''
    },
    active: {
      type: Boolean,
      default: true
    },
  },
  data: () => ({
    opener: null,
    menuExtensionApp: 'DocumentMenu',
    menuExtensionType: 'menuActionMenu',
    menuExtensions: [],
    sharedDocumentSuspended: true,
    downloadDocumentSuspended: true,
    supportedDocuments: null
  }),
  watch: {
    active() {
      if (this.active) {
        this.openMenu();
      } else {
        this.restoreFocus();
      }
    },
  },
  created() {
    this.$root.$on('selection-documents-list-updated', this.refreshMenuExtensions);
    document.addEventListener(`extension-${this.menuExtensionApp}-${this.menuExtensionType}-updated`, this.refreshMenuExtensions);
    this.refreshMenuExtensions();
  },
  mounted() {
    if (this.active) {
      this.openMenu();
    }
  },
  beforeDestroy() {
    this.$root.$off('selection-documents-list-updated', this.refreshMenuExtensions);
  },
  methods: {
    openMenu() {
      if (this.isMobile) {
        return;
      }
      const active = document.activeElement;
      this.opener = active && active !== document.body && !this.$el.contains(active) ? active : null;
      this.focusMenuItem(0);
    },
    getMenuItems() {
      // every entry must carry role="menuitem" to be reachable by the arrow keys
      // this menu's own entries, not those of a sub-menu rendered inside it
      return Array.from(this.$el.querySelectorAll('[role="menuitem"]'))
        .filter(item => item.closest('[data-documents-action-menu]') === this.$el);
    },
    focusMenuItem(index, attempt = 0) {
      const item = this.getMenuItems()[index];
      if (item) {
        item.focus();
      }
      if (document.activeElement !== item && attempt < 10) {
        window.requestAnimationFrame(() => this.focusMenuItem(index, attempt + 1));
      }
    },
    restoreFocus() {
      const active = document.activeElement;
      if (this.opener && document.body.contains(this.opener)
          && (!active || active === document.body || this.$el.contains(active))) {
        this.opener.focus();
      }
    },
    handleItemActivation(event) {
      if (!this.isMobile && !event.target.closest('[aria-haspopup]')) {
        this.restoreFocus();
      }
    },
    handleKeydown(event) {
      if (this.isMobile) {
        return;
      }
      const items = this.getMenuItems();
      const current = items.indexOf(document.activeElement);
      let next;
      if (event.key === 'ArrowDown') {
        next = (current + 1) % items.length;
      } else if (event.key === 'ArrowUp') {
        next = current <= 0 ? items.length - 1 : current - 1;
      } else if (event.key === 'Home') {
        next = 0;
      } else if (event.key === 'End') {
        next = items.length - 1;
      } else {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      if (items[next]) {
        items[next].focus();
      }
    },
    getParams(extension) {
      return {
        ...(extension.componentParams || {}),
        file: this.file,
        isMobile: this.isMobile,
        selectedDocuments: this.selectedDocuments,
        disabledExtension: extension.disabled,
        isMultiSelection: this.isMultiSelection,
        currentView: this.currentView,
        isSearchResult: this.isSearchResult,
        parent: extension.parent,
        id: extension.id,
        labelKey: extension.labelKey,
        icon: extension.icon,
      };
    },
    refreshMenuExtensions() {
      let extensions = extensionRegistry.loadExtensions(this.menuExtensionApp, this.menuExtensionType);
      if (!this.isMultiSelection) {
        let iconExtension;
        if (!this.file?.folder && this.$documentsIconsExtension?.[0]) {
          iconExtension = this.$documentsIconsExtension?.[0]?.get?.(this.file?.mimeType);
        }
        extensions = extensions.filter(extension => extension.enabled(this.file, this.isMobile, this.currentView, this.isSearchResult, iconExtension));

      } else {
        extensions = extensions.filter(extension => extension?.enabledForMultiSelection?.());
        extensions.forEach(extension => {
          extension.disabled = this.selectedDocuments.some(file => !extension.enabled(file));
        });
      }

      if (!this.isMobile && !this.isMultiSelection) {
        const groupingExtensions =  extensions.filter(extension => extension.type === 'group');
        groupingExtensions.forEach(extension => {
          if (!extensions.some(ext => ext.parent === extension.id)) {
            extensions = extensions.filter(extension_ => extension_.id !== extension.id);
          }
        });
        if (this.parent) {
          extensions = extensions.filter(extension => extension.parent === this.parent);
        } else {
          extensions = extensions.filter(extension => !extension.parent);
        }
      }

      this.menuExtensions = extensions;
    },
  }
};
</script>
