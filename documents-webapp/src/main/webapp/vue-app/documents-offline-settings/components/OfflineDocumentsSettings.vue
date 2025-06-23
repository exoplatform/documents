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
    <v-list-item-action class="mt-0 mb-auto">
      <v-switch
        v-if="installed"
        v-model="enabled"
        :loading="loading"
        class="py-2 px-3"
        @click="toogle" />
      <v-tooltip
        v-else
        bottom>
        <template #activator="{on, attrs}">
          <div
            v-on="on"
            v-bind="attrs">
            <v-switch
              :aria-label="$t('UserSettings.pwa.documentsOffline.configure')"
              disabled />
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
  },
  data: () => ({
    enabled: false,
    loading: true,
  }),
  created() {
    this.init();
  },
  methods: {
    async init() {
      this.loading = true;
      try {
        this.enabled = await this.$documentOfflineService.isDatabaseExists();
      } catch (e) {
        console.error(e);
      } finally {
        this.loading = false;
      }
    },
    async toogle() {
      this.loading = true;
      try {
        await this.$nextTick();
        if (this.enabled) {
          await this.$documentOfflineService.createDatabase();
          await this.$documentOfflineService.downloadFavorites();
        } else {
          await this.$documentOfflineService.deleteDatabase();
        }
      } catch (e) {
        console.error(e);
      } finally {
        this.loading = false;
      }
    },
    openDrawer() {
      this.$refs?.drawer?.open?.();
    },
  },
};
</script>
