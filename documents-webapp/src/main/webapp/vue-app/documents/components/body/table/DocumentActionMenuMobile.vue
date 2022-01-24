<template>
  <exo-drawer 
    ref="documentActionsMobileMenu"
    :bottom="true">
    <template slot="content">
      <v-list dense>
        <v-list-item
          v-for="(extension, i) in menuExtensions"
          :key="i"
          :class="isMobile && 'mobile-menu-item'"
          class="px-2 text-left">
          <extension-registry-component
            :component="extension"
            :params="params"
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
    file: {
      type: Object,
      default: null,
    }
  },
  data: () => ({
    menuExtensionApp: 'DocumentMenu',
    menuExtensionType: 'menuActionMenu',
    menuExtensions: {},
    mobileOnlyExtensions: ['favorite'],
    desktopOnlyExtensions: ['edit'],
    editExtensions: 'edit',
  }),
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
  created() {
    document.addEventListener(`extension-${this.menuExtensionApp}-${this.menuExtensionType}-updated`, this.refreshMenuExtensions);
    this.refreshMenuExtensions();
  },
  methods: {
    open() {
      this.$refs.documentActionsMobileMenu.open();
    },
    close() {
      this.$refs.documentActionsMobileMenu.close();
    },
    refreshMenuExtensions() {
      let extensions = extensionRegistry.loadExtensions(this.menuExtensionApp, this.menuExtensionType);
      if (!this.fileCanEdit) {
        extensions = extensions.filter(extension => extension.id !== this.editExtensions);
      }
      let changed = false;
      extensions.forEach(extension => {
        if (extension.id && (!this.menuExtensions[extension.id] || this.menuExtensions[extension.id] !== extension)) {
          if ( (!this.isMobile && !this.mobileOnlyExtensions.includes(extension.id))
              || (this.isMobile && !this.desktopOnlyExtensions.includes(extension.id))) {
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
