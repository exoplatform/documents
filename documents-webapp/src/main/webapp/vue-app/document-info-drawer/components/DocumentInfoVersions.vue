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
  <div class="documentInfoVersions">
    <v-progress-linear v-if="loading" indeterminate />
    <div v-else-if="failed" class="text-sub-title pa-4">
      {{ $t('documents.drawer.details.versions.error') }}
    </div>
    <div v-else-if="!versions.length" class="text-sub-title pa-4">
      {{ $t('documents.drawer.details.versions.empty') }}
    </div>
    <version-card
      v-for="version in versions"
      :key="version.id"
      :version="version"
      class="documentInfoVersion py-3"
      disable-restore-version />
  </div>
</template>
<script>
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
    failed: false,
  }),
  watch: {
    file: {
      immediate: true,
      handler() {
        this.load();
      },
    },
  },
  methods: {
    /**
     * Reads the file's versions, newest first; a shortcut shows the versions
     * of the file it points to. A file never versioned has none to read: the
     * server refuses the request for it.
     * @returns {void}
     */
    load() {
      const nodeId = this.file?.sourceID || this.file?.id;
      this.versions = [];
      this.failed = false;
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
        .catch(() => this.failed = true)
        .finally(() => this.loading = false);
    },
  },
};
</script>
