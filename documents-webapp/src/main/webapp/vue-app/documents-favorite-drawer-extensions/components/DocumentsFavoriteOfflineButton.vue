<!--

  Copyright (C) 2003 - 2025 eXo Platform SAS.

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License
  as published by the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU Affero General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program. If not, see <gnu.org/licenses>.

-->
<template>
  <div
    v-if="offlineModeEnabled"
    :title="$t('documents.offline.accessDocumentsTooltip')">
    <v-btn
      id="offlineDocumentsButton"
      class="ms-4"
      icon
      @click="$root.$emit('open-document-offline-files')">
      <v-icon size="20">fa-power-off</v-icon>
    </v-btn>
    <documents-offline-drawer />
  </div>
</template>
<script>
export default {
  data: () => ({
    offlineModeEnabled: false,
  }),
  created() {
    this.init();
  },
  methods: {
    async init() {
      this.offlineModeEnabled = await this.$documentOfflineService.isDatabaseExists();
    },
  }
};
</script>
