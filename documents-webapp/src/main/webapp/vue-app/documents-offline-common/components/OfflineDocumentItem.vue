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
  <v-hover v-model="hover">
    <tr>
      <td class="no-border">
        <div class="d-flex align-center overflow-hidden">
          <v-card
            class="d-flex align-center overflow-hidden no-border-radius"
            color="transparent"
            flat
            @click="openFile">
            <v-icon
              :color="fileIconColor"
              size="24">
              {{ fileIcon }}
            </v-icon>
            <div
              :title="file.name"
              class="d-flex overflow-hidden ps-5">
              <span class="text-truncate">{{ file.name }}</span>
            </div>
          </v-card>
          <v-spacer />
          <v-tooltip v-if="reverseInfoPosition && accessBadge && offlineAccessTime" bottom>
            <template #activator="{on, attrs}">
              <v-btn
                v-bind="attrs"
                v-on="on"
                :aria-label="$t('OfflineApp.pwa.documents.accessedWhileOffline')"
                small
                icon
                @click="markAsUpdated">
                <v-avatar
                  class="error-color-background"
                  size="12" />
              </v-btn>
            </template>
            <span>{{ $t('OfflineApp.pwa.documents.accessedWhileOffline') }}</span>
          </v-tooltip>
          <documents-offline-info-button
            v-else-if="!reverseInfoPosition && infoIcon && hover"
            :file-id="file.id" />
        </div>
      </td>
      <td
        v-if="!noDates"
        class="no-border"
        width="175px">
        <v-tooltip bottom>
          <template #activator="{on, attrs}">
            <div
              class="text-center"
              v-bind="attrs"
              v-on="on">
              <date-format :value="file.modifiedDate" />
            </div>
          </template>
          <date-format
            :value="file.modifiedDate"
            :format="fullDateFormat" />
        </v-tooltip>
      </td>
      <td
        v-if="!noDates"
        class="no-border"
        width="175px">
        <v-tooltip bottom>
          <template #activator="{on, attrs}">
            <div
              class="text-center"
              v-bind="attrs"
              v-on="on">
              <date-format :value="file.downloadTime" />
            </div>
          </template>
          <date-format
            :value="file.downloadTime"
            :format="fullDateFormat" />
        </v-tooltip>
      </td>
      <td
        :width="`${actionsCellWidth}px`"
        :class="actionsCellClass"
        class="no-border">
        <div :class="accessBadge ? 'justify-end' : 'justify-center'" class="d-flex align-center">
          <div v-if="infoIcon && reverseInfoPosition" class="me-2">
            <documents-offline-info-button
              :file-id="file.id" />
          </div>
          <div v-else-if="!reverseInfoPosition && accessBadge && offlineAccessTime" class="me-2">
            <v-tooltip bottom>
              <template #activator="{on, attrs}">
                <v-btn
                  v-bind="attrs"
                  v-on="on"
                  :aria-label="$t('OfflineApp.pwa.documents.accessedWhileOffline')"
                  icon
                  @click="markAsUpdated">
                  <v-avatar
                    class="error-color-background"
                    size="12" />
                </v-btn>
              </template>
              <span>{{ $t('OfflineApp.pwa.documents.accessedWhileOffline') }}</span>
            </v-tooltip>
          </div>
          <div v-if="allowUpload" class="me-2">
            <v-tooltip bottom>
              <template #activator="{on, attrs}">
                <v-btn
                  v-bind="attrs"
                  v-on="on"
                  :aria-label="$t('OfflineApp.pwa.uploadVersion')"
                  :loading="uploading"
                  icon
                  @click="uploadVersion">
                  <v-icon size="16">fa-upload</v-icon>
                </v-btn>
              </template>
              <span>{{ $t('OfflineApp.pwa.uploadVersion') }}</span>
            </v-tooltip>
          </div>
          <div v-if="!hideDownload" class="me-2">
            <v-tooltip bottom>
              <template #activator="{on, attrs}">
                <v-btn
                  v-bind="attrs"
                  v-on="on"
                  :aria-label="canPreview ? $t('OfflineApp.pwa.preview') : $t('OfflineApp.pwa.download')"
                  icon
                  @click="openFile">
                  <v-icon size="16">{{ canPreview ? 'fa-eye' : 'fa-download' }}</v-icon>
                </v-btn>
              </template>
              <span>{{ canPreview ? $t('OfflineApp.pwa.preview') : $t('OfflineApp.pwa.download') }}</span>
            </v-tooltip>
          </div>
        </div>
      </td>
    </tr>
  </v-hover>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    noDates: {
      type: Boolean,
      default: false,
    },
    allowUpload: {
      type: Boolean,
      default: false,
    },
    hideDownload: {
      type: Boolean,
      default: false,
    },
    accessBadge: {
      type: Boolean,
      default: false,
    },
    infoIcon: {
      type: Boolean,
      default: false,
    },
    reverseInfoPosition: {
      type: Boolean,
      default: false,
    },
    actionsCellClass: {
      type: String,
      default: null,
    },
    actionsCellWidth: {
      type: Number,
      default: () => 75,
    },
  },
  data: () => ({
    fullDateFormat: {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
    },
    hover: false,
    uploading: false,
  }),
  computed: {
    extension() {
      return this.$documentsIconsExtension?.[0]?.get?.(this.file?.mimeType);
    },
    canPreview() {
      return this.extension?.canPreview;
    },
    fileIcon() {
      return this.extension?.class || 'fas fa-file';
    },
    fileIconColor() {
      return this.extension?.color || 'secondary';
    },
    offlineAccessTime() {
      return this.file?.offlineAccessTime;
    },
  },
  methods: {
    openFile() {
      if (this.canPreview) {
        this.$emit('preview', this.file, this.extension);
      } else {
        this.$emit('download', this.file, this.extension);
      }
    },
    async markAsUpdated() {
      await this.$documentOfflineService.markFileAsUpdated(this.file.id);
      this.$emit('updated');
      this.$root.$emit('documents-offline-updated', this.file);
    },
    async uploadVersion() {
      const filePickerOptions = {
        types: [{}],
        multiple: false,
      };
      const fileExtension = this.file.name?.split?.('.')?.pop?.();
      if (fileExtension) {
        filePickerOptions.excludeAcceptAllOption = true;
        filePickerOptions['types'][0]['accept'] = {};
        filePickerOptions['types'][0]['accept'][this.file.mimeType] = [`.${fileExtension}`];
      }
      const [fileHandle] = await window.showOpenFilePicker(filePickerOptions);
      if (fileHandle) {
        this.uploading = true;
        try {
          const blob = await fileHandle.getFile();
          await this.$documentFileService.uploadNewFileVersion(this.file.id, blob);
          await this.$documentOfflineService.saveFile(this.file.id);
          await this.$documentOfflineService.markFileAsUpdated(this.file.id);
          this.$root.$emit('alert-message', this.$t('OfflineApp.pwa.documents.newVersion.success.message'), 'success');
          this.$emit('updated');
        } catch (e) {
          // eslint-disable-next-line no-console
          console.error(e);
          this.$root.$emit('alert-message', this.$t('OfflineApp.pwa.documents.newVersion.error.message'), 'error');
        } finally {
          this.uploading = false;
        }
      }
    },
  },
};
</script>