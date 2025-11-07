/*
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software Foundation,
 */


<template>
  <div class="group-menu-action">
    <v-menu
      v-model="menuDisplayed"
      transition="slide-x-transition"
      content-class="documentActionMenu "
      offset-x
      close-on-click>
      <template #activator="{ on, attrs }">
        <document-action-item
          v-bind="attrs"
          v-on="on"
          :icon="icon"
          :label=" $t(labelKey)"
          is-group
          @click="menuDisplayed = true" />
      </template>
      <documents-actions-menu
        :file="file"
        :current-view="currentView"
        :is-search-result="isSearchResult"
        :is-mobile="isMobile"
        :parent="id" />
    </v-menu>
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
    id: {
      type: String,
      default: ''
    },
    labelKey: {
      type: String,
      default: ''
    },
    icon: {
      type: String,
      default: ''
    },
  },

  data: () => ({
    menuDisplayed: false,
    waitTimeUntilCloseMenu: 100,
  }),
  created() {
    $(document).on('mousedown', () => {      
      if (this.menuDisplayed) {
        window.setTimeout(() => {
          this.menuDisplayed = false;
        }, this.waitTimeUntilCloseMenu);
      }
    });
  },
  methods: {
    disableLeftClick(event) {
      if (event.button === 2) {
        event.preventDefault();
        event.stopPropagation();
      }
    },
  }
 
};
</script>
