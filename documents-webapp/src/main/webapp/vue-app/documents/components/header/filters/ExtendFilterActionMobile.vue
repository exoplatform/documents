<!--
 *
 * Copyright (C) 2024 eXo Platform SAS.
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
 *
-->

<template>
  <div
    v-if="query"
    class="clickable pt-1 mx-2"
    @click="extendSearch()">
    <v-icon
      size="21"
      class="pe-1 pb-1 iconStyle">
      {{ icon }}
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.extendSearch') }}</span>
  </div>
</template>
<script>
export default {
  props: {
    query: {
      type: String,
      default: null,
    },
    extendedSearch: {
      type: Boolean,
      default: false
    },
  },

  computed: {
    icon() {
      return this.extendedSearch && 'mdi-check-bold' || 'mdi-file-search';
    }
  },

  methods: {
    extendSearch() {
      if (!this.extendedSearch) {
        this.$root.$emit('document-extended-search');
        this.$root.$emit('close-mobile-filter-menu',false);
      }
    }
  }

};
</script>