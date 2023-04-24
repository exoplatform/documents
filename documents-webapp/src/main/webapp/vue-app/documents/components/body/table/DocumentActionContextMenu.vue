<!--
 *
 * Copyright (C) 2023 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 *
-->
<template>
  <div
    id="action-context-menu"
    ref="actionContextMenu"
    class="elevation-2 documentActionMenu ">
    <documents-actions-menu
      v-if="show"
      :file="file"
      :selected-documents="selectedDocuments"
      :is-multi-selection="isMultiSelection"
      :is-mobile="false" />
  </div>
</template>

<script>
export default {
  data: () => ({
    file: {},
    show: false,
    selectedDocuments: [],
    isMultiSelection: false,
  }),
  created() {
    document.addEventListener('click', this.handleMenuClose);
    document.addEventListener('contextmenu', this.handleMenuClose);
    this.$root.$on('open-action-context-menu', this.openMenu);
    this.$root.$on('prevent-action-context-menu', this.handlePreventMenu);
    this.$root.$on('selection-documents-list-updated', this.handleUpdateSelected);
    this.$root.$on('set-action-loading', this.closeMenu);
  },
  beforeDestroy() {
    this.$root.$off('open-action-context-menu', this.openMenu);
    this.$root.$off('selection-documents-list-updated', this.handleUpdateSelected);
    this.$root.$off('prevent-action-context-menu', this.handlePreventMenu);
    this.$root.$off('set-action-loading', this.closeMenu);
  },
  computed: {
    isSelected() {
      return this.selectedDocuments.findIndex(file => file.id === this.file.id) !== -1;
    },
  },
  methods: {
    closeMenu() {
      setTimeout(() => this.show = false, 200);
    },
    handlePreventMenu() {
      this.show = false;
    },
    handleMenuClose(event, file) {
      if (event.which === 1) {
        this.show = false;
      }
      if (!file) {
        this.show = false;
      }
    },
    handleUpdateSelected() {
      this.show = false;
    },
    openMenu(event, file, selectedDocuments) {
      event.preventDefault();
      if (event.which === 0) {
        return;
      }
      this.file = file;
      this.selectedDocuments = selectedDocuments;
      this.isMultiSelection = this.selectedDocuments?.length > 1 && this.isSelected ;
      this.$refs.actionContextMenu.style.setProperty('--mouse-x', `${event.clientX  }px`);
      this.$refs.actionContextMenu.style.setProperty('--mouse-y', `${event.clientY  }px`);
      this.$refs.actionContextMenu.style.display = 'block';
      this.show = false;
      setTimeout(() => this.show = true, 100);
    },
  }
};
</script>
