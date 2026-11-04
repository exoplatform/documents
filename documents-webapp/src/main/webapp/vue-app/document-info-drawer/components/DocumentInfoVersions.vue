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
  <version-history-drawer
    ref="drawer"
    :versions="versions"
    :can-manage="canManage"
    :enable-edit-description="canManage"
    :is-loading="loading"
    class="documentInfoVersions"
    @open-version="openVersion"
    @restore-version="restoreVersion"
    @version-update-description="updateSummary" />
</template>
<script>
import {openFileVersion} from '../../../js/DocumentVersionUtils.js';

export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    versions: [],
    loading: false,
  }),
  computed: {
    /**
     * Lets a viewer who may edit the file restore a version and write its
     * summary, as the Documents version history does; the server checks it
     * again.
     * @returns {boolean} true when the viewer may edit the file
     */
    canManage() {
      return !!this.file?.acl?.canEdit;
    },
  },
  methods: {
    /**
     * Opens the file's version history, where a version is restored and its
     * summary written, read afresh.
     * @returns {void}
     */
    open() {
      this.load();
      this.$refs.drawer.open();
    },
    /**
     * Reads the file's versions, newest first; a shortcut shows the versions
     * of the file it points to. A file never versioned has none to read: the
     * server refuses the request for it.
     * @returns {void}
     */
    load() {
      const nodeId = this.file?.sourceID || this.file?.id;
      this.versions = [];
      if (!nodeId || !this.file.versionable) {
        return;
      }
      this.loading = true;
      this.$documentFileService.getFileVersions(nodeId)
        .then(result => {
          if (nodeId === (this.file?.sourceID || this.file?.id)) {
            this.versions = (result?.versions || []).slice().sort((a, b) => b.versionNumber - a.versionNumber);
          }
        })
        .catch(() => this.alert(this.$t('documents.drawer.details.versions.error'), 'error'))
        .finally(() => this.loading = false);
    },
    /**
     * Opens a version read only, as the Documents version history does.
     * @param {Object} version the version
     * @returns {Promise} resolved once opened
     */
    openVersion(version) {
      return openFileVersion(version, this.$supportedDocuments, file => this.iconOf(file))
        .catch(() => this.alert(this.$t('documents.drawer.details.versions.openError'), 'error'));
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
    /**
     * Restores a version as the file's current content, then reads the
     * versions again and tells the drawer, and the Documents list behind it
     * when the page shows one, that the file changed.
     * @param {Object} version the version to restore
     * @returns {Promise} resolved once done
     */
    restoreVersion(version) {
      return this.$documentFileService.restoreVersion(version.id)
        .then(newVersion => {
          this.$root.$emit('version-restored', newVersion || version);
          this.alert(this.$t('documents.restore.version.success'), 'success');
          this.load();
          this.$emit('restored');
          this.$emit('changed');
          document.dispatchEvent(new CustomEvent('document-version-restored', {detail: {fileId: this.file.id}}));
        })
        .catch(() => {
          this.$root.$emit('version-restore-error');
          this.alert(this.$t('documents.restore.version.error'), 'error');
        });
    },
    /**
     * Saves a version's summary, as the Documents version history does.
     * @param {Object} version the version
     * @param {string} summary its new summary
     * @returns {Promise} resolved once saved
     */
    updateSummary(version, summary) {
      if (!summary) {
        this.$root.$emit('version-description-update-error', version);
        this.alert(this.$t('document.summary.add.empty.error'), 'warning');
        return Promise.resolve();
      }
      const cleanSummary = summary.replaceAll(':', ' ').replaceAll('*', ' ');
      return this.$documentFileService.updateVersionSummary(version.originId, version.id, cleanSummary)
        .then(updatedVersion => {
          this.$root.$emit('version-description-updated', updatedVersion || {...version, summary: cleanSummary});
          this.$emit('changed');
          this.alert(this.$t('documents.summary.added.success'), 'success');
        })
        .catch(() => {
          this.$root.$emit('version-description-update-error', version);
          this.alert(this.$t('documents.summary.added.error'), 'error');
        });
    },
    /**
     * Shows a message in the page's alert area.
     * @param {string} message the message
     * @param {string} type success, warning or error
     * @returns {void}
     */
    alert(message, type) {
      document.dispatchEvent(new CustomEvent('alert-message', {detail: {alertMessage: message, alertType: type}}));
    },
  },
};
</script>
