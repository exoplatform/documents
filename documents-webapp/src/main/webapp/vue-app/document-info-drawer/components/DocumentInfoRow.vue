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
  <div :class="alignTop ? 'align-start' : 'align-center'" class="documentInfoRow d-flex py-1">
    <div class="documentInfoRowLabel text-sub-title text-truncate flex-shrink-0">
      {{ label }}
    </div>
    <div class="documentInfoRowValue d-flex align-center flex-grow-1 text-truncate">
      <slot v-if="$scopedSlots.default"></slot>
      <template v-else>{{ value }}</template>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    label: {
      type: String,
      default: null,
    },
    value: {
      type: String,
      default: null,
    },
    /**
     * Aligns the label with the first line of a value spanning several
     * lines, rather than centring it on the whole value.
     */
    alignTop: {
      type: Boolean,
      default: false,
    },
  },
};
</script>
