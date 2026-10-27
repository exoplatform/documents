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
  <div class="d-flex align-center text-caption font-weight-bold overflow-hidden">
    <v-avatar
      color="grey"
      size="22"
      class="flex-shrink-0 me-2"
      aria-hidden="true">
      <v-icon size="11" color="white">fas fa-users</v-icon>
    </v-avatar>
    <span class="text-truncate">{{ name }}</span>
  </div>
</template>
<script>
/**
 * The name of the lane the least active speakers share, « Others (N) »: it
 * holds several speakers, so it offers no naming.
 */
export default {
  props: {
    name: {
      type: String,
      default: null,
    },
  },
};
</script>
