<!--
 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <div
    v-if="!file.drive"
    :id="`document-action-menu-cel-${file.id}`"
    :class="menuDisplayed && 'documents-row-actions--open'"
    class="documents-row-actions d-flex align-center justify-end flex-nowrap">
    <v-tooltip
      :open-delay="500"
      bottom>
      <template #activator="{ on, attrs }">
        <v-btn
          :aria-label="detailsLabel"
          class="documents-row-action documents-row-details"
          icon
          small
          v-bind="attrs"
          v-on="on"
          @click="displayDetails">
          <v-icon size="16">fa-info-circle</v-icon>
        </v-btn>
      </template>
      <span>{{ detailsLabel }}</span>
    </v-tooltip>
    <documents-favorite-button
      v-if="canBeFavorite"
      :file="file"
      class="documents-row-action documents-row-favorite" />
    <span
      v-else
      class="documents-row-action-slot"></span>
    <v-tooltip
      v-if="canManageAccess"
      :open-delay="500"
      bottom>
      <template #activator="{ on, attrs }">
        <v-btn
          :aria-label="accessLabel"
          class="documents-row-action documents-row-access"
          icon
          small
          v-bind="attrs"
          v-on="on"
          @click="manageAccess">
          <v-icon size="16">fas fa-shield-alt</v-icon>
        </v-btn>
      </template>
      <span>{{ accessLabel }}</span>
    </v-tooltip>
    <span
      v-else
      class="documents-row-action-slot"></span>
    <v-tooltip
      :disabled="menuDisplayed"
      :open-delay="500"
      bottom>
      <template #activator="{ on, attrs }">
        <v-btn
          :aria-label="menuLabel"
          aria-haspopup="menu"
          :aria-expanded="String(menuDisplayed)"
          class="documents-row-action documents-row-menu"
          icon
          small
          v-bind="attrs"
          v-on="on"
          @click="displayActionMenu">
          <v-icon size="18">mdi-dots-vertical</v-icon>
        </v-btn>
      </template>
      <span>{{ menuLabel }}</span>
    </v-tooltip>
    <v-menu
      v-model="menuDisplayed"
      transition="slide-x-reverse-transition"
      content-class="documentActionMenu application-menu layout-drawer"
      :position-x="menuX"
      :position-y="menuY"
      :nudge-right="30"
      :nudge-top="25"
      offset-y
      offset-x
      close-on-click
      absolute>
      <documents-actions-menu
        :file="file"
        :current-view="selectedView"
        :is-search-result="isSearchResult"
        :is-mobile="isMobile" />
    </v-menu>
  </div>
</template>
<script>
import {canManageAccess as mayManageAccess, openManageAccess} from '../../../../js/DocumentsSharingSign.js';

export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    extension: {
      type: Object,
      default: null,
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    selectedView: {
      type: String,
      default: null
    },
    isSearchResult: {
      type: Boolean,
      default: false
    },
  },
  data: () => ({
    menuDisplayed: false,
    menuX: 0,
    menuY: 0,
  }),
  computed: {
    canBeFavorite() {
      return !this.file.folder && !this.file.cloudDriveFolder;
    },
    canManageAccess() {
      return mayManageAccess(this.file);
    },
    accessLabel() {
      return this.$t('documents.label.visibility');
    },
    detailsLabel() {
      return this.$t('documents.label.show.details');
    },
    menuLabel() {
      return this.$t('documents.label.menu.action.tooltip');
    },
  },
  watch: {
    menuDisplayed() {
      const row = this.$el?.closest?.('tr');
      if (row) {
        row.classList.toggle('documents-row--menu-open', this.menuDisplayed);
      }
    },
  },
  created() {
    document.addEventListener('mousedown', this.closeMenuOnOutsideClick);
  },
  beforeDestroy() {
    document.removeEventListener('mousedown', this.closeMenuOnOutsideClick);
  },
  methods: {
    /**
     * Closes the action menu when the user presses anywhere but on one of
     * its groups, late enough for a clicked action to run first.
     *
     * @param {MouseEvent} event the mousedown on the page
     * @returns {void}
     */
    closeMenuOnOutsideClick(event) {
      if (this.menuDisplayed && !event.target?.closest?.('.group-menu-action')) {
        window.setTimeout(() => this.menuDisplayed = false, 200);
      }
    },
    /**
     * Opens Manage access for the file.
     *
     * @returns {void}
     */
    manageAccess() {
      openManageAccess(this.$root, this.file, this.selectedView === 'timeline' ? 'recentView' : 'folderView');
    },
    /**
     * Opens the file's details panel.
     *
     * @returns {void}
     */
    displayDetails() {
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: {id: this.file.id, ownerId: this.$root.ownerId}}));
    },
    /**
     * Opens the file's action menu under the ⋮ button.
     *
     * @param {Event} event the click, by pointer or keyboard, on the button
     * @returns {void}
     */
    displayActionMenu(event) {
      const rect = event.currentTarget.getBoundingClientRect();
      this.menuX = rect.left;
      this.menuY = rect.bottom;
      this.menuDisplayed = true;
    },
  },
};
</script>
