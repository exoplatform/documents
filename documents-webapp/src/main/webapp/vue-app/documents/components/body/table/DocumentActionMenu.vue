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
    }
  },
  data: () => ({
    menuExtensionApp: 'DocumentMenu',
    menuExtensionType: 'menuActionMenu',
    menuExtensions: {},
    mobileOnlyExtensions: ['favorite','details'],
    desktopOnlyExtensions: ['edit'],
    editExtensions: 'edit',
    fileOnlyExtension: ['download','favorite','visibility']
  }),
  created() {
    document.addEventListener(`extension-${this.menuExtensionApp}-${this.menuExtensionType}-updated`, this.refreshMenuExtensions);
    this.refreshMenuExtensions();
  },
  computed: {
    params() {
      return {
        file: this.file
      };
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    fileCanEdit(){
      const type = this.file && this.file.mimeType || '';
      return ( type.includes('word') || type.includes('presentation') || type.includes('sheet') );
    }
  },
  methods: {
    isSymlink() {
      return this.file && this.file.sourceID;
    },
    refreshMenuExtensions() {
      let extensions = extensionRegistry.loadExtensions(this.menuExtensionApp, this.menuExtensionType);
      if (!this.fileCanEdit) {
        extensions = extensions.filter(extension => extension.id !== this.editExtensions);
      }
      extensions = extensions.filter(extension => extension.enabled(this.file.acl, this.isSymlink()));
      let changed = false;
      extensions.forEach(extension => {
        if (extension.id && (!this.menuExtensions[extension.id] || this.menuExtensions[extension.id] !== extension)) {
          if (((!this.isMobile && !this.mobileOnlyExtensions.includes(extension.id))
              || (this.isMobile && !this.desktopOnlyExtensions.includes(extension.id)))
          && (!this.file.folder || (this.file.folder && !this.fileOnlyExtension.includes(extension.id)))) {
            this.menuExtensions[extension.id] = extension;
            changed = true;
          }
        }
      });
      // force update of attribute to re-render switch new extension id
      if (changed) {
        this.menuExtensions = Object.assign({}, this.menuExtensions);
      }
    },
  }
};
</script>