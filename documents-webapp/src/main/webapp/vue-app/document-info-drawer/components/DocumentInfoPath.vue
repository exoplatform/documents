<!--
 * Copyright (C) 2026 eXo Platform SAS.
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
-->
<template>
  <div
    v-if="folders.length"
    :title="fullPath"
    class="documentInfoPath d-flex align-center py-1">
    <v-btn
      :aria-label="homeLabel"
      class="flex-shrink-0"
      height="24"
      width="24"
      icon
      @click="openFolder(folders[0])">
      <v-icon size="16">fas fa-home</v-icon>
    </v-btn>
    <template v-if="middleFolders.length">
      <v-icon size="10" class="mx-1 flex-shrink-0">fa-chevron-right</v-icon>
      <v-menu offset-y content-class="documentInfoMenu">
        <template #activator="{on, attrs}">
          <v-btn
            :aria-label="$t('documents.label.parentFolders')"
            class="flex-shrink-0"
            height="24"
            min-width="24"
            text
            small
            v-bind="attrs"
            v-on="on">
            …
          </v-btn>
        </template>
        <v-list dense class="py-1">
          <v-list-item
            v-for="folder in middleFolders"
            :key="folder.id"
            link
            @click="openFolder(folder)">
            <v-list-item-title class="text-body">{{ folder.name }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </template>
    <template v-if="lastFolder">
      <v-icon size="10" class="mx-1 flex-shrink-0">fa-chevron-right</v-icon>
      <button
        type="button"
        class="font-weight-bold primary--text text-truncate pa-0"
        @click="openFolder(lastFolder)">
        {{ lastFolder.name }}
      </button>
    </template>
  </div>
</template>
<script>
export default {
  props: {
    folderId: {
      type: String,
      default: null,
    },
  },
  data: () => ({
    folders: [],
  }),
  computed: {
    /**
     * The folders between the drive's root and the file's own folder,
     * collapsed behind « … ».
     * @returns {Array} the folders, from the root down
     */
    middleFolders() {
      return this.folders.length > 2 && this.folders.slice(1, -1) || [];
    },
    /**
     * The file's own folder, when it is not the drive's root.
     * @returns {Object} the folder, or null
     */
    lastFolder() {
      return this.folders.length > 1 && this.folders[this.folders.length - 1] || null;
    },
    homeLabel() {
      return this.folders[0]?.name === 'Private' && this.$t('documents.label.userHomeDocuments') || this.$t('documents.label.spaceHomeDocuments');
    },
    fullPath() {
      return [this.homeLabel, ...this.folders.slice(1).map(folder => folder.name)].join(' › ');
    },
  },
  watch: {
    folderId: {
      immediate: true,
      handler() {
        this.load();
      },
    },
  },
  methods: {
    /**
     * Reads the folders from the drive's root down to the file's folder.
     * @returns {void}
     */
    load() {
      const folderId = this.folderId;
      this.folders = [];
      if (!folderId) {
        return;
      }
      const ownerId = eXo.env.portal.spaceIdentityId || eXo.env.portal.userIdentityId;
      this.$documentFileService.getBreadCrumbs(folderId, ownerId)
        .then(folders => {
          if (folderId === this.folderId) {
            this.folders = folders || [];
          }
        })
        .catch(() => this.folders = []);
    },
    /**
     * Opens a folder of the path in the Documents application.
     * @param {Object} folder the folder, with its path
     * @returns {void}
     */
    openFolder(folder) {
      this.$root.$emit('open-folder', folder);
    },
  },
};
</script>
