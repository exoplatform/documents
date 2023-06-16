<!--
* Copyright (C) 2022 eXo Platform SAS
*
*  This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program.  If not, see <gnu.org/licenses>.
-->
<template>
  <div class="text-center pe-1">
    <v-menu
      v-model="showMenu"
      transition="slide-x-reverse-transition"
      bottom
      offset-y
      left
      :close-on-content-click="false">
      <template #activator="{ on, attrs }">
        <v-btn
          icon
          small
          v-bind="attrs"
          v-on="on">
          <v-icon
            class="mt-3 pb-2"
            :size="16">
            mdi-chevron-down
          </v-icon>
        </v-btn>
      </template>
      <v-list
        class="pa-0">
        <v-list-item
          class="custom-list-item-min-height"
          v-for="(item, index) in accessibilityLabel"
          :key="index">
          <v-list-item-title @click="$emit('visibility-user', item.value)" class="clickable">
            <v-icon
              class="pb-1"
              :size="13">
              {{ item.icon }}
            </v-icon>
            <span class="text-font-size">
              {{ item.title }}
            </span>
          </v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
  </div>
</template>
<script>
export default {
  data: () => ({
    showMenu: false,
    items: [
      { title: 'edit' },
      { title: 'read' },
    ],
  }),
  created() {
    $(document).on('mousedown', () => {
      if (this.showMenu) {
        window.setTimeout(() => {
          this.showMenu = false;
        }, 200);
      }
    });
  },
  computed: {
    accessibilityLabel() {
      return [
        {
          icon: 'fas fa-edit',
          title: this.$t('documents.label.accessibility.edit'),
          value: 'edit',
        },
        {
          icon: 'fas fa-eye',
          title: this.$t('documents.label.accessibility.view'),
          value: 'read',
        }
      ];
    },
  }
};
</script>