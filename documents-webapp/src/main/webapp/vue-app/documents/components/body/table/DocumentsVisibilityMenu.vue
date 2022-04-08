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
    <v-menu offset-y>
      <template v-slot:activator="{ on, attrs }">
        <v-btn
          icon
          small
          v-bind="attrs"
          v-on="on">
          <v-icon
            class="pb-2"
            :size="16">
            mdi-chevron-down
          </v-icon>
        </v-btn>
      </template>
      <v-list>
        <v-list-item
          v-for="(item, index) in accessibilityLabel"
          :key="index">
          <v-list-item-title @click="$emit('visibility-user', item.value)" class="clickable">{{ item.title }}</v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
  </div>
</template>
<script>
export default {
  data: () => ({
    items: [
      { title: 'edit' },
      { title: 'read' },
    ],
  }),
  computed: {
    accessibilityLabel() {
      return [
        {
          title: this.$t('documents.label.accessibility.view'),
          value: 'read',
        },
        {
          title: this.$t('documents.label.accessibility.edit'),
          value: 'edit',
        },
      ];
    },
  }
};
</script>