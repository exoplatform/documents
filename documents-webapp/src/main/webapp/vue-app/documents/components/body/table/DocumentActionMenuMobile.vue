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
    isMobile: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    file: null,
    menuExtensionApp: 'DocumentMenu',
    menuExtensionType: 'menuActionMenu',
    menuExtensions: {},
  }),
  computed: {
    params() {
      return {
        file: this.file,
        isMobile: this.isMobile
      };
    }
  },
  created() {
    this.$root.$on('open-file-action-menu', this.open);
    this.$root.$on('close-file-action-menu', this.close);
  },
  methods: {
    open(file) {
      this.file = file;
      this.refreshMenuExtensions();
      this.$refs.documentActionsMobileMenu.open();
    },
    close() {
      this.$refs.documentActionsMobileMenu.close();
    },
    refreshMenuExtensions() {
      let extensions = extensionRegistry.loadExtensions(this.menuExtensionApp, this.menuExtensionType);

      extensions = extensions.filter(extension => extension.enabled(this.file, true));

      this.menuExtensions = extensions;

    },
  }
};
</script>
