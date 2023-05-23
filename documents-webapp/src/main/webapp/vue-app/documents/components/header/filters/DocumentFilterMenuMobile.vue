<!--
  This file is part of the Meeds project (https://meeds.io/).
  Copyright (C) 2022 Meeds Association
  contact@meeds.io
  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 3 of the License, or (at your option) any later version.
  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.
  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
-->

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
            element="div" />
        </v-list-item>
      </v-list>
      <exo-drawer />
    </template>
  </exo-drawer>
</template>
<script>

export default {
  props: {
    primaryFilter: {
      type: String,
      default: 'all',
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    query: {
      type: String,
      default: null,
    },
    extendedSearch: {
      type: Boolean,
      default: false
    },
  },
  data: () => ({
    quickFilter: false,
    menuExtensionApp: 'DocumentMobileFilterMenu',
    menuExtensionType: 'menuMobileFilterMenu',
    menuExtensions: {},
  }),
  computed: {
    params() {
      return {
        quickFilter: this.quickFilter,
        quickFilterValue: this.primaryFilter,
        isMobile: this.isMobile,
        query: this.query,
        extendedSearch: this.extendedSearch,
        fileType: this.fileType,
        afterDate: this.afterDate,
        beforDate: this.beforDate,
        minSize: this.minSize,
        maxSize: this.maxSize,
      };
    }
  },
  watch: {
    isMobile() {
      if (!this.isMobile){
        this.close();
      }
    }
  },
  created() {
    this.$root.$on('open-mobile-filter-menu', this.open);
    this.$root.$on('close-mobile-filter-menu', this.close);
    this.$root.$on('set-search-type', this.setFilterType);
  },
  methods: {
    open() {
      this.refreshMenuExtensions();
      this.$refs.documentActionsMobileMenu.open();
    },
    close() {
      this.$refs.documentActionsMobileMenu.close();
      this.quickFilter=false;
    },
    setFilterType() {
      this.quickFilter = true;
      this.refreshMenuExtensions();
    },
    refreshMenuExtensions() {
      this.menuExtensions = {};
      let extensions = extensionRegistry.loadExtensions(this.menuExtensionApp, this.menuExtensionType);
      if (this.quickFilter) {
        extensions = extensions.filter(extension => extension.menuType === 'filterOptions');
      }
      if (!this.quickFilter) {
        extensions = extensions.filter(extension => extension.menuType === 'searchType');
      }
      let changed = false;
      extensions.forEach(extension => {
        if (extension.id && (!this.menuExtensions[extension.id] || this.menuExtensions[extension.id] !== extension)) {
          this.menuExtensions[extension.id] = extension;
          changed = true;
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
