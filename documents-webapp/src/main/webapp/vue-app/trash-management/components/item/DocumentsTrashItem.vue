<!--
 * Copyright (C) 2024 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
-->

<template>
  <tr :key="item.id">
    <td v-if="!isMobile">
      <v-checkbox
        :value="selected"
        on-icon="fas fa-check-square fa-lg primary--text"
        off-icon="far fa-square fa-lg"
        class="my-auto pt-2"
        @change="changeCheckboxStatus" />
    </td>
    <td
      align="left"
      :width="headers[0].width"
      class="text-truncate">
      <v-icon
        class="mt-n1 me-1"
        :color="icon.color"
        size="20">
        {{ icon.class }}
      </v-icon>
      <v-tooltip bottom>
        <template #activator="{ on, attrs }">
          <span
            v-bind="attrs"
            v-on="on"
            :style="menuItemStyle">{{ item.name }}</span>
        </template>
        {{ item.name }}
      </v-tooltip>
    </td>
    <td align="center" :width="headers[1].width">
      <v-tooltip v-if="item.lastModificationDate" bottom>
        <template #activator="{on, attrs}">
          <div
            v-on="on"
            v-bind="attrs"
            :style="menuItemStyle">
            <date-format
              class="pe-4"
              :value="item.lastModificationDate"
              :format="dateFormat" />
          </div>
        </template>
        <date-format :value="item.lastModificationDate" :format="fullDateFormat" />
      </v-tooltip>
    </td>
    <td align="center" :width="headers[2].width">
      <div class="d-flex justify-center align-center">
        <v-tooltip bottom>
          <template #activator="{ on, attrs }">
            <span
              v-bind="attrs"
              v-on="on"
              :style="menuItemStyle">{{ item.origin }}</span>
          </template>
          {{ item.restorePath }}
        </v-tooltip>
      </div>
    </td>
    <td align="center" :width="headers[3].width">
      <document-trash-item-menu
        :trash-element-item="item"
        :bulk-action-progress="bulkActionProgress" />
    </td>
  </tr>
</template>

<script>
export default {
  props: {
    headers: {
      type: Array,
      default: () => [],
    },
    item: {
      type: Object,
      required: true,
    },
    selected: {
      type: Boolean,
      default: false,
    },
    select: {
      type: Object,
      default: null,
    },
    bulkActionProgress: {
      type: Boolean,
      default: false
    }
  },
  data: () => ({
    icon: null,
    dateFormat: {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    },
    fullDateFormat: {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    },
    menuItemStyle: {
      cursor: 'default',
    },
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  created() {
    this.getFileIcon();
  },
  methods: {
    getFileIcon() {
      const extensions = Vue.prototype.$documentsIconsExtension;
      if (this.item?.folder) {
        this.icon = extensions[0].get('folder');
      } else {
        let extension = extensions[0].get(this.item?.mimeType);
        if (!extension) {
          extension = extensions[0].get('file');
        }
        this.icon = extension;
      }
    },
    changeCheckboxStatus(status) {
      this.select(status);
    }
  }
};
</script>