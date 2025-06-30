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
  <tr>
    <td
      :class="cellClass"
      v-bind="noDates && accessBadge && !offlineAccessTime && {
        colspan: 2,
      }">
      <v-card
        class="d-flex align-center overflow-hidden"
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
    </td>
    <td v-if="!noDates" :class="cellClass">
      <v-tooltip bottom>
        <template #activator="{on, attrs}">
          <div
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
    <td v-if="!noDates" :class="cellClass">
      <v-tooltip bottom>
        <template #activator="{on, attrs}">
          <div
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
      v-if="accessBadge && offlineAccessTime"
      :class="cellClass"
      width="24">
      <v-tooltip bottom>
        <template #activator="{on, attrs}">
          <v-btn
            v-bind="attrs"
            v-on="on"
            :aria-label="$t('OfflineApp.pwa.documents.accessedWhileOffline')"
            small
            icon>
            <v-avatar
              class="error-color-background"
              size="12" />
          </v-btn>
        </template>
        <span>{{ $t('OfflineApp.pwa.documents.accessedWhileOffline') }}</span>
      </v-tooltip>
    </td>
    <td width="75px" :class="cellClass">
      <div class="d-flex justify-center align-center">
        <v-tooltip bottom>
          <template #activator="{on, attrs}">
            <v-btn
              v-bind="attrs"
              v-on="on"
              :aria-label="canPreview ? $t('OfflineApp.pwa.preview') : $t('OfflineApp.pwa.download')"
              icon
              @click="openFile">
              <v-icon>{{ canPreview ? 'fa-eye' : 'fa-download' }}</v-icon>
            </v-btn>
          </template>
          <span>{{ canPreview ? $t('OfflineApp.pwa.preview') : $t('OfflineApp.pwa.download') }}</span>
        </v-tooltip>
      </div>
    </td>
  </tr>
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    },
    cellClass: {
      type: String,
      default: null,
    },
    noDates: {
      type: Boolean,
      default: false,
    },
    accessBadge: {
      type: Boolean,
      default: false,
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
  },
};
</script>