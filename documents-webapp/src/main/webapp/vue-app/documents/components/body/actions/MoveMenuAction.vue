<!--
* Copyright (C) 2022 eXo Platform SAS
*
*  This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program.  If not, see <gnu.org/licenses>.
-->
<template>
  <div>
    <div
      class="clickable py-10px px-4"
      @click="moveDocument()">
      <v-icon
        size="16"
        class="pe-1">
        fa-arrows-alt
      </v-icon>
      <span class="ps-1 text-body menu-text-color">{{ $t('document.label.move') }}</span>
    </div>
    <v-divider
      v-if="isMultiSelection"
      class="mt-1" />
  </div>
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
    disabledExtension: {
      type: Boolean,
      default: false
    },
    isMultiSelection: {
      type: Boolean,
      default: false
    },
    selectedDocuments: {
      type: Array,
      default: () => []
    },
  },
  computed: {
    showCurrentLocation() {
      return this.selectedDocuments.every(file => this.getParentPath(file.path) === this.getParentPath(this.selectedDocuments[0].path));
    },
  },
  methods: {
    getParentPath(path) {
      return path.substring(0, path.lastIndexOf('/'));
    },
    moveDocument(){
      this.$root.$emit('open-document-tree-selector-drawer', this.file, 'move', this.isMultiSelection, this.showCurrentLocation);
      if (this.isMobile) {
        this.$root.$emit('close-file-action-menu');
      }
    }
  },
};
</script>