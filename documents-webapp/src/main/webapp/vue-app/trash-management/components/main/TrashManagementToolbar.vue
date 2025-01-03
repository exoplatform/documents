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
  <application-toolbar v-if="hasSelection">
    <template v-if="!isMobile" #left>
      <documents-trash-items-bulk-delete
        :selection-length="selectionLength"
        :bulk-action-progress="bulkActionProgress"
        @delete-items="$emit('delete-items')" />
    </template>
  </application-toolbar>
</template>
<script>
export default {
  props: {
    selectionLength: {
      type: Number,
      default: 0
    },
    bulkActionProgress: {
      type: Boolean,
      default: false
    }

  },
  data: () => ({
    loading: false,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    hasSelection() {
      return this.selectionLength > 0;
    }
  },
};
</script>
