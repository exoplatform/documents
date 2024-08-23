<!--

 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2023 Meeds Association contact@meeds.io

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 3 of the License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

-->
<template>
  <v-list-item
    :title="description || name"
    :href="url"
    :target="target"
    :dense="!largeIcon"
    class="px-0"
    flat>
    <documents-widget-icon
      :icon-size="iconSize"
      :icon-url="iconUrl"
      list />
    <v-list-item-content v-if="showName || showDescription">
      <v-list-item-title
        v-if="showName && name"
        class="text-color text-start text-wrap"
        :class="showDescription && description && 'text-truncate' || 'text-truncate-2'">
        {{ name }}
      </v-list-item-title>
      <v-list-item-subtitle
        v-if="showDescription && description"
        :class="largeIcon && 'text-truncate-2' || 'text-truncate'"
        class="text-start text-wrap">
        {{ description }}
      </v-list-item-subtitle>
    </v-list-item-content>
  </v-list-item>
</template>
<script>
export default {
  props: {
    documentsWidget: {
      type: Object,
      default: null,
    },
    showName: {
      type: Boolean,
      default: false,
    },
    showDescription: {
      type: Boolean,
      default: false,
    },
    largeIcon: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    name() {
      return this.documentsWidget?.name?.[this.$root.language] || this.documentsWidget?.name?.[this.$root.defaultLanguage];
    },
    description() {
      return this.documentsWidget?.description?.[this.$root.language] || this.documentsWidget?.description?.[this.$root.defaultLanguage];
    },
    url() {
      return this.$documentsWidgetService.toLinkUrl(this.documentsWidget?.url);
    },
    target() {
      return this.documentsWidget?.sameTab && '_self' || '_blank';
    },
    iconUrl() {
      if (this.documentsWidget?.iconSrc) {
        return this.$utils.convertImageDataAsSrc(this.documentsWidget.iconSrc);
      } else {
        return this.documentsWidget?.iconUrl;
      }
    },
    iconSize() {
      return this.largeIcon && 48 || 34;
    },
  },
};
</script>
