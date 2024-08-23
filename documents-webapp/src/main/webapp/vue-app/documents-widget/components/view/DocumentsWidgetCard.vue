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
  <component
    v-bind="isCard && {
      hover: true,
      outlined: true,
    } || {
      text: true,
      class: 'transparent',
    }"
    :is="isCard && 'v-card' || 'v-btn'"
    :href="url"
    :target="target"
    :width="itemWidth"
    :height="itemHeight"
    class="mx-2">
    <v-card
      :title="description || name"
      :min-width="itemWidth"
      :max-width="itemWidth"
      :min-height="itemHeight"
      :max-height="itemHeight"
      class="d-flex flex-column full-height full-width transparent border-box-sizing align-center justify-start overflow-hidden text-none"
      flat>
      <documents-widget-icon
        :icon-size="iconSize"
        :icon-url="iconUrl"
        :class="showName && 'pb-0 col-6 align-end' || 'col-12 align-center'"
        class="justify-center" />
      <div
        v-if="showName && name"
        class="pt-3 px-1 full-width text-truncate-2 text-body">
        {{ showName && name || '' }}
      </div>
    </v-card>
  </component>
</template>
<script>
export default {
  props: {
    documentsWidget: {
      type: Object,
      default: null,
    },
    type: {
      type: String,
      default: null,
    },
    showName: {
      type: Boolean,
      default: false,
    },
    largeIcon: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    hover: false,
  }),
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
    itemSize() {
      return this.largeIcon && 150 || 135;
    },
    itemWidth() {
      return this.showName && this.itemSize || parseInt(this.itemSize / 2);
    },
    itemHeight() {
      return this.showName && this.itemSize || parseInt(this.itemSize / 2);
    },
    isCard() {
      return this.type === 'CARD';
    },
  },
};
</script>
