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
    v-if="entry && entry.version"
    class="documentInfoChangesVersion py-3 border-bottom-color">
    <div class="text-body-2 font-weight-bold">
      {{ $t('documents.drawer.details.changes.version', {0: entry.version.versionNumber}) }}
    </div>
    <div v-if="entry.version.summary" class="text-body-2 text-break my-1">{{ entry.version.summary }}</div>
    <div class="d-flex align-center flex-wrap text-caption text-light-color">
      <user-avatar
        v-if="entry.version.author"
        :profile-id="entry.version.author"
        extra-class="me-2"
        :size="20"
        small-font-size
        popover />
      <date-format
        :value="entry.time"
        :format="dateFormat"
        class="me-2" />
      <span class="me-1">·</span>
      <a
        :aria-label="$t('documents.drawer.details.changes.openVersion', {0: entry.version.versionNumber})"
        href="#"
        class="documentInfoChangesVersionOpen primary--text"
        @click.prevent="openVersion">
        {{ $t('documents.drawer.details.changes.open') }}
      </a>
    </div>
  </div>
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
  data: () => ({
    dateFormat: {year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'},
  }),
  methods: {
    /**
     * Opens the version read only; restoring a version and writing its
     * summary stay in the version history the details' menu opens.
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
