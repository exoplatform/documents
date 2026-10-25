<!--
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
-->
<template>
  <v-list-item
    :disabled="loading"
    dense
    link
    @click="$emit('click')">
    <v-list-item-icon class="me-3 my-auto">
      <v-progress-circular
        v-if="loading"
        size="16"
        width="2"
        indeterminate />
      <v-icon v-else size="16">{{ icon }}</v-icon>
    </v-list-item-icon>
    <v-list-item-title class="text-body">{{ label }}</v-list-item-title>
  </v-list-item>
</template>
<script>
export default {
  props: {
    icon: {
      type: String,
      default: null,
    },
    label: {
      type: String,
      default: null,
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
};
</script>
