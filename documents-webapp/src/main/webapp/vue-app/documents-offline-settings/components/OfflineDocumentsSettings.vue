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
    </v-list-item-content>
    <v-list-item-action class="mt-0 mb-auto">
      <v-switch
        v-if="installed"
        v-model="enabled"
        :loading="loading"
        :disabled="loading || (suspended && !enabled)"
        :aria-label="enabled ? $t('UserSettings.pwa.documentsOffline.disable') : $t('UserSettings.pwa.documentsOffline.enable')"
        class="py-2 px-3"
        @click.stop.prevent="toogle" />
      <v-tooltip
        v-else
        bottom>
        <template #activator="{on, attrs}">
          <div
            v-on="on"
            v-bind="attrs">
            <v-switch disabled />
          </div>
        </template>
        <span v-if="suspended">
          {{ $t('UserSettings.pwa.documentsOffline.synchronizationSuspended') }}
        </span>
        <span v-else-if="!pwaSupported">
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
    suspended: false,
    loading: true,
  }),
  created() {
    this.init();
  },
  methods: {
    async init() {
      this.loading = true;
      try {
        this.enabled = await this.$documentOfflineService.isOfflineDocumentsEnabled();
        this.suspended = !(await this.$documentOfflineService.isDownloadDocumentsEnabled());
        if (this.enabled && !this.installed) {
          await this.$documentOfflineService.deleteDatabase();
          this.enabled = false;
        }
      } catch (e) {
        // eslint-disable-next-line no-console
        console.error(e);
      } finally {
        this.loading = false;
      }
    },
    async toogle() {
      this.loading = true;
      this.$root.$emit('close-alert-message');
      try {
        await this.$nextTick();
        if (this.enabled) {
          await this.$documentOfflineService.createDatabase();
          await this.$documentOfflineService.downloadFavorites();
          this.$root.$emit('alert-message', this.$t('UserSettings.pwa.documentsOffline.synchronizationEnabled'), 'success');
        } else {
          await this.$documentOfflineService.deleteDatabase();
          this.$root.$emit('alert-message', this.$t('UserSettings.pwa.documentsOffline.synchronizationDeleted'), 'success');
        }
      } catch (e) {
        // eslint-disable-next-line no-console
        console.error(e);
        if (this.enabled) {
          await this.$documentOfflineService.deleteDatabase();
        }
        this.enabled = !this.enabled;
        this.$root.$emit('alert-message', this.$t('UserSettings.pwa.documentsOffline.synchronizationSettingsUpdateError'), 'error');
      } finally {
        this.enabled = await this.$documentOfflineService.isOfflineDocumentsEnabled();
        this.loading = false;
      }
    },
  },
};
</script>
