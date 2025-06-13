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
  <v-card
    class="px-4"
    flat>
    <div class="text-header my-5">{{ $t('OfflineApp.pwa.offlineDocuments') }}</div>
    <template v-if="hasOfflineFiles">
      <documents-offline-item
        v-for="file in offlineFiles"
        :key="file.id"
        :file="file"
        class="mb-4 me-4" />
    </template>
  </v-card>
</template>
<script>
export default {
  data: () => ({
    offlineFiles: [],
  }),
  computed: {
    hasOfflineFiles() {
      return !!this.offlineFiles?.length;
    },
  },
  created() {
    this.init();
  },
  methods: {
    async init() {
      this.offlineFiles = await this.$documentOfflineService.getFiles();
    },
  },
};
</script>