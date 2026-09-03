<!--

  Copyright (C) 2025 eXo Platform SAS

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program.  If not, see <gnu.org/licenses>.

-->

<template>
  <div>
    <v-divider
      v-if="showDividerAbove && !isMobile"
      class="dividerStyle" />
    <v-list-item
      dense
      link
      class="ps-3"
      :href="href"
      :target="href && target"
      @click.stop="handleClick">
      <v-list-item-icon class="me-2">
        <v-icon
          :class="iconExtraClass"
          class="ma-auto"
          size="16">
          {{ icon }}
        </v-icon>
      </v-list-item-icon>
      <v-list-item-title class="text-body menu-text-color">
        <span :class="labelExtraClass">
          {{ label }}
        </span>
        <v-icon
          v-if="isGroup"
          size="16"
          class="absolute-vertical-center r-3">
          fa-caret-right
        </v-icon>
      </v-list-item-title>
    </v-list-item>
    <v-divider
      v-if="showDivider && !isMobile"
      class="dividerStyle" />
  </div>
</template>

<script>
export default {
  props: {
    icon: { 
      type: String, 
      default: null 
    },
    iconExtraClass: {
      type: String,
      default: null
    },
    labelExtraClass: {
      type: String,
      default: null
    },
    label: { 
      type: String, 
      default: null 
    },
    isMobile: {
      type: Boolean,
      default: false
    },
    showDivider: {
      type: Boolean,
      default: false
    },
    showDividerAbove: {
      type: Boolean,
      default: false
    },
    isGroup: {
      type: Boolean,
      default: false
    },
    href: {
      type: String,
      default: null
    },
    target: {
      type: String,
      default: '_blank'
    },
  },
  methods: {
    handleClick(event) {
      if (!this.href) {
        event.preventDefault();
      }
      this.$emit('click');
      if (!this.isGroup) {
        this.$root.$emit('close-action-context-menu');
      }
    },
  },
};
</script>