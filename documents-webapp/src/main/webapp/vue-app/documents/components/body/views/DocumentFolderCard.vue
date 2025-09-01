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
  <v-hover v-slot="{hover}">
    <v-card
      :elevation="hover ? 4 : 0"
      :class="{ 'border-color': !hover }"
      width="200px"
      @click="openFolder">
      <div class="d-flex flex-no-wrap justify-space-between">
        <div class="d-flex flex-no-wrap">
          <v-avatar
            class="ma-3"
            size="60"
            tile>
            <img
              v-if="isDrive"
              :src="avatarUrl"
              :alt="name"
              width="24"
              height="24">
            <v-icon
              v-else
              size="60"
              class="primary--text">
              fas fa-folder
            </v-icon>
          </v-avatar>
          <div class="align-self-center text-subtitle-2">{{ name }}</div>
        </div>
        <v-card-actions>
          <v-btn
            id="attachment-info"
            :title="$t('attachments.label.details')"
            small
            icon
            class="my-auto mx-0"
            @click="showInfo">
            <v-icon size="20">fa-info-circle</v-icon>
          </v-btn>
        </v-card-actions>
      </div>
    </v-card>
  </v-hover>
</template>

<script>
export default {
  props: {
    folder: {
      type: Object,
      default: null,
    },
  },
  computed: {
    folderId() {
      return this.folder?.id;
    },
    name() {
      return this.folder?.name;
    },
    isDrive() {
      return this.folder?.drive;
    },
    avatarUrl() {
      return this.folder?.avatarUrl;
    }
  },
  methods: {
    showInfo(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
      document.dispatchEvent(new CustomEvent('open-document-info-drawer', {detail: this.folderId}));
    },
    openFolder() {
      this.$root.$emit('document-open-folder', this.folder);
    }
  }
};
</script>