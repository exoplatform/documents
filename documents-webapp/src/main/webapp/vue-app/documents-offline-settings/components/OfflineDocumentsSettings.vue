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
  <v-list-item dense class="mt-3">
    <v-list-item-content>
      <v-list-item-title class="text-wrap">
        {{ $t('UserSettings.pwa.documentsOffline.title') }}
      </v-list-item-title>
      <v-list-item-subtitle class="text-wrap">
        {{ $t('UserSettings.pwa.documentsOffline.description') }}
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action v-if="initialized" class="mt-0 mb-auto">
      <template v-if="installed">
        <v-card
          v-if="offlinePermission === 'granted'"
          class="border-color py-2 px-3"
          disabled
          flat>
          <v-icon class="success--text me-2" size="18">fa-check</v-icon>
          {{ $t('UserSettings.pwa.documentsOffline.granted') }}
        </v-card>
        <v-card
          v-else-if="offlinePermission === 'denied'"
          class="border-color py-2 px-3"
          disabled
          flat>
          <v-icon class="error--text me-2" size="18">fa-times</v-icon>
          {{ $t('UserSettings.pwa.documentsOffline.denied') }}
        </v-card>
        <v-btn
          v-else
          :aria-label="$t('UserSettings.pwa.documentsOffline.configure')"
          :loading="offlineLoading"
          class="btn"
          text
          @click="openDrawer">
          {{ $t('UserSettings.pwa.documentsOffline.configure') }}
        </v-btn>
      </template>
      <v-tooltip
        v-else
        bottom>
        <template #activator="{on, attrs}">
          <div
            v-on="on"
            v-bind="attrs">
            <v-btn
              :aria-label="$t('UserSettings.pwa.documentsOffline.configure')"
              disabled
              class="btn">
              {{ $t('UserSettings.pwa.documentsOffline.configure') }}
            </v-btn>
          </div>
        </template>
        <span v-if="!pwaSupported">
          {{ $t('UserSettings.pwa.browserNotSupported') }}
        </span>
        <span v-else-if="!pwaEnabled">
          {{ $t('UserSettings.pwa.pwaNotEnabled') }}
        </span>
        <span v-else>
          {{ $t('UserSettings.pwa.documentsOffline.pwaNotInstalled') }}
        </span>
      </v-tooltip>
    </v-list-item-action>
    <documents-offline-settings-drawer ref="drawer" />
  </v-list-item>
</template>
<script>
export default {
  props: {
    isMobile: {
      type: Boolean,
      default: false,
    },
    installed: {
      type: Boolean,
      default: false,
    },
    pwaEnabled: {
      type: Boolean,
      default: false,
    },
    pwaSupported: {
      type: Boolean,
      default: false,
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    offlinePermission: null,
    initialized: false,
    offlineLoading: true,
  }),
  created() {
    this.init();
  },
  methods: {
    async init() {
      this.offlineLoading = true;
      try {
        const hasDirectory = await this.$documentOfflineService.isDirectoryHandleExists();
        if (hasDirectory) {
          return 'granted';
        } else {
          return null;
        }
      } catch (e) {
        console.error(e);
      } finally {
        this.offlineLoading = false;
        this.initialized = true;
      }
    },
    openDrawer() {
      this.$refs?.drawer?.open?.();
    },
  },
};
</script>
