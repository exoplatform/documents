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
    class="clickable pt-1 mx-2"
    @click="quickFilter()">
    <v-icon
      size="21"
      class="pe-1 pb-1 iconStyle">
      mdi-filter
    </v-icon>
    <span class="ps-1">{{ $t('documents.label.quickFilter') }}</span>
  </div>
</template>
<script>
export default {
  data: () => ({
    viewTab: 'RECENT',
    spaceId: eXo.env.portal.spaceId,
  }),

  methods: {
    quickFilter(){
      this.$root.$emit('set-search-type', 'All');
    },
  },
};
</script>