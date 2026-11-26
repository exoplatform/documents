<!--

 Copyright (C) 2026 eXo Platform SAS.

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
  <tr :class="selected && 'v-data-table__selected'" class="recycle-bin-item">
    <td class="px-2" style="border-bottom: none">
      <v-checkbox
        ref="select"
        :input-value="selected"
        :aria-label="$t('recycleBin.item.select', {0: item.name})"
        :disabled="busy"
        class="recycle-bin-item-select ma-0 pa-0"
        hide-details
        dense
        @change="$emit('select', !!$event)" />
    </td>
    <td class="recycle-bin-item-name text-start" style="max-width: 0; border-bottom: none">
      <div class="d-flex align-center">
        <div class="d-flex flex-shrink-0 justify-center" style="width: 24px">
          <v-icon :color="icon.color" size="22">{{ icon.class }}</v-icon>
        </div>
        <span :title="item.name" class="text-truncate ms-3">{{ item.name }}</span>
        <template v-if="item.shortcut">
          <v-icon
            :title="$t('recycleBin.item.shortcut')"
            class="recycle-bin-item-shortcut flex-shrink-0 icon-default-color ms-1"
            size="13">
            mdi-link-variant
          </v-icon>
          <span class="d-sr-only">{{ $t('recycleBin.item.shortcut') }}</span>
        </template>
      </div>
    </td>
    <td
      v-if="!mobile"
      class="recycle-bin-item-location text-start"
      style="max-width: 0; border-bottom: none">
      <div
        v-if="location"
        :title="location.path"
        class="d-flex align-center">
        <span class="d-sr-only">{{ location.path }}</span>
        <v-avatar
          v-if="drive && drive.avatarUrl"
          :tile="drive.space"
          class="flex-shrink-0"
          size="24">
          <img
            :src="drive.avatarUrl"
            :class="drive.space && 'rounded'"
            class="object-fit-cover"
            alt="">
        </v-avatar>
        <v-icon
          class="flex-shrink-0 icon-default-color mx-2"
          size="10"
          aria-hidden="true">
          {{ chevron }}
        </v-icon>
        <template v-if="location.nested">
          <span class="flex-shrink-0" aria-hidden="true">...</span>
          <v-icon
            class="flex-shrink-0 icon-default-color mx-2"
            size="10"
            aria-hidden="true">
            {{ chevron }}
          </v-icon>
        </template>
        <span class="primary--text font-weight-bold text-truncate" aria-hidden="true">{{ location.folder }}</span>
      </div>
    </td>
    <td class="recycle-bin-item-date text-center text-no-wrap" style="border-bottom: none">
      <date-format
        v-if="item.deletedDate"
        :value="item.deletedDate"
        :format="dateFormat" />
    </td>
    <td
      v-if="!mobile"
      class="recycle-bin-item-deletion-author"
      style="border-bottom: none">
      <div
        v-if="deleter"
        :title="deleterName"
        class="d-flex justify-center">
        <exo-user-avatar
          :profile-id="deleter"
          :name="deleterName"
          :size="24"
          show-disabled-user
          popover
          avatar />
      </div>
    </td>
    <td
      v-if="!mobile"
      class="recycle-bin-item-size text-center text-no-wrap"
      style="border-bottom: none">
      {{ size.value }} {{ $t(`recycleBin.size.unit.${size.unit}`) }}
    </td>
    <td class="recycle-bin-item-actions text-center text-no-wrap" style="border-bottom: none">
      <v-btn
        v-if="item.canView"
        :title="$t('recycleBin.action.view')"
        :aria-label="$t('recycleBin.action.view.item', {0: item.name})"
        class="recycle-bin-item-view"
        icon
        small
        @click="$emit('view', item)">
        <v-icon size="18" class="icon-default-color">fa-eye</v-icon>
      </v-btn>
      <v-btn
        :title="$t('recycleBin.action.restore')"
        :aria-label="$t('recycleBin.action.restore.item', {0: item.name})"
        :disabled="busy"
        class="recycle-bin-item-restore"
        icon
        small
        @click="$emit('restore', item)">
        <v-icon size="16" class="icon-default-color">fa-redo</v-icon>
      </v-btn>
      <v-btn
        :title="$t('recycleBin.action.delete')"
        :aria-label="$t('recycleBin.action.delete.item', {0: item.name})"
        :disabled="busy"
        class="recycle-bin-item-delete"
        icon
        small
        @click="$emit('delete', item)">
        <v-icon size="16" class="error--text">fa-trash</v-icon>
      </v-btn>
    </td>
  </tr>
</template>
<script>
export default {
  props: {
    item: {
      type: Object,
      required: true,
    },
    // The drive of the bin: {avatarUrl, space, rootLabel}
    drive: {
      type: Object,
      default: null,
    },
    selected: {
      type: Boolean,
      default: false,
    },
    mobile: {
      type: Boolean,
      default: false,
    },
    busy: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    dateFormat: {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    },
  }),
  watch: {
    // The browser restores the native state of the box after a click on it:
    // it is set again once the click is over, for a screen reader to tell it.
    selected() {
      window.setTimeout(() => {
        const input = this.$refs.select?.$el?.querySelector?.('input');
        if (input) {
          input.checked = this.selected;
        }
      });
    },
  },
  computed: {
    icon() {
      const icons = this.$documentsIconsExtension?.[0];
      // A shortcut shows the icon of what it points to, as in the Documents app.
      return icons?.get?.((this.item.folder || this.item.targetFolder) && 'folder' || this.item.mimeType)
          || icons?.get?.('file')
          || {class: 'fas fa-file', color: null};
    },
    chevron() {
      return this.$vuetify.rtl && 'fa-chevron-left' || 'fa-chevron-right';
    },
    // The folder the item was deleted from: its name, whether folders stand
    // between the root of the drive and it, and its whole path from that root.
    location() {
      const path = this.item.originalLocation?.path;
      if (!path) {
        return null;
      }
      const root = this.drive?.rootLabel || '';
      const folders = path.split('/').filter(folder => folder);
      return {
        folder: folders.length && folders[folders.length - 1] || root,
        nested: folders.length > 1,
        path: [root, ...folders].filter(folder => folder).join(' > '),
      };
    },
    deleter() {
      return this.item.deletedBy?.username || null;
    },
    deleterName() {
      return this.item.deletedBy?.fullName || this.deleter || '';
    },
    size() {
      return this.$recycleBinService.formatSize(this.item.size);
    },
  },
};
</script>
