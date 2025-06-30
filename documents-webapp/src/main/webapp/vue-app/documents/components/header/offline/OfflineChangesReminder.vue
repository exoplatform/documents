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
  <changes-reminder
    ref="changesReminder"
    :reminder="reminder">
    <div>{{ $t('OfflineApp.pwa.documents.changesReminder.part1') }}</div>
    <div>{{ $t('OfflineApp.pwa.documents.changesReminder.part2') }}</div>
    <div>{{ $t('OfflineApp.pwa.documents.changesReminder.part3') }}</div>
    <div class="d-flex align-end justify-end mt-4">
      <v-btn
        :loading="loading"
        color="primary"
        class="mb-n5"
        elevation="0"
        @click="enableOfflineDocuments">
        {{ $t('OfflineApp.pwa.documents.changesReminder.button') }}
      </v-btn>
    </div>
  </changes-reminder>
</template>
<script>
export default {
  props: {
    page: {
      type: Object,
      default: null,
    },
    node: {
      type: Object,
      default: null,
    },
    layout: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    loading: false,
  }),
  computed: {
    reminder() {
      return {
        name: 'DocumentsFavoriteOfflineChanges' ,
        title: this.$t('OfflineApp.pwa.documents.changesReminder.title'),
        img: '/documents-portlet/images/OfflineDocuments.png',
      };
    },
  },
  mounted() {
    this.init();
  },
  methods: {
    async init() {
      const isAnnounced = localStorage.getItem('documents-favorite-offline');
      if (isAnnounced) {
        return;
      }
      const isOfflineModeEnabled = await this.$documentOfflineService.isDatabaseExists();
      if (isOfflineModeEnabled) {
        return;
      }
      const pwaSupported = 'onbeforeinstallprompt' in window;
      if (!pwaSupported) {
        return;
      }
      const pwaInstalled = !!(await navigator?.serviceWorker?.getRegistration?.());
      if (!pwaInstalled) {
        return;
      }
      this.$refs.changesReminder.open();
    },
    async enableOfflineDocuments() {
      this.loading = true;
      this.$root.$emit('close-alert-message');
      try {
        await this.$documentOfflineService.createDatabase();
        await this.$documentOfflineService.downloadFavorites();
        await this.$refs.changesReminder.doNotRemindMe();
        this.$root.$emit('alert-message', this.$t('OfflineApp.pwa.documents.synchronizationEnabled'), 'success');
        this.$root.$emit('documents-offline-updated');
      } catch (e) {
        await this.$documentOfflineService.deleteDatabase();
        // eslint-disable-next-line no-console
        console.error(e);
        this.$root.$emit('alert-message', this.$t('OfflineApp.pwa.documents.synchronizationSettingsUpdateError'), 'error');
      } finally {
        this.enabled = await this.$documentOfflineService.isOfflineDocumentsEnabled();
        this.loading = false;
      }
    },
  },
};
</script>
