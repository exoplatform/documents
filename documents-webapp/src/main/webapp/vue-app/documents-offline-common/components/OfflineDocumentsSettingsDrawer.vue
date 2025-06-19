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
    id="offlineDocumentSettingsDrawer"
    ref="drawer"
    v-model="drawer"
    :right="!$vuetify.rtl">
    <template #title>
      {{ $t('UserSettings.pwa.documentsOffline.settingsDrawer.title') }}
    </template>
    <template v-if="drawer" #content>
      <div class="d-flex flex-column full-width text-truncate pa-5">
        <div>
          {{ $t('UserSettings.pwa.documentsOffline.settingsDrawer.linkChoice') }}
        </div>
        <v-radio-group
          v-model="linkType"
          mandatory>
          <v-radio
            value="DOWNLOAD"
            class="mx-0 mt-0 mb-1">
            <template #label>
              <v-list-item class="px-0" dense>
                <v-list-item-content>
                  <v-list-item-title class="text-wrap">
                    {{ $t('UserSettings.pwa.documentsOffline.settingsDrawer.downloadFile') }}
                  </v-list-item-title>
                  <v-list-item-subtitle class="text-wrap">
                    {{ $t('UserSettings.pwa.documentsOffline.settingsDrawer.downloadFile.subtitle') }}
                  </v-list-item-subtitle>
                </v-list-item-content>
              </v-list-item>
            </template>
          </v-radio>
          <v-radio
            :disabled="isMobile"
            value="LINK"
            class="mx-0 mt-0 mb-1">
            <template #label>
              <v-list-item class="px-0" dense>
                <v-list-item-content>
                  <v-list-item-title class="text-wrap">
                    {{ $t('UserSettings.pwa.documentsOffline.settingsDrawer.openFile') }}
                  </v-list-item-title>
                  <v-list-item-subtitle class="text-wrap">
                    {{ $t('UserSettings.pwa.documentsOffline.settingsDrawer.openFile.subtitle') }}
                  </v-list-item-subtitle>
                </v-list-item-content>
              </v-list-item>
            </template>
          </v-radio>
        </v-radio-group>
        <div v-if="isLink" class="ms-8">
          <v-list-item class="px-0" dense>
            <v-list-item-content>
              <v-list-item-title class="text-wrap">
                {{ $t('UserSettings.pwa.documentsOffline.settingsDrawer.localFilesPath') }}
              </v-list-item-title>
              <v-list-item-subtitle class="text-wrap">
                {{ $t('UserSettings.pwa.documentsOffline.settingsDrawer.localFilesPath.subtitle') }}
              </v-list-item-subtitle>
            </v-list-item-content>
          </v-list-item>
          <v-text-field
            id="localFolderPath"
            v-model="localFolderPath"
            :placeholder="$t('UserSettings.pwa.documentsOffline.settingsDrawer.localFilesPath.placeholder')"
            class="border-box-sizing pt-0 full-width"
            name="localFolderPath"
            type="text"
            autofocus
            aria-required="true"
            required
            outlined
            dense />
        </div>
      </div>
    </template>
    <template v-if="drawer" #footer>
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn me-2"
          @click="close">
          {{ $t('UserSettings.pwa.documentsOffline.settingsDrawer.cancel') }}
        </v-btn>
        <v-btn
          :disabled="disabled"
          class="btn btn-primary"
          @click="applySettings">
          {{ $t('UserSettings.pwa.documentsOffline.settingsDrawer.apply') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data: () => ({
    drawer: false,
    linkType: null,
    localFolderPath: null,
    originalLinkType: null,
    originalLocalFolderPath: null,
  }),
  computed: {
    isMobile() {
      return this.$vuetify?.breakpoint?.mdAndDown;
    },
    isLink() {
      return this.linkType === 'LINK';
    },
    disabled() {
      return (this.isLink && !this.localFolderPath)
        || (this.linkType === this.originalLinkType
            && this.localFolderPath?.replaceAll?.('\\', '/')?.replaceAll?.(/\/$/g, '') === this.originalLocalFolderPath);
    },
  },
  created() {
    this.$root.$on('documents-offline-settings-open', this.open);
  },
  beforeDestroy() {
    this.$root.$off('documents-offline-settings-open', this.open);
  },
  methods: {
    async open() {
      if (await this.$documentOfflineService.isDirectoryHandleExists()) {
        this.linkType = await this.$documentOfflineService.getLinkType();
        this.originalLinkType = this.linkType;
        if (this.isLink) {
          this.localFolderPath = await this.$documentOfflineService.getLocalFolderPath();
          this.originalLocalFolderPath = this.localFolderPath;
        } else {
          this.localFolderPath = null;
          this.originalLocalFolderPath = null;
        }
      } else {
        this.linkType = null;
        this.originalLinkType = null;
        this.localFolderPath = null;
        this.originalLocalFolderPath = null;
      }
      this.$refs?.drawer?.open?.();
    },
    async applySettings() {
      try {
        if (this.isLink && this.localFolderPath) {
          this.localFolderPath = this.localFolderPath.replaceAll('\\', '/').replaceAll(/\/$/g, '');
          if (this.localFolderPath !== this.originalLocalFolderPath) {
            await this.$documentOfflineService.removeDirectoryHandle();
          }
        }
        await this.$documentOfflineService.openDirectoryHandle();
        await this.$documentOfflineService.setLinkType(this.linkType);
        if (this.isLink) {
          await this.$documentOfflineService.setLocalFolderPath(this.localFolderPath.replaceAll('\\', '/').replaceAll(/\/$/g, ''));
        }
        this.close();
        this.$root.$emit('alert-message', this.$t('UserSettings.pwa.documentsOffline.settingsDrawer.synchronizationSettingsUpdated'), 'success');
        this.$root.$emit('documents-offline-settings-updated');
      } catch (e) {
        console.error(e);
        this.$root.$emit('alert-message', this.$t('UserSettings.pwa.documentsOffline.settingsDrawer.synchronizationSettingsUpdateError'), 'error');
      }
    },
    close() {
      this.$refs.drawer.close();
    },
  },
};
</script>