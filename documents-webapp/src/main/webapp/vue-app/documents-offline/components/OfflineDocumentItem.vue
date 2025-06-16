<!--
 Copyright (C) 2025 eXo Platform SAS.

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
  <v-hover v-slot="{ hover }">
    <v-card
      :id="id"
      :elevation="hover ? 4 : 0"
      :class="{
        'border-color-transparent': hover,
        'border-color': !hover,
      }"
      :loading="loading"
      :title="file.name"
      max-height="210px"
      max-width="100%"
      height="160px"
      width="180px"
      class="overflow-hidden d-flex flex-column content-box-sizing"
      @click="openFile">
      <div class="d-flex flex-grow-0 flex-shrink-0 align-center justify-center">
        <v-icon
          :color="fileIconColor"
          class="mt-5"
          size="80">
          {{ fileIconClass }}
        </v-icon>
      </div>
      <v-card
        class="d-flex flex-grow-1 flex-shrink-1 flex-column no-border-radius align-center justify-center"
        height="36"
        flat>
        <div class="font-weight-bold text-truncate-2">
          {{ file.name }}
        </div>
      </v-card>
    </v-card>
  </v-hover>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    }
  },
  computed: {
    icon() {
      return this.$documentsIconsExtension?.[0]?.get?.(this.file?.mimeType);
    },
    fileIconClass() {
      return this.icon?.class || 'fas fa-file';
    },
    fileIconColor() {
      return this.icon?.color || 'secondary';
    },
    isMobile() {
      return this.$vuetify.breakpoint.smAndDown;
    },
  },
  methods: {
    openFile() {
      // TODO Open File System
    },
  },
};
</script>