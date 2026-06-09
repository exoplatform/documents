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
  <exo-drawer
    ref="drawer"
    v-model="drawer"
    id="documentsWebdavDrawer"
    :loading="loading"
    :right="!$vuetify.rtl">
    <template #title>
      {{ $t('UserSettings.documents.webdav.mapDrives.title') }}
    </template>
    <template v-if="drawer" #content>
      <div class="d-flex flex-column justify-center align-center pa-5">
        <v-icon color="secondary" size="48">fa-network-wired</v-icon>
        <v-card
          class="text-wrap mt-4"
          min-width="300"
          width="80%"
          flat>
          {{ $t('UserSettings.documents.webdav.mapDrivesDescription') }}
        </v-card>
        <div class="d-flex flex-column">
          <v-btn
            class="mt-4"
            color="primary"
            elevation="0"
            @click="mapDrives">
            {{ $t('UserSettings.documents.webdav.mapDrives') }}
          </v-btn>
          <v-btn
            v-if="hasApiKey"
            class="btn mt-4"
            elevation="0"
            @click="regenerateAccess">
            {{ $t('UserSettings.documents.webdav.regenerateAccess') }}
          </v-btn>
        </div>
      </div>
      <documents-webdav-confirm-access-drawer
        ref="confirmAccessDrawer"
        @validated="hasApiKey = true" />
      <documents-webdav-map-drives-drawer
        ref="mapDrivesDrawer" />
      <documents-webdav-regenerate-access-drawer
        ref="regenerateAccessDrawer" />
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    drawer: false,
    hasApiKey: false,
  }),
  async created() {
    this.hasApiKey = await this.$apiKeyService.hasPassword();
  },
  methods: {
    open() {
      this.$refs.drawer.open();
    },
    mapDrives() {
      console.warn('this.hasApiKey', this.hasApiKey);
      if (this.hasApiKey) {
        this.$refs.mapDrivesDrawer.open();
      } else {
        this.$refs.confirmAccessDrawer.open(this.$refs.mapDrivesDrawer, false);
      }
    },
    regenerateAccess() {
      this.$refs.confirmAccessDrawer.open(this.$refs.regenerateAccessDrawer, true);
    },
  },
};
</script>