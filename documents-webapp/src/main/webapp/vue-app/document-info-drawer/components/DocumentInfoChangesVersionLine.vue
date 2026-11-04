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
  <v-list-item
    v-if="entry && entry.version"
    :title="$t('documents.drawer.details.versions.open')"
    class="documentInfoChangesVersion px-0"
    link
    @click="openVersion">
    <v-list-item-content class="py-3">
      <version-card :version="entry.version" />
    </v-list-item-content>
  </v-list-item>
</template>
<script>
import {openFileVersion} from '../../../js/DocumentVersionUtils.js';

export default {
  props: {
    entry: {
      type: Object,
      default: null,
    },
    file: {
      type: Object,
      default: null,
    },
  },
  methods: {
    /**
     * Opens the version read only, as the Versions tab does; restoring a
     * version and writing its summary stay in that tab.
     * @returns {Promise} resolved once opened
     */
    openVersion() {
      return openFileVersion(this.entry.version, this.$supportedDocuments, file => this.iconOf(file))
        .catch(() => document.dispatchEvent(new CustomEvent('alert-message', {detail: {
          alertMessage: this.$t('documents.drawer.details.versions.openError'),
          alertType: 'error',
        }})));
    },
    /**
     * Gives the icon of a file's type, as the Documents list shows it.
     * @param {Object} file the file
     * @returns {Object} the icon
     */
    iconOf(file) {
      const extension = this.$documentsIconsExtension?.[0];
      return extension?.get(file?.mimeType) || extension?.get('file');
    },
  },
};
</script>
